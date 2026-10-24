package me.diksu.ultra.ui.component.dialog

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import me.diksu.ultra.R
import me.diksu.ultra.ui.LocalUiMode
import me.diksu.ultra.ui.UiMode
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
    onManualInstall: () -> Unit,
    onCancel: () -> Unit,
) {
    when (LocalUiMode.current) {
        UiMode.Miuix, UiMode.MiuixStock ->
            JailbreakDialogMiuix(show, onExploit, onDirtyFrag, onManualInstall, onCancel)
        UiMode.Material -> JailbreakDialogMaterial(show, onExploit, onDirtyFrag, onManualInstall, onCancel)
    }
}

@Composable
private fun JailbreakDialogMiuix(
    show: Boolean,
    onExploit: () -> Unit,
    onDirtyFrag: () -> Unit,
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
                // 两条独立的提权路径，最终都落到 ksud late-load：
                // ghostlock 是本应用内置的二进制，DirtyFrag 是另装的一个应用。
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
                TextButton(onClick = onDirtyFrag) {
                    Text(stringResource(R.string.jailbreak_engine_dirtyfrag))
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onExploit) {
                Text(stringResource(R.string.jailbreak_engine_ghostlock))
            }
        },
        dismissButton = {
            TextButton(onClick = onManualInstall) {
                Text(stringResource(R.string.jailbreak_manual_action))
            }
        }
    )
}