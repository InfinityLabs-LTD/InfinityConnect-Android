package com.infinityconnect.vpn.data.repository

import com.infinityconnect.vpn.data.local.SettingsStore
import com.infinityconnect.vpn.data.remote.api.InfinityApi
import com.infinityconnect.vpn.data.remote.dto.KeysResponseDto
import com.infinityconnect.vpn.domain.model.AppError
import com.infinityconnect.vpn.domain.model.AppResult
import com.infinityconnect.vpn.domain.model.VpnKey
import com.infinityconnect.vpn.domain.model.map
import com.infinityconnect.vpn.domain.repository.KeysRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Ключи пользователя (автоимпорт подписок). Держит кэш в [keys], который
 * обновляется при [sync] — вызывается после логина, при старте и pull-to-refresh.
 *
 * Список ключей персистится в [SettingsStore] (сырой JSON ответа /v1/keys):
 * при холодном старте без сети кэш восстанавливается с диска, поэтому серверы
 * доступны офлайн (как в Happ/INCY). Подтягивается в [keys] при первом обращении.
 */
@Singleton
class KeysRepositoryImpl @Inject constructor(
    private val api: InfinityApi,
    private val settingsStore: SettingsStore,
    private val json: Json,
) : KeysRepository {

    private val cache = MutableStateFlow<List<VpnKey>>(emptyList())
    override val keys: Flow<List<VpnKey>> = cache.asStateFlow()

    /** Признак того, что диск-кэш уже пытались загрузить (чтобы не дёргать DataStore каждый раз). */
    @Volatile
    private var diskLoaded = false

    override suspend fun sync(): AppResult<List<VpnKey>> {
        // Мгновенный первый кадр: поднимаем диск-кэш ДО сетевого запроса,
        // чтобы после пересоздания процесса список ключей не ждал ответ сети
        // (иначе холодный TLS-handshake даёт 1–3 c «пустого» экрана с лоадером).
        loadFromDiskInto()
        val result = safeApiCall { api.keys() }
        return when (result) {
            is AppResult.Success -> {
                cache.value = result.data.keys.map { it.toDomain() }
                // Персистим сырой ответ для офлайн-старта.
                runCatching {
                    settingsStore.saveKeysJson(json.encodeToString(KeysResponseDto.serializer(), result.data))
                }
                AppResult.Success(cache.value)
            }
            is AppResult.Failure -> {
                // Сеть недоступна — поднимаем кэш с диска, чтобы отдать серверы офлайн.
                loadFromDiskInto()
                if (cache.value.isNotEmpty()) AppResult.Success(cache.value) else result
            }
        }
    }

    override suspend fun key(id: Long): AppResult<VpnKey> {
        // Офлайн-путь: сперва ищем ключ в кэше (память → диск). Онлайн-запрос
        // делаем только если ключа нет — иначе connect не строил бы конфиг без сети.
        cachedKey(id)?.let { return AppResult.Success(it) }
        return safeApiCall { api.key(id) }.map { it.toDomain() }
    }

    /** Ключ из кэша: сперва память, при промахе — подгружаем диск и ищем снова. */
    private suspend fun cachedKey(id: Long): VpnKey? {
        cache.value.firstOrNull { it.id == id }?.let { return it }
        loadFromDiskInto()
        return cache.value.firstOrNull { it.id == id }
    }

    /** Загружает список ключей с диска в [cache], если память пуста. */
    private suspend fun loadFromDiskInto() {
        if (cache.value.isNotEmpty() || diskLoaded) return
        diskLoaded = true
        val raw = runCatching { settingsStore.currentKeysJson() }.getOrNull() ?: return
        val dto = runCatching { json.decodeFromString(KeysResponseDto.serializer(), raw) }.getOrNull() ?: return
        val keys = dto.keys.map { it.toDomain() }
        if (keys.isNotEmpty()) cache.value = keys
    }
}
