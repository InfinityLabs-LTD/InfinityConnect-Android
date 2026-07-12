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
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
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
    /** Идёт измерение пинга серверов (кнопка «Пинг всех»). */
    val pinging: Boolean = false,
    /** Активный метод пинга — от него зависит цвет пинг-пилла. */
    val pingMethod: com.infinityconnect.vpn.domain.model.PingMethod =
        com.infinityconnect.vpn.domain.model.PingMethod.TCP,
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
    private val settingsStore: com.infinityconnect.vpn.data.local.SettingsStore,
    private val logoutUseCase: com.infinityconnect.vpn.domain.usecase.LogoutUseCase,
    stateHolder: VpnStateHolder,
) : ViewModel() {

    private val _ui = MutableStateFlow(HomeUiState())
    val ui: StateFlow<HomeUiState> = _ui.asStateFlow()

    private var serversJob: Job? = null
    private var pingJob: Job? = null

    /** Состояние туннеля и статистика — напрямую из holder'а. */
    val tunnelState: StateFlow<TunnelState> = stateHolder.state
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), TunnelState.Disconnected)
    val stats: StateFlow<TunnelStats> = stateHolder.stats
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), TunnelStats())

    // Кэш серверов по ключу на уровне ViewModel — при повторном выборе ключа
    // список показывается мгновенно, без спиннера и без сети.
    private val serversByKey = mutableMapOf<Long, List<SubscriptionServer>>()

    init {
        // Подписка на кэш ключей: обновляем список и авто-выбираем ключ.
        observeKeys()
            .onEach { keys ->
                val prevSelected = _ui.value.selectedKeyId
                val selected = prevSelected
                    ?: keys.firstOrNull { it.isActive }?.id
                    ?: keys.firstOrNull()?.id
                _ui.update { it.copy(keys = keys, selectedKeyId = selected) }
                // Показать серверы авто-выбранного ключа (из кэша, если есть).
                if (prevSelected == null && selected != null) showServers(selected)
            }
            .launchIn(viewModelScope)

        // Метод пинга: обновляем цвет пиллов и перемеряем при смене метода.
        settingsStore.ping
            .onEach { ps ->
                val changed = _ui.value.pingMethod != ps.method
                _ui.update { it.copy(pingMethod = ps.method) }
                if (changed) {
                    // Сбрасываем кэш пингов и меряем заново текущим методом.
                    serversByKey.keys.toList().forEach { keyId ->
                        serversByKey[keyId] = serversByKey[keyId].orEmpty().map { it.copy(pingMs = null) }
                    }
                    val cur = _ui.value.selectedKeyId
                    val servers = _ui.value.servers
                    if (cur != null && servers.isNotEmpty()) {
                        _ui.update { it.copy(servers = servers.map { s -> s.copy(pingMs = null) }) }
                        pingAll(cur, servers)
                    }
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
                    // Предзагружаем серверы всех ключей (форсируем обновление кэша
                    // подписок — это и есть действие кнопки ⟳ / pull-to-refresh).
                    preloadAllServers(result.data.map { it.id }, force = !initial)
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

    fun selectKey(keyId: Long) {
        if (_ui.value.selectedKeyId == keyId) return
        _ui.update {
            it.copy(
                selectedKeyId = keyId,
                selectedServerIndex = 0,
                selectedServerName = null,
                servers = serversByKey[keyId].orEmpty(),
            )
        }
        showServers(keyId)
    }

    /**
     * Показывает серверы ключа: мгновенно из кэша, а сеть трогает только если
     * кэша ещё нет (первый заход). Спиннер — лишь когда реально нечего показать.
     */
    private fun showServers(keyId: Long) {
        serversJob?.cancel()
        val cached = serversByKey[keyId]
        if (cached != null) {
            _ui.update { it.copy(servers = cached, serversLoading = false) }
            // Пинги могли не сохраниться — домеряем, если их нет.
            if (cached.any { it.pingMs == null }) pingAll(keyId, cached)
            return
        }
        _ui.update { it.copy(serversLoading = true) }
        serversJob = viewModelScope.launch {
            when (val result = getServers(keyId)) {
                is AppResult.Success -> {
                    serversByKey[keyId] = result.data
                    if (_ui.value.selectedKeyId == keyId) {
                        _ui.update { it.copy(servers = result.data, serversLoading = false) }
                    }
                    pingAll(keyId, result.data)
                }
                is AppResult.Failure ->
                    _ui.update { it.copy(serversLoading = false) }
            }
        }
    }

    /** Предзагружает (и обновляет) серверы всех ключей в фоне. */
    private fun preloadAllServers(keyIds: List<Long>, force: Boolean) {
        viewModelScope.launch {
            keyIds.forEach { keyId ->
                when (val result = getServers(keyId, forceRefresh = force)) {
                    is AppResult.Success -> {
                        serversByKey[keyId] = result.data
                        // Обновляем видимый список, если это текущий ключ.
                        if (_ui.value.selectedKeyId == keyId) {
                            _ui.update { it.copy(servers = result.data, serversLoading = false) }
                            pingAll(keyId, result.data)
                        }
                    }
                    is AppResult.Failure -> Unit
                }
            }
        }
    }

    /** Кнопка «Пинг всех»: перемеряет пинг серверов текущего ключа. */
    fun pingAllSelected() {
        val keyId = _ui.value.selectedKeyId ?: return
        val servers = _ui.value.servers
        if (servers.isEmpty() || _ui.value.pinging) return
        // Сбрасываем прошлые значения (показываем «…») и меряем заново.
        _ui.update { it.copy(servers = it.servers.map { s -> s.copy(pingMs = null) }) }
        pingAll(keyId, servers)
    }

    /** Пингует серверы и обновляет их в состоянии по мере готовности. */
    private fun pingAll(keyId: Long, servers: List<SubscriptionServer>) {
        pingJob?.cancel()
        _ui.update { it.copy(pinging = true) }
        pingJob = viewModelScope.launch {
            // Ограничиваем число одновременных измерений: параллельные сокеты/DNS
            // конкурируют за сеть и завышают задержку, из-за чего один и тот же
            // метод даёт разброс. Небольшой лимит держит значения стабильными.
            val gate = Semaphore(PING_CONCURRENCY)
            val jobs = servers.map { server ->
                launch {
                    val ping = gate.withPermit { pingServer(server) }
                    // Сохраняем пинг в кэш ключа, чтобы не мерить заново при возврате.
                    serversByKey[keyId] = serversByKey[keyId].orEmpty().map {
                        if (it.index == server.index) it.copy(pingMs = ping) else it
                    }
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
            jobs.forEach { it.join() }
            _ui.update { if (it.selectedKeyId == keyId) it.copy(pinging = false) else it }
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
        vpnController.connect(keyId, _ui.value.selectedServerIndex, _ui.value.selectedServerName)
    }

    fun connectAfterPermission() = connect()

    private companion object {
        /**
         * Максимум одновременных измерений пинга (см. [pingAll]). Держим низким:
         * параллельные TCP-хендшейки конкурируют за сеть и планировщик и дают
         * всплески задержки (тот же метод — то 20–30 мс, то ~400).
         */
        const val PING_CONCURRENCY = 2
    }
}
