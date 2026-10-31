package me.diksu.ultra.foxsu.ui.theme

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import me.diksu.ultra.foxsu.data.settings.CustomizationStore

/**
 * 全局强调色（Brand Accent）。
 *
 * 七套配色取自 FoxSu 的狐狸 logo：紫罗兰、樱花粉、珊瑚橙、蜜糖黄、薄荷绿、晴空蓝、梦幻彩虹。
 * 每一套给三个色阶（起 → 中 → 止），足够撑起「渐变按钮 / 选中态 / 角标 / 描边」的全部用法。
 *
 * 这里的取值刻意比 logo 上的粉彩色更饱和一档：logo 用的是 60% 明度的粉彩做插画，
 * 直接拿来做文字和小图标会糊在浅色卡面上；同一色相抬高明度对比后才既能当强调色、
 * 又能当渐变端。
 */
data class AccentPalette(
    val id: String,
    val label: String,
    /** 主强调色：选中态、按钮、标题渐变起点 */
    val primary: Color,
    /** 渐变中段 */
    val mid: Color,
    /** 次强调色：渐变终点、角标、点缀 */
    val secondary: Color,
    /** 亮色下的选中底衬（极淡同色相） */
    val soft: Color,
    /** 暗色下的选中底衬（低透明度同色相） */
    val softDark: Color,
)

val AccentPalettes: List<AccentPalette> = listOf(
    AccentPalette(
        id = "violet", label = "紫罗兰",
        primary = Color(0xFF6C5CE7), mid = Color(0xFFA855F7), secondary = Color(0xFFEC4899),
        soft = Color(0xFFEDEAFB), softDark = Color(0x2E6C5CE7),
    ),
    AccentPalette(
        id = "sakura", label = "樱花粉",
        primary = Color(0xFFEC4899), mid = Color(0xFFF472B6), secondary = Color(0xFFFF7AA8),
        soft = Color(0xFFFDE8F1), softDark = Color(0x33EC4899),
    ),
    AccentPalette(
        id = "coral", label = "珊瑚橙",
        primary = Color(0xFFF97316), mid = Color(0xFFFB923C), secondary = Color(0xFFF43F5E),
        soft = Color(0xFFFDEEE3), softDark = Color(0x33F97316),
    ),
    AccentPalette(
        id = "honey", label = "蜜糖黄",
        primary = Color(0xFFE9A81C), mid = Color(0xFFF7C948), secondary = Color(0xFFF97316),
        soft = Color(0xFFFCF3DA), softDark = Color(0x33E9A81C),
    ),
    AccentPalette(
        id = "mint", label = "薄荷绿",
        primary = Color(0xFF10B981), mid = Color(0xFF34D399), secondary = Color(0xFF14B8A6),
        soft = Color(0xFFE3F8EF), softDark = Color(0x3310B981),
    ),
    AccentPalette(
        id = "sky", label = "晴空蓝",
        primary = Color(0xFF3B82F6), mid = Color(0xFF60A5FA), secondary = Color(0xFF6366F1),
        soft = Color(0xFFE7F0FE), softDark = Color(0x333B82F6),
    ),
    AccentPalette(
        id = "rainbow", label = "梦幻彩虹",
        primary = Color(0xFF8B5CF6), mid = Color(0xFF38BDF8), secondary = Color(0xFFF472B6),
        soft = Color(0xFFF0E9FE), softDark = Color(0x338B5CF6),
    ),
)

fun accentAt(index: Int): AccentPalette = AccentPalettes[index.coerceIn(0, AccentPalettes.lastIndex)]

/**
 * 用一个自定义主色造一整套配色。
 *
 * 只让用户挑一个颜色，其余三个色阶由它推导：
 * - mid：同色相、饱和度略降、明度抬高一档（渐变的中间站）
 * - secondary：色相 +42°，避免渐变两端撞色（紫→粉、蓝→紫的观感）
 * - soft / softDark：同一色相的极淡底衬，用于选中态背景
 */
fun accentFromColor(argb: Int): AccentPalette {
    val hsv = FloatArray(3)
    android.graphics.Color.colorToHSV(argb, hsv)
    val (h, s, v) = Triple(hsv[0], hsv[1].coerceIn(0.25f, 1f), hsv[2].coerceIn(0.25f, 1f))
    fun of(hh: Float, ss: Float, vv: Float): Color =
        Color(android.graphics.Color.HSVToColor(floatArrayOf(((hh % 360f) + 360f) % 360f, ss, vv)))
    val primary = Color(argb)
    return AccentPalette(
        id = "custom",
        label = "自定义",
        primary = primary,
        mid = of(h + 18f, (s * 0.78f), (v * 1.06f).coerceAtMost(1f)),
        secondary = of(h + 46f, (s * 1.02f).coerceAtMost(1f), (v * 0.98f)),
        soft = of(h, 0.14f, 0.97f),
        softDark = Color(android.graphics.Color.HSVToColor(floatArrayOf(h, 0.55f, v))).copy(alpha = 0.22f),
    )
}

/**
 * 当前生效的强调色。
 *
 * 读的是 [CustomizationStore.current]（Compose 的 MutableState），
 * 所以在任何 Composable 里读到它都会自动订阅，换色即时全量重绘。
 */
val currentAccent: AccentPalette
    get() {
        val c = CustomizationStore.current
        return if (c.accentCustom != 0) accentFromColor(c.accentCustom)
        else accentAt(c.accentPalette)
    }

/** 品牌三色渐变（135°），供按钮、滑块、选中指示条统一取用。 */
fun accentBrush(p: AccentPalette = currentAccent): Brush =
    Brush.linearGradient(listOf(p.primary, p.mid, p.secondary))

/** 两色渐变，面积小的元素（细指示条、描边光）用它更干净。 */
fun accentBrush2(p: AccentPalette = currentAccent): Brush =
    Brush.linearGradient(listOf(p.primary, p.secondary))
