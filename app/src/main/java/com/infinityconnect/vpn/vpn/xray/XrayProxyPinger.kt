package com.infinityconnect.vpn.vpn.xray

import android.content.Context
import android.util.Log
import com.infinityconnect.vpn.domain.engine.EngineConfig
import com.infinityconnect.vpn.domain.engine.XrayConfigBuilder
import com.infinityconnect.vpn.domain.model.PingMode
import dagger.hilt.android.qualifiers.ApplicationContext
import go.Seq
import libv2ray.Libv2ray
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.ServerSocket
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Прокси-пинг через ядро Xray, как в Happ: поднимает временный инстанс ядра с
 * локальным SOCKS-inbound (без TUN) по профилю сервера и гонит через него
 * HTTP-запрос своим методом (GET/HEAD) и режимом ([PingMode]). Возвращает RTT
 * (мс) или -1 при ошибке/недоступности.
 *
 * В отличие от [Libv2ray.measureOutboundDelay] (жёсткий одиночный GET), даёт
 * контроль над методом, числом запросов и переиспользованием TLS-соединения —
 * это и есть режимы Default/Double/Keepalive.
 *
 * Поддерживает только VLESS (ядро Xray); для прочих профилей возвращает -1 —
 * вызывающий откатывается на TCP. Замеры сериализованы (один инстанс ядра за
 * раз): параллельный запуск нескольких ядер конфликтует за go.Seq/JNI.
 */
@Singleton
class XrayProxyPinger @Inject constructor(
    @ApplicationContext private val context: Context,
    private val configBuilder: XrayConfigBuilder,
) {
    /**
     * Меряет задержку до сервера через его протокол. [head] — HTTP-метод
     * (HEAD vs GET), [mode] — режим замера, [timeoutMs] — таймаут всей операции.
     */
    fun measure(config: EngineConfig, testUrl: String, head: Boolean, mode: PingMode, timeoutMs: Int): Int {
        val vless = when (config) {
            is EngineConfig.Vless -> config
            // Для RawXray (автовыбор/balancer) меряем основной сервер (MAIN).
            is EngineConfig.RawXray -> config.primaryOutbound ?: return -1
            else -> return -1
        }
        return synchronized(coreLock) {
            var controller: libv2ray.CoreController? = null
            try {
                ensureEnv()
                val port = freePort()
                val json = configBuilder.buildProxyPingConfig(vless, port)
                controller = Libv2ray.newCoreController(silentHandler())
                controller.startLoop(json, 0) // fd=0 → без TUN, только SOCKS-inbound
                // Ядру нужно мгновение, чтобы поднять inbound перед первым запросом.
                Thread.sleep(CORE_WARMUP_MS)
                requestThroughProxy(port, testUrl, head, mode, timeoutMs)
            } catch (t: Throwable) {
                Log.d(TAG, "proxy ping failed: ${t.message}")
                -1
            } finally {
                runCatching { controller?.stopLoop() }
            }
        }
    }

    /** Гонит HTTP через SOCKS-прокси ядра выбранным режимом; возвращает мс или -1. */
    private fun requestThroughProxy(port: Int, testUrl: String, head: Boolean, mode: PingMode, timeoutMs: Int): Int {
        val proxy = Proxy(Proxy.Type.SOCKS, InetSocketAddress("127.0.0.1", port))
        val client = OkHttpClient.Builder()
            .proxy(proxy)
            .connectTimeout(timeoutMs.toLong(), TimeUnit.MILLISECONDS)
            .readTimeout(timeoutMs.toLong(), TimeUnit.MILLISECONDS)
            .callTimeout(timeoutMs.toLong(), TimeUnit.MILLISECONDS)
            // Один maiden-роут: своим пулом управляем через Connection-заголовок.
            .retryOnConnectionFailure(false)
            .build()
        return try {
            when (mode) {
                // Несколько независимых запросов — берём лучший (минимум).
                PingMode.DEFAULT -> {
                    var best = -1
                    repeat(DEFAULT_ATTEMPTS) {
                        val ms = singleRequest(client, testUrl, head, keepAlive = false)
                        if (ms in 0 until (if (best < 0) Int.MAX_VALUE else best)) best = ms
                    }
                    best
                }
                // Два запроса, каждый на новом соединении; меряем второй (прогрет ядром).
                PingMode.DOUBLE -> {
                    singleRequest(client, testUrl, head, keepAlive = false)
                    singleRequest(client, testUrl, head, keepAlive = false)
                }
                // Два запроса по одному TLS-соединению; меряем второй (без TLS-хендшейка).
                PingMode.KEEPALIVE -> {
                    val first = singleRequest(client, testUrl, head, keepAlive = true)
                    if (first < 0) -1 else singleRequest(client, testUrl, head, keepAlive = true)
                }
            }
        } finally {
            client.dispatcher.executorService.shutdown()
            client.connectionPool.evictAll()
        }
    }

    /** Один HTTP-запрос через прокси; RTT до конца ответа в мс, либо -1. */
    private fun singleRequest(client: OkHttpClient, url: String, head: Boolean, keepAlive: Boolean): Int =
        runCatching {
            val request = Request.Builder()
                .url(url)
                .apply { if (head) head() else get() }
                .header("Cache-Control", "no-cache")
                // keepAlive=false → просим сервер закрыть соединение (Double/Default).
                .apply { if (!keepAlive) header("Connection", "close") }
                .build()
            val start = System.nanoTime()
            client.newCall(request).execute().use { response ->
                // Дочитываем тело, чтобы RTT включал полный ответ (для GET).
                response.body?.bytes()
                if (!response.isSuccessful && response.code !in 200..399) return -1
                ((System.nanoTime() - start) / 1_000_000).toInt()
            }
        }.getOrDefault(-1)

    /** Инициализирует окружение ядра единожды на процесс. */
    private fun ensureEnv() {
        if (envReady) return
        Seq.setContext(context.applicationContext)
        val datDir = File(context.filesDir, "xray").apply { if (!exists()) mkdirs() }
        Libv2ray.initCoreEnv(datDir.absolutePath, XUDP_STUB_KEY)
        envReady = true
    }

    /** Свободный локальный TCP-порт для SOCKS-inbound ядра. */
    private fun freePort(): Int = ServerSocket(0).use { it.localPort }

    private fun silentHandler() = object : libv2ray.CoreCallbackHandler {
        override fun startup(): Long = 0
        override fun shutdown(): Long = 0
        override fun onEmitStatus(l: Long, s: String?): Long = 0
    }

    private companion object {
        const val TAG = "XrayProxyPinger"
        /** Пауза на подъём inbound перед первым запросом. */
        const val CORE_WARMUP_MS = 120L
        /** Число попыток в режиме DEFAULT (берём лучшую). */
        const val DEFAULT_ATTEMPTS = 3
        /** 32 нулевых байта в base64 (url-safe, no-pad) — валидный XUDP-ключ. */
        const val XUDP_STUB_KEY = "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA"

        @Volatile
        private var envReady = false

        /** Сериализует запуск инстансов ядра: параллельные ядра ломают go.Seq/JNI. */
        private val coreLock = Any()
    }
}
