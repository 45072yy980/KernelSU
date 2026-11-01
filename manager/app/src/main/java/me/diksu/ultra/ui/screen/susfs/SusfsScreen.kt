package me.diksu.ultra.ui.screen.susfs

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.diksu.ultra.R
import me.diksu.ultra.data.susfs.SusfsFeatureCatalog
import me.diksu.ultra.data.susfs.SusfsRepository
import me.diksu.ultra.data.susfs.SusfsRule
import me.diksu.ultra.data.susfs.display
import me.diksu.ultra.ui.LocalUiMode
import me.diksu.ultra.ui.component.dialog.AppAlertDialog
import me.diksu.ultra.ui.component.material.TopBarBackButton
import me.diksu.ultra.ui.isMiuixFamily
import me.diksu.ultra.ui.navigation3.LocalNavigator
import top.yukonga.miuix.kmp.basic.Icon as MiuixIcon
import top.yukonga.miuix.kmp.basic.IconButton as MiuixIconButton
import top.yukonga.miuix.kmp.basic.Scaffold as MiuixScaffold
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme

/** 一次输入对话框要的字段。 */
private data class EditorSpec(
    val title: String,
    val fields: List<String>,
    val hints: List<String> = emptyList(),
    /** 打开时预填的值；长度不足时补空串。 */
    val initial: List<String> = emptyList(),
    val onConfirm: (List<String>) -> Unit,
)

/**
 * SuSFS 面板：六个可滑动页签 + 一份配置。
 *
 * 交互照 FinaSU：编辑只改草稿，顶部提示条常驻「有未应用的改动 · 应用」；导入 / 恢复 / 恢复默认
 * 这类整份替换先弹预览，落到草稿后仍要用户按应用才下发。
 *
 * 外观上刻意只用项目现成的行组件（`ArrowPreference` / `SegmentedListItem`），图省事自绘 Row
 * 的结果就是行高松散、没有箭头、和设置页格格不入。
 */
@Composable
fun SusfsScreen() {
    val vm: SusfsViewModel = viewModel()
    val state by vm.state.collectAsStateWithLifecycle()
    val navigator = LocalNavigator.current
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var editor by remember { mutableStateOf<EditorSpec?>(null) }
    var pendingDelete by remember { mutableStateOf<SusfsRule?>(null) }

    val pager = rememberPagerState(pageCount = { 6 })
    // 每页滚动位置挂在 pager 之外：pager 会回收离屏页。
    val scrolls = remember { List(6) { ScrollState(0) } }

    val title = stringResource(R.string.settings_susfs)
    val tabs = listOf(
        stringResource(R.string.susfs_tab_overview),
        stringResource(R.string.susfs_tab_settings),
        stringResource(R.string.susfs_tab_paths),
        stringResource(R.string.susfs_tab_spoof),
        stringResource(R.string.susfs_tab_redirect),
        stringResource(R.string.susfs_tab_extensions),
    )

    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            scope.launch {
                val text = withContext(Dispatchers.IO) {
                    runCatching {
                        context.contentResolver.openInputStream(uri)?.use { it.readBytes().decodeToString() }
                    }.getOrNull()
                }
                if (text == null) vm.reportMessage("读取文件失败") else vm.importConfig(text)
            }
        }
    }
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri ->
        if (uri != null) {
            val text = vm.exportConfig()
            scope.launch {
                withContext(Dispatchers.IO) {
                    runCatching { context.contentResolver.openOutputStream(uri)?.use { it.write(text.toByteArray()) } }
                }
                vm.reportMessage("已导出配置")
            }
        }
    }

    val onBack: () -> Unit = { navigator.pop() }
    val body: @Composable (PaddingValues) -> Unit = { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            SusfsTabBar(tabs = tabs, selected = pager.currentPage, onSelect = { index ->
                scope.launch { pager.animateScrollToPage(index) }
            })
            AnimatedVisibility(
                visible = state.busy,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut(),
            ) { SusfsBusyBar() }
            AnimatedVisibility(
                visible = state.dirty,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut(),
            ) {
                SusfsNotice(
                    text = stringResource(R.string.susfs_dirty),
                    actionText = stringResource(R.string.susfs_apply),
                    onAction = { vm.applyDraft() },
                    secondaryText = stringResource(R.string.susfs_discard),
                    onSecondary = { vm.discardDraft() },
                )
            }
            HorizontalPager(state = pager, modifier = Modifier.weight(1f)) { page ->
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(scrolls[page])
                        .padding(horizontal = 12.dp),
                ) {
                    when (page) {
                        0 -> OverviewPage(state, vm)
                        1 -> SettingsPage(state, vm)
                        2 -> PathsPage(state, vm, { editor = it }, { pendingDelete = it })
                        3 -> SpoofPage(state, vm, { editor = it }, { pendingDelete = it })
                        4 -> RedirectPage(state, vm, { editor = it }, { pendingDelete = it })
                        else -> ExtensionsPage(
                            state = state,
                            vm = vm,
                            onImport = { importLauncher.launch(arrayOf("application/json", "text/plain", "*/*")) },
                            onExport = { exportLauncher.launch("susfs-config.json") },
                        )
                    }
                    Spacer(Modifier.height(24.dp))
                }
            }
            state.message?.let { message ->
                Row(modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
                    SusfsText(text = message, size = 13)
                }
            }
        }
    }

    if (LocalUiMode.current.isMiuixFamily) {
        MiuixScaffold(
            topBar = {
                SmallTopAppBar(
                    title = title,
                    navigationIcon = {
                        MiuixIconButton(onClick = onBack) {
                            MiuixIcon(
                                imageVector = MiuixIcons.Back,
                                contentDescription = null,
                                tint = colorScheme.onBackground,
                            )
                        }
                    },
                )
            },
        ) { padding -> body(padding) }
    } else {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(title) },
                    navigationIcon = { TopBarBackButton(onClick = onBack) },
                )
            },
        ) { padding -> body(padding) }
    }

    editor?.let { spec -> SusfsEditorDialog(spec = spec, onDismiss = { editor = null }) }

    pendingDelete?.let { rule ->
        AppAlertDialog(
            title = stringResource(R.string.susfs_remove),
            onDismissRequest = { pendingDelete = null },
            confirmText = stringResource(R.string.susfs_confirm),
            onConfirm = {
                vm.removeRule(rule)
                pendingDelete = null
            },
            dismissText = stringResource(R.string.susfs_cancel),
        ) {
            SusfsText(text = rule.display, size = 14)
        }
    }

    if (state.pendingReset) {
        AppAlertDialog(
            title = stringResource(R.string.susfs_reset_defaults),
            onDismissRequest = { vm.cancelResetDefaults() },
            confirmText = stringResource(R.string.susfs_confirm),
            onConfirm = { vm.confirmResetDefaults() },
            dismissText = stringResource(R.string.susfs_cancel),
        ) {
            SusfsText(text = stringResource(R.string.susfs_reset_defaults_confirm), size = 14)
        }
    }

    val pendingImport = state.pendingImport
    val pendingDiff = state.pendingDiff
    if (pendingImport != null && pendingDiff != null) {
        val diffText = stringResource(
            R.string.susfs_import_diff,
            pendingDiff.added.size,
            pendingDiff.removed.size,
            pendingDiff.switchesChanged.size,
        )
        AppAlertDialog(
            title = stringResource(R.string.susfs_import),
            onDismissRequest = { vm.cancelImport() },
            confirmText = stringResource(R.string.susfs_confirm),
            onConfirm = { vm.confirmImport() },
            dismissText = stringResource(R.string.susfs_cancel),
        ) {
            SusfsText(text = diffText, size = 14)
        }
    }
}

// ── 各页 ──────────────────────────────────────────────────────────────────────

@Composable
private fun OverviewPage(state: SusfsUiState, vm: SusfsViewModel) {
    SusfsCard(title = stringResource(R.string.susfs_status_title)) {
        val status = state.status
        SusfsRow(
            title = if (status.loaded) {
                stringResource(R.string.susfs_status_loaded, status.version, status.variant)
            } else {
                stringResource(R.string.susfs_status_unloaded)
            },
            summary = stringResource(R.string.susfs_capabilities_count, status.features.size),
            icon = Icons.Filled.Info,
        )
        SusfsRow(
            title = stringResource(R.string.susfs_rules_count, state.draft.rules.size),
            summary = state.draft.rules
                .groupingBy { it::class.simpleName ?: "?" }
                .eachCount()
                .entries
                .joinToString(" · ") { "${it.key} ${it.value}" },
            icon = Icons.Filled.Layers,
        )
        SusfsRow(
            title = stringResource(R.string.susfs_refresh),
            icon = Icons.Filled.Refresh,
            onClick = { vm.refresh() },
        )
    }

    val report = state.report
    if (report != null) {
        SusfsCard(title = stringResource(R.string.susfs_report_title)) {
            SusfsRow(title = report.summary(), icon = Icons.Filled.BugReport)
            report.failed.entries.take(5).forEach { (key, reason) ->
                SusfsRow(title = key, summary = reason, icon = Icons.Filled.Info)
            }
            if (report.pendingReboot.isNotEmpty()) {
                SusfsRow(
                    title = stringResource(R.string.susfs_pending_reboot, report.pendingReboot.size),
                    summary = stringResource(R.string.susfs_pending_reboot_hint),
                    icon = Icons.Filled.RestartAlt,
                )
            }
        }
    }
}

@Composable
private fun SettingsPage(state: SusfsUiState, vm: SusfsViewModel) {
    SusfsCard(title = stringResource(R.string.susfs_features_title)) {
        SusfsSwitchRow(
            title = stringResource(R.string.susfs_log),
            summary = stringResource(R.string.susfs_log_summary),
            icon = Icons.Filled.Terminal,
            checked = state.draft.enabled[SusfsRepository.SWITCH_LOG] == true,
            onCheckedChange = { vm.setSwitch(SusfsRepository.SWITCH_LOG, it) },
        )
        SusfsSwitchRow(
            title = stringResource(R.string.susfs_avc),
            summary = stringResource(R.string.susfs_avc_summary),
            icon = Icons.Filled.BugReport,
            checked = state.draft.enabled[SusfsRepository.SWITCH_AVC] == true,
            onCheckedChange = { vm.setSwitch(SusfsRepository.SWITCH_AVC, it) },
        )
        SusfsSwitchRow(
            title = stringResource(R.string.susfs_hide_mnts),
            summary = stringResource(R.string.susfs_hide_mnts_summary),
            icon = Icons.Filled.VisibilityOff,
            checked = state.draft.enabled[SusfsRepository.SWITCH_HIDE_MOUNTS] == true,
            onCheckedChange = { vm.setSwitch(SusfsRepository.SWITCH_HIDE_MOUNTS, it) },
        )
    }

    SusfsCard(title = stringResource(R.string.susfs_capabilities_title)) {
        SusfsFeatureCatalog.resolve(state.status.features).forEach { (key, enabled) ->
            SusfsRow(
                title = featureLabel(key),
                summary = stringResource(
                    if (enabled) R.string.susfs_feature_enabled else R.string.susfs_feature_disabled,
                ),
                icon = Icons.Filled.Settings,
            )
        }
    }
}

@Composable
private fun PathsPage(
    state: SusfsUiState,
    vm: SusfsViewModel,
    onEdit: (EditorSpec) -> Unit,
    onRequestDelete: (SusfsRule) -> Unit,
) {
    val pathLabel = stringResource(R.string.susfs_field_path)
    val pathTitle = stringResource(R.string.susfs_add_path)
    val loopTitle = stringResource(R.string.susfs_add_path_loop)
    val mapTitle = stringResource(R.string.susfs_add_map)

    RuleSection(
        title = pathTitle,
        rules = state.draft.rules.filterIsInstance<SusfsRule.HiddenPath>(),
        onAdd = {
            onEdit(
                EditorSpec(title = pathTitle, fields = listOf(pathLabel), hints = listOf("/system/app/Example")) { values ->
                    values.firstOrNull()?.trim()?.takeIf { it.isNotEmpty() }?.let { vm.addRule(SusfsRule.HiddenPath(it)) }
                },
            )
        },
        onRequestDelete = onRequestDelete,
    )
    RuleSection(
        title = loopTitle,
        rules = state.draft.rules.filterIsInstance<SusfsRule.HiddenLoop>(),
        onAdd = {
            onEdit(
                EditorSpec(title = loopTitle, fields = listOf(pathLabel), hints = listOf("/data/local/tmp/example")) { values ->
                    values.firstOrNull()?.trim()?.takeIf { it.isNotEmpty() }?.let { vm.addRule(SusfsRule.HiddenLoop(it)) }
                },
            )
        },
        onRequestDelete = onRequestDelete,
    )
    RuleSection(
        title = mapTitle,
        rules = state.draft.rules.filterIsInstance<SusfsRule.SusMap>(),
        onAdd = {
            onEdit(
                EditorSpec(title = mapTitle, fields = listOf(pathLabel), hints = listOf("/system/fonts")) { values ->
                    values.firstOrNull()?.trim()?.takeIf { it.isNotEmpty() }?.let { vm.addRule(SusfsRule.SusMap(it)) }
                },
            )
        },
        onRequestDelete = onRequestDelete,
    )
}

@Composable
private fun SpoofPage(
    state: SusfsUiState,
    vm: SusfsViewModel,
    onEdit: (EditorSpec) -> Unit,
    onRequestDelete: (SusfsRule) -> Unit,
) {
    val uname = state.draft.rules.filterIsInstance<SusfsRule.Uname>().firstOrNull()
    val cmdline = state.draft.rules.filterIsInstance<SusfsRule.Cmdline>().firstOrNull()
    val empty = stringResource(R.string.susfs_empty)
    val releaseLabel = stringResource(R.string.susfs_field_release)
    val versionLabel = stringResource(R.string.susfs_field_version)
    val fileLabel = stringResource(R.string.susfs_field_file)
    val pathLabel = stringResource(R.string.susfs_field_path)
    val unameTitle = stringResource(R.string.susfs_set_uname)
    val cmdlineTitle = stringResource(R.string.susfs_set_cmdline)
    val kstatTitle = stringResource(R.string.susfs_add_kstat)

    // 内核事实：用户改 uname 多半是想"像另一个内核"，预填当前值比空输入框好打字。
    val kernelFacts = remember {
        runCatching {
            val info = android.system.Os.uname()
            listOf(info.release, info.version)
        }.getOrDefault(listOf("", ""))
    }

    SusfsCard(title = unameTitle) {
        SusfsRow(
            title = unameTitle,
            summary = uname?.let { "${it.release}${if (it.version.isEmpty()) "" else " · ${it.version}"}" } ?: empty,
            icon = Icons.Filled.Fingerprint,
            onClick = {
                onEdit(
                    EditorSpec(
                        title = unameTitle,
                        fields = listOf(releaseLabel, versionLabel),
                        hints = listOf(kernelFacts[0], kernelFacts[1]),
                        initial = listOf(uname?.release ?: kernelFacts[0], uname?.version ?: kernelFacts[1]),
                    ) { values ->
                        val release = values.getOrNull(0).orEmpty().trim()
                        val version = values.getOrNull(1).orEmpty().trim()
                        if (release.isNotEmpty() || version.isNotEmpty()) {
                            uname?.let { vm.removeRule(it) }
                            vm.addRule(SusfsRule.Uname(release, version))
                        }
                    }
                )
            },
        )
    }

    SusfsCard(title = cmdlineTitle) {
        SusfsRow(
            title = cmdlineTitle,
            summary = cmdline?.sourcePath ?: empty,
            icon = Icons.Filled.Terminal,
            onClick = {
                onEdit(
                    EditorSpec(title = cmdlineTitle, fields = listOf(fileLabel), hints = listOf("/data/local/tmp/cmdline")) { values ->
                        values.firstOrNull()?.trim()?.takeIf { it.isNotEmpty() }?.let {
                            cmdline?.let { old -> vm.removeRule(old) }
                            vm.addRule(SusfsRule.Cmdline(it))
                        }
                    },
                )
            },
        )
    }

    RuleSection(
        title = kstatTitle,
        rules = state.draft.rules.filterIsInstance<SusfsRule.Kstat>(),
        onAdd = {
            onEdit(
                EditorSpec(title = kstatTitle, fields = listOf(pathLabel), hints = listOf("/system/fonts/Roboto-Regular.ttf")) { values ->
                    values.firstOrNull()?.trim()?.takeIf { it.isNotEmpty() }?.let { vm.addRule(SusfsRule.Kstat(it)) }
                },
            )
        },
        onRequestDelete = onRequestDelete,
    )

    val cloneTitle = stringResource(R.string.susfs_update_kstat_full_clone)
    RuleSection(
        title = cloneTitle,
        rules = state.draft.rules.filterIsInstance<SusfsRule.KstatClone>(),
        onAdd = {
            onEdit(
                EditorSpec(title = cloneTitle, fields = listOf(pathLabel), hints = listOf("/system/fonts/Roboto-Regular.ttf")) { values ->
                    values.firstOrNull()?.trim()?.takeIf { it.isNotEmpty() }?.let { vm.addRule(SusfsRule.KstatClone(it)) }
                },
            )
        },
        onRequestDelete = onRequestDelete,
    )
}

@Composable
private fun RedirectPage(
    state: SusfsUiState,
    vm: SusfsViewModel,
    onEdit: (EditorSpec) -> Unit,
    onRequestDelete: (SusfsRule) -> Unit,
) {
    val targetLabel = stringResource(R.string.susfs_field_target)
    val redirectedLabel = stringResource(R.string.susfs_field_redirected)
    val redirectTitle = stringResource(R.string.susfs_add_redirect)
    RuleSection(
        title = redirectTitle,
        rules = state.draft.rules.filterIsInstance<SusfsRule.Redirect>(),
        onAdd = {
            onEdit(
                EditorSpec(title = redirectTitle, fields = listOf(targetLabel, redirectedLabel), hints = listOf("/system/example", "/data/local/tmp/example")) { values ->
                    val target = values.getOrNull(0).orEmpty().trim()
                    val redirected = values.getOrNull(1).orEmpty().trim()
                    if (target.isNotEmpty() && redirected.isNotEmpty()) {
                        vm.addRule(SusfsRule.Redirect(target, redirected))
                    }
                },
            )
        },
        onRequestDelete = onRequestDelete,
    )
}

@Composable
private fun ExtensionsPage(
    state: SusfsUiState,
    vm: SusfsViewModel,
    onImport: () -> Unit,
    onExport: () -> Unit,
) {
    SusfsCard(title = stringResource(R.string.susfs_config_title)) {
        SusfsRow(title = stringResource(R.string.susfs_import), icon = Icons.Filled.Upload, onClick = onImport)
        SusfsRow(title = stringResource(R.string.susfs_export), icon = Icons.Filled.Download, onClick = onExport)
        SusfsRow(
            title = stringResource(R.string.susfs_restore_last),
            summary = stringResource(R.string.susfs_restore_last_summary),
            icon = Icons.Filled.RestartAlt,
            onClick = { vm.restoreFromKernel() },
        )
        SusfsRow(
            title = stringResource(R.string.susfs_reset_defaults),
            summary = stringResource(R.string.susfs_reset_defaults_summary),
            icon = Icons.Filled.Delete,
            onClick = { vm.requestResetDefaults() },
        )
    }

    SusfsCard(title = stringResource(R.string.susfs_proc_title)) {
        val proc = state.proc
        if (proc.isEmpty) {
            SusfsEmptyHint(stringResource(R.string.susfs_empty))
        } else {
            listOf(
                R.string.susfs_proc_path to proc.path,
                R.string.susfs_proc_hide_mounts to proc.hideMounts,
                R.string.susfs_proc_hide_modules to proc.hideModules,
                R.string.susfs_proc_kstat to proc.kstat,
                R.string.susfs_proc_open_redirect to proc.openRedirect,
                R.string.susfs_proc_avc to proc.avcSpoof,
                R.string.susfs_proc_log to proc.enableLog,
            ).forEach { (res, value) ->
                if (value.isNotBlank()) {
                    SusfsRow(title = stringResource(res), summary = value.take(200), icon = Icons.Filled.Info)
                }
            }
        }
    }
}

/** 一组同类规则：标题 + 添加行 + 每条一行（点击行 = 请求删除，需二次确认）。 */
@Composable
private fun <T : SusfsRule> RuleSection(
    title: String,
    rules: List<T>,
    onAdd: () -> Unit,
    onRequestDelete: (T) -> Unit,
) {
    SusfsCard(title = title) {
        SusfsRow(
            title = stringResource(R.string.susfs_add),
            icon = Icons.Filled.Add,
            onClick = onAdd,
        )
        if (rules.isEmpty()) {
            SusfsEmptyHint(stringResource(R.string.susfs_empty))
        } else {
            rules.forEach { rule ->
                SusfsRow(
                    title = rule.display,
                    icon = Icons.Filled.Link,
                    onClick = { onRequestDelete(rule) },
                )
            }
        }
    }
}

/** 通用输入对话框：1–3 个字段，确认时把值回调出去。 */
@Composable
private fun SusfsEditorDialog(spec: EditorSpec, onDismiss: () -> Unit) {
    var values by remember {
        mutableStateOf(spec.fields.indices.map { spec.initial.getOrNull(it).orEmpty() })
    }
    AppAlertDialog(
        title = spec.title,
        onDismissRequest = onDismiss,
        confirmText = stringResource(R.string.susfs_confirm),
        onConfirm = {
            if (values.any { it.isNotBlank() }) spec.onConfirm(values)
            onDismiss()
        },
        dismissText = stringResource(R.string.susfs_cancel),
    ) {
        Column {
            spec.fields.forEachIndexed { index, label ->
                SusfsTextField(
                    value = values[index],
                    onValueChange = { input ->
                        values = values.toMutableList().also { it[index] = input }
                    },
                    label = label,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
            }
        }
    }
}

/** 把 `CONFIG_KSU_SUSFS_*` 映射成可读名字；没收录的键回退到去掉前缀的原样。 */
@Composable
private fun featureLabel(key: String): String = when (key.removePrefix("CONFIG_KSU_SUSFS_")) {
    "SUS_PATH" -> stringResource(R.string.sus_path_feature_label)
    "SUS_MOUNT" -> stringResource(R.string.sus_mount_feature_label)
    "SUS_KSTAT" -> stringResource(R.string.sus_kstat_feature_label)
    "SUS_MAP" -> stringResource(R.string.sus_map_feature_label)
    "SPOOF_UNAME" -> stringResource(R.string.spoof_uname_feature_label)
    "SPOOF_CMDLINE_OR_BOOTCONFIG" -> stringResource(R.string.spoof_cmdline_feature_label)
    "OPEN_REDIRECT" -> stringResource(R.string.open_redirect_feature_label)
    "ENABLE_LOG" -> stringResource(R.string.enable_log_feature_label)
    "HIDE_KSU_SUSFS_SYMBOLS" -> stringResource(R.string.hide_symbols_feature_label)
    else -> key.removePrefix("CONFIG_KSU_SUSFS_")
}
