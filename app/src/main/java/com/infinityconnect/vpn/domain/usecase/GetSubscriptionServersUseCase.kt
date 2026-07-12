package com.infinityconnect.vpn.domain.usecase

import com.infinityconnect.vpn.domain.engine.EngineConfig
import com.infinityconnect.vpn.domain.engine.Security
import com.infinityconnect.vpn.domain.engine.Transport
import com.infinityconnect.vpn.domain.model.AppResult
import com.infinityconnect.vpn.domain.model.SubscriptionServer
import com.infinityconnect.vpn.domain.model.VpnProtocol
import com.infinityconnect.vpn.domain.model.map
import com.infinityconnect.vpn.domain.repository.KeysRepository
import com.infinityconnect.vpn.domain.repository.SubscriptionRepository
import com.infinityconnect.vpn.domain.subscription.SubscriptionParser
import javax.inject.Inject

/**
 * Загружает серверы подписки ключа для отображения списком (стиль Happ):
 * имя, флаг, метаданные (VLESS | TCP | Reality | JSON) и заготовка под пинг.
 *
 * Источник — subscription_url ключа (с HWID-заголовками), парсинг на клиенте
 * в [EngineConfig], далее маппинг в [SubscriptionServer].
 */
class GetSubscriptionServersUseCase @Inject constructor(
    private val keysRepository: KeysRepository,
    private val subscriptionRepository: SubscriptionRepository,
    private val parser: SubscriptionParser,
) {
    /**
     * @param forceRefresh обойти кэш и перезагрузить подписку (кнопка обновления).
     *   По умолчанию false — серверы берутся из кэша (без спиннера при выборе ключа).
     */
    suspend operator fun invoke(
        keyId: Long,
        forceRefresh: Boolean = false,
    ): AppResult<List<SubscriptionServer>> {
        val keyResult = keysRepository.key(keyId)
        val key = when (keyResult) {
            is AppResult.Success -> keyResult.data
            is AppResult.Failure -> return keyResult
        }
        val subUrl = key.subscriptionUrl?.takeIf { it.isNotBlank() }
            ?: return AppResult.Success(emptyList())

        return subscriptionRepository.fetch(subUrl, forceRefresh).map { body ->
            parser.parseSubscription(body.raw).mapIndexed { index, cfg ->
                cfg.toSubscriptionServer(index)
            }
        }
    }
}

/** Маппинг профиля движка в отображаемый сервер с meta-строкой. */
private fun EngineConfig.toSubscriptionServer(index: Int): SubscriptionServer {
    val parts = buildList {
        add(protocolLabel(protocol))
        when (this@toSubscriptionServer) {
            is EngineConfig.Vless -> {
                add(transportLabel(transport))
                securityLabel(security)?.let { add(it) }
            }
            is EngineConfig.Hysteria2 -> add("UDP")
        }
    }
    val flag = extractFlag(remark)
    return SubscriptionServer(
        index = index,
        // Имя без ведущего флага — флаг показывается отдельной иконкой слева.
        name = stripLeadingFlag(remark).ifBlank { remark },
        flag = flag,
        address = address,
        port = port,
        protocol = protocol,
        meta = parts.joinToString(" | "),
        config = this,
    )
}

/** Убирает ведущий emoji-флаг (пару региональных индикаторов) и пробелы. */
private fun stripLeadingFlag(remark: String): String {
    val trimmed = remark.trimStart()
    if (trimmed.isEmpty()) return trimmed
    val first = trimmed.codePointAt(0)
    if (first !in 0x1F1E6..0x1F1FF) return trimmed
    var idx = Character.charCount(first)
    if (idx < trimmed.length) {
        val second = trimmed.codePointAt(idx)
        if (second in 0x1F1E6..0x1F1FF) idx += Character.charCount(second)
    }
    return trimmed.substring(idx).trimStart()
}

private fun protocolLabel(p: VpnProtocol): String = when (p) {
    VpnProtocol.VLESS -> "VLESS"
    VpnProtocol.HYSTERIA2 -> "Hysteria2"
    VpnProtocol.UNKNOWN -> "—"
}

private fun transportLabel(t: Transport): String = when (t) {
    is Transport.Tcp -> "TCP"
    is Transport.Ws -> "WS"
    is Transport.Grpc -> "gRPC"
    is Transport.Xhttp -> "XHTTP"
}

private fun securityLabel(s: Security): String? = when (s) {
    is Security.Reality -> "Reality"
    is Security.Tls -> "TLS"
    is Security.None -> null
}

/** Извлекает ведущий emoji-флаг из remark (если есть). */
private fun extractFlag(remark: String): String? {
    val trimmed = remark.trimStart()
    if (trimmed.isEmpty()) return null
    // Региональные индикаторы (флаги) — пары кодовых точек U+1F1E6..U+1F1FF.
    val first = trimmed.codePointAt(0)
    if (first in 0x1F1E6..0x1F1FF) {
        val flag = StringBuilder().appendCodePoint(first)
        val nextIdx = Character.charCount(first)
        if (nextIdx < trimmed.length) {
            val second = trimmed.codePointAt(nextIdx)
            if (second in 0x1F1E6..0x1F1FF) flag.appendCodePoint(second)
        }
        return flag.toString()
    }
    return null
}
