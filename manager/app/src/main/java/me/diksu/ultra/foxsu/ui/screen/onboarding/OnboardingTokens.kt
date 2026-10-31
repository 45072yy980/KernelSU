package me.diksu.ultra.foxsu.ui.screen.onboarding

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import me.diksu.ultra.R
import me.diksu.ultra.foxsu.ui.UiMode
import me.diksu.ultra.foxsu.ui.theme.currentAccent

/**
 * 欢迎引导专用的设计令牌。
 *
 * 数值逐条取自设计稿 `tokens.json` / `tokens.css`：
 *  - 皮肤层（skin）负责背景 / 文字 / 强调色 / 次强调色；
 *  - 组件层（component，--pk-*）按明暗双模式提供卡片、描边、分段控件等。
 *
 * 引导页不跟随 App 的 5 套界面风格（那套风格由设置页的 Style Picker 负责），
 * 自己保持与设计稿一致的 Nocturne / Daylight 双模式观感。
 */
@Immutable
data class OnbTokens(
    val dark: Boolean,
    // —— 皮肤层 ——
    val bg: Color,
    val surface: Color,
    val line: Color,
    val tx: Color,
    val tx2: Color,
    val tx3: Color,
    val accent: Color,
    val accent2: Color,
    val mint: Color,
    val lime: Color,
    // —— 组件层 --pk-* ——
    val cardBg: Color,
    val cardBd: Color,
    val chipBg: Color,
    val chipBd: Color,
    val segBg: Color,
    val segBd: Color,
    val segTx: Color,
    val segOnBg: Color,
    val segOnBd: Color,
    val segOnTx: Color,
    val selBg: Color,
    val selBd: Color,
    val selRing: Color,
    val infoBg: Color,
    val infoBd: Color,
    val infoTx: Color,
    val infoB: Color,
    val ghostTx: Color,
    val ghostBd: Color,
    val ripple: Color,
) {
    /**
     * 顶部光晕：直接由 [accent] 派生，不再写死紫罗兰。
     * 暗色 34%、亮色 16%，正好是原设计稿里紫罗兰那两个透明度。
     */
    val glow: Color
        get() = accent.copy(alpha = if (dark) 0.34f else 0.16f)
}

/** Nocturne · 暗夜 */
private val Nocturne = OnbTokens(
    dark = true,
    bg = Color(0xFF07080B),
    surface = Color(0xFF101218),
    line = Color(0x12FFFFFF),
    tx = Color(0xFFF3F4F8),
    tx2 = Color(0xFF9BA0B2),
    tx3 = Color(0xFF5D6376),
    accent = Color(0xFF8B7BFF),
    accent2 = Color(0xFF6A57F5),
    mint = Color(0xFF4DE2C5),
    lime = Color(0xFFC9F45C),
    cardBg = Color(0x0AFFFFFF),
    cardBd = Color(0x12FFFFFF),
    chipBg = Color(0x08FFFFFF),
    chipBd = Color(0x17FFFFFF),
    segBg = Color(0x0DFFFFFF),
    segBd = Color(0x0FFFFFFF),
    segTx = Color(0xFF8A90A2),
    segOnBg = Color(0x338B7BFF),
    segOnBd = Color(0x598B7BFF),
    segOnTx = Color(0xFFCFC7FF),
    selBg = Color(0x1F8B7BFF),
    selBd = Color(0xCC8B7BFF),
    selRing = Color(0x1F8B7BFF),
    infoBg = Color(0x0AFFFFFF),
    infoBd = Color(0x0FFFFFFF),
    infoTx = Color(0xFF9BA0B2),
    infoB = Color(0xFFE8EAF0),
    ghostTx = Color(0xFF9BA0B2),
    ghostBd = Color(0x1F000000).copy(alpha = 0.12f),
    ripple = Color(0x8C8B7BFF),
)

/** Daylight · 亮色 */
private val Daylight = OnbTokens(
    dark = false,
    bg = Color(0xFFF4F5FA),
    surface = Color(0xFFFFFFFF),
    line = Color(0x140F142D),
    tx = Color(0xFF12141B),
    tx2 = Color(0xFF5D6376),
    tx3 = Color(0xFF9AA0B0),
    // 与 FoxSu Redesign（默认主题）同一套品牌色：珊瑚橙 #F97316 → 玫红 #F43F5E
    accent = Color(0xFFF97316),
    accent2 = Color(0xFFF43F5E),
    mint = Color(0xFF0FA98B),
    lime = Color(0xFFC9F45C),
    cardBg = Color(0xFFFFFFFF),
    cardBd = Color(0x140F142D),
    chipBg = Color(0x0F0F142D),
    chipBd = Color(0x140F142D),
    segBg = Color(0xFFF1F2F8),
    segBd = Color(0x120F142D),
    segTx = Color(0xFF7C8294),
    segOnBg = Color(0xFFFFFFFF),
    segOnBd = Color(0x38F97316),
    segOnTx = Color(0xFFC2410C),
    selBg = Color(0x0FF97316),
    selBd = Color(0xBFF97316),
    selRing = Color(0x1AF97316),
    infoBg = Color(0xFFF7F8FC),
    infoBd = Color(0x120F142D),
    infoTx = Color(0xFF6A7080),
    infoB = Color(0xFF1A1C24),
    ghostTx = Color(0xFF6A7080),
    ghostBd = Color(0x1F0F142D),
    ripple = Color(0x596A57F5),
)

/**
 * 把一整组令牌里「所有由强调色派生出来的值」重新算一遍。
 *
 * 令牌表里写死的那些 0x33F97316 / 0xBFF97316 都是珊瑚橙时代的常量，
 * 一旦用户换成薄荷绿就全错。这里统一按 [accent] 现场推导，
 * 明暗两套用不同透明度，保证对比度不掉。
 */
private fun OnbTokens.recolor(accent: Color, accent2: Color): OnbTokens {
    // 强调色上的文字：暗色往上提亮，亮色往下压深，避免橙底白字发飘
    val onAccent = if (dark) lerp(accent, Color.White, 0.42f) else lerp(accent, Color.Black, 0.16f)
    return copy(
        accent = accent,
        accent2 = accent2,
        // 第三光斑取两者的中间色，整页保持同一色系，不再混进一抹薄荷绿
        mint = lerp(accent, accent2, 0.5f),
        segOnBg = accent.copy(alpha = if (dark) 0.20f else 0.16f),
        segOnBd = accent.copy(alpha = if (dark) 0.42f else 0.30f),
        segOnTx = onAccent,
        selBg = accent.copy(alpha = if (dark) 0.12f else 0.07f),
        selBd = accent.copy(alpha = if (dark) 0.72f else 0.85f),
        selRing = accent.copy(alpha = if (dark) 0.14f else 0.10f),
        ripple = accent.copy(alpha = 0.32f),
    )
}

fun onbTokens(dark: Boolean): OnbTokens {
    val base = if (dark) Nocturne else Daylight
    val a = currentAccent
    return base.recolor(a.primary, a.secondary)
}

/**
 * 按当前界面风格取令牌：组件层（--pk-*）随明暗切换，
 * 皮肤层（背景 / 强调色 / 次强调色）取该风格自己的取值，
 * 所以在引导页里换皮肤时整页配色会实时跟着变。
 */
fun onbTokens(mode: UiMode): OnbTokens {
    val skin = OnbSkins.firstOrNull { it.mode == mode } ?: return onbTokens(false)
    val base = if (skin.dark) Nocturne else Daylight
    // 底色跟着皮肤走（五套皮肤的区别就在底色和材质上），
    // 强调色一律取全局设置——和主页、底栏、标题用的是同一个 currentAccent。
    val a = currentAccent
    return base
        .copy(bg = skin.bg, surface = skin.surface)
        .recolor(a.primary, a.secondary)
}

/**
 * 一张风格卡：设计稿 THEMES 表里的 5 套皮肤。
 * [mode] 是 App 里真实存在的界面风格，点击即写库即时生效。
 */
@Immutable
data class OnbSkin(
    val id: String,
    val label: String,
    val sub: String,
    val bg: Color,
    val surface: Color,
    val accent: Color,
    val accent2: Color,
    val mint: Color,
    val dark: Boolean,
    val mode: UiMode,
    val descRes: Int,
)

val OnbSkins = listOf(
    OnbSkin(
        id = "nocturne",
        label = "Nocturne",
        sub = "亮色",
        bg = Color(0xFFF4F5FA),
        surface = Color(0xFFFFFFFF),
        accent = Color(0xFF6A57F5),
        accent2 = Color(0xFF8B7BFF),
        mint = Color(0xFF0FA98B),
        dark = false,
        mode = UiMode.Nocturne,
        descRes = R.string.fx_onboarding_skin_nocturne,
    ),
    OnbSkin(
        id = "pages",
        label = "Pages",
        sub = "极简",
        bg = Color(0xFFFFFFFF),
        surface = Color(0xFFFFFFFF),
        accent = Color(0xFF2D6BFF),
        accent2 = Color(0xFF1E4FD8),
        mint = Color(0xFF2D6BFF),
        dark = false,
        mode = UiMode.Fresh,
        descRes = R.string.fx_onboarding_skin_pages,
    ),
    OnbSkin(
        id = "editorial",
        label = "Editorial",
        sub = "纸面",
        bg = Color(0xFFF5F3ED),
        surface = Color(0xFFFFFCF6),
        accent = Color(0xFFE8502E),
        accent2 = Color(0xFFD8431F),
        mint = Color(0xFFE8502E),
        dark = false,
        mode = UiMode.Material,
        descRes = R.string.fx_onboarding_skin_editorial,
    ),
    OnbSkin(
        id = "glass",
        label = "Glass",
        sub = "玻璃",
        bg = Color(0xFFF4F5FA),
        surface = Color(0xFFFFFFFF),
        accent = Color(0xFF6A57F5),
        accent2 = Color(0xFF8B7BFF),
        mint = Color(0xFF0FA98B),
        dark = false,
        mode = UiMode.Glass,
        descRes = R.string.fx_onboarding_skin_daylight,
    ),
    OnbSkin(
        id = "miuix",
        label = "Miuix",
        sub = "原生",
        bg = Color(0xFFEFEDF8),
        surface = Color(0xFFF7F5FC),
        accent = Color(0xFF6E56F8),
        accent2 = Color(0xFF5B45E0),
        mint = Color(0xFF12B981),
        dark = false,
        mode = UiMode.Miuix,
        descRes = R.string.fx_onboarding_skin_miuix,
    ),
)
