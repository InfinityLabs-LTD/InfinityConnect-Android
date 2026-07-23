package com.infinityconnect.vpn.data.repository

import android.content.Context
import com.infinityconnect.vpn.BuildConfig
import com.infinityconnect.vpn.data.remote.api.ClientUpdateApi
import com.infinityconnect.vpn.domain.model.AppError
import com.infinityconnect.vpn.domain.model.AppResult
import com.infinityconnect.vpn.domain.model.ClientUpdate
import com.infinityconnect.vpn.domain.repository.ClientUpdateRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Реализация обновлений клиента.
 *
 * Скачивает APK в cacheDir/updates (внутренний кэш → наружу отдаётся только
 * через FileProvider), считает sha256 на лету и сверяет с ответом сервера.
 * Битые/старые файлы кэша чистятся перед скачиванием.
 */
@Singleton
class ClientUpdateRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val api: ClientUpdateApi,
) : ClientUpdateRepository {

    override suspend fun check(): AppResult<ClientUpdate?> = safeApiCall {
        val resp = api.latest(
            current = BuildConfig.VERSION_NAME,
            code = BuildConfig.VERSION_CODE.toLong(),
        )
        when {
            resp.code() == 204 -> null
            resp.isSuccessful -> {
                val dto = resp.body() ?: return@safeApiCall null
                // Защита от даунгрейда: сервер обязан отдавать только более новые версии,
                // но перепроверяем versionCode локально.
                if (dto.versionCode in 1..BuildConfig.VERSION_CODE.toLong()) return@safeApiCall null
                ClientUpdate(
                    version = dto.version,
                    versionCode = dto.versionCode,
                    notes = dto.notes.orEmpty(),
                    downloadUrl = dto.apk.url,
                    sizeBytes = dto.apk.size,
                    sha256 = dto.apk.sha256?.lowercase()?.takeIf { it.isNotBlank() },
                )
            }
            else -> throw retrofit2.HttpException(resp)
        }
    }

    override suspend fun download(
        update: ClientUpdate,
        onProgress: (Float) -> Unit,
    ): AppResult<File> = withContext(Dispatchers.IO) {
        val result = safeApiCall {
            val dir = File(context.cacheDir, "updates").apply {
                // Старые скачанные APK не нужны — чистим перед новой загрузкой
                listFiles()?.forEach { it.delete() }
                mkdirs()
            }
            val dest = File(dir, "infinity-connect-${update.version}.apk")

            val resp = api.download(update.downloadUrl)
            if (!resp.isSuccessful) throw retrofit2.HttpException(resp)
            val body = resp.body() ?: throw IllegalStateException("Пустое тело ответа")

            val total = if (update.sizeBytes > 0) update.sizeBytes else body.contentLength()
            val digest = MessageDigest.getInstance("SHA-256")
            body.byteStream().use { input ->
                dest.outputStream().use { out ->
                    val buf = ByteArray(64 * 1024)
                    var read: Int
                    var done = 0L
                    while (input.read(buf).also { read = it } != -1) {
                        out.write(buf, 0, read)
                        digest.update(buf, 0, read)
                        done += read
                        onProgress(if (total > 0) (done.toFloat() / total).coerceIn(0f, 1f) else -1f)
                    }
                }
            }

            val actualSha = digest.digest().joinToString("") { "%02x".format(it) }
            if (update.sha256 != null && actualSha != update.sha256) {
                dest.delete()
                throw ChecksumMismatchException(expected = update.sha256, actual = actualSha)
            }
            dest
        }
        // Checksum-ошибку показываем как Parse (понятное сообщение), а не Unknown
        if (result is AppResult.Failure &&
            (result.error as? AppError.Unknown)?.cause is ChecksumMismatchException
        ) {
            AppResult.Failure(AppError.Parse("Контрольная сумма APK не совпала"))
        } else {
            result
        }
    }
}

/** SHA-256 скачанного APK не совпал с заявленным сервером. */
class ChecksumMismatchException(expected: String, actual: String) :
    Exception("sha256 mismatch: expected=$expected actual=$actual")
