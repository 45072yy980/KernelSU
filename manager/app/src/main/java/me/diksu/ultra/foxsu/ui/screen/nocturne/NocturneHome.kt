package me.diksu.ultra.foxsu.ui.screen.nocturne

import me.diksu.ultra.foxsu.ui.component.bottombar.useSideDock
import android.os.Build
import androidx.compose.foundation.background
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import me.diksu.ultra.R
import me.diksu.ultra.foxsu.data.settings.CustomizationStore
import me.diksu.ultra.foxsu.ui.component.bottombar.NavigationBadgeState
import me.diksu.ultra.foxsu.ui.screen.ag.particleOutline
import me.diksu.ultra.foxsu.ui.screen.home.HeroPhase
import me.diksu.ultra.foxsu.ui.screen.home.HomeActions
import me.diksu.ultra.foxsu.ui.screen.home.HomeIssueStrip
import me.diksu.ultra.foxsu.ui.screen.home.HomeUiState
import me.diksu.ultra.foxsu.ui.screen.home.displayKernelVersion
import me.diksu.ultra.foxsu.ui.screen.home.heroState

/**
 * 主页 —— 严格对照视觉稿 ① 主页：
 * KERNEL MANAGER 眉题 + FoxSu 大标题 → Hero 卡（● RUNNING · 越狱模式 / 大字「工作中」+ LKM 徽章 /
 * 右上 56 环形+对勾 / VERSION·MODE·UAPI 三列）→ DEVICE · 设备 → SECURITY · 安全 → BUILD · 内核与指纹。
 *
 * 所有数值均取自真实运行状态，无任何设计稿假数据。
 */
@Composable
fun NocturneHomeScreen(
    state: HomeUiState,
    actions: HomeActions,
    bottomInnerPadding: Dp,
    navigationBadge: NavigationBadgeState,
    onOpenTab: (Int) -> Unit,
    onOpenAuthor: () -> Unit,
) {
    val t = ncTokens()
    var showReboot by remember { mutableStateOf(false) }
    var showStylePicker by remember { mutableStateOf(false) }
    // 越狱入口条件：未安装 + GKI 内核 + SELinux 宽容（与 Fresh / Miuix 完全一致）
    val canJailbreak = state.ksuVersion == null && state.kernelVersion.isGKI() && state.isSELinuxPermissive
    val sideDock = useSideDock()
    val donateNav = me.diksu.ultra.foxsu.ui.navigation3.LocalNavigator.current
    val onClickDonate = { donateNav.push(me.diksu.ultra.foxsu.ui.navigation3.Route.Donate) }

    Box(Modifier.fillMaxSize()) {
        NcGlowBackground(t)
        Column(
            Modifier
                .fillMaxSize()
                .padding(top = ncStatusTop() + 2.dp, bottom = bottomInnerPadding),
        ) {
            // 页头固定在顶部（不随内容滚动）
            Box(Modifier.padding(start = if (sideDock) 96.dp else 18.dp, end = 18.dp)) {
                val custom = me.diksu.ultra.foxsu.data.settings.CustomizationStore.current
                NcHeader(
                    kicker = "KERNEL MANAGER",
                    title = custom.homeTitle.ifBlank { "FoxSu" },
                    titleColor = if (custom.titleColor == 0) null else Color(custom.titleColor),
                ) {
                    NcIconButton(iconRes = R.drawable.fx_nc_user, onClick = onOpenAuthor)
                    NcIconButton(iconRes = R.drawable.fx_nc_palette, onClick = { showStylePicker = true })
                    NcIconButton(iconRes = R.drawable.fx_nc_power, onClick = { showReboot = true })
                }
            }
            // 仅内容滚动
            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(start = if (sideDock) 96.dp else 18.dp, end = 18.dp, bottom = 86.dp),
            ) {
                NcHero(state = state, actions = actions, onOpenTab = onOpenTab)
                Spacer(Modifier.height(10.dp))

                // 真实告警条：与 Fresh / Miuix 保持一致，逐条显示真实问题
                HomeIssueStrip(
                    state = state,
                    accent = t.red,
                    onInstall = if (state.heroState().canFlash) ({ actions.onInstallClick() }) else null,
                )
                Spacer(Modifier.height(10.dp))

                NcSectionLabel("DEVICE · 设备")
                NcGroupCard {
                    NcRow(
                        iconRes = R.drawable.fx_nc_phone,
                        title = "设备型号",
                        subtitle = state.systemInfo.deviceModel.ifBlank { Build.MODEL },
                        accent = NcAccentTone.Violet,
                        trailing = {
                            Text(
                                text = Build.MODEL,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = t.tx2,
                            )
                        },
                    )
                }

                NcSectionLabel("SECURITY · 安全")
                NcGroupCard {
                    NcRow(
                        iconRes = R.drawable.fx_nc_lock,
                        title = "SELinux",
                        subtitle = if (state.isSELinuxPermissive) "安全策略处于宽容模式" else "安全增强策略已启用",
                        accent = NcAccentTone.Mint,
                        trailing = {
                            Text(
                                text = if (state.isSELinuxPermissive) "宽容" else "强制",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (state.isSELinuxPermissive) t.amber else t.mint,
                            )
                        },
                    )
                    NcRow(
                        iconRes = R.drawable.fx_nc_filter,
                        title = "Seccomp",
                        subtitle = if (state.systemInfo.seccompStatus >= 0) "系统调用过滤已开启" else "系统调用过滤状态未知",
                        accent = NcAccentTone.Default,
                        showDivider = true,
                        trailing = {
                            Text(
                                text = if (state.systemInfo.seccompStatus >= 0) "Filter" else "未知",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = t.tx2,
                            )
                        },
                    )
                    NcRow(
                        iconRes = R.drawable.fx_nc_heart,
                        title = "支持开发",
                        subtitle = "FoxSu 免费开源，欢迎支持后续开发",
                        accent = NcAccentTone.Red,
                        showDivider = true,
                        onClick = onClickDonate,
                        trailing = { NcChevron() },
                    )
                }

                NcSectionLabel("QUICK ACCESS · 快捷入口")
                NcGroupCard {
                    NcRow(
                        iconRes = R.drawable.fx_nc_shield,
                        title = "超级用户",
                        subtitle = "管理每个应用的 Root 授权",
                        accent = NcAccentTone.Violet,
                        onClick = { onOpenTab(1) },
                        trailing = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = navigationBadge.superuserCount.toString(),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = t.tx2,
                                )
                                NcChevron()
                            }
                        },
                    )
                    NcRow(
                        iconRes = R.drawable.fx_nc_grid,
                        title = "模块",
                        subtitle = "已启用 ${navigationBadge.moduleEnabledCount} 个模块",
                        accent = NcAccentTone.Mint,
                        showDivider = true,
                        onClick = { onOpenTab(2) },
                        trailing = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = navigationBadge.moduleEnabledCount.toString(),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = t.tx2,
                                )
                                NcChevron()
                            }
                        },
                    )
                    // 越狱模式入口：真实启动 MagicaService（仅在可越狱时显示）
                    if (canJailbreak) {
                        NcRow(
                            iconRes = R.drawable.fx_nc_terminal,
                            title = "越狱模式",
                            subtitle = "无需解锁 Bootloader 启动 KernelSU",
                            accent = NcAccentTone.Amber,
                            showDivider = true,
                            onClick = { actions.onJailbreakClick() },
                            trailing = { NcChevron() },
                        )
                    }
                }

                NcSectionLabel("BUILD · 内核与指纹")
                NcCodeCard(
                    kicker = "KERNEL · 内核版本",
                    value = buildString {
                        append(state.displayKernelVersion())
                        append("  ·  ")
                        append(state.heroState().modeLabel ?: "非 GKI")
                        if (state.heroState().jailbreakMode) append("  ·  越狱模式")
                    },
                    tag = kernelTag(state.systemInfo.kernelVersion),
                    iconRes = R.drawable.fx_nc_terminal,
                )
                Spacer(Modifier.height(7.dp))
                NcCodeCard(
                    kicker = "FINGERPRINT · 指纹信息",
                    value = state.systemInfo.fingerprint.ifBlank { "—" },
                    tag = fingerprintTag(state.systemInfo.fingerprint),
                    iconRes = R.drawable.fx_nc_lock,
                )
                Spacer(Modifier.height(10.dp))
            }
        }
        // 重启弹层：覆盖整屏（含底栏），放在最外层 Box 最后绘制
        if (showReboot) NcRebootSheet(onDismiss = { showReboot = false })
    }

    if (showStylePicker) {
        me.diksu.ultra.foxsu.ui.screen.glass.GlassStylePickerDialog(
            current = me.diksu.ultra.foxsu.ui.LocalUiMode.current,
            onDismiss = { showStylePicker = false },
        )
    }
}

/** 从真实内核版本推导 KMI 标签，例如 "6.6.118-android15-…" → "6.6 KMI"。 */
private fun kernelTag(kernelVersion: String): String {
    val seg = kernelVersion.substringBefore('-').split('.').filter { it.isNotEmpty() }
    if (seg.size < 2) return "KMI"
    return "${seg[0]}.${seg[1]} KMI"
}

/** 从真实指纹推导签名类型标签。 */
private fun fingerprintTag(fingerprint: String): String = when {
    fingerprint.contains("release-keys") -> "RO"
    fingerprint.contains("test-keys") -> "TEST"
    fingerprint.isBlank() -> "—"
    else -> "USER"
}

/**
 * Hero 状态卡：r24 渐变描边（150deg）+ r23 内层（165deg）+ 右上 180px 紫色透明扩散光晕。
 */
@Composable
private fun NcHero(
    state: HomeUiState,
    actions: HomeActions,
    onOpenTab: (Int) -> Unit,
) {
    val t = ncTokens()
    val hero = state.heroState()

    // 状态语义全部由真实运行态推导：安装 / 刷写中 / 待重启 / 工作中 / 需注意
    val (kicker, bigText) = when (hero.phase) {
        HeroPhase.NOT_INSTALLED -> "STOPPED · 未安装" to "未安装"
        HeroPhase.FLASHING -> "FLASHING · 刷写中" to "刷写中"
        HeroPhase.PENDING_REBOOT -> "PENDING · 待重启" to "待重启"
        HeroPhase.WORKING -> "RUNNING · 运行中" to "工作中"
        HeroPhase.ATTENTION -> "ATTENTION · 需注意" to "需注意"
        HeroPhase.FLASH_FAILED -> "FAILED · 刷写失败" to "未工作"
        HeroPhase.UNSUPPORTED -> "UNSUPPORTED · 不支持" to "不支持"
    }
    val accent = when (hero.phase) {
        HeroPhase.WORKING -> t.mint
        HeroPhase.ATTENTION, HeroPhase.FLASHING -> t.amber
        HeroPhase.FLASH_FAILED, HeroPhase.NOT_INSTALLED, HeroPhase.UNSUPPORTED -> t.red
        else -> t.amber
    }
    // 刷写入口：越狱模式下刷写会变砖，因此禁用
    val flashable = hero.canFlash

    NcGradientBorder(
        radius = 24.dp,
        // 暗夜主题：粒子边挂在渐变小卡外层，颜色取主题强调色（amber/mint），不破坏暗色氛围
        modifier = Modifier.particleOutline(
            shape = RoundedCornerShape(24.dp),
            accent = accent,
            bleed = 11.dp,
        ),
        borderColors = t.heroBorder,
        borderStops = t.heroBorderStops,
        borderAngle = 150f,
        cardColors = t.heroCard,
        cardStops = t.heroCardStops,
        cardAngle = 165f,
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .clickable(enabled = flashable) { actions.onInstallClick() },
        ) {
            // 右上角 180px 紫色光晕（向外透明扩散）
            Box(
                Modifier
                    .size(180.dp)
                    .align(Alignment.TopEnd)
                    .offset(x = 50.dp, y = (-70).dp)
            ) {
                NcHeroGlow(t, Modifier.fillMaxSize())
            }
            Column(Modifier.padding(start = 17.dp, end = 17.dp, top = 15.dp, bottom = 14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    NcPulseDot(color = accent)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = kicker,
                        fontSize = 9.5.sp,
                        letterSpacing = 2.6.sp,
                        color = t.heroStatus,
                        fontWeight = FontWeight.Bold,
                    )
                }
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        text = bigText,
                        fontSize = 29.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = (-1.2).sp,
                        color = t.heroBig,
                    )
                    // 运行模式徽章：仅在真实 LKM/GKI 时显示
                    if (hero.modeLabel != null) {
                        Spacer(Modifier.width(9.dp))
                        Box(
                            Modifier
                                .offset(y = (-4).dp)
                                .clip(RoundedCornerShape(7.dp))
                                .background(t.heroBadgeBg)
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = hero.modeLabel,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp,
                                color = t.heroBadgeText,
                            )
                        }
                    }
                    // 越狱模式徽章：仅在真实越狱模式时显示
                    if (hero.jailbreakMode) {
                        Spacer(Modifier.width(7.dp))
                        Box(
                            Modifier
                                .offset(y = (-4).dp)
                                .clip(RoundedCornerShape(7.dp))
                                .background(t.heroBadgeBg)
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = "越狱",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = t.heroBadgeText,
                            )
                        }
                    }
                }
                Spacer(Modifier.height(11.dp))
                androidx.compose.foundation.Canvas(Modifier.fillMaxWidth().height(1.dp)) { drawRect(t.line) }
                Spacer(Modifier.height(9.dp))
                Row {
                    HeroMeta(
                        "VERSION",
                        hero.versionName,
                        t.heroBig,
                        Modifier.weight(1f),
                    )
                    HeroMeta(
                        "MODE",
                        hero.modeLabel ?: "—",
                        if (hero.modeLabel == "LKM") t.mint else t.heroBig,
                        Modifier.weight(1f),
                    )
                    HeroMeta(
                        "UAPI",
                        state.kernelUAPIVersion?.toString() ?: "—",
                        t.heroBig,
                        Modifier.weight(1f),
                    )
                }
                Spacer(Modifier.height(10.dp))
                // Hero 操作区：真实可点入口。四个 chip 在窄屏上放不下一行，
                // 用 FlowRow 折行；每个 chip 内文字锁单行，避免出现「超级用 / 户」断字。
                androidx.compose.foundation.layout.FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    HeroAction(
                        label = if (hero.installed) "刷写 / 更新" else "安装内核",
                        enabled = flashable,
                        primary = !hero.installed,
                        onClick = { actions.onInstallClick() },
                    )
                    HeroAction(
                        label = "刷新镜像",
                        enabled = flashable,
                        primary = false,
                        onClick = { actions.onRefreshImage() },
                    )
                    HeroAction(
                        label = "检查更新",
                        enabled = true,
                        primary = hero.hasUpdate,
                        onClick = { actions.onCheckUpdate() },
                    )
                }
            }
            NcHeroRing(
                modifier = Modifier.align(Alignment.TopEnd).padding(end = 17.dp, top = 15.dp),
                phase = hero.phase,
            )
        }
    }
}

/** Hero 底部操作按钮 —— 全部绑定真实动作。 */
@Composable
private fun HeroAction(
    label: String,
    enabled: Boolean,
    primary: Boolean,
    onClick: () -> Unit,
) {
    val t = ncTokens()
    Box(
        Modifier
            .clip(RoundedCornerShape(9.dp))
            .background(if (primary) t.heroBadgeBg else t.s2)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 11.dp, vertical = 6.dp),
    ) {
        Text(
            text = label,
            fontSize = 10.5.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            softWrap = false,
            color = when {
                !enabled -> t.tx3
                primary -> t.heroBadgeText
                else -> t.tx2
            },
        )
    }
}

@Composable
private fun HeroMeta(k: String, v: String, valueColor: androidx.compose.ui.graphics.Color, modifier: Modifier = Modifier) {
    val t = ncTokens()
    Column(modifier) {
        Text(k, fontSize = 9.sp, letterSpacing = 1.6.sp, color = t.heroMetaKey, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(5.dp))
        Text(
            v,
            fontSize = 12.5.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.2.sp,
            color = valueColor,
            maxLines = 1,
        )
    }
}
