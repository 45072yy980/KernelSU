package me.diksu.ultra.foxsu.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush.Companion.horizontalGradient
import me.diksu.ultra.foxsu.ui.component.isNightScheme

/**
 * FoxSu Glass 设计稿 token（严格对应 foxsu-glass-ui.html）
 *
 * 暗色默认：
 *   --bg #0d0d14  --panel #16161f  --panel2 #1c1c28  --line rgba(255,255,255,.07)
 *   --ink #f0eef6  --ink2 #c9c6d6  --sub #8e8b9c  --faint #55525f
 *   霓虹：flame #ff7a59 amber #ffb35c lemon #ffe45c lime #6ef08a
 *         cyan #5ce1e6 blue #7aa2ff violet #c77dff pink #ff7ac8 mint #6ef0c0
 *   面板（.hero/.ncard/.wide/.urow/.mod/.srow）rgba(255,255,255,.03) + 1px var(--line)
 *   底栏 .dock rgba(16,16,24,.85) + 1px rgba(255,255,255,.08)
 *
 * 亮色覆盖（液态玻璃）：
 *   --bg #eef0f7  --panel/panel2 rgba(255,255,255,.55)  --line rgba(255,255,255,.7)
 *   --ink #26262e  --ink2 #55525f  --sub #7a7788  --faint #a3a0b2
 *   霓虹：flame #d94f2e amber #c97a10 lemon #a8860e lime #2e9a50
 *         cyan #0e8a99 blue #4d6fd8 violet #8a3fd0 pink #d84f9a mint #1e9a78
 *   面板 rgba(255,255,255,.55) + 1px rgba(255,255,255,.8) + 0 8 24 rgba(90,85,120,.12) + inset 0 1 0 rgba(255,255,255,.9)
 *   底栏 .dock rgba(255,255,255,.7) + 1px rgba(255,255,255,.9)
 */
data class GlassTokens(
    val isDark: Boolean,
    val bg: Color,
    val panel: Color,
    val panel2: Color,
    val line: Color,
    val ink: Color,
    val ink2: Color,
    val sub: Color,
    val faint: Color,
    val flame: Color,
    val amber: Color,
    val lemon: Color,
    val lime: Color,
    val cyan: Color,
    val blue: Color,
    val violet: Color,
    val pink: Color,
    val mint: Color,
) {
    // ---- 玻璃面板 ----
    val glassPanel: Color get() = if (isDark) Color(0x08FFFFFF) else Color(0x9EFFFFFF)
    val glassPanelStrong: Color get() = if (isDark) Color(0x0FFFFFFF) else Color(0xCCFFFFFF)
    val glassBorder: Color get() = if (isDark) Color(0x12FFFFFF) else Color(0xCCFFFFFF)
    val glassShadow: Color get() = if (isDark) Color(0xB3000000) else Color(0x1F5A5578)

    // ---- .chip ----
    val chipBg: Color get() = if (isDark) Color(0x0DFFFFFF) else Color(0x99FFFFFF)
    val chipBorder: Color get() = if (isDark) Color(0x12FFFFFF) else Color(0xD9FFFFFF)

    // ---- .tbtn（顶栏按钮）----
    val tbtnBg: Color get() = if (isDark) Color(0x0DFFFFFF) else Color(0x99FFFFFF)
    val tbtnBorder: Color get() = if (isDark) Color(0x12FFFFFF) else Color(0xD9FFFFFF)
    val tbtnIcon: Color get() = if (isDark) ink2 else Color(0xFF55525F)

    // ---- .ava（头像）----
    val avaBg: Color get() = if (isDark) Color(0xFF050508) else Color.White

    // ---- .hero .seal ----
    val sealBg: Color get() = if (isDark) Color(0xFF0B0B12) else Color(0xBFFFFFFF)

    // ---- .dock（底栏：亮色白玻璃 / 暗色深玻璃）----
    val dockBg: Color get() = if (isDark) Color(0xD9101018) else Color(0xB3FFFFFF)
    val dockBorder: Color get() = if (isDark) Color(0x14FFFFFF) else Color(0xE6FFFFFF)
    val dockShadow: Color get() = if (isDark) Color(0x99000000) else Color(0x405A5578)
    val dockInk: Color get() = if (isDark) Color.White else ink
    val dockSub: Color get() = if (isDark) Color(0xFF8E8B9C) else Color(0xFF7A7788)
    val dockIconOff: Color get() = if (isDark) Color(0xFF6A677A) else Color(0xFFA3A0B2)
    val dockSelectedBg: Color get() = if (isDark) Color(0x0FFFFFFF) else Color(0xE6FFFFFF)

    // ---- 渐变 ----
    val heroGrad: Brush
        get() = if (isDark) {
            horizontalGradient(listOf(Color(0xFFFF7A59), Color(0xFFC77DFF), Color(0xFF5CE1E6)))
        } else {
            horizontalGradient(listOf(Color(0xFFD94F2E), Color(0xFF8A3FD0), Color(0xFF0E8A99)))
        }
    val glineGrad: Brush get() = horizontalGradient(listOf(flame, violet))
    val dockGrad: Brush get() = horizontalGradient(listOf(flame, violet))
    val switchGrad: Brush get() = horizontalGradient(listOf(flame, violet))
    val togOff: Color get() = if (isDark) Color(0xFF2C2C38) else Color(0xFFD9D6E6)

    /** ROOT 标签：单色，不再跟随应用霓虹渐变 */
    val rootTagBg: Color get() = if (isDark) Color(0xFF3A3550) else Color(0xFFE4DFF5)
    val rootTagInk: Color get() = if (isDark) Color(0xFFEDE9FF) else Color(0xFF4B3E7A)
    val togThumbOff: Color get() = if (isDark) Color(0xFF8E8B9C) else Color.White

    /** .screen::before 三层独立径向光斑（设计稿 a1–a4 变体） */
    fun auraLayers(seed: Int): List<Brush> {
        val colors = if (isDark) {
            when (seed % 4) {
                0 -> listOf(Color(0xFF5C2B1E), Color(0xFF2B1E5C), Color(0xFF1E4230))
                1 -> listOf(Color(0xFF1E3A5C), Color(0xFF5C1E47), Color(0xFF1E4242))
                2 -> listOf(Color(0xFF1E4242), Color(0xFF3A2B5C), Color(0xFF5C2B1E))
                else -> listOf(Color(0xFF5C2B47), Color(0xFF42421E), Color(0xFF2B1E5C))
            }
        } else {
            when (seed % 4) {
                0 -> listOf(Color(0xFFFFD3C2), Color(0xFFDDD0FF), Color(0xFFC2ECD4))
                1 -> listOf(Color(0xFFC9DEFC), Color(0xFFFCC9E3), Color(0xFFC2F0EC))
                2 -> listOf(Color(0xFFC2F0EC), Color(0xFFDED0FC), Color(0xFFFFD9C4))
                else -> listOf(Color(0xFFFBD0E3), Color(0xFFFDF0C4), Color(0xFFD4D2FC))
            }
        }
        val centers = listOf(
            androidx.compose.ui.geometry.Offset(0.15f, 0.08f),
            androidx.compose.ui.geometry.Offset(0.90f, 0.30f),
            androidx.compose.ui.geometry.Offset(0.40f, 0.95f),
        )
        // 设计稿亮色 opacity .75 / 暗色 .5，由调用方统一乘到图层 alpha 上
        return colors.mapIndexed { i, c ->
            Brush.radialGradient(
                colors = listOf(c, Color.Transparent),
                center = centers[i],
                radius = 720f,
            )
        }
    }

    /** 兼容旧调用 */
    fun auraBrush(seed: Int): Brush = auraLayers(seed).first()
}

@Composable
fun glassTokens(): GlassTokens {
    val dark = isNightScheme()
    // 全局强调色（定制 → 全局强调色）：霓虹盘里的 violet / pink 两个品牌位跟随主色，
    // flame / cyan / lime 这些纯装饰色保持不变，玻璃的"霓虹感"不会被冲掉。
    val ac = currentAccent
    val base = if (dark) {
        GlassTokens(
            isDark = true,
            bg = Color(0xFF0D0D14), panel = Color(0xFF16161F), panel2 = Color(0xFF1C1C28),
            line = Color(0x12FFFFFF),
            ink = Color(0xFFF0EEF6), ink2 = Color(0xFFC9C6D6), sub = Color(0xFF8E8B9C), faint = Color(0xFF55525F),
            flame = Color(0xFFFF7A59), amber = Color(0xFFFFB35C), lemon = Color(0xFFFFE45C), lime = Color(0xFF6EF08A),
            cyan = Color(0xFF5CE1E6), blue = Color(0xFF7AA2FF), violet = Color(0xFFC77DFF), pink = Color(0xFFFF7AC8),
            mint = Color(0xFF6EF0C0),
        )
    } else {
        GlassTokens(
            isDark = false,
            bg = Color(0xFFEEF0F7), panel = Color(0x8CFFFFFF), panel2 = Color(0x8CFFFFFF),
            line = Color(0xB3FFFFFF),
            ink = Color(0xFF26262E), ink2 = Color(0xFF55525F), sub = Color(0xFF7A7788), faint = Color(0xFFA3A0B2),
            flame = Color(0xFFD94F2E), amber = Color(0xFFC97A10), lemon = Color(0xFFA8860E), lime = Color(0xFF2E9A50),
            cyan = Color(0xFF0E8A99), blue = Color(0xFF4D6FD8), violet = Color(0xFF8A3FD0), pink = Color(0xFFD84F9A),
            mint = Color(0xFF1E9A78),
        )
    }
    return base.copy(violet = ac.primary, pink = ac.secondary)
}
