package com.infinityconnect.vpn.data.repository

import com.infinityconnect.vpn.data.local.DeviceIdProvider
import com.infinityconnect.vpn.data.remote.api.RawApi
import com.infinityconnect.vpn.domain.model.AppError
import com.infinityconnect.vpn.domain.model.AppResult
import com.infinityconnect.vpn.domain.repository.SubscriptionBody
import com.infinityconnect.vpn.domain.repository.SubscriptionRepository
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SubscriptionRepositoryImpl @Inject constructor(
    private val rawApi: RawApi,
    private val deviceIdProvider: DeviceIdProvider,
) : SubscriptionRepository {

    // Кэш тел подписок по URL. Живёт на процесс (Singleton) — серверы
    // предзагружаются один раз при авторизации/refresh и переиспользуются.
    private val cache = ConcurrentHashMap<String, SubscriptionBody>()

    // Сериализуем сетевые загрузки одного URL, чтобы параллельные selectKey не
    // порождали дублирующие запросы.
    private val fetchMutex = Mutex()

    override fun isFresh(subscriptionUrl: String): Boolean {
        val cached = cache[subscriptionUrl] ?: return false
        return !isStale(cached)
    }

    override suspend fun fetch(
        subscriptionUrl: String,
        forceRefresh: Boolean,
    ): AppResult<SubscriptionBody> {
        if (subscriptionUrl.isBlank()) {
            return AppResult.Failure(AppError.Parse("Пустой subscription_url"))
        }

        // Свежий кэш — отдаём без сети (если не форсируем обновление).
        cache[subscriptionUrl]?.let { cached ->
            if (!forceRefresh && !isStale(cached)) {
                return AppResult.Success(cached)
            }
        }

        return fetchMutex.withLock {
            // Повторная проверка под локом: другой корутин мог уже загрузить.
            cache[subscriptionUrl]?.let { cached ->
                if (!forceRefresh && !isStale(cached)) {
                    return@withLock AppResult.Success(cached)
                }
            }

            when (val result = fetchFromNetwork(subscriptionUrl)) {
                is AppResult.Success -> {
                    cache[subscriptionUrl] = result.data
                    AppResult.Success(result.data)
                }
                is AppResult.Failure -> {
                    // Сеть недоступна, но есть кэш — отдаём его, чтобы не ломать UI.
                    cache[subscriptionUrl]?.let { return@withLock AppResult.Success(it) }
                    result
                }
            }
        }
    }

    private suspend fun fetchFromNetwork(url: String): AppResult<SubscriptionBody> {
        // Заголовки клиента Happ + HWID — иначе панель отдаёт заглушку.
        val headers = mapOf(
            "User-Agent" to deviceIdProvider.userAgent,
            "x-hwid" to deviceIdProvider.hwid,
            "x-device-os" to deviceIdProvider.deviceOs,
            "x-ver-os" to deviceIdProvider.osVersion,
            "x-device-model" to deviceIdProvider.deviceModel,
        )
        return safeApiCall {
            val response = rawApi.getRaw(url, headers)
            val body = response.body()?.string().orEmpty()
            val interval = response.headers()["profile-update-interval"]
                ?.trim()?.toIntOrNull()
                ?.takeIf { it in 1..168 } // разумные границы: 1ч..7д
                ?: DEFAULT_UPDATE_INTERVAL_HOURS
            SubscriptionBody(
                raw = body,
                fetchedAtMs = System.currentTimeMillis(),
                updateIntervalHours = interval,
            )
        }
    }

    private fun isStale(body: SubscriptionBody): Boolean {
        val ageMs = System.currentTimeMillis() - body.fetchedAtMs
        return ageMs >= body.updateIntervalHours * HOUR_MS
    }

    private companion object {
        const val DEFAULT_UPDATE_INTERVAL_HOURS = 12
        const val HOUR_MS = 60L * 60L * 1000L
    }
}
