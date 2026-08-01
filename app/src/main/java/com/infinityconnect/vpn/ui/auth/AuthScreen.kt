package com.infinityconnect.vpn.ui.auth

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.infinityconnect.vpn.R
import com.infinityconnect.vpn.ui.components.GradientButton
import com.infinityconnect.vpn.ui.theme.InfinityColors
import com.infinityconnect.vpn.ui.theme.LocalInfinityGradients
import com.infinityconnect.vpn.ui.util.openInBrowser

/**
 * Экран авторизации по веб-аккаунту. Ссылки «Регистрация» и «Забыли пароль»
 * ведут в браузер (URL из discovery). Ручного добавления подписок нет —
 * ключи подтянутся автоматически после входа.
 */
@Composable
fun AuthScreen(
    onLoggedIn: () -> Unit,
    viewModel: AuthViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(28.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        BrandMark()
        Spacer(Modifier.height(20.dp))
        Text(
            text = stringResource(R.string.auth_title),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = InfinityColors.OnSurface,
        )
        Text(
            text = stringResource(R.string.auth_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            color = InfinityColors.Muted,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(32.dp))

        OutlinedTextField(
            value = state.login,
            onValueChange = viewModel::onLoginChange,
            label = { Text(stringResource(R.string.auth_login_hint)) },
            singleLine = true,
            enabled = !state.loading,
            colors = fieldColors(),
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = state.password,
            onValueChange = viewModel::onPasswordChange,
            label = { Text(stringResource(R.string.auth_password_hint)) },
            singleLine = true,
            enabled = !state.loading,
            visualTransformation = PasswordVisualTransformation(),
            isError = state.error != null,
            colors = fieldColors(),
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
        )

        if (state.error != null) {
            Text(
                text = state.error!!,
                color = InfinityColors.Coral,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 8.dp).fillMaxWidth(),
            )
        }

        Spacer(Modifier.height(24.dp))
        GradientButton(
            text = stringResource(R.string.auth_submit),
            onClick = { viewModel.submit(onLoggedIn) },
            enabled = !state.loading,
            loading = state.loading,
            modifier = Modifier.fillMaxWidth(),
        )

        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            TextButton(onClick = { context.openInBrowser(state.registerUrl) }) {
                Text(stringResource(R.string.auth_register), color = InfinityColors.AccentBlue)
            }
            TextButton(onClick = { context.openInBrowser(state.forgotPasswordUrl) }) {
                Text(stringResource(R.string.auth_forgot_password), color = InfinityColors.AccentBlue)
            }
        }
    }
}

/** Брэнд-глиф: градиентная «бесконечность» в мягком свечении. */
@Composable
private fun BrandMark() {
    val gradients = LocalInfinityGradients.current
    Box(
        modifier = Modifier.size(96.dp),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.size(96.dp)) {
            // Ореол.
            drawCircle(
                brush = Brush.radialGradient(
                    listOf(InfinityColors.AccentBlue.copy(alpha = 0.35f), Color.Transparent),
                ),
                radius = size.minDimension / 2,
            )
            // Символ бесконечности из двух окружностей.
            val r = size.minDimension * 0.16f
            val cy = size.height / 2
            val cx = size.width / 2
            val stroke = Stroke(width = 7.dp.toPx(), cap = StrokeCap.Round)
            drawCircle(
                brush = Brush.linearGradient(listOf(InfinityColors.AccentCyan, InfinityColors.AccentBlue)),
                radius = r,
                center = Offset(cx - r * 0.9f, cy),
                style = stroke,
            )
            drawCircle(
                brush = Brush.linearGradient(listOf(InfinityColors.AccentBlue, InfinityColors.AccentIndigo)),
                radius = r,
                center = Offset(cx + r * 0.9f, cy),
                style = stroke,
            )
        }
    }
}

@Composable
private fun fieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = InfinityColors.AccentBlue,
    unfocusedBorderColor = InfinityColors.Stroke,
    focusedContainerColor = InfinityColors.Surface,
    unfocusedContainerColor = InfinityColors.Surface,
    focusedLabelColor = InfinityColors.AccentBlue,
    unfocusedLabelColor = InfinityColors.Muted,
    cursorColor = InfinityColors.AccentBlue,
    focusedTextColor = InfinityColors.OnSurface,
    unfocusedTextColor = InfinityColors.OnSurface,
)
