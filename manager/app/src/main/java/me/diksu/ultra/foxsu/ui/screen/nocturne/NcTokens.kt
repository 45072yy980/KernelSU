package me.diksu.ultra.foxsu.ui.screen.nocturne

import androidx.compose.runtime.Composable
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import me.diksu.ultra.foxsu.ui.LocalUiMode
import me.diksu.ultra.foxsu.ui.UiMode

/**
 * FoxSu Nocturne —— 设计 token。
 * 逐值映射自设计稿 foxsu-nocturne-ui.html 的 :root / .screen.light 变量与配套 CSS。
 * 暗夜：OLED #07080B + violet #8B7BFF + mint #4DE2C5
 * 亮色 Daylight：#F4F5FA + violet #6A57F5 + mint #0FA98B
 */
object NcColors {
    // ── 暗夜 ──
    val bg = Color(0xFF07080B)
    val s1 = Color(0xFF101218)
    val s2 = Color(0xFF161922)
    val line = Color(0x12FFFFFF)
    val fill = Color(0x0DFFFFFF)
    val tx = Color(0xFFF3F4F8)
    val tx2 = Color(0xFF9BA0B2)
    val tx3 = Color(0xFF5D6376)
    val vio = Color(0xFF8B7BFF)
    val vio2 = Color(0xFF6A57F5)
    val mint = Color(0xFF4DE2C5)
    val lime = Color(0xFFC9F45C)
    val red = Color(0xFFFF6B5E)
    val amber = Color(0xFFFFB35C)

    // ── 亮色 Daylight ──
    val lBg = Color(0xFFF4F5FA)
    val lS1 = Color(0xFFFFFFFF)
    val lS2 = Color(0xFFF7F8FC)
    val lLine = Color(0x140F142D)
    val lFill = Color(0xFFF3F4F9)
    val lTx = Color(0xFF12141B)
    val lTx2 = Color(0xFF5D6376)
    val lTx3 = Color(0xFF9AA0B0)
    val lVio = Color(0xFF6A57F5)
    val lMint = Color(0xFF0FA98B)
    val lRed = Color(0xFFE8503F)
    val lAmber = Color(0xFFDE9A21)
}

/** 当前是否为 Nocturne 模式（供共享组件判断）。 */
val isNocturneMode: Boolean @Composable get() = LocalUiMode.current == UiMode.Nocturne

/** 圆角（设计稿 radius token）。 */
object NcRadius {
    val sheet = 30.dp
    val hero = 24.dp
    val panel = 20.dp
    val card = 16.dp
    val nav = 22.dp
    val iconBox = 11.dp
    val btn = 13.dp
    val pill = 99.dp
}

/** 间距 / 尺寸（设计稿 spacing & sizing token）。 */
object NcDimens {
    val pageX = 18.dp
    val iconBox = 34.dp
    val appAvatar = 36.dp
    val authorAvatar = 66.dp
    val ring = 56.dp
    val navHeight = 64.dp
    val fab = 56.dp
    val rowPadY = 8.5.dp
    val rowPadX = 15.dp
}

/**
 * 亮暗感知 token 入口：Nocturne 屏幕统一从这里取色。
 * 外观（暗夜 / 亮色 / 跟随系统）、强调色、光晕强度均由 NcAppearance 真实持久化。
 */
@Composable
fun ncTokens(): NcTokens {
    val dark = NcAppearance.resolveDarkMode()
    val base = if (dark) NcTokens.dark else NcTokens.light
    val t = base
        .withAccent(NcAccents[NcAppearance.accentIndex.coerceIn(NcAccents.indices)])
        .withGlow(NcAppearance.glowLevel.coerceIn(0, 2))
    // 全局强调色覆盖主题内自带的强调色（定制 → 全局强调色）
    val ac = me.diksu.ultra.foxsu.ui.theme.currentAccent
    return t.copy(
        vio = ac.primary,
        vio2 = ac.mid,
        glowViolet = ac.primary.copy(alpha = if (t.dark) 0.13f else 0.16f),
    )
}

data class NcTokens(
    val dark: Boolean,
    // 基础
    val bg: Color,
    val s1: Color,
    val s2: Color,
    val line: Color,
    val fill: Color,
    val tx: Color,
    val tx2: Color,
    val tx3: Color,
    val vio: Color,
    val vio2: Color,
    val mint: Color,
    val lime: Color,
    val red: Color,
    val amber: Color,
    val textMono: Color,
    val iconTint: Color,
    // 页面背景光晕
    val glowViolet: Color,
    val glowMint: Color,
    // Hero 描边 / 卡面 / 右上光晕
    val heroBorder: List<Color>,
    val heroBorderStops: List<Float>,
    val heroCard: List<Color>,
    val heroCardStops: List<Float>,
    val heroGlow: Color,
    val heroGlowAlpha: Float,
    // Hero 语义色
    val heroStatus: Color,
    val heroBig: Color,
    val heroBadgeBg: Color,
    val heroBadgeText: Color,
    val heroMetaKey: Color,
    // Group / Row
    val groupShadow: Color,
    val chevron: Color,
    val icDefaultBg: Color, val icDefaultFg: Color,
    val icVioletBg: Color, val icVioletFg: Color,
    val icMintBg: Color, val icMintFg: Color,
    val icRedBg: Color, val icRedFg: Color,
    val icAmberBg: Color, val icAmberFg: Color,
    val rowDesc: Color,
    val rowDescMono: Color,
    // CodeCard
    val codeTop: Color, val codeBottom: Color,
    val codeBorder: Color, val codeKicker: Color, val codeTag: Color, val codeValue: Color,
    val codeStripeTop: Color, val codeStripeBottom: Color,
    // Search
    val searchBg: Color, val searchBorder: Color, val searchText: Color,
    // App 行 / 徽章
    val badgeOkBg: Color, val badgeOkFg: Color,
    val badgeNoBg: Color, val badgeNoFg: Color,
    // Empty 光球
    val orbInner: Color, val orbMid: Color, val orbIcon: Color,
    val orbRing: Color, val orbGlow: Color,
    // Switch
    val swOffBg: Color, val swThumbOff: Color,
    // 底栏
    val navBg: Color, val navBorder: Color, val navShadow: Color,
    val navActiveText: Color, val navActiveIcon: Color, val navInactive: Color,
    val navIndicator: List<Color>,
    val badgeBg: Color,
    // 弹层
    val scrim: Color,
    val sheetTop: Color, val sheetBottom: Color,
    val sheetBorder: Color, val sheetTopGlow: Color,
    val grab: Color,
    // 重启面板
    val rbKicker: Color, val rbHint: Color,
    val rbPriBg: List<Color>, val rbPriBorder: Color, val rbPriShadow: Color,
    val rbPriGlow: Color, val rbPriText: Color, val rbPriSub: Color, val rbPriArrow: Color,
    val rbCellBg: Color, val rbCellBorder: Color,
    val rbCellIconBg: Color, val rbCellIconFg: Color,
    val rbCellTitle: Color, val rbCellSub: Color,
    val rbDangerBg: Color, val rbDangerBorder: Color,
    val rbDangerIconBg: Color, val rbDangerIconFg: Color,
    val rbDangerTitle: Color, val rbDangerSub: Color, val rbDangerArrow: Color,
    val cancelText: Color, val cancelBorder: Color, val cancelBg: Color,
    // 关于作者
    val acardBorder: List<Color>, val acardBorderStops: List<Float>,
    val acardInner: List<Color>, val acardInnerStops: List<Float>,
    val acardGlow: Color,
    val quoteText: Color, val quoteStrong: Color, val quoteBar: Color,
    val chipBg: Color, val chipFg: Color, val chipBorder: Color,
    val chipVioletBg: Color, val chipVioletFg: Color, val chipVioletBorder: Color,
    // 主题选择
    val tcardBg: Color, val tcardBorder: Color, val tcardSelBg: Color, val tcardSelBorder: Color,
    val swatchSelRing: Color,
    val ritemPriBg: List<Color>, val ritemPriBorder: Color,
) {
    companion object {
        val dark = NcTokens(
            dark = true,
            bg = NcColors.bg, s1 = NcColors.s1, s2 = NcColors.s2,
            line = NcColors.line, fill = NcColors.fill,
            tx = NcColors.tx, tx2 = NcColors.tx2, tx3 = NcColors.tx3,
            vio = NcColors.vio, vio2 = NcColors.vio2, mint = NcColors.mint,
            lime = NcColors.lime, red = NcColors.red, amber = NcColors.amber,
            textMono = Color(0xFFB9BFCB), iconTint = Color(0xFFA9AFC0),
            glowViolet = Color(0x218B7BFF), glowMint = Color(0x0F4DE2C5),
            heroBorder = listOf(Color(0xB38B7BFF), Color(0x594DE2C5), Color(0x0FFFFFFF)),
            heroBorderStops = listOf(0f, 0.45f, 0.80f),
            heroCard = listOf(Color(0xFF141724), Color(0xFF0E1018)),
            heroCardStops = listOf(0f, 0.60f),
            heroGlow = Color(0xFF8B7BFF), heroGlowAlpha = 0.35f,
            heroStatus = Color(0xFF5D6376),
            heroBig = Color(0xFFF3F4F8),
            heroBadgeBg = Color(0x1F4DE2C5),
            heroBadgeText = Color(0xFF4DE2C5),
            heroMetaKey = Color(0xFF5D6376),
            groupShadow = Color(0x00000000),
            chevron = Color(0xFF3F4557),
            icDefaultBg = Color(0x0DFFFFFF), icDefaultFg = Color(0xFFA9AFC0),
            icVioletBg = Color(0x248B7BFF), icVioletFg = Color(0xFFB0A4FF),
            icMintBg = Color(0x1F4DE2C5), icMintFg = Color(0xFF4DE2C5),
            icRedBg = Color(0x1FFF6B5E), icRedFg = Color(0xFFFF6B5E),
            icAmberBg = Color(0x1FFFB35C), icAmberFg = Color(0xFFFFB35C),
            rowDesc = NcColors.tx2, rowDescMono = Color(0xFF8A90A2),
            codeTop = Color(0xFF0F1219), codeBottom = Color(0xFF0B0D13),
            codeBorder = Color(0x298B7BFF),
            codeKicker = Color(0xFF9E93FF), codeTag = Color(0xFF4E5466), codeValue = Color(0xFFB9BFCB),
            codeStripeTop = Color(0xE68B7BFF), codeStripeBottom = Color(0x594DE2C5),
            searchBg = Color(0x0DFFFFFF), searchBorder = NcColors.line, searchText = NcColors.tx3,
            badgeOkBg = Color(0x1F4DE2C5), badgeOkFg = NcColors.mint,
            badgeNoBg = Color(0x0DFFFFFF), badgeNoFg = NcColors.tx3,
            orbInner = Color(0x598B7BFF), orbMid = Color(0x0D8B7BFF), orbIcon = Color(0xFF8E82FF),
            orbRing = Color(0x478B7BFF), orbGlow = Color(0x2E8B7BFF),
            swOffBg = Color(0x1AFFFFFF), swThumbOff = Color(0xFF8C93A5),
            navBg = Color(0xB814161E), navBorder = Color(0x14FFFFFF), navShadow = Color(0x8C000000),
            navActiveText = Color.White, navActiveIcon = Color(0xFFB0A4FF),
            navInactive = Color(0xFF6A7085),
            navIndicator = listOf(Color(0xFF8B7BFF), Color(0xFF4DE2C5)),
            badgeBg = NcColors.vio2,
            scrim = Color(0x9E040508),
            sheetTop = Color(0xFF171A25), sheetBottom = Color(0xFF0E1016),
            sheetBorder = Color(0x4D8B7BFF), sheetTopGlow = Color(0x598B7BFF),
            grab = Color(0x29FFFFFF),
            rbKicker = Color(0xFF7B6FE8), rbHint = Color(0xFF5D6376),
            rbPriBg = listOf(Color(0x478B7BFF), Color(0x128B7BFF), Color(0x0F4DE2C5)),
            rbPriBorder = Color(0x738B7BFF), rbPriShadow = Color(0x388B7BFF),
            rbPriGlow = Color(0x8C8B7BFF), rbPriText = Color.White,
            rbPriSub = Color(0xFFA9A3D8), rbPriArrow = Color(0xFFB0A4FF),
            rbCellBg = Color(0x0AFFFFFF), rbCellBorder = Color(0x12FFFFFF),
            rbCellIconBg = Color(0x0FFFFFFF), rbCellIconFg = Color(0xFF9BA0B2),
            rbCellTitle = Color(0xFFE8EAF0), rbCellSub = Color(0xFF5D6376),
            rbDangerBg = Color(0x12FF6B5E), rbDangerBorder = Color(0x3DFF6B5E),
            rbDangerIconBg = Color(0x24FF6B5E), rbDangerIconFg = Color(0xFFFF8A7D),
            rbDangerTitle = Color(0xFFFFB3A9), rbDangerSub = Color(0xFF8A6A66), rbDangerArrow = Color(0xFF7A5751),
            cancelText = Color(0xFF9BA0B2), cancelBorder = Color(0x1AFFFFFF), cancelBg = Color(0x00000000),
            acardBorder = listOf(Color(0xB38B7BFF), Color(0x594DE2C5), Color(0x0DFFFFFF)),
            acardBorderStops = listOf(0f, 0.50f, 0.85f),
            acardInner = listOf(Color(0xFF161A28), Color(0xFF0D0F16)),
            acardInnerStops = listOf(0f, 0.65f),
            acardGlow = Color(0x598B7BFF),
            quoteText = NcColors.tx2, quoteStrong = NcColors.tx, quoteBar = Color(0x8C8B7BFF),
            chipBg = Color(0x0FFFFFFF), chipFg = Color(0xFFB9BECB), chipBorder = Color(0x14FFFFFF),
            chipVioletBg = Color(0x298B7BFF), chipVioletFg = Color(0xFFB0A4FF), chipVioletBorder = Color(0x4D8B7BFF),
            tcardBg = Color(0x0AFFFFFF), tcardBorder = NcColors.line,
            tcardSelBg = Color(0x1A8B7BFF), tcardSelBorder = Color(0xB38B7BFF),
            swatchSelRing = Color(0x8CFFFFFF),
            ritemPriBg = listOf(Color(0x2E8B7BFF), Color(0x0A8B7BFF)), ritemPriBorder = Color(0x598B7BFF),
        )

        val light = NcTokens(
            dark = false,
            bg = NcColors.lBg, s1 = NcColors.lS1, s2 = NcColors.lS2,
            line = NcColors.lLine, fill = NcColors.lFill,
            tx = NcColors.lTx, tx2 = NcColors.lTx2, tx3 = NcColors.lTx3,
            vio = NcColors.lVio, vio2 = NcColors.lVio, mint = NcColors.lMint,
            lime = NcColors.lime, red = NcColors.lRed, amber = NcColors.lAmber,
            textMono = Color(0xFF4E5464), iconTint = Color(0xFF6F7686),
            glowViolet = Color(0x298B7BFF), glowMint = Color(0x1F4DE2C5),
            heroBorder = listOf(Color(0x8C6A57F5), Color(0x5915A98B), Color(0x140F142D)),
            heroBorderStops = listOf(0f, 0.45f, 0.85f),
            heroCard = listOf(Color(0xFFFFFFFF), Color(0xFFF6F7FC)),
            heroCardStops = listOf(0f, 0.62f),
            heroGlow = Color(0xFF8B7BFF), heroGlowAlpha = 0.22f,
            heroStatus = Color(0xFF8A90A2),
            heroBig = Color(0xFF12141B),
            heroBadgeBg = Color(0x1F0FA98B),
            heroBadgeText = Color(0xFF0B8E75),
            heroMetaKey = Color(0xFF9AA0B0),
            groupShadow = Color(0x0D141E3C),
            chevron = Color(0xFFC3C8D4),
            icDefaultBg = Color(0xFFF3F4F9), icDefaultFg = Color(0xFF6F7686),
            icVioletBg = Color(0x1A6A57F5), icVioletFg = Color(0xFF6A57F5),
            icMintBg = Color(0x1A15A98B), icMintFg = Color(0xFF0FA98B),
            icRedBg = Color(0x1AE8503F), icRedFg = Color(0xFFE8503F),
            icAmberBg = Color(0x1ADE9A21), icAmberFg = Color(0xFFDE9A21),
            rowDesc = Color(0xFF7A8090), rowDescMono = Color(0xFF8A90A0),
            codeTop = Color(0xFFFBFBFD), codeBottom = Color(0xFFFBFBFD),
            codeBorder = Color(0x246A57F5),
            codeKicker = Color(0xFF6A57F5), codeTag = Color(0xFFA9AEBB), codeValue = Color(0xFF4E5464),
            codeStripeTop = Color(0xE66A57F5), codeStripeBottom = Color(0x5915A98B),
            searchBg = Color(0xFFFFFFFF), searchBorder = NcColors.lLine, searchText = Color(0xFF9AA0B0),
            badgeOkBg = Color(0x1A15A98B), badgeOkFg = Color(0xFF0FA98B),
            badgeNoBg = Color(0x0D0F142D), badgeNoFg = Color(0xFF9AA0B0),
            orbInner = Color(0x388B7BFF), orbMid = Color(0x088B7BFF), orbIcon = Color(0xFF6A57F5),
            orbRing = Color(0x336A57F5), orbGlow = Color(0x1A6A57F5),
            swOffBg = Color(0x1F0F142D), swThumbOff = Color(0xFFFFFFFF),
            navBg = Color(0xD1FFFFFF), navBorder = Color(0x120F142D), navShadow = Color(0x24141E3C),
            navActiveText = Color(0xFF12141B), navActiveIcon = Color(0xFF6A57F5),
            navInactive = Color(0xFF98A0AE),
            navIndicator = listOf(Color(0xFF8B7BFF), Color(0xFF4DE2C5)),
            badgeBg = NcColors.lVio,
            scrim = Color(0x52201A23),
            sheetTop = Color(0xFFFFFFFF), sheetBottom = Color(0xFFF4F5FA),
            sheetBorder = Color(0x596A57F5), sheetTopGlow = Color(0x2E8B7BFF),
            grab = Color(0x240F142D),
            rbKicker = Color(0xFF6A57F5), rbHint = Color(0xFF9AA0B0),
            rbPriBg = listOf(Color(0x246A57F5), Color(0x0A6A57F5), Color(0x0D15A98B)),
            rbPriBorder = Color(0x4D6A57F5), rbPriShadow = Color(0x296A57F5),
            rbPriGlow = Color(0x598B7BFF), rbPriText = Color(0xFF1A1C24),
            rbPriSub = Color(0xFF6A57F5), rbPriArrow = Color(0xFF6A57F5),
            rbCellBg = Color(0xFFFFFFFF), rbCellBorder = Color(0x140F142D),
            rbCellIconBg = Color(0xFFF3F4F9), rbCellIconFg = Color(0xFF6F7686),
            rbCellTitle = Color(0xFF1A1C24), rbCellSub = Color(0xFF9AA0B0),
            rbDangerBg = Color(0x0FE8503F), rbDangerBorder = Color(0x33E8503F),
            rbDangerIconBg = Color(0x1AE8503F), rbDangerIconFg = Color(0xFFE8503F),
            rbDangerTitle = Color(0xFFC0392B), rbDangerSub = Color(0xFFB08A86), rbDangerArrow = Color(0xFFC9A9A5),
            cancelText = Color(0xFF7A8090), cancelBorder = Color(0x1F0F142D), cancelBg = Color(0xFFFFFFFF),
            acardBorder = listOf(Color(0x8C6A57F5), Color(0x4D15A98B), Color(0x120F142D)),
            acardBorderStops = listOf(0f, 0.50f, 0.85f),
            acardInner = listOf(Color(0xFFFFFFFF), Color(0xFFF5F6FB)),
            acardInnerStops = listOf(0f, 0.65f),
            acardGlow = Color(0x338B7BFF),
            quoteText = Color(0xFF5D6376), quoteStrong = Color(0xFF12141B), quoteBar = Color(0x736A57F5),
            chipBg = Color(0xFFF2F3F9), chipFg = Color(0xFF6A7080), chipBorder = Color(0x120F142D),
            chipVioletBg = Color(0x1A6A57F5), chipVioletFg = Color(0xFF6A57F5), chipVioletBorder = Color(0x386A57F5),
            tcardBg = Color(0xFFF6F7FB), tcardBorder = Color(0x140F142D),
            tcardSelBg = Color(0x126A57F5), tcardSelBorder = Color(0xB36A57F5),
            swatchSelRing = Color(0x590F142D),
            ritemPriBg = listOf(Color(0x1F6A57F5), Color(0x086A57F5)), ritemPriBorder = Color(0x476A57F5),
        )
    }
}

/**
 * 强调色方案：设计稿 swatch 6 枚。
 * 每色给出暗夜 / 亮色两套（亮色更深，保证白底可读）。
 */
data class NcAccent(
    val name: String,
    val hex: String,
    val darkMain: Color,
    val darkDeep: Color,
    val lightMain: Color,
    val lightDeep: Color,
) {
    fun main(dark: Boolean) = if (dark) darkMain else lightMain
    fun deep(dark: Boolean) = if (dark) darkDeep else lightDeep
    /** 暗夜下需要更亮的图标前景色。 */
    fun fg(dark: Boolean) = if (dark) lerp(darkMain, Color.White, 0.18f) else lightMain
}

val NcAccents = listOf(
    NcAccent(
        "紫罗兰", "#8B7BFF",
        Color(0xFF8B7BFF), Color(0xFF6A57F5),
        Color(0xFF6A57F5), Color(0xFF4F3CE0),
    ),
    NcAccent(
        "薄荷", "#4DE2C5",
        Color(0xFF4DE2C5), Color(0xFF12A98D),
        Color(0xFF0FA98B), Color(0xFF0B7F68),
    ),
    NcAccent(
        "珊瑚", "#FF8A65",
        Color(0xFFFF8A65), Color(0xFFF0503C),
        Color(0xFFE8503F), Color(0xFFC33B2C),
    ),
    NcAccent(
        "琥珀", "#FFC65C",
        Color(0xFFFFC65C), Color(0xFFE39B1E),
        Color(0xFFDE9A21), Color(0xFFB87A12),
    ),
    NcAccent(
        "极光蓝", "#6AA8FF",
        Color(0xFF6AA8FF), Color(0xFF2D6BFF),
        Color(0xFF3B7BF0), Color(0xFF2455C7),
    ),
    NcAccent(
        "樱粉", "#F58BC0",
        Color(0xFFF58BC0), Color(0xFFD3488F),
        Color(0xFFD3488F), Color(0xFFAE2F6D),
    ),
)

/**
 * 用强调色替换整套 violet 家族色，保证换色后界面依旧协调。
 */
fun NcTokens.withAccent(accent: NcAccent): NcTokens {
    val d = dark
    val main = accent.main(d)
    val deep = accent.deep(d)
    val fg = accent.fg(d)
    return copy(
        vio = main,
        vio2 = deep,
        glowViolet = main.copy(alpha = if (d) 0.13f else 0.16f),
        heroBorder = if (d) {
            listOf(main.copy(alpha = 0.70f), mint.copy(alpha = 0.35f), Color(0x0FFFFFFF))
        } else {
            listOf(main.copy(alpha = 0.55f), mint.copy(alpha = 0.35f), Color(0x140F142D))
        },
        heroGlow = main,
        icVioletBg = main.copy(alpha = if (d) 0.14f else 0.10f),
        icVioletFg = fg,
        navActiveIcon = fg,
        navIndicator = listOf(main, mint),
        badgeBg = deep,
        codeBorder = main.copy(alpha = if (d) 0.16f else 0.14f),
        codeKicker = if (d) fg else main,
        codeStripeTop = main.copy(alpha = 0.90f),
        orbInner = main.copy(alpha = if (d) 0.35f else 0.22f),
        orbMid = main.copy(alpha = if (d) 0.05f else 0.03f),
        orbIcon = fg,
        orbRing = main.copy(alpha = if (d) 0.28f else 0.20f),
        orbGlow = main.copy(alpha = if (d) 0.18f else 0.10f),
        sheetBorder = main.copy(alpha = 0.35f),
        sheetTopGlow = main.copy(alpha = 0.35f),
        rbKicker = if (d) fg else main,
        rbPriBg = listOf(
            main.copy(alpha = 0.28f),
            main.copy(alpha = 0.07f),
            mint.copy(alpha = 0.06f),
        ),
        rbPriBorder = main.copy(alpha = 0.45f),
        rbPriShadow = main.copy(alpha = 0.22f),
        rbPriGlow = main.copy(alpha = 0.55f),
        rbPriSub = if (d) fg else main,
        rbPriArrow = fg,
        acardBorder = if (d) {
            listOf(main.copy(alpha = 0.70f), mint.copy(alpha = 0.35f), Color(0x0DFFFFFF))
        } else {
            listOf(main.copy(alpha = 0.55f), mint.copy(alpha = 0.30f), Color(0x120F142D))
        },
        acardGlow = main.copy(alpha = if (d) 0.35f else 0.20f),
        quoteBar = main.copy(alpha = 0.55f),
        chipVioletBg = main.copy(alpha = 0.16f),
        chipVioletFg = fg,
        chipVioletBorder = main.copy(alpha = 0.30f),
        tcardSelBg = main.copy(alpha = 0.10f),
        tcardSelBorder = main.copy(alpha = 0.75f),
        ritemPriBg = listOf(main.copy(alpha = 0.18f), main.copy(alpha = 0.04f)),
        ritemPriBorder = main.copy(alpha = 0.35f),
    )
}

/** 光晕强度：低 / 中 / 高，整体缩放所有光晕层透明度。 */
fun NcTokens.withGlow(level: Int): NcTokens {
    val k = when (level) {
        0 -> 0.55f
        2 -> 1.45f
        else -> 1f
    }
    if (k == 1f) return this
    return copy(
        glowViolet = glowViolet.copy(alpha = (glowViolet.alpha * k).coerceIn(0f, 1f)),
        glowMint = glowMint.copy(alpha = (glowMint.alpha * k).coerceIn(0f, 1f)),
        heroGlowAlpha = (heroGlowAlpha * k).coerceIn(0f, 1f),
        orbGlow = orbGlow.copy(alpha = (orbGlow.alpha * k).coerceIn(0f, 1f)),
        sheetTopGlow = sheetTopGlow.copy(alpha = (sheetTopGlow.alpha * k).coerceIn(0f, 1f)),
        acardGlow = acardGlow.copy(alpha = (acardGlow.alpha * k).coerceIn(0f, 1f)),
        rbPriGlow = rbPriGlow.copy(alpha = (rbPriGlow.alpha * k).coerceIn(0f, 1f)),
    )
}

/**
 * 按 CSS linear-gradient(angle, stops) 语义生成渐变。
 * CSS 角度：0deg 向上、90deg 向右；渐变线过盒子中心。
 */
fun ncLinearGradient(
    colors: List<Color>,
    stops: List<Float>,
    angleDeg: Float,
    width: Float,
    height: Float,
): Brush {
    val rad = Math.toRadians(angleDeg.toDouble())
    val dx = kotlin.math.sin(rad).toFloat()
    val dy = -kotlin.math.cos(rad).toFloat()
    val cx = width / 2f
    val cy = height / 2f
    val len = kotlin.math.abs(width * dx) + kotlin.math.abs(height * dy)
    return Brush.linearGradient(
        colorStops = stops.mapIndexed { i, s -> s to colors[i] }.toTypedArray(),
        start = Offset(cx - dx * len / 2f, cy - dy * len / 2f),
        end = Offset(cx + dx * len / 2f, cy + dy * len / 2f),
    )
}
