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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.infinityconnect.vpn.domain.model.PingMethod
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
                Text("ПИНГ", style = EyebrowStyle, color = InfinityColors.Muted)
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
            Spacer(Modifier.height(4.dp))
            Text(
                text = "Тест-URL для методов через прокси",
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
