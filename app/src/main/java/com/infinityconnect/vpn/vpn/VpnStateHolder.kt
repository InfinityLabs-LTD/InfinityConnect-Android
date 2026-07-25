package com.infinityconnect.vpn.vpn

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Единый источник состояния туннеля и статистики. Обновляется из
 * [InfinityVpnService]/движков, читается ViewModel'ями UI.
 *
 * ВАЖНО: VPN-сервис работает в отдельном процессе (`:vpn`), поэтому экземпляров
 * этого singleton'а ДВА — по одному на процесс. «Правда» о туннеле живёт в
 * процессе сервиса; в UI-процессе holder наполняется через [VpnStateBridge]
 * (broadcast сервис → UI). Для ViewModel'ей разницы нет: они как читали
 * StateFlow, так и читают.
 *
 * Менять состояние из UI-процесса бессмысленно — команды туннелю идут через
 * [VpnController].
 */
@Singleton
class VpnStateHolder @Inject constructor() {

    /**
     * Колбэк публикации снимка наружу (в процессе сервиса — броадкаст в UI).
     * Устанавливается сервисом; в UI-процессе остаётся null.
     */
    var onPublish: ((TunnelState, TunnelStats, String?, ActiveConnection?) -> Unit)? = null

    private val _state = MutableStateFlow<TunnelState>(TunnelState.Disconnected)
    val state: StateFlow<TunnelState> = _state.asStateFlow()

    private val _stats = MutableStateFlow(TunnelStats())
    val stats: StateFlow<TunnelStats> = _stats.asStateFlow()

    /** id ключа/имя активного сервера — для отображения в UI и уведомлении. */
    private val _activeServerName = MutableStateFlow<String?>(null)
    val activeServerName: StateFlow<String?> = _activeServerName.asStateFlow()

    /**
     * Параметры активного подключения (ключ + индекс сервера). Нужны, чтобы
     * применить изменённые настройки маршрутизации «на лету»: настройки читаются
     * движком только при старте, поэтому SettingsViewModel переподключает
     * туннель к тому же серверу.
     */
    data class ActiveConnection(val keyId: Long, val serverIndex: Int, val serverName: String?)

    private val _activeConnection = MutableStateFlow<ActiveConnection?>(null)
    val activeConnection: StateFlow<ActiveConnection?> = _activeConnection.asStateFlow()

    fun setActiveConnection(conn: ActiveConnection?) {
        _activeConnection.value = conn
        publish()
    }

    fun updateState(state: TunnelState) {
        _state.value = state
        publish()
    }

    fun updateStats(stats: TunnelStats) {
        _stats.value = stats
        publish()
    }

    fun setActiveServer(name: String?) {
        _activeServerName.value = name
        publish()
    }

    fun reset() {
        _state.value = TunnelState.Disconnected
        _stats.value = TunnelStats()
        _activeServerName.value = null
        _activeConnection.value = null
        publish()
    }

    /** Отдаёт снимок наружу, если публикация настроена (процесс сервиса). */
    private fun publish() {
        onPublish?.invoke(_state.value, _stats.value, _activeServerName.value, _activeConnection.value)
    }
}
