package me.diksu.ultra.ui.component.bottombar

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import me.diksu.ultra.R
import me.diksu.ultra.ui.LocalUiMode
import me.diksu.ultra.ui.capability.NavGate
import me.diksu.ultra.ui.component.dialog.AppAlertDialog
import me.diksu.ultra.ui.isMiuixFamily
import top.yukonga.miuix.kmp.basic.Icon as MiuixIcon
import top.yukonga.miuix.kmp.basic.Text as MiuixText
import top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme

/** 不可用入口的置灰度，与 4.1 整条导航置灰时保持一致。 */
internal const val NavGateDisabledAlpha = 0.35f

/**
 * 不可用状态下的导航：入口仍然画出来，但整体置灰，点一下说明原因。
 *
 * 这是"入口常驻、置灰、给恢复入口"的落地实现。为什么不做成 `enabled = false`
 * 的条目：置灰条目如果连点击都不响应，用户就永远看不到"为什么不可用"——
 * 而那句话才是这个状态唯一有价值的信息。
 */
@Composable
fun DisabledNavigationRail(
    gate: NavGate,
    onGoHome: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val miuix = LocalUiMode.current.isMiuixFamily
    var showGate by remember { mutableStateOf(false) }
    val destinations = BottomBarDestination.entries

    Box(modifier = modifier.fillMaxHeight()) {
        Column(
            modifier = Modifier
                .fillMaxHeight()
                .width(64.dp)
                .padding(vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Bottom,
        ) {
            destinations.forEach { destination ->
                Box(
                    modifier = Modifier
                        .padding(vertical = 8.dp)
                        .size(40.dp)
                        .clip(CircleShape)
                        .clickable { showGate = true },
                    contentAlignment = Alignment.Center,
                ) {
                    if (miuix) {
                        MiuixIcon(
                            imageVector = destination.icon,
                            contentDescription = stringResource(destination.label),
                            tint = colorScheme.onSurface,
                            modifier = Modifier.size(24.dp).alpha(0.35f),
                        )
                    } else {
                        Icon(
                            imageVector = destination.icon,
                            contentDescription = stringResource(destination.label),
                            modifier = Modifier.size(24.dp).alpha(0.35f),
                        )
                    }
                }
            }
        }
    }

    GateDialog(
        gate = gate,
        show = showGate,
        onDismiss = { showGate = false },
        onGoHome = {
            showGate = false
            onGoHome()
        },
    )
}

/** 底栏版本：图标横向排开，同样置灰、同样点一下说明原因。 */
@Composable
fun DisabledNavigationBar(
    gate: NavGate,
    onGoHome: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val miuix = LocalUiMode.current.isMiuixFamily
    var showGate by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
            .background(if (miuix) colorScheme.surface else androidx.compose.material3.MaterialTheme.colorScheme.surfaceContainer)
            .clickable { showGate = true }
            .padding(vertical = 10.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BottomBarDestination.entries.forEach { destination ->
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    if (miuix) {
                        MiuixIcon(
                            imageVector = destination.icon,
                            contentDescription = stringResource(destination.label),
                            tint = colorScheme.onSurface,
                            modifier = Modifier.size(24.dp).alpha(0.35f),
                        )
                        Spacer(Modifier.height(2.dp))
                        MiuixText(
                            text = stringResource(destination.label),
                            fontSize = 11.sp,
                            color = colorScheme.onSurface.copy(alpha = 0.35f),
                        )
                    } else {
                        Icon(
                            imageVector = destination.icon,
                            contentDescription = stringResource(destination.label),
                            modifier = Modifier.size(24.dp).alpha(0.35f),
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = stringResource(destination.label),
                            fontSize = 11.sp,
                            color = androidx.compose.material3.MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f),
                        )
                    }
                }
            }
        }
    }

    GateDialog(
        gate = gate,
        show = showGate,
        onDismiss = { showGate = false },
        onGoHome = {
            showGate = false
            onGoHome()
        },
    )
}

@Composable
private fun GateDialog(
    gate: NavGate,
    show: Boolean,
    onDismiss: () -> Unit,
    onGoHome: () -> Unit,
) {
    if (!show) return
    val dismissText = stringResource(R.string.nav_gate_dismiss)
    AppAlertDialog(
        title = gate.title,
        onDismissRequest = onDismiss,
        confirmText = gate.action,
        dismissText = dismissText,
        onConfirm = onGoHome,
    ) {
        val miuix = LocalUiMode.current.isMiuixFamily
        if (miuix) {
            MiuixText(
                text = gate.reason,
                fontSize = 14.sp,
                fontWeight = FontWeight.Normal,
                color = colorScheme.onSurface,
            )
        } else {
            Text(
                text = gate.reason,
                fontSize = 14.sp,
                color = androidx.compose.material3.MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

/** 单个入口不可用时的说明弹窗；gate 为 null 表示不显示。 */
@Composable
internal fun NavGateDialog(
    gate: NavGate?,
    onDismiss: () -> Unit,
    onGoHome: () -> Unit,
) {
    if (gate == null) return
    GateDialog(
        gate = gate,
        show = true,
        onDismiss = onDismiss,
        onGoHome = onGoHome,
    )
}
