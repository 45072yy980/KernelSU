package me.diksu.ultra.foxsu.ui.screen.nocturne

import me.diksu.ultra.foxsu.ui.component.bottombar.useSideDock
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import me.diksu.ultra.BuildConfig
import me.diksu.ultra.R
import me.diksu.ultra.foxsu.data.settings.CustomizationStore
import me.diksu.ultra.foxsu.ui.LocalUiMode
import me.diksu.ultra.foxsu.ui.screen.glass.GlassStylePickerDialog
import me.diksu.ultra.foxsu.ui.screen.settings.SettingsScreenActions
import me.diksu.ultra.foxsu.ui.screen.settings.SettingsUiState

/**
 * 设置 —— 严格对照视觉稿 ④ 设置：
 * PREFERENCES / 设置 + 两个圆钮 → UPDATES（检查更新、检查模块更新）→
 * APPEARANCE（界面风格 / 主题设置 / Fox 设置）→ SECURITY（软重启 / 传统 SU / 隐藏 SELinux）
 * → 底部「NOCTURNE · V{version}」。
 */
@Composable
fun NocturneSettingsScreen(
    uiState: SettingsUiState,
    actions: SettingsScreenActions,
    bottomInnerPadding: Dp,
    onOpenCustomization: () -> Unit,
    onOpenSulog: () -> Unit,
) {
    val t = ncTokens()
    var showStylePicker by remember { mutableStateOf(false) }

    Box(Modifier.fillMaxSize()) {
        NcGlowBackground(t)
        Column(Modifier.fillMaxSize().padding(top = ncStatusTop() + 2.dp, bottom = bottomInnerPadding)) {
            Box(Modifier.padding(start = if (useSideDock()) 96.dp else 18.dp, end = 18.dp)) {
                NcHeader(kicker = "PREFERENCES", title = "设置") {
                    NcIconButton(iconRes = R.drawable.fx_nc_user, onClick = actions.onOpenAbout)
                    NcIconButton(iconRes = R.drawable.fx_nc_palette, onClick = onOpenCustomization)
                }
            }
            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(start = if (useSideDock()) 96.dp else 18.dp, end = 18.dp, bottom = 86.dp),
            ) {

                NcSectionLabel("UPDATES")
                NcGroupCard {
                    NcRow(
                        iconRes = R.drawable.fx_nc_download,
                        title = "检查更新",
                        subtitle = "启动时自动检查最新版本",
                        accent = NcAccentTone.Violet,
                        trailing = { NcSwitch(uiState.checkUpdate) { actions.onSetCheckUpdate(it) } },
                    )
                    NcRow(
                        iconRes = R.drawable.fx_nc_refresh,
                        title = "检查模块更新",
                        subtitle = "启动时自动检查模块版本",
                        accent = NcAccentTone.Violet,
                        showDivider = true,
                        trailing = { NcSwitch(uiState.checkModuleUpdate) { actions.onSetCheckModuleUpdate(it) } },
                    )
                }

                NcSectionLabel("APPEARANCE")
                NcGroupCard {
                    NcRow(
                        iconRes = R.drawable.fx_nc_sliders,
                        title = "界面风格",
                        subtitle = "切换 App 整体 UI 主题",
                        accent = NcAccentTone.Default,
                        onClick = { showStylePicker = true },
                        trailing = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("Nocturne", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = t.tx2)
                                Spacer(Modifier.width(6.dp))
                                NcChevron()
                            }
                        },
                    )
                    NcRow(
                        iconRes = R.drawable.fx_nc_doc,
                        title = "定制功能",
                        subtitle = "壁纸、圆角与启动器图标",
                        accent = NcAccentTone.Default,
                        showDivider = true,
                        onClick = onOpenCustomization,
                        trailing = { NcChevron() },
                    )
                    NcRow(
                        iconRes = R.drawable.fx_nc_grid,
                        title = "App Profile 模板",
                        subtitle = "管理本地和在线模板",
                        accent = NcAccentTone.Violet,
                        showDivider = true,
                        onClick = actions.onOpenProfileTemplate,
                        trailing = { NcChevron() },
                    )
                }

                NcSectionLabel("SECURITY")
                NcGroupCard {
                    NcRow(
                        iconRes = R.drawable.fx_nc_soft,
                        title = "使用软重启",
                        subtitle = "代替完整重启，更快生效",
                        accent = NcAccentTone.Mint,
                        trailing = { NcSwitch(uiState.useSoftReboot) { actions.onSetUseSoftReboot(it) } },
                    )
                    NcRow(
                        iconRes = R.drawable.fx_nc_terminal,
                        title = "传统 SU 命令",
                        subtitle = "/system/bin/su 获取 Root",
                        accent = NcAccentTone.Default,
                        showDivider = true,
                        trailing = {
                            NcSwitch(uiState.isSuEnabled) {
                                actions.onSetSuCompatMode(if (it) 0 else 2)
                            }
                        },
                    )
                    NcRow(
                        iconRes = R.drawable.fx_nc_lock,
                        title = "隐藏 SELinux 修改",
                        subtitle = "阻止应用检测修改",
                        accent = NcAccentTone.Default,
                        showDivider = true,
                        trailing = { NcSwitch(uiState.isSelinuxHideEnabled) { actions.onSetSelinuxHideEnabled(it) } },
                    )
                    NcRow(
                        iconRes = R.drawable.fx_nc_chip,
                        title = "卸载模块（内核级）",
                        subtitle = "在内核层卸载挂载的模块",
                        accent = NcAccentTone.Violet,
                        showDivider = true,
                        trailing = { NcSwitch(uiState.isKernelUmountEnabled) { actions.onSetKernelUmountEnabled(it) } },
                    )
                    NcRow(
                        iconRes = R.drawable.fx_nc_doc,
                        title = "SU 日志",
                        subtitle = "查看 Root 授权与命令记录",
                        accent = NcAccentTone.Amber,
                        showDivider = true,
                        onClick = onOpenSulog,
                        trailing = { NcChevron() },
                    )
                    NcRow(
                        iconRes = R.drawable.fx_nc_terminal,
                        title = "ADB Root",
                        subtitle = "允许 adb 直接获得 Root",
                        accent = NcAccentTone.Default,
                        showDivider = true,
                        trailing = { NcSwitch(uiState.isAdbRootEnabled) { actions.onSetAdbRootEnabled(it) } },
                    )
                    NcRow(
                        iconRes = R.drawable.fx_nc_shield,
                        title = "自动越狱",
                        subtitle = "开机检测到 Permissive 时自动处理",
                        accent = NcAccentTone.Mint,
                        showDivider = true,
                        trailing = { NcSwitch(uiState.autoJailbreak) { actions.onSetAutoJailbreak(it) } },
                    )
                }

                Spacer(Modifier.height(18.dp))
                Text(
                    text = "NOCTURNE · V${BuildConfig.VERSION_NAME}",
                    fontSize = 9.sp,
                    letterSpacing = 2.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = t.tx3,
                    modifier = Modifier.padding(start = 2.dp),
                )
                Spacer(Modifier.height(16.dp))
            }
        }

    }

    if (showStylePicker) {
        GlassStylePickerDialog(current = LocalUiMode.current, onDismiss = { showStylePicker = false })
    }
}
