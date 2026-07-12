package com.infinityconnect.vpn.vpn.xray

import android.content.Context
import android.util.Log
import com.infinityconnect.vpn.domain.engine.EngineConfig
import com.infinityconnect.vpn.domain.engine.XrayConfigBuilder
import dagger.hilt.android.qualifiers.ApplicationContext
import go.Seq
import libv2ray.Libv2ray
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * «Реальный» пинг сервера через ядро Xray: строит минимальный outbound по
 * профилю и вызывает [Libv2ray.measureOutboundDelay] — ядро само поднимает
 * outbound, шлёт HTTP-запрос к тест-URL через него и возвращает RTT (мс).
 * Так меряют задержку v2RayTun/Happ: end-to-end через протокол, а не голый TCP.
 *
 * Работает без активного VPN. Инициализация окружения ядра идемпотентна на
 * процесс (см. [ensureEnv]); если VPN уже поднят, env уже проинициализирован
 * его сервисом — повторный вызов безопасно пропускается.
 */
@Singleton
class XrayDelayMeter @Inject constructor(
    @ApplicationContext private val context: Context,
    private val configBuilder: XrayConfigBuilder,
) {
    /**
     * Измеряет задержку до сервера через его протокол. Возвращает мс или -1
     * при ошибке/недоступности. Поддерживает только VLESS (ядро Xray); для
     * прочих профилей возвращает -1 — вызывающий откатывается на TCP.
     */
    fun measure(config: EngineConfig, testUrl: String): Int {
        val vless = config as? EngineConfig.Vless ?: return -1
        return try {
            ensureEnv()
            val json = configBuilder.buildDelayTestConfig(vless)
            val ms = Libv2ray.measureOutboundDelay(json, testUrl)
            if (ms in 0..MAX_MS) ms.toInt() else -1
        } catch (t: Throwable) {
            Log.d(TAG, "measureOutboundDelay failed: ${t.message}")
            -1
        }
    }

    /** Инициализирует окружение ядра единожды на процесс. */
    private fun ensureEnv() {
        if (envReady) return
        synchronized(lock) {
            if (envReady) return
            Seq.setContext(context.applicationContext)
            val datDir = File(context.filesDir, "xray").apply { if (!exists()) mkdirs() }
            // Ключ XUDP для измерения не важен — ставим валидную 32-байтовую заглушку.
            Libv2ray.initCoreEnv(datDir.absolutePath, XUDP_STUB_KEY)
            envReady = true
        }
    }

    private companion object {
        const val TAG = "XrayDelayMeter"
        /** Верхняя граница осмысленного пинга; выше считаем недоступным. */
        const val MAX_MS = 60_000L
        /** 32 нулевых байта в base64 (url-safe, no-pad) — валидный XUDP-ключ. */
        const val XUDP_STUB_KEY = "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA"

        @Volatile
        private var envReady = false
        private val lock = Any()
    }
}
