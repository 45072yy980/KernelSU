package me.diksu.ultra.foxsu.ui.screen.editorial

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import me.diksu.ultra.R
import me.diksu.ultra.foxsu.ui.component.bouncyClick
import me.diksu.ultra.foxsu.ui.component.bottombar.NavigationBadgeState
import me.diksu.ultra.foxsu.ui.screen.ag.particleOutline
import me.diksu.ultra.foxsu.ui.screen.glass.marquee
import me.diksu.ultra.foxsu.ui.component.isNightScheme

/**
 * FoxSu Editorial token（严格取自 foxsu-editorial-ui.html）
 * 亮色：paper #faf9f6 / ink #1d1c22 / hair #e6e4ec / acc #e8502e
 * 暗色：paper #141319 / ink #f0eff5 / hair #2a2931 / acc #ff6a42
 */
data class EdTokens(
    val dark: Boolean,
    val paper: Color,
    val ink: Color,
    val ink2: Color,
    val sub: Color,
    val faint: Color,
    val hair: Color,
    val acc: Color,
    val lime: Color,
    val violet: Color,
    val cyan: Color,
    val amber: Color,
    val rose: Color,
) {
    val mono = FontFamily.Monospace
}

/** 页面底色：用户开了壁纸就让位，让 Editorial 也能看到壁纸。 */
@Composable
fun edPageBg(color: Color): Modifier =
    if (me.diksu.ultra.foxsu.ui.component.hasCustomWallpaper()) Modifier else Modifier.background(color)

@Composable
fun edTokens(): EdTokens {
    // 全局强调色：纸面的信号色（acc）跟随主色，墨色与纸色保持纸面质感
    val ac = me.diksu.ultra.foxsu.ui.theme.currentAccent.primary
    return if (isNightScheme()) {
        EdTokens(
            dark = true,
            paper = Color(0xFF141319), ink = Color(0xFFF0EFF5), ink2 = Color(0xFFC6C3D0),
            sub = Color(0xFF8E8B9C), faint = Color(0xFF4C4956), hair = Color(0xFF2A2931),
            acc = ac, lime = Color(0xFF7FD694), violet = Color(0xFFA89BFF),
            cyan = Color(0xFF4FC3D0), amber = Color(0xFFE8B45C), rose = Color(0xFFF08BB0),
        )
    } else {
        EdTokens(
            dark = false,
            paper = Color(0xFFFAF9F6), ink = Color(0xFF1D1C22), ink2 = Color(0xFF55525F),
            sub = Color(0xFF8E8B9C), faint = Color(0xFFC9C7D4), hair = Color(0xFFE6E4EC),
            acc = ac, lime = Color(0xFF3E7A4C), violet = Color(0xFF6C5FC7),
            cyan = Color(0xFF0E8A99), amber = Color(0xFFC98A2F), rose = Color(0xFFC2567E),
        )
    }
}

/** 发丝线：1px 底部横线 */
fun Modifier.hairBottom(color: Color): Modifier = drawBehind {
    val y = size.height - 1f
    drawLine(color, Offset(0f, y), Offset(size.width, y), strokeWidth = 1f)
}

/** 顶部通栏 masthead：品牌 + 标签 + 期刊副行 + 右侧圆形动作按钮 */
@Composable
fun EdMasthead(
    brand: String,
    tag: String,
    left: String,
    right: String,
    /** 刊名颜色；null = 纸面墨色。定制 → 标题栏 可覆盖。 */
    brandColor: Color? = null,
    brandSize: Int = 34,
    showBack: Boolean = false,
    onBack: (() -> Unit)? = null,
    actions: List<EdMastAction> = emptyList(),
) {
    val t = edTokens()
    // 右侧动作按钮的实际占位：40dp 圆钮 + 9dp 间距 + 14dp 安全间隙
    // （之前写死 108dp，而三个按钮实际占 138dp，ROOT 徽标会被按钮压住）。
    val actionsWidth = if (actions.isEmpty()) 0.dp else (40 * actions.size + 9 * (actions.size - 1) + 14).dp
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .hairBottom(t.ink)
            .padding(start = 22.dp, end = 22.dp, top = 16.dp, bottom = 14.dp),
    ) {
        Column(modifier = Modifier.padding(end = actionsWidth + 8.dp)) {
            Row(verticalAlignment = Alignment.Bottom) {
                if (showBack && onBack != null) {
                    EdCircleButton(iconRes = R.drawable.fx_ic_d_back, onClick = onBack, size = 30)
                    Spacer(Modifier.width(10.dp))
                }
                Text(
                    text = brand,
                    fontSize = brandSize.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = (-0.5).sp,
                    // 自定义标题色优先；没设则跟随全局强调色
                    color = brandColor ?: me.diksu.ultra.foxsu.ui.theme.TitleInk.solid(me.diksu.ultra.foxsu.ui.theme.currentAccent.primary),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    text = tag,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 2.sp,
                    color = t.acc,
                    maxLines = 1,
                    modifier = Modifier
                        .offset(y = (-4).dp)
                        .border(1.5.dp, t.acc, RoundedCornerShape(4.dp))
                        .padding(horizontal = 7.dp, vertical = 2.dp),
                )
            }
            Spacer(Modifier.height(6.dp))
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                // 之前用 marquee 横向滚动：滚动中途截图就是「rnal Kernel Manage…」这种断头文案，
                // 而且滚动层会盖到右侧 VOL 上。这里改成单行省略，保证任何时刻都完整可读。
                Text(
                    text = left,
                    fontSize = 11.sp,
                    color = t.sub,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    text = right,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = t.ink,
                    maxLines = 1,
                )
            }
        }
        Row(
            modifier = Modifier.align(Alignment.TopEnd).padding(top = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(9.dp),
        ) {
            actions.forEach { action ->
                EdPingButton(iconRes = action.iconRes, onClick = action.onClick)
            }
        }
    }
}

data class EdMastAction(val iconRes: Int, val onClick: () -> Unit)

/** 32 圆形描边按钮，点击时外圈 ping 扩散（对应 .mb.ping::after + @keyframes ring） */
@Composable
fun EdPingButton(iconRes: Int, onClick: () -> Unit, size: Int = 40) {
    val t = edTokens()
    val ping = remember { Animatable(0f) }
    var pressed by remember { mutableStateOf(false) }
    LaunchedEffect(pressed) {
        if (pressed) {
            ping.snapTo(0f)
            ping.animateTo(1f, tween(620, easing = FastOutSlowInEasing))
            pressed = false
        }
    }
    Box(contentAlignment = Alignment.Center) {
        // ping 环：scale .82→1.55，透明度 .85→0
        if (ping.value > 0f) {
            Box(
                modifier = Modifier
                    .size((size + 6).dp)
                    .graphicsLayer {
                        val s = 0.82f + 0.73f * ping.value
                        scaleX = s
                        scaleY = s
                        alpha = 0.85f * (1f - ping.value)
                    }
                    .border(1.5.dp, t.acc, CircleShape),
            )
        }
        Box(
            modifier = Modifier
                .size(size.dp)
                .graphicsLayer {
                    val s = if (ping.value > 0f) 1.06f - 0.06f * ping.value else 1f
                    scaleX = s
                    scaleY = s
                }
                .border(1.5.dp, t.ink, CircleShape)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                ) {
                    pressed = true
                    onClick()
                },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(iconRes),
                contentDescription = null,
                modifier = Modifier.size((size * 0.45f).dp),
                tint = t.ink,
            )
        }
    }
}

@Composable
fun EdCircleButton(iconRes: Int, onClick: () -> Unit, size: Int = 40) {
    val t = edTokens()
    Box(
        modifier = Modifier
            .size(size.dp)
            .border(1.5.dp, t.ink, CircleShape)
            .bouncyClick(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = null,
            modifier = Modifier.size((size * 0.45f).dp),
            tint = t.ink,
        )
    }
}

/**
 * 状态通栏：墨色底 + 大字号状态 + 红点 + 三列 KV。
 * 注意：水印必须夹在通栏内且不参与布局测量，避免压住 KV 文字。
 */
@Composable
fun EdStatusBand(
    kicker: String,
    big: String,
    kvs: List<Triple<String, String, Boolean>>,
    onClick: (() -> Unit)? = null,
    onCheckUpdate: (() -> Unit)? = null,
    onRefreshImage: (() -> Unit)? = null,
) {
    val t = edTokens()
    Box(
        modifier = Modifier
            .fillMaxWidth()
            // 编辑风格是直角版式，粒子边跟着走 2dp 微圆角，只在外侧铺一层，不做裁切
            .particleOutline(
                shape = androidx.compose.foundation.shape.RoundedCornerShape(2.dp),
                accent = t.rose,
                bleed = 10.dp,
            )
            .background(t.ink),
    ) {
        // 右上角狐尾水印（设计稿 .tail）：放在顶部角落，尺寸收小，不侵入 KV 区
        Icon(
            painter = painterResource(R.drawable.fx_ic_fox_global),
            contentDescription = null,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .offset(x = 30.dp, y = (-46).dp)
                .size(128.dp),
            tint = t.paper.copy(alpha = 0.09f),
        )
        Column(
            modifier = Modifier.padding(start = 22.dp, end = 22.dp, top = 18.dp, bottom = 20.dp),
        ) {
            Text(
                text = kicker,
                fontSize = 9.5.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 3.sp,
                lineHeight = 13.sp,
                color = t.paper.copy(alpha = 0.55f),
            )
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = big,
                    fontSize = 38.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = (-1).sp,
                    color = t.paper,
                    lineHeight = 44.sp,
                )
                Box(
                    modifier = Modifier
                        .padding(start = 5.dp, bottom = 9.dp)
                        .size(9.dp)
                        .clip(CircleShape)
                        .background(t.acc),
                )
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 18.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                kvs.forEach { (k, v, hot) ->
                    Column(
                        modifier = Modifier
                            // 「界面」列放的是 "FoxSu Editorial" 这类长文案，等比给宽一点，
                            // 否则 390dp 板上第三列永远只能显示 "FoxSu Edi…"。
                            .weight(if (hot) 1.3f else 1f)
                            // 文字左侧留 10dp；竖线再往左挪 4dp → 竖线与文字实际间距 14dp
                            .padding(start = 10.dp, end = 4.dp)
                            .drawBehind {
                                val x = (-4).dp.toPx()
                                drawLine(
                                    color = t.paper.copy(alpha = 0.22f),
                                    start = Offset(x, 0f),
                                    end = Offset(x, size.height),
                                    strokeWidth = 1.5.dp.toPx(),
                                )
                            },
                    ) {
                        Text(
                            text = k,
                            fontSize = 8.5.sp,
                            letterSpacing = 1.4.sp,
                            lineHeight = 12.sp,
                            color = t.paper.copy(alpha = 0.5f),
                            maxLines = 1,
                        )
                        Text(
                            text = v,
                            // 「界面」列是 "FoxSu Editorial" 这类长词，字号收 1sp 换完整显示
                            fontSize = if (hot) 11.5.sp else 12.5.sp,
                            fontWeight = FontWeight.ExtraBold,
                            lineHeight = 17.sp,
                            color = if (hot) t.acc else t.paper,
                            modifier = Modifier.padding(top = 5.dp),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
            // 真实操作行：安装内核 / 刷新镜像 / 检查更新（等距排布 + 统一 11sp 字重）
            val acts = buildList {
                if (onClick != null) add("安装内核" to onClick)
                if (onRefreshImage != null) add(stringResource(R.string.fx_home_refresh_image) to onRefreshImage)
                if (onCheckUpdate != null) add(stringResource(R.string.fx_home_check_update) to onCheckUpdate)
            }
            if (acts.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    acts.forEach { (label, action) ->
                        Text(
                            text = label,
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = t.acc,
                            maxLines = 1,
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .clickable { action() }
                                .padding(vertical = 4.dp),
                        )
                    }
                }
            }
        }
    }
}

/** 编号区块标题：01 ── 设备概况 ────────（线在首次出现时展开） */
@Composable
fun EdSection(number: String, title: String, topPadding: Dp = 22.dp) {
    val t = edTokens()
    val grow = remember { Animatable(0f) }
    LaunchedEffect(Unit) { grow.animateTo(1f, tween(420, easing = FastOutSlowInEasing)) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 22.dp, end = 22.dp, top = topPadding, bottom = 6.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        Text(
            text = number,
            fontSize = 11.sp,
            fontWeight = FontWeight.Black,
            letterSpacing = 1.sp,
            color = t.acc,
        )
        Spacer(Modifier.width(10.dp))
        Text(
            text = title,
            fontSize = 16.sp,
            fontWeight = FontWeight.Black,
            letterSpacing = 0.5.sp,
            color = t.ink,
        )
        Spacer(Modifier.width(10.dp))
        Box(
            modifier = Modifier
                .weight(1f)
                .padding(bottom = 4.dp)
                .height(1.dp)
                .graphicsLayer { alpha = 0.16f }
                .background(t.ink)
                .graphicsLayer {
                    scaleX = grow.value
                    transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0f, 0.5f)
                },
        )
    }
}

/** 条目行：圆形字形 + 标题/说明 + 右侧数值（发丝线分隔，无卡片） */
@Composable
fun EdRow(
    glyph: String = "",
    glyphBg: Color,
    glyphColor: Color,
    title: String,
    subtitle: String,
    iconRes: Int? = null,
    trailing: (@Composable RowScope.() -> Unit)? = null,
    onClick: (() -> Unit)? = null,
) {
    val t = edTokens()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .hairBottom(t.hair)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 22.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier.size(34.dp).clip(CircleShape).background(glyphBg),
            contentAlignment = Alignment.Center,
        ) {
            if (iconRes != null) {
                Icon(
                    painter = painterResource(iconRes),
                    contentDescription = null,
                    modifier = Modifier.size(17.dp),
                    tint = glyphColor,
                )
            } else {
                Text(text = glyph, fontSize = 14.sp, color = glyphColor)
            }
        }
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontSize = 14.5.sp,
                fontWeight = FontWeight.ExtraBold,
                color = t.ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = subtitle,
                fontSize = 10.5.sp,
                color = t.sub,
                lineHeight = 15.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 3.dp),
            )
        }
        if (trailing != null) {
            Spacer(Modifier.width(10.dp))
            trailing.invoke(this)
        }
    }
}

/** 右侧大数字（lrow .num） */
@Composable
fun EdNum(text: String, color: Color) {
    Text(
        text = text,
        fontSize = 26.sp,
        fontWeight = FontWeight.Black,
        letterSpacing = (-1).sp,
        color = color,
        maxLines = 1,
    )
}

/** 右侧数值 + 小药丸（lrow .val / .pill） */
@Composable
fun EdVal(text: String, pill: String, pillColor: Color, filled: Boolean = false) {
    val t = edTokens()
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = text,
            fontSize = 12.sp,
            fontWeight = FontWeight.ExtraBold,
            color = t.ink2,
            maxLines = 1,
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = pill,
            fontSize = 9.sp,
            fontWeight = FontWeight.ExtraBold,
            color = if (filled) t.lime else t.ink2,
            maxLines = 1,
            modifier = Modifier
                .clip(RoundedCornerShape(5.dp))
                .background(if (filled) t.lime.copy(alpha = 0.12f) else t.paper)
                .border(1.dp, if (filled) t.lime.copy(alpha = 0.4f) else t.hair, RoundedCornerShape(5.dp))
                .padding(horizontal = 9.dp, vertical = 3.dp),
        )
    }
}

/** 编辑风开关（40×22，方块直角边框 + 滑动 thumb） */
@Composable
fun EdSwitch(checked: Boolean, onToggle: (() -> Unit)? = null) {
    val t = edTokens()
    val offset = androidx.compose.animation.core.animateDpAsState(
        if (checked) 21.dp else 3.dp,
        tween(200),
        label = "edSwitch",
    )
    val bg = androidx.compose.animation.animateColorAsState(
        if (checked) t.acc else if (t.dark) Color(0xFF33323C) else Color(0xFFE2E0EA),
        tween(200),
        label = "edSwitchBg",
    )
    Box(
        modifier = Modifier
            .size(width = 40.dp, height = 22.dp)
            .clip(RoundedCornerShape(99.dp))
            .background(bg.value)
            .then(if (onToggle != null) Modifier.clickable(onClick = onToggle) else Modifier),
    ) {
        Box(
            modifier = Modifier
                .offset(x = offset.value, y = 3.dp)
                .size(16.dp)
                .clip(CircleShape)
                .background(Color.White),
        )
    }
}

/**
 * 规格卡（设计稿 .spec）：浅纸底 + 左侧红色竖条 + 双语标题行 + 等宽值。
 * 用于内核版本 / 指纹信息。
 */
@Composable
fun EdSpec(title: String, tag: String, value: String) {
    val t = edTokens()
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 22.dp, end = 22.dp, top = 9.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(if (t.dark) Color(0xFF1C1B18) else Color(0xFFF5F3ED))
            .border(
                1.dp,
                if (t.dark) Color(0xFF2E2C26) else Color(0xFFE7E3D8),
                RoundedCornerShape(12.dp),
            )
            .drawBehind {
                val barW = 3.dp.toPx()
                drawRoundRect(
                    color = t.acc,
                    topLeft = Offset(0f, 11.dp.toPx()),
                    size = androidx.compose.ui.geometry.Size(barW, size.height - 22.dp.toPx()),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(0f, barW),
                )
            }
            .padding(start = 16.dp, end = 15.dp, top = 9.dp, bottom = 10.dp),
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = title,
                    fontSize = 8.5.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 2.2.sp,
                    color = t.acc,
                    maxLines = 1,
                )
                Spacer(Modifier.weight(1f))
                Text(
                    text = tag,
                    fontSize = 7.5.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.2.sp,
                    color = if (t.dark) Color(0xFF6B675C) else Color(0xFFB6B1A4),
                    maxLines = 1,
                )
            }
            Text(
                text = value,
                fontSize = 9.8.sp,
                lineHeight = 17.sp,
                letterSpacing = (-0.15).sp,
                fontFamily = t.mono,
                color = if (t.dark) Color(0xFFD6D2C7) else Color(0xFF45423B),
                modifier = Modifier.padding(top = 6.dp),
            )
        }
    }
}

/** 底部通栏 tab：顶部墨线 + 选中下划线（宽度动画）+ 图标上浮 */
@Composable
fun EdTabBar(
    currentTab: Int,
    badge: NavigationBadgeState,
    onOpenTab: (Int) -> Unit,
) {
    val t = edTokens()
    val items = listOf(
        Triple(R.drawable.fx_ic_d_home, R.string.fx_home, 0),
        Triple(R.drawable.fx_ic_d_shield, R.string.fx_superuser, 1),
        Triple(R.drawable.fx_ic_d_grid, R.string.fx_module, 2),
        Triple(R.drawable.fx_ic_d_tune, R.string.fx_settings, 3),
    )
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(t.paper)
            // 边到边显示：纸面底栏要自己吃掉导航栏高度，否则底部会留一条空洞
            .navigationBarsPadding(),
    ) {
        Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(t.ink))
        Row(modifier = Modifier.fillMaxWidth()) {
            items.forEach { (iconRes, labelRes, tab) ->
                val selected = tab == currentTab
                val count = when (tab) {
                    1 -> badge.superuserCount
                    2 -> badge.moduleEnabledCount
                    else -> 0
                }
                val lift = androidx.compose.animation.core.animateDpAsState(
                    if (selected) (-2).dp else 0.dp, tween(260), label = "lift",
                )
                val scale = androidx.compose.animation.core.animateFloatAsState(
                    if (selected) 1.08f else 1f, tween(260), label = "scale",
                )
                val underline = androidx.compose.animation.core.animateDpAsState(
                    if (selected) 18.dp else 0.dp, tween(280), label = "line",
                )
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) { onOpenTab(tab) },
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 11.dp, bottom = 16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Box {
                            Icon(
                                painter = painterResource(iconRes),
                                contentDescription = null,
                                modifier = Modifier
                                    .size(18.dp)
                                    .graphicsLayer {
                                        translationY = lift.value.toPx()
                                        scaleX = scale.value
                                        scaleY = scale.value
                                    },
                                tint = if (selected) t.ink else if (t.dark) Color(0xFF6A677A) else Color(0xFF9A97AB),
                            )
                            if (count > 0) {
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .offset(x = 10.dp, y = (-6).dp)
                                        .size(if (count < 10) 14.dp else 16.dp)
                                        .clip(CircleShape)
                                        .background(t.acc),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(
                                        text = if (count > 99) "99+" else count.toString(),
                                        color = Color.White,
                                        maxLines = 1,
                                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                        style = androidx.compose.ui.text.TextStyle(
                                            fontSize = if (count < 10) 8.sp else 7.sp,
                                            lineHeight = if (count < 10) 8.sp else 7.sp,
                                            fontWeight = FontWeight.ExtraBold,
                                            platformStyle = androidx.compose.ui.text.PlatformTextStyle(
                                                includeFontPadding = false
                                            ),
                                        ),
                                    )
                                }
                            }
                        }
                        Text(
                            text = stringResource(labelRes),
                            fontSize = 9.5.sp,
                            fontWeight = if (selected) FontWeight.ExtraBold else FontWeight.Normal,
                            color = if (selected) t.ink else t.sub,
                            maxLines = 1,
                            modifier = Modifier.padding(top = 3.dp),
                        )
                    }
                    // 选中下划线
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 8.dp)
                            .size(width = underline.value, height = 3.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(t.acc),
                    )
                }
            }
        }
    }
}

/** 数字统计带（.statband）：等分单元格 + 竖发丝线 + 大数字 */
@Composable
fun EdStatBand(cells: List<Triple<String, String, Color?>>) {
    val t = edTokens()
    Column(modifier = Modifier.fillMaxWidth()) {
        Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(t.ink))
        Row(modifier = Modifier.fillMaxWidth()) {
            cells.forEachIndexed { index, (num, label, color) ->
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .drawBehind {
                            if (index != cells.lastIndex) {
                                drawLine(
                                    color = t.hair,
                                    start = Offset(size.width - 1f, 0f),
                                    end = Offset(size.width - 1f, size.height),
                                    strokeWidth = 1f,
                                )
                            }
                        }
                        .padding(horizontal = 10.dp, vertical = 16.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = num,
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Black,
                            letterSpacing = (-1).sp,
                            color = color ?: t.ink,
                        )
                        Text(
                            text = label,
                            fontSize = 9.sp,
                            letterSpacing = 1.5.sp,
                            color = t.sub,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                }
            }
        }
    }
}

/** 搜索行（发丝线底 + 等宽提示字） */
@Composable
fun EdSearchRow(value: String, placeholder: String, onValueChange: (String) -> Unit) {
    val t = edTokens()
    androidx.compose.foundation.text.BasicTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        textStyle = androidx.compose.ui.text.TextStyle(fontSize = 12.sp, color = t.ink),
        cursorBrush = Brush.verticalGradient(listOf(t.acc, t.acc)),
        modifier = Modifier
            .fillMaxWidth()
            .hairBottom(t.hair)
            .padding(horizontal = 22.dp, vertical = 13.dp),
        decorationBox = { inner ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    painter = painterResource(R.drawable.fx_ic_d_search),
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                    tint = t.faint,
                )
                Spacer(Modifier.width(10.dp))
                Box(modifier = Modifier.weight(1f)) {
                    if (value.isEmpty()) {
                        Text(text = placeholder, fontSize = 12.sp, color = t.faint)
                    }
                    inner()
                }
            }
        },
    )
}
