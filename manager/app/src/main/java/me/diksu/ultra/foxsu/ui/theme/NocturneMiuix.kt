package me.diksu.ultra.foxsu.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.theme.Colors
import top.yukonga.miuix.kmp.theme.darkColorScheme
import top.yukonga.miuix.kmp.theme.lightColorScheme

/**
 * FoxSu Nocturne —— Miuix 界面的整体换肤。
 *
 * 依据资料库设计稿《FoxSu Nocturne UI · 开发交付文档》与 nocturne-tokens：
 * 暗夜（默认）：OLED 纯黑底 #07080B + violet #8B7BFF 主强调 + mint #4DE2C5 次强调
 * 亮色（Daylight）：#F4F5FA 底 + violet #6A57F5 + mint #0FA98B
 *
 * 几何/动效完全复用 Miuix 现有结构，仅替换色彩 token（对应设计稿 §07 亮暗切换机制）。
 */

// ---- 暗夜 Nocturne ----
private val NcBg = Color(0xFF07080B)            // OLED 页面底
private val NcSurface1 = Color(0xFF101218)      // 分组卡片
private val NcSurface2 = Color(0xFF161922)      // 次级面板
private val NcSurface3 = Color(0xFF1C2030)      // 更高容器
private val NcFill = Color(0x0DFFFFFF)          // 白 5% 图标底
private val NcLine = Color(0x12FFFFFF)          // 发丝描边
private val NcViolet = Color(0xFF8B7BFF)        // 主强调
private val NcVioletDeep = Color(0xFF6A57F5)    // 渐变深端 / 徽标
private val NcVioletText = Color(0xFFB0A4FF)    // violet 亮文字
private val NcMint = Color(0xFF4DE2C5)          // 次强调 / 成功
private val NcLime = Color(0xFFC9F45C)          // 主按钮 / 状态点
private val NcRed = Color(0xFFFF6B5E)           // 危险
private val NcAmber = Color(0xFFFFB35C)         // 警告
private val NcText1 = Color(0xFFF3F4F8)         // 主标题
private val NcText2 = Color(0xFF9BA0B2)         // 说明
private val NcText3 = Color(0xFF5D6376)         // 注释 / 眉题

// ---- 亮色 Daylight ----
private val DlBg = Color(0xFFF4F5FA)
private val DlSurface1 = Color(0xFFFFFFFF)
private val DlSurface2 = Color(0xFFF7F8FC)
private val DlSurface3 = Color(0xFFEFF1F7)
private val DlLine = Color(0x140F142D)          // #0F142D 8%
private val DlViolet = Color(0xFF6A57F5)
private val DlVioletContainer = Color(0xFFE6E2FF)
private val DlMint = Color(0xFF0FA98B)
private val DlRed = Color(0xFFE8503F)
private val DlAmber = Color(0xFFDE9A21)
private val DlText1 = Color(0xFF12141B)
private val DlText2 = Color(0xFF5D6376)
private val DlText3 = Color(0xFF9AA0B0)

/** Nocturne 暗夜色板（Miuix Colors）。 */
fun nocturneDarkColors(): Colors = darkColorScheme().copy(
    primary = NcViolet,
    onPrimary = Color(0xFF0B0A1A),
    primaryVariant = NcVioletDeep,
    onPrimaryVariant = Color(0xFF0B0A1A),
    primaryContainer = Color(0xFF2A2452),
    onPrimaryContainer = NcVioletText,
    disabledPrimary = NcViolet.copy(alpha = 0.38f),
    disabledOnPrimary = Color(0x660B0A1A),
    secondary = NcMint,
    onSecondary = Color(0xFF04211B),
    secondaryVariant = Color(0xFF35B79B),
    onSecondaryVariant = Color(0xFF04211B),
    secondaryContainer = Color(0xFF12332E),
    onSecondaryContainer = NcMint,
    tertiaryContainer = Color(0xFF2E3A14),
    onTertiaryContainer = NcLime,
    tertiaryContainerVariant = Color(0xFF24300E),
    background = NcBg,
    onBackground = NcText1,
    onBackgroundVariant = NcText2,
    surface = NcSurface1,
    onSurface = NcText1,
    surfaceVariant = NcSurface2,
    onSurfaceSecondary = NcText2,
    onSurfaceVariantSummary = NcText2,
    onSurfaceVariantActions = NcVioletText,
    disabledOnSurface = Color(0x61F3F4F8),
    surfaceContainer = NcSurface2,
    onSurfaceContainer = NcText1,
    onSurfaceContainerVariant = NcText2,
    surfaceContainerHigh = NcSurface3,
    onSurfaceContainerHigh = NcText1,
    surfaceContainerHighest = Color(0xFF232838),
    onSurfaceContainerHighest = NcText1,
    error = NcRed,
    onError = Color(0xFF2B0703),
    errorContainer = Color(0xFF3A130E),
    onErrorContainer = Color(0xFFFFB3A9),
    outline = NcLine,
    dividerLine = NcLine,
    sliderKeyPoint = NcViolet,
    sliderKeyPointForeground = NcMint,
    sliderBackground = NcFill,
)

/** Nocturne 亮色（Daylight）色板（Miuix Colors）。 */
fun nocturneLightColors(): Colors = lightColorScheme().copy(
    primary = DlViolet,
    onPrimary = Color.White,
    primaryVariant = DlViolet,
    onPrimaryVariant = Color.White,
    primaryContainer = DlVioletContainer,
    onPrimaryContainer = DlViolet,
    secondary = DlMint,
    onSecondary = Color.White,
    secondaryVariant = DlMint,
    onSecondaryVariant = Color.White,
    secondaryContainer = Color(0xFFDCF3EC),
    onSecondaryContainer = DlMint,
    tertiaryContainer = Color(0xFFEFF7D2),
    onTertiaryContainer = Color(0xFF55660F),
    tertiaryContainerVariant = Color(0xFFE6F0C2),
    background = DlBg,
    onBackground = DlText1,
    onBackgroundVariant = DlText2,
    surface = DlSurface1,
    onSurface = DlText1,
    surfaceVariant = DlSurface2,
    onSurfaceSecondary = DlText2,
    onSurfaceVariantSummary = DlText2,
    onSurfaceVariantActions = DlViolet,
    disabledOnSurface = Color(0x9912141B),
    surfaceContainer = DlSurface2,
    onSurfaceContainer = DlText1,
    onSurfaceContainerVariant = DlText2,
    surfaceContainerHigh = DlSurface3,
    onSurfaceContainerHigh = DlText1,
    surfaceContainerHighest = Color(0xFFE7E9F2),
    onSurfaceContainerHighest = DlText1,
    error = DlRed,
    onError = Color.White,
    errorContainer = Color(0xFFFBDDD8),
    onErrorContainer = Color(0xFF8A2318),
    outline = DlLine,
    dividerLine = DlLine,
    sliderKeyPoint = DlViolet,
    sliderKeyPointForeground = DlMint,
    sliderBackground = DlSurface3,
)

/** 设计稿颜色速查（供其他 Nocturne 部件复用）。 */
object NocturneTokens {
    val violet = NcViolet
    val violetDeep = NcVioletDeep
    val violetText = NcVioletText
    val mint = NcMint
    val lime = NcLime
    val red = NcRed
    val amber = NcAmber
    val bgDark = NcBg
    val surface1Dark = NcSurface1
    val surface2Dark = NcSurface2
    val bgLight = DlBg
    val surface1Light = DlSurface1
    val surface2Light = DlSurface2
}

/** Nocturne 双径向光晕背景（设计稿 §01：violet 13% 左上 + mint 6% 右上；亮色 16%/12%）。 */
@Composable
fun NocturneGlowBackdrop(modifier: Modifier = Modifier) {
    val dark = isInDarkTheme()
    val vioAlpha = if (dark) 0.13f else 0.16f
    val mintAlpha = if (dark) 0.06f else 0.12f
    Box(modifier) {
        Box(
            modifier = Modifier
                .size(width = 420.dp, height = 260.dp)
                .offset(x = (-84).dp, y = (-40).dp)
                .background(
                    Brush.radialGradient(
                        listOf(NcViolet.copy(alpha = vioAlpha), Color.Transparent)
                    )
                ),
        )
        Box(
            modifier = Modifier
                .size(width = 320.dp, height = 220.dp)
                .offset(x = 96.dp, y = (-36).dp)
                .background(
                    Brush.radialGradient(
                        listOf(NcMint.copy(alpha = mintAlpha), Color.Transparent)
                    )
                ),
        )
    }
}
