package me.diksu.ultra.foxsu.ui.component.dockfx

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.unit.dp
import me.diksu.ultra.foxsu.data.settings.CustomizationStore
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * 底栏外圈 20 套：全部画在底栏容器自己的边界上，用 `drawWithContent` 叠一层，
 * 不改布局、不接指针事件，所以不会影响点击、横滑与底栏自身的圆角裁切。
 *
 * [anchor] 是「选中项中心在底栏宽度上的比例」（0..1），供跟随类使用；
 * [trigger] 变化即视为一次切换，交互类从 0 播到 1。
 */
@Composable
fun Modifier.dockRingFx(
    accent: Color,
    accent2: Color,
    trigger: Int,
    anchor: Float,
    capsule: Boolean = true,
): Modifier {
    val custom = CustomizationStore.current
    val fx = if (custom.dockFxOn) {
        DockFx.ringByIndex(custom.dockFxRing)
    } else {
        null
    }
    return this.dockRingFxWith(fx, accent, accent2, trigger, anchor, capsule)
}

/** 显式指定特效的版本，供选择器预览使用（预览要播自己那张卡的效果）。 */
@Composable
internal fun Modifier.dockRingFxWith(
    fx: DockFx?,
    accent: Color,
    accent2: Color,
    trigger: Int,
    anchor: Float,
    capsule: Boolean = true,
): Modifier {
    if (fx == null) return this

    // 只有常驻那 8 套才需要无限时间线；交互类靠 hit 推进，
    // 否则选择器里 20 张外圈卡片会各常驻一个逐帧动画
    // 这里刻意不 by-delegate：委托会在组合阶段读值，导致整条底栏每帧重组。
    // 拿 State 原样传进 drawWithContent，在里面读 .value 只失效绘制。
    val phaseState = if (fx in DockFx.ambientRings) {
        val loop = rememberInfiniteTransition(label = "dock-ring-fx")
        loop.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(7000, easing = LinearEasing),
                repeatMode = RepeatMode.Restart,
            ),
            label = "dock-ring-phase",
        )
    } else {
        null
    }

    val hit = remember { Animatable(1f) }
    LaunchedEffect(trigger, fx) {
        hit.snapTo(0f)
        hit.animateTo(
            1f,
            tween(
                durationMillis = if (fx == DockFx.RING_PING || fx == DockFx.RING_JELLY) 560 else 620,
                easing = FastOutSlowInEasing,
            ),
        )
    }

    return this.drawWithContent {
        drawContent()
        drawRingFx(fx, hit.value, phaseState?.value ?: 0f, accent, accent2, anchor, capsule)
    }
}

private fun DrawScope.drawRingFx(
    fx: DockFx,
    t: Float,
    phase: Float,
    accent: Color,
    accent2: Color,
    anchor: Float,
    capsule: Boolean,
) {
    val w = size.width
    val h = size.height
    if (w <= 0f || h <= 0f) return
    val r = if (capsule) h / 2f else 12.dp.toPx().coerceAtMost(h / 2f)
    val inset = 1.5f.dp.toPx()
    val iw = w - inset * 2
    val ih = h - inset * 2
    val ir = (r - inset).coerceAtLeast(0f)
    val box = RRLayout(iw, ih, ir)
    val topLeft = Offset(inset, inset)
    val boxSize = Size(iw, ih)
    val corner = CornerRadius(ir, ir)
    val pivot = Offset(w / 2f, h / 2f)

    fun ring(color: Color, width: Float, alpha: Float, shake: Float = 0f) {
        val off = if (shake > 0f) {
            Offset((Random.nextFloat() - 0.5f) * shake, (Random.nextFloat() - 0.5f) * shake)
        } else {
            Offset.Zero
        }
        drawRoundRect(
            color = color.copy(alpha = alpha.coerceIn(0f, 1f)),
            topLeft = topLeft + off,
            size = boxSize,
            cornerRadius = corner,
            style = Stroke(width),
        )
    }

    fun pointAt(u: Float): Offset = box.point(u) + topLeft

    when (fx) {
        // ── 常驻类 ──────────────────────────────────────────
        DockFx.RING_PULSE -> {
            val k = (sin(phase * 2f * PI.toFloat()) + 1f) / 2f
            // 三层由粗到细叠出光晕，比单层 stroke 柔和
            ring(accent, 9.dp.toPx(), 0.05f + 0.06f * k)
            ring(accent, 5.dp.toPx(), 0.09f + 0.10f * k)
            ring(accent, 1.6f.dp.toPx(), 0.45f + 0.40f * k)
        }

        DockFx.RING_ORBIT -> {
            val p = pointAt(phase)
            val glow = accent.copy(alpha = 0.35f)
            drawCircle(
                brush = Brush.radialGradient(listOf(glow, Color.Transparent), center = p, radius = 13.dp.toPx()),
                radius = 13.dp.toPx(),
                center = p,
            )
            drawCircle(accent, radius = 2.6f.dp.toPx(), center = p)
            drawCircle(Color.White.copy(alpha = 0.9f), radius = 1.2f.dp.toPx(), center = p)
        }

        DockFx.RING_SCAN -> {
            ring(accent, 1.2f.dp.toPx(), 0.10f)
            val n = 48
            for (i in 0 until n) {
                val u = i / n.toFloat()
                val raw = (u - phase + 1f) % 1f
                val d = if (raw > 0.5f) 1f - raw else raw
                if (d > 0.20f) continue
                val a = 1f - d / 0.20f
                drawCircle(accent.copy(alpha = a * a * 0.9f), radius = 1.9f.dp.toPx(), center = pointAt(u))
            }
        }

        DockFx.RING_RAINBOW -> {
            // 沿周界铺色相，而不是把整条胶囊旋转 45° 再描边：
            // 旋转会让宽高比 8:1 的胶囊转出一个 3 倍大的外接矩形，
            // 边框直接甩到屏幕上（旧版三个特效同时出这个 bug，都是同一个原因）。
            // 96 段足够让色相过渡看不出台阶；每段多画 60% 长度，接缝处自然压住。
            val segments = 96
            val step = 1f / segments
            val stroke = 2.4f.dp.toPx()
            for (i in 0 until segments) {
                val u = i * step
                drawLine(
                    color = rainbowAt(u + phase),
                    start = pointAt(u),
                    end = pointAt(u + step * 1.6f),
                    strokeWidth = stroke,
                    cap = StrokeCap.Round,
                )
            }
        }

        DockFx.RING_DOTLINE -> {
            // 虚线沿周界一格格前进。PathEffect 没有相位参数，而转画布会把矩形转出边界，
            // 所以干脆自己按「周长上的一段距离」画：每段虚线再细分成 4 小段，
            // 走到圆角处会跟着弯，不会被切出一条直线角。
            val dash = 9.dp.toPx()
            val gapLen = 7.dp.toPx()
            val period = dash + gapLen
            val total = box.perimeter
            if (total > period) {
                val count = (total / period).toInt()
                // 每个循环走 6 个周期：7 秒一圈，肉眼刚好能看出在动又不至于晃
                val offset = (phase * period * 6f) % period
                val stroke = 1.8f.dp.toPx()
                for (i in 0 until count) {
                    val base = i * period + offset
                    var prev = pointAt(base / total)
                    for (k in 1..4) {
                        val p = pointAt((base + dash * k / 4f) / total)
                        drawLine(
                            color = accent.copy(alpha = 0.75f),
                            start = prev,
                            end = p,
                            strokeWidth = stroke,
                            cap = StrokeCap.Round,
                        )
                        prev = p
                    }
                }
            }
        }

        DockFx.RING_HALOROT -> {
            // 两段对置的亮弧沿边框绕行，头部亮、尾部化开，像有光源绕着底栏转。
            // 同样不旋转几何：只让「起点」在周长上走，形状永远是这条胶囊。
            val span = 0.17f
            val steps = 16
            val stroke = 3.4f.dp.toPx()
            fun arc(start: Float, color: Color) {
                for (i in 0 until steps) {
                    val k0 = i / steps.toFloat()
                    val k1 = (i + 1) / steps.toFloat()
                    // 二次曲线收尾：尾巴几乎透明，头部接近实色，接缝才不显断
                    val a = 0.85f * k0 * k0
                    if (a < 0.02f) continue
                    drawLine(
                        color = color.copy(alpha = a),
                        start = pointAt(start + span * k0),
                        end = pointAt(start + span * (k1 + 0.012f)),
                        strokeWidth = stroke,
                        cap = StrokeCap.Round,
                    )
                }
            }
            arc(phase, accent)
            arc(phase + 0.5f, accent2)
        }

        DockFx.RING_BREATH -> {
            val k = (sin(phase * 2f * PI.toFloat() * 1.4f) + 1f) / 2f
            ring(accent, (1.6f + 1.4f * k).dp.toPx(), 0.45f + 0.45f * k)
        }

        DockFx.RING_PLASMA -> {
            val flick = 0.55f + 0.45f * abs(sin(phase * 2f * PI.toFloat() * 6f))
            val hot = lerpColor(accent, Color.White, abs(sin(phase * 2f * PI.toFloat() * 4.5f)))
            ring(hot, 2.4f.dp.toPx(), flick, shake = 1.4f)
            ring(accent, 6.dp.toPx(), flick * 0.12f, shake = 1.4f)
        }

        DockFx.RING_FOLLOW -> {
            val cx = anchor * w
            val radius = h * 0.72f
            drawCircle(
                brush = Brush.radialGradient(
                    listOf(accent.copy(alpha = 0.42f), accent.copy(alpha = 0.12f), Color.Transparent),
                    center = Offset(cx, h / 2f),
                    radius = radius,
                ),
                radius = radius,
                center = Offset(cx, h / 2f),
            )
        }

        // ── 交互类 ──────────────────────────────────────────
        DockFx.RING_RIPPLE, DockFx.RING_WAVE -> {
            val waves = if (fx == DockFx.RING_WAVE) 2 else 1
            repeat(waves) { i ->
                val tt = (t - i * 0.18f).coerceIn(0f, 1f)
                if (tt <= 0f || tt >= 1f) return@repeat
                val grow = if (fx == DockFx.RING_WAVE) 0.18f * tt else 0.10f * tt
                withTransform({ scale(1f + grow, 1f + grow * 1.6f, pivot) }) {
                    drawRoundRect(
                        color = accent.copy(alpha = (1f - tt) * (if (i == 0) 0.75f else 0.40f)),
                        topLeft = topLeft,
                        size = boxSize,
                        cornerRadius = corner,
                        style = Stroke((2.4f * (1f - tt)).dp.toPx().coerceAtLeast(0.6f)),
                    )
                }
            }
        }

        DockFx.RING_FLASH -> {
            val k = if (t < 0.2f) t / 0.2f else (1f - t) / 0.8f
            ring(accent, 2.2f.dp.toPx(), k * 0.95f)
            ring(accent, 8.dp.toPx(), k * 0.16f)
        }

        DockFx.RING_SWEEP -> {
            withTransform({ rotate(t * 320f, pivot) }) {
                drawRoundRect(
                    brush = Brush.sweepGradient(
                        0.00f to Color.Transparent,
                        0.10f to accent.copy(alpha = 0.95f * (1f - t)),
                        0.22f to Color.Transparent,
                        1.00f to Color.Transparent,
                        center = pivot,
                    ),
                    topLeft = topLeft,
                    size = boxSize,
                    cornerRadius = corner,
                    style = Stroke(3.dp.toPx()),
                )
            }
        }

        DockFx.RING_PING -> {
            // 40% 处最外，之后收回；外层同时浮一层光
            val k = if (t < 0.4f) t / 0.4f else 1f - (t - 0.4f) / 0.6f
            withTransform({ scale(1f + 0.09f * k, 1f + 0.26f * k, pivot) }) {
                ring(accent, (2f + 1.6f * k).dp.toPx(), 0.25f + 0.75f * k)
            }
            drawRoundRect(
                brush = Brush.radialGradient(
                    listOf(accent.copy(alpha = 0.20f * (1f - t)), Color.Transparent),
                    center = pivot,
                    radius = w * 0.75f,
                ),
                topLeft = topLeft,
                size = boxSize,
                cornerRadius = corner,
            )
        }

        DockFx.RING_BLOOM -> {
            val s = if (t < 0.6f) 0.4f + 0.72f * (t / 0.6f) else 1.12f - 0.12f * ((t - 0.6f) / 0.4f)
            val cx = anchor * w
            withTransform({ scale(s, s, Offset(cx, h / 2f)) }) {
                drawCircle(
                    brush = Brush.radialGradient(
                        listOf(accent.copy(alpha = 0.50f), Color.Transparent),
                        center = Offset(cx, h / 2f),
                        radius = h * 0.85f,
                    ),
                    radius = h * 0.85f,
                    center = Offset(cx, h / 2f),
                )
            }
        }

        DockFx.RING_ORBITDOT -> {
            val u = (t * 1.5f) % 1f
            val p = pointAt(u)
            val a = if (t < 0.12f) t / 0.12f else ((1f - t) / 0.4f).coerceIn(0f, 1f)
            if (a > 0f) {
                drawCircle(accent.copy(alpha = 0.22f * a), radius = 6.dp.toPx(), center = p)
                drawCircle(accent.copy(alpha = a), radius = 2.8f.dp.toPx(), center = p)
            }
        }

        DockFx.RING_GLINT -> {
            val p = pointAt(t * 0.34f)
            val a = (1f - t) * 0.95f
            drawCircle(
                brush = Brush.radialGradient(
                    listOf(Color.White.copy(alpha = a), accent.copy(alpha = a * 0.5f), Color.Transparent),
                    center = p,
                    radius = 10.dp.toPx(),
                ),
                radius = 10.dp.toPx(),
                center = p,
            )
        }

        DockFx.RING_JELLY -> {
            val damp = (1f - t) * (1f - t)
            val sx = 1f + 0.035f * sin(t * 3f * PI.toFloat()) * damp
            val sy = 1f - 0.055f * sin(t * 3f * PI.toFloat()) * damp
            withTransform({ scale(sx, sy, pivot) }) {
                ring(accent, 1.6f.dp.toPx(), 0.55f)
            }
        }

        DockFx.RING_SHADOW -> {
            val lift = sin(PI.toFloat() * t) * 3f.dp.toPx()
            withTransform({ translate(0f, -lift) }) {
                ring(accent, 2.6f.dp.toPx(), sin(PI.toFloat() * t) * 0.30f)
            }
            drawRoundRect(
                color = Color.Black.copy(alpha = 0.10f * sin(PI.toFloat() * t)),
                topLeft = topLeft + Offset(0f, lift * 0.8f),
                size = boxSize,
                cornerRadius = corner,
            )
        }

        DockFx.RING_SPARK -> {
            // 顶部边缘冒出几粒火星，向上飘
            repeat(7) { i ->
                val tt = (t - i * 0.05f).coerceIn(0f, 1f)
                if (tt <= 0f || t >= 1f) return@repeat
                val sx = w * (0.15f + 0.7f * ((i * 37) % 100) / 100f)
                val rise = 14.dp.toPx() * tt
                drawCircle(
                    color = accent.copy(alpha = (1f - tt) * 0.85f),
                    radius = (1.2f + 0.8f * (1f - tt)).dp.toPx(),
                    center = Offset(sx + sin(tt * 9f + i) * 3.dp.toPx(), -rise),
                )
            }
        }

        else -> Unit
    }
}

/** 圆角矩形周长参数化：t ∈ [0,1) 顺时针取点，用于让光点沿边框跑。 */
private class RRLayout(private val w: Float, private val h: Float, private val r: Float) {
    private val sh = (w - 2f * r).coerceAtLeast(0f)
    private val sv = (h - 2f * r).coerceAtLeast(0f)
    private val arc = (PI.toFloat() / 2f) * r
    val perimeter = 2f * sh + 2f * sv + 4f * arc

    fun point(t: Float): Offset {
        if (perimeter <= 0f) return Offset(w / 2f, h / 2f)
        var d = ((t % 1f + 1f) % 1f) * perimeter
        if (d <= sh) return Offset(r + d, 0f)
        d -= sh
        if (d <= arc) return arcPoint(w - r, r, -90f, d)
        d -= arc
        if (d <= sv) return Offset(w, r + d)
        d -= sv
        if (d <= arc) return arcPoint(w - r, h - r, 0f, d)
        d -= arc
        if (d <= sh) return Offset(w - r - d, h)
        d -= sh
        if (d <= arc) return arcPoint(r, h - r, 90f, d)
        d -= arc
        if (d <= sv) return Offset(0f, h - r - d)
        d -= sv
        return arcPoint(r, r, 180f, d)
    }

    private fun arcPoint(cx: Float, cy: Float, startDeg: Float, d: Float): Offset {
        val rr = r.coerceAtLeast(0.0001f)
        val deg = startDeg + d / rr * 180f / PI.toFloat()
        val a = Math.toRadians(deg.toDouble()).toFloat()
        return Offset(cx + rr * cos(a), cy + rr * sin(a))
    }
}


/** 七色环上取一个颜色：u 任意实数，内部取小数部分，传 phase 即整体推色。 */
private fun rainbowAt(u: Float): Color {
    val stops = RAINBOW_STOPS
    val f = ((u % 1f) + 1f) % 1f * (stops.size - 1)
    val i = f.toInt().coerceIn(0, stops.size - 2)
    return lerpColor(stops[i], stops[i + 1], f - i)
}

private val RAINBOW_STOPS = listOf(
    Color(0xFFFF7A3C), Color(0xFFFFD84D), Color(0xFF7FD896),
    Color(0xFF7FC0F2), Color(0xFFA88BFF), Color(0xFFF76FB0), Color(0xFFFF7A3C),
)

private fun lerpColor(a: Color, b: Color, k: Float): Color {
    val t = k.coerceIn(0f, 1f)
    return Color(
        red = a.red + (b.red - a.red) * t,
        green = a.green + (b.green - a.green) * t,
        blue = a.blue + (b.blue - a.blue) * t,
        alpha = a.alpha + (b.alpha - a.alpha) * t,
    )
}
