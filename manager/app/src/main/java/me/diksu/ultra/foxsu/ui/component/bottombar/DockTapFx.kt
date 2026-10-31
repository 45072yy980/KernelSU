package me.diksu.ultra.foxsu.ui.component.bottombar

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 底栏图标的点击特效入口。
 *
 * 实现在 `component/dockfx/DockFxInner.kt`：那里有栏内 20 套共用的时间线，
 * 按设置里选中的那套分派。这里只做一层转发，四套底栏的调用点因此不用改。
 */
@Composable
fun DockIconTapFx(
    pressed: Boolean,
    accent: Color,
    modifier: Modifier = Modifier,
    iconSize: Dp = 21.dp,
    content: @Composable BoxScope.() -> Unit,
) {
    me.diksu.ultra.foxsu.ui.component.dockfx.DockIconTapFx(
        pressed = pressed,
        accent = accent,
        modifier = modifier,
        iconSize = iconSize,
        content = content,
    )
}

/** 中心光点：点击时闪一下，颜色取选中色。 */
internal fun androidx.compose.ui.graphics.drawscope.DrawScope.drawTapSpark(
    accent: Color,
    progress: Float,
    at: Offset = center,
) {
    if (progress <= 0f || progress >= 1f) return
    val grow = 1f - kotlin.math.abs(progress * 2f - 1f)
    drawCircle(
        color = accent.copy(alpha = 0.28f * grow),
        radius = 7.dp.toPx() * (0.6f + progress * 0.9f),
        center = at,
    )
}
