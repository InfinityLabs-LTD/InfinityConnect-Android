package com.infinityconnect.vpn.vpn

import com.infinityconnect.vpn.domain.engine.EngineConfig

/**
 * Абстракция VPN-движка. Реализации: [com.infinityconnect.vpn.vpn.xray.XrayEngine]
 * (VLESS/Reality/XHTTP) и [com.infinityconnect.vpn.vpn.hysteria2.Hysteria2Engine].
 *
 * Движок работает поверх уже установленного TUN-интерфейса: [tunFd] — файловый
 * дескриптор из VpnService.Builder.establish(). Внутри движок разворачивает
 * стек (для Xray — tun2socks → локальный SOCKS Xray).
 */
interface VpnEngine {

    /** Поддерживает ли движок данный профиль. */
    fun supports(config: EngineConfig): Boolean

    /**
     * Запускает туннель. Блокирующая инициализация; при ошибке бросает
     * исключение (перехватывается сервисом → TunnelState.Error).
     *
     * @param config профиль сервера.
     * @param tunFd дескриптор TUN-интерфейса.
     * @param mtu MTU туннеля.
     */
    fun start(config: EngineConfig, tunFd: Int, mtu: Int)

    /** Останавливает туннель и освобождает ресурсы. Идемпотентно. */
    fun stop()

    /** Текущая статистика (мгновенные скорости/трафик), либо null если недоступна. */
    fun queryStats(): TunnelStats?
}
