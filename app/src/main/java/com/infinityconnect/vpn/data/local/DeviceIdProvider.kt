package com.infinityconnect.vpn.data.local

import android.content.Context
import android.os.Build
import android.provider.Settings
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Идентификатор устройства (HWID) и метаданные для заголовков подписки.
 *
 * Панель Remnawave отдаёт реальные конфигы только при запросе подписки с
 * заголовками клиента Happ (User-Agent + x-hwid + x-device-*). Без них
 * возвращается заглушка «Приложение не поддерживается». HWID должен быть
 * стабильным между запусками — берём ANDROID_ID, при недоступности генерируем
 * и сохраняем UUID.
 */
@Singleton
class DeviceIdProvider @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val prefs by lazy {
        context.getSharedPreferences("device_id", Context.MODE_PRIVATE)
    }

    /** Стабильный HWID устройства (верхний регистр, как у Happ). */
    val hwid: String by lazy {
        val androidId = runCatching {
            Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
        }.getOrNull()?.takeIf { !it.isNullOrBlank() && it != "9774d56d682e549c" }

        val base = androidId ?: prefs.getString(KEY_HWID, null) ?: UUID.randomUUID().toString().also {
            prefs.edit().putString(KEY_HWID, it).apply()
        }
        base.uppercase()
    }

    val deviceOs: String get() = "Android"
    val osVersion: String get() = Build.VERSION.RELEASE ?: Build.VERSION.SDK_INT.toString()
    val deviceModel: String get() = "${Build.MANUFACTURER} ${Build.MODEL}".trim()

    /**
     * User-Agent клиента: панель Remnawave настроена отдавать реальные конфиги
     * (VLESS-URI в base64) именно для InfinityVPN. Версия синхронизирована с
     * versionName приложения.
     */
    val userAgent: String get() = "InfinityVPN/1.0.0"

    private companion object {
        const val KEY_HWID = "hwid"
    }
}
