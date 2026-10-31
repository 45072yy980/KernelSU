package me.diksu.ultra.foxsu.ui.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.dp
import me.diksu.ultra.foxsu.data.settings.CustomizationStore

/** True when the active scheme is the night one (works for forced dark / AMOLED too). */
@Composable
@ReadOnlyComposable
fun isNightScheme(): Boolean = MaterialTheme.colorScheme.surface.luminance() < 0.5f

/** True when a wallpaper is painted behind the UI (global wallpaper or glass mode). */
@Composable
@ReadOnlyComposable
fun hasWallpaperBackdrop(): Boolean {
    val custom = CustomizationStore.current
    val global = custom.wallpaperUri != null && custom.wallpaperEnabled
    return global || custom.cardGlass
}

/**
 * Card surface with the user card color (or the theme fallback) and opacity applied.
 * Custom colours are a light-mode option: at night the card always uses the dark surface so
 * white cards never glare on a black canvas.
 */
@Composable
@ReadOnlyComposable
fun rememberCardSurface(fallback: Color): Color {
    val custom = CustomizationStore.current
    val useCustom = custom.cardColor != 0 && !isNightScheme()
    if (useCustom) return Color(custom.cardColor).copy(alpha = custom.cardAlpha)
    // 玻璃薄片自带透明度，只按用户滑杆等比缩放，不能直接被覆盖成不透明
    return if (custom.cardGlass) {
        fallback.copy(alpha = fallback.alpha * custom.cardAlpha)
    } else {
        fallback.copy(alpha = custom.cardAlpha)
    }
}

/** Text color that stays readable on a custom card color. */
@Composable
@ReadOnlyComposable
fun rememberCardContentColor(fallback: Color): Color {
    val custom = CustomizationStore.current
    if (custom.cardColor == 0 || isNightScheme()) return fallback
    return if (Color(custom.cardColor).luminance() > 0.55f) Color(0xFF141A14) else Color.White
}

/** Secondary (supporting) text on a card surface. */
@Composable
@ReadOnlyComposable
fun rememberCardSupportingColor(): Color = LocalContentColor.current.copy(alpha = supportingTextAlpha())

/**
 * Card colour for the ColorUI style. In dark mode the card has to sit *above* the canvas, so we
 * use a higher container tone instead of the lowest one (which is darker than the background and
 * made text look muddy). The surface is translucent so the wallpaper shows through, matching
 * the floating bottom bar.
 */
@Composable
@ReadOnlyComposable
fun freshCardColor(): Color {
    val custom = CustomizationStore.current
    // 玻璃卡面：极淡的白色薄片，靠描边和高光撑出层次（参考图二的透明卡片）
    if (custom.cardGlass) {
        // 参考图风格：深色磨砂玻璃板 + 白字，任何壁纸上都稳定可读
        if (custom.cardColor != 0) {
            return Color(custom.cardColor).copy(alpha = custom.cardAlpha.coerceAtLeast(0.6f))
        }
        return Color(0xFF101014).copy(alpha = 0.46f)
    }
    // 有壁纸/玻璃背景时：卡片改成半透明磨砂板（不再是不透明白卡），
    // 让背景透上来；浅色下用极淡的黑雾代替纯白，避免大片死白压住磨砂质感
    if (hasWallpaperBackdrop()) {
        val base = if (isSystemInDarkTheme()) {
            MaterialTheme.colorScheme.surfaceContainerHigh
        } else {
            Color(0xFF20242A)
        }
        val baseAlpha = if (isSystemInDarkTheme()) 0.58f else 0.34f
        return base.copy(alpha = baseAlpha)
    }
    val base = if (isSystemInDarkTheme()) {
        MaterialTheme.colorScheme.surfaceContainerHigh
    } else {
        MaterialTheme.colorScheme.surfaceContainerLowest
    }
    return base.copy(alpha = 0.84f)
}

/** Hairline that lifts a card off the canvas; only dark mode needs it (shadows read as noise). */
@Composable
@ReadOnlyComposable
fun freshCardBorder(): BorderStroke? {
    val custom = CustomizationStore.current
    if (custom.cardGlass) {
        // 白卡才用浅描边；磨砂板统一用一层薄白高光勾边
        return BorderStroke(1.dp, Color.White.copy(alpha = 0.18f))
    }
    // 半透明磨砂卡：白描边 + 顶部高光撑出玻璃层次
    if (hasWallpaperBackdrop()) {
        return BorderStroke(1.dp, Color.White.copy(alpha = 0.20f))
    }
    return if (isSystemInDarkTheme()) {
        BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
    } else {
        // 白底 + 白卡：用一条极淡的描边勾出边界，代替阴影
        BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.055f))
    }
}

/**
 * 全局文字明暗：先看用户自定义的卡片颜色（卡片白 → 深字），
 * 否则交给玻璃/夜间逻辑判断。
 */
@Composable
@ReadOnlyComposable
fun cardNeedsLightText(): Boolean {
    val custom = CustomizationStore.current
    if (custom.cardColor != 0 && custom.cardAlpha >= 0.55f) {
        return Color(custom.cardColor).luminance() < 0.5f
    }
    if (custom.cardGlass) return glassNeedsLightText()
    // 铺了壁纸时的半透明磨砂卡：底色是深雾，文字统一转白
    if (hasWallpaperBackdrop()) return true
    return isNightScheme()
}

/** 顶栏底色：玻璃模式下透明，让壁纸透上来。 */
@Composable
@ReadOnlyComposable
fun freshTopBarColor(): Color =
    if (CustomizationStore.current.cardGlass) Color.Transparent else MaterialTheme.colorScheme.surface

/** 卡片内容的文字色：跟着卡片本身的明暗，白卡用墨色、深卡用白色。 */
@Composable
@ReadOnlyComposable
fun cardContentColor(): Color = if (cardNeedsLightText()) Color.White else Color(0xFF1A1D1A)

/**
 * 页面画布（标题栏、底栏）的磨砂底色：
 * 有壁纸时返回带透明度的雾面，透出背景又保证白字可读；没壁纸时交回主题色。
 */
@Composable
@ReadOnlyComposable
fun frostedChromeColor(fallback: Color): Color {
    if (!hasWallpaperBackdrop()) return fallback
    val dark = isNightScheme()
    return if (dark) Color(0xFF101014).copy(alpha = 0.42f) else Color(0xFF20242A).copy(alpha = 0.40f)
}

/** Supporting text on a card: a bit brighter in dark mode so it stays legible. */
@Composable
@ReadOnlyComposable
fun supportingTextAlpha(): Float = if (isSystemInDarkTheme()) 0.82f else 0.65f


/**
 * 磨砂玻璃的高光层：顶部一层亮白、向下淡出，模拟真实玻璃的反射。
 * 放在卡片背景之上、内容之下（作为卡内 Box 的第一个子节点）。
 */
@Composable
fun GlassSheen(modifier: Modifier = Modifier) {
    val custom = CustomizationStore.current
    // 磨砂卡面（玻璃模式 / 铺了壁纸时的半透明卡）：顶部一层薄白高光，向下淡出
    if (!custom.cardGlass && !hasWallpaperBackdrop()) return
    Box(
        modifier = modifier.background(
            Brush.verticalGradient(
                0f to Color.White.copy(alpha = 0.13f),
                0.35f to Color.White.copy(alpha = 0.04f),
                1f to Color.White.copy(alpha = 0f),
            )
        )
    )
}
