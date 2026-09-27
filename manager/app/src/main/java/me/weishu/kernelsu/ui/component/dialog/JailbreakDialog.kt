package me.weishu.kernelsu.ui.component.dialog

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
import me.weishu.kernelsu.R
import me.weishu.kernelsu.ui.LocalUiMode
import me.weishu.kernelsu.ui.UiMode
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
    onManualInstall: () -> Unit,
    onCancel: () -> Unit,
) {
    when (LocalUiMode.current) {
        UiMode.Miuix -> JailbreakDialogMiuix(show, onExploit, onManualInstall, onCancel)
        UiMode.Material -> JailbreakDialogMaterial(show, onExploit, onManualInstall, onCancel)
    }
}

@Composable
private fun JailbreakDialogMiuix(
    show: Boolean,
    onExploit: () -> Unit,
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
                Row(
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.padding(top = 12.dp)
                ) {
                    MiuixTextButton(
                        text = stringResource(R.string.jailbreak_manual_action),
                        onClick = onManualInstall,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(Modifier.width(20.dp))
                    MiuixTextButton(
                        text = stringResource(R.string.jailbreak_exploit_action),
                        onClick = onExploit,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.textButtonColorsPrimary()
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
    onManualInstall: () -> Unit,
    onCancel: () -> Unit,
) {
    if (!show) return
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text(stringResource(R.string.jailbreak_choose_title)) },
        text = { Text(stringResource(R.string.jailbreak_choose_message)) },
        confirmButton = {
            TextButton(onClick = onExploit) {
                Text(stringResource(R.string.jailbreak_exploit_action))
            }
        },
        dismissButton = {
            TextButton(onClick = onManualInstall) {
                Text(stringResource(R.string.jailbreak_manual_action))
            }
        }
    )
}