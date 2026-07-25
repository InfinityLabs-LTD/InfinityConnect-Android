package com.infinityconnect.vpn.data.local

import com.infinityconnect.vpn.data.remote.ApiBaseUrlProvider
import com.infinityconnect.vpn.data.remote.TokenProvider
import com.infinityconnect.vpn.data.remote.dto.RefreshRequestDto
import com.infinityconnect.vpn.data.remote.dto.TokenResponseDto
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

/**
 * Реализация [TokenProvider] поверх [TokenStorage]. Токены читаются/пишутся
 * синхронно (Keystore), а обновление выполняется прямым блокирующим вызовом
 * refresh-эндпоинта.
 *
 * Важно: refresh идёт через ОТДЕЛЬНЫЙ OkHttp-клиент (без Authenticator/Bearer),
 * чтобы не создавать рекурсию с сетевым слоем InfinityApi, который сам
 * зависит от TokenProvider.
 */
@Singleton
class KeystoreTokenProvider @Inject constructor(
    private val storage: TokenStorage,
    private val baseUrlProvider: ApiBaseUrlProvider,
    @Named("discovery") private val refreshClient: OkHttpClient,
    private val json: Json,
    private val sessionState: SessionState,
) : TokenProvider {

    override fun accessToken(): String? = storage.accessToken

    override fun refreshToken(): String? = storage.refreshToken

    /**
     * Отверг ли сервер refresh-токен при последней попытке (401/403).
     * Сетевые сбои и 5xx сюда не попадают — иначе временная недоступность
     * сервера выкидывала бы пользователя из аккаунта.
     */
    @Volatile
    private var refreshRejected = false

    override fun lastRefreshRejected(): Boolean = refreshRejected

    override fun refreshTokensBlocking(): String? {
        val refresh = storage.refreshToken?.takeIf { it.isNotBlank() } ?: run {
            // Токена нет вовсе — восстанавливать нечего, это честный разлогин.
            refreshRejected = true
            return null
        }
        val base = baseUrlProvider.get() ?: run {
            // Discovery ещё не поднялся — не трогаем сессию.
            refreshRejected = false
            return null
        }

        // Собираем URL refresh-эндпоинта: <api_base>/auth/refresh.
        val url = base.newBuilder().addPathSegments("auth/refresh").build()

        val bodyJson = json.encodeToString(RefreshRequestDto(refresh))
        val request = Request.Builder()
            .url(url)
            .post(bodyJson.toRequestBody(JSON_MEDIA))
            .build()

        return runCatching {
            refreshClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    // Отвергнут токен только при 401/403. 5xx и прочее —
                    // проблема сервера: сессию сохраняем, повторим позже.
                    refreshRejected = response.code == 401 || response.code == 403
                    return null
                }
                val payload = response.body?.string() ?: run {
                    refreshRejected = false
                    return null
                }
                val tokens = json.decodeFromString<TokenResponseDto>(payload)
                storage.save(tokens.accessToken, tokens.refreshToken)
                refreshRejected = false
                tokens.accessToken
            }
        }.getOrElse {
            // Таймаут, обрыв, DNS — сеть, а не отказ в доступе.
            refreshRejected = false
            null
        }
    }

    override fun clear() {
        storage.clear()
        // Уведомляем навигацию: refresh истёк → требуется повторный вход.
        sessionState.setLoggedIn(false)
    }

    private companion object {
        val JSON_MEDIA = "application/json".toMediaType()
    }
}
