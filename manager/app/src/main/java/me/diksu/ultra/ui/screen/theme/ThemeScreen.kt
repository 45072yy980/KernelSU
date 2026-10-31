package me.diksu.ultra.ui.screen.theme

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.diksu.ultra.R
import me.diksu.ultra.data.repository.SettingsRepositoryImpl
import me.diksu.ultra.data.repository.currentThemeId
import me.diksu.ultra.theme.InstalledTheme
import me.diksu.ultra.theme.ThemeImportResult
import me.diksu.ultra.theme.ThemeStore
import me.diksu.ultra.ui.LocalUiMode
import me.diksu.ultra.ui.UiMode
import me.diksu.ultra.ui.component.dialog.AppAlertDialog
import me.diksu.ultra.ui.isMiuixFamily
import me.diksu.ultra.ui.navigation3.LocalNavigator
import me.diksu.ultra.ui.theme.isInDarkTheme
import top.yukonga.miuix.kmp.theme.ColorSchemeMode
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.ThemeController
import top.yukonga.miuix.kmp.basic.Card as MiuixCard
import top.yukonga.miuix.kmp.basic.Icon as MiuixIcon
import top.yukonga.miuix.kmp.basic.IconButton as MiuixIconButton
import top.yukonga.miuix.kmp.basic.Scaffold as MiuixScaffold
import top.yukonga.miuix.kmp.basic.Text as MiuixText
import top.yukonga.miuix.kmp.basic.TextButton as MiuixTextButton
import top.yukonga.miuix.kmp.basic.TopAppBar as MiuixTopAppBar
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme

private val AccentError = Color(0xFFE53935)

/**
 * 自定义主题库：导入 / 应用 / 导出 / 删除已安装的主题包。
 *
 * 这一页用 miuix 组件实现，三种皮肤都走同一套内容：miuix 系直接渲染；Material 下没有 miuix 的
 * 颜色局部量，所以进页面时补一层跟随当前明暗的 miuix 调色板，而不是让组件落到"只有浅色"的库默认值。
 * 所有磁盘与解密工作都在 [ThemeStore] 的 suspend 函数里（内部切到 IO），本页不读文件。
 *
 * 失败路径（都不隐藏）：
 * - 导入失败：`ThemeImportResult.errors` 原样贴成页面顶部的红条，不自动消失；
 * - 导出失败：同样的红条，写"导出失败"；
 * - 应用时切换 uiMode 是最后一次写入，写失败也不会让列表状态假装成功 —— 列表状态来自磁盘。
 */
@Composable
fun ThemeScreen() {
    if (LocalUiMode.current.isMiuixFamily) {
        ThemeScreenContent()
    } else {
        val dark = isInDarkTheme()
        MiuixTheme(
            controller = ThemeController(
                colorSchemeMode = if (dark) ColorSchemeMode.Dark else ColorSchemeMode.Light,
            ),
        ) {
            ThemeScreenContent()
        }
    }
}

@Composable
private fun ThemeScreenContent() {
    val navigator = LocalNavigator.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var themes by remember { mutableStateOf<List<InstalledTheme>>(emptyList()) }
    var currentId by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(true) }
    var pageError by remember { mutableStateOf<String?>(null) }
    var notice by remember { mutableStateOf<String?>(null) }
    var preview by remember { mutableStateOf<ThemeImportResult?>(null) }
    var pendingDelete by remember { mutableStateOf<InstalledTheme?>(null) }
    var exportTarget by remember { mutableStateOf<InstalledTheme?>(null) }

    val appliedHint = stringResource(R.string.theme_applied_hint)
    val importFailed = stringResource(R.string.theme_import_failed)
    val exportFailed = stringResource(R.string.theme_export_failed)
    val exportDone = stringResource(R.string.theme_export_done)
    val deleteDone = stringResource(R.string.theme_delete_done)
    val deleteFailed = stringResource(R.string.theme_delete_failed)

    fun reload() {
        scope.launch {
            val loaded = withContext(Dispatchers.IO) {
                ThemeStore.list() to currentThemeId()
            }
            themes = loaded.first
            currentId = loaded.second
            loading = false
        }
    }

    LaunchedEffect(Unit) { reload() }

    // A short-lived confirmation line; errors stay on screen until the next action.
    LaunchedEffect(notice) {
        if (notice != null) {
            delay(2500)
            notice = null
        }
    }

    fun applyTheme(id: String) {
        pageError = null
        scope.launch {
            val stored = withContext(Dispatchers.IO) {
                ThemeStore.setCurrent(id)
                currentThemeId()
            }
            if (stored != id) {
                pageError = importFailed
                return@launch
            }
            // The style has to be Themed or the package would have no visible effect.
            // MainActivityViewModel listens for the "ui_mode" preference, so the swap is live.
            SettingsRepositoryImpl().uiMode = UiMode.Themed.value
            currentId = id
            reload()
            notice = appliedHint
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            pageError = null
            scope.launch {
                // ThemeStore.import decrypts, validates and installs on the IO dispatcher.
                val result = ThemeStore.import(uri)
                if (result.ok) {
                    // Installed but not selected yet: the preview decides that.
                    preview = result
                    reload()
                } else {
                    pageError = result.errors.joinToString("\n").ifBlank { importFailed }
                }
            }
        }
    }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri: Uri? ->
        val theme = exportTarget
        exportTarget = null
        if (uri != null && theme != null) {
            pageError = null
            scope.launch {
                val done = withContext(Dispatchers.IO) {
                    runCatching {
                        context.contentResolver.openOutputStream(uri)?.use { out ->
                            // export() switches to IO itself and closes nothing we own here.
                            ThemeStore.export(theme.id, out)
                        } ?: false
                    }.getOrDefault(false)
                }
                if (done) notice = exportDone else pageError = exportFailed
            }
        }
    }

    MiuixScaffold(
        topBar = {
            MiuixTopAppBar(
                title = stringResource(R.string.theme_page_title),
                navigationIcon = {
                    MiuixIconButton(onClick = { navigator.pop() }) {
                        MiuixIcon(
                            imageVector = MiuixIcons.Back,
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
            item { HintCard(stringResource(R.string.theme_page_summary)) }
            pageError?.let { message ->
                item { MessageCard(message, AccentError) }
            }
            notice?.let { message ->
                item { MessageCard(message, colorScheme.primary) }
            }
            item {
                MiuixCard(modifier = Modifier.fillMaxWidth().padding(top = 12.dp)) {
                    Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                        MiuixTextButton(
                            text = stringResource(R.string.theme_import),
                            onClick = { importLauncher.launch(arrayOf("*/*")) },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
            if (!loading && themes.isEmpty()) {
                item { EmptyCard() }
            }
            items(themes, key = { it.id }) { theme ->
                ThemeRow(
                    theme = theme,
                    isCurrent = theme.id == currentId,
                    onApply = { applyTheme(theme.id) },
                    onExport = {
                        exportTarget = theme
                        exportLauncher.launch(exportFileName(theme.name))
                    },
                    onDelete = { pendingDelete = theme },
                )
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }

    preview?.let { result ->
        AppAlertDialog(
            title = stringResource(R.string.theme_preview_title),
            confirmText = stringResource(R.string.theme_preview_confirm),
            dismissText = stringResource(R.string.theme_preview_later),
            onConfirm = {
                preview = null
                val importedId = result.id
                if (importedId != null) applyTheme(importedId)
            },
            onDismissRequest = { preview = null },
        ) {
            PreviewBody(result)
        }
    }

    pendingDelete?.let { theme ->
        AppAlertDialog(
            title = stringResource(R.string.theme_delete_title),
            confirmText = stringResource(R.string.theme_delete),
            dismissText = stringResource(android.R.string.cancel),
            confirmEnabled = true,
            onDismissRequest = { pendingDelete = null },
            onConfirm = {
                pendingDelete = null
                scope.launch {
                    val deleted = ThemeStore.delete(theme.id)
                    reload()
                    if (deleted) notice = deleteDone else pageError = deleteFailed
                }
            },
        ) {
            MiuixText(
                text = stringResource(
                    if (theme.id == currentId) R.string.theme_delete_current_message
                    else R.string.theme_delete_message,
                    theme.name,
                ),
                fontSize = 14.sp,
                color = colorScheme.onSurface,
            )
        }
    }
}

@Composable
private fun HintCard(summary: String) {
    MiuixCard(modifier = Modifier.fillMaxWidth()) {
        MiuixText(
            text = summary,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            fontSize = 12.sp,
            color = colorScheme.onSurfaceVariantSummary,
        )
    }
}

@Composable
private fun MessageCard(message: String, color: Color) {
    MiuixCard(modifier = Modifier.fillMaxWidth().padding(top = 12.dp)) {
        MiuixText(
            text = message,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            fontSize = 12.sp,
            color = color,
        )
    }
}

@Composable
private fun EmptyCard() {
    MiuixCard(modifier = Modifier.fillMaxWidth().padding(top = 12.dp)) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            MiuixText(
                text = stringResource(R.string.theme_empty_title),
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                color = colorScheme.onSurface,
            )
            Spacer(Modifier.height(4.dp))
            MiuixText(
                text = stringResource(R.string.theme_empty_summary),
                fontSize = 12.sp,
                color = colorScheme.onSurfaceVariantSummary,
            )
        }
    }
}

@Composable
private fun ThemeRow(
    theme: InstalledTheme,
    isCurrent: Boolean,
    onApply: () -> Unit,
    onExport: () -> Unit,
    onDelete: () -> Unit,
) {
    val meta = stringResource(
        R.string.theme_meta,
        theme.author.ifBlank { "—" },
        theme.version,
        theme.assetCount,
    )
    MiuixCard(modifier = Modifier.fillMaxWidth().padding(top = 12.dp)) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                MiuixText(
                    text = theme.name,
                    modifier = Modifier.weight(1f),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    color = colorScheme.onSurface,
                )
                if (isCurrent) {
                    MiuixText(
                        text = stringResource(R.string.theme_in_use),
                        fontSize = 12.sp,
                        color = colorScheme.primary,
                    )
                }
            }
            Spacer(Modifier.height(2.dp))
            MiuixText(text = meta, fontSize = 12.sp, color = colorScheme.onSurfaceVariantSummary)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MiuixTextButton(
                    text = stringResource(R.string.theme_apply),
                    onClick = onApply,
                    enabled = !isCurrent,
                )
                MiuixTextButton(text = stringResource(R.string.theme_export), onClick = onExport)
                MiuixTextButton(text = stringResource(R.string.theme_delete), onClick = onDelete)
            }
        }
    }
}

/** Two columns the importer promised: what will be applied, what was dropped and why. */
/** SAF suggests the file name verbatim; keep only characters a file name can carry. */
private fun exportFileName(name: String): String {
    val cleaned = name.map { ch ->
        if (ch.isLetterOrDigit() || ch == '.' || ch == '_' || ch == '-' || ch == ' ') ch else '_'
    }.joinToString("").trim()
    return (cleaned.ifEmpty { "theme" }) + ".theme"
}

@Composable
private fun PreviewBody(result: ThemeImportResult) {
    val maxLines = 6
    Column {
        MiuixText(
            text = stringResource(R.string.theme_preview_applied, result.applied.size),
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            color = colorScheme.onSurface,
        )
        result.applied.take(maxLines).forEach { path ->
            MiuixText(text = "· $path", fontSize = 12.sp, color = colorScheme.onSurfaceVariantSummary)
        }
        if (result.applied.size > maxLines) {
            MiuixText(text = "…", fontSize = 12.sp, color = colorScheme.onSurfaceVariantSummary)
        }
        Spacer(Modifier.height(10.dp))
        MiuixText(
            text = stringResource(R.string.theme_preview_ignored, result.ignored.size),
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            color = colorScheme.onSurface,
        )
        result.ignored.take(maxLines).forEach { path ->
            MiuixText(text = "· $path", fontSize = 12.sp, color = colorScheme.onSurfaceVariantSummary)
        }
        if (result.ignored.size > maxLines) {
            MiuixText(text = "…", fontSize = 12.sp, color = colorScheme.onSurfaceVariantSummary)
        }
    }
}
