package com.infinityconnect.vpn.data.remote

import okhttp3.Interceptor
import okhttp3.Response
import java.io.IOException

/**
 * Подменяет схему/хост/порт/префикс пути запроса на актуальный api_base_url из
 * [ApiBaseUrlProvider]. Нужен потому, что фактический адрес API известен лишь
 * после discovery, а Retrofit создаётся с плейсхолдерным baseUrl.
 *
 * Относительные запросы [InfinityApi] получают префикс base-пути; запросы с
 * готовым абсолютным адресом (@Url — например, скачивание APK по ссылке из
 * ответа сервера) пропускаются как есть.
 */
class InfinityApiInterceptor(
    private val baseUrlProvider: ApiBaseUrlProvider,
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val base = baseUrlProvider.get()
            ?: throw IOException("api_base_url не задан: выполните discovery перед запросами")

        val original = chain.request()

        // Запрос уже адресован реальному API-хосту — значит это абсолютный
        // @Url из ответа сервера (ссылка на APK), и путь в нём полный.
        // Приклеивать base-префикс нельзя: получилось бы /v1/v1/... → 404,
        // из-за чего скачивание обновления падало с «Ошибка сервера (404)».
        if (original.url.host == base.host) {
            return chain.proceed(original)
        }

        // Префикс пути из base (например, "v1") + путь конкретного запроса
        // (например, "auth/login"). Плейсхолдерный хост Retrofit отбрасываем.
        val basePath = base.pathSegments.filter { it.isNotEmpty() }
        val requestPath = original.url.pathSegments.filter { it.isNotEmpty() }
        val encodedPath = "/" + (basePath + requestPath).joinToString("/")

        val newUrl = original.url.newBuilder()
            .scheme(base.scheme)
            .host(base.host)
            .port(base.port)
            .encodedPath(encodedPath)
            .build()

        val request = original.newBuilder().url(newUrl).build()
        return chain.proceed(request)
    }
}
