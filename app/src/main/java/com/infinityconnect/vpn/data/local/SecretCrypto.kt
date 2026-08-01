package com.infinityconnect.vpn.data.local

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Шифрование локальных секретов на ключе из Android Keystore (AES-256/GCM).
 *
 * ЗАЧЕМ. На диске лежат фактические учётные данные доступа к VPN: `subscription_url`
 * и VLESS-URI с UUID (список ключей в DataStore, тела подписок в filesDir). Токены
 * проекта давно живут в EncryptedSharedPreferences ([TokenStorage]), а эти два кэша
 * оставались открытым текстом — на рутованном устройстве или через `adb run-as`
 * они читались как есть. Здесь общий помощник, который приводит их к тому же уровню.
 *
 * ПОЧЕМУ СВОЙ ПОМОЩНИК, А НЕ EncryptedSharedPreferences/EncryptedFile.
 * Данные уже хранятся в готовых контейнерах (Preferences DataStore и файлы кэша),
 * менять контейнер значило бы переписывать миграцию, потребителей и офлайн-логику.
 * Проще шифровать *значение*, оставив контейнер прежним: строка на входе — строка
 * на выходе, вызывающие ([SettingsStore], [SubscriptionCacheStore]) не меняются.
 *
 * ФОРМАТ. Base64(IV ‖ ciphertext‖tag) с префиксом-маркером [PREFIX]. Маркер нужен,
 * чтобы отличить наш шифротекст от старого плоского значения (см. «Миграция»);
 * base64 — потому что оба контейнера строковые (DataStore-строка и текстовый файл).
 * IV генерируется провайдером на КАЖДОЕ шифрование (мы намеренно не передаём свой
 * IV: повтор IV в GCM полностью ломает конфиденциальность) и кладётся рядом с
 * шифротекстом — расшифровка без него невозможна.
 *
 * КЛЮЧ. Один AES-256 на приложение, `setUserAuthenticationRequired` НЕ включаем:
 * данные читает фоновый VPN-сервис при автозапуске туннеля, когда экран заблокирован,
 * — требование аутентификации сделало бы кэш недоступным ровно тогда, когда он нужен.
 *
 * МНОГОПРОЦЕССНОСТЬ. Сервис живёт в отдельном процессе `:vpn` и читает те же кэши
 * (BuildConnectionUseCase → Keys/SubscriptionRepository). Keystore — системное
 * хранилище уровня приложения (не процесса), поэтому ключ, созданный UI-процессом,
 * виден и в `:vpn`. Гонка «оба процесса впервые создают ключ одновременно» разрешена
 * в [obtainKey]: генерация делается под локом внутри процесса, а межпроцессное
 * состязание ловится повторным чтением из Keystore — победил тот, чью запись
 * увидели оба (проигравший перечитает ключ и пойдёт дальше, а не перезапишет его).
 *
 * ОТКАЗОУСТОЙЧИВОСТЬ. Ни один метод не бросает: Keystore может быть недоступен
 * (сломанный keymaster на отдельных прошивках), а ключ — инвалидирован при смене
 * блокировки экрана или восстановлении из бэкапа (`KeyPermanentlyInvalidatedException`).
 * В таких случаях [encrypt] возвращает исходную строку (лучше сохранить плоско, чем
 * потерять офлайн-кэш и разлогинить пользователя), а [decrypt] — null, и вызывающий
 * трактует кэш как отсутствующий: данные подтянутся из сети.
 */
@Singleton
class SecretCrypto @Inject constructor() {

    /**
     * Кэш ключа в памяти процесса. Обращение к Keystore — IPC в keystore2 и стоит
     * миллисекунды; эти кэши читаются на холодном старте ради «мгновенного первого
     * кадра» (см. KeysRepositoryImpl.sync), поэтому платить за IPC каждый раз нельзя.
     * @Volatile + двойная проверка под [lock]: к хранилищу ходят и UI, и `:vpn`.
     */
    @Volatile
    private var cachedKey: SecretKey? = null

    private val lock = Any()

    /**
     * Шифрует строку. При недоступном Keystore возвращает [plain] как есть —
     * деградация к прежнему (плоскому) поведению вместо потери данных.
     */
    fun encrypt(plain: String): String {
        val key = obtainKey() ?: return plain
        return runCatching {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, key)
            // IV выдаёт провайдер (случайный, 12 байт для GCM) — свой не задаём.
            val iv = cipher.iv
            val encrypted = cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
            PREFIX + android.util.Base64.encodeToString(
                iv + encrypted,
                android.util.Base64.NO_WRAP,
            )
        }.getOrElse { error ->
            // Ключ сбрасываем только при явной инвалидации (смена блокировки
            // экрана, восстановление из бэкапа): пересоздать его — единственный
            // способ снова шифровать. При любой другой ошибке ключ трогать
            // нельзя, иначе всё ранее зашифрованное станет нечитаемым из-за
            // одной неудачной записи. Саму запись сохраняем плоско: потерять
            // офлайн-кэш хуже, чем разово записать без шифрования.
            if (error is android.security.keystore.KeyPermanentlyInvalidatedException) {
                invalidateKey()
            }
            plain
        }
    }

    /**
     * Расшифровывает значение, ранее полученное из [encrypt].
     *
     * МИГРАЦИЯ СТАРЫХ ДАННЫХ. Значение без маркера [PREFIX] — это плоский текст,
     * записанный версией приложения до шифрования. Возвращаем его как есть: кэш
     * остаётся рабочим, а при первой же перезаписи (sync ключей / refresh подписки)
     * ляжет уже зашифрованным. Отдельного шага миграции нет намеренно — он потребовал
     * бы блокирующей записи на старте в обоих процессах и гонки между ними.
     *
     * @return расшифрованная строка, plain-значение для старых данных, либо null,
     *   если шифротекст не читается (ключ пересоздан/инвалидирован, файл повреждён).
     */
    fun decrypt(stored: String): String? {
        if (!stored.startsWith(PREFIX)) return stored // старый формат — до шифрования
        val key = obtainKey() ?: return null
        return runCatching {
            val blob = android.util.Base64.decode(
                stored.substring(PREFIX.length),
                android.util.Base64.NO_WRAP,
            )
            if (blob.size <= IV_SIZE) return null
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(
                Cipher.DECRYPT_MODE,
                key,
                GCMParameterSpec(TAG_BITS, blob, 0, IV_SIZE),
            )
            String(
                cipher.doFinal(blob, IV_SIZE, blob.size - IV_SIZE),
                Charsets.UTF_8,
            )
        }.getOrElse { error ->
            // ВАЖНО: ключ здесь НЕ удаляем. Причин у ошибки две, и они требуют
            // разного обращения:
            //  - повреждён/обрезан конкретный шифротекст (AEADBadTagException) —
            //    ключ цел, и остальные записи им прекрасно читаются;
            //  - ключ действительно инвалидирован (KeyPermanentlyInvalidated) —
            //    вот тогда он бесполезен для всех записей сразу.
            // Удалять ключ в первом случае значило бы из-за одного битого файла
            // подписки обнулить и весь остальной кэш (список ключей в частности).
            // Поэтому сбрасываем ключ только при явной инвалидации; в остальных
            // случаях просто сообщаем «не прочиталось» — перезальётся из сети.
            if (error is android.security.keystore.KeyPermanentlyInvalidatedException) {
                invalidateKey()
            }
            null
        }
    }

    /**
     * Ключ из Keystore, при отсутствии — генерирует. null только если Keystore
     * недоступен в принципе (тогда работаем без шифрования, см. класс-док).
     */
    private fun obtainKey(): SecretKey? {
        cachedKey?.let { return it }
        synchronized(lock) {
            cachedKey?.let { return it }
            val key = runCatching { loadOrCreateKey() }.getOrNull()
            cachedKey = key
            return key
        }
    }

    private fun loadOrCreateKey(): SecretKey? {
        val store = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        (store.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry)?.secretKey?.let { return it }
        return runCatching { generateKey() }.getOrElse {
            // Другой процесс (UI vs :vpn) мог создать ключ ровно между нашими
            // getEntry и generateKey — тогда генерация упирается в занятый алиас.
            // Перечитываем: побеждает уже существующая запись, иначе процессы
            // затирали бы ключ друг друга и взаимно ломали свои шифротексты.
            runCatching {
                val fresh = KeyStore.getInstance(KEYSTORE).apply { load(null) }
                (fresh.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry)?.secretKey
            }.getOrNull()
        }
    }

    private fun generateKey(): SecretKey {
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(KEY_SIZE_BITS)
                // Аутентификация пользователя НЕ требуется: кэш читает фоновый
                // VPN-сервис при заблокированном экране (автозапуск туннеля).
                .setUserAuthenticationRequired(false)
                .build(),
        )
        return generator.generateKey()
    }

    /** Удаляет битый/инвалидированный ключ, чтобы следующий вызов создал новый. */
    private fun invalidateKey() {
        synchronized(lock) {
            cachedKey = null
            runCatching {
                KeyStore.getInstance(KEYSTORE).apply { load(null) }.deleteEntry(KEY_ALIAS)
            }
        }
    }

    private companion object {
        const val KEYSTORE = "AndroidKeyStore"
        const val KEY_ALIAS = "infinity_secret_v1"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val KEY_SIZE_BITS = 256
        const val IV_SIZE = 12   // GCM: 96-битный IV — рекомендованный размер
        const val TAG_BITS = 128 // полный тег аутентификации
        /** Маркер «значение зашифровано» — отличает наш формат от старого плоского. */
        const val PREFIX = "enc1:"
    }
}
