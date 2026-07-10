package com.infinityconnect.vpn.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Ответ публичного эндпоинта GET /v1/discovery.
 * Клиент знает только домен сервера; всё остальное узнаёт отсюда.
 */
@Serializable
data class DiscoveryDto(
    @SerialName("api_base_url") val apiBaseUrl: String,
    @SerialName("site_url") val siteUrl: String? = null,
    @SerialName("auth_endpoint") val authEndpoint: String? = null,
    @SerialName("refresh_endpoint") val refreshEndpoint: String? = null,
    @SerialName("keys_endpoint") val keysEndpoint: String? = null,
    @SerialName("register_url") val registerUrl: String? = null,
    @SerialName("forgot_password_url") val forgotPasswordUrl: String? = null,
    @SerialName("support_url") val supportUrl: String? = null,
    @SerialName("project_name") val projectName: String? = null,
    @SerialName("api_version") val apiVersion: Int? = null,
    @SerialName("features") val features: DiscoveryFeaturesDto? = null,
)

@Serializable
data class DiscoveryFeaturesDto(
    @SerialName("trial_enabled") val trialEnabled: Boolean = false,
    @SerialName("referrals_enabled") val referralsEnabled: Boolean = false,
)
