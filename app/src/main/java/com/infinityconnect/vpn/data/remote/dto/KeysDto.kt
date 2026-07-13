package com.infinityconnect.vpn.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Ответ GET /v1/keys — список ключей (подписок) пользователя. */
@Serializable
data class KeysResponseDto(
    @SerialName("keys") val keys: List<KeyDto> = emptyList(),
)

/**
 * Один ключ (подписка Remnawave). Также возвращается из GET /v1/keys/{id}.
 *
 * subscription_url в спецификации API прямо не перечислен, но именно он —
 * первичный источник конфигов для XHTTP/Hysteria2 (парсинг на клиенте).
 * Поэтому принимаем его опционально под несколькими возможными именами.
 */
@Serializable
data class KeyDto(
    @SerialName("id") val id: Long,
    @SerialName("name") val name: String? = null,
    @SerialName("server_address") val serverAddress: String? = null,
    @SerialName("location") val location: String? = null,
    @SerialName("country_flag") val countryFlag: String? = null,
    @SerialName("is_active") val isActive: Boolean = false,
    @SerialName("expires_at") val expiresAt: String? = null,
    @SerialName("used_traffic_bytes") val usedTrafficBytes: Long? = null,
    @SerialName("traffic_limit_bytes") val trafficLimitBytes: Long? = null,
    @SerialName("protocol") val protocol: String? = null,
    @SerialName("subscription_url") val subscriptionUrl: String? = null,
    /**
     * Премиум-ключ: его сервер (host_name) — премиум-хост (обслуживает план с
     * is_premium=1). Опционально: старые версии API не отдают → false.
     */
    @SerialName("is_premium") val isPremium: Boolean = false,
    /**
     * Статус подписки Remnawave: ACTIVE / EXPIRED / DISABLED / LIMITED.
     * Опционально: старые версии API не отдают → клиент выводит статус сам.
     */
    @SerialName("status") val status: String? = null,
    /**
     * Лимит устройств (HWID) Remnawave и число подключённых. Принимаем под
     * несколькими возможными именами, опционально.
     */
    @SerialName("device_limit") val deviceLimit: Int? = null,
    @SerialName("hwid_device_limit") val hwidDeviceLimit: Int? = null,
    @SerialName("devices_used") val devicesUsed: Int? = null,
    @SerialName("hwid_devices_used") val hwidDevicesUsed: Int? = null,
)
