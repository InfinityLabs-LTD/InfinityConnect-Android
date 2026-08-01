package com.infinityconnect.vpn.domain.model

/**
 * Язык интерфейса, выбранный пользователем.
 *
 * [SYSTEM] — не навязывать ничего: приложение следует языку системы (пустой
 * список локалей в AppCompatDelegate). Остальные значения соответствуют
 * локалям из res/xml/locales_config.xml — при добавлении новой локали править
 * оба места и resourceConfigurations в build.gradle.kts.
 */
enum class Language(val tag: String) {
    SYSTEM(""),
    RUSSIAN("ru"),
    ENGLISH("en"),
    ;

    companion object {
        /** Разбор сохранённого значения; неизвестное/пустое → [SYSTEM]. */
        fun from(tag: String?): Language =
            entries.firstOrNull { it.tag.isNotEmpty() && it.tag == tag } ?: SYSTEM
    }
}
