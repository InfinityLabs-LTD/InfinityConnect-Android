package com.infinityconnect.vpn.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Ответ GET /v1/user/info — данные аккаунта и статус подписки. */
@Serializable
data class UserInfoDto(
    @SerialName("user_id") val userId: Long? = null,
    @SerialName("username") val username: String? = null,
    @SerialName("email") val email: String? = null,
    @SerialName("is_subscription_active") val isSubscriptionActive: Boolean = false,
    @SerialName("subscription_expires_at") val subscriptionExpiresAt: String? = null,
    @SerialName("plan_name") val planName: String? = null,
)

/** Ответ GET /v1/user/subscription — агрегированные данные по подписке. */
@Serializable
data class SubscriptionInfoDto(
    @SerialName("is_active") val isActive: Boolean = false,
    @SerialName("keys_count") val keysCount: Int = 0,
    @SerialName("latest_expiry") val latestExpiry: String? = null,
    // Наименьшая дата окончания среди активных ключей — реальный «ближайший» срок.
    @SerialName("earliest_expiry") val earliestExpiry: String? = null,
    @SerialName("total_spent") val totalSpent: Double? = null,
    @SerialName("total_months") val totalMonths: Int? = null,
)
