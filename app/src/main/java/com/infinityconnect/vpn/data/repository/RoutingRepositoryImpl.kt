package com.infinityconnect.vpn.data.repository

import com.infinityconnect.vpn.data.local.SettingsStore
import com.infinityconnect.vpn.data.remote.api.RawApi
import com.infinityconnect.vpn.domain.model.AppError
import com.infinityconnect.vpn.domain.model.AppResult
import com.infinityconnect.vpn.domain.model.AppRoutingMode
import com.infinityconnect.vpn.domain.model.RoutingMode
import com.infinityconnect.vpn.domain.model.RoutingSettings
import com.infinityconnect.vpn.domain.model.SitePreset
import com.infinityconnect.vpn.domain.model.SiteRoutingMode
import com.infinityconnect.vpn.domain.repository.RoutingRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RoutingRepositoryImpl @Inject constructor(
    private val settingsStore: SettingsStore,
    private val rawApi: RawApi,
    private val json: Json,
) : RoutingRepository {

    override val settings: Flow<RoutingSettings> = settingsStore.routing

    override suspend fun current(): RoutingSettings = settingsStore.currentRouting()

    override suspend fun setMode(mode: RoutingMode) = settingsStore.setRoutingMode(mode)

    override suspend fun setAppMode(mode: AppRoutingMode) = settingsStore.setAppRoutingMode(mode)

    override suspend fun setApps(packages: Set<String>) = settingsStore.setAppList(packages)

    override suspend fun setSiteMode(mode: SiteRoutingMode) = settingsStore.setSiteRoutingMode(mode)

    override suspend fun setSites(domains: List<String>) = settingsStore.setSiteList(domains)

    override suspend fun setSitePresets(presets: Set<SitePreset>) = settingsStore.setSitePresets(presets)

    override suspend fun downloadRules(url: String): AppResult<RoutingSettings> {
        val trimmed = url.trim()
        if (trimmed.isBlank() || !(trimmed.startsWith("http://") || trimmed.startsWith("https://"))) {
            return AppResult.Failure(AppError.Parse("Некорректный URL правил"))
        }
        // Сохраняем URL сразу, чтобы поле не терялось при ошибке загрузки.
        settingsStore.setRoutingRulesUrl(trimmed)

        val fetched = safeApiCall { rawApi.getRaw(trimmed, emptyMap()).body()?.string().orEmpty() }
        val body = when (fetched) {
            is AppResult.Success -> fetched.data
            is AppResult.Failure -> return fetched
        }

        val rules = extractRules(body)
            ?: return AppResult.Failure(
                AppError.Parse("Файл не похож на конфиг правил (нет rules)"),
            )

        settingsStore.saveRoutingRules(trimmed, rules, System.currentTimeMillis())
        return AppResult.Success(settingsStore.currentRouting())
    }

    /**
     * Извлекает массив rules из тела конфига. Принимает:
     *  - объект Xray с полем "routing":{"rules":[...]} или верхним "rules":[...];
     *  - «голый» массив правил [...].
     * Возвращает JSON-строку массива rules, либо null если не найдено/невалидно.
     */
    private fun extractRules(body: String): String? {
        val root = runCatching { json.parseToJsonElement(body.trim()) }.getOrNull() ?: return null
        val rules: JsonArray? = when (root) {
            is JsonArray -> root
            is JsonObject -> {
                val routing = root["routing"] as? JsonObject
                (routing?.get("rules") ?: root["rules"])?.let {
                    runCatching { it.jsonArray }.getOrNull()
                }
            }
            else -> null
        }
        if (rules == null || rules.isEmpty()) return null
        return json.encodeToString(JsonArray.serializer(), rules)
    }
}
