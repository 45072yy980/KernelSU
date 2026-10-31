package me.diksu.ultra.foxsu.ui.component.bottombar

import me.diksu.ultra.foxsu.ui.component.dockfx.dockRingFx
import android.graphics.BitmapFactory
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import me.diksu.ultra.foxsu.ui.screen.ag.drawInnerShadow
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.diksu.ultra.R
import me.diksu.ultra.foxsu.data.settings.CustomizationStore
import me.diksu.ultra.foxsu.ui.LocalMainPagerState
import me.diksu.ultra.foxsu.ui.screen.ag.Ag
import me.diksu.ultra.foxsu.ui.util.savePickedImage

/**
 * 设计稿 FoxSu Dock Kit v2.0 的 7 套底栏。
 *
 * 和前 14 套「同一份 78×60 栅格换造型」不同，这一组是另一套版式：
 * 48dp 方形热区、2dp 间隙、6dp 内衬，中央留一枚圆形狐狸头像（部分变体没有）。
 * key 与定制页、设计稿类名一一对应，便于对照。
 */
internal enum class DockKitStyle(
    val key: String,
    val title: String,
    /** 是否有中央狐狸卡位（单点聚焦 / 滑轨卡片没有） */
    val hasCenter: Boolean,
    val centerSize: Dp,
) {
    BUBBLE("bubble", "珊瑚气泡", true, 50.dp),
    FILM("film", "胶片孔", true, 46.dp),
    NEON("neon", "霓虹管", true, 44.dp),
    FROST("frost", "玻璃叠层", true, 48.dp),
    FOCUS("focus", "单点聚焦", false, 0.dp),
    STICKER("sticker", "云朵贴纸", true, 52.dp),
    TRACK("track", "滑轨卡片", false, 46.dp),
    ;

    companion object {
        fun of(raw: String): DockKitStyle? = entries.firstOrNull { it.key == raw }
    }
}

/**
 * 设计稿的 24×24 线性图标（house / shield-check / box / gear）。
 *
 * 不用 Material 的实心图标：那套笔画粗、重心低，塞进 48dp 圆钮里会显得发灰；
 * 设计稿这套 1.9 线宽的几何路径在同样的尺寸下更轻、留白更均匀。
 */
private object KitGlyphs {

    private fun outline(name: String, paths: List<String>): ImageVector =
        ImageVector.Builder(
            name = name,
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).apply {
            paths.forEachIndexed { index, d ->
                addPath(
                    pathData = PathParser().parsePathString(d).toNodes(),
                    pathFillType = PathFillType.NonZero,
                    name = "$name-$index",
                    fill = null,
                    stroke = SolidColor(Color.Black),
                    strokeLineWidth = 1.9f,
                    strokeLineCap = StrokeCap.Round,
                    strokeLineJoin = StrokeJoin.Round,
                )
            }
        }.build()

    val Home: ImageVector = outline(
        "KitHome",
        listOf(
            "m3 9 9-7 9 7v11a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2z",
            "M9 22V12h6v10",
        ),
    )

    val SuperUser: ImageVector = outline(
        "KitSu",
        listOf(
            "M20 13c0 5-3.5 7.5-7.66 8.95a1 1 0 0 1-.67-.01C7.5 20.5 4 18 4 13V6a1 1 0 0 1 1-1" +
                "c2 0 4.5-1.2 6.24-2.72a1.17 1.17 0 0 1 1.52 0C14.51 3.81 17 5 19 5a1 1 0 0 1 1 1z",
            "m9 12 2 2 4-4",
        ),
    )

    val Module: ImageVector = outline(
        "KitModule",
        listOf(
            "M21 8a2 2 0 0 0-1-1.73l-7-4a2 2 0 0 0-2 0l-7 4A2 2 0 0 0 3 8v8a2 2 0 0 0 1 1.73l7 4" +
                "a2 2 0 0 0 2 0l7-4A2 2 0 0 0 21 16Z",
            "m3.3 7 8.7 5 8.7-5",
            "M12 22V12",
        ),
    )

    val Settings: ImageVector = outline(
        "KitSettings",
        listOf(
            "M12.22 2h-.44a2 2 0 0 0-2 2v.18a2 2 0 0 1-1 1.73l-.43.25a2 2 0 0 1-2 0l-.15-.08" +
                "a2 2 0 0 0-2.73.73l-.22.38a2 2 0 0 0 .73 2.73l.15.1a2 2 0 0 1 1 1.72v.51" +
                "a2 2 0 0 1-1 1.74l-.15.09a2 2 0 0 0-.73 2.73l.22.38a2 2 0 0 0 2.73.73l.15-.08" +
                "a2 2 0 0 1 2 0l.43.25a2 2 0 0 1 1 1.73V20a2 2 0 0 0 2 2h.44a2 2 0 0 0 2-2v-.18" +
                "a2 2 0 0 1 1-1.73l.43-.25a2 2 0 0 1 2 0l.15.08a2 2 0 0 0 2.73-.73l.22-.39" +
                "a2 2 0 0 0-.73-2.73l-.15-.08a2 2 0 0 1-1-1.74v-.5a2 2 0 0 1 1-1.74l.15-.09" +
                "a2 2 0 0 0 .73-2.73l-.22-.38a2 2 0 0 0-2.73-.73l-.15.08a2 2 0 0 1-2 0l-.43-.25" +
                "a2 2 0 0 1-1-1.73V4a2 2 0 0 0-2-2z",
            "M15 12a3 3 0 1 1-6 0 3 3 0 0 1 6 0",
        ),
    )

    fun of(item: BottomBarDestination): ImageVector = when (item) {
        BottomBarDestination.Home -> Home
        BottomBarDestination.SuperUser -> SuperUser
        BottomBarDestination.Module -> Module
        BottomBarDestination.Setting -> Settings
    }
}

/**
 * FoxSu Dock Kit 底栏（7 套）。
 *
 * 尺寸、圆角、选中态、中央头像的处理全部照设计稿，只有三处按手机使用习惯做了微调：
 * - 触控热区统一 48dp（设计稿 ≤380px 会收到 42，真机上小拇指容易点空）；
 * - 图标 21dp、文字 9.5sp（设计稿是 21px / 9.5px，密度的差异在 1x 屏上几乎看不出）；
 * - 选中态全部带 220ms 的颜色过渡 + 按下缩放，避免状态切换「硬跳」。
 *
 * 中央狐狸的交互沿用「中央头像」那套：单击进捐赠页，长按换图。
 */
@Composable
internal fun DockKitBar(
    style: DockKitStyle,
    navigationBadge: NavigationBadgeState,
    modifier: Modifier = Modifier,
    onOpenDonate: () -> Unit = {},
) {
    val mainState = LocalMainPagerState.current
    val items = BottomBarDestination.entries.toList()
    val custom = CustomizationStore.current
    val dark = Ag.isDark
    val accent = Ag.accent

    // ── 读秒收起：和主底栏同一套语义 ──
    val reveal = LocalDockReveal.current
    val hideSec = custom.dockAutoHide
    val countdown = remember { Animatable(1f) }
    LaunchedEffect(hideSec, reveal.pulse, mainState.selectedPage) {
        countdown.snapTo(1f)
        reveal.hidden = false
        if (hideSec <= 0) return@LaunchedEffect
        countdown.animateTo(0f, tween(hideSec * 1000, easing = LinearEasing))
        reveal.hidden = true
    }
    val sinkY by animateDpAsState(
        targetValue = if (reveal.hidden) 170.dp else 0.dp,
        animationSpec = if (reveal.hidden) {
            spring(dampingRatio = 0.9f, stiffness = 320f)
        } else {
            spring(dampingRatio = 0.55f, stiffness = 460f)
        },
        label = "kit-dock-sink",
    )
    val sinkAlpha by animateFloatAsState(
        targetValue = if (reveal.hidden) 0f else 1f,
        animationSpec = tween(if (reveal.hidden) 260 else 200),
        label = "kit-dock-alpha",
    )
    val sinkModifier = Modifier.graphicsLayer {
        translationY = sinkY.toPx()
        alpha = sinkAlpha
    }

    val navInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val bottomPad = if (navInset != 0.dp) 8.dp + navInset else 14.dp

    // ── 栅格：设计稿 48 / 2 / 6，滑轨卡片单独一套 68×62 ──
    val track = style == DockKitStyle.TRACK
    val focus = style == DockKitStyle.FOCUS
    val itemW = when {
        track -> 68.dp
        focus -> 44.dp
        else -> 48.dp
    }
    val itemH = if (track) 62.dp else 48.dp
    val padH = if (track) 7.dp else 6.dp
    val padV = if (track) 7.dp else 6.dp
    val gap = if (track) 0.dp else 2.dp
    val barH = itemH + padV * 2
    val centerD = if (style.hasCenter) style.centerSize else 0.dp
    // 聚焦变体：选中项展开到 96，其余 44 —— 栏宽按展开态算，切换时不抖
    val focusExpanded = 96.dp
    val barW = if (style.hasCenter) {
        itemW * 4 + centerD + gap * 4 + padH * 2
    } else if (focus) {
        itemW * 3 + focusExpanded + gap * 3 + padH * 2
    } else {
        itemW * 4 + gap * 3 + padH * 2
    }

    val shape = RoundedCornerShape(
        when (style) {
            DockKitStyle.FILM -> 14.dp
            DockKitStyle.NEON -> 18.dp
            DockKitStyle.FROST -> 22.dp
            DockKitStyle.TRACK -> 16.dp
            else -> barH / 2
        }
    )

    // ── 配色：暗色下各变体各自加深，亮色下保持设计稿的轻 ──
    val surf = when (style) {
        DockKitStyle.BUBBLE -> if (dark) Color(0xFF2A2636) else Color.White
        DockKitStyle.FILM -> if (dark) Color(0xFF0E0C15) else Color(0xFFF7F1E8)
        DockKitStyle.NEON -> if (dark) Color(0xFF221D31) else Color(0xF2FFFFFF)
        DockKitStyle.FROST -> if (dark) Color(0x8C3A344A) else Color(0xE6FFFFFF)
        DockKitStyle.FOCUS -> if (dark) Color(0xFF2A2636) else Color.White
        DockKitStyle.STICKER -> if (dark) Color(0xFF2A2636) else Color.White
        DockKitStyle.TRACK -> if (dark) Color(0xFF221F2C) else Color(0xFFF4EFFA)
    }
    val edge = when (style) {
        DockKitStyle.FILM -> if (dark) Color(0x29FFD84D) else Color(0x231D1C22)
        DockKitStyle.NEON -> accent.primary.copy(alpha = if (dark) 0.28f else 0.38f)
        DockKitStyle.FROST -> if (dark) Color(0x1FFFFFFF) else Color(0x66FFFFFF)
        DockKitStyle.TRACK -> Color.Transparent
        else -> if (dark) Color(0x1AFFFFFF) else Color(0x0F1E1946)
    }
    val unselected = when (style) {
        DockKitStyle.FILM -> if (dark) Color(0xFF8C86A0) else Color(0xFF9A93AC)
        DockKitStyle.NEON -> if (dark) Color(0xFF5E5872) else Color(0xFF9AA0B0)
        DockKitStyle.FROST -> if (dark) Color(0xFF7C8294) else Color(0xFF9AA0B0)
        DockKitStyle.TRACK -> if (dark) Color(0xFF6F6884) else Color(0xFFB9B3C6)
        else -> if (dark) Color(0xFF6F6884) else Color(0xFFB9B3C6)
    }

    // ── 中央狐狸：本地图片 → 位图；没有就用随包的圆形头像 ──
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
    val avatarPath = custom.donateAvatar
    val avatar = remember(avatarPath) {
        avatarPath?.let { runCatching { BitmapFactory.decodeFile(it)?.asImageBitmap() }.getOrNull() }
    }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val saved = withContext(Dispatchers.IO) {
                savePickedImage(context, uri, "donate_avatar.jpg", maxSide = 512, quality = 90)
            }
            if (saved == null) {
                Toast.makeText(context, "读取这张图片失败，换一张试试", Toast.LENGTH_SHORT).show()
            } else {
                CustomizationStore.setDonateAvatar(saved)
                Toast.makeText(context, "中间头像已更换 · 长按可再换", Toast.LENGTH_SHORT).show()
            }
        }
    }
    val pickAvatar = {
        picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
    }

    // ── 材质：液态玻璃 / 实时模糊开启时，底交给 DockMaterial 采样身后页面 ──
    val appBackdrop = me.diksu.ultra.foxsu.ui.component.LocalAppBackdrop.current
    val liquid = custom.bottomBarLiquidGlass && appBackdrop != null
    val blurOn = !liquid && me.diksu.ultra.foxsu.ui.theme.LocalEnableBlur.current && appBackdrop != null
    val materialOn = liquid || blurOn

    Box(
        modifier = modifier
            .fillMaxWidth()
            // 在底栏上横滑也能切页：这一圈没有滚动容器，不会和页面列表抢手势
            .dockSwipeToPager(mainState.pagerState, LocalDockSwipeEnabled.current, scope),
        contentAlignment = Alignment.BottomCenter,
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .then(sinkModifier)
                .padding(bottom = bottomPad)
                .widthIn(max = barW)
                .fillMaxWidth()
                .height(barH)
                .shadow(
                    if (style == DockKitStyle.NEON) 16.dp else 12.dp,
                    shape,
                    spotColor = if (dark) Color(0x99000000) else Color(0x2A32286E),
                    ambientColor = if (dark) Color(0x66000000) else Color(0x1832286E),
                )
                .clip(shape)
                .drawBehind {
                    if (!materialOn) {
                        drawRect(surf)
                        if (style == DockKitStyle.FROST) {
                            drawRect(
                                Brush.verticalGradient(
                                    listOf(
                                        if (dark) Color(0x1FFFFFFF) else Color(0xB3FFFFFF),
                                        Color.Transparent,
                                    ),
                                    startY = 0f,
                                    endY = size.height * 0.4f,
                                )
                            )
                        }
                    }
                }
                .border(if (style == DockKitStyle.TRACK) 0.dp else 1.dp, edge, shape)
                .dockRingFx(
                    accent = accent.primary,
                    accent2 = accent.secondary,
                    trigger = mainState.selectedPage,
                    anchor = ((mainState.selectedPage + 0.5f) /
                        mainState.pagerState.pageCount.coerceAtLeast(1)),
                    capsule = true,
                ),
        ) {
            if (materialOn) {
                DockMaterial(
                    shape = shape,
                    tint = surf,
                    liquid = liquid,
                    blur = blurOn,
                    theme = DockTheme(
                        bg = surf, border = edge, shadow = Color.Transparent,
                        selected = accent.primary, unselected = unselected,
                        selectedBg = accent.soft, badge = accent.secondary,
                    ),
                    modifier = Modifier.matchParentSize(),
                )
            }
            // 胶片齿孔要在材质之上才透得出来
            if (style == DockKitStyle.FILM) {
                Box(
                    Modifier
                        .matchParentSize()
                        .drawBehind {
                            drawFilmPerforations(if (dark) Color(0xB34A4560) else Color(0x33504A64))
                        }
                )
            }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = padH, vertical = padV),
            ) {
                // 滑动指示器垫在整排之下：它跨格移动，只能由父级统一持有位置，
                // 不能每格各画一枚（那样中间的空隙里会断开）。
                // 有中央狐狸的变体是「左 2 + 缺口 + 右 2」两段，色块跨不过缺口，
                // 所以只在无缺口（单点聚焦 / 滑轨）时启用。
                // 只有滑轨卡片启用：它的四格等宽且不展开，色块能严丝合缝地停在格子里。
                // 单点聚焦的选中项会从 44 撑到 96，固定宽度的色块跟不上，保持原样。
                if (style == DockKitStyle.TRACK) {
                    val plateShape = when (style) {
                        DockKitStyle.TRACK -> RoundedCornerShape(12.dp)
                        DockKitStyle.STICKER -> RoundedCornerShape(14.dp)
                        else -> CircleShape
                    }
                    DockSlideIndicator(
                        pagerState = mainState.pagerState,
                        count = items.size,
                        slotW = itemW,
                        slotH = itemH,
                        gap = gap,
                        plateW = 62.dp,
                        plateH = 54.dp,
                        shape = plateShape,
                        brush = Brush.verticalGradient(
                            listOf(
                                accent.primary.copy(alpha = if (dark) 0.95f else 0.92f),
                                accent.primary.copy(alpha = if (dark) 0.78f else 0.70f),
                            ),
                        ),
                        highlight = Color.White,
                    )
                }
                Row(
                    modifier = Modifier.fillMaxSize(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(gap),
                ) {
                    items.forEachIndexed { index, item ->
                        if (index == 2 && style.hasCenter) Spacer(Modifier.width(centerD))
                        KitTab(
                            style = style,
                            item = item,
                            index = index,
                            selected = mainState.selectedPage == index,
                            navigationBadge = navigationBadge,
                            dark = dark,
                            unselected = unselected,
                            itemW = itemW,
                            itemH = itemH,
                            focusExpanded = focusExpanded,
                            modifier = if (focus) Modifier else Modifier.weight(1f),
                            onClick = { mainState.animateToPage(index) },
                        )
                    }
                }
            }
        }

        if (style.hasCenter) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .then(sinkModifier)
                    .padding(bottom = bottomPad)
                    .height(barH),
                contentAlignment = Alignment.Center,
            ) {
                KitAvatar(
                    style = style,
                    avatar = avatar,
                    dark = dark,
                    modifier = Modifier.size(centerD),
                    onClick = onOpenDonate,
                    onLongClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        pickAvatar()
                    },
                )
            }
        }
    }
}

/** 胶片孔：上下各一排 6×5 的齿孔，跟着栏宽自动铺，不留残边。 */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawFilmPerforations(color: Color) {
    val holeW = 6.dp.toPx()
    val holeH = 5.dp.toPx()
    val step = 12.dp.toPx()
    val inset = 8.dp.toPx()
    val radius = CornerRadius(1.5.dp.toPx())
    var x = inset
    while (x + holeW <= size.width - inset) {
        drawRoundRect(color, Offset(x, 3.dp.toPx()), Size(holeW, holeH), radius)
        drawRoundRect(color, Offset(x, size.height - 3.dp.toPx() - holeH), Size(holeW, holeH), radius)
        x += step
    }
}

/** 单个导航项：形状 / 底衬 / 文字随 7 套变体分叉。 */
@Composable
private fun KitTab(
    style: DockKitStyle,
    item: BottomBarDestination,
    index: Int,
    selected: Boolean,
    navigationBadge: NavigationBadgeState,
    dark: Boolean,
    unselected: Color,
    itemW: Dp,
    itemH: Dp,
    focusExpanded: Dp,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val accent = Ag.accent
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val pressScale by animateFloatAsState(
        targetValue = if (pressed) 0.96f else 1f,
        // 按下快速压扁（高刚度），松手用低阻尼弹簧超调回弹（Spring Overshoot）
        animationSpec = if (pressed) {
            spring(dampingRatio = 0.72f, stiffness = 1600f)
        } else {
            spring(dampingRatio = 0.35f, stiffness = 760f)
        },
        label = "kit-press-$index",
    )
    // 内阴影已移除：48dp 圆片上那是一块发黑的脏灰，改由图标点击特效表达按压
    // 选中项放大交给「落点回弹」那条线（水滴关时它恒为 1），这里只负责按压
    val waterDrop = CustomizationStore.current.dockWaterDrop
    val dropScale = rememberDropIconScale(selected)
    // 滑轨卡片的选中底由整排的滑动指示器统一绘制
    val hasSlide = style == DockKitStyle.TRACK
    val iconScale by animateFloatAsState(
        targetValue = when {
            pressed -> 0.82f
            waterDrop -> 1f
            selected -> 1.06f
            else -> 1f
        },
        animationSpec = spring(dampingRatio = 0.45f, stiffness = 620f),
        label = "kit-icon-$index",
    )
    val iconScaleFinal = iconScale * dropScale

    // 选中底衬：每套一套「实底 / 描边 / 无底」的处理。
    // 有滑动指示器时这里全部透明——否则会多出一枚原地淡入的小色块，
    // 色块滑走之后原地还留着一个"尾巴"。
    val plate by animateColorAsState(
        targetValue = when {
            !selected -> Color.Transparent
            hasSlide -> Color.Transparent
            style == DockKitStyle.STICKER -> if (dark) Color(0xFF3A2A32) else Color(0xFFFFE7DF)
            style == DockKitStyle.FOCUS -> if (dark) accent.softDark else accent.soft
            style == DockKitStyle.FROST -> if (dark) Color(0xFF3A3448) else Color.White
            style == DockKitStyle.TRACK -> if (dark) Color(0xFF322D40) else Color.White
            else -> Color.Transparent
        },
        animationSpec = tween(220),
        label = "kit-plate-$index",
    )
    // 选中项底衬由整排统一绘制的强调色实底，前景必须反色，
    // 否则「强调色圆 + 同色图标」会糊成一片
    val dropInk = if (hasSlide) slidePlateInk(dark) else null
    val fg by animateColorAsState(
        targetValue = when {
            !selected -> unselected
            dropInk != null -> dropInk
            style == DockKitStyle.FILM -> if (dark) Color(0xFFFFD84D) else accent.primary
            style == DockKitStyle.NEON -> if (dark) accent.secondary else accent.primary
            style == DockKitStyle.BUBBLE -> Color.White
            else -> accent.primary
        },
        animationSpec = tween(200),
        label = "kit-fg-$index",
    )
    val rotation by animateFloatAsState(
        targetValue = if (style == DockKitStyle.STICKER && selected) -8f else 0f,
        animationSpec = spring(dampingRatio = 0.42f, stiffness = 520f),
        label = "kit-rot-$index",
    )
    // 聚焦变体：选中项从 44 展开到 96，横排「图标 + 文字」
    val width by animateDpAsState(
        targetValue = when {
            style == DockKitStyle.FOCUS && selected -> focusExpanded
            else -> itemW
        },
        animationSpec = spring(dampingRatio = 0.78f, stiffness = 620f),
        label = "kit-w-$index",
    )

    val grad = Brush.linearGradient(listOf(accent.primary, accent.mid, accent.secondary))
    val plateShape = when {
        style == DockKitStyle.TRACK -> RoundedCornerShape(12.dp)
        style == DockKitStyle.STICKER -> RoundedCornerShape(14.dp)
        else -> CircleShape
    }
    // 只有「选中后真的浮起来」的三套给投影，其余保持平贴
    val lifted = selected && (
        style == DockKitStyle.BUBBLE || style == DockKitStyle.FROST || style == DockKitStyle.TRACK
        )

    Box(
        modifier = modifier
            .width(width)
            .height(itemH)
            .graphicsLayer {
                scaleX = pressScale
                scaleY = pressScale
            }
            // 投影必须画在裁切之前，否则会被自己的圆角剪掉
            .then(
                if (lifted) {
                    Modifier.shadow(
                        if (style == DockKitStyle.TRACK) 5.dp else 8.dp,
                        plateShape,
                        spotColor = if (style == DockKitStyle.TRACK) {
                            if (dark) Color(0x73000000) else Color(0x1F32286E)
                        } else {
                            accent.primary.copy(alpha = 0.45f)
                        },
                        ambientColor = if (style == DockKitStyle.TRACK) {
                            Color.Transparent
                        } else {
                            accent.primary.copy(alpha = 0.28f)
                        },
                    )
                } else {
                    Modifier
                }
            )
            .clip(plateShape)
            .drawBehind {
                when {
                    // 气泡：选中是珊瑚渐变实心圆 + 顶部一层薄高光
                    style == DockKitStyle.BUBBLE && selected -> {
                        drawRect(grad)
                        drawRect(
                            Brush.verticalGradient(
                                listOf(Color(0x33FFFFFF), Color(0x00FFFFFF)),
                                startY = 0f,
                                endY = size.height * 0.55f,
                            )
                        )
                    }
                    // 霓虹：选中时图标身后一圈同色辉光
                    style == DockKitStyle.NEON && selected -> drawRect(
                        Brush.radialGradient(
                            listOf(accent.secondary.copy(alpha = 0.22f), Color.Transparent),
                            center = center,
                            radius = size.maxDimension * 0.75f,
                        )
                    )
                    else -> drawRect(plate)
                }
            }
            .then(
                if (style == DockKitStyle.FROST && selected) {
                    Modifier.border(1.dp, if (dark) Color(0x1FFFFFFF) else Color(0x0F1E1946), plateShape)
                } else {
                    Modifier
                }
            )
            .rotate(rotation)
            .clickable(interactionSource = src, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        val label: @Composable (Boolean) -> Unit = { compact ->
            Text(
                text = stringResource(item.label),
                fontSize = if (compact) 9.5.sp else 11.sp,
                lineHeight = if (compact) 11.sp else 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                fontWeight = FontWeight.ExtraBold,
                color = fg,
                modifier = Modifier.padding(
                    start = if (compact) 0.dp else 4.dp,
                    top = if (compact) 3.dp else 0.dp,
                ),
            )
        }

        val glyph: @Composable () -> Unit = {
            Box(contentAlignment = Alignment.Center) {
                // 按压/点击特效挂在这里：涟漪从图标中心荡开，图标本身有一次回弹
                DockIconTapFx(
                    pressed = pressed,
                    accent = accent.primary,
                    iconSize = if (style == DockKitStyle.TRACK) 20.dp else 21.dp,
                ) {
                    Icon(
                        imageVector = dockIconOverride(item) ?: KitGlyphs.of(item),
                        contentDescription = stringResource(item.label),
                        tint = fg,
                        modifier = Modifier
                            .size(if (style == DockKitStyle.TRACK) 20.dp else 21.dp)
                            .graphicsLayer {
                                scaleX = iconScaleFinal
                                scaleY = iconScaleFinal
                                // 贴纸变体的图标反向转回来，图案本身始终是正的
                                rotationZ = if (style == DockKitStyle.STICKER) -rotation * 0.85f else 0f
                            },
                    )
                }
                val count = badgeFor(index, navigationBadge)?.count
                if (count != null) {
                    Box(
                        Modifier
                            .align(Alignment.TopEnd)
                            .offset(x = 7.dp, y = (-6).dp)
                            .height(15.dp)
                            .widthIn(min = 15.dp)
                            .clip(CircleShape)
                            .background(accent.secondary)
                            .border(1.6.dp, if (dark) Color(0xFF2A2636) else Color.White, CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = if (count > 99) "99+" else count.toString(),
                            fontSize = 9.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color.White,
                            maxLines = 1,
                            modifier = Modifier.padding(horizontal = 3.dp),
                        )
                    }
                }
            }
        }

        if (style == DockKitStyle.TRACK) {
            // 滑轨：图标在上、文字在下，整格 68×62
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                glyph()
                label(true)
            }
        } else {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
            ) {
                glyph()
                if (style == DockKitStyle.FOCUS && selected) label(false)
            }
        }
    }
}

/**
 * 中央狐狸卡位。样式差异只在「外环」：气泡/贴纸是白圈，玻璃是 2.5dp 白圈 + 珊瑚投影，
 * 霓虹是珊瑚描边 + 半透明底，胶片是黄色光晕，滑轨无环。
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun KitAvatar(
    style: DockKitStyle,
    avatar: ImageBitmap?,
    dark: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val accent = Ag.accent
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.9f else 1f,
        animationSpec = spring(dampingRatio = 0.4f, stiffness = 800f),
        label = "kit-avatar-press",
    )
    val ring = when (style) {
        DockKitStyle.FILM -> if (dark) Color(0xFFFFD84D).copy(alpha = 0.55f) else accent.primary.copy(alpha = 0.35f)
        DockKitStyle.NEON -> accent.primary.copy(alpha = 0.45f)
        DockKitStyle.STICKER, DockKitStyle.FROST -> if (dark) Color(0x24FFFFFF) else Color.White
        DockKitStyle.TRACK -> Color.Transparent
        else -> if (dark) Color(0x1FFFFFFF) else Color.White
    }

    val glow = when (style) {
        DockKitStyle.FILM -> if (dark) Color(0x73FFD84D) else accent.primary.copy(alpha = 0.30f)
        DockKitStyle.NEON -> accent.primary.copy(alpha = 0.6f)
        DockKitStyle.STICKER -> accent.primary.copy(alpha = 0.28f)
        DockKitStyle.TRACK -> Color.Transparent
        else -> accent.primary.copy(alpha = 0.32f)
    }

    Box(
        modifier = modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .shadow(
                if (style == DockKitStyle.TRACK) 0.dp else 7.dp,
                CircleShape,
                spotColor = glow,
                ambientColor = glow.copy(alpha = 0.5f),
            )
            .background(if (dark) Color(0xFF2A2842) else Color.White, CircleShape)
            .padding(if (style == DockKitStyle.NEON) 1.5.dp else 2.5.dp)
            .clip(CircleShape)
            .border(if (style == DockKitStyle.TRACK) 0.dp else 1.dp, ring, CircleShape)
            .combinedClickable(
                interactionSource = src,
                indication = null,
                onClick = onClick,
                onLongClick = onLongClick,
            ),
    ) {
        if (avatar != null) {
            Image(
                bitmap = avatar,
                contentDescription = "支持 FoxSu",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .clip(CircleShape),
            )
        } else {
            // 设计稿的圆形头像本身已经占满画布，直接铺满即可
            Image(
                painter = painterResource(R.drawable.fx_fox_avatar),
                contentDescription = "支持 FoxSu",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .clip(CircleShape),
            )
        }
    }
}
