package com.infinityconnect.vpn.ui.util

import android.app.LocaleManager
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.LocaleList
import android.os.Looper
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import com.infinityconnect.vpn.domain.model.Language
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Применение выбранного языка интерфейса.
 *
 * Штатный per-app language: на Android 13+ выбор хранит система (LocaleManager,
 * виден в «Настройки → Приложения → Infinity Connect → Язык»), ниже — библиотека
 * AppCompat. В обоих случаях Activity пересоздаётся системой/библиотекой, поэтому
 * здесь нет ни ручного recreate(), ни подмены Context.
 *
 * Свой ключ в DataStore ([com.infinityconnect.vpn.data.local.SettingsStore])
 * всё равно нужен: на API < 33 AppCompat поднимает сохранённые локали только
 * после первого создания Activity, а на 13+ пользователь может сменить язык из
 * системных настроек мимо приложения. Источником правды остаётся
 * [current] — то, что реально применено, — а DataStore лишь отражает выбор для UI.
 */
@Singleton
class LanguageController @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    /**
     * Язык, применённый сейчас (SYSTEM, если приложение следует системе).
     *
     * На Android 13+ спрашиваем системный LocaleManager, а не AppCompat: язык
     * хранит система, и её значение верно всегда — в том числе когда его
     * поменяли в системных настройках мимо приложения и когда AppCompat ещё не
     * успел подхватить локали (его кэш пуст до создания первой Activity, из-за
     * чего экран языка показывал «Системный» при реально включённом English).
     */
    val current: Language
        get() {
            val tag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.getSystemService(LocaleManager::class.java)
                    ?.applicationLocales
                    ?.takeIf { !it.isEmpty }
                    ?.get(0)
                    ?.language
            } else {
                AppCompatDelegate.getApplicationLocales()[0]?.language
            }
            return Language.from(tag)
        }

    /**
     * Применяет язык. Вызов идемпотентен: если запрошенный язык уже применён,
     * AppCompat не пересоздаёт Activity.
     *
     * На Android 13+ идём прямо в системный LocaleManager. AppCompat здесь
     * бесполезен: его setApplicationLocales доносит выбор до системы через
     * AppCompatActivity/AppCompatDelegate, а единственная Activity приложения —
     * ComponentActivity (чистый Compose). Вызов молча ничего не делал: отметка в
     * UI переключалась, язык — нет (проверено на эмуляторе, Android 17).
     *
     * На API < 33 остаётся AppCompat: там он хранит выбор сам и пересоздаёт
     * Activity. Требует главного потока (@MainThread), поэтому при вызове из
     * корутины перекладываем на main looper.
     */
    fun apply(language: Language) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val locales = if (language == Language.SYSTEM) {
                LocaleList.getEmptyLocaleList()
            } else {
                LocaleList.forLanguageTags(language.tag)
            }
            context.getSystemService(LocaleManager::class.java)?.applicationLocales = locales
            return
        }
        val locales = if (language == Language.SYSTEM) {
            LocaleListCompat.getEmptyLocaleList()
        } else {
            LocaleListCompat.forLanguageTags(language.tag)
        }
        if (Looper.myLooper() == Looper.getMainLooper()) {
            AppCompatDelegate.setApplicationLocales(locales)
        } else {
            Handler(Looper.getMainLooper()).post {
                AppCompatDelegate.setApplicationLocales(locales)
            }
        }
    }
}
