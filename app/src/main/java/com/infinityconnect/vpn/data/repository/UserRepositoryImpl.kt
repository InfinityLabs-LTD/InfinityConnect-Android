package com.infinityconnect.vpn.data.repository

import com.infinityconnect.vpn.data.remote.api.InfinityApi
import com.infinityconnect.vpn.domain.model.AppResult
import com.infinityconnect.vpn.domain.model.SubscriptionInfo
import com.infinityconnect.vpn.domain.model.UserInfo
import com.infinityconnect.vpn.domain.model.map
import com.infinityconnect.vpn.domain.repository.UserRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UserRepositoryImpl @Inject constructor(
    private val api: InfinityApi,
) : UserRepository {

    override suspend fun userInfo(): AppResult<UserInfo> =
        safeApiCall { api.userInfo() }.map { it.toDomain() }

    override suspend fun subscriptionInfo(): AppResult<SubscriptionInfo> =
        safeApiCall { api.subscriptionInfo() }.map { it.toDomain() }
}
