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
 * Singleton, чтобы сервис (отдельный процесс/компонент) и UI видели одно и то
 * же состояние в рамках одного процесса приложения.
 */
@Singleton
class VpnStateHolder @Inject constructor() {

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
    }

    fun updateState(state: TunnelState) {
        _state.value = state
    }

    fun updateStats(stats: TunnelStats) {
        _stats.value = stats
    }

    fun setActiveServer(name: String?) {
        _activeServerName.value = name
    }

    fun reset() {
        _state.value = TunnelState.Disconnected
        _stats.value = TunnelStats()
        _activeServerName.value = null
        _activeConnection.value = null
    }
}
