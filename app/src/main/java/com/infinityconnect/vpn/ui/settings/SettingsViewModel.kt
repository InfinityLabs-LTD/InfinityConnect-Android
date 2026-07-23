package com.infinityconnect.vpn.ui.settings

import android.content.Context
import android.content.pm.ApplicationInfo
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.infinityconnect.vpn.data.local.SettingsStore
import com.infinityconnect.vpn.domain.model.AppResult
import com.infinityconnect.vpn.domain.model.AppRoutingMode
import com.infinityconnect.vpn.domain.model.PingMethod
import com.infinityconnect.vpn.domain.model.PingMode
import com.infinityconnect.vpn.domain.model.PingSettings
import com.infinityconnect.vpn.domain.model.RoutingMode
import com.infinityconnect.vpn.domain.model.RoutingSettings
import com.infinityconnect.vpn.domain.model.SitePreset
import com.infinityconnect.vpn.domain.model.SiteRoutingMode
import com.infinityconnect.vpn.domain.repository.RoutingRepository
import com.infinityconnect.vpn.ui.util.toMessage
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

/** Установленное приложение для экрана выбора per-app маршрутизации. */
data class InstalledApp(
    val packageName: String,
    val label: String,
    val isSystem: Boolean,
)

/** UI-состояние экрана настроек (маршрутизация + пинг). */
data class SettingsUiState(
    // Маршрутизация
    val mode: RoutingMode = RoutingMode.ALL,
    val rulesUrl: String = "",
    val rulesUpdatedAt: Long? = null,
    val hasRules: Boolean = false,
    val downloading: Boolean = false,
    val rulesError: String? = null,
    val rulesMessage: String? = null,
    // Маршрутизация по приложениям
    val appMode: AppRoutingMode = AppRoutingMode.OFF,
    val selectedApps: Set<String> = emptySet(),
    // Маршрутизация по сайтам
    val siteMode: SiteRoutingMode = SiteRoutingMode.OFF,
    val sitesText: String = "",
    val sitePresets: Set<SitePreset> = emptySet(),
    // Пинг
    val pingMethod: PingMethod = PingMethod.TCP,
    val pingMode: PingMode = PingMode.DEFAULT,
    val pingUrl: String = PingSettings.DEFAULT_TEST_URL,
    val pingTimeoutSec: Int = PingSettings.DEFAULT_TIMEOUT_SEC,
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val routingRepository: RoutingRepository,
    private val settingsStore: SettingsStore,
    @ApplicationContext private val appContext: Context,
) : ViewModel() {

    private val _ui = MutableStateFlow(SettingsUiState())
    val ui: StateFlow<SettingsUiState> = _ui.asStateFlow()

    /** Кэш списка установленных приложений (грузится один раз по требованию). */
    private val _apps = MutableStateFlow<List<InstalledApp>>(emptyList())
    val apps: StateFlow<List<InstalledApp>> = _apps.asStateFlow()

    private var rulesUrlEdited = false
    private var pingUrlEdited = false
    private var sitesEdited = false

    init {
        routingRepository.settings
            .onEach { s -> applyRouting(s) }
            .launchIn(viewModelScope)
        settingsStore.ping
            .onEach { p -> applyPing(p) }
            .launchIn(viewModelScope)
    }

    private fun applyRouting(s: RoutingSettings) {
        _ui.update {
            it.copy(
                mode = s.mode,
                rulesUrl = if (rulesUrlEdited) it.rulesUrl else (s.rulesUrl ?: ""),
                rulesUpdatedAt = s.rulesUpdatedAt,
                hasRules = s.rulesJson != null,
                appMode = s.appMode,
                selectedApps = s.apps,
                siteMode = s.siteMode,
                sitesText = if (sitesEdited) it.sitesText else s.sites.joinToString("\n"),
                sitePresets = s.sitePresets,
            )
        }
    }

    private fun applyPing(p: PingSettings) {
        _ui.update {
            it.copy(
                pingMethod = p.method,
                pingMode = p.mode,
                pingUrl = if (pingUrlEdited) it.pingUrl else p.testUrl,
                pingTimeoutSec = p.timeoutSec,
            )
        }
    }

    // ── Маршрутизация ──

    fun selectMode(mode: RoutingMode) {
        _ui.update { it.copy(mode = mode) }
        viewModelScope.launch { routingRepository.setMode(mode) }
    }

    fun onRulesUrlChange(url: String) {
        rulesUrlEdited = true
        _ui.update { it.copy(rulesUrl = url, rulesError = null) }
    }

    fun downloadRules() {
        val url = _ui.value.rulesUrl.trim()
        _ui.update { it.copy(downloading = true, rulesError = null, rulesMessage = null) }
        viewModelScope.launch {
            when (val r = routingRepository.downloadRules(url)) {
                is AppResult.Success -> {
                    rulesUrlEdited = false
                    routingRepository.setMode(RoutingMode.CUSTOM)
                    _ui.update {
                        it.copy(downloading = false, rulesMessage = "Правила загружены", mode = RoutingMode.CUSTOM)
                    }
                }
                is AppResult.Failure ->
                    _ui.update { it.copy(downloading = false, rulesError = r.error.toMessage()) }
            }
        }
    }

    // ── Маршрутизация по приложениям ──

    fun selectAppMode(mode: AppRoutingMode) {
        _ui.update { it.copy(appMode = mode) }
        viewModelScope.launch { routingRepository.setAppMode(mode) }
        if (mode != AppRoutingMode.OFF) loadApps()
    }

    /** Переключает вхождение пакета в allow/disallow-список. */
    fun toggleApp(packageName: String) {
        val next = _ui.value.selectedApps.toMutableSet().apply {
            if (!add(packageName)) remove(packageName)
        }
        _ui.update { it.copy(selectedApps = next) }
        viewModelScope.launch { routingRepository.setApps(next) }
    }

    /** Лениво загружает список установленных приложений (тяжёлая операция → IO). */
    fun loadApps() {
        if (_apps.value.isNotEmpty()) return
        viewModelScope.launch {
            val list = withContext(Dispatchers.IO) {
                val pm = appContext.packageManager
                pm.getInstalledApplications(0)
                    .filter { it.packageName != appContext.packageName }
                    // Оставляем приложения с интернетом — остальные бессмысленны для VPN.
                    .map {
                        InstalledApp(
                            packageName = it.packageName,
                            label = pm.getApplicationLabel(it).toString(),
                            isSystem = (it.flags and ApplicationInfo.FLAG_SYSTEM) != 0,
                        )
                    }
                    .sortedWith(compareBy({ it.isSystem }, { it.label.lowercase() }))
            }
            _apps.update { list }
        }
    }

    // ── Маршрутизация по сайтам ──

    fun selectSiteMode(mode: SiteRoutingMode) {
        _ui.update { it.copy(siteMode = mode) }
        viewModelScope.launch { routingRepository.setSiteMode(mode) }
    }

    /** Переключает пресет доменной маршрутизации (multi-select). */
    fun toggleSitePreset(preset: SitePreset) {
        val next = _ui.value.sitePresets.toMutableSet().apply {
            if (!add(preset)) remove(preset)
        }
        _ui.update { it.copy(sitePresets = next) }
        viewModelScope.launch { routingRepository.setSitePresets(next) }
    }

    fun onSitesChange(text: String) {
        sitesEdited = true
        _ui.update { it.copy(sitesText = text) }
    }

    /** Сохраняет список доменов (по кнопке): по одному на строку/через запятую. */
    fun saveSites() {
        val domains = _ui.value.sitesText
            .split('\n', ',', ' ')
            .map { it.trim().removePrefix("https://").removePrefix("http://").trimEnd('/') }
            .filter { it.isNotBlank() }
            .distinct()
        sitesEdited = false
        _ui.update { it.copy(sitesText = domains.joinToString("\n")) }
        viewModelScope.launch { routingRepository.setSites(domains) }
    }

    // ── Пинг ──

    fun selectPingMethod(method: PingMethod) {
        _ui.update { it.copy(pingMethod = method) }
        viewModelScope.launch { settingsStore.setPingMethod(method) }
    }

    fun selectPingMode(mode: PingMode) {
        _ui.update { it.copy(pingMode = mode) }
        viewModelScope.launch { settingsStore.setPingMode(mode) }
    }

    /** Живое обновление слайдера таймаута (без записи в хранилище на каждый тик). */
    fun onPingTimeoutChange(sec: Int) {
        _ui.update { it.copy(pingTimeoutSec = sec) }
    }

    /** Фиксация таймаута в хранилище (по отпусканию слайдера). */
    fun savePingTimeout() {
        val sec = _ui.value.pingTimeoutSec
        viewModelScope.launch { settingsStore.setPingTimeout(sec) }
    }

    fun onPingUrlChange(url: String) {
        pingUrlEdited = true
        _ui.update { it.copy(pingUrl = url) }
    }

    /** Сохраняет тест-URL (по потере фокуса/кнопке). Пустой → дефолт Cloudflare. */
    fun savePingUrl() {
        val url = _ui.value.pingUrl.trim().ifBlank { PingSettings.DEFAULT_TEST_URL }
        pingUrlEdited = false
        _ui.update { it.copy(pingUrl = url) }
        viewModelScope.launch { settingsStore.setPingUrl(url) }
    }
}
