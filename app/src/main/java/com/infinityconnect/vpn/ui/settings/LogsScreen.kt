package com.infinityconnect.vpn.ui.settings

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.infinityconnect.vpn.R
import com.infinityconnect.vpn.data.local.LogEntry
import com.infinityconnect.vpn.data.local.LogLevel
import com.infinityconnect.vpn.ui.theme.InfinityColors

/**
 * Экран журнала: события приложения, VPN-сервиса и обоих ядер.
 *
 * Записи переживают перезапуск и краш (см. `LogStore`), поэтому здесь видна и
 * прошлая сессия — именно в ней обычно и лежит причина падения. Журнал можно
 * отфильтровать по уровню и отправить файлом в поддержку.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogsScreen(
    onBack: () -> Unit,
    viewModel: LogsViewModel = hiltViewModel(),
) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val entries by viewModel.entries.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val listState = rememberLazyListState()
    val snackbarHostState = remember { SnackbarHostState() }

    // Автопрокрутка к свежим записям, пока пользователь не листает сам.
    LaunchedEffect(entries.size, ui.autoScroll) {
        if (ui.autoScroll && entries.isNotEmpty()) {
            listState.scrollToItem(entries.lastIndex)
        }
    }

    // Пользователь пролистал вверх — выключаем автопрокрутку, чтобы не дёргало.
    LaunchedEffect(listState) {
        snapshotFlowIsAtBottom(listState, entries.size).collect { atBottom ->
            viewModel.setAutoScroll(atBottom)
        }
    }

    // Текст снекбара резолвится здесь: VM хранит только ID строки, поэтому
    // сообщение всегда приходит на текущем языке интерфейса.
    val noticeText = ui.notice?.let { stringResource(it) }
    LaunchedEffect(noticeText) {
        noticeText?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.noticeShown()
        }
    }

    // Строки для интента резолвим в композиции: внутри LaunchedEffect
    // stringResource недоступен (не @Composable-контекст).
    val shareSubject = stringResource(R.string.logs_share_subject)
    val shareClipLabel = stringResource(R.string.logs_share_clip)
    val shareChooserTitle = stringResource(R.string.logs_share_chooser)

    // Файл готов — отдаём системному «Поделиться».
    LaunchedEffect(ui.exportFile) {
        val file = ui.exportFile ?: return@LaunchedEffect
        runCatching {
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file,
            )
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, shareSubject)
                // clipData обязателен: без него системный диалог не получает
                // доступ к URI — не показывает превью, а часть приложений
                // получает файл без прав на чтение.
                clipData = android.content.ClipData.newRawUri(shareClipLabel, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(intent, shareChooserTitle))
        }
        viewModel.shareHandled()
    }

    Scaffold(
        containerColor = Color.Transparent,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Text(stringResource(R.string.logs_title), fontWeight = FontWeight.Bold)
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.common_back),
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.share() }) {
                        Icon(
                            Icons.Filled.Share,
                            contentDescription = stringResource(R.string.logs_share),
                        )
                    }
                    IconButton(onClick = { viewModel.clear() }) {
                        Icon(
                            Icons.Filled.Delete,
                            contentDescription = stringResource(R.string.logs_clear),
                        )
                    }
                },
            )
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            FilterRow(
                selected = ui.filter,
                onSelect = viewModel::setFilter,
            )

            if (entries.isEmpty()) {
                EmptyState(filtered = ui.filter != LogFilter.ALL)
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    items(entries) { entry -> LogRow(entry) }
                }
            }
        }
    }
}

/** Полоса фильтров по уровню. */
@Composable
private fun FilterRow(
    selected: LogFilter,
    onSelect: (LogFilter) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        LogFilter.entries.forEach { filter ->
            val active = filter == selected
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (active) InfinityColors.SurfaceHi else InfinityColors.Surface)
                    .border(
                        1.dp,
                        if (active) InfinityColors.AccentBlue else InfinityColors.Stroke,
                        RoundedCornerShape(10.dp),
                    )
                    .clickable { onSelect(filter) }
                    .padding(horizontal = 14.dp, vertical = 8.dp),
            ) {
                Text(
                    text = stringResource(filter.labelRes),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (active) InfinityColors.OnSurface else InfinityColors.Muted,
                )
            }
        }
    }
}

/** Одна строка журнала: время, уровень, источник, текст. */
@Composable
private fun LogRow(entry: LogEntry) {
    val levelColor = when (entry.level) {
        LogLevel.DEBUG -> InfinityColors.MutedDim
        LogLevel.INFO -> InfinityColors.Mint
        LogLevel.WARN -> InfinityColors.Amber
        LogLevel.ERROR -> InfinityColors.Coral
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(InfinityColors.Surface)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.Top,
    ) {
        // Цветная метка уровня — глазом видно ошибки в потоке строк.
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(4.dp))
                .background(levelColor)
                .padding(horizontal = 5.dp, vertical = 1.dp),
        ) {
            Text(
                text = entry.level.name.first().toString(),
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = InfinityColors.Space,
            )
        }
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = entry.timeOnly(),
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    color = InfinityColors.MutedDim,
                )
                Text(
                    text = entry.tag,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = InfinityColors.Muted,
                )
            }
            Text(
                text = entry.message,
                fontSize = 12.sp,
                fontFamily = FontFamily.Monospace,
                color = InfinityColors.OnSurface,
            )
        }
    }
}

@Composable
private fun EmptyState(filtered: Boolean) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = stringResource(
                if (filtered) R.string.logs_empty_filtered else R.string.logs_empty,
            ),
            style = MaterialTheme.typography.bodyMedium,
            color = InfinityColors.Muted,
            modifier = Modifier.padding(32.dp),
        )
    }
}

/** Время без даты — дата и так видна по порядку записей. */
private fun LogEntry.timeOnly(): String =
    format().substring(11, 23)

/**
 * Поток «список пролистан до конца»: пока пользователь у последней записи,
 * автопрокрутка продолжается, стоит отлистать вверх — отключается.
 */
private fun snapshotFlowIsAtBottom(
    listState: androidx.compose.foundation.lazy.LazyListState,
    total: Int,
) = androidx.compose.runtime.snapshotFlow {
    val last = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
    total == 0 || last >= total - 2
}
