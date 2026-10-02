package me.weishu.kernelsu.ui.component

import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import me.weishu.kernelsu.ui.util.clearManagerHidden
import me.weishu.kernelsu.ui.util.matchesTriggerCode

/**
 * The way back out of the disguise, wrapped around whatever row shows the manager version.
 *
 * With stealth mode on there is no settings page to switch it off from, and the calculator that
 * opens the web UI may be uninstalled as well, so the home screen has to carry it. Five taps in a
 * row on the version row opens a code prompt; the right code turns both disguises off.
 *
 * The code comes first because five taps is a gesture anyone holding the phone could make and the
 * code is not. A wrong code does nothing and says nothing.
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
        AlertDialog(
            onDismissRequest = { asking = false },
            title = { Text("解除伪装") },
            text = {
                Column {
                    Text("输入解除码。忘了的话，在计算器里输入的也是同一个码。")
                    OutlinedTextField(
                        value = typed,
                        onValueChange = { entry ->
                            typed = entry.filter { it.isDigit() }
                            wrong = false
                        },
                        singleLine = true,
                        isError = wrong,
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    if (wrong) {
                        // The old behaviour said nothing at all, which left "wrong code" and
                        // "nothing happened" looking identical. The code is still not revealed:
                        // this only says the entry was not it.
                        Text(
                            "解除码不对",
                            modifier = Modifier.padding(top = 8.dp),
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
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
                }) {
                    Text("确定")
                }
            },
            dismissButton = {
                TextButton(onClick = { asking = false }) {
                    Text("取消")
                }
            },
        )
    }
}
