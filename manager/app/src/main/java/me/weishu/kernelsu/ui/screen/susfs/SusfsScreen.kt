package me.weishu.kernelsu.ui.screen.susfs

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.dropUnlessResumed
import androidx.lifecycle.viewmodel.compose.viewModel
import me.weishu.kernelsu.R
import me.weishu.kernelsu.susfs.SusfsUiState
import me.weishu.kernelsu.susfs.SusfsViewModel
import me.weishu.kernelsu.ui.LocalUiMode
import me.weishu.kernelsu.ui.UiMode
import me.weishu.kernelsu.ui.component.material.SegmentedColumn
import me.weishu.kernelsu.ui.component.material.SegmentedListItem
import me.weishu.kernelsu.ui.component.material.SegmentedSwitchItem
import me.weishu.kernelsu.ui.navigation3.LocalNavigator
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon as MiuixIcon
import top.yukonga.miuix.kmp.basic.IconButton as MiuixIconButton
import top.yukonga.miuix.kmp.basic.Scaffold as MiuixScaffold
import top.yukonga.miuix.kmp.basic.Text as MiuixText
import top.yukonga.miuix.kmp.basic.TopAppBar as MiuixTopAppBar
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme

/**
 * The SuSFS panel.
 *
 * One screen for every skin: the body is built from the shared segmented
 * components and the miuix preferences, which render under all three modes.
 * Material builds get a short note instead of a second implementation —
 * the Material skin is on its way out, and a second copy of this screen
 * would only bit-rot.
 *
 * The screen is a thin shell over [SusfsViewModel]: it renders what the
 * kernel reports and forwards taps, and owns nothing but the small input
 * dialogs.
 */
@Composable
fun SusfsScreen() {
    val navigator = LocalNavigator.current
    val onBack = dropUnlessResumed { navigator.pop() }
    val viewModel = viewModel<SusfsViewModel>()
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) { viewModel.refresh() }

    // The one dialog shape this screen needs: collect two strings and hand
    // them to a callback. Which fields are shown is decided by the caller.
    var pending by remember { mutableStateOf<SusfsInput?>(null) }

    when (LocalUiMode.current) {
        UiMode.Miuix, UiMode.MiuixStock -> SusfsMiuix(
            state = state,
            onBack = onBack,
            onRefresh = viewModel::refresh,
            onInput = { pending = it },
            viewModel = viewModel,
        )

        UiMode.Material -> SusfsMaterialFallback(onBack)
    }

    val input = pending
    if (input != null) {
        SusfsInputDialog(
            input = input,
            onDismiss = { pending = null },
            onConfirm = { a, b ->
                pending = null
                input.onConfirm(a, b)
            },
        )
    }
}

/**
 * A pending two-field prompt.
 *
 * [fieldA]/[fieldB] are the visible labels; the single-field prompts leave
 * [fieldB] null and the dialog hides that line. Keeping it to two strings
 * covers every command the screen runs, so there is one dialog rather than
 * six.
 */
private data class SusfsInput(
    val title: String,
    val fieldA: String,
    val fieldB: String? = null,
    val valueA: String = "",
    val valueB: String = "",
    val onConfirm: (String, String) -> Unit,
)

@Composable
private fun SusfsMiuix(
    state: SusfsUiState,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onInput: (SusfsInput) -> Unit,
    viewModel: SusfsViewModel,
) {
    MiuixScaffold(
        topBar = {
            MiuixTopAppBar(
                title = stringResource(R.string.settings_susfs),
                navigationIcon = {
                    MiuixIconButton(onClick = onBack) {
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
            item { SusfsStatusCard(state, onRefresh) }

            if (state.status.loaded) {
                item { SusfsFeaturesCard(state) }
                item { SusfsSwitchCard(state, viewModel) }
                item { SusfsPathCard(onInput, viewModel) }
                item { SusfsKstatCard(onInput, viewModel) }
                item { SusfsRedirectCard(onInput, viewModel) }
                item { SusfsUnameCard(state, onInput, viewModel) }
            }

            item { SusfsProcCard(state) }
            item { SusfsLogCard(state) }
        }
    }
}

/** Version / variant / feature list, or a note that nothing answered. */
@Composable
private fun SusfsStatusCard(state: SusfsUiState, onRefresh: () -> Unit) {
    Card(modifier = Modifier.padding(top = 12.dp)) {
        Column(modifier = Modifier.padding(16.dp)) {
            MiuixText(
                text = stringResource(R.string.susfs_status_title),
                style = MiuixTheme.textStyles.title2,
                color = colorScheme.onSurface,
            )
            val line = when {
                state.isLoading -> stringResource(R.string.susfs_status_loading)
                state.status.loaded -> stringResource(
                    R.string.susfs_status_loaded,
                    state.status.version,
                    state.status.variant,
                )

                else -> stringResource(R.string.susfs_status_unloaded)
            }
            MiuixText(
                text = line,
                style = MiuixTheme.textStyles.body2,
                color = colorScheme.onSurfaceVariantSummary,
                modifier = Modifier.padding(top = 6.dp),
            )
            if (state.status.features.isNotEmpty()) {
                MiuixText(
                    text = state.status.features.joinToString("  "),
                    style = MiuixTheme.textStyles.footnote1,
                    color = colorScheme.onSurfaceVariantSummary,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
            val error = state.error
            if (error != null) {
                MiuixText(
                    text = error,
                    style = MiuixTheme.textStyles.footnote1,
                    color = colorScheme.error,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
        }
        ArrowPreference(
            title = stringResource(R.string.susfs_refresh),
            startAction = {
                MiuixIcon(
                    imageVector = Icons.Filled.Refresh,
                    contentDescription = null,
                    tint = colorScheme.onBackground,
                    modifier = Modifier.padding(end = 6.dp),
                )
            },
            onClick = onRefresh,
        )
    }
}

/**
 * The kernel's enabled-feature list, one row per known feature.
 *
 * Mirrors the upstream SuSFS panel: every feature the build can have is
 * listed, and the ones the kernel did not report simply read as disabled.
 * That is more useful than hiding them, because "this build has no SUS_MOUNT"
 * is exactly the thing a reader wants to know.
 */
@Composable
private fun SusfsFeaturesCard(state: SusfsUiState) {
    Card(modifier = Modifier.padding(top = 12.dp)) {
        Column(modifier = Modifier.padding(16.dp)) {
            MiuixText(
                text = stringResource(R.string.susfs_features_title),
                style = MiuixTheme.textStyles.title2,
                color = colorScheme.onSurface,
            )
            MiuixText(
                text = stringResource(R.string.susfs_features_summary),
                style = MiuixTheme.textStyles.footnote1,
                color = colorScheme.onSurfaceVariantSummary,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
        for (feature in state.features) {
            val name = stringResource(feature.nameRes)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                MiuixText(
                    text = name,
                    style = MiuixTheme.textStyles.body1,
                    color = colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(
                            if (feature.enabled) colorScheme.primaryContainer
                            else colorScheme.surfaceVariant
                        )
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                ) {
                    MiuixText(
                        text = stringResource(
                            if (feature.enabled) R.string.susfs_feature_enabled
                            else R.string.susfs_feature_disabled
                        ),
                        style = MiuixTheme.textStyles.footnote1,
                        color = if (feature.enabled) colorScheme.onPrimaryContainer
                        else colorScheme.onSurfaceVariantSummary,
                    )
                }
            }
        }
    }
}

/** The three kernel-wide toggles. */
@Composable
private fun SusfsSwitchCard(state: SusfsUiState, viewModel: SusfsViewModel) {
    Card(modifier = Modifier.padding(top = 12.dp)) {
        SwitchPreference(
            title = stringResource(R.string.susfs_log),
            summary = stringResource(R.string.susfs_log_summary),
            startAction = {
                MiuixIcon(
                    imageVector = Icons.Filled.Info,
                    contentDescription = null,
                    tint = colorScheme.onBackground,
                    modifier = Modifier.padding(end = 6.dp),
                )
            },
            checked = state.logEnabled,
            onCheckedChange = viewModel::setLogEnabled,
        )
        SwitchPreference(
            title = stringResource(R.string.susfs_avc),
            summary = stringResource(R.string.susfs_avc_summary),
            startAction = {
                MiuixIcon(
                    imageVector = Icons.Filled.Security,
                    contentDescription = null,
                    tint = colorScheme.onBackground,
                    modifier = Modifier.padding(end = 6.dp),
                )
            },
            checked = state.avcLogSpoofing,
            onCheckedChange = viewModel::setAvcLogSpoofing,
        )
        SwitchPreference(
            title = stringResource(R.string.susfs_hide_mnts),
            summary = stringResource(R.string.susfs_hide_mnts_summary),
            startAction = {
                MiuixIcon(
                    imageVector = Icons.Filled.VisibilityOff,
                    contentDescription = null,
                    tint = colorScheme.onBackground,
                    modifier = Modifier.padding(end = 6.dp),
                )
            },
            checked = state.hideSusMntsForNonSuProcs,
            onCheckedChange = viewModel::setHideSusMnts,
        )
    }
}

/** Hidden-path list. */
@Composable
private fun SusfsPathCard(onInput: (SusfsInput) -> Unit, viewModel: SusfsViewModel) {
    // Resolved here, in composable scope: an onClick lambda is not a @Composable
    // context, so the strings cannot be read inside it.
    val addPath = stringResource(R.string.susfs_add_path)
    val addPathSummary = stringResource(R.string.susfs_add_path_summary)
    val addPathLoop = stringResource(R.string.susfs_add_path_loop)
    val addPathLoopSummary = stringResource(R.string.susfs_add_path_loop_summary)
    val addMap = stringResource(R.string.susfs_add_map)
    val addMapSummary = stringResource(R.string.susfs_add_map_summary)
    val fieldPath = stringResource(R.string.susfs_field_path)
    Card(modifier = Modifier.padding(top = 12.dp)) {
        ArrowPreference(
            title = addPath,
            summary = addPathSummary,
            startAction = {
                MiuixIcon(
                    imageVector = Icons.Filled.Add,
                    contentDescription = null,
                    tint = colorScheme.onBackground,
                    modifier = Modifier.padding(end = 6.dp),
                )
            },
            onClick = {
                onInput(
                    SusfsInput(title = addPath, fieldA = fieldPath) { path, _ ->
                        viewModel.addSusPath(path)
                    },
                )
            },
        )
        ArrowPreference(
            title = addPathLoop,
            summary = addPathLoopSummary,
            startAction = {
                MiuixIcon(
                    imageVector = Icons.Filled.Add,
                    contentDescription = null,
                    tint = colorScheme.onBackground,
                    modifier = Modifier.padding(end = 6.dp),
                )
            },
            onClick = {
                onInput(
                    SusfsInput(title = addPathLoop, fieldA = fieldPath) { path, _ ->
                        viewModel.addSusPathLoop(path)
                    },
                )
            },
        )
        ArrowPreference(
            title = addMap,
            summary = addMapSummary,
            startAction = {
                MiuixIcon(
                    imageVector = Icons.Filled.Add,
                    contentDescription = null,
                    tint = colorScheme.onBackground,
                    modifier = Modifier.padding(end = 6.dp),
                )
            },
            onClick = {
                onInput(
                    SusfsInput(title = addMap, fieldA = fieldPath) { path, _ ->
                        viewModel.addSusMap(path)
                    },
                )
            },
        )
    }
}

/** kstat spoofing. */
@Composable
private fun SusfsKstatCard(onInput: (SusfsInput) -> Unit, viewModel: SusfsViewModel) {
    val addKstat = stringResource(R.string.susfs_add_kstat)
    val addKstatSummary = stringResource(R.string.susfs_add_kstat_summary)
    val updateKstat = stringResource(R.string.susfs_update_kstat)
    val updateKstatSummary = stringResource(R.string.susfs_update_kstat_summary)
    val fieldPath = stringResource(R.string.susfs_field_path)
    Card(modifier = Modifier.padding(top = 12.dp)) {
        ArrowPreference(
            title = addKstat,
            summary = addKstatSummary,
            startAction = {
                MiuixIcon(
                    imageVector = Icons.Filled.Tune,
                    contentDescription = null,
                    tint = colorScheme.onBackground,
                    modifier = Modifier.padding(end = 6.dp),
                )
            },
            onClick = {
                onInput(
                    SusfsInput(title = addKstat, fieldA = fieldPath) { path, _ ->
                        viewModel.addSusKstat(path)
                    },
                )
            },
        )
        ArrowPreference(
            title = updateKstat,
            summary = updateKstatSummary,
            startAction = {
                MiuixIcon(
                    imageVector = Icons.Filled.Tune,
                    contentDescription = null,
                    tint = colorScheme.onBackground,
                    modifier = Modifier.padding(end = 6.dp),
                )
            },
            onClick = {
                onInput(
                    SusfsInput(title = updateKstat, fieldA = fieldPath) { path, _ ->
                        viewModel.updateSusKstat(path)
                    },
                )
            },
        )
    }
}

/** open() redirection. */
@Composable
private fun SusfsRedirectCard(onInput: (SusfsInput) -> Unit, viewModel: SusfsViewModel) {
    val addRedirect = stringResource(R.string.susfs_add_redirect)
    val addRedirectSummary = stringResource(R.string.susfs_add_redirect_summary)
    val fieldTarget = stringResource(R.string.susfs_field_target)
    val fieldRedirected = stringResource(R.string.susfs_field_redirected)
    Card(modifier = Modifier.padding(top = 12.dp)) {
        ArrowPreference(
            title = addRedirect,
            summary = addRedirectSummary,
            startAction = {
                MiuixIcon(
                    imageVector = Icons.Filled.SwapHoriz,
                    contentDescription = null,
                    tint = colorScheme.onBackground,
                    modifier = Modifier.padding(end = 6.dp),
                )
            },
            onClick = {
                onInput(
                    SusfsInput(
                        title = addRedirect,
                        fieldA = fieldTarget,
                        fieldB = fieldRedirected,
                    ) { target, redirected ->
                        viewModel.addOpenRedirect(target, redirected)
                    },
                )
            },
        )
    }
}

/** uname spoofing, showing what is currently set. */
@Composable
private fun SusfsUnameCard(
    state: SusfsUiState,
    onInput: (SusfsInput) -> Unit,
    viewModel: SusfsViewModel,
) {
    val setUname = stringResource(R.string.susfs_set_uname)
    val setUnameSummary = stringResource(R.string.susfs_set_uname_summary)
    val fieldRelease = stringResource(R.string.susfs_field_release)
    val fieldVersion = stringResource(R.string.susfs_field_version)
    val setCmdline = stringResource(R.string.susfs_set_cmdline)
    val setCmdlineSummary = stringResource(R.string.susfs_set_cmdline_summary)
    val fieldFile = stringResource(R.string.susfs_field_file)
    Card(modifier = Modifier.padding(top = 12.dp)) {
        ArrowPreference(
            title = setUname,
            summary = state.unameRelease.ifEmpty { setUnameSummary },
            startAction = {
                MiuixIcon(
                    imageVector = Icons.Filled.Lock,
                    contentDescription = null,
                    tint = colorScheme.onBackground,
                    modifier = Modifier.padding(end = 6.dp),
                )
            },
            onClick = {
                onInput(
                    SusfsInput(
                        title = setUname,
                        fieldA = fieldRelease,
                        fieldB = fieldVersion,
                        valueA = state.unameRelease,
                        valueB = state.unameVersion,
                    ) { release, version ->
                        viewModel.setUname(release, version)
                    },
                )
            },
        )
        ArrowPreference(
            title = setCmdline,
            summary = setCmdlineSummary,
            startAction = {
                MiuixIcon(
                    imageVector = Icons.Filled.Lock,
                    contentDescription = null,
                    tint = colorScheme.onBackground,
                    modifier = Modifier.padding(end = 6.dp),
                )
            },
            onClick = {
                onInput(
                    SusfsInput(title = setCmdline, fieldA = fieldFile) { path, _ ->
                        viewModel.setCmdline(path)
                    },
                )
            },
        )
    }
}

/** The raw `/proc/susfs_*` contents, as the module writes them. */
@Composable
private fun SusfsProcCard(state: SusfsUiState) {
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
    Card(modifier = Modifier.padding(top = 12.dp)) {
        Column(modifier = Modifier.padding(16.dp)) {
            MiuixText(
                text = stringResource(R.string.susfs_proc_title),
                style = MiuixTheme.textStyles.title2,
                color = colorScheme.onSurface,
            )
            for ((label, value) in entries) {
                MiuixText(
                    text = "$label: ${value.ifEmpty { "—" }}",
                    style = MiuixTheme.textStyles.footnote1,
                    fontFamily = FontFamily.Monospace,
                    color = colorScheme.onSurfaceVariantSummary,
                    modifier = Modifier
                        .padding(top = 6.dp)
                        .horizontalScroll(rememberScrollState()),
                    maxLines = 3,
                )
            }
        }
    }
}

/** The last command's output. */
@Composable
private fun SusfsLogCard(state: SusfsUiState) {
    Card(modifier = Modifier.padding(top = 12.dp, bottom = 12.dp)) {
        Column(modifier = Modifier.padding(16.dp)) {
            MiuixText(
                text = stringResource(R.string.susfs_log_title),
                style = MiuixTheme.textStyles.title2,
                color = colorScheme.onSurface,
            )
            SelectionContainer {
                MiuixText(
                    text = state.lastOutput.ifEmpty { stringResource(R.string.susfs_log_idle) },
                    style = MiuixTheme.textStyles.footnote1,
                    fontFamily = FontFamily.Monospace,
                    color = colorScheme.onSurfaceVariantSummary,
                    modifier = Modifier
                        .padding(top = 6.dp)
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                )
            }
        }
    }
}

/**
 * What Material builds see.
 *
 * A one-line note rather than a second copy of the screen: the Material skin
 * is barely used, and maintaining two layouts for the same panel is not
 * worth it. The miuix options are one switch away in settings.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SusfsMaterialFallback(onBack: () -> Unit) {
    Column(modifier = Modifier.padding(16.dp)) {
        SegmentedColumn(
            content = buildList<@Composable () -> Unit> {
                add {
                    SegmentedListItem(
                        onClick = onBack,
                        headlineContent = { Text(stringResource(R.string.susfs_back)) },
                        supportingContent = {
                            Text(stringResource(R.string.susfs_material_note))
                        },
                        leadingContent = { Icon(Icons.Filled.Info, contentDescription = null) },
                    )
                }
            },
        )
    }
}

/** The two-field prompt used by every action on this screen. */
@Composable
private fun SusfsInputDialog(
    input: SusfsInput,
    onDismiss: () -> Unit,
    onConfirm: (String, String) -> Unit,
) {
    var valueA by remember { mutableStateOf(input.valueA) }
    var valueB by remember { mutableStateOf(input.valueB) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(input.title) },
        text = {
            Column {
                OutlinedTextField(
                    value = valueA,
                    onValueChange = { valueA = it },
                    singleLine = true,
                    label = { Text(input.fieldA) },
                    modifier = Modifier.fillMaxWidth(),
                )
                val labelB = input.fieldB
                if (labelB != null) {
                    OutlinedTextField(
                        value = valueB,
                        onValueChange = { valueB = it },
                        singleLine = true,
                        label = { Text(labelB) },
                        modifier = Modifier
                            .padding(top = 12.dp)
                            .fillMaxWidth(),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(valueA, valueB) },
                enabled = valueA.isNotBlank(),
            ) {
                Text(stringResource(R.string.susfs_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.susfs_cancel))
            }
        },
    )
}