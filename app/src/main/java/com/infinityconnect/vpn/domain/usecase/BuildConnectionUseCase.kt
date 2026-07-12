package com.infinityconnect.vpn.domain.usecase

import com.infinityconnect.vpn.data.remote.dto.ConfigDto
import com.infinityconnect.vpn.domain.engine.EngineConfig
import com.infinityconnect.vpn.domain.engine.Security
import com.infinityconnect.vpn.domain.engine.Transport
import com.infinityconnect.vpn.domain.model.AppError
import com.infinityconnect.vpn.domain.model.AppResult
import com.infinityconnect.vpn.domain.model.VpnKey
import com.infinityconnect.vpn.domain.repository.ConfigRepository
import com.infinityconnect.vpn.domain.repository.KeysRepository
import com.infinityconnect.vpn.domain.repository.SubscriptionRepository
import com.infinityconnect.vpn.domain.subscription.SubscriptionParser
import javax.inject.Inject

/**
 * Готовит [EngineConfig] для подключения к выбранному серверу ключа.
 *
 * Стратегия (по ТЗ): subscription_url — первичный источник (единственный
 * надёжный путь для XHTTP и Hysteria2), /v1/config — вспомогательный для
 * VLESS-метаданных, когда подписка недоступна.
 *
 *  1. Есть subscription_url → грузим тело, парсим в список профилей, берём по
 *     индексу сервера.
 *  2. Иначе → /v1/config: если есть raw_uri, парсим его; иначе собираем VLESS
 *     из отдельных полей DTO.
 */
class BuildConnectionUseCase @Inject constructor(
    private val keysRepository: KeysRepository,
    private val configRepository: ConfigRepository,
    private val subscriptionRepository: SubscriptionRepository,
    private val parser: SubscriptionParser,
) {
    suspend operator fun invoke(keyId: Long, serverIndex: Int): AppResult<EngineConfig> {
        // 1. Получаем ключ (нужен subscription_url).
        val keyResult = keysRepository.key(keyId)
        val key = when (keyResult) {
            is AppResult.Success -> keyResult.data
            is AppResult.Failure -> return keyResult
        }

        // 2. Первичный путь: подписка.
        key.subscriptionUrl?.takeIf { it.isNotBlank() }?.let { subUrl ->
            fromSubscription(subUrl, serverIndex)?.let { return AppResult.Success(it) }
        }

        // 3. Fallback: /v1/config (только VLESS).
        return fromServerConfig(key, serverIndex)
    }

    /** Пытается получить профиль из подписки; null — если не удалось. */
    private suspend fun fromSubscription(
        subscriptionUrl: String,
        serverIndex: Int,
    ): EngineConfig? {
        // Кэш подписки: при подключении используем уже загруженное тело.
        val body = when (val r = subscriptionRepository.fetch(subscriptionUrl)) {
            is AppResult.Success -> r.data
            is AppResult.Failure -> return null
        }
        val configs = parser.parseSubscription(body.raw)
        if (configs.isEmpty()) return null
        // Индекс сервера соответствует порядку в подписке; при выходе за границы
        // берём первый профиль как безопасный дефолт.
        return configs.getOrElse(serverIndex) { configs.first() }
    }

    /** Собирает VLESS-профиль из ответа /v1/config. */
    private suspend fun fromServerConfig(
        key: VpnKey,
        serverIndex: Int,
    ): AppResult<EngineConfig> {
        val dto = when (val r = configRepository.config(key.id, serverIndex)) {
            is AppResult.Success -> r.data
            is AppResult.Failure -> return r
        }

        // Приоритетно пытаемся распарсить raw_uri (в нём вся правда о транспорте).
        dto.rawUri?.takeIf { it.isNotBlank() }?.let { uri ->
            parser.parseSingleUri(uri)?.let { return AppResult.Success(it) }
        }

        // Иначе — собираем VLESS из отдельных полей DTO.
        return buildVlessFromDto(dto, key)
    }

    private fun buildVlessFromDto(dto: ConfigDto, key: VpnKey): AppResult<EngineConfig> {
        val address = dto.serverAddress ?: key.serverAddress
            ?: return AppResult.Failure(AppError.Parse("Нет адреса сервера в конфиге"))
        val port = dto.serverPort
            ?: return AppResult.Failure(AppError.Parse("Нет порта сервера в конфиге"))
        val uuid = dto.uuid
            ?: return AppResult.Failure(AppError.Parse("Нет UUID в конфиге"))

        val transport = when (dto.network?.lowercase()) {
            "ws", "websocket" -> Transport.Ws(dto.path, dto.host)
            "grpc" -> Transport.Grpc(dto.path)
            "xhttp", "splithttp" -> Transport.Xhttp(dto.path, dto.host, null)
            else -> Transport.Tcp
        }

        val security = when (dto.security?.lowercase()) {
            "reality" -> {
                val pbk = dto.publicKey
                    ?: return AppResult.Failure(AppError.Parse("Reality без public_key"))
                Security.Reality(
                    sni = dto.sni,
                    fingerprint = dto.fingerprint ?: "chrome",
                    publicKey = pbk,
                    shortId = dto.shortId,
                )
            }
            "tls" -> Security.Tls(sni = dto.sni, fingerprint = dto.fingerprint)
            else -> Security.None
        }

        return AppResult.Success(
            EngineConfig.Vless(
                remark = key.name,
                address = address,
                port = port,
                uuid = uuid,
                transport = transport,
                security = security,
                flow = dto.flow?.takeIf { it.isNotBlank() },
            ),
        )
    }
}
