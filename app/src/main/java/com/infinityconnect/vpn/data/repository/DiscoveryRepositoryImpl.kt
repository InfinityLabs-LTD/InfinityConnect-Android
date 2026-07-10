package com.infinityconnect.vpn.data.repository

import com.infinityconnect.vpn.data.local.SettingsStore
import com.infinityconnect.vpn.data.remote.ApiBaseUrlProvider
import com.infinityconnect.vpn.data.remote.api.DiscoveryApi
import com.infinityconnect.vpn.domain.model.AppError
import com.infinityconnect.vpn.domain.model.AppResult
import com.infinityconnect.vpn.domain.model.Discovery
import com.infinityconnect.vpn.domain.repository.DiscoveryRepository
import kotlinx.coroutines.flow.Flow
import java.util.concurrent.atomic.AtomicReference
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Discovery по домену сервера. Нормализует домен, строит URL /v1/discovery,
 * сохраняет ответ и активирует api_base_url в сетевом слое.
 */
@Singleton
class DiscoveryRepositoryImpl @Inject constructor(
    private val discoveryApi: DiscoveryApi,
    private val settings: SettingsStore,
    private val baseUrlProvider: ApiBaseUrlProvider,
) : DiscoveryRepository {

    private val cachedDiscovery = AtomicReference<Discovery?>(null)

    override fun savedDomain(): Flow<String?> = settings.domain

    override suspend fun discover(domain: String): AppResult<Discovery> {
        val url = buildDiscoveryUrl(domain)
        return when (val result = safeApiCall { discoveryApi.discover(url) }) {
            is AppResult.Success -> {
                val dto = result.data
                if (dto.apiBaseUrl.isBlank()) {
                    return AppResult.Failure(AppError.Parse("Пустой api_base_url в discovery"))
                }
                // Сохраняем и активируем.
                settings.saveDomain(normalizeDomain(domain))
                settings.saveDiscovery(dto)
                baseUrlProvider.set(dto.apiBaseUrl)
                val discovery = dto.toDomain().also { cachedDiscovery.set(it) }
                AppResult.Success(discovery)
            }
            is AppResult.Failure -> result
        }
    }

    override suspend fun restore(): AppResult<Discovery> {
        val dto = settings.currentDiscovery()
            ?: return AppResult.Failure(AppError.Parse("Нет сохранённого discovery"))
        baseUrlProvider.set(dto.apiBaseUrl)
        val discovery = dto.toDomain().also { cachedDiscovery.set(it) }
        return AppResult.Success(discovery)
    }

    override fun cached(): Discovery? = cachedDiscovery.get()

    /** Приводит ввод пользователя к схеме https и хосту без хвостового слэша. */
    private fun normalizeDomain(input: String): String {
        var d = input.trim()
        d = d.removeSuffix("/")
        if (!d.startsWith("http://") && !d.startsWith("https://")) {
            d = "https://$d"
        }
        return d
    }

    private fun buildDiscoveryUrl(input: String): String =
        "${normalizeDomain(input)}/v1/discovery"
}
