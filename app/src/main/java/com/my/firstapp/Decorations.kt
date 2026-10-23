package com.my.firstapp

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.sin
import kotlin.random.Random

fun heartPath(center: Offset, size: Float): Path = Path().apply {
    val x = center.x
    val y = center.y
    moveTo(x, y + size * 0.45f)
    cubicTo(x - size * 1.0f, y - size * 0.2f, x - size * 0.45f, y - size * 0.95f, x, y - size * 0.35f)
    cubicTo(x + size * 0.45f, y - size * 0.95f, x + size * 1.0f, y - size * 0.2f, x, y + size * 0.45f)
    close()
}

/** 四角小星星 ✦ */
fun sparklePath(center: Offset, size: Float): Path = Path().apply {
    val x = center.x
    val y = center.y
    moveTo(x, y - size)
    quadraticTo(x, y, x + size, y)
    quadraticTo(x, y, x, y + size)
    quadraticTo(x, y, x - size, y)
    quadraticTo(x, y, x, y - size)
    close()
}

private class Floater(
    val x: Float,
    val speed: Float,
    val phase: Float,
    val size: Float,
    val sway: Float,
    val kind: Int,
)

/** 奶油纸纹、午夜灯光、薄荷网格：三种独立的空间氛围。 */
@Composable
fun FloatingBackground(modifier: Modifier = Modifier) {
    val floaters = remember {
        val rnd = Random(7)
        List(6) {
            Floater(
                x = rnd.nextFloat(),
                speed = 0.5f + rnd.nextFloat() * 0.7f,
                phase = rnd.nextFloat(),
                size = 7f + rnd.nextFloat() * 9f,
                sway = 8f + rnd.nextFloat() * 18f,
                kind = it % 3,
            )
        }
    }
    val time = rememberInfiniteTransition(label = "float").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(40_000, easing = LinearEasing), RepeatMode.Restart),
        label = "t",
    )
    val palette = AppColors.palette
    Canvas(modifier) {
        val t = time.value
        when (palette.style) {
            ThemeStyle.Cream -> {
                // 固定的细小纸纤维，滚动时不会像噪点一样闪动。
                val grain = 24.dp.toPx()
                var y = 0f
                var row = 0
                while (y < size.height) {
                    var x = if (row % 2 == 0) 8.dp.toPx() else grain * 0.7f
                    while (x < size.width) {
                        drawCircle(palette.accent.copy(alpha = 0.10f), 0.65.dp.toPx(), Offset(x, y))
                        x += grain
                    }
                    y += grain
                    row++
                }
                drawCircle(
                    brush = Brush.radialGradient(listOf(palette.light.copy(alpha = 0.65f), Color.Transparent),
                        center = Offset(size.width, size.height * 0.18f), radius = size.width * 0.65f),
                    radius = size.width * 0.65f, center = Offset(size.width, size.height * 0.18f),
                )
                floaters.forEach { f ->
                    val progress = (t * f.speed * 3f + f.phase) % 1f
                    val s = f.size.dp.toPx()
                    val yPos = size.height + s - progress * (size.height + 2 * s)
                    val x = f.x * size.width + sin(progress * 2 * PI * 2 + f.phase * 6).toFloat() * f.sway.dp.toPx()
                    val color = (if (f.kind == 1) palette.accent else palette.primary)
                        .copy(alpha = 0.08f * (1f - progress * progress))
                    if (f.kind == 1) drawPath(sparklePath(Offset(x, yPos), s * 0.8f), color)
                    else drawPath(heartPath(Offset(x, yPos), s), color)
                }
            }
            ThemeStyle.Midnight -> {
                // 两盏克制的暖色壁灯；铜线拱门给深色背景一点餐厅感。
                val glowRadius = size.width * 0.8f
                val pulse = 0.11f + 0.025f * sin(t * 2 * PI).toFloat()
                listOf(Offset(size.width * 0.05f, size.height * 0.16f),
                    Offset(size.width * 0.95f, size.height * 0.72f)).forEach { center ->
                    drawCircle(Brush.radialGradient(listOf(palette.primary.copy(alpha = pulse), Color.Transparent),
                        center = center, radius = glowRadius), radius = glowRadius, center = center)
                }
                val inset = 24.dp.toPx()
                drawArc(
                    color = palette.primary.copy(alpha = 0.10f), startAngle = 180f, sweepAngle = 180f,
                    useCenter = false, topLeft = Offset(inset, 48.dp.toPx()),
                    size = Size((size.width - inset * 2).coerceAtLeast(1f), size.width * 0.85f),
                    style = Stroke(1.dp.toPx()),
                )
                for (i in 0..5) {
                    val center = Offset(size.width * (0.12f + i * 0.155f), 42.dp.toPx() + (i % 2) * 17.dp.toPx())
                    drawPath(sparklePath(center, 2.5.dp.toPx()), palette.primary.copy(alpha = 0.24f))
                }
                drawLine(palette.primary.copy(alpha = 0.09f), Offset(inset, size.height * 0.88f),
                    Offset(size.width - inset, size.height * 0.88f), 1.dp.toPx())
            }
            ThemeStyle.Mint -> {
                // 印刷式网格和定位十字，搭配直角卡片的机械感。
                val step = 28.dp.toPx()
                val grid = palette.primary.copy(alpha = 0.055f)
                var x = 0f
                while (x < size.width) {
                    drawLine(grid, Offset(x, 0f), Offset(x, size.height), 0.7.dp.toPx())
                    x += step
                }
                var y = 0f
                while (y < size.height) {
                    drawLine(grid, Offset(0f, y), Offset(size.width, y), 0.7.dp.toPx())
                    y += step
                }
                val mark = 4.dp.toPx()
                for (i in 0..4) {
                    val center = Offset(if (i % 2 == 0) step else size.width - step, step * (2f + i * 5f))
                    drawLine(palette.primary.copy(alpha = 0.22f), center - Offset(mark, 0f), center + Offset(mark, 0f), 1.dp.toPx())
                    drawLine(palette.primary.copy(alpha = 0.22f), center - Offset(0f, mark), center + Offset(0f, mark), 1.dp.toPx())
                }
            }
        }
    }
}
