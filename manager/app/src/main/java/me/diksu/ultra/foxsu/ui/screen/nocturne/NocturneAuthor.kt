package me.diksu.ultra.foxsu.ui.screen.nocturne

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import me.diksu.ultra.BuildConfig
import me.diksu.ultra.R

/**
 * 关于作者 —— 严格对照视觉稿 ⑥ 关于作者：
 * AUTHOR · 造物者 / 关于 + 返回/资料按钮 → acard（66 头像 + 虚线外环 + 名字 + 角色 + 徽章行）
 * → aquote 引言 → stat3 数据条 → CONTACT · 联系 → PROJECTS · 作品。
 *
 * 所有数值与链接均来自项目真实信息，不使用设计稿示例中的假数据。
 */
@Composable
fun NocturneAuthorScreen(
    onBack: () -> Unit,
    onOpenUrl: (String) -> Unit,
) {
    val t = ncTokens()

    Box(Modifier.fillMaxSize()) {
        NcGlowBackground(t)
        Column(Modifier.fillMaxSize().padding(top = ncStatusTop() + 2.dp)) {
            Box(Modifier.padding(horizontal = 18.dp)) {
                NcHeader(kicker = "AUTHOR · 造物者", title = "关于") {
                    NcIconButton(iconRes = R.drawable.fx_nc_back, onClick = onBack)
                    NcIconButton(iconRes = R.drawable.fx_nc_user, onClick = {}, active = true)
                }
            }
            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(start = 18.dp, end = 18.dp, bottom = 40.dp),
            ) {

                // acard
                NcGradientBorder(
                    radius = 24.dp,
                    borderColors = t.acardBorder,
                    borderStops = t.acardBorderStops,
                    borderAngle = 150f,
                    cardColors = t.acardInner,
                    cardStops = t.acardInnerStops,
                    cardAngle = 165f,
                ) {
                    Box(Modifier.fillMaxWidth()) {
                        // 左下 170px 光晕
                        Box(
                            Modifier
                                .size(170.dp)
                                .align(Alignment.BottomStart)
                                .offset(x = (-60).dp, y = 90.dp)
                        ) {
                            Canvas(Modifier.fillMaxSize()) {
                                drawRect(
                                    brush = Brush.radialGradient(
                                        0.0f to t.acardGlow,
                                        0.65f to t.acardGlow.copy(alpha = t.acardGlow.alpha * 0.18f),
                                        1.0f to Color.Transparent,
                                        center = androidx.compose.ui.geometry.Offset(
                                            size.width / 2f,
                                            size.height / 2f,
                                        ),
                                        radius = size.minDimension / 2f,
                                    )
                                )
                            }
                        }
                        Row(
                            Modifier.padding(horizontal = 17.dp, vertical = 18.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            // 66 头像 + 虚线外环
                            Box(Modifier.size(78.dp), contentAlignment = Alignment.Center) {
                                Canvas(Modifier.fillMaxSize()) {
                                    drawCircle(
                                        color = if (t.dark) Color(0x38FFFFFF) else Color(0x66A9AFC0),
                                        radius = size.width / 2 - 3.dp.toPx(),
                                        style = Stroke(
                                            width = 1.dp.toPx(),
                                            pathEffect = PathEffect.dashPathEffect(
                                                floatArrayOf(4.dp.toPx(), 4.dp.toPx()),
                                            ),
                                        ),
                                    )
                                }
                                Box(
                                    Modifier
                                        .size(66.dp)
                                        .clip(CircleShape)
                                        .background(Brush.linearGradient(listOf(t.vio, t.vio2))),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    // 头像位改用启动器图标
                                    me.diksu.ultra.foxsu.ui.component.LauncherMark(
                                        modifier = Modifier.size(50.dp),
                                        cornerPercent = 28,
                                    )
                                }
                            }
                            Spacer(Modifier.width(14.dp))
                            Column {
                                Row(verticalAlignment = Alignment.Bottom) {
                                    Text(
                                        "Fox",
                                        fontSize = 21.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        letterSpacing = (-0.6).sp,
                                        color = t.tx,
                                    )
                                    Spacer(Modifier.width(7.dp))
                                    Text("@Cmry-mlc", fontSize = 10.5.sp, fontWeight = FontWeight.SemiBold, color = t.tx2)
                                }
                                Spacer(Modifier.height(6.dp))
                                Text(
                                    "FOXS 维护者 ·\nINTERFACE DESIGNER",
                                    fontSize = 9.sp,
                                    letterSpacing = 1.8.sp,
                                    lineHeight = 14.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = t.mint,
                                )
                                Spacer(Modifier.height(9.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    AuthorChip("MAINTAINER", t.chipVioletFg, t.chipVioletBg)
                                    AuthorChip("GPL-3.0", t.chipFg)
                                    AuthorChip("SINCE 2020", t.chipFg)
                                }
                            }
                        }
                    }
                }

                // aquote
                Spacer(Modifier.height(14.dp))
                Row(Modifier.padding(horizontal = 14.dp)) {
                    Box(Modifier.width(2.dp).height(46.dp).background(t.quoteBar))
                    Spacer(Modifier.width(10.dp))
                    Text(
                        "基于 KernelSU 管理器深度定制的版本，FoxSu 为其重新设计了整套界面语言，Nocturne 主题即出自这里。",
                        fontSize = 10.5.sp,
                        lineHeight = 18.sp,
                        color = t.quoteText,
                    )
                }

                Spacer(Modifier.height(12.dp))
                NcStat3(
                    listOf(
                        Triple("版本", BuildConfig.VERSION_NAME, t.vio),
                        Triple("主题", "Nocturne", t.mint),
                        Triple("许可", "GPL-3.0", t.tx),
                    )
                )

                NcSectionLabel("CONTACT · 联系")
                NcGroupCard {
                    NcRow(
                        iconRes = R.drawable.fx_nc_link,
                        title = "GitHub 仓库",
                        subtitle = "github.com/Cmry-mlc/FoxSu",
                        subtitleMono = true,
                        accent = NcAccentTone.Violet,
                        onClick = { onOpenUrl("https://github.com/Cmry-mlc/FoxSu") },
                        trailing = { NcChevron() },
                    )
                    NcRow(
                        iconRes = R.drawable.fx_nc_doc,
                        title = "问题反馈",
                        subtitle = "github.com/Cmry-mlc/FoxSu/issues",
                        subtitleMono = true,
                        accent = NcAccentTone.Default,
                        showDivider = true,
                        onClick = { onOpenUrl("https://github.com/Cmry-mlc/FoxSu/issues") },
                        trailing = { NcChevron() },
                    )
                    NcRow(
                        iconRes = R.drawable.fx_nc_down_box,
                        title = "版本发布",
                        subtitle = "github.com/Cmry-mlc/FoxSu/releases",
                        subtitleMono = true,
                        accent = NcAccentTone.Mint,
                        showDivider = true,
                        onClick = { onOpenUrl("https://github.com/Cmry-mlc/FoxSu/releases") },
                        trailing = { NcChevron() },
                    )
                }

                NcSectionLabel("PROJECTS · 作品")
                NcGroupCard {
                    NcRow(
                        iconRes = R.drawable.fx_nc_power,
                        title = "FoxSu Manager",
                        subtitle = "内核级 Root 管理器 · 主项目",
                        accent = NcAccentTone.Violet,
                        onClick = { onOpenUrl("https://github.com/Cmry-mlc/FoxSu") },
                        trailing = { NcChevron() },
                    )
                    NcRow(
                        iconRes = R.drawable.fx_nc_palette,
                        title = "Nocturne Theme",
                        subtitle = "暗夜主题引擎 · 本界面",
                        accent = NcAccentTone.Mint,
                        showDivider = true,
                        onClick = { onOpenUrl("https://github.com/Cmry-mlc/FoxSu") },
                        trailing = { NcChevron() },
                    )
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun AuthorChip(text: String, color: Color, bg: Color = Color(0x0FFFFFFF)) {
    Box(
        Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(bg)
            .padding(horizontal = 7.dp, vertical = 3.dp)
    ) {
        Text(text, fontSize = 8.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 0.9.sp, color = color)
    }
}
