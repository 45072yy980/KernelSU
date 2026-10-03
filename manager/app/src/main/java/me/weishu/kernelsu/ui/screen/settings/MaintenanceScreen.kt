package me.weishu.kernelsu.ui.screen.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.weishu.kernelsu.R
import me.weishu.kernelsu.ui.LocalUiMode
import me.weishu.kernelsu.ui.UiMode
import me.weishu.kernelsu.ui.component.material.SegmentedColumn
import me.weishu.kernelsu.ui.component.material.SegmentedListItem
import me.weishu.kernelsu.ui.navigation3.LocalNavigator
import me.weishu.kernelsu.ui.util.isSoftRebootSupported
import me.weishu.kernelsu.ui.util.isStealthSupported
import me.weishu.kernelsu.ui.util.reboot
import me.weishu.kernelsu.ui.util.reloadModules
import me.weishu.kernelsu.ui.util.setStealthEnabled
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon as MiuixIcon
import top.yukonga.miuix.kmp.basic.IconButton as MiuixIconButton
import top.yukonga.miuix.kmp.basic.Scaffold as MiuixScaffold
import top.yukonga.miuix.kmp.basic.TopAppBar as MiuixTopAppBar
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme

/** A single line in the on-screen log. */
private data class LogLine(val text: String, val kind: Kind) {
    enum class Kind { INFO, OK, WARN, ERROR }
}

/**
 * Maintenance tools, the part of an installation that deals with the device rather than with the
 * app: reloading module scripts, and the two kinds of reboot an emulated restart offers.
 *
 * These are the operations a shell module usually ships its own web UI for. Here they run through
 * ksud directly, so nothing has to be installed from a third party to reach them, and the screen
 * follows whichever of the three skins is selected rather than being Material-only.
 */
@Composable
fun MaintenanceScreen() {
    val navigator = LocalNavigator.current
    val onBack = { navigator.pop() }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var stealthSupported by remember { mutableStateOf(false) }
    var log by remember { mutableStateOf(listOf<LogLine>()) }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            stealthSupported = isStealthSupported()
            // Read once so the kernel's answer is known before the button is pressed. The result is
            // not used for anything here; the command itself still refuses on an old kernel.
            runCatching { isSoftRebootSupported() }
        }
    }

    fun append(text: String, kind: LogLine.Kind = LogLine.Kind.INFO) {
        log = log + LogLine(text, kind)
    }

    // Runs the three ksud boot stages. Reported as a count rather than a bare failure, because a
    // module whose boot-completed script fails still had its earlier scripts run, and saying which
    // ones completed is more useful than a single red line.
    val onReloadModules: () -> Unit = {
        scope.launch {
            append(context.getString(R.string.maintenance_log_reload_start), LogLine.Kind.INFO)
            val ok = withContext(Dispatchers.IO) { reloadModules() }
            if (ok == 3) {
                append(context.getString(R.string.maintenance_log_reload_ok), LogLine.Kind.OK)
            } else {
                append(context.getString(R.string.maintenance_log_reload_partial, ok), LogLine.Kind.WARN)
            }
        }
    }

    // Plain soft reboot: ksud emulates the whole restart (stop, post-fs-data, start, services,
    // boot-completed) so a module installed since boot is picked up without touching the device.
    val onSoftReboot: () -> Unit = {
        scope.launch {
            append(context.getString(R.string.maintenance_log_soft_start), LogLine.Kind.INFO)
            append(context.getString(R.string.maintenance_log_soft_note), LogLine.Kind.INFO)
            delay(1500)
            withContext(Dispatchers.IO) { reboot("soft_reboot") }
        }
    }

    // Advanced: turn stealth on first, so the reboot comes back with the manager hidden. The
    // switch is applied to the kernel immediately, which is why it is written before the restart
    // rather than as part of it.
    val onSoftRebootHidden: () -> Unit = {
        scope.launch {
            append(context.getString(R.string.maintenance_log_hidden_start), LogLine.Kind.INFO)
            if (stealthSupported) {
                val on = withContext(Dispatchers.IO) { setStealthEnabled(true) }
                if (on) {
                    append(context.getString(R.string.maintenance_log_hidden_on), LogLine.Kind.OK)
                } else {
                    append(context.getString(R.string.maintenance_log_hidden_fail), LogLine.Kind.WARN)
                }
            } else {
                append(
                    context.getString(R.string.maintenance_log_hidden_unsupported),
                    LogLine.Kind.WARN,
                )
            }
            delay(1500)
            withContext(Dispatchers.IO) { reboot("soft_reboot") }
        }
    }

    when (LocalUiMode.current) {
        UiMode.Material -> MaintenanceMaterial(
            onBack = onBack,
            log = log,
            onReloadModules = onReloadModules,
            onSoftReboot = onSoftReboot,
            onSoftRebootHidden = onSoftRebootHidden,
            stealthSupported = stealthSupported,
        )
        UiMode.Miuix, UiMode.MiuixStock -> MaintenanceMiuix(
            onBack = onBack,
            log = log,
            onReloadModules = onReloadModules,
            onSoftReboot = onSoftReboot,
            onSoftRebootHidden = onSoftRebootHidden,
            stealthSupported = stealthSupported,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MaintenanceMaterial(
    onBack: () -> Unit,
    log: List<LogLine>,
    onReloadModules: () -> Unit,
    onSoftReboot: () -> Unit,
    onSoftRebootHidden: () -> Unit,
    stealthSupported: Boolean,
) {
    var confirming by remember { mutableStateOf<(() -> Unit)?>(null) }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_maintenance)) },
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
                        val title = stringResource(R.string.maintenance_reload_modules)
                        SegmentedListItem(
                            onClick = onReloadModules,
                            headlineContent = { Text(title) },
                            supportingContent = {
                                Text(stringResource(R.string.maintenance_reload_modules_summary))
                            },
                            leadingContent = { Icon(Icons.Filled.Refresh, null) },
                        )
                    }
                    add {
                        val title = stringResource(R.string.maintenance_soft_reboot)
                        SegmentedListItem(
                            onClick = { confirming = onSoftReboot },
                            headlineContent = { Text(title) },
                            supportingContent = {
                                Text(stringResource(R.string.maintenance_soft_reboot_summary))
                            },
                            leadingContent = { Icon(Icons.Filled.RestartAlt, null) },
                        )
                    }
                    add {
                        val title = stringResource(R.string.maintenance_soft_reboot_hidden)
                        SegmentedListItem(
                            onClick = { confirming = onSoftRebootHidden },
                            headlineContent = { Text(title) },
                            supportingContent = {
                                Text(
                                    stringResource(
                                        if (stealthSupported) R.string.maintenance_soft_reboot_hidden_summary
                                        else R.string.maintenance_soft_reboot_hidden_summary_unsupported
                                    )
                                )
                            },
                            leadingContent = { Icon(Icons.Filled.VisibilityOff, null) },
                        )
                    }
                },
            )
            LogPanel(log)
        }
    }
    ConfirmDialog(confirming) { confirming = null }
}

@Composable
private fun MaintenanceMiuix(
    onBack: () -> Unit,
    log: List<LogLine>,
    onReloadModules: () -> Unit,
    onSoftReboot: () -> Unit,
    onSoftRebootHidden: () -> Unit,
    stealthSupported: Boolean,
) {
    var confirming by remember { mutableStateOf<(() -> Unit)?>(null) }
    MiuixScaffold(
        topBar = {
            MiuixTopAppBar(
                title = stringResource(R.string.settings_maintenance),
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
                        title = stringResource(R.string.maintenance_reload_modules),
                        summary = stringResource(R.string.maintenance_reload_modules_summary),
                        startAction = {
                            MiuixIcon(
                                imageVector = Icons.Filled.Refresh,
                                contentDescription = null,
                                tint = colorScheme.onBackground,
                                modifier = Modifier.padding(end = 6.dp),
                            )
                        },
                        onClick = onReloadModules,
                    )
                    ArrowPreference(
                        title = stringResource(R.string.maintenance_soft_reboot),
                        summary = stringResource(R.string.maintenance_soft_reboot_summary),
                        startAction = {
                            MiuixIcon(
                                imageVector = Icons.Filled.RestartAlt,
                                contentDescription = null,
                                tint = colorScheme.onBackground,
                                modifier = Modifier.padding(end = 6.dp),
                            )
                        },
                        onClick = { confirming = onSoftReboot },
                    )
                    ArrowPreference(
                        title = stringResource(R.string.maintenance_soft_reboot_hidden),
                        summary = stringResource(
                            if (stealthSupported) R.string.maintenance_soft_reboot_hidden_summary
                            else R.string.maintenance_soft_reboot_hidden_summary_unsupported
                        ),
                        startAction = {
                            MiuixIcon(
                                imageVector = Icons.Filled.VisibilityOff,
                                contentDescription = null,
                                tint = colorScheme.onBackground,
                                modifier = Modifier.padding(end = 6.dp),
                            )
                        },
                        onClick = { confirming = onSoftRebootHidden },
                    )
                }
            }
            item { LogPanel(log) }
        }
    }
    ConfirmDialog(confirming) { confirming = null }
}

/** Confirmation for the two reboots, asked before anything runs. */
@Composable
private fun ConfirmDialog(pending: (() -> Unit)?, onDismiss: () -> Unit) {
    if (pending == null) return
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.maintenance_confirm_title)) },
        text = { Text(stringResource(R.string.maintenance_confirm_message)) },
        confirmButton = {
            TextButton(onClick = {
                pending()
                onDismiss()
            }) {
                Text(stringResource(R.string.maintenance_confirm_ok))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.maintenance_confirm_cancel))
            }
        },
    )
}

/**
 * The run log. Kept on the screen rather than in a snackbar so an operation that ends in a reboot
 * still leaves its trace behind for the moment before the screen goes away.
 */
@Composable
private fun LogPanel(log: List<LogLine>) {
    val text = if (log.isEmpty()) {
        stringResource(R.string.maintenance_log_idle)
    } else {
        log.joinToString("\n") { it.text }
    }
    // The panel is shared by all three skins, so the colours come from whichever one is
    // current rather than from Miuix alone -- Material builds have no Miuix theme to read.
    val isMiuix = LocalUiMode.current != UiMode.Material
    val idleTint = if (isMiuix) colorScheme.onSurfaceVariantSummary
    else MaterialTheme.colorScheme.onSurfaceVariant
    val panelColor = if (isMiuix) colorScheme.surfaceVariant
    else MaterialTheme.colorScheme.surfaceVariant
    val tint = when (log.lastOrNull()?.kind) {
        LogLine.Kind.OK -> Color(0xFF2E7D32)
        LogLine.Kind.WARN -> Color(0xFFE65100)
        LogLine.Kind.ERROR -> Color(0xFFC62828)
        else -> idleTint
    }
    Box(
        modifier = Modifier
            .padding(horizontal = 16.dp)
            .padding(bottom = 16.dp)
            .fillMaxWidth()
            .heightIn(min = 96.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(panelColor)
            .padding(12.dp),
    ) {
        Text(
            text = text,
            fontSize = 12.sp,
            fontFamily = FontFamily.Monospace,
            color = tint,
        )
    }
}
