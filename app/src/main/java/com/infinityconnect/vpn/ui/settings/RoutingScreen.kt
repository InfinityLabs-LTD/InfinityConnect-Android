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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.infinityconnect.vpn.R
import com.infinityconnect.vpn.domain.model.AppRoutingMode
import com.infinityconnect.vpn.domain.model.SitePreset
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

    SettingsScaffold(title = stringResource(R.string.routing_title), onBack = onBack) {
        AppRoutingSection(ui, viewModel, onOpenAppPicker)
        SitePresetsSection(ui, viewModel)
        SiteRoutingSection(ui, viewModel)
        Spacer(Modifier.height(8.dp))
    }
}

// ── По приложениям ──

@Composable
private fun AppRoutingSection(ui: SettingsUiState, vm: SettingsViewModel, onOpenAppPicker: () -> Unit) {
    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.fillMaxWidth()) {
            SectionTitle(stringResource(R.string.routing_section_apps))
            OptionRow(
                title = stringResource(R.string.routing_apps_off),
                subtitle = stringResource(R.string.routing_apps_off_desc),
                selected = ui.appMode == AppRoutingMode.OFF,
                onSelect = { vm.selectAppMode(AppRoutingMode.OFF) },
            )
            OptionRow(
                title = stringResource(R.string.routing_apps_allow),
                subtitle = stringResource(R.string.routing_apps_allow_desc),
                selected = ui.appMode == AppRoutingMode.ALLOW,
                onSelect = { vm.selectAppMode(AppRoutingMode.ALLOW) },
            )
            OptionRow(
                title = stringResource(R.string.routing_apps_disallow),
                subtitle = stringResource(R.string.routing_apps_disallow_desc),
                selected = ui.appMode == AppRoutingMode.DISALLOW,
                onSelect = { vm.selectAppMode(AppRoutingMode.DISALLOW) },
            )
            if (ui.appMode != AppRoutingMode.OFF) {
                Spacer(Modifier.height(12.dp))
                GradientButton(
                    text = stringResource(R.string.routing_apps_choose, ui.selectedApps.size),
                    onClick = onOpenAppPicker,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

// ── Пресеты сайтов ──

/**
 * Готовые наборы сайтов (multi-select): можно включить несколько пресетов
 * одновременно, у каждого своё направление (в обход VPN / через VPN).
 */
@Composable
private fun SitePresetsSection(ui: SettingsUiState, vm: SettingsViewModel) {
    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.fillMaxWidth()) {
            SectionTitle(stringResource(R.string.routing_section_presets))
            Text(
                text = stringResource(R.string.routing_presets_hint),
                style = MaterialTheme.typography.bodySmall,
                color = InfinityColors.Muted,
                modifier = Modifier.padding(bottom = 10.dp),
            )
            SitePreset.entries.forEach { preset ->
                CheckRow(
                    title = stringResource(preset.titleRes()),
                    subtitle = stringResource(preset.subtitleRes()),
                    checked = preset in ui.sitePresets,
                    onToggle = { vm.toggleSitePreset(preset) },
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
            SectionTitle(stringResource(R.string.routing_section_sites))
            Text(
                text = stringResource(R.string.routing_sites_hint),
                style = MaterialTheme.typography.bodySmall,
                color = InfinityColors.Muted,
                modifier = Modifier.padding(bottom = 10.dp),
            )
            OptionRow(
                title = stringResource(R.string.routing_sites_off),
                subtitle = stringResource(R.string.routing_sites_off_desc),
                selected = ui.siteMode == SiteRoutingMode.OFF,
                onSelect = { vm.selectSiteMode(SiteRoutingMode.OFF) },
            )
            OptionRow(
                title = stringResource(R.string.routing_sites_proxy),
                subtitle = stringResource(R.string.routing_sites_proxy_desc),
                selected = ui.siteMode == SiteRoutingMode.PROXY,
                onSelect = { vm.selectSiteMode(SiteRoutingMode.PROXY) },
            )
            OptionRow(
                title = stringResource(R.string.routing_sites_direct),
                subtitle = stringResource(R.string.routing_sites_direct_desc),
                selected = ui.siteMode == SiteRoutingMode.DIRECT,
                onSelect = { vm.selectSiteMode(SiteRoutingMode.DIRECT) },
            )
            if (ui.siteMode != SiteRoutingMode.OFF) {
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = ui.sitesText,
                    onValueChange = vm::onSitesChange,
                    label = { Text(stringResource(R.string.routing_sites_label)) },
                    placeholder = { Text(stringResource(R.string.routing_sites_placeholder)) },
                    colors = fieldColors(),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(12.dp))
                GradientButton(
                    text = stringResource(R.string.routing_sites_save),
                    onClick = vm::saveSites,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}
