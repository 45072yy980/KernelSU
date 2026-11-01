package me.diksu.ultra.ui.screen.home

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import me.diksu.ultra.R
import me.diksu.ultra.ui.LocalUiMode
import me.diksu.ultra.ui.component.dialog.AppAlertDialog
import me.diksu.ultra.ui.isMiuixFamily
import top.yukonga.miuix.kmp.basic.Text as MiuixText
import top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme

/**
 * The "manager and ksud disagree" confirmation, shared by the three skins' status cards.
 *
 * It is the only new UI in this feature: the card itself stays exactly as it was, it just
 * becomes clickable while [HomeUiState.hasVersionMismatch] is true. The whole action can be
 * done from a running session, which is the point - a jailbreak user no longer has to reboot
 * and re-exploit just to refresh ksud.
 *
 * While the update runs the dialog cannot be dismissed; afterwards it reports success or the
 * failure verbatim instead of closing silently.
 */
@Composable
fun KsudUpdateDialog(
    state: HomeUiState,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val running = state.ksudUpdate == KsudUpdateStatus.Running
    val succeeded = state.ksudUpdate == KsudUpdateStatus.Succeeded
    val failed = state.ksudUpdate == KsudUpdateStatus.Failed

    // Strings are fetched here, in composition: the callbacks below are plain lambdas.
    val title = stringResource(R.string.home_ksud_update_title)
    val message = stringResource(R.string.home_ksud_update_message)
    val runningText = stringResource(R.string.home_ksud_update_running)
    val successText = stringResource(R.string.home_ksud_update_success)
    val failureText = stringResource(R.string.home_ksud_update_failure)
    val confirmText = when {
        succeeded -> stringResource(R.string.home_ksud_update_done)
        running -> runningText
        else -> stringResource(R.string.home_ksud_update_confirm)
    }
    val cancelText = stringResource(R.string.home_ksud_update_cancel)
    // 把读到的实际版本摆出来：修的就是「更新完看不出变化」，这里至少让人能对照。
    val currentText = state.ksudVersion?.let {
        stringResource(R.string.home_ksud_update_current, it)
    }

    AppAlertDialog(
        title = title,
        dismissText = if (running || succeeded) null else cancelText,
        confirmText = confirmText,
        confirmEnabled = !running,
        onConfirm = { if (succeeded) onDismiss() else onConfirm() },
        onDismissRequest = { if (!running) onDismiss() },
    ) {
        val miuix = LocalUiMode.current.isMiuixFamily
        val statusColor = when {
            failed -> if (miuix) Color(0xFFE53935) else MaterialTheme.colorScheme.error
            else -> if (miuix) colorScheme.primary else MaterialTheme.colorScheme.primary
        }
        Column {
            if (miuix) {
                MiuixText(text = message, fontSize = 14.sp, color = colorScheme.onSurface)
            } else {
                Text(text = message, style = MaterialTheme.typography.bodyMedium)
            }
            if (currentText != null) {
                Spacer(Modifier.height(6.dp))
                if (miuix) {
                    MiuixText(
                        text = currentText,
                        fontSize = 13.sp,
                        color = colorScheme.onSurfaceVariantSummary,
                    )
                } else {
                    Text(
                        text = currentText,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (running || succeeded || failed) {
                Spacer(Modifier.height(8.dp))
                val statusText = when {
                    running -> runningText
                    succeeded -> successText
                    else -> failureText
                }
                if (miuix) {
                    MiuixText(text = statusText, fontSize = 14.sp, color = statusColor)
                } else {
                    Text(text = statusText, style = MaterialTheme.typography.bodyMedium, color = statusColor)
                }
            }
        }
    }
}
