package me.diksu.ultra.ui.screen.backup

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Build
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.diksu.ultra.R
import me.diksu.ultra.data.backup.BackupEntry
import me.diksu.ultra.data.backup.BackupKind
import me.diksu.ultra.data.backup.BackupRepository
import me.diksu.ultra.ui.LocalUiMode
import me.diksu.ultra.ui.UiMode
import me.diksu.ultra.ui.component.dialog.rememberConfirmDialog
import me.diksu.ultra.ui.component.material.SegmentedColumn
import me.diksu.ultra.ui.component.material.SegmentedListItem
import me.diksu.ultra.ui.component.material.TopBarBackButton
import me.diksu.ultra.ui.navigation3.LocalNavigator
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon as MiuixIcon
import top.yukonga.miuix.kmp.basic.IconButton as MiuixIconButton
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.basic.Text as MiuixText
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.icon.extended.Delete
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme
import top.yukonga.miuix.kmp.basic.Scaffold as MiuixScaffold

/**
 * 备份与恢复：授权规则和模块各一份，都打成 zip 放在 App 私有目录（`Android/data/.../backups`）。
 *
 * 页面只负责三件事：说明备份的是什么、说明恢复后要重启、把失败原因原样显示。恢复不做任何
 * 「自动选最新」之类的猜测——从哪一份、什么时候恢复，都由用户按下去。
 */
@Composable
fun BackupScreen() {
    val repo = remember { BackupRepository() }
    val scope = rememberCoroutineScope()
    var entries by remember { mutableStateOf<List<BackupEntry>>(emptyList()) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var pending by remember { mutableStateOf<Pair<BackupEntry, BackupAction>?>(null) }

    fun refresh() {
        scope.launch { entries = withContext(Dispatchers.IO) { repo.list() } }
    }

    /** 跑一个备份/恢复动作：返回值为完成后显示的提示，异常转成一行原因，不吞掉。 */
    fun exec(action: suspend () -> String?) {
        if (busy) return
        scope.launch {
            busy = true
            message = try {
                action()
            } catch (e: Exception) {
                e.message ?: e::class.java.simpleName
            }
            busy = false
            refresh()
        }
    }

    LaunchedEffect(Unit) { refresh() }

    val restoreTitle = stringResource(R.string.backup_restore_confirm)
    val deleteTitle = stringResource(R.string.backup_delete_confirm)
    val restoredNotice = stringResource(R.string.backup_restored_reboot)

    val confirm = rememberConfirmDialog(
        onConfirm = {
            pending?.let { (entry, action) ->
                when (action) {
                    BackupAction.Restore -> exec {
                        withContext(Dispatchers.IO) { repo.restore(entry) }
                        restoredNotice
                    }

                    BackupAction.Delete -> exec {
                        withContext(Dispatchers.IO) { repo.delete(entry) }
                        null
                    }
                }
            }
            pending = null
        },
        onDismiss = { pending = null },
    )

    val requestRestore: (BackupEntry) -> Unit = { entry ->
        pending = entry to BackupAction.Restore
        confirm.showConfirm(title = restoreTitle, content = entry.displayName)
    }
    val requestDelete: (BackupEntry) -> Unit = { entry ->
        pending = entry to BackupAction.Delete
        confirm.showConfirm(title = deleteTitle, content = entry.displayName)
    }
    val onCreate: (BackupKind) -> Unit = { kind ->
        exec {
            withContext(Dispatchers.IO) { repo.create(kind) }
            null
        }
    }

    when (LocalUiMode.current) {
        UiMode.Miuix, UiMode.MiuixStock -> BackupScreenMiuix(
            entries = entries,
            message = message,
            onCreate = onCreate,
            onRestore = requestRestore,
            onDelete = requestDelete,
        )

        UiMode.Material -> BackupScreenMaterial(
            entries = entries,
            message = message,
            onCreate = onCreate,
            onRestore = requestRestore,
            onDelete = requestDelete,
        )
    }
}

private enum class BackupAction { Restore, Delete }

@Composable
private fun BackupScreenMiuix(
    entries: List<BackupEntry>,
    message: String?,
    onCreate: (BackupKind) -> Unit,
    onRestore: (BackupEntry) -> Unit,
    onDelete: (BackupEntry) -> Unit,
) {
    val navigator = LocalNavigator.current
    MiuixScaffold(
        topBar = {
            SmallTopAppBar(
                title = stringResource(id = R.string.backup_title),
                navigationIcon = {
                    MiuixIconButton(onClick = { navigator.pop() }) {
                        MiuixIcon(
                            imageVector = MiuixIcons.Back,
                            contentDescription = null,
                            tint = colorScheme.onBackground,
                        )
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 12.dp),
        ) {
            item {
                SmallTitle(
                    text = stringResource(R.string.backup_section_create),
                    modifier = Modifier.padding(top = 12.dp),
                )
                Card(modifier = Modifier.padding(top = 12.dp).fillMaxWidth()) {
                    ArrowPreference(
                        title = stringResource(R.string.backup_create_superuser),
                        summary = stringResource(R.string.backup_create_superuser_summary),
                        startAction = {
                            MiuixIcon(
                                imageVector = Icons.Rounded.Security,
                                contentDescription = null,
                                modifier = Modifier.padding(end = 6.dp),
                                tint = colorScheme.onBackground,
                            )
                        },
                        onClick = { onCreate(BackupKind.Superuser) },
                    )
                    ArrowPreference(
                        title = stringResource(R.string.backup_create_modules),
                        summary = stringResource(R.string.backup_create_modules_summary),
                        startAction = {
                            MiuixIcon(
                                imageVector = Icons.Rounded.Build,
                                contentDescription = null,
                                modifier = Modifier.padding(end = 6.dp),
                                tint = colorScheme.onBackground,
                            )
                        },
                        onClick = { onCreate(BackupKind.Modules) },
                    )
                }
                MiuixText(
                    text = stringResource(R.string.backup_reboot_note),
                    color = colorScheme.onSurfaceVariantSummary,
                    style = MiuixTheme.textStyles.footnote1,
                    modifier = Modifier.padding(top = 8.dp, start = 4.dp),
                )
                message?.let {
                    MiuixText(
                        text = it,
                        color = colorScheme.primary,
                        style = MiuixTheme.textStyles.footnote1,
                        modifier = Modifier.padding(top = 6.dp, start = 4.dp),
                    )
                }
                SmallTitle(
                    text = stringResource(R.string.backup_section_existing),
                    modifier = Modifier.padding(top = 12.dp),
                )
            }
            if (entries.isEmpty()) {
                item {
                    Card(modifier = Modifier.padding(top = 12.dp).fillMaxWidth()) {
                        MiuixText(
                            text = stringResource(R.string.backup_empty),
                            color = colorScheme.onSurfaceVariantSummary,
                            style = MiuixTheme.textStyles.body1,
                            modifier = Modifier.padding(16.dp),
                        )
                    }
                }
            } else {
                items(entries, key = { it.file.absolutePath }) { entry ->
                    Card(
                        modifier = Modifier.padding(top = 12.dp).fillMaxWidth(),
                        onClick = { onRestore(entry) },
                    ) {
                        BasicComponent(
                            title = "${kindLabel(entry.kind)} · ${entry.displayName}",
                            summary = "${formatSize(entry.sizeBytes)} · ${entry.appVersion}",
                            endActions = {
                                MiuixIconButton(onClick = { onDelete(entry) }) {
                                    MiuixIcon(
                                        imageVector = MiuixIcons.Delete,
                                        contentDescription = stringResource(R.string.backup_delete),
                                        tint = colorScheme.onSurfaceVariantSummary,
                                    )
                                }
                            },
                        )
                    }
                }
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

@Composable
private fun BackupScreenMaterial(
    entries: List<BackupEntry>,
    message: String?,
    onCreate: (BackupKind) -> Unit,
    onRestore: (BackupEntry) -> Unit,
    onDelete: (BackupEntry) -> Unit,
) {
    val navigator = LocalNavigator.current
    androidx.compose.material3.Scaffold(
        topBar = {
            androidx.compose.material3.TopAppBar(
                title = { Text(stringResource(R.string.backup_title)) },
                navigationIcon = { TopBarBackButton(onClick = { navigator.pop() }) },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
        ) {
            item {
                SegmentedColumn(
                    modifier = Modifier.padding(top = 12.dp),
                    title = stringResource(R.string.backup_section_create),
                    content = buildList<@Composable () -> Unit> {
                        add {
                            SegmentedListItem(
                                onClick = { onCreate(BackupKind.Superuser) },
                                headlineContent = { Text(stringResource(R.string.backup_create_superuser)) },
                                supportingContent = {
                                    Text(stringResource(R.string.backup_create_superuser_summary))
                                },
                                leadingContent = { Icon(Icons.Rounded.Security, null) },
                            )
                        }
                        add {
                            SegmentedListItem(
                                onClick = { onCreate(BackupKind.Modules) },
                                headlineContent = { Text(stringResource(R.string.backup_create_modules)) },
                                supportingContent = {
                                    Text(stringResource(R.string.backup_create_modules_summary))
                                },
                                leadingContent = { Icon(Icons.Rounded.Build, null) },
                            )
                        }
                    },
                )
                Text(
                    text = stringResource(R.string.backup_reboot_note),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 8.dp, start = 4.dp),
                )
                message?.let {
                    Text(
                        text = it,
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 6.dp, start = 4.dp),
                    )
                }
            }
            if (entries.isEmpty()) {
                item {
                    Text(
                        text = stringResource(R.string.backup_empty),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(top = 16.dp),
                    )
                }
            } else {
                item {
                    SegmentedColumn(
                        modifier = Modifier.padding(top = 12.dp),
                        title = stringResource(R.string.backup_section_existing),
                        content = buildList<@Composable () -> Unit> {
                            entries.forEach { entry ->
                                add {
                                    SegmentedListItem(
                                        onClick = { onRestore(entry) },
                                        headlineContent = {
                                            Text("${kindLabel(entry.kind)} · ${entry.displayName}")
                                        },
                                        supportingContent = {
                                            Text("${formatSize(entry.sizeBytes)} · ${entry.appVersion}")
                                        },
                                        trailingContent = {
                                            IconButton(onClick = { onDelete(entry) }) {
                                                Icon(
                                                    Icons.Rounded.Delete,
                                                    contentDescription = stringResource(R.string.backup_delete),
                                                )
                                            }
                                        },
                                    )
                                }
                            }
                        },
                    )
                }
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

@Composable
private fun kindLabel(kind: BackupKind): String = when (kind) {
    BackupKind.Superuser -> stringResource(R.string.backup_kind_superuser)
    BackupKind.Modules -> stringResource(R.string.backup_kind_modules)
}

private fun formatSize(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val kb = bytes / 1024.0
    return if (kb < 1024) String.format("%.1f KB", kb) else String.format("%.1f MB", kb / 1024.0)
}
