package com.infinityconnect.vpn.vpn.hysteria2

import android.net.VpnService
import com.infinityconnect.vpn.domain.engine.EngineConfig
import com.infinityconnect.vpn.domain.repository.RoutingRepository
import com.infinityconnect.vpn.vpn.TunnelStats
import com.infinityconnect.vpn.vpn.VpnEngine
import kotlinx.coroutines.runBlocking
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Движок Hysteria2 на базе нативного клиента github.com/apernet/hysteria
 * (пакет hysteria2, собран gomobile).
 *
 * Схема запуска (аналогична [com.infinityconnect.vpn.vpn.xray.XrayEngine]):
 *  1. Строим JSON-конфиг клиента из [EngineConfig.Hysteria2] ([Hysteria2ConfigBuilder]).
 *  2. Передаём контекст VpnService клиенту (для protect своих сокетов).
 *  3. Запускаем клиент, передавая TUN fd — встроенный стек заворачивает трафик
 *     TUN в QUIC-соединение ([Hysteria2CoreBridge]); tun2socks не нужен.
 *
 * Реальная работа требует подключённого нативного AAR libhysteria2 — см. README.
 * До подключения AAR вызовы Go-обёртки бросают исключение/NoClassDefFoundError,
 * которое сервис показывает в UI как «протокол пока не поддерживается».
 */
@Singleton
class Hysteria2Engine @Inject constructor(
    private val configBuilder: Hysteria2ConfigBuilder,
    private val routingRepository: RoutingRepository,
    private val logStore: com.infinityconnect.vpn.data.local.LogStore,
) : VpnEngine {

    @Volatile
    private var running = false

    private var bridge: Hysteria2CoreBridge? = null

    /** Колбэк «ядро остановилось само» — сервис подхватывает разрыв. */
    var onCoreStopped: (() -> Unit)? = null

    override fun supports(config: EngineConfig): Boolean = config is EngineConfig.Hysteria2

    override fun start(service: VpnService, config: EngineConfig, tunFd: Int, mtu: Int) {
        require(config is EngineConfig.Hysteria2) { "Hysteria2Engine поддерживает только Hysteria2" }

        val routing = runBlocking { routingRepository.current() }
        val json = configBuilder.build(config, routing = routing)
        logStore.d(TAG, "Hysteria2 config построен для ${config.remark} (routing=${routing.mode})")

        val core = Hysteria2CoreBridge(
            service = service,
            logStore = logStore,
            onCoreShutdown = { onCoreStopped?.invoke() },
        )
        // Протектор на базе VpnService — через него клиент защищает свой сокет.
        core.initEnv()
        core.start(configJson = json, tunFd = tunFd, mtu = mtu, tunCidr = TUN_CIDR)

        bridge = core
        running = true
        logStore.i(TAG, "Hysteria2Engine запущен")
    }

    override fun stop() {
        if (!running) return
        runCatching { bridge?.stop() }
        bridge = null
        running = false
        logStore.i(TAG, "Hysteria2Engine остановлен")
    }

    override fun queryStats(): TunnelStats? {
        val traffic = bridge?.queryTraffic() ?: return null
        return TunnelStats(
            totalUploadBytes = traffic.first,
            totalDownloadBytes = traffic.second,
        )
    }

    companion object {
        private const val TAG = "Hysteria2Engine"

        /**
         * Адрес TUN с префиксом для системного стека sing-tun.
         *
         * Стек трактует ПЕРВЫЙ адрес префикса (10.10.0.1) как свой — на нём он
         * биндит TCP-форвардер, — а следующий (10.10.0.2) как адрес клиента.
         * Поэтому 10.10.0.1 обязан быть выставлен на интерфейсе: он совпадает с
         * `TUN_ADDRESS`/`TUN_PREFIX` в
         * [com.infinityconnect.vpn.vpn.InfinityVpnService]. Разъезд этих значений
         * даёт "bind: cannot assign requested address" при старте стека.
         * В префиксе нужно ≥2 адреса, поэтому /30 — минимальный допустимый.
         */
        const val TUN_CIDR = "10.10.0.1/30"
    }
}
