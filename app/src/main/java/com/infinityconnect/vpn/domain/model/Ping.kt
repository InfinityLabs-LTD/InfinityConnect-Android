package com.infinityconnect.vpn.domain.model

/**
 * Протокол измерения пинга серверов (как в Happ).
 *
 * У каждого метода свой смысл задержки, поэтому и цвет пинга в UI зависит от
 * метода (прокси-методы дают бОльшие значения, чем TCP/ICMP).
 */
enum class PingMethod {
    /**
     * HTTP(S) GET к тест-URL, проведённый ЧЕРЕЗ сам протокол сервера: ядро Xray
     * поднимает локальный SOCKS-inbound по профилю, запрос идёт через него.
     * Меряет end-to-end задержку через VLESS/Reality до тест-URL (полный ответ).
     * Для Hysteria2 (другое ядро) откатывается на TCP.
     */
    PROXY_GET,

    /** HTTP(S) HEAD через прокси-сервер: только заголовки, легче GET. */
    PROXY_HEAD,

    /** TCP-хендшейк до host:port сервера: чистая задержка до узла. */
    TCP,

    /** ICMP echo (ping) до адреса сервера: сетевой RTT без TCP/TLS. */
    ICMP;

    /** Метод идёт через прокси-сервер ядра (GET/HEAD) — в отличие от TCP/ICMP. */
    val isProxy: Boolean get() = this == PROXY_GET || this == PROXY_HEAD

    companion object {
        fun from(raw: String?): PingMethod =
            entries.firstOrNull { it.name == raw } ?: PROXY_GET
    }
}

/**
 * Режим прокси-пинга (via …) — как в Happ. Определяет, как ядро/клиент
 * выполняют замер в рамках инстанса ядра. Действует только для прокси-методов
 * ([PingMethod.isProxy]); для TCP/ICMP игнорируется.
 */
enum class PingMode {
    /**
     * Несколько независимых запросов, берётся лучший (минимальный) результат.
     * Сглаживает разовые всплески на прогреве.
     */
    DEFAULT,

    /**
     * Запрос выполняется дважды в рамках одной сессии ядра; замеряется второй
     * (первый — прогрев ядра/маршрута, его результат отбрасывается).
     */
    DOUBLE,

    /**
     * Два запроса по одному уже установленному TLS-соединению (keepalive);
     * второй меряет чистую скорость ответа без повторного TLS-хендшейка.
     * Даёт самые низкие «прокси»-значения.
     */
    KEEPALIVE;

    companion object {
        fun from(raw: String?): PingMode =
            entries.firstOrNull { it.name == raw } ?: DEFAULT
    }
}

/**
 * Настройки измерения пинга.
 *
 * @param method активный протокол (по умолчанию прокси-GET: меряет end-to-end
 *   задержку через сам протокол сервера, а не только доступность узла).
 * @param mode режим прокси-пинга (via …); влияет только на прокси-методы.
 * @param testUrl URL для прокси-методов (по умолчанию gstatic 204).
 * @param timeoutSec таймаут прокси-пинга в секундах (5..15).
 */
data class PingSettings(
    val method: PingMethod = PingMethod.PROXY_GET,
    val mode: PingMode = PingMode.DEFAULT,
    val testUrl: String = DEFAULT_TEST_URL,
    val timeoutSec: Int = DEFAULT_TIMEOUT_SEC,
) {
    /** Таймаут в мс с зажимом в допустимый диапазон. */
    val timeoutMs: Int get() = timeoutSec.coerceIn(MIN_TIMEOUT_SEC, MAX_TIMEOUT_SEC) * 1000

    companion object {
        /** Быстрый эндпоинт, отдающий 204 без тела (как в Happ). */
        const val DEFAULT_TEST_URL = "https://www.gstatic.com/generate_204"
        const val MIN_TIMEOUT_SEC = 5
        const val MAX_TIMEOUT_SEC = 15
        const val DEFAULT_TIMEOUT_SEC = 7
    }
}
