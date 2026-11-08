package me.diksu.ultra.ui.screen.uts

import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.diksu.ultra.R
import me.diksu.ultra.data.uts.UtsConfig
import me.diksu.ultra.data.uts.UtsRepository
import me.diksu.ultra.ksuApp
import me.diksu.ultra.ui.component.AppSearchField
import me.diksu.ultra.ui.component.AppToggleRow
import me.diksu.ultra.ui.component.FeatureActionButton
import me.diksu.ultra.ui.component.FeatureCard
import me.diksu.ultra.ui.component.FeatureMessage
import me.diksu.ultra.ui.component.FeatureProgress
import me.diksu.ultra.ui.component.FeatureRadioRow
import me.diksu.ultra.ui.component.FeatureScaffold
import me.diksu.ultra.ui.component.FeatureSectionTitle
import me.diksu.ultra.ui.component.FeatureText
import me.diksu.ultra.ui.component.FeatureValueRow
import me.diksu.ultra.ui.component.PickedApp
import me.diksu.ultra.ui.component.dialog.AppAlertDialog
import me.diksu.ultra.ui.navigation3.LocalNavigator
import me.diksu.ultra.ui.screen.susfs.SusfsNote
import me.diksu.ultra.ui.screen.susfs.SusfsTextField

private const val MAX_TARGETS = 64
private const val MAX_FIELD_BYTES = 64
private const val MAX_SEARCH_RESULTS = 80

private data class UtsUiState(
    val loading: Boolean = true,
    val busy: Boolean = false,
    val supported: Boolean = false,
    val config: UtsConfig = UtsConfig(),
    val appliedConfig: UtsConfig? = null,
    val apps: List<PickedApp> = emptyList(),
    val query: String = "",
    val message: String? = null,
    val messageIsError: Boolean = false,
) {
    val hasChanges: Boolean get() = appliedConfig != null && config != appliedConfig
}

/** 按 uid 伪装 uname(2)，字段留空表示不改写。 */
class UtsViewModel : ViewModel() {
    private val repository = UtsRepository()
    private val _state = MutableStateFlow(UtsUiState())
    val state = _state.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        if (_state.value.busy) return
        viewModelScope.launch {
            _state.update { it.copy(loading = true, message = null) }
            val remote = withContext(Dispatchers.IO) { repository.read() }
            val apps = withContext(Dispatchers.IO) { loadUserApps() }
            val facts = runCatching {
                val info = android.system.Os.uname()
                listOf(info.sysname, info.nodename, info.release, info.version, info.machine, "")
            }.getOrDefault(List(6) { "" })
            val fallback = UtsConfig(
                sysname = facts[0], nodename = facts[1], release = facts[2],
                version = facts[3], machine = facts[4], domainname = facts[5],
            )
            _state.update {
                it.copy(
                    loading = false,
                    supported = remote != null,
                    config = remote ?: fallback,
                    appliedConfig = remote,
                    apps = apps,
                    message = if (remote == null) {
                        ksuApp.getString(R.string.uts_unsupported_summary)
                    } else {
                        null
                    },
                    messageIsError = remote == null,
                )
            }
        }
    }

    fun update(config: UtsConfig) {
        _state.update {
            if (it.busy || it.loading || !it.supported) it else it.copy(config = config, message = null)
        }
    }

    fun setQuery(query: String) {
        _state.update { it.copy(query = query) }
    }

    fun toggleApp(app: PickedApp, checked: Boolean) {
        val current = _state.value
        if (!current.supported || current.busy || current.loading) return
        val targets = current.config.targetUids
        if (checked && targets.size >= MAX_TARGETS && app.uid !in targets) {
            _state.update {
                it.copy(
                    message = ksuApp.getString(R.string.feature_target_limit, MAX_TARGETS),
                    messageIsError = true,
                )
            }
            return
        }
        _state.update {
            it.copy(
                config = current.config.copy(
                    targetUids = if (checked) {
                        (current.config.targetUids + app.uid).distinct()
                    } else {
                        current.config.targetUids - app.uid
                    },
                ),
                message = null,
            )
        }
    }

    fun apply() {
        val snapshot = _state.value
        if (snapshot.busy || snapshot.loading || !snapshot.supported || !snapshot.hasChanges) return
        viewModelScope.launch {
            _state.update { it.copy(busy = true, message = null, messageIsError = false) }
            val error = repository.write(snapshot.config)
            _state.update {
                it.copy(
                    busy = false,
                    appliedConfig = if (error == null) snapshot.config else it.appliedConfig,
                    message = error ?: ksuApp.getString(R.string.uts_applied),
                    messageIsError = error != null,
                )
            }
        }
    }

    private fun loadUserApps(): List<PickedApp> {
        val pm = ksuApp.packageManager
        return pm.getInstalledApplications(PackageManager.GET_META_DATA)
            .asSequence()
            .filter { (it.flags and ApplicationInfo.FLAG_SYSTEM) == 0 }
            .map { PickedApp(it.packageName, pm.getApplicationLabel(it).toString(), it.uid, it) }
            .sortedBy { it.label.lowercase() }
            .toList()
    }
}

@Composable
fun UtsScreen() {
    val vm: UtsViewModel = viewModel()
    val state by vm.state.collectAsStateWithLifecycle()
    val navigator = LocalNavigator.current
    val title = stringResource(R.string.uts_title)
    val canEdit = state.supported && !state.loading && !state.busy
    val dirty = state.hasChanges
    var confirmBack by remember { mutableStateOf(false) }
    var confirmRefresh by remember { mutableStateOf(false) }
    val selectedCount = state.config.targetUids.size
    val visibleApps = remember(state.apps, state.query, state.config.targetUids) {
        if (state.query.isBlank()) {
            state.apps.filter { state.config.targetUids.contains(it.uid) }
        } else {
            state.apps.filter {
                it.label.contains(state.query, ignoreCase = true) ||
                    it.packageName.contains(state.query, ignoreCase = true)
            }.take(MAX_SEARCH_RESULTS)
        }
    }
    val validationError = when {
        !state.supported -> stringResource(R.string.uts_unsupported_summary)
        state.config.mode != UtsConfig.MODE_DISABLED && state.config.fieldMask == 0 ->
            stringResource(R.string.uts_validation_fields)
        state.config.mode == UtsConfig.MODE_SELECTED && selectedCount == 0 ->
            stringResource(R.string.uts_validation_targets)
        FIELD_TITLES.indices.any { state.config.field(it).toByteArray().size > MAX_FIELD_BYTES } ->
            stringResource(R.string.uts_validation_length)
        else -> null
    }
    val applied = state.appliedConfig ?: state.config

    FeatureScaffold(
        title = title,
        onBack = { if (dirty) confirmBack = true else navigator.pop() },
        onRefresh = { if (dirty) confirmRefresh = true else vm.refresh() },
        refreshEnabled = !state.busy && !state.loading,
    ) { padding ->
        LazyColumn(
            modifier = Modifier.padding(horizontal = 12.dp),
            contentPadding = padding,
        ) {
            item { Spacer(Modifier.height(4.dp)) }
            item { FeatureProgress(state.loading) }
            item {
                FeatureCard {
                    FeatureValueRow(
                        label = stringResource(R.string.uts_kernel_support),
                        value = if (state.supported) {
                            stringResource(R.string.netisolate_kernel_supported)
                        } else {
                            stringResource(R.string.netisolate_kernel_unsupported)
                        },
                    )
                    FeatureValueRow(
                        label = stringResource(R.string.uts_mode),
                        value = modeLabel(applied.mode),
                    )
                    FeatureValueRow(
                        label = stringResource(R.string.uts_targets),
                        value = stringResource(R.string.feature_selected_count, applied.targetUids.size),
                    )
                }
            }
            item {
                FeatureSectionTitle(stringResource(R.string.uts_note_title))
                FeatureCard {
                    SusfsNote(
                        text = stringResource(R.string.uts_note_body),
                        modifier = Modifier.padding(start = 16.dp, top = 12.dp),
                    )
                    SusfsNote(
                        text = stringResource(R.string.uts_note_limit),
                        muted = true,
                        modifier = Modifier.padding(start = 16.dp),
                    )
                }
            }
            item {
                FeatureSectionTitle(stringResource(R.string.uts_mode))
                FeatureCard {
                    FeatureRadioRow(
                        title = stringResource(R.string.uts_mode_off), summary = null,
                        selected = state.config.mode == UtsConfig.MODE_DISABLED, enabled = canEdit,
                        onClick = { vm.update(state.config.copy(mode = UtsConfig.MODE_DISABLED)) },
                    )
                    FeatureRadioRow(
                        title = stringResource(R.string.uts_mode_global),
                        summary = null,
                        selected = state.config.mode == UtsConfig.MODE_GLOBAL,
                        enabled = canEdit,
                        onClick = { vm.update(state.config.copy(mode = UtsConfig.MODE_GLOBAL)) },
                    )
                    FeatureRadioRow(
                        title = stringResource(R.string.uts_mode_selected),
                        summary = null,
                        selected = state.config.mode == UtsConfig.MODE_SELECTED,
                        enabled = canEdit,
                        onClick = { vm.update(state.config.copy(mode = UtsConfig.MODE_SELECTED)) },
                    )
                }
            }
            item {
                FeatureSectionTitle(stringResource(R.string.uts_fields))
                FeatureCard {
                    SusfsNote(
                        text = stringResource(R.string.uts_fields_hint),
                        muted = true,
                        modifier = Modifier.padding(horizontal = 16.dp, top = 12.dp),
                    )
                    FIELD_TITLES.forEachIndexed { index, res ->
                        SusfsTextField(
                            value = state.config.field(index),
                            onValueChange = { vm.update(state.config.withField(index, it)) },
                            label = stringResource(res),
                            enabled = canEdit,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                }
            }
            if (state.config.mode == UtsConfig.MODE_SELECTED) {
                item {
                    FeatureSectionTitle(
                        stringResource(R.string.uts_targets) + "（" +
                            stringResource(R.string.feature_selected_count, selectedCount) + "）",
                    )
                    FeatureCard {
                        AppSearchField(
                            value = state.query,
                            onValueChange = vm::setQuery,
                            label = stringResource(R.string.feature_app_search),
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                        )
                        if (visibleApps.isEmpty()) {
                            FeatureText(
                                text = stringResource(
                                    if (state.query.isBlank()) R.string.feature_app_search_hint
                                    else R.string.feature_app_no_match,
                                ),
                                muted = true,
                                size = 13,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                            )
                        } else {
                            visibleApps.forEach { app ->
                                AppToggleRow(
                                    app = app,
                                    checked = state.config.targetUids.contains(app.uid),
                                    enabled = canEdit,
                                    onCheckedChange = { on -> vm.toggleApp(app, on) },
                                )
                            }
                        }
                        Spacer(Modifier.height(4.dp))
                    }
                }
            }
            item {
                FeatureCard {
                    FeatureActionButton(
                        text = if (state.busy) {
                            stringResource(R.string.feature_applying)
                        } else {
                            stringResource(R.string.uts_apply)
                        },
                        onClick = vm::apply,
                        enabled = canEdit && dirty && validationError == null,
                        modifier = Modifier.padding(8.dp),
                    )
                }
                if (dirty) FeatureMessage(stringResource(R.string.feature_unsaved_changes))
                if (validationError != null) {
                    FeatureMessage(text = validationError, error = true)
                }
                state.message?.let {
                    FeatureMessage(text = it, error = state.messageIsError)
                }
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }

    if (confirmBack) {
        AppAlertDialog(
            title = stringResource(R.string.feature_discard_title),
            onDismissRequest = { confirmBack = false },
            confirmText = stringResource(R.string.feature_discard),
            onConfirm = { confirmBack = false; navigator.pop() },
            dismissText = stringResource(R.string.feature_keep_editing),
        ) {
            FeatureText(text = stringResource(R.string.feature_discard_summary))
        }
    }
    if (confirmRefresh) {
        AppAlertDialog(
            title = stringResource(R.string.feature_discard_title),
            onDismissRequest = { confirmRefresh = false },
            confirmText = stringResource(R.string.feature_refresh),
            onConfirm = { confirmRefresh = false; vm.refresh() },
            dismissText = stringResource(R.string.feature_keep_editing),
        ) {
            FeatureText(text = stringResource(R.string.feature_refresh_warning))
        }
    }
}

private val FIELD_TITLES = listOf(
    R.string.uts_field_sysname,
    R.string.uts_field_nodename,
    R.string.uts_field_release,
    R.string.uts_field_version,
    R.string.uts_field_machine,
    R.string.uts_field_domainname,
)

@Composable
private fun modeLabel(mode: Int): String = when (mode) {
    UtsConfig.MODE_GLOBAL -> stringResource(R.string.uts_mode_global)
    UtsConfig.MODE_SELECTED -> stringResource(R.string.uts_mode_selected)
    else -> stringResource(R.string.uts_mode_off)
}
