package me.diksu.ultra.foxsu.ui.screen.glass

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import me.diksu.ultra.R
import me.diksu.ultra.foxsu.ui.screen.flash.FlashUiState
import me.diksu.ultra.foxsu.ui.screen.flash.FlashScreenActions
import me.diksu.ultra.foxsu.ui.screen.flash.FlashingStatus
import me.diksu.ultra.foxsu.ui.theme.glassTokens

/**
 * Glass 刷写 / 重启页 —— 与设计稿同风格：
 *   顶栏（圆形返回按钮 + 19px 标题 + 保存日志按钮）
 *   控制台日志：白玻璃面板 + 等宽字体 + 顶部状态点
 *   底部霓虹渐变「重启」胶囊按钮
 */
@Composable
fun GlassFlashScreen(
    state: FlashUiState,
    actions: FlashScreenActions,
) {
    val t = glassTokens()
    val scroll = rememberScrollState()
    val statusColor = when (state.flashingStatus) {
        FlashingStatus.FLASHING -> t.amber
        FlashingStatus.SUCCESS -> t.lime
        FlashingStatus.FAILED -> t.flame
    }
    val titleRes = when (state.flashingStatus) {
        FlashingStatus.FLASHING -> R.string.fx_flashing
        FlashingStatus.SUCCESS -> R.string.fx_flash_success
        FlashingStatus.FAILED -> R.string.fx_flash_failed
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(horizontal = 13.dp),
    ) {
        // 顶栏
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 10.dp, bottom = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .glassCard(99.dp)
                    .clickable(onClick = actions.onBack),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(R.drawable.fx_ic_d_back),
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = t.ink2,
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(titleRes),
                    fontSize = 19.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = t.ink,
                    maxLines = 1,
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(top = 3.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(statusColor),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = stringResource(R.string.fx_glass_flash_sub),
                        fontSize = 11.sp,
                        color = t.sub,
                        maxLines = 1,
                    )
                }
            }
            Spacer(Modifier.width(8.dp))
            Box(
                modifier = Modifier
                    .size(width = 38.dp, height = 34.dp)
                    .glassCard(99.dp)
                    .clickable(onClick = actions.onSaveLog),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(R.drawable.fx_ic_d_download),
                    contentDescription = stringResource(R.string.fx_save_log),
                    modifier = Modifier.size(15.dp),
                    tint = t.ink2,
                )
            }
        }

        // 控制台
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .glassCard(22.dp),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(scroll)
                    .padding(15.dp),
            ) {
                LaunchedEffect(state.text) {
                    scroll.animateScrollTo(scroll.maxValue)
                }
                Text(
                    text = state.text,
                    fontSize = 10.5.sp,
                    lineHeight = 16.sp,
                    fontFamily = FontFamily.Monospace,
                    color = if (t.isDark) t.ink2 else Color(0xFF3A3A46),
                )
            }
        }

        // 重启按钮
        if (state.showRebootAction) {
            Spacer(Modifier.height(14.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 20.dp)
                    .shadow(
                        14.dp,
                        RoundedCornerShape(99.dp),
                        ambientColor = t.flame.copy(alpha = 0.35f),
                        spotColor = t.violet.copy(alpha = 0.35f),
                    )
                    .clip(RoundedCornerShape(99.dp))
                    .background(t.dockGrad)
                    .clickable(onClick = actions.onReboot)
                    .padding(vertical = 15.dp),
                contentAlignment = Alignment.Center,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        painter = painterResource(R.drawable.fx_ic_d_refresh),
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = Color.White,
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = stringResource(state.rebootLabelRes),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color.White,
                    )
                }
            }
        } else {
            Spacer(Modifier.height(20.dp))
        }
    }
}
