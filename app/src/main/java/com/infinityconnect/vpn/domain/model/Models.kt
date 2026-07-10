package com.infinityconnect.vpn.domain.model

/** Тип VPN-протокола ключа/сервера — определяет выбор движка. */
enum class VpnProtocol {
    VLESS,       // включая Reality и транспорт XHTTP — движок Xray
    HYSTERIA2,   // движок Hysteria2
    UNKNOWN;

    companion object {
        /** Разбор строкового обозначения протокола из API/подписки. */
        fun from(raw: String?): VpnProtocol = when (raw?.trim()?.lowercase()) {
            "vless" -> VLESS
            "hysteria2", "hy2", "hysteria" -> HYSTERIA2
            else -> UNKNOWN
        }
    }
}

/** Конфигурация discovery — что клиент узнал по домену сервера. */
data class Discovery(
    val apiBaseUrl: String,
    val siteUrl: String?,
    val registerUrl: String?,
    val forgotPasswordUrl: String?,
    val supportUrl: String?,
    val projectName: String?,
    val apiVersion: Int?,
    val trialEnabled: Boolean,
    val referralsEnabled: Boolean,
)

/** Данные аккаунта и статус подписки (GET /v1/user/info). */
data class UserInfo(
    val userId: Long?,
    val username: String?,
    val email: String?,
    val isSubscriptionActive: Boolean,
    val subscriptionExpiresAt: String?,
    val planName: String?,
)

/** Агрегированные данные по подписке (GET /v1/user/subscription). */
data class SubscriptionInfo(
    val isActive: Boolean,
    val keysCount: Int,
    val latestExpiry: String?,
    val totalSpent: Double?,
    val totalMonths: Int?,
)

/** Ключ (подписка) пользователя. */
data class VpnKey(
    val id: Long,
    val name: String,
    val serverAddress: String?,
    val location: String?,
    val countryFlag: String?,
    val isActive: Boolean,
    val expiresAt: String?,
    val usedTrafficBytes: Long?,
    val trafficLimitBytes: Long?,
    val protocol: VpnProtocol,
    val subscriptionUrl: String?,
)

/** Элемент списка серверов ключа (GET /v1/config/servers). */
data class ServerEntry(
    val index: Int,
    val name: String,
    val flag: String?,
    val serverAddress: String?,
)
