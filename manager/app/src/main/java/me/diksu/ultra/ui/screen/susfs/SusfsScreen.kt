package me.diksu.ultra.ui.screen.susfs

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.basic.Scaffold as MiuixScaffold
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme

/** 一次输入对话框要的字段。 */
private data class EditorSpec(
    val title: String,
    val fields: List<String>,
    val hints: List<String> = emptyList(),
    val onConfirm: (List<String>) -> Unit,
)

/**
 * SuSFS 面板：六个页签，一份配置。
 *
 * 与旧实现相比，逻辑只写一份（皮肤差异收在 [SusfsComponents]）；所有改动先落配置、再统一
 * 下发，结果按"成功 / 失败 / 跳过 / 待重启"四类呈现。
 */
@Composable
fun SusfsScreen() {
    val vm: SusfsViewModel = viewModel()
    val state by vm.state.collectAsStateWithLifecycle()
    val navigator = LocalNavigator.current
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var editor by remember { mutableStateOf<EditorSpec?>(null) }

    val title = stringResource(R.string.settings_susfs)
    val tabs = listOf(
        stringResource(R.string.susfs_tab_overview),
        stringResource(R.string.susfs_tab_settings),
        stringResource(R.string.susfs_tab_paths),
        stringResource(R.string.susfs_tab_spoof),
        stringResource(R.string.susfs_tab_redirect),
        stringResource(R.string.susfs_tab_extensions),
    )

    // 导入 / 导出走系统文件选择器，不自己拼路径。
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
        SusfsBody(padding, tabs, tab, { tab = it }, state) { page ->
            SusfsPage(
                page = page,
                state = state,
                vm = vm,
                onEdit = { editor = it },
                onImport = { importLauncher.launch(arrayOf("application/json", "text/plain", "*/*")) },
                onExport = { exportLauncher.launch("susfs-config.json") },
            )
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

    // 导入预览：先看会变什么，再决定要不要覆盖。
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

@Composable
private fun SusfsBody(
    padding: PaddingValues,
    tabs: List<String>,
    selected: Int,
    onSelect: (Int) -> Unit,
    state: SusfsUiState,
    content: @Composable (Int) -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize().padding(padding)) {
        SusfsTabBar(tabs = tabs, selected = selected, onSelect = onSelect)
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 12.dp),
        ) {
            content(selected)
            Spacer(Modifier.height(24.dp))
        }
        state.message?.let { message ->
            Row(modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
                SusfsText(text = message, size = 13)
            }
        }
    }
}

@Composable
private fun SusfsPage(
    page: Int,
    state: SusfsUiState,
    vm: SusfsViewModel,
    onEdit: (EditorSpec) -> Unit,
    onImport: () -> Unit,
    onExport: () -> Unit,
) {
    when (page) {
        0 -> OverviewPage(state, vm)
        1 -> SettingsPage(state, vm)
        2 -> PathsPage(state, vm, onEdit)
        3 -> SpoofPage(state, vm, onEdit)
        4 -> RedirectPage(state, vm, onEdit)
        else -> ExtensionsPage(state, vm, onImport, onExport)
    }
}

// ── 各页 ──────────────────────────────────────────────────────────────────────

@Composable
private fun OverviewPage(state: SusfsUiState, vm: SusfsViewModel) {
    SusfsSectionTitle(stringResource(R.string.susfs_status_title))
    SusfsCard {
        val status = state.status
        SusfsRow(
            title = if (status.loaded) {
                stringResource(R.string.susfs_status_loaded, status.version, status.variant)
            } else {
                stringResource(R.string.susfs_status_unloaded)
            },
            summary = stringResource(R.string.susfs_capabilities_count, status.features.size),
        )
        SusfsRow(
            title = stringResource(R.string.susfs_rules_count, state.config.rules.size),
            summary = state.config.rules
                .groupingBy { it::class.simpleName ?: "?" }
                .eachCount()
                .entries
                .joinToString(" · ") { "${it.key} ${it.value}" },
        )
        SusfsRow(title = stringResource(R.string.susfs_refresh), onClick = { vm.refresh() })
    }

    val report = state.report
    if (report != null) {
        SusfsSectionTitle(stringResource(R.string.susfs_report_title))
        SusfsCard {
            SusfsRow(title = report.summary())
            report.failed.entries.take(5).forEach { (key, reason) ->
                SusfsRow(title = key, summary = reason)
            }
            if (report.pendingReboot.isNotEmpty()) {
                SusfsRow(
                    title = stringResource(R.string.susfs_pending_reboot, report.pendingReboot.size),
                    summary = stringResource(R.string.susfs_pending_reboot_hint),
                )
            }
        }
    }
}

@Composable
private fun SettingsPage(state: SusfsUiState, vm: SusfsViewModel) {
    SusfsSectionTitle(stringResource(R.string.susfs_features_title))
    SusfsCard {
        SusfsSwitchRow(
            title = stringResource(R.string.susfs_log),
            summary = stringResource(R.string.susfs_log_summary),
            checked = state.config.enabled[SusfsRepository.SWITCH_LOG] == true,
            onCheckedChange = { vm.setSwitch(SusfsRepository.SWITCH_LOG, it) },
        )
        SusfsSwitchRow(
            title = stringResource(R.string.susfs_avc),
            summary = stringResource(R.string.susfs_avc_summary),
            checked = state.config.enabled[SusfsRepository.SWITCH_AVC] == true,
            onCheckedChange = { vm.setSwitch(SusfsRepository.SWITCH_AVC, it) },
        )
        SusfsSwitchRow(
            title = stringResource(R.string.susfs_hide_mnts),
            summary = stringResource(R.string.susfs_hide_mnts_summary),
            checked = state.config.enabled[SusfsRepository.SWITCH_HIDE_MOUNTS] == true,
            onCheckedChange = { vm.setSwitch(SusfsRepository.SWITCH_HIDE_MOUNTS, it) },
        )
    }

    SusfsSectionTitle(stringResource(R.string.susfs_capabilities_title))
    SusfsCard {
        SusfsFeatureCatalog.resolve(state.status.features).forEach { (key, enabled) ->
            SusfsRow(
                title = featureLabel(key),
                trailing = {
                    SusfsPill(
                        text = stringResource(
                            if (enabled) R.string.susfs_feature_enabled else R.string.susfs_feature_disabled,
                        ),
                        highlighted = enabled,
                    )
                },
            )
        }
    }
}

@Composable
private fun PathsPage(state: SusfsUiState, vm: SusfsViewModel, onEdit: (EditorSpec) -> Unit) {
    val pathLabel = stringResource(R.string.susfs_field_path)
    val pathTitle = stringResource(R.string.susfs_add_path)
    val loopTitle = stringResource(R.string.susfs_add_path_loop)
    val mapTitle = stringResource(R.string.susfs_add_map)
    RuleSection(
        title = pathTitle,
        rules = state.config.rules.filterIsInstance<SusfsRule.HiddenPath>(),
        onAdd = {
            onEdit(
                EditorSpec(
                    title = pathTitle,
                    fields = listOf(pathLabel),
                    hints = listOf("/system/app/Example"),
                ) { values -> values.firstOrNull()?.trim()?.takeIf { it.isNotEmpty() }?.let { vm.addRule(SusfsRule.HiddenPath(it)) } },
            )
        },
        onRemove = { vm.removeRule(it) },
    )
    RuleSection(
        title = loopTitle,
        rules = state.config.rules.filterIsInstance<SusfsRule.HiddenLoop>(),
        onAdd = {
            onEdit(
                EditorSpec(
                    title = loopTitle,
                    fields = listOf(pathLabel),
                    hints = listOf("/data/local/tmp/example"),
                ) { values -> values.firstOrNull()?.trim()?.takeIf { it.isNotEmpty() }?.let { vm.addRule(SusfsRule.HiddenLoop(it)) } },
            )
        },
        onRemove = { vm.removeRule(it) },
    )
    RuleSection(
        title = mapTitle,
        rules = state.config.rules.filterIsInstance<SusfsRule.SusMap>(),
        onAdd = {
            onEdit(
                EditorSpec(
                    title = mapTitle,
                    fields = listOf(pathLabel),
                    hints = listOf("/system/fonts"),
                ) { values -> values.firstOrNull()?.trim()?.takeIf { it.isNotEmpty() }?.let { vm.addRule(SusfsRule.SusMap(it)) } },
            )
        },
        onRemove = { vm.removeRule(it) },
    )
}

@Composable
private fun SpoofPage(state: SusfsUiState, vm: SusfsViewModel, onEdit: (EditorSpec) -> Unit) {
    val uname = state.config.rules.filterIsInstance<SusfsRule.Uname>().firstOrNull()
    val cmdline = state.config.rules.filterIsInstance<SusfsRule.Cmdline>().firstOrNull()
    val empty = stringResource(R.string.susfs_empty)
    val releaseLabel = stringResource(R.string.susfs_field_release)
    val versionLabel = stringResource(R.string.susfs_field_version)
    val fileLabel = stringResource(R.string.susfs_field_file)
    val pathLabel = stringResource(R.string.susfs_field_path)
    val unameTitle = stringResource(R.string.susfs_set_uname)
    val cmdlineTitle = stringResource(R.string.susfs_set_cmdline)
    val kstatTitle = stringResource(R.string.susfs_add_kstat)

    SusfsSectionTitle(unameTitle)
    SusfsCard {
        SusfsRow(
            title = unameTitle,
            summary = uname?.let { "${it.release}${if (it.version.isEmpty()) "" else " · ${it.version}"}" } ?: empty,
            onClick = {
                onEdit(
                    EditorSpec(
                        title = unameTitle,
                        fields = listOf(releaseLabel, versionLabel),
                        hints = listOf("6.6.0-abogki", "#1 SMP PREEMPT"),
                    ) { values ->
                        val release = values.getOrNull(0).orEmpty().trim()
                        val version = values.getOrNull(1).orEmpty().trim()
                        if (release.isNotEmpty() || version.isNotEmpty()) {
                            uname?.let { vm.removeRule(it) }
                            vm.addRule(SusfsRule.Uname(release, version))
                        }
                    },
                )
            },
        )
    }

    SusfsSectionTitle(cmdlineTitle)
    SusfsCard {
        SusfsRow(
            title = cmdlineTitle,
            summary = cmdline?.sourcePath ?: empty,
            onClick = {
                onEdit(
                    EditorSpec(
                        title = cmdlineTitle,
                        fields = listOf(fileLabel),
                        hints = listOf("/data/local/tmp/cmdline"),
                    ) { values ->
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
        rules = state.config.rules.filterIsInstance<SusfsRule.Kstat>(),
        onAdd = {
            onEdit(
                EditorSpec(
                    title = kstatTitle,
                    fields = listOf(pathLabel),
                    hints = listOf("/system/fonts/Roboto-Regular.ttf"),
                ) { values -> values.firstOrNull()?.trim()?.takeIf { it.isNotEmpty() }?.let { vm.addRule(SusfsRule.Kstat(it)) } },
            )
        },
        onRemove = { vm.removeRule(it) },
    )
}

@Composable
private fun RedirectPage(state: SusfsUiState, vm: SusfsViewModel, onEdit: (EditorSpec) -> Unit) {
    val targetLabel = stringResource(R.string.susfs_field_target)
    val redirectedLabel = stringResource(R.string.susfs_field_redirected)
    val redirectTitle = stringResource(R.string.susfs_add_redirect)
    RuleSection(
        title = redirectTitle,
        rules = state.config.rules.filterIsInstance<SusfsRule.Redirect>(),
        onAdd = {
            onEdit(
                EditorSpec(
                    title = redirectTitle,
                    fields = listOf(targetLabel, redirectedLabel),
                    hints = listOf("/system/example", "/data/local/tmp/example"),
                ) { values ->
                    val target = values.getOrNull(0).orEmpty().trim()
                    val redirected = values.getOrNull(1).orEmpty().trim()
                    if (target.isNotEmpty() && redirected.isNotEmpty()) {
                        vm.addRule(SusfsRule.Redirect(target, redirected))
                    }
                },
            )
        },
        onRemove = { vm.removeRule(it) },
    )
}

@Composable
private fun ExtensionsPage(
    state: SusfsUiState,
    vm: SusfsViewModel,
    onImport: () -> Unit,
    onExport: () -> Unit,
) {
    SusfsSectionTitle(stringResource(R.string.susfs_config_title))
    SusfsCard {
        SusfsRow(title = stringResource(R.string.susfs_import), onClick = onImport)
        SusfsRow(title = stringResource(R.string.susfs_export), onClick = onExport)
        // 恢复：把 ksud 里上次成功应用的配置读回来，先走导入预览，确认后才覆盖当前配置。
        SusfsRow(
            title = stringResource(R.string.susfs_restore_last),
            summary = stringResource(R.string.susfs_restore_last_summary),
            onClick = { vm.restoreFromKernel() },
        )
        SusfsRow(
            title = stringResource(R.string.susfs_clear_config),
            summary = stringResource(R.string.susfs_clear_config_summary),
            onClick = { vm.clearConfig() },
        )
        SusfsRow(
            title = stringResource(R.string.susfs_rules_count, state.config.rules.size),
            summary = stringResource(R.string.susfs_config_hint),
        )
    }

    SusfsSectionTitle(stringResource(R.string.susfs_proc_title))
    SusfsCard {
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
                    SusfsRow(title = stringResource(res), summary = value.take(200))
                }
            }
        }
    }
}

/** 一组同类规则：标题 + 添加行 + 每条一行（右侧删除）。 */
@Composable
private fun <T : SusfsRule> RuleSection(
    title: String,
    rules: List<T>,
    onAdd: () -> Unit,
    onRemove: (T) -> Unit,
) {
    SusfsSectionTitle(title)
    SusfsCard {
        SusfsRow(title = stringResource(R.string.susfs_add), onClick = onAdd)
        if (rules.isEmpty()) {
            SusfsEmptyHint(stringResource(R.string.susfs_empty))
        } else {
            rules.forEach { rule ->
                // 只有右侧的「删除」可点：整行可点会在滑动列表时误删规则。
                SusfsRow(
                    title = rule.display,
                    trailing = {
                        SusfsTrailingAction(
                            text = stringResource(R.string.susfs_remove),
                            onClick = { onRemove(rule) },
                            danger = true,
                        )
                    },
                )
            }
        }
    }
}

/** 通用输入对话框：1–3 个字段，确认时把值回调出去。 */
@Composable
private fun SusfsEditorDialog(spec: EditorSpec, onDismiss: () -> Unit) {
    var values by remember { mutableStateOf(spec.fields.map { "" }) }
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
