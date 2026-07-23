package com.infinityconnect.vpn.domain.usecase

import com.infinityconnect.vpn.domain.model.AppResult
import com.infinityconnect.vpn.domain.model.ClientUpdate
import com.infinityconnect.vpn.domain.repository.ClientUpdateRepository
import java.io.File
import javax.inject.Inject

/** Проверка обновления клиента. Success(null) — версия актуальна. */
class CheckClientUpdateUseCase @Inject constructor(
    private val repository: ClientUpdateRepository,
) {
    suspend operator fun invoke(): AppResult<ClientUpdate?> = repository.check()
}

/** Скачивание APK обновления (с прогрессом и проверкой sha256). */
class DownloadClientUpdateUseCase @Inject constructor(
    private val repository: ClientUpdateRepository,
) {
    suspend operator fun invoke(
        update: ClientUpdate,
        onProgress: (Float) -> Unit = {},
    ): AppResult<File> = repository.download(update, onProgress)
}
