package com.infinityconnect.vpn.data.local

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.infinityconnect.vpn.data.remote.dto.DiscoveryDto
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
    }
}
