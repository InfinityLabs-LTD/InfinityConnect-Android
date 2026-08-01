package com.infinityconnect.vpn.ui.home

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.NetworkPing
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.infinityconnect.vpn.R
import com.infinityconnect.vpn.ui.components.FullScreenLoading
import com.infinityconnect.vpn.ui.components.FullScreenMessage
import com.infinityconnect.vpn.ui.components.StatusPill
import com.infinityconnect.vpn.ui.theme.EyebrowStyle
import com.infinityconnect.vpn.ui.theme.InfinityColors
import com.infinityconnect.vpn.ui.theme.LocalInfinityGradients
import com.infinityconnect.vpn.domain.model.KeyStatus
import com.infinityconnect.vpn.domain.model.status
import com.infinityconnect.vpn.ui.util.errorText
import com.infinityconnect.vpn.ui.util.formatBytes
import com.infinityconnect.vpn.ui.util.formatDuration
import com.infinityconnect.vpn.ui.util.isExpired
import com.infinityconnect.vpn.vpn.TunnelState
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onOpenProfile: () -> Unit,
    onOpenRouting: () -> Unit,
    onOpenPing: () -> Unit,
    onOpenLogs: () -> Unit,
    onOpenLanguage: () -> Unit,
    onOpenAbout: () -> Unit,
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

    // Snackbar одноразовых уведомлений VM (например, «лимит устройств»).
    val snackbarHostState = remember { androidx.compose.material3.SnackbarHostState() }
    // Текст резолвим в композиции: VM хранит ID строки, поэтому уведомление
    // всегда приходит на текущем языке интерфейса.
    val noticeText = errorText(ui.notice)
    androidx.compose.runtime.LaunchedEffect(noticeText) {
        noticeText?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.noticeShown()
        }
    }
    val errorMessage = errorText(ui.error)

    // Диалог о новой версии. Именно диалог, а не snackbar: обновление легко
    // пропустить, а установка APK — осознанное действие пользователя.
    ui.updatePrompt?.let { update ->
        UpdateAvailableDialog(
            update = update,
            onUpdate = { skip ->
                viewModel.dismissUpdatePrompt(skip)
                onOpenAbout()
            },
            onDismiss = { skip -> viewModel.dismissUpdatePrompt(skip) },
        )
    }

    // Боковое меню в стиле сайдбара Windows-клиента.
    val drawerState = androidx.compose.material3.rememberDrawerState(
        androidx.compose.material3.DrawerValue.Closed,
    )
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    // Навигируем сразу, не дожидаясь анимации закрытия: drawerState.close() —
    // suspend, и при двойном тапе вторая корутина отменяла первую, из-за чего
    // переход либо терялся, либо уходил дважды. Дубли отсекает navigateOnce на
    // уровне навграфа (проверка RESUMED), закрытие меню — просто косметика.
    fun closeThen(action: () -> Unit) {
        action()
        scope.launch { runCatching { drawerState.close() } }
    }
    androidx.compose.material3.ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            AppDrawer(
                items = listOf(
                    DrawerItem("⚡", stringResource(R.string.home_drawer_connection)) { closeThen {} },
                    DrawerItem("🧭", stringResource(R.string.home_drawer_routing)) { closeThen(onOpenRouting) },
                    DrawerItem("📶", stringResource(R.string.home_drawer_ping)) { closeThen(onOpenPing) },
                    DrawerItem("📝", stringResource(R.string.home_drawer_logs)) { closeThen(onOpenLogs) },
                    DrawerItem("🌐", stringResource(R.string.home_drawer_language)) { closeThen(onOpenLanguage) },
                    DrawerItem("👤", stringResource(R.string.home_drawer_profile)) { closeThen(onOpenProfile) },
                    DrawerItem("ℹ", stringResource(R.string.home_drawer_about)) { closeThen(onOpenAbout) },
                ),
                // HOME — единственный экран с меню, поэтому активен всегда пункт 0:
                // остальные пункты открывают экраны поверх, со своим топбаром.
                activeIndex = 0,
                connected = tunnel is TunnelState.Connected,
            )
        },
    ) {
    Scaffold(
        containerColor = Color.Transparent,
        snackbarHost = { androidx.compose.material3.SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(R.string.home_title),
                        fontWeight = FontWeight.Bold,
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
                navigationIcon = {
                    IconButton(onClick = { scope.launch { drawerState.open() } }) {
                        Icon(Icons.Filled.Menu, contentDescription = stringResource(R.string.home_menu))
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.refresh() }) {
                        Icon(
                            Icons.Filled.Refresh,
                            contentDescription = stringResource(R.string.home_refresh_subscriptions),
                        )
                    }
                },
            )
        },
    ) { padding ->
        when {
            ui.loadingFirstTime && ui.keys.isEmpty() ->
                FullScreenLoading(Modifier.padding(padding))

            ui.keys.isEmpty() && errorMessage != null ->
                FullScreenMessage(
                    title = stringResource(R.string.home_load_failed),
                    description = errorMessage,
                    actionLabel = stringResource(R.string.common_retry),
                    onAction = { viewModel.refresh() },
                    modifier = Modifier.padding(padding),
                )

            ui.keys.isEmpty() ->
                FullScreenMessage(
                    title = stringResource(R.string.home_no_keys_title),
                    description = stringResource(R.string.home_no_keys_description),
                    actionLabel = stringResource(R.string.common_refresh),
                    onAction = { viewModel.refresh() },
                    modifier = Modifier.padding(padding),
                )

            else -> HomeContent(
                ui = ui,
                tunnel = tunnel,
                stats = stats,
                onRefresh = { viewModel.refresh() },
                onPingAll = { viewModel.pingAllSelected() },
                onSelectServer = viewModel::selectServer,
                onSelectKey = viewModel::selectKey,
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
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HomeContent(
    ui: HomeUiState,
    tunnel: TunnelState,
    stats: com.infinityconnect.vpn.vpn.TunnelStats,
    onRefresh: () -> Unit,
    onPingAll: () -> Unit,
    onSelectServer: (Long, com.infinityconnect.vpn.domain.model.SubscriptionServer) -> Unit,
    onSelectKey: (Long) -> Unit,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // В состоянии «не подключено» hero компактнее — список серверов сразу виден.
    val compactHero = tunnel is TunnelState.Disconnected || tunnel is TunnelState.Error
    // Выбранный сервер — для подписи под кнопкой.
    val selectedServer = ui.serversByKey[ui.selectedKeyId]
        ?.firstOrNull { it.index == ui.selectedServerIndex }

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
                    compact = compactHero,
                    selectedServer = selectedServer,
                    pingMethod = ui.pingMethod,
                    onToggle = onToggle,
                )
                Spacer(Modifier.height(8.dp))
                // Кнопка пинга — в самом верху, пингует все серверы всех подписок.
                PingAllBar(
                    pinging = ui.pinging,
                    onPingAll = onPingAll,
                )
                Spacer(Modifier.height(4.dp))
            }
            // Если ни одна подписка не активна (все истекли/отключены/лимит) —
            // предупреждающий баннер над списком.
            val allInactive = ui.keys.isNotEmpty() &&
                ui.keys.none { it.status(isExpired(it.expiresAt)) == KeyStatus.ACTIVE }
            if (allInactive) {
                item(key = "no-active-banner") {
                    NoActiveSubscriptionsBanner()
                }
            }
            // Каждая подписка — единый блок: карточка-ключ + её серверы под ней
            // в общем контейнере, чтобы группировка «ключ → серверы» читалась.
            ui.keys.forEachIndexed { index, key ->
                item(key = "group-${key.id}") {
                    val servers = ui.serversByKey[key.id].orEmpty()
                    KeyGroup(
                        key = key,
                        number = index + 1,
                        servers = servers,
                        loading = servers.isEmpty() && key.id in ui.loadingKeys,
                        selectedKeyId = ui.selectedKeyId,
                        selectedServerIndex = ui.selectedServerIndex,
                        pingMethod = ui.pingMethod,
                        onSelectServer = onSelectServer,
                        onSelectKey = onSelectKey,
                    )
                }
            }
            item { Spacer(Modifier.height(16.dp)) }
        }
    }
}

/**
 * Группа одной подписки: карточка-ключ и её серверы в общем контейнере.
 * Серверы раскрыты только у ВЫБРАННОЙ подписки; остальные свёрнуты со сводкой
 * (лучший пинг, число серверов) — тап по ключу разворачивает группу. У сервера
 * с наименьшим пингом в группе — бейдж «⚡ Быстрейший».
 */
@Composable
private fun KeyGroup(
    key: com.infinityconnect.vpn.domain.model.VpnKey,
    number: Int,
    servers: List<com.infinityconnect.vpn.domain.model.SubscriptionServer>,
    loading: Boolean,
    selectedKeyId: Long?,
    selectedServerIndex: Int,
    pingMethod: com.infinityconnect.vpn.domain.model.PingMethod,
    onSelectServer: (Long, com.infinityconnect.vpn.domain.model.SubscriptionServer) -> Unit,
    onSelectKey: (Long) -> Unit,
) {
    val isSelectedKey = key.id == selectedKeyId
    // Индекс сервера с наименьшим пингом (валидным) — для бейджа «Быстрейший».
    val fastestIndex = servers
        .filter { (it.pingMs ?: -1) >= 0 }
        .minByOrNull { it.pingMs ?: Int.MAX_VALUE }
        ?.index
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(androidx.compose.foundation.shape.RoundedCornerShape(22.dp))
            .background(InfinityColors.SpaceElevated)
            .border(
                width = 1.dp,
                color = if (isSelectedKey) InfinityColors.AccentBlue.copy(alpha = 0.4f)
                else InfinityColors.Stroke,
                shape = androidx.compose.foundation.shape.RoundedCornerShape(22.dp),
            )
            // Плавное раскрытие/сворачивание группы при смене выбранного ключа.
            .animateContentSize(
                animationSpec = androidx.compose.animation.core.tween(250),
            )
            .padding(start = 6.dp, top = 6.dp, end = 6.dp, bottom = if (isSelectedKey) 12.dp else 6.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // Подсветку выделения несёт контейнер группы, поэтому саму карточку-ключ
        // не подсвечиваем — иначе двойная рамка. Тап по свёрнутому ключу —
        // выбирает подписку и разворачивает её серверы.
        KeyCard(
            key = key,
            number = number,
            selected = false,
            onClick = { if (!isSelectedKey) onSelectKey(key.id) },
        )
        if (isSelectedKey) {
            if (loading) {
                ServersLoadingRow()
            }
            servers.forEach { server ->
                ServerRow(
                    server = server,
                    selected = server.index == selectedServerIndex,
                    pingMethod = pingMethod,
                    isFastest = server.index == fastestIndex,
                    blocked = key.status(isExpired(key.expiresAt)) != KeyStatus.ACTIVE,
                    onClick = { onSelectServer(key.id, server) },
                )
            }
        } else {
            // Свёрнутая подписка — компактная сводка.
            CollapsedSummary(
                serverCount = servers.size,
                bestPing = servers.mapNotNull { it.pingMs?.takeIf { p -> p >= 0 } }.minOrNull(),
                pingMethod = pingMethod,
                loading = loading,
            )
        }
    }
}

/** Сводка свёрнутой подписки: число серверов и лучший пинг. */
@Composable
private fun CollapsedSummary(
    serverCount: Int,
    bestPing: Int?,
    pingMethod: com.infinityconnect.vpn.domain.model.PingMethod,
    loading: Boolean,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = when {
                loading -> stringResource(R.string.home_servers_loading)
                serverCount == 0 -> stringResource(R.string.home_no_servers)
                else -> stringResource(
                    R.string.home_collapsed_summary,
                    pluralStringResource(R.plurals.home_server_count, serverCount, serverCount),
                )
            },
            style = MaterialTheme.typography.bodySmall,
            color = InfinityColors.Muted,
        )
        bestPing?.let {
            StatusPill(text = stringResource(R.string.home_ping_best_ms, it), color = com.infinityconnect.vpn.ui.settings.pingColor(pingMethod, it))
        }
    }
}

/**
 * Баннер над списком: все подписки неактивны (истекли/отключены/лимит).
 * Подсказывает продлить/оформить подписку на сайте.
 */
@Composable
private fun NoActiveSubscriptionsBanner() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(androidx.compose.foundation.shape.RoundedCornerShape(16.dp))
            .background(InfinityColors.Coral.copy(alpha = 0.10f))
            .border(
                1.dp,
                InfinityColors.Coral.copy(alpha = 0.4f),
                androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
            )
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(
            Icons.Filled.Warning,
            contentDescription = null,
            tint = InfinityColors.Coral,
            modifier = Modifier.size(22.dp),
        )
        Column {
            Text(
                text = stringResource(R.string.home_no_active_title),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                color = InfinityColors.OnSurface,
            )
            Text(
                text = stringResource(R.string.home_no_active_description),
                style = MaterialTheme.typography.bodySmall,
                color = InfinityColors.Muted,
            )
        }
    }
}

/** Hero-блок: статус-пилл, круглая кнопка со свечением, статистика. */
@Composable
private fun ConnectionHeader(
    tunnel: TunnelState,
    stats: com.infinityconnect.vpn.vpn.TunnelStats,
    canConnect: Boolean,
    compact: Boolean,
    selectedServer: com.infinityconnect.vpn.domain.model.SubscriptionServer?,
    pingMethod: com.infinityconnect.vpn.domain.model.PingMethod,
    onToggle: () -> Unit,
) {
    val isActive = tunnel is TunnelState.Connected || tunnel is TunnelState.Connecting
    val gap = if (compact) 14.dp else 20.dp
    Column(
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        StatusPill(text = statusTitle(tunnel), color = statusColor(tunnel))
        Spacer(Modifier.height(gap))
        ConnectHero(
            tunnel = tunnel,
            enabled = canConnect || isActive,
            onToggle = onToggle,
            compact = compact,
        )
        Spacer(Modifier.height(gap))
        if (tunnel is TunnelState.Connected) {
            StatsRow(stats)
        } else if (canConnect) {
            // Показываем целевой сервер, чтобы было понятно, куда подключимся.
            SelectedServerHint(server = selectedServer, pingMethod = pingMethod)
        } else {
            Text(
                text = stringResource(R.string.home_pick_server),
                style = MaterialTheme.typography.bodyMedium,
                color = InfinityColors.Muted,
            )
        }
    }
}

/** Подпись под кнопкой: выбранный сервер (флаг, имя) и его пинг. */
@Composable
private fun SelectedServerHint(
    server: com.infinityconnect.vpn.domain.model.SubscriptionServer?,
    pingMethod: com.infinityconnect.vpn.domain.model.PingMethod,
) {
    if (server == null) {
        Text(
            text = stringResource(R.string.home_tap_to_connect),
            style = MaterialTheme.typography.bodyMedium,
            color = InfinityColors.Muted,
        )
        return
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = "${server.flag ?: stringResource(R.string.home_globe_fallback)}  ${server.name}",
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.SemiBold,
            color = InfinityColors.OnSurface,
        )
        server.pingMs?.takeIf { it >= 0 }?.let {
            StatusPill(text = stringResource(R.string.home_ping_ms, it), color = com.infinityconnect.vpn.ui.settings.pingColor(pingMethod, it))
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
        // Объём за сессию, а не скорость: плитки подписаны «Скачано»/«Отдано».
        // Скорость в подписи — мгновенная дельта, она часто нулевая (ядра
        // обновляют счётчики реже, чем раз в секунду) и выглядела как «трафика нет».
        val context = androidx.compose.ui.platform.LocalContext.current
        StatTile(
            stringResource(R.string.home_stat_downloaded),
            context.formatBytes(stats.totalDownloadBytes),
            Modifier.weight(1f),
        )
        StatTile(
            stringResource(R.string.home_stat_uploaded),
            context.formatBytes(stats.totalUploadBytes),
            Modifier.weight(1f),
        )
        StatTile(
            stringResource(R.string.home_stat_time),
            formatDuration(stats.sessionSeconds),
            Modifier.weight(1f),
        )
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

/**
 * Верхняя панель со списком «МОИ ПОДПИСКИ» и кнопкой «Пинг всех» — пингует
 * серверы всех подписок сразу.
 */
@Composable
private fun PingAllBar(pinging: Boolean, onPingAll: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(R.string.home_my_subscriptions),
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
                Text(stringResource(R.string.home_ping_running))
            } else {
                Icon(
                    Icons.Filled.NetworkPing,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.home_ping_all))
            }
        }
    }
}

@Composable
private fun statusTitle(tunnel: TunnelState): String = stringResource(
    when (tunnel) {
        TunnelState.Connected -> R.string.home_status_connected
        TunnelState.Connecting -> R.string.home_status_connecting
        TunnelState.Disconnecting -> R.string.home_status_disconnecting
        TunnelState.Disconnected -> R.string.home_status_disconnected
        is TunnelState.Error -> R.string.home_status_error
    },
)

private fun statusColor(tunnel: TunnelState): Color = when (tunnel) {
    TunnelState.Connected -> InfinityColors.Mint
    is TunnelState.Error -> InfinityColors.Coral
    TunnelState.Connecting, TunnelState.Disconnecting -> InfinityColors.AccentCyan
    else -> InfinityColors.Muted
}
