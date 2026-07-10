package com.infinityconnect.vpn.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.infinityconnect.vpn.domain.model.AppResult
import com.infinityconnect.vpn.domain.model.SubscriptionServer
import com.infinityconnect.vpn.domain.model.VpnKey
import com.infinityconnect.vpn.domain.usecase.GetSubscriptionServersUseCase
import com.infinityconnect.vpn.domain.usecase.ObserveKeysUseCase
import com.infinityconnect.vpn.domain.usecase.PingServerUseCase
import com.infinityconnect.vpn.domain.usecase.SyncKeysUseCase
import com.infinityconnect.vpn.ui.util.toMessage
import com.infinityconnect.vpn.vpn.TunnelState
import com.infinityconnect.vpn.vpn.TunnelStats
import com.infinityconnect.vpn.vpn.VpnController
import com.infinityconnect.vpn.vpn.VpnStateHolder
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** UI-состояние главного экрана. */
data class HomeUiState(
    val keys: List<VpnKey> = emptyList(),
    val selectedKeyId: Long? = null,
    val selectedServerIndex: Int = 0,
    val selectedServerName: String? = null,
    /** Серверы выбранного ключа (стиль Happ) — с метаданными и пингом. */
    val servers: List<SubscriptionServer> = emptyList(),
    val serversLoading: Boolean = false,
    val refreshing: Boolean = false,
    val loadingFirstTime: Boolean = true,
    val error: String? = null,
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    observeKeys: ObserveKeysUseCase,
    private val syncKeys: SyncKeysUseCase,
    private val getServers: GetSubscriptionServersUseCase,
    private val pingServer: PingServerUseCase,
    private val vpnController: VpnController,
    stateHolder: VpnStateHolder,
) : ViewModel() {

    private val _ui = MutableStateFlow(HomeUiState())
    val ui: StateFlow<HomeUiState> = _ui.asStateFlow()

    private var serversJob: Job? = null

    /** Состояние туннеля и статистика — напрямую из holder'а. */
    val tunnelState: StateFlow<TunnelState> = stateHolder.state
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), TunnelState.Disconnected)
    val stats: StateFlow<TunnelStats> = stateHolder.stats
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), TunnelStats())

    init {
        // Подписка на кэш ключей: обновляем список и авто-выбираем ключ.
        observeKeys()
            .onEach { keys ->
                val prevSelected = _ui.value.selectedKeyId
                val selected = prevSelected
                    ?: keys.firstOrNull { it.isActive }?.id
                    ?: keys.firstOrNull()?.id
                _ui.update { it.copy(keys = keys, selectedKeyId = selected) }
                // Загружаем серверы для авто-выбранного ключа.
                if (prevSelected == null && selected != null) loadServers(selected)
            }
            .launchIn(viewModelScope)

        refresh(initial = true)
    }

    fun refresh(initial: Boolean = false) {
        _ui.update { it.copy(refreshing = !initial, loadingFirstTime = initial, error = null) }
        viewModelScope.launch {
            when (val result = syncKeys()) {
                is AppResult.Success ->
                    _ui.update { it.copy(refreshing = false, loadingFirstTime = false) }
                is AppResult.Failure ->
                    _ui.update {
                        it.copy(
                            refreshing = false,
                            loadingFirstTime = false,
                            error = result.error.toMessage(),
                        )
                    }
            }
        }
    }

    fun selectKey(keyId: Long) {
        if (_ui.value.selectedKeyId == keyId) return
        _ui.update {
            it.copy(
                selectedKeyId = keyId,
                selectedServerIndex = 0,
                selectedServerName = null,
                servers = emptyList(),
            )
        }
        loadServers(keyId)
    }

    /** Загружает серверы подписки ключа и асинхронно пингует каждый. */
    private fun loadServers(keyId: Long) {
        serversJob?.cancel()
        _ui.update { it.copy(serversLoading = true, servers = emptyList()) }
        serversJob = viewModelScope.launch {
            when (val result = getServers(keyId)) {
                is AppResult.Success -> {
                    _ui.update { it.copy(servers = result.data, serversLoading = false) }
                    pingAll(keyId, result.data)
                }
                is AppResult.Failure ->
                    _ui.update { it.copy(serversLoading = false) }
            }
        }
    }

    /** Пингует серверы и обновляет их в состоянии по мере готовности. */
    private fun pingAll(keyId: Long, servers: List<SubscriptionServer>) {
        servers.forEach { server ->
            viewModelScope.launch {
                val ping = pingServer(server.address, server.port)
                _ui.update { state ->
                    // Пропускаем, если ключ уже переключён.
                    if (state.selectedKeyId != keyId) return@update state
                    state.copy(
                        servers = state.servers.map {
                            if (it.index == server.index) it.copy(pingMs = ping) else it
                        },
                    )
                }
            }
        }
    }

    /** Выбор сервера из раскрытого списка. */
    fun selectServer(server: SubscriptionServer) {
        _ui.update { it.copy(selectedServerIndex = server.index, selectedServerName = server.name) }
    }

    /** Активно ли соединение (подключено/подключается). */
    fun isConnectingOrConnected(): Boolean {
        val s = tunnelState.value
        return s is TunnelState.Connected || s is TunnelState.Connecting
    }

    fun disconnect() = vpnController.disconnect()

    fun vpnPrepareIntent() = vpnController.prepareIntent()

    fun connect() {
        val keyId = _ui.value.selectedKeyId ?: return
        vpnController.connect(keyId, _ui.value.selectedServerIndex, _ui.value.selectedServerName)
    }

    fun connectAfterPermission() = connect()
}
