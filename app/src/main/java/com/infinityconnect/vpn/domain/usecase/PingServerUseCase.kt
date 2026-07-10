package com.infinityconnect.vpn.domain.usecase

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.InetSocketAddress
import java.net.Socket
import javax.inject.Inject

/**
 * Измеряет пинг до сервера как время установления TCP-соединения (мс).
 * Возвращает -1 при недоступности/таймауте. Используется для отображения
 * задержки в списке серверов (стиль Happ).
 */
class PingServerUseCase @Inject constructor() {

    suspend operator fun invoke(address: String, port: Int, timeoutMs: Int = 3000): Int =
        withContext(Dispatchers.IO) {
            runCatching {
                Socket().use { socket ->
                    val start = System.currentTimeMillis()
                    socket.connect(InetSocketAddress(address, port), timeoutMs)
                    (System.currentTimeMillis() - start).toInt()
                }
            }.getOrDefault(-1)
        }
}
