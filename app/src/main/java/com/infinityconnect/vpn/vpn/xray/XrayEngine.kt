package com.infinityconnect.vpn.vpn.xray

import android.content.Context
import android.net.VpnService
import android.util.Log
import com.infinityconnect.vpn.domain.engine.EngineConfig
import com.infinityconnect.vpn.domain.engine.XrayConfigBuilder
import com.infinityconnect.vpn.vpn.TunnelStats
import com.infinityconnect.vpn.vpn.VpnEngine
import dagger.hilt.android.qualifiers.ApplicationContext
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
) : VpnEngine {

    @Volatile
    private var running = false

    private var bridge: XrayCoreBridge? = null

    /** Колбэк «ядро остановилось само» — сервис подхватывает разрыв. */
    var onCoreStopped: (() -> Unit)? = null

    override fun supports(config: EngineConfig): Boolean = config is EngineConfig.Vless

    override fun start(service: VpnService, config: EngineConfig, tunFd: Int, mtu: Int) {
        require(config is EngineConfig.Vless) { "XrayEngine поддерживает только VLESS" }

        val json = configBuilder.build(config, mtu = mtu)
        Log.d(TAG, "Xray config построен для ${config.remark}")

        val core = XrayCoreBridge(onCoreShutdown = { onCoreStopped?.invoke() })
        // Контекст самого VpnService — через него ядро вызывает protect().
        core.initEnv(service, ensureDatDir())
        core.start(configJson = json, tunFd = tunFd)

        bridge = core
        running = true
        Log.i(TAG, "XrayEngine запущен")
    }

    override fun stop() {
        if (!running) return
        runCatching { bridge?.stop() }
        bridge = null
        running = false
        Log.i(TAG, "XrayEngine остановлен")
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
