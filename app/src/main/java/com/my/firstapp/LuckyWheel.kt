package com.my.firstapp

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

data class WheelItem(val label: String, val emoji: String? = null)

/** 指针在正上方，算出转盘停下时指着第几块。 */
fun pickedIndex(rotation: Float, count: Int): Int {
    val sweep = 360f / count
    val angle = ((-rotation) % 360f + 360f) % 360f
    return (angle / sweep).toInt().coerceIn(0, count - 1)
}

internal fun shouldFlipWheelLabel(wheelRotation: Float, sectorRotation: Float): Boolean {
    val worldRotation = ((wheelRotation + sectorRotation) % 360f + 360f) % 360f
    return worldRotation > 90f && worldRotation < 270f
}

/** 排好版的一行字。distance = 这一行的中心离圆心多远（扇区转到正上方时，往上为正）。 */
private class LabelLine(val layout: TextLayoutResult, val distance: Float, val isEmoji: Boolean = false)

@Composable
fun LuckyWheel(
    items: List<WheelItem>,
    rotation: () -> Float,
    spinning: Boolean,
    onGo: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val textMeasurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val haptic = LocalHapticFeedback.current
    val sound = LocalSound.current
    val isSpinning by rememberUpdatedState(spinning)

    // 转的时候每经过一格「咔哒」一下，再轻轻震一下
    LaunchedEffect(items.size) {
        if (items.isEmpty()) return@LaunchedEffect
        snapshotFlow { pickedIndex(rotation(), items.size) }
            .distinctUntilChanged()
            .collect {
                if (isSpinning) {
                    sound?.tick()
                    haptic.performHapticFeedback(HapticFeedbackType.SegmentFrequentTick)
                }
            }
    }

    val transition = rememberInfiniteTransition(label = "wheel")
    val blink = transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(if (spinning) 260 else 900, easing = LinearEasing), RepeatMode.Restart),
        label = "lights",
    )

    BoxWithConstraints(modifier.fillMaxWidth().aspectRatio(1f), contentAlignment = Alignment.Center) {
        val outer = with(density) { min(maxWidth.toPx(), maxHeight.toPx()) } / 2f
        val ring = outer * 0.1f
        val radius = (outer - ring) * 0.97f
        val palette = AppColors.palette
        // 排版比较费时：只在内容、大小、主题变了的时候算，转动的每一帧直接画
        val labels = remember(items, radius, palette) {
            items.map { layoutLabel(it, items.size, radius, textMeasurer, density, palette.text) }
        }

        Canvas(Modifier.fillMaxSize()) {
            // 转盘底下一圈柔和的光晕
            drawCircle(palette.primary.copy(alpha = 0.12f), outer, center)
            drawRing(palette.ring, outer * 0.97f, ring, blink.value < 0.5f)
            drawCircle(Color.White, radius + 3.dp.toPx(), center)
            if (items.isEmpty()) {
                drawCircle(palette.light, radius, center)
                return@Canvas
            }
            val currentRotation = rotation()
            rotate(currentRotation, center) {
                drawSlices(palette.slices, items.size, radius, labels, currentRotation)
            }
        }
        GoButton(spinning, onGo, Modifier.fillMaxSize(0.23f))
    }
}

private fun DrawScope.drawRing(colors: List<Color>, outer: Float, ring: Float, evenLit: Boolean) {
    drawCircle(Brush.sweepGradient(colors, center), outer, center)
    val lights = 20
    for (k in 0 until lights) {
        val a = 2 * PI * k / lights
        val p = center + Offset(cos(a).toFloat(), sin(a).toFloat()) * (outer - ring / 2f)
        val lit = (k % 2 == 0) == evenLit
        val bulb = if (lit) Color(0xFFFFF59D) else Color.White
        if (lit) drawCircle(Color(0x88FFF3A0), ring * 0.42f, p)
        // 每隔几颗灯换成一颗小爱心
        if (k % 5 == 0) drawPath(heartPath(p, ring * 0.32f), bulb) else drawCircle(bulb, ring * 0.2f, p)
    }
}

private fun DrawScope.drawSlices(
    slices: List<Color>,
    count: Int,
    radius: Float,
    labels: List<List<LabelLine>>,
    wheelRotation: Float,
) {
    val sweep = 360f / count
    for (i in 0 until count) {
        // 最后一块如果和第一块同色，就换一个
        val sameAsFirst = i > 0 && i == count - 1 && i % slices.size == 0
        val color = if (sameAsFirst) slices[2] else slices[i % slices.size]
        drawArc(
            brush = Brush.radialGradient(listOf(lerp(color, Color.White, 0.55f), color), center, radius),
            startAngle = -90f + i * sweep,
            sweepAngle = sweep,
            useCenter = true,
            topLeft = center - Offset(radius, radius),
            size = Size(radius * 2, radius * 2),
        )
    }
    if (count > 1) {
        for (i in 0 until count) {
            val a = Math.toRadians((-90f + i * sweep).toDouble())
            drawLine(
                Color.White,
                center,
                center + Offset(cos(a).toFloat(), sin(a).toFloat()) * radius,
                strokeWidth = 2.5.dp.toPx(),
            )
        }
    }
    // 文字跟着扇区移动，但落到下半圈时原地翻正；多行文字同时反转行序，保持从上往下正常阅读。
    labels.forEachIndexed { i, lines ->
        val sectorRotation = i * sweep + sweep / 2
        val flipUpright = shouldFlipWheelLabel(wheelRotation, sectorRotation)
        rotate(sectorRotation, center) {
            val emojiLines = lines.filter { it.isEmoji }
            val textSlots = lines.filterNot { it.isEmoji }
            val textLayouts = if (flipUpright) textSlots.reversed() else textSlots
            val placed = emojiLines + textSlots.mapIndexed { index, slot ->
                LabelLine(textLayouts[index].layout, slot.distance)
            }
            placed.forEach { line ->
                val lineCenter = Offset(center.x, center.y - line.distance)
                if (flipUpright) {
                    rotate(180f, lineCenter) {
                        drawText(
                            line.layout,
                            topLeft = Offset(
                                lineCenter.x - line.layout.size.width / 2f,
                                lineCenter.y - line.layout.size.height / 2f,
                            ),
                        )
                    }
                } else {
                    drawText(
                        line.layout,
                        topLeft = Offset(
                            lineCenter.x - line.layout.size.width / 2f,
                            lineCenter.y - line.layout.size.height / 2f,
                        ),
                    )
                }
            }
        }
    }
}

/** 估算一个字有多宽（按字号的倍数）：汉字 1 个字宽，英文数字大约半个。 */
private fun charWidth(c: Char) = if (c.code < 0x2E80) 0.58f else 1f

/**
 * 给一块扇区排文字：先放图标，再把名字横着一行行往圆心方向排，越靠圆心扇区越窄、每行放的字越少。
 * 从大字号开始试，放得下就用；最小字号也放不下才在最后加「…」。
 */
private fun layoutLabel(
    item: WheelItem,
    count: Int,
    radius: Float,
    measurer: TextMeasurer,
    density: Density,
    color: Color,
): List<LabelLine> {
    val text = item.label.trim()
    val outerEdge = radius * 0.93f
    val innerEdge = radius * 0.34f // GO 按钮外面一点
    val half = min(PI / count, PI / 3) // 半个扇区的角度，块很大时按 60° 算，免得字太宽
    fun widthAt(d: Float) = (2 * d * sin(half)).toFloat() * 0.78f

    /** 返回每行的 (文字, 中心距离)，以及图标的中心距离；放不下返回 null。 */
    fun attempt(fs: Float, allowCut: Boolean): Pair<List<Pair<String, Float>>, Float?>? {
        var d = outerEdge
        var emojiAt: Float? = null
        if (item.emoji != null) {
            val eh = fs * 1.5f
            emojiAt = d - eh / 2f
            d -= eh
        }
        val lines = mutableListOf<Pair<String, Float>>()
        var rest = text
        while (rest.isNotEmpty()) {
            val lh = fs * 1.18f
            if (lines.size == 3 || d - lh < innerEdge) {
                if (!allowCut || lines.isEmpty()) return null
                // 放不下了：最后一行末尾换成「…」
                val (last, at) = lines.removeAt(lines.lastIndex)
                val maxW = widthAt(at - lh / 2f) / fs
                var cut = last
                while (cut.isNotEmpty() && cut.sumOf { charWidth(it).toDouble() } + 1f > maxW) cut = cut.dropLast(1)
                lines += "$cut…" to at
                break
            }
            val maxW = widthAt(d - lh) / fs
            var n = 0
            var acc = 0f
            while (n < rest.length && acc + charWidth(rest[n]) <= maxW) {
                acc += charWidth(rest[n])
                n++
            }
            if (n == 0) return null
            // 尽量在括号、空格前换行，比如「超级至尊披萨 / （9寸）」
            if (n < rest.length) {
                val brk = rest.take(n + 1).indexOfLast { it == '（' || it == '(' || it == ' ' }
                if (brk >= 2) n = brk
            }
            lines += rest.take(n).trim() to (d - lh / 2f)
            rest = rest.drop(n).trimStart()
            d -= lh
        }
        return lines to emojiAt
    }

    var fs = radius * 0.13f
    val minFs = radius * 0.06f
    var result = attempt(fs, allowCut = false)
    while (result == null && fs > minFs) {
        fs *= 0.9f
        result = attempt(fs, allowCut = false)
    }
    if (result == null) {
        fs = minFs
        result = attempt(fs, allowCut = true) ?: (emptyList<Pair<String, Float>>() to null)
    }

    val style = TextStyle(color = color, fontSize = with(density) { fs.toSp() }, fontFamily = CuteFont)
    val (lines, emojiAt) = result
    return buildList {
        if (item.emoji != null && emojiAt != null) {
            val emojiStyle = TextStyle(fontSize = with(density) { (fs * 1.3f).toSp() })
            add(LabelLine(measurer.measure(item.emoji, emojiStyle), emojiAt, isEmoji = true))
        }
        lines.forEach { (t, at) -> add(LabelLine(measurer.measure(t, style), at)) }
    }
}

@Composable
private fun GoButton(spinning: Boolean, onGo: () -> Unit, modifier: Modifier) {
    val sound = LocalSound.current
    val pulse = rememberInfiniteTransition(label = "go")
    val scale by pulse.animateFloat(
        initialValue = 1f,
        targetValue = 1.07f,
        animationSpec = infiniteRepeatable(tween(700), RepeatMode.Reverse),
        label = "pulse",
    )
    BoxWithConstraints(modifier.scale(if (spinning) 1f else scale), contentAlignment = Alignment.Center) {
        val goSize = (maxWidth.value * 0.3f).sp
        // 指针：从 GO 按钮往上伸出的小三角
        Canvas(Modifier.fillMaxSize()) {
            val r = size.minDimension / 2f
            val pointer = Path().apply {
                moveTo(center.x, center.y - r * 1.75f)
                lineTo(center.x + r * 0.38f, center.y - r * 0.7f)
                lineTo(center.x - r * 0.38f, center.y - r * 0.7f)
                close()
            }
            drawPath(pointer, AppColors.Deep)
        }
        Box(
            Modifier
                .fillMaxSize()
                .shadow(8.dp, CircleShape, spotColor = AppColors.Deep)
                .clip(CircleShape)
                .background(Brush.verticalGradient(listOf(lerp(AppColors.Primary, Color.White, 0.35f), AppColors.Deep)))
                .border(4.dp, Color.White, CircleShape)
                .clickable(enabled = !spinning) {
                    sound?.pop()
                    onGo()
                },
            contentAlignment = Alignment.Center,
        ) {
            Text("GO", color = Color.White, fontSize = goSize, fontFamily = CuteFont)
        }
    }
}
