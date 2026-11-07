package me.diksu.ultra.ui.screen.uts

import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
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
import me.diksu.ultra.ui.component.material.TopBarBackButton
import me.diksu.ultra.ui.isMiuixFamily
import me.diksu.ultra.ui.navigation3.LocalNavigator
import me.diksu.ultra.ui.screen.susfs.SusfsCard
import me.diksu.ultra.ui.screen.susfs.SusfsRow
import me.diksu.ultra.ui.screen.susfs.SusfsSwitchRow
import me.diksu.ultra.ui.screen.susfs.SusfsText
import me.diksu.ultra.ui.screen.susfs.SusfsTextField
import top.yukonga.miuix.kmp.basic.Icon as MiuixIcon
import top.yukonga.miuix.kmp.basic.IconButton as MiuixIconButton
import top.yukonga.miuix.kmp.basic.Scaffold as MiuixScaffold
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme

/** 一个可选的目标应用。 */
data class UtsApp(val label: String, val uid: Int)

data class UtsUiState(
    val loading: Boolean = true,
    val busy: Boolean = false,
    val config: UtsConfig = UtsConfig(),
    val apps: List<UtsApp> = emptyList(),
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

    fun apply() {
        if (_state.value.busy) return
        viewModelScope.launch {
            _state.update { it.copy(busy = true, message = null) }
            val error = repository.write(_state.value.config)
            _state.update { it.copy(busy = false, message = error ?: "已应用") }
        }
    }

    private fun loadUserApps(): List<UtsApp> {
        val pm = ksuApp.packageManager
        return pm.getInstalledApplications(PackageManager.GET_META_DATA)
            .asSequence()
            .filter { (it.flags and ApplicationInfo.FLAG_SYSTEM) == 0 }
            .map { UtsApp(pm.getApplicationLabel(it).toString(), it.uid) }
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

    val body: @Composable (PaddingValues) -> Unit = { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 12.dp),
        ) {
            SusfsCard(title = stringResource(R.string.uts_note_title)) {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                    SusfsText(text = stringResource(R.string.uts_note_body), size = 13)
                }
            }

            SusfsCard(title = stringResource(R.string.uts_mode)) {
                ModeRow(R.string.uts_mode_off, state.config.mode == UtsConfig.MODE_DISABLED) {
                    vm.update(state.config.copy(mode = UtsConfig.MODE_DISABLED))
                }
                ModeRow(R.string.uts_mode_global, state.config.mode == UtsConfig.MODE_GLOBAL) {
                    vm.update(state.config.copy(mode = UtsConfig.MODE_GLOBAL))
                }
                ModeRow(R.string.uts_mode_selected, state.config.mode == UtsConfig.MODE_SELECTED) {
                    vm.update(state.config.copy(mode = UtsConfig.MODE_SELECTED))
                }
            }

            SusfsCard(title = stringResource(R.string.uts_fields)) {
                FIELD_TITLES.forEachIndexed { index, res ->
                    SusfsTextField(
                        value = state.config.field(index),
                        onValueChange = { vm.update(state.config.withField(index, it)) },
                        label = stringResource(res),
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                    )
                }
            }

            if (state.config.mode == UtsConfig.MODE_SELECTED) {
                SusfsCard(title = stringResource(R.string.uts_targets)) {
                    if (state.apps.isEmpty()) {
                        SusfsRow(title = stringResource(R.string.uts_targets_empty))
                    } else {
                        state.apps.take(60).forEach { app ->
                            val checked = state.config.targetUids.contains(app.uid)
                            SusfsSwitchRow(
                                title = app.label,
                                summary = "uid ${app.uid}",
                                checked = checked,
                                onCheckedChange = { on ->
                                    val next = if (on) {
                                        state.config.targetUids + app.uid
                                    } else {
                                        state.config.targetUids - app.uid
                                    }
                                    vm.update(state.config.copy(targetUids = next.distinct()))
                                },
                            )
                        }
                    }
                }
            }

            SusfsCard {
                SusfsRow(
                    title = stringResource(R.string.uts_apply),
                    onClick = { vm.apply() },
                )
            }
            state.message?.let {
                SusfsText(text = it, size = 13)
            }
            Spacer(Modifier.height(24.dp))
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

@Composable
private fun ModeRow(labelRes: Int, selected: Boolean, onClick: () -> Unit) {
    SusfsRow(
        title = stringResource(labelRes),
        summary = if (selected) stringResource(R.string.uts_mode_current) else null,
        onClick = onClick,
    )
}

private val FIELD_TITLES = listOf(
    R.string.uts_field_sysname,
    R.string.uts_field_nodename,
    R.string.uts_field_release,
    R.string.uts_field_version,
    R.string.uts_field_machine,
    R.string.uts_field_domainname,
)
