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
     * Xray: формат релиза YY.M.D (тег обёртки AndroidLibXrayLite = версия ядра).
     * Обновлять вручную при замене AAR: github.com/xtls/xray-core и apernet/hysteria.
     */
    const val XRAY_CORE_VERSION = "26.7.28"
    const val HYSTERIA2_CORE_VERSION = "2.x"

    /**
     * Подробный лог Xray-ядра (loglevel=warning вместо none).
     *
     * Сообщения ядра приходят в `onEmitStatus` и попадают в журнал приложения —
     * без них диагноз «туннель поднят, а трафик не идёт» поставить нельзя:
     * причины отказа хендшейка, Reality и маршрутизации печатает только ядро.
     * Включено намеренно и в релизе: журнал пользователь отправляет из
     * «Настройки → Логи». Выключить, когда разбор завершится.
     */
    const val VERBOSE_CORE_LOG = true
}
