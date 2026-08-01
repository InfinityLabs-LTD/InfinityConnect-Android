package com.infinityconnect.vpn.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.infinityconnect.vpn.R
import com.infinityconnect.vpn.domain.model.Language
import com.infinityconnect.vpn.ui.components.GlassCard
import com.infinityconnect.vpn.ui.theme.InfinityColors

/** Экран выбора языка интерфейса: системный / русский / английский. */
@Composable
fun LanguageScreen(
    viewModel: SettingsViewModel,
    onBack: () -> Unit,
) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()

    SettingsScaffold(title = stringResource(R.string.language_title), onBack = onBack) {
        GlassCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.fillMaxWidth()) {
                SectionTitle(stringResource(R.string.language_section))
                Language.entries.forEach { language ->
                    OptionRow(
                        title = stringResource(language.titleRes()),
                        subtitle = stringResource(language.subtitleRes()),
                        selected = ui.language == language,
                        onSelect = { viewModel.selectLanguage(language) },
                    )
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.language_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = InfinityColors.Muted,
                )
            }
        }
    }
}

/**
 * Названия языков НЕ локализуются по смыслу: «Русский» и «English» всегда
 * пишутся на своём языке, иначе выбор нечитаем для того, кто открыл экран на
 * чужом языке. Локализуется только «Системный».
 */
private fun Language.titleRes(): Int = when (this) {
    Language.SYSTEM -> R.string.language_system
    Language.RUSSIAN -> R.string.language_russian
    Language.ENGLISH -> R.string.language_english
}

private fun Language.subtitleRes(): Int = when (this) {
    Language.SYSTEM -> R.string.language_system_desc
    Language.RUSSIAN -> R.string.language_russian_desc
    Language.ENGLISH -> R.string.language_english_desc
}
