package com.infinityconnect.vpn.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import com.infinityconnect.vpn.ui.theme.InfinityColors
import kotlin.math.PI
import kotlin.math.sin

/**
 * Фоновая «сетевая» анимация в стиле Windows-клиента (MeshBackground.tsx):
 * radial-градиент космоса, плывущие размытые glow-пятна, дрейфующая сетка
 * линий с радиальным затуханием и пульсирующие glow-узлы.
 *
 * Один Canvas без композиций на каждый кадр тяжёлых эффектов: blur пятен
 * имитируется radial-градиентами (дёшево по GPU), сетка — линии с альфой,
 * зависящей от расстояния до «центра» затухания.
 *
 * Рисовать ПОД контентом экрана: `Box { MeshBackground(); content }`.
 */
@Composable
fun MeshBackground(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "mesh")

    // Дрейф сетки: сдвиг на один шаг за цикл — бесшовный цикл.
    val drift by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 12_000, easing = LinearEasing),
        ),
        label = "drift",
    )
    // Общая фаза для плавания glow-пятен (0..2π за 26 с, как в Windows).
    val blobPhase by transition.animateFloat(
        initialValue = 0f,
        targetValue = (2 * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 26_000, easing = LinearEasing),
        ),
        label = "blobs",
    )
    // Фаза пульсации узлов (общая; у каждого узла свой сдвиг/скорость).
    val nodePhase by transition.animateFloat(
        initialValue = 0f,
        targetValue = (2 * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 7_000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "nodes",
    )

    Canvas(modifier = modifier.fillMaxSize()) {
        // 1) Космический фон: radial из левого-верхнего угла (как в Windows).
        drawRect(
            brush = Brush.radialGradient(
                colors = listOf(Color(0xFF1B1140), InfinityColors.Space),
                center = Offset(size.width * 0.2f, -size.height * 0.1f),
                radius = size.maxDimension * 1.1f,
            ),
        )

        // 2) Плывущие glow-пятна (aurora под сеткой).
        drawBlob(InfinityColors.AccentIndigo, 0.05f, 0.05f, 0.55f, blobPhase, dx = 0.07f, dy = 0.05f)
        drawBlob(InfinityColors.AccentMagenta, 0.85f, 0.22f, 0.45f, blobPhase + 2.1f, dx = -0.06f, dy = 0.06f)
        drawBlob(InfinityColors.AccentBlue, 0.45f, 0.75f, 0.50f, blobPhase + 4.2f, dx = 0.05f, dy = -0.05f)

        // 3) Дрейфующая сетка с радиальным затуханием от точки (50%, 35%).
        drawGrid(drift)

        // 4) Пульсирующие glow-узлы сети.
        NODES.forEach { n ->
            // sin-пульсация 0.2..1.0 альфы и 2..4dp радиуса, у каждого свой сдвиг.
            val pulse = (sin(nodePhase * n.speed + n.offset) + 1f) / 2f // 0..1
            val alpha = 0.2f + 0.8f * pulse
            val r = (2f + 2f * pulse) * density
            val center = Offset(size.width * n.x, size.height * n.y)
            // Glow-ореол + ядро.
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(n.color.copy(alpha = alpha * 0.5f), Color.Transparent),
                    center = center,
                    radius = r * 4,
                ),
                radius = r * 4,
                center = center,
            )
            drawCircle(color = n.color.copy(alpha = alpha), radius = r, center = center)
        }
    }
}

/** Размытое glow-пятно: radial-градиент вместо дорогого blur. */
private fun DrawScope.drawBlob(
    color: Color,
    x: Float,
    y: Float,
    sizeFrac: Float,
    phase: Float,
    dx: Float,
    dy: Float,
) {
    val radius = size.maxDimension * sizeFrac / 2f
    val center = Offset(
        size.width * (x + dx * sin(phase)),
        size.height * (y + dy * sin(phase * 0.9f)),
    )
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(color.copy(alpha = 0.20f), Color.Transparent),
            center = center,
            radius = radius,
        ),
        radius = radius,
        center = center,
    )
}

/** Сетка линий с дрейфом по диагонали и радиальным затуханием альфы. */
private fun DrawScope.drawGrid(drift: Float) {
    val step = 52f * density
    val offset = drift * step
    val fadeCenter = Offset(size.width * 0.5f, size.height * 0.35f)
    val fadeRadius = size.maxDimension * 0.85f
    val stroke = 1f * density
    val base = InfinityColors.AccentIndigo

    fun alphaAt(p: Offset): Float {
        val d = (p - fadeCenter).getDistance() / fadeRadius
        // 0%→1.0, 70%→0.4, 100%→0 (как gridFade в Windows-клиенте).
        return when {
            d <= 0.7f -> 1f - d / 0.7f * 0.6f
            d <= 1f -> (1f - d) / 0.3f * 0.4f
            else -> 0f
        } * 0.28f
    }

    // Вертикальные линии: альфа меняется вдоль линии — рисуем сегментами по шагу.
    var x = -step + offset
    while (x < size.width + step) {
        var y = 0f
        while (y < size.height) {
            val segEnd = minOf(y + step, size.height)
            val mid = Offset(x, (y + segEnd) / 2f)
            val a = alphaAt(mid)
            if (a > 0.005f) {
                drawLine(
                    color = base.copy(alpha = a),
                    start = Offset(x, y),
                    end = Offset(x, segEnd),
                    strokeWidth = stroke,
                )
            }
            y = segEnd
        }
        x += step
    }
    // Горизонтальные линии.
    var yy = -step + offset
    while (yy < size.height + step) {
        var xx = 0f
        while (xx < size.width) {
            val segEnd = minOf(xx + step, size.width)
            val mid = Offset((xx + segEnd) / 2f, yy)
            val a = alphaAt(mid)
            if (a > 0.005f) {
                drawLine(
                    color = base.copy(alpha = a),
                    start = Offset(xx, yy),
                    end = Offset(segEnd, yy),
                    strokeWidth = stroke,
                )
            }
            xx = segEnd
        }
        yy += step
    }
}

/** Узлы сети: позиция в долях экрана, цвет, скорость и фазовый сдвиг пульса. */
private data class MeshNode(
    val x: Float,
    val y: Float,
    val color: Color,
    val speed: Float,
    val offset: Float,
)

private val NODES = listOf(
    MeshNode(0.12f, 0.18f, InfinityColors.AccentCyan, 1.00f, 0.0f),
    MeshNode(0.82f, 0.24f, InfinityColors.AccentBlue, 0.85f, 1.5f),
    MeshNode(0.45f, 0.68f, InfinityColors.Mint, 0.95f, 3.0f),
    MeshNode(0.68f, 0.82f, InfinityColors.AccentMagenta, 0.75f, 2.0f),
    MeshNode(0.28f, 0.48f, InfinityColors.AccentBlue, 1.05f, 4.0f),
    MeshNode(0.92f, 0.60f, InfinityColors.AccentCyan, 0.90f, 1.0f),
    MeshNode(0.58f, 0.12f, InfinityColors.AccentMagenta, 0.80f, 2.5f),
    MeshNode(0.08f, 0.78f, InfinityColors.AccentBlue, 1.02f, 3.5f),
    MeshNode(0.38f, 0.90f, InfinityColors.AccentCyan, 0.72f, 0.5f),
)
