package me.weishu.kernelsu.ui.screen.shizuku

import android.content.pm.PackageInfo
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Article
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.weishu.kernelsu.shizuku.ShizukuServiceManager
import me.weishu.kernelsu.ui.LocalUiMode
import me.weishu.kernelsu.ui.isMiuixFamily
import me.weishu.kernelsu.ui.navigation3.LocalNavigator
import me.weishu.kernelsu.ui.navigation3.Route
import top.yukonga.miuix.kmp.basic.Card as MiuixCard
import top.yukonga.miuix.kmp.basic.Icon as MiuixIcon
import top.yukonga.miuix.kmp.basic.IconButton as MiuixIconButton
import top.yukonga.miuix.kmp.basic.Scaffold as MiuixScaffold
import top.yukonga.miuix.kmp.basic.Text as MiuixText
import top.yukonga.miuix.kmp.basic.TextButton as MiuixTextButton
import top.yukonga.miuix.kmp.basic.TopAppBar as MiuixTopAppBar
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back

/**
 * Shizuku 内置服务管理面板。
 *
 * 功能：
 * - 查看服务运行状态与运行身份（root / shell），手动启动/停止
 * - 开机自启开关
 * - 已授权应用列表，按名称排序，显示授权状态与权限等级
 * - 每个应用可独立控制授权与 root 访问（shellOnly 分权）
 * - 入口跳转至独立日志页面
 */
@Composable
fun ShizukuScreen() {
    val navigator = LocalNavigator.current
    val uiMode = LocalUiMode.current
    if (uiMode.isMiuixFamily) {
        ShizukuScreenMiuix(onBack = { navigator.pop() }, onOpenLog = { navigator.push(Route.ShizukuLog) })
    } else {
        ShizukuScreenMaterial(onBack = { navigator.pop() }, onOpenLog = { navigator.push(Route.ShizukuLog) })
    }
}

// ── 共享状态与数据模型 ──────────────────────────────────────────────────────

private data class ShizukuAppEntry(
    val info: PackageInfo,
    val uid: Int,
    val allowed: Boolean,
    val shellOnly: Boolean,
)

private data class ShizukuState(
    val running: Boolean = false,
    val serverIsRoot: Boolean = false,
    val bootEnabled: Boolean = false,
    val apps: List<ShizukuAppEntry> = emptyList(),
    val loading: Boolean = false,
    val starting: Boolean = false,
    val stopping: Boolean = false,
    val startError: String? = null,
)

@Composable
private fun rememberShizukuState(): Pair<ShizukuState, ShizukuActions> {
    var state by remember { mutableStateOf(ShizukuState()) }
    val scope = rememberCoroutineScope()
    val context = androidx.compose.ui.platform.LocalContext.current

    suspend fun refresh() {
        state = state.copy(loading = true)
        val running = withContext(Dispatchers.IO) { ShizukuServiceManager.isServerRunning() }
        val serverIsRoot = if (running) withContext(Dispatchers.IO) { ShizukuServiceManager.isRootServer() } else false
        val apps = if (running) {
            withContext(Dispatchers.IO) {
                ShizukuServiceManager.getApplications()?.mapNotNull { pkg ->
                    val uid = pkg.applicationInfo?.uid ?: return@mapNotNull null
                    ShizukuAppEntry(
                        info = pkg,
                        uid = uid,
                        allowed = runCatching { ShizukuServiceManager.isAllowed(uid) }.getOrDefault(false),
                        shellOnly = runCatching { ShizukuServiceManager.getShellOnly(uid) }.getOrDefault(false),
                    )
                }?.distinctBy { it.uid }?.sortedBy { app ->
                    runCatching {
                        app.info.applicationInfo?.loadLabel(context.packageManager)?.toString()?.lowercase().orEmpty()
                    }.getOrDefault("")
                }
            } ?: emptyList()
        } else {
            emptyList()
        }
        state = state.copy(
            running = running,
            serverIsRoot = serverIsRoot,
            apps = apps,
            loading = false,
        )
    }

    LaunchedEffect(Unit) {
        state = state.copy(bootEnabled = ShizukuServiceManager.isEnabled())
        refresh()
    }

    val actions = ShizukuActions(
        onStart = {
            scope.launch {
                state = state.copy(starting = true, startError = null)
                val ok = withContext(Dispatchers.IO) { ShizukuServiceManager.start(it) }
                state = state.copy(starting = false)
                if (!ok) {
                    state = state.copy(startError = "启动失败，请查看日志")
                }
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
        onBootChange = { enabled ->
            ShizukuServiceManager.setEnabled(enabled)
            state = state.copy(bootEnabled = enabled)
        },
        onRefresh = { scope.launch { refresh() } },
        onToggleApp = { uid, currentlyAllowed ->
            scope.launch {
                runCatching { ShizukuServiceManager.setAllowed(uid, !currentlyAllowed) }
                refresh()
            }
        },
        onToggleRoot = { uid, currentlyShellOnly ->
            scope.launch {
                runCatching { ShizukuServiceManager.setShellOnly(uid, !currentlyShellOnly) }
                refresh()
            }
        },
    )

    return state to actions
}

private data class ShizukuActions(
    val onStart: (android.content.Context) -> Unit,
    val onStop: () -> Unit,
    val onBootChange: (Boolean) -> Unit,
    val onRefresh: () -> Unit,
    val onToggleApp: (uid: Int, currentlyAllowed: Boolean) -> Unit,
    val onToggleRoot: (uid: Int, currentlyShellOnly: Boolean) -> Unit,
)

// ── Miuix 版本 ──────────────────────────────────────────────────────────────

@Composable
private fun ShizukuScreenMiuix(onBack: () -> Unit, onOpenLog: () -> Unit) {
    val (state, actions) = rememberShizukuState()
    val context = androidx.compose.ui.platform.LocalContext.current

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

            // 服务状态卡片
            item {
                MiuixCard(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            MiuixIcon(
                                imageVector = Icons.Filled.WaterDrop,
                                contentDescription = null,
                                tint = if (state.running) Color(0xFF4CAF50) else colorScheme.onSurfaceVariantSummary,
                                modifier = Modifier.size(32.dp),
                            )
                            Spacer(Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                MiuixText(
                                    text = if (state.running) "运行中" else "已停止",
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = colorScheme.onSurface,
                                )
                                MiuixText(
                                    text = buildString {
                                        if (state.running) {
                                            append(if (state.serverIsRoot) "Root 模式" else "Shell 模式")
                                            append(" · Shizuku 服务正在运行")
                                        } else {
                                            append("Shizuku 服务未启动")
                                        }
                                    },
                                    fontSize = 13.sp,
                                    color = colorScheme.onSurfaceVariantSummary,
                                )
                            }
                            if (state.running) {
                                MiuixTextButton(
                                    text = if (state.stopping) "停止中..." else "停止",
                                    onClick = { if (!state.stopping) actions.onStop() },
                                )
                            } else {
                                MiuixTextButton(
                                    text = if (state.starting) "启动中..." else "启动",
                                    onClick = { if (!state.starting) actions.onStart(context) },
                                )
                            }
                        }

                        state.startError?.let {
                            Spacer(Modifier.height(8.dp))
                            MiuixText(
                                text = it,
                                fontSize = 13.sp,
                                color = Color(0xFFE53935),
                            )
                        }
                    }
                }
            }

            item { Spacer(Modifier.height(12.dp)) }

            // 开机自启开关
            item {
                MiuixCard(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            MiuixText(
                                text = "开机自启",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Medium,
                                color = colorScheme.onSurface,
                            )
                            MiuixText(
                                text = "设备启动后自动启动 Shizuku 服务",
                                fontSize = 13.sp,
                                color = colorScheme.onSurfaceVariantSummary,
                            )
                        }
                        androidx.compose.material3.Switch(
                            checked = state.bootEnabled,
                            onCheckedChange = actions.onBootChange,
                        )
                    }
                }
            }

            item { Spacer(Modifier.height(12.dp)) }

            // 已授权应用
            item {
                MiuixCard(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            MiuixText(
                                text = "应用授权",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Medium,
                                color = colorScheme.onSurface,
                                modifier = Modifier.weight(1f),
                            )
                            MiuixText(
                                text = "${state.apps.count { it.allowed }} 个已授权",
                                fontSize = 13.sp,
                                color = colorScheme.onSurfaceVariantSummary,
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                        if (!state.running) {
                            MiuixText(
                                text = "服务未启动，无法获取应用列表",
                                fontSize = 14.sp,
                                color = colorScheme.onSurfaceVariantSummary,
                            )
                        } else if (state.apps.isEmpty()) {
                            MiuixText(
                                text = "暂无支持 Shizuku 的应用",
                                fontSize = 14.sp,
                                color = colorScheme.onSurfaceVariantSummary,
                            )
                        } else {
                            state.apps.forEach { app ->
                                AppRowMiuix(
                                    app = app,
                                    serverIsRoot = state.serverIsRoot,
                                    onToggleAllowed = { actions.onToggleApp(app.uid, app.allowed) },
                                    onToggleRoot = { actions.onToggleRoot(app.uid, app.shellOnly) },
                                )
                            }
                        }
                    }
                }
            }

            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

@Composable
private fun AppRowMiuix(
    app: ShizukuAppEntry,
    serverIsRoot: Boolean,
    onToggleAllowed: () -> Unit,
    onToggleRoot: () -> Unit,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val appInfo = app.info.applicationInfo
    val label = appInfo?.loadLabel(context.packageManager)?.toString() ?: app.info.packageName
    val icon = try {
        appInfo?.loadIcon(context.packageManager)
    } catch (e: Exception) {
        null
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (icon != null) {
                androidx.compose.foundation.Image(
                    painter = androidx.compose.ui.graphics.painter.BitmapPainter(
                        icon.toBitmap().asImageBitmap()
                    ),
                    contentDescription = null,
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(10.dp)),
                )
            } else {
                MiuixIcon(
                    imageVector = Icons.Filled.Check,
                    contentDescription = null,
                    tint = colorScheme.primary,
                    modifier = Modifier.size(40.dp),
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                MiuixText(
                    text = label,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    color = colorScheme.onSurface,
                )
                MiuixText(
                    text = "${app.info.packageName} · UID ${app.uid}",
                    fontSize = 12.sp,
                    color = colorScheme.onSurfaceVariantSummary,
                )
                // 状态标签行
                Row(
                    modifier = Modifier.padding(top = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    StatusChip(
                        text = if (app.allowed) "已授权" else "未授权",
                        color = if (app.allowed) Color(0xFF4CAF50) else colorScheme.onSurfaceVariantSummary,
                    )
                    if (serverIsRoot) {
                        StatusChip(
                            text = if (app.shellOnly) "Shell 权限" else "Root 权限",
                            color = if (!app.shellOnly) Color(0xFF2196F3) else colorScheme.onSurfaceVariantSummary,
                        )
                    }
                }
            }
        }

        // 控制开关行
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                MiuixText(
                    text = "Shizuku 授权",
                    fontSize = 14.sp,
                    color = colorScheme.onSurface,
                )
                MiuixText(
                    text = if (app.allowed) "允许该应用使用 Shizuku" else "拒绝该应用使用 Shizuku",
                    fontSize = 12.sp,
                    color = colorScheme.onSurfaceVariantSummary,
                )
            }
            Switch(checked = app.allowed, onCheckedChange = { onToggleAllowed() })
        }

        if (serverIsRoot) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    MiuixText(
                        text = "Root 访问",
                        fontSize = 14.sp,
                        color = colorScheme.onSurface,
                    )
                    MiuixText(
                        text = if (app.shellOnly) "降级为 Shell 权限执行命令" else "以 Root 权限执行命令",
                        fontSize = 12.sp,
                        color = colorScheme.onSurfaceVariantSummary,
                    )
                }
                Switch(checked = !app.shellOnly, onCheckedChange = { onToggleRoot() })
            }
        }
    }
}

@Composable
private fun StatusChip(text: String, color: Color) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(color.copy(alpha = 0.15f))
            .padding(horizontal = 8.dp, vertical = 2.dp),
    ) {
        MiuixText(
            text = text,
            fontSize = 11.sp,
            color = color,
            fontWeight = FontWeight.Medium,
        )
    }
}

// ── Material 3 版本 ─────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ShizukuScreenMaterial(onBack: () -> Unit, onOpenLog: () -> Unit) {
    val (state, actions) = rememberShizukuState()
    val context = androidx.compose.ui.platform.LocalContext.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Shizuku") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = MiuixIcons.Back,
                            contentDescription = null,
                        )
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
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // 服务状态卡片
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainer,
                    ),
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                imageVector = Icons.Filled.WaterDrop,
                                contentDescription = null,
                                tint = if (state.running) Color(0xFF4CAF50) else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(32.dp),
                            )
                            Spacer(Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = if (state.running) "运行中" else "已停止",
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.SemiBold,
                                )
                                Text(
                                    text = buildString {
                                        if (state.running) {
                                            append(if (state.serverIsRoot) "Root 模式" else "Shell 模式")
                                            append(" · Shizuku 服务正在运行")
                                        } else {
                                            append("Shizuku 服务未启动")
                                        }
                                    },
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            if (state.running) {
                                TextButton(onClick = { if (!state.stopping) actions.onStop() }) {
                                    Text(if (state.stopping) "停止中..." else "停止")
                                }
                            } else {
                                TextButton(onClick = { if (!state.starting) actions.onStart(context) }) {
                                    Text(if (state.starting) "启动中..." else "启动")
                                }
                            }
                        }

                        state.startError?.let {
                            Spacer(Modifier.height(8.dp))
                            Text(
                                text = it,
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color(0xFFE53935),
                            )
                        }
                    }
                }
            }

            // 开机自启开关
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainer,
                    ),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "开机自启",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Medium,
                            )
                            Text(
                                text = "设备启动后自动启动 Shizuku 服务",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Switch(
                            checked = state.bootEnabled,
                            onCheckedChange = actions.onBootChange,
                        )
                    }
                }
            }

            // 已授权应用
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainer,
                    ),
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = "应用授权",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.weight(1f),
                            )
                            Text(
                                text = "${state.apps.count { it.allowed }} 个已授权",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                        if (!state.running) {
                            Text(
                                text = "服务未启动，无法获取应用列表",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        } else if (state.apps.isEmpty()) {
                            Text(
                                text = "暂无支持 Shizuku 的应用",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        } else {
                            state.apps.forEach { app ->
                                AppRowMaterial(
                                    app = app,
                                    serverIsRoot = state.serverIsRoot,
                                    onToggleAllowed = { actions.onToggleApp(app.uid, app.allowed) },
                                    onToggleRoot = { actions.onToggleRoot(app.uid, app.shellOnly) },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AppRowMaterial(
    app: ShizukuAppEntry,
    serverIsRoot: Boolean,
    onToggleAllowed: () -> Unit,
    onToggleRoot: () -> Unit,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val appInfo = app.info.applicationInfo
    val label = appInfo?.loadLabel(context.packageManager)?.toString() ?: app.info.packageName
    val icon = try {
        appInfo?.loadIcon(context.packageManager)
    } catch (e: Exception) {
        null
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (icon != null) {
                androidx.compose.foundation.Image(
                    painter = androidx.compose.ui.graphics.painter.BitmapPainter(
                        icon.toBitmap().asImageBitmap()
                    ),
                    contentDescription = null,
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(10.dp)),
                )
            } else {
                Icon(
                    imageVector = Icons.Filled.Check,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(40.dp),
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium,
                )
                Text(
                    text = "${app.info.packageName} · UID ${app.uid}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(
                    modifier = Modifier.padding(top = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    StatusChipMaterial(
                        text = if (app.allowed) "已授权" else "未授权",
                        color = if (app.allowed) Color(0xFF4CAF50) else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (serverIsRoot) {
                        StatusChipMaterial(
                            text = if (app.shellOnly) "Shell 权限" else "Root 权限",
                            color = if (!app.shellOnly) Color(0xFF2196F3) else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Shizuku 授权",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    text = if (app.allowed) "允许该应用使用 Shizuku" else "拒绝该应用使用 Shizuku",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(checked = app.allowed, onCheckedChange = { onToggleAllowed() })
        }

        if (serverIsRoot) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Root 访问",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        text = if (app.shellOnly) "降级为 Shell 权限执行命令" else "以 Root 权限执行命令",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(checked = !app.shellOnly, onCheckedChange = { onToggleRoot() })
            }
        }
    }
}

@Composable
private fun StatusChipMaterial(text: String, color: Color) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(color.copy(alpha = 0.15f))
            .padding(horizontal = 8.dp, vertical = 2.dp),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = color,
            fontWeight = FontWeight.Medium,
        )
    }
}
