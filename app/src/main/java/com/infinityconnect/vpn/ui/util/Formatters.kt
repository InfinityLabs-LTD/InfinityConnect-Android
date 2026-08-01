package com.infinityconnect.vpn.ui.util

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import com.infinityconnect.vpn.R
import com.infinityconnect.vpn.domain.model.AppError
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Сообщение об ошибке для UI.
 *
 * Возвращает пару «ID строки + аргументы», а не готовый текст: ошибки
 * формируются во ViewModel, у которой нет Context, а язык интерфейса может
 * смениться между появлением ошибки и её показом. Резолвится в композиции
 * через [errorText].
 */
fun AppError.toMessage(): ErrorMessage = when (this) {
    is AppError.Network -> ErrorMessage(R.string.error_network)
    is AppError.InvalidCredentials -> ErrorMessage(R.string.error_invalid_credentials)
    is AppError.Unauthorized -> ErrorMessage(R.string.error_unauthorized)
    is AppError.Server -> ErrorMessage(R.string.error_server, listOf(code))
    is AppError.Parse -> ErrorMessage(R.string.error_parse)
    is AppError.Unknown -> ErrorMessage(R.string.error_unknown)
}

/**
 * Локализуемое сообщение: ID строки и аргументы форматирования.
 *
 * [raw] — готовый текст, пришедший от сервера: локализовать его нечем, но и
 * терять не хочется (он конкретнее любой нашей формулировки). Если задан,
 * показывается вместо строки из ресурсов.
 */
data class ErrorMessage(
    val resId: Int,
    val args: List<Any> = emptyList(),
    val raw: String? = null,
)

/** Резолвит [ErrorMessage] в текст на текущем языке интерфейса. */
@Composable
fun errorText(message: ErrorMessage?): String? {
    val context = LocalContext.current
    return message?.let { it.raw ?: context.getString(it.resId, *it.args.toTypedArray()) }
}

/** Форматирует трафик в человекочитаемый вид (КБ/МБ/ГБ). */
fun Context.formatBytes(bytes: Long): String {
    if (bytes < 1024) return getString(R.string.unit_bytes, bytes)
    val units = intArrayOf(R.string.unit_kb, R.string.unit_mb, R.string.unit_gb, R.string.unit_tb)
    var value = bytes.toDouble() / 1024
    var unitIndex = 0
    while (value >= 1024 && unitIndex < units.lastIndex) {
        value /= 1024
        unitIndex++
    }
    return String.format(Locale.getDefault(), "%.1f %s", value, getString(units[unitIndex]))
}

/** Форматирует скорость (байт/с) → «X МБ/с». */
fun Context.formatSpeed(bytesPerSec: Long): String =
    getString(R.string.unit_per_second, formatBytes(bytesPerSec))

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

/** Форматирует epoch-миллисекунды в «ДД.ММ.ГГГГ ЧЧ:ММ». */
fun formatDateTime(epochMs: Long): String =
    SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault()).format(Date(epochMs))

/**
 * Парсит ISO-дату (день YYYY-MM-DD, разделитель даты/времени — пробел или T)
 * в epoch-мс начала дня. null — если формат неразборчив.
 */
fun parseIsoDateMs(iso: String?): Long? {
    if (iso.isNullOrBlank()) return null
    val parts = iso.take(10).split("-")
    if (parts.size != 3) return null
    return try {
        val cal = java.util.Calendar.getInstance()
        cal.clear()
        cal.set(parts[0].toInt(), parts[1].toInt() - 1, parts[2].toInt())
        cal.timeInMillis
    } catch (_: Exception) {
        null
    }
}

/** Срок в прошлом (истёк). Неразборчивую/пустую дату считаем не истёкшей. */
fun isExpired(iso: String?): Boolean {
    val ms = parseIsoDateMs(iso) ?: return false
    // Прибавляем сутки: дата без времени — истекает в конце дня.
    return ms + 86_400_000L < System.currentTimeMillis()
}

/** Число полных дней до даты (может быть отрицательным). null — не разобрана. */
fun daysUntil(iso: String?): Int? {
    val ms = parseIsoDateMs(iso) ?: return null
    return ((ms - System.currentTimeMillis()) / 86_400_000L).toInt()
}
