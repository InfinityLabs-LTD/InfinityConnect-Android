package com.infinityconnect.vpn.data.repository

import com.infinityconnect.vpn.data.remote.api.InfinityApi
import com.infinityconnect.vpn.data.remote.dto.ConfigDto
import com.infinityconnect.vpn.domain.model.AppResult
import com.infinityconnect.vpn.domain.model.ServerEntry
import com.infinityconnect.vpn.domain.model.map
import com.infinityconnect.vpn.domain.repository.ConfigRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ConfigRepositoryImpl @Inject constructor(
    private val api: InfinityApi,
) : ConfigRepository {

    override suspend fun servers(keyId: Long): AppResult<List<ServerEntry>> =
        safeApiCall { api.servers(keyId) }
            .map { dto -> dto.servers.map { it.toDomain() } }

    override suspend fun config(keyId: Long, serverIndex: Int): AppResult<ConfigDto> =
        safeApiCall { api.config(keyId, serverIndex) }
}
