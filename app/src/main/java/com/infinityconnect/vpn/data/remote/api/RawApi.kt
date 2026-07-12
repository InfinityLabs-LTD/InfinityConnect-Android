package com.infinityconnect.vpn.data.remote.api

import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.HeaderMap
import retrofit2.http.Url

/**
 * Загрузка произвольного URL как сырого тела (подписка Remnawave).
 *
 * Заголовки (HeaderMap) обязательны: панель отдаёт реальные конфиги только при
 * запросе с User-Agent Happ + HWID (x-hwid, x-device-os, x-ver-os,
 * x-device-model). Без них возвращается заглушка. Тело может быть base64,
 * списком URI или JSON-массивом конфигов Xray.
 *
 * Возвращаем полный [Response], чтобы прочитать заголовок
 * `profile-update-interval` (интервал обновления подписки в часах, из Remnawave).
 */
interface RawApi {
    @GET
    suspend fun getRaw(
        @Url url: String,
        @HeaderMap headers: Map<String, String>,
    ): Response<ResponseBody>
}
