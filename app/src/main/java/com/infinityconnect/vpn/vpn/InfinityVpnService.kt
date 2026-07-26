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
                releaseActiveTunnel()

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
                val onCoreStopped = { if (tunnelEstablished) stopWithError("Соединение разорвано") }
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
     */
    private fun releaseActiveTunnel() {
        tunnelEstablished = false
        statsJob?.cancel()
        statsJob = null
        unregisterNetworkCallback()
        runCatching { activeEngine?.stop() }
        activeEngine = null
        runCatching { tunInterface?.close() }
        tunInterface = null
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
                logStore.i(TAG, "Сеть переключена на $network")
            }

            override fun onLost(network: Network) {
                // Текущая сеть пропала. Не сбрасываем в null — ждём onAvailable
                // следующей default-сети; система придержит пакеты до переключения.
                logStore.w(TAG, "Сеть потеряна: $network")
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
        // Скорость = дельта суммарного трафика между замерами.
        var prevUp = 0L
        var prevDown = 0L
        var prevMs = System.currentTimeMillis()
        statsJob = scope.launch {
            while (isActive) {
                val now = System.currentTimeMillis()
                val elapsed = (now - sessionStartMs) / 1000
                val raw = engine.queryStats()
                val totalUp = raw?.totalUploadBytes ?: 0
                val totalDown = raw?.totalDownloadBytes ?: 0
                val dtSec = ((now - prevMs) / 1000.0).coerceAtLeast(0.001)
                val upSpeed = ((totalUp - prevUp).coerceAtLeast(0) / dtSec).toLong()
                val downSpeed = ((totalDown - prevDown).coerceAtLeast(0) / dtSec).toLong()
                prevUp = totalUp; prevDown = totalDown; prevMs = now

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

    override fun onDestroy() {
        scope.cancel()
        unregisterNetworkCallback()
        runCatching { activeEngine?.stop() }
        runCatching { tunInterface?.close() }
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
        private const val DNS_PRIMARY = "1.1.1.1"
        private const val DNS_SECONDARY = "8.8.8.8"
        private const val STATS_INTERVAL_MS = 1000L

        /** requestCode отложенной команды CONNECT при перезапуске под другое ядро. */
        private const val REQUEST_RESTART = 1001

        /**
         * Пауза перед отложенным CONNECT: команда должна прийти уже после того,
         * как процесс :vpn умер, иначе система доставит её в старый рантайм.
         */
        private const val RESTART_DELAY_MS = 600L
    }
}
