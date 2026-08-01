package com.infinityconnect.vpn.ui.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.AlternateEmail
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.SupportAgent
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.infinityconnect.vpn.R
import com.infinityconnect.vpn.domain.model.UserInfo
import com.infinityconnect.vpn.ui.components.FullScreenLoading
import com.infinityconnect.vpn.ui.components.GlassCard
import com.infinityconnect.vpn.ui.components.StatusPill
import com.infinityconnect.vpn.ui.theme.EyebrowStyle
import com.infinityconnect.vpn.ui.theme.InfinityColors
import com.infinityconnect.vpn.ui.theme.LocalInfinityGradients
import com.infinityconnect.vpn.ui.util.daysUntil
import com.infinityconnect.vpn.ui.util.formatDate
import com.infinityconnect.vpn.ui.util.openInBrowser

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    onBack: () -> Unit,
    onLoggedOut: () -> Unit,
    viewModel: ProfileViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            androidx.compose.material3.TopAppBar(
                title = { Text(stringResource(R.string.profile_title), fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.common_back),
                        )
                    }
                },
            )
        },
    ) { padding ->
        if (state.loading) {
            FullScreenLoading(Modifier.padding(padding))
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Spacer(Modifier.height(4.dp))

            // --- Hero-шапка: аватар-инициалы, логин, бейдж статуса подписки ---
            ProfileHero(user = state.user)

            // --- Подписка ---
            // Срок берём из earliest_expiry — ближайшая дата среди активных
            // ключей (реальный «до когда»), а не максимум по всем ключам.
            SubscriptionCard(
                active = state.user?.isSubscriptionActive == true,
                expiresAt = state.subscription?.earliestExpiry,
                keys = state.keys,
                keysCount = state.subscription?.keysCount,
                totalMonths = state.subscription?.totalMonths,
                totalSpent = state.subscription?.totalSpent,
            )

            // --- Аккаунт ---
            SectionCard(title = stringResource(R.string.profile_section_account), icon = Icons.Filled.AlternateEmail) {
                InfoRow(
                    Icons.Filled.WorkspacePremium,
                    stringResource(R.string.profile_login),
                    state.user?.username ?: stringResource(R.string.common_dash),
                )
                // E-mail показываем только если это действительно похоже на почту:
                // сервер иногда присылает служебное значение ("admin") — прячем его.
                state.user?.email?.takeIf(::looksLikeEmail)?.let {
                    Divider()
                    InfoRow(Icons.Filled.AlternateEmail, stringResource(R.string.profile_email), it)
                }
                state.planLabel?.let {
                    Divider()
                    InfoRow(Icons.Filled.VpnKey, stringResource(R.string.profile_plan), it)
                }
            }

            // --- Поддержка ---
            state.supportUrl?.let { support ->
                OutlinedButton(
                    onClick = { context.openInBrowser(support) },
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape = RoundedCornerShape(16.dp),
                    border = com.infinityconnect.vpn.ui.components.accentOutline(),
                ) {
                    Icon(
                        Icons.Filled.SupportAgent,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                        tint = InfinityColors.AccentBlue,
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(stringResource(R.string.profile_support), color = InfinityColors.OnSurface)
                }
            }

            OutlinedButton(
                onClick = { viewModel.logout(onLoggedOut) },
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(16.dp),
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    InfinityColors.Coral.copy(alpha = 0.5f),
                ),
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.Logout,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = InfinityColors.Coral,
                )
                Spacer(Modifier.width(10.dp))
                Text(stringResource(R.string.profile_logout), color = InfinityColors.Coral)
            }

            Spacer(Modifier.height(8.dp))
        }
    }
}

/** Шапка: круглый аватар с инициалами (акцентный градиент), логин и бейдж подписки. */
@Composable
private fun ProfileHero(user: UserInfo?) {
    val gradients = LocalInfinityGradients.current
    val active = user?.isSubscriptionActive == true
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(88.dp)
                .clip(CircleShape)
                .background(gradients.accent)
                .border(1.dp, InfinityColors.Stroke, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = initials(user?.username),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = Color.White,
            )
        }
        Spacer(Modifier.height(14.dp))
        Text(
            text = user?.username ?: stringResource(R.string.common_dash),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = InfinityColors.OnSurface,
        )
        Spacer(Modifier.height(8.dp))
        StatusPill(
            text = stringResource(
                if (active) R.string.profile_subscription_active
                else R.string.profile_subscription_inactive,
            ),
            color = if (active) InfinityColors.Mint else InfinityColors.Muted,
        )
    }
}

/** Карточка подписки: статус, прогресс-бар до окончания, ключи, потрачено. */
@Composable
private fun SubscriptionCard(
    active: Boolean,
    expiresAt: String?,
    keys: List<com.infinityconnect.vpn.domain.model.VpnKey>,
    keysCount: Int?,
    totalMonths: Int?,
    totalSpent: Double?,
) {
    val accent = if (active) InfinityColors.Mint else InfinityColors.Muted
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(InfinityColors.Surface)
            .border(1.dp, LocalInfinityGradients.current.cardStroke, RoundedCornerShape(20.dp))
            .height(IntrinsicSize.Min),
    ) {
        // Цветная левая грань — визуальный маркер статуса.
        Box(
            modifier = Modifier
                .width(4.dp)
                .fillMaxHeight()
                .background(accent),
        )
        Column(modifier = Modifier.fillMaxWidth().padding(18.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.profile_subscription_eyebrow),
                    style = EyebrowStyle,
                    color = InfinityColors.Muted,
                )
                Icon(
                    Icons.Filled.CalendarMonth,
                    contentDescription = null,
                    tint = accent,
                    modifier = Modifier.size(20.dp),
                )
            }
            Spacer(Modifier.height(12.dp))

            val remaining = daysUntil(expiresAt)
            Text(
                text = when {
                    !active -> stringResource(R.string.profile_subscription_off)
                    remaining != null && remaining >= 0 -> stringResource(
                        R.string.profile_days_left,
                        pluralStringResource(R.plurals.profile_days, remaining, remaining),
                    )
                    else -> stringResource(R.string.profile_subscription_on)
                },
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = InfinityColors.OnSurface,
            )
            if (!expiresAt.isNullOrBlank()) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = stringResource(R.string.profile_until, formatDate(expiresAt)),
                    style = MaterialTheme.typography.bodyMedium,
                    color = InfinityColors.Muted,
                )
            }

            // Сроки каждой подписки — не только ближайшей.
            if (keys.isNotEmpty()) {
                Spacer(Modifier.height(16.dp))
                Divider()
                Spacer(Modifier.height(4.dp))
                keys.forEach { key -> KeyExpiryRow(key) }
            }

            Spacer(Modifier.height(16.dp))
            Divider()
            Spacer(Modifier.height(12.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                keysCount?.let {
                    MetricTile(
                        stringResource(R.string.profile_metric_keys),
                        it.toString(),
                        Modifier.weight(1f),
                    )
                }
                totalMonths?.takeIf { it > 0 }?.let {
                    MetricTile(
                        stringResource(R.string.profile_metric_months),
                        it.toString(),
                        Modifier.weight(1f),
                    )
                }
                totalSpent?.takeIf { it > 0 }?.let {
                    MetricTile(
                        stringResource(R.string.profile_metric_spent),
                        formatMoney(it),
                        Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

/** Строка «имя ключа — осталось N дней (до даты)» в карточке подписки. */
@Composable
private fun KeyExpiryRow(key: com.infinityconnect.vpn.domain.model.VpnKey) {
    val remaining = daysUntil(key.expiresAt)
    val expired = remaining != null && remaining < 0
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = if (key.isPremium) "👑" else "🌐",
            style = MaterialTheme.typography.bodyMedium,
        )
        Spacer(Modifier.width(10.dp))
        Text(
            text = key.name,
            style = MaterialTheme.typography.bodyMedium,
            color = InfinityColors.OnSurface,
            maxLines = 1,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(10.dp))
        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = when {
                    expired || !key.isActive -> stringResource(R.string.profile_key_expired)
                    remaining != null ->
                        pluralStringResource(R.plurals.profile_days, remaining, remaining)
                    else -> stringResource(R.string.profile_key_forever)
                },
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = if (expired || !key.isActive) InfinityColors.Coral else InfinityColors.OnSurface,
            )
            key.expiresAt?.takeIf { it.isNotBlank() && !expired && key.isActive }?.let {
                Text(
                    text = stringResource(R.string.profile_until, formatDate(it)),
                    style = MaterialTheme.typography.labelSmall,
                    color = InfinityColors.Muted,
                )
            }
        }
    }
}

@Composable
private fun MetricTile(label: String, value: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(InfinityColors.SpaceElevated)
            .padding(vertical = 12.dp, horizontal = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = InfinityColors.OnSurface,
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = InfinityColors.Muted,
        )
    }
}

@Composable
private fun SectionCard(title: String, icon: ImageVector, content: @Composable () -> Unit) {
    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = InfinityColors.Muted,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = title.uppercase(),
                    style = EyebrowStyle,
                    color = InfinityColors.Muted,
                )
            }
            Spacer(Modifier.height(12.dp))
            content()
        }
    }
}

@Composable
private fun InfoRow(icon: ImageVector, label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = InfinityColors.MutedDim,
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.width(12.dp))
        Text(label, color = InfinityColors.Muted, modifier = Modifier.weight(1f))
        Text(value, fontWeight = FontWeight.Medium, color = InfinityColors.OnSurface)
    }
}

@Composable
private fun Divider() {
    androidx.compose.material3.HorizontalDivider(color = InfinityColors.Stroke)
}

// --- Хелперы форматирования/логики ---

private fun initials(name: String?): String {
    val n = name?.trim().orEmpty()
    if (n.isEmpty()) return "?"
    val parts = n.split(" ", "-", "_").filter { it.isNotBlank() }
    return when {
        parts.size >= 2 -> "${parts[0].first()}${parts[1].first()}".uppercase()
        else -> n.take(2).uppercase()
    }
}

private fun looksLikeEmail(s: String): Boolean {
    val t = s.trim()
    val at = t.indexOf('@')
    return at > 0 && t.indexOf('.', at) > at + 1
}

@Composable
private fun formatMoney(value: Double): String {
    val rounded = if (value % 1.0 == 0.0) value.toInt().toString()
    else String.format(java.util.Locale.getDefault(), "%.2f", value)
    return stringResource(R.string.profile_money, rounded)
}
