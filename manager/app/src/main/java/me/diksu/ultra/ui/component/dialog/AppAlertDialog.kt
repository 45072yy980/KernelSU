package me.diksu.ultra.ui.component.dialog

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import me.diksu.ultra.ui.LocalUiMode
import me.diksu.ultra.ui.isMiuixFamily
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.TextButton as MiuixTextButton
import top.yukonga.miuix.kmp.window.WindowDialog

/**
 * 一套皮肤一致的确认/输入弹窗。
 *
 * 3.x 里不少面板（SuSFS、隐藏应用列表、KeyMint、其他功能……）在 miuix 皮肤下
 * 仍然弹 Material 的 `AlertDialog`：圆角、按钮排布、标题字号全都对不上，观感
 * 上像另一个应用的窗口。这里把"标题 + 正文 + 取消/确定"抽成一个组件，
 * 由当前皮肤决定用 miuix 的 [WindowDialog] 还是 Material 3 的 [AlertDialog]。
 *
 * 正文由调用方通过 [body] 提供，因此同一个组件既能承载纯文字，也能承载
 * 输入框、分段选择等表单 —— 输入控件本身仍由调用方按皮肤选择。
 *
 * @param confirmEnabled false 时确定按钮置灰（例如必填项为空）。
 * @param dismissText 传 null 表示只有一个按钮。
 */
@Composable
fun AppAlertDialog(
    title: String,
    onDismissRequest: () -> Unit,
    confirmText: String,
    onConfirm: () -> Unit,
    dismissText: String? = null,
    confirmEnabled: Boolean = true,
    body: @Composable () -> Unit,
) {
    if (LocalUiMode.current.isMiuixFamily) {
        WindowDialog(
            show = true,
            modifier = Modifier.windowInsetsPadding(
                WindowInsets.systemBars.only(WindowInsetsSides.Top)
            ),
            title = title,
            onDismissRequest = onDismissRequest,
            content = {
                Column {
                    body()
                    Row(
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.padding(top = 12.dp),
                    ) {
                        MiuixTextButton(
                            text = dismissText ?: stringResource(android.R.string.cancel),
                            onClick = onDismissRequest,
                            modifier = Modifier.weight(1f),
                        )
                        Spacer(Modifier.width(20.dp))
                        MiuixTextButton(
                            text = confirmText,
                            onClick = { if (confirmEnabled) onConfirm() },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.textButtonColorsPrimary(),
                        )
                    }
                }
            },
        )
    } else {
        AlertDialog(
            onDismissRequest = onDismissRequest,
            title = { Text(title) },
            text = { body() },
            confirmButton = {
                TextButton(onClick = onConfirm, enabled = confirmEnabled) {
                    Text(confirmText)
                }
            },
            dismissButton = dismissText?.let { label ->
                {
                    TextButton(onClick = onDismissRequest) {
                        Text(label)
                    }
                }
            },
        )
    }
}
