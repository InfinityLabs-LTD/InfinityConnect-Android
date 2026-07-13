package com.infinityconnect.vpn.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.infinityconnect.vpn.data.local.SettingsStore
import com.infinityconnect.vpn.domain.model.AppResult
import com.infinityconnect.vpn.domain.model.PingMethod
import com.infinityconnect.vpn.domain.model.PingMode
import com.infinityconnect.vpn.domain.model.PingSettings
import com.infinityconnect.vpn.domain.model.RoutingMode
import com.infinityconnect.vpn.domain.model.RoutingSettings
import com.infinityconnect.vpn.domain.repository.RoutingRepository
import com.infinityconnect.vpn.ui.util.toMessage
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

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
) : ViewModel() {

    private val _ui = MutableStateFlow(SettingsUiState())
    val ui: StateFlow<SettingsUiState> = _ui.asStateFlow()

    private var rulesUrlEdited = false
    private var pingUrlEdited = false

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
