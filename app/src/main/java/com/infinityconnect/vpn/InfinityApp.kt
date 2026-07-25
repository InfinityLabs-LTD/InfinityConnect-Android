package com.infinityconnect.vpn

import android.app.Application
import android.os.Build
import com.infinityconnect.vpn.data.local.LogStore
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

/**
 * Точка входа приложения. Инициализирует Hilt-граф зависимостей и журнал.
 */
@HiltAndroidApp
class InfinityApp : Application() {

    @Inject lateinit var logStore: LogStore
    @Inject lateinit var vpnStateHolder: com.infinityconnect.vpn.vpn.VpnStateHolder

    override fun onCreate() {
        super.onCreate()
        logStore.i(TAG, "=== Запуск ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE}) ===")
        logStore.i(
            TAG,
            "Устройство: ${Build.MANUFACTURER} ${Build.MODEL}, " +
                "Android ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})",
        )
        installCrashHandler()

        // VPN-сервис работает в отдельном процессе (:vpn), и Application.onCreate
        // выполняется в КАЖДОМ процессе. Подписка на состояние туннеля нужна
        // только UI-процессу: в процессе сервиса holder заполняется напрямую, а
        // приёмник лишь ловил бы собственные броадкасты.
        if (!isVpnProcess()) {
            com.infinityconnect.vpn.vpn.VpnStateBridge.subscribe(this, vpnStateHolder)
        }
    }

    /** true в процессе VPN-сервиса (`:vpn` из манифеста). */
    private fun isVpnProcess(): Boolean {
        val current = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            getProcessName()
        } else {
            null
        }
        return current?.endsWith(VPN_PROCESS_SUFFIX) == true
    }

    /**
     * Пишет необработанные исключения в журнал перед смертью процесса.
     *
     * Без этого краш виден только в logcat — то есть недоступен, когда
     * приложение падает у пользователя на его устройстве. Родной обработчик
     * вызывается следом, чтобы система штатно показала диалог и собрала
     * свой отчёт: подменять её поведение мы не хотим.
     */
    private fun installCrashHandler() {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            runCatching {
                logStore.e(TAG, "КРАШ в потоке ${thread.name}", error)
                // Журнал пишется в отдельном потоке — даём ему успеть на диск,
                // иначе процесс умрёт раньше, чем строка дойдёт до файла.
                logStore.flushBlocking()
            }
            previous?.uncaughtException(thread, error)
        }
    }

    private companion object {
        const val TAG = "App"

        /** Суффикс имени процесса VPN-сервиса (`android:process=":vpn"`). */
        const val VPN_PROCESS_SUFFIX = ":vpn"
    }
}
