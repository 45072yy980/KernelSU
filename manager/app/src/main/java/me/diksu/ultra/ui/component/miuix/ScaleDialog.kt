package me.diksu.ultra.ui.component.miuix

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import me.diksu.ultra.R
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme

@Composable
fun ScaleDialog(
    show: Boolean,
    onDismissRequest: () -> Unit,
    volumeState: () -> Float,
    onVolumeChange: (Float) -> Unit,
) {
    OverlayDialog(
        show = show,
        title = stringResource(R.string.settings_page_scale),
        summary = "80% - 110%",
        onDismissRequest = onDismissRequest,
        content = {
            var text by remember(show) {
                mutableStateOf((volumeState() * 100).toInt().toString())
            }
            TextField(
                modifier = Modifier.padding(bottom = 16.dp),
                value = text,
                maxLines = 1,
                trailingIcon = {
                    Text(
                        text = "%",
                        modifier = Modifier.padding(horizontal = 16.dp),
                        color = colorScheme.onSurfaceVariantActions,
                    )
                },
                onValueChange = { newValue ->
                    if (newValue.isEmpty()) {
                        text = ""
                    } else {
                        val valid = newValue.all { it.isDigit() }
                        if (valid) {
                            text = newValue
                        }
                    }
                },
            )
            // 越界以前是静默夹到边界（输入 200、确认后变 110），看不出来被改过。
            // 现在越界直接标错并禁用确定，让数字和标题的 80–110% 对得上。
            val parsed = text.toIntOrNull()
            val inRange = parsed != null && parsed in 80..110
            if (!inRange) {
                Text(
                    text = "请输入 80 到 110 之间的整数",
                    modifier = Modifier.padding(bottom = 8.dp),
                    color = colorScheme.error,
                )
            }
            Row(horizontalArrangement = Arrangement.SpaceBetween) {
                TextButton(
                    text = stringResource(android.R.string.cancel),
                    onClick = onDismissRequest,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(20.dp))
                TextButton(
                    text = stringResource(android.R.string.ok),
                    enabled = inRange,
                    onClick = {
                        val value = text.toIntOrNull() ?: return@TextButton
                        onVolumeChange(value / 100f)
                        onDismissRequest()
                    },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.textButtonColorsPrimary(),
                )
            }
        }
    )
}
