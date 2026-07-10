package com.infinityconnect.vpn.domain.engine

import com.infinityconnect.vpn.domain.model.VpnProtocol

/**
 * Разобранный профиль одного сервера подписки — вход для VPN-движка.
 *
 * Парсер подписки ([com.infinityconnect.vpn.domain.subscription.SubscriptionParser])
 * преобразует VLESS/hy2-URI в один из вариантов ниже. Движок выбирается по
 * [protocol]: VLESS → Xray, HYSTERIA2 → Hysteria2.
 */
sealed interface EngineConfig {
    /** Отображаемое имя сервера (remark из URI / имя из API). */
    val remark: String
    val address: String
    val port: Int
    val protocol: VpnProtocol

    /** Профиль VLESS (в т.ч. Reality и транспорт XHTTP) — движок Xray. */
    data class Vless(
        override val remark: String,
        override val address: String,
        override val port: Int,
        val uuid: String,
        val transport: Transport,
        val security: Security,
        val flow: String? = null,
    ) : EngineConfig {
        override val protocol: VpnProtocol get() = VpnProtocol.VLESS
    }

    /** Профиль Hysteria2 — движок Hysteria2. */
    data class Hysteria2(
        override val remark: String,
        override val address: String,
        override val port: Int,
        val auth: String,
        val sni: String? = null,
        val insecure: Boolean = false,
        val obfsPassword: String? = null,
        val upMbps: Int? = null,
        val downMbps: Int? = null,
    ) : EngineConfig {
        override val protocol: VpnProtocol get() = VpnProtocol.HYSTERIA2
    }
}

/** Транспорт VLESS. */
sealed interface Transport {
    /** TCP без надстроек (raw). */
    data object Tcp : Transport

    /** WebSocket. */
    data class Ws(val path: String?, val host: String?) : Transport

    /** gRPC. */
    data class Grpc(val serviceName: String?) : Transport

    /**
     * XHTTP (SplitHTTP) — современный транспорт Xray.
     * mode: auto | packet-up | stream-up | stream-one.
     */
    data class Xhttp(
        val path: String?,
        val host: String?,
        val mode: String?,
    ) : Transport
}

/** Слой безопасности VLESS. */
sealed interface Security {
    /** Без TLS (обычно только для отладки). */
    data object None : Security

    /** Классический TLS. */
    data class Tls(
        val sni: String?,
        val fingerprint: String?,
        val alpn: List<String>? = null,
        val allowInsecure: Boolean = false,
    ) : Security

    /** Reality. */
    data class Reality(
        val sni: String?,
        val fingerprint: String?,
        val publicKey: String,
        val shortId: String?,
        val spiderX: String? = null,
    ) : Security
}
