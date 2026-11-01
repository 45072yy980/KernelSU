package me.diksu.ultra.ui.screen.about

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.diksu.ultra.Natives
import me.diksu.ultra.R
import me.diksu.ultra.ksuApp
import me.diksu.ultra.ui.LocalUiMode
import me.diksu.ultra.ui.UiMode
import me.diksu.ultra.ui.component.material.SegmentedColumn
import me.diksu.ultra.ui.component.material.SegmentedListItem
import me.diksu.ultra.ui.component.material.TopBarBackButton
import me.diksu.ultra.ui.navigation3.LocalNavigator
import me.diksu.ultra.ui.screen.home.getManagerVersion
import me.diksu.ultra.ui.util.KsudProbe
import me.diksu.ultra.ui.util.probeInstalledKsudVersion
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.basic.Text as MiuixText
import top.yukonga.miuix.kmp.basic.TextButton as MiuixTextButton
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme
import top.yukonga.miuix.kmp.basic.Scaffold as MiuixScaffold

/**
 * 三份版本 + 越狱运行自检。
 *
 * 版本区回答"谁是谁"：管理器 APK、真正驻留内核的模块、磁盘上的 ksud。首页状态卡片只显示内核
 * 模块版本，而越狱模式下那个数字不会跟着管理器变——摆在一起，"更新了却没变"才读得出是设计如此。
 *
 * 自检区回答"哪一项能修"：能修的（重装 ksud、重建软链）给按钮，修不了的（内核模块要重新注入）
 * 只如实报告，不假装。
 */
@Immutable
data class VersionDetails(
    val manager: String = "",
    val kernel: String? = null,
    val ksud: String? = null,
    /** 读到了就是来源路径；读不到就是各条路径的失败原因。 */
    val ksudDetail: String? = null,
    /** 另一处候选路径读到的版本（`路径 → 版本`），两处不是同一份时用它对照。 */
    val ksudOthers: List<String> = emptyList(),
    val mode: String = "",
)

@Composable
fun VersionDetailsScreen() {
    var refreshKey by remember { mutableStateOf(0) }
    val details by produceState(initialValue = VersionDetails(), refreshKey) {
        value = withContext(Dispatchers.IO) { readVersionDetails() }
    }
    val checks by produceState(initialValue = emptyList<CheckRow>(), refreshKey) {
        value = runSelfCheck()
    }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val fixOk = stringResource(R.string.check_fix_ok)
    val fixFailed = stringResource(R.string.check_fix_failed)
    val onFix: (CheckFix) -> Unit = { fix ->
        if (!busy) {
            scope.launch {
                busy = true
                val ok = runCatching { runCheckFix(fix) }.getOrDefault(false)
                message = if (ok) fixOk else fixFailed
                busy = false
                refreshKey++
            }
        }
    }

    when (LocalUiMode.current) {
        UiMode.Miuix, UiMode.MiuixStock ->
            VersionDetailsScreenMiuix(details, checks, message, busy, onFix)

        UiMode.Material -> VersionDetailsScreenMaterial(details, checks, message, busy, onFix)
    }
}

/** 只读快照；每次调用都可能走 root（磁盘上的 ksud），所以放在 IO 线程。 */
private fun readVersionDetails(): VersionDetails {
    val managerInfo = getManagerVersion(ksuApp)
    val probe = runCatching { probeInstalledKsudVersion() }
        .getOrElse { KsudProbe(null, "探测本身失败：${it.message ?: it::class.java.simpleName}") }
    val kernelVersion = runCatching { Natives.version }.getOrDefault(0)
    val mode = when {
        runCatching { Natives.isLateLoadMode }.getOrDefault(false) -> MODE_JAILBREAK
        runCatching { Natives.isLkmMode }.getOrDefault(false) -> MODE_LKM
        kernelVersion > 0 -> MODE_BUILTIN
        else -> MODE_NONE
    }
    return VersionDetails(
        manager = "${managerInfo.versionName} (${managerInfo.versionCode})",
        kernel = if (kernelVersion > 0) {
            "$kernelVersion (${Natives.kernelUAPIVersion})"
        } else {
            null
        },
        ksud = probe.version,
        ksudDetail = probe.detail,
        ksudOthers = probe.others,
        mode = mode,
    )
}

private const val MODE_JAILBREAK = "jailbreak"
private const val MODE_LKM = "lkm"
private const val MODE_BUILTIN = "builtin"
private const val MODE_NONE = "none"

@Composable
private fun modeLabel(mode: String): String = when (mode) {
    MODE_JAILBREAK -> stringResource(R.string.jailbreak_mode)
    MODE_LKM -> "LKM"
    MODE_BUILTIN -> stringResource(R.string.version_details_mode_builtin)
    else -> stringResource(R.string.version_details_unknown)
}

@Composable
private fun versionRows(details: VersionDetails): List<Pair<String, String>> {
    val unknown = stringResource(R.string.version_details_unknown)
    return listOf(
        stringResource(R.string.version_details_manager) to details.manager,
        stringResource(R.string.version_details_kernel) to (details.kernel ?: unknown),
        stringResource(R.string.version_details_ksud) to buildString {
            append(details.ksud ?: details.ksudDetail?.take(160)?.let { "$unknown（$it）" } ?: unknown)
            // 两处 ksud 不是同一份时，把另一处也列出来，省得以为读到了错的那份。
            details.ksudOthers.forEach { append("\n").append(it) }
        },
        stringResource(R.string.version_details_mode) to modeLabel(details.mode),
    )
}

@Composable
private fun checkTitle(kind: CheckKind): String = when (kind) {
    CheckKind.Kernel -> stringResource(R.string.check_kernel)
    CheckKind.Ksud -> stringResource(R.string.check_ksud)
    CheckKind.Link -> stringResource(R.string.check_link)
    CheckKind.Uapi -> stringResource(R.string.check_uapi)
    CheckKind.Guard -> stringResource(R.string.check_guard)
    CheckKind.Susfs -> stringResource(R.string.check_susfs)
}

@Composable
private fun statusLabel(status: CheckStatus): String = when (status) {
    CheckStatus.Ok -> stringResource(R.string.check_status_ok)
    CheckStatus.Warn -> stringResource(R.string.check_status_warn)
    CheckStatus.Unknown -> stringResource(R.string.check_status_unknown)
}

@Composable
private fun fixLabel(fix: CheckFix): String = when (fix) {
    CheckFix.ReinstallKsud -> stringResource(R.string.check_fix_reinstall_ksud)
    CheckFix.RebuildLink -> stringResource(R.string.check_fix_rebuild_link)
}

@Composable
private fun VersionDetailsScreenMiuix(
    details: VersionDetails,
    checks: List<CheckRow>,
    message: String?,
    busy: Boolean,
    onFix: (CheckFix) -> Unit,
) {
    val navigator = LocalNavigator.current
    val rows = versionRows(details)
    MiuixScaffold(
        topBar = {
            SmallTopAppBar(
                title = stringResource(id = R.string.version_details_title),
                navigationIcon = {
                    IconButton(onClick = { navigator.pop() }) {
                        Icon(
                            imageVector = MiuixIcons.Back,
                            contentDescription = null,
                            tint = colorScheme.onBackground,
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 12.dp),
        ) {
            item {
                SmallTitle(
                    text = stringResource(R.string.version_details_section),
                    modifier = Modifier.padding(top = 12.dp),
                )
                Card(modifier = Modifier.padding(top = 12.dp).fillMaxWidth()) {
                    rows.forEach { (label, value) ->
                        BasicComponent(title = label, summary = value)
                    }
                }
            }

            item {
                SmallTitle(
                    text = stringResource(R.string.check_section_title),
                    modifier = Modifier.padding(top = 12.dp),
                )
                Card(modifier = Modifier.padding(top = 12.dp).fillMaxWidth()) {
                    checks.forEach { row ->
                        BasicComponent(
                            title = checkTitle(row.kind),
                            summary = "${statusLabel(row.status)} · ${row.detail}",
                            endActions = {
                                row.fix?.let { fix ->
                                    MiuixTextButton(
                                        text = fixLabel(fix),
                                        onClick = { if (!busy) onFix(fix) },
                                    )
                                }
                            },
                        )
                    }
                }
                message?.let {
                    MiuixText(
                        text = it,
                        color = colorScheme.primary,
                        style = MiuixTheme.textStyles.footnote1,
                        modifier = Modifier.padding(top = 8.dp, start = 4.dp),
                    )
                }
                MiuixText(
                    text = stringResource(R.string.version_details_note),
                    color = colorScheme.onSurfaceVariantSummary,
                    style = MiuixTheme.textStyles.footnote1,
                    modifier = Modifier.padding(top = 8.dp, start = 4.dp),
                )
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun VersionDetailsScreenMaterial(
    details: VersionDetails,
    checks: List<CheckRow>,
    message: String?,
    busy: Boolean,
    onFix: (CheckFix) -> Unit,
) {
    val navigator = LocalNavigator.current
    val rows = versionRows(details)
    androidx.compose.material3.Scaffold(
        topBar = {
            androidx.compose.material3.TopAppBar(
                title = { Text(stringResource(id = R.string.version_details_title)) },
                navigationIcon = { TopBarBackButton(onClick = { navigator.pop() }) },
            )
        },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
        ) {
            item {
                SegmentedColumn(
                    modifier = Modifier.padding(top = 12.dp),
                    title = stringResource(R.string.version_details_section),
                    content = buildList<@Composable () -> Unit> {
                        rows.forEach { (label, value) ->
                            add {
                                SegmentedListItem(
                                    headlineContent = { Text(label) },
                                    supportingContent = {
                                        Text(text = value, fontSize = 13.sp)
                                    },
                                )
                            }
                        }
                    },
                )
            }

            item {
                SegmentedColumn(
                    modifier = Modifier.padding(top = 12.dp),
                    title = stringResource(R.string.check_section_title),
                    content = buildList<@Composable () -> Unit> {
                        checks.forEach { row ->
                            add {
                                SegmentedListItem(
                                    headlineContent = { Text(checkTitle(row.kind)) },
                                    supportingContent = {
                                        Text(
                                            text = "${statusLabel(row.status)} · ${row.detail}",
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    },
                                    trailingContent = {
                                        row.fix?.let { fix ->
                                            TextButton(onClick = { if (!busy) onFix(fix) }) {
                                                Text(fixLabel(fix))
                                            }
                                        }
                                    },
                                )
                            }
                        }
                    },
                )
                message?.let {
                    Text(
                        text = it,
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 8.dp, start = 4.dp),
                    )
                }
                Text(
                    text = stringResource(R.string.version_details_note),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 8.dp, start = 4.dp, bottom = 24.dp),
                )
            }
        }
    }
}
