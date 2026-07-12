package com.infinityconnect.vpn.domain.model

/**
 * Режим маршрутизации трафика (стиль Happ).
 */
enum class RoutingMode {
    /** Весь трафик — через VPN (кроме приватных сетей). */
    ALL,

    /** Обход российских сайтов/сервисов напрямую, остальное — через VPN. */
    BYPASS_RU,

    /** По внешнему конфигу правил (загружается по URL). */
    CUSTOM;

    companion object {
        fun from(raw: String?): RoutingMode =
            entries.firstOrNull { it.name == raw } ?: ALL
    }
}

/**
 * Настройки маршрутизации.
 *
 * @param mode активный режим.
 * @param rulesUrl URL внешнего конфига правил (для [RoutingMode.CUSTOM]).
 * @param rulesJson загруженное и сохранённое тело конфига правил (Xray routing
 *   JSON: массив rules или объект с полем "rules"); null — не загружено.
 * @param rulesUpdatedAt время последней успешной загрузки (epoch ms) или null.
 */
data class RoutingSettings(
    val mode: RoutingMode = RoutingMode.ALL,
    val rulesUrl: String? = null,
    val rulesJson: String? = null,
    val rulesUpdatedAt: Long? = null,
)
