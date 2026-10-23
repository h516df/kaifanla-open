package com.my.firstapp

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

enum class ThemeStyle { Cream, Midnight, Mint }

/** 一套主题皮肤的所有颜色。主题 ID 保持稳定以兼容已有设置。 */
data class Palette(
    val id: String,
    val name: String,
    val emoji: String,
    val primary: Color,
    val deep: Color,
    val light: Color,
    val accent: Color,
    val text: Color,
    val subText: Color,
    val line: Color,
    val bgTop: Color,
    val bgBottom: Color,
    val slices: List<Color>,
    val ring: List<Color>,
    val surface: Color = Color(0xFFFFFCF7),
    val cardRadius: Dp = 24.dp,
    val style: ThemeStyle = ThemeStyle.Cream,
)

val Palettes = listOf(
    Palette(
        id = "strawberry", name = "奶油食堂", emoji = "🍓",
        primary = Color(0xFFA74D64), deep = Color(0xFF874A59), light = Color(0xFFF3E3E6), accent = Color(0xFFAA8980),
        text = Color(0xFF352D2C), subText = Color(0xFF786665), line = Color(0xFFEADCD6),
        bgTop = Color(0xFFFBF8F3), bgBottom = Color(0xFFF6F0E8),
        slices = listOf(
            Color(0xFFFFC8DD), Color(0xFFBDE0FE), Color(0xFFFFE5A8), Color(0xFFCDB4DB),
            Color(0xFFC7F0D8), Color(0xFFFFAFCC), Color(0xFFA2D2FF), Color(0xFFFFD6C2),
        ),
        ring = listOf(Color(0xFFBA8C94), Color(0xFFDDC2C4), Color(0xFFD3BEB2), Color(0xFFBA8C94)),
    ),
    Palette(
        id = "lavender", name = "午夜小馆", emoji = "🌙",
        primary = Color(0xFFE0B985), deep = Color(0xFFB78753), light = Color(0xFF303A49), accent = Color(0xFFA3BBCB),
        text = Color(0xFFF5EBDD), subText = Color(0xFFB6BDCA), line = Color(0xFF4E5060),
        bgTop = Color(0xFF101722), bgBottom = Color(0xFF1B2635),
        surface = Color(0xFF222E3E), cardRadius = 16.dp, style = ThemeStyle.Midnight,
        slices = listOf(
            Color(0xFFB58B60), Color(0xFF6D8797), Color(0xFF9FAD9C), Color(0xFFD0B38B),
            Color(0xFF8B9EBA), Color(0xFFA18A7C), Color(0xFF9EA58B), Color(0xFFB7A9BF),
        ),
        ring = listOf(Color(0xFFB78753), Color(0xFFE0B985), Color(0xFFA3BBCB), Color(0xFFB78753)),
    ),
    Palette(
        id = "peach", name = "蜜桃食堂", emoji = "🍑",
        primary = Color(0xFFBA795E), deep = Color(0xFF92543E), light = Color(0xFFFFE8DE), accent = Color(0xFFB49B65),
        text = Color(0xFF5C4033), subText = Color(0xFF80685B), line = Color(0xFFF9D9CC),
        bgTop = Color(0xFFFFF9F4), bgBottom = Color(0xFFFFE9DC),
        slices = listOf(
            Color(0xFFFFD3C2), Color(0xFFFFE7B3), Color(0xFFFFC9D6), Color(0xFFD7F0C8),
            Color(0xFFFFDFC9), Color(0xFFC9E6FF), Color(0xFFFFF0C2), Color(0xFFF8C8E0),
        ),
        ring = listOf(Color(0xFFFFA07A), Color(0xFFFFD0B0), Color(0xFFFFC1D3), Color(0xFFFFA07A)),
    ),
    Palette(
        id = "mint", name = "薄荷街角", emoji = "🌿",
        primary = Color(0xFF226B55), deep = Color(0xFF164D3F), light = Color(0xFFD6EADF), accent = Color(0xFFB56535),
        text = Color(0xFF17392F), subText = Color(0xFF526B60), line = Color(0xFFA9C7B7),
        bgTop = Color(0xFFEAF3E9), bgBottom = Color(0xFFDCEBDD),
        surface = Color(0xFFF5F8EE), cardRadius = 4.dp, style = ThemeStyle.Mint,
        slices = listOf(
            Color(0xFFC7F2E4), Color(0xFFCDE9FF), Color(0xFFFFF3C4), Color(0xFFE2D9FF),
            Color(0xFFFFD9E4), Color(0xFFD5F5D0), Color(0xFFC2ECF5), Color(0xFFFFE6CC),
        ),
        ring = listOf(Color(0xFF226B55), Color(0xFFA9C7B7), Color(0xFFB56535), Color(0xFF226B55)),
    ),
)

/** 当前主题的颜色。切换主题时整个界面会跟着变。 */
object AppColors {
    var palette by mutableStateOf(Palettes.first())

    val Primary get() = palette.primary
    val Deep get() = palette.deep
    val Light get() = palette.light
    val Accent get() = palette.accent
    val Text get() = palette.text
    val SubText get() = palette.subText
    val Line get() = palette.line
    val Slices get() = palette.slices
    val Ring get() = palette.ring
    val Surface get() = palette.surface
    val CardRadius get() = palette.cardRadius
    val TitleFont get() = when (palette.style) {
        ThemeStyle.Cream -> LoadedCuteFont
        ThemeStyle.Midnight -> FontFamily.Serif
        ThemeStyle.Mint -> FontFamily.SansSerif
    }
    val OnPrimary get() = if (palette.style == ThemeStyle.Midnight) Color(0xFF182331) else Color.White
    val ButtonRadius get() = if (palette.style == ThemeStyle.Mint) 4.dp else 28.dp

    val background get() = Brush.verticalGradient(listOf(palette.bgTop, palette.bgBottom))
    val buttonGradient
        get() = Brush.horizontalGradient(listOf(palette.deep, palette.primary))
}

/** 保留旧的字体入口，标题与按钮会随主题切换；正文仍用系统字体。 */
private val LoadedCuteFont = FontFamily(Font(R.font.cute))
val CuteFont: FontFamily get() = AppColors.TitleFont

@Composable
fun CuteTheme(content: @Composable () -> Unit) {
    val colors = if (AppColors.palette.style == ThemeStyle.Midnight) darkColorScheme() else lightColorScheme()
    MaterialTheme(
        colorScheme = colors.copy(
            primary = AppColors.Primary,
            onPrimary = AppColors.OnPrimary,
            primaryContainer = AppColors.Light,
            onPrimaryContainer = AppColors.Text,
            secondary = AppColors.Accent,
            onSecondary = AppColors.OnPrimary,
            secondaryContainer = AppColors.Light,
            onSecondaryContainer = AppColors.Text,
            background = AppColors.palette.bgTop,
            onBackground = AppColors.Text,
            surface = AppColors.Surface,
            onSurface = AppColors.Text,
            surfaceVariant = AppColors.Light,
            onSurfaceVariant = AppColors.SubText,
            outline = AppColors.Line,
            outlineVariant = AppColors.Line,
            surfaceTint = AppColors.Primary,
            surfaceContainer = AppColors.Surface,
            surfaceContainerLow = AppColors.Surface,
            surfaceContainerLowest = AppColors.palette.bgTop,
            surfaceContainerHigh = AppColors.Surface,
            surfaceContainerHighest = AppColors.Light,
        ),
        shapes = Shapes(
            small = RoundedCornerShape(if (AppColors.palette.style == ThemeStyle.Mint) 2.dp else 12.dp),
            medium = RoundedCornerShape(AppColors.CardRadius),
            large = RoundedCornerShape(AppColors.CardRadius),
        ),
        content = content,
    )
}

/** 每个页面统一的可滚动内容区，宽屏时居中不拉太宽。 */
@Composable
fun ScreenColumn(content: @Composable ColumnScope.() -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(
            modifier = Modifier
                .widthIn(max = 520.dp)
                .fillMaxWidth()
                .fillMaxHeight()
                .verticalScroll(rememberScrollState())
                .padding(start = 20.dp, end = 20.dp, top = 24.dp, bottom = 40.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(18.dp),
            content = content,
        )
    }
}

@Composable
fun PageTitle(title: String, subtitle: String) {
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.Start) {
        Text(title, fontSize = 30.sp, fontFamily = CuteFont, color = AppColors.Text)
        Text(subtitle, fontSize = 14.sp, color = AppColors.SubText, modifier = Modifier.padding(top = 6.dp))
    }
}

@Composable
fun SectionLabel(text: String) {
    Text(
        text,
        fontSize = 15.sp,
        fontWeight = FontWeight.Bold,
        color = AppColors.SubText,
        modifier = Modifier.fillMaxWidth().padding(start = 4.dp, top = 4.dp),
    )
}

@Composable
fun CuteCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = RoundedCornerShape(AppColors.CardRadius)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .border(1.dp, AppColors.Line.copy(alpha = 0.7f), shape)
            .clip(shape)
            .background(AppColors.Surface)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(20.dp),
        content = content,
    )
}

@Composable
fun GradientButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    Box(
        modifier = modifier
            .height(52.dp)
            .shadow(if (enabled && AppColors.palette.style != ThemeStyle.Mint) 8.dp else 0.dp, RoundedCornerShape(AppColors.ButtonRadius), spotColor = AppColors.Primary)
            .clip(RoundedCornerShape(AppColors.ButtonRadius))
            .background(if (enabled) AppColors.buttonGradient else SolidColor(AppColors.Light))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 28.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = if (enabled) AppColors.OnPrimary else AppColors.SubText, fontSize = 19.sp, fontFamily = CuteFont)
    }
}

/** 浅色的次要按钮 */
@Composable
fun SoftButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .height(48.dp)
            .clip(RoundedCornerShape(AppColors.ButtonRadius))
            .background(AppColors.Light)
            .clickable(onClick = onClick)
            .padding(horizontal = 24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = AppColors.Primary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
    }
}

/** 小胶囊标签，比如转盘规则 */
@Composable
fun Pill(text: String, active: Boolean = false, onClick: (() -> Unit)? = null) {
    Box(
        Modifier
            .clip(RoundedCornerShape(AppColors.ButtonRadius))
            .background(if (active) AppColors.Light else AppColors.palette.bgTop)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 14.dp, vertical = 7.dp),
    ) {
        Text(text, fontSize = 14.sp, color = if (active) AppColors.Primary else AppColors.SubText, fontWeight = FontWeight.Medium)
    }
}

@Composable
fun EmojiBadge(emoji: String, size: Dp = 48.dp, color: Color = AppColors.Light) {
    Box(
        Modifier.size(size).clip(CircleShape).background(color),
        contentAlignment = Alignment.Center,
    ) {
        Text(emoji, fontSize = (size.value * 0.5f).sp)
    }
}

@Composable
fun PriceTag(price: Double?, groupPrice: Double?, big: Boolean = false) {
    val main = if (big) 26.sp else 15.sp
    val small = if (big) 15.sp else 12.sp
    Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        when {
            groupPrice != null -> {
                Text("团购 ${groupPrice.yuan()}", color = AppColors.Primary, fontWeight = FontWeight.Bold, fontSize = main)
                if (price != null) {
                    Text(price.yuan(), color = AppColors.SubText, fontSize = small, textDecoration = TextDecoration.LineThrough)
                }
            }
            price != null -> Text(price.yuan(), color = AppColors.Text, fontWeight = FontWeight.Bold, fontSize = main)
            else -> Text("还没填价格", color = AppColors.SubText, fontSize = small)
        }
    }
}

@Composable
fun EmptyCard(emoji: String, title: String, hint: String, action: String? = null, onAction: () -> Unit = {}) {
    CuteCard {
        Column(Modifier.fillMaxWidth().padding(vertical = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(emoji, fontSize = 48.sp)
            Text(title, fontSize = 20.sp, fontFamily = CuteFont, color = AppColors.Text, modifier = Modifier.padding(top = 8.dp))
            Text(hint, fontSize = 14.sp, color = AppColors.SubText, modifier = Modifier.padding(top = 4.dp))
            if (action != null) {
                GradientButton(action, onAction, Modifier.padding(top = 16.dp))
            }
        }
    }
}
