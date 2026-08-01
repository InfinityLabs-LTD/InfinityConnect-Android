package com.infinityconnect.vpn.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.infinityconnect.vpn.R
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
import com.infinityconnect.vpn.ui.util.ErrorMessage
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
        com.infinityconnect.vpn.domain.model.PingMethod.PROXY_GET,
    val refreshing: Boolean = false,
    val loadingFirstTime: Boolean = true,
    /** Ошибка как ID строки + аргументы — резолвится на экране (см. errorText). */
    val error: ErrorMessage? = null,
    /** Одноразовое уведомление (snackbar), напр. «лимит устройств». */
    val notice: ErrorMessage? = null,
    /** Доступное обновление клиента (фоновая проверка при входе). */
    val availableUpdate: com.infinityconnect.vpn.domain.model.ClientUpdate? = null,
    /**
     * Обновление, о котором нужно показать диалог. Отдельно от
     * [availableUpdate]: само наличие обновления держим для экрана «О
     * приложении», а диалог показываем один раз и только если пользователь от
     * этой версии не отказался.
     */
    val updatePrompt: com.infinityconnect.vpn.domain.model.ClientUpdate? = null,
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
    private val checkClientUpdate: com.infinityconnect.vpn.domain.usecase.CheckClientUpdateUseCase,
) : ViewModel() {

    private val _ui = MutableStateFlow(HomeUiState())
    val ui: StateFlow<HomeUiState> = _ui.asStateFlow()

    private var pingJob: Job? = null
    private var switchJob: Job? = null

    /** Восстановленный из хранилища выбор — ждёт сверки со списком серверов. */
    private var restoredLast: com.infinityconnect.vpn.data.local.LastServer? = null

    /**
     * Пользователь сам выбрал сервер в этой сессии. Блокирует отложенное
     * восстановление: асинхронное чтение DataStore не должно перебить
     * осознанный выбор, если тот случился раньше.
     */
    private var userPickedServer = false

    /** Состояние туннеля и статистика — напрямую из holder'а. */
    val tunnelState: StateFlow<TunnelState> = stateHolder.state
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), TunnelState.Disconnected)
    val stats: StateFlow<TunnelStats> = stateHolder.stats
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), TunnelStats())

    init {
        // Восстанавливаем последний выбранный сервер ДО подписки на ключи,
        // иначе автовыбор в observeKeys успеет поставить первый активный ключ
        // и запомненный выбор будет затёрт.
        restoreLastServer()

        // Подписка на кэш ключей: обновляем список и авто-выбираем ключ.
        observeKeys()
            .onEach { keys ->
                val selected = _ui.value.selectedKeyId
                    ?.takeIf { id -> keys.any { it.id == id } }
                    ?: keys.firstOrNull {
                        it.status(expired = isExpired(it.expiresAt)) ==
                            KeyStatus.ACTIVE
                    }?.id
                    ?: keys.firstOrNull()?.id
                // Ключ сменился (запомненная подписка пропала) — снимаем выбор
                // сервера, иначе индекс/имя указывали бы на чужую подписку.
                _ui.update { st ->
                    if (selected != st.selectedKeyId) {
                        st.copy(
                            keys = keys,
                            selectedKeyId = selected,
                            selectedServerIndex = 0,
                            selectedServerName = null,
                        )
                    } else {
                        st.copy(keys = keys, selectedKeyId = selected)
                    }
                }
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

        // Останавливаем замер пинга, как только начинается подключение:
        // измерения конкурируют с туннелем за сеть, а после поднятия VPN
        // замеры шли бы уже через туннель и были бы бессмысленны.
        stateHolder.state
            .onEach { state ->
                if (state is TunnelState.Connecting || state is TunnelState.Connected) {
                    cancelPing()
                }
            }
            .launchIn(viewModelScope)

        refresh(initial = true)
        checkUpdateInBackground()
    }

    /**
     * Поднимает последний выбранный сервер из DataStore, чтобы после перезахода
     * кнопка подключения сразу целилась в него.
     *
     * Чтение асинхронное и может завершиться уже после первой эмиссии ключей,
     * поэтому выбор восстанавливается, только если пользователь к этому моменту
     * ничего не выбрал сам (`userPickedServer`) — иначе затёрли бы его действие.
     * Валидность запомненного ключа проверяет [observeKeys]: если такой подписки
     * больше нет, выбор там же сбрасывается на автоматический.
     */
    private fun restoreLastServer() {
        viewModelScope.launch {
            val last = settingsStore.currentLastServer() ?: return@launch
            if (userPickedServer) return@launch
            _ui.update { st ->
                st.copy(
                    selectedKeyId = last.keyId,
                    selectedServerIndex = last.serverIndex,
                    selectedServerName = last.serverName,
                )
            }
            restoredLast = last
        }
    }

    /**
     * Сверяет восстановленный выбор с реально пришедшим списком серверов.
     * Индекс мог «съехать» (подписка перестроилась на панели), поэтому имя
     * приоритетнее: если сервер с таким именем есть — берём его актуальный
     * индекс. Если имени нет, а индекс за пределами списка — откатываемся
     * на первый сервер, чтобы не подключаться в пустоту.
     */
    private fun reconcileRestoredServer(keyId: Long, servers: List<SubscriptionServer>) {
        val last = restoredLast ?: return
        if (last.keyId != keyId || userPickedServer) return
        restoredLast = null
        if (servers.isEmpty()) return
        val byName = last.serverName?.let { name -> servers.firstOrNull { it.name == name } }
        val target = byName
            ?: servers.firstOrNull { it.index == last.serverIndex }
            ?: servers.first()
        _ui.update {
            it.copy(
                selectedServerIndex = target.index,
                selectedServerName = target.name,
            )
        }
    }

    /**
     * Фоновая проверка обновления клиента при входе в приложение — один раз
     * за жизнь процесса. При наличии обновления показывает ДИАЛОГ (раньше был
     * snackbar — его слишком легко пропустить) и помечает состояние; кнопка
     * установки есть и на экране «О приложении».
     *
     * Диалог не показывается, если пользователь уже отказался именно от этой
     * версии («Не напоминать об этой версии»). О следующем релизе он узнает
     * снова — отказ привязан к versionCode, а не «навсегда».
     */
    private fun checkUpdateInBackground() {
        if (updateCheckDone) return
        updateCheckDone = true
        viewModelScope.launch {
            val result = checkClientUpdate()
            if (result is AppResult.Success) {
                result.data?.let { update ->
                    val skipped = settingsStore.skippedUpdateCode.first()
                    _ui.update {
                        it.copy(
                            availableUpdate = update,
                            updatePrompt = update.takeIf { u -> u.versionCode != skipped },
                        )
                    }
                }
            }
            // Ошибки проверки молча игнорируем — это фоновая операция.
        }
    }

    /** Закрывает диалог обновления. [skip] — больше не напоминать об этой версии. */
    fun dismissUpdatePrompt(skip: Boolean) {
        val update = _ui.value.updatePrompt
        _ui.update { it.copy(updatePrompt = null) }
        if (skip && update != null) {
            viewModelScope.launch { settingsStore.skipUpdate(update.versionCode) }
        }
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
                    is AppResult.Success -> {
                        _ui.update { st ->
                            st.copy(
                                serversByKey = st.serversByKey + (keyId to result.data),
                                loadingKeys = st.loadingKeys - keyId,
                            )
                        }
                        // Список пришёл — уточняем восстановленный выбор по имени.
                        reconcileRestoredServer(keyId, result.data)
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
        userPickedServer = true
        restoredLast = null
        _ui.update {
            it.copy(
                selectedKeyId = keyId,
                selectedServerIndex = 0,
                selectedServerName = null,
            )
        }
        // Запоминаем и смену подписки: цель подключения сменилась.
        rememberLastServer(keyId, serverIndex = 0, serverName = null)
    }

    /** Кнопка «Пинг всех» (вверху экрана): перемеряет пинг по ВСЕМ подпискам. */
    fun pingAllSelected() {
        if (_ui.value.pinging) return
        // Через активный туннель замеры бессмысленны (RTT пошёл бы через VPN).
        if (isConnectingOrConnected()) {
            _ui.update { it.copy(notice = ErrorMessage(R.string.home_ping_needs_disconnect)) }
            return
        }
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

    /** Причина недоступности ключа — сообщение для snackbar. */
    private fun blockedReason(keyId: Long): ErrorMessage {
        val key = _ui.value.keys.firstOrNull { it.id == keyId }
            ?: return ErrorMessage(R.string.home_blocked_unavailable)
        val status = key.status(expired = isExpired(key.expiresAt))
        return ErrorMessage(
            when (status) {
                KeyStatus.EXPIRED -> R.string.home_blocked_expired
                KeyStatus.DISABLED -> R.string.home_blocked_disabled
                KeyStatus.LIMITED ->
                    if (key.devicesExhausted) R.string.home_blocked_device_limit
                    else R.string.home_blocked_traffic_limit
                else -> R.string.home_blocked_unavailable
            },
        )
    }

    /** Останавливает текущий замер пинга (перед подключением/переключением). */
    private fun cancelPing() {
        if (pingJob?.isActive == true) {
            pingJob?.cancel()
            _ui.update { it.copy(pinging = false) }
        }
    }

    /**
     * Пингует серверы всех ключей и обновляет их по мере готовности.
     * При активном/поднимающемся туннеле авто-пинг не запускается: замеры
     * пошли бы через VPN и мешали бы самому подключению.
     */
    private fun pingAllKeys() {
        pingJob?.cancel()
        if (isConnectingOrConnected()) {
            _ui.update { it.copy(pinging = false) }
            return
        }
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
        userPickedServer = true
        restoredLast = null
        _ui.update {
            it.copy(
                selectedKeyId = keyId,
                selectedServerIndex = server.index,
                selectedServerName = server.name,
            )
        }
        rememberLastServer(keyId, server.index, server.name)
        if (isConnectingOrConnected()) {
            switchTo(keyId, server.index, server.name)
        }
    }

    /** Персистит выбор, чтобы следующий запуск стартовал с этого же сервера. */
    private fun rememberLastServer(keyId: Long, serverIndex: Int, serverName: String?) {
        viewModelScope.launch {
            settingsStore.saveLastServer(keyId, serverIndex, serverName)
        }
    }

    /**
     * Переключение на другой сервер «на лету»: отключаем текущий туннель,
     * дожидаемся полной остановки и поднимаем новый.
     */
    private fun switchTo(keyId: Long, serverIndex: Int, serverName: String?) {
        cancelPing()
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
        // Прерываем замер пинга: он конкурирует с подключением за сеть.
        cancelPing()
        // Запоминаем цель ИМЕННО при подключении, а не только при выборе из
        // списка: сервер мог быть восстановлен из прошлой сессии и в UI
        // показываться выбранным, хотя в хранилище лежит запись постарше.
        // Тогда плитка в шторке (VpnTileService берёт сервер отсюда) подняла бы
        // не тот сервер, что видит пользователь на экране.
        rememberLastServer(keyId, _ui.value.selectedServerIndex, _ui.value.selectedServerName)
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

        /**
         * Фоновая проверка обновления выполняется один раз за жизнь процесса
         * (не при каждом пересоздании VM после поворота/возврата на экран).
         */
        @Volatile
        var updateCheckDone = false
    }
}
