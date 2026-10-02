package me.weishu.kernelsu.ui.screen.settings

import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.dropUnlessResumed
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.weishu.kernelsu.Natives
import me.weishu.kernelsu.R
import me.weishu.kernelsu.ui.LocalUiMode
import me.weishu.kernelsu.ui.UiMode
import me.weishu.kernelsu.ui.component.material.SegmentedColumn
import me.weishu.kernelsu.ui.component.material.SegmentedListItem
import me.weishu.kernelsu.ui.component.material.SegmentedSwitchItem
import me.weishu.kernelsu.ui.navigation3.LocalNavigator
import me.weishu.kernelsu.ui.navigation3.Route
import me.weishu.kernelsu.ui.util.CalculatorLauncher
import me.weishu.kernelsu.ui.util.HideAppList
import me.weishu.kernelsu.ui.util.isStealthEnabled
import me.weishu.kernelsu.ui.util.isStealthSupported
import me.weishu.kernelsu.ui.util.setStealthEnabled
import me.weishu.kernelsu.data.repository.isPartitionGuardEnabled
import me.weishu.kernelsu.data.repository.isRuntimeGuardEnabled
import me.weishu.kernelsu.data.repository.setPartitionGuardEnabled
import me.weishu.kernelsu.data.repository.setRuntimeGuardEnabled
import me.weishu.kernelsu.data.repository.syncRuntimeGuardToKernel
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon as MiuixIcon
import top.yukonga.miuix.kmp.basic.IconButton as MiuixIconButton
import top.yukonga.miuix.kmp.basic.Scaffold as MiuixScaffold
import top.yukonga.miuix.kmp.basic.TopAppBar as MiuixTopAppBar
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme

/** The two extras that came over from DikSU: the one-tap Hide My Applist config, and the keyMint panel. */
@Composable
fun OtherFeaturesScreen() {
    val navigator = LocalNavigator.current
    val onBack = dropUnlessResumed { navigator.pop() }
    val onOpenKeymint = dropUnlessResumed { navigator.push(Route.Keymint) }

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    // System-partition guard: forced on (locked) in jailbreak/late-load mode,
    // otherwise it follows the user setting.
    val isLateLoad = Natives.isLateLoadMode
    var guardEnabled by remember { mutableStateOf(isPartitionGuardEnabled()) }
    // The runtime layer is a *child* of the guard switch: it only shows (and
    // only means anything) while the parent protection is on, and it is OFF by
    // default in every mode, including jailbreak. It hooks very hot syscalls
    // (write/writev), so we never turn it on for the user.
    var runtimeGuardEnabled by remember { mutableStateOf(isRuntimeGuardEnabled()) }
    val onGuardChange: (Boolean) -> Unit = { value ->
        setPartitionGuardEnabled(value)
        guardEnabled = isPartitionGuardEnabled()
        // setPartitionGuardEnabled(false) also clears the child; mirror that here.
        runtimeGuardEnabled = isRuntimeGuardEnabled()
    }
    val onRuntimeGuardChange: (Boolean) -> Unit = { value ->
        setRuntimeGuardEnabled(value)
        runtimeGuardEnabled = isRuntimeGuardEnabled()
    }
    // Push the runtime switch to the kernel when (and only when) it changes.
    // The kernel flag is what actually blocks a *live* write from a rooted
    // process, and it is in-memory, so the value has to be re-sent per session.
    //
    // We skip the very first composition: the kernel already reflects this
    // value right after load (ksud replays it from the feature config), and
    // re-sending "ksud feature set ... 0" on every visit would silently undo a
    // value another screen/instance had just set.
    var syncedOnce by remember { mutableStateOf(false) }
    LaunchedEffect(runtimeGuardEnabled) {
        if (!syncedOnce) {
            syncedOnce = true
            return@LaunchedEffect
        }
        withContext(Dispatchers.IO) {
            runCatching { syncRuntimeGuardToKernel(runtimeGuardEnabled) }
        }
    }

    // Kernel stealth mode: with it on, GET_INFO stops reporting the manager and
    // late-load flags, so nothing outside can tell this device is rooted. The
    // switch is only offered when the running kernel knows the command.
    var stealthSupported by remember { mutableStateOf(false) }
    var stealthEnabled by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            stealthSupported = isStealthSupported()
            if (stealthSupported) {
                stealthEnabled = isStealthEnabled()
            }
        }
    }
    val onStealthChange: (Boolean) -> Unit = { value ->
        scope.launch {
            val ok = withContext(Dispatchers.IO) { setStealthEnabled(value) }
            // Re-read rather than trusting the request, so the switch never
            // shows a state the kernel did not accept.
            stealthEnabled = withContext(Dispatchers.IO) { isStealthEnabled() }
            if (!ok) {
                stealthEnabled = false
            }
            // Say where the way back is, once, at the moment it starts to matter. Turning
            // stealth on hides this screen along with the rest of the app, so the reminder
            // has to be given now or not at all. The code itself is not printed: whoever is
            // looking over a shoulder should not learn it from a toast.
            if (stealthEnabled) {
                Toast.makeText(
                    context,
                    R.string.settings_stealth_enabled_toast,
                    Toast.LENGTH_LONG,
                ).show()
            }
        }
    }
    var phase by remember { mutableStateOf<HideAppListPhase?>(null) }

    // Calculator launcher: a small calculator that doubles as the way to open the web UI on
    // demand. The code is asked for first, because it is what the calculator watches for and it
    // is written as part of installing. The install itself can fail (the name may be taken, or
    // the asset may be missing from the build), so the message says which.
    var calcInstalled by remember { mutableStateOf(false) }
    var codeDialogShown by remember { mutableStateOf(false) }
    var codeText by remember { mutableStateOf("") }
    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            calcInstalled = CalculatorLauncher.isInstalled(context)
        }
    }
    val onCalculatorChange: (Boolean) -> Unit = { value ->
        if (value) {
            scope.launch {
                codeText = withContext(Dispatchers.IO) {
                    CalculatorLauncher.readTriggerCode()
                }.ifEmpty { "1234" }
                codeDialogShown = true
            }
        } else {
            scope.launch {
                val installed = withContext(Dispatchers.IO) {
                    CalculatorLauncher.uninstall(context)
                    CalculatorLauncher.isInstalled(context)
                }
                calcInstalled = installed
            }
        }
    }
    val onCalculatorCodeConfirmed: () -> Unit = {
        codeDialogShown = false
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                CalculatorLauncher.setTriggerCode(codeText)
                // install() reports whether the install itself succeeded; isInstalled() is the
                // verdict afterwards. A silent failure here is what made this switch look broken,
                // so both are checked and the reader is told which one failed.
                val reported = CalculatorLauncher.install(context)
                val present = CalculatorLauncher.isInstalled(context)
                reported to present
            }
            val (reported, present) = result
            calcInstalled = present
            if (!present) {
                Toast.makeText(
                    context,
                    if (reported) {
                        R.string.calculator_install_failed
                    } else {
                        R.string.calculator_install_no_apk
                    },
                    Toast.LENGTH_LONG,
                ).show()
            }
        }
    }

    val onHideAppList = { phase = HideAppListPhase.Pick }
    val onRunHideAppList: (Boolean) -> Unit = { scene ->
        phase = HideAppListPhase.Running
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val script = context.assets.open(HideAppList.SCRIPT_ASSET).use {
                        it.readBytes().toString(Charsets.UTF_8)
                    }
                    HideAppList.run(scene, script)
                }
            }
            phase = HideAppListPhase.Done(
                result.getOrElse { it.message ?: context.getString(R.string.hide_applist_failed) }
            )
        }
    }

    when (LocalUiMode.current) {
        UiMode.Material -> OtherFeaturesMaterial(
            onBack = onBack,
            onOpenKeymint = onOpenKeymint,
            onHideAppList = onHideAppList,
            guardEnabled = guardEnabled,
            guardLocked = isLateLoad,
            onGuardChange = onGuardChange,
            runtimeGuardEnabled = runtimeGuardEnabled,
            onRuntimeGuardChange = onRuntimeGuardChange,
            stealthSupported = stealthSupported,
            stealthEnabled = stealthEnabled,
            onStealthChange = onStealthChange,
            calculatorInstalled = calcInstalled,
            onCalculatorChange = onCalculatorChange,
        )
        UiMode.Miuix, UiMode.MiuixStock -> OtherFeaturesMiuix(
            onBack = onBack,
            onOpenKeymint = onOpenKeymint,
            onHideAppList = onHideAppList,
            guardEnabled = guardEnabled,
            guardLocked = isLateLoad,
            onGuardChange = onGuardChange,
            runtimeGuardEnabled = runtimeGuardEnabled,
            onRuntimeGuardChange = onRuntimeGuardChange,
            stealthSupported = stealthSupported,
            stealthEnabled = stealthEnabled,
            onStealthChange = onStealthChange,
            calculatorInstalled = calcInstalled,
            onCalculatorChange = onCalculatorChange,
        )
    }

    val open = phase
    if (open != null) {
        HideAppListDialog(
            phase = open,
            onDismiss = { phase = null },
            onRun = onRunHideAppList,
        )
    }

    if (codeDialogShown) {
        AlertDialog(
            onDismissRequest = { codeDialogShown = false },
            title = { Text(stringResource(R.string.calculator_code_title)) },
            text = {
                Column {
                    Text(stringResource(R.string.calculator_code_summary))
                    OutlinedTextField(
                        value = codeText,
                        onValueChange = { typed -> codeText = typed.filter { it.isDigit() } },
                        singleLine = true,
                        label = { Text(stringResource(R.string.calculator_code_label)) },
                        modifier = Modifier
                            .padding(top = 12.dp)
                            .fillMaxWidth(),
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = onCalculatorCodeConfirmed) {
                    Text("确定")
                }
            },
            dismissButton = {
                TextButton(onClick = { codeDialogShown = false }) {
                    Text("取消")
                }
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun OtherFeaturesMaterial(
    onBack: () -> Unit,
    onOpenKeymint: () -> Unit,
    onHideAppList: () -> Unit,
    guardEnabled: Boolean,
    guardLocked: Boolean,
    onGuardChange: (Boolean) -> Unit,
    runtimeGuardEnabled: Boolean,
    onRuntimeGuardChange: (Boolean) -> Unit,
    stealthSupported: Boolean,
    stealthEnabled: Boolean,
    onStealthChange: (Boolean) -> Unit,
    calculatorInstalled: Boolean,
    onCalculatorChange: (Boolean) -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_other)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .verticalScroll(rememberScrollState()),
        ) {
            SegmentedColumn(
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 13.dp, bottom = 13.dp),
                content = buildList<@Composable () -> Unit> {
                    add {
                        val hide = stringResource(R.string.settings_hide_applist)
                        SegmentedListItem(
                            onClick = onHideAppList,
                            headlineContent = { Text(hide) },
                            supportingContent = {
                                Text(stringResource(R.string.settings_hide_applist_summary))
                            },
                            leadingContent = { Icon(Icons.Filled.VisibilityOff, hide) },
                            trailingContent = {
                                Icon(
                                    Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                    contentDescription = null
                                )
                            },
                        )
                    }
                    add {
                        val keymint = stringResource(R.string.settings_keymint_config)
                        SegmentedListItem(
                            onClick = onOpenKeymint,
                            headlineContent = { Text(keymint) },
                            supportingContent = {
                                Text(stringResource(R.string.settings_keymint_config_summary))
                            },
                            leadingContent = { Icon(Icons.Filled.Security, keymint) },
                            trailingContent = {
                                Icon(
                                    Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                    contentDescription = null
                                )
                            },
                        )
                    }
                    add {
                        val guardTitle = stringResource(R.string.settings_partition_guard)
                        val guardSummary = stringResource(
                            if (guardLocked) R.string.settings_partition_guard_summary_jailbreak
                            else R.string.settings_partition_guard_summary
                        )
                        SegmentedSwitchItem(
                            icon = Icons.Filled.Security,
                            title = guardTitle,
                            summary = guardSummary,
                            enabled = !guardLocked,
                            checked = guardEnabled,
                            onCheckedChange = onGuardChange,
                        )
                    }
                    // Runtime child: only surfaced (and only meaningful) while
                    // the parent guard is on. Off by default in every mode.
                    if (guardEnabled) add {
                        SegmentedSwitchItem(
                            icon = Icons.Filled.Lock,
                            title = stringResource(R.string.settings_runtime_guard),
                            summary = stringResource(R.string.settings_runtime_guard_summary),
                            checked = runtimeGuardEnabled,
                            onCheckedChange = onRuntimeGuardChange,
                        )
                    }
                    // Only offered when the kernel knows the command; an older
                    // kernel would take the switch and do nothing with it.
                    if (stealthSupported) add {
                        SegmentedSwitchItem(
                            icon = Icons.Filled.VisibilityOff,
                            title = stringResource(R.string.settings_stealth),
                            summary = stringResource(R.string.settings_stealth_summary),
                            checked = stealthEnabled,
                            onCheckedChange = onStealthChange,
                        )
                    }
                    add {
                        SegmentedSwitchItem(
                            icon = Icons.Filled.Calculate,
                            title = stringResource(R.string.calculator_title),
                            summary = stringResource(
                                if (calculatorInstalled) R.string.calculator_installed_summary
                                else R.string.calculator_not_installed_summary
                            ),
                            checked = calculatorInstalled,
                            onCheckedChange = onCalculatorChange,
                        )
                    }
                },
            )
        }
    }
}

@Composable
private fun OtherFeaturesMiuix(
    onBack: () -> Unit,
    onOpenKeymint: () -> Unit,
    onHideAppList: () -> Unit,
    guardEnabled: Boolean,
    guardLocked: Boolean,
    onGuardChange: (Boolean) -> Unit,
    runtimeGuardEnabled: Boolean,
    onRuntimeGuardChange: (Boolean) -> Unit,
    stealthSupported: Boolean,
    stealthEnabled: Boolean,
    onStealthChange: (Boolean) -> Unit,
    calculatorInstalled: Boolean,
    onCalculatorChange: (Boolean) -> Unit,
) {
    MiuixScaffold(
        topBar = {
            MiuixTopAppBar(
                title = stringResource(R.string.settings_other),
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
            item {
                Card(modifier = Modifier.padding(top = 12.dp)) {
                    ArrowPreference(
                        title = stringResource(R.string.settings_hide_applist),
                        summary = stringResource(R.string.settings_hide_applist_summary),
                        startAction = {
                            MiuixIcon(
                                imageVector = Icons.Filled.VisibilityOff,
                                contentDescription = null,
                                tint = colorScheme.onBackground,
                                modifier = Modifier.padding(end = 6.dp),
                            )
                        },
                        onClick = onHideAppList,
                    )
                    ArrowPreference(
                        title = stringResource(R.string.settings_keymint_config),
                        summary = stringResource(R.string.settings_keymint_config_summary),
                        startAction = {
                            MiuixIcon(
                                imageVector = Icons.Filled.Security,
                                contentDescription = null,
                                tint = colorScheme.onBackground,
                                modifier = Modifier.padding(end = 6.dp),
                            )
                        },
                        onClick = onOpenKeymint,
                    )
                    SwitchPreference(
                        title = stringResource(R.string.settings_partition_guard),
                        summary = stringResource(
                            if (guardLocked) R.string.settings_partition_guard_summary_jailbreak
                            else R.string.settings_partition_guard_summary
                        ),
                        startAction = {
                            MiuixIcon(
                                imageVector = Icons.Filled.Security,
                                contentDescription = null,
                                tint = colorScheme.onBackground,
                                modifier = Modifier.padding(end = 6.dp),
                            )
                        },
                        enabled = !guardLocked,
                        checked = guardEnabled,
                        onCheckedChange = onGuardChange,
                    )
                    // Runtime child: only surfaced (and only meaningful) while the
                    // parent guard is on. Off by default in every mode.
                    if (guardEnabled) {
                        SwitchPreference(
                            title = stringResource(R.string.settings_runtime_guard),
                            summary = stringResource(R.string.settings_runtime_guard_summary),
                            startAction = {
                                MiuixIcon(
                                    imageVector = Icons.Filled.Lock,
                                    contentDescription = null,
                                    tint = colorScheme.onBackground,
                                    modifier = Modifier.padding(end = 6.dp),
                                )
                            },
                            checked = runtimeGuardEnabled,
                            onCheckedChange = onRuntimeGuardChange,
                        )
                    }
                    // Only offered when the kernel knows the command; an older
                    // kernel would take the switch and do nothing with it.
                    if (stealthSupported) {
                        SwitchPreference(
                            title = stringResource(R.string.settings_stealth),
                            summary = stringResource(R.string.settings_stealth_summary),
                            startAction = {
                                MiuixIcon(
                                    imageVector = Icons.Filled.VisibilityOff,
                                    contentDescription = null,
                                    tint = colorScheme.onBackground,
                                    modifier = Modifier.padding(end = 6.dp),
                                )
                            },
                            checked = stealthEnabled,
                            onCheckedChange = onStealthChange,
                        )
                    }
                    SwitchPreference(
                        title = stringResource(R.string.calculator_title),
                        summary = stringResource(
                            if (calculatorInstalled) R.string.calculator_installed_summary
                            else R.string.calculator_not_installed_summary
                        ),
                        startAction = {
                            MiuixIcon(
                                imageVector = Icons.Filled.Calculate,
                                contentDescription = null,
                                tint = colorScheme.onBackground,
                                modifier = Modifier.padding(end = 6.dp),
                            )
                        },
                        checked = calculatorInstalled,
                        onCheckedChange = onCalculatorChange,
                    )
                }
            }
        }
    }
}
