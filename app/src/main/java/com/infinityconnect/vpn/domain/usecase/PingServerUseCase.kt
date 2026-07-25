package com.infinityconnect.vpn.domain.usecase

import com.infinityconnect.vpn.data.local.SettingsStore
import com.infinityconnect.vpn.domain.model.PingMethod
import com.infinityconnect.vpn.domain.model.SubscriptionServer
import com.infinityconnect.vpn.domain.model.VpnProtocol
import com.infinityconnect.vpn.vpn.xray.XrayProxyPinger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.net.SocketTimeoutException
import kotlin.random.Random
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
                PingMethod.TCP -> transportPing(server)
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
        return if (ms >= 0) ms else transportPing(server)
    }

    /**
     * Замер по транспорту сервера: TCP-хендшейк или (для Hysteria2) UDP-проба.
     *
     * Hysteria2 работает поверх QUIC/UDP — TCP-порта на сервере нет, поэтому
     * TCP-хендшейк там всегда давал -1 (в списке — прочерк). Разводим по
     * [SubscriptionServer.protocol].
     */
    private fun transportPing(server: SubscriptionServer): Int =
        if (server.protocol == VpnProtocol.HYSTERIA2) {
            udpPing(server.address, server.port)
        } else {
            tcpPing(server.address, server.port)
        }

    /**
     * Задержка до Hysteria2-сервера: UDP-проба (живость) + ICMP (величина).
     *
     * У Hysteria2 нет TCP-порта — хендшейк, которым меряются остальные
     * протоколы, здесь невозможен, и раньше в списке стоял прочерк. Прямой
     * round-trip по UDP тоже не снять: сервер маскируется и на чужую датаграмму
     * молча не отвечает, так что «ответ» — это всегда таймаут ожидания, а не
     * задержка (отсюда были ~1200 мс у живого сервера).
     *
     * Поэтому роли разделены:
     *  - UDP-проба отвечает на вопрос «сервер жив?». Закрытый порт выдаёт себя
     *    ICMP «port unreachable» → [PortUnreachableException] → -1. Молчание
     *    означает, что порт открыт и слушается;
     *  - величину задержки даёт [icmpPing] до того же хоста.
     *
     * Если ICMP в сети зарезан (частая история на мобильных операторах), но
     * сервер жив, отдаём [UDP_ALIVE_FALLBACK_MS]: без этого живой сервер
     * выглядел бы недоступным.
     */
    private fun udpPing(address: String, port: Int): Int {
        val resolved = runCatching { InetAddress.getByName(address) }.getOrNull() ?: return -1
        if (!isUdpPortAlive(resolved, port)) return -1
        val icmp = icmpPing(address)
        return if (icmp >= 0) icmp else UDP_ALIVE_FALLBACK_MS
    }

    /**
     * Жив ли UDP-порт: шлём датаграмму и ждём ICMP-отказа.
     * true — отказа не было (порт открыт либо фильтруется), false — порт закрыт.
     */
    private fun isUdpPortAlive(address: InetAddress, port: Int): Boolean = runCatching {
        DatagramSocket().use { socket ->
            // connect() обязателен: только на «подключённом» UDP-сокете Java
            // доставляет ICMP-ошибку как PortUnreachableException.
            socket.connect(address, port)
            socket.soTimeout = UDP_TIMEOUT_MS
            // Случайные байты: валидный QUIC Initial слать незачем — важен не
            // ответ, а отсутствие ICMP-отказа.
            val probe = ByteArray(UDP_PROBE_SIZE).also { bytes -> Random.nextBytes(bytes) }
            socket.send(DatagramPacket(probe, probe.size))
            try {
                socket.receive(DatagramPacket(ByteArray(UDP_RECV_BUFFER), UDP_RECV_BUFFER))
            } catch (_: SocketTimeoutException) {
                // Штатное молчание сервера.
            }
            true
        }
    }.getOrDefault(false)

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

        /**
         * Ожидание после UDP-пробы: столько ждём ICMP «port unreachable».
         * Ответа по существу не будет (сервер молчит намеренно), поэтому время
         * короткое — оно лишь даёт отказу дойти и не подвешивает список.
         */
        const val UDP_TIMEOUT_MS = 700
        /** Размер пробной датаграммы — как у типичного QUIC Initial. */
        const val UDP_PROBE_SIZE = 64
        /** Размер буфера приёма пробы (хватает на любой ICMP-ответ). */
        const val UDP_RECV_BUFFER = 1500
        /**
         * Что показать для живого Hysteria2-сервера, когда ICMP зарезан.
         * Значение заведомо «среднее»: сказать «сервер доступен» важнее, чем
         * показать прочерк, но выдавать точную цифру нам неоткуда.
         */
        const val UDP_ALIVE_FALLBACK_MS = 999
    }
}
