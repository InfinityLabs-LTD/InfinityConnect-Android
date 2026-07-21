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
 * Режим маршрутизации по приложениям (split-tunnel на уровне TUN-интерфейса).
 * Реализуется через VpnService.Builder — работает для всех движков (Xray/Hy2).
 */
enum class AppRoutingMode {
    /** Не фильтровать по приложениям — весь трафик по общим правилам. */
    OFF,

    /** Через VPN идут ТОЛЬКО выбранные приложения (addAllowedApplication). */
    ALLOW,

    /** Через VPN идёт всё, КРОМЕ выбранных (addDisallowedApplication). */
    DISALLOW;

    companion object {
        fun from(raw: String?): AppRoutingMode =
            entries.firstOrNull { it.name == raw } ?: OFF
    }
}

/**
 * Режим маршрутизации по сайтам (доменам) — правила routing.rules Xray-ядра.
 * Работает на VLESS-серверах; для Hysteria2 доменные правила из UI не применяются.
 */
enum class SiteRoutingMode {
    /** Список доменов не используется. */
    OFF,

    /** Указанные домены — через VPN (proxy), остальное по общему режиму. */
    PROXY,

    /** Указанные домены — напрямую (direct), остальное по общему режиму. */
    DIRECT;

    companion object {
        fun from(raw: String?): SiteRoutingMode =
            entries.firstOrNull { it.name == raw } ?: OFF
    }
}

/**
 * Настройки маршрутизации.
 *
 * @param mode активный режим по трафику в целом.
 * @param rulesUrl URL внешнего конфига правил (для [RoutingMode.CUSTOM]).
 * @param rulesJson загруженное и сохранённое тело конфига правил (Xray routing
 *   JSON: массив rules или объект с полем "rules"); null — не загружено.
 * @param rulesUpdatedAt время последней успешной загрузки (epoch ms) или null.
 * @param appMode режим фильтрации по приложениям (split-tunnel).
 * @param apps набор package-name приложений для [appMode] (allow/disallow-список).
 * @param siteMode режим маршрутизации по списку доменов.
 * @param sites список доменов для [siteMode] (например "youtube.com").
 */
data class RoutingSettings(
    val mode: RoutingMode = RoutingMode.ALL,
    val rulesUrl: String? = null,
    val rulesJson: String? = null,
    val rulesUpdatedAt: Long? = null,
    val appMode: AppRoutingMode = AppRoutingMode.OFF,
    val apps: Set<String> = emptySet(),
    val siteMode: SiteRoutingMode = SiteRoutingMode.OFF,
    val sites: List<String> = emptyList(),
)
