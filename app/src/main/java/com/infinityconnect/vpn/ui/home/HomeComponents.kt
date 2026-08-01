package com.infinityconnect.vpn.ui.home

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.infinityconnect.vpn.R
import com.infinityconnect.vpn.domain.model.KeyStatus
import com.infinityconnect.vpn.domain.model.PingMethod
import com.infinityconnect.vpn.domain.model.SubscriptionServer
import com.infinityconnect.vpn.domain.model.VpnKey
import com.infinityconnect.vpn.domain.model.VpnProtocol
import com.infinityconnect.vpn.domain.model.status
import com.infinityconnect.vpn.ui.components.EmojiBadge
import com.infinityconnect.vpn.ui.components.GlassCard
import com.infinityconnect.vpn.ui.components.StatusPill
import com.infinityconnect.vpn.ui.settings.pingColor
import com.infinityconnect.vpn.ui.theme.InfinityColors
import com.infinityconnect.vpn.ui.util.formatBytes
import com.infinityconnect.vpn.ui.util.formatDate
import com.infinityconnect.vpn.ui.util.isExpired

/** Карточка ключа (подписки) в списке. */
@Composable
fun KeyCard(
    key: VpnKey,
    number: Int,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    GlassCard(
        modifier = modifier.fillMaxWidth().clickable(onClick = onClick),
        highlighted = selected,
        contentPadding = PaddingValues(14.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            // Значок: корона у премиум-ключа (его сервер — премиум-хост),
            // иначе глобус (обычная подписка / «все сервера»).
            EmojiBadge(emoji = if (key.isPremium) "👑" else "🌐", size = 46.dp)
            val status = key.status(expired = isExpired(key.expiresAt))
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        // Порядковый номер ключа у пользователя; название — в скобках.
                        text = keyTitle(number, key.name),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = InfinityColors.OnSurface,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    ProtocolChip(key.protocol)
                }
                // Строка статуса: для активной — срок, для проблемной — причина.
                Text(
                    text = keyStatusLine(status, key.expiresAt),
                    style = MaterialTheme.typography.bodySmall,
                    color = statusTextColor(status),
                )
                if (key.trafficLimitBytes != null && key.trafficLimitBytes > 0) {
                    val used = key.usedTrafficBytes ?: 0
                    val context = androidx.compose.ui.platform.LocalContext.current
                    Text(
                        text = stringResource(
                            R.string.home_key_traffic,
                            context.formatBytes(used),
                            context.formatBytes(key.trafficLimitBytes),
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = if (key.trafficExhausted) InfinityColors.Coral else InfinityColors.MutedDim,
                    )
                }
                // Лимит устройств — показываем только если сервер прислал данные.
                if (key.deviceLimit != null && key.deviceLimit > 0) {
                    Text(
                        text = stringResource(
                            R.string.home_key_devices,
                            key.devicesUsed ?: 0,
                            key.deviceLimit,
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = if (key.devicesExhausted) InfinityColors.Coral else InfinityColors.MutedDim,
                    )
                }
            }
            KeyStatusDot(status)
        }
    }
}

/** Компактный чип протокола (VLESS / Hysteria2). */
@Composable
private fun ProtocolChip(protocol: VpnProtocol) {
    val label = protocolLabel(protocol) ?: return
    Text(
        text = label,
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.SemiBold,
        color = InfinityColors.AccentBlue,
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(InfinityColors.AccentBlue.copy(alpha = 0.12f))
            .padding(horizontal = 7.dp, vertical = 2.dp),
    )
}

/** Индикатор-точка статуса ключа: зелёный активна, серый/жёлтый/красный иначе. */
@Composable
private fun KeyStatusDot(status: KeyStatus) {
    val color by animateColorAsState(statusDotColor(status), label = "dot")
    Box(
        modifier = Modifier
            .size(10.dp)
            .clip(RoundedCornerShape(50))
            .background(color)
            .border(3.dp, color.copy(alpha = 0.18f), RoundedCornerShape(50)),
    )
}

private fun statusDotColor(status: KeyStatus): Color = when (status) {
    KeyStatus.ACTIVE -> InfinityColors.Mint
    KeyStatus.LIMITED -> InfinityColors.Amber
    KeyStatus.EXPIRED -> InfinityColors.Coral
    KeyStatus.DISABLED -> InfinityColors.MutedDim
}

private fun statusTextColor(status: KeyStatus): Color = when (status) {
    KeyStatus.ACTIVE -> InfinityColors.Muted
    KeyStatus.LIMITED -> InfinityColors.Amber
    KeyStatus.EXPIRED -> InfinityColors.Coral
    KeyStatus.DISABLED -> InfinityColors.MutedDim
}

/** Строка статуса под названием ключа. */
@Composable
private fun keyStatusLine(status: KeyStatus, expiresAt: String?): String = when (status) {
    KeyStatus.ACTIVE ->
        if (expiresAt.isNullOrBlank()) stringResource(R.string.home_key_active)
        else stringResource(R.string.home_key_active_until, formatDate(expiresAt))
    KeyStatus.EXPIRED ->
        if (expiresAt.isNullOrBlank()) stringResource(R.string.home_key_expired)
        else stringResource(R.string.home_key_expired_at, formatDate(expiresAt))
    KeyStatus.DISABLED -> stringResource(R.string.home_key_disabled)
    KeyStatus.LIMITED -> stringResource(R.string.home_key_limited)
}

/**
 * Заголовок ключа: порядковый номер у пользователя, а название — в скобках,
 * если оно есть и осмысленно (не совпадает с дефолтным «Ключ #id»).
 */
@Composable
private fun keyTitle(number: Int, name: String): String {
    val label = name.trim()
    // Дефолтное имя с сервера («Ключ #12») в заголовок не выносим — это шум.
    val hasName = label.isNotBlank() &&
        !label.startsWith(stringResource(R.string.home_key_default_prefix))
    return if (hasName) stringResource(R.string.home_key_title_named, number, label)
    else stringResource(R.string.home_key_title, number)
}

/** Имя протокола — бренды, не переводятся. null у неизвестного (чип не рисуем). */
private fun protocolLabel(p: VpnProtocol): String? = when (p) {
    VpnProtocol.VLESS -> "VLESS"
    VpnProtocol.HYSTERIA2 -> "Hysteria2"
    VpnProtocol.UNKNOWN -> null
}

/**
 * Строка сервера подписки: флаг-иконка слева, имя, метаданные и пинг-пилл справа.
 * Вложена под ключом (отступ). Выбранный — с акцентной вертикальной полосой.
 */
@Composable
fun ServerRow(
    server: SubscriptionServer,
    selected: Boolean,
    pingMethod: PingMethod,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    /** Сервер с наименьшим пингом в подписке — показываем бейдж «Быстрейший». */
    isFastest: Boolean = false,
    /**
     * Подписка недоступна (отключена/истекла/лимит): строка приглушена, вместо
     * пинга пилл «Недоступно», тап обрабатывает VM (показывает причину).
     */
    blocked: Boolean = false,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(if (selected) InfinityColors.SurfaceHi else InfinityColors.Surface)
            .border(
                width = 1.dp,
                color = if (selected) InfinityColors.AccentBlue.copy(alpha = 0.55f) else InfinityColors.Stroke,
                shape = RoundedCornerShape(16.dp),
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        EmojiBadge(emoji = server.flag ?: stringResource(R.string.home_globe_fallback), size = 38.dp)
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = server.name,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = if (blocked) InfinityColors.MutedDim else InfinityColors.OnSurface,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (isFastest && !blocked) FastestBadge()
            }
            Text(
                text = server.meta,
                style = MaterialTheme.typography.labelMedium,
                color = if (blocked) InfinityColors.MutedDim else InfinityColors.Muted,
            )
        }
        if (blocked) {
            StatusPill(text = stringResource(R.string.home_server_blocked), color = InfinityColors.Amber)
        } else {
            PingLabel(server.pingMs, pingMethod)
        }
    }
}

/**
 * Пинг как цветной пилл. Значение всегда в мс; «…» пока измеряется; «—»
 * недоступен. Цвет зависит от метода пинга (см. [pingColor]).
 */
@Composable
private fun PingLabel(pingMs: Int?, method: PingMethod) {
    val text = when {
        pingMs == null -> stringResource(R.string.home_ping_measuring)
        pingMs < 0 -> stringResource(R.string.home_ping_unavailable)
        else -> stringResource(R.string.home_ping_ms, pingMs)
    }
    StatusPill(text = text, color = pingColor(method, pingMs))
}

/** Бейдж «⚡ Быстрейший» на сервере с наименьшим пингом в подписке. */
@Composable
private fun FastestBadge() {
    Text(
        text = stringResource(R.string.home_server_fastest),
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.SemiBold,
        color = InfinityColors.Mint,
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(InfinityColors.Mint.copy(alpha = 0.12f))
            .padding(horizontal = 7.dp, vertical = 2.dp),
    )
}

/** Индикатор загрузки списка серверов. */
@Composable
fun ServersLoadingRow(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.fillMaxWidth().padding(start = 14.dp, top = 6.dp, bottom = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(22.dp),
            strokeWidth = 2.dp,
            color = InfinityColors.AccentBlue,
        )
    }
}
