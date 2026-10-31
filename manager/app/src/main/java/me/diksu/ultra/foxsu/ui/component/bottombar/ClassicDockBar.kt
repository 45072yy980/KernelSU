package me.diksu.ultra.foxsu.ui.component.bottombar

import me.diksu.ultra.foxsu.ui.component.dockfx.dockRingFx
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import me.diksu.ultra.foxsu.data.settings.CustomizationStore
import me.diksu.ultra.foxsu.ui.LocalMainPagerState

/**
 * 传统底栏（定制 → 底部导航栏 → 「传统底栏」开关）。
 *
 * 设计：通栏、贴底、无圆角、无浮起阴影——就是 Android 最经典的那条导航栏。
 * - 顶部 1px 发丝线把内容和栏分开；
 * - 四个 tab 严格等分，图标 22dp + 文字 10sp，选中项图标上浮 1dp、文字加粗；
 * - 选中态用「顶部 2.5dp 渐变短条 + 图标下的同色药丸衬底」两层表达，不抢内容区的视觉；
 * - 底色取当前皮肤的胶囊底色（[DockTheme.bg]），所以换肤后仍然是同一套语言。
 *
 * 与胶囊互斥：开关关着 = 胶囊，开着 = 这条传统底栏（默认关）。
 */
@Composable
fun ClassicDockBar(
    theme: DockTheme,
    navigationBadge: NavigationBadgeState,
    modifier: Modifier = Modifier,
) {
    val style = currentClassicStyle()
    if (style != ClassicDockStyle.CLASSIC) {
        ClassicStructureBar(style, theme, navigationBadge, modifier)
        return
    }
    ClassicFlatBar(theme, navigationBadge, modifier)
}

@Composable
private fun ClassicFlatBar(
    theme: DockTheme,
    navigationBadge: NavigationBadgeState,
    modifier: Modifier = Modifier,
) {
    val mainState = LocalMainPagerState.current
    val items = BottomBarDestination.entries.toList()
    val custom = CustomizationStore.current
    val dark = me.diksu.ultra.foxsu.ui.screen.ag.Ag.isDark
    val accent = me.diksu.ultra.foxsu.ui.screen.ag.Ag.accent
    val alphaK = custom.bottomBarAlpha.coerceIn(0.2f, 1f)
    val rainbow = custom.navRainbow

    val navInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val barBg = theme.bg.copy(alpha = (if (dark) 1f else 0.97f) * alphaK)
    val hairline = if (dark) Color(0x1AFFFFFF) else Color(0x121E1946)

    // 通栏传统底栏同样支持横滑切页：这一条也是纯按钮区，没有滚动容器
    val swipeScope = androidx.compose.runtime.rememberCoroutineScope()
    val swipeOn = LocalDockSwipeEnabled.current
    Column(
        modifier
            .fillMaxWidth()
            .dockSwipeToPager(mainState.pagerState, swipeOn, swipeScope)
            .background(barBg)
            // 顶部 1px 发丝线（画在底色之上、内容之下）
            .drawBehind {
                drawRect(
                    color = hairline,
                    size = androidx.compose.ui.geometry.Size(size.width, 1.dp.toPx()),
                )
            }
            .padding(bottom = navInset)
            // 外圈特效：通栏底栏是直角，capsule=false 让描边落在真正的拐角上
            .dockRingFx(
                accent = accent.primary,
                accent2 = accent.secondary,
                trigger = mainState.selectedPage,
                anchor = ((mainState.selectedPage + 0.5f) /
                    mainState.pagerState.pageCount.coerceAtLeast(1)),
                capsule = false,
            ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(62.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            items.forEachIndexed { index, item ->
                ClassicDockItem(
                    item = item,
                    index = index,
                    selected = mainState.selectedPage == index,
                    navigationBadge = navigationBadge,
                    rainbow = rainbow,
                    accentTint = accent.primary,
                    accentSecond = accent.secondary,
                    unselected = theme.unselected,
                    dark = dark,
                    onClick = { mainState.animateToPage(index) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun ClassicDockItem(
    item: BottomBarDestination,
    index: Int,
    selected: Boolean,
    navigationBadge: NavigationBadgeState,
    rainbow: Boolean,
    accentTint: Color,
    accentSecond: Color,
    unselected: Color,
    dark: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.92f else 1f,
        animationSpec = spring(dampingRatio = 0.4f, stiffness = 900f),
        label = "classic-press-$index",
    )
    val tint by animateColorAsState(
        targetValue = when {
            selected -> accentTint
            rainbow -> rainbowColorsForClassic(index)
            else -> unselected
        },
        animationSpec = tween(220),
        label = "classic-tint-$index",
    )
    val lift by animateDpAsState(
        targetValue = if (selected) (-1).dp else 0.dp,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "classic-lift-$index",
    )
    val pillAlpha by animateFloatAsState(
        targetValue = if (selected) 1f else 0f,
        animationSpec = tween(200),
        label = "classic-pill-$index",
    )
    val indicatorWidth by animateDpAsState(
        targetValue = if (selected) 18.dp else 0.dp,
        animationSpec = spring(dampingRatio = 0.7f, stiffness = 420f),
        label = "classic-ind-$index",
    )

    Box(
        modifier = modifier
            .height(62.dp)
            .clickable(interactionSource = src, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        // 顶部选中指示条
        Box(
            Modifier
                .align(Alignment.TopCenter)
                .width(indicatorWidth)
                .height(2.5.dp)
                .clip(RoundedCornerShape(bottomStart = 3.dp, bottomEnd = 3.dp))
                .background(Brush.horizontalGradient(listOf(accentTint, accentSecond))),
        )
        Column(
            modifier = Modifier.graphicsLayer {
                scaleX = scale
                scaleY = scale
            },
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Box(contentAlignment = Alignment.Center) {
                // 选中：图标下一枚淡淡的同色药丸
                Box(
                    Modifier
                        .size(width = 40.dp, height = 26.dp)
                        .graphicsLayer { alpha = pillAlpha }
                        .clip(RoundedCornerShape(percent = 50))
                        .background(accentTint.copy(alpha = if (dark) 0.20f else 0.11f)),
                )
                // 栏内特效：通栏底栏此前漏挂了，切过来就没有点击动效
                DockIconTapFx(pressed = pressed, accent = accentTint, iconSize = 22.dp) {
                    Icon(
                        imageVector = dockIconOverride(item) ?: item.filledIcon,
                        contentDescription = stringResource(item.label),
                        tint = tint,
                        modifier = Modifier
                            .size(22.dp)
                            .graphicsLayer { translationY = lift.toPx() },
                    )
                }
                // 角标：小圆点 + 数字
                val badge = badgeFor(index, navigationBadge)
                if (badge != null) {
                    Box(
                        Modifier
                            .align(Alignment.TopEnd)
                            .padding(start = 26.dp, bottom = 16.dp)
                            .size(15.dp)
                            .clip(CircleShape)
                            .background(accentSecond),
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
            }
            Spacer(Modifier.height(3.dp))
            Text(
                text = stringResource(item.label),
                fontSize = 10.sp,
                fontWeight = if (selected) FontWeight.ExtraBold else FontWeight.Medium,
                color = tint,
                maxLines = 1,
            )
        }
    }
}

private fun rainbowColorsForClassic(index: Int): Color = when (index % 4) {
    0 -> Color(0xFF6C5CE7)
    1 -> Color(0xFF14B8A6)
    2 -> Color(0xFFF59E0B)
    else -> Color(0xFFEC4899)
}
