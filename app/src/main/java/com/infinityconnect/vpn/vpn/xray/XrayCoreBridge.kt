package com.infinityconnect.vpn.vpn.xray

import android.util.Log

/**
 * Тонкий адаптер к нативной библиотеке Xray (libXray AAR), собранной через
 * gomobile. AAR подключается вручную (см. README) и в этой сборке может
 * отсутствовать — поэтому вызовы идут через рефлексию: проект компилируется
 * без AAR, а при его наличии мост находит и вызывает нужные методы.
 *
 * ────────────────────────────────────────────────────────────────────────
 * КОНТРАКТ (что должен предоставлять AAR):
 *
 * Разные сборки libXray экспонируют разный API. Ниже — типичный вариант
 * (класс `libXray.LibXray` из github.com/XTLS/libXray). Если у вас другая
 * сборка, скорректируйте имена класса/методов в константах ниже.
 *
 *   - runXray(datDir: String, configJson: String): String  // "" при успехе
 *   - stopXray(): String
 *   - queryStats(...): String   // опционально, зависит от сборки
 *
 * Для tun2socks (заворачивание TUN → локальный SOCKS Xray) обычно используется
 * отдельная библиотека (hev-socks5-tunnel / tun2socks AAR) — см. [Tun2SocksBridge].
 * ────────────────────────────────────────────────────────────────────────
 */
object XrayCoreBridge {

    private const val TAG = "XrayCoreBridge"

    // Имя класса и методов реальной библиотеки. При иной сборке — поменять здесь.
    private const val LIB_CLASS = "libXray.LibXray"
    private const val METHOD_RUN = "runXray"
    private const val METHOD_STOP = "stopXray"

    /** Доступен ли нативный Xray (подключён ли AAR). */
    val isAvailable: Boolean by lazy {
        runCatching { Class.forName(LIB_CLASS) }.isSuccess
    }

    /**
     * Запускает ядро Xray с данным JSON-конфигом.
     * @param datDir каталог для geoip/geosite и служебных данных Xray.
     * @throws XrayUnavailableException если AAR не подключён.
     * @throws XrayStartException при ошибке запуска ядра.
     */
    fun run(datDir: String, configJson: String) {
        val clazz = loadLibClassOrThrow()
        val result = runCatching {
            val method = clazz.getMethod(METHOD_RUN, String::class.java, String::class.java)
            method.invoke(null, datDir, configJson) as? String
        }.getOrElse { e ->
            throw XrayStartException("Не удалось вызвать $METHOD_RUN: ${e.message}", e)
        }
        // Соглашение libXray: пустая строка — успех, иначе текст ошибки.
        if (!result.isNullOrEmpty()) {
            throw XrayStartException("Xray вернул ошибку: $result")
        }
        Log.i(TAG, "Xray-ядро запущено")
    }

    /** Останавливает ядро Xray. Ошибки логируются, но не пробрасываются. */
    fun stop() {
        val clazz = runCatching { Class.forName(LIB_CLASS) }.getOrNull() ?: return
        runCatching {
            clazz.getMethod(METHOD_STOP).invoke(null)
        }.onFailure { Log.w(TAG, "Ошибка остановки Xray: ${it.message}") }
    }

    private fun loadLibClassOrThrow(): Class<*> =
        runCatching { Class.forName(LIB_CLASS) }.getOrElse {
            throw XrayUnavailableException(
                "libXray AAR не подключён. Добавьте libXray.aar в app/libs и " +
                    "раскомментируйте зависимость в app/build.gradle.kts (см. README).",
            )
        }
}

/** AAR Xray не подключён. */
class XrayUnavailableException(message: String) : Exception(message)

/** Ошибка запуска ядра Xray. */
class XrayStartException(message: String, cause: Throwable? = null) : Exception(message, cause)
