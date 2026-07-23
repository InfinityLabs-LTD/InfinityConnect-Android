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
 * Пресет доменной маршрутизации — готовый набор сайтов с фиксированным
 * направлением (direct = в обход VPN, proxy = через VPN). Пользователь может
 * включить несколько пресетов одновременно; они складываются с ручным списком
 * доменов. Применяется только на Xray (как и вся доменная маршрутизация).
 *
 * @param title название в UI.
 * @param subtitle краткое описание для UI.
 * @param direction направление трафика для доменов пресета
 *   ([SiteRoutingMode.DIRECT] или [SiteRoutingMode.PROXY]).
 * @param domains доменные суффиксы (Xray `domain:` — матчит и поддомены).
 * @param regexps regexp-правила Xray (например, все зоны `.ru`).
 */
enum class SitePreset(
    val title: String,
    val subtitle: String,
    val direction: SiteRoutingMode,
    val domains: List<String>,
    val regexps: List<String> = emptyList(),
) {
    /** Все российские зоны и популярные RU-сервисы — в обход VPN. */
    RU_BYPASS(
        title = "Российские сайты — напрямую",
        subtitle = "Зоны .ru/.su/.рф и популярные RU-сервисы идут в обход VPN",
        direction = SiteRoutingMode.DIRECT,
        domains = emptyList(), // домены берутся из RU_SERVICES + regexp-зон
        regexps = listOf(""".*\.ru$""", """.*\.su$""", """.*\.рф$"""),
    ),

    /** Российские банки и госсервисы — в обход VPN (антифрод любит RU-IP). */
    RU_BANKS(
        title = "Банки и Госуслуги — напрямую",
        subtitle = "Банковские приложения и госсервисы часто блокируют зарубежные IP",
        direction = SiteRoutingMode.DIRECT,
        domains = listOf(
            "sberbank.ru", "sberbank.com", "sber.ru", "online.sberbank.ru",
            "tinkoff.ru", "tbank.ru", "alfabank.ru", "vtb.ru", "gazprombank.ru",
            "raiffeisen.ru", "psbank.ru", "rshb.ru", "open.ru", "sovcombank.ru",
            "gosuslugi.ru", "nalog.gov.ru", "nalog.ru", "mos.ru", "pfr.gov.ru",
            "sfr.gov.ru", "esia.gosuslugi.ru", "mir-platform.ru", "nspk.ru",
        ),
    ),

    /** Российские соцсети/почта/маркетплейсы — в обход VPN. */
    RU_SERVICES(
        title = "RU-сервисы — напрямую",
        subtitle = "VK, Яндекс, Ozon, Wildberries, Avito и другие — в обход VPN",
        direction = SiteRoutingMode.DIRECT,
        domains = listOf(
            "vk.com", "vk.ru", "vk.cc", "vkvideo.ru", "userapi.com", "mail.ru",
            "yandex.ru", "yandex.net", "yandex.com", "ya.ru", "dzen.ru",
            "kinopoisk.ru", "ozon.ru", "ozone.ru", "wildberries.ru", "wb.ru",
            "avito.ru", "2gis.ru", "hh.ru", "rutube.ru", "sima-land.ru",
            "mts.ru", "megafon.ru", "beeline.ru", "t2.ru", "rt.ru", "drom.ru",
        ),
    ),

    /** Зарубежные видеосервисы — принудительно через VPN. */
    STREAMING_PROXY(
        title = "YouTube и стриминги — через VPN",
        subtitle = "YouTube, Netflix, Twitch и др. всегда идут через VPN",
        direction = SiteRoutingMode.PROXY,
        domains = listOf(
            "youtube.com", "youtu.be", "ytimg.com", "googlevideo.com",
            "ggpht.com", "netflix.com", "nflxvideo.net", "twitch.tv",
            "ttvnw.net", "spotify.com", "scdn.co", "soundcloud.com",
        ),
    ),

    /** Заблокированные в РФ соцсети — принудительно через VPN. */
    SOCIAL_PROXY(
        title = "Соцсети и мессенджеры — через VPN",
        subtitle = "Instagram, Facebook, X (Twitter), Discord — всегда через VPN",
        direction = SiteRoutingMode.PROXY,
        domains = listOf(
            "instagram.com", "cdninstagram.com", "facebook.com", "fbcdn.net",
            "twitter.com", "x.com", "twimg.com", "discord.com", "discord.gg",
            "discordapp.com", "discordapp.net", "linkedin.com", "licdn.com",
            "patreon.com", "medium.com",
        ),
    );

    companion object {
        /** Восстанавливает набор пресетов из сохранённых имён (неизвестные — игнор). */
        fun setFrom(names: Collection<String>): Set<SitePreset> =
            names.mapNotNull { n -> entries.firstOrNull { it.name == n } }.toSet()
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
 * @param sitePresets включённые пресеты доменной маршрутизации (multi-select);
 *   действуют независимо от [siteMode] — у каждого пресета своё направление.
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
    val sitePresets: Set<SitePreset> = emptySet(),
)
