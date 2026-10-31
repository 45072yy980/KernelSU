package me.diksu.ultra.foxsu.ui.component.bottombar

import me.diksu.ultra.foxsu.ui.component.dockfx.dockRingFx
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import me.diksu.ultra.foxsu.ui.screen.ag.drawInnerShadow
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import me.diksu.ultra.foxsu.data.settings.CustomizationStore
import me.diksu.ultra.foxsu.ui.LocalMainPagerState
import me.diksu.ultra.foxsu.ui.component.LocalAppBackdrop
import me.diksu.ultra.foxsu.ui.component.liquid.lens
import me.diksu.ultra.foxsu.ui.component.liquid.vibrancy
import top.yukonga.miuix.kmp.blur.BlendColorEntry
import top.yukonga.miuix.kmp.blur.BlurColors
import top.yukonga.miuix.kmp.blur.blur
import top.yukonga.miuix.kmp.blur.drawBackdrop
import top.yukonga.miuix.kmp.blur.textureBlur

/** 各主题的抽屉胶囊配色（设计语言驱动） */
data class DockTheme(
    val bg: Color,              // 胶囊底
    val border: Color,          // 描边
    val shadow: Color,          // 软阴影
    val selected: Color,        // 选中强调色
    val unselected: Color,      // 未选中文字/图标
    val selectedBg: Color,      // 选中底衬
    val badge: Color,           // 徽标底
    val glass: Boolean = false, // 顶部高光（沉浸光感）
    val backdrop: Color? = null, // 槽位底色（与页面底部背景同色，杜绝窗口白底透出）
)

/**
 * 底栏样式：设计稿 9 种（d0/v1/v5-v11）+ 由其他皮肤原生底栏复刻的 4 种
 * （editorial 纸面墨线 / pages 白色药丸 / glass 液态药丸 / nocturne 暗夜霓虹）。
 * 全部皮肤共用这一套选择器，样式全局生效。
 */
enum class DockStyle { D0, V1, V5, V6, V7, V8, V9, V10, V11, CENTER, EDITORIAL, PAGES, GLASS, NOCTURNE;

    /** 是否为「复刻自其他皮肤」的样式（这些不参与 V 系列的项宽/指示器逻辑） */
    val isPort: Boolean get() = this == EDITORIAL || this == PAGES || this == GLASS || this == NOCTURNE

    companion object {
        fun fromRaw(raw: String): DockStyle = when (raw) {
            "v1" -> V1; "v5" -> V5; "v6" -> V6; "v7" -> V7; "v8" -> V8
            "v9" -> V9; "v10" -> V10; "v11" -> V11; "center" -> CENTER
            "editorial" -> EDITORIAL; "pages" -> PAGES
            "glass" -> GLASS; "nocturne" -> NOCTURNE
            else -> D0
        }
    }
}

/** V5 是否停靠在右侧（定制 → 底部导航栏 → 胶囊停靠）。 */
val sideDockOnRight: Boolean get() = CustomizationStore.current.dockSide == 1

private val rainbowColors = listOf(
    Color(0xFF6C5CE7), Color(0xFFEC4899), Color(0xFF14B8A6), Color(0xFFF59E0B),
)

/**
 * 底栏统一尺寸（设计稿 .dock 的默认规格）。
 *
 * 13 种样式共用同一组尺寸：项宽 78dp、项高 60dp、间距 4dp、内衬 8dp。
 * 样式只决定「长什么样」，不决定「有多大」——切换样式时底栏的长宽不再跳变。
 */
internal object DockMetrics {
    val itemW = 78.dp
    val itemH = 60.dp
    val gap = 4.dp
    val pad = 8.dp
}

/**
 * 主题化悬浮抽屉胶囊底栏。
 *
 * 三层能力叠在一起：
 * 1. 容器造型（[DockStyle] 13 种）+ 按钮造型（[DockButton] 10 种）；
 * 2. 材质（沉浸光感 / 实时模糊 / 液态玻璃折射）；
 * 3. 行为（按下回弹、无操作 N 秒自动沉底、点屏幕召回）。
 */
@Composable
fun ThemeDockBar(
    theme: DockTheme,
    navigationBadge: NavigationBadgeState,
    modifier: Modifier = Modifier,
    onOpenDonate: () -> Unit = {},
) {
    val mainState = LocalMainPagerState.current
    val items = BottomBarDestination.entries.toList()
    val custom = CustomizationStore.current
    val style = DockStyle.fromRaw(custom.dockStyle)
    val button = DockButton.of(custom.dockButton)
    val immersive = custom.bottomBarImmersive
    val rainbow = custom.navRainbow
    val alphaK = custom.bottomBarAlpha.coerceIn(0.2f, 1f)
    val dockRadius = custom.bottomBarRadius.coerceIn(12f, 40f).dp

    val dark = me.diksu.ultra.foxsu.ui.screen.ag.Ag.isDark
    val accent = me.diksu.ultra.foxsu.ui.screen.ag.Ag.accent

    // ── 自动收起：无操作 N 秒后沉到屏幕外，点一下屏幕再回来 ──
    // hideSec 来自定制页（0 = 关闭）。每次触摸 / 切页都会把 reveal.pulse 加一，
    // 于是这个 effect 被取消重来：读秒归零、胶囊立刻滑回来。
    val reveal = LocalDockReveal.current
    val hideSec = custom.dockAutoHide
    // 读秒：1 → 0 线性走完就收起；底缘那道细线就是它，所见即所得
    val countdown = remember { Animatable(1f) }
    LaunchedEffect(hideSec, reveal.pulse, mainState.selectedPage) {
        countdown.snapTo(1f)
        reveal.hidden = false
        if (hideSec <= 0) return@LaunchedEffect
        countdown.animateTo(0f, tween(hideSec * 1000, easing = LinearEasing))
        reveal.hidden = true
    }
    // 收起时整体下移：76dp 胶囊 + 22dp 内衬，150dp 一定完全移出视口
    val sinkY by animateDpAsState(
        targetValue = if (reveal.hidden) 150.dp else 0.dp,
        animationSpec = if (reveal.hidden) {
            spring(dampingRatio = 0.9f, stiffness = 320f)
        } else {
            spring(dampingRatio = 0.55f, stiffness = 460f)
        },
        label = "dock-sink",
    )
    val sinkAlpha by animateFloatAsState(
        targetValue = if (reveal.hidden) 0f else 1f,
        animationSpec = tween(if (reveal.hidden) 260 else 200),
        label = "dock-sink-alpha",
    )

    // ── 材质：液态玻璃（真折射） > 实时模糊 > 静态磨砂 ──
    val appBackdrop = LocalAppBackdrop.current
    val liquid = custom.bottomBarLiquidGlass && appBackdrop != null
    val blurOn = !liquid && me.diksu.ultra.foxsu.ui.theme.LocalEnableBlur.current && appBackdrop != null

    val effBg = when {
        liquid -> theme.bg.copy(alpha = theme.bg.alpha * 0.18f * alphaK)
        blurOn -> theme.bg.copy(alpha = theme.bg.alpha * 0.55f * alphaK)
        immersive -> theme.bg.copy(alpha = theme.bg.alpha * 0.86f * alphaK)
        else -> theme.bg.copy(alpha = theme.bg.alpha * alphaK)
    }
    val effBorder = if (immersive || liquid || blurOn) Color.Transparent else theme.border

    val navInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    // 离屏幕底部 14dp：胶囊是悬浮件，贴太近会像通栏，留太大又显得空
    val bottomPad = if (navInset != 0.dp) 8.dp + navInset else 14.dp

    val itemW = DockMetrics.itemW
    val itemH = DockMetrics.itemH
    val gap = DockMetrics.gap
    val pad = DockMetrics.pad
    val shape = RoundedCornerShape(dockRadius)

    val sinkModifier = Modifier.graphicsLayer {
        translationY = sinkY.toPx()
        alpha = sinkAlpha
    }

    // ── 设计稿 Dock Kit（7 套）：另一套 48dp 栅格的版式，单独实现 ──
    DockKitStyle.of(custom.dockStyle)?.let { kit ->
        DockKitBar(
            style = kit,
            navigationBadge = navigationBadge,
            modifier = modifier,
            onOpenDonate = onOpenDonate,
        )
        return
    }

    // ── CENTER：宽胶囊 + 中间凸起头像（点击进捐赠页，长按换图）──
    if (style == DockStyle.CENTER) {
        CenterDockBar(
            theme = theme,
            navigationBadge = navigationBadge,
            modifier = modifier,
            onOpenDonate = onOpenDonate,
        )
        return
    }

    // ── V5：侧边抽屉（左右停靠由「胶囊停靠」决定）──
    if (style == DockStyle.V5) {
        val onRight = sideDockOnRight
        val sideShape = RoundedCornerShape(dockRadius)
        val sideBase = if (dark) 0.86f else 0.86f * alphaK
        val sideBg = theme.bg.copy(alpha = sideBase.coerceIn(0.2f, 1f))
        Box(
            modifier = modifier
                .then(sinkModifier)
                .padding(start = if (onRight) 0.dp else 14.dp, end = if (onRight) 14.dp else 0.dp)
                .shadow(1.dp, sideShape, spotColor = theme.shadow, ambientColor = theme.shadow)
                .drawBehind {
                    drawDockHalo(
                        radiusPx = dockRadius.toPx(),
                        primary = accent.primary,
                        secondary = accent.secondary,
                        dark = dark,
                    )
                }
                .clip(sideShape)
                .border(1.dp, effBorder, sideShape),
        ) {
            DockMaterial(
                shape = sideShape,
                tint = sideBg,
                liquid = liquid,
                blur = blurOn,
                theme = theme,
                modifier = Modifier.matchParentSize(),
            )
            Column(
                modifier = Modifier.padding(horizontal = pad, vertical = pad),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(gap),
            ) {
                items.forEachIndexed { index, item ->
                    val selected = mainState.selectedPage == index
                    val tint = when {
                        selected -> Color.White
                        rainbow -> rainbowColors[index % 4]
                        dark -> Color(0xFFF2F1FA)
                        else -> theme.unselected
                    }
                    DockItem(
                        style = style,
                        button = button,
                        item = item,
                        index = index,
                        selected = selected,
                        navigationBadge = navigationBadge,
                        rainbow = rainbow,
                        theme = theme,
                        dark = dark,
                        tint = tint,
                        // 侧边抽屉比横向胶囊窄一截：78 宽的项竖排在屏幕边上会占掉一整条
                        itemW = 58.dp,
                        itemH = 54.dp,
                        onClick = { mainState.animateToPage(index) },
                    )
                }
            }
        }
        return
    }

    // ── 横向胶囊：所有样式共用一个外壳（同宽同高）──
    // 底栏这一圈没有滚动容器，横滑在这里能被稳定识别（页面里的列表不会抢）。
    // 但它仍然受「滑动切换 Tab」总开关约束：关档时底栏也不该翻页，
    // 否则设置里写着「关」，手指在胶囊上照样能切——这是之前的一个真 bug。
    val swipeScope = androidx.compose.runtime.rememberCoroutineScope()
    val swipeOn = LocalDockSwipeEnabled.current
    Box(
        modifier = modifier.fillMaxWidth().dockSwipeToPager(mainState.pagerState, swipeOn, swipeScope),
        contentAlignment = Alignment.BottomCenter,
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .then(sinkModifier)
                .padding(bottom = bottomPad)
                .shadow(
                    if (immersive || liquid || blurOn) 0.dp else 1.dp,
                    shape,
                    spotColor = theme.shadow,
                    ambientColor = theme.shadow,
                )
                // 外圈强调色光晕：跟着圆角一起变，画在裁切之前所以不会被剪掉
                .drawBehind {
                    drawDockHalo(
                        radiusPx = dockRadius.toPx(),
                        primary = accent.primary,
                        secondary = accent.secondary,
                        dark = dark,
                    )
                }
                .clip(shape)
                .border(1.dp, effBorder, shape)
                .dockRingFx(
                    accent = accent.primary,
                    accent2 = accent.secondary,
                    trigger = mainState.selectedPage,
                    anchor = ((mainState.selectedPage + 0.5f) /
                        mainState.pagerState.pageCount.coerceAtLeast(1)),
                    capsule = true,
                ),
        ) {
            DockMaterial(
                    shape = shape,
                    tint = effBg,
                    liquid = liquid,
                    blur = blurOn,
                    theme = theme,
                    modifier = Modifier.matchParentSize(),
                )
                // 玻璃顶部高光（液态玻璃自带边缘折射，不再叠静态高光）
                if (!liquid && (theme.glass || style == DockStyle.GLASS || blurOn)) {
                    Box(
                        Modifier
                            .matchParentSize()
                            .clip(shape)
                            .drawBehind {
                                drawRect(
                                    Brush.verticalGradient(
                                        listOf(if (dark) Color(0x1AFFFFFF) else Color(0x73FFFFFF), Color.Transparent),
                                        startY = 0f,
                                        endY = size.height * 0.32f,
                                    )
                                )
                            },
                    )
                }
                // 滑动指示器：胶囊 / 正圆 / 超椭圆 / 切角这四种的选中态就是「一块渐变实底」，
                // 换成整排统一绘制的滑动色块是无损替换。
                // 描边、双层、立体键、浮片、玻璃、光晕各有额外装饰（环、发光、厚度），
                // 跳过它们自己的填充会丢掉这些装饰，所以保持原来的原地淡入。
                val slideable = button in setOf(
                    DockButton.PILL, DockButton.CIRCLE, DockButton.SQUIRCLE, DockButton.CUT,
                )
                val square = button.isSquare
                val plateW = if (square) 54.dp else itemW
                val plateH = if (square) 54.dp else itemH
                if (slideable) {
                    DockSlideIndicator(
                        pagerState = mainState.pagerState,
                        count = items.size,
                        slotW = itemW,
                        slotH = itemH,
                        gap = gap,
                        plateW = plateW,
                        plateH = plateH,
                        shape = button.shape(plateW, plateH),
                        brush = Brush.linearGradient(
                            listOf(accent.primary, accent.mid, accent.secondary),
                        ),
                        highlight = Color.White,
                        // Row 自带 pad 内衬，指示器要跟着让开同样的边
                        modifier = Modifier.padding(start = pad, top = pad),
                    )
                }
                Row(
                    modifier = Modifier.padding(pad),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(gap),
                ) {
                    items.forEachIndexed { index, item ->
                        DockItem(
                            style = style,
                            button = button,
                            item = item,
                            index = index,
                            selected = mainState.selectedPage == index,
                            navigationBadge = navigationBadge,
                            rainbow = rainbow,
                            theme = theme,
                            dark = dark,
                            tint = null,
                            slidePlate = slideable,
                            onClick = { mainState.animateToPage(index) },
                        )
                    }
                }
        }
    }
}

/**
 * 胶囊外圈的光晕。
 *
 * 不是真的高斯模糊：用 9 层同心圆角矩形叠出一条平滑的衰减带（每层外扩 1.5dp，
 * 透明度按 (1-d)² 逐层递减）。好处是：
 * - 圆角参数一变，整圈的半径跟着变，永远贴着胶囊轮廓；
 * - 只依赖 drawRoundRect，任何 GPU / 软件渲染都能画，不像 BlurMaskFilter 在硬件
 *   加速下会被直接忽略；而且只在底栏自身重绘时执行，静止时零开销。
 */
internal fun androidx.compose.ui.graphics.drawscope.DrawScope.drawDockHalo(
    radiusPx: Float,
    primary: Color,
    secondary: Color,
    dark: Boolean,
) {
    val layers = 9
    val step = 1.5.dp.toPx()
    val peak = if (dark) 0.42f else 0.30f
    val brush = Brush.linearGradient(
        colors = listOf(primary.copy(alpha = 1f), secondary.copy(alpha = 1f)),
        start = Offset(0f, 0f),
        end = Offset(size.width, size.height),
    )
    for (k in 0 until layers) {
        val inner = k.toFloat() / layers
        val outer = (k + 1).toFloat() / layers
        // 相邻两层累计透明度之差：叠加后正好是一条 (1-d)² 的衰减曲线
        val a = peak * ((1f - inner) * (1f - inner) - (1f - outer) * (1f - outer))
        val grow = step * (k + 1)
        drawRoundRect(
            brush = brush,
            alpha = a,
            topLeft = Offset(-grow, -grow),
            size = Size(size.width + grow * 2, size.height + grow * 2),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(radiusPx + grow, radiusPx + grow),
        )
    }
}

/**
 * 底栏材质层：液态玻璃 / 实时模糊 / 静态磨砂，只画底，不画内容。
 *
 * - 液态玻璃：直接采样身后页面（Miuix backdrop），做 vibrancy + 模糊 + 圆角矩形折射（lens），
 *   所以底栏里的图标下面是「真的」在透过一块厚玻璃看内容，滚动的卡片会在边缘被折弯；
 * - 实时模糊：同样的采样，只做高斯模糊 + 混色；
 * - 两者都不可用（GPU 不支持 / 没有 backdrop）时退回静态磨砂，保证不会变成全透明。
 */
@Composable
internal fun DockMaterial(
    shape: Shape,
    tint: Color,
    liquid: Boolean,
    blur: Boolean,
    theme: DockTheme,
    modifier: Modifier = Modifier,
) {
    val backdrop = LocalAppBackdrop.current
    val dark = me.diksu.ultra.foxsu.ui.screen.ag.Ag.isDark
    if (liquid && backdrop != null) {
        Box(
            modifier
                .clip(shape)
                .drawBackdrop(
                    backdrop = backdrop,
                    shape = { shape },
                    effects = {
                        vibrancy()
                        blur(8.dp.toPx(), 8.dp.toPx())
                        lens(
                            refractionHeight = 22.dp.toPx(),
                            refractionAmount = 26.dp.toPx(),
                            chromaticAberration = 0.35f,
                        )
                    },
                )
                // 薄薄一层同色玻璃，让内容不是「生糊」，而是隔着玻璃
                .background(tint)
                .border(
                    1.dp,
                    Brush.verticalGradient(
                        listOf(
                            if (dark) Color(0x40FFFFFF) else Color(0x66FFFFFF),
                            if (dark) Color(0x0DFFFFFF) else Color(0x1AFFFFFF),
                        )
                    ),
                    shape,
                ),
        )
        return
    }
    if (blur && backdrop != null) {
        Box(
            modifier
                .clip(shape)
                .textureBlur(
                    backdrop = backdrop,
                    shape = shape,
                    blurRadius = 26f,
                    colors = BlurColors(blendColors = listOf(BlendColorEntry(color = tint))),
                )
                .background(tint.copy(alpha = tint.alpha * 0.5f))
                .border(1.dp, if (dark) Color(0x14FFFFFF) else Color(0x1F1E1946), shape),
        )
        return
    }
    Box(
        modifier
            .clip(shape)
            .background(tint)
            .drawBehind {
                drawRect(
                    brush = Brush.verticalGradient(
                        0f to if (dark) Color(0x08FFFFFF) else Color(0x30FFFFFF),
                        0.55f to Color(0x00FFFFFF),
                        1f to Color(0x00FFFFFF),
                        startY = 0f,
                        endY = size.height,
                    ),
                )
            },
    )
}

/**
 * 单个 Dock 项。
 *
 * 按钮造型由 [button] 决定，尺寸固定 78×60（与默认胶囊一致）：
 * 按下 → 整体缩到 0.9 并轻微下沉（立体键多沉 2dp），松手带过冲弹回。
 */
@Composable
private fun DockItem(
    style: DockStyle,
    button: DockButton,
    item: BottomBarDestination,
    index: Int,
    selected: Boolean,
    navigationBadge: NavigationBadgeState,
    rainbow: Boolean,
    theme: DockTheme,
    dark: Boolean,
    tint: Color?,
    forceShape: Shape? = null,
    itemW: Dp = DockMetrics.itemW,
    itemH: Dp = DockMetrics.itemH,
    /** 指示器已由整排统一绘制（滑动色块），本格不再自己画选中底 */
    slidePlate: Boolean = false,
    onClick: () -> Unit,
) {
    val accent = me.diksu.ultra.foxsu.ui.screen.ag.Ag.accent

    val baseTint = tint ?: if (rainbow && !selected) rainbowColors[index % 4] else theme.unselected
    // 正圆 / 超椭圆必须落在正方形里，否则会被 78×60 的项拉成椭圆
    val square = button.isSquare
    val fillW = if (square) 54.dp else itemW
    val fillH = if (square) 54.dp else itemH
    val shape = forceShape ?: button.shape(fillW, fillH)

    val gradFill = selected && button in setOf(
        DockButton.PILL, DockButton.CIRCLE, DockButton.SQUIRCLE, DockButton.CUT, DockButton.GLOW, DockButton.DOUBLE,
    )
    // 滑动指示器盖在本格时，前景必须反色，否则「强调色圆 + 同色图标」会糊成一片。
    // 用渐变动画而不是直接赋值：色块滑过来的那 400ms 里，图标和文字要跟着一起
    // 从灰过渡到白，颜色瞬切会让「色块到达」这个瞬间显得很硬。
    val fgTint by animateColorAsState(
        targetValue = when {
            slidePlate && selected -> slidePlateInk(dark)
            gradFill -> Color.White
            style == DockStyle.EDITORIAL && selected -> theme.selected
            selected -> theme.selected
            else -> baseTint
        },
        animationSpec = tween(durationMillis = if (slidePlate) 260 else 180),
        label = "dock-fg-$index",
    )

    // 按下反馈
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val pressScale by animateFloatAsState(
        targetValue = if (pressed) 0.96f else 1f,
        // 按下快速压扁，松手低阻尼弹簧超调回弹（Spring Overshoot）
        animationSpec = if (pressed) {
            spring(dampingRatio = 0.72f, stiffness = 1600f)
        } else {
            spring(dampingRatio = 0.35f, stiffness = 760f)
        },
        label = "dock-press-$index",
    )
    // 内阴影已移除：小圆片上会显出一块发黑的脏影，改由图标点击涟漪表达按压
    val pressSink by animateDpAsState(
        targetValue = if (pressed) (if (button == DockButton.KEYCAP) 2.dp else 1.dp) else 0.dp,
        animationSpec = spring(dampingRatio = 0.5f, stiffness = 800f),
        label = "dock-sink-$index",
    )
    val dropScale = rememberDropIconScale(selected)
    val iconScale by animateFloatAsState(
        targetValue = if (selected) 1.08f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "dock-icon-$index",
    )
    val lift by animateDpAsState(
        targetValue = if (style == DockStyle.EDITORIAL && selected) (-2).dp else 0.dp,
        animationSpec = tween(260),
        label = "dock-lift-$index",
    )
    val underline by animateDpAsState(
        targetValue = if (style == DockStyle.EDITORIAL && selected) 18.dp else 0.dp,
        animationSpec = tween(280),
        label = "dock-underline-$index",
    )

    Box(
        modifier = Modifier
            .width(itemW)
            .height(itemH)
            .graphicsLayer {
                scaleX = pressScale
                scaleY = pressScale
            }
            .clickable(
                interactionSource = src,
                indication = null,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        // 滑动的色块由整排统一画，这里只画未选中态（选中的图形已经在滑动层上了）
        if (!(slidePlate && selected)) {
            Box(
                Modifier
                    .size(fillW, fillH)
            ) {
                DockButtonFill(
                    style = button,
                    selected = selected,
                    shape = shape,
                    accent = accent.primary,
                    accentMid = accent.mid,
                    accentSecond = accent.secondary,
                    dark = dark,
                )
            }
        }

        // V11 浮起圆片：选中时按钮下面再垫一枚更小的实底圆
        if (style == DockStyle.V11 && selected && button != DockButton.CIRCLE) {
            Box(
                Modifier
                    .size(50.dp)
                    .clip(CircleShape)
                    .background(Brush.linearGradient(listOf(accent.primary, accent.mid, accent.secondary))),
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .offset(y = lift + pressSink),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            DockIcon(
                item, index, selected, navigationBadge, rainbow,
                tint = fgTint,
                badgeTintOverride = if (fgTint == Color.White) Color.White else null,
                scale = iconScale * dropScale,
                glow = style == DockStyle.V6 && selected,
                big = false,
                pressed = pressed,
                pressAccent = accent.primary,
            )
            if (showLabel(style, selected)) {
                Text(
                    text = stringResource(item.label),
                    fontSize = if (style == DockStyle.GLASS) 7.5.sp else 10.sp,
                    lineHeight = if (style == DockStyle.GLASS) 9.sp else 12.sp,
                    maxLines = 1,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                    color = fgTint,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
            if (style == DockStyle.EDITORIAL) {
                Spacer(Modifier.height(3.dp))
                Box(
                    Modifier
                        .width(underline)
                        .height(2.dp)
                        .clip(CircleShape)
                        .background(if (selected) theme.selected else Color.Transparent),
                )
            }
        }

        // V10 顶指示条
        if (style == DockStyle.V10 && selected) {
            Box(
                Modifier
                    .align(Alignment.TopCenter)
                    .offset(y = (-5).dp)
                    .width(22.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(99.dp))
                    .background(Brush.linearGradient(listOf(accent.primary, accent.secondary))),
            )
        }
        if (style == DockStyle.GLASS && selected) {
            Box(
                Modifier
                    .align(Alignment.TopCenter)
                    .offset(y = (-8).dp)
                    .size(width = 22.dp, height = 3.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(Brush.horizontalGradient(listOf(accent.primary, accent.secondary))),
            )
        }
        if (style == DockStyle.NOCTURNE && selected) {
            Box(
                Modifier
                    .align(Alignment.TopCenter)
                    .offset(y = (-2).dp)
                    .size(width = 24.dp, height = 2.5.dp)
                    .clip(CircleShape)
                    .background(Brush.horizontalGradient(listOf(accent.primary, accent.secondary))),
            )
        }
    }
}

private fun showLabel(style: DockStyle, selected: Boolean): Boolean = when (style) {
    DockStyle.D0, DockStyle.GLASS, DockStyle.NOCTURNE, DockStyle.CENTER -> true
    DockStyle.V1, DockStyle.PAGES -> selected
    else -> false
}

/** Dock 单项图标 + 徽标 */
@Composable
private fun DockIcon(
    item: BottomBarDestination,
    index: Int,
    selected: Boolean,
    navigationBadge: NavigationBadgeState,
    rainbow: Boolean,
    tint: Color,
    badgeTintOverride: Color?,
    scale: Float = 1f,
    glow: Boolean = false,
    big: Boolean = false,
    pressed: Boolean = false,
    pressAccent: Color = Color.Unspecified,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier, contentAlignment = Alignment.TopCenter) {
        val iconSize = if (big) 24.dp else 22.dp
        // 点击特效：涟漪 + 图标回弹（替代发黑的内阴影）
        DockIconTapFx(
            pressed = pressed,
            accent = if (pressAccent == Color.Unspecified) {
                me.diksu.ultra.foxsu.ui.screen.ag.Ag.accent.primary
            } else pressAccent,
            iconSize = iconSize,
        ) {
            Icon(
                imageVector = dockIconOverride(item) ?: item.filledIcon,
                contentDescription = stringResource(item.label),
                tint = tint,
                modifier = Modifier
                    .size(iconSize)
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                        if (selected && !big) translationY = -1.dp.toPx()
                    },
            )
        }
        val badge = navigationBadgeFor(index, navigationBadge, floating = true)
        if (badge != null) {
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = (-8).dp, y = (-2).dp)
            ) {
                Box(
                    Modifier
                        .height(17.dp)
                        .defaultMinSize(minWidth = 17.dp)
                        .clip(RoundedCornerShape(9.dp))
                        .background(me.diksu.ultra.foxsu.ui.screen.ag.Ag.accent.secondary)
                        .border(
                            2.dp,
                            if (me.diksu.ultra.foxsu.ui.screen.ag.Ag.isDark) Color(0xFF1C1B2E) else Color.White,
                            RoundedCornerShape(9.dp),
                        )
                        .padding(horizontal = 4.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        badgeTextFor(index, navigationBadge) ?: "",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color.White,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

/** 各皮肤各自的专属抽屉风格（跟随皮肤自身的明暗与设计语言） */
object AgDockThemes {

    /** FoxSu Redesign：晨曦玻璃，明暗双态（强调色跟随全局配色） */
    @Composable
    fun redesign(): DockTheme {
        val dark = me.diksu.ultra.foxsu.ui.screen.ag.Ag.isDark
        val ac = me.diksu.ultra.foxsu.ui.screen.ag.Ag.accent
        // 选了背景色主题时，胶囊与衬底跟着那一套走：底栏悬在页面底部，
        // 页面换了暖米色它还留一层冷白，会显得像另一块贴上去的板子
        val pal = me.diksu.ultra.foxsu.ui.theme.currentBgPalette(dark)
        return if (dark) DockTheme(
            bg = pal?.surface?.copy(alpha = 0.90f) ?: Color(0xCC1C1B2E),
            border = Color(0x1FFFFFFF), shadow = Color(0x66000000),
            selected = ac.primary,
            unselected = pal?.text2?.copy(alpha = 0.80f) ?: Color(0xFF8C89A6),
            selectedBg = ac.softDark, badge = ac.secondary, glass = false,
            backdrop = pal?.bg ?: Color(0xFF1A192E),
        ) else DockTheme(
            bg = pal?.surface?.copy(alpha = 0.94f) ?: Color(0xF2FFFFFF),
            border = Color(0x66FFFFFF), shadow = Color(0x2432286E),
            selected = ac.primary,
            unselected = pal?.text2?.copy(alpha = 0.80f) ?: Color(0xFF8E8AA3),
            selectedBg = ac.soft, badge = ac.secondary, glass = false,
            backdrop = pal?.bg ?: Color(0xFFF2F4FA),
        )
    }

    /** Pages：极简留白（亮）→ 墨面（暗），单一强调点 */
    @Composable
    fun fresh(): DockTheme {
        val dark = me.diksu.ultra.foxsu.ui.screen.ag.Ag.isDark
        val ac = me.diksu.ultra.foxsu.ui.screen.ag.Ag.accent
        return if (dark) DockTheme(
            bg = Color(0xF219191F), border = Color(0x29FFFFFF), shadow = Color(0x66000000),
            selected = ac.primary, unselected = Color(0xFF8A8F9C),
            selectedBg = Color(0x1FFFFFFF), badge = ac.primary,
            backdrop = Color(0xFF141419),
        ) else DockTheme(
            bg = Color(0xF6FFFFFF), border = Color(0x14111111), shadow = Color(0x1A111111),
            selected = ac.primary, unselected = Color(0xFF9AA0B0),
            selectedBg = ac.soft, badge = ac.primary,
            backdrop = Color(0xFFFAFAF7),
        )
    }

    /** Editorial：纸面质感 + 墨描边 + 强调色 */
    val editorial: DockTheme
        @Composable get() = DockTheme(
            bg = Color(0xFFFFFCF6), border = Color(0x471D1C22), shadow = Color(0x33282418),
            selected = me.diksu.ultra.foxsu.ui.screen.ag.Ag.accent.primary, unselected = Color(0xFF9C968B),
            selectedBg = Color(0x141D1C22), badge = me.diksu.ultra.foxsu.ui.screen.ag.Ag.accent.secondary,
            backdrop = Color(0xFFFFFCF6),
        )

    /** Daylight：白玻璃（亮）→ 炭玻璃（暗） */
    @Composable
    fun glass(): DockTheme {
        val dark = me.diksu.ultra.foxsu.ui.theme.glassTokens().isDark
        val ac = me.diksu.ultra.foxsu.ui.screen.ag.Ag.accent
        return if (dark) DockTheme(
            bg = Color(0xE614141E), border = Color(0x2EFFFFFF), shadow = Color(0x66000000),
            selected = ac.primary, unselected = Color(0xFF7C8294),
            selectedBg = ac.softDark, badge = ac.secondary, glass = true,
            backdrop = Color(0xFF0F0F16),
        ) else DockTheme(
            bg = Color(0xCCFFFFFF), border = Color(0x33FFFFFF), shadow = Color(0x2E32286E),
            selected = ac.primary, unselected = Color(0xFF9AA0B0),
            selectedBg = ac.soft, badge = ac.secondary, glass = true,
            backdrop = Color(0xFFF7F6FB),
        )
    }

    /** Nocturne：OLED 纯黑胶囊 + 极光（恒暗） */
    val nocturne: DockTheme
        @Composable get() = DockTheme(
            bg = Color(0xF2101218), border = Color(0x26FFFFFF), shadow = Color(0x66000000),
            selected = me.diksu.ultra.foxsu.ui.screen.ag.Ag.accent.primary, unselected = Color(0xFF5D6376),
            selectedBg = me.diksu.ultra.foxsu.ui.screen.ag.Ag.accent.softDark,
            badge = me.diksu.ultra.foxsu.ui.screen.ag.Ag.accent.secondary, glass = true,
            backdrop = Color(0xFF0B0A14),
        )
}

/** 取导航角标数值（设计稿 .bdg 显示纯数字） */
private fun badgeTextFor(index: Int, state: NavigationBadgeState): String? {
    val badge = badgeFor(index, state) ?: return null
    return badge.count.toString()
}
