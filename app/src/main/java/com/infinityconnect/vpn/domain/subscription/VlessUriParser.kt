package com.infinityconnect.vpn.domain.subscription

import com.infinityconnect.vpn.domain.engine.EngineConfig
import com.infinityconnect.vpn.domain.engine.Security
import com.infinityconnect.vpn.domain.engine.Transport
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject

/**
 * Разбор VLESS-URI в [EngineConfig.Vless].
 *
 * Формат: vless://<uuid>@<host>:<port>?<params>#<remark>
 * Значимые параметры:
 *  - type: транспорт (tcp | ws | grpc | xhttp | splithttp)
 *  - security: none | tls | reality
 *  - sni, fp (fingerprint), alpn, allowInsecure
 *  - Reality: pbk (public key), sid (short id), spx (spiderX)
 *  - flow (например, xtls-rprx-vision)
 *  - транспорт: path, host, mode, serviceName
 */
internal object VlessUriParser {

    fun parse(uri: String): EngineConfig.Vless? {
        val trimmed = uri.trim()
        if (!trimmed.startsWith("vless://")) return null

        // Отделяем схему и remark (#...).
        val hashIdx = trimmed.indexOf('#')
        val body = if (hashIdx >= 0) trimmed.substring(0, hashIdx) else trimmed
        val afterScheme = body.removePrefix("vless://")

        // Разделяем на userinfo@authority?query
        val queryIdx = afterScheme.indexOf('?')
        val beforeQuery = if (queryIdx >= 0) afterScheme.substring(0, queryIdx) else afterScheme
        val query = if (queryIdx >= 0) afterScheme.substring(queryIdx + 1) else null

        val atIdx = beforeQuery.indexOf('@')
        if (atIdx <= 0) return null
        val uuid = beforeQuery.substring(0, atIdx)
        val authority = beforeQuery.substring(atIdx + 1)

        val (host, port) = parseHostPort(authority) ?: return null
        if (uuid.isBlank() || host.isBlank()) return null

        val params = UriParsing.parseQuery(query)
        val remark = UriParsing.extractRemark(trimmed, fallback = host)

        val transport = parseTransport(params)
        val security = parseSecurity(params)
        // flow актуален для vision-профилей; для не-tcp транспортов обычно пуст.
        val flow = params["flow"]?.takeIf { it.isNotBlank() }

        return EngineConfig.Vless(
            remark = remark,
            address = host,
            port = port,
            uuid = uuid,
            transport = transport,
            security = security,
            flow = flow,
        )
    }

    /** Разбирает "host:port", поддерживая IPv6 в скобках [::1]:443. */
    private fun parseHostPort(authority: String): Pair<String, Int>? {
        if (authority.startsWith("[")) {
            val close = authority.indexOf(']')
            if (close < 0) return null
            val host = authority.substring(1, close)
            val rest = authority.substring(close + 1)
            val port = rest.removePrefix(":").toIntOrNull() ?: return null
            return host to port
        }
        val colon = authority.lastIndexOf(':')
        if (colon <= 0) return null
        val host = authority.substring(0, colon)
        val port = authority.substring(colon + 1).toIntOrNull() ?: return null
        return host to port
    }

    private fun parseTransport(p: Map<String, String>): Transport {
        return when (p["type"]?.lowercase()) {
            "ws", "websocket" -> Transport.Ws(
                path = p["path"],
                host = p["host"],
            )
            "grpc" -> Transport.Grpc(
                serviceName = p["serviceName"] ?: p["servicename"],
            )
            "xhttp", "splithttp" -> Transport.Xhttp(
                path = p["path"],
                host = p["host"],
                mode = p["mode"],
                // extra в vless://-URI — это URL-encoded JSON (xmux/xPadding/…).
                // UriParsing.parseQuery уже раскодировал %xx, осталось разобрать JSON.
                extra = parseExtraJson(p["extra"]),
            )
            // tcp/raw/пусто
            else -> Transport.Tcp
        }
    }

    private fun parseSecurity(p: Map<String, String>): Security {
        return when (p["security"]?.lowercase()) {
            "reality" -> {
                val pbk = p["pbk"] ?: p["publicKey"] ?: return Security.None
                Security.Reality(
                    sni = p["sni"],
                    fingerprint = p["fp"] ?: "chrome",
                    publicKey = pbk,
                    shortId = p["sid"],
                    spiderX = p["spx"],
                )
            }
            "tls" -> Security.Tls(
                sni = p["sni"],
                fingerprint = p["fp"],
                alpn = p["alpn"]?.split(',')?.map { it.trim() }?.filter { it.isNotEmpty() },
                allowInsecure = p["allowInsecure"] == "1" || p["allowInsecure"] == "true",
            )
            else -> Security.None
        }
    }

    /** Разбирает значение параметра `extra` (JSON-объект) из vless://-URI. */
    private fun parseExtraJson(raw: String?): JsonObject? {
        val text = raw?.trim()?.takeIf { it.startsWith("{") } ?: return null
        return runCatching { LENIENT_JSON.parseToJsonElement(text) as? JsonObject }.getOrNull()
    }

    private val LENIENT_JSON = Json { ignoreUnknownKeys = true; isLenient = true }
}
