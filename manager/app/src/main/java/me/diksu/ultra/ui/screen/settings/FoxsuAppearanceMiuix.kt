package me.diksu.ultra.ui.screen.settings

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.BlurOn
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Colorize
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.Style
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.ViewCarousel
import androidx.compose.material.icons.rounded.Wallpaper
import androidx.compose.material.icons.rounded.WaterDrop
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import me.diksu.ultra.R
import me.diksu.ultra.foxsu.data.settings.CustomizationStore
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.OverlayDropdownPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 设置页的「自定义外观」分组（Miuix 美化版 / 原版共用一套行）。
 *
 * 里面装的是 Fresh / Glass / Nocturne 三种 FoxSu 风格的底层可调项：底栏结构、底栏特效、
 * 图标造型、画布配色、背景装饰、卡面配色、字体、标题。**不另开设置页**——用户在我们的
 * 设置页里就能改完，和界面风格、主题这些现有条目排在一起。
 *
 * 三种老风格（Miuix / MiuixStock / Material）下整组可见但置灰，并给一句说明：
 * 这些项对老皮肤没有任何作用，藏起来反而会让人以为功能没了。
 *
 * 取值全部走 [CustomizationStore]（底层是 `settings` 偏好里的 `fx_*` 键），
 * 所以改完立刻生效：FoxSu 那批渲染代码读的就是同一个 [CustomizationStore.current]。
 */
@Composable
internal fun FoxsuAppearanceGroupMiuix() {
    val enabled = fxStyleActive()
    val custom = CustomizationStore.current

    val dockStyles = fxDockStyleLabels()
    val dockIcons = fxDockIconLabels()
    val dockFxInners = fxDockFxInnerLabels()
    val dockFxRings = fxDockFxRingLabels()
    val bgThemes = fxBgThemeLabels()
    val bgFxs = fxBgFxLabels()
    val cardColors = fxCardColorLabels()

    val dockStyleTitle = stringResource(R.string.fx_custom_dock_style)
    val dockIconTitle = stringResource(R.string.fx_custom_dock_icon)
    val dockFxTitle = stringResource(R.string.fx_custom_dock_fx)
    val dockFxInnerTitle = stringResource(R.string.fx_custom_dock_fx_inner)
    val dockFxRingTitle = stringResource(R.string.fx_custom_dock_fx_ring)
    val bgThemeTitle = stringResource(R.string.fx_custom_bg_theme)
    val bgFxTitle = stringResource(R.string.fx_custom_bg_fx)
    val bgFxStyleTitle = stringResource(R.string.fx_custom_bg_fx_style)
    val cardColorTitle = stringResource(R.string.fx_custom_card_color)
    val cardImageTitle = stringResource(R.string.fx_custom_card_image)
    val fontTitle = stringResource(R.string.fx_custom_font)
    val titleTitle = stringResource(R.string.fx_custom_title)

    SmallTitle(
        text = stringResource(R.string.fx_custom_section),
        modifier = Modifier.padding(top = 12.dp),
    )
    if (!enabled) {
        // 说明放在卡片外面：置灰的卡片里再塞一行灰字，等于让用户去点一个点不动的东西。
        Text(
            text = stringResource(R.string.fx_custom_only_for_foxsu_styles),
            modifier = Modifier.padding(start = 12.dp, end = 12.dp, top = 6.dp),
            color = MiuixTheme.colorScheme.onBackground.copy(alpha = 0.6f),
            style = MiuixTheme.textStyles.footnote1,
        )
    }
    Card(
        modifier = Modifier
            .padding(top = 12.dp)
            .fillMaxWidth(),
    ) {
        OverlayDropdownPreference(
            title = dockStyleTitle,
            summary = stringResource(R.string.fx_custom_dock_style_summary),
            items = dockStyles,
            startAction = {
                Icon(
                    Icons.Rounded.Tune,
                    modifier = Modifier.padding(end = 6.dp),
                    contentDescription = dockStyleTitle,
                    tint = MiuixTheme.colorScheme.onBackground,
                )
            },
            enabled = enabled,
            selectedIndex = fxDockStyleIndex(custom.dockStyle),
            onSelectedIndexChange = { index ->
                FX_DOCK_STYLES.getOrNull(index)?.let { CustomizationStore.setDockStyle(it.first) }
            },
        )
        OverlayDropdownPreference(
            title = dockIconTitle,
            summary = stringResource(R.string.fx_custom_dock_icon_summary),
            items = dockIcons,
            startAction = {
                Icon(
                    Icons.Rounded.ViewCarousel,
                    modifier = Modifier.padding(end = 6.dp),
                    contentDescription = dockIconTitle,
                    tint = MiuixTheme.colorScheme.onBackground,
                )
            },
            enabled = enabled,
            selectedIndex = custom.dockIconPack.coerceIn(0, dockIcons.lastIndex),
            onSelectedIndexChange = { index -> CustomizationStore.setDockIconPack(index) },
        )
        SwitchPreference(
            title = dockFxTitle,
            summary = stringResource(R.string.fx_custom_dock_fx_summary),
            startAction = {
                Icon(
                    Icons.Rounded.Bolt,
                    modifier = Modifier.padding(end = 6.dp),
                    contentDescription = dockFxTitle,
                    tint = MiuixTheme.colorScheme.onBackground,
                )
            },
            checked = custom.dockFxOn,
            enabled = enabled,
            onCheckedChange = { CustomizationStore.setDockFxOn(it) },
        )
        OverlayDropdownPreference(
            title = dockFxInnerTitle,
            items = dockFxInners,
            startAction = {
                Icon(
                    Icons.Rounded.BlurOn,
                    modifier = Modifier.padding(end = 6.dp),
                    contentDescription = dockFxInnerTitle,
                    tint = MiuixTheme.colorScheme.onBackground,
                )
            },
            // 总开关关掉时这两个选择器没有意义，一并置灰。
            enabled = enabled && custom.dockFxOn,
            selectedIndex = custom.dockFxInner.coerceIn(0, dockFxInners.lastIndex),
            onSelectedIndexChange = { index -> CustomizationStore.setDockFxInner(index) },
        )
        OverlayDropdownPreference(
            title = dockFxRingTitle,
            items = dockFxRings,
            startAction = {
                Icon(
                    Icons.Rounded.AutoAwesome,
                    modifier = Modifier.padding(end = 6.dp),
                    contentDescription = dockFxRingTitle,
                    tint = MiuixTheme.colorScheme.onBackground,
                )
            },
            enabled = enabled && custom.dockFxOn,
            selectedIndex = custom.dockFxRing.coerceIn(0, dockFxRings.lastIndex),
            onSelectedIndexChange = { index -> CustomizationStore.setDockFxRing(index) },
        )
        OverlayDropdownPreference(
            title = bgThemeTitle,
            summary = stringResource(R.string.fx_custom_bg_theme_summary),
            items = bgThemes,
            startAction = {
                Icon(
                    Icons.Rounded.Palette,
                    modifier = Modifier.padding(end = 6.dp),
                    contentDescription = bgThemeTitle,
                    tint = MiuixTheme.colorScheme.onBackground,
                )
            },
            enabled = enabled,
            selectedIndex = custom.bgColorTheme.coerceIn(0, bgThemes.lastIndex),
            onSelectedIndexChange = { index -> CustomizationStore.setBgColorTheme(index) },
        )
        SwitchPreference(
            title = bgFxTitle,
            summary = stringResource(R.string.fx_custom_bg_fx_summary),
            startAction = {
                Icon(
                    Icons.Rounded.WaterDrop,
                    modifier = Modifier.padding(end = 6.dp),
                    contentDescription = bgFxTitle,
                    tint = MiuixTheme.colorScheme.onBackground,
                )
            },
            checked = custom.bgFxOn,
            enabled = enabled,
            onCheckedChange = { CustomizationStore.setBgFxOn(it) },
        )
        OverlayDropdownPreference(
            title = bgFxStyleTitle,
            items = bgFxs,
            startAction = {
                Icon(
                    Icons.Rounded.Star,
                    modifier = Modifier.padding(end = 6.dp),
                    contentDescription = bgFxStyleTitle,
                    tint = MiuixTheme.colorScheme.onBackground,
                )
            },
            enabled = enabled && custom.bgFxOn,
            selectedIndex = custom.bgFxStyle.coerceIn(0, bgFxs.lastIndex),
            onSelectedIndexChange = { index -> CustomizationStore.setBgFxStyle(index) },
        )
        OverlayDropdownPreference(
            title = cardColorTitle,
            summary = stringResource(R.string.fx_custom_card_color_summary),
            items = cardColors,
            startAction = {
                Icon(
                    Icons.Rounded.Colorize,
                    modifier = Modifier.padding(end = 6.dp),
                    contentDescription = cardColorTitle,
                    tint = MiuixTheme.colorScheme.onBackground,
                )
            },
            enabled = enabled,
            selectedIndex = fxCardColorIndex(custom.cardColor),
            onSelectedIndexChange = { index ->
                CustomizationStore.cardColorOptions.getOrNull(index)
                    ?.let { CustomizationStore.setCardColor(it) }
            },
        )
        // 下面三项本轮只展示现状（选图 / 换字体文件 / 输入标题都不在同一条管线上），
        // 一律置灰：给一个点了没反应的行，比明说「暂不可改」更糟。
        ArrowPreference(
            title = fontTitle,
            summary = stringResource(R.string.fx_custom_current, fxFontLabel(custom.fontStyle)),
            startAction = {
                Icon(
                    Icons.Rounded.Style,
                    modifier = Modifier.padding(end = 6.dp),
                    contentDescription = fontTitle,
                    tint = MiuixTheme.colorScheme.onBackground,
                )
            },
            enabled = false,
            onClick = { },
        )
        ArrowPreference(
            title = titleTitle,
            summary = stringResource(
                R.string.fx_custom_current,
                fxTitleLabel(custom.homeTitle),
            ),
            startAction = {
                Icon(
                    Icons.Rounded.Description,
                    modifier = Modifier.padding(end = 6.dp),
                    contentDescription = titleTitle,
                    tint = MiuixTheme.colorScheme.onBackground,
                )
            },
            enabled = false,
            onClick = { },
        )
        ArrowPreference(
            title = cardImageTitle,
            summary = stringResource(R.string.fx_custom_current, fxCardImageSummary(custom)),
            startAction = {
                Icon(
                    Icons.Rounded.Wallpaper,
                    modifier = Modifier.padding(end = 6.dp),
                    contentDescription = cardImageTitle,
                    tint = MiuixTheme.colorScheme.onBackground,
                )
            },
            enabled = false,
            onClick = { },
        )
    }
}
