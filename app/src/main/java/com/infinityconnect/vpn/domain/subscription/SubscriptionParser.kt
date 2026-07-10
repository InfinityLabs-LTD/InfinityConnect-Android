package com.infinityconnect.vpn.domain.subscription

import android.util.Base64
import com.infinityconnect.vpn.domain.engine.EngineConfig
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Парсер подписки Remnawave. Принимает сырое тело подписки (обычно base64,
 * содержащий несколько URI, по одному на строку) или единичный URI (raw_uri) и
 * возвращает список [EngineConfig] — по одному профилю на сервер.
 *
 * Поддерживает VLESS (Reality/XHTTP → Xray) и Hysteria2. Неизвестные схемы
 * (vmess/ss/trojan и т.п.) пропускаются, т.к. движки под них не подключены.
 */
@Singleton
class SubscriptionParser @Inject constructor() {

    /**
     * Разбирает полное тело подписки в список профилей.
     * @param raw содержимое subscription_url (base64 или plain со строками-URI).
     */
    fun parseSubscription(raw: String): List<EngineConfig> {
        val decoded = maybeBase64Decode(raw.trim())
        return decoded
            .lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .mapNotNull { parseSingleUri(it) }
            .toList()
    }

    /** Разбирает единичный URI (raw_uri из /v1/config или ключа). */
    fun parseSingleUri(uri: String): EngineConfig? = when {
        uri.startsWith("vless://") -> VlessUriParser.parse(uri)
        uri.startsWith("hy2://") || uri.startsWith("hysteria2://") ->
            Hysteria2UriParser.parse(uri)
        else -> null // vmess/ss/trojan и прочее не поддерживаются
    }

    /**
     * Тело подписки Remnawave обычно приходит как base64 всего списка URI.
     * Пытаемся декодировать; если результат не похож на набор URI — считаем,
     * что тело уже в открытом виде, и возвращаем как есть.
     */
    private fun maybeBase64Decode(raw: String): String {
        // Уже содержит явные схемы — не base64.
        if (raw.contains("://")) return raw

        val candidate = raw.replace("\n", "").replace("\r", "")
        val bytes = runCatching {
            Base64.decode(candidate, Base64.DEFAULT or Base64.URL_SAFE)
        }.getOrNull() ?: return raw

        val text = runCatching { String(bytes, Charsets.UTF_8) }.getOrNull() ?: return raw
        // Валидно, только если внутри действительно есть поддерживаемые URI.
        return if (text.contains("://")) text else raw
    }
}
