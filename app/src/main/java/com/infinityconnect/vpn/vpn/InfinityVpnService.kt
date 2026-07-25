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
     * Колбэк смены нижележащей сети. При Wi-Fi ↔ мобильный система меняет
     * default network — мы сообщаем её туннелю через [setUnderlyingNetworks],
     * чтобы TUN не «завис» на исчезнувшей сети и учёт трафика/энергосбережение
     * оставались корректными. Сокеты ядра (через protect) переустановятся на
     * новую сеть автоматически при следующем запросе.
     */
    private var networkCallback: ConnectivityManager.NetworkCallback? = null
    private var sessionStartMs: Long = 0

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
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
            try {
                val config = when (val r = buildConnection(keyId, serverIndex)) {
                    is AppResult.Success -> r.data
                    is AppResult.Failure -> {
                        stopWithError("Не удалось получить конфигурацию сервера")
                        return@launch
                    }
                }

                val engine = engineSelector.select(config)
                logStore.i(
                    TAG,
                    "Профиль готов: ${config.remark}, движок=${engine.javaClass.simpleName}",
                )
                val tun = establishTun(config) ?: run {
                    stopWithError("Не удалось создать TUN-интерфейс")
                    return@launch
                }
                tunInterface = tun
                logStore.i(TAG, "TUN поднят (fd=${tun.fd}, mtu=$MTU)")

                // Следим за нижележащей сетью — туннель переживает Wi-Fi ↔ мобильный.
                registerNetworkCallback()

                // Если ядро само остановится (разрыв) — отражаем это в UI.
                val onCoreStopped = { if (activeEngine != null) stopWithError("Соединение разорвано") }
                when (engine) {
                    is com.infinityconnect.vpn.vpn.xray.XrayEngine -> engine.onCoreStopped = onCoreStopped
                    is com.infinityconnect.vpn.vpn.hysteria2.Hysteria2Engine -> engine.onCoreStopped = onCoreStopped
                }

                // Запуск движка (блокирующая инициализация).
                // service = this — ядру нужен VpnService для protect() сокетов.
                engine.start(this@InfinityVpnService, config, tunFd = tun.fd, mtu = MTU)
                activeEngine = engine

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
                    is NoClassDefFoundError, is UnsatisfiedLinkError ->
                        "Нативный движок Xray (libv2ray) не подключён. См. README."
                    else -> e.message ?: "Ошибка подключения"
                }
                stopWithError(msg)
            }
        }
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
        statsJob?.cancel()
        statsJob = null
        unregisterNetworkCallback()
        runCatching { activeEngine?.stop() }
        activeEngine = null
        runCatching { tunInterface?.close() }
        tunInterface = null
        stateHolder.reset()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun stopWithError(message: String) {
        logStore.e(TAG, "Остановка с ошибкой: $message")
        statsJob?.cancel()
        unregisterNetworkCallback()
        runCatching { activeEngine?.stop() }
        activeEngine = null
        runCatching { tunInterface?.close() }
        tunInterface = null
        stateHolder.setActiveConnection(null)
        stateHolder.updateState(TunnelState.Error(message))
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
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
        private const val TUN_ADDRESS = "10.10.0.2"
        private const val TUN_PREFIX = 30
        private const val DNS_PRIMARY = "1.1.1.1"
        private const val DNS_SECONDARY = "8.8.8.8"
        private const val STATS_INTERVAL_MS = 1000L
    }
}
