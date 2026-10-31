package me.diksu.ultra.foxsu.ui.component.bottombar

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import me.diksu.ultra.foxsu.data.settings.CustomizationStore
import me.diksu.ultra.foxsu.ui.LocalMainPagerState
import me.diksu.ultra.foxsu.ui.component.dockfx.dockRingFx
import me.diksu.ultra.foxsu.ui.screen.ag.Ag

/**
 * 传统底栏的 20 套结构（Dock Kit v3.1，结构版）。
 *
 * 与「胶囊样式」是两条正交的轴：胶囊管的是浮岛形态下的按钮造型，
 * 这里管的是「贴底通栏」这条栏本身的结构语言——齿边、星形、温度计竖槽、
 * 棱柱、光环、分体胶囊、舱门、折纸、蜂窝、弧形、信号灯、堆叠、侧锚、
 * 穿孔带、镜像、浮雕、折叠屏中缝。
 *
 * 配色不照抄设计稿的固定橙：设计稿是一张独立的 demo 页，颜色写死无妨；
 * 放进 App 必须跟着「全局强调色」走，否则换强调色/换皮肤时这条栏就是孤岛。
 * 底材取主题卡面（暗色提亮一档），选中态取强调色与其渐变的次级色。
 */
internal enum class ClassicDockStyle(
    val id: Int,
    val title: String,
    val hint: String,
    /** 栏体高度（不含系统导航条内边距）。 */
    val height: Dp,
    /** 选中项图标上浮。 */
    val lift: Dp = 0.dp,
    /** 选中项图标缩放。 */
    val selScale: Float = 1f,
    /** 选中项图标旋转（度）。 */
    val selRotate: Float = 0f,
    /** 每项是否自带独立卡面（棱柱/分体胶囊/折纸/蜂窝/堆叠卡）。 */
    val plate: Boolean = false,
    /** 选中时是否整块填强调色并反白（而不是只染图标）。 */
    val solid: Boolean = false,
    /** 未选中时隐藏文字（分体胶囊）。 */
    val labelOnSelOnly: Boolean = false,
) {
    /** 0 保留原来的「发丝线 + 顶部短条」经典外观。 */
    CLASSIC(0, "经典", "顶部发丝线 + 选中短条，最稳妥，不抢内容", 62.dp),

    SAW(1, "锯齿顶边", "顶边一排三角齿，选中项像被抓住一个齿往上提", 70.dp, lift = (-3).dp, selRotate = -4f),
    ASYM(2, "不对称圆角", "左上大圆、右下大圆，选中项放大，圆角跟着呼吸", 66.dp, selScale = 1.15f),
    STAR(3, "四角星绽放", "选中项背后浮出 8 点星形并旋转 45° 绽放", 64.dp),
    THERMO(4, "温度计竖槽", "右侧竖槽液位随选中项升降，像一根温度计", 68.dp, selScale = 1.1f),
    PRISM(5, "旋转棱柱", "选中块绕 X 轴立起，露出深色侧面", 64.dp, lift = (-4).dp, plate = true, solid = true),
    HALO(6, "呼吸光环", "选中项外圈持续扩散脉冲，2 秒一拍", 66.dp),
    CAPSULE(7, "分体胶囊", "四个独立胶囊，选中那颗拉长并展开文字", 52.dp, plate = true, solid = true, labelOnSelOnly = true),
    HATCH(8, "舱门开启", "图标抬起，门缝橙线从左展开", 64.dp, lift = (-5).dp),
    NUM(9, "序号角标", "选中项右上角淡入等宽编号", 62.dp, lift = (-1).dp, selScale = 1.1f),
    PAPER(10, "折纸页角", "选中项拔高，右上角折出一个三角折痕", 64.dp, selScale = 1.1f, plate = true),
    HONEY(11, "蜂窝六边", "六边形切角，选中填满强调色", 60.dp, plate = true, solid = true),
    ARC(12, "弧形塌陷", "底栏整体下凹成弧，选中图标沉进弧里", 76.dp, lift = 3.dp, selScale = 1.12f),
    TOOTH(13, "三角齿", "比锯齿更尖的三角齿，选中项啄一下", 70.dp, lift = (-4).dp),
    RAIL(14, "滑轨信号灯", "顶部一条横轨，信号球滑到选中项上方", 62.dp, selScale = 1.1f),
    STACK(15, "堆叠卡", "负边距叠层，选中的那张被抽出来", 70.dp, plate = true, solid = true),
    ANCHOR(16, "侧锚引线", "左缘锚点球引一条线到选中项", 64.dp, selScale = 1.12f),
    PERF(17, "穿孔带", "顶缘一排冲孔，像吉他背带", 64.dp, selScale = 1.1f),
    MIRROR(18, "镜像折叠", "底部反光带，选中图标抬起破对称", 66.dp, lift = (-2).dp, selScale = 1.1f),
    RELIEF(19, "浮雕凸面", "整条栏是椭圆凸面，带内高光", 70.dp, lift = (-1).dp, selScale = 1.12f),
    FOLD(20, "折叠屏中缝", "中央竖缝，选中项文字字距向中缝聚拢", 64.dp, selScale = 1.1f);

    companion object {
        fun fromId(id: Int): ClassicDockStyle = entries.firstOrNull { it.id == id } ?: CLASSIC
    }
}

/** 当前选中的传统底栏结构。 */
internal fun currentClassicStyle(): ClassicDockStyle =
    ClassicDockStyle.fromId(CustomizationStore.current.classicDockStyle)

@Composable
internal fun ClassicStructureBar(
    style: ClassicDockStyle,
    theme: DockTheme,
    navigationBadge: NavigationBadgeState,
    modifier: Modifier = Modifier,
) {
    val mainState = LocalMainPagerState.current
    val items = BottomBarDestination.entries.toList()
    val custom = CustomizationStore.current
    val dark = Ag.isDark
    val ac = Ag.accent
    val selected = mainState.selectedPage.coerceIn(0, items.lastIndex)

    val navInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val alphaK = custom.bottomBarAlpha.coerceIn(0.2f, 1f)

    // 底材：暗色用比页面亮一档的实色（半透明会让齿边/切角露馅），亮色用纯白。
    val surface = if (dark) Color(0xFF1E1D30) else Color.White
    val barBg = surface.copy(alpha = (if (dark) 1f else 0.97f) * alphaK)
    val hairline = if (dark) Color(0x1AFFFFFF) else Color(0x121E1946)
    val unsel = theme.unselected

    val swipeScope = androidx.compose.runtime.rememberCoroutineScope()
    val swipeOn = LocalDockSwipeEnabled.current

    // 呼吸环只在选中 HALO 时跑，别让一条常驻无限动画拖住其它 19 套
    val haloPhase = if (style == ClassicDockStyle.HALO) {
        val t = rememberInfiniteTransition(label = "halo")
        t.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(2000, easing = androidx.compose.animation.core.LinearEasing), RepeatMode.Restart),
            label = "halo-phase",
        ).value
    } else {
        0f
    }

    Column(
        modifier
            .fillMaxWidth()
            .dockSwipeToPager(mainState.pagerState, swipeOn, swipeScope)
            // 底色要先铺到系统导航条那一段，否则栏体裁出圆角/齿形后，
            // 手势条下面会露出一条页面色，看起来像底栏悬空。
            .background(barBg)
            .padding(bottom = navInset),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(style.height)
                .clip(shapeOf(style, androidx.compose.ui.platform.LocalDensity.current))
                .background(barBg)
                .drawBehind {
                    drawStructureBackdrop(
                        style = style,
                        accent = ac.primary,
                        accent2 = ac.secondary,
                        surface = surface,
                        hairline = hairline,
                        dark = dark,
                        selected = selected,
                        count = items.size,
                        haloPhase = haloPhase,
                    )
                }
                .dockRingFx(
                    accent = ac.primary,
                    accent2 = ac.secondary,
                    trigger = selected,
                    anchor = (selected + 0.5f) / items.size.coerceAtLeast(1),
                    capsule = false,
                ),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = if (style == ClassicDockStyle.CAPSULE) 6.dp else 8.dp),
                verticalAlignment = if (style == ClassicDockStyle.ARC) Alignment.Top else Alignment.CenterVertically,
            ) {
                items.forEachIndexed { index, item ->
                    val on = selected == index
                    // 分体胶囊：选中的那颗按 1.9 倍宽展开，weight 是唯一能吃到剩余空间的做法
                    val weight by animateFloatAsState(
                        targetValue = if (style == ClassicDockStyle.CAPSULE && on) 1.9f else 1f,
                        animationSpec = spring(dampingRatio = 0.72f, stiffness = 420f),
                        label = "struct-weight-$index",
                    )
                    StructureTab(
                        item = item,
                        index = index,
                        style = style,
                        selected = on,
                        navigationBadge = navigationBadge,
                        accent = ac.primary,
                        accent2 = ac.secondary,
                        surface = surface,
                        unselected = unsel,
                        dark = dark,
                        rainbow = custom.navRainbow,
                        onClick = { mainState.animateToPage(index) },
                        modifier = Modifier
                            .weight(weight)
                            .fillMaxHeight(),
                    )
                }
            }
        }
    }
}

/* ───────────────────────── 形状 ───────────────────────── */

private fun shapeOf(style: ClassicDockStyle, density: androidx.compose.ui.unit.Density): Shape = when (style) {
    ClassicDockStyle.SAW -> sawShape(24.dp, 12.dp, density)
    ClassicDockStyle.TOOTH -> sawShape(18.dp, 10.dp, density)
    ClassicDockStyle.ASYM -> RoundedCornerShape(34.dp, 8.dp, 34.dp, 8.dp)
    ClassicDockStyle.STAR -> RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp)
    ClassicDockStyle.THERMO -> RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)
    ClassicDockStyle.HALO -> RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
    ClassicDockStyle.HATCH -> RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp)
    ClassicDockStyle.NUM -> RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)
    ClassicDockStyle.PAPER -> RoundedCornerShape(topStart = 14.dp, topEnd = 14.dp)
    ClassicDockStyle.ARC -> RoundedCornerShape(bottomStart = 40.dp, bottomEnd = 40.dp)
    ClassicDockStyle.ANCHOR -> RoundedCornerShape(topEnd = 20.dp, bottomEnd = 20.dp)
    ClassicDockStyle.PERF -> RoundedCornerShape(topStart = 14.dp, topEnd = 14.dp)
    ClassicDockStyle.MIRROR -> RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)
    ClassicDockStyle.RELIEF -> RoundedCornerShape(topStart = 50.dp, topEnd = 50.dp)
    ClassicDockStyle.FOLD, ClassicDockStyle.PRISM, ClassicDockStyle.CAPSULE,
    ClassicDockStyle.HONEY, ClassicDockStyle.STACK,
    -> RoundedCornerShape(0.dp)
    else -> RoundedCornerShape(0.dp)
}

/**
 * 顶部锯齿/尖齿：每个周期一个三角，峰在顶部、谷落在 [depth] 处。
 * 设计稿用 conic-gradient 的 mask 做这件事；Compose 没有 mask，
 * 直接把整条栏裁成这个轮廓，视觉等价（底栏底下是页面色，看不出差别）。
 */
private fun sawShape(period: Dp, depth: Dp, density: androidx.compose.ui.unit.Density): Shape =
    GenericShape { size, _ ->
    val p = with(density) { period.toPx() }.coerceAtLeast(1f)
    val d = with(density) { depth.toPx() }
    moveTo(0f, d)
    var x = 0f
    while (x < size.width) {
        lineTo(x + p / 2f, 0f)
        lineTo((x + p).coerceAtMost(size.width), d)
        x += p
    }
    lineTo(size.width, size.height)
    lineTo(0f, size.height)
    close()
}

/* ───────────────────────── 容器装饰 ───────────────────────── */

private fun DrawScope.drawStructureBackdrop(
    style: ClassicDockStyle,
    accent: Color,
    accent2: Color,
    surface: Color,
    hairline: Color,
    dark: Boolean,
    selected: Int,
    count: Int,
    haloPhase: Float,
) {
    val w = size.width
    val h = size.height
    val slot = w / count.coerceAtLeast(1)
    val cx = slot * (selected + 0.5f)

    when (style) {
        ClassicDockStyle.STAR -> {
            // 8 点星：两个正方形错 45° 叠成，用多边形近似（clip 一个 45° 旋转的方形）
            val r = 26.dp.toPx()
            val cy = h / 2f
            starPath(cx, cy - 2.dp.toPx(), r).let { p ->
                drawPath(
                    p,
                    brush = Brush.radialGradient(
                        listOf(accent.copy(alpha = 0.26f), accent2.copy(alpha = 0.10f)),
                        center = Offset(cx, cy),
                        radius = r * 1.6f,
                    ),
                )
            }
        }

        ClassicDockStyle.THERMO -> {
            val tubeW = 6.dp.toPx()
            val left = w - 7.dp.toPx() - tubeW
            val top = 9.dp.toPx()
            val bottom = h - 9.dp.toPx()
            val r = tubeW / 2f
            drawRoundRect(
                color = accent.copy(alpha = 0.14f),
                topLeft = Offset(left, top),
                size = Size(tubeW, bottom - top),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(r, r),
            )
            val level = (selected + 1f) / count.coerceAtLeast(1)
            val fillH = (bottom - top) * level
            drawRoundRect(
                brush = Brush.verticalGradient(listOf(accent2, accent)),
                topLeft = Offset(left, bottom - fillH),
                size = Size(tubeW, fillH),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(r, r),
            )
        }

        ClassicDockStyle.HALO -> {
            // 呼吸环：0→1 的相位驱动半径与透明度，2 秒一拍
            val r = 19.dp.toPx() * (0.55f + 0.95f * haloPhase)
            drawCircle(
                color = accent.copy(alpha = (1f - haloPhase) * 0.55f),
                radius = r,
                center = Offset(cx, h / 2f - 4.dp.toPx()),
                style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.5.dp.toPx()),
            )
        }

        ClassicDockStyle.HATCH -> {
            // 舱门门缝：从左侧 4dp 向内展开
            val inset = 4.dp.toPx()
            drawRoundRect(
                color = accent,
                topLeft = Offset(cx - slot / 2f + inset, h - 5.dp.toPx()),
                size = Size(slot - inset * 2, 3.dp.toPx()),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(1.5.dp.toPx()),
            )
        }

        ClassicDockStyle.PAPER -> {
            // 折痕三角：右上角 14dp
            val d = 14.dp.toPx()
            val right = cx + slot / 2f
            drawPath(
                Path().apply {
                    moveTo(right - d, 0f)
                    lineTo(right, 0f)
                    lineTo(right, d)
                    close()
                },
                color = accent.copy(alpha = 0.85f),
            )
        }

        ClassicDockStyle.RAIL -> {
            val top = 5.dp.toPx()
            drawRoundRect(
                color = accent.copy(alpha = 0.14f),
                topLeft = Offset(12.dp.toPx(), top),
                size = Size(w - 24.dp.toPx(), 2.dp.toPx()),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(1.dp.toPx()),
            )
            drawCircle(
                color = accent.copy(alpha = 0.18f),
                radius = 7.dp.toPx(),
                center = Offset(cx, top + 1.dp.toPx()),
            )
            drawCircle(color = accent, radius = 4.dp.toPx(), center = Offset(cx, top + 1.dp.toPx()))
        }

        ClassicDockStyle.ANCHOR -> {
            val cy = h / 2f
            // 引线：从锚点右侧伸到选中项中心
            drawRect(
                brush = Brush.horizontalGradient(
                    listOf(accent, accent.copy(alpha = 0.2f)),
                    startX = 14.dp.toPx(),
                    endX = cx,
                ),
                topLeft = Offset(14.dp.toPx(), cy - 1.dp.toPx()),
                size = Size((cx - 14.dp.toPx()).coerceAtLeast(0f), 2.dp.toPx()),
            )
            drawCircle(
                brush = Brush.linearGradient(listOf(accent, accent2)),
                radius = 7.dp.toPx(),
                center = Offset(7.dp.toPx(), cy),
            )
            drawCircle(color = accent.copy(alpha = 0.14f), radius = 11.dp.toPx(), center = Offset(7.dp.toPx(), cy))
        }

        ClassicDockStyle.PERF -> {
            val r = 2.dp.toPx()
            val step = 16.dp.toPx()
            val y = 5.5.dp.toPx()
            var x = 8.dp.toPx() + step / 2f
            while (x < w - 8.dp.toPx()) {
                drawCircle(color = hairline, radius = r, center = Offset(x, y))
                x += step
            }
        }

        ClassicDockStyle.MIRROR -> {
            drawRect(
                brush = Brush.verticalGradient(
                    listOf(accent.copy(alpha = 0.06f), Color.Transparent),
                    startY = h - 22.dp.toPx(),
                    endY = h,
                ),
                topLeft = Offset(0f, h - 22.dp.toPx()),
                size = Size(w, 22.dp.toPx()),
            )
        }

        ClassicDockStyle.RELIEF -> {
            // 内高光：顶部 2dp 的白色描边
            drawRect(
                color = if (dark) Color(0x18FFFFFF) else Color(0xB3FFFFFF),
                topLeft = Offset(0f, 0f),
                size = Size(w, 2.dp.toPx()),
            )
        }

        ClassicDockStyle.FOLD -> {
            drawRect(
                brush = Brush.verticalGradient(
                    listOf(Color.Transparent, hairline, Color.Transparent),
                ),
                topLeft = Offset(w / 2f, 8.dp.toPx()),
                size = Size(1.dp.toPx(), h - 16.dp.toPx()),
            )
        }

        else -> Unit
    }
}

private fun starPath(cx: Float, cy: Float, r: Float): Path = Path().apply {
    // 8 点星：16 个顶点，外半径 r、内半径 0.382r（与 CSS polygon 的 50%/39% 同比例）
    val inner = r * 0.382f
    for (i in 0 until 16) {
        val angle = Math.PI / 8.0 * i - Math.PI / 2.0
        val radius = if (i % 2 == 0) r else inner
        val x = cx + (radius * Math.cos(angle)).toFloat()
        val y = cy + (radius * Math.sin(angle)).toFloat()
        if (i == 0) moveTo(x, y) else lineTo(x, y)
    }
    close()
}

/* ───────────────────────── 单个 Tab ───────────────────────── */

@Composable
private fun StructureTab(
    item: BottomBarDestination,
    index: Int,
    style: ClassicDockStyle,
    selected: Boolean,
    navigationBadge: NavigationBadgeState,
    accent: Color,
    accent2: Color,
    surface: Color,
    unselected: Color,
    dark: Boolean,
    rainbow: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()

    val scale by animateFloatAsState(
        targetValue = when {
            pressed -> 0.92f
            selected -> style.selScale
            else -> 1f
        },
        animationSpec = spring(dampingRatio = 0.45f, stiffness = if (pressed) 900f else 420f),
        label = "struct-scale-$index",
    )
    val lift by animateDpAsState(
        targetValue = if (selected) style.lift else 0.dp,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "struct-lift-$index",
    )
    val rot by animateFloatAsState(
        targetValue = if (selected) style.selRotate else 0f,
        animationSpec = spring(dampingRatio = 0.55f, stiffness = 380f),
        label = "struct-rot-$index",
    )
    // 分体胶囊的宽度动画由外层 weight 负责，这里只补一个文字淡入
    val labelWeight by animateFloatAsState(
        targetValue = if (style.labelOnSelOnly && !selected) 0f else 1f,
        animationSpec = tween(180),
        label = "struct-lb-$index",
    )

    val tint = when {
        // 整块填色的套别，选中后字与图标一律反白
        selected && style.solid -> Color.White
        selected -> accent
        rainbow -> rainbowForStructure(index)
        else -> unselected
    }
    val plateBg: Brush? = if (style.plate) {
        if (selected && style.solid) {
            Brush.verticalGradient(listOf(accent, accent2))
        } else {
            Brush.verticalGradient(listOf(surface, surface))
        }
    } else {
        null
    }

    Box(
        modifier = modifier
            .clickable(interactionSource = src, indication = null, onClick = onClick),
        contentAlignment = when (style) {
            ClassicDockStyle.PAPER, ClassicDockStyle.STACK -> Alignment.BottomCenter
            ClassicDockStyle.ARC -> Alignment.TopCenter
            else -> Alignment.Center
        },
    ) {
        // 每项独立卡面：棱柱 / 分体胶囊 / 折纸 / 蜂窝 / 堆叠卡
        if (plateBg != null) {
            val plateH = when (style) {
                ClassicDockStyle.CAPSULE -> 40.dp
                ClassicDockStyle.PAPER -> if (selected) 64.dp else 56.dp
                ClassicDockStyle.STACK -> if (selected) 68.dp else 54.dp
                ClassicDockStyle.HONEY -> 56.dp
                ClassicDockStyle.PRISM -> 64.dp
                else -> 56.dp
            }
            val animatedH by animateDpAsState(plateH, spring(dampingRatio = 0.7f, stiffness = 420f), label = "plate-h-$index")
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(animatedH)
                    .then(
                        when (style) {
                            ClassicDockStyle.CAPSULE -> Modifier
                                .padding(horizontal = 5.dp, vertical = 4.dp)
                                .clip(CircleShape)

                            ClassicDockStyle.HONEY -> Modifier
                                .padding(horizontal = 1.5.dp)
                                .clip(hexagonShape())

                            ClassicDockStyle.STACK -> Modifier
                                .fillMaxHeight()
                                .clip(RoundedCornerShape(topStart = 14.dp, topEnd = 14.dp))

                            ClassicDockStyle.PAPER -> Modifier
                                .clip(RoundedCornerShape(0.dp))

                            else -> Modifier
                        }
                    )
                    .background(plateBg),
            )
        }

        // 棱柱：整块立起，露出底面
        val prismTilt by animateFloatAsState(
            targetValue = if (style == ClassicDockStyle.PRISM && selected) -14f else 0f,
            animationSpec = spring(dampingRatio = 0.6f, stiffness = 360f),
            label = "prism-$index",
        )

        Column(
            modifier = Modifier
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    rotationZ = rot
                    if (style == ClassicDockStyle.PRISM) {
                        rotationX = prismTilt
                        cameraDistance = 12f * density
                    }
                },
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Box(contentAlignment = Alignment.Center) {
                DockIconTapFx(pressed = pressed, accent = accent, iconSize = 20.dp) {
                    Icon(
                        imageVector = dockIconOverride(item) ?: item.filledIcon,
                        contentDescription = stringResource(item.label),
                        tint = tint,
                        modifier = Modifier
                            .size(20.dp)
                            .graphicsLayer { translationY = lift.toPx() },
                    )
                }
                val badge = badgeFor(index, navigationBadge)
                if (badge != null) {
                    Box(
                        Modifier
                            .align(Alignment.TopEnd)
                            .offset(x = 7.dp, y = (-6).dp)
                            .size(15.dp)
                            .clip(CircleShape)
                            .background(accent2),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            badge.count.toString(),
                            fontSize = 9.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color.White,
                            maxLines = 1,
                        )
                    }
                }
                // 序号角标：只有选中项显示
                if (style == ClassicDockStyle.NUM && selected) {
                    Text(
                        text = "0${index + 1}",
                        fontSize = 7.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = accent,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .offset(x = 9.dp, y = (-8).dp),
                    )
                }
            }
            if (labelWeight > 0.01f) {
                Spacer(Modifier.height(if (style.height >= 68.dp) 4.dp else 3.dp))
                Text(
                    text = stringResource(item.label),
                    fontSize = if (style == ClassicDockStyle.CAPSULE) 8.5.sp else 9.5.sp,
                    fontWeight = if (selected) FontWeight.ExtraBold else FontWeight.Medium,
                    color = tint,
                    maxLines = 1,
                    modifier = Modifier.graphicsLayer {
                        alpha = labelWeight
                        translationY = lift.toPx() * if (style == ClassicDockStyle.HATCH) 1f else 0f
                    },
                )
            }
        }
    }
}

private fun hexagonShape(): Shape = GenericShape { size, _ ->
    val cut = size.width * 0.14f
    moveTo(cut, 0f)
    lineTo(size.width - cut, 0f)
    lineTo(size.width, size.height / 2f)
    lineTo(size.width - cut, size.height)
    lineTo(cut, size.height)
    lineTo(0f, size.height / 2f)
    close()
}

private fun rainbowForStructure(index: Int): Color = when (index % 4) {
    0 -> Color(0xFF6C5CE7)
    1 -> Color(0xFF14B8A6)
    2 -> Color(0xFFF59E0B)
    else -> Color(0xFFEC4899)
}
