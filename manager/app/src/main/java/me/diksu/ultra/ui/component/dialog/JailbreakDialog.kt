package me.diksu.ultra.ui.component.dialog

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import me.diksu.ultra.R
import me.diksu.ultra.ui.LocalUiMode
import me.diksu.ultra.ui.UiMode
import me.diksu.ultra.ui.isMiuixFamily
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Text as MiuixText
import top.yukonga.miuix.kmp.basic.TextButton as MiuixTextButton
import top.yukonga.miuix.kmp.window.WindowDialog

/**
 * Chooses how to gain root when the kernel is not installed yet.
 *
 * Unlike a plain confirm dialog, tapping outside or the back gesture only
 * dismisses the dialog - it does not fall through to the manual install page.
 * The two buttons are the only way to actually proceed.
 */
@Composable
fun JailbreakDialog(
    show: Boolean,
    onExploit: () -> Unit,
    onDirtyFrag: () -> Unit,
    onGhostKey: () -> Unit,
    onSynOpt: () -> Unit,
    onManualInstall: () -> Unit,
    onCancel: () -> Unit,
) {
    when (LocalUiMode.current) {
        UiMode.Miuix, UiMode.MiuixStock ->
            JailbreakDialogMiuix(show, onExploit, onDirtyFrag, onGhostKey, onSynOpt, onManualInstall, onCancel)
        UiMode.Material -> JailbreakDialogMaterial(show, onExploit, onDirtyFrag, onGhostKey, onSynOpt, onManualInstall, onCancel)
    }
}

@Composable
private fun JailbreakDialogMiuix(
    show: Boolean,
    onExploit: () -> Unit,
    onDirtyFrag: () -> Unit,
    onGhostKey: () -> Unit,
    onSynOpt: () -> Unit,
    onManualInstall: () -> Unit,
    onCancel: () -> Unit,
) {
    WindowDialog(
        show = show,
        title = stringResource(R.string.jailbreak_choose_title),
        onDismissRequest = onCancel,
        content = {
            Column(modifier = Modifier.fillMaxWidth()) {
                MiuixText(text = stringResource(R.string.jailbreak_choose_message))
                // 五条独立的提权路径，最终都落到 ksud late-load：
                // GhostLock / DirtyFrag / SynOpt 是自有链，GhostKey 移植自月虹。
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(top = 12.dp)
                ) {
                    MiuixTextButton(
                        text = stringResource(R.string.jailbreak_engine_ghostlock),
                        onClick = onExploit,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.textButtonColorsPrimary()
                    )
                    MiuixTextButton(
                        text = stringResource(R.string.jailbreak_engine_dirtyfrag),
                        onClick = onDirtyFrag,
                        modifier = Modifier.fillMaxWidth()
                    )
                    MiuixTextButton(
                        text = stringResource(R.string.jailbreak_engine_ghostkey),
                        onClick = onGhostKey,
                        modifier = Modifier.fillMaxWidth()
                    )
                    MiuixTextButton(
                        text = stringResource(R.string.jailbreak_engine_98432),
                        onClick = onSynOpt,
                        modifier = Modifier.fillMaxWidth()
                    )
                    MiuixTextButton(
                        text = stringResource(R.string.jailbreak_manual_action),
                        onClick = onManualInstall,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    )
}

@Composable
private fun JailbreakDialogMaterial(
    show: Boolean,
    onExploit: () -> Unit,
    onDirtyFrag: () -> Unit,
    onGhostKey: () -> Unit,
    onSynOpt: () -> Unit,
    onManualInstall: () -> Unit,
    onCancel: () -> Unit,
) {
    if (!show) return
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text(stringResource(R.string.jailbreak_choose_title)) },
        text = {
            Column {
                Text(stringResource(R.string.jailbreak_choose_message))
                // 六个选项一列排开：挤在 confirm/dismiss 三个位里放不下，
                // 也会让主次看不出来。
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(top = 12.dp),
                ) {
                    listOf(
                        stringResource(R.string.jailbreak_engine_ghostlock) to onExploit,
                        stringResource(R.string.jailbreak_engine_dirtyfrag) to onDirtyFrag,
                        stringResource(R.string.jailbreak_engine_ghostkey) to onGhostKey,
                        stringResource(R.string.jailbreak_engine_98432) to onSynOpt,
                        stringResource(R.string.jailbreak_manual_action) to onManualInstall,
                    ).forEachIndexed { index, (label, action) ->
                        TextButton(onClick = action, modifier = Modifier.fillMaxWidth()) {
                            Text(
                                text = label,
                                color = if (index == 0) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurface
                                },
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {}
    )
}

/**
 * SynOpt 的两种跑法。
 *
 * 这条链的写原语会真的改内核（16 字节越界写），在 CONFIG_PANIC_ON_OOPS=y
 * 的机器上写错就是重启。所以不能默认开，必须让用户自己选。
 */
@Composable
fun SynOptModeDialog(
    show: Boolean,
    onProbe: () -> Unit,
    onWrite: () -> Unit,
    onCancel: () -> Unit,
) {
    if (!show) return
    val title = stringResource(R.string.synopt_mode_title)
    val message = stringResource(R.string.synopt_mode_message)
    val probe = stringResource(R.string.synopt_mode_probe)
    val write = stringResource(R.string.synopt_mode_write)

    if (LocalUiMode.current.isMiuixFamily) {
        WindowDialog(
            show = show,
            title = title,
            onDismissRequest = onCancel,
            content = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    MiuixText(text = message)
                    Column(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(top = 12.dp)
                    ) {
                        MiuixTextButton(
                            text = probe,
                            onClick = onProbe,
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.textButtonColorsPrimary()
                        )
                        MiuixTextButton(
                            text = write,
                            onClick = onWrite,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        )
    } else {
        AlertDialog(
            onDismissRequest = onCancel,
            title = { Text(title) },
            text = {
                Column {
                    Text(message)
                    Column(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(top = 12.dp),
                    ) {
                        TextButton(onClick = onProbe, modifier = Modifier.fillMaxWidth()) {
                            Text(probe, color = MaterialTheme.colorScheme.primary)
                        }
                        TextButton(onClick = onWrite, modifier = Modifier.fillMaxWidth()) {
                            Text(write, color = MaterialTheme.colorScheme.onSurface)
                        }
                    }
                }
            },
            confirmButton = {},
        )
    }
}
