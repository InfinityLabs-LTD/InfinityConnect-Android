package com.infinityconnect.vpn.data.remote

import okhttp3.Authenticator
import okhttp3.Request
import okhttp3.Response
import okhttp3.Route

/**
 * Обрабатывает ответ 401 на защищённых запросах: пытается обновить токены
 * через refresh и повторяет исходный запрос с новым access-токеном.
 *
 * OkHttp вызывает Authenticator автоматически при 401 и сам повторяет запрос,
 * если вернуть не-null Request. Если refresh не удался (истёк/ошибка) —
 * возвращаем null: запрос завершится 401, а токены будут очищены (разлогин).
 */
class TokenAuthenticator(
    private val tokenProvider: TokenProvider,
) : Authenticator {

    override fun authenticate(route: Route?, response: Response): Request? {
        // Не пытаемся рефрешить сам refresh-запрос — иначе цикл.
        if (response.request.url.encodedPath.endsWith("/auth/refresh")) {
            return null
        }

        // Защита от бесконечного повтора: не более одной попытки на запрос.
        if (responseCount(response) >= 2) {
            return null
        }

        val currentAccess = tokenProvider.accessToken()
        synchronized(this) {
            // Возможно, параллельный запрос уже обновил токен — проверим.
            val latest = tokenProvider.accessToken()
            val newAccess = if (latest != null && latest != currentAccess) {
                latest
            } else {
                tokenProvider.refreshTokensBlocking()
            }

            if (newAccess.isNullOrBlank()) {
                // Разлогиниваем ТОЛЬКО если сервер явно отверг refresh (401/403).
                // При сетевом сбое или 5xx токен ещё жив — молча отдаём 401 на
                // текущий запрос, сессия переживёт временную недоступность.
                if (tokenProvider.lastRefreshRejected()) {
                    tokenProvider.clear()
                }
                return null
            }

            return response.request.newBuilder()
                .header("Authorization", "Bearer $newAccess")
                .build()
        }
    }

    private fun responseCount(response: Response): Int {
        var count = 1
        var prior = response.priorResponse
        while (prior != null) {
            count++
            prior = prior.priorResponse
        }
        return count
    }
}
