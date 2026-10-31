package me.diksu.ultra.foxsu.ui.screen.ag

import me.diksu.ultra.foxsu.ui.component.bottombar.useSideDock
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.ViewModule
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Construction
import androidx.compose.material.icons.rounded.Dashboard
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import me.diksu.ultra.R
import me.diksu.ultra.foxsu.data.settings.CustomizationStore
import me.diksu.ultra.foxsu.ui.LocalUiMode
import me.diksu.ultra.foxsu.ui.component.StylePickerDialog
import me.diksu.ultra.foxsu.ui.screen.settings.SettingsScreenActions
import me.diksu.ultra.foxsu.ui.screen.settings.SettingsUiState

/**
 * 晨曦玻璃 · 设置（设计稿 p-set）。
 * 通用 / 模块与 Profile / Root 与安全 / 关于 四组；开关与入口全部接真实功能。
 */
@Composable
fun AgSettings(
    uiState: SettingsUiState,
    actions: SettingsScreenActions,
    bottomInnerPadding: androidx.compose.ui.unit.Dp,
    onOpenCustomization: () -> Unit,
    onOpenAuthor: () -> Unit,
) {
    val context = LocalContext.current
    val donateNav = me.diksu.ultra.foxsu.ui.navigation3.LocalNavigator.current
    val nav = me.diksu.ultra.foxsu.ui.navigation3.LocalNavigator.current
    var showStyle by remember { mutableStateOf(false) }

    val styleName = when (LocalUiMode.current.value) {
        "miuix" -> "晨曦玻璃"
        "fresh" -> "Pages"
        "material" -> "Editorial"
        "glass" -> "Daylight"
        "nocturne" -> "Nocturne"
        else -> "晨曦玻璃"
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(Ag.background())
            .statusBarsPadding(),
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = if (useSideDock()) 106.dp else 18.dp,
                end = 18.dp, bottom = bottomInnerPadding + 110.dp),
        ) {
            item { AgTitle("设", "置", topMargin = 12.dp) }

            // ── 通用 ──
            item { AgGHead("通用") }
            item {
                AgCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp)) {
                    AgToggleRow(
                        stringResource(R.string.fx_settings_check_update), stringResource(R.string.fx_settings_check_update_summary),
                        Ag.Vio, AgSettingsIcons.CheckUpdate,
                        trailing = { AgToggle(checked = uiState.checkUpdate, onCheckedChange = { actions.onSetCheckUpdate(!uiState.checkUpdate) }) },
                    )
                    AgToggleRow(
                        stringResource(R.string.fx_settings_module_check_update), stringResource(R.string.fx_settings_check_update_summary),
                        Ag.Mag, AgSettingsIcons.Modules,
                        trailing = { AgToggle(checked = uiState.checkModuleUpdate, onCheckedChange = { actions.onSetCheckModuleUpdate(!uiState.checkModuleUpdate) }) },
                    )
                    AgRow(
                        "界面风格", "选择应用的界面风格",
                        Ag.Vio, AgSettingsIcons.Style,
                        trailing = {
                            Text("$styleName ⇅", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Ag.Vio)
                        },
                        onClick = { showStyle = true },
                    )
                    AgRow(
                        "定制功能", "背景壁纸、卡片样式与启动器图标",
                        Ag.Amber, AgSettingsIcons.Customize, showChev = true, onClick = onOpenCustomization,
                    )
                }
            }

            // ── 模块与 Profile ──
            item { AgGHead("模块与 Profile") }
            item {
                AgCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp)) {
                    AgRow(
                        stringResource(R.string.fx_settings_profile_template), stringResource(R.string.fx_settings_profile_template_summary),
                        Ag.Teal, AgSettingsIcons.Modules, showChev = true, onClick = actions.onOpenProfileTemplate)
                    AgToggleRow(
                        "卸载模块（内核级）", "在内核给需要的应用卸载模块",
                        Ag.Vio, AgSettingsIcons.Moon,
                        trailing = { AgToggle(checked = uiState.isKernelUmountEnabled, onCheckedChange = { actions.onSetKernelUmountEnabled(!uiState.isKernelUmountEnabled) }) },
                    )
                    AgToggleRow(
                        "默认卸载模块", "启用模块默认卸载行为",
                        Ag.Mag, AgSettingsIcons.Modules,
                        trailing = { AgToggle(checked = uiState.isDefaultUmountModules, onCheckedChange = { actions.onSetDefaultUmountModules(!uiState.isDefaultUmountModules) }) },
                    )
                    AgToggleRow(
                        "启用 Web 调试", "允许模块 WebUI 的调试模式",
                        Ag.Amber, AgSettingsIcons.LocalTemplate,
                        trailing = { AgToggle(checked = uiState.enableWebDebugging, onCheckedChange = { actions.onSetEnableWebDebugging(!uiState.enableWebDebugging) }) },
                        divider = false,
                    )
                }
            }

            // ── Root 与安全 ──
            item { AgGHead("Root 与安全") }
            item {
                AgCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp)) {
                    AgToggleRow(
                        "默认 SU 授权", uiState.suCompatStatus.ifBlank { if (uiState.isSuEnabled) "已启用" else "已停用" },
                        Ag.Vio, AgSettingsIcons.Shield,
                        trailing = { AgToggle(checked = uiState.isSuEnabled, onCheckedChange = { actions.onSetSuCompatMode(if (uiState.isSuEnabled) 1 else 0) }) },
                    )
                    AgToggleRow(
                        "隐藏 SELinux 修改", "阻止应用检测 SELinux 修改",
                        Ag.Teal, AgSettingsIcons.EyeOff,
                        trailing = { AgToggle(checked = uiState.isSelinuxHideEnabled, onCheckedChange = { actions.onSetSelinuxHideEnabled(!uiState.isSelinuxHideEnabled) }) },
                    )
                    AgToggleRow(
                        "SU 日志", "将 Root 相关事件记录到 KernelSU 的 SU 日志",
                        Ag.Mag, AgSettingsIcons.Log,
                        trailing = { AgToggle(checked = uiState.isSulogEnabled, onCheckedChange = { actions.onSetSulogEnabled(!uiState.isSulogEnabled) }) },
                    )
                    // SusFs 管理：隐藏路径 / 隐藏挂载 / uname、cmdline 伪装 / 开机自动应用。
                    // 内核由用户自行刷入 susfs 补丁，管理器只提供配置界面与开机脚本。
                    AgRow(
                        "SusFs 管理", "Root 隐藏：路径 / 挂载 / 伪装（需 SusFs 内核）",
                        Ag.Teal, AgSettingsIcons.Moon, showChev = true,
                        onClick = { nav.push(me.diksu.ultra.foxsu.ui.navigation3.Route.SusFs) },
                    )
                    AgRow(
                        "KPM 模块", "加载 / 卸载内核补丁模块（需 KPM 内核）",
                        Ag.Amber, AgSettingsIcons.Puzzle, showChev = true,
                        onClick = { nav.push(me.diksu.ultra.foxsu.ui.navigation3.Route.Kpm) },
                    )
                    AgRow(
                        "去广告", "系统级 hosts 屏蔽：无 VPN、无后台、零耗电",
                        Ag.Mag, AgSettingsIcons.EyeOff, showChev = true,
                        onClick = { nav.push(me.diksu.ultra.foxsu.ui.navigation3.Route.AdBlock) },
                    )
                    // 恢复上游设置项：安装/更新/开关模块后用的重启方式。
                    // 面板里的「重启 / Recovery / EDL」入口仍在主页电源键里，设置页只保留这个开关。
                    AgToggleRow(
                        stringResource(R.string.fx_settings_soft_reboot),
                        stringResource(R.string.fx_settings_soft_reboot_summary),
                        Ag.Amber, AgSettingsIcons.SoftReboot,
                        trailing = {
                            AgToggle(
                                checked = uiState.isLateLoadMode || uiState.useSoftReboot,
                                onCheckedChange = { actions.onSetUseSoftReboot(!uiState.useSoftReboot) },
                                small = true,
                            )
                        },
                        divider = false,
                    )
                }
            }

            // ── 关于 ──
            item { AgGHead("关于") }
            item {
                AgCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp)) {
                    AgRow(
                        "FoxSu Manager",
                        "版本 ${me.diksu.ultra.BuildConfig.VERSION_NAME} · 基于 KernelSU 开源项目",
                        Ag.Vio, AgSettingsIcons.Manager, showChev = true, onClick = onOpenAuthor,
                    )
                    AgRow(
                        stringResource(R.string.fx_home_support_title), stringResource(R.string.fx_home_support_desc),
                        Ag.Mag, AgHomeIcons.Heart, showChev = true,
                        onClick = { donateNav.push(me.diksu.ultra.foxsu.ui.navigation3.Route.Donate) },
                    )
                    AgRow(
                        "GitHub 仓库", "查看源码与提交 Issue",
                        Ag.Amber, AgSettingsIcons.Github, showChev = true,
                        onClick = {
                            runCatching {
                                context.startActivity(
                                    android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse("https://github.com/Cmry-mlc/FoxSu"))
                                )
                            }
                        },
                    )
                    AgRow(
                        "加入频道", "QQ 频道【FoxSu】交流与反馈",
                        Ag.Teal, AgSettingsIcons.Chat, showChev = true,
                        onClick = {
                            runCatching {
                                context.startActivity(
                                    android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse("https://pd.qq.com/s/8glxclfk6?b=9"))
                                )
                            }
                        }, divider = false)
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }

    if (showStyle) {
        StylePickerDialog(current = LocalUiMode.current, onDismiss = { showStyle = false })
    }
}
