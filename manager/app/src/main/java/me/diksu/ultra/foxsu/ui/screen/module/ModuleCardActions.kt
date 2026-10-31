package me.diksu.ultra.foxsu.ui.screen.module

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import me.diksu.ultra.R

/**
 * 模块卡片内联操作条 —— 点卡片后在卡片自身内部展开，不是二级面板。
 *
 * 左侧「执行」跑模块自带的 action.sh，右侧「卸载」直接走内核模块卸载流程
 * （真实生效：调用 ksu module uninstall，已标记卸载的模块则变成「撤销」）。
 */
@Composable
fun ModuleCardActions(
    runnable: Boolean,
    reason: String?,
    accent: Color,
    neutralBg: Color,
    neutralFg: Color,
    pendingRemoval: Boolean = false,
    onExecute: () -> Unit,
    onUninstall: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val tintBg = if (runnable) accent.copy(alpha = 0.14f) else neutralBg
    val tintFg = if (runnable) accent else neutralFg.copy(alpha = 0.55f)

    Column(modifier = modifier.fillMaxWidth().padding(top = 10.dp)) {
        if (!runnable && reason != null) {
            Text(
                text = reason,
                fontSize = 10.sp,
                lineHeight = 15.sp,
                color = neutralFg.copy(alpha = 0.75f),
                modifier = Modifier.padding(bottom = 8.dp),
            )
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // 执行
            Row(
                modifier = Modifier
                    .height(34.dp)
                    .clip(RoundedCornerShape(11.dp))
                    .background(tintBg)
                    .then(
                        if (runnable) {
                            Modifier.clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = onExecute,
                            )
                        } else {
                            Modifier
                        }
                    )
                    .padding(horizontal = 13.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    painter = painterResource(R.drawable.fx_ic_d_play),
                    contentDescription = null,
                    modifier = Modifier.size(13.dp),
                    tint = tintFg,
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = stringResource(R.string.fx_module_action_sheet_run),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = tintFg,
                )
            }

            // 卸载（已标记卸载时变为撤销）
            Row(
                modifier = Modifier
                    .height(34.dp)
                    .clip(RoundedCornerShape(11.dp))
                    .background(neutralBg)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onUninstall,
                    )
                    .padding(horizontal = 13.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    painter = painterResource(
                        if (pendingRemoval) R.drawable.fx_ic_d_refresh else R.drawable.fx_ic_d_trash
                    ),
                    contentDescription = null,
                    modifier = Modifier.size(13.dp),
                    tint = neutralFg,
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = stringResource(
                        if (pendingRemoval) R.string.fx_undo else R.string.fx_uninstall
                    ),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = neutralFg,
                )
            }
        }
    }
}
