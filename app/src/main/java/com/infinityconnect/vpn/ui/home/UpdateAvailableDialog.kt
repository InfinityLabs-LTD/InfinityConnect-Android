package com.infinityconnect.vpn.ui.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.infinityconnect.vpn.R
import com.infinityconnect.vpn.domain.model.ClientUpdate
import com.infinityconnect.vpn.ui.theme.InfinityColors
import com.infinityconnect.vpn.ui.util.formatBytes

/**
 * Диалог «доступна новая версия».
 *
 * Показывается вместо прежнего snackbar: сообщение об обновлении легко
 * пропустить, а установка APK — осознанное действие, которое стоит предложить
 * явно. Галочка «Не напоминать об этой версии» гасит диалог только для текущего
 * релиза (запоминается versionCode) — о следующем пользователь узнает снова.
 *
 * Оба колбэка получают состояние галочки, потому что отказ нужно сохранить и
 * при закрытии диалога, и при переходе к установке.
 */
@Composable
fun UpdateAvailableDialog(
    update: ClientUpdate,
    onUpdate: (skip: Boolean) -> Unit,
    onDismiss: (skip: Boolean) -> Unit,
) {
    var skip by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = { onDismiss(skip) },
        containerColor = InfinityColors.Surface,
        titleContentColor = InfinityColors.OnSurface,
        textContentColor = InfinityColors.Muted,
        title = {
            Text(
                stringResource(R.string.home_update_title),
                fontWeight = FontWeight.Bold,
                color = InfinityColors.OnSurface,
            )
        },
        text = {
            Column {
                Text(
                    stringResource(R.string.home_update_version, update.version, update.versionCode),
                    color = InfinityColors.OnSurface,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                )
                if (update.notes.isNotBlank()) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        update.notes,
                        color = InfinityColors.Muted,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    stringResource(R.string.home_update_size, formatBytes(update.sizeBytes)),
                    color = InfinityColors.MutedDim,
                    style = MaterialTheme.typography.bodySmall,
                )

                Spacer(Modifier.height(14.dp))
                // Строка целиком кликабельна: попасть в сам чекбокс пальцем
                // неудобно, а промах читается как «диалог не реагирует».
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { skip = !skip },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Start,
                ) {
                    Checkbox(
                        checked = skip,
                        onCheckedChange = { skip = it },
                        colors = CheckboxDefaults.colors(
                            checkedColor = InfinityColors.AccentBlue,
                            uncheckedColor = InfinityColors.Muted,
                            checkmarkColor = InfinityColors.OnSurface,
                        ),
                    )
                    Text(
                        stringResource(R.string.home_update_skip),
                        color = InfinityColors.Muted,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(start = 2.dp),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onUpdate(skip) }) {
                Text(
                    stringResource(R.string.home_update_confirm),
                    color = InfinityColors.AccentBlue,
                    fontWeight = FontWeight.Bold,
                )
            }
        },
        dismissButton = {
            TextButton(onClick = { onDismiss(skip) }) {
                Text(stringResource(R.string.home_update_later), color = InfinityColors.Muted)
            }
        },
    )
}
