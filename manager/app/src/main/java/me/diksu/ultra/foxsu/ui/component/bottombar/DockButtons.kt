package me.diksu.ultra.foxsu.ui.component.bottombar

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline as GfxOutline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp

/**
 * 胶囊按钮造型 —— 定制 → 按钮形状。
 *
 * 原来的「图标风格」（圆角方 / 正圆 / 超椭圆 / 七彩）只能改 Dock 项的圆角，
 * 四套之间差别小到几乎看不出来。这里换成 10 套**按钮**样式：每套的轮廓、底衬、
 * 描边、厚度各不相同，选中与未选中是同一个造型的两种状态，切换时整体一起换。
 */
enum class DockButton(val label: String, val hint: String) {
    PILL("胶囊", "全圆角药丸，渐变实底"),
    CIRCLE("正圆", "圆形按钮，图标居中"),
    SQUIRCLE("超椭圆", "大圆角方形，iOS 味"),
    OUTLINE("描边框", "透明底 + 渐变描边"),
    DOUBLE("双层", "外环内芯，双击感"),
    CUT("切角", "对角斜切的硬朗造型"),
    KEYCAP("立体键", "按键厚度 + 按下回弹"),
    CARD("浮片", "白卡浮起，投影分离"),
    GLASS("玻璃", "半透明玻璃 + 顶部高光"),
    GLOW("光晕", "渐变底 + 外发光环"),
    ;

    /** 必须是正方形才成立的造型（正圆 / 超椭圆）。 */
    val isSquare: Boolean get() = this == CIRCLE || this == SQUIRCLE

    companion object {
        val all: List<DockButton> = entries
        fun of(index: Int): DockButton = all[index.coerceIn(0, all.lastIndex)]
    }
}

/** 切角形状：左上 / 右下各切一刀（8dp 斜角）。 */
private class ChamferShape(private val cutPx: Float) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): GfxOutline {
        val c = cutPx.coerceAtMost(minOf(size.width, size.height) / 2f)
        val path = Path().apply {
            moveTo(c, 0f)
            lineTo(size.width, 0f)
            lineTo(size.width, size.height - c)
            lineTo(size.width - c, size.height)
            lineTo(0f, size.height)
            lineTo(0f, c)
            close()
        }
        return GfxOutline.Generic(path)
    }
}

/** 按钮外轮廓。宽高参与计算，保证「正圆」在任何项尺寸下都是真正的圆。 */
fun DockButton.shape(width: Dp, height: Dp): Shape = when (this) {
    DockButton.CIRCLE -> CircleShape
    DockButton.SQUIRCLE -> RoundedCornerShape(percent = 34)
    DockButton.CUT -> ChamferShape(minOf(width.value, height.value) * 0.18f)
    DockButton.KEYCAP -> RoundedCornerShape(15.dp)
    DockButton.CARD -> RoundedCornerShape(13.dp)
    else -> RoundedCornerShape(percent = 50)
}

/**
 * 一枚按钮的底衬。放在固定尺寸的容器里，铺满父级。
 *
 * @param selected 选中态：实底 / 描边全部拉满；未选中只留一层极淡的形（能看出造型）。
 */
@Composable
fun DockButtonFill(
    style: DockButton,
    selected: Boolean,
    shape: Shape,
    accent: Color,
    accentMid: Color,
    accentSecond: Color,
    dark: Boolean,
    modifier: Modifier = Modifier,
) {
    val grad = Brush.linearGradient(listOf(accent, accentMid, accentSecond))
    // 未选中与选中之间用颜色过渡，切换 tab 时是「渐亮」而不是硬切
    val softBg by animateColorAsState(
        targetValue = if (selected) Color.Transparent else if (dark) Color(0x14FFFFFF) else Color(0x0D1E1946),
        animationSpec = tween(220),
        label = "dock-btn-soft",
    )
    val cardBg by animateColorAsState(
        targetValue = when {
            selected -> if (dark) Color(0xFF2A2842) else Color.White
            else -> if (dark) Color(0x0FFFFFFF) else Color(0x66FFFFFF)
        },
        animationSpec = tween(220),
        label = "dock-btn-card",
    )
    val glassBg by animateColorAsState(
        targetValue = if (selected) accent.copy(alpha = 0.16f) else Color(0x0DFFFFFF),
        animationSpec = tween(220),
        label = "dock-btn-glass",
    )

    when (style) {
        DockButton.PILL, DockButton.CIRCLE, DockButton.SQUIRCLE, DockButton.CUT ->
            if (selected) {
                Box(
                    modifier
                        .fillMaxSize()
                        .clip(shape)
                        .background(grad)
                        // 顶部一层薄高光：纯渐变会显得很「塑料」
                        .drawBehind {
                            drawRect(
                                Brush.verticalGradient(
                                    listOf(Color(0x33FFFFFF), Color(0x00FFFFFF)),
                                    startY = 0f,
                                    endY = size.height * 0.55f,
                                )
                            )
                        },
                )
            } else {
                Box(modifier.fillMaxSize().clip(shape).background(softBg))
            }

        DockButton.OUTLINE -> Box(
            modifier
                .fillMaxSize()
                .clip(shape)
                .background(if (selected) Color.Transparent else softBg)
                .drawBehind {
                    val w = 1.6.dp.toPx()
                    drawRoundRect(
                        brush = if (selected) grad else Brush.linearGradient(
                            listOf(Color(0x33FFFFFF), Color(0x14FFFFFF))
                        ),
                        cornerRadius = CornerRadius(size.height / 2f),
                        style = Stroke(w),
                        topLeft = Offset(w / 2f, w / 2f),
                        size = Size(size.width - w, size.height - w),
                    )
                },
        )

        DockButton.DOUBLE -> Box(modifier.fillMaxSize().clip(shape).background(if (selected) accent.copy(alpha = 0.13f) else softBg)) {
            if (selected) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .padding(4.5.dp)
                        .clip(RoundedCornerShape(percent = 50))
                        .background(grad)
                )
            }
            Box(
                Modifier
                    .fillMaxSize()
                    .drawBehind {
                        val w = 1.4.dp.toPx()
                        drawRoundRect(
                            color = if (selected) accent.copy(alpha = 0.55f) else Color(0x14FFFFFF),
                            cornerRadius = CornerRadius(size.height / 2f),
                            style = Stroke(w),
                            topLeft = Offset(w / 2f, w / 2f),
                            size = Size(size.width - w, size.height - w),
                        )
                    }
            )
        }

        DockButton.KEYCAP -> {
            // 立体键：底部 3dp「厚度」用同色系压深一档画出来，按下时整体下沉
            val edge by animateColorAsState(
                targetValue = if (selected) accent.copy(alpha = 0.55f) else if (dark) Color(0x1FFFFFFF) else Color(0x141E1946),
                animationSpec = tween(200),
                label = "dock-btn-edge",
            )
            Box(modifier.fillMaxSize().drawBehind {
                val d = 3.dp.toPx()
                drawRoundRect(
                    color = edge,
                    topLeft = Offset(0f, d),
                    size = Size(size.width, size.height - d),
                    cornerRadius = CornerRadius(15.dp.toPx()),
                )
            }) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .padding(bottom = 3.dp)
                        .clip(shape)
                        .background(
                            if (selected) grad
                            else SolidColor(if (dark) Color(0xFF232135) else Color.White)
                        )
                        .border(
                            1.dp,
                            if (selected) Color.Transparent else if (dark) Color(0x1AFFFFFF) else Color(0x0F1E1946),
                            shape,
                        )
                )
            }
        }

        DockButton.CARD -> Box(
            modifier
                .fillMaxSize()
                .clip(shape)
                .background(cardBg)
                .border(
                    1.dp,
                    if (selected) accent.copy(alpha = 0.28f) else if (dark) Color(0x14FFFFFF) else Color(0x0F1E1946),
                    shape,
                )
                .drawBehind {
                    if (selected) {
                        // 顶缘一道 1.5dp 的强调色细线，读起来像「选中卡」
                        drawRect(
                            Brush.horizontalGradient(
                                listOf(Color.Transparent, accent.copy(alpha = 0.9f), Color.Transparent)
                            ),
                            topLeft = Offset(size.width * 0.12f, 0f),
                            size = Size(size.width * 0.76f, 1.6.dp.toPx()),
                        )
                    }
                },
        )

        DockButton.GLASS -> Box(
            modifier
                .fillMaxSize()
                .clip(shape)
                .background(glassBg)
                .border(1.dp, if (selected) accent.copy(alpha = 0.42f) else Color(0x1FFFFFFF), shape)
                .drawBehind {
                    drawRect(
                        Brush.verticalGradient(
                            listOf(Color(0x40FFFFFF), Color(0x00FFFFFF)),
                            startY = 0f,
                            endY = size.height * 0.48f,
                        )
                    )
                },
        )

        DockButton.GLOW -> {
            val glow by animateFloatAsState(
                targetValue = if (selected) 1f else 0f,
                animationSpec = spring(dampingRatio = 0.7f, stiffness = 380f),
                label = "dock-btn-glow",
            )
            Box(
                modifier
                    .fillMaxSize()
                    .drawBehind {
                        if (glow > 0.01f) {
                            // 外发光：两层同色圆角矩形，外层更淡更大
                            val g = 5.dp.toPx() * glow
                            drawRoundRect(
                                color = accent.copy(alpha = 0.14f * glow),
                                topLeft = Offset(-g, -g),
                                size = Size(size.width + g * 2, size.height + g * 2),
                                cornerRadius = CornerRadius(size.height / 2f + g),
                            )
                        }
                    }
            ) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .clip(shape)
                        .background(if (selected) grad else SolidColor(softBg))
                )
            }
        }
    }
}

/**
 * 定制页里的按钮形状小样：按 44×32 的比例画出真实轮廓，
 * 选中态就是真机上的选中样式（渐变实底 / 描边 / 高光）。
 */
@Composable
fun DockButtonSwatch(
    style: DockButton,
    selected: Boolean,
    accent: Color,
    accentMid: Color,
    accentSecond: Color,
    dark: Boolean,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier, contentAlignment = androidx.compose.ui.Alignment.Center) {
        // 正圆必须落在正方形里，否则会被拉成椭圆
        val square = style == DockButton.CIRCLE || style == DockButton.SQUIRCLE
        Box(
            Modifier
                .then(if (square) Modifier.fillMaxHeight().aspectRatio(1f) else Modifier.fillMaxSize()),
        ) {
            DockButtonFill(
                style = style,
                selected = selected,
                shape = style.shape(if (square) 30.dp else 44.dp, if (square) 30.dp else 32.dp),
                accent = accent,
                accentMid = accentMid,
                accentSecond = accentSecond,
                dark = dark,
            )
        }
    }
}
