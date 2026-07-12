package com.infinityconnect.vpn.vpn.hysteria2

import android.net.VpnService
import android.util.Log
import hysteria2.Hysteria2
import hysteria2.Protector
import hysteria2.Tunnel
import hysteria2.TunnelCallbackHandler

/**
 * Мост к нативному клиенту Hysteria2 (обёртка github.com/apernet/hysteria,
 * собрана gomobile; пакет hysteria2). Прямые вызовы Go-обёртки; компиляция —
 * против стаб-модуля libhysteria2-stub, в рантайме работает реальный AAR.
 *
 * Клиент работает поверх уже установленного TUN: fd передаётся в ядро через
 * [Hysteria2.newTunnel], встроенный gVisor-стек (sing-tun) заворачивает трафик
 * TUN в QUIC-соединение. Собственный UDP-сокет QUIC клиент защищает через
 * [Protector] (VpnService.protect), иначе его трафик зациклится в TUN.
 */
class Hysteria2CoreBridge(
    private val service: VpnService,
    private val onCoreShutdown: () -> Unit,
) {
    private var tunnel: Tunnel? = null

    /** Протектор сокетов: делегирует VpnService.protect(fd). */
    private val protector = Protector { fd ->
        runCatching { service.protect(fd.toInt()) }.getOrDefault(false)
    }

    /** Регистрирует протектор в Go-слое. Вызывать до [start]. */
    fun initEnv() {
        Hysteria2.setContext(protector)
        Log.i(TAG, "Hysteria2 env инициализирован, версия ядра: ${runCatching { Hysteria2.version() }.getOrDefault("?")}")
    }

    /**
     * Запускает клиент с JSON-конфигом, TUN-дескриптором, MTU и адресом TUN.
     * [tunCidr] — адрес интерфейса с префиксом (например "10.10.0.1/30"); нужен
     * системному стеку sing-tun и должен согласовываться с адресом, который
     * VpnService выставил на TUN.
     * @throws Exception при ошибке запуска (перехватывается движком → Error).
     */
    fun start(configJson: String, tunFd: Int, mtu: Int, tunCidr: String) {
        val handler = object : TunnelCallbackHandler {
            override fun onShutdown() {
                // Ядро сообщило о завершении — уведомляем сервис.
                runCatching { onCoreShutdown() }
            }

            override fun onStatus(level: Long, message: String?) {
                if (!message.isNullOrBlank()) Log.d(TAG, "core status[$level]: $message")
            }
        }
        tunnel = Hysteria2.newTunnel(configJson, tunFd.toLong(), mtu.toLong(), tunCidr, handler)
        Log.i(TAG, "Hysteria2-клиент запущен (tunFd=$tunFd, mtu=$mtu, tun=$tunCidr)")
    }

    /** Останавливает клиент. Идемпотентно. */
    fun stop() {
        val t = tunnel ?: return
        runCatching { t.close() }
            .onFailure { Log.w(TAG, "Ошибка остановки клиента: ${it.message}") }
        tunnel = null
        Log.i(TAG, "Hysteria2-клиент остановлен")
    }

    /** Суммарный трафик (uplink, downlink) в байтах, либо null. */
    fun queryTraffic(): Pair<Long, Long>? {
        val t = tunnel ?: return null
        val up = runCatching { t.uplinkBytes() }.getOrNull() ?: return null
        val down = runCatching { t.downlinkBytes() }.getOrNull() ?: 0L
        return up to down
    }

    private companion object {
        const val TAG = "Hysteria2CoreBridge"
    }
}
