package com.infinityconnect.vpn.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.infinityconnect.vpn.domain.model.AppRoutingMode
import com.infinityconnect.vpn.domain.model.SiteRoutingMode
import com.infinityconnect.vpn.ui.components.GlassCard
import com.infinityconnect.vpn.ui.components.GradientButton
import com.infinityconnect.vpn.ui.theme.InfinityColors

/**
 * Экран маршрутизации: split-tunnel по приложениям и правила по доменам.
 * Общий режим трафика — весь трафик через VPN (кроме приватных сетей); выбор
 * общего режима и загрузка внешнего конфига правил убраны из UI.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RoutingScreen(
    viewModel: SettingsViewModel,
    onBack: () -> Unit,
    onOpenAppPicker: () -> Unit,
) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()

    SettingsScaffold(title = "Маршрутизация", onBack = onBack) {
        AppRoutingSection(ui, viewModel, onOpenAppPicker)
        SiteRoutingSection(ui, viewModel)
        Spacer(Modifier.height(8.dp))
    }
}

// ── По приложениям ──

@Composable
private fun AppRoutingSection(ui: SettingsUiState, vm: SettingsViewModel, onOpenAppPicker: () -> Unit) {
    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.fillMaxWidth()) {
            SectionTitle("ПО ПРИЛОЖЕНИЯМ")
            OptionRow(
                title = "Выключено",
                subtitle = "Весь трафик приложений — по общим правилам",
                selected = ui.appMode == AppRoutingMode.OFF,
                onSelect = { vm.selectAppMode(AppRoutingMode.OFF) },
            )
            OptionRow(
                title = "Только выбранные — через VPN",
                subtitle = "Остальные приложения идут напрямую",
                selected = ui.appMode == AppRoutingMode.ALLOW,
                onSelect = { vm.selectAppMode(AppRoutingMode.ALLOW) },
            )
            OptionRow(
                title = "Все, кроме выбранных",
                subtitle = "Выбранные приложения идут напрямую (в обход VPN)",
                selected = ui.appMode == AppRoutingMode.DISALLOW,
                onSelect = { vm.selectAppMode(AppRoutingMode.DISALLOW) },
            )
            if (ui.appMode != AppRoutingMode.OFF) {
                Spacer(Modifier.height(12.dp))
                GradientButton(
                    text = "Выбрать приложения (${ui.selectedApps.size})",
                    onClick = onOpenAppPicker,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

// ── По сайтам ──

@Composable
private fun SiteRoutingSection(ui: SettingsUiState, vm: SettingsViewModel) {
    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.fillMaxWidth()) {
            SectionTitle("ПО САЙТАМ")
            Text(
                text = "Доменные правила применяются на VLESS-серверах. " +
                    "На серверах Hysteria2 список сайтов не действует.",
                style = MaterialTheme.typography.bodySmall,
                color = InfinityColors.Muted,
                modifier = Modifier.padding(bottom = 10.dp),
            )
            OptionRow(
                title = "Выключено",
                subtitle = "Список доменов не используется",
                selected = ui.siteMode == SiteRoutingMode.OFF,
                onSelect = { vm.selectSiteMode(SiteRoutingMode.OFF) },
            )
            OptionRow(
                title = "Домены — через VPN",
                subtitle = "Указанные сайты идут через VPN, остальное — по общему режиму",
                selected = ui.siteMode == SiteRoutingMode.PROXY,
                onSelect = { vm.selectSiteMode(SiteRoutingMode.PROXY) },
            )
            OptionRow(
                title = "Домены — напрямую",
                subtitle = "Указанные сайты идут в обход VPN",
                selected = ui.siteMode == SiteRoutingMode.DIRECT,
                onSelect = { vm.selectSiteMode(SiteRoutingMode.DIRECT) },
            )
            if (ui.siteMode != SiteRoutingMode.OFF) {
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = ui.sitesText,
                    onValueChange = vm::onSitesChange,
                    label = { Text("Домены (по одному на строку)") },
                    placeholder = { Text("youtube.com\nnetflix.com") },
                    colors = fieldColors(),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(12.dp))
                GradientButton(
                    text = "Сохранить список",
                    onClick = vm::saveSites,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}
