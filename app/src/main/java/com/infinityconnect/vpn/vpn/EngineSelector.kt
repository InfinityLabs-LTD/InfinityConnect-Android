package com.infinityconnect.vpn.vpn

import com.infinityconnect.vpn.domain.engine.EngineConfig
import com.infinityconnect.vpn.vpn.hysteria2.Hysteria2Engine
import com.infinityconnect.vpn.vpn.xray.XrayEngine
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Выбирает подходящий [VpnEngine] по типу профиля. VLESS → Xray,
 * Hysteria2 → Hysteria2Engine.
 */
@Singleton
class EngineSelector @Inject constructor(
    private val xrayEngine: XrayEngine,
    private val hysteria2Engine: Hysteria2Engine,
) {
    private val engines: List<VpnEngine> = listOf(xrayEngine, hysteria2Engine)

    /** @throws IllegalStateException если движок под профиль не найден. */
    fun select(config: EngineConfig): VpnEngine =
        engines.firstOrNull { it.supports(config) }
            ?: error("Нет движка для протокола ${config.protocol}")
}
