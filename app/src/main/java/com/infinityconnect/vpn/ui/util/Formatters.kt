package com.infinityconnect.vpn.ui.util

import com.infinityconnect.vpn.domain.model.AppError
import java.util.Locale

/** Человекочитаемое сообщение об ошибке для UI. */
fun AppError.toMessage(): String = when (this) {
    is AppError.Network -> "Нет соединения с сервером. Проверьте интернет."
    is AppError.InvalidCredentials -> "Неверный логин или пароль."
    is AppError.Unauthorized -> "Сессия истекла. Войдите заново."
    is AppError.Server -> "Ошибка сервера (код $code). Попробуйте позже."
    is AppError.Parse -> "Некорректный ответ сервера."
    is AppError.Unknown -> "Непредвиденная ошибка."
}

/** Форматирует трафик в человекочитаемый вид (КБ/МБ/ГБ). */
fun formatBytes(bytes: Long): String {
    if (bytes < 1024) return "$bytes Б"
    val units = arrayOf("КБ", "МБ", "ГБ", "ТБ")
    var value = bytes.toDouble() / 1024
    var unitIndex = 0
    while (value >= 1024 && unitIndex < units.lastIndex) {
        value /= 1024
        unitIndex++
    }
    return String.format(Locale.getDefault(), "%.1f %s", value, units[unitIndex])
}

/** Форматирует скорость (байт/с) → «X МБ/с». */
fun formatSpeed(bytesPerSec: Long): String = "${formatBytes(bytesPerSec)}/с"

/** Форматирует длительность сессии в HH:MM:SS. */
fun formatDuration(seconds: Long): String {
    val h = seconds / 3600
    val m = (seconds % 3600) / 60
    val s = seconds % 60
    return String.format(Locale.getDefault(), "%02d:%02d:%02d", h, m, s)
}

/**
 * Приводит ISO-дату (например, 2026-01-15T00:00:00Z) к «ДД.ММ.ГГГГ».
 * При неразборчивом формате возвращает исходную строку.
 */
fun formatDate(iso: String?): String {
    if (iso.isNullOrBlank()) return "—"
    // Берём только дату (первые 10 символов YYYY-MM-DD).
    val datePart = iso.take(10)
    val parts = datePart.split("-")
    return if (parts.size == 3) "${parts[2]}.${parts[1]}.${parts[0]}" else iso
}
