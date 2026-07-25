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
import com.infinityconnect.vpn.domain.model.KeyStatus
import com.infinityconnect.vpn.domain.model.status
import com.infinityconnect.vpn.ui.util.formatDuration
import com.infinityconnect.vpn.ui.util.formatSpeed
import com.infinityconnect.vpn.ui.util.isExpired
import com.infinityconnect.vpn.vpn.TunnelState
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onOpenProfile: () -> Unit,
    onOpenRouting: () -> Unit,
    onOpenPing: () -> Unit,
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
    androidx.compose.runtime.LaunchedEffect(ui.notice) {
        ui.notice?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.noticeShown()
        }
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
                    DrawerItem("⚡", "Подключение") { closeThen {} },
                    DrawerItem("🧭", "Маршрутизация") { closeThen(onOpenRouting) },
                    DrawerItem("📶", "Пинг") { closeThen(onOpenPing) },
                    DrawerItem("👤", "Профиль") { closeThen(onOpenProfile) },
                    DrawerItem("ℹ", "О приложении") { closeThen(onOpenAbout) },
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
                        "Infinity Connect",
                        fontWeight = FontWeight.Bold,
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
                navigationIcon = {
                    IconButton(onClick = { scope.launch { drawerState.open() } }) {
                        Icon(Icons.Filled.Menu, contentDescription = "Меню")
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.refresh() }) {
                        Icon(Icons.Filled.Refresh, contentDescription = "Обновить подписки")
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
                loading -> "Загрузка серверов…"
                serverCount == 0 -> "Нет серверов"
                else -> "$serverCount ${plural(serverCount, "сервер", "сервера", "серверов")} · нажмите, чтобы выбрать"
            },
            style = MaterialTheme.typography.bodySmall,
            color = InfinityColors.Muted,
        )
        bestPing?.let {
            StatusPill(text = "⚡ $it мс", color = com.infinityconnect.vpn.ui.settings.pingColor(pingMethod, it))
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
                text = "Нет активных подписок",
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                color = InfinityColors.OnSurface,
            )
            Text(
                text = "Все ключи истекли, отключены или достигли лимита. " +
                    "Продлите подписку на сайте — доступ восстановится автоматически.",
                style = MaterialTheme.typography.bodySmall,
                color = InfinityColors.Muted,
            )
        }
    }
}

/** Множественное число (день/дня/дней). */
private fun plural(n: Int, one: String, few: String, many: String): String {
    val m10 = n % 10
    val m100 = n % 100
    return when {
        m10 == 1 && m100 != 11 -> one
        m10 in 2..4 && m100 !in 12..14 -> few
        else -> many
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
                text = "Выберите сервер",
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
            text = "Нажмите, чтобы подключиться",
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
            text = "${server.flag ?: "🌐"}  ${server.name}",
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.SemiBold,
            color = InfinityColors.OnSurface,
        )
        server.pingMs?.takeIf { it >= 0 }?.let {
            StatusPill(text = "$it мс", color = com.infinityconnect.vpn.ui.settings.pingColor(pingMethod, it))
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
            text = "МОИ ПОДПИСКИ",
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
