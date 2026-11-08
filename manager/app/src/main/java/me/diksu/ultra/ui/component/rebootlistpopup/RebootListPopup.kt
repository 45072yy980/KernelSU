package me.diksu.ultra.ui.component.rebootlistpopup

import android.content.Context
import android.os.PowerManager
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import me.diksu.ultra.Natives
import me.diksu.ultra.R
import me.diksu.ultra.ui.LocalUiMode
import me.diksu.ultra.ui.UiMode
import me.diksu.ultra.ui.component.dialog.rememberConfirmDialog
import me.diksu.ultra.ui.util.reboot

data class RebootListOption(
    @param:StringRes val labelRes: Int,
    val reason: String,
)

@Composable
fun getRebootListOption(): List<RebootListOption> {
    val pm = LocalContext.current.getSystemService(Context.POWER_SERVICE) as PowerManager?

    @Suppress("DEPRECATION")
    val isRebootingUserspaceSupported = pm?.isRebootingUserspaceSupported == true

    return buildList {
        add(RebootListOption(R.string.reboot, ""))
        if (isRebootingUserspaceSupported) {
            add(RebootListOption(R.string.reboot_userspace, "userspace"))
        }
        add(RebootListOption(R.string.reboot_soft, "soft_reboot"))
        add(RebootListOption(R.string.reboot_recovery, "recovery"))
        add(RebootListOption(R.string.reboot_bootloader, "bootloader"))
        add(RebootListOption(R.string.reboot_download, "download"))
        add(RebootListOption(R.string.reboot_edl, "edl"))
    }
}

/** 会离开当前系统、回程要靠音量键或电脑的目标。 */
private val DangerousRebootReasons = setOf("recovery", "bootloader", "download", "edl")

/**
 * 重启：普通重启照旧，但**越狱模式下会掉 root**，recovery / bootloader / download / EDL
 * 一旦进去就得手动回来，所以这几种也先确认一次，而不是点错就直接走。
 */
@Composable
fun rememberRebootAction(): (String) -> Unit {
    val title = stringResource(R.string.reboot)
    val jailbreakMessage = stringResource(R.string.jailbreak_reboot_warning)
    val genericMessage = stringResource(R.string.reboot_confirm_message)
    val pending = remember { mutableStateOf("") }
    val confirmDialog = rememberConfirmDialog(onConfirm = { reboot(pending.value) })

    return remember(title, jailbreakMessage, genericMessage, confirmDialog) {
        { reason ->
            val needsConfirm = reason in DangerousRebootReasons ||
                (Natives.isLateLoadMode && reason.isEmpty())
            if (needsConfirm) {
                pending.value = reason
                confirmDialog.showConfirm(
                    title = title,
                    content = if (reason.isEmpty()) jailbreakMessage else genericMessage,
                )
            } else {
                reboot(reason)
            }
        }
    }
}

@Composable
fun RebootListPopup() {
    when (LocalUiMode.current) {
        UiMode.Miuix, UiMode.MiuixStock -> RebootListPopupMiuix()
        UiMode.Material -> RebootListPopupMaterial()
    }
}
