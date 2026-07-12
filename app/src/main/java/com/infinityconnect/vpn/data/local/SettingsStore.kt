package com.infinityconnect.vpn.data.local

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.infinityconnect.vpn.data.remote.dto.DiscoveryDto
import com.infinityconnect.vpn.domain.model.PingMethod
import com.infinityconnect.vpn.domain.model.PingSettings
import com.infinityconnect.vpn.domain.model.RoutingMode
import com.infinityconnect.vpn.domain.model.RoutingSettings
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore by preferencesDataStore(name = "infinity_settings")

/**
 * Настройки приложения на DataStore: домен сервера и кэш ответа discovery
 * (для быстрого старта и офлайн-доступа к ссылкам регистрация/поддержка).
 */
@Singleton
class SettingsStore @Inject constructor(
    @ApplicationContext private val context: Context,
    private val json: Json,
) {
    val domain: Flow<String?> = context.dataStore.data.map { it[KEY_DOMAIN] }

    val discoveryJson: Flow<DiscoveryDto?> = context.dataStore.data.map { prefs ->
        prefs[KEY_DISCOVERY]?.let { runCatching { json.decodeFromString<DiscoveryDto>(it) }.getOrNull() }
    }

    /** Настройки маршрутизации (режим + внешние правила). */
    val routing: Flow<RoutingSettings> = context.dataStore.data.map { prefs ->
        RoutingSettings(
            mode = RoutingMode.from(prefs[KEY_ROUTING_MODE]),
            rulesUrl = prefs[KEY_ROUTING_RULES_URL],
            rulesJson = prefs[KEY_ROUTING_RULES_JSON],
            rulesUpdatedAt = prefs[KEY_ROUTING_RULES_AT],
        )
    }

    suspend fun setRoutingMode(mode: RoutingMode) {
        context.dataStore.edit { it[KEY_ROUTING_MODE] = mode.name }
    }

    suspend fun setRoutingRulesUrl(url: String) {
        context.dataStore.edit { it[KEY_ROUTING_RULES_URL] = url }
    }

    /** Сохраняет загруженное тело правил и время загрузки. */
    suspend fun saveRoutingRules(url: String, rulesJson: String, updatedAt: Long) {
        context.dataStore.edit {
            it[KEY_ROUTING_RULES_URL] = url
            it[KEY_ROUTING_RULES_JSON] = rulesJson
            it[KEY_ROUTING_RULES_AT] = updatedAt
        }
    }

    suspend fun currentRouting(): RoutingSettings = routing.first()

    /** Настройки пинга (метод + тест-URL). */
    val ping: Flow<PingSettings> = context.dataStore.data.map { prefs ->
        PingSettings(
            method = PingMethod.from(prefs[KEY_PING_METHOD]),
            testUrl = prefs[KEY_PING_URL]?.takeIf { it.isNotBlank() } ?: PingSettings.DEFAULT_TEST_URL,
        )
    }

    suspend fun setPingMethod(method: PingMethod) {
        context.dataStore.edit { it[KEY_PING_METHOD] = method.name }
    }

    suspend fun setPingUrl(url: String) {
        context.dataStore.edit { it[KEY_PING_URL] = url }
    }

    suspend fun currentPing(): PingSettings = ping.first()

    suspend fun saveDomain(domain: String) {
        context.dataStore.edit { it[KEY_DOMAIN] = domain }
    }

    suspend fun saveDiscovery(dto: DiscoveryDto) {
        context.dataStore.edit { it[KEY_DISCOVERY] = json.encodeToString(dto) }
    }

    /** Синхронное чтение кэша discovery при инициализации (может быть null). */
    suspend fun currentDiscovery(): DiscoveryDto? = discoveryJson.first()

    suspend fun currentDomain(): String? = domain.first()

    suspend fun clear() {
        context.dataStore.edit { it.clear() }
    }

    private companion object {
        val KEY_DOMAIN = stringPreferencesKey("server_domain")
        val KEY_DISCOVERY = stringPreferencesKey("discovery_json")
        val KEY_ROUTING_MODE = stringPreferencesKey("routing_mode")
        val KEY_ROUTING_RULES_URL = stringPreferencesKey("routing_rules_url")
        val KEY_ROUTING_RULES_JSON = stringPreferencesKey("routing_rules_json")
        val KEY_ROUTING_RULES_AT = longPreferencesKey("routing_rules_at")
        val KEY_PING_METHOD = stringPreferencesKey("ping_method")
        val KEY_PING_URL = stringPreferencesKey("ping_url")
    }
}
