package com.infinityconnect.vpn.ui.home

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.infinityconnect.vpn.ui.components.FullScreenLoading
import com.infinityconnect.vpn.ui.components.FullScreenMessage
import com.infinityconnect.vpn.ui.util.formatDuration
import com.infinityconnect.vpn.ui.util.formatSpeed
import com.infinityconnect.vpn.vpn.TunnelState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onOpenProfile: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val tunnel by viewModel.tunnelState.collectAsStateWithLifecycle()
    val stats by viewModel.stats.collectAsStateWithLifecycle()

    // Лаунчер системного разрешения VPN: при успехе — подключаемся.
    val vpnPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            viewModel.connectAfterPermission()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Infinity Connect") },
                actions = {
                    IconButton(onClick = onOpenProfile) {
                        Icon(Icons.Filled.Person, contentDescription = "Профиль")
                    }
                },
            )
        },
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
                statsText = statsLine(tunnel, stats),
                onRefresh = { viewModel.refresh() },
                onSelectKey = viewModel::selectKey,
                onSelectServer = viewModel::selectServer,
                onToggle = {
                    if (viewModel.isConnectingOrConnected()) {
                        viewModel.disconnect()
                    } else {
                        // Проверяем системное разрешение VPN перед подключением.
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
    statsText: String,
    onRefresh: () -> Unit,
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
            modifier = Modifier.fillMaxSize().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                ConnectionPanel(
                    tunnel = tunnel,
                    statsText = statsText,
                    canConnect = ui.selectedKeyId != null,
                    onToggle = onToggle,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "Мои ключи",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            // Под каждым ключом (стиль Happ) раскрываем список его серверов,
            // если ключ выбран.
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
                    items(ui.servers, key = { "srv-${key.id}-${it.index}" }) { server ->
                        ServerRow(
                            server = server,
                            selected = server.index == ui.selectedServerIndex,
                            onClick = { onSelectServer(server) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ConnectionPanel(
    tunnel: TunnelState,
    statsText: String,
    canConnect: Boolean,
    onToggle: () -> Unit,
) {
    val isActive = tunnel is TunnelState.Connected || tunnel is TunnelState.Connecting
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = statusTitle(tunnel),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = statusColor(tunnel),
        )
        if (statsText.isNotEmpty()) {
            Text(text = statsText, style = MaterialTheme.typography.bodyMedium)
        }
        Button(
            onClick = onToggle,
            enabled = canConnect || isActive,
            colors = if (isActive) {
                ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
            } else {
                ButtonDefaults.buttonColors()
            },
            modifier = Modifier.fillMaxWidth().height(52.dp),
        ) {
            Text(if (isActive) "Отключить" else "Подключить")
        }
    }
}

private fun statusTitle(tunnel: TunnelState): String = when (tunnel) {
    TunnelState.Connected -> "Подключено"
    TunnelState.Connecting -> "Подключение…"
    TunnelState.Disconnecting -> "Отключение…"
    TunnelState.Disconnected -> "Не подключено"
    is TunnelState.Error -> "Ошибка: ${tunnel.message}"
}

private fun statusColor(tunnel: TunnelState): Color = when (tunnel) {
    TunnelState.Connected -> Color(0xFF34C759)
    is TunnelState.Error -> Color(0xFFFF3B30)
    else -> Color.Unspecified
}

/** Строка статистики (скорость + время) при активном соединении. */
private fun statsLine(tunnel: TunnelState, stats: com.infinityconnect.vpn.vpn.TunnelStats): String {
    if (tunnel !is TunnelState.Connected) return ""
    val up = formatSpeed(stats.uploadBytesPerSec)
    val down = formatSpeed(stats.downloadBytesPerSec)
    val time = formatDuration(stats.sessionSeconds)
    return "↑ $up   ↓ $down   ⏱ $time"
}
