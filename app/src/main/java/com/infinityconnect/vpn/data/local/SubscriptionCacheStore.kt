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
 *
 * Содержимое файла ШИФРУЕТСЯ ([SecretCrypto], AES-256/GCM на ключе Keystore):
 * в теле подписки лежат VLESS-URI с UUID — по сути учётные данные доступа к
 * серверам, и на диске они не должны читаться напрямую. Старые (плоские) файлы
 * от предыдущих версий продолжают читаться и перешифровываются при первой же
 * перезаписи — см. [SecretCrypto.decrypt].
 */
@Singleton
class SubscriptionCacheStore @Inject constructor(
    @ApplicationContext private val context: Context,
    private val json: Json,
    private val crypto: SecretCrypto,
) {
    private val dir: File by lazy {
        File(context.filesDir, "subscription_cache").apply { if (!exists()) mkdirs() }
    }

    /** Читает сохранённое тело для URL или null, если его нет/битое/не расшифровалось. */
    fun load(url: String): SubscriptionBody? {
        val file = fileFor(url)
        if (!file.exists()) return null
        return runCatching {
            // decrypt вернёт null, если ключ Keystore был инвалидирован (смена
            // блокировки экрана / восстановление из бэкапа) — тогда кэш считаем
            // отсутствующим, и тело подтянется из сети.
            val plain = crypto.decrypt(file.readText()) ?: return null
            json.decodeFromString(SubscriptionBody.serializer(), plain)
        }.getOrNull()
    }

    /** Сохраняет тело подписки на диск (перезаписывает предыдущее). */
    fun save(url: String, body: SubscriptionBody) {
        runCatching {
            val payload = crypto.encrypt(json.encodeToString(SubscriptionBody.serializer(), body))
            // Пишем через временный файл + rename: тот же кэш читает второй
            // процесс (:vpn строит конфиг при подключении), и он не должен
            // наткнуться на файл, дописанный наполовину. rename в пределах
            // одного каталога атомарен, поэтому читатель видит либо старое
            // содержимое целиком, либо новое целиком.
            val target = fileFor(url)
            val tmp = File(dir, "${target.name}.tmp")
            tmp.writeText(payload)
            if (!tmp.renameTo(target)) {
                // На отдельных прошивках rename поверх существующего файла может
                // не сработать — тогда пишем напрямую (хуже по атомарности, но
                // лучше, чем потерянный кэш) и убираем временный файл.
                target.writeText(payload)
                tmp.delete()
            }
        }
    }

    private fun fileFor(url: String): File = File(dir, "${sha256(url)}.json")

    private fun sha256(input: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(input.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
}
