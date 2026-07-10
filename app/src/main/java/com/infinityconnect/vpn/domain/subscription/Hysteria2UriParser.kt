package com.infinityconnect.vpn.domain.subscription

import com.infinityconnect.vpn.domain.engine.EngineConfig

/**
 * Разбор Hysteria2-URI в [EngineConfig.Hysteria2].
 *
 * Форматы: hy2://<auth>@<host>:<port>?<params>#<remark>
 *          hysteria2://...
 * Параметры:
 *  - sni
 *  - insecure (0/1)
 *  - obfs (=salamander) + obfs-password
 *  - upmbps / downmbps (иногда up / down)
 */
internal object Hysteria2UriParser {

    fun parse(uri: String): EngineConfig.Hysteria2? {
        val trimmed = uri.trim()
        val scheme = when {
            trimmed.startsWith("hysteria2://") -> "hysteria2://"
            trimmed.startsWith("hy2://") -> "hy2://"
            else -> return null
        }

        val hashIdx = trimmed.indexOf('#')
        val body = if (hashIdx >= 0) trimmed.substring(0, hashIdx) else trimmed
        val afterScheme = body.removePrefix(scheme)

        val queryIdx = afterScheme.indexOf('?')
        val beforeQuery = if (queryIdx >= 0) afterScheme.substring(0, queryIdx) else afterScheme
        val query = if (queryIdx >= 0) afterScheme.substring(queryIdx + 1) else null

        // auth@host:port; auth может отсутствовать (тогда берётся из params позже).
        val atIdx = beforeQuery.indexOf('@')
        val auth: String
        val authority: String
        if (atIdx >= 0) {
            auth = UriParsing.decode(beforeQuery.substring(0, atIdx))
            authority = beforeQuery.substring(atIdx + 1)
        } else {
            auth = ""
            authority = beforeQuery
        }

        val (host, port) = parseHostPort(authority) ?: return null
        if (host.isBlank()) return null

        val params = UriParsing.parseQuery(query)
        val remark = UriParsing.extractRemark(trimmed, fallback = host)

        val obfsType = params["obfs"]?.lowercase()
        val obfsPassword = if (obfsType == "salamander") {
            params["obfs-password"] ?: params["obfsParam"]
        } else {
            null
        }

        return EngineConfig.Hysteria2(
            remark = remark,
            address = host,
            port = port,
            auth = auth.ifBlank { params["auth"] ?: "" },
            sni = params["sni"] ?: params["peer"],
            insecure = params["insecure"] == "1" || params["insecure"] == "true",
            obfsPassword = obfsPassword,
            upMbps = (params["upmbps"] ?: params["up"])?.toIntOrNull(),
            downMbps = (params["downmbps"] ?: params["down"])?.toIntOrNull(),
        )
    }

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
}
