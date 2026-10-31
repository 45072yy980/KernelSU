package me.diksu.ultra.foxsu.ui.screen.nocturne

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import me.diksu.ultra.R

/**
 * 主题选择弹层 —— 严格对照设计稿 ⑦ 主题选择：
 * sheet r30 渐变 + grab → 「主题 / 外观与强调色」→ 模式（3 张 tcard：Nocturne 暗夜 / Daylight 亮色 / 跟随系统）
 * → 强调色（6 色 30 圆，选中外环）→ 选项（跟随日出日落 开关 / 光晕强度）。
 *
 * 所有选择真实写入 NcAppearance（SharedPreferences 持久化），立即生效。
 */
@Composable
fun NcThemeSheet(onDismiss: () -> Unit) {
    val t = ncTokens()

    NcBottomSheet(onDismiss = onDismiss) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text("主题", fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, color = t.tx)
            Spacer(Modifier.weight(1f))
            Text("外观与强调色", fontSize = 10.sp, color = t.tx3, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(10.dp))

        SheetSectionLabel("模式")
        Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
            ModeCard("Nocturne", "暗夜", 0, Modifier.weight(1f)) { NcAppearance.updateMode(0) }
            ModeCard("Daylight", "亮色", 1, Modifier.weight(1f)) { NcAppearance.updateMode(1) }
            ModeCard("跟随", "系统", 2, Modifier.weight(1f)) { NcAppearance.updateMode(2) }
        }

        SheetSectionLabel("强调色")
        Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
            NcAccents.forEachIndexed { i, accent ->
                Box(
                    Modifier
                        .size(34.dp)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) { NcAppearance.setAccent(i) },
                    contentAlignment = Alignment.Center,
                ) {
                    if (NcAppearance.accentIndex == i) {
                        Box(
                            Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .border(1.6.dp, t.swatchSelRing, CircleShape)
                        )
                    }
                    Box(
                        Modifier
                            .size(26.dp)
                            .clip(CircleShape)
                            .background(Brush.linearGradient(listOf(accent.darkMain, accent.darkDeep)))
                    )
                }
            }
        }

        SheetSectionLabel("选项")
        OptionRow(
            iconRes = R.drawable.fx_nc_refresh,
            title = "跟随日出日落",
            sub = "auto switch 18:30 / 06:30",
            trailing = {
                NcSwitch(NcAppearance.followSunset) { NcAppearance.updateFollowSunset(it) }
            },
        )
        OptionRow(
            iconRes = R.drawable.fx_nc_palette,
            title = "光晕强度",
            sub = "glow intensity · ${when (NcAppearance.glowLevel) { 0 -> "低"; 2 -> "高"; else -> "中" }}",
            onClick = { NcAppearance.setGlow((NcAppearance.glowLevel + 1) % 3) },
            trailing = {
                Icon(painterResource(R.drawable.fx_nc_chev), null, tint = t.tx3, modifier = Modifier.size(16.dp))
            },
        )
    }
}

@Composable
private fun SheetSectionLabel(text: String) {
    val t = ncTokens()
    Text(
        text,
        fontSize = 9.sp,
        letterSpacing = 2.sp,
        fontWeight = FontWeight.ExtraBold,
        color = t.tx3,
        modifier = Modifier.padding(start = 2.dp, top = 12.dp, bottom = 6.dp),
    )
}

/** tcard：r16 padding 8，缩略图 54 r11，选中 violet 75% 描边 + 3px 外环。 */
@Composable
private fun ModeCard(
    title: String,
    sub: String,
    index: Int,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val t = ncTokens()
    val on = NcAppearance.mode == index
    Column(
        modifier
            .clip(RoundedCornerShape(16.dp))
            .background(if (on) t.tcardSelBg else t.tcardBg)
            .border(
                width = if (on) 1.dp else 1.dp,
                color = if (on) t.tcardSelBorder else t.tcardBorder,
                shape = RoundedCornerShape(16.dp),
            )
            .clickable(onClick = onClick)
            .padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // 缩略图
        Box(
            Modifier
                .fillMaxWidth()
                .height(54.dp)
                .clip(RoundedCornerShape(11.dp))
                .background(
                    when (index) {
                        0 -> Brush.linearGradient(listOf(Color(0xFF0B0D13), Color(0xFF0B0D13)))
                        1 -> Brush.linearGradient(listOf(Color(0xFFF5F6FA), Color(0xFFF5F6FA)))
                        else -> Brush.verticalGradient(listOf(Color(0xFF161A28), Color(0xFFF5F6FA)), 0f, 220f)
                    }
                )
                .padding(8.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.fillMaxSize()) {
                MiniBar(width = 0.7f, color = t.vio.copy(alpha = 0.75f))
                MiniBar(width = 0.9f, color = if (index == 1) Color(0x1F0F142D) else t.grab)
                MiniBar(width = 0.55f, color = if (index == 1) Color(0x120F142D) else Color(0x1AFFFFFF))
                if (index != 2) MiniBar(width = 0.4f, color = t.mint.copy(alpha = 0.55f))
            }
        }
        Spacer(Modifier.height(7.dp))
        Text(
            title,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            color = if (on) t.vio else t.tx,
            textAlign = TextAlign.Center,
        )
        Text(sub, fontSize = 8.sp, color = t.tx3, letterSpacing = 0.6.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun MiniBar(width: Float, color: Color) {
    Box(Modifier.fillMaxWidth(width).height(6.dp).clip(RoundedCornerShape(3.dp)).background(color))
}

/** ritem：36 r12 图标 + 标题/副标题 + 右侧控件。 */
@Composable
private fun OptionRow(
    iconRes: Int,
    title: String,
    sub: String,
    onClick: () -> Unit = {},
    trailing: @Composable () -> Unit,
) {
    val t = ncTokens()
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
            .padding(horizontal = 2.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(36.dp).clip(RoundedCornerShape(12.dp)).background(t.fill),
            contentAlignment = Alignment.Center,
        ) {
            Icon(painterResource(iconRes), null, tint = t.iconTint, modifier = Modifier.size(17.dp))
        }
        Spacer(Modifier.width(13.dp))
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold, color = t.tx)
            Spacer(Modifier.height(2.dp))
            Text(sub, fontSize = 9.5.sp, fontFamily = ncMono, color = t.tx3)
        }
        trailing()
    }
}
