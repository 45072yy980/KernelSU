package me.weishu.kernelsu.ui.screen.shizuku

import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.weishu.kernelsu.shizuku.ShizukuServiceManager
import me.weishu.kernelsu.ui.LocalUiMode
import me.weishu.kernelsu.ui.isMiuixFamily
import me.weishu.kernelsu.ui.navigation3.LocalNavigator
import top.yukonga.miuix.kmp.basic.Card as MiuixCard
import top.yukonga.miuix.kmp.basic.Icon as MiuixIcon
import top.yukonga.miuix.kmp.basic.IconButton as MiuixIconButton
import top.yukonga.miuix.kmp.basic.Scaffold as MiuixScaffold
import top.yukonga.miuix.kmp.basic.Text as MiuixText
import top.yukonga.miuix.kmp.basic.TopAppBar as MiuixTopAppBar
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme
import top.yukonga.miuix.kmp.utils.BackHandler

/**
 * Shizuku 内置服务管理面板。
 *
 * 功能：
 * - 查看服务运行状态，手动启动/停止
 * - 开机自启开关
 * - 已授权应用列表，可撤销权限
 * - 查看服务器日志，可刷新/清除
 */
@Composable
fun ShizukuScreen() {
    val navigator = LocalNavigator.current
    BackHandler { navigator.pop() }

    val uiMode = LocalUiMode.current
    if (uiMode.isMiuixFamily) {
        ShizukuScreenMiuix(onBack = { navigator.pop() })
    } else {
        ShizukuScreenMaterial(onBack = { navigator.pop() })
    }
}

// ── 共享状态逻辑 ────────────────────────────────────────────────────────────

private data class ShizukuState(
    val running: Boolean = false,
    val bootEnabled: Boolean = false,
    val apps: List<PackageInfo> = emptyList(),
    val log: String = "",
    val loading: Boolean = false,
    val starting: Boolean = false,
    val stopping: Boolean = false,
    val startError: String? = null,
)

@Composable
private fun rememberShizukuState(): Pair<ShizukuState, ShizukuActions> {
    var state by remember { mutableStateOf(ShizukuState()) }
    val scope = rememberCoroutineScope()

    suspend fun refresh() {
        state = state.copy(loading = true)
        val running = withContext(Dispatchers.IO) { ShizukuServiceManager.isServerRunning() }
        val apps = withContext(Dispatchers.IO) { ShizukuServiceManager.getApplications() }
        val log = withContext(Dispatchers.IO) {
            if (running) ShizukuServiceManager.getServerLog() else ""
        }
        state = state.copy(
            running = running,
            apps = apps ?: emptyList(),
            log = log,
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
                    val log = withContext(Dispatchers.IO) { ShizukuServiceManager.getServerLog() }
                    state = state.copy(
                        startError = "启动失败，请查看日志",
                        log = log,
                    )
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
        onClearLog = {
            scope.launch {
                withContext(Dispatchers.IO) { ShizukuServiceManager.clearServerLog() }
                state = state.copy(log = "")
            }
        },
        onRevokeApp = { uid ->
            scope.launch {
                withContext(Dispatchers.IO) { ShizukuServiceManager.setAllowed(uid, false) }
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
    val onClearLog: () -> Unit,
    val onRevokeApp: (Int) -> Unit,
)

// ── Miuix 版本 ──────────────────────────────────────────────────────────────

@Composable
private fun ShizukuScreenMiuix(onBack: () -> Unit) {
    val (state, actions) = rememberShizukuState()
    val context = androidx.compose.ui.platform.LocalContext.current

    MiuixScaffold(
        topBar = {
            MiuixTopAppBar(
                title = "Shizuku",
                navigationIcon = {
                    MiuixIconButton(onClick = onBack) {
                        MiuixIcon(
                            imageVector = top.yukonga.miuix.kmp.utils.MiuixIcons.Back,
                            contentDescription = null,
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
                                    text = if (state.running) "Shizuku 服务正在运行" else "Shizuku 服务未启动",
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
                                text = "已授权应用",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Medium,
                                color = colorScheme.onSurface,
                                modifier = Modifier.weight(1f),
                            )
                            MiuixText(
                                text = "${state.apps.size} 个",
                                fontSize = 13.sp,
                                color = colorScheme.onSurfaceVariantSummary,
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                        if (state.apps.isEmpty()) {
                            MiuixText(
                                text = "暂无已授权应用",
                                fontSize = 14.sp,
                                color = colorScheme.onSurfaceVariantSummary,
                            )
                        } else {
                            state.apps.forEach { pkg ->
                                AppRowMiuix(
                                    pkg = pkg,
                                    onRevoke = { actions.onRevokeApp(pkg.applicationInfo?.uid ?: -1) },
                                )
                            }
                        }
                    }
                }
            }

            item { Spacer(Modifier.height(12.dp)) }

            // 日志卡片
            item {
                MiuixCard(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            MiuixText(
                                text = "服务日志",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Medium,
                                color = colorScheme.onSurface,
                                modifier = Modifier.weight(1f),
                            )
                            MiuixIconButton(onClick = actions.onRefresh) {
                                MiuixIcon(
                                    imageVector = Icons.Filled.Refresh,
                                    contentDescription = "刷新",
                                    tint = colorScheme.onSurface,
                                    modifier = Modifier.size(20.dp),
                                )
                            }
                            MiuixIconButton(onClick = actions.onClearLog) {
                                MiuixIcon(
                                    imageVector = Icons.Filled.Delete,
                                    contentDescription = "清除",
                                    tint = colorScheme.onSurface,
                                    modifier = Modifier.size(20.dp),
                                )
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(200.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0xFF1A1A1A)),
                        ) {
                            if (state.log.isBlank()) {
                                MiuixText(
                                    text = if (state.running) "暂无日志" else "服务未启动",
                                    fontSize = 13.sp,
                                    color = Color(0xFF888888),
                                    modifier = Modifier.align(Alignment.Center),
                                )
                            } else {
                                androidx.compose.foundation.text.selection.SelectionContainer {
                                    MiuixText(
                                        text = state.log,
                                        fontSize = 11.sp,
                                        color = Color(0xFFCCCCCC),
                                        fontFamily = FontFamily.Monospace,
                                    )
                                }
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
private fun AppRowMiuix(pkg: PackageInfo, onRevoke: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val appInfo = pkg.applicationInfo
    val label = appInfo?.loadLabel(context.packageManager)?.toString() ?: pkg.packageName
    val icon = try {
        appInfo?.loadIcon(context.packageManager)
    } catch (e: Exception) {
        null
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            androidx.compose.foundation.Image(
                painter = androidx.compose.ui.graphics.painter.BitmapPainter(
                    icon.toBitmap().asImageBitmap()
                ),
                contentDescription = null,
                modifier = Modifier.size(36.dp),
            )
        } else {
            MiuixIcon(
                imageVector = Icons.Filled.Check,
                contentDescription = null,
                tint = colorScheme.primary,
                modifier = Modifier.size(36.dp),
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            MiuixText(
                text = label,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = colorScheme.onSurface,
            )
            MiuixText(
                text = pkg.packageName,
                fontSize = 12.sp,
                color = colorScheme.onSurfaceVariantSummary,
            )
        }
        MiuixIconButton(onClick = onRevoke) {
            MiuixIcon(
                imageVector = Icons.Filled.Block,
                contentDescription = "撤销权限",
                tint = Color(0xFFE53935),
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

// ── Material 版本 ────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ShizukuScreenMaterial(onBack: () -> Unit) {
    val (state, actions) = rememberShizukuState()
    val context = androidx.compose.ui.platform.LocalContext.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Shizuku") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.Filled.WaterDrop,
                            contentDescription = null,
                        )
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
                                    text = if (state.running) "Shizuku 服务正在运行" else "Shizuku 服务未启动",
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
                                text = "已授权应用",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.weight(1f),
                            )
                            Text(
                                text = "${state.apps.size} 个",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                        if (state.apps.isEmpty()) {
                            Text(
                                text = "暂无已授权应用",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        } else {
                            state.apps.forEach { pkg ->
                                AppRowMaterial(
                                    pkg = pkg,
                                    onRevoke = { actions.onRevokeApp(pkg.applicationInfo?.uid ?: -1) },
                                )
                            }
                        }
                    }
                }
            }

            // 日志卡片
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
                                text = "服务日志",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.weight(1f),
                            )
                            IconButton(onClick = actions.onRefresh) {
                                Icon(
                                    imageVector = Icons.Filled.Refresh,
                                    contentDescription = "刷新",
                                )
                            }
                            IconButton(onClick = actions.onClearLog) {
                                Icon(
                                    imageVector = Icons.Filled.Delete,
                                    contentDescription = "清除",
                                )
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(200.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0xFF1A1A1A)),
                        ) {
                            if (state.log.isBlank()) {
                                Text(
                                    text = if (state.running) "暂无日志" else "服务未启动",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color(0xFF888888),
                                    modifier = Modifier.align(Alignment.Center),
                                )
                            } else {
                                androidx.compose.foundation.text.selection.SelectionContainer {
                                    Text(
                                        text = state.log,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Color(0xFFCCCCCC),
                                        fontFamily = FontFamily.Monospace,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AppRowMaterial(pkg: PackageInfo, onRevoke: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val appInfo = pkg.applicationInfo
    val label = appInfo?.loadLabel(context.packageManager)?.toString() ?: pkg.packageName
    val icon = try {
        appInfo?.loadIcon(context.packageManager)
    } catch (e: Exception) {
        null
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            androidx.compose.foundation.Image(
                painter = androidx.compose.ui.graphics.painter.BitmapPainter(
                    icon.toBitmap().asImageBitmap()
                ),
                contentDescription = null,
                modifier = Modifier.size(36.dp),
            )
        } else {
            Icon(
                imageVector = Icons.Filled.Check,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(36.dp),
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
                text = pkg.packageName,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        IconButton(onClick = onRevoke) {
            Icon(
                imageVector = Icons.Filled.Block,
                contentDescription = "撤销权限",
                tint = Color(0xFFE53935),
            )
        }
    }
}
