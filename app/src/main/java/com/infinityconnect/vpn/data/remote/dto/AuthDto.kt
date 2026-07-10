package com.infinityconnect.vpn.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Тело запроса POST /v1/auth/login — логин/пароль веб-аккаунта. */
@Serializable
data class LoginRequestDto(
    @SerialName("login") val login: String,
    @SerialName("password") val password: String,
)

/** Тело запроса POST /v1/auth/refresh. */
@Serializable
data class RefreshRequestDto(
    @SerialName("refresh_token") val refreshToken: String,
)

/**
 * Ответ login/refresh: пара HMAC-подписанных токенов.
 * access живёт ~1 час, refresh ~30 дней.
 */
@Serializable
data class TokenResponseDto(
    @SerialName("access_token") val accessToken: String,
    @SerialName("refresh_token") val refreshToken: String,
    @SerialName("expires_in") val expiresIn: Long? = null,
    @SerialName("refresh_expires_in_days") val refreshExpiresInDays: Int? = null,
)
