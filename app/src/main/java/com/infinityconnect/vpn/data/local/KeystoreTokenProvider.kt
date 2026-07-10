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

    override fun refreshTokensBlocking(): String? {
        val refresh = storage.refreshToken?.takeIf { it.isNotBlank() } ?: return null
        val base = baseUrlProvider.get() ?: return null

        // Собираем URL refresh-эндпоинта: <api_base>/auth/refresh.
        val url = base.newBuilder().addPathSegments("auth/refresh").build()

        val bodyJson = json.encodeToString(RefreshRequestDto(refresh))
        val request = Request.Builder()
            .url(url)
            .post(bodyJson.toRequestBody(JSON_MEDIA))
            .build()

        return runCatching {
            refreshClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return null
                val payload = response.body?.string() ?: return null
                val tokens = json.decodeFromString<TokenResponseDto>(payload)
                storage.save(tokens.accessToken, tokens.refreshToken)
                tokens.accessToken
            }
        }.getOrNull()
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
