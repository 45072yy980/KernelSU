package me.diksu.ultra.foxsu.ui.screen.glass

import me.diksu.ultra.foxsu.ui.component.bottombar.useSideDock
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import me.diksu.ultra.R
import me.diksu.ultra.foxsu.data.settings.CustomizationStore
import me.diksu.ultra.foxsu.ui.LocalUiMode
import me.diksu.ultra.foxsu.ui.screen.settings.SettingsScreenActions
import me.diksu.ultra.foxsu.ui.screen.settings.SettingsUiState
import me.diksu.ultra.foxsu.ui.theme.glassTokens
import me.diksu.ultra.foxsu.ui.uiModeLabelRes

/** .srow：20 圆角玻璃行 —— chip 38 + sT 13.5/700 + sS 10 faint + 右侧 st/tog/chevron */
private data class GlassSettingRow(
    val iconRes: Int,
    val title: String,
    val subtitle: String,
    val accent: Color,
    val stText: String? = null,
    val stColor: Color? = null,
    val checked: Boolean? = null,
    val showChevron: Boolean = false,
    val onClick: () -> Unit = {},
)

@Composable
private fun GlassSettingRowComposable(row: GlassSettingRow) {
    val t = glassTokens()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 10.dp)
            .glassCard(20.dp)
            .clickable(onClick = row.onClick)
            .padding(horizontal = 15.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(t.chipBg)
                .border(1.dp, t.chipBorder, RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(row.iconRes),
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = row.accent,
            )
        }
        Spacer(Modifier.width(13.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                row.title,
                fontSize = 13.5.sp,
                fontWeight = FontWeight.Bold,
                color = t.ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (row.subtitle.isNotBlank()) {
                Text(
                    row.subtitle,
                    fontSize = 10.sp,
                    color = t.faint,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .padding(top = 2.dp)
                        .marquee(),
                )
            }
        }
        when {
            row.stText != null -> {
                Spacer(Modifier.width(10.dp))
                GlassStBadge(text = row.stText, color = row.stColor ?: t.lime)
            }
            row.checked != null -> {
                Spacer(Modifier.width(10.dp))
                GlassToggle(checked = row.checked, onClick = row.onClick)
            }
            row.showChevron -> {
                Spacer(Modifier.width(6.dp))
                Text("›", fontSize = 17.sp, color = t.faint)
            }
        }
    }
}

@Composable
private fun GlassSettingGroup(title: String, rows: List<GlassSettingRow>) {
    Column {
        GlassSectionHead(title)
        rows.forEach { GlassSettingRowComposable(it) }
    }
}

/**
 * Glass 设置页整页 —— 严格按设计稿 ④ 设置：
 *   顶栏 violet 狐头像（19px 标题 + 副标题）+ 搜索按钮
 *   通用：检查更新 / 检查模块更新
 *   外观：界面风格 / 背景与壁纸 / 字体 / 底部导航栏
 *   安全与调试：传统 SU 命令支持 / 隐藏 SELinux 修改 / SU 日志
 */
@Composable
fun GlassSettingsList(
    uiState: SettingsUiState,
    actions: SettingsScreenActions,
    bottomInnerPadding: Dp,
    onOpenCustomization: () -> Unit = {},
    onOpenSulog: () -> Unit = {},
) {
    var showStylePicker by remember { mutableStateOf(false) }
    val t = glassTokens()

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = if (useSideDock()) 96.dp else 13.dp, end = 13.dp, top = glassStatusBarTop() + 4.dp, bottom = bottomInnerPadding + 92.dp),
    ) {
        item {
            GlassTopBar(
                title = stringResource(R.string.fx_settings),
                subtitle = stringResource(R.string.fx_glass_settings_sub),
                titleSize = 19,
                accent = t.violet,
                trailing = {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        GlassTopButton(iconRes = R.drawable.fx_ic_d_palette, onClick = { showStylePicker = true })
                        // 这里原来还有一颗搜索图标，但 Glass 设置页没有搜索实现，按下去只会
                        // 弹不出任何东西。宁可少一颗图标，也不留按不动的按钮。
                    }
                },
            )
        }
        item {
            GlassSettingGroup(
                title = stringResource(R.string.fx_glass_group_general),
                rows = listOf(
                    GlassSettingRow(
                        iconRes = R.drawable.fx_ic_d_download,
                        title = stringResource(R.string.fx_settings_check_update),
                        subtitle = stringResource(
                            R.string.fx_glass_version_fmt,
                            me.diksu.ultra.BuildConfig.VERSION_NAME
                        ),
                        accent = t.cyan,
                        checked = uiState.checkUpdate,
                        onClick = { actions.onSetCheckUpdate(!uiState.checkUpdate) },
                    ),
                    GlassSettingRow(
                        iconRes = R.drawable.fx_ic_d_refresh,
                        title = stringResource(R.string.fx_settings_module_check_update),
                        subtitle = stringResource(R.string.fx_settings_check_update_summary),
                        accent = t.amber,
                        checked = uiState.checkModuleUpdate,
                        onClick = { actions.onSetCheckModuleUpdate(!uiState.checkModuleUpdate) },
                    ),
                    GlassSettingRow(
                        iconRes = R.drawable.fx_ic_d_power2,
                        title = stringResource(R.string.fx_settings_soft_reboot),
                        subtitle = stringResource(R.string.fx_settings_soft_reboot_summary),
                        accent = t.flame,
                        checked = uiState.useSoftReboot,
                        onClick = { actions.onSetUseSoftReboot(!uiState.useSoftReboot) },
                    ),
                    GlassSettingRow(
                        iconRes = R.drawable.fx_ic_d_hammer,
                        title = stringResource(R.string.fx_settings_auto_jailbreak),
                        subtitle = stringResource(R.string.fx_settings_auto_jailbreak_summary),
                        accent = t.lime,
                        checked = uiState.autoJailbreak,
                        onClick = { actions.onSetAutoJailbreak(!uiState.autoJailbreak) },
                    ),
                ),
            )
        }
        item {
            GlassSettingGroup(
                title = stringResource(R.string.fx_glass_group_appearance),
                rows = listOf(
                    GlassSettingRow(
                        iconRes = R.drawable.fx_ic_d_palette,
                        title = stringResource(R.string.fx_settings_ui_mode),
                        subtitle = stringResource(uiModeLabelRes(LocalUiMode.current)),
                        accent = t.pink,
                        showChevron = true,
                        onClick = { showStylePicker = true },
                    ),
                    GlassSettingRow(
                        iconRes = R.drawable.fx_ic_d_image,
                        title = stringResource(R.string.fx_customization_title),
                        subtitle = stringResource(R.string.fx_customization_entry_summary),
                        accent = t.violet,
                        showChevron = true,
                        onClick = onOpenCustomization,
                    ),
                    GlassSettingRow(
                        iconRes = R.drawable.fx_ic_d_grid,
                        title = stringResource(R.string.fx_settings_profile_template),
                        subtitle = stringResource(R.string.fx_settings_profile_template_summary),
                        accent = t.cyan,
                        showChevron = true,
                        onClick = actions.onOpenProfileTemplate,
                    ),
                ),
            )
        }
        item {
            GlassSettingGroup(
                title = stringResource(R.string.fx_glass_group_security),
                rows = listOf(
                    GlassSettingRow(
                        iconRes = R.drawable.fx_ic_d_terminal,
                        title = stringResource(R.string.fx_settings_sucompat),
                        subtitle = uiState.suCompatStatus.ifBlank {
                            stringResource(R.string.fx_settings_sucompat_summary)
                        },
                        accent = t.lime,
                        stText = if (uiState.isSuEnabled) stringResource(R.string.fx_glass_st_on)
                        else stringResource(R.string.fx_glass_st_off),
                        stColor = if (uiState.isSuEnabled) t.lime else t.faint,
                    ),
                    GlassSettingRow(
                        iconRes = R.drawable.fx_ic_d_shield2,
                        title = stringResource(R.string.fx_settings_kernel_umount),
                        subtitle = uiState.kernelUmountStatus.ifBlank {
                            stringResource(R.string.fx_settings_kernel_umount_summary)
                        },
                        accent = t.violet,
                        checked = uiState.isKernelUmountEnabled,
                        onClick = { actions.onSetKernelUmountEnabled(!uiState.isKernelUmountEnabled) },
                    ),
                    GlassSettingRow(
                        iconRes = R.drawable.fx_ic_d_lock,
                        title = stringResource(R.string.fx_settings_selinux_hide),
                        subtitle = uiState.selinuxHideStatus.ifBlank {
                            stringResource(R.string.fx_settings_selinux_hide_summary)
                        },
                        accent = t.blue,
                        checked = uiState.isSelinuxHideEnabled,
                        onClick = { actions.onSetSelinuxHideEnabled(!uiState.isSelinuxHideEnabled) },
                    ),
                    GlassSettingRow(
                        iconRes = R.drawable.fx_ic_d_log,
                        title = stringResource(R.string.fx_settings_sulog),
                        subtitle = stringResource(R.string.fx_settings_sulog_summary),
                        accent = t.amber,
                        showChevron = true,
                        onClick = onOpenSulog,
                    ),
                    GlassSettingRow(
                        iconRes = R.drawable.fx_ic_d_android,
                        title = stringResource(R.string.fx_settings_adb_root),
                        subtitle = uiState.adbRootStatus.ifBlank {
                            stringResource(R.string.fx_settings_adb_root_summary)
                        },
                        accent = t.mint,
                        checked = uiState.isAdbRootEnabled,
                        onClick = { actions.onSetAdbRootEnabled(!uiState.isAdbRootEnabled) },
                    ),
                    GlassSettingRow(
                        iconRes = R.drawable.fx_ic_d_terminal,
                        title = stringResource(R.string.fx_settings_shell_su_adb_only),
                        subtitle = uiState.shellSuAdbOnlyStatus.ifBlank {
                            stringResource(R.string.fx_settings_shell_su_adb_only_summary)
                        },
                        accent = t.cyan,
                        checked = uiState.isShellSuAdbOnlyEnabled,
                        onClick = { actions.onSetShellSuAdbOnlyEnabled(!uiState.isShellSuAdbOnlyEnabled) },
                    ),
                ),
            )
        }
        item {
            GlassSettingGroup(
                title = stringResource(R.string.fx_glass_group_advanced),
                rows = listOf(
                    GlassSettingRow(
                        iconRes = R.drawable.fx_ic_d_grid,
                        title = stringResource(R.string.fx_settings_umount_modules_default),
                        subtitle = stringResource(R.string.fx_settings_umount_modules_default_summary),
                        accent = t.pink,
                        checked = uiState.isDefaultUmountModules,
                        onClick = { actions.onSetDefaultUmountModules(!uiState.isDefaultUmountModules) },
                    ),
                    GlassSettingRow(
                        iconRes = R.drawable.fx_ic_d_tool,
                        title = stringResource(R.string.fx_enable_web_debugging),
                        subtitle = stringResource(R.string.fx_enable_web_debugging_summary),
                        accent = t.blue,
                        checked = uiState.enableWebDebugging,
                        onClick = { actions.onSetEnableWebDebugging(!uiState.enableWebDebugging) },
                    ),
                    GlassSettingRow(
                        iconRes = R.drawable.fx_ic_d_book,
                        title = stringResource(R.string.fx_about),
                        subtitle = "FoxSu Glass",
                        accent = t.violet,
                        showChevron = true,
                        onClick = actions.onOpenAbout,
                    ),
                ),
            )
        }
    }

    if (showStylePicker) {
        GlassStylePickerDialog(
            current = LocalUiMode.current,
            onDismiss = { showStylePicker = false },
        )
    }
}
