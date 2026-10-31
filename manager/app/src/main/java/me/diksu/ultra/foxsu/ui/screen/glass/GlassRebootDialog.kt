package me.diksu.ultra.foxsu.ui.screen.glass

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import me.diksu.ultra.R
import me.diksu.ultra.foxsu.ui.component.rebootlistpopup.getRebootListOption
import me.diksu.ultra.foxsu.ui.component.rebootlistpopup.rememberRebootAction
import me.diksu.ultra.foxsu.ui.theme.glassTokens

/**
 * Glass 电源面板 —— 与 Glass 设计语言一致：
 *   毛玻璃面板（白玻璃/深玻璃 + 1dp 高光描边 + 24dp 柔投影）
 *   顶部霓虹渐变圆形图标 + 呼吸光环
 *   主操作「重启」为整行渐变胶囊，其余为两列玻璃胶囊
 *   底部半透明「取消」
 * 进入时面板做 0.94→1 缩放 + 淡入。
 */
@Composable
fun GlassRebootDialog(onDismiss: () -> Unit) {
    val t = glassTokens()
    val options = getRebootListOption()
    val doReboot = rememberRebootAction()
    val primary = options.firstOrNull()
    val others = options.drop(1)

    // 面板共用的随机七彩：取自 Glass 调色板的 9 个霓虹色，每次打开顺序不同
    val rainbow = remember {
        listOf(
            t.flame, t.amber, t.lemon, t.lime, t.mint,
            t.cyan, t.blue, t.violet, t.pink,
        ).shuffled()
    }
    // 七彩呼吸相位：hue 让色环缓慢流转，breath 让圈与填充同步呼吸
    val aura = rememberInfiniteTransition(label = "aura")
    val hue by aura.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(7000, easing = LinearEasing), RepeatMode.Restart),
        label = "hue",
    )
    val auraPulse by aura.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2400, easing = LinearEasing), RepeatMode.Reverse),
        label = "auraPulse",
    )

    val enter = remember { androidx.compose.animation.core.Animatable(0f) }
    LaunchedEffect(Unit) { enter.animateTo(1f, tween(260)) }

    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .graphicsLayer {
                    val s = 0.94f + 0.06f * enter.value
                    scaleX = s
                    scaleY = s
                    alpha = enter.value
                }
                .shadow(
                    24.dp,
                    RoundedCornerShape(28.dp),
                    ambientColor = t.glassShadow,
                    spotColor = t.glassShadow,
                )
                .clip(RoundedCornerShape(28.dp))
                .background(if (t.isDark) Color(0xF714141E) else Color(0xF7FFFFFF))
                .border(
                    1.dp,
                    if (t.isDark) Color(0x1FFFFFFF) else Color(0xFFFFFFFF),
                    RoundedCornerShape(28.dp),
                )
                .padding(horizontal = 18.dp, vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // 霓虹图标 + 呼吸光环
            Box(contentAlignment = Alignment.Center) {
                val hud = rememberInfiniteTransition(label = "reboot")
                val breath by hud.animateFloat(
                    initialValue = 0f,
                    targetValue = 1f,
                    animationSpec = infiniteRepeatable(tween(2200, easing = LinearEasing), RepeatMode.Restart),
                    label = "breath",
                )
                // 外层呼吸辉光环：纯圆环（不用 blur，避免被裁成方框），沿圈扩散
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .graphicsLayer {
                            val s = 1f + breath * 0.20f
                            scaleX = s
                            scaleY = s
                            alpha = (0.55f + 0.45f * (1f - breath)) * 0.7f
                        }
                        .border(
                            width = 2.dp,
                            brush = Brush.sweepGradient(
                                hueShift(rainbow, hue) + hueShift(rainbow, hue).first()
                            ),
                            shape = CircleShape,
                        ),
                )
                // 第二层外扩环：更细、更淡，形成双环呼吸
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .graphicsLayer {
                            val s = 1f + breath * 0.34f
                            scaleX = s
                            scaleY = s
                            alpha = (1f - breath) * 0.42f
                        }
                        .border(
                            width = 1.5.dp,
                            brush = Brush.sweepGradient(rainbow + rainbow.first()),
                            shape = CircleShape,
                        ),
                )
                // 内核圆：真正的七彩描边，紧紧贴着圆边
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .shadow(
                            12.dp,
                            CircleShape,
                            ambientColor = rainbow.first().copy(alpha = 0.26f),
                            spotColor = rainbow.last().copy(alpha = 0.26f),
                        )
                        .clip(CircleShape)
                        .background(if (t.isDark) Color(0xCC1A1A24) else Color(0xCCFFFAF8))
                        .animatedRainbowRing(CircleShape, 2.dp, rainbow, hue, breath),
                    contentAlignment = Alignment.Center,
                ) {

                    Icon(
                        painter = painterResource(R.drawable.fx_ic_d_power2),
                        contentDescription = null,
                        modifier = Modifier.size(24.dp),
                        tint = t.flame,
                    )
                }
            }
            Spacer(Modifier.height(14.dp))
            Text(
                text = stringResource(R.string.fx_reboot),
                fontSize = 18.sp,
                fontWeight = FontWeight.ExtraBold,
                color = t.ink,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.fx_glass_reboot_hint),
                fontSize = 11.sp,
                color = t.sub,
            )
            Spacer(Modifier.height(18.dp))

            // 主操作：重启（白玻璃 + 七彩外圈，与英雄卡同一套描边语言）
            if (primary != null) {
                val interaction = remember { MutableInteractionSource() }
                val pressed by interaction.collectIsPressedAsState()
                val scale by animateFloatAsState(if (pressed) 0.97f else 1f, tween(120), label = "press")
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .graphicsLayer {
                            scaleX = scale
                            scaleY = scale
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    // 呼吸光晕：向外扩散的七彩柔光（半透明，不遮挡内容）
                    BreathAura(
                        shape = RoundedCornerShape(99.dp),
                        colors = rainbow,
                        hue = hue,
                        pulse = auraPulse,
                        strength = 0.30f,
                    )
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .shadow(
                            14.dp,
                            RoundedCornerShape(99.dp),
                            ambientColor = rainbow.first().copy(alpha = 0.30f),
                            spotColor = rainbow.last().copy(alpha = 0.30f),
                        )
                        .clip(RoundedCornerShape(99.dp))
                        .background(
                            if (t.isDark) {
                                Brush.linearGradient(listOf(Color(0xCC1A1A24), Color(0xCC1A1A24)))
                            } else {
                                Brush.linearGradient(listOf(Color(0xCCFFFAF8), Color(0xCCFCF8FF)))
                            }
                        )
                        .animatedRainbowRing(RoundedCornerShape(99.dp), 1.7.dp, rainbow, hue, auraPulse)
                        .clickable(interactionSource = interaction, indication = null) {
                            onDismiss()
                            doReboot(primary.reason)
                        }
                        .padding(vertical = 14.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            painter = painterResource(R.drawable.fx_ic_d_refresh),
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = t.flame,
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = stringResource(primary.labelRes),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.ExtraBold,
                            style = androidx.compose.ui.text.TextStyle(brush = t.glineGrad),
                        )
                    }
                }
                }
                Spacer(Modifier.height(12.dp))
            }

            // 其余选项：两列玻璃胶囊；落单的最后一项（EDL）占整行，与「重启」同款胶囊
            others.chunked(2).forEachIndexed { chunkIndex, chunk ->
                if (chunk.size == 1) {
                    val option = chunk.first()
                    GlassWidePill(
                        label = stringResource(option.labelRes),
                        iconRes = R.drawable.fx_ic_d_power2,
                        ring = rainbow,
                        hue = hue,
                        pulse = auraPulse,
                        onClick = {
                            onDismiss()
                            doReboot(option.reason)
                        },
                    )
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        chunk.forEachIndexed { i, option ->
                            val accent = rainbow[(chunkIndex * 2 + i) % rainbow.size]
                            GlassOptionChip(
                                modifier = Modifier.weight(1f),
                                label = stringResource(option.labelRes),
                                accent = accent,
                                ring = rainbow,
                                onClick = {
                                    onDismiss()
                                    doReboot(option.reason)
                                },
                            )
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))
            }

            Spacer(Modifier.height(2.dp))
            Text(
                text = stringResource(android.R.string.cancel),
                fontSize = 12.5.sp,
                fontWeight = FontWeight.Bold,
                color = t.faint,
                textAlign = TextAlign.Center,
                maxLines = 1,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .clickable(onClick = onDismiss)
                    .padding(vertical = 10.dp),
            )
        }
    }
}

/** 玻璃选项胶囊：白色玻璃底 + 同色小圆点 + 标签，按下轻微缩放 */
@Composable
private fun GlassOptionChip(
    modifier: Modifier = Modifier,
    label: String,
    accent: Color,
    ring: List<Color>,
    onClick: () -> Unit,
) {
    val t = glassTokens()
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.96f else 1f, tween(120), label = "chip")
    Row(
        modifier = modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(RoundedCornerShape(14.dp))
            .background(if (t.isDark) Color(0x14FFFFFF) else Color(0xE6FFFFFF))
            .rainbowRing(RoundedCornerShape(14.dp), 1.2.dp, ring)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(7.dp)
                .clip(CircleShape)
                .background(accent),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = label,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = t.ink2,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * 七彩外圈：把调色板色首尾闭合后做 sweep 渐变描边，
 * 视觉上与英雄卡外圈一致，但换成整圈彩虹（对应「七彩圈」）。
 */
@Composable
private fun Modifier.rainbowRing(
    shape: androidx.compose.ui.graphics.Shape,
    width: androidx.compose.ui.unit.Dp,
    colors: List<Color>,
): Modifier = this.border(
    width = width,
    brush = Brush.sweepGradient(colors + colors.first()),
    shape = shape,
)

/** 整行七彩胶囊：与主操作「重启」同款（半透明玻璃 + 动态七彩圈 + 呼吸光晕 + 渐变文字） */
@Composable
private fun GlassWidePill(
    label: String,
    iconRes: Int,
    ring: List<Color>,
    hue: Float,
    pulse: Float,
    onClick: () -> Unit,
) {
    val t = glassTokens()
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.97f else 1f, tween(120), label = "wide")
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            },
        contentAlignment = Alignment.Center,
    ) {
        BreathAura(
            shape = RoundedCornerShape(99.dp),
            colors = ring,
            hue = hue,
            pulse = pulse,
            strength = 0.30f,
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .shadow(
                    14.dp,
                    RoundedCornerShape(99.dp),
                    ambientColor = ring.first().copy(alpha = 0.30f),
                    spotColor = ring.last().copy(alpha = 0.30f),
                )
                .clip(RoundedCornerShape(99.dp))
                .background(
                    if (t.isDark) {
                        Brush.linearGradient(listOf(Color(0xCC1A1A24), Color(0xCC1A1A24)))
                    } else {
                        Brush.linearGradient(listOf(Color(0xCCFFFAF8), Color(0xCCFCF8FF)))
                    }
                )
                .animatedRainbowRing(RoundedCornerShape(99.dp), 1.7.dp, ring, hue, pulse)
                .clickable(interactionSource = interaction, indication = null, onClick = onClick)
                .padding(vertical = 14.dp),
            contentAlignment = Alignment.Center,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    painter = painterResource(iconRes),
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = t.flame,
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = label,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.ExtraBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = androidx.compose.ui.text.TextStyle(brush = t.glineGrad),
                )
            }
        }
    }
}

/**
 * 呼吸七彩外圈：与胶囊/圆形完全同尺寸同形状（matchParentSize），
 * 从边缘(1.0)向外扩散，贴边最亮、扩散渐隐——光始终「贴在边上」呼吸。
 */
@Composable
private fun androidx.compose.foundation.layout.BoxScope.BreathAura(
    shape: androidx.compose.ui.graphics.Shape,
    colors: List<Color>,
    hue: Float,
    pulse: Float,
    strength: Float,
) {
    // grow: 外扩倍率权重；width: 描边宽度；weight: 透明度权重
    val layers = listOf(
        Triple(1f, 3.dp, 1f),
        Triple(2f, 2.dp, 0.5f),
    )
    layers.forEachIndexed { index, (grow, width, weight) ->
        val shifted = hueShift(colors, hue + index * 0.1f)
        Box(
            modifier = Modifier
                .matchParentSize()
                .graphicsLayer {
                    scaleX = 1f + pulse * 0.045f * grow
                    scaleY = 1f + pulse * 0.09f * grow
                    // 贴边(pulse=0)最亮，向外扩散渐隐
                    alpha = (1f - pulse) * 0.6f * weight * (strength / 0.30f)
                }
                .border(
                    width = width,
                    brush = Brush.sweepGradient(shifted + shifted.first()),
                    shape = shape,
                ),
        )
    }
}

/**
 * 动态七彩描边：在静态七彩圈外再叠一层高速旋转的高光段，
 * 让"呼吸"不仅体现在透明度上，也能看到光在圈上跑动。
 */
@Composable
private fun Modifier.animatedRainbowRing(
    shape: androidx.compose.ui.graphics.Shape,
    width: androidx.compose.ui.unit.Dp,
    colors: List<Color>,
    hue: Float,
    pulse: Float,
): Modifier = this
    .border(
        width = width,
        brush = Brush.sweepGradient(colors + colors.first()),
        shape = shape,
    )
    .border(
        width = width,
        brush = Brush.sweepGradient(
            hueShift(colors, hue).mapIndexed { i, c ->
                // 前 1/4 段提亮，其余压低 → 一道高光沿圈流动
                if (i < colors.size / 4 + 1) c.copy(alpha = 0.45f + 0.50f * pulse)
                else c.copy(alpha = 0f)
            }
        ),
        shape = shape,
    )

/** 按 hue∈[0,1) 循环右移调色板（色相流转） */
private fun hueShift(colors: List<Color>, hue: Float): List<Color> {
    if (colors.isEmpty()) return colors
    val n = colors.size
    val shift = ((hue % 1f + 1f) % 1f * n).toInt()
    return List(n) { i -> colors[(i + shift) % n] }
}
