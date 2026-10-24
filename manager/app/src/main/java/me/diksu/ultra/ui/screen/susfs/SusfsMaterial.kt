package me.diksu.ultra.ui.screen.susfs

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import me.diksu.ultra.R
import me.diksu.ultra.susfs.SusfsRepository
import me.diksu.ultra.susfs.SusfsUiState
import me.diksu.ultra.susfs.SusfsViewModel
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back

/**
 * SuSFS 面板的 Material 3 实现。
 *
 * 3.x 这里只有一句"请切到 miuix 皮肤"的说明 —— 那是 Material 用户唯一的
 * 入口，等于整个面板不可用。4.0 补齐成与 miuix 皮肤**同等能力**的实现：
 * 状态、特性、三个内核开关、全部添加动作、持久化规则、procfs 快照、日志。
 *
 * 另外补上了 miuix 皮肤当时也没有的"规则清单"：SuSFS 内核侧没有 remove
 * 命令，删除只影响**下次开机重放**，本次开机内规则仍然生效 —— 这一点在卡片
 * 底部直接写明，不假装删除已经立刻生效。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SusfsMaterial(
    state: SusfsUiState,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onInput: (SusfsInput) -> Unit,
    onStaticKstat: () -> Unit,
    viewModel: SusfsViewModel,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("SuSFS") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(MiuixIcons.Back, contentDescription = null)
                    }
                },
                actions = {
                    IconButton(onClick = onRefresh) {
                        Icon(Icons.Filled.Refresh, contentDescription = stringResource(R.string.susfs_refresh))
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { MaterialStatusCard(state) }
            item { MaterialFeaturesCard(state) }
            item { MaterialTogglesCard(state, viewModel) }
            item { MaterialPathsCard(onInput, viewModel) }
            item { MaterialKstatCard(onInput, onStaticKstat, viewModel) }
            item { MaterialRedirectCard(onInput, viewModel) }
            item { MaterialUnameCard(state, onInput, viewModel) }
            item { MaterialRulesCard(state) }
            item { MaterialProcCard(state) }
            item { MaterialLogCard(state) }
        }
    }
}

@Composable
private fun MaterialSectionCard(
    title: String? = null,
    summary: String? = null,
    content: @Composable () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column {
            if (title != null) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(title, style = MaterialTheme.typography.titleSmall)
                    if (summary != null) {
                        Text(
                            text = summary,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            }
            content()
        }
    }
}

@Composable
private fun MaterialActionRow(
    icon: ImageVector,
    title: String,
    summary: String?,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (summary != null) {
                Text(
                    text = summary,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun MaterialStatusCard(state: SusfsUiState) {
    val scheme = MaterialTheme.colorScheme
    val accent = if (state.status.loaded) Color(0xFF2FA84F) else scheme.onSurfaceVariant
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = scheme.surfaceContainer),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(accent.copy(alpha = 0.14f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Filled.Security,
                        contentDescription = null,
                        tint = accent,
                        modifier = Modifier.size(22.dp),
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (state.status.loaded) "已加载" else "未加载",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = when {
                            state.isLoading -> stringResource(R.string.susfs_status_loading)
                            state.status.loaded -> stringResource(
                                R.string.susfs_status_loaded,
                                state.status.version,
                                state.status.variant,
                            )

                            else -> stringResource(R.string.susfs_status_unloaded)
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = scheme.onSurfaceVariant,
                    )
                }
                if (state.status.loaded) {
                    MaterialPill("${state.features.count { it.enabled }} / ${state.features.size}", scheme.primary)
                }
            }
            state.error?.let {
                Spacer(Modifier.height(8.dp))
                Text(it, style = MaterialTheme.typography.bodySmall, color = scheme.error)
            }
        }
    }
}

@Composable
private fun MaterialFeaturesCard(state: SusfsUiState) {
    val scheme = MaterialTheme.colorScheme
    if (state.features.isEmpty()) return
    MaterialSectionCard(
        title = stringResource(R.string.susfs_features_title),
        summary = stringResource(R.string.susfs_features_summary),
    ) {
        state.features.forEach { feature ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(feature.nameRes),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f),
                )
                MaterialPill(
                    text = stringResource(
                        if (feature.enabled) R.string.susfs_feature_enabled else R.string.susfs_feature_disabled
                    ),
                    color = if (feature.enabled) scheme.primary else scheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun MaterialTogglesCard(state: SusfsUiState, viewModel: SusfsViewModel) {
    val scheme = MaterialTheme.colorScheme
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = scheme.surfaceContainer),
    ) {
        Column {
            MaterialSwitchRow(
                icon = Icons.Filled.Info,
                title = stringResource(R.string.susfs_log),
                summary = stringResource(R.string.susfs_log_summary),
                checked = state.logEnabled,
                onCheckedChange = viewModel::setLogEnabled,
            )
            HorizontalDivider(color = scheme.outlineVariant.copy(alpha = 0.5f))
            MaterialSwitchRow(
                icon = Icons.Filled.Security,
                title = stringResource(R.string.susfs_avc),
                summary = stringResource(R.string.susfs_avc_summary),
                checked = state.avcLogSpoofing,
                onCheckedChange = viewModel::setAvcLogSpoofing,
            )
            HorizontalDivider(color = scheme.outlineVariant.copy(alpha = 0.5f))
            MaterialSwitchRow(
                icon = Icons.Filled.VisibilityOff,
                title = stringResource(R.string.susfs_hide_mnts),
                summary = stringResource(R.string.susfs_hide_mnts_summary),
                checked = state.hideSusMntsForNonSuProcs,
                onCheckedChange = viewModel::setHideSusMnts,
            )
        }
    }
}

@Composable
private fun MaterialSwitchRow(
    icon: ImageVector,
    title: String,
    summary: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = summary,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun MaterialPathsCard(onInput: (SusfsInput) -> Unit, viewModel: SusfsViewModel) {
    val scheme = MaterialTheme.colorScheme
    val addPath = stringResource(R.string.susfs_add_path)
    val addPathLoop = stringResource(R.string.susfs_add_path_loop)
    val addMap = stringResource(R.string.susfs_add_map)
    val fieldPath = stringResource(R.string.susfs_field_path)
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = scheme.surfaceContainer),
    ) {
        Column {
            MaterialActionRow(
                icon = Icons.Filled.Add,
                title = addPath,
                summary = stringResource(R.string.susfs_add_path_summary),
                onClick = { onInput(SusfsInput(title = addPath, fieldA = fieldPath) { p, _, _ -> viewModel.addSusPath(p) }) },
            )
            HorizontalDivider(color = scheme.outlineVariant.copy(alpha = 0.5f))
            MaterialActionRow(
                icon = Icons.Filled.Add,
                title = addPathLoop,
                summary = stringResource(R.string.susfs_add_path_loop_summary),
                onClick = { onInput(SusfsInput(title = addPathLoop, fieldA = fieldPath) { p, _, _ -> viewModel.addSusPathLoop(p) }) },
            )
            HorizontalDivider(color = scheme.outlineVariant.copy(alpha = 0.5f))
            MaterialActionRow(
                icon = Icons.Filled.Add,
                title = addMap,
                summary = stringResource(R.string.susfs_add_map_summary),
                onClick = { onInput(SusfsInput(title = addMap, fieldA = fieldPath) { p, _, _ -> viewModel.addSusMap(p) }) },
            )
        }
    }
}

@Composable
private fun MaterialKstatCard(
    onInput: (SusfsInput) -> Unit,
    onStaticKstat: () -> Unit,
    viewModel: SusfsViewModel,
) {
    val scheme = MaterialTheme.colorScheme
    val addKstat = stringResource(R.string.susfs_add_kstat)
    val updateKstat = stringResource(R.string.susfs_update_kstat)
    val fullClone = stringResource(R.string.susfs_update_kstat_full_clone)
    val fieldPath = stringResource(R.string.susfs_field_path)
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = scheme.surfaceContainer),
    ) {
        Column {
            MaterialActionRow(
                icon = Icons.Filled.Tune,
                title = addKstat,
                summary = stringResource(R.string.susfs_add_kstat_summary),
                onClick = { onInput(SusfsInput(title = addKstat, fieldA = fieldPath) { p, _, _ -> viewModel.addSusKstat(p) }) },
            )
            HorizontalDivider(color = scheme.outlineVariant.copy(alpha = 0.5f))
            MaterialActionRow(
                icon = Icons.Filled.Tune,
                title = updateKstat,
                summary = stringResource(R.string.susfs_update_kstat_summary),
                onClick = { onInput(SusfsInput(title = updateKstat, fieldA = fieldPath) { p, _, _ -> viewModel.updateSusKstat(p) }) },
            )
            HorizontalDivider(color = scheme.outlineVariant.copy(alpha = 0.5f))
            MaterialActionRow(
                icon = Icons.Filled.Tune,
                title = fullClone,
                summary = stringResource(R.string.susfs_update_kstat_full_clone_summary),
                onClick = { onInput(SusfsInput(title = fullClone, fieldA = fieldPath) { p, _, _ -> viewModel.updateSusKstatFullClone(p) }) },
            )
            HorizontalDivider(color = scheme.outlineVariant.copy(alpha = 0.5f))
            MaterialActionRow(
                icon = Icons.Filled.Tune,
                title = stringResource(R.string.susfs_add_kstat_static),
                summary = stringResource(R.string.susfs_add_kstat_static_summary),
                onClick = onStaticKstat,
            )
        }
    }
}

@Composable
private fun MaterialRedirectCard(onInput: (SusfsInput) -> Unit, viewModel: SusfsViewModel) {
    val addRedirect = stringResource(R.string.susfs_add_redirect)
    // 注意：stringResource 是 @Composable，只能在组合期调用；不能写进 onClick 里。
    val fieldTarget = stringResource(R.string.susfs_field_target)
    val fieldRedirected = stringResource(R.string.susfs_field_redirected)
    MaterialSectionCard {
        MaterialActionRow(
            icon = Icons.Filled.SwapHoriz,
            title = addRedirect,
            summary = stringResource(R.string.susfs_add_redirect_summary),
            onClick = {
                onInput(
                    SusfsInput(
                        title = addRedirect,
                        fieldA = fieldTarget,
                        fieldB = fieldRedirected,
                        showUidScheme = true,
                    ) { target, redirected, uid ->
                        viewModel.addOpenRedirect(target, redirected, uid)
                    }
                )
            },
        )
    }
}

@Composable
private fun MaterialUnameCard(
    state: SusfsUiState,
    onInput: (SusfsInput) -> Unit,
    viewModel: SusfsViewModel,
) {
    val scheme = MaterialTheme.colorScheme
    val setUname = stringResource(R.string.susfs_set_uname)
    val setCmdline = stringResource(R.string.susfs_set_cmdline)
    val fieldRelease = stringResource(R.string.susfs_field_release)
    val fieldVersion = stringResource(R.string.susfs_field_version)
    val fieldFile = stringResource(R.string.susfs_field_file)
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = scheme.surfaceContainer),
    ) {
        Column {
            MaterialActionRow(
                icon = Icons.Filled.Lock,
                title = setUname,
                summary = state.unameRelease.ifEmpty { stringResource(R.string.susfs_set_uname_summary) },
                onClick = {
                    onInput(
                        SusfsInput(
                            title = setUname,
                            fieldA = fieldRelease,
                            fieldB = fieldVersion,
                            valueA = state.unameRelease,
                            valueB = state.unameVersion,
                        ) { release, version, _ -> viewModel.setUname(release, version) }
                    )
                },
            )
            HorizontalDivider(color = scheme.outlineVariant.copy(alpha = 0.5f))
            MaterialActionRow(
                icon = Icons.Filled.Lock,
                title = setCmdline,
                summary = stringResource(R.string.susfs_set_cmdline_summary),
                onClick = {
                    onInput(
                        SusfsInput(
                            title = setCmdline,
                            fieldA = fieldFile,
                        ) { path, _, _ -> viewModel.setCmdline(path) }
                    )
                },
            )
        }
    }
}

/**
 * 已持久化的规则清单。
 *
 * SuSFS 内核侧没有 remove 命令，所以删除只影响下次开机重放；卡片底部把
 * 这句话直接写出来，避免用户以为规则已经立刻从内核里消失。
 */
@Composable
private fun MaterialRulesCard(state: SusfsUiState) {
    val scheme = MaterialTheme.colorScheme
    // 每次命令结束（lastOutput 变化）或本地删除后重新读一遍仓库。
    var localTick by remember { mutableIntStateOf(0) }
    val paths = remember(state.lastOutput, localTick) { SusfsRepository.susPaths.sorted() }
    val loops = remember(state.lastOutput, localTick) { SusfsRepository.susPathLoops.sorted() }
    val maps = remember(state.lastOutput, localTick) { SusfsRepository.susMaps.sorted() }
    val kstats = remember(state.lastOutput, localTick) { SusfsRepository.susKstatPaths.sorted() }
    val redirects = remember(state.lastOutput, localTick) {
        SusfsRepository.susRedirects.sorted().map { packed ->
            val (target, redirected, uid) = SusfsRepository.unpackRedirect(packed)
            "$target → $redirected (uid $uid)"
        }
    }
    val total = paths.size + loops.size + maps.size + kstats.size + redirects.size
    if (total == 0) return

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = scheme.surfaceContainer),
    ) {
        Column {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("已保存的规则（$total）", style = MaterialTheme.typography.titleSmall)
                Text(
                    text = "开机后自动重放；删除只影响下次开机，本次开机内规则仍然生效",
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
            HorizontalDivider(color = scheme.outlineVariant.copy(alpha = 0.5f))
            MaterialRuleGroup("隐藏路径", paths) {
                SusfsRepository.removeSusPath(it); localTick++
            }
            MaterialRuleGroup("循环隐藏路径", loops) {
                SusfsRepository.removeSusPathLoop(it); localTick++
            }
            MaterialRuleGroup("sus map", maps) {
                SusfsRepository.removeSusMap(it); localTick++
            }
            MaterialRuleGroup("kstat", kstats) {
                SusfsRepository.removeSusKstatPath(it); localTick++
            }
            MaterialRuleGroup("open redirect", redirects) { }
        }
    }
}

@Composable
private fun MaterialRuleGroup(label: String, items: List<String>, onDelete: (String) -> Unit) {
    if (items.isEmpty()) return
    val scheme = MaterialTheme.colorScheme
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = scheme.primary)
        items.forEach { item ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = item,
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    color = scheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f).padding(vertical = 4.dp),
                    maxLines = 2,
                )
                IconButton(onClick = { onDelete(item) }) {
                    Icon(
                        imageVector = Icons.Filled.Delete,
                        contentDescription = "删除",
                        tint = scheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun MaterialProcCard(state: SusfsUiState) {
    val proc = state.proc
    val entries = listOf(
        stringResource(R.string.susfs_proc_path) to proc.path,
        stringResource(R.string.susfs_proc_hide_mounts) to proc.hideMounts,
        stringResource(R.string.susfs_proc_hide_modules) to proc.hideModules,
        stringResource(R.string.susfs_proc_kstat) to proc.kstat,
        stringResource(R.string.susfs_proc_open_redirect) to proc.openRedirect,
        stringResource(R.string.susfs_proc_avc) to proc.avcSpoof,
        stringResource(R.string.susfs_proc_log) to proc.enableLog,
    )
    MaterialSectionCard(title = stringResource(R.string.susfs_proc_title)) {
        SelectionContainer {
            Column(modifier = Modifier.padding(16.dp)) {
                entries.forEach { (label, value) ->
                    Text(
                        text = "$label: ${value.ifEmpty { "—" }}",
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 3,
                        modifier = Modifier
                            .padding(top = 6.dp)
                            .horizontalScroll(rememberScrollState()),
                    )
                }
            }
        }
    }
}

@Composable
private fun MaterialLogCard(state: SusfsUiState) {
    if (state.lastOutput.isBlank()) return
    MaterialSectionCard(title = stringResource(R.string.susfs_log_title)) {
        SelectionContainer {
            Text(
                text = state.lastOutput,
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(16.dp).horizontalScroll(rememberScrollState()),
            )
        }
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
            fontSize = 10.sp,
        )
    }
}
