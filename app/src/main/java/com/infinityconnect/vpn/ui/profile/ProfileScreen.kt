package com.infinityconnect.vpn.ui.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.background
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.infinityconnect.vpn.ui.components.FullScreenLoading
import com.infinityconnect.vpn.ui.components.GlassCard
import com.infinityconnect.vpn.ui.theme.InfinityColors
import com.infinityconnect.vpn.ui.theme.LocalInfinityGradients
import com.infinityconnect.vpn.ui.util.formatDate
import com.infinityconnect.vpn.ui.util.openInBrowser

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    onBack: () -> Unit,
    onLoggedOut: () -> Unit,
    viewModel: ProfileViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    val gradients = LocalInfinityGradients.current
    Scaffold(
        containerColor = Color.Transparent,
        modifier = Modifier.background(gradients.screen),
        topBar = {
            androidx.compose.material3.TopAppBar(
                title = { Text("Профиль", fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                    }
                },
            )
        },
    ) { padding ->
        if (state.loading) {
            FullScreenLoading(Modifier.padding(padding))
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // --- Аккаунт ---
            InfoCard(title = "Аккаунт") {
                InfoRow("Логин", state.user?.username ?: "—")
                InfoRow("E-mail", state.user?.email ?: "—")
                state.user?.planName?.let { InfoRow("Тариф", it) }
            }

            // --- Подписка ---
            InfoCard(title = "Подписка") {
                val active = state.user?.isSubscriptionActive == true
                InfoRow("Статус", if (active) "Активна" else "Неактивна")
                InfoRow("Действует до", formatDate(state.user?.subscriptionExpiresAt))
                state.subscription?.let { sub ->
                    InfoRow("Ключей", sub.keysCount.toString())
                    sub.totalMonths?.let { InfoRow("Всего месяцев", it.toString()) }
                }
            }

            // --- Поддержка ---
            state.supportUrl?.let { support ->
                TextButton(onClick = { context.openInBrowser(support) }) {
                    Text("Написать в поддержку")
                }
            }

            OutlinedButton(
                onClick = { viewModel.logout(onLoggedOut) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Выйти")
            }
        }
    }
}

@Composable
private fun InfoCard(title: String, content: @Composable () -> Unit) {
    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = title.uppercase(),
                style = com.infinityconnect.vpn.ui.theme.EyebrowStyle,
                color = InfinityColors.Muted,
                modifier = Modifier.padding(bottom = 10.dp),
            )
            content()
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, color = InfinityColors.Muted)
        Text(value, fontWeight = FontWeight.Medium, color = InfinityColors.OnSurface)
    }
    HorizontalDivider(
        modifier = Modifier.padding(top = 6.dp),
        color = InfinityColors.Stroke,
    )
}
