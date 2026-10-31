package me.diksu.ultra.foxsu.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import me.diksu.ultra.foxsu.data.settings.CustomizationStore

/**
 * 背景色主题：Theme Kit v1.0 的 20 套配色，每套自带亮色 + 暗色两版。
 *
 * 和「全局强调色」是两条正交的轴：
 * - 强调色管按钮、选中态、标题渐变（7 套，取自 logo）；
 * - 背景色管画布、卡片、描边、正文与角部柔光（20 套 × 亮暗）。
 * 主题包里每套也带了 accent，这里**故意不采用**：用户如果已经挑了珊瑚橙的强调色，
 * 换背景色时被顺手改掉，等于偷偷改了两个设置。所以选背景色只动「底」，
 * 强调色仍归上面那个选择器——两张选择器各管一半，互不干扰。
 *
 * 亮暗版本由「外观模式」决定，不是自动跟随系统：外观模式选深色时，
 * 即使系统是浅色也取 dark 版，和整个界面的明暗保持一致。
 *
 * 数据来自 FoxSu Theme Kit v1.0 的 foxsu-theme.css，逐值搬运、未做二次调色：
 * 原包里 40 个模式的正文对比度都按 WCAG AAA（≥ 12:1）校过，自己再动一次只会把对比度改坏。
 */
data class BgPalette(
    /** 画布主色 */
    val bg: Color,
    /** 画布渐变副色（agPageBg 的 160° 渐变终点） */
    val bg2: Color,
    /** 卡片 / 面板底 */
    val surface: Color,
    /** 次级面板底（输入框、分组、选中底衬） */
    val surface2: Color,
    /** 分隔线 / 描边（原包已带透明度） */
    val line: Color,
    /** 主文字 */
    val text: Color,
    /** 次文字 */
    val text2: Color,
    /** 角部柔光 */
    val glow: Color,
)

data class BgTheme(
    /** 1..20，对应 [CustomizationStore.bgColorTheme] */
    val id: Int,
    val slug: String,
    val name: String,
    val light: BgPalette,
    val dark: BgPalette,
) {
    fun palette(dark: Boolean): BgPalette = if (dark) this.dark else this.light
}

/** 20 套，顺序即选择器顺序（1 = 奶油米 … 20 = 暖咖啡）。 */
val BgThemes: List<BgTheme> = listOf(
    BgTheme(
        id = 1, slug = "cream", name = "奶油米",
        light = BgPalette(
            bg = Color(0xFFFDFAF6),
            bg2 = Color(0xFFF7EDE2),
            surface = Color(0xFFFFFFFF),
            surface2 = Color(0xFFF7EDE2),
            line = Color(0x142B2420),
            text = Color(0xFF2B2420),
            text2 = Color(0xFF8A7E74),
            glow = Color(0x57FFBE96),
        ),
        dark = BgPalette(
            bg = Color(0xFF1E1913),
            bg2 = Color(0xFF171310),
            surface = Color(0xFF2A221B),
            surface2 = Color(0xFF1E1913),
            line = Color(0x1AF2E9DF),
            text = Color(0xFFF2E9DF),
            text2 = Color(0xFFA0907F),
            glow = Color(0x21FF9455),
        ),
    ),
    BgTheme(
        id = 2, slug = "blush", name = "腮红粉",
        light = BgPalette(
            bg = Color(0xFFFFF6F8),
            bg2 = Color(0xFFFDE8EE),
            surface = Color(0xFFFFFFFF),
            surface2 = Color(0xFFFDE8EE),
            line = Color(0x142E2226),
            text = Color(0xFF2E2226),
            text2 = Color(0xFF8E7880),
            glow = Color(0x5CFFAABE),
        ),
        dark = BgPalette(
            bg = Color(0xFF221519),
            bg2 = Color(0xFF1A1114),
            surface = Color(0xFF2E1F24),
            surface2 = Color(0xFF221519),
            line = Color(0x1AF6E8EC),
            text = Color(0xFFF6E8EC),
            text2 = Color(0xFFA98C95),
            glow = Color(0x24FF8CA0),
        ),
    ),
    BgTheme(
        id = 3, slug = "peach", name = "蜜桃橙",
        light = BgPalette(
            bg = Color(0xFFFFF9F3),
            bg2 = Color(0xFFFFEADA),
            surface = Color(0xFFFFFFFF),
            surface2 = Color(0xFFFFEADA),
            line = Color(0x142D2318),
            text = Color(0xFF2D2318),
            text2 = Color(0xFF8E7C68),
            glow = Color(0x61FFAF7D),
        ),
        dark = BgPalette(
            bg = Color(0xFF211810),
            bg2 = Color(0xFF18110B),
            surface = Color(0xFF2C2116),
            surface2 = Color(0xFF211810),
            line = Color(0x1AF5EBDF),
            text = Color(0xFFF5EBDF),
            text2 = Color(0xFFA89684),
            glow = Color(0x26FFA366),
        ),
    ),
    BgTheme(
        id = 4, slug = "fog", name = "雾灰",
        light = BgPalette(
            bg = Color(0xFFFAFAF9),
            bg2 = Color(0xFFEDEDEB),
            surface = Color(0xFFFFFFFF),
            surface2 = Color(0xFFEDEDEB),
            line = Color(0x14262626),
            text = Color(0xFF262626),
            text2 = Color(0xFF7E7E7E),
            glow = Color(0x3D96918C),
        ),
        dark = BgPalette(
            bg = Color(0xFF1A1A1A),
            bg2 = Color(0xFF141414),
            surface = Color(0xFF242424),
            surface2 = Color(0xFF1A1A1A),
            line = Color(0x1AEDEDED),
            text = Color(0xFFEDEDED),
            text2 = Color(0xFF9A9A9A),
            glow = Color(0x0DFFFFFF),
        ),
    ),
    BgTheme(
        id = 5, slug = "mint", name = "薄荷",
        light = BgPalette(
            bg = Color(0xFFF7FBFA),
            bg2 = Color(0xFFE3F2EE),
            surface = Color(0xFFFFFFFF),
            surface2 = Color(0xFFE3F2EE),
            line = Color(0x141C2A26),
            text = Color(0xFF1C2A26),
            text2 = Color(0xFF6E8680),
            glow = Color(0x4C78C8B4),
        ),
        dark = BgPalette(
            bg = Color(0xFF101A17),
            bg2 = Color(0xFF0C1512),
            surface = Color(0xFF1B2825),
            surface2 = Color(0xFF101A17),
            line = Color(0x1AE4F2ED),
            text = Color(0xFFE4F2ED),
            text2 = Color(0xFF8AA69F),
            glow = Color(0x1F3FD3A5),
        ),
    ),
    BgTheme(
        id = 6, slug = "lavender", name = "薰衣草",
        light = BgPalette(
            bg = Color(0xFFFAF8FC),
            bg2 = Color(0xFFEDE7F6),
            surface = Color(0xFFFFFFFF),
            surface2 = Color(0xFFEDE7F6),
            line = Color(0x14241E30),
            text = Color(0xFF241E30),
            text2 = Color(0xFF7E7590),
            glow = Color(0x47A591D7),
        ),
        dark = BgPalette(
            bg = Color(0xFF171420),
            bg2 = Color(0xFF110F1A),
            surface = Color(0xFF221D32),
            surface2 = Color(0xFF171420),
            line = Color(0x1AEDE8F8),
            text = Color(0xFFEDE8F8),
            text2 = Color(0xFF9A93AE),
            glow = Color(0x21A88CFF),
        ),
    ),
    BgTheme(
        id = 7, slug = "oat", name = "燕麦",
        light = BgPalette(
            bg = Color(0xFFFCFAF4),
            bg2 = Color(0xFFF0E8D6),
            surface = Color(0xFFFFFFFF),
            surface2 = Color(0xFFF0E8D6),
            line = Color(0x142B2820),
            text = Color(0xFF2B2820),
            text2 = Color(0xFF88806C),
            glow = Color(0x4CC8AF82),
        ),
        dark = BgPalette(
            bg = Color(0xFF1C1811),
            bg2 = Color(0xFF15120C),
            surface = Color(0xFF282218),
            surface2 = Color(0xFF1C1811),
            line = Color(0x1AF0E8DA),
            text = Color(0xFFF0E8DA),
            text2 = Color(0xFFA09680),
            glow = Color(0x1FE0A65E),
        ),
    ),
    BgTheme(
        id = 8, slug = "sky", name = "天空蓝",
        light = BgPalette(
            bg = Color(0xFFF8FAFD),
            bg2 = Color(0xFFE6EEF8),
            surface = Color(0xFFFFFFFF),
            surface2 = Color(0xFFE6EEF8),
            line = Color(0x141E2733),
            text = Color(0xFF1E2733),
            text2 = Color(0xFF6C7C90),
            glow = Color(0x478CAFE6),
        ),
        dark = BgPalette(
            bg = Color(0xFF101620),
            bg2 = Color(0xFF0C1119),
            surface = Color(0xFF1A2330),
            surface2 = Color(0xFF101620),
            line = Color(0x1AE9EEF5),
            text = Color(0xFFE9EEF5),
            text2 = Color(0xFF8A98AB),
            glow = Color(0x215C9DFF),
        ),
    ),
    BgTheme(
        id = 9, slug = "deepsea", name = "深海青",
        light = BgPalette(
            bg = Color(0xFFF5FAFA),
            bg2 = Color(0xFFDFEFEF),
            surface = Color(0xFFFFFFFF),
            surface2 = Color(0xFFDFEFEF),
            line = Color(0x141A2A2C),
            text = Color(0xFF1A2A2C),
            text2 = Color(0xFF6B8487),
            glow = Color(0x476EB9C3),
        ),
        dark = BgPalette(
            bg = Color(0xFF0E181A),
            bg2 = Color(0xFF0A1315),
            surface = Color(0xFF172426),
            surface2 = Color(0xFF0E181A),
            line = Color(0x1AE3F0F2),
            text = Color(0xFFE3F0F2),
            text2 = Color(0xFF87A3A7),
            glow = Color(0x1F3FC3D6),
        ),
    ),
    BgTheme(
        id = 10, slug = "sakura", name = "樱绯",
        light = BgPalette(
            bg = Color(0xFFFFF7F9),
            bg2 = Color(0xFFFBE3EA),
            surface = Color(0xFFFFFFFF),
            surface2 = Color(0xFFFBE3EA),
            line = Color(0x142E1F26),
            text = Color(0xFF2E1F26),
            text2 = Color(0xFF8C7480),
            glow = Color(0x52F596B4),
        ),
        dark = BgPalette(
            bg = Color(0xFF211317),
            bg2 = Color(0xFF190F12),
            surface = Color(0xFF2D1D23),
            surface2 = Color(0xFF211317),
            line = Color(0x1AF6E6EC),
            text = Color(0xFFF6E6EC),
            text2 = Color(0xFFA98A96),
            glow = Color(0x24FF6E9C),
        ),
    ),
    BgTheme(
        id = 11, slug = "lemon", name = "柠檬黄",
        light = BgPalette(
            bg = Color(0xFFFFFDF3),
            bg2 = Color(0xFFFBF0CF),
            surface = Color(0xFFFFFFFF),
            surface2 = Color(0xFFFBF0CF),
            line = Color(0x142C2A1A),
            text = Color(0xFF2C2A1A),
            text2 = Color(0xFF8B8465),
            glow = Color(0x57F0CD6E),
        ),
        dark = BgPalette(
            bg = Color(0xFF1D1B11),
            bg2 = Color(0xFF161409),
            surface = Color(0xFF2A2718),
            surface2 = Color(0xFF1D1B11),
            line = Color(0x1AF2EFDC),
            text = Color(0xFFF2EFDC),
            text2 = Color(0xFFA8A183),
            glow = Color(0x21F5C542),
        ),
    ),
    BgTheme(
        id = 12, slug = "matcha", name = "抹茶",
        light = BgPalette(
            bg = Color(0xFFF9FBF3),
            bg2 = Color(0xFFE8F0D6),
            surface = Color(0xFFFFFFFF),
            surface2 = Color(0xFFE8F0D6),
            line = Color(0x14242B18),
            text = Color(0xFF242B18),
            text2 = Color(0xFF7A8560),
            glow = Color(0x4CAFCD78),
        ),
        dark = BgPalette(
            bg = Color(0xFF161A11),
            bg2 = Color(0xFF10130C),
            surface = Color(0xFF22271A),
            surface2 = Color(0xFF161A11),
            line = Color(0x1AECF2E0),
            text = Color(0xFFECF2E0),
            text2 = Color(0xFF9BA487),
            glow = Color(0x1FA3D45C),
        ),
    ),
    BgTheme(
        id = 13, slug = "dune", name = "沙丘",
        light = BgPalette(
            bg = Color(0xFFFCF8F2),
            bg2 = Color(0xFFEFE2D0),
            surface = Color(0xFFFFFFFF),
            surface2 = Color(0xFFEFE2D0),
            line = Color(0x142B2318),
            text = Color(0xFF2B2318),
            text2 = Color(0xFF8B7C68),
            glow = Color(0x52D2AA82),
        ),
        dark = BgPalette(
            bg = Color(0xFF1C1710),
            bg2 = Color(0xFF15110B),
            surface = Color(0xFF282017),
            surface2 = Color(0xFF1C1710),
            line = Color(0x1AF2EAE0),
            text = Color(0xFFF2EAE0),
            text2 = Color(0xFFA2947F),
            glow = Color(0x1FE0915A),
        ),
    ),
    BgTheme(
        id = 14, slug = "coral", name = "珊瑚",
        light = BgPalette(
            bg = Color(0xFFFFF8F5),
            bg2 = Color(0xFFFCE4DB),
            surface = Color(0xFFFFFFFF),
            surface2 = Color(0xFFFCE4DB),
            line = Color(0x142E221D),
            text = Color(0xFF2E221D),
            text2 = Color(0xFF8D786F),
            glow = Color(0x57FFA082),
        ),
        dark = BgPalette(
            bg = Color(0xFF211512),
            bg2 = Color(0xFF190F0D),
            surface = Color(0xFF2D1F1A),
            surface2 = Color(0xFF211512),
            line = Color(0x1AF6E9E4),
            text = Color(0xFFF6E9E4),
            text2 = Color(0xFFAA9086),
            glow = Color(0x24FF8B6B),
        ),
    ),
    BgTheme(
        id = 15, slug = "berry", name = "莓紫",
        light = BgPalette(
            bg = Color(0xFFFBF7FC),
            bg2 = Color(0xFFF0E3F3),
            surface = Color(0xFFFFFFFF),
            surface2 = Color(0xFFF0E3F3),
            line = Color(0x14281F2C),
            text = Color(0xFF281F2C),
            text2 = Color(0xFF83728A),
            glow = Color(0x4CC891D7),
        ),
        dark = BgPalette(
            bg = Color(0xFF1A121C),
            bg2 = Color(0xFF140E16),
            surface = Color(0xFF261B29),
            surface2 = Color(0xFF1A121C),
            line = Color(0x1AF1E6F5),
            text = Color(0xFFF1E6F5),
            text2 = Color(0xFF9E8EA6),
            glow = Color(0x21D07CF0),
        ),
    ),
    BgTheme(
        id = 16, slug = "inkblue", name = "墨蓝",
        light = BgPalette(
            bg = Color(0xFFF8F9FB),
            bg2 = Color(0xFFE4E8F0),
            surface = Color(0xFFFFFFFF),
            surface2 = Color(0xFFE4E8F0),
            line = Color(0x141B2130),
            text = Color(0xFF1B2130),
            text2 = Color(0xFF6D7788),
            glow = Color(0x427891D7),
        ),
        dark = BgPalette(
            bg = Color(0xFF0E1420),
            bg2 = Color(0xFF0A0F18),
            surface = Color(0xFF172134),
            surface2 = Color(0xFF0E1420),
            line = Color(0x1AE8ECF6),
            text = Color(0xFFE8ECF6),
            text2 = Color(0xFF8592A8),
            glow = Color(0x217B94FF),
        ),
    ),
    BgTheme(
        id = 17, slug = "darkrose", name = "暗樱",
        light = BgPalette(
            bg = Color(0xFFFDF7F9),
            bg2 = Color(0xFFF6E4EC),
            surface = Color(0xFFFFFFFF),
            surface2 = Color(0xFFF6E4EC),
            line = Color(0x142A1E23),
            text = Color(0xFF2A1E23),
            text2 = Color(0xFF87717A),
            glow = Color(0x47DC96AF),
        ),
        dark = BgPalette(
            bg = Color(0xFF1C1114),
            bg2 = Color(0xFF150C0F),
            surface = Color(0xFF2A191F),
            surface2 = Color(0xFF1C1114),
            line = Color(0x1AF4E6EB),
            text = Color(0xFFF4E6EB),
            text2 = Color(0xFFA58A94),
            glow = Color(0x21E8789C),
        ),
    ),
    BgTheme(
        id = 18, slug = "graphite", name = "石墨",
        light = BgPalette(
            bg = Color(0xFFF7F7F8),
            bg2 = Color(0xFFE5E5E8),
            surface = Color(0xFFFFFFFF),
            surface2 = Color(0xFFE5E5E8),
            line = Color(0x141F1F22),
            text = Color(0xFF1F1F22),
            text2 = Color(0xFF74747C),
            glow = Color(0x388C8C96),
        ),
        dark = BgPalette(
            bg = Color(0xFF121214),
            bg2 = Color(0xFF0D0D0F),
            surface = Color(0xFF1E1E21),
            surface2 = Color(0xFF121214),
            line = Color(0x1AECECEF),
            text = Color(0xFFECECEF),
            text2 = Color(0xFF8E8E96),
            glow = Color(0x1AFF9455),
        ),
    ),
    BgTheme(
        id = 19, slug = "viola", name = "紫罗兰夜",
        light = BgPalette(
            bg = Color(0xFFF9F8FD),
            bg2 = Color(0xFFE7E4F6),
            surface = Color(0xFFFFFFFF),
            surface2 = Color(0xFFE7E4F6),
            line = Color(0x14211E2E),
            text = Color(0xFF211E2E),
            text2 = Color(0xFF7A748E),
            glow = Color(0x479682DC),
        ),
        dark = BgPalette(
            bg = Color(0xFF121020),
            bg2 = Color(0xFF0D0B17),
            surface = Color(0xFF1D1A2E),
            surface2 = Color(0xFF121020),
            line = Color(0x1AEAE6F8),
            text = Color(0xFFEAE6F8),
            text2 = Color(0xFF928CAB),
            glow = Color(0x249B7CFF),
        ),
    ),
    BgTheme(
        id = 20, slug = "mocha", name = "暖咖啡",
        light = BgPalette(
            bg = Color(0xFFFCF9F6),
            bg2 = Color(0xFFEFE1D6),
            surface = Color(0xFFFFFFFF),
            surface2 = Color(0xFFEFE1D6),
            line = Color(0x142A211A),
            text = Color(0xFF2A211A),
            text2 = Color(0xFF8A7666),
            glow = Color(0x4CC39673),
        ),
        dark = BgPalette(
            bg = Color(0xFF1A1410),
            bg2 = Color(0xFF130F0C),
            surface = Color(0xFF261D17),
            surface2 = Color(0xFF1A1410),
            line = Color(0x1AF1E7DD),
            text = Color(0xFFF1E7DD),
            text2 = Color(0xFFA2907F),
            glow = Color(0x1FCE8A55),
        ),
    ),
)

/**
 * 当前生效的背景色；返回 null 表示用回皮肤自带配色（选择器的第 1 项「默认」）。
 *
 * 读的是 [CustomizationStore.current]，所以换色时所有引用了它的页面会自动重组，
 * 不需要在各处传参。
 */
@Composable
fun currentBgPalette(dark: Boolean): BgPalette? {
    val id = CustomizationStore.current.bgColorTheme
    if (id !in 1..BgThemes.size) return null
    return BgThemes[id - 1].palette(dark)
}
