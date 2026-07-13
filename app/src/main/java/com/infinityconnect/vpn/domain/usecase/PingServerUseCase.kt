package com.infinityconnect.vpn.domain.usecase

import com.infinityconnect.vpn.data.local.SettingsStore
import com.infinityconnect.vpn.domain.model.PingMethod
import com.infinityconnect.vpn.domain.model.SubscriptionServer
import com.infinityconnect.vpn.vpn.xray.XrayProxyPinger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Измеряет задержку до сервера выбранным протоколом (как в Happ). Значение —
 * всегда в мс, -1 при недоступности/таймауте.
 *
 * Протоколы:
 *  - [PingMethod.PROXY_GET]/[PingMethod.PROXY_HEAD] — HTTP(S) GET/HEAD к тест-URL
 *    ЧЕРЕЗ сам протокол сервера (локальный SOCKS-inbound ядра); режим (via …) и
 *    таймаут — из настроек. Для не-VLESS профилей откат на TCP.
 *  - [PingMethod.TCP]  — время TCP-хендшейка до host:port сервера;
 *  - [PingMethod.ICMP] — ICMP echo (InetAddress.isReachable) до адреса сервера.
 */
@Singleton
class PingServerUseCase @Inject constructor(
    private val settingsStore: SettingsStore,
    private val proxyPinger: XrayProxyPinger,
) {
    /**
     * Пинг сервера подписки текущим протоколом. Принимает весь [server], т.к.
     * прокси-методы меряют задержку через ядро по его профилю ([server.config]).
     */
    suspend operator fun invoke(server: SubscriptionServer): Int {
        val settings = settingsStore.currentPing()
        return withContext(Dispatchers.IO) {
            when (settings.method) {
                PingMethod.PROXY_GET -> proxyPing(server, head = false)
                PingMethod.PROXY_HEAD -> proxyPing(server, head = true)
                PingMethod.TCP -> tcpPing(server.address, server.port)
                PingMethod.ICMP -> icmpPing(server.address)
            }
        }
    }

    /**
     * Прокси-пинг через ядро Xray. Доступен только для VLESS; для Hysteria2 и
     * при ошибке ядра откатываемся на TCP-хендшейк, чтобы в списке всё равно
     * было осмысленное значение.
     */
    private suspend fun proxyPing(server: SubscriptionServer, head: Boolean): Int {
        val s = settingsStore.currentPing()
        val ms = proxyPinger.measure(server.config, s.testUrl, head, s.mode, s.timeoutMs)
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

    private companion object {
        const val TIMEOUT_MS = 6000
        /** Прогревочные хендшейки, результат которых отбрасываем. */
        const val TCP_WARMUP = 1
        /** Учитываемые хендшейки за замер; из них берём медиану. */
        const val TCP_ATTEMPTS = 4
    }
}
