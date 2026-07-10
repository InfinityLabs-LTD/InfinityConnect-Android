package com.infinityconnect.vpn.data.remote

import okhttp3.Interceptor
import okhttp3.Response

/**
 * Подставляет заголовок `Authorization: Bearer <access>` во все защищённые
 * запросы. Эндпоинты авторизации (login/refresh) исключаются — им токен не
 * нужен, а на refresh он ещё и мешал бы (используется тело с refresh_token).
 */
class AuthInterceptor(
    private val tokenProvider: TokenProvider,
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()

        // Пропускаем auth-эндпоинты без Bearer.
        val path = request.url.encodedPath
        if (path.endsWith("/auth/login") || path.endsWith("/auth/refresh")) {
            return chain.proceed(request)
        }

        val token = tokenProvider.accessToken()
        val authorized = if (token.isNullOrBlank()) {
            request
        } else {
            request.newBuilder()
                .header("Authorization", "Bearer $token")
                .build()
        }
        return chain.proceed(authorized)
    }
}
