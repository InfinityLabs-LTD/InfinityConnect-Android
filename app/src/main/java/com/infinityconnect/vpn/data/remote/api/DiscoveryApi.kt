package com.infinityconnect.vpn.data.remote.api

import com.infinityconnect.vpn.data.remote.dto.DiscoveryDto
import retrofit2.http.GET
import retrofit2.http.Url

/**
 * Публичный discovery-эндпоинт (без авторизации).
 *
 * Вызывается по абсолютному URL `https://<domain>/v1/discovery`, поэтому
 * используем @Url — базовый адрес Retrofit тут не задействован.
 */
interface DiscoveryApi {
    @GET
    suspend fun discover(@Url discoveryUrl: String): DiscoveryDto
}
