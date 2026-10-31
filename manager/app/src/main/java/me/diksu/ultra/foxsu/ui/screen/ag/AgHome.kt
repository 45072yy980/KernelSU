package me.diksu.ultra.foxsu.ui.screen.ag

import me.diksu.ultra.foxsu.ui.component.bottombar.useSideDock
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import me.diksu.ultra.R
import me.diksu.ultra.foxsu.data.settings.CustomizationStore
import me.diksu.ultra.foxsu.ui.LocalUiMode
import me.diksu.ultra.foxsu.ui.component.StylePickerDialog
import me.diksu.ultra.foxsu.ui.screen.home.HomeActions
import me.diksu.ultra.foxsu.ui.screen.home.HomeUiState
import me.diksu.ultra.foxsu.ui.screen.home.HeroPhase
import me.diksu.ultra.foxsu.ui.screen.home.heroState

/**
 * 晨曦玻璃 · 主页（设计稿 p-home 完整实现）。
 * 状态卡（渐变描边 2px + 白芯 + 右上 64dp 环形勾 + 青色光斑）→ 统计卡 ×2 → 信息卡 → 安全状态卡 → 支持卡。
 */
@Composable
fun AgHome(
    state: HomeUiState,
    actions: HomeActions,
    onOpenTab: (Int) -> Unit,
    onOpenAuthor: () -> Unit,
    bottomInnerPadding: androidx.compose.ui.unit.Dp = androidx.compose.ui.unit.Dp.Unspecified,
) {
    // LocalNavigator 必须在 composable 上下文取一次：onClick 里不能再读 CompositionLocal
    val donateNav = me.diksu.ultra.foxsu.ui.navigation3.LocalNavigator.current
    val hero = state.heroState()
    var showReboot by remember { mutableStateOf(false) }
    var showStyle by remember { mutableStateOf(false) }
    // 顶栏三个按钮的屏幕位置：弹层从这里做共享元素放大，关闭时缩回
    var authorBtnBounds by remember { mutableStateOf(androidx.compose.ui.geometry.Rect.Zero) }
    var themeBtnBounds by remember { mutableStateOf(androidx.compose.ui.geometry.Rect.Zero) }
    var powerBtnBounds by remember { mutableStateOf(androidx.compose.ui.geometry.Rect.Zero) }
    val sideDock = useSideDock()
    // 设计稿画板宽 390dp：`.screen{padding:0 18px}`、`.headwrap` 顶栏 22px。
    // 按实际屏宽等比换算，任何机型上卡片留白占比都与设计稿一致
    // （360dp 屏 → 16.6dp，411dp 屏 → 19dp），而不是固定 18dp。
    val boardW = androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp.dp
    val edgePad = boardW * (18f / 390f)
    val headPad = boardW * (22f / 390f)
    val listBottomPad = if (bottomInnerPadding == androidx.compose.ui.unit.Dp.Unspecified) {
        24.dp
    } else {
        bottomInnerPadding + 24.dp
    }

    // 固定顶栏 + 毛玻璃：不随内容滚动（dev-handoff .headwrap 要求）
    Column(
        Modifier
            .fillMaxSize()
            .agPageBg()
            .statusBarsPadding(),
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .background(Ag.headSurface()).agHeadBlur(),
        ) {
            Spacer(Modifier.height(10.dp))
            // ── 顶栏：左侧渐变标题 + 右侧 作者/主题/电源（设计稿 .top：padding 10px 4px 14px）──
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(start = if (sideDock) 118.dp else headPad, end = headPad),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // 标题文字与颜色都可在「定制 → 标题栏」里改；留空 / 跟随主题时保持品牌渐变
                val custom = me.diksu.ultra.foxsu.data.settings.CustomizationStore.current
                Text(
                    custom.homeTitle.ifBlank { "FoxSu" },
                    fontSize = if (sideDock) 26.sp else 32.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = (-0.6).sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = me.diksu.ultra.foxsu.ui.theme.TitleInk.style(),
                )
                Spacer(Modifier.weight(1f))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    AgIconBtn(AgTopIcons.User, onClick = onOpenAuthor,
                        modifier = Modifier.onGloballyPositioned { authorBtnBounds = it.boundsInRoot() })
                    // 主题：打开界面风格选择（原深色切换位）
                    AgIconBtn(AgTopIcons.Theme, onClick = { showStyle = true },
                        modifier = Modifier.onGloballyPositioned { themeBtnBounds = it.boundsInRoot() })
                    AgIconBtn(AgTopIcons.Power, onClick = { showReboot = true },
                        modifier = Modifier.onGloballyPositioned { powerBtnBounds = it.boundsInRoot() })
                }
            }
            Spacer(Modifier.height(14.dp))
        }
        // ── 内容滚动区（侧边抽屉的让位统一由 MainActivity 的 pager inset 负责）──
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                // 左右各让出 6dp：大卡片外轮廓的粒子要飘到卡片之外，
                // 贴边放会被滚动容器裁掉半截
                .padding(start = if (sideDock) 106.dp else edgePad, end = edgePad)
                .padding(horizontal = 6.dp),
        ) {
            Spacer(Modifier.height(2.dp))

        // ── 状态卡：外层渐变底 2px + 内层 22dp 白芯 ──
        // 阴影必须在 clip/background 之前，否则 elevation 光晕会盖在卡片上（视觉上卡片变宽）
        Box(
            Modifier
                .fillMaxWidth()
                .tiltCardFx()
                // 卡片「外围」的科幻粒子边：沿 24dp 圆角轮廓流动，尾段向外飘散
                .particleOutline(
                    shape = RoundedCornerShape(24.dp),
                    accent = Ag.Vio,
                    bleed = 12.dp,
                )
                .shadow(
                    12.dp, RoundedCornerShape(24.dp),
                    spotColor = Ag.Vio.copy(alpha = 0.251f),
                    ambientColor = Ag.Vio.copy(alpha = 0.078f),
                )
                .clip(RoundedCornerShape(24.dp))
                .background(Ag.Grad)
                .padding(2.dp),
        ) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(22.dp))
                    .background(Ag.CardSolid),
            ) {
                // .status .in::after —— 右上 130dp 青色径向光斑
                Box(
                    Modifier
                        .align(Alignment.TopEnd)
                        .offset(x = 30.dp, y = (-30).dp)
                        .size(130.dp)
                        .background(
                            Brush.radialGradient(
                                0f to Color(0x2414B8A6),
                                0.7f to Color(0x0014B8A6),
                                1f to Color(0x0014B8A6),
                                center = androidx.compose.ui.geometry.Offset(65f, 65f),
                                radius = 65f,
                            )
                        )
                )
                Column(Modifier.padding(start = 18.dp, end = 18.dp, top = 18.dp, bottom = 16.dp)) {
                    AgBadge(
                        when (hero.phase) {
                            HeroPhase.WORKING -> if (hero.jailbreakMode) "工作中 · 越狱模式" else "工作中"
                            HeroPhase.PENDING_REBOOT -> "已刷入 · 待重启"
                            HeroPhase.FLASHING -> "刷写中"
                            HeroPhase.ATTENTION -> "需注意"
                            HeroPhase.NOT_INSTALLED -> "未安装"
                            HeroPhase.FLASH_FAILED -> "刷写失败"
                            HeroPhase.UNSUPPORTED -> "设备不支持"
                        }
                    )
                    Spacer(Modifier.height(12.dp))
                    // 三个 chip 在 390dp 板上会超过一行，用 FlowRow 让它自然折行，
                    // 右侧给环形状态留出 66dp，避免 chip 被圆环压住。
                    androidx.compose.foundation.layout.FlowRow(
                        modifier = Modifier.padding(end = 66.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        if (hero.modeLabel != null) AgChip(hero.modeLabel)
                        AgChip("版本 ${state.ksuVersion ?: "—"}")
                        AgChip("${hero.versionName}${if (state.showCustomLkmBadge) " · 自定义" else ""}")
                    }
                    Spacer(Modifier.height(14.dp))
                    // 三个真实动作：安装内核（打开镜像安装页）/ 刷新镜像 / 检查更新。
                    // 之前大卡上只有后两个，安装入口缺失，用户点卡片进不了安装镜像界面。
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        AgButton(
                            primary = !hero.installed,
                            text = if (hero.installed) "刷写更新" else "安装内核",
                            onClick = actions.onInstallClick,
                            modifier = Modifier.weight(1f),
                            enabled = hero.canFlash,
                            // 大卡片本身已经有外轮廓粒子边，按钮再来一圈会互相抢，
                            // 所以这三个按钮一律走干净的渐变底（用户：取消外圈特效）
                            particleEdge = false,
                        )
                        AgButton(
                            primary = false,
                            text = stringResource(R.string.fx_home_refresh_image),
                            onClick = actions.onRefreshImage,
                            modifier = Modifier.weight(1f),
                            enabled = hero.canFlash,
                        )
                        AgButton(
                            primary = hero.installed,
                            text = stringResource(R.string.fx_home_check_update),
                            onClick = actions.onCheckUpdate,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
                // .okring：绝对定位 right 14 / top 14，64dp
                Box(
                    Modifier
                        .align(Alignment.TopEnd)
                        .offset(x = (-14).dp, y = 14.dp)
                        .size(64.dp)
                ) {
                    AgStatusRing(ok = hero.phase == HeroPhase.WORKING || hero.phase == HeroPhase.PENDING_REBOOT)
                }
            }
        }
        Spacer(Modifier.height(22.dp))

        // ── 统计卡 ×2（数字来自真实 ViewModel 数据）──
        val suVm: me.diksu.ultra.foxsu.ui.viewmodel.SuperUserViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
        val modVm: me.diksu.ultra.foxsu.ui.viewmodel.ModuleViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
        androidx.compose.runtime.LaunchedEffect(Unit) {
            suVm.loadAppList()
            modVm.fetchModuleList()
        }
        val suState by suVm.uiState.collectAsStateWithLifecycle()
        val modState by modVm.uiState.collectAsStateWithLifecycle()
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            AgStatCard(
                "超级用户",
                "${suState.groupedApps.count { it.anyAllowSu }}",
                "已授权应用", Modifier.weight(1f),
            ) { onOpenTab(1) }
            AgStatCard(
                "模块",
                "${modState.modules.count { it.enabled }}",
                "已启用模块", Modifier.weight(1f),
            ) { onOpenTab(2) }
        }
        Spacer(Modifier.height(14.dp))

        // ── 信息卡 ──
        AgCard(Modifier.fillMaxWidth(), contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 6.dp)) {
            AgRow(stringResource(R.string.fx_home_manager_version), "${state.ksuVersion ?: "—"}（${hero.versionName}）", Ag.Vio, AgHomeIcons.Version)
            AgRow(stringResource(R.string.fx_home_kernel_version), state.systemInfo.kernelVersion, Ag.Vio, AgHomeIcons.Cpu)
            AgRow(stringResource(R.string.fx_home_device_model), state.systemInfo.deviceModel, Ag.Mag, AgHomeIcons.Phone)
            AgRow(stringResource(R.string.fx_home_system_fingerprint), state.systemInfo.fingerprint, Ag.Teal, AgHomeIcons.Fingerprint, divider = false)
        }
        Spacer(Modifier.height(14.dp))

        // ── 安全状态卡 ──
        AgCard(Modifier.fillMaxWidth(), contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 6.dp)) {
            AgRow(stringResource(R.string.fx_home_selinux_status), when (state.systemInfo.selinuxStatus) {
                    "Enforcing" -> "强制执行"
                    "Permissive" -> "宽松模式"
                    "Disabled" -> "已禁用"
                    else -> "未知"
                }, Ag.Teal, AgHomeIcons.ShieldCheck)
            AgRow(
                stringResource(R.string.fx_home_seccomp_status),
                when (state.systemInfo.seccompStatus) {
                    -1 -> "内核未开启"
                    0 -> "未启用"
                    1 -> "严格模式"
                    2 -> "过滤模式"
                    else -> "未知"
                },
                Ag.Teal, AgHomeIcons.Layers, divider = false,
            )
        }
        Spacer(Modifier.height(14.dp))

        // ── 支持开发 / 了解 KernelSU ──
        AgCard(Modifier.fillMaxWidth(), contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 6.dp)) {
            AgRow(
                stringResource(R.string.fx_home_support_title),
                stringResource(R.string.fx_home_support_desc),
                Ag.Amber, AgHomeIcons.Heart, showChev = true,
                onClick = { donateNav.push(me.diksu.ultra.foxsu.ui.navigation3.Route.Donate) },
            )
            AgRow(
                stringResource(R.string.fx_home_learn_kernelsu),
                stringResource(R.string.fx_home_learn_kernelsu_desc),
                Ag.Vio, AgHomeIcons.Book, showChev = true, divider = false,
                onClick = { actions.onOpenUrl("https://kernelsu.org/") },
            )
        }
            Spacer(Modifier.height(24.dp))
        }
    }

    if (showReboot) {
        AgRebootSheet(origin = powerBtnBounds, onDismiss = { showReboot = false })
    }
    if (showStyle) {
        StylePickerDialog(current = LocalUiMode.current, origin = themeBtnBounds, onDismiss = { showStyle = false })
    }
}

/** 右上 64dp 环形勾 .okring（设计稿 SVG 精确复刻：r=27 开口环 132/38 + 折线对勾，5px 圆头描边） */
@Composable
private fun androidx.compose.foundation.layout.BoxScope.AgStatusRing(ok: Boolean) {
    val color = if (ok) Ag.Success else Ag.Amber
    androidx.compose.foundation.Canvas(Modifier.size(64.dp)) {
        val stroke = 5.dp.toPx()
        // circle r=27, dasharray 132 38 → 实弧占 132/170
        drawArc(
            color = color,
            startAngle = -90f,
            sweepAngle = 360f * 132f / 170f,
            useCenter = false,
            style = androidx.compose.ui.graphics.drawscope.Stroke(
                width = stroke,
                cap = androidx.compose.ui.graphics.StrokeCap.Round,
            ),
        )
        // path M22 32 L29 39 L43 25（64 视箱坐标）
        val path = androidx.compose.ui.graphics.Path().apply {
            moveTo(22.dp.toPx(), 32.dp.toPx())
            lineTo(29.dp.toPx(), 39.dp.toPx())
            lineTo(43.dp.toPx(), 25.dp.toPx())
        }
        drawPath(
            path,
            color = color,
            style = androidx.compose.ui.graphics.drawscope.Stroke(
                width = stroke,
                cap = androidx.compose.ui.graphics.StrokeCap.Round,
                join = androidx.compose.ui.graphics.StrokeJoin.Round,
            ),
        )
    }
}

/** 统计卡 .stat（radius 18 / padding 16 / blur 玻璃） */
@Composable
private fun androidx.compose.foundation.layout.RowScope.AgStatCard(
    label: String,
    num: String,
    sub: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    AgCard(modifier, radius = 18.dp, contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp), onClick = onClick) {
        Text(label, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Ag.Tx2)
        Text(num, fontSize = 34.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-1).sp, color = Ag.Vio, modifier = Modifier.padding(top = 4.dp, bottom = 2.dp))
        Text(sub, fontSize = 11.5.sp, color = Ag.Tx3)
    }
}
