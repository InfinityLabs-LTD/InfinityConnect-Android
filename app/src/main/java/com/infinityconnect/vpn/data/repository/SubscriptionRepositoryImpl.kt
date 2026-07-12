package com.infinityconnect.vpn.data.repository

import com.infinityconnect.vpn.data.local.DeviceIdProvider
import com.infinityconnect.vpn.data.local.SubscriptionCacheStore
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
    private val cacheStore: SubscriptionCacheStore,
) : SubscriptionRepository {

    // Кэш тел подписок по URL. Живёт на процесс (Singleton) — серверы
    // предзагружаются один раз при авторизации/refresh и переиспользуются.
    // Персистентная копия — на диске ([cacheStore]): переживает перезапуск,
    // поэтому серверы доступны офлайн даже после закрытия приложения.
    private val cache = ConcurrentHashMap<String, SubscriptionBody>()

    /** Достаёт тело из памяти, а при промахе — подгружает с диска в память. */
    private fun cached(url: String): SubscriptionBody? =
        cache[url] ?: cacheStore.load(url)?.also { cache[url] = it }

    // Сериализуем сетевые загрузки одного URL, чтобы параллельные selectKey не
    // порождали дублирующие запросы.
    private val fetchMutex = Mutex()

    override fun isFresh(subscriptionUrl: String): Boolean {
        val body = cached(subscriptionUrl) ?: return false
        return !isStale(body)
    }

    override suspend fun fetch(
        subscriptionUrl: String,
        forceRefresh: Boolean,
    ): AppResult<SubscriptionBody> {
        if (subscriptionUrl.isBlank()) {
            return AppResult.Failure(AppError.Parse("Пустой subscription_url"))
        }

        // Свежий кэш (память или диск) — отдаём без сети (если не форсируем).
        cached(subscriptionUrl)?.let { body ->
            if (!forceRefresh && !isStale(body)) {
                return AppResult.Success(body)
            }
        }

        return fetchMutex.withLock {
            // Повторная проверка под локом: другой корутин мог уже загрузить.
            cached(subscriptionUrl)?.let { body ->
                if (!forceRefresh && !isStale(body)) {
                    return@withLock AppResult.Success(body)
                }
            }

            when (val result = fetchFromNetwork(subscriptionUrl)) {
                is AppResult.Success -> {
                    cache[subscriptionUrl] = result.data
                    // Персистим на диск — для офлайн-старта после перезапуска.
                    cacheStore.save(subscriptionUrl, result.data)
                    AppResult.Success(result.data)
                }
                is AppResult.Failure -> {
                    // Сеть недоступна, но есть кэш (память/диск) — отдаём его,
                    // даже если он устарел: лучше показать серверы, чем пустоту.
                    cached(subscriptionUrl)?.let { return@withLock AppResult.Success(it) }
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
