package com.infinityconnect.vpn.data.remote

import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.util.concurrent.atomic.AtomicReference

/**
 * Хранит текущий базовый URL API (api_base_url из discovery), который
 * становится известен только в рантайме — после ввода домена и discovery.
 *
 * Retrofit требует какой-то baseUrl при создании, но фактический хост
 * подставляется динамически через [InfinityApiInterceptor], читающий это
 * значение. Так один экземпляр Retrofit обслуживает любой домен без
 * пересоздания графа зависимостей.
 */
class ApiBaseUrlProvider {

    private val current = AtomicReference<HttpUrl?>(null)

    /** Устанавливает базовый URL (например, "https://host/v1/"). */
    fun set(apiBaseUrl: String) {
        val normalized = if (apiBaseUrl.endsWith("/")) apiBaseUrl else "$apiBaseUrl/"
        current.set(normalized.toHttpUrlOrNull())
    }

    fun get(): HttpUrl? = current.get()

    fun clear() = current.set(null)
}
