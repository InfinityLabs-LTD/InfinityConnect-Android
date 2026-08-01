package com.infinityconnect.vpn.vpn

import android.app.PendingIntent
import android.content.Intent
import android.net.ConnectivityManager
import android.net.Network
import android.net.VpnService
import android.os.ParcelFileDescriptor
import com.infinityconnect.vpn.domain.engine.EngineConfig
import com.infinityconnect.vpn.domain.model.AppResult
import com.infinityconnect.vpn.domain.model.AppRoutingMode
import com.infinityconnect.vpn.domain.repository.RoutingRepository
import com.infinityconnect.vpn.domain.usecase.BuildConnectionUseCase
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * VPN-сервис Infinity Connect. Поднимает TUN-интерфейс, выбирает движок по
 * протоколу выбранного сервера и запускает туннель; ведёт foreground-уведомление
 * и обновляет [VpnStateHolder] для UI.
 *
 * Команды через action Intent:
 *  - [ACTION_CONNECT] + extras key_id/server_index → построить профиль и подключить;
 *  - [ACTION_DISCONNECT] → отключить.
 */
@AndroidEntryPoint
class InfinityVpnService : VpnService() {

    @Inject lateinit var buildConnection: BuildConnectionUseCase
    @Inject lateinit var engineSelector: EngineSelector
    @Inject lateinit var stateHolder: VpnStateHolder
    @Inject lateinit var routingRepository: RoutingRepository
    @Inject lateinit var logStore: com.infinityconnect.vpn.data.local.LogStore

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var tunInterface: ParcelFileDescriptor? = null
    private var activeEngine: VpnEngine? = null
    private var statsJob: Job? = null

    /**
     * Туннель полностью поднят (движок стартовал без ошибок). Отделяет разрыв
     * рабочего соединения от неудачи самого запуска: колбэк «ядро остановилось»
     * приходит и в том, и в другом случае, но причину падения на старте
     * сообщает исключение из `engine.start()`, а не этот колбэк.
     * @Volatile — читается из колбэка ядра (нативный поток).
     */
    @Volatile
    private var tunnelEstablished = false

    /**
     * startId последней принятой команды. Передаётся в [stopSelfResult], чтобы
     * не убить сервис, в который уже пришла более свежая команда.
     *
     * Без этого быстрый цикл «отключить → подключить» (переключение сервера
     * за доли секунды) ронял приложение: stopTunnel() звал stopSelf(), система
     * начинала сносить сервис, а прилетевший следом startForegroundService()
     * попадал в тот же, уже помеченный на уничтожение ServiceRecord. Промоушен
     * в нём не засчитывался, и через ~5 секунд прилетал
     * ForegroundServiceDidNotStartInTimeException — причём в UI-процесс,
     * который и вызывал startForegroundService().
     */
    @Volatile
    private var lastStartId = 0


    /**
     * Колбэк смены нижележащей сети. При Wi-Fi ↔ мобильный система меняет
     * default network — мы сообщаем её туннелю через [setUnderlyingNetworks],
     * чтобы TUN не «завис» на исчезнувшей сети и учёт трафика/энергосбережение
     * оставались корректными. Сокеты ядра (через protect) переустановятся на
     * новую сеть автоматически при следующем запросе.
     */
    private var networkCallback: ConnectivityManager.NetworkCallback? = null
    private var sessionStartMs: Long = 0

    /**
     * TUN намеренно оставлен поднятым после аварии, движка за ним нет
     * (см. [KILL_SWITCH_ENABLED]). Нужен, чтобы отличать «висящий» интерфейс от
     * рабочего: пока флаг стоит, туннель трафик не передаёт, а только поглощает.
     * @Volatile — выставляется из колбэков ядра/сети (чужие потоки).
     */
    @Volatile
    private var tunHeldByKillSwitch = false

    /**
     * Сторож затянувшегося подключения ([CONNECT_TIMEOUT_MS]) и сторож
     * длительной потери сети ([NETWORK_LOSS_TIMEOUT_MS]).
     *
     * Оба взводятся и снимаются из разных потоков (колбэк ConnectivityManager
     * приходит на своём), поэтому доступ к ним синхронизирован по [timerLock]:
     * без этого гонка «onLost взвёл / onAvailable отменил» могла оставить
     * висящий job, который через полминуты убьёт живой туннель.
     */
    private var connectWatchdogJob: Job? = null
    private var networkLossJob: Job? = null
    private val timerLock = Any()

    override fun onCreate() {
        super.onCreate()
        // Сервис живёт в своём процессе (:vpn), поэтому его VpnStateHolder — не
        // тот же объект, что читает UI. Транслируем каждое изменение броадкастом.
        stateHolder.onPublish = { state, stats, serverName, connection ->
            runCatching {
                VpnStateBridge.publish(this, state, stats, serverName, connection)
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        lastStartId = startId
        // Сервис всегда стартует через startForegroundService(), поэтому промоушен
        // обязан произойти на ЛЮБОМ пути — включая disconnect и неизвестную
        // команду. Иначе система убьёт процесс за то, что мы не промоутились
        // в отведённые ~5 секунд (ForegroundServiceDidNotStartInTimeException).
        when (intent?.action) {
            ACTION_CONNECT -> {
                val keyId = intent.getLongExtra(EXTRA_KEY_ID, -1)
                val serverIndex = intent.getIntExtra(EXTRA_SERVER_INDEX, 0)
                val serverName = intent.getStringExtra(EXTRA_SERVER_NAME)
                if (keyId <= 0) {
                    promoteToForeground("Infinity Connect", "Остановка")
                    stopWithError("Некорректный ключ")
                    return START_NOT_STICKY
                }
                startTunnel(keyId, serverIndex, serverName)
            }
            else -> {
                // DISCONNECT и всё прочее: промоутимся, чтобы легально
                // завершиться, и сразу гасим туннель.
                promoteToForeground("Infinity Connect", "Отключение")
                stopTunnel()
            }
        }
        // NOT_STICKY: пересоздавать сервис без команды бессмысленно — туннель
        // всё равно поднимается только по явному ACTION_CONNECT с параметрами.
        return START_NOT_STICKY
    }

    /** Строит профиль и поднимает туннель. */
    private fun startTunnel(keyId: Long, serverIndex: Int, serverName: String?) {
        stateHolder.updateState(TunnelState.Connecting)
        stateHolder.setActiveServer(serverName)
        // Запоминаем параметры активного подключения — SettingsViewModel по ним
        // переподключает туннель при изменении настроек маршрутизации.
        stateHolder.setActiveConnection(
            VpnStateHolder.ActiveConnection(keyId, serverIndex, serverName),
        )
        if (!promoteToForeground(serverName ?: "Подключение…", "Устанавливаем соединение")) {
            // Система не дала уйти в foreground (фон + Android 12+, либо запрет
            // вендорской прошивки). Поднимать туннель нельзя: процесс всё равно
            // будет убит. Сообщаем UI понятной ошибкой вместо тихого краша.
            stopWithError("Система запретила запуск VPN в фоне. Откройте приложение и повторите.")
            return
        }

        logStore.i(TAG, "Подключение: keyId=$keyId, сервер=${serverName ?: "#$serverIndex"}")

        // Сторож зависшего подключения: взводим ДО ухода в корутину, чтобы под
        // таймаут попали и «залипшие» сетевые шаги (загрузка подписки, DNS),
        // а не только запуск ядра. Снимется при успехе, ошибке или stopTunnel.
        startConnectWatchdog()

        scope.launch {
            // Объявлен снаружи try: обработчику ошибок нужен протокол, чтобы
            // назвать в сообщении именно то ядро, которое не запустилось.
            var startedConfig: EngineConfig? = null
            try {
                val config = when (val r = buildConnection(keyId, serverIndex)) {
                    is AppResult.Success -> r.data
                    is AppResult.Failure -> {
                        stopWithError("Не удалось получить конфигурацию сервера")
                        return@launch
                    }
                }
                startedConfig = config

                val engine = engineSelector.select(config)
                logStore.i(
                    TAG,
                    "Профиль готов: ${config.remark}, движок=${engine.javaClass.simpleName}",
                )

                // Смена ядра в рамках процесса невозможна (два Go-рантайма не
                // сосуществуют — см. манифест). Перезапускаем процесс сервиса:
                // система поднимет его заново по этой же команде, и ядро
                // стартует в чистом рантайме.
                if (restartIfEngineChanged(engine, keyId, serverIndex, serverName)) return@launch

                // CONNECT поверх живого туннеля (переключение сервера в рамках
                // одного ядра) — гасим предыдущий, иначе его движок продолжит
                // читать свой TUN, а дескриптор утечёт: establishTun() ниже
                // перезапишет tunInterface, и закрыть старый будет уже некому.
                //
                // Этот же вызов освобождает «висящий» после kill-switch TUN:
                // keepTun по умолчанию false, поэтому переподключение после
                // аварии корректно закрывает старый интерфейс перед новым.
                //
                // keepWatchdog: сторож взведён в startTunnel и должен пережить
                // эту зачистку — впереди establishTun и engine.start, самые
                // долгие шаги, ради которых он и заводился.
                releaseActiveTunnel(keepWatchdog = true)

                val tun = establishTun(config) ?: run {
                    stopWithError("Не удалось создать TUN-интерфейс")
                    return@launch
                }
                tunInterface = tun
                logStore.i(TAG, "TUN поднят (fd=${tun.fd}, mtu=$MTU)")

                // Следим за нижележащей сетью — туннель переживает Wi-Fi ↔ мобильный.
                registerNetworkCallback()

                // Если ядро само остановится (разрыв) — отражаем это в UI.
                // Реагируем только на разрыв УЖЕ установленного туннеля: во время
                // start() ядро может дёрнуть shutdown по ошибке запуска, и её
                // разбирает catch ниже — иначе показали бы «Соединение разорвано»
                // вместо настоящей причины.
                // Обрыв рабочего туннеля — это авария, а не пользовательский
                // disconnect, поэтому идём через kill-switch: движок гасим, но
                // TUN оставляем поднятым, чтобы трафик не хлынул мимо VPN.
                val onCoreStopped = { if (tunnelEstablished) failWithKillSwitch("Соединение разорвано") }
                when (engine) {
                    is com.infinityconnect.vpn.vpn.xray.XrayEngine -> engine.onCoreStopped = onCoreStopped
                    is com.infinityconnect.vpn.vpn.hysteria2.Hysteria2Engine -> engine.onCoreStopped = onCoreStopped
                }

                // Запуск движка (блокирующая инициализация).
                // service = this — ядру нужен VpnService для protect() сокетов.
                // activeEngine выставляем ДО start(): если start() бросит на
                // полпути (часть ресурсов ядра уже поднята), обработчик ошибки
                // обязан вызвать engine.stop() — иначе они утекут до перезапуска.
                activeEngine = engine
                // Ядру передаётся ОРИГИНАЛЬНЫЙ дескриптор, владельцем остаётся
                // tunInterface (ParcelFileDescriptor). Передавать сюда дубликат
                // нельзя: libv2ray читает TUN именно через тот fd, что связан с
                // интерфейсом VpnService, и на копии трафик в обратную сторону
                // не идёт (пакеты уходят, ответы не приходят). Двойного закрытия
                // при этом нет — см. engine.stop() строго перед
                // tunInterface.close() в stopTunnel(), а Go-обёртка Hysteria2
                // дублирует fd у себя (dupFD в hysteria2.go).
                engine.start(this@InfinityVpnService, config, tunFd = tun.fd, mtu = MTU)

                tunnelEstablished = true
                // Туннель поднят — сторож зависшего подключения больше не нужен
                // (иначе через 45 с он бы разобрал живое соединение).
                cancelConnectWatchdog()
                sessionStartMs = System.currentTimeMillis()
                stateHolder.updateState(TunnelState.Connected)
                updateNotification(config.remark, "Подключено")
                logStore.i(TAG, "Туннель установлен: ${config.remark}")
                startStatsLoop(engine)
            } catch (e: Throwable) {
                // Throwable, а не Exception: отсутствие нативного AAR даёт
                // NoClassDefFoundError (Error) — его тоже показываем в UI.
                logStore.e(TAG, "Ошибка запуска туннеля", e)
                val msg = when (e) {
                    is NoClassDefFoundError, is UnsatisfiedLinkError -> {
                        // Какого именно ядра нет — видно только по конфигу:
                        // сообщение про Xray при падении Hysteria2 дезориентирует.
                        val core = if (startedConfig is EngineConfig.Hysteria2) {
                            "Hysteria2 (libhysteria2)"
                        } else {
                            "Xray (libv2ray)"
                        }
                        "Нативный движок $core не подключён. См. README."
                    }
                    else -> e.message ?: "Ошибка подключения"
                }
                stopWithError(msg)
            }
        }
    }

    /**
     * Гасит активный туннель: останавливает движок и закрывает TUN.
     *
     * Порядок обязателен: сначала движок, потом TUN. Ядро держит собственную
     * (dup) копию TUN-дескриптора и закрывает её в stop(); закрыть свой fd
     * раньше — значит оставить ядро читать уже закрытый интерфейс, а двойное
     * закрытие ловит fdsan (Android 12+) и роняет процесс.
     *
     * @param keepTun не закрывать TUN (режим kill-switch, см.
     *   [KILL_SWITCH_ENABLED]). Движок останавливается в любом случае, так что
     *   порядок «сначала движок, потом TUN» соблюдается и здесь: закрытие лишь
     *   откладывается до явного disconnect/onDestroy/следующего CONNECT.
     *   Интерфейс остаётся поднятым и поглощает трафик — маршруты на месте,
     *   читателя нет, наружу мимо VPN ничего не уходит.
     */
    /**
     * @param keepWatchdog не снимать сторож подключения. Нужен ровно одному
     *   вызову — тому, что гасит предыдущий туннель ВНУТРИ нового подключения
     *   (переключение сервера). Там сторож обязан пережить зачистку: самая
     *   долгая часть (establishTun + engine.start) идёт следом, и снятый здесь
     *   таймаут оставил бы зависшее подключение навсегда в Connecting — то
     *   есть ровно то, от чего сторож и защищает.
     */
    private fun releaseActiveTunnel(keepTun: Boolean = false, keepWatchdog: Boolean = false) {
        tunnelEstablished = false
        if (!keepWatchdog) cancelConnectWatchdog()
        cancelNetworkLossTimer()
        statsJob?.cancel()
        statsJob = null
        unregisterNetworkCallback()
        runCatching { activeEngine?.stop() }
        activeEngine = null
        if (keepTun) {
            // Дескриптор осознанно остаётся жить в tunInterface: его закроет
            // следующий releaseActiveTunnel() без keepTun (disconnect, новый
            // CONNECT) либо onDestroy. Помечаем флагом, чтобы UI/логи отличали
            // «висящий» TUN от полностью снятого.
            tunHeldByKillSwitch = true
            logStore.w(
                TAG,
                "Kill-switch: движок остановлен, TUN оставлен поднятым — " +
                    "трафик не пойдёт мимо VPN до явного отключения",
            )
            return
        }
        runCatching { tunInterface?.close() }
        tunInterface = null
        tunHeldByKillSwitch = false
    }

    /**
     * Обрабатывает НЕОЖИДАННЫЙ обрыв: падение ядра или длительную потерю сети.
     *
     * В отличие от [stopWithError] (который снимает туннель целиком) здесь при
     * включённом kill-switch TUN остаётся поднятым: пользователь видит ошибку,
     * но трафик не начинает идти напрямую в обход VPN. Закроет интерфейс уже
     * явный disconnect — там [stopTunnel] зовёт releaseActiveTunnel() без
     * keepTun, — либо следующий CONNECT, либо onDestroy.
     *
     * Активное подключение НЕ сбрасываем в отличие от stopWithError: параметры
     * сервера нужны, чтобы пользователь мог переподключиться одной кнопкой.
     */
    private fun failWithKillSwitch(message: String) {
        logStore.e(TAG, "Аварийный обрыв: $message (kill-switch=$KILL_SWITCH_ENABLED)")
        if (!KILL_SWITCH_ENABLED) {
            stopWithError(message)
            return
        }
        releaseActiveTunnel(keepTun = true)
        stateHolder.updateState(TunnelState.Error(message))
        // Уведомление оставляем и НЕ снимаем foreground: процесс обязан жить,
        // пока держит открытый TUN, иначе система прибьёт его вместе с
        // интерфейсом — и kill-switch перестанет что-либо защищать. По той же
        // причине здесь нет stopSelf().
        updateNotification("Infinity Connect", message)
    }

    /**
     * Взводит сторож затянувшегося подключения. Если через [CONNECT_TIMEOUT_MS]
     * туннель так и не поднялся, пользователь остался бы в Connecting навсегда —
     * сообщаем об этом явно и снимаем попытку.
     *
     * Предыдущий сторож отменяется, чтобы быстрые переподключения не копили
     * висящие job'ы (утечка + чужой таймаут, убивающий уже другой туннель).
     */
    private fun startConnectWatchdog() {
        synchronized(timerLock) {
            connectWatchdogJob?.cancel()
            connectWatchdogJob = scope.launch {
                delay(CONNECT_TIMEOUT_MS)
                // Проверяем оба условия: состояние могло уйти в Error своим
                // путём, а tunnelEstablished — единственный надёжный признак
                // реально поднятого туннеля.
                if (!tunnelEstablished && stateHolder.state.value == TunnelState.Connecting) {
                    logStore.e(TAG, "Подключение не завершилось за ${CONNECT_TIMEOUT_MS / 1000} с")
                    stopWithError(
                        "Не удалось подключиться за ${CONNECT_TIMEOUT_MS / 1000} секунд. " +
                            "Проверьте интернет или выберите другой сервер.",
                    )
                }
            }
        }
    }

    private fun cancelConnectWatchdog() {
        synchronized(timerLock) {
            connectWatchdogJob?.cancel()
            connectWatchdogJob = null
        }
    }

    /**
     * Взводит таймер длительной потери сети. Короткие провалы лечит хендовер
     * (onAvailable отменит таймер), а вот [NETWORK_LOSS_TIMEOUT_MS] без единой
     * сети — это уже мёртвый туннель, и держать «Подключено» нечестно.
     */
    private fun startNetworkLossTimer() {
        synchronized(timerLock) {
            if (networkLossJob?.isActive == true) return // уже тикает с прошлого onLost
            networkLossJob = scope.launch {
                delay(NETWORK_LOSS_TIMEOUT_MS)
                if (tunnelEstablished) {
                    failWithKillSwitch("Нет сети")
                }
            }
        }
    }

    private fun cancelNetworkLossTimer() {
        synchronized(timerLock) {
            networkLossJob?.cancel()
            networkLossJob = null
        }
    }

    /**
     * Если в этом процессе уже отработало ДРУГОЕ ядро — перезапускает процесс
     * сервиса и возвращает true (вызывающий обязан прервать подключение).
     *
     * Xray (libv2ray) и Hysteria2 (libhysteria2) — независимые Go-рантаймы;
     * второй за жизнь процесса падает на первом же cgo-переходе, унося приложение
     * (см. комментарий к `android:process=":vpn"` в манифесте). Переиспользовать
     * процесс можно только под то же ядро, поэтому при смене движка глушим
     * туннель, отправляем себе отложенную команду CONNECT и завершаем процесс:
     * система поднимет сервис заново, уже с чистым рантаймом.
     */
    private fun restartIfEngineChanged(
        engine: VpnEngine,
        keyId: Long,
        serverIndex: Int,
        serverName: String?,
    ): Boolean {
        val previous = loadedEngineClass
        if (previous == null || previous == engine.javaClass) {
            loadedEngineClass = engine.javaClass
            return false
        }

        logStore.i(
            TAG,
            "Смена ядра ${previous.simpleName} → ${engine.javaClass.simpleName}: " +
                "перезапускаем процесс сервиса",
        )
        // Туннель и предыдущее ядро гасим штатно — иначе TUN останется висеть,
        // а ядро не успеет закрыть свою копию дескриптора.
        releaseActiveTunnel()

        // Состояние оставляем Connecting: для пользователя это одно непрерывное
        // подключение, а сервис вот-вот стартует заново и доведёт его до конца.
        val restart = Intent(this, InfinityVpnService::class.java).apply {
            action = ACTION_CONNECT
            putExtra(EXTRA_KEY_ID, keyId)
            putExtra(EXTRA_SERVER_INDEX, serverIndex)
            putExtra(EXTRA_SERVER_NAME, serverName)
        }
        val pending = PendingIntent.getForegroundService(
            this,
            REQUEST_RESTART,
            restart,
            PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_UPDATE_CURRENT or
                PendingIntent.FLAG_IMMUTABLE,
        )
        // Небольшая задержка: команда должна прийти уже после смерти процесса.
        runCatching {
            getSystemService(android.app.AlarmManager::class.java)?.set(
                android.app.AlarmManager.RTC_WAKEUP,
                System.currentTimeMillis() + RESTART_DELAY_MS,
                pending,
            )
        }.onFailure { logStore.e(TAG, "Не удалось запланировать перезапуск сервиса", it) }

        logStore.flushBlocking()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
        // Только :vpn-процесс — UI в основном процессе не затрагивается.
        android.os.Process.killProcess(android.os.Process.myPid())
        return true
    }

    /**
     * Создаёт TUN-интерфейс. Применяет split-tunnel по приложениям
     * ([AppRoutingMode]) на уровне VpnService.Builder — это работает для всех
     * движков (Xray/Hysteria2), в отличие от доменных правил (только Xray).
     */
    private fun establishTun(config: EngineConfig): ParcelFileDescriptor? {
        val routing = runCatching {
            kotlinx.coroutines.runBlocking { routingRepository.current() }
        }.getOrNull()

        val builder = Builder()
            .setSession(config.remark)
            .setMtu(MTU)
            .addAddress(TUN_ADDRESS, TUN_PREFIX)
            .addRoute("0.0.0.0", 0)        // весь IPv4-трафик в туннель
            .also { b ->
                // Системному стеку sing-tun (Hysteria2) нужны ОБА адреса
                // префикса: на первом (10.10.0.1) он слушает TCP-форвардер, а
                // вторым (10.10.0.2) подписывает пакеты, которые к этому
                // форвардеру идут, — и на него же приходят ответы. Если второго
                // адреса нет на интерфейсе, ответный трафик отбрасывается ядром:
                // соединения устанавливаются, наружу данные уходят, а обратно в
                // приложения не возвращаются («сайты не открываются»).
                // Xray этот адрес не использует, лишним он ему не мешает.
                runCatching { b.addAddress(TUN_ADDRESS_PEER, TUN_PEER_PREFIX) }
            }
            .also { b ->
                // Закрываем IPv6-утечку: без адреса и маршрута ::/0 весь
                // IPv6-трафик на двустековой сети идёт МИМО туннеля с реальным
                // IP пользователя (см. TUN_ADDRESS_V6). Адрес и маршрут ставим
                // только вместе: маршрут без адреса на интерфейсе система
                // отвергает, а адрес без маршрута ничего не заворачивает.
                //
                // runCatching обязателен: часть вендорских прошивок (и старые
                // устройства с урезанным IPv6-стеком) бросают на addAddress/
                // addRoute для IPv6. Терять из-за этого весь туннель нельзя —
                // откатываемся на IPv4-only, но громко пишем в журнал, потому
                // что в этом режиме IPv6 действительно течёт и при разборе
                // жалоб на «видно мой IP» это первое, что надо знать.
                runCatching {
                    b.addAddress(TUN_ADDRESS_V6, TUN_PREFIX_V6)
                    b.addRoute("::", 0)
                }.onFailure {
                    logStore.w(
                        TAG,
                        "IPv6 на TUN не поднялся (${it.message}) — туннель работает " +
                            "в режиме IPv4-only, IPv6-трафик пойдёт мимо VPN",
                    )
                }
            }
            .addDnsServer(DNS_PRIMARY)
            .addDnsServer(DNS_SECONDARY)

        applyPerAppRouting(builder, routing?.appMode ?: AppRoutingMode.OFF, routing?.apps ?: emptySet())
        return runCatching { builder.establish() }.getOrNull()
    }

    /**
     * Настраивает фильтрацию по приложениям на билдере TUN.
     *  - OFF: весь трафик в VPN, но собственный пакет исключаем (петля);
     *  - ALLOW: через VPN только выбранные (собственный пакет НЕ добавляем, иначе
     *    завернём сами себя — он и так не в списке);
     *  - DISALLOW: через VPN всё, кроме выбранных + собственный пакет.
     * Несуществующие пакеты (удалённые) молча пропускаем — иначе establish() кинет
     * PackageManager.NameNotFoundException и туннель не поднимется.
     */
    private fun applyPerAppRouting(builder: Builder, mode: AppRoutingMode, apps: Set<String>) {
        when (mode) {
            AppRoutingMode.OFF -> {
                runCatching { builder.addDisallowedApplication(packageName) }
            }
            AppRoutingMode.ALLOW -> {
                var added = 0
                apps.forEach { pkg ->
                    if (pkg == packageName) return@forEach // себя в VPN не заворачиваем
                    if (runCatching { builder.addAllowedApplication(pkg) }.isSuccess) added++
                }
                // Пустой allow-список означал бы «ничего в VPN» — бессмысленно.
                // Тогда откатываемся к поведению OFF (весь трафик, кроме себя).
                if (added == 0) runCatching { builder.addDisallowedApplication(packageName) }
            }
            AppRoutingMode.DISALLOW -> {
                runCatching { builder.addDisallowedApplication(packageName) }
                apps.forEach { pkg ->
                    if (pkg == packageName) return@forEach
                    runCatching { builder.addDisallowedApplication(pkg) }
                }
            }
        }
    }

    /**
     * Регистрирует слежение за default-сетью и прокидывает её как underlying для
     * туннеля. При Wi-Fi ↔ мобильный система переносит default network — TUN
     * должен «переехать» на неё, иначе трафик зависнет на исчезнувшей сети.
     * `setUnderlyingNetworks(null)` означало бы «следовать за системным default»,
     * но явная привязка к текущей сети надёжнее для учёта трафика и хендовера.
     */
    private fun registerNetworkCallback() {
        if (networkCallback != null) return
        val cm = getSystemService(ConnectivityManager::class.java) ?: return
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                // default-сеть сменилась (Wi-Fi ↔ мобильный) — отдаём новую туннелю.
                runCatching { setUnderlyingNetworks(arrayOf(network)) }
                // Связь вернулась раньше таймаута — снимаем сторож потери сети,
                // иначе он бы позже уронил уже восстановленный туннель.
                cancelNetworkLossTimer()
                logStore.i(TAG, "Сеть переключена на $network")
            }

            override fun onLost(network: Network) {
                // Текущая сеть пропала. Не сбрасываем underlying в null — ждём
                // onAvailable следующей default-сети; система придержит пакеты
                // до переключения, и короткий провал пользователь не заметит.
                //
                // Но «ждём» не может быть вечным: если сети нет дольше
                // NETWORK_LOSS_TIMEOUT_MS, туннель мёртв, а статус всё ещё
                // «Подключено». Взводим таймер — он переведёт состояние в
                // ошибку (с сохранением TUN, если включён kill-switch).
                logStore.w(TAG, "Сеть потеряна: $network")
                startNetworkLossTimer()
            }
        }
        runCatching {
            // registerDefaultNetworkCallback отслеживает СМЕНУ активной сети и не
            // требует CHANGE_NETWORK_STATE (в отличие от requestNetwork) — только
            // ACCESS_NETWORK_STATE, который у нас уже есть.
            cm.registerDefaultNetworkCallback(callback)
            networkCallback = callback
            // Привязываем активную сеть сразу, не дожидаясь первого колбэка.
            cm.activeNetwork?.let { runCatching { setUnderlyingNetworks(arrayOf(it)) } }
        }.onFailure { logStore.w(TAG, "Не удалось зарегистрировать NetworkCallback: ${it.message}") }
    }

    private fun unregisterNetworkCallback() {
        val cb = networkCallback ?: return
        networkCallback = null
        runCatching {
            getSystemService(ConnectivityManager::class.java)?.unregisterNetworkCallback(cb)
        }
    }

    /** Периодически опрашивает статистику движка и обновляет состояние/уведомление. */
    private fun startStatsLoop(engine: VpnEngine) {
        statsJob?.cancel()
        // Скорость = дельта суммарного трафика. Считаем её от ПОСЛЕДНЕГО замера,
        // где счётчик реально изменился, а не от предыдущего тика: ядра
        // обновляют свои счётчики реже, чем раз в секунду, и деление нулевой
        // дельты на 1 с давало «0 Б/с» при живом трафике. Прошлые значения
        // держим до следующего изменения — тогда дельта делится на реальный
        // интервал между изменениями и скорость выходит правдивой.
        var prevUp = 0L
        var prevDown = 0L
        var prevMs = System.currentTimeMillis()
        var lastUpSpeed = 0L
        var lastDownSpeed = 0L
        statsJob = scope.launch {
            while (isActive) {
                val now = System.currentTimeMillis()
                val elapsed = (now - sessionStartMs) / 1000
                val raw = engine.queryStats()
                val totalUp = raw?.totalUploadBytes ?: 0
                val totalDown = raw?.totalDownloadBytes ?: 0

                val changed = totalUp != prevUp || totalDown != prevDown
                if (changed) {
                    val dtSec = ((now - prevMs) / 1000.0).coerceAtLeast(0.001)
                    lastUpSpeed = ((totalUp - prevUp).coerceAtLeast(0) / dtSec).toLong()
                    lastDownSpeed = ((totalDown - prevDown).coerceAtLeast(0) / dtSec).toLong()
                    prevUp = totalUp
                    prevDown = totalDown
                    prevMs = now
                } else if (now - prevMs > SPEED_IDLE_RESET_MS) {
                    // Трафика давно нет — показываем ноль, а не последнюю скорость.
                    lastUpSpeed = 0
                    lastDownSpeed = 0
                }
                val upSpeed = lastUpSpeed
                val downSpeed = lastDownSpeed

                stateHolder.updateStats(
                    TunnelStats(
                        uploadBytesPerSec = upSpeed,
                        downloadBytesPerSec = downSpeed,
                        totalUploadBytes = totalUp,
                        totalDownloadBytes = totalDown,
                        sessionSeconds = elapsed,
                    ),
                )
                delay(STATS_INTERVAL_MS)
            }
        }
    }

    private fun stopTunnel() {
        logStore.i(TAG, "Отключение по команде пользователя")
        stateHolder.updateState(TunnelState.Disconnecting)
        releaseActiveTunnel()
        stateHolder.reset()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopIfNoNewerCommand()
    }

    private fun stopWithError(message: String) {
        logStore.e(TAG, "Остановка с ошибкой: $message")
        releaseActiveTunnel()
        stateHolder.setActiveConnection(null)
        stateHolder.updateState(TunnelState.Error(message))
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopIfNoNewerCommand()
    }

    /**
     * Останавливает сервис, только если за время работы не пришла более свежая
     * команда. [stopSelfResult] возвращает false, когда startId устарел —
     * значит уже прилетел новый CONNECT, и убивать сервис нельзя: система
     * начала бы уничтожение ServiceRecord, в котором новая команда должна
     * промоутиться в foreground (см. [lastStartId]).
     */
    private fun stopIfNoNewerCommand() {
        val stopped = runCatching { stopSelfResult(lastStartId) }.getOrDefault(true)
        if (!stopped) {
            logStore.i(TAG, "Остановка отменена: пришла новая команда")
        }
    }

    /**
     * Переводит сервис в foreground. Возвращает false, если система отказала.
     *
     * Вызывать ОБЯЗАТЕЛЬНО на каждом пути обработки команды: сервис стартует
     * через `startForegroundService()`, и если не промоутиться за ~5 секунд,
     * система убивает процесс с ForegroundServiceDidNotStartInTimeException.
     *
     * Сам вызов тоже может бросить (ForegroundServiceStartNotAllowedException
     * на Android 12+, если приложение уже в фоне; вендорские прошивки вроде
     * ColorOS отказывают охотнее стокового Android), поэтому исключения ловим:
     * упасть здесь — значит убить приложение на глазах пользователя.
     */
    private fun promoteToForeground(title: String, text: String): Boolean {
        return runCatching {
            VpnNotifications.ensureChannel(this)
            val notification = VpnNotifications.build(
                context = this,
                title = title,
                text = text,
                disconnectIntent = disconnectPendingIntent(),
            )
            // На Android 10+ тип FGS указывается явно и обязан совпадать с
            // манифестом. Тип "vpn" (API 36) здесь недоступен: проект собирается
            // под compileSdk 35, где нет ни атрибута, ни константы, — поэтому
            // и манифест, и рантайм используют SPECIAL_USE.
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                startForeground(
                    VpnNotifications.NOTIFICATION_ID,
                    notification,
                    android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
                )
            } else {
                startForeground(VpnNotifications.NOTIFICATION_ID, notification)
            }
            true
        }.getOrElse { e ->
            logStore.e(TAG, "startForeground отклонён системой", e)
            false
        }
    }

    /**
     * Обновляет текст постоянного уведомления. Косметика: notify() может
     * бросить при заблокированном канале или отсутствующем разрешении
     * (частая история на вендорских прошивках), и ронять из-за этого
     * работающий туннель недопустимо.
     */
    private fun updateNotification(title: String, text: String) {
        runCatching {
            val notification = VpnNotifications.build(
                context = this,
                title = title,
                text = text,
                disconnectIntent = disconnectPendingIntent(),
            )
            VpnNotifications.ensureChannel(this)
            getSystemService(android.app.NotificationManager::class.java)
                ?.notify(VpnNotifications.NOTIFICATION_ID, notification)
        }.onFailure { logStore.w(TAG, "Не удалось обновить уведомление: ${it.message}") }
    }

    private fun disconnectPendingIntent(): PendingIntent {
        val intent = Intent(this, InfinityVpnService::class.java).apply {
            action = ACTION_DISCONNECT
        }
        return PendingIntent.getService(
            this,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }

    /**
     * Система отозвала VPN-разрешение: пользователь включил другой VPN
     * (одновременно работает только один) или отключил наш в системных
     * настройках. Наш TUN при этом уже недействителен.
     *
     * Без этого обработчика сервис ничего не замечал: движок продолжал крутиться
     * на мёртвом дескрипторе, TUN не закрывался, а UI бодро показывал
     * «Подключено» — пользователь считал себя под VPN, не будучи под ним.
     *
     * Состояние выставляем Error, а не reset: reset выглядел бы как штатное
     * отключение по кнопке, и пользователь не понял бы, почему туннель вдруг
     * пропал. Явный текст объясняет причину и подсказывает, что делать.
     *
     * Kill-switch здесь НЕ применяем: разрешение отозвано, интерфейс уже не наш,
     * держать его бессмысленно (и невозможно) — снимаем туннель полностью.
     *
     * Вызывается системой на отдельном потоке (не на main), поэтому вся работа
     * идёт через потокобезопасные пути: releaseActiveTunnel синхронизирует свои
     * таймеры, stateHolder — на StateFlow, stopForeground/stopSelf безопасны.
     */
    override fun onRevoke() {
        logStore.w(TAG, "VPN-разрешение отозвано системой (другой VPN или отключение в настройках)")
        releaseActiveTunnel()
        stateHolder.setActiveConnection(null)
        stateHolder.updateState(
            TunnelState.Error("VPN отключён системой (возможно, включён другой VPN)"),
        )
        stopForeground(STOP_FOREGROUND_REMOVE)
        // Здесь именно stopSelf(), а не stopIfNoNewerCommand(): отзыв разрешения
        // не связан с очередью команд, и ждать «более свежей команды» нечего.
        stopSelf()
        super.onRevoke()
    }

    override fun onDestroy() {
        cancelConnectWatchdog()
        cancelNetworkLossTimer()
        scope.cancel()
        unregisterNetworkCallback()
        runCatching { activeEngine?.stop() }
        // Закрываем TUN в том числе «висящий» после kill-switch: процесс
        // умирает, удерживать интерфейс больше нечем и незачем. Инвариант
        // порядка соблюдён — engine.stop() выше.
        runCatching { tunInterface?.close() }
        tunInterface = null
        tunHeldByKillSwitch = false
        super.onDestroy()
    }

    companion object {
        const val ACTION_CONNECT = "com.infinityconnect.vpn.CONNECT"
        const val ACTION_DISCONNECT = "com.infinityconnect.vpn.DISCONNECT"
        const val EXTRA_KEY_ID = "key_id"
        const val EXTRA_SERVER_INDEX = "server_index"
        const val EXTRA_SERVER_NAME = "server_name"

        private const val TAG = "InfinityVpnService"
        private const val MTU = 1500

        /**
         * Класс ядра, ЗАГРУЖЕННОГО в текущий процесс. Статическое поле, а не
         * поле сервиса: сервис пересоздаётся (stopSelf → новый onCreate) внутри
         * того же процесса, а нативная библиотека остаётся загруженной навсегда.
         *
         * Xray (libv2ray) и Hysteria2 (libhysteria2) — независимые Go-рантаймы,
         * и второй за жизнь процесса роняет его на первом же cgo-переходе
         * (подробно — в комментарии к `android:process=":vpn"` в манифесте).
         * Повторный запуск ТОГО ЖЕ ядра безопасен, поэтому храним именно класс.
         */
        @Volatile
        private var loadedEngineClass: Class<out VpnEngine>? = null

        /**
         * Адрес TUN-интерфейса. Обязан совпадать с ПЕРВЫМ адресом префикса,
         * который получает системный стек sing-tun в Hysteria2
         * ([com.infinityconnect.vpn.vpn.hysteria2.Hysteria2Engine.TUN_CIDR]):
         * стек биндит свой TCP-форвардер именно на него, и если адреса нет на
         * интерфейсе, старт падает с "bind: cannot assign requested address".
         * Xray адрес TUN не использует, так что значение общее для обоих движков.
         */
        private const val TUN_ADDRESS = "10.10.0.1"
        private const val TUN_PREFIX = 30

        /**
         * Второй адрес того же префикса. Системный стек sing-tun подписывает им
         * пакеты, идущие на свой TCP-форвардер (`inet4Address` в его коде), и
         * ждёт на него ответы — без этого адреса на интерфейсе обратный трафик
         * не доходит до приложений. Префикс /32: адрес добавляется точечно, сеть
         * уже описана [TUN_ADDRESS]/[TUN_PREFIX].
         */
        private const val TUN_ADDRESS_PEER = "10.10.0.2"
        private const val TUN_PEER_PREFIX = 32

        /**
         * IPv6-адрес TUN и маршрут `::/0`.
         *
         * Зачем: без IPv6 на интерфейсе система оставляет весь IPv6-трафик вне
         * туннеля — на двустековой сети (почти любой современный мобильный
         * оператор и Wi-Fi) приложение ходит в интернет напрямую, и настоящий
         * IP пользователя утекает мимо VPN, хотя UI показывает «Подключено».
         * Это классическая IPv6-утечка, и «VPN включён» её маскирует.
         *
         * Как решаем: вешаем на интерфейс ULA-адрес (fd00::/8 — приватный
         * диапазон, не маршрутизируемый в интернете, поэтому конфликт с реальной
         * адресацией исключён) и заворачиваем в туннель дефолтный IPv6-маршрут.
         * Дальше возможны два исхода, и оба безопасны:
         *  - ядро умеет IPv6 (Xray/Hysteria2 через свой outbound) — трафик идёт
         *    через туннель;
         *  - ядро IPv6 не обслуживает — пакеты просто тонут в TUN, приложение
         *    получает недоступность IPv6 и по Happy Eyeballs откатывается на
         *    IPv4 через туннель. Утечки нет в любом случае.
         *
         * Префикс /128: адрес точечный, целую IPv6-сеть на интерфейсе описывать
         * незачем — маршрутизацию задаёт отдельный addRoute("::", 0).
         */
        private const val TUN_ADDRESS_V6 = "fd00:1:1::1"
        private const val TUN_PREFIX_V6 = 128

        private const val DNS_PRIMARY = "1.1.1.1"
        private const val DNS_SECONDARY = "8.8.8.8"
        private const val STATS_INTERVAL_MS = 1000L

        /**
         * Через сколько простоя (счётчики не менялись) показывать нулевую
         * скорость. Больше интервала опроса, иначе редкие обновления счётчиков
         * ядра снова выглядели бы как «трафика нет».
         */
        private const val SPEED_IDLE_RESET_MS = 3000L

        /** requestCode отложенной команды CONNECT при перезапуске под другое ядро. */
        private const val REQUEST_RESTART = 1001

        /**
         * Пауза перед отложенным CONNECT: команда должна прийти уже после того,
         * как процесс :vpn умер, иначе система доставит её в старый рантайм.
         */
        private const val RESTART_DELAY_MS = 600L

        /**
         * Kill-switch: не закрывать TUN при НЕОЖИДАННОМ обрыве (падение ядра,
         * длительная потеря сети), пока пользователь сам не нажмёт «Отключить».
         *
         * Зачем: закрытый TUN снимает маршрут по умолчанию, и трафик мгновенно
         * уходит в обход VPN — ровно в тот момент, когда пользователь считает
         * себя защищённым и ничего не заметил (в UI просто «ошибка»). Оставляя
         * интерфейс поднятым БЕЗ движка, мы получаем чёрную дыру: маршруты на
         * месте, пакеты уходят в TUN, читать их некому — соединения не идут
         * никуда, вместо того чтобы пойти напрямую.
         *
         * Инвариант порядка закрытия при этом не нарушается: движок мы всё равно
         * останавливаем немедленно, а tunInterface.close() лишь откладывается до
         * явного disconnect/onDestroy/следующего CONNECT — то есть по-прежнему
         * происходит СТРОГО после engine.stop().
         *
         * Константа, а не настройка: поведение должно быть предсказуемым, но
         * оставляем один переключатель на случай, если понадобится вернуть
         * старое поведение (fail-open) без хирургии по коду.
         */
        private const val KILL_SWITCH_ENABLED = true

        /**
         * Сколько ждать поднятия туннеля, прежде чем признать попытку зависшей.
         * Без этого «залипшая» сеть или молчащее ядро оставляют пользователя в
         * состоянии Connecting навсегда: кнопки нет, статус не меняется, и
         * единственный выход — убить приложение. 45 с — компромисс: медленный
         * DNS + TLS-хендшейк на плохом мобильном интернете укладываются, а
         * реальный «висяк» уже очевиден.
         */
        private const val CONNECT_TIMEOUT_MS = 45_000L

        /**
         * Сколько терпеть полное отсутствие сети, прежде чем перевести туннель в
         * ошибку. Короткие провалы (лифт, переключение Wi-Fi → LTE) отрабатывает
         * штатный хендовер через onAvailable, и дёргать пользователя из-за них
         * нельзя. 30 с — уже не провал, а реальная потеря связи, и честнее
         * показать «Нет сети», чем держать статус «Подключено» при мёртвом
         * туннеле.
         */
        private const val NETWORK_LOSS_TIMEOUT_MS = 30_000L
    }
}
