package me.diksu.ultra.foxsu.ui.screen.ag

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material.icons.rounded.FileOpen
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Tune
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
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import me.diksu.ultra.R
import me.diksu.ultra.foxsu.ui.screen.install.InstallMethod
import me.diksu.ultra.foxsu.ui.screen.install.InstallScreenActions
import me.diksu.ultra.foxsu.ui.screen.install.InstallUiState
import me.diksu.ultra.foxsu.ui.util.LkmSelection
import me.diksu.ultra.foxsu.ui.util.sanitizeBlockModules
import me.diksu.ultra.foxsu.ui.util.splitBlockModulesInput

/**
 * 镜像安装（全皮肤统一，单屏完成）。
 *
 * 版式对齐 KernelSU 原版安装页的「一屏到底」：安装方式单选 → 本地 LKM → 高级选项 → 下一步，
 * 不再有二级页面/分步向导；所有可选项都直接铺在同一页里。
 * 视觉走晨曦玻璃（渐变标题 + 玻璃卡 + 柔和图标块），任何皮肤下都是同一套结构与交互。
 */
@Composable
internal fun AgInstall(
    uiState: InstallUiState,
    actions: InstallScreenActions,
) {
    val boardW = LocalConfiguration.current.screenWidthDp.dp
    val edge = boardW * (18f / 390f)

    val isDownload = uiState.installMethod is InstallMethod.DownloadFile
    val partitions = if (isDownload) uiState.remoteDisplayPartitions else uiState.displayPartitions
    val partIndex = if (isDownload) uiState.remotePartitionSelectionIndex else uiState.partitionSelectionIndex
    val selectedLabel = uiState.installMethod?.label
    var advancedOpen by remember { mutableStateOf(uiState.advancedOptionsShown) }
    val lkmChosen = uiState.lkmSelection != LkmSelection.KmiNone
    val ready = uiState.installMethod != null || lkmChosen

    Column(
        Modifier
            .fillMaxSize()
            .agPageBg()
            .statusBarsPadding()
            .imePadding(),
    ) {
        // ── 顶栏：返回 + 标题 + KMI ──
        Column(
            Modifier
                .fillMaxWidth()
                .background(Ag.headSurface())
                .agHeadBlur()
                .padding(horizontal = edge),
        ) {
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                AgIconBtn(AgTopIcons.Back, onClick = actions.onBack)
                Spacer(Modifier.weight(1f))
                AgChip("KMI ${uiState.currentKmi.ifBlank { "自动" }}")
            }
            Spacer(Modifier.height(14.dp))
            Text(
                text = stringResource(R.string.fx_install),
                fontSize = 30.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = (-0.6).sp,
                style = TextStyle(brush = Ag.TitleGrad),
            )
            Spacer(Modifier.height(12.dp))
        }

        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = edge),
        ) {
            // ── 当前环境：一行只读信息，不占版面 ──
            FlowRow(
                modifier = Modifier.padding(start = 2.dp, end = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                AgChip("KMI ${uiState.currentKmi.ifBlank { "自动识别" }}")
                if (uiState.slotSuffix.isNotBlank()) AgChip("槽位 ${uiState.slotSuffix.trim()}")
                if (partitions.isNotEmpty() && uiState.canSelectPartition) {
                    AgChip("目标 ${partitions.getOrNull(partIndex) ?: "—"}")
                }
            }
            Spacer(Modifier.height(14.dp))

            // ── 安装方式：单选卡（左圆点 + 标题 + 说明），整卡可点 ──
            uiState.installMethodOptions.forEach { method ->
                val selected = selectedLabel == method.label
                val (icon, tint) = when (method) {
                    is InstallMethod.SelectFile -> Icons.Rounded.FileOpen to Ag.Vio
                    is InstallMethod.DownloadFile -> Icons.Rounded.CloudDownload to Ag.Teal
                    is InstallMethod.DirectInstall -> Icons.Rounded.Bolt to Ag.Mag
                    is InstallMethod.DirectInstallToInactiveSlot -> Icons.Rounded.Bolt to Ag.Amber
                }
                AgMethodRow(
                    title = stringResource(method.label),
                    desc = method.summary,
                    icon = icon,
                    tint = tint,
                    selected = selected,
                    onClick = {
                        actions.onSelectMethod(method)
                        when (method) {
                            is InstallMethod.SelectFile -> actions.onSelectBootImage()
                            is InstallMethod.DownloadFile -> actions.onDownloadFile()
                            else -> Unit
                        }
                    },
                )
                Spacer(Modifier.height(9.dp))
            }

            // ── 分区选择：只在「直接刷入 / 在线下载」时可换 ──
            if (partitions.isNotEmpty() && uiState.canSelectPartition) {
                Spacer(Modifier.height(4.dp))
                AgGHead(stringResource(R.string.fx_install_select_partition))
                AgCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(14.dp)) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        partitions.forEachIndexed { i, p ->
                            val on = i == partIndex
                            Box(
                                Modifier
                                    .agClick(onClick = { actions.onSelectPartition(i) })
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(if (on) Ag.Grad else SolidColor(Ag.TintInk))
                                    .border(
                                        1.dp,
                                        if (on) Color.Transparent else Ag.Line,
                                        RoundedCornerShape(12.dp),
                                    )
                                    .padding(horizontal = 15.dp, vertical = 9.dp),
                            ) {
                                Text(
                                    p,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (on) Color.White else Ag.Tx2,
                                )
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(14.dp))

            // ── 本地 LKM：始终可见（原版就在这一步，不藏在选完方式之后）──
            AgCard(
                Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 13.dp),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .agClick(onClick = actions.onUploadLkm),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        Modifier
                            .size(34.dp)
                            .clip(RoundedCornerShape(11.dp))
                            .background(Ag.TintInk),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.Rounded.PlayArrow, null,
                            tint = Ag.Tx,
                            modifier = Modifier.size(19.dp),
                        )
                    }
                    Spacer(Modifier.width(13.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            "使用本地 LKM 文件",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = Ag.Tx,
                        )
                        val uri = (uiState.lkmSelection as? LkmSelection.LkmUri)?.uri
                        if (uri != null) {
                            Spacer(Modifier.height(3.dp))
                            Text(
                                uri.toString().substringAfterLast('/'),
                                fontSize = 12.sp,
                                color = Ag.Tx2,
                                maxLines = 1,
                            )
                        }
                    }
                    Icon(
                        Icons.Rounded.KeyboardArrowDown, null,
                        tint = Ag.Chev,
                        modifier = Modifier
                            .size(20.dp)
                            .rotate(-90f),
                    )
                }
            }
            Spacer(Modifier.height(9.dp))

            // ── 高级选项：原地展开，不跳页 ──
            AgCard(
                Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
            ) {
                val arrow by animateFloatAsState(
                    targetValue = if (advancedOpen) 180f else 0f,
                    animationSpec = tween(200),
                    label = "install-adv",
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .agClick {
                            advancedOpen = !advancedOpen
                            actions.onAdvancedOptionsClicked()
                        }
                        .padding(vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Rounded.Tune, null, tint = Ag.Tx3, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(12.dp))
                    Text(
                        "高级选项",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = Ag.Tx,
                        modifier = Modifier.weight(1f),
                    )
                    Icon(
                        Icons.Rounded.KeyboardArrowDown, null,
                        tint = Ag.Chev,
                        modifier = Modifier
                            .size(22.dp)
                            .rotate(arrow),
                    )
                }
                AnimatedVisibility(
                    visible = advancedOpen,
                    enter = fadeIn(tween(160)) + expandVertically(tween(200)),
                    exit = fadeOut(tween(120)) + shrinkVertically(tween(160)),
                ) {
                    Column {
                        AgToggleRow(
                            stringResource(R.string.fx_allow_shell),
                            stringResource(R.string.fx_allow_shell_summary),
                            Ag.Teal, Icons.Rounded.Tune,
                            trailing = {
                                AgToggle(
                                    checked = uiState.allowShell,
                                    onCheckedChange = { actions.onSelectAllowShell(!uiState.allowShell) },
                                    small = true,
                                )
                            },
                        )
                        AgToggleRow(
                            stringResource(R.string.fx_enable_adb),
                            stringResource(R.string.fx_enable_adb_summary),
                            Ag.Mag, Icons.Rounded.Tune,
                            trailing = {
                                AgToggle(
                                    checked = uiState.enableAdb,
                                    onCheckedChange = { actions.onSelectEnableAdb(!uiState.enableAdb) },
                                    small = true,
                                )
                            },
                            divider = uiState.canForceBackup,
                        )
                        if (uiState.canForceBackup) {
                            AgToggleRow(
                                stringResource(R.string.fx_install_force_backup),
                                stringResource(R.string.fx_install_force_backup_summary),
                                Ag.Amber, Icons.Rounded.Bolt,
                                divider = false,
                                trailing = {
                                    AgToggle(
                                        checked = uiState.forceBackup,
                                        onCheckedChange = { actions.onSelectForceBackup(!uiState.forceBackup) },
                                        small = true,
                                    )
                                },
                            )
                        }

                        // ── 屏蔽内核模块：启动时不加载（主要针对厂商反 root 模块）──
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .height(1.dp)
                                .background(Ag.Line),
                        )
                        Column(Modifier.padding(top = 14.dp, bottom = 14.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Rounded.Block, null,
                                    tint = Ag.Tx3,
                                    modifier = Modifier.size(18.dp),
                                )
                                Spacer(Modifier.width(12.dp))
                                Text(
                                    stringResource(R.string.fx_install_block_modules),
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Ag.Tx,
                                    modifier = Modifier.weight(1f),
                                )
                                if (uiState.blockModules.isNotBlank()) {
                                    Text(
                                        stringResource(R.string.fx_install_block_modules_clear),
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Ag.Tx3,
                                        modifier = Modifier
                                            .agClick(onClick = { actions.onBlockModulesChange("") })
                                            .padding(horizontal = 4.dp, vertical = 2.dp),
                                    )
                                }
                            }
                            Spacer(Modifier.height(5.dp))
                            Text(
                                stringResource(R.string.fx_install_block_modules_summary),
                                fontSize = 11.5.sp,
                                lineHeight = 17.sp,
                                color = Ag.Tx3,
                                modifier = Modifier.padding(start = 30.dp),
                            )
                            Spacer(Modifier.height(10.dp))
                            Box(
                                Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(Ag.TintInk)
                                    .border(1.dp, Ag.Line, RoundedCornerShape(12.dp))
                                    .padding(horizontal = 12.dp, vertical = 11.dp),
                            ) {
                                if (uiState.blockModules.isEmpty()) {
                                    Text(
                                        stringResource(R.string.fx_install_block_modules_hint),
                                        fontSize = 13.sp,
                                        color = Ag.Tx3,
                                    )
                                }
                                BasicTextField(
                                    value = uiState.blockModules,
                                    onValueChange = actions.onBlockModulesChange,
                                    textStyle = TextStyle(
                                        fontSize = 13.sp,
                                        fontFamily = FontFamily.Monospace,
                                        color = Ag.Tx,
                                    ),
                                    cursorBrush = SolidColor(Ag.Vio),
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii),
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
                            // 把「真正会写进 ksu_config 的名字」摊开给用户看：
                            // 输错了不报错、静默丢弃最坑人，这里直接说清吃了哪几个。
                            val typed = splitBlockModulesInput(uiState.blockModules)
                            if (typed.isNotEmpty()) {
                                val parsed = sanitizeBlockModules(uiState.blockModules)
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    when {
                                        parsed.isEmpty() ->
                                            stringResource(R.string.fx_install_block_modules_none)
                                        else -> buildString {
                                            append(stringResource(R.string.fx_install_block_modules_count, parsed.size))
                                            append("：")
                                            append(parsed.joinToString(", "))
                                            if (parsed.size < typed.size) {
                                                append("（")
                                                append(
                                                    stringResource(
                                                        R.string.fx_install_block_modules_ignored,
                                                        typed.size - parsed.size,
                                                    )
                                                )
                                                append("）")
                                            }
                                        }
                                    },
                                    fontSize = 11.sp,
                                    lineHeight = 16.sp,
                                    color = if (parsed.isEmpty()) Ag.Amber else Ag.Tx2,
                                )
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(14.dp))

            // ── 安全提示 ──
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 2.dp, vertical = 2.dp),
                verticalAlignment = Alignment.Top,
            ) {
                Icon(
                    Icons.Rounded.Info,
                    null,
                    tint = Ag.Tx3,
                    modifier = Modifier
                        .padding(top = 1.dp)
                        .size(14.dp),
                )
                Spacer(Modifier.width(7.dp))
                Text(
                    "刷写前会自动备份当前 boot 分区；越狱模式（晚加载）下刷写会导致无法开机，入口会被禁用。",
                    fontSize = 11.5.sp,
                    lineHeight = 17.sp,
                    color = Ag.Tx3,
                )
            }
            Spacer(Modifier.height(20.dp))
        }

        // ── 底部主按钮 ──
        Column(
            Modifier
                .fillMaxWidth()
                // 安装页是路由页，宿主没把导航栏 inset 交给内容，这里自己吃掉，
                // 否则「下一步」会被手势条压住。
                .navigationBarsPadding()
                .background(Ag.headSurface())
                .padding(horizontal = edge),
        ) {
            Box(Modifier.fillMaxWidth().height(1.dp).background(Ag.Line))
            Spacer(Modifier.height(12.dp))
            val shape = RoundedCornerShape(16.dp)
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .agClick(enabled = ready, onClick = { actions.onNext() })
                    .clip(shape)
                    .then(
                        if (ready) Modifier
                            .background(Ag.Grad, shape)
                            .shadow(10.dp, shape, spotColor = Ag.Vio.copy(alpha = 0.302f), ambientColor = Ag.Vio.copy(alpha = 0.102f))
                        else Modifier.background(SolidColor(Ag.ToggleOff), shape)
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    stringResource(R.string.fx_install_next),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = if (ready) Color.White else Ag.Tx3,
                )
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

/**
 * 安装方式行：左侧单选圆点 + 标题/说明 + 右侧图标块。
 * 对齐原版安装页的「选择文件并修补 / 下载文件并修补」两行结构，但走晨曦玻璃的圆角与描边。
 */
@Composable
private fun AgMethodRow(
    title: String,
    desc: String?,
    icon: ImageVector,
    tint: Color,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(20.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(if (selected) Ag.Vio.copy(alpha = 0.059f) else Ag.Card)
            .border(
                1.dp,
                if (selected) Ag.Vio.copy(alpha = 0.349f) else Ag.Line,
                shape,
            )
            .agClick(onClick = onClick)
            .padding(horizontal = 15.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // 单选圆点：未选是空心环，选中是渐变实心 + 白点
        Box(
            Modifier
                .size(20.dp)
                .clip(CircleShape)
                .then(
                    if (selected) Modifier.background(Ag.Grad)
                    else Modifier.border(1.6.dp, Ag.Chev, CircleShape)
                ),
            contentAlignment = Alignment.Center,
        ) {
            if (selected) {
                Box(
                    Modifier
                        .size(7.dp)
                        .clip(CircleShape)
                        .background(Color.White),
                )
            }
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Ag.Tx)
            if (!desc.isNullOrBlank()) {
                Spacer(Modifier.height(3.dp))
                Text(desc, fontSize = 12.5.sp, lineHeight = 18.sp, color = Ag.Tx2, maxLines = 2)
            }
        }
        Spacer(Modifier.width(10.dp))
        Box(
            Modifier
                .size(34.dp)
                .clip(RoundedCornerShape(11.dp))
                .background(if (selected) Ag.Grad else SolidColor(tint.copy(alpha = Ag.ricAlpha(tint)))),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                icon, null,
                tint = if (selected) Color.White else tint,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}
