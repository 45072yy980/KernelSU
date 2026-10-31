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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.diksu.ultra.R
import me.diksu.ultra.foxsu.data.settings.CustomizationStore
import me.diksu.ultra.foxsu.ui.LocalMainPagerState
import me.diksu.ultra.foxsu.ui.component.LocalAppBackdrop
import me.diksu.ultra.foxsu.ui.screen.ag.Ag
import me.diksu.ultra.foxsu.ui.theme.LocalEnableBlur
import me.diksu.ultra.foxsu.ui.util.savePickedImage

/**
 * 「凸起中间」底栏 —— 定制 → 底部导航栏 → 中央头像。
 *
 * 和其余的 DockStyle 完全不同：那些是同一份 78×60 项栅格换造型，这个是另一套版式 ——
 * 一条更宽的白色药丸、选中项是圆角方块底衬而不是圆钮、图标在上文字在下，
 * 正中留出一个圆形卡位。卡位不是装饰：
 * - 点一下 → 打开捐赠页；
 * - 长按 → 换一张图（存本地文件，不是临时 Uri，重启仍在）。
 *
 * 收起读秒、材质（磨砂/模糊/液态玻璃）、外圈光晕、按下回弹都沿用主底栏那一套，
 * 所以它在任何皮肤下的手感都和现有胶囊一致。
 */
@Composable
internal fun CenterDockBar(
    theme: DockTheme,
    navigationBadge: NavigationBadgeState,
    modifier: Modifier = Modifier,
    onOpenDonate: () -> Unit = {},
) {
    val mainState = LocalMainPagerState.current
    val items = BottomBarDestination.entries.toList()
    val custom = CustomizationStore.current
    val dark = Ag.isDark
    val accent = Ag.accent
    val alphaK = custom.bottomBarAlpha.coerceIn(0.2f, 1f)

    // ── 读秒收起：和主底栏同一套语义（触摸 / 切页都会把读秒清零）──
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
        targetValue = if (reveal.hidden) 160.dp else 0.dp,
        animationSpec = if (reveal.hidden) {
            spring(dampingRatio = 0.9f, stiffness = 320f)
        } else {
            spring(dampingRatio = 0.55f, stiffness = 460f)
        },
        label = "center-dock-sink",
    )
    val sinkAlpha by animateFloatAsState(
        targetValue = if (reveal.hidden) 0f else 1f,
        animationSpec = tween(if (reveal.hidden) 260 else 200),
        label = "center-dock-alpha",
    )

    // ── 材质：液态玻璃 > 实时模糊 > 静态磨砂 ──
    val appBackdrop = LocalAppBackdrop.current
    val liquid = custom.bottomBarLiquidGlass && appBackdrop != null
    val blurOn = !liquid && LocalEnableBlur.current && appBackdrop != null
    val effBg = when {
        liquid -> theme.bg.copy(alpha = theme.bg.alpha * 0.18f * alphaK)
        blurOn -> theme.bg.copy(alpha = theme.bg.alpha * 0.55f * alphaK)
        else -> theme.bg.copy(alpha = theme.bg.alpha * alphaK)
    }
    val effBorder = if (liquid || blurOn) Color.Transparent else theme.border

    val navInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val bottomPad = if (navInset != 0.dp) 8.dp + navInset else 14.dp

    // 尺寸不自己写死：直接套主底栏那套栅格（4×78 + 3×4 间隙 + 两侧 8 内边距），
    // 所以不管以后怎么调 DockMetrics，这里永远和默认胶囊同宽同高。
    val barW = DockMetrics.itemW * 4 + DockMetrics.gap * 3 + DockMetrics.pad * 2
    val barH = DockMetrics.itemH + DockMetrics.pad * 2
    // 参考图里头像是「嵌在底栏里」的：直径约为栏高的 92%（167/180），上下各留 3dp，
    // 不顶出胶囊。里面图案再占头像的七成，靠白环撑出呼吸感。
    val avatarD = barH - 6.dp
    val shape = RoundedCornerShape(barH / 2)

    val sinkModifier = Modifier.graphicsLayer {
        translationY = sinkY.toPx()
        alpha = sinkAlpha
    }

    // ── 中央头像：本地文件 → 位图；没有就用随包内置的狐狸标 ──
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

    Box(
        modifier = modifier.fillMaxWidth()
            // 居中头像这个形态以前漏挂了：切到它以后底栏横滑就失灵，
            // 页面又因为列表抢手势滑不动，用户会觉得「整个 App 只能点图标」
            .dockSwipeToPager(mainState.pagerState, LocalDockSwipeEnabled.current, scope),
        contentAlignment = Alignment.BottomCenter,
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .then(sinkModifier)
                .padding(bottom = bottomPad)
                // widthIn 在前：小屏（<340dp）自动退化成填满可用宽度，不溢出
                .widthIn(max = barW)
                .fillMaxWidth()
                .height(barH)
                // 参考图那层很淡的落地阴影：胶囊是浮在页面上的，不是贴死在底边
                .shadow(
                    if (liquid || blurOn) 0.dp else 14.dp,
                    shape,
                    spotColor = if (dark) Color(0x99000000) else Color(0x2E32286E),
                    ambientColor = if (dark) Color(0x66000000) else Color(0x1F32286E),
                )
                .drawBehind {
                    drawDockHalo(
                        radiusPx = (barH / 2).toPx(),
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

            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                items.forEachIndexed { index, item ->
                    // 正中留出卡位：头像画在外层（不被胶囊裁切），这里只占一格宽度
                    if (index == 2) Spacer(Modifier.weight(1f))
                    CenterDockTab(
                        item = item,
                        index = index,
                        selected = mainState.selectedPage == index,
                        navigationBadge = navigationBadge,
                        theme = theme,
                        dark = dark,
                        modifier = Modifier.weight(1f),
                        onClick = { mainState.animateToPage(index) },
                    )
                }
            }
        }

        // 头像单独一层：居中对齐胶囊中线，70 < 76，完整落在底栏内部，不顶出。
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .then(sinkModifier)
                .padding(bottom = bottomPad)
                .height(barH),
            contentAlignment = Alignment.Center,
        ) {
            CenterAvatar(
                avatar = avatar,
                dark = dark,
                modifier = Modifier.size(avatarD),
                onClick = onOpenDonate,
                onLongClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    pickAvatar()
                },
            )
        }
    }
}

/** 单个 tab：图标在上、文字在下，选中是圆角方块底衬（参考图 .tab.sel）。 */
@Composable
private fun CenterDockTab(
    item: BottomBarDestination,
    index: Int,
    selected: Boolean,
    navigationBadge: NavigationBadgeState,
    theme: DockTheme,
    dark: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val accent = Ag.accent
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
        label = "center-dock-press-$index",
    )
    // 内阴影已移除：改由图标点击涟漪表达按压
    val plate by animateColorAsState(
        targetValue = if (selected) (if (dark) accent.softDark else accent.soft) else Color.Transparent,
        animationSpec = tween(220),
        label = "center-dock-plate-$index",
    )
    val fg by animateColorAsState(
        targetValue = if (selected) accent.primary else theme.unselected,
        animationSpec = tween(220),
        label = "center-dock-fg-$index",
    )
    val iconScale by animateFloatAsState(
        targetValue = if (selected) 1.06f else 1f,
        animationSpec = spring(dampingRatio = 0.45f, stiffness = 500f),
        label = "center-dock-icon-$index",
    )

    Column(
        modifier = modifier
            // 底衬左右各留 5dp：整格铺满会让相邻两个 tab 的选中块黏在一起
            .padding(horizontal = 5.dp)
            .height(DockMetrics.itemH)
            .graphicsLayer {
                scaleX = pressScale
                scaleY = pressScale
            }
            .clip(RoundedCornerShape(20.dp))
            .background(plate)
            .clickable(interactionSource = src, indication = null, onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(contentAlignment = Alignment.Center) {
            DockIconTapFx(pressed = pressed, accent = accent.primary, iconSize = 21.dp) {
                Icon(
                    imageVector = dockIconOverride(item) ?: item.filledIcon,
                    contentDescription = stringResource(item.label),
                    tint = fg,
                    modifier = Modifier
                        .size(21.dp)
                        .graphicsLayer {
                            scaleX = iconScale
                            scaleY = iconScale
                        },
                )
            }
            // 角标：贴在图标右上角外侧，洋红实心 + 白色 2dp 描边（和参考图一致）
            val badgeCount = badgeFor(index, navigationBadge)?.count
            if (badgeCount != null) {
                Box(
                    Modifier
                        .align(Alignment.TopEnd)
                        .offset(x = 8.dp, y = (-6).dp)
                        .height(16.dp)
                        .widthIn(min = 16.dp)
                        .clip(CircleShape)
                        .background(accent.secondary)
                        .border(2.dp, if (dark) Color(0xFF1C1B2E) else Color.White, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = if (badgeCount > 99) "99+" else badgeCount.toString(),
                        fontSize = 9.5.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color.White,
                        maxLines = 1,
                        modifier = Modifier.padding(horizontal = 4.dp),
                    )
                }
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(
            text = stringResource(item.label),
            fontSize = 10.5.sp,
            lineHeight = 12.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            color = fg,
        )
    }
}

/**
 * 中央卡位：外圈白环 + 圆图。
 * 没换过图时画随包内置的狐狸标（`foxsu_mark`，透明底），不再用「渐变色 + 心形」凑数。
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CenterAvatar(
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
        targetValue = if (pressed) 0.90f else 1f,
        animationSpec = spring(dampingRatio = 0.4f, stiffness = 800f),
        label = "center-avatar-press",
    )

    Box(
        modifier = modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .shadow(
                6.dp,
                CircleShape,
                spotColor = if (dark) Color(0x99000000) else Color(0x3332286E),
                ambientColor = if (dark) Color(0x66000000) else Color(0x1F32286E),
            )
            .background(if (dark) Color(0xFF2A2842) else Color.White, CircleShape)
            .padding(3.dp)
            .clip(CircleShape)
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
            // 默认狐狸标：原图内容占画布 90.8%，这里缩到 87%，
            // 内容直径约为头像的 72%，四周留约 9dp 白 —— 与参考图一致。
            Image(
                painter = painterResource(R.drawable.fx_foxsu_mark),
                contentDescription = "支持 FoxSu",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        scaleX = 0.87f
                        scaleY = 0.87f
                    },
            )
        }
    }
}
