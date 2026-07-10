package com.infinityconnect.vpn.vpn.hysteria2

import com.infinityconnect.vpn.domain.engine.EngineConfig
import com.infinityconnect.vpn.vpn.TunnelStats
import com.infinityconnect.vpn.vpn.VpnEngine
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Движок Hysteria2 — ЗАГЛУШКА.
 *
 * Полная реализация требует официальной библиотеки hysteria2, собранной в AAR
 * через gomobile (см. README). Контракт интеграции:
 *  1. Собрать конфиг Hysteria2 из [EngineConfig.Hysteria2] (server/auth/tls/obfs/
 *     bandwidth) в формате, ожидаемом библиотекой (обычно YAML/JSON).
 *  2. Запустить клиент hysteria2 поверх переданного TUN fd (библиотека сама
 *     читает/пишет пакеты в TUN — tun2socks не нужен).
 *  3. Пробросить статистику в [queryStats].
 *
 * До подключения AAR метод [start] бросает исключение с понятным сообщением —
 * UI покажет «протокол пока не поддерживается».
 */
@Singleton
class Hysteria2Engine @Inject constructor() : VpnEngine {

    override fun supports(config: EngineConfig): Boolean = config is EngineConfig.Hysteria2

    override fun start(config: EngineConfig, tunFd: Int, mtu: Int) {
        throw Hysteria2NotIntegratedException(
            "Движок Hysteria2 ещё не интегрирован. Подключите hysteria2 AAR (см. README).",
        )
    }

    override fun stop() {
        // Нечего останавливать — движок не запускается.
    }

    override fun queryStats(): TunnelStats? = null
}

/** Hysteria2-движок не интегрирован (AAR не подключён/реализация не готова). */
class Hysteria2NotIntegratedException(message: String) : Exception(message)
