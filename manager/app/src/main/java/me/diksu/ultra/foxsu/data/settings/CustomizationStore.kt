package me.diksu.ultra.foxsu.data.settings

import android.content.Context
import androidx.core.content.edit
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import me.diksu.ultra.ksuApp

/**
 * User personalisation of the ColorUI look: wallpaper, card surfaces and launcher icon.
 *
 * Backed by SharedPreferences so it survives restarts, and exposed as a [StateFlow] so the
 * home screen repaints immediately while the settings screen is being used.
 */
data class Customization(
    val wallpaperUri: String? = null,
    /** 1f = fill, larger values zoom in (custom cropping). */
    val wallpaperScale: Float = 1f,
    /** -0.4f..0.4f, horizontal pan of the wallpaper. */
    val wallpaperOffsetX: Float = 0f,
    /** -0.4f..0.4f, vertical pan of the wallpaper. */
    val wallpaperOffsetY: Float = 0f,
    /** 0f..0.8f black scrim over the wallpaper so text stays readable. */
    val wallpaperDim: Float = 0.15f,
    /** Optional picture used as the backdrop of the home hero cards. */
    val heroWallpaperUri: String? = null,
    val heroScale: Float = 1f,
    val heroOffsetX: Float = 0f,
    val heroOffsetY: Float = 0f,
    /** 0f..0.8f black scrim over the hero picture, keeps text readable. */
    val heroDim: Float = 0.35f,
    /** Pictures for the four home KPI cards, index 0..3, null = follow the card surface. */
    val kpiImages: List<String?> = List(KPI_CARD_COUNT) { null },
    /** Opacity of each KPI card picture, 0.2f..1f. */
    val kpiAlphas: List<Float> = List(KPI_CARD_COUNT) { 0.7f },
    /** Per-module card background picture, keyed by module id. */
    val moduleImages: Map<String, String> = emptyMap(),
    /** Opacity of the module card pictures. */
    val moduleImageAlpha: Float = 0.7f,
    /**
     * Per-card surface override: slot 0 is the hero card, 1..4 are the KPI cards.
     * 0 = follow the global card colour.
     */
    val surfaceColors: List<Int> = List(SURFACE_SLOT_COUNT) { 0 },
    /** Per-card opacity override, -1f = follow the global card opacity. */
    val surfaceAlphas: List<Float> = List(SURFACE_SLOT_COUNT) { -1f },
    /** 0 = follow the theme scheme, otherwise an ARGB color. */
    val cardColor: Int = 0,
    /** 0.2f..1f, applied on top of [cardColor] (or the themed card surface). */
    val cardAlpha: Float = 1f,
    /** Title shown at the top of the home screen; blank = the app name. */
    val homeTitle: String = "",
    /** 首页标题文字颜色：0 = 跟随主题（Ag 主题下是品牌渐变），否则 ARGB。 */
    val titleColor: Int = 0,
    /** 0 = pill bar, 1 = drawer bar that expands the selected tab. */
    val bottomBarStyle: Int = 0,
    val dockStyle: String = "d0",
    /**
     * Editorial（Material 皮肤）是否使用 Redesign 的胶囊底栏。
     * 默认 false：Editorial 保留自己的「纸面」底栏——胶囊是 Redesign 的语言，
     * 强行套在杂志版面上会破坏版式（也容易被误认成主题 BUG）。
     */
    val dockOnEditorial: Boolean = false,
    /** Tint the four home KPI cards with the built-in palette (light mode only). */
    val homePalette: Boolean = true,
    /** Corner radius of the floating bottom bar, in dp. 38 已是完整药丸（76dp 高的一半）。 */
    val bottomBarRadius: Float = 38f,
    /** 鸿蒙式沉浸光感：底栏加一层柔光渐变，更通透。 */
    val bottomBarImmersive: Boolean = false,
    /** 底栏液态玻璃：玻璃条 + 可拖动高光，完全覆盖沉浸光感。 */
    val bottomBarLiquidGlass: Boolean = false,
    // 底栏图标七彩：每个 tab 用一套随机彩色（默认关）
    val navRainbow: Boolean = false,
    // 底栏图标莫奈取色：跟随系统动态色（默认关）
    val navMonet: Boolean = false,
    /** 底栏不透明度，1f = 完全不透明（默认）。 */
    val bottomBarAlpha: Float = 1f,
    /** 玻璃质感卡面：接近全透明的白色薄片 + 白色描边。 */
    val cardGlass: Boolean = false,
    /** 是否启用全局背景图。 */
    val wallpaperEnabled: Boolean = true,
    /** 玻璃卡面专用背景图；空 = 使用系统壁纸。 */
    val glassWallpaper: String? = null,
    /** 七彩卡片：首页/模块卡全部换成柔和的七彩配色。 */
    val rainbowCards: Boolean = true,
    /** 0 = 系统默认，1 = 衬线，2 = 等宽，3 = 手写圆体。 */
    val fontStyle: Int = 0,
    /** 用户导入的系统字体文件（应用私有目录内的绝对路径）。 */
    val fontPath: String? = null,
    /** Dock 图标造型：100 圆角方（默认）/ 101 正圆 / 102 超椭圆 / 103 七彩（渐变实底）。 */
    /** 桌面启动器图标形象：0 彩虹狐狸 / 1 KernelSU / 2 招手 / 3 回眸 / 4 开心。 */
    val launcherIcon: Int = 0,
    /** 全局强调色：0..6 对应 AccentPalettes（7 套取自 logo 的配色），默认珊瑚橙。 */
    val accentPalette: Int = 2,
    /** 自定义强调色（ARGB）；非 0 时覆盖 [accentPalette]。 */
    val accentCustom: Int = 0,
    /**
     * 背景色主题：0 = 皮肤自带配色，1..20 对应
     * [me.diksu.ultra.foxsu.ui.theme.BgThemes]（Theme Kit v1.0 的 20 套，每套自带亮暗两版）。
     *
     * 和 [accentPalette] 是两条正交的轴：这里只换画布、卡片、描边、正文与柔光，
     * 强调色仍由 [accentPalette] / [accentCustom] 决定。用哪一版（亮/暗）
     * 取决于外观模式，不单独存。
     */
    val bgColorTheme: Int = 0,
    /** 侧边胶囊停靠：0 = 左侧（默认），1 = 右侧。 */
    val dockSide: Int = 0,
    /**
     * 胶囊按钮造型：0..9 对应 DockButton 的 10 套按钮样式。
     * （旧版这里是「图标风格」圆角/正圆/超椭圆/七彩，已整体升级为按钮形状。）
     */
    val dockButton: Int = 1,
    /** 胶囊自动收起：0 = 关闭；3..60 = 无操作多少秒后沉到屏幕下方。 */
    val dockAutoHide: Int = 0,
    /** 传统底栏：false = 胶囊（默认），true = 通栏传统底栏。 */
    val classicDock: Boolean = false,
    /**
     * 传统底栏的结构：0 = 经典（发丝线 + 顶部短条），1..20 对应
     * [me.diksu.ultra.foxsu.ui.component.bottombar.ClassicDockStyle] 的 Dock Kit 结构。
     *
     * 和 [dockButton]（胶囊按钮造型）是两条正交的轴：那边管浮岛形态下的按钮，
     * 这边管贴底通栏这条栏本身。配色全部跟随全局强调色，不写死。
     */
    val classicDockStyle: Int = 0,
    /**
     * 胶囊底栏「水滴」流动指示器。
     *
     * 开启后选中项背后的胶囊色块不再原地淡入淡出，而是从前一格「流」到后一格：
     * 前缘先到、后缘慢半拍，中途被拉长、高度略压扁，最后带一点回弹停稳；
     * 与此同时新选中项图标先缩小再弹回、页面内容沿点击方向轻推淡入。
     * 默认 false——411ms 的编排对敏感人群不友好，也让低端机多一帧成本。
     */
    val dockWaterDrop: Boolean = false,

    /**
     * 切 Tab 时页面「沿方向轻推 + 淡入」。
     *
     * 从 [dockWaterDrop] 里拆出来的：色块拉长是给眼睛看的形变，
     * 页面轻推是给空间感看的位移，两者审美取向不同——
     * 有人只想让色块不动、页面照常滑入，所以必须能单独关。
     * 默认 true，保持原本「打开水滴就整套动效」的观感。
     */
    val dockPagePush: Boolean = true,

    /**
     * 主页装饰层总开关：控制「背景特效」选择器是否生效。
     *
     * 关掉后主页、捐赠页、定制页头卡三处的浮层一起停，选择器里选了什么仍会记住。
     * 老字段 `homeAmbient` 已并入这里——原来只有一个「漂浮爪印」开关，
     * 加了 20 套背景特效之后两者共用一个槽位，分成两个开关只会让用户对不上账。
     */
    val bgFxOn: Boolean = true,

    /**
     * 背景特效选择：0 = 猫爪（原氛围层），1..20 对应 [me.diksu.ultra.foxsu.ui.component.ambient.FX_NAMES]。
     *
     * 猫爪放在 0 位而不是额外开一个布尔，是因为它们互斥——同一时刻主页只需要一种氛围，
     * 选背景特效时再叠一层猫爪会互相抢注意力。
     */
    val bgFxStyle: Int = 0,

    /**
     * 全场特效档位（0 关闭 / 1 克制 / 2 全开）。
     *
     * 统一管三类动效：卡片外轮廓粒子边、按压内阴影、3D 触摸倾斜。
     * 默认 2——新装用户直接看到完整效果；老版本升级上来的人读到默认值也是 2，
     * 因为之前这些特效本来就一直在跑。
     */
    val fxLevel: Int = 2,

    /**
     * 底栏特效总开关。关掉后栏内与边框两套都不播，只留系统点击反馈。
     *
     * 和 [fxLevel] 分开是有意的：[fxLevel] 管的是全局性能档（卡片粒子、3D 倾斜），
     * 有人只嫌底栏花，不该被迫把卡片动效一起关掉。
     */
    val dockFxOn: Boolean = true,

    /** 栏内特效：0 = 关闭，1..20 对应 [me.diksu.ultra.foxsu.ui.component.dockfx.DockFx.inners]。 */
    val dockFxInner: Int = 0,

    /** 外圈特效：0 = 关闭，1..20 对应同文件的 rings。 */
    val dockFxRing: Int = 0,

    /**
     * 底栏图标风格：0 = 原生（跟随皮肤），1..20 对应
     * [me.diksu.ultra.foxsu.ui.component.bottombar.DockIconPacks.packs]。
     *
     * 只换形状不换颜色——着色仍走 `Icon(tint = ...)` 的 ColorFilter，
     * 所以强调色、选中态这些既有逻辑一行都不用改。
     */
    val dockIconPack: Int = 0,
    /**
     * 「凸起中间」底栏中央那颗头像的本地图片路径（长按可换）。
     * 只存路径不存 Uri：GET_CONTENT 的读权限是临时的，重启后 Uri 就打不开了。
     */
    val donateAvatar: String? = null,
)

/** Number of equal-sized KPI cards on the ColorUI home screen. */
const val KPI_CARD_COUNT = 4

/** 七彩配色：依次覆盖大卡、四张 KPI 与模块卡。 */
val RainbowCards = listOf(
    0xFFDDEBFA.toInt() to 0xFF17324D.toInt(), // 雾蓝
    0xFFE2F5E6.toInt() to 0xFF14401F.toInt(), // 薄荷
    0xFFFFF3D2.toInt() to 0xFF4A3710.toInt(), // 奶油黄
    0xFFFFE4E0.toInt() to 0xFF4A1F1A.toInt(), // 蜜桃
    0xFFEFE6FB.toInt() to 0xFF2E2148.toInt(), // 薰衣草
    0xFFDFF3F3.toInt() to 0xFF0F3A3A.toInt(), // 湖水
    0xFFFCE8F3.toInt() to 0xFF4A1733.toInt(), // 樱粉
    0xFFE8F1D8.toInt() to 0xFF2F3A12.toInt(), // 青柠
    0xFFEDE9DC.toInt() to 0xFF3A3418.toInt(), // 沙金
    0xFFF6E3D2.toInt() to 0xFF431F0C.toInt(), // 焦糖
    0xFFE0E7F7.toInt() to 0xFF1C2550.toInt(), // 靛蓝
    0xFFF2E2F7.toInt() to 0xFF3C1440.toInt(), // 紫罗兰
)

/** 夜间七彩：深色卡片底 + 亮色图标，保证在黑底上也是一眼能认出的彩色卡片。 */
val RainbowCardsDark = listOf(
    0xFF172A3D.toInt() to 0xFF9EC9F0.toInt(), // 雾蓝
    0xFF14301F.toInt() to 0xFF9BE0B4.toInt(), // 薄荷
    0xFF34290F.toInt() to 0xFFF0CE83.toInt(), // 奶油黄
    0xFF381A16.toInt() to 0xFFF2A99C.toInt(), // 蜜桃
    0xFF251A3D.toInt() to 0xFFC4A9F5.toInt(), // 薰衣草
    0xFF10302F.toInt() to 0xFF8ADCD8.toInt(), // 湖水
    0xFF36142A.toInt() to 0xFFF1A6CD.toInt(), // 樱粉
    0xFF232B12.toInt() to 0xFFCBDD97.toInt(), // 青柠
    0xFF2C2814.toInt() to 0xFFE4D69B.toInt(), // 沙金
    0xFF32190E.toInt() to 0xFFEDB489.toInt(), // 焦糖
    0xFF161E42.toInt() to 0xFFA3B4F0.toInt(), // 靛蓝
    0xFF2E1235.toInt() to 0xFFDDA6E8.toInt(), // 紫罗兰
)

/** Hero card + four KPI cards + the module list cards. */
const val SURFACE_SLOT_COUNT = KPI_CARD_COUNT + 2
const val HERO_SLOT = 0
const val MODULE_SLOT = KPI_CARD_COUNT + 1

object CustomizationStore {
    private const val PREFS = "customization"
    private const val KEY_WALLPAPER = "wallpaper_uri"
    private const val KEY_SCALE = "wallpaper_scale"
    private const val KEY_OFFSET_X = "wallpaper_offset_x"
    private const val KEY_OFFSET_Y = "wallpaper_offset_y"
    private const val KEY_DIM = "wallpaper_dim"
    private const val KEY_HERO_URI = "hero_wallpaper_uri"
    private const val KEY_HERO_SCALE = "hero_wallpaper_scale"
    private const val KEY_HERO_OFFSET_X = "hero_wallpaper_offset_x"
    private const val KEY_HERO_OFFSET_Y = "hero_wallpaper_offset_y"
    private const val KEY_HERO_DIM = "hero_wallpaper_dim"
    private const val KEY_KPI_IMAGE = "kpi_image_"
    private const val KEY_KPI_ALPHA = "kpi_alpha_"
    private const val KEY_MODULE_IMAGE_PREFIX = "module_image::"
    private const val KEY_MODULE_IMAGE_ALPHA = "module_image_alpha"
    private const val KEY_SURFACE_COLOR = "surface_color_"
    private const val KEY_SURFACE_ALPHA = "surface_alpha_"
    private const val KEY_CARD_COLOR = "card_color"
    private const val KEY_CARD_ALPHA = "card_alpha"
    private const val KEY_HOME_TITLE = "home_title"
    private const val KEY_TITLE_COLOR = "title_color"
    private const val KEY_BOTTOM_STYLE = "bottom_bar_style"
private const val KEY_DOCK_STYLE = "dock_style"
    private const val KEY_DOCK_EDITORIAL = "dock_on_editorial"
    private const val KEY_HOME_PALETTE = "home_palette"
    private const val KEY_BOTTOM_RADIUS = "bottom_bar_radius"
    private const val KEY_BOTTOM_IMMERSIVE = "bottom_bar_immersive"
    private const val KEY_BOTTOM_LIQUID_GLASS = "bottom_bar_liquid_glass"
    private const val KEY_NAV_RAINBOW = "nav_rainbow"
    private const val KEY_NAV_MONET = "nav_monet"
    private const val KEY_BOTTOM_ALPHA = "bottom_bar_alpha"
    private const val KEY_FONT_PATH = "font_path"
    private const val KEY_CARD_GLASS = "card_glass"
    private const val KEY_WALLPAPER_ENABLED = "wallpaper_enabled"
    private const val KEY_GLASS_WALLPAPER = "glass_wallpaper"
    private const val KEY_RAINBOW = "rainbow_cards"
    private const val KEY_FONT_STYLE = "font_style"
    private const val KEY_ICON_STYLE = "icon_style"
    private const val KEY_LAUNCHER_ICON = "launcher_icon"
    private const val KEY_ACCENT_PALETTE = "accent_palette"
    private const val KEY_ACCENT_CUSTOM = "accent_custom"
    private const val KEY_BG_COLOR_THEME = "bg_color_theme"
    private const val KEY_DOCK_SIDE = "dock_side"
    private const val KEY_DOCK_BUTTON = "dock_button_style"
    private const val KEY_DOCK_AUTOHIDE = "dock_auto_hide"
    private const val KEY_FX_LEVEL = "fx_level"
    private const val KEY_CLASSIC_DOCK = "classic_dock"
    private const val KEY_CLASSIC_DOCK_STYLE = "classic_dock_style"
    private const val KEY_DOCK_WATER_DROP = "dock_water_drop"
    private const val KEY_DOCK_PAGE_PUSH = "dock_page_push"
    /** 已废弃：旧版「漂浮爪印氛围」开关，仅用于一次性迁移到 [KEY_BG_FX_ON]。 */
    private const val KEY_HOME_AMBIENT = "home_ambient"
    private const val KEY_BG_FX_ON = "bg_fx_on"
    private const val KEY_BG_FX_STYLE = "bg_fx_style"
    private const val KEY_DOCK_FX_ON = "dock_fx_on"
    private const val KEY_DOCK_FX_INNER = "dock_fx_inner"
    private const val KEY_DOCK_FX_RING = "dock_fx_ring"
    private const val KEY_DOCK_ICON_PACK = "dock_icon_pack"
    private const val KEY_DONATE_AVATAR = "donate_avatar"
    private const val KEY_BRAND_DEFAULTS = "brand_defaults_v1"

    val cardColorOptions = listOf(
        0x00000000, // follow theme
        0xFFFFFFFF.toInt(),
        0xFFF1F6EF.toInt(),
        0xFFE8F1FB.toInt(),
        0xFF1A201A.toInt(),
        0xFF111827.toInt(),
        0xFFFFF4E5.toInt(),
        0xFFFDE8EF.toInt(),
        0xFFE9F7EF.toInt(),
    )

    private val prefs by lazy {
        ksuApp.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    }

    /**
     * 品牌默认值迁移（珊瑚橙 + 正圆按钮）。
     *
     * 老安装里 `accent_palette=0` / `dock_button=0` 就是「从来没改过」的旧默认值，
     * 直接抬到新默认；只要用户动过（`!= 0`）就原样保留。标记位保证只跑一次。
     */
    private fun migrateBrandDefaults() {
        val p = prefs
        if (p.getBoolean(KEY_BRAND_DEFAULTS, false)) return
        p.edit {
            if (p.getInt(KEY_ACCENT_CUSTOM, 0) == 0 && p.getInt(KEY_ACCENT_PALETTE, 0) == 0) {
                putInt(KEY_ACCENT_PALETTE, 2)
            }
            if (p.getInt(KEY_DOCK_BUTTON, 0) == 0) putInt(KEY_DOCK_BUTTON, 1)
            putBoolean(KEY_BRAND_DEFAULTS, true)
        }
    }

    /** Compose-observable snapshot so every screen repaints as soon as it changes. */
    var current by mutableStateOf(read())
        private set

    private fun read(): Customization = with(prefs) {
        migrateBrandDefaults()
        Customization(
            wallpaperUri = getString(KEY_WALLPAPER, null),
            wallpaperScale = getFloat(KEY_SCALE, 1f),
            wallpaperOffsetX = getFloat(KEY_OFFSET_X, 0f),
            wallpaperOffsetY = getFloat(KEY_OFFSET_Y, 0f),
            wallpaperDim = getFloat(KEY_DIM, 0.15f),
            heroWallpaperUri = getString(KEY_HERO_URI, null),
            heroScale = getFloat(KEY_HERO_SCALE, 1f),
            heroOffsetX = getFloat(KEY_HERO_OFFSET_X, 0f),
            heroOffsetY = getFloat(KEY_HERO_OFFSET_Y, 0f),
            heroDim = getFloat(KEY_HERO_DIM, 0.35f),
            kpiImages = List(KPI_CARD_COUNT) { getString(KEY_KPI_IMAGE + it, null) },
            kpiAlphas = List(KPI_CARD_COUNT) { getFloat(KEY_KPI_ALPHA + it, 0.7f) },
            moduleImages = all.entries
                .filter { it.key.startsWith(KEY_MODULE_IMAGE_PREFIX) && it.value is String }
                .associate { it.key.removePrefix(KEY_MODULE_IMAGE_PREFIX) to it.value as String },
            moduleImageAlpha = getFloat(KEY_MODULE_IMAGE_ALPHA, 0.7f),
            surfaceColors = List(SURFACE_SLOT_COUNT) { getInt(KEY_SURFACE_COLOR + it, 0) },
            surfaceAlphas = List(SURFACE_SLOT_COUNT) { getFloat(KEY_SURFACE_ALPHA + it, -1f) },
            cardColor = getInt(KEY_CARD_COLOR, 0),
            cardAlpha = getFloat(KEY_CARD_ALPHA, 1f),
            homeTitle = getString(KEY_HOME_TITLE, "").orEmpty(),
            titleColor = getInt(KEY_TITLE_COLOR, 0),
            bottomBarStyle = getInt(KEY_BOTTOM_STYLE, 0),
            dockStyle = getString(KEY_DOCK_STYLE, "d0").orEmpty(),
            dockOnEditorial = getBoolean(KEY_DOCK_EDITORIAL, false),
            homePalette = getBoolean(KEY_HOME_PALETTE, true),
            bottomBarRadius = getFloat(KEY_BOTTOM_RADIUS, 38f),
            bottomBarImmersive = getBoolean(KEY_BOTTOM_IMMERSIVE, false),
            bottomBarLiquidGlass = getBoolean(KEY_BOTTOM_LIQUID_GLASS, false),
            navRainbow = getBoolean(KEY_NAV_RAINBOW, false),
            navMonet = getBoolean(KEY_NAV_MONET, false),
            bottomBarAlpha = getFloat(KEY_BOTTOM_ALPHA, 1f),
            cardGlass = getBoolean(KEY_CARD_GLASS, false),
            wallpaperEnabled = getBoolean(KEY_WALLPAPER_ENABLED, true),
            glassWallpaper = getString(KEY_GLASS_WALLPAPER, null),
            rainbowCards = getBoolean(KEY_RAINBOW, true),
            fontStyle = getInt(KEY_FONT_STYLE, 0),
            fontPath = getString(KEY_FONT_PATH, null),
            // 老版本把启动器形象塞在 iconStyle(0/2/3/4) 里，首次升级时继承过来
            // 旧版本把图标形象也存在 icon_style 里（0..4），新版本改用 launcher_icon：
            // 没写过 launcher_icon 就迁移一次，写过就直接用——以前的实现读的是 icon_style，
            // 而它存的是 100..103 的图标形状，takeIf 永远不成立，于是每次启动都回落 0。
            launcherIcon = if (contains(KEY_LAUNCHER_ICON)) {
                getInt(KEY_LAUNCHER_ICON, 0).coerceIn(0, 5)
            } else {
                getInt(KEY_ICON_STYLE, 100).takeIf { it in 0..5 } ?: 0
            },
            accentPalette = getInt(KEY_ACCENT_PALETTE, 2),
            accentCustom = getInt(KEY_ACCENT_CUSTOM, 0),
            bgColorTheme = getInt(KEY_BG_COLOR_THEME, 0).coerceIn(0, 20),
            dockSide = getInt(KEY_DOCK_SIDE, 0),
            dockButton = getInt(KEY_DOCK_BUTTON, 1).coerceIn(0, 9),
            dockAutoHide = getInt(KEY_DOCK_AUTOHIDE, 0).coerceIn(0, 60),
            fxLevel = getInt(KEY_FX_LEVEL, 2).coerceIn(0, 2),
            classicDock = getBoolean(KEY_CLASSIC_DOCK, false),
            classicDockStyle = getInt(KEY_CLASSIC_DOCK_STYLE, 0),
            dockWaterDrop = getBoolean(KEY_DOCK_WATER_DROP, false),
            dockPagePush = getBoolean(KEY_DOCK_PAGE_PUSH, true),
            // 旧版本只有一个「漂浮爪印」开关，升级上来时继承它的值，避免用户关掉的浮层又自动打开
            bgFxOn = if (contains(KEY_BG_FX_ON)) {
                getBoolean(KEY_BG_FX_ON, true)
            } else {
                getBoolean(KEY_HOME_AMBIENT, true)
            },
            bgFxStyle = getInt(KEY_BG_FX_STYLE, 0).coerceIn(0, 20),
            dockFxOn = getBoolean(KEY_DOCK_FX_ON, true),
            dockFxInner = getInt(KEY_DOCK_FX_INNER, 0).coerceIn(0, 20),
            dockFxRing = getInt(KEY_DOCK_FX_RING, 0).coerceIn(0, 20),
            dockIconPack = getInt(KEY_DOCK_ICON_PACK, 0).coerceIn(0, 20),
            donateAvatar = getString(KEY_DONATE_AVATAR, null),
        )
    }

    fun setWallpaper(uri: String?) = update { it.copy(wallpaperUri = uri) }
    fun setScale(scale: Float) = update { it.copy(wallpaperScale = scale) }
    fun setOffsetX(offset: Float) = update { it.copy(wallpaperOffsetX = offset) }
    fun setOffsetY(offset: Float) = update { it.copy(wallpaperOffsetY = offset) }
    fun setDim(dim: Float) = update { it.copy(wallpaperDim = dim) }
    fun setHeroWallpaper(uri: String?) = update { it.copy(heroWallpaperUri = uri) }
    fun setHeroScale(scale: Float) = update { it.copy(heroScale = scale) }
    fun setHeroOffsetX(offset: Float) = update { it.copy(heroOffsetX = offset) }
    fun setHeroOffsetY(offset: Float) = update { it.copy(heroOffsetY = offset) }
    fun setHeroDim(dim: Float) = update { it.copy(heroDim = dim) }
    fun setKpiImage(index: Int, uri: String?) = update {
        if (index !in 0 until KPI_CARD_COUNT) it else it.copy(
            kpiImages = it.kpiImages.toMutableList().also { list -> list[index] = uri }
        )
    }

    fun setKpiAlpha(index: Int, alpha: Float) = update {
        if (index !in 0 until KPI_CARD_COUNT) it else it.copy(
            kpiAlphas = it.kpiAlphas.toMutableList().also { list -> list[index] = alpha }
        )
    }

    fun setSurfaceColor(index: Int, color: Int) = update {
        if (index !in 0 until SURFACE_SLOT_COUNT) it else it.copy(
            surfaceColors = it.surfaceColors.toMutableList().also { list -> list[index] = color }
        )
    }

    fun setSurfaceAlpha(index: Int, alpha: Float) = update {
        if (index !in 0 until SURFACE_SLOT_COUNT) it else it.copy(
            surfaceAlphas = it.surfaceAlphas.toMutableList().also { list -> list[index] = alpha }
        )
    }

    fun setCardColor(color: Int) = update { it.copy(cardColor = color) }
    fun setModuleImage(moduleId: String, uri: String?) = update {
        val images = it.moduleImages.toMutableMap()
        if (uri == null) images.remove(moduleId) else images[moduleId] = uri
        it.copy(moduleImages = images)
    }
    fun setModuleImageAlpha(alpha: Float) = update { it.copy(moduleImageAlpha = alpha) }
    fun setCardAlpha(alpha: Float) = update { it.copy(cardAlpha = alpha) }
    fun setHomeTitle(title: String) = update { it.copy(homeTitle = title.take(24)) }

    /** 0 = 跟随主题；否则写入 ARGB。 */
    fun setTitleColor(argb: Int) = update { it.copy(titleColor = argb) }
    fun setBottomBarStyle(style: Int) = update { it.copy(bottomBarStyle = style) }
    fun setDockStyle(style: String) = update { it.copy(dockStyle = style) }
    fun setDockOnEditorial(enabled: Boolean) = update { it.copy(dockOnEditorial = enabled) }

    fun setHomePalette(enabled: Boolean) = update { it.copy(homePalette = enabled) }
    fun setBottomBarRadius(radius: Float) = update { it.copy(bottomBarRadius = radius) }
    fun setBottomBarImmersive(enabled: Boolean) = update { it.copy(bottomBarImmersive = enabled) }
    fun setBottomBarLiquidGlass(enabled: Boolean) = update { it.copy(bottomBarLiquidGlass = enabled) }
    fun setNavRainbow(enabled: Boolean) = update { it.copy(navRainbow = enabled) }
    fun setNavMonet(enabled: Boolean) = update { it.copy(navMonet = enabled) }
    fun setBottomBarAlpha(alpha: Float) = update { it.copy(bottomBarAlpha = alpha.coerceIn(0.2f, 1f)) }
    fun setCardGlass(enabled: Boolean) = update { it.copy(cardGlass = enabled) }
    fun setWallpaperEnabled(enabled: Boolean) = update { it.copy(wallpaperEnabled = enabled) }
    fun setGlassWallpaper(uri: String?) = update { it.copy(glassWallpaper = uri) }
    fun setRainbowCards(enabled: Boolean) = update { it.copy(rainbowCards = enabled) }
    fun setFontStyle(style: Int) = update { it.copy(fontStyle = style) }
    fun setFontPath(path: String?) = update { it.copy(fontPath = path) }
    fun setLauncherIcon(style: Int) = update { it.copy(launcherIcon = style) }
    fun setAccentPalette(index: Int) =
        update { it.copy(accentPalette = index.coerceIn(0, 6), accentCustom = 0) }

    /** 自定义强调色；传 0 表示回到当前预设配色。 */
    fun setAccentCustom(argb: Int) = update { it.copy(accentCustom = argb) }

    /** 0 = 皮肤自带配色，1..20 = 背景色主题套号。 */
    fun setBgColorTheme(index: Int) = update { it.copy(bgColorTheme = index.coerceIn(0, 20)) }
    /** 0 关闭 / 1 克制 / 2 全开 */
    fun setFxLevel(level: Int) = update { it.copy(fxLevel = level.coerceIn(0, 2)) }
    fun setDockSide(side: Int) = update { it.copy(dockSide = side.coerceIn(0, 1)) }

    fun setDockButton(style: Int) = update { it.copy(dockButton = style.coerceIn(0, 9)) }

    fun setDonateAvatar(path: String?) = update { it.copy(donateAvatar = path) }

    /** 0 = 关闭自动收起；其余为秒数（3..60）。 */
    fun setDockAutoHide(seconds: Int) = update { it.copy(dockAutoHide = seconds.coerceIn(0, 60)) }

    fun setClassicDock(enabled: Boolean) = update { it.copy(classicDock = enabled) }

    fun setClassicDockStyle(style: Int) = update { it.copy(classicDockStyle = style.coerceIn(0, 20)) }

    fun setDockWaterDrop(enabled: Boolean) = update { it.copy(dockWaterDrop = enabled) }

    fun setDockPagePush(enabled: Boolean) = update { it.copy(dockPagePush = enabled) }

    fun setBgFxOn(enabled: Boolean) = update { it.copy(bgFxOn = enabled) }

    /** 0 = 猫爪，1..20 = 背景特效套号。 */
    fun setBgFxStyle(style: Int) = update { it.copy(bgFxStyle = style.coerceIn(0, 20)) }

    fun setDockFxOn(enabled: Boolean) = update { it.copy(dockFxOn = enabled) }

    fun setDockFxInner(index: Int) = update { it.copy(dockFxInner = index.coerceIn(0, 20)) }

    fun setDockFxRing(index: Int) = update { it.copy(dockFxRing = index.coerceIn(0, 20)) }

    fun setDockIconPack(index: Int) = update { it.copy(dockIconPack = index.coerceIn(0, 20)) }

    /**
     * 恢复默认：直接把整份 SharedPreferences 清空再读回默认值。
     *
     * 旧实现只把默认值「写一遍」，凡是没被写到的键（历史遗留 / 后续新增）都会留在里面，
     * 于是「恢复默认」之后仍有若干项保持原样；而且 `update` 遇到与当前值相同就直接返回，
     * 从默认态点重置时连一次写入都不会发生。清表是唯一能真正回到初始状态的写法。
     */
    fun reset() {
        prefs.edit { clear() }
        current = Customization()
    }

    private fun update(transform: (Customization) -> Customization) {
        val next = transform(current)
        // Dragging a colour slider fires many identical values; skip the disk write for them.
        if (next == current) return
        // 逐模块图片：先清掉旧键再写，避免残留已删除模块的图片
        val staleModuleKeys = prefs.all.keys.filter { it.startsWith(KEY_MODULE_IMAGE_PREFIX) }
        prefs.edit {
            putString(KEY_WALLPAPER, next.wallpaperUri)
            putFloat(KEY_SCALE, next.wallpaperScale)
            putFloat(KEY_OFFSET_X, next.wallpaperOffsetX)
            putFloat(KEY_OFFSET_Y, next.wallpaperOffsetY)
            putFloat(KEY_DIM, next.wallpaperDim)
            putString(KEY_HERO_URI, next.heroWallpaperUri)
            putFloat(KEY_HERO_SCALE, next.heroScale)
            putFloat(KEY_HERO_OFFSET_X, next.heroOffsetX)
            putFloat(KEY_HERO_OFFSET_Y, next.heroOffsetY)
            putFloat(KEY_HERO_DIM, next.heroDim)
            repeat(KPI_CARD_COUNT) { index ->
                putString(KEY_KPI_IMAGE + index, next.kpiImages.getOrNull(index))
                putFloat(KEY_KPI_ALPHA + index, next.kpiAlphas.getOrNull(index) ?: 0.7f)
            }
            repeat(SURFACE_SLOT_COUNT) { index ->
                putInt(KEY_SURFACE_COLOR + index, next.surfaceColors.getOrNull(index) ?: 0)
                putFloat(KEY_SURFACE_ALPHA + index, next.surfaceAlphas.getOrNull(index) ?: -1f)
            }
            putInt(KEY_CARD_COLOR, next.cardColor)
            putFloat(KEY_CARD_ALPHA, next.cardAlpha)
            staleModuleKeys.forEach { remove(it) }
            next.moduleImages.forEach { (id, uri) -> putString(KEY_MODULE_IMAGE_PREFIX + id, uri) }
            putFloat(KEY_MODULE_IMAGE_ALPHA, next.moduleImageAlpha)
            putString(KEY_HOME_TITLE, next.homeTitle)
            putInt(KEY_TITLE_COLOR, next.titleColor)
            putInt(KEY_BOTTOM_STYLE, next.bottomBarStyle)
            putString(KEY_DOCK_STYLE, next.dockStyle)
            putBoolean(KEY_DOCK_EDITORIAL, next.dockOnEditorial)
            putBoolean(KEY_HOME_PALETTE, next.homePalette)
            putFloat(KEY_BOTTOM_RADIUS, next.bottomBarRadius)
            putBoolean(KEY_BOTTOM_IMMERSIVE, next.bottomBarImmersive)
            putBoolean(KEY_BOTTOM_LIQUID_GLASS, next.bottomBarLiquidGlass)
            putBoolean(KEY_NAV_RAINBOW, next.navRainbow)
            putBoolean(KEY_NAV_MONET, next.navMonet)
            putFloat(KEY_BOTTOM_ALPHA, next.bottomBarAlpha)
            putBoolean(KEY_CARD_GLASS, next.cardGlass)
            putBoolean(KEY_WALLPAPER_ENABLED, next.wallpaperEnabled)
            putString(KEY_GLASS_WALLPAPER, next.glassWallpaper)
            putBoolean(KEY_RAINBOW, next.rainbowCards)
            putInt(KEY_FONT_STYLE, next.fontStyle)
            putString(KEY_FONT_PATH, next.fontPath)
            putInt(KEY_LAUNCHER_ICON, next.launcherIcon)
            putInt(KEY_ACCENT_PALETTE, next.accentPalette)
            putInt(KEY_ACCENT_CUSTOM, next.accentCustom)
            putInt(KEY_BG_COLOR_THEME, next.bgColorTheme)
            putInt(KEY_DOCK_SIDE, next.dockSide)
            putInt(KEY_DOCK_BUTTON, next.dockButton)
            putInt(KEY_DOCK_AUTOHIDE, next.dockAutoHide)
            putInt(KEY_FX_LEVEL, next.fxLevel)
            putBoolean(KEY_CLASSIC_DOCK, next.classicDock)
            putInt(KEY_CLASSIC_DOCK_STYLE, next.classicDockStyle)
            putBoolean(KEY_DOCK_WATER_DROP, next.dockWaterDrop)
            putBoolean(KEY_DOCK_PAGE_PUSH, next.dockPagePush)
            putBoolean(KEY_BG_FX_ON, next.bgFxOn)
            putInt(KEY_BG_FX_STYLE, next.bgFxStyle)
            putBoolean(KEY_DOCK_FX_ON, next.dockFxOn)
            putInt(KEY_DOCK_FX_INNER, next.dockFxInner)
            putInt(KEY_DOCK_FX_RING, next.dockFxRing)
            putInt(KEY_DOCK_ICON_PACK, next.dockIconPack)
            putString(KEY_DONATE_AVATAR, next.donateAvatar)
        }
        current = next
    }
}
