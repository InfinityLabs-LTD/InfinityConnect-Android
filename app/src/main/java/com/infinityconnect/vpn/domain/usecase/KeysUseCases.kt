package com.infinityconnect.vpn.domain.usecase

import com.infinityconnect.vpn.domain.model.AppResult
import com.infinityconnect.vpn.domain.model.ServerEntry
import com.infinityconnect.vpn.domain.model.VpnKey
import com.infinityconnect.vpn.domain.repository.ConfigRepository
import com.infinityconnect.vpn.domain.repository.KeysRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/** Наблюдение за кэшем ключей (для списка на главном экране). */
class ObserveKeysUseCase @Inject constructor(
    private val keysRepository: KeysRepository,
) {
    operator fun invoke(): Flow<List<VpnKey>> = keysRepository.keys
}

/** Синхронизация ключей (/v1/keys): старт, pull-to-refresh, после логина. */
class SyncKeysUseCase @Inject constructor(
    private val keysRepository: KeysRepository,
) {
    suspend operator fun invoke(): AppResult<List<VpnKey>> = keysRepository.sync()
}

/** Список серверов выбранного ключа (для переключения локации). */
class GetServersUseCase @Inject constructor(
    private val configRepository: ConfigRepository,
) {
    suspend operator fun invoke(keyId: Long): AppResult<List<ServerEntry>> =
        configRepository.servers(keyId)
}
