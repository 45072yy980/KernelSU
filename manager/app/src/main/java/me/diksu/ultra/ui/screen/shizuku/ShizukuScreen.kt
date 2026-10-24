package me.diksu.ultra.ui.screen.shizuku

import android.content.Context
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Article
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.diksu.ultra.shizuku.ShizukuAutostart
import me.diksu.ultra.shizuku.ShizukuServiceManager
import me.diksu.ultra.ui.LocalUiMode
import me.diksu.ultra.ui.isMiuixFamily
import me.diksu.ultra.ui.navigation3.LocalNavigator
import me.diksu.ultra.ui.navigation3.Route
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import top.yukonga.miuix.kmp.basic.Card as MiuixCard
import top.yukonga.miuix.kmp.basic.Icon as MiuixIcon
import top.yukonga.miuix.kmp.basic.IconButton as MiuixIconButton
import top.yukonga.miuix.kmp.basic.Scaffold as MiuixScaffold
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Switch as MiuixSwitch
import top.yukonga.miuix.kmp.basic.Text as MiuixText
import top.yukonga.miuix.kmp.basic.TextButton as MiuixTextButton
import top.yukonga.miuix.kmp.basic.TextField as MiuixTextField
import top.yukonga.miuix.kmp.basic.TopAppBar as MiuixTopAppBar
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme

/**
 * 内置 Shizuku 服务管理面板（4.0 重写）。
 *
 * 与 3.x 的差别：
 * - 状态卡改成"一眼可读"的三段式：运行结论 + 运行身份/进程/授权数 + 主操作；
 * - 开机自启独立成卡片，直接展示**上次自启的结果**（时间、来源、失败原因），
 *   并提供"立即自启一次"，走的正是开机那条路径 —— 自启到底行不行当场可验；
 * - 应用列表加了搜索与筛选，授权数实时统计；未授权时不再显示 Root 分权开关，
 *   避免"两个开关都亮着但都不生效"的误解；
 * - Material 3 皮肤是一套独立实现，不再是"Miuix 的降级说明"。
 */
@Composable
fun ShizukuScreen() {
    val navigator = LocalNavigator.current
    val uiMode = LocalUiMode.current
    val onBack = { navigator.pop() }
    val onOpenLog = { navigator.push(Route.ShizukuLog) }
    if (uiMode.isMiuixFamily) {
        ShizukuScreenMiuix(onBack = onBack, onOpenLog = onOpenLog)
    } else {
        ShizukuScreenMaterial(onBack = onBack, onOpenLog = onOpenLog)
    }
}

// ── 共享状态 ────────────────────────────────────────────────────────────────

private val AccentRunning = Color(0xFF2FA84F)
private val AccentStopped = Color(0xFF8A8A8E)
private val AccentRoot = Color(0xFF1A73E8)
private val AccentDanger = Color(0xFFE53935)

private enum class AppFilter { All, Allowed, Denied }

private data class ShizukuAppEntry(
    val packageName: String,
    val uid: Int,
    val label: String,
    val icon: ImageBitmap?,
    val allowed: Boolean,
    val shellOnly: Boolean,
)

private data class ShizukuUiState(
    val running: Boolean = false,
    val serverIsRoot: Boolean = false,
    val serverUid: Int? = null,
    val serverPid: Int? = null,
    val bootEnabled: Boolean = false,
    val apps: List<ShizukuAppEntry> = emptyList(),
    val loading: Boolean = false,
    val starting: Boolean = false,
    val stopping: Boolean = false,
    val autostarting: Boolean = false,
    val startError: String? = null,
    val lastAttempt: ShizukuAutostart.LastAttempt? = null,
    val query: String = "",
    val filter: AppFilter = AppFilter.All,
) {
    val allowedCount: Int get() = apps.count { it.allowed }

    val visibleApps: List<ShizukuAppEntry>
        get() {
            val q = query.trim().lowercase()
            return apps.filter { app ->
                val matchFilter = when (filter) {
                    AppFilter.All -> true
                    AppFilter.Allowed -> app.allowed
                    AppFilter.Denied -> !app.allowed
                }
                val matchQuery = q.isEmpty() ||
                    app.label.lowercase().contains(q) ||
                    app.packageName.lowercase().contains(q)
                matchFilter && matchQuery
            }
        }
}

private class ShizukuActions(
    val onStart: () -> Unit,
    val onStop: () -> Unit,
    val onRestart: () -> Unit,
    val onBootChange: (Boolean) -> Unit,
    val onAutostartNow: () -> Unit,
    val onRefresh: () -> Unit,
    val onToggleApp: (ShizukuAppEntry) -> Unit,
    val onToggleRoot: (ShizukuAppEntry) -> Unit,
    val onQueryChange: (String) -> Unit,
    val onFilterChange: (AppFilter) -> Unit,
)

@Composable
private fun rememberShizukuState(): Pair<ShizukuUiState, ShizukuActions> {
    var state by remember { mutableStateOf(ShizukuUiState()) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    suspend fun refresh() {
        state = state.copy(loading = true)
        val running = withContext(Dispatchers.IO) { ShizukuServiceManager.isServerRunning() }
        val serverIsRoot = if (running) {
            withContext(Dispatchers.IO) { ShizukuServiceManager.isRootServer() }
        } else {
            false
        }
        val serverUid = if (running) withContext(Dispatchers.IO) { ShizukuServiceManager.serverUid() } else null
        val serverPid = if (running) withContext(Dispatchers.IO) { ShizukuServiceManager.serverPid() } else null
        val apps = if (running) {
            withContext(Dispatchers.IO) { loadApps(context) }
        } else {
            emptyList()
        }
        state = state.copy(
            running = running,
            serverIsRoot = serverIsRoot,
            serverUid = serverUid,
            serverPid = serverPid,
            apps = apps,
            loading = false,
        )
    }

    LaunchedEffect(Unit) {
        state = state.copy(
            bootEnabled = ShizukuServiceManager.isEnabled(),
            lastAttempt = ShizukuAutostart.lastAttempt(),
        )
        refresh()
    }

    val actions = ShizukuActions(
        onStart = {
            scope.launch {
                state = state.copy(starting = true, startError = null)
                val ok = withContext(Dispatchers.IO) { ShizukuServiceManager.start(context) }
                state = state.copy(starting = false)
                if (!ok) state = state.copy(startError = "启动失败，请到日志页查看服务端原因")
                refresh()
            }
        },
        onStop = {
            scope.launch {
                state = state.copy(stopping = true)
                withContext(Dispatchers.IO) { ShizukuServiceManager.stop() }
                state = state.copy(stopping = false)
                refresh()
            }
        },
        onRestart = {
            scope.launch {
                state = state.copy(stopping = true, startError = null)
                withContext(Dispatchers.IO) { ShizukuServiceManager.stop() }
                state = state.copy(stopping = false, starting = true)
                val ok = withContext(Dispatchers.IO) { ShizukuServiceManager.start(context) }
                state = state.copy(starting = false)
                if (!ok) state = state.copy(startError = "重启失败，请到日志页查看服务端原因")
                refresh()
            }
        },
        onBootChange = { enabled ->
            ShizukuServiceManager.setEnabled(enabled)
            state = state.copy(bootEnabled = enabled)
            // 刚打开自启时立刻跑一次，用户马上就能知道这条链路通不通。
            if (enabled && !state.running) {
                scope.launch {
                    state = state.copy(autostarting = true)
                    withContext(Dispatchers.IO) { ShizukuAutostart.run(context, "enable-toggle", 1) }
                    state = state.copy(
                        autostarting = false,
                        lastAttempt = ShizukuAutostart.lastAttempt(),
                    )
                    refresh()
                }
            }
        },
        onAutostartNow = {
            scope.launch {
                state = state.copy(autostarting = true, startError = null)
                withContext(Dispatchers.IO) { ShizukuAutostart.run(context, "manual", 1) }
                state = state.copy(
                    autostarting = false,
                    lastAttempt = ShizukuAutostart.lastAttempt(),
                )
                refresh()
            }
        },
        onRefresh = { scope.launch { refresh() } },
        onToggleApp = { app ->
            scope.launch {
                runCatching { ShizukuServiceManager.setAllowed(app.uid, !app.allowed) }
                refresh()
            }
        },
        onToggleRoot = { app ->
            scope.launch {
                runCatching { ShizukuServiceManager.setShellOnly(app.uid, !app.shellOnly) }
                refresh()
            }
        },
        onQueryChange = { state = state.copy(query = it) },
        onFilterChange = { state = state.copy(filter = it) },
    )

    return state to actions
}

/** 在 IO 线程把包信息翻成可直接渲染的条目（图标、名称只查一次）。 */
private fun loadApps(context: Context): List<ShizukuAppEntry> {
    val pm = context.packageManager
    val raw = ShizukuServiceManager.getApplications().orEmpty()
    return raw.mapNotNull { pkg ->
        val ai = pkg.applicationInfo ?: return@mapNotNull null
        val uid = ai.uid
        val label = runCatching { ai.loadLabel(pm).toString() }.getOrDefault(pkg.packageName)
        val icon = runCatching { ai.loadIcon(pm).toBitmap().asImageBitmap() }.getOrNull()
        ShizukuAppEntry(
            packageName = pkg.packageName,
            uid = uid,
            label = label,
            icon = icon,
            allowed = runCatching { ShizukuServiceManager.isAllowed(uid) }.getOrDefault(false),
            shellOnly = runCatching { ShizukuServiceManager.getShellOnly(uid) }.getOrDefault(false),
        )
    }.distinctBy { it.uid }.sortedBy { it.label.lowercase(Locale.getDefault()) }
}

private fun formatAttemptTime(millis: Long): String =
    runCatching {
        SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(Date(millis))
    }.getOrDefault("")

private fun attemptSourceLabel(source: String): String = when (source) {
    "boot" -> "开机"
    "package-replaced" -> "应用更新后"
    "manual" -> "手动"
    "enable-toggle" -> "打开开关时"
    else -> source.ifBlank { "未知" }
}

private fun autostartSummary(attempt: ShizukuAutostart.LastAttempt?, autostarting: Boolean): String {
    if (autostarting) return "正在尝试自启…"
    if (attempt == null) return "本次安装后尚未尝试过自启"
    val whenText = "${formatAttemptTime(attempt.atMillis)} · ${attemptSourceLabel(attempt.source)}"
    return if (attempt.success) {
        "上次自启成功 · $whenText"
    } else {
        "上次自启失败 · $whenText\n${attempt.detail}"
    }
}

// ── Miuix 皮肤 ──────────────────────────────────────────────────────────────

@Composable
private fun ShizukuScreenMiuix(onBack: () -> Unit, onOpenLog: () -> Unit) {
    val (state, actions) = rememberShizukuState()

    MiuixScaffold(
        topBar = {
            MiuixTopAppBar(
                title = "Shizuku",
                navigationIcon = {
                    MiuixIconButton(onClick = onBack) {
                        MiuixIcon(
                            imageVector = MiuixIcons.Back,
                            contentDescription = null,
                            tint = colorScheme.onSurface,
                        )
                    }
                },
                actions = {
                    MiuixIconButton(onClick = onOpenLog) {
                        MiuixIcon(
                            imageVector = Icons.Filled.Article,
                            contentDescription = "日志",
                            tint = colorScheme.onSurface,
                        )
                    }
                    MiuixIconButton(onClick = actions.onRefresh) {
                        MiuixIcon(
                            imageVector = Icons.Filled.Refresh,
                            contentDescription = "刷新",
                            tint = colorScheme.onSurface,
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.padding(horizontal = 12.dp),
            contentPadding = innerPadding,
        ) {
            item { Spacer(Modifier.height(12.dp)) }
            item { MiuixStatusCard(state, actions) }
            item {
                SmallTitle(text = "开机自启", modifier = Modifier.padding(top = 12.dp))
                MiuixCard(modifier = Modifier.padding(top = 12.dp)) {
                    MiuixAutostartBlock(state, actions)
                }
            }
            item {
                SmallTitle(
                    text = "应用授权（${state.allowedCount}/${state.apps.size}）",
                    modifier = Modifier.padding(top = 12.dp),
                )
                MiuixCard(modifier = Modifier.padding(top = 12.dp)) {
                    MiuixAppListHeader(state, actions)
                    if (state.running && state.visibleApps.isNotEmpty()) {
                        state.visibleApps.forEach { app ->
                            MiuixAppRow(
                                app = app,
                                serverIsRoot = state.serverIsRoot,
                                onToggleAllowed = { actions.onToggleApp(app) },
                                onToggleRoot = { actions.onToggleRoot(app) },
                            )
                        }
                    }
                }
                MiuixAppListEmpty(state)
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

@Composable
private fun MiuixStatusCard(state: ShizukuUiState, actions: ShizukuActions) {
    val accent = if (state.running) AccentRunning else AccentStopped
    MiuixCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(accent.copy(alpha = 0.14f)),
                    contentAlignment = Alignment.Center,
                ) {
                    MiuixIcon(
                        imageVector = Icons.Filled.WaterDrop,
                        contentDescription = null,
                        tint = accent,
                        modifier = Modifier.size(24.dp),
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    MiuixText(
                        text = if (state.running) "运行中" else "已停止",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = colorScheme.onSurface,
                    )
                    Spacer(Modifier.height(2.dp))
                    MiuixText(
                        text = if (state.running) {
                            if (state.serverIsRoot) "Root 模式 · 命令以 root 身份执行" else "Shell 模式 · 命令以 shell 身份执行"
                        } else {
                            "服务未启动，应用无法使用 Shizuku 能力"
                        },
                        fontSize = 12.sp,
                        color = colorScheme.onSurfaceVariantSummary,
                    )
                }
                if (state.running) {
                    MiuixPill(
                        text = if (state.serverIsRoot) "root" else "shell",
                        color = if (state.serverIsRoot) AccentRoot else AccentStopped,
                    )
                }
            }

            if (state.running) {
                Spacer(Modifier.height(14.dp))
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    MiuixMetric(
                        label = "运行身份",
                        value = state.serverUid?.toString() ?: "—",
                        modifier = Modifier.weight(1f),
                    )
                    MiuixMetric(
                        label = "进程 PID",
                        value = state.serverPid?.toString() ?: "—",
                        modifier = Modifier.weight(1f),
                    )
                    MiuixMetric(
                        label = "已授权",
                        value = "${state.allowedCount} / ${state.apps.size}",
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            state.startError?.let {
                Spacer(Modifier.height(10.dp))
                MiuixText(text = it, fontSize = 12.sp, color = AccentDanger)
            }

            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (state.running) {
                    MiuixTextButton(
                        text = if (state.stopping) "停止中…" else "停止服务",
                        onClick = { if (!state.stopping) actions.onStop() },
                        modifier = Modifier.weight(1f),
                    )
                    MiuixTextButton(
                        text = if (state.starting) "重启中…" else "重启服务",
                        onClick = { if (!state.starting && !state.stopping) actions.onRestart() },
                        modifier = Modifier.weight(1f),
                    )
                } else {
                    MiuixTextButton(
                        text = if (state.starting) "启动中…" else "启动服务",
                        onClick = { if (!state.starting) actions.onStart() },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun MiuixAutostartBlock(state: ShizukuUiState, actions: ShizukuActions) {
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                MiuixText(
                    text = "开机自启",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    color = colorScheme.onSurface,
                )
                Spacer(Modifier.height(2.dp))
                MiuixText(
                    text = "设备启动后自动拉起内置服务",
                    fontSize = 12.sp,
                    color = colorScheme.onSurfaceVariantSummary,
                )
            }
            MiuixSwitch(checked = state.bootEnabled, onCheckedChange = actions.onBootChange)
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .height(1.dp)
                .background(colorScheme.onSurface.copy(alpha = 0.06f)),
        )
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            val attempt = state.lastAttempt
            val failed = attempt != null && !attempt.success
            MiuixText(
                text = autostartSummary(attempt, state.autostarting),
                fontSize = 12.sp,
                color = if (failed) AccentDanger else colorScheme.onSurfaceVariantSummary,
            )
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MiuixTextButton(
                    text = if (state.autostarting) "执行中…" else "立即自启一次",
                    onClick = { if (!state.autostarting) actions.onAutostartNow() },
                )
            }
        }
    }
}

@Composable
private fun MiuixAppListHeader(state: ShizukuUiState, actions: ShizukuActions) {
    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
        MiuixTextField(
            value = state.query,
            onValueChange = actions.onQueryChange,
            label = "搜索应用名或包名",
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MiuixFilterPill("全部", state.filter == AppFilter.All) { actions.onFilterChange(AppFilter.All) }
            MiuixFilterPill("已授权", state.filter == AppFilter.Allowed) { actions.onFilterChange(AppFilter.Allowed) }
            MiuixFilterPill("未授权", state.filter == AppFilter.Denied) { actions.onFilterChange(AppFilter.Denied) }
        }
    }
}

@Composable
private fun MiuixFilterPill(text: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(
                if (selected) colorScheme.primary.copy(alpha = 0.14f)
                else colorScheme.onSurface.copy(alpha = 0.05f)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        MiuixText(
            text = text,
            fontSize = 12.sp,
            fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
            color = if (selected) colorScheme.primary else colorScheme.onSurfaceVariantSummary,
        )
    }
}

@Composable
private fun MiuixAppRow(
    app: ShizukuAppEntry,
    serverIsRoot: Boolean,
    onToggleAllowed: () -> Unit,
    onToggleRoot: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            AppIcon(app, size = 38)
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    MiuixText(
                        text = app.label,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium,
                        color = colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    Spacer(Modifier.width(6.dp))
                    MiuixPill(
                        text = if (app.allowed) "已授权" else "未授权",
                        color = if (app.allowed) AccentRunning else AccentStopped,
                    )
                    if (serverIsRoot && app.allowed) {
                        Spacer(Modifier.width(4.dp))
                        MiuixPill(
                            text = if (app.shellOnly) "shell" else "root",
                            color = if (app.shellOnly) AccentStopped else AccentRoot,
                        )
                    }
                }
                Spacer(Modifier.height(2.dp))
                MiuixText(
                    text = "${app.packageName} · UID ${app.uid}",
                    fontSize = 11.sp,
                    color = colorScheme.onSurfaceVariantSummary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.width(8.dp))
            MiuixSwitch(checked = app.allowed, onCheckedChange = { onToggleAllowed() })
        }
        // 未授权时藏起分权开关：它只在"允许使用 + server 以 root 运行"时才有意义。
        if (serverIsRoot && app.allowed) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp, start = 50.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    MiuixText(
                        text = "Root 访问",
                        fontSize = 13.sp,
                        color = colorScheme.onSurface,
                    )
                    MiuixText(
                        text = if (app.shellOnly) "降级为 shell 权限执行" else "以 root 权限执行",
                        fontSize = 11.sp,
                        color = colorScheme.onSurfaceVariantSummary,
                    )
                }
                MiuixSwitch(checked = !app.shellOnly, onCheckedChange = { onToggleRoot() })
            }
        }
    }
}

@Composable
private fun MiuixAppListEmpty(state: ShizukuUiState) {
    if (!state.running) {
        MiuixCard(modifier = Modifier.padding(top = 12.dp)) {
            Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                MiuixText(
                    text = "服务未启动，无法读取应用列表",
                    fontSize = 13.sp,
                    color = colorScheme.onSurfaceVariantSummary,
                )
            }
        }
        return
    }
    if (state.visibleApps.isNotEmpty()) return
    MiuixCard(modifier = Modifier.padding(top = 12.dp)) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            MiuixText(
                text = if (state.apps.isEmpty()) "暂无声明 Shizuku 权限的应用" else "没有匹配的应用",
                fontSize = 13.sp,
                color = colorScheme.onSurfaceVariantSummary,
            )
            if (state.apps.isEmpty()) {
                Spacer(Modifier.height(4.dp))
                MiuixText(
                    text = "应用首次请求 Shizuku 时才会出现在这里",
                    fontSize = 11.sp,
                    color = colorScheme.onSurfaceVariantSummary,
                )
            }
        }
    }
}

@Composable
private fun MiuixMetric(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        MiuixText(
            text = value,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            color = colorScheme.onSurface,
        )
        Spacer(Modifier.height(2.dp))
        MiuixText(text = label, fontSize = 11.sp, color = colorScheme.onSurfaceVariantSummary)
    }
}

@Composable
private fun MiuixPill(text: String, color: Color) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(color.copy(alpha = 0.14f))
            .padding(horizontal = 8.dp, vertical = 2.dp),
    ) {
        MiuixText(
            text = text,
            fontSize = 10.sp,
            fontWeight = FontWeight.Medium,
            color = color,
        )
    }
}

@Composable
private fun AppIcon(app: ShizukuAppEntry, size: Int) {
    val icon = app.icon
    if (icon != null) {
        Image(
            bitmap = icon,
            contentDescription = null,
            modifier = Modifier.size(size.dp).clip(RoundedCornerShape(10.dp)),
        )
    } else {
        Box(
            modifier = Modifier
                .size(size.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(AccentStopped.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Filled.Check,
                contentDescription = null,
                tint = AccentStopped,
                modifier = Modifier.size((size / 2).dp),
            )
        }
    }
}

// ── Material 3 皮肤 ────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ShizukuScreenMaterial(onBack: () -> Unit, onOpenLog: () -> Unit) {
    val (state, actions) = rememberShizukuState()
    val scheme = MaterialTheme.colorScheme

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Shizuku") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(imageVector = MiuixIcons.Back, contentDescription = null)
                    }
                },
                actions = {
                    IconButton(onClick = onOpenLog) {
                        Icon(Icons.Filled.Article, contentDescription = "日志")
                    }
                    IconButton(onClick = actions.onRefresh) {
                        Icon(Icons.Filled.Refresh, contentDescription = "刷新")
                    }
                },
            )
        },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.padding(innerPadding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { MaterialStatusCard(state, actions) }
            item { MaterialAutostartCard(state, actions) }
            item {
                Text(
                    text = "应用授权（${state.allowedCount}/${state.apps.size}）",
                    style = MaterialTheme.typography.titleSmall,
                    color = scheme.primary,
                    modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
                )
                MaterialAppListCard(state, actions)
            }
        }
    }
}

@Composable
private fun MaterialStatusCard(state: ShizukuUiState, actions: ShizukuActions) {
    val scheme = MaterialTheme.colorScheme
    val accent = if (state.running) AccentRunning else scheme.onSurfaceVariant
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = scheme.surfaceContainer),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(accent.copy(alpha = 0.14f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Filled.WaterDrop,
                        contentDescription = null,
                        tint = accent,
                        modifier = Modifier.size(24.dp),
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (state.running) "运行中" else "已停止",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = if (state.running) {
                            if (state.serverIsRoot) "Root 模式 · 命令以 root 身份执行" else "Shell 模式 · 命令以 shell 身份执行"
                        } else {
                            "服务未启动，应用无法使用 Shizuku 能力"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = scheme.onSurfaceVariant,
                    )
                }
                if (state.running) {
                    MaterialPill(
                        text = if (state.serverIsRoot) "root" else "shell",
                        color = if (state.serverIsRoot) AccentRoot else scheme.onSurfaceVariant,
                    )
                }
            }

            if (state.running) {
                Spacer(Modifier.height(14.dp))
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    MaterialMetric("运行身份", state.serverUid?.toString() ?: "—", Modifier.weight(1f))
                    MaterialMetric("进程 PID", state.serverPid?.toString() ?: "—", Modifier.weight(1f))
                    MaterialMetric("已授权", "${state.allowedCount} / ${state.apps.size}", Modifier.weight(1f))
                }
            }

            state.startError?.let {
                Spacer(Modifier.height(10.dp))
                Text(text = it, style = MaterialTheme.typography.bodySmall, color = scheme.error)
            }

            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (state.running) {
                    TextButton(
                        onClick = { if (!state.stopping) actions.onStop() },
                        modifier = Modifier.weight(1f),
                    ) { Text(if (state.stopping) "停止中…" else "停止服务") }
                    TextButton(
                        onClick = { if (!state.starting && !state.stopping) actions.onRestart() },
                        modifier = Modifier.weight(1f),
                    ) { Text(if (state.starting) "重启中…" else "重启服务") }
                } else {
                    TextButton(
                        onClick = { if (!state.starting) actions.onStart() },
                        modifier = Modifier.weight(1f),
                    ) { Text(if (state.starting) "启动中…" else "启动服务") }
                }
            }
        }
    }
}

@Composable
private fun MaterialAutostartCard(state: ShizukuUiState, actions: ShizukuActions) {
    val scheme = MaterialTheme.colorScheme
    val attempt = state.lastAttempt
    val failed = attempt != null && !attempt.success
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = scheme.surfaceContainer),
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("开机自启", style = MaterialTheme.typography.titleSmall)
                    Text(
                        text = "设备启动后自动拉起内置服务",
                        style = MaterialTheme.typography.bodySmall,
                        color = scheme.onSurfaceVariant,
                    )
                }
                Switch(checked = state.bootEnabled, onCheckedChange = actions.onBootChange)
            }
            HorizontalDivider(color = scheme.outlineVariant.copy(alpha = 0.5f))
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                Text(
                    text = autostartSummary(attempt, state.autostarting),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (failed) scheme.error else scheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(4.dp))
                TextButton(
                    onClick = { if (!state.autostarting) actions.onAutostartNow() },
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                ) { Text(if (state.autostarting) "执行中…" else "立即自启一次") }
            }
        }
    }
}

@Composable
private fun MaterialAppListCard(state: ShizukuUiState, actions: ShizukuActions) {
    val scheme = MaterialTheme.colorScheme
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = scheme.surfaceContainer),
    ) {
        Column {
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                OutlinedTextField(
                    value = state.query,
                    onValueChange = actions.onQueryChange,
                    singleLine = true,
                    label = { Text("搜索应用名或包名") },
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    MaterialFilterChip("全部", state.filter == AppFilter.All) { actions.onFilterChange(AppFilter.All) }
                    MaterialFilterChip("已授权", state.filter == AppFilter.Allowed) { actions.onFilterChange(AppFilter.Allowed) }
                    MaterialFilterChip("未授权", state.filter == AppFilter.Denied) { actions.onFilterChange(AppFilter.Denied) }
                }
            }
            HorizontalDivider(color = scheme.outlineVariant.copy(alpha = 0.5f))
            when {
                !state.running -> Text(
                    text = "服务未启动，无法读取应用列表",
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp),
                )
                state.visibleApps.isEmpty() -> Text(
                    text = if (state.apps.isEmpty()) "暂无声明 Shizuku 权限的应用" else "没有匹配的应用",
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp),
                )
                else -> Column {
                    state.visibleApps.forEachIndexed { index, app ->
                        if (index > 0) {
                            HorizontalDivider(color = scheme.outlineVariant.copy(alpha = 0.5f))
                        }
                        MaterialAppRow(
                            app = app,
                            serverIsRoot = state.serverIsRoot,
                            onToggleAllowed = { actions.onToggleApp(app) },
                            onToggleRoot = { actions.onToggleRoot(app) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MaterialAppRow(
    app: ShizukuAppEntry,
    serverIsRoot: Boolean,
    onToggleAllowed: () -> Unit,
    onToggleRoot: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            AppIcon(app, size = 38)
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = app.label,
                        style = MaterialTheme.typography.bodyLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    Spacer(Modifier.width(6.dp))
                    MaterialPill(
                        text = if (app.allowed) "已授权" else "未授权",
                        color = if (app.allowed) AccentRunning else scheme.onSurfaceVariant,
                    )
                    if (serverIsRoot && app.allowed) {
                        Spacer(Modifier.width(4.dp))
                        MaterialPill(
                            text = if (app.shellOnly) "shell" else "root",
                            color = if (app.shellOnly) scheme.onSurfaceVariant else AccentRoot,
                        )
                    }
                }
                Text(
                    text = "${app.packageName} · UID ${app.uid}",
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.width(8.dp))
            Switch(checked = app.allowed, onCheckedChange = { onToggleAllowed() })
        }
        if (serverIsRoot && app.allowed) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 6.dp, start = 50.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Root 访问", style = MaterialTheme.typography.bodyMedium)
                    Text(
                        text = if (app.shellOnly) "降级为 shell 权限执行" else "以 root 权限执行",
                        style = MaterialTheme.typography.bodySmall,
                        color = scheme.onSurfaceVariant,
                    )
                }
                Switch(checked = !app.shellOnly, onCheckedChange = { onToggleRoot() })
            }
        }
    }
}

@Composable
private fun MaterialMetric(label: String, value: String, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        Text(label, style = MaterialTheme.typography.labelSmall, color = scheme.onSurfaceVariant)
    }
}

@Composable
private fun MaterialPill(text: String, color: Color) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(color.copy(alpha = 0.14f))
            .padding(horizontal = 8.dp, vertical = 2.dp),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Medium,
            color = color,
        )
    }
}

@Composable
private fun MaterialFilterChip(text: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(text) },
    )
}
