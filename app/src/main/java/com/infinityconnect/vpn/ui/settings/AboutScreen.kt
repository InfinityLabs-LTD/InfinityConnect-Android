package com.infinityconnect.vpn.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import android.content.Context
import com.infinityconnect.vpn.domain.model.ClientUpdate
import com.infinityconnect.vpn.BuildConfig
import com.infinityconnect.vpn.BuildFlags
import com.infinityconnect.vpn.ui.components.GlassCard
import com.infinityconnect.vpn.ui.theme.InfinityColors

/** Экран «О приложении»: версия, ядра, разработчик, обновление клиента. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(
    onBack: () -> Unit,
    viewModel: AboutViewModel = hiltViewModel(),
) {
    val updateState by viewModel.updateState.collectAsState()

    SettingsScaffold(title = "О приложении", onBack = onBack) {
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
        Spacer(Modifier.height(12.dp))
        UpdateCard(
            state = updateState,
            onCheck = viewModel::check,
            onDownload = viewModel::downloadAndInstall,
        )
        Spacer(Modifier.height(8.dp))
    }
}

/** Карточка «Обновление»: проверка версии на сервере, скачивание и установка APK. */
@Composable
private fun UpdateCard(
    state: UpdateUiState,
    onCheck: () -> Unit,
    onDownload: (Context, ClientUpdate) -> Unit,
) {
    val context = LocalContext.current
    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.fillMaxWidth()) {
            SectionTitle("ОБНОВЛЕНИЕ")
            when (state) {
                is UpdateUiState.Idle, UpdateUiState.UpToDate, is UpdateUiState.Error -> {
                    if (state is UpdateUiState.UpToDate) {
                        StatusText("У вас последняя версия")
                    }
                    if (state is UpdateUiState.Error) {
                        StatusText(state.message, error = true)
                    }
                    Button(
                        onClick = onCheck,
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    ) {
                        Text("Проверить обновление")
                    }
                }

                UpdateUiState.Checking -> Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.size(10.dp))
                    Text(
                        "Проверка обновления…",
                        style = MaterialTheme.typography.bodyMedium,
                        color = InfinityColors.Muted,
                    )
                }

                is UpdateUiState.Available -> {
                    StatusText("Доступна версия ${state.update.version}")
                    if (state.update.notes.isNotBlank()) {
                        Text(
                            text = state.update.notes,
                            style = MaterialTheme.typography.bodySmall,
                            color = InfinityColors.Muted,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                    Button(
                        onClick = { onDownload(context, state.update) },
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    ) {
                        Text("Скачать и установить")
                    }
                }

                is UpdateUiState.Downloading -> {
                    StatusText("Скачивание версии ${state.update.version}…")
                    if (state.progress >= 0f) {
                        LinearProgressIndicator(
                            progress = { state.progress },
                            modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                        )
                    } else {
                        LinearProgressIndicator(
                            modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusText(text: String, error: Boolean = false) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        fontWeight = FontWeight.SemiBold,
        color = if (error) MaterialTheme.colorScheme.error else InfinityColors.OnSurface,
        modifier = Modifier.padding(top = 2.dp),
    )
}

/** Строка «ключ — значение». */
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
