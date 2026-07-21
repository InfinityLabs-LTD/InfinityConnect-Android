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

    /** Разработчик приложения (для раздела «О приложении»). */
    const val DEVELOPER = "Infinity Labs"

    /**
     * Версии нативных ядер, вкомпилированных в AAR (app/libs).
     * Держим строками — точные версии не экспонируются рантаймом gomobile-обёрток.
     * Xray: формат релиза YY.M.D (из go-модуля v1.260327.1 → тег 26.3.27).
     * Обновлять вручную при замене AAR: github.com/xtls/xray-core и apernet/hysteria.
     */
    const val XRAY_CORE_VERSION = "26.3.27"
    const val HYSTERIA2_CORE_VERSION = "2.x"
}
