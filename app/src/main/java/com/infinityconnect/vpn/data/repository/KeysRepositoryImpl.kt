package com.infinityconnect.vpn.data.repository

import com.infinityconnect.vpn.data.remote.api.InfinityApi
import com.infinityconnect.vpn.domain.model.AppResult
import com.infinityconnect.vpn.domain.model.VpnKey
import com.infinityconnect.vpn.domain.model.map
import com.infinityconnect.vpn.domain.repository.KeysRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Ключи пользователя (автоимпорт подписок). Держит кэш в [keys], который
 * обновляется при [sync] — вызывается после логина, при старте и pull-to-refresh.
 */
@Singleton
class KeysRepositoryImpl @Inject constructor(
    private val api: InfinityApi,
) : KeysRepository {

    private val cache = MutableStateFlow<List<VpnKey>>(emptyList())
    override val keys: Flow<List<VpnKey>> = cache.asStateFlow()

    override suspend fun sync(): AppResult<List<VpnKey>> {
        val result = safeApiCall { api.keys() }
            .map { dto -> dto.keys.map { it.toDomain() } }
        if (result is AppResult.Success) {
            cache.value = result.data
        }
        return result
    }

    override suspend fun key(id: Long): AppResult<VpnKey> =
        safeApiCall { api.key(id) }.map { it.toDomain() }
}
