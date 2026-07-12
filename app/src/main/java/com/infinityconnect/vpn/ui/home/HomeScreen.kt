package com.infinityconnect.vpn.ui.home

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.NetworkPing
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.infinityconnect.vpn.ui.components.FullScreenLoading
import com.infinityconnect.vpn.ui.components.FullScreenMessage
import com.infinityconnect.vpn.ui.components.StatusPill
import com.infinityconnect.vpn.ui.theme.EyebrowStyle
import com.infinityconnect.vpn.ui.theme.InfinityColors
import com.infinityconnect.vpn.ui.theme.LocalInfinityGradients
import com.infinityconnect.vpn.ui.util.formatDuration
import com.infinityconnect.vpn.ui.util.formatSpeed
import com.infinityconnect.vpn.vpn.TunnelState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onOpenProfile: () -> Unit,
    onOpenSettings: () -> Unit,
    onLogout: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val tunnel by viewModel.tunnelState.collectAsStateWithLifecycle()
    val stats by viewModel.stats.collectAsStateWithLifecycle()
    val gradients = LocalInfinityGradients.current

    // Лаунчер системного разрешения VPN: при успехе — подключаемся.
    val vpnPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            viewModel.connectAfterPermission()
        }
    }

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Infinity Connect",
                        fontWeight = FontWeight.Bold,
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
                actions = {
                    IconButton(onClick = { viewModel.refresh() }) {
                        Icon(Icons.Filled.Refresh, contentDescription = "Обновить подписки")
                    }
                    OverflowMenu(
                        onProfile = onOpenProfile,
                        onSettings = onOpenSettings,
                        onLogout = { viewModel.logout(onLogout) },
                    )
                },
            )
        },
        modifier = Modifier.background(gradients.screen),
    ) { padding ->
        when {
            ui.loadingFirstTime && ui.keys.isEmpty() ->
                FullScreenLoading(Modifier.padding(padding))

            ui.keys.isEmpty() && ui.error != null ->
                FullScreenMessage(
                    title = "Не удалось загрузить",
                    description = ui.error,
                    actionLabel = "Повторить",
                    onAction = { viewModel.refresh() },
                    modifier = Modifier.padding(padding),
                )

            ui.keys.isEmpty() ->
                FullScreenMessage(
                    title = "Нет доступных ключей",
                    description = "У вашего аккаунта пока нет активных подписок. " +
                        "Оформите подписку на сайте — ключи появятся автоматически.",
                    actionLabel = "Обновить",
                    onAction = { viewModel.refresh() },
                    modifier = Modifier.padding(padding),
                )

            else -> HomeContent(
                ui = ui,
                tunnel = tunnel,
                stats = stats,
                onRefresh = { viewModel.refresh() },
                onPingAll = { viewModel.pingAllSelected() },
                onSelectKey = viewModel::selectKey,
                onSelectServer = viewModel::selectServer,
                onToggle = {
                    if (viewModel.isConnectingOrConnected()) {
                        viewModel.disconnect()
                    } else {
                        val intent = viewModel.vpnPrepareIntent()
                        if (intent != null) {
                            vpnPermissionLauncher.launch(intent)
                        } else {
                            viewModel.connect()
                        }
                    }
                },
                modifier = Modifier.padding(padding),
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HomeContent(
    ui: HomeUiState,
    tunnel: TunnelState,
    stats: com.infinityconnect.vpn.vpn.TunnelStats,
    onRefresh: () -> Unit,
    onPingAll: () -> Unit,
    onSelectKey: (Long) -> Unit,
    onSelectServer: (com.infinityconnect.vpn.domain.model.SubscriptionServer) -> Unit,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    PullToRefreshBox(
        isRefreshing = ui.refreshing,
        onRefresh = onRefresh,
        modifier = modifier.fillMaxSize(),
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                ConnectionHeader(
                    tunnel = tunnel,
                    stats = stats,
                    canConnect = ui.selectedKeyId != null,
                    onToggle = onToggle,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "МОИ ПОДПИСКИ",
                    style = EyebrowStyle,
                    color = InfinityColors.Muted,
                    modifier = Modifier.padding(start = 4.dp, bottom = 4.dp),
                )
            }
            // Под каждым ключом раскрываем список его серверов, если ключ выбран.
            ui.keys.forEach { key ->
                item(key = "key-${key.id}") {
                    KeyCard(
                        key = key,
                        selected = key.id == ui.selectedKeyId,
                        onClick = { onSelectKey(key.id) },
                    )
                }
                if (key.id == ui.selectedKeyId) {
                    if (ui.serversLoading && ui.servers.isEmpty()) {
                        item(key = "srv-loading-${key.id}") {
                            ServersLoadingRow()
                        }
                    }
                    if (ui.servers.isNotEmpty()) {
                        item(key = "srv-header-${key.id}") {
                            ServersHeader(
                                pinging = ui.pinging,
                                onPingAll = onPingAll,
                            )
                        }
                    }
                    items(ui.servers, key = { "srv-${key.id}-${it.index}" }) { server ->
                        ServerRow(
                            server = server,
                            selected = server.index == ui.selectedServerIndex,
                            pingMethod = ui.pingMethod,
                            onClick = { onSelectServer(server) },
                        )
                    }
                }
            }
            item { Spacer(Modifier.height(16.dp)) }
        }
    }
}

/** Hero-блок: статус-пилл, круглая кнопка со свечением, статистика. */
@Composable
private fun ConnectionHeader(
    tunnel: TunnelState,
    stats: com.infinityconnect.vpn.vpn.TunnelStats,
    canConnect: Boolean,
    onToggle: () -> Unit,
) {
    val isActive = tunnel is TunnelState.Connected || tunnel is TunnelState.Connecting
    Column(
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        StatusPill(text = statusTitle(tunnel), color = statusColor(tunnel))
        Spacer(Modifier.height(20.dp))
        ConnectHero(
            tunnel = tunnel,
            enabled = canConnect || isActive,
            onToggle = onToggle,
        )
        Spacer(Modifier.height(20.dp))
        if (tunnel is TunnelState.Connected) {
            StatsRow(stats)
        } else {
            Text(
                text = if (canConnect) "Нажмите, чтобы подключиться" else "Выберите сервер",
                style = MaterialTheme.typography.bodyMedium,
                color = InfinityColors.Muted,
            )
        }
    }
}

/** Три метрики активной сессии: скачано, отдано, время. */
@Composable
private fun StatsRow(stats: com.infinityconnect.vpn.vpn.TunnelStats) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        StatTile("↓ Скачано", formatSpeed(stats.downloadBytesPerSec), Modifier.weight(1f))
        StatTile("↑ Отдано", formatSpeed(stats.uploadBytesPerSec), Modifier.weight(1f))
        StatTile("Время", formatDuration(stats.sessionSeconds), Modifier.weight(1f))
    }
}

@Composable
private fun StatTile(label: String, value: String, modifier: Modifier = Modifier) {
    com.infinityconnect.vpn.ui.components.GlassCard(
        modifier = modifier,
        contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 12.dp, horizontal = 8.dp),
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
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
}

/** Сендвич-меню (⋮/☰): Профиль, Настройки, Выйти. */
@Composable
private fun OverflowMenu(
    onProfile: () -> Unit,
    onSettings: () -> Unit,
    onLogout: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    IconButton(onClick = { expanded = true }) {
        Icon(Icons.Filled.Menu, contentDescription = "Меню")
    }
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = { expanded = false },
        containerColor = InfinityColors.Surface,
    ) {
        DropdownMenuItem(
            text = { Text("Профиль", color = InfinityColors.OnSurface) },
            leadingIcon = { Icon(Icons.Filled.Person, null, tint = InfinityColors.Muted) },
            onClick = { expanded = false; onProfile() },
        )
        DropdownMenuItem(
            text = { Text("Настройки", color = InfinityColors.OnSurface) },
            leadingIcon = { Icon(Icons.Filled.Settings, null, tint = InfinityColors.Muted) },
            onClick = { expanded = false; onSettings() },
        )
        DropdownMenuItem(
            text = { Text("Выйти", color = InfinityColors.Coral) },
            leadingIcon = { Icon(Icons.AutoMirrored.Filled.Logout, null, tint = InfinityColors.Coral) },
            onClick = { expanded = false; onLogout() },
        )
    }
}

/** Заголовок списка серверов с кнопкой «Пинг всех». */
@Composable
private fun ServersHeader(pinging: Boolean, onPingAll: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 6.dp, bottom = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "СЕРВЕРЫ",
            style = EyebrowStyle,
            color = InfinityColors.Muted,
            modifier = Modifier.padding(start = 4.dp),
        )
        OutlinedButton(
            onClick = onPingAll,
            enabled = !pinging,
            border = com.infinityconnect.vpn.ui.components.accentOutline(),
        ) {
            if (pinging) {
                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(8.dp))
                Text("Пинг…")
            } else {
                Icon(
                    Icons.Filled.NetworkPing,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text("Пинг всех")
            }
        }
    }
}

private fun statusTitle(tunnel: TunnelState): String = when (tunnel) {
    TunnelState.Connected -> "Подключено"
    TunnelState.Connecting -> "Подключение…"
    TunnelState.Disconnecting -> "Отключение…"
    TunnelState.Disconnected -> "Не подключено"
    is TunnelState.Error -> "Ошибка"
}

private fun statusColor(tunnel: TunnelState): Color = when (tunnel) {
    TunnelState.Connected -> InfinityColors.Mint
    is TunnelState.Error -> InfinityColors.Coral
    TunnelState.Connecting, TunnelState.Disconnecting -> InfinityColors.AccentCyan
    else -> InfinityColors.Muted
}
