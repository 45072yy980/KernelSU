package me.diksu.ultra.foxsu.ui.screen.ag

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.launch
import me.diksu.ultra.foxsu.ui.component.bottombar.useSideDock
import me.diksu.ultra.foxsu.ui.theme.TitleInk
import me.diksu.ultra.foxsu.ui.util.BuiltinModule
import me.diksu.ultra.foxsu.ui.util.BuiltinModules
import me.diksu.ultra.foxsu.ui.util.BuiltinStatus
import me.diksu.ultra.foxsu.ui.util.reboot

/**
 * 内置模块。
 *
 * 这里放的是随 APK 打包的三颗真实模块，不是四个「内核引擎」式的占位开关：
 * Magic Mount-rs（元模块，接管挂载层）、Oh My Keymint（自建 KeyMint）、Zygisk Next（独立 Zygisk 实现）。
 * 管理器只负责把包装进去、启停、以及打开模块自带的界面；按钮按下去设备上真的会有变化。
 */
@Composable
fun AgBuiltinModules(
    onBack: () -> Unit,
    onOpenDetail: (String) -> Unit,
    onOpenMetaModule: () -> Unit,
) {
    var statuses by remember { mutableStateOf<List<BuiltinStatus>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var tick by remember { mutableIntStateOf(0) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    LaunchedEffect(tick) {
        loading = true
        statuses = BuiltinModules.probeAll()
        loading = false
    }

    fun toggle(status: BuiltinStatus, enabled: Boolean) {
        scope.launch {
            val ok = BuiltinModules.setEnabled(status.module, enabled)
            Toast.makeText(
                context,
                if (ok) (if (enabled) "已启用，重启后生效" else "已停用，重启后生效")
                else "操作失败：ksud 拒绝了这次改动",
                Toast.LENGTH_SHORT,
            ).show()
            tick++
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .agPageBg()
            .statusBarsPadding(),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .background(Ag.headSurface())
                .agHeadBlur()
                .padding(start = if (useSideDock()) 106.dp else 18.dp, end = 18.dp)
                .padding(top = 10.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AgIconBtn(AgTopIcons.Back, onClick = onBack, tint = Ag.Tx)
            Spacer(Modifier.width(6.dp))
            Text(
                "内置模块",
                fontSize = 20.sp,
                fontWeight = FontWeight.ExtraBold,
                style = TitleInk.style(),
            )
        }

        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                start = 18.dp,
                end = 18.dp,
                top = 4.dp,
                bottom = 120.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Text(
                    "安装包随管理器打包，不需要联网找 release。开关和卸载都要重启才生效——" +
                        "这些模块在开机早期接管挂载或注入 zygote，没有热加载。",
                    fontSize = 11.5.sp,
                    lineHeight = 17.sp,
                    color = Ag.Tx3,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 6.dp),
                )
            }

            items(statuses, key = { it.module.id }) { status ->
                BuiltinCard(
                    status = status,
                    busy = loading,
                    onOpen = {
                        if (status.module.isMetaModule) onOpenMetaModule() else onOpenDetail(status.module.id)
                    },
                    onToggle = { toggle(status, it) },
                )
            }

            if (statuses.isEmpty() && loading) {
                item {
                    Text(
                        "正在读取模块状态…",
                        fontSize = 12.sp,
                        color = Ag.Tx3,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 12.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun BuiltinCard(
    status: BuiltinStatus,
    busy: Boolean,
    onOpen: () -> Unit,
    onToggle: (Boolean) -> Unit,
) {
    val module = status.module
    val on = status.installed && status.enabled
    val dark = Ag.isDark
    val accent = Ag.Vio
    val accent2 = Ag.Vio2
    val shape = RoundedCornerShape(22.dp)
    val surface = if (on) Ag.CardSolid else (if (dark) Color(0xFF1A1928) else Color(0xFFF1F0F6))

    Column(
        Modifier
            .fillMaxWidth()
            .shadow(
                2.dp,
                shape,
                spotColor = accent.copy(alpha = if (dark) 0.20f else 0.05f),
                ambientColor = accent.copy(alpha = if (dark) 0.12f else 0.03f),
            )
            .clip(shape)
            .background(surface)
            .border(
                1.dp,
                if (on) accent.copy(alpha = if (dark) 0.24f else 0.14f) else Ag.Line,
                shape,
            )
            // 左侧 3dp 主题渐变竖条：直接画在卡面上，不再额外套一层 Box 抢测量约束
            .drawBehind {
                if (on) {
                    drawRect(
                        brush = Brush.verticalGradient(listOf(accent, accent2)),
                        size = androidx.compose.ui.geometry.Size(3.dp.toPx(), size.height),
                    )
                }
            }
            .agClick(onClick = onOpen),
    ) {
        Column(Modifier.padding(start = 17.dp, end = 15.dp, top = 14.dp, bottom = 14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Badge(name = module.name, on = on)
                Spacer(Modifier.width(11.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        module.name,
                        fontSize = 15.5.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = if (on) Ag.Tx else Ag.Tx2,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(3.dp))
                    Text(
                        versionLine(status),
                        fontSize = 11.5.sp,
                        color = Ag.Tx3,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(Modifier.width(8.dp))
                if (status.canToggle) {
                    AgToggle(checked = status.enabled, onCheckedChange = { if (!busy) onToggle(!status.enabled) }, small = true)
                } else {
                    StatusPill(status)
                }
            }

            Spacer(Modifier.height(11.dp))
            Text(
                module.tagline,
                fontSize = 12.5.sp,
                lineHeight = 19.sp,
                color = if (on) Ag.Tx2 else Ag.Tx3,
            )

            Spacer(Modifier.height(12.dp))
            Box(Modifier.fillMaxWidth().height(1.dp).background(Ag.Line))
            Spacer(Modifier.height(10.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                if (status.installed && status.module.isMetaModule) {
                    Text(
                        "高级设置 · 挂载配置与诊断",
                        fontSize = 11.5.sp,
                        color = Ag.Tx3,
                    )
                } else if (!status.installed) {
                    Text(
                        "点开安装（内置包，无需联网）",
                        fontSize = 11.5.sp,
                        color = Ag.Tx3,
                    )
                } else if (status.hasUpdate) {
                    Text(
                        "有新版内置包：${status.builtinVersion}",
                        fontSize = 11.5.sp,
                        color = Ag.Amber,
                    )
                }
                Spacer(Modifier.weight(1f))
                Text(
                    "详情 ›",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (on) accent else Ag.Tx3,
                )
            }
        }
    }
}

/** 状态胶囊：未安装 / 已停用 / 待重启 / 已生效 */
@Composable
private fun StatusPill(status: BuiltinStatus) {
    val (text, color) = when {
        !status.installed -> "未安装" to Ag.Tx3
        !status.enabled -> "已停用" to Ag.Amber
        status.pendingReboot -> "待重启" to Ag.Amber
        else -> "已生效" to Ag.Success
    }
    Text(
        text,
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        color = color,
        modifier = Modifier
            .clip(RoundedCornerShape(99.dp))
            .background(color.copy(alpha = if (Ag.isDark) 0.16f else 0.10f))
            .padding(horizontal = 9.dp, vertical = 4.dp),
    )
}

private fun versionLine(status: BuiltinStatus): String = buildString {
    if (status.installed) {
        append('v')
        append(status.version.ifBlank { "—" })
        if (status.author.isNotBlank()) {
            append("  ·  ")
            append(status.author)
        }
    } else {
        append("内置 v")
        append(status.builtinVersion.ifBlank { "—" })
        append("  ·  未安装")
    }
}

@Composable
private fun Badge(name: String, on: Boolean) {
    // 渐变与纯色统一成 Brush：background(color) / background(brush) 是两个重载，混着写推不出类型
    val fill: Brush = if (on) Ag.Grad else SolidColor(Ag.TintInk)
    Box(
        Modifier
            .size(38.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(fill),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            name.trim().take(1).uppercase(),
            fontSize = 16.sp,
            fontWeight = FontWeight.ExtraBold,
            color = if (on) Color.White else Ag.Tx3,
        )
    }
}

/* ────────────────────────────── 详情页 ────────────────────────────── */

@Composable
fun AgBuiltinDetail(
    moduleId: String,
    onBack: () -> Unit,
    onOpenWebUi: (String) -> Unit,
    onRunAction: (String) -> Unit,
) {
    val module = remember(moduleId) { BuiltinModules.byId(moduleId) }
    if (module == null) {
        onBack()
        return
    }

    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var status by remember { mutableStateOf(BuiltinStatus(module)) }
    var loading by remember { mutableStateOf(true) }
    var busy by remember { mutableStateOf(false) }
    var tick by remember { mutableIntStateOf(0) }
    /** 元模块是否已装：普通模块的 system 文件要靠它挂上去，缺了就是装了也不生效。 */
    var metaInstalled by remember { mutableStateOf<Boolean?>(null) }

    var installing by remember { mutableStateOf(false) }
    var showLog by remember { mutableStateOf(false) }
    val log = remember { mutableStateListOf<String>() }
    var confirmUninstall by remember { mutableStateOf(false) }

    LaunchedEffect(tick) {
        loading = true
        val all = BuiltinModules.probeAll()
        status = all.first { it.module.id == module.id }
        metaInstalled = all.firstOrNull { it.module.isMetaModule }?.installed
        loading = false
    }

    fun install() {
        if (installing) return
        installing = true
        log.clear()
        log.add("准备安装 ${module.name} ${status.builtinVersion}…")
        showLog = true
        scope.launch {
            val ok = BuiltinModules.install(module) { line ->
                if (line.isNotBlank() && log.size < 400) log.add(line.trimEnd())
            }
            log.add(if (ok) "✔ 安装完成 · 重启后生效" else "✘ 安装没有完成，请把上面的输出发出来")
            installing = false
            tick++
        }
    }

    fun toggle(enabled: Boolean) {
        if (busy) return
        busy = true
        scope.launch {
            val ok = BuiltinModules.setEnabled(module, enabled)
            busy = false
            Toast.makeText(
                context,
                if (ok) (if (enabled) "已启用，重启后生效" else "已停用，重启后生效")
                else "操作失败：ksud 拒绝了这次改动",
                Toast.LENGTH_SHORT,
            ).show()
            tick++
        }
    }

    fun uninstall() {
        if (busy) return
        busy = true
        scope.launch {
            val ok = BuiltinModules.uninstall(module)
            busy = false
            Toast.makeText(
                context,
                if (ok) "已标记卸载，重启后完全移除" else "卸载失败：ksud 拒绝了这次操作",
                Toast.LENGTH_SHORT,
            ).show()
            tick++
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .agPageBg()
            .statusBarsPadding(),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .background(Ag.headSurface())
                .agHeadBlur()
                .padding(start = if (useSideDock()) 106.dp else 18.dp, end = 18.dp)
                .padding(top = 10.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AgIconBtn(AgTopIcons.Back, onClick = onBack, tint = Ag.Tx)
            Spacer(Modifier.width(6.dp))
            Text(
                module.name,
                fontSize = 20.sp,
                fontWeight = FontWeight.ExtraBold,
                style = TitleInk.style(),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.weight(1f))
            AgIconBtn(
                icon = AgTopIcons.More,
                tint = if (loading || installing) Ag.Tx3 else Ag.Tx,
                onClick = {
                    if (!loading && !installing) {
                        val text = diagnosticText(status)
                        (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager)
                            .setPrimaryClip(ClipData.newPlainText(module.name, text))
                        Toast.makeText(context, "诊断信息已复制", Toast.LENGTH_SHORT).show()
                    }
                },
            )
        }

        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp),
        ) {
            Spacer(Modifier.height(6.dp))

            // 头部卡：状态 + 版本
            AgCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Badge(name = module.name, on = status.installed && status.enabled)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            module.name,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = Ag.Tx,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Spacer(Modifier.height(3.dp))
                        Text(module.tagline, fontSize = 11.5.sp, color = Ag.Tx3)
                    }
                    Spacer(Modifier.width(8.dp))
                    StatusPill(status)
                }

                Spacer(Modifier.height(14.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    VersionTile("设备版本", if (status.installed) status.version.ifBlank { "—" } else "—", Modifier.weight(1f))
                    VersionTile("内置版本", status.builtinVersion.ifBlank { "—" }, Modifier.weight(1f), status.hasUpdate)
                }
            }

            // 功能开关
            AgGHead("功能开关")
            AgCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("启用", fontSize = 14.5.sp, fontWeight = FontWeight.Bold, color = Ag.Tx)
                        Spacer(Modifier.height(3.dp))
                        Text(
                            when {
                                !status.installed -> "先安装才能启用。"
                                !status.probeOk -> "读不到 /data/adb 下的状态，先确认 root 授权。"
                                status.enabled -> "已启用：下次开机会加载这颗模块。"
                                else -> "已停用：模块仍在设备上，但开机时不会介入。"
                            },
                            fontSize = 11.5.sp,
                            lineHeight = 17.sp,
                            color = Ag.Tx3,
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    AgToggle(
                        checked = status.installed && status.enabled,
                        onCheckedChange = {
                            if (status.canToggle && !busy) toggle(!status.enabled)
                        },
                    )
                }

                if (status.pendingReboot) {
                    Spacer(Modifier.height(12.dp))
                    Note("待重启", "文件已经就位，但还没有接手机器上的运行状态。重启一次即可生效。", Ag.Amber)
                }

                // FoxSu 的 ksud 不自带挂载实现，普通模块的 system 目录要靠元模块挂上去。
                // 缺了它，装再多模块也只是躺在 /data/adb/modules 里。
                if (!module.isMetaModule && metaInstalled == false) {
                    Spacer(Modifier.height(12.dp))
                    Note(
                        "还缺挂载后端",
                        "普通模块的 system 文件要靠元模块挂到分区上。设备上还没装 Magic Mount-rs，" +
                            "这颗模块即使装上也不会生效，先去内置模块里把它装上。",
                        Ag.Amber,
                    )
                }

                Spacer(Modifier.height(14.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (!status.installed || status.hasUpdate) {
                        AgButton(
                            primary = true,
                            text = if (!status.installed) "安装" else "更新到 ${status.builtinVersion}",
                            onClick = { install() },
                            modifier = Modifier.weight(1f),
                            enabled = !installing,
                        )
                    }
                    if (status.installed) {
                        AgButton(
                            primary = false,
                            text = "卸载",
                            onClick = { confirmUninstall = true },
                            modifier = Modifier.weight(1f),
                            enabled = !busy,
                        )
                    }
                }
            }

            // 模块自带界面
            if (status.installed && (status.hasWebUi || status.hasAction)) {
                AgGHead("模块界面")
                AgCard {
                    Text(
                        "这部分界面由模块自己提供，管理器只负责把它显示出来——" +
                            "里面的每一项都直接作用于模块本身，和装/停/卸是同一套真实状态。",
                        fontSize = 11.5.sp,
                        lineHeight = 17.sp,
                        color = Ag.Tx3,
                    )
                    Spacer(Modifier.height(14.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        if (status.hasWebUi) {
                            AgButton(
                                primary = true,
                                text = "打开模块界面",
                                onClick = { onOpenWebUi(module.id) },
                                modifier = Modifier.weight(1f),
                            )
                        }
                        if (status.hasAction) {
                            AgButton(
                                primary = !status.hasWebUi,
                                text = "执行模块动作",
                                onClick = { onRunAction(module.id) },
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }
            }

            AgGHead("关于这颗模块")
            AgCard {
                InfoRow("来源", module.sourceUrl)
                InfoRow("许可证", module.license)
                InfoRow("形态", if (module.isMetaModule) "元模块 · 接管挂载层" else "普通模块")
                Spacer(Modifier.height(10.dp))
                Text(
                    module.description,
                    fontSize = 12.sp,
                    lineHeight = 19.sp,
                    color = Ag.Tx2,
                )
                if (module.isMetaModule) {
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "配置（config.toml）与挂载诊断在「高级设置」里，这里只管装卸与启停。",
                        fontSize = 11.5.sp,
                        lineHeight = 17.sp,
                        color = Ag.Tx3,
                    )
                }
            }

            if (status.installed) {
                Spacer(Modifier.height(16.dp))
                AgButton(
                    primary = false,
                    text = "重启以应用改动",
                    onClick = { reboot() },
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            Spacer(Modifier.height(48.dp))
        }
    }

    if (showLog) {
        InstallLogDialog(
            title = module.name,
            lines = log,
            running = installing,
            onDismiss = { showLog = false },
        )
    }

    if (confirmUninstall) {
        ConfirmDialog(
            title = "卸载 ${module.name}？",
            body = "会在 /data/adb/modules/${module.id} 下写 remove 标记，重启后模块目录被清掉。" +
                "如果这是当前正在用的挂载后端，重启前请确认没有别的元模块接手。",
            onConfirm = {
                confirmUninstall = false
                uninstall()
            },
            onDismiss = { confirmUninstall = false },
        )
    }
}

@Composable
private fun VersionTile(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    highlight: Boolean = false,
) {
    Column(
        modifier
            .clip(RoundedCornerShape(14.dp))
            .background(Ag.TintInk)
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Text(label, fontSize = 10.5.sp, color = Ag.Tx3)
        Spacer(Modifier.height(3.dp))
        Text(
            value,
            fontSize = 13.5.sp,
            fontWeight = FontWeight.ExtraBold,
            color = if (highlight) Ag.Amber else Ag.Tx,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 5.dp)) {
        Text(label, fontSize = 12.sp, color = Ag.Tx3, modifier = Modifier.width(64.dp))
        Text(
            value,
            fontSize = 12.sp,
            color = Ag.Tx2,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun Note(title: String, body: String, color: Color) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(color.copy(alpha = if (Ag.isDark) 0.12f else 0.07f))
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Text(title, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = color)
        Spacer(Modifier.height(3.dp))
        Text(body, fontSize = 11.5.sp, lineHeight = 17.sp, color = Ag.Tx2)
    }
}

@Composable
private fun InstallLogDialog(
    title: String,
    lines: List<String>,
    running: Boolean,
    onDismiss: () -> Unit,
) {
    Dialog(onDismissRequest = { if (!running) onDismiss() }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            Modifier
                .fillMaxWidth(0.92f)
                .clip(RoundedCornerShape(24.dp))
                .background(if (Ag.isDark) Color(0xFF1A1928) else Color(0xFFFBFAFE))
                .border(1.dp, Ag.Line, RoundedCornerShape(24.dp))
                .padding(18.dp),
        ) {
            Text("安装 $title", fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, color = Ag.Tx)
            Spacer(Modifier.height(10.dp))
            SelectionContainer {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .height(260.dp)
                        .verticalScroll(rememberScrollState())
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (Ag.isDark) Color(0x40000000) else Color(0x0A000000))
                        .padding(10.dp),
                ) {
                    lines.forEach { line ->
                        Text(
                            line,
                            fontSize = 10.5.sp,
                            lineHeight = 15.sp,
                            fontFamily = FontFamily.Monospace,
                            color = Ag.Tx2,
                        )
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            AgButton(
                primary = true,
                text = if (running) "安装中…" else "完成",
                onClick = { if (!running) onDismiss() },
                modifier = Modifier.fillMaxWidth(),
                enabled = !running,
            )
        }
    }
}

@Composable
private fun ConfirmDialog(
    title: String,
    body: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            Modifier
                .fillMaxWidth(0.9f)
                .clip(RoundedCornerShape(24.dp))
                .background(if (Ag.isDark) Color(0xFF1A1928) else Color(0xFFFBFAFE))
                .border(1.dp, Ag.Line, RoundedCornerShape(24.dp))
                .padding(20.dp),
        ) {
            Text(title, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, color = Ag.Tx)
            Spacer(Modifier.height(8.dp))
            Text(body, fontSize = 12.5.sp, lineHeight = 19.sp, color = Ag.Tx2)
            Spacer(Modifier.height(18.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                AgButton(primary = false, text = "取消", onClick = onDismiss, modifier = Modifier.weight(1f))
                AgButton(primary = true, text = "卸载", onClick = onConfirm, modifier = Modifier.weight(1f))
            }
        }
    }
}

private fun diagnosticText(status: BuiltinStatus): String = buildString {
    val m = status.module
    appendLine("FoxSu 内置模块 · ${m.name}")
    appendLine("id：${m.id}")
    appendLine("状态：${status.stateLabel}")
    appendLine("设备版本：${status.version.ifEmpty { "—" }} (${status.versionCode})")
    appendLine("内置版本：${status.builtinVersion.ifEmpty { "—" }} (${status.builtinCode})")
    appendLine("webroot：${if (status.hasWebUi) "有" else "无"}")
    appendLine("action.sh：${if (status.hasAction) "有" else "无"}")
    appendLine("来源：${m.sourceUrl}")
    appendLine("许可证：${m.license}")
}
