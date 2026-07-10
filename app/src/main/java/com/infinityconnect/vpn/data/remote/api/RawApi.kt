package com.infinityconnect.vpn.data.remote.api

import okhttp3.ResponseBody
import retrofit2.http.GET
import retrofit2.http.Url

/**
 * Загрузка произвольного URL как сырого тела (подписка Remnawave).
 * Тело возвращается строкой (base64 или список URI) для клиентского парсинга.
 */
interface RawApi {
    @GET
    suspend fun getRaw(@Url url: String): ResponseBody
}
