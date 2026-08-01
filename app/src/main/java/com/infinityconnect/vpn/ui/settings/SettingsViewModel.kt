package com.infinityconnect.vpn.ui.settings

import android.Manifest
import android.content.Context
import androidx.annotation.StringRes
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.infinityconnect.vpn.R
import com.infinityconnect.vpn.data.local.SettingsStore
import com.infinityconnect.vpn.domain.model.AppResult
import com.infinityconnect.vpn.domain.model.AppRoutingMode
import com.infinityconnect.vpn.domain.model.Language
import com.infinityconnect.vpn.domain.model.PingMethod
import com.infinityconnect.vpn.domain.model.PingMode
import com.infinityconnect.vpn.domain.model.PingSettings
import com.infinityconnect.vpn.domain.model.RoutingMode
import com.infinityconnect.vpn.domain.model.RoutingSettings
import com.infinityconnect.vpn.domain.model.SitePreset
import com.infinityconnect.vpn.domain.model.SiteRoutingMode
import com.infinityconnect.vpn.domain.repository.RoutingRepository
import com.infinityconnect.vpn.ui.util.ErrorMessage
import com.infinityconnect.vpn.ui.util.LanguageController
import com.infinityconnect.vpn.ui.util.toMessage
import com.infinityconnect.vpn.vpn.TunnelState
import com.infinityconnect.vpn.vpn.VpnController
import com.infinityconnect.vpn.vpn.VpnStateHolder
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
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
    /** Ошибка как ID строки + аргументы — резолвится на экране (см. errorText). */
    val rulesError: ErrorMessage? = null,
    @StringRes val rulesMessage: Int? = null,
    // Маршрутизация по приложениям
    val appMode: AppRoutingMode = AppRoutingMode.OFF,
    val selectedApps: Set<String> = emptySet(),
    // Маршрутизация по сайтам
    val siteMode: SiteRoutingMode = SiteRoutingMode.OFF,
    val sitesText: String = "",
    val sitePresets: Set<SitePreset> = emptySet(),
    // Пинг
    val pingMethod: PingMethod = PingMethod.PROXY_GET,
    val pingMode: PingMode = PingMode.DEFAULT,
    val pingUrl: String = PingSettings.DEFAULT_TEST_URL,
    val pingTimeoutSec: Int = PingSettings.DEFAULT_TIMEOUT_SEC,
    // Язык интерфейса
    val language: Language = Language.SYSTEM,
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val routingRepository: RoutingRepository,
    private val settingsStore: SettingsStore,
    private val vpnController: VpnController,
    private val vpnStateHolder: VpnStateHolder,
    private val languageController: LanguageController,
    @ApplicationContext private val appContext: Context,
) : ViewModel() {

    private val _ui = MutableStateFlow(SettingsUiState())
    val ui: StateFlow<SettingsUiState> = _ui.asStateFlow()

    /** Кэш списка установленных приложений (грузится один раз по требованию). */
    private val _apps = MutableStateFlow<List<InstalledApp>>(emptyList())
    val apps: StateFlow<List<InstalledApp>> = _apps.asStateFlow()

    /** true, пока идёт разбор пакетов: список наполняется порциями и ещё неполон. */
    private val _appsLoading = MutableStateFlow(false)
    val appsLoading: StateFlow<Boolean> = _appsLoading.asStateFlow()

    private var appsJob: Job? = null

    private var rulesUrlEdited = false
    private var pingUrlEdited = false
    private var sitesEdited = false
    private var restartJob: Job? = null

    /**
     * Применяет изменённые настройки маршрутизации к активному туннелю:
     * конфиг ядра/TUN строится только при подключении, поэтому при активном
     * туннеле переподключаемся к тому же серверу. Дебаунс — пользователь
     * обычно щёлкает несколько пресетов подряд.
     */
    private fun scheduleTunnelRestart() {
        if (vpnStateHolder.activeConnection.value == null) return
        if (vpnStateHolder.state.value !is TunnelState.Connected) return
        restartJob?.cancel()
        restartJob = viewModelScope.launch {
            delay(RESTART_DEBOUNCE_MS)
            // Перечитываем: за время дебаунса туннель могли выключить.
            val current = vpnStateHolder.activeConnection.value ?: return@launch
            if (vpnStateHolder.state.value !is TunnelState.Connected) return@launch
            vpnController.disconnect()
            vpnStateHolder.state
                .first { it is TunnelState.Disconnected || it is TunnelState.Error }
            vpnController.connect(current.keyId, current.serverIndex, current.serverName)
        }
    }

    init {
        routingRepository.settings
            .onEach { s -> applyRouting(s) }
            .launchIn(viewModelScope)
        settingsStore.ping
            .onEach { p -> applyPing(p) }
            .launchIn(viewModelScope)
        // Отметку в списке языков берём из того, что РЕАЛЬНО применено
        // (languageController), а не из хранилища: на Android 13+ язык можно
        // сменить в системных настройках мимо приложения, и хранилище об этом
        // не знает. Значение из DataStore нужно лишь как исходное на API < 33
        // до первого применения.
        _ui.update { it.copy(language = languageController.current) }
        viewModelScope.launch {
            val stored = settingsStore.currentLanguage()
            if (languageController.current == Language.SYSTEM && stored != Language.SYSTEM) {
                _ui.update { it.copy(language = stored) }
            }
        }
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

    // ── Язык интерфейса ──

    /**
     * Меняет язык интерфейса. Сохранение и применение идут вместе: применение —
     * источник правды для UI, хранилище нужно для восстановления на API < 33.
     *
     * После [LanguageController.apply] система пересоздаёт Activity, поэтому
     * писать в хранилище нужно ДО применения — иначе корутина VM может не
     * успеть выполниться.
     */
    fun selectLanguage(language: Language) {
        if (_ui.value.language == language) return
        _ui.update { it.copy(language = language) }
        viewModelScope.launch {
            settingsStore.setLanguage(language)
            languageController.apply(language)
        }
    }

    // ── Маршрутизация ──

    fun selectMode(mode: RoutingMode) {
        _ui.update { it.copy(mode = mode) }
        viewModelScope.launch { routingRepository.setMode(mode); scheduleTunnelRestart() }
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
                        it.copy(
                            downloading = false,
                            rulesMessage = R.string.routing_rules_downloaded,
                            mode = RoutingMode.CUSTOM,
                        )
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
        viewModelScope.launch { routingRepository.setAppMode(mode); scheduleTunnelRestart() }
        if (mode != AppRoutingMode.OFF) loadApps()
    }

    /** Переключает вхождение пакета в allow/disallow-список. */
    fun toggleApp(packageName: String) {
        val next = _ui.value.selectedApps.toMutableSet().apply {
            if (!add(packageName)) remove(packageName)
        }
        _ui.update { it.copy(selectedApps = next) }
        viewModelScope.launch { routingRepository.setApps(next); scheduleTunnelRestart() }
    }

    /**
     * Лениво загружает список установленных приложений.
     *
     * Тяжёлая часть — не перечисление пакетов, а [PackageManager.getApplicationLabel]:
     * каждый вызов открывает и парсит ресурсы чужого APK через IPC. На устройстве с
     * сотнями пакетов последовательный проход занимал десятки секунд (а на медленном
     * хранилище — минуты). Поэтому:
     *  - отбрасываем пакеты без разрешения INTERNET (для VPN они бессмысленны) — это
     *    убирает основную массу служебных системных пакетов ещё до чтения меток;
     *  - метки читаем параллельно на [Dispatchers.IO] (операция IO-bound, не CPU-bound);
     *  - результат отдаём порциями, чтобы список появлялся сразу и наполнялся на глазах.
     *
     * Разрешения НЕЛЬЗЯ запрашивать одним `getInstalledPackages(GET_PERMISSIONS)`:
     * requestedPermissions всех пакетов разом не влезает в буфер Binder-транзакции
     * (1 МБ на процесс) и вызов падает с TransactionTooLargeException — список
     * получался пустым. Поэтому перечисляем пакеты «лёгким» вызовом, а разрешения
     * дотягиваем поштучно внутри тех же параллельных порций, что и метки.
     */
    fun loadApps() {
        if (_apps.value.isNotEmpty() || appsJob?.isActive == true) return
        _appsLoading.value = true
        appsJob = viewModelScope.launch {
            try {
                val pm = appContext.packageManager
                val installed = withContext(Dispatchers.IO) {
                    runCatching { pm.getInstalledApplications(0) }.getOrDefault(emptyList())
                }

                val candidates = installed.filter { info ->
                    info.enabled && info.packageName != appContext.packageName
                }

                val collected = ArrayList<InstalledApp>(candidates.size)
                // Порции: параллельно читаем разрешения и метки, сразу публикуем готовое.
                candidates.chunked(APPS_CHUNK).forEach { chunk ->
                    val part = withContext(Dispatchers.IO) {
                        chunk.map { info ->
                            async {
                                val hasInternet = runCatching {
                                    pm.getPackageInfo(info.packageName, PackageManager.GET_PERMISSIONS)
                                        .requestedPermissions
                                        ?.contains(Manifest.permission.INTERNET)
                                }.getOrNull() ?: false
                                if (!hasInternet) return@async null
                                val label = runCatching { pm.getApplicationLabel(info).toString() }
                                    .getOrNull()
                                    ?.takeIf { it.isNotBlank() }
                                    ?: info.packageName
                                InstalledApp(
                                    packageName = info.packageName,
                                    label = label,
                                    isSystem = (info.flags and ApplicationInfo.FLAG_SYSTEM) != 0,
                                )
                            }
                        }.awaitAll().filterNotNull()
                    }
                    collected += part
                    _apps.value = collected.sortedWith(APPS_ORDER)
                }
            } finally {
                _appsLoading.value = false
            }
        }
    }

    // ── Маршрутизация по сайтам ──

    fun selectSiteMode(mode: SiteRoutingMode) {
        _ui.update { it.copy(siteMode = mode) }
        viewModelScope.launch { routingRepository.setSiteMode(mode); scheduleTunnelRestart() }
    }

    /** Переключает пресет доменной маршрутизации (multi-select). */
    fun toggleSitePreset(preset: SitePreset) {
        val next = _ui.value.sitePresets.toMutableSet().apply {
            if (!add(preset)) remove(preset)
        }
        _ui.update { it.copy(sitePresets = next) }
        viewModelScope.launch { routingRepository.setSitePresets(next); scheduleTunnelRestart() }
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
        viewModelScope.launch { routingRepository.setSites(domains); scheduleTunnelRestart() }
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

    private companion object {
        /** Пауза перед переподключением после последнего изменения настроек. */
        const val RESTART_DEBOUNCE_MS = 1500L

        /** Размер порции пакетов, метки которых читаются параллельно и публикуются разом. */
        const val APPS_CHUNK = 40

        /** Пользовательские приложения сверху, дальше по алфавиту. */
        val APPS_ORDER = compareBy<InstalledApp>({ it.isSystem }, { it.label.lowercase() })
    }
}
