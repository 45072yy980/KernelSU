package me.diksu.ultra.ui.screen.settings

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import me.diksu.ultra.R
import me.diksu.ultra.foxsu.data.settings.Customization
import me.diksu.ultra.foxsu.data.settings.CustomizationStore
import me.diksu.ultra.foxsu.ui.component.ambient.FX_NAMES
import me.diksu.ultra.foxsu.ui.component.dockfx.DockFx
import me.diksu.ultra.foxsu.ui.component.bottombar.DockIconPacks
import me.diksu.ultra.foxsu.ui.theme.BgThemes
import me.diksu.ultra.ui.LocalUiMode
import me.diksu.ultra.ui.isFoxSuStyle
import me.diksu.ultra.foxsu.ui.LocalUiMode as FoxsuHostUiMode
import me.diksu.ultra.foxsu.ui.UiMode as FoxsuHostStyle

/**
 * 「自定义外观」分组的数据来源。
 *
 * 这一组项原来住在 FoxSu 自己的定制页（`foxsu/ui/screen/ag/AgCustomization.kt` 等），
 * 我们现在把它们搬进管理器的设置页，但**不搬家它们的取值**：
 * 能直接复用 FoxSu 数据类里现成名单的（底栏特效、图标包、背景色主题、背景特效）就地复用，
 * 只有底栏样式那张表是定制页里的 private 常量，只能在这里镜像一份。
 *
 * 名单里的名字都是 FoxSu 数据类自带的中文字面量，和它自己的选择器完全一致——
 * 本轮不做本地化，改这里的名字会让同一个选项在两种界面里叫法不同。
 */
internal val FX_DOCK_STYLES: List<Pair<String, String>> = listOf(
    "bubble" to "珊瑚气泡", "film" to "胶片孔",
    "neon" to "霓虹管", "frost" to "玻璃叠层",
    "focus" to "单点聚焦", "sticker" to "云朵贴纸",
    "track" to "滑轨卡片",
    "d0" to "原有胶囊", "v1" to "展开药丸", "v5" to "侧边抽屉",
    "v6" to "流光光标", "v7" to "描边霓虹", "v8" to "方块卡片",
    "v9" to "渐变实底", "v10" to "顶指示条", "v11" to "浮起圆片",
    "center" to "中央头像",
    "editorial" to "纸面墨线", "pages" to "白色药丸",
    "glass" to "液态药丸", "nocturne" to "暗夜霓虹",
)

/**
 * 卡面配色的显示名，**顺序必须和 [CustomizationStore.cardColorOptions] 逐项对齐**。
 *
 * 那边只存 ARGB、没有名字（原来是色块选择器），所以名字只能在这里补。
 * 以后那边增删颜色，这里要跟着改，否则下拉框会显示错位。
 */
internal val FX_CARD_COLOR_LABELS: List<Int> = listOf(
    R.string.fx_custom_follow_theme,
    R.string.fx_custom_color_white,
    R.string.fx_custom_color_mist_green,
    R.string.fx_custom_color_mist_blue,
    R.string.fx_custom_color_ink,
    R.string.fx_custom_color_indigo,
    R.string.fx_custom_color_sand,
    R.string.fx_custom_color_blossom,
    R.string.fx_custom_color_mint,
)

/** 底栏样式：21 套，返回展示名；下标即 [FX_DOCK_STYLES] 的序号。 */
@Composable
internal fun fxDockStyleLabels(): List<String> = FX_DOCK_STYLES.map { it.second }

/** 底栏样式当前值在下拉框里的下标；存的是编码（"d0" / "glass" …）而不是序号。 */
internal fun fxDockStyleIndex(dockStyle: String): Int =
    FX_DOCK_STYLES.indexOfFirst { it.first == dockStyle }.coerceAtLeast(0)

/** 底栏图标：0 = 原生（跟随皮肤），1..20 = FoxSu 的 20 套造型。 */
@Composable
internal fun fxDockIconLabels(): List<String> =
    listOf(stringResource(R.string.fx_custom_native)) + DockIconPacks.packs.map { it.name }

/** 栏内特效：0 = 关闭，1..20 = [DockFx.inners]。 */
@Composable
internal fun fxDockFxInnerLabels(): List<String> =
    listOf(stringResource(R.string.fx_custom_off)) + DockFx.inners.map { it.label }

/** 外圈特效：0 = 关闭，1..20 = [DockFx.rings]。 */
@Composable
internal fun fxDockFxRingLabels(): List<String> =
    listOf(stringResource(R.string.fx_custom_off)) + DockFx.rings.map { it.label }

/** 背景色主题：0 = 皮肤自带配色，1..20 = [BgThemes]。 */
@Composable
internal fun fxBgThemeLabels(): List<String> =
    listOf(stringResource(R.string.fx_custom_theme_default)) + BgThemes.map { it.name }

/** 背景特效：0 = 猫爪（原有的氛围层），1..20 = [FX_NAMES]。 */
@Composable
internal fun fxBgFxLabels(): List<String> =
    listOf(stringResource(R.string.fx_custom_bg_fx_paw)) + FX_NAMES

/**
 * 卡面配色：与 [CustomizationStore.cardColorOptions] 同序。
 *
 * 用 for 循环而不是 `map {}`：`stringResource` 是 @Composable，只能在本函数体内直接调用，
 * 不能塞进非 @Composable 的 lambda（项目里踩过的坑）。
 */
@Composable
internal fun fxCardColorLabels(): List<String> {
    val labels = ArrayList<String>(FX_CARD_COLOR_LABELS.size)
    for (res in FX_CARD_COLOR_LABELS) {
        labels += stringResource(res)
    }
    return labels
}

/** 卡面配色当前值在下拉框里的下标；用户选过色块以外的自定义色时退回「跟随主题」。 */
internal fun fxCardColorIndex(cardColor: Int): Int =
    CustomizationStore.cardColorOptions.indexOf(cardColor).coerceAtLeast(0)

/** 字体当前值。0/1/2/3 = 内置字体，4 = 用户导入的字体文件。 */
@Composable
internal fun fxFontLabel(fontStyle: Int): String = stringResource(
    when (fontStyle) {
        1 -> R.string.fx_custom_font_serif
        2 -> R.string.fx_custom_font_mono
        3 -> R.string.fx_custom_font_wenkai
        4 -> R.string.fx_custom_font_imported
        else -> R.string.fx_custom_font_system
    }
)

/** 首页标题当前值：留空就是应用名。 */
@Composable
internal fun fxTitleLabel(homeTitle: String): String =
    if (homeTitle.isBlank()) stringResource(R.string.fx_custom_title_default) else homeTitle

/**
 * 卡面配图的现状：首页主卡一张、四张 KPI、逐模块卡各一张，都是可选图。
 *
 * 这一轮只显示现状、不给选择器——选图要走系统图库 + 裁切对话框，
 * 和 FoxSu 定制页那套 `ImageCropDialog` 绑在一起，等下一块再接。
 */
@Composable
internal fun fxCardImageSummary(custom: Customization): String {
    val count = listOfNotNull(custom.heroWallpaperUri).size +
        custom.kpiImages.count { it != null } +
        custom.moduleImages.size
    return if (count == 0) {
        stringResource(R.string.fx_custom_image_none)
    } else {
        stringResource(R.string.fx_custom_image_count, count)
    }
}

/**
 * 当前是否处于 Fresh / Glass / Nocturne 三种风格。
 *
 * 主判断用我们自己的 [LocalUiMode]。再兜一层 FoxSu 侧的 `LocalUiMode`：新风格下设置页
 * 可能是在 `FoxSuHost` 自己的导航里渲染的，那种情况下提供的是 FoxSu 那套
 * CompositionLocal，我们这份还是默认值（Miuix）。两个默认值都是 Miuix，
 * 所以老风格下这条兜底恒为 false，不会把置灰逻辑弄反。
 */
@Composable
internal fun fxStyleActive(): Boolean {
    if (LocalUiMode.current.isFoxSuStyle) return true
    return when (FoxsuHostUiMode.current) {
        FoxsuHostStyle.Fresh, FoxsuHostStyle.Glass, FoxsuHostStyle.Nocturne -> true
        else -> false
    }
}
