package com.infinityconnect.vpn.vpn.xray

import android.util.Log

/**
 * Адаптер к tun2socks — компоненту, который читает пакеты из TUN и заворачивает
 * их в локальный SOCKS-прокси (поднятый ядром Xray). Реализуется, как правило,
 * библиотекой hev-socks5-tunnel (собирается в .so/AAR) либо аналогом.
 *
 * Как и [XrayCoreBridge], вызовы идут через рефлексию — проект компилируется
 * без нативной библиотеки. Точные имена класса/методов зависят от выбранной
 * сборки; ниже — заготовка контракта, замените под свою библиотеку.
 *
 * КОНТРАКТ (пример hev-socks5-tunnel):
 *   - TProxyStartService(configPath: String, tunFd: Int)
 *   - TProxyStopService()
 *
 * config (YAML) обычно указывает: mtu, адрес TUN и socks5 { address, port }.
 */
object Tun2SocksBridge {

    private const val TAG = "Tun2SocksBridge"
    private const val LIB_CLASS = "hev.htproxy.TProxyService" // при иной сборке — заменить

    val isAvailable: Boolean by lazy {
        runCatching { Class.forName(LIB_CLASS) }.isSuccess
    }

    /**
     * Запускает tun2socks: TUN [tunFd] → SOCKS 127.0.0.1:[socksPort].
     * @throws Tun2SocksUnavailableException если библиотека не подключена.
     */
    fun start(tunFd: Int, socksPort: Int, mtu: Int) {
        val clazz = runCatching { Class.forName(LIB_CLASS) }.getOrElse {
            throw Tun2SocksUnavailableException(
                "tun2socks-библиотека не подключена. См. README (интеграция AAR).",
            )
        }
        runCatching {
            // Точная сигнатура зависит от сборки — здесь абстрактный вызов.
            val method = clazz.getMethod(
                "startTunnel",
                Int::class.javaPrimitiveType,
                Int::class.javaPrimitiveType,
                Int::class.javaPrimitiveType,
            )
            method.invoke(null, tunFd, socksPort, mtu)
            Log.i(TAG, "tun2socks запущен: fd=$tunFd → socks:$socksPort")
        }.onFailure { throw Tun2SocksUnavailableException("Ошибка запуска tun2socks: ${it.message}") }
    }

    fun stop() {
        val clazz = runCatching { Class.forName(LIB_CLASS) }.getOrNull() ?: return
        runCatching { clazz.getMethod("stopTunnel").invoke(null) }
            .onFailure { Log.w(TAG, "Ошибка остановки tun2socks: ${it.message}") }
    }
}

class Tun2SocksUnavailableException(message: String) : Exception(message)
