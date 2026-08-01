package com.infinityconnect.vpn.data.local

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.infinityconnect.vpn.data.remote.dto.DiscoveryDto
import androidx.datastore.preferences.core.intPreferencesKey
import com.infinityconnect.vpn.domain.model.PingMethod
import com.infinityconnect.vpn.domain.model.PingMode
import com.infinityconnect.vpn.domain.model.PingSettings
import com.infinityconnect.vpn.domain.model.AppRoutingMode
import com.infinityconnect.vpn.domain.model.Language
import com.infinityconnect.vpn.domain.model.RoutingMode
import com.infinityconnect.vpn.domain.model.RoutingSettings
import com.infinityconnect.vpn.domain.model.SitePreset
import com.infinityconnect.vpn.domain.model.SiteRoutingMode
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
    private val crypto: SecretCrypto,
) {
    val domain: Flow<String?> = context.dataStore.data.map { it[KEY_DOMAIN] }

    val discoveryJson: Flow<DiscoveryDto?> = context.dataStore.data.map { prefs ->
        prefs[KEY_DISCOVERY]?.let { runCatching { json.decodeFromString<DiscoveryDto>(it) }.getOrNull() }
    }

    /**
     * Выбранный язык интерфейса. Пустое значение = «Системный».
     *
     * Применяет язык [com.infinityconnect.vpn.ui.util.LanguageController]; здесь
     * выбор хранится, чтобы восстановить его на API < 33 при старте и чтобы
     * экран настроек показывал отметку до первого применения.
     */
    val language: Flow<Language> = context.dataStore.data.map { Language.from(it[KEY_LANGUAGE]) }

    suspend fun setLanguage(language: Language) {
        context.dataStore.edit { it[KEY_LANGUAGE] = language.tag }
    }

    suspend fun currentLanguage(): Language = language.first()

    /** Настройки маршрутизации (режим + внешние правила + per-app + домены). */
    val routing: Flow<RoutingSettings> = context.dataStore.data.map { prefs ->
        RoutingSettings(
            mode = RoutingMode.from(prefs[KEY_ROUTING_MODE]),
            rulesUrl = prefs[KEY_ROUTING_RULES_URL],
            rulesJson = prefs[KEY_ROUTING_RULES_JSON],
            rulesUpdatedAt = prefs[KEY_ROUTING_RULES_AT],
            appMode = AppRoutingMode.from(prefs[KEY_APP_MODE]),
            apps = prefs[KEY_APP_LIST]?.split('\n')?.filter { it.isNotBlank() }?.toSet() ?: emptySet(),
            siteMode = SiteRoutingMode.from(prefs[KEY_SITE_MODE]),
            sites = prefs[KEY_SITE_LIST]?.split('\n')?.map { it.trim() }?.filter { it.isNotBlank() } ?: emptyList(),
            sitePresets = SitePreset.setFrom(prefs[KEY_SITE_PRESETS]?.split('\n').orEmpty()),
        )
    }

    suspend fun setRoutingMode(mode: RoutingMode) {
        context.dataStore.edit { it[KEY_ROUTING_MODE] = mode.name }
    }

    /** Режим фильтрации по приложениям (OFF/ALLOW/DISALLOW). */
    suspend fun setAppRoutingMode(mode: AppRoutingMode) {
        context.dataStore.edit { it[KEY_APP_MODE] = mode.name }
    }

    /** Набор package-name выбранных приложений (хранится строками через \n). */
    suspend fun setAppList(packages: Set<String>) {
        context.dataStore.edit { it[KEY_APP_LIST] = packages.joinToString("\n") }
    }

    /** Режим маршрутизации по доменам (OFF/PROXY/DIRECT). */
    suspend fun setSiteRoutingMode(mode: SiteRoutingMode) {
        context.dataStore.edit { it[KEY_SITE_MODE] = mode.name }
    }

    /** Список доменов для доменной маршрутизации (хранится строками через \n). */
    suspend fun setSiteList(domains: List<String>) {
        context.dataStore.edit { it[KEY_SITE_LIST] = domains.joinToString("\n") }
    }

    /** Набор включённых пресетов доменной маршрутизации (имена через \n). */
    suspend fun setSitePresets(presets: Set<SitePreset>) {
        context.dataStore.edit { it[KEY_SITE_PRESETS] = presets.joinToString("\n") { p -> p.name } }
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

    /** Настройки пинга (протокол + режим + тест-URL + таймаут). */
    val ping: Flow<PingSettings> = context.dataStore.data.map { prefs ->
        PingSettings(
            method = PingMethod.from(prefs[KEY_PING_METHOD]),
            mode = PingMode.from(prefs[KEY_PING_MODE]),
            testUrl = prefs[KEY_PING_URL]?.takeIf { it.isNotBlank() } ?: PingSettings.DEFAULT_TEST_URL,
            timeoutSec = prefs[KEY_PING_TIMEOUT] ?: PingSettings.DEFAULT_TIMEOUT_SEC,
        )
    }

    suspend fun setPingMethod(method: PingMethod) {
        context.dataStore.edit { it[KEY_PING_METHOD] = method.name }
    }

    suspend fun setPingMode(mode: PingMode) {
        context.dataStore.edit { it[KEY_PING_MODE] = mode.name }
    }

    suspend fun setPingUrl(url: String) {
        context.dataStore.edit { it[KEY_PING_URL] = url }
    }

    suspend fun setPingTimeout(sec: Int) {
        context.dataStore.edit {
            it[KEY_PING_TIMEOUT] = sec.coerceIn(PingSettings.MIN_TIMEOUT_SEC, PingSettings.MAX_TIMEOUT_SEC)
        }
    }

    suspend fun currentPing(): PingSettings = ping.first()

    /**
     * Последний выбранный сервер — восстанавливается при следующем запуске,
     * чтобы кнопка подключения сразу целилась в него. Индекс сам по себе
     * ненадёжен (список серверов подписки может перестроиться на сервере),
     * поэтому храним и имя: при восстановлении оно приоритетнее индекса.
     */
    val lastServer: Flow<LastServer?> = context.dataStore.data.map { prefs ->
        val keyId = prefs[KEY_LAST_KEY_ID] ?: return@map null
        LastServer(
            keyId = keyId,
            serverIndex = prefs[KEY_LAST_SERVER_INDEX] ?: 0,
            serverName = prefs[KEY_LAST_SERVER_NAME],
        )
    }

    /**
     * versionCode обновления, о котором пользователь просил не напоминать.
     * 0 — не отказывался ни от чего.
     *
     * Храним именно код версии, а не флаг «не уведомлять вообще»: отказ касается
     * конкретного релиза, и о следующем пользователя нужно предупредить снова.
     */
    val skippedUpdateCode: Flow<Long> = context.dataStore.data
        .map { it[KEY_UPDATE_SKIPPED_CODE] ?: 0L }

    suspend fun skipUpdate(versionCode: Long) {
        context.dataStore.edit { it[KEY_UPDATE_SKIPPED_CODE] = versionCode }
    }

    suspend fun saveLastServer(keyId: Long, serverIndex: Int, serverName: String?) {
        context.dataStore.edit {
            it[KEY_LAST_KEY_ID] = keyId
            it[KEY_LAST_SERVER_INDEX] = serverIndex
            if (serverName != null) it[KEY_LAST_SERVER_NAME] = serverName
            else it.remove(KEY_LAST_SERVER_NAME)
        }
    }

    suspend fun currentLastServer(): LastServer? = lastServer.first()

    suspend fun saveDomain(domain: String) {
        context.dataStore.edit { it[KEY_DOMAIN] = domain }
    }

    suspend fun saveDiscovery(dto: DiscoveryDto) {
        context.dataStore.edit { it[KEY_DISCOVERY] = json.encodeToString(dto) }
    }

    /**
     * Персист списка ключей (сырой JSON ответа /v1/keys) для офлайн-старта.
     *
     * Значение ШИФРУЕТСЯ ([SecretCrypto], AES-256/GCM на ключе Keystore): в ответе
     * /v1/keys лежат `subscription_url` — фактические учётные данные доступа к
     * подпискам, а DataStore хранит их на диске открытым текстом. Остальные
     * настройки не секретны и шифрования не требуют.
     */
    suspend fun saveKeysJson(raw: String) {
        val payload = crypto.encrypt(raw)
        context.dataStore.edit { it[KEY_KEYS_JSON] = payload }
    }

    /**
     * Сохранённый JSON ключей (или null) — читается при холодном старте.
     *
     * Значения, записанные версиями до шифрования, читаются как есть и лягут
     * зашифрованными при следующем [saveKeysJson] (первый же успешный sync) —
     * пользователь не теряет офлайн-список серверов при обновлении приложения.
     * Если ключ Keystore инвалидирован (смена блокировки экрана / восстановление
     * из бэкапа), [SecretCrypto.decrypt] вернёт null — кэш считается отсутствующим,
     * и ключи подтянутся из сети, вместо падения на старте.
     */
    suspend fun currentKeysJson(): String? =
        context.dataStore.data.first()[KEY_KEYS_JSON]?.let { crypto.decrypt(it) }

    /** Синхронное чтение кэша discovery при инициализации (может быть null). */
    suspend fun currentDiscovery(): DiscoveryDto? = discoveryJson.first()

    suspend fun currentDomain(): String? = domain.first()

    suspend fun clear() {
        context.dataStore.edit { it.clear() }
    }

    private companion object {
        val KEY_DOMAIN = stringPreferencesKey("server_domain")
        val KEY_DISCOVERY = stringPreferencesKey("discovery_json")
        val KEY_KEYS_JSON = stringPreferencesKey("keys_json")
        val KEY_LANGUAGE = stringPreferencesKey("ui_language")
        val KEY_ROUTING_MODE = stringPreferencesKey("routing_mode")
        val KEY_ROUTING_RULES_URL = stringPreferencesKey("routing_rules_url")
        val KEY_ROUTING_RULES_JSON = stringPreferencesKey("routing_rules_json")
        val KEY_ROUTING_RULES_AT = longPreferencesKey("routing_rules_at")
        val KEY_APP_MODE = stringPreferencesKey("app_routing_mode")
        val KEY_APP_LIST = stringPreferencesKey("app_routing_list")
        val KEY_SITE_MODE = stringPreferencesKey("site_routing_mode")
        val KEY_SITE_LIST = stringPreferencesKey("site_routing_list")
        val KEY_SITE_PRESETS = stringPreferencesKey("site_routing_presets")
        val KEY_PING_METHOD = stringPreferencesKey("ping_method")
        val KEY_PING_MODE = stringPreferencesKey("ping_mode")
        val KEY_PING_URL = stringPreferencesKey("ping_url")
        val KEY_PING_TIMEOUT = intPreferencesKey("ping_timeout_sec")
        val KEY_LAST_KEY_ID = longPreferencesKey("last_key_id")
        val KEY_LAST_SERVER_INDEX = intPreferencesKey("last_server_index")
        val KEY_LAST_SERVER_NAME = stringPreferencesKey("last_server_name")
        val KEY_UPDATE_SKIPPED_CODE = longPreferencesKey("update_skipped_code")
    }
}

/** Последний выбранный пользователем сервер (подписка + позиция + имя). */
data class LastServer(
    val keyId: Long,
    val serverIndex: Int,
    val serverName: String?,
)
