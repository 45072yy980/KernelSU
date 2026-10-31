package me.diksu.ultra.foxsu.ui.screen.editorial

import me.diksu.ultra.foxsu.ui.component.bottombar.useSideDock
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
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
import me.diksu.ultra.foxsu.ui.uiModeLabelRes

private data class EdSettingItem(
    val index: String,
    val title: String,
    val value: String,
    val sw: Boolean? = null,
    val chevron: Boolean = false,
    val onClick: () -> Unit = {},
)

private data class EdGroup(val number: String, val title: String, val items: List<EdSettingItem>)

/**
 * FoxSu Editorial 设置页：字母编号 + 标题 + 右侧数值（开关 / 箭头），发丝线分隔。
 */
@Composable
fun EditorialSettings(
    uiState: SettingsUiState,
    actions: SettingsScreenActions,
    bottomInnerPadding: Dp,
    onOpenCustomization: () -> Unit = {},
    onOpenSulog: () -> Unit = {},
) {
    val t = edTokens()
    var showStyle by remember { mutableStateOf(false) }

    // 所有文案在组合上下文中取好，LazyColumn 内只处理纯数据
    val groups = listOf(
        EdGroup(
            "01", stringResource(R.string.fx_glass_group_general),
            listOf(
                EdSettingItem(
                    "A", stringResource(R.string.fx_settings_check_update),
                    stringResource(R.string.fx_glass_version_fmt, me.diksu.ultra.BuildConfig.VERSION_NAME),
                    sw = uiState.checkUpdate,
                    onClick = { actions.onSetCheckUpdate(!uiState.checkUpdate) },
                ),
                EdSettingItem(
                    "B", stringResource(R.string.fx_settings_module_check_update),
                    stringResource(R.string.fx_settings_check_update_summary),
                    sw = uiState.checkModuleUpdate,
                    onClick = { actions.onSetCheckModuleUpdate(!uiState.checkModuleUpdate) },
                ),
                EdSettingItem(
                    "C", stringResource(R.string.fx_settings_soft_reboot),
                    stringResource(R.string.fx_settings_soft_reboot_summary),
                    sw = uiState.useSoftReboot,
                    onClick = { actions.onSetUseSoftReboot(!uiState.useSoftReboot) },
                ),
                EdSettingItem(
                    "D", stringResource(R.string.fx_settings_auto_jailbreak),
                    stringResource(R.string.fx_settings_auto_jailbreak_summary),
                    sw = uiState.autoJailbreak,
                    onClick = { actions.onSetAutoJailbreak(!uiState.autoJailbreak) },
                ),
                EdSettingItem(
                    "E", stringResource(R.string.fx_enable_web_debugging),
                    stringResource(R.string.fx_enable_web_debugging_summary),
                    sw = uiState.enableWebDebugging,
                    onClick = { actions.onSetEnableWebDebugging(!uiState.enableWebDebugging) },
                ),
            ),
        ),
        EdGroup(
            "02", stringResource(R.string.fx_glass_group_appearance),
            listOf(
                EdSettingItem(
                    "F", stringResource(R.string.fx_settings_ui_mode),
                    stringResource(uiModeLabelRes(LocalUiMode.current)),
                    chevron = true,
                    onClick = { showStyle = true },
                ),
                EdSettingItem(
                    "G", stringResource(R.string.fx_customization_title),
                    stringResource(R.string.fx_customization_entry_summary),
                    chevron = true,
                    onClick = onOpenCustomization,
                ),
                EdSettingItem(
                    "I", stringResource(R.string.fx_settings_profile_template),
                    stringResource(R.string.fx_settings_profile_template_summary),
                    chevron = true,
                    onClick = actions.onOpenProfileTemplate,
                ),
            ),
        ),
        EdGroup(
            "03", stringResource(R.string.fx_glass_group_security),
            listOf(
                EdSettingItem(
                    "J", stringResource(R.string.fx_settings_sucompat),
                    uiState.suCompatStatus.ifBlank { stringResource(R.string.fx_settings_sucompat_summary) },
                    chevron = true,
                    onClick = { actions.onSetSuCompatMode(if (uiState.isSuEnabled) 1 else 0) },
                ),
                EdSettingItem(
                    "K", stringResource(R.string.fx_settings_kernel_umount),
                    uiState.kernelUmountStatus.ifBlank { stringResource(R.string.fx_settings_kernel_umount_summary) },
                    sw = uiState.isKernelUmountEnabled,
                    onClick = { actions.onSetKernelUmountEnabled(!uiState.isKernelUmountEnabled) },
                ),
                EdSettingItem(
                    "L", stringResource(R.string.fx_settings_selinux_hide),
                    uiState.selinuxHideStatus.ifBlank { stringResource(R.string.fx_settings_selinux_hide_summary) },
                    sw = uiState.isSelinuxHideEnabled,
                    onClick = { actions.onSetSelinuxHideEnabled(!uiState.isSelinuxHideEnabled) },
                ),
                EdSettingItem(
                    "M", stringResource(R.string.fx_settings_sulog),
                    stringResource(R.string.fx_settings_sulog_summary),
                    chevron = true,
                    onClick = onOpenSulog,
                ),
                EdSettingItem(
                    "N", stringResource(R.string.fx_settings_adb_root),
                    uiState.adbRootStatus.ifBlank { stringResource(R.string.fx_settings_adb_root_summary) },
                    sw = uiState.isAdbRootEnabled,
                    onClick = { actions.onSetAdbRootEnabled(!uiState.isAdbRootEnabled) },
                ),
                EdSettingItem(
                    "O", stringResource(R.string.fx_settings_shell_su_adb_only),
                    uiState.shellSuAdbOnlyStatus.ifBlank { stringResource(R.string.fx_settings_shell_su_adb_only_summary) },
                    sw = uiState.isShellSuAdbOnlyEnabled,
                    onClick = { actions.onSetShellSuAdbOnlyEnabled(!uiState.isShellSuAdbOnlyEnabled) },
                ),
            ),
        ),
        EdGroup(
            "04", stringResource(R.string.fx_glass_group_advanced),
            listOf(
                EdSettingItem(
                    "P", stringResource(R.string.fx_settings_umount_modules_default),
                    stringResource(R.string.fx_settings_umount_modules_default_summary),
                    sw = uiState.isDefaultUmountModules,
                    onClick = { actions.onSetDefaultUmountModules(!uiState.isDefaultUmountModules) },
                ),
                EdSettingItem(
                    "Q", stringResource(R.string.fx_about),
                    "FoxSu Editorial",
                    chevron = true,
                    onClick = actions.onOpenAbout,
                ),
            ),
        ),
    )

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .then(edPageBg(t.paper))
            .statusBarsPadding(),
        contentPadding = PaddingValues(start = if (useSideDock()) 96.dp else 0.dp, bottom = bottomInnerPadding + 24.dp),
    ) {
        item {
            EdMasthead(
                brand = stringResource(R.string.fx_settings),
                tag = "SETTINGS",
                left = "FoxSu Editorial",
                right = "VOL.${me.diksu.ultra.BuildConfig.VERSION_NAME}",
                brandSize = 28,
            )
        }
        groups.forEachIndexed { groupIndex, group ->
            item {
                EdSection(group.number, group.title, topPadding = if (groupIndex == 0) 14.dp else 20.dp)
            }
            group.items.forEach { setting ->
                item { EdSettingRow(setting) }
            }
        }
    }

    if (showStyle) {
        EdStyleSheet(onDismiss = { showStyle = false })
    }
}

/** 设置行（.srow2）：字母编号 + 标题 + 右侧数值 + 开关/箭头 */
@Composable
private fun EdSettingRow(item: EdSettingItem) {
    val t = edTokens()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .hairBottom(t.hair)
            .clickable(onClick = item.onClick)
            .padding(horizontal = 22.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = item.index,
            fontSize = 10.sp,
            fontWeight = FontWeight.Black,
            color = t.acc,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.width(22.dp),
        )
        Text(
            text = item.title,
            fontSize = 14.sp,
            fontWeight = FontWeight.ExtraBold,
            color = t.ink,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(10.dp))
        Text(
            text = item.value,
            fontSize = 11.sp,
            color = t.sub,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.widthIn(max = 132.dp),
        )
        Spacer(Modifier.width(8.dp))
        when {
            item.sw != null -> EdSwitch(checked = item.sw)
            item.chevron -> Text("›", fontSize = 13.sp, color = t.faint)
        }
    }
}
