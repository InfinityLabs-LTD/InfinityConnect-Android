package com.infinityconnect.vpn.data.repository

import com.infinityconnect.vpn.data.local.DeviceIdProvider
import com.infinityconnect.vpn.data.remote.api.RawApi
import com.infinityconnect.vpn.domain.model.AppError
import com.infinityconnect.vpn.domain.model.AppResult
import com.infinityconnect.vpn.domain.repository.SubscriptionRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SubscriptionRepositoryImpl @Inject constructor(
    private val rawApi: RawApi,
    private val deviceIdProvider: DeviceIdProvider,
) : SubscriptionRepository {

    override suspend fun fetchRawSubscription(subscriptionUrl: String): AppResult<String> {
        if (subscriptionUrl.isBlank()) {
            return AppResult.Failure(AppError.Parse("Пустой subscription_url"))
        }
        // Заголовки клиента Happ + HWID — иначе панель отдаёт заглушку
        // «Приложение не поддерживается» вместо реальных конфигов.
        val headers = mapOf(
            "User-Agent" to deviceIdProvider.userAgent,
            "x-hwid" to deviceIdProvider.hwid,
            "x-device-os" to deviceIdProvider.deviceOs,
            "x-ver-os" to deviceIdProvider.osVersion,
            "x-device-model" to deviceIdProvider.deviceModel,
        )
        return safeApiCall { rawApi.getRaw(subscriptionUrl, headers).string() }
    }
}
