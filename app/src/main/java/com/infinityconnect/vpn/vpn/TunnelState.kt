package com.infinityconnect.vpn.vpn

/** Состояние VPN-туннеля для UI. */
sealed interface TunnelState {
    data object Disconnected : TunnelState
    data object Connecting : TunnelState
    data object Connected : TunnelState
    data object Disconnecting : TunnelState

    /** Ошибка подключения/разрыв. [message] — человекочитаемое описание. */
    data class Error(val message: String) : TunnelState
}

/** Статистика сессии: скорость и суммарный трафик, время. */
data class TunnelStats(
    val uploadBytesPerSec: Long = 0,
    val downloadBytesPerSec: Long = 0,
    val totalUploadBytes: Long = 0,
    val totalDownloadBytes: Long = 0,
    /** Секунды с момента установления соединения. */
    val sessionSeconds: Long = 0,
)
