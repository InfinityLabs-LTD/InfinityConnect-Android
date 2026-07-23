package com.infinityconnect.vpn.domain.repository

import com.infinityconnect.vpn.domain.model.AppResult
import com.infinityconnect.vpn.domain.model.ClientUpdate
import java.io.File

/**
 * Обновления Android-клиента с сервера сервиса (/v1/client-updates/android/*).
 * Та же серверная система, что у Windows-клиента (client_updates.py).
 */
interface ClientUpdateRepository {

    /** Проверяет наличие обновления. Success(null) — клиент актуален (204). */
    suspend fun check(): AppResult<ClientUpdate?>

    /**
     * Скачивает APK во внутренний кэш, проверяет sha256 (если задан).
     * [onProgress] — 0..1 (или -1, если размер неизвестен).
     * Возвращает файл, готовый к передаче установщику.
     */
    suspend fun download(
        update: ClientUpdate,
        onProgress: (Float) -> Unit = {},
    ): AppResult<File>
}
