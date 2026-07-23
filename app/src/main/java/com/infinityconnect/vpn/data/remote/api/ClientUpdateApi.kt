package com.infinityconnect.vpn.data.remote.api

import com.infinityconnect.vpn.data.remote.dto.ClientUpdateDto
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Query
import retrofit2.http.Streaming
import retrofit2.http.Url

/**
 * API обновлений Android-клиента (та же система, что у Windows-клиента:
 * /v1/client-updates/*, см. серверный client_updates.py).
 *
 * Эндпоинты публичные (без Bearer) — обновление должно быть доступно и до
 * логина. Пути относительные от api_base_url (`.../v1/`), хост подставляет
 * [InfinityApiInterceptor]; AuthInterceptor Bearer сюда не добавляет —
 * лишний заголовок сервер игнорирует.
 */
interface ClientUpdateApi {

    /**
     * Проверка обновления. 200 → тело [ClientUpdateDto], 204 → актуально.
     * @param current  текущий versionName (semver)
     * @param code     текущий versionCode — первичный критерий сравнения
     */
    @GET("client-updates/android/apk/latest")
    suspend fun latest(
        @Query("current") current: String,
        @Query("code") code: Long,
    ): Response<ClientUpdateDto>

    /** Скачивание APK по абсолютному URL из ответа latest (стримингом, с поддержкой Range на сервере). */
    @Streaming
    @GET
    suspend fun download(@Url url: String): Response<ResponseBody>
}
