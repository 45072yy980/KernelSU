package me.diksu.ultra.foxsu.ui.screen.nocturne

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import me.diksu.ultra.R
import me.diksu.ultra.foxsu.ui.component.rebootlistpopup.getRebootListOption
import me.diksu.ultra.foxsu.ui.component.rebootlistpopup.rememberRebootAction

/**
 * 重启弹层 —— 按 Nocturne 设计语言重构：
 *
 * ```
 * grab
 * POWER · 电源                  长按电源键 3 秒
 * 重启                          可直接强制重启
 * ┌ rbpri 主项 ─────────────────────────────┐
 * │ [渐变紫图标] 重启 / reboot            > │
 * └─────────────────────────────────────────┘
 * OTHER · 其他重启方式
 * [软重启] [Recovery]
 * [Bootloader] [Download]
 * DANGER · 高危操作
 * [EDL]
 * [取 消]
 * ```
 *
 * 层级用「眉题 + 分区标题 + 卡片」三段式，与主页 / 设置页一致；
 * 选项、副标题、点击行为全部来自系统真实的 getRebootListOption() / rememberRebootAction()。
 */
@Composable
fun NcRebootSheet(onDismiss: () -> Unit) {
    val t = ncTokens()
    val options = getRebootListOption()
    val doReboot = rememberRebootAction()
    val primary = options.firstOrNull()
    val others = options.drop(1)
    val edl = others.lastOrNull { it.reason == "edl" }
    val grid = others.filter { it !== edl }

    NcBottomSheet(onDismiss = onDismiss) {
        // 眉题
        Text(
            "POWER · 电源",
            fontSize = 8.5.sp,
            letterSpacing = 2.8.sp,
            fontWeight = FontWeight.Bold,
            color = t.rbKicker,
        )
        Spacer(Modifier.height(7.dp))

        // 标题 + 右侧提示
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                stringResource(R.string.fx_reboot),
                fontSize = 23.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = (-0.6).sp,
                color = t.tx,
            )
            Spacer(Modifier.weight(1f))
            Text(
                "长按电源键 3 秒\n可直接强制重启",
                fontSize = 9.sp,
                lineHeight = 13.sp,
                color = t.rbHint,
                fontWeight = FontWeight.Bold,
                textAlign = androidx.compose.ui.text.style.TextAlign.End,
            )
        }
        Spacer(Modifier.height(14.dp))

        // 主项 rbpri
        if (primary != null) {
            RbPrimary(
                title = stringResource(primary.labelRes),
                reason = primary.reason.ifEmpty { "reboot" },
                onClick = { onDismiss(); doReboot(primary.reason) },
            )
        }

        // 其他重启方式
        if (grid.isNotEmpty()) {
            RbGroupLabel("OTHER · 其他重启方式")
            grid.chunked(2).forEachIndexed { index, rowItems ->
                if (index > 0) Spacer(Modifier.height(9.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                    rowItems.forEach { opt ->
                        RbCell(
                            title = stringResource(opt.labelRes),
                            sub = opt.reason,
                            modifier = Modifier.weight(1f),
                            onClick = { onDismiss(); doReboot(opt.reason) },
                        )
                    }
                    if (rowItems.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        }

        // 高危：EDL
        if (edl != null) {
            RbGroupLabel("DANGER · 高危操作")
            RbDanger(
                title = stringResource(edl.labelRes),
                sub = edl.reason,
                onClick = { onDismiss(); doReboot(edl.reason) },
            )
        }

        Spacer(Modifier.height(12.dp))
        NcCancelButton("取 消", onDismiss)
    }
}

/** 分区标题 —— 与主页 NcSectionLabel 同一条视觉规则，但间距按弹层收紧。 */
@Composable
private fun RbGroupLabel(text: String) {
    val t = ncTokens()
    Text(
        text = text,
        fontSize = 8.5.sp,
        letterSpacing = 2.4.sp,
        fontWeight = FontWeight.Bold,
        color = t.tx3,
        modifier = Modifier.padding(start = 2.dp, top = 18.dp, bottom = 8.dp),
    )
}

/** rbpri —— r20，渐变底 + 45% 描边 + 右上紫色光晕 + 44 渐变图标。 */
@Composable
private fun RbPrimary(title: String, reason: String, onClick: () -> Unit) {
    val t = ncTokens()
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val sc by animateFloatAsState(
        if (pressed) 0.985f else 1f,
        spring(0.6f),
        label = "pri",
    )
    Box(
        Modifier
            .fillMaxWidth()
            .scale(sc)
            .clip(RoundedCornerShape(20.dp))
            .background(Brush.linearGradient(t.rbPriBg))
            .border(1.dp, t.rbPriBorder, RoundedCornerShape(20.dp))
            .clickable(interactionSource = src, indication = null, onClick = onClick)
    ) {
        // 右上 150px 紫色光晕（随卡片裁剪，不外溢）
        Canvas(Modifier.matchParentSize()) {
            drawRect(
                brush = Brush.radialGradient(
                    0.0f to t.rbPriGlow,
                    0.55f to t.rbPriGlow.copy(alpha = t.rbPriGlow.alpha * 0.22f),
                    1.0f to Color.Transparent,
                    center = Offset(size.width - 28.dp.toPx(), -6.dp.toPx()),
                    radius = size.height * 1.25f,
                )
            )
        }
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 15.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(15.dp))
                    .background(Brush.linearGradient(listOf(t.vio, t.vio2))),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painterResource(R.drawable.fx_nc_power),
                    null,
                    tint = Color.White,
                    modifier = Modifier.size(20.dp),
                )
            }
            Spacer(Modifier.width(13.dp))
            Column(Modifier.weight(1f)) {
                Text(title, fontSize = 15.5.sp, fontWeight = FontWeight.Bold, color = t.rbPriText)
                Spacer(Modifier.height(3.dp))
                Text(reason, fontSize = 9.sp, fontFamily = ncMono, color = t.rbPriSub)
            }
            Icon(
                painterResource(R.drawable.fx_nc_chev_big),
                null,
                tint = t.rbPriArrow,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

/** rbgrid 单元：r16 bg + 发丝描边，图标 30 r10，标题 13/700，副 mono 9。 */
@Composable
private fun RbCell(title: String, sub: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val t = ncTokens()
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val sc by animateFloatAsState(
        if (pressed) 0.97f else 1f,
        spring(0.6f),
        label = "cell",
    )
    Column(
        modifier
            .scale(sc)
            .clip(RoundedCornerShape(16.dp))
            .background(t.rbCellBg)
            .border(1.dp, t.rbCellBorder, RoundedCornerShape(16.dp))
            .clickable(interactionSource = src, indication = null, onClick = onClick)
            .padding(13.dp)
    ) {
        Box(
            Modifier
                .size(30.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(t.rbCellIconBg),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painterResource(rebootIcon(sub)),
                null,
                tint = t.rbCellIconFg,
                modifier = Modifier.size(15.dp),
            )
        }
        Spacer(Modifier.height(11.dp))
        Text(
            title,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            color = t.rbCellTitle,
            maxLines = 1,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(3.dp))
        Text(
            sub,
            fontSize = 9.sp,
            fontFamily = ncMono,
            color = t.rbCellSub,
            maxLines = 1,
        )
    }
}

/** rbdanger —— 红 7% 底 + 24% 描边 + 红 14% 图标底。 */
@Composable
private fun RbDanger(title: String, sub: String, onClick: () -> Unit) {
    val t = ncTokens()
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val sc by animateFloatAsState(
        if (pressed) 0.98f else 1f,
        spring(0.6f),
        label = "danger",
    )
    Row(
        Modifier
            .fillMaxWidth()
            .scale(sc)
            .clip(RoundedCornerShape(16.dp))
            .background(t.rbDangerBg)
            .border(1.dp, t.rbDangerBorder, RoundedCornerShape(16.dp))
            .clickable(interactionSource = src, indication = null, onClick = onClick)
            .padding(horizontal = 13.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(32.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(t.rbDangerIconBg),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painterResource(R.drawable.fx_nc_bolt),
                null,
                tint = t.rbDangerIconFg,
                modifier = Modifier.size(16.dp),
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = t.rbDangerTitle, maxLines = 1)
            Spacer(Modifier.height(3.dp))
            Text(sub, fontSize = 9.sp, fontFamily = ncMono, color = t.rbDangerSub, maxLines = 1)
        }
        Icon(painterResource(R.drawable.fx_nc_chev), null, tint = t.rbDangerArrow, modifier = Modifier.size(15.dp))
    }
}

private fun rebootIcon(reason: String): Int = when {
    reason.contains("recovery", true) -> R.drawable.fx_nc_recovery
    reason.contains("bootloader", true) || reason.contains("fastboot", true) -> R.drawable.fx_nc_boot
    reason.contains("download", true) || reason.contains("odin", true) -> R.drawable.fx_nc_download
    reason.contains("soft", true) -> R.drawable.fx_nc_soft
    else -> R.drawable.fx_nc_refresh
}
