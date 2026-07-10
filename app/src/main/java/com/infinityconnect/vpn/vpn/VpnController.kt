package com.infinityconnect.vpn.vpn

import android.content.Context
import android.content.Intent
import android.net.VpnService
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Фасад управления VPN для UI-слоя. Скрывает работу с Intent-командами
 * [InfinityVpnService] и проверку системного разрешения VpnService.
 */
@Singleton
class VpnController @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    /**
     * Возвращает Intent запроса разрешения на VPN, если оно ещё не выдано,
     * иначе null. UI должен запустить его через ActivityResult перед подключением.
     */
    fun prepareIntent(): Intent? = VpnService.prepare(context)

    /** Запускает подключение к серверу [serverIndex] ключа [keyId]. */
    fun connect(keyId: Long, serverIndex: Int, serverName: String?) {
        val intent = Intent(context, InfinityVpnService::class.java).apply {
            action = InfinityVpnService.ACTION_CONNECT
            putExtra(InfinityVpnService.EXTRA_KEY_ID, keyId)
            putExtra(InfinityVpnService.EXTRA_SERVER_INDEX, serverIndex)
            putExtra(InfinityVpnService.EXTRA_SERVER_NAME, serverName)
        }
        ContextCompat.startForegroundService(context, intent)
    }

    /** Останавливает туннель. */
    fun disconnect() {
        val intent = Intent(context, InfinityVpnService::class.java).apply {
            action = InfinityVpnService.ACTION_DISCONNECT
        }
        ContextCompat.startForegroundService(context, intent)
    }
}
