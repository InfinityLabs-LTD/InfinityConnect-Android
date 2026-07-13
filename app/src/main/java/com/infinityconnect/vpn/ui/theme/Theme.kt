package com.infinityconnect.vpn.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * Тёмная premium-палитра Infinity Connect: глубокий космический сине-чёрный фон,
 * электрический сине-индиго акцент, мятный «подключено» и коралловый «ошибка».
 * Нейтрали смещены в синеву — не серый по умолчанию, а выбранный оттенок.
 */
object InfinityColors {
    val Space = Color(0xFF0B0716)        // самый тёмный фон (фиолетово-чёрный)
    val SpaceElevated = Color(0xFF150E28) // приподнятый фон (за картами)
    val Surface = Color(0xFF1C1338)       // карточка
    val SurfaceHi = Color(0xFF261A4C)     // карточка (выделенная/ховер)
    val Stroke = Color(0xFF352455)        // тонкая обводка карточек

    val AccentBlue = Color(0xFF9D5CFF)    // основной акцент (фиолетовый)
    val AccentIndigo = Color(0xFF6C3CFF)  // глубокий индиго-фиолет
    val AccentCyan = Color(0xFFC77DFF)    // светлый пурпурно-розовый
    val AccentMagenta = Color(0xFFE85CD8) // розово-маджента (тёплый край градиента)

    val Mint = Color(0xFF22E1A1)          // подключено
    val Coral = Color(0xFFFF5A6E)         // ошибка / отключить
    val Amber = Color(0xFFFFB020)         // средний пинг

    val OnSurface = Color(0xFFEDE9F7)     // основной текст
    val Muted = Color(0xFF9A8FB6)         // вторичный текст
    val MutedDim = Color(0xFF685C83)      // самый приглушённый

    // Базовые цвета пинг-пилла по методу измерения (окрашивают шкалу качества).
    val PingReal = Mint          // «реальный» пинг через ядро — флагманский
    val PingTcp = AccentBlue
    val PingIcmp = AccentCyan
    val PingGet = AccentIndigo
    val PingHead = Color(0xFF9B7BFF)
}

/** Градиенты и брэнд-кисти, недоступные в стандартной ColorScheme. */
data class InfinityGradients(
    val accent: Brush,          // основной акцент (кнопки, hero idle)
    val accentSoft: Brush,      // мягкий фон-подсветка
    val connected: Brush,       // hero подключено
    val screen: Brush,          // фон экрана (вертикальный)
    val cardStroke: Brush,      // градиентная обводка карточек
)

private val gradients = InfinityGradients(
    accent = Brush.linearGradient(
        listOf(InfinityColors.AccentIndigo, InfinityColors.AccentBlue, InfinityColors.AccentMagenta),
    ),
    accentSoft = Brush.linearGradient(
        listOf(InfinityColors.AccentBlue.copy(alpha = 0.20f), InfinityColors.AccentMagenta.copy(alpha = 0.06f)),
    ),
    connected = Brush.linearGradient(
        listOf(InfinityColors.Mint, InfinityColors.AccentCyan),
    ),
    screen = Brush.verticalGradient(
        listOf(Color(0xFF160D2B), InfinityColors.Space),
    ),
    cardStroke = Brush.linearGradient(
        listOf(Color(0x33FFFFFF), Color(0x08FFFFFF)),
    ),
)

val LocalInfinityGradients = staticCompositionLocalOf { gradients }

private val DarkColors = darkColorScheme(
    primary = InfinityColors.AccentBlue,
    onPrimary = Color.White,
    secondary = InfinityColors.AccentIndigo,
    onSecondary = Color.White,
    tertiary = InfinityColors.AccentCyan,
    background = InfinityColors.Space,
    onBackground = InfinityColors.OnSurface,
    surface = InfinityColors.Surface,
    onSurface = InfinityColors.OnSurface,
    surfaceVariant = InfinityColors.SurfaceHi,
    onSurfaceVariant = InfinityColors.Muted,
    error = InfinityColors.Coral,
    onError = Color.White,
    outline = InfinityColors.Stroke,
)

/** Типографика: чуть плотнее и выразительнее стандартной Material. */
private val InfinityTypography = Typography().run {
    copy(
        displaySmall = displaySmall.copy(fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp),
        headlineMedium = headlineMedium.copy(fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp),
        titleLarge = titleLarge.copy(fontWeight = FontWeight.Bold),
        titleMedium = titleMedium.copy(fontWeight = FontWeight.SemiBold),
        titleSmall = titleSmall.copy(fontWeight = FontWeight.SemiBold, letterSpacing = 0.6.sp),
        labelLarge = labelLarge.copy(fontWeight = FontWeight.SemiBold),
    )
}

/** Стиль для eyebrow-меток (SERVERS, MODE) — капс + трекинг. */
val EyebrowStyle = TextStyle(
    fontWeight = FontWeight.SemiBold,
    fontSize = 12.sp,
    letterSpacing = 1.4.sp,
)

/**
 * Тема приложения — тёмная premium по умолчанию (фирменный вид Infinity Connect).
 * Dynamic color не используем: держим брэнд-палитру.
 */
@Composable
fun InfinityTheme(
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(LocalInfinityGradients provides gradients) {
        MaterialTheme(
            colorScheme = DarkColors,
            typography = InfinityTypography,
            content = content,
        )
    }
}
