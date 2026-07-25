package com.infinityconnect.vpn.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.infinityconnect.vpn.ui.components.GlassCard
import com.infinityconnect.vpn.ui.theme.InfinityColors

/**
 * Экран «Настройки» — хаб-меню из отдельных пунктов. Каждый пункт открывает
 * свой экран: маршрутизация, настройки пинга, о приложении.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onOpenRouting: () -> Unit,
    onOpenPing: () -> Unit,
    onOpenLogs: () -> Unit,
    onOpenAbout: () -> Unit,
) {
    SettingsScaffold(title = "Настройки", onBack = onBack) {
        MenuItem(
            icon = Icons.Outlined.Share,
            title = "Маршрутизация",
            subtitle = "По приложениям и по сайтам",
            onClick = onOpenRouting,
        )
        MenuItem(
            icon = Icons.Filled.Speed,
            title = "Настройки пинга",
            subtitle = "Протокол, режим, таймаут и URL-тест",
            onClick = onOpenPing,
        )
        MenuItem(
            icon = Icons.AutoMirrored.Filled.List,
            title = "Логи",
            subtitle = "Журнал приложения и ядер, отправка в поддержку",
            onClick = onOpenLogs,
        )
        MenuItem(
            icon = Icons.Filled.Info,
            title = "О приложении",
            subtitle = "Версия, ядра, разработчик",
            onClick = onOpenAbout,
        )
    }
}

@Composable
private fun MenuItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
) {
    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Icon(icon, contentDescription = null, tint = InfinityColors.AccentBlue)
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    color = InfinityColors.OnSurface,
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = InfinityColors.Muted,
                )
            }
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = InfinityColors.Muted,
            )
        }
    }
}
