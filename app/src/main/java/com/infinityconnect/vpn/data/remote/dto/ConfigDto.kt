package com.infinityconnect.vpn.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Ответ GET /v1/config/servers?key_id=... — список серверов ключа. */
@Serializable
data class ServersResponseDto(
    @SerialName("servers") val servers: List<ServerEntryDto> = emptyList(),
)

@Serializable
data class ServerEntryDto(
    @SerialName("index") val index: Int,
    @SerialName("name") val name: String? = null,
    @SerialName("flag") val flag: String? = null,
    @SerialName("server_address") val serverAddress: String? = null,
)

/**
 * Ответ GET /v1/config?key_id=...&server=... — разобранный конфиг.
 *
 * Сервер надёжно разбирает только VLESS. Поля Reality (public_key/short_id/
 * fingerprint/flow/sni) и транспорта присутствуют не всегда; для XHTTP и
 * Hysteria2 первичным источником служит raw_uri / subscription_url (парсинг
 * на клиенте). Поэтому все поля, кроме raw_uri, опциональны.
 */
@Serializable
data class ConfigDto(
    @SerialName("server_address") val serverAddress: String? = null,
    @SerialName("server_port") val serverPort: Int? = null,
    @SerialName("uuid") val uuid: String? = null,
    @SerialName("protocol") val protocol: String? = null,
    @SerialName("network") val network: String? = null,
    @SerialName("security") val security: String? = null,
    @SerialName("sni") val sni: String? = null,
    @SerialName("fingerprint") val fingerprint: String? = null,
    @SerialName("public_key") val publicKey: String? = null,
    @SerialName("short_id") val shortId: String? = null,
    @SerialName("flow") val flow: String? = null,
    @SerialName("path") val path: String? = null,
    @SerialName("host") val host: String? = null,
    @SerialName("raw_uri") val rawUri: String? = null,
)
