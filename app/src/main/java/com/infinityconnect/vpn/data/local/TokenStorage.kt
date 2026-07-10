package com.infinityconnect.vpn.data.local

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Защищённое хранилище токенов на EncryptedSharedPreferences (ключ шифрования
 * в Android Keystore). Хранит пару access/refresh и служебные метки.
 *
 * Доступ синхронный — этого требует сетевой слой (OkHttp Authenticator).
 */
@Singleton
class TokenStorage @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val prefs: SharedPreferences by lazy { createPrefs(context) }

    var accessToken: String?
        get() = prefs.getString(KEY_ACCESS, null)
        set(value) = prefs.edit().putStringOrRemove(KEY_ACCESS, value).apply()

    var refreshToken: String?
        get() = prefs.getString(KEY_REFRESH, null)
        set(value) = prefs.edit().putStringOrRemove(KEY_REFRESH, value).apply()

    /** true, если есть refresh-токен (пользователь считается авторизованным). */
    fun hasSession(): Boolean = !refreshToken.isNullOrBlank()

    /** Атомарно сохраняет новую пару токенов. */
    fun save(access: String, refresh: String) {
        prefs.edit()
            .putString(KEY_ACCESS, access)
            .putString(KEY_REFRESH, refresh)
            .apply()
    }

    /** Полная очистка (разлогин). */
    fun clear() {
        prefs.edit().clear().apply()
    }

    private fun createPrefs(context: Context): SharedPreferences {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        return EncryptedSharedPreferences.create(
            context,
            FILE_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    private fun SharedPreferences.Editor.putStringOrRemove(
        key: String,
        value: String?,
    ): SharedPreferences.Editor =
        if (value == null) remove(key) else putString(key, value)

    private companion object {
        const val FILE_NAME = "infinity_tokens"
        const val KEY_ACCESS = "access_token"
        const val KEY_REFRESH = "refresh_token"
    }
}
