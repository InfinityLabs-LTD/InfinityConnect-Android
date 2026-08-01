package com.infinityconnect.vpn.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.fillMaxSize
import androidx.core.content.ContextCompat
import com.infinityconnect.vpn.data.local.SettingsStore
import com.infinityconnect.vpn.domain.model.Language
import com.infinityconnect.vpn.ui.navigation.AppNavHost
import com.infinityconnect.vpn.ui.theme.InfinityTheme
import com.infinityconnect.vpn.ui.util.LanguageController
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.runBlocking
import javax.inject.Inject

/**
 * Единственная Activity (single-activity + Compose Navigation).
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var settingsStore: SettingsStore
    @Inject lateinit var languageController: LanguageController

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { /* результат не критичен: без уведомлений сервис всё равно работает */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        restoreLanguageIfNeeded()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        requestNotificationPermissionIfNeeded()
        setContent {
            InfinityTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = com.infinityconnect.vpn.ui.theme.InfinityColors.Space,
                ) {
                    // Общий анимированный фон (сетка + glow) под всеми экранами —
                    // экраны держат свои контейнеры прозрачными.
                    androidx.compose.foundation.layout.Box(Modifier.fillMaxSize()) {
                        com.infinityconnect.vpn.ui.components.MeshBackground()
                        AppNavHost()
                    }
                }
            }
        }
    }

    /**
     * Поднимает сохранённый язык интерфейса до первой отрисовки.
     *
     * Нужно только на API < 33: там выбор хранит сам AppCompat, но применяет его
     * лишь после создания первой Activity — то есть первый кадр успел бы уехать
     * на системном языке, а следом Activity пересоздалась бы с миганием. На 13+
     * язык хранит система и подставляет его ещё до onCreate, поэтому здесь мы
     * не трогаем ничего: пользователь мог сменить язык из системных настроек
     * мимо приложения, и наш DataStore перебил бы этот выбор.
     *
     * runBlocking на главном потоке — сознательно: одно чтение DataStore
     * (обычно уже прогретого) против гарантии, что первый кадр уже на нужном
     * языке. Ошибка чтения не должна ронять запуск — тогда просто остаётся
     * системный язык.
     */
    private fun restoreLanguageIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) return
        if (languageController.current != Language.SYSTEM) return
        runCatching { runBlocking { settingsStore.currentLanguage() } }
            .getOrNull()
            ?.takeIf { it != Language.SYSTEM }
            ?.let(languageController::apply)
    }

    /** На Android 13+ уведомление foreground-сервиса требует разрешения. */
    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS,
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }
}
