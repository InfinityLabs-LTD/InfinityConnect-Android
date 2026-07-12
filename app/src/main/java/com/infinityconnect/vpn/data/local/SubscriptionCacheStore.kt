package com.infinityconnect.vpn.data.local

import android.content.Context
import com.infinityconnect.vpn.domain.repository.SubscriptionBody
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.serialization.json.Json
import java.io.File
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Персистентный кэш тел подписок на диске — по одному JSON-файлу на URL.
 *
 * Нужен, чтобы предзагруженные серверы были доступны после перезапуска
 * приложения даже без интернета: in-memory кэш репозитория живёт лишь на
 * процесс и теряется при закрытии, из-за чего офлайн-старт показывал пустой
 * список. Здесь тело переживает перезапуск и подхватывается при промахе сети.
 *
 * Имя файла — SHA-256 от URL (URL может содержать символы, недопустимые в
 * имени файла). Тело сериализуется как [SubscriptionBody] (raw + метаданные).
 */
@Singleton
class SubscriptionCacheStore @Inject constructor(
    @ApplicationContext private val context: Context,
    private val json: Json,
) {
    private val dir: File by lazy {
        File(context.filesDir, "subscription_cache").apply { if (!exists()) mkdirs() }
    }

    /** Читает сохранённое тело для URL или null, если его нет/битое. */
    fun load(url: String): SubscriptionBody? {
        val file = fileFor(url)
        if (!file.exists()) return null
        return runCatching {
            json.decodeFromString(SubscriptionBody.serializer(), file.readText())
        }.getOrNull()
    }

    /** Сохраняет тело подписки на диск (перезаписывает предыдущее). */
    fun save(url: String, body: SubscriptionBody) {
        runCatching {
            fileFor(url).writeText(json.encodeToString(SubscriptionBody.serializer(), body))
        }
    }

    private fun fileFor(url: String): File = File(dir, "${sha256(url)}.json")

    private fun sha256(input: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(input.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
}
