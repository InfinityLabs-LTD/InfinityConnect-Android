package com.infinityconnect.vpn.domain.usecase

import com.infinityconnect.vpn.data.local.SettingsStore
import com.infinityconnect.vpn.domain.model.PingMethod
import com.infinityconnect.vpn.domain.model.PingSettings
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
) {
    private val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(TIMEOUT_MS.toLong(), TimeUnit.MILLISECONDS)
            .readTimeout(TIMEOUT_MS.toLong(), TimeUnit.MILLISECONDS)
            .callTimeout(TIMEOUT_MS.toLong(), TimeUnit.MILLISECONDS)
            .build()
    }

    suspend operator fun invoke(address: String, port: Int): Int {
        val settings = settingsStore.currentPing()
        return invokeWith(address, port, settings)
    }

    suspend fun invokeWith(address: String, port: Int, settings: PingSettings): Int =
        withContext(Dispatchers.IO) {
            when (settings.method) {
                PingMethod.TCP -> tcpPing(address, port)
                PingMethod.ICMP -> icmpPing(address)
                PingMethod.PROXY_GET -> httpPing(settings.testUrl, head = false)
                PingMethod.PROXY_HEAD -> httpPing(settings.testUrl, head = true)
            }
        }

    /**
     * Берём лучший (минимальный) из нескольких TCP-хендшейков: первое измерение
     * включает разовые накладные расходы (DNS-резолв, прогрев), а параллельная
     * нагрузка добавляет джиттер — минимум даёт стабильную оценку задержки узла.
     */
    private fun tcpPing(address: String, port: Int): Int {
        // Резолвим адрес заранее, чтобы DNS не попадал в измерение хендшейка.
        val resolved = runCatching { InetAddress.getByName(address) }.getOrNull() ?: return -1
        var best = -1
        repeat(TCP_ATTEMPTS) {
            val ms = runCatching {
                Socket().use { socket ->
                    val start = System.nanoTime()
                    socket.connect(InetSocketAddress(resolved, port), TIMEOUT_MS)
                    ((System.nanoTime() - start) / 1_000_000).toInt()
                }
            }.getOrDefault(-1)
            if (ms >= 0 && (best < 0 || ms < best)) best = ms
        }
        return best
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
        const val TIMEOUT_MS = 4000
        /** Число TCP-хендшейков за замер; берём лучший, чтобы сгладить джиттер. */
        const val TCP_ATTEMPTS = 3
    }
}
