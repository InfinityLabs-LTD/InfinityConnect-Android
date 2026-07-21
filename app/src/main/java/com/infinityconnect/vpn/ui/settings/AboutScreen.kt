package com.infinityconnect.vpn.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.infinityconnect.vpn.BuildConfig
import com.infinityconnect.vpn.BuildFlags
import com.infinityconnect.vpn.ui.components.GlassCard
import com.infinityconnect.vpn.ui.theme.InfinityColors

/** Экран «О приложении»: версия, ядра, разработчик. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(onBack: () -> Unit) {
    SettingsScaffold(title = "О приложении", onBack = onBack) {
        GlassCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.fillMaxWidth()) {
                SectionTitle("О ПРИЛОЖЕНИИ")
                InfoRow("Приложение", "Infinity Connect")
                InfoRow("Разработчик", BuildFlags.DEVELOPER)
                InfoRow("Версия", "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
                InfoRow("Ядро Xray", BuildFlags.XRAY_CORE_VERSION)
                InfoRow("Ядро Hysteria2", BuildFlags.HYSTERIA2_CORE_VERSION)
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}

/** Строка «ключ — значение». */
@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = InfinityColors.Muted,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = InfinityColors.OnSurface,
        )
    }
}
