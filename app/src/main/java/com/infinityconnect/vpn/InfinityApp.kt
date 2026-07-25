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

    override fun onCreate() {
        super.onCreate()
        logStore.i(TAG, "=== Запуск ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE}) ===")
        logStore.i(
            TAG,
            "Устройство: ${Build.MANUFACTURER} ${Build.MODEL}, " +
                "Android ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})",
        )
        installCrashHandler()
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
    }
}
