package com.infinityconnect.vpn.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
import com.infinityconnect.vpn.BuildConfig
import com.infinityconnect.vpn.BuildFlags
import com.infinityconnect.vpn.domain.model.PingMethod
import com.infinityconnect.vpn.domain.model.PingMode
import com.infinityconnect.vpn.domain.model.PingSettings
import com.infinityconnect.vpn.domain.model.RoutingMode
import com.infinityconnect.vpn.ui.components.GlassCard
import com.infinityconnect.vpn.ui.components.GradientButton
import com.infinityconnect.vpn.ui.components.StatusPill
import com.infinityconnect.vpn.ui.theme.EyebrowStyle
import com.infinityconnect.vpn.ui.theme.InfinityColors
import com.infinityconnect.vpn.ui.theme.LocalInfinityGradients
import com.infinityconnect.vpn.ui.util.formatDateTime

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val gradients = LocalInfinityGradients.current

    Scaffold(
        containerColor = Color.Transparent,
        modifier = Modifier.background(gradients.screen),
        topBar = {
            TopAppBar(
                title = { Text("Настройки", fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            RoutingSection(ui, viewModel)
            RulesSection(ui, viewModel)
            PingSection(ui, viewModel)
            AboutSection()
            Spacer(Modifier.height(8.dp))
        }
    }
}

// ── Маршрутизация ──

@Composable
private fun RoutingSection(ui: SettingsUiState, vm: SettingsViewModel) {
    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.fillMaxWidth()) {
            SectionTitle("МАРШРУТИЗАЦИЯ")
            OptionRow(
                title = "Весь трафик через VPN",
                subtitle = "Кроме приватных сетей",
                selected = ui.mode == RoutingMode.ALL,
                onSelect = { vm.selectMode(RoutingMode.ALL) },
            )
            OptionRow(
                title = "Обход российских сайтов",
                subtitle = "Рос. сервисы — напрямую, остальное через VPN",
                selected = ui.mode == RoutingMode.BYPASS_RU,
                onSelect = { vm.selectMode(RoutingMode.BYPASS_RU) },
            )
            OptionRow(
                title = "По своим правилам",
                subtitle = if (ui.hasRules) "Внешний конфиг правил загружен" else "Загрузите конфиг правил ниже",
                selected = ui.mode == RoutingMode.CUSTOM,
                enabled = ui.hasRules,
                onSelect = { vm.selectMode(RoutingMode.CUSTOM) },
            )
        }
    }
}

@Composable
private fun RulesSection(ui: SettingsUiState, vm: SettingsViewModel) {
    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.fillMaxWidth()) {
            SectionTitle("КОНФИГ ПРАВИЛ")
            Text(
                text = "Ссылка на JSON с правилами маршрутизации (routing.rules).",
                style = MaterialTheme.typography.bodySmall,
                color = InfinityColors.Muted,
                modifier = Modifier.padding(bottom = 10.dp),
            )
            OutlinedTextField(
                value = ui.rulesUrl,
                onValueChange = vm::onRulesUrlChange,
                label = { Text("URL правил") },
                singleLine = true,
                enabled = !ui.downloading,
                colors = fieldColors(),
                modifier = Modifier.fillMaxWidth(),
            )
            ui.rulesUpdatedAt?.let {
                Text(
                    text = "Обновлено: ${formatDateTime(it)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = InfinityColors.Muted,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
            ui.rulesError?.let { Hint(it, InfinityColors.Coral) }
            ui.rulesMessage?.let { Hint(it, InfinityColors.Mint) }
            Spacer(Modifier.height(12.dp))
            GradientButton(
                text = "Загрузить правила",
                onClick = vm::downloadRules,
                enabled = ui.rulesUrl.isNotBlank(),
                loading = ui.downloading,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

// ── Пинг ──

@Composable
private fun PingSection(ui: SettingsUiState, vm: SettingsViewModel) {
    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("ПРОТОКОЛЫ ПИНГА", style = EyebrowStyle, color = InfinityColors.Muted)
                // Демонстрация цвета текущего метода на примере значения.
                StatusPill(text = "132 мс", color = pingColor(ui.pingMethod, 132))
            }
            PingMethod.entries.forEach { method ->
                OptionRow(
                    title = method.title(),
                    subtitle = method.description(),
                    selected = ui.pingMethod == method,
                    accent = method.baseColor(),
                    onSelect = { vm.selectPingMethod(method) },
                )
            }

            // Режим и таймаут применяются только к прокси-методам (GET/HEAD).
            val proxyActive = ui.pingMethod.isProxy
            if (proxyActive) {
                Spacer(Modifier.height(12.dp))
                PingModeRow(ui.pingMode, vm::selectPingMode)
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "default — берётся лучший из нескольких запросов.\n" +
                        "double — запрос выполняется дважды в рамках одной сессии ядра; замеряется второй.\n" +
                        "keepalive — второй запрос замеряет чистую скорость ответа через уже установленное TLS-соединение.",
                    style = MaterialTheme.typography.bodySmall,
                    color = InfinityColors.Muted,
                )
                Spacer(Modifier.height(16.dp))
                PingTimeoutSlider(ui.pingTimeoutSec, vm::onPingTimeoutChange, vm::savePingTimeout)
            }

            Spacer(Modifier.height(16.dp))
            Text(
                text = "НАСТРОЙКИ URL-ТЕСТА",
                style = EyebrowStyle,
                color = InfinityColors.Muted,
                modifier = Modifier.padding(bottom = 8.dp),
            )
            Text(
                text = "URL для прокси-методов (GET/HEAD).",
                style = MaterialTheme.typography.bodySmall,
                color = InfinityColors.Muted,
                modifier = Modifier.padding(bottom = 8.dp),
            )
            OutlinedTextField(
                value = ui.pingUrl,
                onValueChange = vm::onPingUrlChange,
                label = { Text("URL теста") },
                singleLine = true,
                colors = fieldColors(),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(12.dp))
            GradientButton(
                text = "Сохранить URL",
                onClick = vm::savePingUrl,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/** Строка «Режим (via …)» с выпадающим списком Default/Double/Keepalive. */
@Composable
private fun PingModeRow(mode: PingMode, onSelect: (PingMode) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "Режим (via)",
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.SemiBold,
            color = InfinityColors.OnSurface,
        )
        OutlinedButton(onClick = { expanded = true }) {
            Text(mode.label(), color = InfinityColors.OnSurface)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            PingMode.entries.forEach { m ->
                DropdownMenuItem(
                    text = { Text(m.label()) },
                    onClick = {
                        expanded = false
                        onSelect(m)
                    },
                )
            }
        }
    }
}

/** Слайдер таймаута прокси-пинга (5..15 с). Пишет в хранилище по отпусканию. */
@Composable
private fun PingTimeoutSlider(sec: Int, onChange: (Int) -> Unit, onCommit: () -> Unit) {
    Text(
        text = "ТАЙМАУТ ПРОКСИ-ПИНГА",
        style = EyebrowStyle,
        color = InfinityColors.Muted,
        modifier = Modifier.padding(bottom = 4.dp),
    )
    Slider(
        value = sec.toFloat(),
        onValueChange = { onChange(it.toInt()) },
        onValueChangeFinished = onCommit,
        valueRange = PingSettings.MIN_TIMEOUT_SEC.toFloat()..PingSettings.MAX_TIMEOUT_SEC.toFloat(),
        steps = PingSettings.MAX_TIMEOUT_SEC - PingSettings.MIN_TIMEOUT_SEC - 1,
        colors = SliderDefaults.colors(
            thumbColor = InfinityColors.AccentBlue,
            activeTrackColor = InfinityColors.AccentBlue,
        ),
    )
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text("${PingSettings.MIN_TIMEOUT_SEC}", style = MaterialTheme.typography.bodySmall, color = InfinityColors.Muted)
        Text("${sec}s", style = MaterialTheme.typography.bodySmall, color = InfinityColors.OnSurface, fontWeight = FontWeight.SemiBold)
        Text("${PingSettings.MAX_TIMEOUT_SEC}", style = MaterialTheme.typography.bodySmall, color = InfinityColors.Muted)
    }
}

/** Отображаемое название режима прокси-пинга. */
private fun PingMode.label(): String = when (this) {
    PingMode.DEFAULT -> "Default"
    PingMode.DOUBLE -> "Double"
    PingMode.KEEPALIVE -> "Keepalive"
}

// ── О приложении ──

@Composable
private fun AboutSection() {
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
}

/** Строка «ключ — значение» для раздела «О приложении». */
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

// ── Общие элементы ──

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = EyebrowStyle,
        color = InfinityColors.Muted,
        modifier = Modifier.padding(bottom = 8.dp),
    )
}

@Composable
private fun Hint(text: String, color: Color) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = color,
        modifier = Modifier.padding(top = 8.dp),
    )
}

@Composable
private fun OptionRow(
    title: String,
    subtitle: String,
    selected: Boolean,
    onSelect: () -> Unit,
    enabled: Boolean = true,
    accent: Color = InfinityColors.AccentBlue,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = selected, enabled = enabled, onClick = onSelect)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        RadioButton(
            selected = selected,
            onClick = onSelect,
            enabled = enabled,
            colors = RadioButtonDefaults.colors(
                selectedColor = accent,
                unselectedColor = InfinityColors.Muted,
                disabledUnselectedColor = InfinityColors.MutedDim,
            ),
        )
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                color = if (enabled) InfinityColors.OnSurface else InfinityColors.MutedDim,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = InfinityColors.Muted,
            )
        }
    }
}

@Composable
private fun fieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = InfinityColors.AccentBlue,
    unfocusedBorderColor = InfinityColors.Stroke,
    focusedLabelColor = InfinityColors.AccentBlue,
    unfocusedLabelColor = InfinityColors.Muted,
    cursorColor = InfinityColors.AccentBlue,
    focusedTextColor = InfinityColors.OnSurface,
    unfocusedTextColor = InfinityColors.OnSurface,
)
