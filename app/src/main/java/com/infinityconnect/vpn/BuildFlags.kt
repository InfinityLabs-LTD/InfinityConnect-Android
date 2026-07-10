package com.infinityconnect.vpn

/**
 * Фиксированная конфигурация фирменного клиента.
 *
 * Домен сервера захардкожен — это закрытый клиент только для Infinity Connect,
 * пользователь не вводит адрес. Discovery выполняется автоматически по этому
 * домену при первом запуске.
 */
object BuildFlags {
    /** Домен сервера Infinity Connect (хост:порт API). */
    const val SERVER_DOMAIN = "bot.infinityconnect.ru:8443"
}
