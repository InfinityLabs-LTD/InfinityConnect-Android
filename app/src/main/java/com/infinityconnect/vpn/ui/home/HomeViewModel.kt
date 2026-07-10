package com.infinityconnect.vpn.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.infinityconnect.vpn.domain.model.AppResult
import com.infinityconnect.vpn.domain.model.VpnKey
import com.infinityconnect.vpn.domain.usecase.ObserveKeysUseCase
import com.infinityconnect.vpn.domain.usecase.SyncKeysUseCase
import com.infinityconnect.vpn.ui.util.toMessage
import com.infinityconnect.vpn.vpn.TunnelState
import com.infinityconnect.vpn.vpn.TunnelStats
import com.infinityconnect.vpn.vpn.VpnController
import com.infinityconnect.vpn.vpn.VpnStateHolder
import dagger.hilt.android.lifecycle.HiltViewModel
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
    val refreshing: Boolean = false,
    val loadingFirstTime: Boolean = true,
    val error: String? = null,
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    observeKeys: ObserveKeysUseCase,
    private val syncKeys: SyncKeysUseCase,
    private val vpnController: VpnController,
    stateHolder: VpnStateHolder,
) : ViewModel() {

    private val _ui = MutableStateFlow(HomeUiState())
    val ui: StateFlow<HomeUiState> = _ui.asStateFlow()

    /** Состояние туннеля и статистика — напрямую из holder'а. */
    val tunnelState: StateFlow<TunnelState> = stateHolder.state
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), TunnelState.Disconnected)
    val stats: StateFlow<TunnelStats> = stateHolder.stats
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), TunnelStats())

    init {
        // Подписка на кэш ключей: обновляем список и авто-выбираем ключ.
        observeKeys()
            .onEach { keys ->
                _ui.update { state ->
                    val selected = state.selectedKeyId
                        ?: keys.firstOrNull { it.isActive }?.id
                        ?: keys.firstOrNull()?.id
                    state.copy(keys = keys, selectedKeyId = selected)
                }
            }
            .launchIn(viewModelScope)

        // Синхронизация при старте.
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
        _ui.update { it.copy(selectedKeyId = keyId, selectedServerIndex = 0, selectedServerName = null) }
    }

    /** Обновляет выбранный сервер (вызывается с экрана серверов). */
    fun selectServer(index: Int, name: String?) {
        _ui.update { it.copy(selectedServerIndex = index, selectedServerName = name) }
    }

    /** Активно ли соединение (подключено/подключается). */
    fun isConnectingOrConnected(): Boolean {
        val s = tunnelState.value
        return s is TunnelState.Connected || s is TunnelState.Connecting
    }

    fun disconnect() = vpnController.disconnect()

    /** Intent запроса разрешения VPN (или null, если уже выдано). */
    fun vpnPrepareIntent() = vpnController.prepareIntent()

    /** Подключается к выбранному ключу/серверу (после проверки разрешения). */
    fun connect() {
        val keyId = _ui.value.selectedKeyId ?: return
        vpnController.connect(keyId, _ui.value.selectedServerIndex, _ui.value.selectedServerName)
    }

    /** Совместимость с экраном: подключение после выданного разрешения. */
    fun connectAfterPermission() = connect()
}
