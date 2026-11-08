package me.diksu.ultra.ui.component

import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import me.diksu.ultra.ui.component.dialog.AppAlertDialog
import me.diksu.ultra.ui.screen.susfs.SusfsTextField
import me.diksu.ultra.ui.util.clearManagerHidden
import me.diksu.ultra.ui.util.matchesTriggerCode

/**
 * The way back out of the disguise, wrapped around whatever row shows the manager version.
 *
 * With stealth mode on there is no settings page to switch it off from, and the calculator that
 * opens the web UI may be uninstalled as well, so the home screen has to carry it. Five taps in a
 * row on the version row opens a code prompt; the right code turns both disguises off.
 *
 * The code comes first because five taps is a gesture anyone holding the phone could make and the
 * code is not. A wrong code marks the field and says so; it is never printed anywhere, including
 * in the toast that announces stealth mode, so a glance over the shoulder learns nothing.
 *
 * This used to live inline in the stock miuix home screen only, which meant a reader who had
 * picked either of the other two skins had no way out at all.
 */
@Composable
fun ManagerHiddenRecovery(content: @Composable () -> Unit) {
    val context = LocalContext.current
    var taps by remember { mutableIntStateOf(0) }
    var asking by remember { mutableStateOf(false) }
    var typed by remember { mutableStateOf("") }
    var wrong by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                taps++
                if (taps >= 5) {
                    taps = 0
                    typed = ""
                    wrong = false
                    asking = true
                }
            },
    ) {
        content()
    }

    if (asking) {
        AppAlertDialog(
            title = "解除伪装",
            onDismissRequest = { asking = false },
            confirmText = "确定",
            onConfirm = {
                val entry = typed
                // Off the main thread: this reads the trigger file and may run two root
                // commands.
                Thread {
                    val ok = matchesTriggerCode(entry)
                    if (ok) {
                        clearManagerHidden()
                    }
                    Handler(Looper.getMainLooper()).post {
                        if (ok) {
                            asking = false
                            Toast.makeText(context, "已解除", Toast.LENGTH_SHORT).show()
                        } else {
                            wrong = true
                        }
                    }
                }.start()
            },
            dismissText = "取消",
            confirmEnabled = typed.isNotEmpty(),
        ) {
            Column {
                FeatureText(
                    text = "输入解除码。忘了的话，在计算器里输入的也是同一个码。",
                    size = 13,
                    muted = true,
                )
                SusfsTextField(
                    value = typed,
                    onValueChange = { entry ->
                        typed = entry.filter { it.isDigit() }
                        wrong = false
                    },
                    label = "解除码",
                    modifier = Modifier.padding(top = 12.dp).fillMaxWidth(),
                )
                if (wrong) {
                    // The old behaviour said nothing at all, which left "wrong code" and
                    // "nothing happened" looking identical. The code is still not revealed:
                    // this only says the entry was not it.
                    FeatureText(text = "解除码不对", size = 13, modifier = Modifier.padding(top = 8.dp))
                }
            }
        }
    }
}
