package me.diksu.ultra.foxsu.ui.screen.ag

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
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
import me.diksu.ultra.foxsu.ui.util.MetaModule
import me.diksu.ultra.foxsu.ui.util.MetaState
import me.diksu.ultra.foxsu.ui.util.MetaStatus
import me.diksu.ultra.foxsu.ui.util.reboot

/**
 * 内核元模块 · Magic Mount-rs。
 *
 * 上一版这里是四个「内置引擎」——它们其实都是内核级能力，FoxSu 的内核并没有提供，
 * 页面只能诚实地写「内核未提供」，等于四个摆设。这一版换成一个**真的能装**的东西：
 * 随 APK 内置一颗元模块包，管理器负责安装、启停、配置，开关按下去设备上真的有变化。
 */
@Composable
fun AgMetaModule(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var status by remember { mutableStateOf(MetaStatus()) }
    var loading by remember { mutableStateOf(true) }
    var tick by remember { mutableIntStateOf(0) }

    var installing by remember { mutableStateOf(false) }
    var showLog by remember { mutableStateOf(false) }
    val log = remember { mutableStateListOf<String>() }

    var showConfig by remember { mutableStateOf(false) }
    var confirmUninstall by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }

    LaunchedEffect(tick) {
        loading = true
        status = MetaModule.probe()
        loading = false
    }

    fun startInstall() {
        if (installing) return
        installing = true
        log.clear()
        log.add("准备安装 ${status.builtinVersion.ifEmpty { "内置包" }}…")
        showLog = true
        scope.launch {
            val ok = MetaModule.install { line ->
                if (line.isNotBlank()) {
                    if (log.size < 400) log.add(line.trimEnd())
                }
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
            val ok = MetaModule.setEnabled(enabled)
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

    fun doUninstall() {
        if (busy) return
        busy = true
        scope.launch {
            val ok = MetaModule.uninstall()
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
                .background(Ag.headSurface()).agHeadBlur()
                .padding(
                    start = if (useSideDock()) 106.dp else 18.dp,
                    end = 18.dp,
                )
                .padding(top = 10.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AgIconBtn(AgTopIcons.Back, onClick = onBack, tint = Ag.Tx)
            Spacer(Modifier.weight(1f))
            AgIconBtn(
                icon = AgTopIcons.More,
                tint = if (loading || installing) Ag.Tx3 else Ag.Tx,
                onClick = { if (!loading && !installing) tick++ },
            )
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                start = 18.dp,
                end = 18.dp,
                top = 6.dp,
                bottom = 48.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { MetaHero(status, loading, installing) }
            item {
                StatusCard(
                    status = status,
                    loading = loading,
                    busy = busy || installing,
                    onInstall = { startInstall() },
                    onUninstall = { confirmUninstall = true },
                )
            }
            item { EnableCard(status, busy || installing, ::toggle) }
            item { ConfigCard(status, onEdit = { showConfig = true }) }
            item { AboutCard() }
            item {
                MetaDiagnostics(status) {
                    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    cm.setPrimaryClip(ClipData.newPlainText("foxsu-metamodule", MetaModule.diagnosticText(status)))
                    Toast.makeText(context, "诊断信息已复制", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    if (showLog) {
        InstallDialog(
            title = if (installing) "正在安装元模块" else "安装结束",
            lines = log,
            running = installing,
            onDismiss = { if (!installing) showLog = false },
            onReboot = { reboot() },
        )
    }

    if (showConfig) {
        ConfigDialog(
            initial = status.configToml.ifEmpty { MetaModule.defaultConfig() },
            onDismiss = { showConfig = false },
            onSave = { text ->
                scope.launch {
                    val ok = MetaModule.writeConfig(text)
                    showConfig = false
                    Toast.makeText(
                        context,
                        if (ok) "配置已写入，重启后生效" else "写入失败：没有权限修改 /data/adb/magic_mount",
                        Toast.LENGTH_SHORT,
                    ).show()
                    tick++
                }
            },
        )
    }

    if (confirmUninstall) {
        AgConfirmDialog(
            title = "卸载元模块？",
            message = "卸载后所有模块会退回 ksud 自带的挂载引擎，改动在下次开机生效。" +
                "如果你是因为装坏了进不去系统，可以长按音量键进入安全模式再卸载。",
            confirmText = "卸载",
            danger = true,
            onDismiss = { confirmUninstall = false },
            onConfirm = {
                confirmUninstall = false
                doUninstall()
            },
        )
    }
}

/** 页头：图标 + 标题 + 一句话 + 状态胶囊。 */
@Composable
private fun MetaHero(status: MetaStatus, loading: Boolean, installing: Boolean) {
    Column(Modifier.padding(top = 8.dp, bottom = 4.dp)) {
        Box(
            Modifier
                .size(64.dp)
                .shadow(14.dp, RoundedCornerShape(20.dp), spotColor = Ag.Vio.copy(alpha = 0.35f))
                .clip(RoundedCornerShape(20.dp))
                .background(Ag.Grad),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                AgEngineIcons.Mount,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(30.dp),
            )
        }
        Spacer(Modifier.height(16.dp))
        Text(
            "内核元模块",
            fontSize = 30.sp,
            fontWeight = FontWeight.ExtraBold,
            letterSpacing = (-0.8).sp,
            style = TitleInk.style(),
        )
        Spacer(Modifier.height(6.dp))
        Text(
            "Magic Mount-rs。一颗随管理器内置的元模块，接管系统分区的挂载层。",
            fontSize = 13.sp,
            lineHeight = 20.sp,
            color = Ag.Tx2,
        )
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            val color = stateColor(status.state)
            Box(
                Modifier
                    .clip(RoundedCornerShape(99.dp))
                    .background(color.copy(alpha = 0.12f))
                    .padding(horizontal = 10.dp, vertical = 4.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(6.dp).background(color, CircleShape))
                    Spacer(Modifier.width(6.dp))
                    Text(
                        when {
                            loading -> "正在读取…"
                            installing -> "正在安装…"
                            else -> MetaModule.stateLabel(status.state)
                        },
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = color,
                    )
                }
            }
            Spacer(Modifier.width(8.dp))
            Text(
                if (status.installedVersion.isNotEmpty()) status.installedVersion else "设备上未安装",
                fontSize = 11.5.sp,
                color = Ag.Tx3,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** 主卡：版本对照 + 状态解释 + 安装 / 卸载。 */
@Composable
private fun StatusCard(
    status: MetaStatus,
    loading: Boolean,
    busy: Boolean,
    onInstall: () -> Unit,
    onUninstall: () -> Unit,
) {
    Column {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            VersionTile("内置版本", status.builtinVersion.ifEmpty { "—" }, Modifier.weight(1f))
            VersionTile(
                "设备上的版本",
                status.installedVersion.ifEmpty { "未安装" },
                Modifier.weight(1f),
                highlight = status.hasUpdate,
            )
        }

        Spacer(Modifier.height(14.dp))
        Text(
            stateExplanation(status),
            fontSize = 12.5.sp,
            lineHeight = 19.sp,
            color = Ag.Tx2,
        )

        if (status.state == MetaState.BLOCKED) {
            Spacer(Modifier.height(10.dp))
            Note(
                "已经被「${status.activeId}」占用",
                "KernelSU 同时只允许一个元模块。要装这个名字的，先卸载现有的那颗并重启，" +
                    "再来这里安装。管理器不会替你顶掉别人的元模块。",
                Ag.Amber,
            )
        }
        if (!loading && !status.probeOk) {
            Spacer(Modifier.height(10.dp))
            Note(
                "读取不到设备状态",
                "探测脚本没有跑完，通常是 root 授权没给到位。安装与卸载会直接失败，先确认 root 可用。",
                Ag.Danger,
            )
        }

        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
            if (!status.installed) {
                AgButton(
                    primary = true,
                    text = if (busy) "请稍候…" else "安装到设备",
                    modifier = Modifier.weight(1f),
                    enabled = !busy && !loading && status.state != MetaState.BLOCKED,
                    onClick = onInstall,
                )
            } else {
                AgButton(
                    primary = status.hasUpdate,
                    text = when {
                        busy -> "请稍候…"
                        status.hasUpdate -> "更新到 ${status.builtinVersion}"
                        else -> "重新安装"
                    },
                    modifier = Modifier.weight(1f),
                    enabled = !busy,
                    onClick = onInstall,
                )
                AgButton(
                    primary = false,
                    text = "卸载",
                    modifier = Modifier.width(96.dp),
                    enabled = !busy,
                    onClick = onUninstall,
                )
            }        }
    }
}

@Composable
private fun VersionTile(label: String, value: String, modifier: Modifier = Modifier, highlight: Boolean = false) {
    Column(
        modifier
            .clip(RoundedCornerShape(16.dp))
            .background(Ag.TintInk)
            .border(1.dp, Ag.Line, RoundedCornerShape(16.dp))
            .padding(horizontal = 13.dp, vertical = 11.dp),
    ) {
        Text(
            label,
            fontSize = 10.5.sp,
            fontWeight = FontWeight.ExtraBold,
            letterSpacing = 0.8.sp,
            color = Ag.Tx3,
        )
        Spacer(Modifier.height(5.dp))
        Text(
            value,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = if (highlight) Ag.Vio else Ag.Tx,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** 启停卡：元模块的开关只写 `disable` 标记，真正生效在下次开机。 */
@Composable
private fun EnableCard(status: MetaStatus, busy: Boolean, onToggle: (Boolean) -> Unit) {
    AgCard {
        AgToggleRow(
            title = "启用元模块",
            desc = if (status.installed) {
                if (status.state == MetaState.DISABLED) "当前已停用，重启后可恢复" else "当前启用中"
            } else {
                "安装后这里才会生效"
            },
            tint = Ag.Success,
            icon = AgSettingsIcons.Shield,
            divider = false,
        ) {
            // AgToggle 没有 enabled 参数：不可用时压暗并吞掉点击，视觉上也不像能拨
            val usable = status.canToggle && !busy
            Box(Modifier.alpha(if (usable) 1f else 0.4f)) {
                AgToggle(
                    checked = status.installed && status.state != MetaState.DISABLED,
                    onCheckedChange = {
                        if (usable) onToggle(status.state == MetaState.DISABLED)
                    },
                )
            }
        }
    }
}

/** 配置卡：只读摘要 + 一个编辑入口，避免把 TOML 拆成一堆半吊子表单。 */
@Composable
private fun ConfigCard(status: MetaStatus, onEdit: () -> Unit) {
    val cfg = status.configToml
    Column {
        AgCard(Modifier.fillMaxWidth(), onClick = onEdit) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AgRic(Ag.Vio, AgSettingsIcons.Modules)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text("挂载配置", fontSize = 14.5.sp, fontWeight = FontWeight.Bold, color = Ag.Tx)
                    Spacer(Modifier.height(2.dp))
                    Text(
                        "config.toml · 改完重启生效",
                        fontSize = 12.5.sp,
                        color = Ag.Tx2,
                    )
                }
                Text("›", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Ag.Chev)
            }
            if (cfg.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Ag.TintInk)
                        .padding(horizontal = 11.dp, vertical = 9.dp),
                ) {
                    cfg.lineSequence().filter { it.isNotBlank() }.take(5).forEach { line ->
                        Text(
                            line.trim(),
                            fontSize = 11.5.sp,
                            lineHeight = 17.sp,
                            fontFamily = FontFamily.Monospace,
                            color = Ag.Tx2,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}

/** 说明卡：这东西做什么、会动到什么、坏了怎么救。 */
@Composable
private fun AboutCard() {
    Column {
        AgGHead("它到底做了什么")
        AgCard {
            Bullet(
                "接管挂载层",
                "开机时由它自己的 Rust 二进制把每个模块的文件逐条叠到 /system、/vendor 上，" +
                    "取代 ksud 内置的那套实现。",
            )
            Bullet(
                "支持额外分区",
                "默认只处理 system/vendor/product/system_ext；写进 extra_mount 的分区" +
                    "（如 my_product、mi_ext）也会被合并挂载。",
            )
            Bullet(
                "规则文件",
                "/data/adb/magic_mount/custom 里可以 ignore 某个来源文件、把任意路径 bind 到目标，",
            )
            Bullet(
                "自带 WebUI",
                "模块包里带了一个网页配置界面，装好后可以在模块详情里直接打开。",
            )
            Spacer(Modifier.height(6.dp))
            Note(
                "先看清楚再用",
                "元模块管的是所有模块的挂载。装错或配置写坏，可能导致模块全部失效——" +
                    "长按音量上/下键进安全模式卸载即可恢复。",
                Ag.Amber,
            )
        }
    }
}

@Composable
private fun MetaDiagnostics(status: MetaStatus, onCopy: () -> Unit) {
    Column {
        AgGHead("诊断")
        AgCard {
            DiagRow("当前挂载后端", if (status.activeId.isNotEmpty()) "metamodule · ${status.activeId}" else "ksud 内置 · overlayfs")
            DiagRow("元模块二进制", if (status.binaryOk) "已就位" else "未找到")
            DiagRow("模块总数", status.moduleCount.toString())
            DiagRow("内置包", "arm64-v8a · 随 APK 内置")
            Spacer(Modifier.height(12.dp))
            AgButton(
                primary = false,
                text = "复制诊断",
                modifier = Modifier.fillMaxWidth(),
                onClick = onCopy,
            )        }
    }
}

/* ───────────────────────── 小组件 ───────────────────────── */

@Composable
private fun Bullet(title: String, body: String) {
    Row(Modifier.padding(vertical = 6.dp)) {
        Box(
            Modifier
                .padding(top = 6.dp)
                .size(5.dp)
                .background(Ag.Vio, CircleShape),
        )
        Spacer(Modifier.width(10.dp))
        Column {
            Text(title, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Ag.Tx)
            Spacer(Modifier.height(2.dp))
            Text(body, fontSize = 12.sp, lineHeight = 18.sp, color = Ag.Tx2)
        }
    }
}

@Composable
private fun Note(title: String, body: String, color: Color) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(color.copy(alpha = 0.09f))
            .border(1.dp, color.copy(alpha = 0.22f), RoundedCornerShape(14.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Box(
            Modifier
                .padding(top = 5.dp)
                .size(6.dp)
                .background(color, CircleShape),
        )
        Spacer(Modifier.width(9.dp))
        Column {
            Text(title, fontSize = 12.5.sp, fontWeight = FontWeight.Bold, color = color)
            Spacer(Modifier.height(3.dp))
            Text(body, fontSize = 11.5.sp, lineHeight = 17.sp, color = Ag.Tx2)
        }
    }
}

@Composable
private fun DiagRow(label: String, value: String) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, fontSize = 12.5.sp, color = Ag.Tx2)
        Spacer(Modifier.weight(1f))
        Text(
            value,
            fontSize = 12.5.sp,
            fontWeight = FontWeight.Bold,
            color = Ag.Tx,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = 12.dp),
        )
    }
}

@Composable
private fun stateColor(state: MetaState): Color = when (state) {
    MetaState.ACTIVE -> Ag.Success
    MetaState.PENDING -> Ag.Amber
    MetaState.DISABLED -> Ag.Tx2
    MetaState.BLOCKED -> Ag.Amber
    MetaState.NOT_INSTALLED -> Ag.Tx3
    MetaState.UNKNOWN -> Ag.Tx3
}

private fun stateExplanation(s: MetaStatus): String = when (s.state) {
    MetaState.ACTIVE ->
        "已经在用它挂载了。模块文件由这颗元模块处理，ksud 内置的挂载不会重复介入。"
    MetaState.PENDING ->
        "文件已经就位，但还没有真正接手机器上的挂载——重启一次即可生效。"
    MetaState.DISABLED ->
        "已安装但处于停用状态，模块暂时由 ksud 内置引擎挂载。打开开关并重启即可切回来。"
    MetaState.NOT_INSTALLED ->
        "设备上还没有安装。安装包已经随管理器内置，点下面的按钮即可写入 /data/adb，无需联网。"
    MetaState.BLOCKED ->
        "设备上已经有另一个元模块在履职，同一时间只能有一个。"
    MetaState.UNKNOWN ->
        "读不到 /data/adb 下的状态，先确认 root 授权正常。"
}

/* ───────────────────────── 对话框 ───────────────────────── */

@Composable
private fun InstallDialog(
    title: String,
    lines: List<String>,
    running: Boolean,
    onDismiss: () -> Unit,
    onReboot: () -> Unit,
) {
    val listState = rememberLazyListState()
    LaunchedEffect(lines.size) {
        if (lines.isNotEmpty()) listState.animateScrollToItem(lines.lastIndex)
    }
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnClickOutside = !running),
    ) {
        Column(
            Modifier
                .padding(horizontal = 22.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(Ag.CardSolid)
                .border(1.dp, Ag.Line, RoundedCornerShape(24.dp))
                .padding(18.dp),
        ) {
            Text(title, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, color = Ag.Tx)
            Spacer(Modifier.height(4.dp))
            Text(
                if (running) "交给 ksud 执行，输出如下" else "完成后需要重启才会接管挂载",
                fontSize = 11.5.sp,
                color = Ag.Tx3,
            )
            if (running) {
                Spacer(Modifier.height(10.dp))
                IndeterminateBar()
            }
            Spacer(Modifier.height(12.dp))
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 120.dp, max = 260.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Ag.TintInk)
                    .padding(horizontal = 10.dp, vertical = 8.dp),
            ) {
                itemsIndexed(lines) { _, line ->
                    Text(
                        line,
                        fontSize = 11.sp,
                        lineHeight = 16.sp,
                        fontFamily = FontFamily.Monospace,
                        color = when {
                            line.startsWith("✔") -> Ag.Success
                            line.startsWith("✘") || line.startsWith("!") -> Ag.Danger
                            else -> Ag.Tx2
                        },
                    )
                }
            }
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                if (!running) {
                    AgButton(
                        primary = false,
                        text = "关闭",
                        modifier = Modifier.weight(1f),
                        onClick = { onDismiss() },
                    )
                    AgButton(
                        primary = true,
                        text = "立即重启",
                        modifier = Modifier.weight(1f),
                        onClick = { onReboot() },
                    )
                }
            }
        }
    }
}

/** 不定长进度条：只在安装那几秒跑，不常驻。 */
@Composable
private fun IndeterminateBar() {
    val transition = rememberInfiniteTransition(label = "mm-bar")
    val x by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1100), RepeatMode.Restart),
        label = "mm-bar-x",
    )
    Box(
        Modifier
            .fillMaxWidth()
            .height(3.dp)
            .clip(RoundedCornerShape(99.dp))
            .background(Ag.TintInk),
    ) {
        Box(
            Modifier
                .fillMaxWidth(0.34f)
                .height(3.dp)
                .offsetFraction(x)
                .clip(RoundedCornerShape(99.dp))
                .background(Ag.Vio),
        )
    }
}

private fun Modifier.offsetFraction(progress: Float): Modifier =
    this.then(
        Modifier.layout { measurable, constraints ->
            val placeable = measurable.measure(constraints)
            layout(placeable.width, placeable.height) {
                val travel = (constraints.maxWidth - placeable.width).coerceAtLeast(0)
                placeable.placeRelative((travel * progress).toInt(), 0)
            }
        },
    )

@Composable
private fun ConfigDialog(initial: String, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var text by remember { mutableStateOf(initial) }
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnClickOutside = false),
    ) {
        Column(
            Modifier
                .padding(horizontal = 22.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(Ag.CardSolid)
                .border(1.dp, Ag.Line, RoundedCornerShape(24.dp))
                .padding(18.dp),
        ) {
            Text("挂载配置", fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, color = Ag.Tx)
            Spacer(Modifier.height(4.dp))
            Text(
                "mountsource / umount / partitions / extra_mount，改完重启生效",
                fontSize = 11.5.sp,
                color = Ag.Tx3,
            )
            Spacer(Modifier.height(12.dp))
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(230.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Ag.TintInk)
                    .border(1.dp, Ag.Line, RoundedCornerShape(12.dp))
                    .padding(10.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                BasicTextField(
                    value = text,
                    onValueChange = { text = it },
                    textStyle = TextStyle(
                        fontSize = 12.sp,
                        lineHeight = 18.sp,
                        fontFamily = FontFamily.Monospace,
                        color = Ag.Tx,
                    ),
                    cursorBrush = SolidColor(Ag.Vio),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                AgButton(
                    primary = false,
                    text = "恢复默认",
                    modifier = Modifier.weight(1f),
                    onClick = { text = MetaModule.defaultConfig() },
                )
                AgButton(
                    primary = false,
                    text = "取消",
                    modifier = Modifier.weight(1f),
                    onClick = { onDismiss() },
                )
                AgButton(
                    primary = true,
                    text = "保存",
                    modifier = Modifier.weight(1f),
                    onClick = { onSave(text) },
                )
            }
        }
    }
}

@Composable
private fun AgConfirmDialog(
    title: String,
    message: String,
    confirmText: String,
    danger: Boolean,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(Ag.CardSolid)
                .border(1.dp, Ag.Line, RoundedCornerShape(24.dp))
                .padding(20.dp),
        ) {
            Text(title, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, color = Ag.Tx)
            Spacer(Modifier.height(8.dp))
            Text(message, fontSize = 12.5.sp, lineHeight = 19.sp, color = Ag.Tx2)
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                AgButton(
                    primary = false,
                    text = "取消",
                    modifier = Modifier.weight(1f),
                    onClick = { onDismiss() },
                )
                Box(Modifier.weight(1f)) {
                    if (danger) {
                        DangerButton(confirmText, onConfirm)
                    } else {
                        AgButton(
                            primary = true,
                            text = confirmText,
                            modifier = Modifier.fillMaxWidth(),
                            onClick = onConfirm,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DangerButton(text: String, onClick: () -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .height(38.dp)
            .agClick(onClick = onClick)
            .clip(RoundedCornerShape(12.dp))
            .background(Ag.Danger.copy(alpha = 0.10f))
            .border(1.dp, Ag.Danger.copy(alpha = 0.32f), RoundedCornerShape(12.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Ag.Danger)
    }
}
