package com.infinityconnect.vpn.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Фирменная палитра Infinity Connect (тёмно-синяя основа + акцент).
private val Accent = Color(0xFF4F8CFF)
private val AccentDark = Color(0xFF2E6BE0)

private val DarkColors = darkColorScheme(
    primary = Accent,
    onPrimary = Color.White,
    secondary = AccentDark,
    background = Color(0xFF0B1020),
    surface = Color(0xFF141A2E),
    onBackground = Color(0xFFE6EAF2),
    onSurface = Color(0xFFE6EAF2),
)

private val LightColors = lightColorScheme(
    primary = AccentDark,
    onPrimary = Color.White,
    secondary = Accent,
    background = Color(0xFFF7F9FC),
    surface = Color.White,
)

/**
 * Тема приложения. Поддерживает светлый и тёмный режимы; по умолчанию следует
 * системной настройке. Dynamic color сознательно не используем — держим
 * фирменные цвета Infinity Connect.
 */
@Composable
fun InfinityTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = Typography(),
        content = content,
    )
}
