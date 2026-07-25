package com.infinityconnect.vpn.vpn

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.core.content.ContextCompat

/**
 * Мост состояния туннеля между процессом VPN-сервиса (`:vpn`) и UI-процессом.
 *
 * Сервис живёт в отдельном процессе (см. `android:process=":vpn"` в манифесте —
 * там же причина: два Go-рантайма не сосуществуют в одном адресном пространстве),
 * поэтому [VpnStateHolder] у процессов РАЗНЫЕ: singleton'ы Hilt на процесс, а не
 * на приложение. Сервис вещает своё состояние броадкастом, приёмник в UI-процессе
 * укладывает его в локальный holder — для ViewModel'ей ничего не меняется.
 *
 * Направление одно: сервис → UI. Команды в обратную сторону идут Intent'ами через
 * [VpnController], как и раньше.
 */
object VpnStateBridge {

    private const val ACTION_STATE = "com.infinityconnect.vpn.STATE"

    private const val EXTRA_KIND = "kind"
    private const val EXTRA_ERROR = "error"
    private const val EXTRA_SERVER_NAME = "server_name"

    // Активное подключение: нужно UI, чтобы переподключиться при смене настроек.
    private const val EXTRA_CONN_KEY_ID = "conn_key_id"
    private const val EXTRA_CONN_SERVER_INDEX = "conn_server_index"
    private const val EXTRA_CONN_SERVER_NAME = "conn_server_name"
    private const val EXTRA_HAS_CONN = "has_conn"

    private const val EXTRA_UP_SPEED = "up_speed"
    private const val EXTRA_DOWN_SPEED = "down_speed"
    private const val EXTRA_UP_TOTAL = "up_total"
    private const val EXTRA_DOWN_TOTAL = "down_total"
    private const val EXTRA_SESSION_SEC = "session_sec"

    private const val KIND_DISCONNECTED = 0
    private const val KIND_CONNECTING = 1
    private const val KIND_CONNECTED = 2
    private const val KIND_DISCONNECTING = 3
    private const val KIND_ERROR = 4

    /**
     * Вещает полный снимок состояния. Именно снимок, а не дельта: broadcast может
     * потеряться (процесс UI мог быть не запущен), и следующая же публикация
     * приводит UI в актуальное состояние без накопления рассинхрона.
     */
    fun publish(
        context: Context,
        state: TunnelState,
        stats: TunnelStats,
        activeServerName: String?,
        activeConnection: VpnStateHolder.ActiveConnection?,
    ) {
        val intent = Intent(ACTION_STATE).apply {
            // Явный пакет: broadcast остаётся внутри приложения.
            setPackage(context.packageName)
            putExtra(EXTRA_KIND, state.toKind())
            if (state is TunnelState.Error) putExtra(EXTRA_ERROR, state.message)
            putExtra(EXTRA_SERVER_NAME, activeServerName)

            putExtra(EXTRA_HAS_CONN, activeConnection != null)
            activeConnection?.let {
                putExtra(EXTRA_CONN_KEY_ID, it.keyId)
                putExtra(EXTRA_CONN_SERVER_INDEX, it.serverIndex)
                putExtra(EXTRA_CONN_SERVER_NAME, it.serverName)
            }

            putExtra(EXTRA_UP_SPEED, stats.uploadBytesPerSec)
            putExtra(EXTRA_DOWN_SPEED, stats.downloadBytesPerSec)
            putExtra(EXTRA_UP_TOTAL, stats.totalUploadBytes)
            putExtra(EXTRA_DOWN_TOTAL, stats.totalDownloadBytes)
            putExtra(EXTRA_SESSION_SEC, stats.sessionSeconds)
        }
        context.sendBroadcast(intent)
    }

    /**
     * Подписывает [holder] в UI-процессе на состояние, публикуемое сервисом.
     * Вызывается один раз при старте приложения; отписка не нужна — приёмник
     * живёт столько же, сколько процесс.
     */
    fun subscribe(context: Context, holder: VpnStateHolder) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) {
                if (intent?.action != ACTION_STATE) return
                holder.updateState(intent.toState())
                holder.setActiveServer(intent.getStringExtra(EXTRA_SERVER_NAME))
                holder.setActiveConnection(intent.toActiveConnection())
                holder.updateStats(intent.toStats())
            }
        }
        ContextCompat.registerReceiver(
            context,
            receiver,
            IntentFilter(ACTION_STATE),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
    }

    private fun TunnelState.toKind(): Int = when (this) {
        TunnelState.Disconnected -> KIND_DISCONNECTED
        TunnelState.Connecting -> KIND_CONNECTING
        TunnelState.Connected -> KIND_CONNECTED
        TunnelState.Disconnecting -> KIND_DISCONNECTING
        is TunnelState.Error -> KIND_ERROR
    }

    private fun Intent.toState(): TunnelState = when (getIntExtra(EXTRA_KIND, KIND_DISCONNECTED)) {
        KIND_CONNECTING -> TunnelState.Connecting
        KIND_CONNECTED -> TunnelState.Connected
        KIND_DISCONNECTING -> TunnelState.Disconnecting
        KIND_ERROR -> TunnelState.Error(getStringExtra(EXTRA_ERROR) ?: "Ошибка подключения")
        else -> TunnelState.Disconnected
    }

    private fun Intent.toActiveConnection(): VpnStateHolder.ActiveConnection? {
        if (!getBooleanExtra(EXTRA_HAS_CONN, false)) return null
        return VpnStateHolder.ActiveConnection(
            keyId = getLongExtra(EXTRA_CONN_KEY_ID, -1),
            serverIndex = getIntExtra(EXTRA_CONN_SERVER_INDEX, 0),
            serverName = getStringExtra(EXTRA_CONN_SERVER_NAME),
        )
    }

    private fun Intent.toStats(): TunnelStats = TunnelStats(
        uploadBytesPerSec = getLongExtra(EXTRA_UP_SPEED, 0),
        downloadBytesPerSec = getLongExtra(EXTRA_DOWN_SPEED, 0),
        totalUploadBytes = getLongExtra(EXTRA_UP_TOTAL, 0),
        totalDownloadBytes = getLongExtra(EXTRA_DOWN_TOTAL, 0),
        sessionSeconds = getLongExtra(EXTRA_SESSION_SEC, 0),
    )
}
