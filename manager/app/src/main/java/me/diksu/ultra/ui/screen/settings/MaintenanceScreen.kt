package me.diksu.ultra.ui.screen.settings

import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.diksu.ultra.R
import me.diksu.ultra.ui.LocalUiMode
import me.diksu.ultra.ui.component.dialog.AppAlertDialog
import me.diksu.ultra.ui.component.material.TopBarBackButton
import me.diksu.ultra.ui.isMiuixFamily
import me.diksu.ultra.ui.navigation3.LocalNavigator
import me.diksu.ultra.ui.screen.susfs.SusfsCard
import me.diksu.ultra.ui.screen.susfs.SusfsRow
import me.diksu.ultra.ui.screen.susfs.SusfsText
import me.diksu.ultra.ui.util.isStealthSupported
import me.diksu.ultra.ui.util.reboot
import me.diksu.ultra.ui.util.reloadModules
import me.diksu.ultra.ui.util.setStealthEnabled
import top.yukonga.miuix.kmp.basic.Icon as MiuixIcon
import top.yukonga.miuix.kmp.basic.IconButton as MiuixIconButton
import top.yukonga.miuix.kmp.basic.Scaffold as MiuixScaffold
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme

/**
 * 维护合集：重载模块与两种软重启。
 *
 * 这三件事原来在「其他功能」里各占一行，但它们都是低频、带副作用的操作，
 * 挤在设置页里既占地方又容易被误点。收进一个子页面，重启一律先确认。
 */
@Composable
fun MaintenanceScreen() {
    val navigator = LocalNavigator.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val title = stringResource(R.string.maintenance_title)

    var pendingReboot by remember { mutableStateOf(RebootKind.None) }
    var stealthSupported by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) { stealthSupported = isStealthSupported() }
    }

    val onReloadModules: () -> Unit = {
        scope.launch {
            val ok = withContext(Dispatchers.IO) { reloadModules() }
            val text = if (ok == 3) {
                context.getString(R.string.maintenance_log_reload_ok)
            } else {
                context.getString(R.string.maintenance_log_reload_partial, ok)
            }
            Toast.makeText(context, text, Toast.LENGTH_SHORT).show()
        }
    }

    val body: @Composable (PaddingValues) -> Unit = { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 12.dp),
        ) {
            SusfsCard(title = stringResource(R.string.maintenance_group_actions)) {
                SusfsRow(
                    title = stringResource(R.string.maintenance_reload_modules),
                    summary = stringResource(R.string.maintenance_reload_modules_summary),
                    icon = Icons.Filled.Refresh,
                    onClick = onReloadModules,
                )
                SusfsRow(
                    title = stringResource(R.string.maintenance_soft_reboot),
                    summary = stringResource(R.string.maintenance_soft_reboot_summary),
                    icon = Icons.Filled.RestartAlt,
                    onClick = { pendingReboot = RebootKind.Plain },
                )
                SusfsRow(
                    title = stringResource(R.string.maintenance_soft_reboot_hidden),
                    summary = if (stealthSupported) {
                        stringResource(R.string.maintenance_soft_reboot_hidden_summary)
                    } else {
                        stringResource(R.string.maintenance_soft_reboot_hidden_summary_unsupported)
                    },
                    icon = Icons.Filled.VisibilityOff,
                    onClick = { pendingReboot = RebootKind.Hidden },
                )
            }
            SusfsText(
                text = stringResource(R.string.maintenance_group_note),
                size = 13,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            )
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

    if (pendingReboot != RebootKind.None) {
        val hidden = pendingReboot == RebootKind.Hidden
        AppAlertDialog(
            title = stringResource(R.string.maintenance_confirm_title),
            onDismissRequest = { pendingReboot = RebootKind.None },
            confirmText = stringResource(R.string.maintenance_confirm_ok),
            dismissText = stringResource(R.string.maintenance_confirm_cancel),
            onConfirm = {
                pendingReboot = RebootKind.None
                scope.launch {
                    // 隐藏重启：先把开关写进内核，再重启；写失败也照常重启，
                    // 只是这一趟回来管理器仍然可见。
                    if (hidden && stealthSupported) {
                        withContext(Dispatchers.IO) { setStealthEnabled(true) }
                    }
                    delay(1200)
                    withContext(Dispatchers.IO) { reboot("soft_reboot") }
                }
            },
        ) {
            SusfsText(text = stringResource(R.string.maintenance_confirm_message), size = 14)
        }
    }
}

/** 两个软重启之间只差一件事：重启前先把隐藏模式打开。 */
private enum class RebootKind { None, Plain, Hidden }
