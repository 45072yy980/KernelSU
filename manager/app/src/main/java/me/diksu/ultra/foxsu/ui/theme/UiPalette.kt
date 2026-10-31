package me.diksu.ultra.foxsu.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.dp

/** 把强调色往白色混一点，得到暗色模式下的提亮版。 */
private fun lerpAccent(c: Color, amount: Float): Color = lerp(c, Color.White, amount)

/**
 * 参考图（foxsu-ui-refined）的设计规范：主页 / 设置 / 重启弹层共用同一套
 * 柔和马卡龙配色、圆角、留白与图标规格，保证几个页面看起来是同一个 App。
 *
 * 配色规则（与参考图逐色对照）：
 *  - 画布：极浅的灰紫 / 灰蓝，卡片浮在上面
 *  - 主卡（工作中）：淡薰衣草紫 + 薰衣草色图标
 *  - KPI 四格：薄荷绿 / 奶油黄 / 淡紫 / 淡粉，逐格不同色
 *  - 工具胶囊：浅紫、薄荷、浅黄、淡紫，图标色比底色深
 *  - 文字：标题近黑（#1B1B1F），说明文字 12~13sp 半透明
 */
object UiPalette {
    /** 画布底色：设计稿那种带一点紫灰的浅色。 */
    val CanvasLight = Color(0xFFF5F4F9)
    val CanvasDark = Color(0xFF16161D)

    // —— 主页各卡片的底色 / 内容色（浅色模式，与设计稿逐色对照）——
    /** 主状态卡：粉桃渐变（135deg #FDE4E0 → #FBD8E4）。 */
    val HeroGradientStart = Color(0xFFFDE4E0)
    val HeroGradientEnd = Color(0xFFFBD8E4)
    /** 主卡文字墨色。 */
    val HeroContent = Color(0xFF2B2B33)

    /**
     * 主卡强调色。
     *
     * 原来写死成薰衣草紫（#6C5FC7），用户在「界面风格」里换任何配色都不动，
     * 于是浅色主题里总有一块永远紫的按钮。现在直接取全局强调色，
     * 跟着定制中心的七套配色一起换。
     */
    val HeroAccent: Color
        @Composable get() = me.diksu.ultra.foxsu.ui.theme.currentAccent.primary
    /** 状态章描边玫红。 */
    val StatusRose = Color(0xFFC2567E)
    /** 兼容旧引用（作者页头像卡 / 重启弹层图标底）：淡紫。 */
    val HeroSurface = Color(0xFFE4DEF8)

    /** KPI 四格：设备型号=薄荷 / 界面风格=奶油 / 超级用户=淡紫 / 模块=玫瑰。 */
    val KpiMint = Color(0xFFDDEEDC) to Color(0xFF3E7A4C)
    val KpiCream = Color(0xFFFAEDCF) to Color(0xFF9A7A1E)
    val KpiLilac = Color(0xFFE4DEF8) to Color(0xFF6C5FC7)
    val KpiRose = Color(0xFFF7DFE9) to Color(0xFFB0508A)

    /** 工具胶囊：SELinux=薄荷 / Seccomp=灰 / 支持开发=蜜桃 / 了解 KernelSU=天蓝。 */
    val PillMint = Color(0xFFDDEEDC) to Color(0xFF3E7A4C)
    val PillGray = Color(0xFFECECF2) to Color(0xFF55525F)
    val PillPeach = Color(0xFFFBE3D6) to Color(0xFFB06A3A)
    val PillSky = Color(0xFFDDE9F7) to Color(0xFF3F6AA6)
    val PillLilac = Color(0xFFE4DEF8) to Color(0xFF6C5FC7)
    val PillSand = Color(0xFFFAEDCF) to Color(0xFF9A7A1E)

    // —— 深色模式：与设计稿 dark-mode 稿逐色对齐 ——
    // bg #16161D / ink #ECEAF2 / sub #9A97AB / faint #5A5768
    val InkDark = Color(0xFFECEAF2)
    val SubDark = Color(0xFF9A97AB)
    val FaintDark = Color(0xFF5A5768)

    val HeroSurfaceDark = Color(0xFF2A2124)
    val HeroContentDark = InkDark
    /** 暗色下把主色提亮一档，避免深底上的对比度不足。 */
    val HeroAccentDark: Color
        @Composable get() = lerpAccent(
            me.diksu.ultra.foxsu.ui.theme.currentAccent.primary, 0.34f,
        )

    val KpiMintDark = Color(0xFF1F2F26) to Color(0xFF8FD6A4)
    val KpiCreamDark = Color(0xFF332C1C) to Color(0xFFE3C26E)
    val KpiLilacDark = Color(0xFF262437) to Color(0xFFA99DF0)
    val KpiRoseDark = Color(0xFF33222B) to Color(0xFFF08AB4)

    val PillMintDark = Color(0xFF1F2F26) to Color(0xFF8FD6A4)
    val PillGrayDark = Color(0xFF24242E) to Color(0xFFB5B2C2)
    val PillPeachDark = Color(0xFF332620) to Color(0xFFF0A874)
    val PillSkyDark = Color(0xFF1F2836) to Color(0xFF82ABDF)
    val PillLilacDark = Color(0xFF262437) to Color(0xFFA99DF0)
    val PillSandDark = Color(0xFF332C1C) to Color(0xFFE3C26E)

    // —— 底栏（设计稿三页同款）——
    /** 未选中图标 / 文字。 */
    val NavIdle = Color(0xFFA5A2B5)
    /** 选中色 + 选中底。 */
    val NavActive = Color(0xFF6C5FC7)
    val NavActiveBg = Color(0xFFF3F0FB)
    /** 角标：薄荷绿底白字。 */
    val NavBadge = Color(0xFF3E7A4C)

    // —— 重启弹层用的柔和色（与主页同一套马卡龙）——
    val RebootTones = listOf(
        Color(0xFFE9E1FB) to Color(0xFF3A2A63),
        Color(0xFFE3F5E0) to Color(0xFF1D4526),
        Color(0xFFFCF0CE) to Color(0xFF4A3710),
        Color(0xFFFBE0EA) to Color(0xFF4A1F31),
        Color(0xFFDDEBFA) to Color(0xFF17324D),
        Color(0xFFDFF3F3) to Color(0xFF0F3A3A),
    )

    // —— 重启弹层深色版（dark-mode 稿）——
    val RebootTonesDark = listOf(
        KpiLilacDark, KpiMintDark, KpiCreamDark,
        KpiRoseDark, PillSkyDark, PillGrayDark,
    )

    // —— 形状与尺寸 ——
    /** 大卡片圆角（主卡 / KPI 卡）。 */
    val CardShape = RoundedCornerShape(24.dp)
    /** 工具胶囊圆角：完全药丸。 */
    val PillShape = RoundedCornerShape(26.dp)
    /** 图标底板圆角。 */
    val IconBoxShape = RoundedCornerShape(12.dp)

    /** 卡片间距。 */
    val CardGap = 12.dp
    /** 页面左右留白。 */
    val PagePadding = 16.dp
}
