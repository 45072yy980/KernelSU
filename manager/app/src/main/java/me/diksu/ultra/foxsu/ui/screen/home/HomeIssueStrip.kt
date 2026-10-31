package me.diksu.ultra.foxsu.ui.screen.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import me.diksu.ultra.R

/**
 * 主页通用告警条 —— 把 [HomeUiState.freshIssues] 的真实告警逐条显示出来。
 *
 * Fresh / Miuix 已有各自的告警实现；Editorial / Glass / Nocturne 通过本组件补齐，
 * 保证 5 套主题对同一台设备给出完全一致的告警提示。
 *
 * @param onInstall 点击告警时的动作（真实跳转刷写页）
 */
@Composable
fun HomeIssueStrip(
    state: HomeUiState,
    modifier: Modifier = Modifier,
    accent: Color = MaterialTheme.colorScheme.error,
    onInstall: (() -> Unit)? = null,
) {
    val issues = state.freshIssues()
    if (issues.isEmpty()) return

    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        issues.forEach { issue ->
            val text = issue.issueText(state)
            val actionable = issue.actionable && onInstall != null
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(accent.copy(alpha = 0.12f))
                    .border(1.dp, accent.copy(alpha = 0.28f), RoundedCornerShape(12.dp))
                    .then(
                        if (actionable) Modifier.clickable { onInstall() } else Modifier
                    )
                    .padding(horizontal = 12.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = text,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = accent,
                    modifier = Modifier.weight(1f),
                )
                if (actionable) {
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = stringResource(R.string.fx_fresh_home_install),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color.White,
                        modifier = Modifier
                            .clip(RoundedCornerShape(99.dp))
                            .background(accent)
                            .padding(horizontal = 11.dp, vertical = 4.dp),
                    )
                }
            }
        }
    }
}

/** 告警文案：全部由真实状态推导，不含任何硬编码假信息。 */
@Composable
private fun FreshIssue.issueText(state: HomeUiState): String = when (this) {
    FreshIssue.RootMissing -> stringResource(R.string.fx_grant_root_failed)
    FreshIssue.KernelOutdated -> stringResource(
        if (state.canInstallKernelUpdate) R.string.fx_require_kernel_version
        else R.string.fx_require_kernel_version_gki
    )
    FreshIssue.ManagerOutdated -> stringResource(R.string.fx_require_manager_version)
    FreshIssue.SafeMode -> stringResource(R.string.fx_safe_mode)
    FreshIssue.NotGki -> stringResource(R.string.fx_home_gki_warning)
    FreshIssue.SelinuxPermissive -> stringResource(R.string.fx_fresh_home_selinux_permissive)
    FreshIssue.UpdateAvailable -> stringResource(
        R.string.fx_new_version_available,
        state.latestVersionInfo.versionCode,
    )
    FreshIssue.LkmUpdate -> stringResource(R.string.fx_home_lkm_update_available)
}

/** 该告警是否可以点击跳转到刷写页处理。 */
private val FreshIssue.actionable: Boolean
    get() = when (this) {
        FreshIssue.KernelOutdated, FreshIssue.LkmUpdate -> true
        else -> false
    }
