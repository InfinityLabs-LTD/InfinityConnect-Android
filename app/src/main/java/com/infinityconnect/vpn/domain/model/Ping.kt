package com.infinityconnect.vpn.domain.model

/**
 * Метод измерения пинга серверов.
 *
 * У каждого метода свой смысл задержки, поэтому и цвет пинга в UI зависит от
 * метода (HTTP-методы дают бОльшие значения, чем TCP/ICMP).
 */
enum class PingMethod {
    /**
     * «Реальный» пинг: HTTP-запрос к тест-URL, проведённый ЧЕРЕЗ сам протокол
     * сервера ядром Xray (Libv2ray.measureOutboundDelay) — как в v2RayTun/Happ.
     * Меряет end-to-end задержку через VLESS/Reality, а не голый TCP до хоста.
     * Для Hysteria2 (другое ядро) откатывается на TCP.
     */
    REAL,

    /** HTTP(S) GET через прокси-сервер до тест-URL: полный ответ (реальная задержка сайта). */
    PROXY_GET,

    /** HTTP(S) HEAD через прокси: только заголовки, легче GET. */
    PROXY_HEAD,

    /** TCP-хендшейк до host:port сервера: чистая задержка до узла. */
    TCP,

    /** ICMP echo (ping) до адреса сервера: сетевой RTT без TCP/TLS. */
    ICMP;

    companion object {
        fun from(raw: String?): PingMethod =
            entries.firstOrNull { it.name == raw } ?: TCP
    }
}

/**
 * Настройки измерения пинга.
 *
 * @param method активный метод.
 * @param testUrl URL для HTTP-методов (по умолчанию Cloudflare 204).
 */
data class PingSettings(
    val method: PingMethod = PingMethod.TCP,
    val testUrl: String = DEFAULT_TEST_URL,
) {
    companion object {
        /** Быстрый эндпоинт Cloudflare, отдающий 204 без тела. */
        const val DEFAULT_TEST_URL = "https://cp.cloudflare.com/generate_204"
    }
}
