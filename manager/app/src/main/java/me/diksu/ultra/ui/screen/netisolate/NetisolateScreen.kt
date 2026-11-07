package me.diksu.ultra.ui.screen.netisolate

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
import androidx.compose.runtime.getValue
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
import me.diksu.ultra.data.netisolate.NetisolateConfig
import me.diksu.ultra.data.netisolate.NetisolateRepository
import me.diksu.ultra.ksuApp
import me.diksu.ultra.ui.LocalUiMode
import me.diksu.ultra.ui.component.material.TopBarBackButton
import me.diksu.ultra.ui.isMiuixFamily
import me.diksu.ultra.ui.navigation3.LocalNavigator
import me.diksu.ultra.ui.screen.susfs.SusfsCard
import me.diksu.ultra.ui.screen.susfs.SusfsNote
import me.diksu.ultra.ui.screen.susfs.SusfsRow
import me.diksu.ultra.ui.screen.susfs.SusfsSwitchRow
import me.diksu.ultra.ui.screen.susfs.SusfsText
import top.yukonga.miuix.kmp.basic.Icon as MiuixIcon
import top.yukonga.miuix.kmp.basic.IconButton as MiuixIconButton
import top.yukonga.miuix.kmp.basic.Scaffold as MiuixScaffold
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme

/** 一个可选的目标应用。 */
data class NetisolateApp(val label: String, val uid: Int)

data class NetisolateUiState(
    val loading: Boolean = true,
    val busy: Boolean = false,
    val config: NetisolateConfig = NetisolateConfig(),
    val apps: List<NetisolateApp> = emptyList(),
    val message: String? = null,
)

/**
 * 网络隔离页面：让选中的应用连不上外网。
 *
 * 内核侧只拦 `connect(2)`，所以页面要把两件事说清楚：系统应用不在名单里（会把手机
 * 自己断网），以及不 connect 直接 sendto 的 UDP 拦不住。
 */
class NetisolateViewModel : ViewModel() {
    private val repository = NetisolateRepository()
    private val _state = MutableStateFlow(NetisolateUiState())
    val state = _state.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _state.update { it.copy(loading = true) }
            val remote = withContext(Dispatchers.IO) { repository.read() }
            val apps = withContext(Dispatchers.IO) { loadUserApps() }
            // 读不到（老内核没有这个 ioctl）就别假装支持：supported=false 会显示提示条，
            // 免得用户以为能开、点「应用配置」才吃一个报错。
            val config = remote ?: NetisolateConfig(supported = false)
            _state.update { it.copy(loading = false, config = config, apps = apps) }
        }
    }

    fun update(config: NetisolateConfig) {
        _state.update { it.copy(config = config) }
    }

    fun apply() {
        if (_state.value.busy) return
        viewModelScope.launch {
            _state.update { it.copy(busy = true, message = null) }
            val error = repository.write(_state.value.config)
            _state.update {
                it.copy(
                    busy = false,
                    message = error ?: ksuApp.getString(R.string.netisolate_applied),
                )
            }
        }
    }

    private fun loadUserApps(): List<NetisolateApp> {
        val pm = ksuApp.packageManager
        return pm.getInstalledApplications(PackageManager.GET_META_DATA)
            .asSequence()
            .filter { (it.flags and ApplicationInfo.FLAG_SYSTEM) == 0 }
            .map { NetisolateApp(pm.getApplicationLabel(it).toString(), it.uid) }
            .sortedBy { it.label.lowercase() }
            .toList()
    }
}

@Composable
fun NetisolateScreen() {
    val vm: NetisolateViewModel = viewModel()
    val state by vm.state.collectAsStateWithLifecycle()
    val navigator = LocalNavigator.current
    val title = stringResource(R.string.netisolate_title)

    val body: @Composable (PaddingValues) -> Unit = { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 12.dp),
        ) {
            SusfsCard(title = stringResource(R.string.netisolate_note_title)) {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
                    SusfsNote(text = stringResource(R.string.netisolate_note_body))
                    SusfsNote(text = stringResource(R.string.netisolate_note_limit), muted = true)
                }
            }

            if (!state.config.supported) {
                SusfsCard {
                    SusfsRow(
                        title = stringResource(R.string.netisolate_unsupported),
                        summary = stringResource(R.string.netisolate_unsupported_summary),
                    )
                }
            }

            SusfsCard(title = stringResource(R.string.netisolate_mode)) {
                ModeRow(R.string.netisolate_mode_off, state.config.mode == NetisolateConfig.MODE_DISABLED) {
                    vm.update(state.config.copy(mode = NetisolateConfig.MODE_DISABLED))
                }
                ModeRow(R.string.netisolate_mode_selected, state.config.mode == NetisolateConfig.MODE_SELECTED) {
                    vm.update(state.config.copy(mode = NetisolateConfig.MODE_SELECTED))
                }
            }

            SusfsCard(title = stringResource(R.string.netisolate_options)) {
                SusfsSwitchRow(
                    title = stringResource(R.string.netisolate_allow_loopback),
                    summary = stringResource(R.string.netisolate_allow_loopback_summary),
                    checked = state.config.allowLoopback,
                    onCheckedChange = { on -> vm.update(state.config.withFlag(NetisolateConfig.FLAG_ALLOW_LOOPBACK, on)) },
                )
                SusfsSwitchRow(
                    title = stringResource(R.string.netisolate_allow_private),
                    summary = stringResource(R.string.netisolate_allow_private_summary),
                    checked = state.config.allowPrivate,
                    onCheckedChange = { on -> vm.update(state.config.withFlag(NetisolateConfig.FLAG_ALLOW_PRIVATE, on)) },
                )
            }

            if (state.config.mode == NetisolateConfig.MODE_SELECTED) {
                SusfsCard(title = stringResource(R.string.netisolate_targets)) {
                    if (state.apps.isEmpty()) {
                        SusfsRow(title = stringResource(R.string.netisolate_targets_empty))
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
                    title = stringResource(R.string.netisolate_apply),
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
        summary = if (selected) stringResource(R.string.netisolate_mode_current) else null,
        onClick = onClick,
    )
}
