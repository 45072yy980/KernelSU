package me.diksu.ultra.ui.screen.uts

import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
import me.diksu.ultra.ui.LocalUiMode
import me.diksu.ultra.ui.component.AppSearchField
import me.diksu.ultra.ui.component.AppToggleRow
import me.diksu.ultra.ui.component.FeatureCard
import me.diksu.ultra.ui.component.FeatureRadioRow
import me.diksu.ultra.ui.component.FeatureSectionTitle
import me.diksu.ultra.ui.component.FeatureText
import me.diksu.ultra.ui.component.FeatureValueRow
import me.diksu.ultra.ui.component.PickedApp
import me.diksu.ultra.ui.component.material.TopBarBackButton
import me.diksu.ultra.ui.isMiuixFamily
import me.diksu.ultra.ui.navigation3.LocalNavigator
import me.diksu.ultra.ui.screen.susfs.SusfsNote
import me.diksu.ultra.ui.screen.susfs.SusfsRow
import me.diksu.ultra.ui.screen.susfs.SusfsText
import me.diksu.ultra.ui.screen.susfs.SusfsTextField
import top.yukonga.miuix.kmp.basic.Icon as MiuixIcon
import top.yukonga.miuix.kmp.basic.IconButton as MiuixIconButton
import top.yukonga.miuix.kmp.basic.Scaffold as MiuixScaffold
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme

data class UtsUiState(
    val loading: Boolean = true,
    val busy: Boolean = false,
    val config: UtsConfig = UtsConfig(),
    val apps: List<PickedApp> = emptyList(),
    val query: String = "",
    val message: String? = null,
)

/**
 * UTS 页面：按 uid 伪装 `uname(2)`。
 *
 * 三个模式：关闭 / 全局 / 仅指定应用。字段留空表示不改写该字段（内核按 field_mask 处理），
 * 首次打开时用当前内核值预填，用户只改想改的那几项。
 */
class UtsViewModel : ViewModel() {
    private val repository = UtsRepository()
    private val _state = MutableStateFlow(UtsUiState())
    val state = _state.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _state.update { it.copy(loading = true) }
            val remote = withContext(Dispatchers.IO) { repository.read() }
            val apps = withContext(Dispatchers.IO) { loadUserApps() }
            val kernelFacts = runCatching {
                val info = android.system.Os.uname()
                // StructUtsname 在 Android 上没有 domainname 字段，留空
                listOf(info.sysname, info.nodename, info.release, info.version, info.machine, "")
            }.getOrDefault(List(6) { "" })
            val base = remote ?: UtsConfig(
                sysname = kernelFacts[0],
                nodename = kernelFacts[1],
                release = kernelFacts[2],
                version = kernelFacts[3],
                machine = kernelFacts[4],
                domainname = kernelFacts[5],
            )
            _state.update { it.copy(loading = false, config = base, apps = apps) }
        }
    }

    fun update(config: UtsConfig) {
        _state.update { it.copy(config = config) }
    }

    fun setQuery(query: String) {
        _state.update { it.copy(query = query) }
    }

    fun apply() {
        if (_state.value.busy) return
        viewModelScope.launch {
            _state.update { it.copy(busy = true, message = null) }
            val error = repository.write(_state.value.config)
            _state.update { it.copy(busy = false, message = error ?: "已应用") }
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

    val selectedCount = state.config.targetUids.size
    val visibleApps = remember(state.apps, state.query, state.config.targetUids) {
        if (state.query.isBlank()) {
            state.apps.filter { state.config.targetUids.contains(it.uid) }
        } else {
            state.apps
                .filter {
                    it.label.contains(state.query, ignoreCase = true) ||
                        it.packageName.contains(state.query, ignoreCase = true)
                }
                .take(80)
        }
    }

    val body: @Composable (PaddingValues) -> Unit = { padding ->
        LazyColumn(
            modifier = Modifier.padding(horizontal = 12.dp),
            contentPadding = padding,
        ) {
            item { Spacer(Modifier.height(4.dp)) }

            item {
                FeatureCard {
                    FeatureValueRow(
                        label = stringResource(R.string.uts_mode),
                        value = when (state.config.mode) {
                            UtsConfig.MODE_GLOBAL -> stringResource(R.string.uts_mode_global)
                            UtsConfig.MODE_SELECTED -> stringResource(R.string.uts_mode_selected)
                            else -> stringResource(R.string.uts_mode_off)
                        },
                    )
                    FeatureValueRow(
                        label = stringResource(R.string.uts_targets),
                        value = stringResource(R.string.feature_selected_count, selectedCount),
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
                FeatureCard() {
                    FeatureRadioRow(
                        title = stringResource(R.string.uts_mode_off),
                        summary = null,
                        selected = state.config.mode == UtsConfig.MODE_DISABLED,
                        onClick = { vm.update(state.config.copy(mode = UtsConfig.MODE_DISABLED)) },
                    )
                    FeatureRadioRow(
                        title = stringResource(R.string.uts_mode_global),
                        summary = null,
                        selected = state.config.mode == UtsConfig.MODE_GLOBAL,
                        onClick = { vm.update(state.config.copy(mode = UtsConfig.MODE_GLOBAL)) },
                    )
                    FeatureRadioRow(
                        title = stringResource(R.string.uts_mode_selected),
                        summary = null,
                        selected = state.config.mode == UtsConfig.MODE_SELECTED,
                        onClick = { vm.update(state.config.copy(mode = UtsConfig.MODE_SELECTED)) },
                    )
                }
            }

            item {
                FeatureSectionTitle(stringResource(R.string.uts_fields))
                FeatureCard() {
                    FIELD_TITLES.forEachIndexed { index, res ->
                        SusfsTextField(
                            value = state.config.field(index),
                            onValueChange = { vm.update(state.config.withField(index, it)) },
                            label = stringResource(res),
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
                    FeatureCard() {
                        AppSearchField(
                            value = state.query,
                            onValueChange = vm::setQuery,
                            label = stringResource(R.string.feature_app_search),
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                        )
                        if (visibleApps.isEmpty()) {
                            FeatureText(
                                text = stringResource(
                                    if (state.query.isBlank()) {
                                        R.string.feature_app_search_hint
                                    } else {
                                        R.string.feature_app_no_match
                                    },
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
                                    onCheckedChange = { on ->
                                        val next = if (on) {
                                            // 内核侧上限 64，超了整份配置都会被拒；这里直接截住。
                                            if (state.config.targetUids.size >= MAX_TARGETS) {
                                                state.config.targetUids
                                            } else {
                                                state.config.targetUids + app.uid
                                            }
                                        } else {
                                            state.config.targetUids - app.uid
                                        }
                                        vm.update(state.config.copy(targetUids = next.distinct()))
                                    },
                                )
                            }
                        }
                        Spacer(Modifier.height(4.dp))
                    }
                }
            }

            item {
                FeatureCard {
                    SusfsRow(
                        title = stringResource(R.string.uts_apply),
                        onClick = { vm.apply() },
                    )
                }
                state.message?.let {
                    SusfsText(text = it, size = 13, modifier = Modifier.padding(start = 16.dp, top = 8.dp))
                }
            }

            item { Spacer(Modifier.height(24.dp)) }
        }
    }

    if (LocalUiMode.current.isMiuixFamily) {
        MiuixScaffold(
            topBar = {
                SmallTopAppBar(
                    title = title,
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
        ) { padding -> body(padding) }
    } else {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(title) },
                    navigationIcon = { TopBarBackButton(onClick = { navigator.pop() }) },
                )
            },
        ) { padding -> body(padding) }
    }
}

/** 与 kernel/include/uapi/uts_view.h 的 KSU_UTS_MAX_TARGETS 保持一致。 */
private const val MAX_TARGETS = 64

private val FIELD_TITLES = listOf(
    R.string.uts_field_sysname,
    R.string.uts_field_nodename,
    R.string.uts_field_release,
    R.string.uts_field_version,
    R.string.uts_field_machine,
    R.string.uts_field_domainname,
)
