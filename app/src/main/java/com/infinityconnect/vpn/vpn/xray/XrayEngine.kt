package com.infinityconnect.vpn.vpn.xray

import android.content.Context
import android.net.VpnService
import com.infinityconnect.vpn.BuildFlags
import com.infinityconnect.vpn.domain.engine.EngineConfig
import com.infinityconnect.vpn.domain.engine.XrayConfigBuilder
import com.infinityconnect.vpn.domain.repository.RoutingRepository
import com.infinityconnect.vpn.vpn.TunnelStats
import com.infinityconnect.vpn.vpn.VpnEngine
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.runBlocking
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Движок для VLESS (включая Reality и транспорт XHTTP) на базе Xray-core
 * (AndroidLibXrayLite / libv2ray).
 *
 * Схема запуска:
 *  1. Строим JSON-конфиг Xray с inbound типа "tun" ([XrayConfigBuilder]).
 *  2. Инициализируем окружение ядра (контекст VpnService для protect + assets).
 *  3. Запускаем ядро, передавая TUN fd — встроенный tun2socks заворачивает
 *     трафик TUN в аутбаунд vless ([XrayCoreBridge]).
 *
 * Реальная работа требует подключённого нативного AAR libv2ray — см. README.
 */
@Singleton
class XrayEngine @Inject constructor(
    @ApplicationContext private val context: Context,
    private val configBuilder: XrayConfigBuilder,
    private val routingRepository: RoutingRepository,
    private val logStore: com.infinityconnect.vpn.data.local.LogStore,
) : VpnEngine {

    @Volatile
    private var running = false

    private var bridge: XrayCoreBridge? = null

    /** Колбэк «ядро остановилось само» — сервис подхватывает разрыв. */
    var onCoreStopped: (() -> Unit)? = null

    override fun supports(config: EngineConfig): Boolean =
        config is EngineConfig.Vless || config is EngineConfig.RawXray

    override fun start(service: VpnService, config: EngineConfig, tunFd: Int, mtu: Int) {
        // Актуальные настройки маршрутизации (режим/пресеты/домены) — нужны
        // обоим путям: Vless применяет их целиком, RawXray — доменные правила.
        val routing = runBlocking { routingRepository.current() }
        val json = when (config) {
            // Готовый конфиг из подписки (автовыбор/balancer/WHITE) — пробрасываем
            // целиком; серверный routing важнее пользовательского режима, но
            // клиентские доменные правила (пресеты/сайты) подмешиваются сверху.
            is EngineConfig.RawXray -> {
                logStore.d(TAG, "Xray raw-config проброшен для ${config.remark} (presets=${routing.sitePresets.size})")
                configBuilder.buildRaw(
                    config,
                    mtu = mtu,
                    routing = routing,
                    enableLogging = BuildFlags.VERBOSE_CORE_LOG,
                )
            }
            is EngineConfig.Vless -> {
                logStore.d(TAG, "Xray config построен для ${config.remark} (routing=${routing.mode})")
                configBuilder.build(
                    config,
                    mtu = mtu,
                    routing = routing,
                    enableLogging = BuildFlags.VERBOSE_CORE_LOG,
                )
            }
            else -> throw IllegalArgumentException("XrayEngine поддерживает только VLESS/RawXray")
        }

        val core = XrayCoreBridge(
            logStore = logStore,
            onCoreShutdown = { onCoreStopped?.invoke() },
        )
        // Контекст самого VpnService — через него ядро вызывает protect().
        core.initEnv(service, ensureDatDir())
        core.start(configJson = json, tunFd = tunFd)

        bridge = core
        running = true
        logStore.i(TAG, "XrayEngine запущен")
    }

    override fun stop() {
        if (!running) return
        runCatching { bridge?.stop() }
        bridge = null
        running = false
        logStore.i(TAG, "XrayEngine остановлен")
    }

    override fun queryStats(): TunnelStats? {
        val traffic = bridge?.queryProxyTraffic() ?: return null
        return TunnelStats(
            totalUploadBytes = traffic.first,
            totalDownloadBytes = traffic.second,
        )
    }

    /** Каталог для geoip/geosite и служебных файлов Xray. */
    private fun ensureDatDir(): File =
        File(context.filesDir, "xray").apply { if (!exists()) mkdirs() }

    private companion object {
        const val TAG = "XrayEngine"
    }
}
