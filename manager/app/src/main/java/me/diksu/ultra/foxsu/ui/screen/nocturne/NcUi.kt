package me.diksu.ultra.foxsu.ui.screen.nocturne

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.sp
import me.diksu.ultra.foxsu.ui.screen.home.HeroPhase

/**
 * Nocturne 通用组件 —— 严格对照视觉稿 foxsu-nocturne-ui.html 的 CSS 数值实现。
 */

fun <T> ncSpring() = spring<T>(dampingRatio = 0.6f, stiffness = Spring.StiffnessMediumLow)

@Composable
fun ncStatusTop(): Dp =
    WindowInsets.statusBars.asPaddingValues().calculateTopPadding()

val ncMono: FontFamily = FontFamily.Monospace

/**
 * 页面背景 .screen —— 底色 + 两团径向光晕：
 * violet 13%（420×260 at 20% 0%）+ mint 6%（320×220 at 100% 12%）；
 * 亮色为 violet 16% / mint 12%。
 */
@Composable
fun NcGlowBackground(t: NcTokens, modifier: Modifier = Modifier) {
    // 有壁纸时这层纯色底会盖住壁纸，只保留光晕
    val wall = me.diksu.ultra.foxsu.ui.component.hasWallpaperBackdrop()
    Box(modifier.fillMaxSize().then(if (wall) Modifier else Modifier.background(t.bg))) {
        Canvas(Modifier.fillMaxSize()) {
            drawRect(
                brush = Brush.radialGradient(
                    colors = listOf(t.glowViolet, Color.Transparent),
                    center = Offset(size.width * 0.20f, 0f),
                    radius = size.width * 0.95f,
                )
            )
            drawRect(
                brush = Brush.radialGradient(
                    colors = listOf(t.glowMint, Color.Transparent),
                    center = Offset(size.width, size.height * 0.12f),
                    radius = size.width * 0.85f,
                )
            )
        }
    }
}

/**
 * 渐变描边卡片（Hero / 作者卡共用技巧）：
 * 外层 padding 1px 渐变底 → 1px 描边；内层渐变填充。
 * 渐变角度按 CSS 语义计算（150deg / 165deg 等）。
 */
@Composable
fun NcGradientBorder(
    radius: Dp,
    modifier: Modifier = Modifier,
    borderColors: List<Color>,
    borderStops: List<Float>,
    borderAngle: Float,
    cardColors: List<Color>,
    cardStops: List<Float>,
    cardAngle: Float,
    content: @Composable BoxScope.() -> Unit,
) {
    BoxWithConstraints(
        modifier
            .fillMaxWidth()
    ) {
        val wPx = constraints.maxWidth.toFloat()
        val hPx = constraints.maxHeight.let { if (it == Constraints.Infinity) 220f else it.toFloat() }
        Box(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(radius))
                .background(ncLinearGradient(borderColors, borderStops, borderAngle, wPx, hPx))
                .padding(1.dp)
        ) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(radius - 1.dp))
                    .background(ncLinearGradient(cardColors, cardStops, cardAngle, wPx, hPx)),
                content = content,
            )
        }
    }
}

/** 顶部页头 .hd：眉题 9.5/ls3 + 标题 30/800 + 右侧 36 r13 圆钮。 */
@Composable
fun NcHeader(
    kicker: String,
    title: String,
    /** 标题文字颜色；null = 跟随主题（t.tx）。定制 → 标题栏 可覆盖。 */
    titleColor: Color? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    val t = ncTokens()
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 2.dp)
            .padding(top = 13.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = kicker,
                fontSize = 9.5.sp,
                letterSpacing = 3.sp,
                color = t.tx3,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = title,
                fontSize = 30.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = (-1).sp,
                lineHeight = 32.sp,
                // 没设自定义色时跟随全局强调色（原来固定 t.tx，跟配色完全脱节）
                color = titleColor ?: me.diksu.ultra.foxsu.ui.theme.TitleInk.solid(me.diksu.ultra.foxsu.ui.theme.currentAccent.primary),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        // 有真实新版本时，标题栏右侧出现流动胶囊（无更新时零占位）
        NcUpdateCapsule(Modifier.padding(end = 7.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(7.dp), verticalAlignment = Alignment.CenterVertically) {
            actions()
        }
    }
}

/**
 * 头部圆钮 .ha .b —— 36×36 r13：暗色白 5.5% 底 + 发丝描边；
 * 亮色白底 + rgba(15,20,45,.08) 描边。
 * active 态：160deg #8B7BFF→#6A57F5 渐变 + 白色图标 + 紫色投影。
 */
@Composable
fun NcIconButton(
    iconRes: Int,
    onClick: () -> Unit,
    active: Boolean = false,
) {
    val t = ncTokens()
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val scale by animateFloatAsState(
        if (pressed) 0.9f else 1f,
        spring(0.55f, Spring.StiffnessMedium),
        label = "hb",
    )
    Box(
        Modifier
            .size(40.dp)
            .scale(scale)
            .clip(RoundedCornerShape(14.dp))
            .background(
                if (active) Brush.linearGradient(
                    listOf(t.vio, t.vio2),
                    start = Offset.Zero,
                    end = Offset(40f, 40f),
                ) else Brush.linearGradient(listOf(t.fill, t.fill))
            )
            .then(
                if (active) Modifier
                else Modifier.border(1.dp, t.line, RoundedCornerShape(13.dp))
            )
            .clickable(interactionSource = src, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = null,
            tint = if (active) Color.White else t.tx,
            modifier = Modifier.size(18.dp),
        )
    }
}

/** 分区标题 .gtitle —— 9px / ls2.4 / tx3 / 700，padding 10px 6px 5px。 */
@Composable
fun NcSectionLabel(text: String, modifier: Modifier = Modifier) {
    val t = ncTokens()
    Text(
        text = text,
        fontSize = 9.sp,
        letterSpacing = 2.4.sp,
        color = t.tx3,
        fontWeight = FontWeight.Bold,
        modifier = modifier.padding(start = 2.dp, top = 10.dp, bottom = 5.dp),
    )
}

/** 分组卡 .group —— r20，底 s1 + 发丝描边（亮色补轻投影）。 */
@Composable
fun NcGroupCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val t = ncTokens()
    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(t.s1)
            .border(1.dp, t.line, RoundedCornerShape(20.dp)),
        content = content,
    )
}

/**
 * 分组行 .row —— padding 8.5/15，gap 12；图标 34 r11；标题 13/650；副标题 10.5 tx2。
 * 与上一行之间画 1px 分隔线（left 57 / right 16）。
 */
@Composable
fun NcRow(
    iconRes: Int,
    title: String,
    subtitle: String? = null,
    subtitleMono: Boolean = false,
    accent: NcAccentTone = NcAccentTone.Default,
    showDivider: Boolean = false,
    onClick: (() -> Unit)? = null,
    trailing: @Composable (() -> Unit)? = null,
) {
    val t = ncTokens()
    Column(Modifier.fillMaxWidth()) {
        if (showDivider) {
            Canvas(Modifier.fillMaxWidth().padding(start = 57.dp, end = 16.dp).height(1.dp)) {
                drawRect(t.line)
            }
        }
        Row(
            Modifier
                .fillMaxWidth()
                .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
                .padding(horizontal = 15.dp, vertical = 8.5.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            NcIconBox(iconRes, accent)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = title,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = (-0.1).sp,
                    color = t.tx,
                )
                if (subtitle != null) {
                    Spacer(Modifier.height(3.dp))
                    Text(
                        text = subtitle,
                        fontSize = if (subtitleMono) 9.5.sp else 10.5.sp,
                        color = if (subtitleMono) t.rowDescMono else t.rowDesc,
                        fontFamily = if (subtitleMono) ncMono else null,
                        lineHeight = if (subtitleMono) 14.sp else 16.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (trailing != null) {
                Spacer(Modifier.width(10.dp))
                trailing()
            }
        }
    }
}

/** 34×34 r11 图标盒（四色系）。 */
@Composable
fun NcIconBox(iconRes: Int, accent: NcAccentTone, size: Dp = 34.dp) {
    val t = ncTokens()
    val (bg, fg) = when (accent) {
        NcAccentTone.Default -> t.icDefaultBg to t.icDefaultFg
        NcAccentTone.Violet -> t.icVioletBg to t.icVioletFg
        NcAccentTone.Mint -> t.icMintBg to t.icMintFg
        NcAccentTone.Red -> t.icRedBg to t.icRedFg
        NcAccentTone.Amber -> t.icAmberBg to t.icAmberFg
    }
    Box(
        Modifier.size(size).clip(RoundedCornerShape(11.dp)).background(bg),
        contentAlignment = Alignment.Center,
    ) {
        Icon(painterResource(iconRes), null, tint = fg, modifier = Modifier.size(17.dp))
    }
}

enum class NcAccentTone { Default, Violet, Mint, Red, Amber }

/** 右侧 chevron（15，stroke #3F4557 / 亮色 #C3C8D4）。 */
@Composable
fun NcChevron(tint: Color? = null) {
    val t = ncTokens()
    Icon(
        painter = painterResource(me.diksu.ultra.R.drawable.fx_nc_chev),
        contentDescription = null,
        tint = tint ?: t.chevron,
        modifier = Modifier.size(15.dp),
    )
}

/** 小徽章 .badge —— 8.5/800/padding 3-8/r6。 */
@Composable
fun NcBadge(text: String, color: Color, bgAlpha: Float = 0.12f) {
    Box(
        Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(color.copy(alpha = bgAlpha))
            .padding(horizontal = 8.dp, vertical = 3.dp)
    ) {
        Text(text, fontSize = 8.5.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 0.8.sp, color = color)
    }
}

/** 代码卡 .codecard —— r16 渐变底 + 左侧 2px violet→mint 光条 + 眉题 + mono 值。 */
@Composable
fun NcCodeCard(kicker: String, value: String, tag: String? = null, iconRes: Int? = null) {
    val t = ncTokens()
    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Brush.linearGradient(listOf(t.codeTop, t.codeBottom)))
            .border(1.dp, t.codeBorder, RoundedCornerShape(16.dp))
    ) {
        // 左侧 2px 光条
        Box(
            Modifier
                .align(Alignment.CenterStart)
                .width(2.dp)
                .fillMaxHeight()
                .background(Brush.verticalGradient(listOf(t.codeStripeTop, t.codeStripeBottom)))
        )
        Column(Modifier.padding(start = 13.dp, end = 13.dp, top = 10.dp, bottom = 11.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (iconRes != null) {
                    Icon(painterResource(iconRes), null, tint = t.codeKicker, modifier = Modifier.size(12.dp))
                    Spacer(Modifier.width(7.dp))
                }
                Text(
                    kicker,
                    fontSize = 8.5.sp,
                    letterSpacing = 2.2.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = t.codeKicker,
                )
                if (tag != null) {
                    Spacer(Modifier.weight(1f))
                    Text(tag, fontSize = 8.sp, letterSpacing = 1.2.sp, color = t.codeTag)
                }
            }
            Spacer(Modifier.height(7.dp))
            Text(
                text = value,
                fontFamily = ncMono,
                fontSize = 9.3.sp,
                lineHeight = 15.sp,
                letterSpacing = (-0.15).sp,
                color = t.codeValue,
            )
        }
    }
}

/** 开关 .sw —— 42×25，thumb 19，on 渐变 #6A57F5→#8B7BFF。 */
@Composable
fun NcSwitch(checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    val t = ncTokens()
    val offset by animateFloatAsState(
        if (checked) 20f else 3f,
        tween(220, easing = NcEasing),
        label = "thumb",
    )
    Box(
        Modifier
            .size(42.dp, 25.dp)
            .clip(CircleShape)
            .background(
                if (checked) Brush.horizontalGradient(listOf(t.vio2, t.vio))
                else Brush.horizontalGradient(listOf(t.swOffBg, t.swOffBg))
            )
            .clickable { onCheckedChange(!checked) },
    ) {
        Box(
            Modifier
                .offset(x = offset.dp, y = 3.dp)
                .size(19.dp)
                .clip(CircleShape)
                .background(if (checked) Color.White else t.swThumbOff)
        )
    }
}

private val NcEasing = androidx.compose.animation.core.CubicBezierEasing(0.2f, 1.2f, 0.4f, 1f)

/** 搜索框 .search —— r15 fill 5% + 发丝边框。 */
@Composable
fun NcSearchField(value: String, onValueChange: (String) -> Unit, placeholder: String, iconRes: Int) {
    val t = ncTokens()
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(15.dp))
            .background(t.searchBg)
            .border(1.dp, t.searchBorder, RoundedCornerShape(15.dp))
            .padding(horizontal = 14.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(painterResource(iconRes), null, tint = t.tx3, modifier = Modifier.size(15.dp))
        Spacer(Modifier.width(9.dp))
        Box(Modifier.weight(1f)) {
            if (value.isEmpty()) {
                Text(placeholder, fontSize = 12.sp, color = t.searchText)
            }
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                textStyle = androidx.compose.ui.text.TextStyle(fontSize = 12.sp, color = t.tx),
                cursorBrush = SolidColor(t.vio),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/** stat3 数据条 —— 等分 + 竖线分隔，值 21/800。 */
@Composable
fun NcStat3(items: List<Triple<String, String, Color?>>) {
    val t = ncTokens()
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(t.s1)
            .border(1.dp, t.line, RoundedCornerShape(20.dp))
            .padding(horizontal = 15.dp, vertical = 14.dp)
    ) {
        items.forEachIndexed { i, (k, v, c) ->
            if (i > 0) {
                Box(Modifier.width(1.dp).height(34.dp).background(t.line).align(Alignment.CenterVertically))
                Spacer(Modifier.width(14.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(k, fontSize = 8.5.sp, letterSpacing = 1.6.sp, color = t.tx3, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(5.dp))
                Text(
                    v,
                    fontSize = 21.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = (-0.8).sp,
                    color = c ?: t.tx,
                )
            }
        }
    }
}

/**
 * Hero 卡右上 180px 紫色光晕 —— **紫色必须向外透明扩散**。
 *
 * 设计稿：`.hero .glow { width:180px; height:180px; border-radius:50%;
 *   background:radial-gradient(circle, rgba(139,123,255,.35), transparent 65%);
 *   right:-50px; top:-70px; filter:blur(6px) }`
 *
 * 实现：把圆心定位到卡片右上角外侧（right:-50 → 圆心 x = 卡片宽 + 50 - 90），
 * 用「中心浓 → 中段半透 → 边缘全透明」的三段径向渐变绘制，
 * 保证紫色平滑衰减到全透明，不会出现硬边圆盘。
 */
@Composable
fun NcHeroGlow(t: NcTokens, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val r = size.minDimension / 2f
        // 圆心 = 180px 盒子中心；65% 处完全透明 → 紫色向外平滑扩散
        drawRect(
            brush = Brush.radialGradient(
                0.0f to t.heroGlow.copy(alpha = t.heroGlowAlpha),
                0.35f to t.heroGlow.copy(alpha = t.heroGlowAlpha * 0.55f),
                0.65f to t.heroGlow.copy(alpha = t.heroGlowAlpha * 0.14f),
                1.0f to Color.Transparent,
                center = Offset(size.width / 2f, size.height / 2f),
                radius = r,
            )
        )
    }
}

/**
 * 环形状态 .ringwrap —— 56×56，r24，stroke 3，轨道白 8%。
 *
 * 图标与颜色**完全由真实 HeroPhase 决定**，不再是一个永远画对勾的装饰：
 * · WORKING         → ✓（mint，满环）
 * · PENDING_REBOOT  → ✓（amber，满环，刷写已成功、等待重启）
 * · FLASHING        → 刷新图标 + 不定量旋转弧（不伪造进度）
 * · ATTENTION       → !（amber）
 * · 其余（未安装 / 刷写失败 / 不支持）→ ×（red）
 */
@Composable
fun NcHeroRing(modifier: Modifier = Modifier, phase: HeroPhase) {
    val t = ncTokens()
    val track = if (t.dark) Color(0x14FFFFFF) else Color(0x1F0F142D)
    val tint = when (phase) {
        HeroPhase.WORKING -> t.mint
        HeroPhase.PENDING_REBOOT, HeroPhase.FLASHING, HeroPhase.ATTENTION -> t.amber
        else -> t.red
    }
    val glyph = when (phase) {
        HeroPhase.WORKING, HeroPhase.PENDING_REBOOT -> me.diksu.ultra.R.drawable.fx_nc_check
        HeroPhase.FLASHING -> me.diksu.ultra.R.drawable.fx_nc_refresh
        HeroPhase.ATTENTION -> me.diksu.ultra.R.drawable.fx_nc_alert
        else -> me.diksu.ultra.R.drawable.fx_nc_cross
    }
    val spin = rememberInfiniteTransition(label = "ring")
    val angle by spin.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(1100, easing = LinearEasing)),
        label = "ring-spin",
    )
    Box(modifier.size(56.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = 3.dp.toPx()
            val inset = stroke / 2
            val d = size.minDimension - stroke
            drawArc(
                color = track,
                startAngle = 0f, sweepAngle = 360f, useCenter = false,
                topLeft = Offset(inset, inset),
                size = androidx.compose.ui.geometry.Size(d, d),
                style = Stroke(stroke),
            )
            if (phase == HeroPhase.FLASHING) {
                drawArc(
                    color = tint,
                    startAngle = angle,
                    sweepAngle = 96f,
                    useCenter = false,
                    topLeft = Offset(inset, inset),
                    size = androidx.compose.ui.geometry.Size(d, d),
                    style = Stroke(stroke, cap = androidx.compose.ui.graphics.StrokeCap.Round),
                )
            } else {
                drawArc(
                    color = tint,
                    startAngle = -90f,
                    sweepAngle = 360f,
                    useCenter = false,
                    topLeft = Offset(inset, inset),
                    size = androidx.compose.ui.geometry.Size(d, d),
                    style = Stroke(stroke, cap = androidx.compose.ui.graphics.StrokeCap.Round),
                )
            }
        }
        Icon(
            painter = painterResource(glyph),
            contentDescription = null,
            tint = tint,
            modifier = Modifier
                .size(19.dp)
                .rotate(if (phase == HeroPhase.FLASHING) angle else 0f),
        )
    }
}

/** 呼吸状态点 .pulse —— 7px mint + 3px 光环 + 12px 辉光，2.4s 呼吸。 */
@Composable
fun NcPulseDot(color: Color, modifier: Modifier = Modifier, alpha: Float = 1f) {
    Box(modifier.size(7.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            drawCircle(
                color = color.copy(alpha = 0.16f * alpha),
                radius = size.minDimension / 2 + 3.dp.toPx(),
            )
            drawCircle(color = color.copy(alpha = alpha), radius = size.minDimension / 2)
        }
    }
}

/** 空态光球 .empty .orb —— 104 圆 + inset 描边 + 外发光 + 中心 38px 图标。 */
@Composable
fun NcEmptyOrb(iconRes: Int, modifier: Modifier = Modifier) {
    val t = ncTokens()
    Box(modifier.size(104.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            // 外发光 0 0 40px
            drawCircle(color = t.orbGlow, radius = size.width / 2 + 10f)
            // 主体 radial：30% 25% 中心浓 → 60% 淡 → 72% 透明
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(t.orbInner, t.orbMid, Color.Transparent),
                    center = Offset(size.width * 0.3f, size.height * 0.25f),
                    radius = size.width * 0.72f,
                ),
                radius = size.width / 2,
            )
            // inset 1px 描边
            drawCircle(
                color = t.orbRing,
                radius = size.width / 2 - 1f,
                style = Stroke(2f),
            )
        }
        Icon(painterResource(iconRes), null, tint = t.orbIcon, modifier = Modifier.size(38.dp))
    }
}

/** FAB —— 56 圆，160deg #8B7BFF→#6A57F5 + 白色内描边 + 紫色投影。 */
@Composable
fun NcFab(iconRes: Int, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val t = ncTokens()
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val sc by animateFloatAsState(
        if (pressed) 0.92f else 1f,
        spring(0.6f, Spring.StiffnessMedium),
        label = "fab",
    )
    Box(
        modifier
            .size(56.dp)
            .scale(sc)
            .clip(CircleShape)
            .background(
                Brush.linearGradient(
                    listOf(t.vio, t.vio2),
                    start = Offset.Zero,
                    end = Offset(56f, 56f),
                )
            )
            .border(1.dp, Color(0xB3FFFFFF), CircleShape)
            .clickable(interactionSource = src, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(painterResource(iconRes), null, tint = Color.White, modifier = Modifier.size(24.dp))
    }
}

/** 通用 ghost 取消按钮 .rbcancel —— r14 白 10% 描边。 */
@Composable
fun NcCancelButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val t = ncTokens()
    Box(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(t.cancelBg)
            .border(1.dp, t.cancelBorder, RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 11.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 3.sp, color = t.cancelText)
    }
}

/** 底栏数字徽标 —— 数字严格居中于圆形背景。 */
@Composable
fun NcBadgeDot(count: Int, modifier: Modifier = Modifier) {
    val t = ncTokens()
    Box(
        modifier
            .size(14.dp)
            .clip(CircleShape)
            .background(t.badgeBg),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = if (count > 9) "9+" else "$count",
            fontSize = 8.sp,
            lineHeight = 14.sp,
            fontWeight = FontWeight.ExtraBold,
            color = Color.White,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
