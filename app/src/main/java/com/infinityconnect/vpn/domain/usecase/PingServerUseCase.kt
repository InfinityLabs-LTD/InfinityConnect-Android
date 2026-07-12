package com.infinityconnect.vpn.domain.usecase

import com.infinityconnect.vpn.data.local.SettingsStore
import com.infinityconnect.vpn.domain.model.PingMethod
import com.infinityconnect.vpn.domain.model.PingSettings
import com.infinityconnect.vpn.domain.model.SubscriptionServer
import com.infinityconnect.vpn.vpn.xray.XrayDelayMeter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Измеряет задержку до сервера выбранным методом. Значение — всегда в мс,
 * -1 при недоступности/таймауте.
 *
 * Методы:
 *  - [PingMethod.TCP]  — время TCP-хендшейка до host:port сервера;
 *  - [PingMethod.ICMP] — ICMP echo (InetAddress.isReachable) до адреса сервера;
 *  - [PingMethod.PROXY_GET]/[PingMethod.PROXY_HEAD] — HTTP(S)-запрос до тест-URL
 *    (по умолчанию Cloudflare), меряет время до первого байта ответа.
 */
@Singleton
class PingServerUseCase @Inject constructor(
    private val settingsStore: SettingsStore,
    private val delayMeter: XrayDelayMeter,
) {
    private val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(TIMEOUT_MS.toLong(), TimeUnit.MILLISECONDS)
            .readTimeout(TIMEOUT_MS.toLong(), TimeUnit.MILLISECONDS)
            .callTimeout(TIMEOUT_MS.toLong(), TimeUnit.MILLISECONDS)
            .build()
    }

    /**
     * Пинг сервера подписки текущим методом. Принимает весь [server], т.к.
     * метод REAL меряет задержку через ядро по его профилю ([server.config]).
     */
    suspend operator fun invoke(server: SubscriptionServer): Int {
        val settings = settingsStore.currentPing()
        return withContext(Dispatchers.IO) {
            when (settings.method) {
                PingMethod.REAL -> realPing(server, settings.testUrl)
                PingMethod.TCP -> tcpPing(server.address, server.port)
                PingMethod.ICMP -> icmpPing(server.address)
                PingMethod.PROXY_GET -> httpPing(settings.testUrl, head = false)
                PingMethod.PROXY_HEAD -> httpPing(settings.testUrl, head = true)
            }
        }
    }

    /** Пинг по адресу без профиля (методы, не требующие ядра). */
    suspend fun invokeWith(address: String, port: Int, settings: PingSettings): Int =
        withContext(Dispatchers.IO) {
            when (settings.method) {
                // Без профиля «реальный» пинг невозможен — откатываемся на TCP.
                PingMethod.REAL, PingMethod.TCP -> tcpPing(address, port)
                PingMethod.ICMP -> icmpPing(address)
                PingMethod.PROXY_GET -> httpPing(settings.testUrl, head = false)
                PingMethod.PROXY_HEAD -> httpPing(settings.testUrl, head = true)
            }
        }

    /**
     * «Реальный» пинг через ядро Xray (measureOutboundDelay). Доступен только
     * для VLESS; для Hysteria2 и при ошибке ядра откатываемся на TCP-хендшейк,
     * чтобы в списке всё равно было осмысленное значение.
     */
    private fun realPing(server: SubscriptionServer, testUrl: String): Int {
        val ms = delayMeter.measure(server.config, testUrl)
        return if (ms >= 0) ms else tcpPing(server.address, server.port)
    }

    /**
     * TCP-хендшейк host:port, усреднённый устойчиво к выбросам.
     *
     * Наблюдается разброс (то 20–30 мс, то ~400) при неизменной сети: первый
     * коннект тянет разовые накладные (прогрев сокета/маршрута), а под
     * параллельной нагрузкой планировщик даёт всплески. Поэтому:
     *  - первая попытка — прогревочная, её результат отбрасываем;
     *  - из оставшихся замеров берём МЕДИАНУ (а не единичный минимум: минимум
     *    может «повезти» и занизить, медиана отсекает случайные всплески).
     */
    private fun tcpPing(address: String, port: Int): Int {
        // Резолвим адрес заранее, чтобы DNS не попадал в измерение хендшейка.
        val resolved = runCatching { InetAddress.getByName(address) }.getOrNull() ?: return -1
        val samples = ArrayList<Int>(TCP_ATTEMPTS)
        repeat(TCP_WARMUP + TCP_ATTEMPTS) { attempt ->
            val ms = runCatching {
                Socket().use { socket ->
                    val start = System.nanoTime()
                    socket.connect(InetSocketAddress(resolved, port), TIMEOUT_MS)
                    ((System.nanoTime() - start) / 1_000_000).toInt()
                }
            }.getOrDefault(-1)
            // Прогревочные попытки не учитываем в итоговом значении.
            if (attempt >= TCP_WARMUP && ms >= 0) samples += ms
        }
        return median(samples)
    }

    /** Медиана списка замеров; -1, если ни одного успешного. */
    private fun median(samples: List<Int>): Int {
        if (samples.isEmpty()) return -1
        val sorted = samples.sorted()
        val mid = sorted.size / 2
        return if (sorted.size % 2 == 1) sorted[mid] else (sorted[mid - 1] + sorted[mid]) / 2
    }

    private fun icmpPing(address: String): Int = runCatching {
        val inet = InetAddress.getByName(address)
        val start = System.nanoTime()
        if (inet.isReachable(TIMEOUT_MS)) {
            ((System.nanoTime() - start) / 1_000_000).toInt()
        } else {
            -1
        }
    }.getOrDefault(-1)

    private fun httpPing(url: String, head: Boolean): Int = runCatching {
        val request = Request.Builder()
            .url(url)
            .apply { if (head) head() else get() }
            .header("Cache-Control", "no-cache")
            .build()
        val start = System.nanoTime()
        httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful && response.code !in 200..399) {
                return@runCatching -1
            }
            ((System.nanoTime() - start) / 1_000_000).toInt()
        }
    }.getOrElse { if (it is IOException) -1 else -1 }

    private companion object {
        const val TIMEOUT_MS = 6000
        /** Прогревочные хендшейки, результат которых отбрасываем. */
        const val TCP_WARMUP = 1
        /** Учитываемые хендшейки за замер; из них берём медиану. */
        const val TCP_ATTEMPTS = 4
    }
}
