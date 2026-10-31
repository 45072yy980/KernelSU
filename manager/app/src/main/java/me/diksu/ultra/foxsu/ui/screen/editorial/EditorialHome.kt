package me.diksu.ultra.foxsu.ui.screen.editorial

import me.diksu.ultra.foxsu.ui.component.bottombar.useSideDock
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import me.diksu.ultra.BuildConfig
import me.diksu.ultra.R
import me.diksu.ultra.foxsu.data.settings.CustomizationStore
import me.diksu.ultra.foxsu.ui.LocalUiMode
import me.diksu.ultra.foxsu.ui.component.bottombar.NavigationBadgeState
import me.diksu.ultra.foxsu.ui.component.rebootlistpopup.getRebootListOption
import me.diksu.ultra.foxsu.ui.component.rebootlistpopup.rememberRebootAction
import me.diksu.ultra.foxsu.ui.screen.home.HeroPhase
import me.diksu.ultra.foxsu.ui.screen.home.HomeActions
import me.diksu.ultra.foxsu.ui.screen.home.HomeIssueStrip
import me.diksu.ultra.foxsu.ui.screen.home.HomeUiState
import me.diksu.ultra.foxsu.ui.screen.home.freshIssues
import me.diksu.ultra.foxsu.ui.screen.home.heroState
import me.diksu.ultra.foxsu.ui.uiModeLabelRes

/**
 * FoxSu Editorial 主页 —— 杂志排版：
 * 通栏 masthead（FoxSu / ROOT / JOURNAL / VOL）→ 墨色状态带 → 编号区块 + 发丝线条目
 * → 内核代码条 → 底部通栏 tab。
 */
@Composable
fun EditorialHome(
    state: HomeUiState,
    actions: HomeActions,
    bottomInnerPadding: Dp,
    navigationBadge: NavigationBadgeState,
    onOpenTab: (Int) -> Unit,
    onOpenAuthor: () -> Unit = {},
) {
    val t = edTokens()
    val issues = state.freshIssues()
    var showReboot by remember { mutableStateOf(false) }
    var showStyle by remember { mutableStateOf(false) }
    val scroll = rememberScrollState()
    val sideDock = useSideDock()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .then(edPageBg(t.paper))
            .statusBarsPadding(),
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = if (sideDock) 96.dp else 0.dp)
                .verticalScroll(scroll),
        ) {
            EdMasthead(
                brand = CustomizationStore.current.homeTitle.ifBlank { "FoxSu" },
                brandColor = CustomizationStore.current.titleColor
                    .takeIf { it != 0 }?.let { Color(it) },
                tag = "ROOT",
                left = "Kernel Management Journal",
                right = "VOL.${BuildConfig.VERSION_CODE}",
                actions = listOf(
                    EdMastAction(R.drawable.fx_ic_d_user, onOpenAuthor),
                    EdMastAction(R.drawable.fx_ic_d_palette) { showStyle = true },
                    EdMastAction(R.drawable.fx_ic_d_power2) { showReboot = true },
                ),
            )

            val hero = state.heroState()
            // 真实告警条：与 Fresh / Miuix / Glass / Nocturne 对齐
            HomeIssueStrip(
                state = state,
                accent = t.rose,
                onInstall = if (hero.canFlash) ({ actions.onInstallClick() }) else null,
            )
            Spacer(Modifier.height(12.dp))
            EdStatusBand(
                kicker = "CURRENT STATUS · 当前状态",
                big = when (hero.phase) {
                    HeroPhase.NOT_INSTALLED -> "未安装"
                    HeroPhase.FLASHING -> "刷写中"
                    HeroPhase.PENDING_REBOOT -> "待重启"
                    HeroPhase.WORKING -> "工作中"
                    HeroPhase.ATTENTION -> "需注意"
                    HeroPhase.FLASH_FAILED -> "未工作"
                    HeroPhase.UNSUPPORTED -> "不支持"
                },
                kvs = listOf(
                    Triple("版本", hero.versionName, false),
                    // 模式：真实 LKM/GKI/越狱，非 GKI 内核不瞎写
                    Triple(
                        "模式",
                        listOfNotNull(
                            hero.modeLabel,
                            if (hero.jailbreakMode) "越狱" else null,
                        ).joinToString(" · ").ifEmpty { "—" },
                        false,
                    ),
                    Triple("界面", stringResource(uiModeLabelRes(LocalUiMode.current)), true),
                ),
                onClick = if (hero.canFlash) ({ actions.onInstallClick() }) else null,
                onCheckUpdate = { actions.onCheckUpdate() },
                onRefreshImage = if (hero.canFlash) ({ actions.onRefreshImage() }) else null,
            )

            EdSection("01", "设备概况")
            EdRow(
                iconRes = R.drawable.fx_ic_d_phone, glyphBg = t.cyan.copy(alpha = 0.10f), glyphColor = t.cyan,
                title = stringResource(R.string.fx_home_device_model),
                subtitle = stringResource(R.string.fx_fresh_home_device_sub),
                trailing = {
                    Text(
                        text = state.systemInfo.deviceModel.ifBlank { "—" },
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Black,
                        color = t.ink,
                        maxLines = 1,
                    )
                },
            )
            EdRow(
                iconRes = R.drawable.fx_ic_d_shield, glyphBg = t.violet.copy(alpha = 0.10f), glyphColor = t.violet,
                title = stringResource(R.string.fx_superuser),
                subtitle = stringResource(R.string.fx_fresh_home_superuser_sub),
                trailing = { EdNum(navigationBadge.superuserCount.toString(), t.violet) },
                onClick = { onOpenTab(1) },
            )
            EdRow(
                iconRes = R.drawable.fx_ic_d_grid, glyphBg = t.rose.copy(alpha = 0.10f), glyphColor = t.rose,
                title = stringResource(R.string.fx_module),
                subtitle = stringResource(R.string.fx_fresh_home_module_sub),
                trailing = { EdNum(navigationBadge.moduleEnabledCount.toString(), t.rose) },
                onClick = { onOpenTab(2) },
            )

            EdSection("02", "安全状态")
            EdRow(
                iconRes = R.drawable.fx_ic_d_lock, glyphBg = t.lime.copy(alpha = 0.10f), glyphColor = t.lime,
                title = "SELinux",
                subtitle = stringResource(R.string.fx_glass_home_selinux_sub),
                trailing = {
                    EdVal(
                        text = if (state.isSELinuxPermissive) stringResource(R.string.fx_selinux_status_permissive)
                        else stringResource(R.string.fx_selinux_status_enforcing),
                        pill = if (state.isSELinuxPermissive) "Permissive" else "Enforcing",
                        pillColor = t.lime,
                        filled = !state.isSELinuxPermissive,
                    )
                },
            )
            EdRow(
                iconRes = R.drawable.fx_ic_d_sliders, glyphBg = t.amber.copy(alpha = 0.10f), glyphColor = t.amber,
                title = "Seccomp",
                subtitle = stringResource(R.string.fx_glass_home_seccomp_sub),
                trailing = {
                    EdVal(
                        text = stringResource(seccompLabelRes(state.systemInfo.seccompStatus)),
                        pill = seccompShort(state.systemInfo.seccompStatus),
                        pillColor = t.amber,
                    )
                },
            )

            EdSection("03", "了解更多")
            EdRow(
                iconRes = R.drawable.fx_ic_d_heart, glyphBg = t.acc.copy(alpha = 0.10f), glyphColor = t.acc,
                title = stringResource(R.string.fx_glass_home_support),
                subtitle = stringResource(R.string.fx_glass_home_support_sub),
                trailing = { Text("›", fontSize = 13.sp, color = t.faint) },
                onClick = { actions.onOpenUrl("https://github.com/Cmry-mlc/FoxSu") },
            )
            // 越狱模式入口：真实启动 MagicaService（条件与 Fresh / Miuix / Glass / Nocturne 一致）
            if (state.ksuVersion == null && state.kernelVersion.isGKI() && state.isSELinuxPermissive) {
                EdRow(
                    iconRes = R.drawable.fx_ic_d_terminal, glyphBg = t.amber.copy(alpha = 0.10f), glyphColor = t.amber,
                    title = stringResource(R.string.fx_home_jailbreak),
                    subtitle = stringResource(R.string.fx_fresh_home_jailbreak_sub),
                    trailing = { Text("›", fontSize = 13.sp, color = t.faint) },
                    onClick = { actions.onJailbreakClick() },
                )
            }
            EdRow(
                iconRes = R.drawable.fx_ic_d_book, glyphBg = t.ink2.copy(alpha = 0.08f), glyphColor = t.ink2,
                title = stringResource(R.string.fx_glass_home_learn),
                subtitle = stringResource(R.string.fx_glass_home_learn_sub),
                trailing = { Text("›", fontSize = 13.sp, color = t.faint) },
                onClick = { actions.onOpenUrl("https://kernelsu.org") },
            )

            // 规格卡：内核 / 指纹（设计稿 .spec）
            EdSpec(
                title = "KERNEL · 内核版本",
                tag = "KMI " + state.systemInfo.kernelVersion.substringBefore(".").let { major ->
                    val parts = state.systemInfo.kernelVersion.split(".")
                    if (parts.size >= 2) "${parts[0]}.${parts[1]}" else major
                },
                value = state.systemInfo.kernelVersion.ifBlank { "—" },
            )
            EdSpec(
                title = "FINGERPRINT · 指纹信息",
                tag = "RO 只读",
                value = state.systemInfo.fingerprint.ifBlank { "—" },
            )
            Spacer(Modifier.height(bottomInnerPadding + 20.dp))
        }
    }

    if (showReboot) {
        EdRebootSheet(onDismiss = { showReboot = false })
    }
    if (showStyle) {
        EdStyleSheet(onDismiss = { showStyle = false })
    }
}

private fun seccompShort(status: Int): String = when (status) {
    2 -> "Strict"
    1 -> "Filter"
    -1 -> "Off"
    else -> "N/A"
}

@androidx.annotation.StringRes
private fun seccompLabelRes(status: Int): Int = when (status) {
    2 -> R.string.fx_seccomp_status_strict
    1 -> R.string.fx_seccomp_status_filter
    -1 -> R.string.fx_seccomp_status_disabled
    else -> R.string.fx_seccomp_status_not_supported
}

/** 编辑风重启面板（.ovl + .sheet：墨色描边、编号选项、底部分隔按钮） */
@Composable
fun EdRebootSheet(onDismiss: () -> Unit) {
    val t = edTokens()
    val options = getRebootListOption()
    val doReboot = rememberRebootAction()

    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(t.paper)
                .border(1.5.dp, t.ink, RoundedCornerShape(20.dp))
                .padding(top = 16.dp, bottom = 14.dp),
        ) {
            // .sh-kick
            Text(
                text = "REBOOT · 电源菜单",
                fontSize = 8.5.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 2.5.sp,
                color = t.acc,
                modifier = Modifier.padding(horizontal = 18.dp),
            )
            // .sh-t：标题 + 右侧说明
            Row(
                modifier = Modifier.padding(start = 18.dp, end = 18.dp, top = 3.dp, bottom = 8.dp),
                verticalAlignment = Alignment.Bottom,
            ) {
                Text(
                    text = stringResource(R.string.fx_reboot),
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Black,
                    color = t.ink,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = "选择一种重启方式",
                    fontSize = 9.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = t.sub,
                    maxLines = 1,
                )
            }
            options.forEachIndexed { index, option ->
                val desc = rebootDesc(option.reason)
                val primary = index == 0
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .hairBottom(t.hair)
                        .clickable {
                            onDismiss()
                            doReboot(option.reason)
                        }
                        .padding(horizontal = 18.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "%02d".format(index + 1),
                        fontSize = 9.5.sp,
                        fontWeight = FontWeight.Black,
                        color = t.faint,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.width(22.dp),
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(option.labelRes),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = if (primary) t.acc else t.ink,
                            maxLines = 1,
                        )
                        Text(
                            text = desc,
                            fontSize = 8.5.sp,
                            color = t.sub,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(top = 1.dp),
                        )
                    }
                    Text(
                        text = if (primary) "↻" else "›",
                        fontSize = 13.sp,
                        color = t.faint,
                    )
                }
            }
            // .cancel：墨色描边圆角按钮
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 18.dp, end = 18.dp, top = 10.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .border(1.5.dp, t.ink, RoundedCornerShape(10.dp))
                    .clickable(onClick = onDismiss)
                    .padding(vertical = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = stringResource(android.R.string.cancel),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 2.sp,
                    color = t.ink,
                )
            }
        }
    }
}

/** 重启方式说明（对应设计稿 .rd 文案） */
private fun rebootDesc(reason: String): String = when (reason) {
    "" -> "完整重启系统 Reboot"
    "userspace" -> "仅重启系统界面 Userspace"
    "soft_reboot" -> "仅重启系统界面 Soft Reboot"
    "recovery" -> "进入第三方 Recovery 模式"
    "bootloader" -> "Fastboot 引导模式"
    "download" -> "Odin 刷机下载模式"
    "edl" -> "高通紧急下载模式（9008）"
    else -> ""
}

/** 界面风格切换（编辑风） */
@Composable
fun EdStyleSheet(onDismiss: () -> Unit) {
    me.diksu.ultra.foxsu.ui.component.StylePickerDialog(
        current = LocalUiMode.current,
        onDismiss = onDismiss,
    )
}
