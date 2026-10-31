package me.diksu.ultra.foxsu.ui.screen.glass

import me.diksu.ultra.foxsu.ui.component.bottombar.useSideDock
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.MarqueeAnimationMode
import androidx.compose.foundation.MarqueeSpacing
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.annotation.StringRes
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import me.diksu.ultra.BuildConfig
import me.diksu.ultra.R
import me.diksu.ultra.foxsu.ui.component.bouncyClick
import me.diksu.ultra.foxsu.data.settings.CustomizationStore
import me.diksu.ultra.foxsu.ui.LocalUiMode
import me.diksu.ultra.foxsu.ui.component.bottombar.NavigationBadgeState
import me.diksu.ultra.foxsu.ui.screen.home.HeroPhase
import me.diksu.ultra.foxsu.ui.screen.home.HomeActions
import me.diksu.ultra.foxsu.ui.screen.home.HomeIssueStrip
import me.diksu.ultra.foxsu.ui.screen.ag.particleOutline
import me.diksu.ultra.foxsu.ui.screen.home.HomeUiState
import me.diksu.ultra.foxsu.ui.screen.home.freshIssues
import me.diksu.ultra.foxsu.ui.screen.home.heroState
import me.diksu.ultra.foxsu.ui.theme.glassTokens
import me.diksu.ultra.foxsu.ui.uiModeLabelRes

/** FoxSu Glass 主页：环境光晕 + 白色玻璃面板 + 霓虹描边英雄卡 + 2×2 统计卡。 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun GlassHome(
    state: HomeUiState,
    actions: HomeActions,
    bottomInnerPadding: Dp,
    navigationBadge: NavigationBadgeState,
    onOpenTab: (Int) -> Unit,
    onOpenAuthor: () -> Unit = {},
) {
    val issues = state.freshIssues()
    val sideDock = useSideDock()
    var showStylePicker by remember { mutableStateOf(false) }
    var showRebootDialog by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = if (sideDock) 96.dp else 13.dp, end = 13.dp, top = glassStatusBarTop() + 4.dp, bottom = bottomInnerPadding + 88.dp),
    ) {
        item {
            GlassTopBar(
                title = CustomizationStore.current.homeTitle.ifBlank { "FoxSu" },
                titleColor = CustomizationStore.current.titleColor
                    .takeIf { it != 0 }?.let { Color(it) },
                subtitle = stringResource(R.string.fx_glass_home_subtitle),
                showDot = state.isRootAvailable,
                trailing = {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        // 主题（界面风格选择器）
                        GlassTopButton(iconRes = R.drawable.fx_ic_d_palette, onClick = { showStylePicker = true })
                        // 作者
                        GlassTopButton(iconRes = R.drawable.fx_ic_d_user, onClick = onOpenAuthor)
                        // 重启（真正的重启面板，不是刷写页）
                        GlassTopButton(iconRes = R.drawable.fx_ic_d_power2, onClick = { showRebootDialog = true })
                    }
                },
            )
        }
        item {
            HomeIssueStrip(
                state = state,
                accent = glassTokens().amber,
                onInstall = if (state.heroState().canFlash) ({ actions.onInstallClick() }) else null,
            )
            GlassHeroCard(state = state, actions = actions, issueCount = issues.size, onInstall = actions.onInstallClick)
        }
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(IntrinsicSize.Min),
                horizontalArrangement = Arrangement.spacedBy(11.dp),
            ) {
                GlassNCard(
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                    iconRes = R.drawable.fx_ic_d_phone,
                    title = stringResource(R.string.fx_home_device_model),
                    value = state.systemInfo.deviceModel,
                    caption = stringResource(R.string.fx_fresh_home_device_sub),
                    accent = glassTokens().mint,
                )
                GlassNCard(
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                    iconRes = R.drawable.fx_ic_d_palette,
                    title = stringResource(R.string.fx_settings_ui_mode),
                    value = stringResource(uiModeLabelRes(LocalUiMode.current))
                        .removePrefix("FoxSu "),
                    caption = stringResource(R.string.fx_fresh_home_style_switch),
                    accent = glassTokens().cyan,
                    onClick = { showStylePicker = true },
                )
            }
        }
        // 越狱模式入口：真实启动 MagicaService（条件与 Fresh / Miuix / Nocturne 一致）
        if (state.ksuVersion == null && state.kernelVersion.isGKI() && state.isSELinuxPermissive) {
            item {
                GlassNCard(
                    modifier = Modifier.fillMaxWidth(),
                    iconRes = R.drawable.fx_ic_d_terminal,
                    title = stringResource(R.string.fx_home_jailbreak),
                    value = stringResource(R.string.fx_jailbreak_mode),
                    caption = stringResource(R.string.fx_fresh_home_jailbreak_sub),
                    accent = glassTokens().amber,
                    onClick = { actions.onJailbreakClick() },
                )
                Spacer(Modifier.height(11.dp))
            }
        }
        item { Spacer(Modifier.height(11.dp)) }
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(IntrinsicSize.Min),
                horizontalArrangement = Arrangement.spacedBy(11.dp),
            ) {
                GlassNCard(
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                    iconRes = R.drawable.fx_ic_d_shield,
                    title = stringResource(R.string.fx_superuser),
                    value = navigationBadge.superuserCount.toString(),
                    caption = stringResource(R.string.fx_fresh_home_superuser_sub),
                    accent = glassTokens().violet,
                    onClick = { onOpenTab(1) },
                )
                GlassNCard(
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                    iconRes = R.drawable.fx_ic_d_grid,
                    title = stringResource(R.string.fx_module),
                    value = navigationBadge.moduleEnabledCount.toString(),
                    caption = stringResource(R.string.fx_fresh_home_module_sub),
                    accent = glassTokens().pink,
                    onClick = { onOpenTab(2) },
                )
            }
        }
        item { Spacer(Modifier.height(11.dp)) }
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(IntrinsicSize.Min),
                horizontalArrangement = Arrangement.spacedBy(11.dp),
            ) {
                GlassStatusCard(
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                    iconRes = R.drawable.fx_ic_d_lock,
                    title = stringResource(R.string.fx_glass_home_selinux),
                    subtitle = stringResource(
                        if (state.isSELinuxPermissive) R.string.fx_selinux_status_permissive
                        else R.string.fx_selinux_status_enforcing
                    ),
                    accent = glassTokens().blue,
                    badge = if (state.isSELinuxPermissive) "Permissive" else "Enforcing",
                    badgeColor = if (state.isSELinuxPermissive) glassTokens().amber else glassTokens().lime,
                )
                GlassStatusCard(
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                    iconRes = R.drawable.fx_ic_d_sliders,
                    title = stringResource(R.string.fx_home_seccomp_status),
                    subtitle = seccompLabelRes(state.systemInfo.seccompStatus).let { stringResource(it) },
                    accent = glassTokens().amber,
                    badge = seccompShort(state.systemInfo.seccompStatus),
                    badgeColor = glassTokens().amber,
                )
            }
        }
        item { Spacer(Modifier.height(12.dp)) }
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(IntrinsicSize.Min),
                horizontalArrangement = Arrangement.spacedBy(11.dp),
            ) {
                GlassLinkCard(
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                    iconRes = R.drawable.fx_ic_d_heart,
                    title = stringResource(R.string.fx_glass_home_support),
                    subtitle = stringResource(R.string.fx_glass_home_support_sub),
                    accent = glassTokens().flame,
                    onClick = { actions.onOpenUrl("https://github.com/Cmry-mlc/FoxSu") },
                )
                GlassLinkCard(
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                    iconRes = R.drawable.fx_ic_d_book,
                    title = stringResource(R.string.fx_glass_home_learn),
                    subtitle = stringResource(R.string.fx_glass_home_learn_sub),
                    accent = glassTokens().lemon,
                    onClick = { actions.onOpenUrl("https://kernelsu.org") },
                )
            }
        }
        item { Spacer(Modifier.height(11.dp)) }
        item {
            GlassKernelRow(kernelVersion = state.systemInfo.kernelVersion)
        }
        item { Spacer(Modifier.height(10.dp)) }
        item {
            // 系统指纹（单独一行，可长按复制由系统选择器处理，此处只展示）
            GlassWide(
                iconRes = R.drawable.fx_ic_d_android,
                title = stringResource(R.string.fx_glass_home_fingerprint),
                subtitle = state.systemInfo.fingerprint.ifBlank { "—" },
                accent = glassTokens().blue,
            )
        }
    }

    if (showStylePicker) {
        GlassStylePickerDialog(current = LocalUiMode.current, onDismiss = { showStylePicker = false })
    }
    if (showRebootDialog) {
        GlassRebootDialog(onDismiss = { showRebootDialog = false })
    }
}

@StringRes
private fun seccompLabelRes(status: Int): Int = when (status) {
    2 -> R.string.fx_seccomp_status_strict
    1 -> R.string.fx_seccomp_status_filter
    -1 -> R.string.fx_seccomp_status_disabled
    else -> R.string.fx_seccomp_status_not_supported
}

private fun seccompShort(status: Int): String = when (status) {
    2 -> "Strict"
    1 -> "Filter"
    -1 -> "Off"
    else -> "N/A"
}

/**
 * .top：46 圆头像（狐头，颜色随页面：主页 flame / 超管 amber / 模块 cyan / 设置 violet）
 * + 22/800 标题 + 11 sub + 右侧 38×34 圆按钮（gap 8）
 */
@Composable
fun GlassTopBar(
    title: String,
    subtitle: String,
    showDot: Boolean = false,
    onStyleClick: (() -> Unit)? = null,
    onActionClick: (() -> Unit)? = null,
    actionIconRes: Int = R.drawable.fx_ic_d_user,
    styleIconRes: Int = R.drawable.fx_ic_d_palette,
    titleSize: Int = 22,
    /** 标题文字颜色；null = 跟随主题。定制 → 标题栏 可覆盖。 */
    titleColor: Color? = null,
    accent: Color? = null,
    trailing: (@Composable RowScope.() -> Unit)? = null,
) {
    val t = glassTokens()
    val foxColor = accent ?: t.flame
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp, bottom = 14.dp, start = 2.dp, end = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(modifier = Modifier.size(46.dp)) {
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .shadow(
                        6.dp,
                        CircleShape,
                        ambientColor = foxColor.copy(alpha = 0.22f),
                        spotColor = foxColor.copy(alpha = 0.22f),
                    )
                    .clip(CircleShape)
                    .background(t.avaBg),
                contentAlignment = Alignment.Center,
            ) {
                // Glass 的界面图标改为启动器图标（与桌面一致）
                me.diksu.ultra.foxsu.ui.component.LauncherMark(
                    modifier = Modifier.size(36.dp),
                    cornerPercent = 28,
                )
            }
            if (showDot) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .size(12.dp)
                        .clip(CircleShape)
                        .background(t.lime)
                        .border(2.dp, t.avaBg, CircleShape),
                )
            }
        }
        Spacer(Modifier.width(13.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                title,
                fontSize = titleSize.sp,
                fontWeight = FontWeight.ExtraBold,
                // 自定义标题色优先；没设则跟随全局强调色
                color = titleColor ?: me.diksu.ultra.foxsu.ui.theme.TitleInk.solid(me.diksu.ultra.foxsu.ui.theme.currentAccent.primary),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                subtitle,
                fontSize = 11.sp,
                color = t.sub,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .padding(top = 3.dp)
                    .marquee(),
            )
        }
        if (trailing != null) {
            trailing.invoke(this)
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (onStyleClick != null) {
                    GlassTopButton(iconRes = styleIconRes, onClick = onStyleClick)
                }
                if (onActionClick != null) {
                    GlassTopButton(iconRes = actionIconRes, onClick = onActionClick)
                }
            }
        }
    }
}

/** .tbtn：36 正圆玻璃按钮（与 46 头像同为圆形，边界更整齐） */
@Composable
fun GlassTopButton(iconRes: Int, onClick: () -> Unit) {
    val t = glassTokens()
    Box(
        modifier = Modifier
            .size(40.dp)
            .shadow(4.dp, CircleShape, spotColor = t.glassShadow, ambientColor = t.glassShadow)
            .clip(CircleShape)
            .background(t.tbtnBg)
            .border(1.dp, t.tbtnBorder, CircleShape)
            .bouncyClick(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = null,
            modifier = Modifier.size(18.dp),
            tint = t.tbtnIcon,
        )
    }
}

/**
 * .hero：26 圆角白玻璃 + 120° 霓虹渐变描边（仅 1dp 描边，卡面是白玻璃，不是彩色块）。
 * 内容顺序：gline 渐变小字 → 23/800 状态 → 「版本：xxx」+ tag 组同一行 → hint；
 * 右侧 60 圆白 seal（violet 对勾）垂直居中并压住 tag 行。
 */
@Composable
fun GlassHeroCard(
    state: HomeUiState,
    actions: HomeActions,
    issueCount: Int,
    onInstall: () -> Unit,
) {
    val t = glassTokens()
    val hero = state.heroState()
    val hasIssue = issueCount > 0
    // 状态语义由真实运行态推导
    val (kickerText, statusText) = when (hero.phase) {
        HeroPhase.NOT_INSTALLED -> R.string.fx_glass_hero_stopped to R.string.fx_glass_status_not_installed
        HeroPhase.FLASHING -> R.string.fx_glass_hero_flashing to R.string.fx_glass_status_flashing
        HeroPhase.PENDING_REBOOT -> R.string.fx_glass_hero_pending to R.string.fx_glass_status_pending_reboot
        HeroPhase.WORKING -> R.string.fx_glass_hero_working to R.string.fx_glass_status_ok
        HeroPhase.ATTENTION -> R.string.fx_glass_hero_attention to R.string.fx_glass_status_issue
        HeroPhase.FLASH_FAILED -> R.string.fx_glass_hero_failed to R.string.fx_glass_status_flash_failed
        HeroPhase.UNSUPPORTED -> R.string.fx_glass_hero_unsupported to R.string.fx_glass_status_unsupported
    }
    // 刷写入口：越狱模式下刷写会变砖
    val flashable = hero.canFlash
    // seal 字形跟着真实阶段走：工作中 / 待重启（刷写已成功）才是对勾，其余一律叉号
    val sealOk = hero.phase == HeroPhase.WORKING || hero.phase == HeroPhase.PENDING_REBOOT
    val sealTint = when {
        hero.phase == HeroPhase.WORKING -> t.violet
        hero.phase == HeroPhase.PENDING_REBOOT || hero.phase == HeroPhase.FLASHING -> t.amber
        else -> t.flame
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            // 玻璃主题：粒子边取主题自身强调色，和玻璃高光同频，不抢主体
            .particleOutline(
                shape = RoundedCornerShape(26.dp),
                accent = t.violet,
                bleed = 11.dp,
            )
            .shadow(
                10.dp,
                RoundedCornerShape(26.dp),
                ambientColor = t.glassShadow,
                spotColor = t.glassShadow,
            )
            .clip(RoundedCornerShape(26.dp))
            .background(if (t.isDark) Color(0x08FFFFFF) else Color(0x9EFFFFFF))
            .border(1.dp, t.heroGrad, RoundedCornerShape(26.dp))
            .clickable(enabled = flashable) { onInstall() },
    ) {
        Box {
        Box(modifier = Modifier.padding(20.dp)) {
            // 对勾已移到右上角，正文只需让出右上徽章宽度
            Column(modifier = Modifier.padding(end = 58.dp)) {
                Text(
                    text = stringResource(kickerText),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 2.sp,
                    style = TextStyle(brush = t.glineGrad),
                )
                Text(
                    text = stringResource(statusText),
                    fontSize = 23.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = t.ink,
                    modifier = Modifier.padding(top = 7.dp),
                )
                if (hasIssue) {
                    Row(
                        modifier = Modifier
                            .padding(top = 10.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(t.amber.copy(alpha = 0.15f))
                            .border(1.dp, t.amber.copy(alpha = 0.3f), RoundedCornerShape(10.dp))
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = stringResource(R.string.fx_fresh_home_issue_count, issueCount),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = t.amber,
                            modifier = Modifier.weight(1f, fill = false),
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(
                            text = stringResource(R.string.fx_fresh_home_install),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color.White,
                            modifier = Modifier
                                .clip(RoundedCornerShape(99.dp))
                                .background(t.flame)
                                .clickable(onClick = onInstall)
                                .padding(horizontal = 12.dp, vertical = 5.dp),
                        )
                    }
                }
                // .verrow：版本文字 + tag 组（flex-wrap，同设计稿）
                FlowRow(
                    modifier = Modifier.padding(top = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(9.dp),
                    verticalArrangement = Arrangement.spacedBy(7.dp),
                ) {
                    // 版本号：点击真实检查更新
                    Text(
                        text = stringResource(R.string.fx_glass_version_fmt, hero.versionName),
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (hero.hasUpdate) t.flame else t.ink2,
                        modifier = Modifier.clickable { actions.onCheckUpdate() },
                    )
                    // 自定义 LKM：仅在真实自定义时显示
                    if (hero.customLkm) {
                        GlassTag(
                            text = stringResource(R.string.fx_glass_tag_custom),
                            color = t.amber,
                            bgAlpha = 0.15f,
                        )
                    }
                    // 运行模式：仅在真实 LKM/GKI 时显示
                    if (hero.modeLabel != null) {
                        GlassTag(
                            text = hero.modeLabel,
                            color = t.violet,
                            bgAlpha = 0.12f,
                        )
                    }
                    // 越狱模式：仅在真实越狱时显示
                    if (hero.jailbreakMode) {
                        GlassTag(
                            text = stringResource(R.string.fx_glass_tag_jailbreak),
                            color = t.cyan,
                            bgAlpha = 0.12f,
                        )
                    }
                    // 安全模式：仅在真实安全模式时显示
                    if (hero.safeMode) {
                        GlassTag(
                            text = stringResource(R.string.fx_safe_mode),
                            color = t.amber,
                            bgAlpha = 0.15f,
                        )
                    }
                    // 刷新镜像：真实执行 ksud boot-patch -f
                    Text(
                        text = stringResource(R.string.fx_home_refresh_image),
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = t.violet,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable(enabled = hero.canFlash) { actions.onRefreshImage() }
                            .padding(horizontal = 10.dp, vertical = 4.dp),
                    )
                    // 有新版本：真实徽标
                    if (hero.hasUpdate) {
                        GlassTag(
                            text = stringResource(R.string.fx_glass_tag_update, hero.latestVersionCode),
                            color = t.flame,
                            bgAlpha = 0.15f,
                        )
                    }
                }
                Text(
                    text = stringResource(
                        if (hero.installed) R.string.fx_fresh_home_flash_hint else R.string.fx_home_click_to_install
                    ),
                    fontSize = 10.5.sp,
                    color = if (t.isDark) t.faint else Color(0xFFA39AB2),
                    modifier = Modifier.padding(top = 14.dp),
                )
            }
        }

        // .seal：贴卡片右上角（不再是内容区右上角），距边 10dp
        GlassSeal(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 10.dp, end = 10.dp),
            checkColor = sealTint,
            sealBg = if (t.isDark) Color(0xFF14141E) else Color(0xF2FFFFFF),
            border = if (t.isDark) Color(0x1FFFFFFF) else Color(0xFFFFFFFF),
            ok = sealOk,
            coreSize = 40,
            iconSize = 18,
        )
        }
    }
    Spacer(Modifier.height(13.dp))
}

/** .tag：霓虹亮字 + 同色半透明底 + 同色描边 */
@Composable
fun GlassTag(text: String, color: Color, bgAlpha: Float = 0.15f) {
    Text(
        text = text,
        fontSize = 10.sp,
        fontWeight = FontWeight.ExtraBold,
        color = color,
        maxLines = 1,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(color.copy(alpha = bgAlpha))
            .border(1.dp, color.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
            .padding(horizontal = 10.dp, vertical = 4.dp),
    )
}

/** .st：行尾状态徽章 */
@Composable
fun GlassStBadge(text: String, color: Color, compact: Boolean = false) {
    Text(
        text = text,
        fontSize = if (compact) 9.sp else 9.5.sp,
        fontWeight = FontWeight.ExtraBold,
        color = color,
        maxLines = 1,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(color.copy(alpha = 0.12f))
            .border(1.dp, color.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
            .padding(horizontal = if (compact) 7.dp else 10.dp, vertical = if (compact) 3.dp else 4.dp),
    )
}

/**
 * .ncard：22 圆角玻璃统计卡。右上角一枚大半径淡色霓虹圆（74px，右下 1/4 内扣），
 * 内容自上而下：38 chip → 16 间距 → cT 11.5 sub → cV 17/800 彩色值 → 15 间距 → cS 10 faint
 */
@Composable
fun GlassNCard(
    modifier: Modifier = Modifier,
    iconRes: Int,
    title: String,
    value: String,
    caption: String,
    accent: Color,
    onClick: (() -> Unit)? = null,
) {
    val t = glassTokens()
    Box(
        modifier = modifier
            .shadow(
                8.dp,
                RoundedCornerShape(22.dp),
                ambientColor = t.glassShadow,
                spotColor = t.glassShadow,
            )
            .clip(RoundedCornerShape(22.dp))
            // .ncard：暗色 --panel2 #1c1c28（实色）/ 亮色 rgba(255,255,255,.55)
            .background(if (t.isDark) t.panel2 else Color(0x9EFFFFFF))
            .border(1.dp, t.glassBorder, RoundedCornerShape(22.dp))
            .clickable(enabled = onClick != null) { onClick?.invoke() },
    ) {
        // ::after 右上角彩色扩散 —— 74 圆 / right-top -22 / opacity .28 / blur(14)
        // 关键：blur 必须 Unbounded，否则默认 Rectangle 会把模糊裁在 74dp 边界上，变成硬边方块
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .offset(x = 22.dp, y = (-22).dp)
                .size(74.dp)
                .blur(16.dp, edgeTreatment = BlurredEdgeTreatment.Unbounded)
                .background(accent.copy(alpha = if (t.isDark) 0.34f else 0.30f), CircleShape),
        )
        // 布局：chip(38) + 16 + cT(15) + cV(25) + 15 + cS(13) + padding(30) ≈ 152
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(15.dp),
        ) {
            GlassChip(iconRes = iconRes, accent = accent, shadowed = true)
            Spacer(Modifier.height(16.dp))
            Text(title, fontSize = 11.5.sp, fontWeight = FontWeight.Bold, color = t.sub, maxLines = 1)
            Text(
                value,
                fontSize = 17.sp,
                fontWeight = FontWeight.ExtraBold,
                color = accent,
                lineHeight = 22.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 3.dp),
            )
            Spacer(Modifier.height(15.dp))
            Text(caption, fontSize = 10.sp, color = t.faint, maxLines = 1)
        }
    }
}

/** Glass 页面统一的状态栏顶部内边距（避免标题栏与状态栏重叠） */
@Composable
fun glassStatusBarTop(): Dp =
    WindowInsets.statusBars.asPaddingValues().calculateTopPadding()

/** .chip：38 圆角12 玻璃 chip（亮色 rgba(255,255,255,.6) + 白描边 + 0 2 8 投影；暗色 rgba(255,255,255,.05)） */
@Composable
fun GlassChip(
    iconRes: Int,
    accent: Color,
    shadowed: Boolean = false,
    /** 实玻璃卡上使用：底色改为同色系淡色，避免白色 chip 淹没在白色卡面上 */
    tinted: Boolean = false,
) {
    val t = glassTokens()
    Box(
        modifier = Modifier
            .size(38.dp)
            .then(
                if (shadowed && !t.isDark && !tinted) {
                    Modifier.shadow(2.dp, RoundedCornerShape(12.dp), spotColor = Color(0x1A5A5578))
                } else {
                    Modifier
                }
            )
            .clip(RoundedCornerShape(12.dp))
            .background(
                when {
                    tinted -> accent.copy(alpha = if (t.isDark) 0.16f else 0.12f)
                    t.isDark -> Color(0x0DFFFFFF)
                    else -> Color(0x99FFFFFF)
                }
            )
            .border(
                1.dp,
                when {
                    tinted -> accent.copy(alpha = 0.22f)
                    t.isDark -> t.glassBorder
                    else -> Color(0xD9FFFFFF)
                },
                RoundedCornerShape(12.dp),
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = null,
            modifier = Modifier.size(18.dp),
            tint = accent,
        )
    }
}

/**
 * .wide（两列版）：20 圆角玻璃，chip + 标题（13/800）/副标题（10.5 sub）纵向 + 右侧 st 徽章。
 * 用于 SELinux / Seccomp 这类状态条。
 */
@Composable
fun GlassStatusCard(
    modifier: Modifier = Modifier,
    iconRes: Int,
    title: String,
    subtitle: String,
    accent: Color,
    badge: String,
    badgeColor: Color,
) {
    val t = glassTokens()
    Row(
        modifier = modifier
            .glassCard(20.dp)
            .padding(horizontal = 14.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        GlassChip(iconRes = iconRes, accent = accent, shadowed = true)
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                title,
                fontSize = 13.sp,
                fontWeight = FontWeight.ExtraBold,
                color = t.ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                subtitle,
                fontSize = 10.5.sp,
                color = t.sub,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .padding(top = 2.dp)
                    .marquee(),
            )
        }
        Spacer(Modifier.width(6.dp))
        GlassStBadge(text = badge, color = badgeColor, compact = true)
    }
}

/** .wide（两列版，带箭头）：chip + 标题/副标题 + › */
@Composable
fun GlassLinkCard(
    modifier: Modifier = Modifier,
    iconRes: Int,
    title: String,
    subtitle: String,
    accent: Color,
    onClick: () -> Unit,
) {
    val t = glassTokens()
    Row(
        modifier = modifier
            .glassCard(20.dp)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        GlassChip(iconRes = iconRes, accent = accent, shadowed = true)
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                title,
                fontSize = 13.sp,
                fontWeight = FontWeight.ExtraBold,
                color = t.ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                subtitle,
                fontSize = 10.sp,
                color = t.sub,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .padding(top = 2.dp)
                    .marquee(),
            )
        }
        Text("›", fontSize = 17.sp, color = t.faint, modifier = Modifier.padding(start = 2.dp))
    }
}

/** 单列整行 .wide：内核版本 */
@Composable
fun GlassKernelRow(kernelVersion: String) {
    val t = glassTokens()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .glassCard(20.dp)
            .padding(horizontal = 14.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        GlassChip(iconRes = R.drawable.fx_ic_d_chip, accent = t.lime, shadowed = true)
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(stringResource(R.string.fx_home_kernel), fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, color = t.ink)
            Text(
                text = kernelVersion.ifBlank { "—" },
                fontSize = 10.5.sp,
                color = t.sub,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
}

/** 通用单列 .wide（模块页快捷操作用） */
@Composable
fun GlassWide(
    modifier: Modifier = Modifier,
    iconRes: Int,
    title: String,
    subtitle: String,
    accent: Color,
    /** true 时使用不透明「实玻璃」，用于详情页（避免透出导航栈上一页） */
    solid: Boolean = false,
    trailing: (@Composable RowScope.() -> Unit)? = null,
    showChevron: Boolean = false,
    onClick: (() -> Unit)? = null,
) {
    val t = glassTokens()
    Row(
        modifier = modifier
            .fillMaxWidth()
            .then(if (solid) Modifier.glassCardSolid(20.dp) else Modifier.glassCard(20.dp))
            .clickable(enabled = onClick != null) { onClick?.invoke() }
            .padding(horizontal = 14.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        GlassChip(iconRes = iconRes, accent = accent, shadowed = true, tinted = solid)
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, color = t.ink, maxLines = 1)
            Text(
                subtitle,
                fontSize = 10.5.sp,
                color = t.sub,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .padding(top = 2.dp)
                    .marquee(),
            )
        }
        if (trailing != null) {
            Spacer(Modifier.width(8.dp))
            trailing.invoke(this)
        } else if (showChevron) {
            Text("›", fontSize = 17.sp, color = t.faint)
        }
    }
}

/** 设计稿玻璃面板（.hero,.ncard,.wide,.urow,.mod,.srow,.search）：
 *  亮色 rgba(255,255,255,.55) + 1px rgba(255,255,255,.8) + 0 8 24 rgba(90,85,120,.12) + inset 0 1 0 rgba(255,255,255,.9)
 *  暗色 rgba(255,255,255,.03) + 1px rgba(255,255,255,.07)
 */
@Composable
fun Modifier.glassCard(radius: androidx.compose.ui.unit.Dp): Modifier {
    val t = glassTokens()
    return this
        .shadow(
            8.dp,
            RoundedCornerShape(radius),
            ambientColor = if (t.isDark) Color(0x99000000) else Color(0x1F5A5578),
            spotColor = if (t.isDark) Color(0x99000000) else Color(0x1F5A5578),
        )
        .clip(RoundedCornerShape(radius))
        .background(if (t.isDark) Color(0x08FFFFFF) else Color(0x9EFFFFFF))
        .border(1.dp, if (t.isDark) Color(0x12FFFFFF) else Color(0xCCFFFFFF), RoundedCornerShape(radius))
}

/** 界面风格切换弹窗（Glass 版） */
@Composable
fun GlassStylePickerDialog(
    current: me.diksu.ultra.foxsu.ui.UiMode,
    onDismiss: () -> Unit,
) {
    me.diksu.ultra.foxsu.ui.component.StylePickerDialog(current = current, onDismiss = onDismiss)
}

/**
 * 英雄卡右侧 .seal：60 圆 + 双层呼吸霓虹外圈（随机七彩，色相缓慢流转）。
 * - 外圈 1：scale 1.0→1.22 + alpha 0.55→0 的扩散环（呼吸）
 * - 外圈 2：固定 1px 七彩描边，色相随时间旋转
 * - 内核 60 圆白/黑底 + 状态字形，字形自带轻微呼吸透明度
 *
 * [ok] 由真实 HeroPhase 推导：只有刷写成功（工作中 / 待重启）才是对勾，其余一律叉号。
 */
@Composable
fun GlassSeal(
    modifier: Modifier = Modifier,
    checkColor: Color,
    sealBg: Color,
    border: Color,
    ok: Boolean = true,
    coreSize: Int = 44,
    iconSize: Int = 20,
) {
    val hud = rememberInfiniteTransition(label = "seal")
    val breath by hud.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2200, easing = LinearEasing), RepeatMode.Restart),
        label = "breath",
    )
    val huePhase by hud.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(6000, easing = LinearEasing), RepeatMode.Restart),
        label = "hue",
    )
    // 随机七彩：以当前时间为种子，每次进入页面得到一组随机起始色
    val palette = remember {
        listOf(
            Color(0xFFFF7A59), Color(0xFFFFB35C), Color(0xFFFFE45C), Color(0xFF6EF08A),
            Color(0xFF6EF0C0), Color(0xFF5CE1E6), Color(0xFF7AA2FF), Color(0xFFC77DFF), Color(0xFFFF7AC8),
        ).shuffled()
    }
    val ringColor = lerpHue(palette, huePhase)
    val ringColor2 = lerpHue(palette, (huePhase + 0.35f) % 1f)

    val boxSize = (coreSize + 22).dp
    Box(modifier = modifier.size(boxSize), contentAlignment = Alignment.Center) {
        // 扩散呼吸环
        Box(
            modifier = Modifier
                .size(coreSize.dp)
                .graphicsLayer {
                    val s = 1f + breath * 0.34f
                    scaleX = s
                    scaleY = s
                    alpha = (1f - breath) * 0.55f
                }
                .border(1.5.dp, Brush.sweepGradient(listOf(ringColor.copy(alpha = 0f), ringColor, ringColor2, ringColor.copy(alpha = 0f))), CircleShape),
        )
        // 固定七彩描边（色相流转）
        Box(
            modifier = Modifier
                .size((coreSize + 3).dp)
                .graphicsLayer { alpha = 0.5f + 0.3f * (1f - Math.abs(breath - 0.5f) * 2f) }
                .border(
                    1.dp,
                    Brush.sweepGradient(listOf(ringColor, ringColor2, ringColor)),
                    CircleShape,
                ),
        )
        // 内核
        Box(
            modifier = Modifier
                .size(coreSize.dp)
                .shadow(
                    12.dp,
                    CircleShape,
                    ambientColor = ringColor.copy(alpha = 0.32f),
                    spotColor = ringColor.copy(alpha = 0.32f),
                )
                .clip(CircleShape)
                .background(sealBg)
                .border(1.dp, border, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(if (ok) R.drawable.fx_ic_d_check else R.drawable.fx_ic_d_cross),
                contentDescription = null,
                modifier = Modifier
                    .size(iconSize.dp)
                    .graphicsLayer { alpha = 0.85f + 0.15f * breath },
                tint = checkColor,
            )
        }
    }
}

/** 在调色板里按 t∈[0,1) 线性插值取色，用于色相缓慢流转 */
private fun lerpHue(palette: List<Color>, t: Float): Color {
    val n = palette.size
    val pos = (t % 1f + 1f) % 1f * n
    val i = pos.toInt() % n
    val f = pos - pos.toInt()
    val a = palette[i]
    val b = palette[(i + 1) % n]
    return Color(
        red = a.red + (b.red - a.red) * f,
        green = a.green + (b.green - a.green) * f,
        blue = a.blue + (b.blue - a.blue) * f,
        alpha = 1f,
    )
}

/**
 * 单行文字自动横向滚动（跑马灯）：文字超出可用宽度时循环滚动，未超出时静止不动。
 * 用于设置项 / 功能名的副标题，避免「显示不完整」。
 */
@OptIn(ExperimentalFoundationApi::class)
fun Modifier.marquee(): Modifier = this.basicMarquee(
    iterations = Int.MAX_VALUE,
    animationMode = MarqueeAnimationMode.Immediately,
    repeatDelayMillis = 1600,
    initialDelayMillis = 1200,
    spacing = MarqueeSpacing(6.dp),
    velocity = 28.dp,
)

/** 详情页用「实玻璃」：比列表页更不透明，保证文字可读、不透视下层内容 */
@Composable
fun Modifier.glassCardSolid(radius: androidx.compose.ui.unit.Dp): Modifier {
    val t = glassTokens()
    return this
        .shadow(
            8.dp,
            RoundedCornerShape(radius),
            ambientColor = t.glassShadow,
            spotColor = t.glassShadow,
        )
        .clip(RoundedCornerShape(radius))
        .background(if (t.isDark) Color(0xFF16161F) else Color(0xF2FFFFFF))
        .border(1.dp, if (t.isDark) Color(0x14FFFFFF) else Color(0xFFFFFFFF), RoundedCornerShape(radius))
}
