package me.diksu.ultra.ui.screen.shizuku

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Article
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.diksu.ultra.shizuku.ShizukuServiceManager
import me.diksu.ultra.ui.LocalUiMode
import me.diksu.ultra.ui.isMiuixFamily
import me.diksu.ultra.ui.navigation3.LocalNavigator
import top.yukonga.miuix.kmp.basic.Card as MiuixCard
import top.yukonga.miuix.kmp.basic.Icon as MiuixIcon
import top.yukonga.miuix.kmp.basic.IconButton as MiuixIconButton
import top.yukonga.miuix.kmp.basic.Scaffold as MiuixScaffold
import top.yukonga.miuix.kmp.basic.Text as MiuixText
import top.yukonga.miuix.kmp.basic.TopAppBar as MiuixTopAppBar
import top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import java.io.File

/**
 * Shizuku 服务日志独立页面。
 *
 * 功能：
 * - 日志来源切换：服务端持久日志 / 系统 logcat（Shizuku 相关 tag）
 * - 日志级别过滤：V / D / I / W / E
 * - 关键词搜索
 * - 复制全部 / 导出为文件分享 / 清除日志 / 刷新
 * - 彩色级别标识，自动滚动到底部
 */
@Composable
fun ShizukuLogScreen() {
    val navigator = LocalNavigator.current
    val uiMode = LocalUiMode.current
    if (uiMode.isMiuixFamily) {
        ShizukuLogScreenMiuix(onBack = { navigator.pop() })
    } else {
        ShizukuLogScreenMaterial(onBack = { navigator.pop() })
    }
}

// ── 共享状态与日志解析 ──────────────────────────────────────────────────────

private enum class LogSource { SERVER, LOGCAT }

private enum class LogLevel(val letter: Char, val color: Color) {
    Verbose('V', Color(0xFF9E9E9E)),
    Debug('D', Color(0xFF64B5F6)),
    Info('I', Color(0xFF81C784)),
    Warn('W', Color(0xFFFFB74D)),
    Error('E', Color(0xFFE57373)),
    Unknown('?', Color(0xFFBDBDBD)),
}

private data class LogLine(val level: LogLevel, val text: String)

private fun parseLogLevel(line: String): LogLevel {
    // 匹配常见日志格式：" I/Tag "、"12-34 56:78:90.123  1234  5678 I Tag: message"
    val patterns = listOf(
        Regex("""\s([VDIWE])/"""),
        Regex("""\s([VDIWE])\s"""),
    )
    for (pattern in patterns) {
        val match = pattern.find(line)
        if (match != null) {
            return when (match.groupValues[1][0]) {
                'V' -> LogLevel.Verbose
                'D' -> LogLevel.Debug
                'I' -> LogLevel.Info
                'W' -> LogLevel.Warn
                'E' -> LogLevel.Error
                else -> LogLevel.Unknown
            }
        }
    }
    return LogLevel.Unknown
}

private fun parseLines(raw: String): List<LogLine> {
    if (raw.isBlank()) return emptyList()
    return raw.split('\n')
        .filter { it.isNotBlank() }
        .map { LogLine(parseLogLevel(it), it) }
}

@Composable
private fun rememberLogState(): Pair<LogState, LogActions> {
    var source by remember { mutableStateOf(LogSource.SERVER) }
    var allLines by remember { mutableStateOf<List<LogLine>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var query by remember { mutableStateOf("") }
    var activeLevels by remember { mutableStateOf<Set<LogLevel>>(emptySet()) }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current

    fun refresh() {
        scope.launch {
            isLoading = true
            val raw = withContext(Dispatchers.IO) {
                when (source) {
                    LogSource.SERVER -> ShizukuServiceManager.getServerLog()
                    LogSource.LOGCAT -> ShizukuServiceManager.getLogcat()
                }
            }
            allLines = parseLines(raw)
            isLoading = false
        }
    }

    LaunchedEffect(source) { refresh() }

    val visibleLines = remember(allLines, query, activeLevels) {
        allLines.filter { line ->
            (activeLevels.isEmpty() || line.level in activeLevels) &&
                (query.isBlank() || line.text.contains(query, ignoreCase = true))
        }
    }

    // 自动滚动到底部
    LaunchedEffect(visibleLines.size, isLoading) {
        if (!isLoading && visibleLines.isNotEmpty()) {
            listState.scrollToItem(visibleLines.size - 1)
        }
    }

    val actions = LogActions(
        onRefresh = { refresh() },
        onCopy = {
            val text = visibleLines.joinToString("\n") { it.text }
            if (text.isNotBlank()) {
                clipboard.setText(AnnotatedString(text))
            }
        },
        onExport = {
            scope.launch {
                val text = withContext(Dispatchers.IO) {
                    buildString {
                        append("==== Shizuku server log ====\n")
                        append(ShizukuServiceManager.getServerLog())
                        append("\n\n==== logcat (shizuku tags) ====\n")
                        append(ShizukuServiceManager.getLogcat())
                    }
                }
                if (text.isBlank()) return@launch
                val file = File(context.cacheDir, "shizuku_log.txt")
                file.writeText(text)
                val uri = FileProvider.getUriForFile(
                    context, "${context.packageName}.fileprovider", file
                )
                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                context.startActivity(Intent.createChooser(shareIntent, "导出 Shizuku 日志"))
            }
        },
        onClear = {
            scope.launch {
                withContext(Dispatchers.IO) { ShizukuServiceManager.clearServerLog() }
                refresh()
            }
        },
    )

    return LogState(
        source = source,
        allLines = allLines,
        visibleLines = visibleLines,
        isLoading = isLoading,
        query = query,
        activeLevels = activeLevels,
        listState = listState,
    ) to actions
}

private data class LogState(
    val source: LogSource,
    val allLines: List<LogLine>,
    val visibleLines: List<LogLine>,
    val isLoading: Boolean,
    val query: String,
    val activeLevels: Set<LogLevel>,
    val listState: androidx.compose.foundation.lazy.LazyListState,
)

private data class LogActions(
    val onRefresh: () -> Unit,
    val onCopy: () -> Unit,
    val onExport: () -> Unit,
    val onClear: () -> Unit,
)

// ── Miuix 版本 ──────────────────────────────────────────────────────────────

@Composable
private fun ShizukuLogScreenMiuix(onBack: () -> Unit) {
    val (state, actions) = rememberLogState()
    var source by remember { mutableStateOf(state.source) }
    var query by remember { mutableStateOf(state.query) }
    var activeLevels by remember { mutableStateOf(state.activeLevels) }
    var showMenu by remember { mutableStateOf(false) }
    var isSearchActive by remember { mutableStateOf(false) }

    // 同步外部状态变化
    LaunchedEffect(state.source) { source = state.source }
    LaunchedEffect(state.query) { query = state.query }
    LaunchedEffect(state.activeLevels) { activeLevels = state.activeLevels }

    MiuixScaffold(
        topBar = {
            MiuixTopAppBar(
                title = if (isSearchActive) "" else "Shizuku 日志",
                navigationIcon = {
                    MiuixIconButton(onClick = {
                        if (isSearchActive) {
                            isSearchActive = false
                            query = ""
                        } else {
                            onBack()
                        }
                    }) {
                        MiuixIcon(MiuixIcons.Back, contentDescription = null, tint = colorScheme.onSurface)
                    }
                },
                actions = {
                    if (isSearchActive) {
                        if (query.isNotEmpty()) {
                            MiuixIconButton(onClick = { query = "" }) {
                                MiuixIcon(Icons.Filled.Close, contentDescription = null, tint = colorScheme.onSurface)
                            }
                        }
                    } else {
                        MiuixIconButton(onClick = { isSearchActive = true }) {
                            MiuixIcon(Icons.Filled.Search, contentDescription = null, tint = colorScheme.onSurface)
                        }
                    }
                    Box {
                        MiuixIconButton(onClick = { showMenu = !showMenu }) {
                            MiuixIcon(Icons.Filled.Refresh, contentDescription = "更多", tint = colorScheme.onSurface)
                        }
                        DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                            DropdownMenuItem(
                                text = { Text("刷新") },
                                onClick = { showMenu = false; actions.onRefresh() },
                                leadingIcon = { Icon(Icons.Filled.Refresh, contentDescription = null) },
                            )
                            DropdownMenuItem(
                                text = { Text("复制全部") },
                                onClick = { showMenu = false; actions.onCopy() },
                                leadingIcon = { Icon(Icons.Filled.ContentCopy, contentDescription = null) },
                            )
                            DropdownMenuItem(
                                text = { Text("导出日志") },
                                onClick = { showMenu = false; actions.onExport() },
                                leadingIcon = { Icon(Icons.Filled.Share, contentDescription = null) },
                            )
                            DropdownMenuItem(
                                text = { Text("清除日志") },
                                onClick = { showMenu = false; actions.onClear() },
                                leadingIcon = { Icon(Icons.Filled.DeleteSweep, contentDescription = null) },
                            )
                        }
                    }
                },
            )
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            // 搜索栏
            if (isSearchActive) {
                Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        placeholder = { Text("搜索日志...", fontSize = 14.sp) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    )
                }
            }

            // 来源与级别过滤
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = source == LogSource.SERVER,
                        onClick = { source = LogSource.SERVER },
                        label = { Text("服务日志", fontSize = 13.sp) },
                    )
                    FilterChip(
                        selected = source == LogSource.LOGCAT,
                        onClick = { source = LogSource.LOGCAT },
                        label = { Text("Logcat", fontSize = 13.sp) },
                    )
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    LogLevel.entries.filter { it != LogLevel.Unknown }.forEach { level ->
                        FilterChip(
                            selected = level in activeLevels,
                            onClick = {
                                activeLevels = if (level in activeLevels) {
                                    activeLevels - level
                                } else {
                                    activeLevels + level
                                }
                            },
                            label = {
                                Text(
                                    "${level.letter} ${level.name}",
                                    fontSize = 12.sp,
                                    color = level.color,
                                    fontWeight = FontWeight.Medium,
                                )
                            },
                        )
                    }
                }
            }

            // 日志内容
            when {
                state.isLoading -> Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) { CircularProgressIndicator() }
                state.visibleLines.isEmpty() -> Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.AutoMirrored.Filled.Article,
                            contentDescription = null,
                            modifier = Modifier.size(48.dp),
                            tint = colorScheme.onSurfaceVariantSummary,
                        )
                        Spacer(Modifier.height(12.dp))
                        MiuixText(
                            text = "暂无日志",
                            fontSize = 14.sp,
                            color = colorScheme.onSurfaceVariantSummary,
                        )
                    }
                }
                else -> Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFF1A1A1A)),
                ) {
                    LazyColumn(
                        state = state.listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                    ) {
                        items(state.visibleLines) { line ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 1.dp),
                            ) {
                                Text(
                                    text = "${line.level.letter} ",
                                    fontSize = 11.sp,
                                    color = line.level.color,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace,
                                )
                                Text(
                                    text = line.text,
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
    }
}

// ── Material 3 版本 ─────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ShizukuLogScreenMaterial(onBack: () -> Unit) {
    val (state, actions) = rememberLogState()
    var source by remember { mutableStateOf(state.source) }
    var query by remember { mutableStateOf(state.query) }
    var activeLevels by remember { mutableStateOf(state.activeLevels) }
    var showMenu by remember { mutableStateOf(false) }
    var isSearchActive by remember { mutableStateOf(false) }

    LaunchedEffect(state.source) { source = state.source }
    LaunchedEffect(state.query) { query = state.query }
    LaunchedEffect(state.activeLevels) { activeLevels = state.activeLevels }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { if (!isSearchActive) Text("Shizuku 日志") },
                navigationIcon = {
                    IconButton(onClick = {
                        if (isSearchActive) {
                            isSearchActive = false
                            query = ""
                        } else {
                            onBack()
                        }
                    }) {
                        Icon(MiuixIcons.Back, contentDescription = null)
                    }
                },
                actions = {
                    if (isSearchActive) {
                        if (query.isNotEmpty()) {
                            IconButton(onClick = { query = "" }) {
                                Icon(Icons.Filled.Close, contentDescription = null)
                            }
                        }
                    } else {
                        IconButton(onClick = { isSearchActive = true }) {
                            Icon(Icons.Filled.Search, contentDescription = null)
                        }
                    }
                    Box {
                        IconButton(onClick = { showMenu = !showMenu }) {
                            Icon(Icons.Filled.Refresh, contentDescription = "更多")
                        }
                        DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                            DropdownMenuItem(
                                text = { Text("刷新") },
                                onClick = { showMenu = false; actions.onRefresh() },
                                leadingIcon = { Icon(Icons.Filled.Refresh, contentDescription = null) },
                            )
                            DropdownMenuItem(
                                text = { Text("复制全部") },
                                onClick = { showMenu = false; actions.onCopy() },
                                leadingIcon = { Icon(Icons.Filled.ContentCopy, contentDescription = null) },
                            )
                            DropdownMenuItem(
                                text = { Text("导出日志") },
                                onClick = { showMenu = false; actions.onExport() },
                                leadingIcon = { Icon(Icons.Filled.Share, contentDescription = null) },
                            )
                            DropdownMenuItem(
                                text = { Text("清除日志") },
                                onClick = { showMenu = false; actions.onClear() },
                                leadingIcon = { Icon(Icons.Filled.DeleteSweep, contentDescription = null) },
                            )
                        }
                    }
                },
            )
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            if (isSearchActive) {
                Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        placeholder = { Text("搜索日志...") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    )
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = source == LogSource.SERVER,
                        onClick = { source = LogSource.SERVER },
                        label = { Text("服务日志") },
                    )
                    FilterChip(
                        selected = source == LogSource.LOGCAT,
                        onClick = { source = LogSource.LOGCAT },
                        label = { Text("Logcat") },
                    )
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    LogLevel.entries.filter { it != LogLevel.Unknown }.forEach { level ->
                        FilterChip(
                            selected = level in activeLevels,
                            onClick = {
                                activeLevels = if (level in activeLevels) {
                                    activeLevels - level
                                } else {
                                    activeLevels + level
                                }
                            },
                            label = {
                                Text(
                                    "${level.letter} ${level.name}",
                                    color = level.color,
                                    fontWeight = FontWeight.Medium,
                                )
                            },
                        )
                    }
                }
            }

            when {
                state.isLoading -> Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) { CircularProgressIndicator() }
                state.visibleLines.isEmpty() -> Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.AutoMirrored.Filled.Article,
                            contentDescription = null,
                            modifier = Modifier.size(48.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(12.dp))
                        Text(
                            text = "暂无日志",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                else -> Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFF1A1A1A)),
                ) {
                    LazyColumn(
                        state = state.listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                    ) {
                        items(state.visibleLines) { line ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 1.dp),
                            ) {
                                Text(
                                    text = "${line.level.letter} ",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = line.level.color,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace,
                                )
                                Text(
                                    text = line.text,
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
