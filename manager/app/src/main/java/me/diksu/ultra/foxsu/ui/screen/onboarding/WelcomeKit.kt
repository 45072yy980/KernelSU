package me.diksu.ultra.foxsu.ui.screen.onboarding

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Extension
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import me.diksu.ultra.foxsu.ui.component.bottombar.DockButton
import me.diksu.ultra.foxsu.ui.component.bottombar.DockButtonFill
import me.diksu.ultra.foxsu.ui.component.bottombar.shape
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * 欢迎页的「可视零件」。
 *
 * 上一版每一页都是「大标题 + 一句话 + 六行文字」，读起来像说明书；这一版把要讲的东西
 * 直接做成能看的物件：一台迷你手机、一支会变形的底栏、几块实时读数。
 * 页面上出现的每个数字、每一种形状，都是 App 里真实存在的东西。
 */

/** 极光底：三团缓慢游动的光斑 + 一层几乎看不见的网格。 */
@Composable
fun AuroraBackdrop(t: OnbTokens, modifier: Modifier = Modifier) {
    // 只让「整层透明度」缓慢呼吸：光斑本身是静态绘制的，动画由 GPU 合成，
    // 不会每帧重建三个径向渐变的 shader。
    val transition = rememberInfiniteTransition(label = "onb-bg")
    val breath by transition.animateFloat(
        initialValue = 0.78f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(7000, easing = androidx.compose.animation.core.FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "onb-bg-breath",
    )

    Box(modifier.background(t.bg)) {
        Canvas(
            Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = breath },
        ) {
            val w = size.width
            val h = size.height
            fun blob(cx: Float, cy: Float, radius: Float, color: Color) {
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(color, color.copy(alpha = color.alpha * 0.32f), Color.Transparent),
                        center = Offset(cx, cy),
                        radius = radius,
                    ),
                    radius = radius,
                    center = Offset(cx, cy),
                )
            }

            blob(w * 0.14f, h * 0.06f, w * 0.95f, t.accent.copy(alpha = if (t.dark) 0.30f else 0.15f))
            blob(w * 0.95f, h * 0.34f, w * 0.80f, t.accent2.copy(alpha = if (t.dark) 0.24f else 0.11f))
            blob(w * 0.32f, h * 0.97f, w * 0.88f, t.mint.copy(alpha = if (t.dark) 0.15f else 0.09f))

            // 网格：34dp 一格，只比背景亮一点点，用来托住上面的卡片
            val step = 34.dp.toPx()
            val line = if (t.dark) Color(0x0CFFFFFF) else Color(0x07000000)
            var x = 0f
            while (x <= w) {
                drawLine(line, Offset(x, 0f), Offset(x, h), strokeWidth = 1f)
                x += step
            }
            var y = 0f
            while (y <= h) {
                drawLine(line, Offset(0f, y), Offset(w, y), strokeWidth = 1f)
                y += step
            }
        }
    }
}

/** 迷你手机：外壳 + 状态栏 + 一张卡 + 一条真实造型的底栏。 */
@Composable
fun MiniPhone(
    t: OnbTokens,
    accent: Color,
    accent2: Color,
    screenBg: Color,
    title: String,
    dock: DockButton,
    dark: Boolean,
    modifier: Modifier = Modifier,
    glassDock: Boolean = false,
    selectedDock: Int = 0,
    width: Dp = 206.dp,
    height: Dp = 418.dp,
    body: @Composable ColumnScope.() -> Unit = {},
) {
    val frame = if (dark) Color(0xFF16151F) else Color(0xFF23222E)
    Box(
        modifier
            .width(width)
            .height(height),
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .shadow(
                    elevation = 26.dp,
                    shape = RoundedCornerShape(38.dp),
                    spotColor = accent.copy(alpha = 0.35f),
                    ambientColor = accent.copy(alpha = 0.18f),
                )
                .clip(RoundedCornerShape(38.dp))
                .background(frame)
                .padding(7.dp),
        ) {
            Box(
                Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(31.dp))
                    .background(screenBg),
            ) {
                // 屏幕顶光：一块很淡的径向亮斑，屏幕才不像纯色板
                Canvas(Modifier.fillMaxSize()) {
                    drawCircle(
                        brush = Brush.radialGradient(
                            listOf(accent.copy(alpha = 0.20f), Color.Transparent),
                            center = Offset(size.width * 0.1f, size.height * 0.02f),
                            radius = size.width * 0.95f,
                        ),
                        center = Offset(size.width * 0.1f, size.height * 0.02f),
                        radius = size.width * 0.95f,
                    )
                }

                val onScreen = if (dark) Color(0xFFF2F1FA) else Color(0xFF191826)
                val dim = onScreen.copy(alpha = 0.45f)
                val screenLine = if (dark) Color(0x1AFFFFFF) else Color(0x0F000000)

                Column(Modifier.fillMaxSize()) {
                    MiniStatusBar(onScreen)

                    Column(
                        Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp),
                    ) {
                        MiniHeader(title, onScreen, dim)
                        Spacer(Modifier.height(10.dp))
                        body()
                    }

                    MiniDock(
                        onScreen = onScreen,
                        screenLine = screenLine,
                        accent = accent,
                        accent2 = accent2,
                        dock = dock,
                        dark = dark,
                        glass = glassDock,
                        selected = selectedDock,
                    )
                    Spacer(Modifier.height(10.dp))
                }

                // 斜向高光：一条 12% 白的斜带，模拟玻璃反射
                Box(
                    Modifier
                        .fillMaxSize()
                        .drawBehind {
                            drawRect(
                                Brush.linearGradient(
                                    colors = listOf(Color(0x1AFFFFFF), Color(0x00FFFFFF)),
                                    start = Offset(0f, 0f),
                                    end = Offset(size.width * 0.9f, size.height * 0.5f),
                                ),
                            )
                        },
                )
            }
        }
    }
}

@Composable
private fun MiniStatusBar(onScreen: Color) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(24.dp)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "9:41",
            fontSize = 7.5.sp,
            fontWeight = FontWeight.Bold,
            color = onScreen.copy(alpha = 0.7f),
        )
        Spacer(Modifier.weight(1f))
        repeat(3) { i ->
            Box(
                Modifier
                    .padding(start = 2.dp)
                    .size(width = if (i == 2) 10.dp else 3.dp, height = 3.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(onScreen.copy(alpha = 0.4f)),
            )
        }
    }
}

@Composable
private fun MiniHeader(title: String, onScreen: Color, dim: Color) {
    Column(Modifier.padding(top = 6.dp)) {
        Text(
            title,
            fontSize = 15.sp,
            fontWeight = FontWeight.ExtraBold,
            letterSpacing = (-0.3).sp,
            color = onScreen,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(2.dp))
        Text(
            "全部就绪 · 刚刚",
            fontSize = 7.sp,
            color = dim,
        )
    }
}

/** 迷你底栏：四枚按钮，造型取自真实的 DockButton，不是画着玩的方块。 */
@Composable
private fun MiniDock(
    onScreen: Color,
    screenLine: Color,
    accent: Color,
    accent2: Color,
    dock: DockButton,
    dark: Boolean,
    glass: Boolean,
    selected: Int,
) {
    val icons = listOf(
        Icons.Rounded.Shield,
        Icons.Rounded.Extension,
        Icons.Rounded.Bolt,
        Icons.Rounded.Person,
    )
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp),
        horizontalArrangement = Arrangement.Center,
    ) {
        Row(
            Modifier
                .clip(RoundedCornerShape(26.dp))
                .background(
                    if (glass) onScreen.copy(alpha = 0.06f)
                    else if (dark) Color(0x1FFFFFFF) else Color(0xB3FFFFFF),
                )
                .border(
                    1.dp,
                    if (glass) Color(0x33FFFFFF) else screenLine,
                    RoundedCornerShape(26.dp),
                )
                .padding(horizontal = 6.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(3.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            repeat(4) { i ->
                val on = i == selected
                Box(Modifier.size(26.dp), contentAlignment = Alignment.Center) {
                    DockButtonFill(
                        style = dock,
                        selected = on,
                        shape = dock.shape(26.dp, 26.dp),
                        accent = accent,
                        accentMid = accent2,
                        accentSecond = accent2,
                        dark = dark,
                        modifier = Modifier.size(26.dp),
                    )
                    Icon(
                        icons[i],
                        contentDescription = null,
                        tint = if (on) Color.White else onScreen.copy(alpha = 0.55f),
                        modifier = Modifier.size(12.dp),
                    )
                }
            }
        }
    }
}

/** 迷你卡：图标块 + 两条信息线 + 小开关。 */
@Composable
fun MiniCard(
    accent: Color,
    icon: ImageVector,
    on: Boolean,
    label: String,
    sub: String,
    dark: Boolean,
) {
    val onScreen = if (dark) Color(0xFFF2F1FA) else Color(0xFF191826)
    val dim = onScreen.copy(alpha = 0.42f)
    val screenCard = if (dark) Color(0x14FFFFFF) else Color(0xD9FFFFFF)
    val screenLine = if (dark) Color(0x1AFFFFFF) else Color(0x0F000000)
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(13.dp))
            .background(screenCard)
            .border(1.dp, screenLine, RoundedCornerShape(13.dp))
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(24.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Brush.linearGradient(listOf(accent, accent.copy(alpha = 0.65f)))),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(12.dp))
        }
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            Text(
                label,
                fontSize = 7.5.sp,
                fontWeight = FontWeight.Bold,
                color = onScreen,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                sub,
                fontSize = 6.sp,
                color = dim,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(6.dp))
        Box(
            Modifier
                .size(width = 18.dp, height = 10.dp)
                .clip(RoundedCornerShape(99.dp))
                .background(if (on) accent else onScreen.copy(alpha = 0.16f)),
        ) {
            Box(
                Modifier
                    .offset(x = if (on) 9.dp else 1.5.dp, y = 1.5.dp)
                    .size(7.dp)
                    .clip(CircleShape)
                    .background(Color.White),
            )
        }
    }
}

/** 实时读数块：设备 / 内核 / 模块 / 授权。 */
@Composable
fun FactTile(
    label: String,
    value: String,
    t: OnbTokens,
    modifier: Modifier = Modifier,
    accent: Boolean = false,
) {
    Column(
        modifier
            .clip(RoundedCornerShape(14.dp))
            .background(t.cardBg)
            .border(1.dp, t.cardBd, RoundedCornerShape(14.dp))
            .padding(horizontal = 11.dp, vertical = 9.dp),
    ) {
        Text(
            label,
            fontSize = 8.sp,
            letterSpacing = 1.2.sp,
            fontWeight = FontWeight.ExtraBold,
            color = t.tx3,
            maxLines = 1,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            value,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = if (accent) t.accent else t.tx,
            fontFamily = if (accent) FontFamily.Monospace else FontFamily.Default,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** 十种底栏造型的选择格：每一格都是真实 shape() 画出来的轮廓。 */
@Composable
fun DockShapeCell(
    style: DockButton,
    selected: Boolean,
    t: OnbTokens,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Column(
        modifier
            .clip(RoundedCornerShape(14.dp))
            .background(if (selected) t.selBg else Color.Transparent)
            .border(
                1.dp,
                if (selected) t.selBd else Color.Transparent,
                RoundedCornerShape(14.dp),
            )
            .clickable(onClick = onClick)
            .padding(vertical = 7.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.size(32.dp), contentAlignment = Alignment.Center) {
            DockButtonFill(
                style = style,
                selected = selected,
                shape = style.shape(28.dp, 28.dp),
                accent = t.accent,
                accentMid = t.accent2,
                accentSecond = t.accent2,
                dark = t.dark,
                modifier = Modifier.size(28.dp),
            )
        }
        Spacer(Modifier.height(5.dp))
        Text(
            style.label,
            fontSize = 8.sp,
            fontWeight = FontWeight.Bold,
            color = if (selected) t.accent else t.tx3,
            maxLines = 1,
        )
    }
}

/** 胶囊开关：读秒收起 / 液态玻璃这类「一拨就变」的项。 */
@Composable
fun PillToggle(label: String, on: Boolean, t: OnbTokens, onClick: () -> Unit) {
    Row(
        Modifier
            .clip(RoundedCornerShape(99.dp))
            .background(if (on) t.accent.copy(alpha = 0.14f) else t.chipBg)
            .border(
                1.dp,
                if (on) t.accent.copy(alpha = 0.45f) else t.chipBd,
                RoundedCornerShape(99.dp),
            )
            .clickable(onClick = onClick)
            .padding(start = 11.dp, end = 5.dp, top = 5.dp, bottom = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            fontSize = 9.5.sp,
            fontWeight = FontWeight.Bold,
            color = if (on) t.accent else t.tx2,
        )
        Spacer(Modifier.width(8.dp))
        Box(
            Modifier
                .size(width = 26.dp, height = 15.dp)
                .clip(RoundedCornerShape(99.dp))
                .background(if (on) t.accent else t.tx.copy(alpha = 0.14f)),
        ) {
            Box(
                Modifier
                    .offset(x = if (on) 12.dp else 2.dp, y = 2.dp)
                    .size(11.dp)
                    .clip(CircleShape)
                    .background(Color.White),
            )
        }
    }
}

/** 一句小注：放在页脚上方，说明「下面这些东西都是真的」。 */
@Composable
fun Caption(text: String, t: OnbTokens, modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(5.dp)
                .clip(CircleShape)
                .background(t.accent),
        )
        Spacer(Modifier.width(7.dp))
        Text(
            text,
            fontSize = 9.5.sp,
            lineHeight = 14.sp,
            color = t.tx3,
            modifier = Modifier.weight(1f, fill = false),
        )
    }
}

/** 细线分隔（1px，暗色更淡）。 */
@Composable
fun Hairline(t: OnbTokens, modifier: Modifier = Modifier) {
    Box(
        modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(t.line),
    )
}

/** 一个带渐变的图标方块，用于页首的小标题。 */
@Composable
fun KickerMark(t: OnbTokens, icon: ImageVector, modifier: Modifier = Modifier) {
    Box(
        modifier
            .size(26.dp)
            .clip(RoundedCornerShape(9.dp))
            .background(Brush.linearGradient(listOf(t.accent, t.accent2)))
            .drawBehind {
                drawRect(
                    Brush.verticalGradient(listOf(Color(0x33FFFFFF), Color(0x00FFFFFF))),
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
    }
}

/** 页面主标题：比上一版更大、字距更紧，和 App 自己的标题同一套。 */
@Composable
fun OnbTitle(text: String, t: OnbTokens) {
    Text(
        text,
        fontSize = 30.sp,
        lineHeight = 35.sp,
        fontWeight = FontWeight.ExtraBold,
        letterSpacing = (-0.9).sp,
        color = t.tx,
    )
}

/** 正文：一行 20sp 行高，最多三行。 */
@Composable
fun OnbBody(text: String, t: OnbTokens) {
    Text(
        text,
        fontSize = 12.5.sp,
        lineHeight = 20.sp,
        color = t.tx2,
    )
}

/** 小标签行（kicker）。 */
@Composable
fun OnbKicker(text: String, t: OnbTokens) {
    Text(
        text,
        fontSize = 9.sp,
        letterSpacing = 3.sp,
        fontWeight = FontWeight.ExtraBold,
        color = t.accent,
    )
}

/** 渐变描边按钮（次级操作）。 */
@Composable
fun GhostPill(
    label: String,
    t: OnbTokens,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier
            .height(44.dp)
            .clip(RoundedCornerShape(15.dp))
            .border(1.dp, t.ghostBd, RoundedCornerShape(15.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = t.ghostTx)
    }
}
