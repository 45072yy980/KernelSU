package me.diksu.ultra.foxsu.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.Shapes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowInsetsControllerCompat
import me.diksu.ultra.foxsu.data.settings.CustomizationStore
import me.diksu.ultra.foxsu.ui.component.isNightScheme as isNightSchemeForTheme
import me.diksu.ultra.foxsu.ui.webui.MonetColorsProvider
import me.diksu.ultra.foxsu.ui.theme.UiPalette

/**
 * "ColorUI" style.
 *
 * A card-first layout (soft tinted canvas, white rounded cards, one accent color).
 * Unlike the original fixed green palette, the scheme follows the same customization as
 * the built-in styles: key color, palette style and color spec from the theme settings,
 * falling back to the system dynamic color when no key color is chosen.
 */
object ColorUiTokens {
    val Warning = Color(0xFFFFA726)
    val Danger = Color(0xFFE5484D)
}

internal val FreshShapes = Shapes(
    extraSmall = RoundedCornerShape(10.dp),
    small = RoundedCornerShape(14.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(26.dp),
    extraLarge = RoundedCornerShape(32.dp),
)

@Composable
fun FreshKernelSUTheme(
    appSettings: AppSettings,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val systemDarkTheme = isSystemInDarkTheme()
    val darkTheme = appSettings.colorMode.isDark || (appSettings.colorMode.isSystem && systemDarkTheme)
    // 全局强调色优先：Pages / Glass / Nocturne 的 Material 色板都从这里取种子色
    val seedColor = me.diksu.ultra.foxsu.ui.theme.currentAccent.primary

    val baseScheme = rememberKernelSUColorScheme(
        seedColor = seedColor,
        isDark = darkTheme,
        isAmoled = appSettings.colorMode.isAmoled,
        paletteStyle = appSettings.paletteStyle,
        colorSpec = appSettings.colorSpec,
    )

    // 浅色：画布统一成参考图的浅紫灰（#F6F4FB），卡片用近白/马卡龙浮在上面
    // 夜间：强调色保留，中性色切到安静的灰阶。
    val colorScheme = if (!darkTheme) {
        baseScheme.copy(
            surface = UiPalette.CanvasLight,
            background = UiPalette.CanvasLight,
            surfaceBright = UiPalette.CanvasLight,
            surfaceDim = Color(0xFFE9E6F2),
            surfaceContainerLowest = Color(0xFFFBFAFE),
            surfaceContainerLow = Color(0xFFF5F4F9),
            surfaceContainer = Color(0xFFF0EFF5),
            surfaceContainerHigh = Color(0xFFEBEAF1),
            surfaceContainerHighest = Color(0xFFE5E2EE),
            onSurface = Color(0xFF1B1B1F),
            onSurfaceVariant = Color(0xFF5F5B6B),
            outlineVariant = Color(0xFFE5E2EE),
        )
    } else {
        // Pitch-black canvas + #1E1E1E cards: the same look on every screen, with pure white
        // titles and a mid-grey secondary so nothing blends into the background.
        // 设计稿 dark-mode：画布 #16161D，卡片 #1E1E28 一档档浮起，字 #ECEAF2 / #9A97AB
        baseScheme.copy(
            surface = UiPalette.CanvasDark,
            surfaceDim = Color(0xFF121218),
            background = UiPalette.CanvasDark,
            surfaceBright = Color(0xFF2A2A36),
            surfaceContainerLowest = Color(0xFF121218),
            surfaceContainerLow = Color(0xFF1A1A22),
            surfaceContainer = Color(0xFF1E1E28),
            surfaceContainerHigh = Color(0xFF24242E),
            surfaceContainerHighest = Color(0xFF2C2C38),
            onSurface = UiPalette.InkDark,
            onSurfaceVariant = UiPalette.SubDark,
            onBackground = UiPalette.InkDark,
            outline = UiPalette.SubDark,
            outlineVariant = UiPalette.FaintDark,
            inverseSurface = UiPalette.InkDark,
            inverseOnSurface = UiPalette.CanvasDark,
        )
    }

    val animatedColorScheme = colorScheme.animateAsState()

    // 玻璃卡面 + 深色壁纸：整体文字转白，避免深色字贴在照片上看不清
    // 页面级文字跟背景走：玻璃/夜间一律白字；卡片内部再按卡面颜色单独决定
    val glassLightText = CustomizationStore.current.cardGlass || isNightSchemeForTheme()
    val textScheme = if (glassLightText) {
        // 深色壁纸的玻璃模式：文字转白，同时把容器色压深，
        // 否则对话框 / 开关 / 弹出菜单还是浅灰底，白字直接看不见。
        animatedColorScheme.copy(
            onSurface = Color.White,
            onSurfaceVariant = Color.White.copy(alpha = 0.78f),
            surfaceVariant = Color.White.copy(alpha = 0.12f),
            outlineVariant = Color.White.copy(alpha = 0.24f),
            outline = Color.White.copy(alpha = 0.5f),
            surfaceContainerLowest = Color(0xFF121212),
            surfaceContainerLow = Color(0xFF161616),
            surfaceContainer = Color(0xFF1A1A1A),
            surfaceContainerHigh = Color(0xFF1F1F1F),
            surfaceContainerHighest = Color(0xFF252525),
            surfaceBright = Color(0xFF2A2A2A),
            surfaceDim = Color(0xFF0E0E0E),
            inverseSurface = Color.White,
            inverseOnSurface = Color(0xFF1B1B1B),
            surfaceTint = Color.Transparent,
        )
    } else {
        animatedColorScheme
    }

    val typography = rememberTypography(
        CustomizationStore.current.fontStyle,
        CustomizationStore.current.fontPath,
    ).let { if (glassLightText) it.withoutShadow() else it }

    MaterialExpressiveTheme(
        colorScheme = textScheme,
        motionScheme = MotionScheme.expressive(),
        // 玻璃模式不再给文字加投影：字要“正常”，靠颜色和卡面明暗保可读
        typography = typography,
        shapes = FreshShapes,
        content = {
            MonetColorsProvider.UpdateCss(animatedColorScheme)
            // 同 MaterialKernelSUTheme：Material3 不提供 LocalTextStyle，
            // 裸 Text() 会拿平台默认字，导致「定制 → 字体」在这套皮肤下完全没反应。
            CompositionLocalProvider(LocalTextStyle provides typography.defaultTextStyle) {
                content()
            }
        }
    )
}
