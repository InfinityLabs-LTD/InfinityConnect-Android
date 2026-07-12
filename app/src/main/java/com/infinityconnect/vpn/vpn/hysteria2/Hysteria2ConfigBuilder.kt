package com.infinityconnect.vpn.vpn.hysteria2

import com.infinityconnect.vpn.domain.engine.EngineConfig
import com.infinityconnect.vpn.domain.model.RoutingMode
import com.infinityconnect.vpn.domain.model.RoutingSettings
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Строит JSON-конфиг клиента Hysteria2 из [EngineConfig.Hysteria2].
 *
 * Формат соответствует конфигу клиента github.com/apernet/hysteria:
 *  - server: "host:port"
 *  - auth: строка аутентификации
 *  - tls: { sni, insecure }
 *  - obfs: { type: salamander, salamander: { password } }  (если задан obfs)
 *  - bandwidth: { up: "N mbps", down: "N mbps" }            (если заданы лимиты)
 *
 * TUN-режим клиенту не описываем — TUN fd передаётся ядру напрямую через
 * gomobile ([Hysteria2CoreBridge]).
 */
@Singleton
class Hysteria2ConfigBuilder @Inject constructor() {

    fun build(
        config: EngineConfig.Hysteria2,
        routing: RoutingSettings = RoutingSettings(),
    ): String {
        val obj = buildJsonObject {
            put("server", "${hostForServer(config.address)}:${config.port}")
            put("auth", config.auth)

            // Блок routing для маршрутизатора в Go-обёртке (mode + правила).
            putJsonObject("routing") {
                put("mode", routing.mode.name)
                // Для CUSTOM отдаём массив правил (domain/ip/outboundTag).
                // Для ALL/BYPASS_RU правила на Go-стороне (RU-данные встроены).
                if (routing.mode == RoutingMode.CUSTOM) {
                    val rules = routing.rulesJson
                        ?.let { runCatching { JSON.parseToJsonElement(it) as? JsonArray }.getOrNull() }
                    if (rules != null) put("rules", rules)
                }
            }

            putJsonObject("tls") {
                config.sni?.takeIf { it.isNotBlank() }?.let { put("sni", it) }
                put("insecure", config.insecure)
            }

            config.obfsPassword?.takeIf { it.isNotBlank() }?.let { pwd ->
                putJsonObject("obfs") {
                    put("type", "salamander")
                    putJsonObject("salamander") {
                        put("password", pwd)
                    }
                }
            }

            val up = config.upMbps
            val down = config.downMbps
            if (up != null || down != null) {
                putJsonObject("bandwidth") {
                    up?.let { put("up", "$it mbps") }
                    down?.let { put("down", "$it mbps") }
                }
            }
        }
        return JSON.encodeToString(JsonObject.serializer(), obj)
    }

    /** IPv6-хост берём в квадратные скобки для "host:port". */
    private fun hostForServer(host: String): String =
        if (host.contains(':') && !host.startsWith("[")) "[$host]" else host

    private companion object {
        val JSON = Json { prettyPrint = false }
    }
}
