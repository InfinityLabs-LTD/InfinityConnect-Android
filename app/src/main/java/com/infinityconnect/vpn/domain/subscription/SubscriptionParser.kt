package com.infinityconnect.vpn.domain.subscription

import android.util.Base64
import com.infinityconnect.vpn.domain.engine.EngineConfig
import com.infinityconnect.vpn.domain.engine.Security
import com.infinityconnect.vpn.domain.engine.Transport
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Парсер подписки Remnawave. Принимает сырое тело подписки и возвращает список
 * [EngineConfig] — по одному профилю на сервер.
 *
 * Форматы тела (в порядке проверки):
 *  1. JSON-массив готовых Xray-конфигов — панель отдаёт его клиенту Happ с HWID
 *     (каждый элемент: {remarks, outbounds:[{protocol:vless,...}], ...}).
 *  2. base64 со списком URI (vless://, hy2://), по одному на строку.
 *  3. Список URI в открытом виде.
 *
 * Поддерживает VLESS (Reality/XHTTP → Xray) и Hysteria2. Неизвестные схемы
 * пропускаются.
 */
@Singleton
class SubscriptionParser @Inject constructor(
    private val json: Json,
) {

    /** Разбирает полное тело подписки в список профилей. */
    fun parseSubscription(raw: String): List<EngineConfig> {
        val trimmed = raw.trim()

        // 1. JSON-массив/объект конфигов Xray (клиент Happ + HWID).
        if (trimmed.startsWith("[") || trimmed.startsWith("{")) {
            parseJsonConfigs(trimmed).takeIf { it.isNotEmpty() }?.let { return it }
        }

        // 2/3. base64 или открытый список URI.
        val decoded = maybeBase64Decode(trimmed)
        // Вдруг после base64-декода оказался JSON.
        val d = decoded.trim()
        if (d.startsWith("[") || d.startsWith("{")) {
            parseJsonConfigs(d).takeIf { it.isNotEmpty() }?.let { return it }
        }
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
        else -> null
    }

    // ── JSON-конфиги Xray ──

    /**
     * Разбирает JSON-массив готовых Xray-конфигов. Из каждого берёт proxy-outbound
     * (первый vless) и remark, строит [EngineConfig.Vless].
     */
    private fun parseJsonConfigs(text: String): List<EngineConfig> {
        val root = runCatching { json.parseToJsonElement(text) }.getOrNull() ?: return emptyList()
        val array: JsonArray = when {
            root is JsonArray -> root
            root is JsonObject -> JsonArray(listOf(root))
            else -> return emptyList()
        }
        return array.mapNotNull { element ->
            (element as? JsonObject)?.let { parseSingleJsonConfig(it) }
        }
    }

    private fun parseSingleJsonConfig(config: JsonObject): EngineConfig? {
        val outbounds = config["outbounds"]?.jsonArray ?: return null
        // Берём proxy-outbound: vless. Пропускаем direct/block.
        val proxy = outbounds.firstOrNull { ob ->
            (ob as? JsonObject)?.get("protocol")?.str() == "vless"
        }?.jsonObject ?: return null

        val settings = proxy["settings"]?.jsonObject ?: return null
        val vnext = settings["vnext"]?.jsonArray?.firstOrNull()?.jsonObject ?: return null
        val address = vnext["address"]?.str() ?: return null
        val port = vnext["port"]?.int() ?: return null
        val user = vnext["users"]?.jsonArray?.firstOrNull()?.jsonObject ?: return null
        val uuid = user["id"]?.str() ?: return null
        val flow = user["flow"]?.str()?.takeIf { it.isNotBlank() }

        val stream = proxy["streamSettings"]?.jsonObject
        val transport = parseTransport(stream)
        val security = parseSecurity(stream)

        val remark = config["remarks"]?.str()
            ?: config["remark"]?.str()
            ?: address

        return EngineConfig.Vless(
            remark = remark,
            address = address,
            port = port,
            uuid = uuid,
            transport = transport,
            security = security,
            flow = flow,
        )
    }

    private fun parseTransport(stream: JsonObject?): Transport {
        val network = stream?.get("network")?.str()?.lowercase() ?: "tcp"
        return when (network) {
            "ws", "websocket" -> {
                val ws = stream?.get("wsSettings")?.jsonObject
                Transport.Ws(
                    path = ws?.get("path")?.str(),
                    host = ws?.get("headers")?.jsonObject?.get("Host")?.str(),
                )
            }
            "grpc" -> Transport.Grpc(
                serviceName = stream?.get("grpcSettings")?.jsonObject?.get("serviceName")?.str(),
            )
            "xhttp", "splithttp" -> {
                val xh = stream?.get("xhttpSettings")?.jsonObject
                    ?: stream?.get("splithttpSettings")?.jsonObject
                Transport.Xhttp(
                    path = xh?.get("path")?.str(),
                    host = xh?.get("host")?.str(),
                    mode = xh?.get("mode")?.str(),
                )
            }
            else -> Transport.Tcp
        }
    }

    private fun parseSecurity(stream: JsonObject?): Security {
        return when (stream?.get("security")?.str()?.lowercase()) {
            "reality" -> {
                val r = stream["realitySettings"]?.jsonObject
                    ?: return Security.None
                val pbk = r["publicKey"]?.str() ?: return Security.None
                Security.Reality(
                    sni = r["serverName"]?.str(),
                    fingerprint = r["fingerprint"]?.str() ?: "chrome",
                    publicKey = pbk,
                    shortId = r["shortId"]?.str(),
                    spiderX = r["spiderX"]?.str(),
                )
            }
            "tls" -> {
                val t = stream["tlsSettings"]?.jsonObject
                Security.Tls(
                    sni = t?.get("serverName")?.str(),
                    fingerprint = t?.get("fingerprint")?.str(),
                    allowInsecure = t?.get("allowInsecure")?.jsonPrimitive?.contentOrNull == "true",
                )
            }
            else -> Security.None
        }
    }

    // Хелперы извлечения примитивов.
    private fun kotlinx.serialization.json.JsonElement.str(): String? =
        runCatching { jsonPrimitive.contentOrNull }.getOrNull()

    private fun kotlinx.serialization.json.JsonElement.int(): Int? =
        runCatching { jsonPrimitive.intOrNull }.getOrNull()

    // ── base64 ──

    private fun maybeBase64Decode(raw: String): String {
        if (raw.contains("://")) return raw
        val candidate = raw.replace("\n", "").replace("\r", "")
        val bytes = runCatching {
            Base64.decode(candidate, Base64.DEFAULT or Base64.URL_SAFE)
        }.getOrNull() ?: return raw
        val text = runCatching { String(bytes, Charsets.UTF_8) }.getOrNull() ?: return raw
        return if (text.contains("://") || text.trimStart().startsWith("[")) text else raw
    }
}
