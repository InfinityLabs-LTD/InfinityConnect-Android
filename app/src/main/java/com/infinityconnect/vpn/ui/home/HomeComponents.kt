package com.infinityconnect.vpn.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.infinityconnect.vpn.domain.model.SubscriptionServer
import com.infinityconnect.vpn.domain.model.VpnKey
import com.infinityconnect.vpn.domain.model.VpnProtocol
import com.infinityconnect.vpn.ui.util.formatBytes
import com.infinityconnect.vpn.ui.util.formatDate

/** Карточка ключа (подписки) в списке. */
@Composable
fun KeyCard(
    key: VpnKey,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            },
        ),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = key.countryFlag ?: "🌐",
                style = MaterialTheme.typography.headlineSmall,
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = key.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = buildString {
                        append(key.location ?: "—")
                        append(" · ")
                        append(protocolLabel(key.protocol))
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                key.expiresAt?.let {
                    Text(
                        text = "До ${formatDate(it)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (key.trafficLimitBytes != null && key.trafficLimitBytes > 0) {
                    val used = key.usedTrafficBytes ?: 0
                    Text(
                        text = "Трафик: ${formatBytes(used)} / ${formatBytes(key.trafficLimitBytes)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            // Индикатор активности ключа.
            StatusDot(active = key.isActive)
        }
    }
}

@Composable
private fun StatusDot(active: Boolean) {
    val color = if (active) Color(0xFF34C759) else Color(0xFF8E8E93)
    androidx.compose.foundation.Canvas(modifier = Modifier.size(10.dp)) {
        drawCircle(color)
    }
}

private fun protocolLabel(p: VpnProtocol): String = when (p) {
    VpnProtocol.VLESS -> "VLESS"
    VpnProtocol.HYSTERIA2 -> "Hysteria2"
    VpnProtocol.UNKNOWN -> "—"
}

/**
 * Строка сервера подписки (стиль Happ): флаг, имя, метаданные
 * (VLESS | TCP | Reality | JSON) и пинг справа. Вложена под ключом (отступ).
 */
@Composable
fun ServerRow(
    server: SubscriptionServer,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        onClick = onClick,
        modifier = modifier.fillMaxWidth().padding(start = 16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            },
        ),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = server.flag ?: "🌐",
                style = MaterialTheme.typography.titleLarge,
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = server.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = server.meta,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            PingLabel(server.pingMs)
        }
    }
}

/** Пинг: «478 мс» с цветом по качеству; «…» пока измеряется; «—» недоступен. */
@Composable
private fun PingLabel(pingMs: Int?) {
    val (text, color) = when {
        pingMs == null -> "…" to MaterialTheme.colorScheme.onSurfaceVariant
        pingMs < 0 -> "—" to Color(0xFF8E8E93)
        pingMs < 150 -> "$pingMs мс" to Color(0xFF34C759)
        pingMs < 400 -> "$pingMs мс" to Color(0xFFFF9500)
        else -> "$pingMs мс" to Color(0xFFFF3B30)
    }
    Text(text = text, style = MaterialTheme.typography.bodySmall, color = color)
}

/** Индикатор загрузки списка серверов. */
@Composable
fun ServersLoadingRow(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.fillMaxWidth().padding(start = 16.dp, top = 4.dp, bottom = 4.dp),
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
    }
}
