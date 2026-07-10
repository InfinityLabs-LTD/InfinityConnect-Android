package com.infinityconnect.vpn.domain.subscription

import java.net.URLDecoder

/** Общие утилиты разбора URI подписок. */
internal object UriParsing {

    /** URL-декодирование (UTF-8), безопасное к некорректному вводу. */
    fun decode(value: String): String =
        runCatching { URLDecoder.decode(value, "UTF-8") }.getOrDefault(value)

    /**
     * Разбирает query-строку "a=b&c=d" в map. Значения URL-декодируются.
     * Пустые/некорректные пары пропускаются.
     */
    fun parseQuery(query: String?): Map<String, String> {
        if (query.isNullOrBlank()) return emptyMap()
        return query.split('&').mapNotNull { pair ->
            val idx = pair.indexOf('=')
            if (idx <= 0) return@mapNotNull null
            val key = pair.substring(0, idx)
            val raw = pair.substring(idx + 1)
            key to decode(raw)
        }.toMap()
    }

    /** Извлекает remark (часть после #), URL-декодируя её. */
    fun extractRemark(uri: String, fallback: String): String {
        val hashIdx = uri.indexOf('#')
        if (hashIdx < 0 || hashIdx == uri.length - 1) return fallback
        val raw = uri.substring(hashIdx + 1)
        return decode(raw).ifBlank { fallback }
    }
}
