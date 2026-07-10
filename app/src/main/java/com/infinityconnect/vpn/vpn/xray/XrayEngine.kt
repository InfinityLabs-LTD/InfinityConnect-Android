package com.infinityconnect.vpn.vpn.xray

import android.content.Context
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
 * Движок для VLESS (включая Reality и транспорт XHTTP) на базе Xray-core.
 *
 * Схема запуска:
 *  1. Строим JSON-конфиг Xray с локальным SOCKS-inbound ([XrayConfigBuilder]).
 *  2. Запускаем ядро Xray ([XrayCoreBridge]).
 *  3. Заворачиваем TUN → локальный SOCKS через tun2socks ([Tun2SocksBridge]).
 *
 * Реальная работа требует подключённых AAR (libXray + tun2socks) — см. README.
 */
@Singleton
class XrayEngine @Inject constructor(
    @ApplicationContext private val context: Context,
    private val configBuilder: XrayConfigBuilder,
) : VpnEngine {

    @Volatile
    private var running = false

    override fun supports(config: EngineConfig): Boolean = config is EngineConfig.Vless

    override fun start(config: EngineConfig, tunFd: Int, mtu: Int) {
        require(config is EngineConfig.Vless) { "XrayEngine поддерживает только VLESS" }

        val json = configBuilder.build(config, socksPort = SOCKS_PORT)
        Log.d(TAG, "Xray config построен для ${config.remark}")

        val datDir = ensureDatDir()
        // 1. Ядро Xray (поднимает SOCKS на 127.0.0.1:SOCKS_PORT).
        XrayCoreBridge.run(datDir = datDir.absolutePath, configJson = json)
        // 2. TUN → SOCKS.
        Tun2SocksBridge.start(tunFd = tunFd, socksPort = SOCKS_PORT, mtu = mtu)

        running = true
        Log.i(TAG, "XrayEngine запущен")
    }

    override fun stop() {
        if (!running) return
        runCatching { Tun2SocksBridge.stop() }
        runCatching { XrayCoreBridge.stop() }
        running = false
        Log.i(TAG, "XrayEngine остановлен")
    }

    override fun queryStats(): TunnelStats? {
        // Статистику по трафику libXray умеет отдавать через stats API, но это
        // зависит от сборки AAR. Пока возвращаем null — сервис оценит скорость
        // по счётчикам TUN-интерфейса. TODO: пробросить stats из libXray.
        return null
    }

    /** Каталог для geoip/geosite и служебных файлов Xray. */
    private fun ensureDatDir(): File =
        File(context.filesDir, "xray").apply { if (!exists()) mkdirs() }

    private companion object {
        const val TAG = "XrayEngine"
        const val SOCKS_PORT = 10808
    }
}
