package com.infinityconnect.vpn.domain.repository

import com.infinityconnect.vpn.data.remote.dto.ConfigDto
import com.infinityconnect.vpn.domain.model.AppResult
import com.infinityconnect.vpn.domain.model.Discovery
import com.infinityconnect.vpn.domain.model.ServerEntry
import com.infinityconnect.vpn.domain.model.SubscriptionInfo
import com.infinityconnect.vpn.domain.model.UserInfo
import com.infinityconnect.vpn.domain.model.VpnKey
import kotlinx.coroutines.flow.Flow

/**
 * Discovery по домену сервера. Кэширует результат и применяет api_base_url
 * к сетевому слою (через ApiBaseUrlProvider).
 */
interface DiscoveryRepository {
    /** Текущий сохранённый домен сервера (или null, если онбординг не пройден). */
    fun savedDomain(): Flow<String?>

    /** Выполняет discovery по домену, сохраняет и активирует конфигурацию. */
    suspend fun discover(domain: String): AppResult<Discovery>

    /** Восстанавливает ранее сохранённый discovery (при старте приложения). */
    suspend fun restore(): AppResult<Discovery>

    /** Текущая discovery-конфигурация (для ссылок регистрация/поддержка). */
    fun cached(): Discovery?
}

/** Авторизация по веб-аккаунту. */
interface AuthRepository {
    /** Поток флага авторизованности (наличие валидных токенов). */
    val isLoggedIn: Flow<Boolean>

    suspend fun login(login: String, password: String): AppResult<Unit>

    /** Разлогин: серверный logout (best-effort) + очистка токенов. */
    suspend fun logout()
}

/** Аккаунт и подписка пользователя. */
interface UserRepository {
    suspend fun userInfo(): AppResult<UserInfo>
    suspend fun subscriptionInfo(): AppResult<SubscriptionInfo>
}

/** Ключи (автоимпорт подписок). */
interface KeysRepository {
    /** Кэшированный список ключей (обновляется методом sync). */
    val keys: Flow<List<VpnKey>>

    /** Загружает /v1/keys, обновляет кэш и возвращает список. */
    suspend fun sync(): AppResult<List<VpnKey>>

    suspend fun key(id: Long): AppResult<VpnKey>
}

/** Конфигурация подключения (серверы ключа + разобранный конфиг). */
interface ConfigRepository {
    suspend fun servers(keyId: Long): AppResult<List<ServerEntry>>

    /** Сырой конфиг сервера (метаданные VLESS + raw_uri) — вход для парсера. */
    suspend fun config(keyId: Long, serverIndex: Int): AppResult<ConfigDto>
}
