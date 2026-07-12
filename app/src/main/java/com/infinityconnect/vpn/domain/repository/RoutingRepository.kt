package com.infinityconnect.vpn.domain.repository

import com.infinityconnect.vpn.domain.model.AppResult
import com.infinityconnect.vpn.domain.model.RoutingMode
import com.infinityconnect.vpn.domain.model.RoutingSettings
import kotlinx.coroutines.flow.Flow

/**
 * Настройки маршрутизации: режим и внешний конфиг правил (стиль Happ).
 * Правила загружаются по URL, валидируются как JSON и сохраняются локально;
 * применяются в [com.infinityconnect.vpn.domain.engine.XrayConfigBuilder].
 */
interface RoutingRepository {

    /** Текущие настройки маршрутизации (реактивно). */
    val settings: Flow<RoutingSettings>

    /** Синхронное чтение текущих настроек (для сборки конфига движка). */
    suspend fun current(): RoutingSettings

    suspend fun setMode(mode: RoutingMode)

    /**
     * Загружает конфиг правил по [url], валидирует как JSON и сохраняет.
     * @return обновлённые настройки при успехе.
     */
    suspend fun downloadRules(url: String): AppResult<RoutingSettings>
}
