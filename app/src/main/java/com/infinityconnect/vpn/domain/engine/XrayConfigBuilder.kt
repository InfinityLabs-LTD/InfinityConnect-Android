package com.infinityconnect.vpn.domain.engine

import com.infinityconnect.vpn.domain.model.RoutingMode
import com.infinityconnect.vpn.domain.model.RoutingSettings
import com.infinityconnect.vpn.domain.model.SitePreset
import com.infinityconnect.vpn.domain.model.SiteRoutingMode
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Строит JSON-конфигурацию Xray-core из [EngineConfig.Vless].
 *
 * Схема:
 *  - inbound "socks" на 127.0.0.1:<socksPort> — точка входа для tun2socks
 *    (TUN-трафик заворачивается в этот SOCKS-порт на этапе VpnService);
 *  - outbound "vless" с streamSettings под транспорт (tcp/ws/grpc/xhttp) и
 *    security (none/tls/reality);
 *  - outbound "freedom" (direct) и "blackhole" для маршрутизации.
 *
 * Возвращает готовую JSON-строку для передачи в libXray.
 */
@Singleton
class XrayConfigBuilder @Inject constructor(
    private val json: Json,
) {

    /**
     * @param config профиль VLESS.
     * @param mtu MTU TUN-интерфейса (должен совпадать с VpnService).
     * @param enableLogging уровень лога Xray (warning при true, none иначе).
     *
     * Строит конфиг для TUN-режима AndroidLibXrayLite: inbound типа "tun"
     * (сам fd передаётся в ядро через startLoop), outbound vless + freedom/block,
     * DNS и routing (весь трафик в proxy, приватные сети — direct).
     */
    fun build(
        config: EngineConfig.Vless,
        mtu: Int = DEFAULT_MTU,
        enableLogging: Boolean = false,
        routing: RoutingSettings = RoutingSettings(),
    ): String {
        val root = buildJsonObject {
            putJsonObject("log") {
                put("loglevel", if (enableLogging) "warning" else "none")
            }
            // Сбор статистики трафика по аутбаундам — иначе queryStats пуст.
            putJsonObject("stats") {}
            putJsonObject("policy") {
                putJsonObject("system") {
                    put("statsOutboundUplink", true)
                    put("statsOutboundDownlink", true)
                }
            }
            putJsonObject("dns") {
                putJsonArray("servers") {
                    add("1.1.1.1"); add("8.8.8.8")
                }
            }
            putJsonArray("inbounds") {
                addJsonObject {
                    put("tag", "tun")
                    put("protocol", "tun")
                    // Схема TUN-инбаунда форка xray-core (autorepobot): fd берётся
                    // ядром из env xray.tun.fd (ставит libv2ray.startLoop).
                    putJsonObject("settings") {
                        put("name", "tun0")
                        put("mtu", mtu)
                        putJsonArray("gateway") {
                            add("$TUN_ADDRESS/30")
                        }
                        putJsonArray("dns") {
                            add("1.1.1.1"); add("8.8.8.8")
                        }
                    }
                    putJsonObject("sniffing") {
                        put("enabled", true)
                        putJsonArray("destOverride") {
                            add("http"); add("tls"); add("quic")
                        }
                    }
                }
            }
            putJsonArray("outbounds") {
                add(buildVlessOutbound(config))
                addJsonObject {
                    put("tag", "direct")
                    put("protocol", "freedom")
                }
                addJsonObject {
                    put("tag", "block")
                    put("protocol", "blackhole")
                }
            }
            put("routing", buildRouting(routing))
        }
        return json.encodeToString(JsonObject.serializer(), root)
    }

    /**
     * Пробрасывает готовый Xray-конфиг из подписки ([EngineConfig.RawXray]) в
     * ядро, подменяя его inbounds на TUN. Сохраняет outbounds, routing,
     * balancers, dns и burstObservatory как есть — иначе потеряется автовыбор и
     * fallback (balancer MAIN → скрытый WHITE-хост для обхода белых списков).
     *
     * Пользовательский общий режим (ALL/BYPASS_RU/CUSTOM) для таких конфигов
     * НЕ применяется: серверный routing уже содержит нужные правила, и его
     * balancer'ы важнее клиентских предпочтений. Но точечные ДОМЕННЫЕ правила
     * клиента (пресеты + ручной список сайтов) подмешиваются ПЕРЕД серверными:
     * direct-правила ведут в freedom-outbound (добавляется, если его нет),
     * proxy-правила — в дефолтный путь трафика (balancer или первый
     * proxy-outbound), так что fallback WHITE не ломается.
     */
    fun buildRaw(
        config: EngineConfig.RawXray,
        mtu: Int = DEFAULT_MTU,
        routing: RoutingSettings = RoutingSettings(),
    ): String {
        val src = config.root
        val merged = mergeClientRulesIntoRaw(src, routing)
        val root = buildJsonObject {
            // log: приглушаем (в подписке может стоять debug).
            putJsonObject("log") { put("loglevel", "none") }
            // Статистика трафика по аутбаундам (для UI-счётчика скорости).
            putJsonObject("stats") {}
            putJsonObject("policy") {
                putJsonObject("system") {
                    put("statsOutboundUplink", true)
                    put("statsOutboundDownlink", true)
                }
            }
            // Сохраняем dns/routing/outbounds/balancers/burstObservatory из подписки
            // (routing/outbounds — с подмешанными клиентскими доменными правилами).
            src["dns"]?.let { put("dns", it) }
            merged.routing?.let { put("routing", it) }
            put("outbounds", merged.outbounds)
            src["burstObservatory"]?.let { put("burstObservatory", it) }
            src["observatory"]?.let { put("observatory", it) }
            // Подменяем inbounds на наш TUN (socks/http из подписки не нужны).
            putJsonArray("inbounds") { add(buildTunInbound(mtu)) }
        }
        return json.encodeToString(JsonObject.serializer(), root)
    }

    /** Результат подмешивания клиентских правил в raw-конфиг подписки. */
    private data class MergedRaw(val routing: JsonElement?, val outbounds: JsonElement)

    /**
     * Подмешивает клиентские доменные правила (пресеты + ручной список сайтов)
     * в routing raw-конфига. Клиентские правила ставятся ПЕРЕД серверными
     * (первое совпавшее правило побеждает):
     *  1. proxy-исключения direct-пресетов (Сбер/Т-Банк — всегда через VPN);
     *  2. правила пресетов и пользовательских доменов.
     *
     * Направления: proxy → дефолтный путь трафика конфига (balancerTag первого
     * balancer'а, иначе тег первого proxy-outbound); direct → freedom-outbound
     * конфига (если его нет — добавляется "direct-client").
     */
    private fun mergeClientRulesIntoRaw(src: JsonObject, routing: RoutingSettings): MergedRaw {
        val srcRouting = src["routing"] as? JsonObject
        val srcOutbounds = src["outbounds"]?.jsonArray ?: JsonArray(emptyList())

        val clientRules = buildClientDomainRules(routing, srcRouting, srcOutbounds)
        if (clientRules.isEmpty()) return MergedRaw(srcRouting, srcOutbounds)

        // Нужен ли direct-outbound: клиентские direct-правила требуют freedom.
        val needsDirect = clientRules.any { rule ->
            (rule as? JsonObject)?.get("outboundTag").str() == directTagFor(srcOutbounds)
        }
        val outbounds: JsonElement =
            if (needsDirect && findFreedomTag(srcOutbounds) == null) {
                buildJsonArray {
                    srcOutbounds.forEach { add(it) }
                    addJsonObject {
                        put("tag", CLIENT_DIRECT_TAG)
                        put("protocol", "freedom")
                    }
                }
            } else {
                srcOutbounds
            }

        val srcRules = srcRouting?.get("rules")?.jsonArray ?: JsonArray(emptyList())
        val mergedRouting = buildJsonObject {
            // Сохраняем все поля серверного routing (balancers, domainStrategy…).
            srcRouting?.forEach { (k, v) -> if (k != "rules") put(k, v) }
            if (srcRouting?.get("domainStrategy") == null) {
                put("domainStrategy", "IPIfNonMatch")
            }
            putJsonArray("rules") {
                clientRules.forEach { add(it) }
                srcRules.forEach { add(it) }
            }
        }
        return MergedRaw(mergedRouting, outbounds)
    }

    /**
     * Клиентские доменные правила для raw-конфига: та же логика, что во 2-3
     * шагах [buildRouting], но таргеты берутся из самого конфига подписки.
     */
    private fun buildClientDomainRules(
        routing: RoutingSettings,
        srcRouting: JsonObject?,
        srcOutbounds: JsonArray,
    ): List<JsonElement> {
        val rules = mutableListOf<JsonElement>()
        val directTag = directTagFor(srcOutbounds)
        val proxyTarget = proxyTargetFor(srcRouting, srcOutbounds)

        fun domainRule(target: RuleTarget, domains: List<String>) = buildJsonObject {
            put("type", "field")
            when (target) {
                is RuleTarget.Outbound -> put("outboundTag", target.tag)
                is RuleTarget.Balancer -> put("balancerTag", target.tag)
            }
            putJsonArray("domain") { domains.forEach { add(it) } }
        }

        // 1) пользовательский список доменов (приоритет над пресетами).
        val sites = routing.sites.filter { it.isNotBlank() }
        if (routing.siteMode != SiteRoutingMode.OFF && sites.isNotEmpty()) {
            val target = if (routing.siteMode == SiteRoutingMode.PROXY) proxyTarget
            else RuleTarget.Outbound(directTag)
            if (target != null) {
                rules += domainRule(target, sites.map { "domain:$it" })
            }
        }

        // 2) proxy-исключения direct-пресетов (выше самих пресетов).
        val exceptions = routing.sitePresets.flatMap { it.proxyExceptions }.distinct()
        if (exceptions.isNotEmpty() && proxyTarget != null) {
            rules += domainRule(proxyTarget, exceptions.map { "domain:$it" })
        }

        // 3) пресеты.
        routing.sitePresets.forEach { preset ->
            val domains = when (preset) {
                SitePreset.RU_BYPASS -> SitePreset.RU_SERVICES.domains
                else -> preset.domains
            }
            if (domains.isEmpty() && preset.regexps.isEmpty()) return@forEach
            val target = if (preset.direction == SiteRoutingMode.PROXY) proxyTarget
            else RuleTarget.Outbound(directTag)
            if (target == null) return@forEach
            rules += domainRule(
                target,
                preset.regexps.map { "regexp:$it" } + domains.map { "domain:$it" },
            )
        }
        return rules
    }

    /** Куда направлять правило: конкретный outbound или balancer. */
    private sealed interface RuleTarget {
        data class Outbound(val tag: String) : RuleTarget
        data class Balancer(val tag: String) : RuleTarget
    }

    /** Тег freedom-outbound конфига, либо null если его нет. */
    private fun findFreedomTag(outbounds: JsonArray): String? =
        outbounds.firstNotNullOfOrNull { ob ->
            val obj = ob as? JsonObject ?: return@firstNotNullOfOrNull null
            if (obj["protocol"].str() == "freedom") obj["tag"].str() else null
        }

    /** Тег для клиентских direct-правил (freedom из конфига или добавляемый). */
    private fun directTagFor(outbounds: JsonArray): String =
        findFreedomTag(outbounds) ?: CLIENT_DIRECT_TAG

    /**
     * Дефолтный путь трафика raw-конфига для клиентских proxy-правил:
     * balancerTag первого balancer'а (автовыбор MAIN→WHITE), иначе — тег
     * первого proxy-outbound. null — направить некуда (правило пропускается,
     * трафик и так идёт по дефолту).
     */
    private fun proxyTargetFor(srcRouting: JsonObject?, srcOutbounds: JsonArray): RuleTarget? {
        val balancerTag = srcRouting?.get("balancers")?.jsonArray
            ?.firstNotNullOfOrNull { (it as? JsonObject)?.get("tag").str() }
        if (balancerTag != null) return RuleTarget.Balancer(balancerTag)
        val proxyTag = srcOutbounds.firstNotNullOfOrNull { ob ->
            val obj = ob as? JsonObject ?: return@firstNotNullOfOrNull null
            if (obj["protocol"].str() in RAW_PROXY_PROTOCOLS) obj["tag"].str() else null
        }
        return proxyTag?.let { RuleTarget.Outbound(it) }
    }

    private fun JsonElement?.str(): String? = (this as? JsonPrimitive)?.contentOrNull

    /** TUN-инбаунд форка xray-core: fd берётся ядром из env xray.tun.fd. */
    private fun buildTunInbound(mtu: Int) = buildJsonObject {
        put("tag", "tun")
        put("protocol", "tun")
        putJsonObject("settings") {
            put("name", "tun0")
            put("mtu", mtu)
            putJsonArray("gateway") { add("$TUN_ADDRESS/30") }
            putJsonArray("dns") { add("1.1.1.1"); add("8.8.8.8") }
        }
        putJsonObject("sniffing") {
            put("enabled", true)
            putJsonArray("destOverride") { add("http"); add("tls"); add("quic") }
        }
    }

    /**
     * Конфиг для прокси-пинга: локальный SOCKS-inbound на 127.0.0.1:[socksPort]
     * и outbound «proxy» под профиль сервера, без TUN/routing. Ядро поднимается
     * без TUN (startLoop с fd=0), клиент гонит HTTP через этот SOCKS-порт своим
     * методом (GET/HEAD) и режимом (Default/Double/Keepalive) — так меряет
     * прокси-пинг Happ: end-to-end через протокол (VLESS/Reality).
     */
    fun buildProxyPingConfig(config: EngineConfig.Vless, socksPort: Int): String {
        val root = buildJsonObject {
            putJsonObject("log") { put("loglevel", "none") }
            putJsonArray("inbounds") {
                addJsonObject {
                    put("tag", "socks")
                    put("protocol", "socks")
                    put("listen", "127.0.0.1")
                    put("port", socksPort)
                    putJsonObject("settings") {
                        put("auth", "noauth")
                        put("udp", false)
                    }
                }
            }
            putJsonArray("outbounds") {
                add(buildVlessOutbound(config))
            }
        }
        return json.encodeToString(JsonObject.serializer(), root)
    }

    /**
     * Строит блок routing по режиму:
     *  - приватные сети всегда direct (первым правилом);
     *  - ALL: остальной трафик в proxy (нет доп. правил);
     *  - BYPASS_RU: российские домены/ip — direct, остальное proxy;
     *  - CUSTOM: правила из загруженного внешнего конфига (rulesJson);
     *    если правил нет — ведём себя как ALL.
     */
    private fun buildRouting(routing: RoutingSettings) = buildJsonObject {
        put("domainStrategy", "IPIfNonMatch")
        putJsonArray("rules") {
            // 1) Приватные/локальные сети — напрямую, мимо прокси.
            // Явные CIDR (не geoip:private), чтобы не требовать geoip.dat.
            addJsonObject {
                put("type", "field")
                put("outboundTag", "direct")
                putJsonArray("ip") {
                    add("10.0.0.0/8")
                    add("172.16.0.0/12")
                    add("192.168.0.0/16")
                    add("127.0.0.0/8")
                    add("::1/128")
                    add("fc00::/7")
                    add("fe80::/10")
                }
            }
            // 2) Пользовательские домены (выше пресетов и режима — приоритет).
            //    PROXY: перечисленные домены — через VPN; DIRECT: напрямую.
            //    Правило `domain: "example.com"` матчит и поддомены (Xray-семантика).
            val sites = routing.sites.filter { it.isNotBlank() }
            if (routing.siteMode != SiteRoutingMode.OFF && sites.isNotEmpty()) {
                addJsonObject {
                    put("type", "field")
                    put("outboundTag", if (routing.siteMode == SiteRoutingMode.PROXY) "proxy" else "direct")
                    putJsonArray("domain") { sites.forEach { add("domain:$it") } }
                }
            }
            // 3) Пресеты доменной маршрутизации (multi-select). Каждый пресет
            //    несёт своё направление (direct/proxy); RU_BYPASS дополнительно
            //    подхватывает список RU_SERVICES.domains + regexp-зоны.
            //    Сначала — proxy-исключения direct-пресетов (Сбер/Т-Банк через
            //    VPN даже при обходе .ru): первое совпавшее правило побеждает.
            val exceptions = routing.sitePresets.flatMap { it.proxyExceptions }.distinct()
            if (exceptions.isNotEmpty()) {
                addJsonObject {
                    put("type", "field")
                    put("outboundTag", "proxy")
                    putJsonArray("domain") { exceptions.forEach { add("domain:$it") } }
                }
            }
            routing.sitePresets.forEach { preset ->
                val domains = when (preset) {
                    SitePreset.RU_BYPASS -> SitePreset.RU_SERVICES.domains
                    else -> preset.domains
                }
                if (domains.isEmpty() && preset.regexps.isEmpty()) return@forEach
                addJsonObject {
                    put("type", "field")
                    put("outboundTag", if (preset.direction == SiteRoutingMode.PROXY) "proxy" else "direct")
                    putJsonArray("domain") {
                        preset.regexps.forEach { add("regexp:$it") }
                        domains.forEach { add("domain:$it") }
                    }
                }
            }
            when (routing.mode) {
                RoutingMode.ALL -> { /* всё остальное — proxy по умолчанию */ }
                RoutingMode.BYPASS_RU -> {
                    // Российские домены — напрямую. По доменам (не geoip:ru),
                    // чтобы не требовать geoip.dat в ассетах ядра.
                    addJsonObject {
                        put("type", "field")
                        put("outboundTag", "direct")
                        putJsonArray("domain") {
                            add("regexp:.*\\.ru$")
                            add("regexp:.*\\.su$")
                            add("regexp:.*\\.рф$")
                            RU_DOMAINS.forEach { add("domain:$it") }
                        }
                    }
                }
                RoutingMode.CUSTOM -> {
                    // Подмешиваем правила из внешнего конфига (если загружены).
                    routing.rulesJson
                        ?.let { runCatching { json.parseToJsonElement(it) as? JsonArray }.getOrNull() }
                        ?.forEach { add(it) }
                }
            }
        }
    }

    private fun buildVlessOutbound(config: EngineConfig.Vless) = buildJsonObject {
        put("tag", "proxy")
        put("protocol", "vless")
        putJsonObject("settings") {
            putJsonArray("vnext") {
                addJsonObject {
                    put("address", config.address)
                    put("port", config.port)
                    putJsonArray("users") {
                        addJsonObject {
                            put("id", config.uuid)
                            put("encryption", "none")
                            config.flow?.let { put("flow", it) }
                        }
                    }
                }
            }
        }
        put("streamSettings", buildStreamSettings(config))
    }

    private fun buildStreamSettings(config: EngineConfig.Vless) = buildJsonObject {
        val network = when (config.transport) {
            is Transport.Tcp -> "tcp"
            is Transport.Ws -> "ws"
            is Transport.Grpc -> "grpc"
            is Transport.Xhttp -> "xhttp"
        }
        put("network", network)

        // --- security ---
        when (val sec = config.security) {
            is Security.None -> put("security", "none")
            is Security.Tls -> {
                put("security", "tls")
                putJsonObject("tlsSettings") {
                    sec.sni?.let { put("serverName", it) }
                    sec.fingerprint?.let { put("fingerprint", it) }
                    put("allowInsecure", sec.allowInsecure)
                    sec.alpn?.let { alpn ->
                        putJsonArray("alpn") { alpn.forEach { add(it) } }
                    }
                }
            }
            is Security.Reality -> {
                put("security", "reality")
                putJsonObject("realitySettings") {
                    sec.sni?.let { put("serverName", it) }
                    sec.fingerprint?.let { put("fingerprint", it) }
                    put("publicKey", sec.publicKey)
                    sec.shortId?.let { put("shortId", it) }
                    sec.spiderX?.let { put("spiderX", it) }
                }
            }
        }

        // --- transport-specific settings ---
        when (val t = config.transport) {
            is Transport.Tcp -> { /* по умолчанию */ }
            is Transport.Ws -> putJsonObject("wsSettings") {
                t.path?.let { put("path", it) }
                t.host?.let {
                    putJsonObject("headers") { put("Host", it) }
                }
            }
            is Transport.Grpc -> putJsonObject("grpcSettings") {
                t.serviceName?.let { put("serviceName", it) }
            }
            is Transport.Xhttp -> putJsonObject("xhttpSettings") {
                t.path?.let { put("path", it) }
                t.host?.let { put("host", it) }
                t.mode?.let { put("mode", it) }
                // extra (xmux/xPadding/session/seq/uplink/…) — пробрасываем как
                // есть: сервер белых списков сверяет эти поля, без них рвёт связь.
                t.extra?.let { put("extra", it) }
            }
        }
    }

    private companion object {
        const val DEFAULT_MTU = 1500
        const val TUN_ADDRESS = "10.10.0.2"

        /** Тег freedom-outbound, добавляемого клиентом в raw-конфиг для direct-правил. */
        const val CLIENT_DIRECT_TAG = "direct-client"

        /** Протоколы, считающиеся proxy-outbound'ами в raw-конфиге подписки. */
        val RAW_PROXY_PROTOCOLS = setOf("vless", "vmess", "trojan", "shadowsocks", "hysteria", "hysteria2")

        /**
         * Популярные российские сервисы для режима «обход РФ» (direct).
         * Дополняет regexp по зонам .ru/.su/.рф — на случай сервисов в других
         * зонах (.com/.net). Список компактный; полноценные списки грузятся
         * через режим CUSTOM (внешний конфиг правил).
         */
        val RU_DOMAINS = listOf(
            "vk.com", "vk.ru", "vk.cc", "mail.ru", "yandex.ru", "ya.ru",
            "gosuslugi.ru", "sberbank.ru", "tinkoff.ru", "alfabank.ru",
            "ozon.ru", "wildberries.ru", "wb.ru", "avito.ru", "2gis.ru",
            "kinopoisk.ru", "rutube.ru", "dzen.ru", "hh.ru",
        )
    }
}
