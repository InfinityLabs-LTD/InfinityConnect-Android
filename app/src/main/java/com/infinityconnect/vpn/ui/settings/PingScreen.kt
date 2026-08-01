package com.infinityconnect.vpn.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.infinityconnect.vpn.R
import com.infinityconnect.vpn.domain.model.PingMethod
import com.infinityconnect.vpn.domain.model.PingMode
import com.infinityconnect.vpn.domain.model.PingSettings
import com.infinityconnect.vpn.ui.components.GlassCard
import com.infinityconnect.vpn.ui.components.GradientButton
import com.infinityconnect.vpn.ui.components.StatusPill
import com.infinityconnect.vpn.ui.theme.EyebrowStyle
import com.infinityconnect.vpn.ui.theme.InfinityColors

/**
 * Значение примера рядом с заголовком «протоколы пинга»: пилл красится по
 * порогам текущего метода, поэтому число должно совпадать с тем, что показано
 * в строке `ping_sample_ms`.
 */
private const val SAMPLE_PING_MS = 132

/** Экран настроек пинга: протокол, режим (via), таймаут и URL-тест. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PingScreen(
    viewModel: SettingsViewModel,
    onBack: () -> Unit,
) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()

    SettingsScaffold(title = stringResource(R.string.ping_title), onBack = onBack) {
        PingSection(ui, viewModel)
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun PingSection(ui: SettingsUiState, vm: SettingsViewModel) {
    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    stringResource(R.string.ping_section_methods),
                    style = EyebrowStyle,
                    color = InfinityColors.Muted,
                )
                // Демонстрация цвета текущего метода на примере значения.
                StatusPill(
                    text = stringResource(R.string.ping_sample_ms),
                    color = pingColor(ui.pingMethod, SAMPLE_PING_MS),
                )
            }
            PingMethod.entries.forEach { method ->
                OptionRow(
                    title = stringResource(method.titleRes()),
                    subtitle = stringResource(method.descriptionRes()),
                    selected = ui.pingMethod == method,
                    accent = method.baseColor(),
                    onSelect = { vm.selectPingMethod(method) },
                )
            }

            // Режим и таймаут применяются только к прокси-методам (GET/HEAD).
            if (ui.pingMethod.isProxy) {
                Spacer(Modifier.height(12.dp))
                PingModeRow(ui.pingMode, vm::selectPingMode)
                Spacer(Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.ping_mode_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = InfinityColors.Muted,
                )
                Spacer(Modifier.height(16.dp))
                PingTimeoutSlider(ui.pingTimeoutSec, vm::onPingTimeoutChange, vm::savePingTimeout)
            }

            Spacer(Modifier.height(16.dp))
            Text(
                text = stringResource(R.string.ping_section_url),
                style = EyebrowStyle,
                color = InfinityColors.Muted,
                modifier = Modifier.padding(bottom = 8.dp),
            )
            Text(
                text = stringResource(R.string.ping_url_hint),
                style = MaterialTheme.typography.bodySmall,
                color = InfinityColors.Muted,
                modifier = Modifier.padding(bottom = 8.dp),
            )
            OutlinedTextField(
                value = ui.pingUrl,
                onValueChange = vm::onPingUrlChange,
                label = { Text(stringResource(R.string.ping_url_label)) },
                singleLine = true,
                colors = fieldColors(),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(12.dp))
            GradientButton(
                text = stringResource(R.string.ping_url_save),
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
            text = stringResource(R.string.ping_mode_row),
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
        text = stringResource(R.string.ping_section_timeout),
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
        Text(
            stringResource(R.string.ping_seconds_short, sec),
            style = MaterialTheme.typography.bodySmall,
            color = InfinityColors.OnSurface,
            fontWeight = FontWeight.SemiBold,
        )
        Text("${PingSettings.MAX_TIMEOUT_SEC}", style = MaterialTheme.typography.bodySmall, color = InfinityColors.Muted)
    }
}

/** Отображаемое название режима прокси-пинга. */
private fun PingMode.label(): String = when (this) {
    PingMode.DEFAULT -> "Default"
    PingMode.DOUBLE -> "Double"
    PingMode.KEEPALIVE -> "Keepalive"
}
