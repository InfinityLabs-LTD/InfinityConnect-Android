package com.infinityconnect.vpn.vpn

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.infinityconnect.vpn.R
import com.infinityconnect.vpn.ui.MainActivity

/**
 * Формирование постоянного уведомления foreground-сервиса VPN.
 */
object VpnNotifications {

    const val CHANNEL_ID = "vpn_tunnel"
    const val NOTIFICATION_ID = 1001

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "VPN-соединение",
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = "Статус VPN-туннеля Infinity Connect"
                setShowBadge(false)
            }
            context.getSystemService(NotificationManager::class.java)
                .createNotificationChannel(channel)
        }
    }

    fun build(
        context: Context,
        title: String,
        text: String,
        disconnectIntent: PendingIntent?,
    ): Notification {
        val contentIntent = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(contentIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)

        if (disconnectIntent != null) {
            builder.addAction(
                0,
                "Отключить",
                disconnectIntent,
            )
        }
        return builder.build()
    }
}
