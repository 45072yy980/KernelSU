package me.diksu.ultra.foxsu.ui.screen.nocturne

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import me.diksu.ultra.R
import me.diksu.ultra.foxsu.ui.util.UpdateCenter

/**
 * 标题栏更新胶囊 —— OPPO「流动岛」那种形态：
 * 平时是一枚 30dp 高的渐变描边药丸，内部有一道高光持续横向流过；
 * 点开后在下方升起一块 Nocturne 弹层，展示版本号、更新说明与下载入口。
 *
 * 数据全部来自 [UpdateCenter]（由主页的 checkNewVersion 真实结果喂入），
 * 没有更新时整枚胶囊不占任何布局空间。
 */
@Composable
fun NcUpdateCapsule(modifier: Modifier = Modifier) {
    val t = ncTokens()
    val info = UpdateCenter.latest
    if (!UpdateCenter.hasUpdate) return

    var showSheet by remember { mutableStateOf(false) }
    val uriHandler = LocalUriHandler.current

    val flow = rememberInfiniteTransition(label = "upd-flow")
    val phase by flow.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2400, easing = LinearEasing), RepeatMode.Restart),
        label = "upd-phase",
    )
    val pulse = rememberInfiniteTransition(label = "upd-pulse")
    val dotAlpha by pulse.animateFloat(
        initialValue = 0.45f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1100, easing = LinearEasing), RepeatMode.Reverse),
        label = "upd-dot",
    )

    Box(
        modifier
            .height(30.dp)
            .clip(RoundedCornerShape(99.dp))
            .background(
                Brush.horizontalGradient(
                    listOf(
                        t.vio.copy(alpha = if (t.dark) 0.22f else 0.14f),
                        t.mint.copy(alpha = if (t.dark) 0.14f else 0.10f),
                    )
                )
            )
            .border(
                BorderStroke(
                    1.dp,
                    Brush.horizontalGradient(
                        listOf(t.vio.copy(alpha = 0.60f), t.mint.copy(alpha = 0.45f))
                    )
                ),
                RoundedCornerShape(99.dp),
            )
            .clickable { showSheet = true }
    ) {
        // 高光带：横向流动
        Canvas(Modifier.matchParentSize()) {
            val band = size.width * 0.55f
            val center = (size.width + band * 2f) * phase - band
            drawRect(
                brush = Brush.horizontalGradient(
                    colors = listOf(Color.Transparent, Color.White.copy(alpha = 0.18f), Color.Transparent),
                    startX = center - band / 2f,
                    endX = center + band / 2f,
                )
            )
        }
        Row(
            Modifier
                .height(30.dp)
                .padding(start = 10.dp, end = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            Box(
                Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(t.mint.copy(alpha = dotAlpha))
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = "v${info.versionCode}",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.2.sp,
                color = t.tx,
                maxLines = 1,
            )
            Spacer(Modifier.width(5.dp))
            Icon(
                painter = painterResource(R.drawable.fx_nc_download),
                contentDescription = stringResource(R.string.fx_module_update),
                tint = t.vio,
                modifier = Modifier.size(13.dp),
            )
        }
    }

    if (showSheet) {
        NcUpdateSheet(
            versionCode = info.versionCode,
            changelog = info.changelog,
            downloadUrl = info.downloadUrl,
            onDismiss = { showSheet = false },
            onDownload = {
                showSheet = false
                if (info.downloadUrl.isNotBlank()) uriHandler.openUri(info.downloadUrl)
            },
        )
    }
}

/** 更新详情弹层：眉题 + 大版本号 + 更新说明（可滚动）+ 下载主按钮。 */
@Composable
private fun NcUpdateSheet(
    versionCode: Int,
    changelog: String,
    downloadUrl: String,
    onDismiss: () -> Unit,
    onDownload: () -> Unit,
) {
    val t = ncTokens()
    NcBottomSheet(onDismiss = onDismiss) {
        Text(
            "UPDATE · 新版本",
            fontSize = 8.5.sp,
            letterSpacing = 2.8.sp,
            fontWeight = FontWeight.Bold,
            color = t.rbKicker,
        )
        Spacer(Modifier.height(7.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                "v$versionCode",
                fontSize = 23.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = (-0.6).sp,
                color = t.tx,
            )
            Spacer(Modifier.weight(1f))
            Text(
                stringResource(R.string.fx_new_version_available, versionCode),
                fontSize = 9.sp,
                lineHeight = 13.sp,
                fontWeight = FontWeight.Bold,
                color = t.rbHint,
            )
        }
        Spacer(Modifier.height(12.dp))

        Text(
            stringResource(R.string.fx_module_changelog),
            fontSize = 8.5.sp,
            letterSpacing = 2.4.sp,
            fontWeight = FontWeight.Bold,
            color = t.tx3,
            modifier = Modifier.padding(start = 2.dp, bottom = 7.dp),
        )
        Box(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(t.rbCellBg)
                .border(1.dp, t.rbCellBorder, RoundedCornerShape(16.dp))
                .padding(13.dp)
        ) {
            Text(
                text = changelog.ifBlank { "—" },
                fontSize = 11.5.sp,
                lineHeight = 18.sp,
                color = t.tx2,
                maxLines = 12,
                overflow = TextOverflow.Ellipsis,
            )
        }

        Spacer(Modifier.height(14.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(Brush.horizontalGradient(listOf(t.vio, t.vio2)))
                .clickable(enabled = downloadUrl.isNotBlank(), onClick = onDownload)
                .padding(vertical = 12.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                stringResource(R.string.fx_module_update),
                fontSize = 12.5.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.2.sp,
                color = Color.White,
            )
        }
        Spacer(Modifier.height(9.dp))
        NcCancelButton("取 消", onDismiss)
    }
}
