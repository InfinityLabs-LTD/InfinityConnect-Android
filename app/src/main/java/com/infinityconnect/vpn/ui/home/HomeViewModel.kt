package com.infinityconnect.vpn.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.infinityconnect.vpn.domain.model.AppResult
import com.infinityconnect.vpn.domain.model.SubscriptionServer
import com.infinityconnect.vpn.domain.model.KeyStatus
import com.infinityconnect.vpn.domain.model.VpnKey
import com.infinityconnect.vpn.domain.model.status
import com.infinityconnect.vpn.ui.util.isExpired
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
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject

/** UI-состояние главного экрана. */
data class HomeUiState(
    val keys: List<VpnKey> = emptyList(),
    /** Выбранный для подключения ключ. */
    val selectedKeyId: Long? = null,
    val selectedServerIndex: Int = 0,
    val selectedServerName: String? = null,
    /**
     * Серверы КАЖДОГО ключа (стиль Happ) — списки развёрнуты сразу у всех
     * подписок. Ключ карты — id подписки, значение — её серверы с пингом.
     */
    val serversByKey: Map<Long, List<SubscriptionServer>> = emptyMap(),
    /** Ключи, для которых список серверов ещё грузится (первый заход). */
    val loadingKeys: Set<Long> = emptySet(),
    /** Идёт измерение пинга серверов (кнопка «Пинг всех» — по всем подпискам). */
    val pinging: Boolean = false,
    /** Активный метод пинга — от него зависит цвет пинг-пилла. */
    val pingMethod: com.infinityconnect.vpn.domain.model.PingMethod =
        com.infinityconnect.vpn.domain.model.PingMethod.TCP,
    val refreshing: Boolean = false,
    val loadingFirstTime: Boolean = true,
    val error: String? = null,
    /** Одноразовое уведомление (snackbar), напр. «лимит устройств». */
    val notice: String? = null,
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    observeKeys: ObserveKeysUseCase,
    private val syncKeys: SyncKeysUseCase,
    private val getServers: GetSubscriptionServersUseCase,
    private val pingServer: PingServerUseCase,
    private val vpnController: VpnController,
    private val settingsStore: com.infinityconnect.vpn.data.local.SettingsStore,
    private val logoutUseCase: com.infinityconnect.vpn.domain.usecase.LogoutUseCase,
    private val stateHolder: VpnStateHolder,
) : ViewModel() {

    private val _ui = MutableStateFlow(HomeUiState())
    val ui: StateFlow<HomeUiState> = _ui.asStateFlow()

    private var pingJob: Job? = null
    private var switchJob: Job? = null

    /** Состояние туннеля и статистика — напрямую из holder'а. */
    val tunnelState: StateFlow<TunnelState> = stateHolder.state
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), TunnelState.Disconnected)
    val stats: StateFlow<TunnelStats> = stateHolder.stats
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), TunnelStats())

    init {
        // Подписка на кэш ключей: обновляем список и авто-выбираем ключ.
        observeKeys()
            .onEach { keys ->
                val selected = _ui.value.selectedKeyId
                    ?: keys.firstOrNull {
                        it.status(expired = isExpired(it.expiresAt)) ==
                            KeyStatus.ACTIVE
                    }?.id
                    ?: keys.firstOrNull()?.id
                _ui.update { it.copy(keys = keys, selectedKeyId = selected) }
            }
            .launchIn(viewModelScope)

        // Метод пинга: обновляем цвет пиллов и перемеряем при смене метода.
        settingsStore.ping
            .onEach { ps ->
                val changed = _ui.value.pingMethod != ps.method
                _ui.update { it.copy(pingMethod = ps.method) }
                if (changed) {
                    // Сбрасываем кэш пингов и меряем заново текущим методом.
                    _ui.update { st ->
                        st.copy(serversByKey = st.serversByKey.mapValues { (_, list) ->
                            list.map { it.copy(pingMs = null) }
                        })
                    }
                    pingAllKeys()
                }
            }
            .launchIn(viewModelScope)

        refresh(initial = true)
    }

    fun refresh(initial: Boolean = false) {
        _ui.update { it.copy(refreshing = !initial, loadingFirstTime = initial, error = null) }
        viewModelScope.launch {
            when (val result = syncKeys()) {
                is AppResult.Success -> {
                    _ui.update { it.copy(refreshing = false, loadingFirstTime = false) }
                    // Загружаем серверы всех ключей — списки развёрнуты у всех
                    // сразу. Подписки всегда перечитываем с сервера: при старте
                    // приложения (автообновление) и по кнопке обновления; кэш
                    // остаётся только офлайн-фолбэком.
                    loadAllServers(result.data.map { it.id }, force = true)
                }
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

    /** Загружает серверы всех ключей и запускает пинг по всем подпискам сразу. */
    private fun loadAllServers(keyIds: List<Long>, force: Boolean) {
        viewModelScope.launch {
            // Спиннер только у ключей, для которых серверов ещё нет.
            _ui.update { st ->
                val missing = keyIds.filter { st.serversByKey[it].isNullOrEmpty() }.toSet()
                st.copy(loadingKeys = st.loadingKeys + missing)
            }
            keyIds.forEach { keyId ->
                when (val result = getServers(keyId, forceRefresh = force)) {
                    is AppResult.Success ->
                        _ui.update { st ->
                            st.copy(
                                serversByKey = st.serversByKey + (keyId to result.data),
                                loadingKeys = st.loadingKeys - keyId,
                            )
                        }
                    is AppResult.Failure ->
                        _ui.update { it.copy(loadingKeys = it.loadingKeys - keyId) }
                }
            }
            pingAllKeys()
        }
    }

    /** Выбор ключа (подписки) — как цель для подключения. */
    fun selectKey(keyId: Long) {
        if (_ui.value.selectedKeyId == keyId) return
        _ui.update {
            it.copy(
                selectedKeyId = keyId,
                selectedServerIndex = 0,
                selectedServerName = null,
            )
        }
    }

    /** Кнопка «Пинг всех» (вверху экрана): перемеряет пинг по ВСЕМ подпискам. */
    fun pingAllSelected() {
        if (_ui.value.pinging) return
        _ui.update { st ->
            st.copy(serversByKey = st.serversByKey.mapValues { (_, list) ->
                list.map { it.copy(pingMs = null) }
            })
        }
        pingAllKeys()
    }

    /**
     * id недоступных ключей: любой статус, кроме ACTIVE (отключена, истекла,
     * лимит трафика/устройств) — их серверы не пингуем и не подключаем.
     */
    private fun blockedKeyIds(): Set<Long> = _ui.value.keys
        .filter {
            it.status(expired = isExpired(it.expiresAt)) !=
                KeyStatus.ACTIVE
        }
        .map { it.id }
        .toSet()

    /** Причина недоступности ключа — текст для snackbar. */
    private fun blockedReason(keyId: Long): String {
        val key = _ui.value.keys.firstOrNull { it.id == keyId }
            ?: return "Подписка недоступна"
        val status = key.status(expired = isExpired(key.expiresAt))
        return when (status) {
            KeyStatus.EXPIRED -> "Срок подписки истёк"
            KeyStatus.DISABLED -> "Подписка отключена"
            KeyStatus.LIMITED ->
                if (key.devicesExhausted) "Достигнут лимит устройств этой подписки"
                else "Достигнут лимит трафика этой подписки"
            else -> "Подписка недоступна"
        }
    }

    /** Пингует серверы всех ключей и обновляет их по мере готовности. */
    private fun pingAllKeys() {
        pingJob?.cancel()
        // Плоский список (keyId, server) по всем подпискам. Недоступные ключи
        // (отключена/истекла/лимит) не пингуем — подключение к ним запрещено.
        val blocked = blockedKeyIds()
        val targets = _ui.value.serversByKey.flatMap { (keyId, list) ->
            if (keyId in blocked) emptyList() else list.map { keyId to it }
        }
        if (targets.isEmpty()) {
            _ui.update { it.copy(pinging = false) }
            return
        }
        _ui.update { it.copy(pinging = true) }
        pingJob = viewModelScope.launch {
            // Ограничиваем число одновременных измерений: параллельные сокеты/DNS
            // конкурируют за сеть и завышают задержку. Небольшой лимит держит
            // значения стабильными.
            val gate = Semaphore(PING_CONCURRENCY)
            val jobs = targets.map { (keyId, server) ->
                launch {
                    val ping = gate.withPermit { pingServer(server) }
                    _ui.update { state ->
                        val list = state.serversByKey[keyId] ?: return@update state
                        val updated = list.map {
                            if (it.index == server.index) it.copy(pingMs = ping) else it
                        }
                        state.copy(serversByKey = state.serversByKey + (keyId to updated))
                    }
                }
            }
            jobs.forEach { it.join() }
            _ui.update { it.copy(pinging = false) }
        }
    }

    /**
     * Выбор сервера из раскрытого списка конкретной подписки. Если уже есть
     * активное соединение — отключаемся от текущего и подключаемся к новому.
     */
    fun selectServer(keyId: Long, server: SubscriptionServer) {
        // Подписка недоступна (отключена/истекла/лимит) — подключение запрещено.
        if (keyId in blockedKeyIds()) {
            _ui.update { it.copy(notice = blockedReason(keyId)) }
            return
        }
        _ui.update {
            it.copy(
                selectedKeyId = keyId,
                selectedServerIndex = server.index,
                selectedServerName = server.name,
            )
        }
        if (isConnectingOrConnected()) {
            switchTo(keyId, server.index, server.name)
        }
    }

    /**
     * Переключение на другой сервер «на лету»: отключаем текущий туннель,
     * дожидаемся полной остановки и поднимаем новый.
     */
    private fun switchTo(keyId: Long, serverIndex: Int, serverName: String?) {
        switchJob?.cancel()
        switchJob = viewModelScope.launch {
            vpnController.disconnect()
            // Ждём, пока сервис действительно остановит туннель, иначе новый
            // CONNECT придёт в ещё живой сервис и наложится на старый.
            withTimeoutOrNull(SWITCH_TIMEOUT_MS) {
                stateHolder.state
                    .filter { it is TunnelState.Disconnected || it is TunnelState.Error }
                    .first()
            }
            vpnController.connect(keyId, serverIndex, serverName)
        }
    }

    /** Снимает показанное уведомление (после отображения snackbar). */
    fun noticeShown() = _ui.update { it.copy(notice = null) }

    /** Активно ли соединение (подключено/подключается). */
    fun isConnectingOrConnected(): Boolean {
        val s = tunnelState.value
        return s is TunnelState.Connected || s is TunnelState.Connecting
    }

    /** Разлогин из меню; onDone — навигация на экран входа. */
    fun logout(onDone: () -> Unit) {
        viewModelScope.launch {
            vpnController.disconnect()
            logoutUseCase()
            onDone()
        }
    }

    fun disconnect() = vpnController.disconnect()

    fun vpnPrepareIntent() = vpnController.prepareIntent()

    fun connect() {
        val keyId = _ui.value.selectedKeyId ?: return
        // Не подключаем недоступный ключ (отключена/истекла/лимит).
        if (keyId in blockedKeyIds()) {
            _ui.update { it.copy(notice = blockedReason(keyId)) }
            return
        }
        vpnController.connect(keyId, _ui.value.selectedServerIndex, _ui.value.selectedServerName)
    }

    fun connectAfterPermission() = connect()

    private companion object {
        /**
         * Максимум одновременных измерений пинга (см. [pingAllKeys]). Держим
         * низким: параллельные TCP-хендшейки конкурируют за сеть и планировщик
         * и дают всплески задержки (тот же метод — то 20–30 мс, то ~400).
         */
        const val PING_CONCURRENCY = 2

        /** Максимум ожидания остановки туннеля при переключении сервера. */
        const val SWITCH_TIMEOUT_MS = 5000L
    }
}
