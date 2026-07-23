package com.infinityconnect.vpn.domain.model

/** Доступное обновление приложения (ответ /v1/client-updates/android/apk/latest). */
data class ClientUpdate(
    val version: String,
    val versionCode: Long,
    val notes: String,
    val downloadUrl: String,
    val sizeBytes: Long,
    val sha256: String?,
)

/** Прогресс скачивания APK. */
sealed interface UpdateDownloadState {
    data object Idle : UpdateDownloadState
    /** [progress] 0..1; -1 если размер неизвестен. */
    data class Downloading(val progress: Float) : UpdateDownloadState
    /** Файл скачан и проверен, готов к установке. */
    data class Ready(val update: ClientUpdate) : UpdateDownloadState
    data class Failed(val error: AppError) : UpdateDownloadState
}
