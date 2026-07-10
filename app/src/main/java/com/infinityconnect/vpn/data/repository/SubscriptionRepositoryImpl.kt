package com.infinityconnect.vpn.data.repository

import com.infinityconnect.vpn.data.remote.api.RawApi
import com.infinityconnect.vpn.domain.model.AppError
import com.infinityconnect.vpn.domain.model.AppResult
import com.infinityconnect.vpn.domain.repository.SubscriptionRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SubscriptionRepositoryImpl @Inject constructor(
    private val rawApi: RawApi,
) : SubscriptionRepository {

    override suspend fun fetchRawSubscription(subscriptionUrl: String): AppResult<String> {
        if (subscriptionUrl.isBlank()) {
            return AppResult.Failure(AppError.Parse("Пустой subscription_url"))
        }
        return safeApiCall { rawApi.getRaw(subscriptionUrl).string() }
    }
}
