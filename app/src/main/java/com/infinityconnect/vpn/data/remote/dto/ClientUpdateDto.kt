package com.infinityconnect.vpn.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Ответ GET /v1/client-updates/android/apk/latest?current={semver}&code={versionCode}.
 *
 * 200 — есть обновление (это тело); 204 — клиент актуален.
 * Сервер сравнивает по version_code (монотонный int), semver — для отображения.
 */
@Serializable
data class ClientUpdateDto(
    @SerialName("version") val version: String,
    @SerialName("version_code") val versionCode: Long = 0,
    @SerialName("notes") val notes: String? = null,
    @SerialName("pub_date") val pubDate: String? = null,
    @SerialName("apk") val apk: ApkArtifactDto,
)

/** Метаданные APK-артефакта обновления. */
@Serializable
data class ApkArtifactDto(
    /** Абсолютный URL скачивания (/v1/client-updates/download/{id}). */
    @SerialName("url") val url: String,
    @SerialName("size") val size: Long = 0,
    /** SHA-256 файла (hex) — проверяется после скачивания перед установкой. */
    @SerialName("sha256") val sha256: String? = null,
)
