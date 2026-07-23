package com.infinityconnect.vpn.ui.home

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.infinityconnect.vpn.R
import com.infinityconnect.vpn.ui.theme.InfinityColors
import com.infinityconnect.vpn.ui.theme.LocalInfinityGradients

/** Пункт бокового меню (как сайдбар Windows-клиента). */
data class DrawerItem(
    val icon: String,
    val label: String,
    val onClick: () -> Unit,
)

/**
 * Боковое меню в стиле сайдбара Windows-клиента: лого сверху, пункты-«пилюли»
 * (активный — с акцентным градиентом), статус-индикатор туннеля внизу.
 */
@Composable
fun AppDrawer(
    items: List<DrawerItem>,
    activeIndex: Int,
    connected: Boolean,
) {
    ModalDrawerSheet(
        drawerContainerColor = InfinityColors.SpaceElevated,
        drawerShape = RoundedCornerShape(topEnd = 24.dp, bottomEnd = 24.dp),
        modifier = Modifier.width(300.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxHeight()
                .padding(horizontal = 14.dp, vertical = 22.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            // --- Лого (как в сайдбаре Windows). ---
            Row(
                modifier = Modifier.padding(start = 10.dp, end = 10.dp, bottom = 20.dp, top = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Image(
                    painter = painterResource(R.drawable.logo_app),
                    contentDescription = null,
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(12.dp)),
                )
                Column {
                    Text(
                        text = "Infinity",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleMedium,
                        color = InfinityColors.OnSurface,
                    )
                    Text(
                        text = "Connect",
                        style = MaterialTheme.typography.bodySmall,
                        color = InfinityColors.Muted,
                    )
                }
            }

            // --- Пункты меню. ---
            items.forEachIndexed { index, item ->
                DrawerNavItem(
                    icon = item.icon,
                    label = item.label,
                    active = index == activeIndex,
                    onClick = item.onClick,
                )
            }

            Spacer(Modifier.weight(1f))

            // --- Статус-индикатор внизу (как в Windows-сайдбаре). ---
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(InfinityColors.Surface)
                    .border(1.dp, InfinityColors.Stroke, RoundedCornerShape(12.dp))
                    .padding(horizontal = 12.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(9.dp)
                        .clip(CircleShape)
                        .background(if (connected) InfinityColors.Mint else InfinityColors.MutedDim),
                )
                Text(
                    text = if (connected) "Подключено" else "Отключено",
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (connected) InfinityColors.Mint else InfinityColors.Muted,
                )
            }
        }
    }
}

/** Пункт меню: emoji-иконка + подпись; активный — градиентная пилюля. */
@Composable
private fun DrawerNavItem(
    icon: String,
    label: String,
    active: Boolean,
    onClick: () -> Unit,
) {
    val gradients = LocalInfinityGradients.current
    val shape = RoundedCornerShape(12.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .then(
                if (active) Modifier.background(gradients.accent)
                else Modifier,
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(text = icon, style = MaterialTheme.typography.titleMedium)
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = if (active) FontWeight.SemiBold else FontWeight.Medium,
            color = if (active) Color.White else InfinityColors.Muted,
        )
    }
}
