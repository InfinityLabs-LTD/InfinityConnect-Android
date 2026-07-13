package com.infinityconnect.vpn.ui.home

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.infinityconnect.vpn.ui.theme.InfinityColors
import com.infinityconnect.vpn.vpn.TunnelState

/**
 * Центральная круглая кнопка подключения с радиальным свечением, меняющим цвет
 * по состоянию туннеля: idle → синий, connecting → пульсирующий индиго,
 * connected → мятный. Вокруг — вращающееся кольцо-прогресс при подключении.
 */
@Composable
fun ConnectHero(
    tunnel: TunnelState,
    enabled: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
    /** Компактный размер (когда не подключён — освобождает место списку). */
    compact: Boolean = false,
) {
    val connected = tunnel is TunnelState.Connected
    val connecting = tunnel is TunnelState.Connecting || tunnel is TunnelState.Disconnecting

    // Внешний ореол/кнопка сжимаются в компактном режиме.
    val heroSize = if (compact) 176.dp else 248.dp
    val discSize = if (compact) 112.dp else 150.dp
    val iconSize = if (compact) 40.dp else 52.dp

    val accent by animateColorAsState(
        targetValue = when {
            connected -> InfinityColors.Mint
            tunnel is TunnelState.Error -> InfinityColors.Coral
            else -> InfinityColors.AccentBlue
        },
        animationSpec = tween(600),
        label = "accent",
    )

    val transition = rememberInfiniteTransition(label = "hero")
    // Пульс свечения (дыхание) — активнее при connecting.
    val pulse by transition.animateFloat(
        initialValue = 0.85f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(if (connecting) 900 else 2600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "pulse",
    )
    // Вращение кольца при connecting.
    val sweep by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "sweep",
    )

    val interaction = remember { MutableInteractionSource() }

    Box(
        modifier = modifier.size(heroSize),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.size(heroSize)) {
            val c = Offset(size.width / 2, size.height / 2)
            val glowScale = if (connecting || connected) pulse else 1f

            // Радиальное свечение-ореол.
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(accent.copy(alpha = 0.45f), Color.Transparent),
                    center = c,
                    radius = size.minDimension / 2 * glowScale,
                ),
                radius = size.minDimension / 2 * glowScale,
                center = c,
            )

            val ringRadius = size.minDimension / 2 * 0.62f
            // Тонкая базовая окружность.
            drawCircle(
                color = InfinityColors.Stroke,
                radius = ringRadius,
                center = c,
                style = Stroke(width = 3.dp.toPx()),
            )
            // Активная дуга: полное кольцо когда connected, вращающийся сегмент при connecting.
            if (connected) {
                drawCircle(
                    color = accent,
                    radius = ringRadius,
                    center = c,
                    style = Stroke(width = 4.dp.toPx()),
                )
            } else if (connecting) {
                drawArc(
                    color = accent,
                    startAngle = sweep,
                    sweepAngle = 90f,
                    useCenter = false,
                    topLeft = Offset(c.x - ringRadius, c.y - ringRadius),
                    size = androidx.compose.ui.geometry.Size(ringRadius * 2, ringRadius * 2),
                    style = Stroke(width = 4.dp.toPx()),
                )
            }
        }

        // Внутренний диск-кнопка.
        Box(
            modifier = Modifier
                .size(discSize)
                .clickable(
                    interactionSource = interaction,
                    indication = null,
                    enabled = enabled,
                    onClick = onToggle,
                )
                .then(Modifier),
            contentAlignment = Alignment.Center,
        ) {
            Canvas(modifier = Modifier.size(discSize)) {
                val c = Offset(size.width / 2, size.height / 2)
                drawCircle(
                    brush = Brush.linearGradient(
                        colors = if (connected) {
                            listOf(InfinityColors.Mint, InfinityColors.AccentCyan)
                        } else {
                            listOf(InfinityColors.AccentBlue, InfinityColors.AccentIndigo)
                        },
                    ),
                    radius = size.minDimension / 2,
                    center = c,
                )
                // Внутренняя тень-глянец сверху.
                drawCircle(
                    brush = Brush.verticalGradient(
                        colors = listOf(Color.White.copy(alpha = 0.18f), Color.Transparent),
                        startY = 0f,
                        endY = size.height,
                    ),
                    radius = size.minDimension / 2,
                    center = c,
                )
            }
            Icon(
                imageVector = Icons.Filled.PowerSettingsNew,
                contentDescription = if (connected) "Отключить" else "Подключить",
                tint = Color.White,
                modifier = Modifier.size(iconSize),
            )
        }
    }
}
