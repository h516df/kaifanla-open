package com.my.firstapp

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.heightIn
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlin.random.Random

/** 抽中时随机出现的一句话 */
private val SweetWords = listOf(
    "好好吃饭，才有力气想我呀 💕",
    "吃饱饱，心情才会好好 🌈",
    "今天也要被好吃的治愈哦 ✨",
    "慢慢吃，不着急 💗",
    "吃完记得拍照给我看～ 📸",
    "不许挑食，要乖乖吃完 🥢",
    "你开心，比什么都重要 🌷",
    "认真吃饭的你最可爱了 🐰",
    "美食和你，都是今天的小确幸 🍀",
)

/** 抽中结果的弹窗：弹出动画 + 彩带 + 音效 + 震动。 */
@Composable
fun ResultDialog(
    emoji: String,
    photo: String = "",
    kicker: String,
    title: String,
    subtitle: String,
    confirmText: String,
    skipText: String,
    onConfirm: () -> Unit,
    onAgain: () -> Unit,
    onSkip: () -> Unit,
    onDismiss: () -> Unit,
    extra: @Composable ColumnScope.() -> Unit = {},
) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        val maxCardHeight = (LocalConfiguration.current.screenHeightDp * 0.85f).dp
        val haptic = LocalHapticFeedback.current
        val sound = LocalSound.current
        val words = remember { SweetWords.random() }
        val pop = remember { Animatable(0.5f) }
        LaunchedEffect(Unit) {
            sound?.ding()
            haptic.performHapticFeedback(HapticFeedbackType.Confirm)
            pop.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow))
        }
        Box(
            Modifier
                .fillMaxSize()
                .clickable(remember { MutableInteractionSource() }, indication = null, onClick = onDismiss),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = 340.dp)
                    .fillMaxWidth(0.86f)
                    .graphicsLayer {
                        scaleX = pop.value
                        scaleY = pop.value
                    }
                    .heightIn(max = maxCardHeight)
                    .clip(RoundedCornerShape(AppColors.CardRadius))
                    .background(Brush.verticalGradient(listOf(AppColors.Light, AppColors.Surface, AppColors.Surface)))
                    .clickable(remember { MutableInteractionSource() }, indication = null) {}
                    .verticalScroll(rememberScrollState())
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (photo.isBlank()) EmojiBadge(emoji, size = 92.dp, color = AppColors.Surface)
                else ShopArtwork(emoji, photo, Modifier.fillMaxWidth().height(140.dp), fallbackSize = 92.dp)
                Text(kicker, fontSize = 16.sp, color = AppColors.SubText)
                Text(
                    title,
                    fontSize = 32.sp,
                    fontFamily = CuteFont,
                    color = AppColors.Primary,
                    textAlign = TextAlign.Center,
                )
                Text(subtitle, fontSize = 15.sp, color = AppColors.SubText, textAlign = TextAlign.Center)
                extra()
                Text(
                    words,
                    fontSize = 14.sp,
                    color = AppColors.Primary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 4.dp),
                )
                GradientButton(confirmText, onConfirm, Modifier.fillMaxWidth().padding(top = 6.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    TextButton(onClick = onAgain) { Text("再抽一次", color = AppColors.SubText) }
                    TextButton(onClick = onSkip) { Text(skipText, color = AppColors.SubText) }
                }
            }
            Confetti(Modifier.fillMaxSize())
        }
    }
}

private class Piece(
    val x: Float,
    val vx: Float,
    val vy: Float,
    val color: Color,
    val size: Float,
    val spin: Float,
    val kind: Int,
)

@Composable
fun Confetti(modifier: Modifier = Modifier) {
    val colors = listOf(
        AppColors.Primary, AppColors.Light, AppColors.Accent, Color(0xFFA2D2FF),
        Color(0xFFFFE066), Color(0xFF8CE99A), Color.White,
    )
    val pieces = remember {
        List(110) {
            Piece(
                x = 0.5f + (Random.nextFloat() - 0.5f) * 0.2f,
                vx = (Random.nextFloat() - 0.5f) * 1.3f,
                vy = -0.6f - Random.nextFloat() * 0.8f,
                color = colors.random(),
                size = 6f + Random.nextFloat() * 7f,
                spin = (Random.nextFloat() - 0.5f) * 1440f,
                kind = Random.nextInt(3),
            )
        }
    }
    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) { progress.animateTo(1f, tween(3200, easing = LinearEasing)) }

    Canvas(modifier) {
        val p = progress.value
        if (p >= 1f) return@Canvas
        val t = p * 2f
        val alpha = if (p > 0.7f) (1f - p) / 0.3f else 1f
        pieces.forEach { piece ->
            val pos = Offset(
                (piece.x + piece.vx * t) * size.width,
                (0.4f + piece.vy * t + 0.9f * t * t) * size.height,
            )
            val s = piece.size.dp.toPx()
            val color = piece.color.copy(alpha = alpha)
            rotate(piece.spin * t, pos) {
                when (piece.kind) {
                    0 -> drawCircle(color, s / 2f, pos)
                    1 -> drawRect(color, pos - Offset(s / 2f, s / 4f), Size(s, s / 2f))
                    else -> drawPath(heartPath(pos, s * 0.7f), color)
                }
            }
        }
    }
}

