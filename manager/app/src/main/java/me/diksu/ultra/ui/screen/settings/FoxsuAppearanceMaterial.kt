package me.diksu.ultra.ui.screen.settings

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Brightness
import androidx.compose.material.icons.filled.ColorLens
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.TextSnippet
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Wallpaper
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import me.diksu.ultra.R
import me.diksu.ultra.foxsu.data.settings.CustomizationStore
import me.diksu.ultra.ui.component.material.SegmentedColumn
import me.diksu.ultra.ui.component.material.SegmentedDropdownItem
import me.diksu.ultra.ui.component.material.SegmentedListItem
import me.diksu.ultra.ui.component.material.SegmentedSwitchItem

/**
 * 「自定义外观」分组的 Material 3 版：同样的条目、同样的取值，只换成 Material 行。
 *
 * 与 Miuix 版共用 [CustomizationStore] 与 [FX_DOCK_STYLES] 等数据，所以两套皮肤下
 * 改的是同一份设置；老风格（Miuix / MiuixStock / Material）下整组置灰并给一句说明。
 */
@Composable
internal fun FoxsuAppearanceGroupMaterial() {
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

    FxSectionTitle(stringResource(R.string.fx_custom_section))
    if (!enabled) {
        Text(
            text = stringResource(R.string.fx_custom_only_for_foxsu_styles),
            modifier = Modifier.padding(start = 24.dp, end = 24.dp, bottom = 6.dp),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    SegmentedColumn(
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 13.dp),
        content = buildList {
            add {
                SegmentedDropdownItem(
                    icon = Icons.Filled.Tune,
                    title = dockStyleTitle,
                    summary = stringResource(R.string.fx_custom_dock_style_summary),
                    items = dockStyles,
                    enabled = enabled,
                    selectedIndex = fxDockStyleIndex(custom.dockStyle),
                    onItemSelected = { index ->
                        FX_DOCK_STYLES.getOrNull(index)?.let { CustomizationStore.setDockStyle(it.first) }
                    },
                )
            }
            add {
                SegmentedDropdownItem(
                    icon = Icons.Filled.GridView,
                    title = dockIconTitle,
                    summary = stringResource(R.string.fx_custom_dock_icon_summary),
                    items = dockIcons,
                    enabled = enabled,
                    selectedIndex = custom.dockIconPack.coerceIn(0, dockIcons.lastIndex),
                    onItemSelected = { index -> CustomizationStore.setDockIconPack(index) },
                )
            }
            add {
                SegmentedSwitchItem(
                    icon = Icons.Filled.FlashOn,
                    title = dockFxTitle,
                    summary = stringResource(R.string.fx_custom_dock_fx_summary),
                    checked = custom.dockFxOn,
                    enabled = enabled,
                    onCheckedChange = { CustomizationStore.setDockFxOn(it) },
                )
            }
            add {
                SegmentedDropdownItem(
                    icon = Icons.Filled.FilterList,
                    title = dockFxInnerTitle,
                    items = dockFxInners,
                    enabled = enabled && custom.dockFxOn,
                    selectedIndex = custom.dockFxInner.coerceIn(0, dockFxInners.lastIndex),
                    onItemSelected = { index -> CustomizationStore.setDockFxInner(index) },
                )
            }
            add {
                SegmentedDropdownItem(
                    icon = Icons.Filled.Favorite,
                    title = dockFxRingTitle,
                    items = dockFxRings,
                    enabled = enabled && custom.dockFxOn,
                    selectedIndex = custom.dockFxRing.coerceIn(0, dockFxRings.lastIndex),
                    onItemSelected = { index -> CustomizationStore.setDockFxRing(index) },
                )
            }
            add {
                SegmentedDropdownItem(
                    icon = Icons.Filled.Palette,
                    title = bgThemeTitle,
                    summary = stringResource(R.string.fx_custom_bg_theme_summary),
                    items = bgThemes,
                    enabled = enabled,
                    selectedIndex = custom.bgColorTheme.coerceIn(0, bgThemes.lastIndex),
                    onItemSelected = { index -> CustomizationStore.setBgColorTheme(index) },
                )
            }
            add {
                SegmentedSwitchItem(
                    icon = Icons.Filled.WaterDrop,
                    title = bgFxTitle,
                    summary = stringResource(R.string.fx_custom_bg_fx_summary),
                    checked = custom.bgFxOn,
                    enabled = enabled,
                    onCheckedChange = { CustomizationStore.setBgFxOn(it) },
                )
            }
            add {
                SegmentedDropdownItem(
                    icon = Icons.Filled.Brightness,
                    title = bgFxStyleTitle,
                    items = bgFxs,
                    enabled = enabled && custom.bgFxOn,
                    selectedIndex = custom.bgFxStyle.coerceIn(0, bgFxs.lastIndex),
                    onItemSelected = { index -> CustomizationStore.setBgFxStyle(index) },
                )
            }
            add {
                SegmentedDropdownItem(
                    icon = Icons.Filled.ColorLens,
                    title = cardColorTitle,
                    summary = stringResource(R.string.fx_custom_card_color_summary),
                    items = cardColors,
                    enabled = enabled,
                    selectedIndex = fxCardColorIndex(custom.cardColor),
                    onItemSelected = { index ->
                        CustomizationStore.cardColorOptions.getOrNull(index)
                            ?.let { CustomizationStore.setCardColor(it) }
                    },
                )
            }
            // 与 Miuix 版一致：选图 / 换字体 / 输入标题本轮只展示现状，一律置灰。
            add {
                SegmentedListItem(
                    onClick = { },
                    enabled = false,
                    headlineContent = { Text(fontTitle) },
                    supportingContent = {
                        Text(stringResource(R.string.fx_custom_current, fxFontLabel(custom.fontStyle)))
                    },
                    leadingContent = { Icon(Icons.Filled.TextSnippet, fontTitle) },
                )
            }
            add {
                SegmentedListItem(
                    onClick = { },
                    enabled = false,
                    headlineContent = { Text(titleTitle) },
                    supportingContent = {
                        Text(
                            stringResource(
                                R.string.fx_custom_current,
                                fxTitleLabel(custom.homeTitle),
                            )
                        )
                    },
                    leadingContent = { Icon(Icons.Filled.Description, titleTitle) },
                )
            }
            add {
                SegmentedListItem(
                    onClick = { },
                    enabled = false,
                    headlineContent = { Text(cardImageTitle) },
                    supportingContent = {
                        Text(stringResource(R.string.fx_custom_current, fxCardImageSummary(custom)))
                    },
                    leadingContent = { Icon(Icons.Filled.Wallpaper, cardImageTitle) },
                )
            }
        }
    )
}

/** 与 [SettingsMaterial] 里的分组标题同款；那边的是文件私有，这里补一个同名版式的。 */
@Composable
private fun FxSectionTitle(text: String) {
    Text(
        text = text,
        modifier = Modifier.padding(start = 24.dp, top = 16.dp, bottom = 6.dp),
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
    )
}
