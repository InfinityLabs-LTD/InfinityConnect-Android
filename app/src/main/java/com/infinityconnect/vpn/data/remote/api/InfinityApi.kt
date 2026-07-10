package com.infinityconnect.vpn.data.remote.api

import com.infinityconnect.vpn.data.remote.dto.ConfigDto
import com.infinityconnect.vpn.data.remote.dto.KeyDto
import com.infinityconnect.vpn.data.remote.dto.KeysResponseDto
import com.infinityconnect.vpn.data.remote.dto.LoginRequestDto
import com.infinityconnect.vpn.data.remote.dto.RefreshRequestDto
import com.infinityconnect.vpn.data.remote.dto.ServersResponseDto
import com.infinityconnect.vpn.data.remote.dto.SubscriptionInfoDto
import com.infinityconnect.vpn.data.remote.dto.TokenResponseDto
import com.infinityconnect.vpn.data.remote.dto.UserInfoDto
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * Основной REST API Infinity Connect (относительно api_base_url из discovery).
 *
 * Пути заданы относительными (без ведущего слэша), т.к. базовый URL Retrofit —
 * `.../v1/`. Авторизация подставляется [AuthInterceptor]; обновление токена
 * при 401 — [TokenAuthenticator]. Эндпоинты авторизации от авто-Bearer исключаются
 * по логике интерцептора (см. этап network-модуля).
 */
interface InfinityApi {

    // --- Авторизация ---

    @POST("auth/login")
    suspend fun login(@Body body: LoginRequestDto): TokenResponseDto

    @POST("auth/refresh")
    suspend fun refresh(@Body body: RefreshRequestDto): TokenResponseDto

    @POST("auth/logout")
    suspend fun logout(): Response<Unit>

    // --- Аккаунт и подписка ---

    @GET("user/info")
    suspend fun userInfo(): UserInfoDto

    @GET("user/subscription")
    suspend fun subscriptionInfo(): SubscriptionInfoDto

    // --- Ключи (автоимпорт подписок) ---

    @GET("keys")
    suspend fun keys(): KeysResponseDto

    @GET("keys/{id}")
    suspend fun key(@Path("id") id: Long): KeyDto

    // --- Конфигурация подключения ---

    @GET("config/servers")
    suspend fun servers(@Query("key_id") keyId: Long): ServersResponseDto

    @GET("config")
    suspend fun config(
        @Query("key_id") keyId: Long,
        @Query("server") serverIndex: Int,
    ): ConfigDto
}
