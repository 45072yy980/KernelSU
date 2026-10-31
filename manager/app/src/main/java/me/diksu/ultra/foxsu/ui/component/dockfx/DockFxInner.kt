package me.diksu.ultra.foxsu.ui.component.dockfx

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import me.diksu.ultra.foxsu.data.settings.CustomizationStore
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * 底栏图标的点击特效。栏内 20 套共用这一个入口，按设置里选中的那套分派。
 *
 * 为什么不再拆 20 个函数：这些效果的差别只在「怎么算变换」和「额外画什么」，
 * 驱动时间线完全一致（一次 press → 一个 0..1 的进度）。放在一个 `when` 里，
 * 增删一套只改两处，也不会让四套底栏各接一条不同的调用链。
 *
 * 进度语义：
 * - [pressed] 由 true 变 false 视为一次「点击」，此时把进度从 0 播到 1；
 * - 需要跟随手指的（[DockFx.INNER_MAGNET]）直接读 [pressed]；
 * - 常驻循环的（[DockFx.INNER_GRADFLOW]）另走 `rememberInfiniteTransition`，
 *   和点击无关，选中就一直在流。
 */
@Composable
fun DockIconTapFx(
    pressed: Boolean,
    accent: Color,
    modifier: Modifier = Modifier,
    iconSize: Dp = 21.dp,
    content: @Composable BoxScope.() -> Unit,
) {
    val custom = CustomizationStore.current
    val fx = if (custom.dockFxOn) {
        DockFx.innerByIndex(custom.dockFxInner)
    } else {
        null
    }
    DockInnerFxCore(fx, pressed, accent, modifier, iconSize, content)
}

/**
 * 显式指定特效的实现体。
 *
 * 选择器里的每张卡片都要能播自己那套（而不是当前选中的那套），
 * 所以把「读设置」和「播特效」拆开，预览直接调这里。
 */
@Composable
internal fun DockInnerFxCore(
    fx: DockFx?,
    pressed: Boolean,
    accent: Color,
    modifier: Modifier = Modifier,
    iconSize: Dp = 21.dp,
    content: @Composable BoxScope.() -> Unit,
) {
    val progress = remember { Animatable(1f) }
    val press = remember { Animatable(0f) }

    LaunchedEffect(pressed, fx) {
        if (pressed) {
            progress.snapTo(0f)
            if (fx == DockFx.INNER_MAGNET) {
                press.animateTo(1f, spring(dampingRatio = 0.55f, stiffness = 1600f))
            }
        } else {
            if (fx == DockFx.INNER_MAGNET) {
                press.animateTo(0f, spring(dampingRatio = Spring.DampingRatioHighBouncy, stiffness = 520f))
            }
            progress.animateTo(1f, tween(durationFor(fx), easing = easingFor(fx)))
        }
    }

    // 渐变流转是常驻的：用它自己的时间线，不跟点击走。
    // 只在真的选中那一套时才建这个无限动画——否则选择器里 20 张卡片
    // 会各自常驻一个逐帧推进的 transition，一屏 20 份重组，低端机直接掉帧。
    val flowXState = if (fx == DockFx.INNER_GRADFLOW) {
        val flow = rememberInfiniteTransition(label = "dock-fx-gradflow")
        flow.animateFloat(
            initialValue = -0.4f,
            targetValue = 1.4f,
            animationSpec = infiniteRepeatable(
                animation = tween(2600, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "dock-fx-gradflow-x",
        )
    } else {
        null
    }

    Box(
        modifier = modifier.drawWithContent {
            drawContent()
            // 光效与粒子画在变换之外：它们从图标中心向四周散开，
            // 跟着图标一起缩放会显得「被捏住」，反而不自然
            if (fx != null) {
                drawInnerOverlay(fx, progress.value, accent, flowXState?.value ?: 0f)
            }
        },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(iconSize)
                .graphicsLayer {
                    // 变换放 graphicsLayer：能拿到真正的节点矩阵，
                    // 负 scale 也能正确镜像（绘制作用域做不到）
                    val m = transformFor(fx, progress.value, press.value)
                    scaleX = m.scaleX
                    scaleY = m.scaleY
                    rotationZ = m.rotation
                    translationX = m.tx
                    translationY = m.ty
                },
            contentAlignment = Alignment.Center,
            content = content,
        )
    }
}

/** 一帧变换的结果，全部是相对图标自身的倍数/角度/位移（单位 px）。 */
private data class FxTransform(
    val scaleX: Float = 1f,
    val scaleY: Float = 1f,
    val rotation: Float = 0f,
    val rotationY: Float = 0f,
    val tx: Float = 0f,
    val ty: Float = 0f,
)

private fun transformFor(fx: DockFx?, t: Float, pressed: Float): FxTransform {
    if (fx == null) {
        // 没有选中任何一套时保持原来的「压一下」手感：按下缩到 0.86
        val s = 1f - pressed * 0.14f
        return FxTransform(scaleX = s, scaleY = s)
    }
    val inv = 1f - t
    return when (fx) {
        DockFx.INNER_SLIDE -> FxTransform(
            scaleX = 1f + 0.18f * sin(PI.toFloat() * t),
            scaleY = 1f - 0.12f * sin(PI.toFloat() * t),
            ty = -5f * inv,
        )

        DockFx.INNER_BOUNCE -> {
            // 前半程弹起，后半程落下；落地那 15% 压扁
            val hop = if (t < 0.72f) sin(t / 0.72f * PI.toFloat()) else 0f
            val squash = if (t > 0.72f) sin((t - 0.72f) / 0.28f * PI.toFloat()) else 0f
            FxTransform(
                scaleX = 1f + 0.12f * squash,
                scaleY = 1f - 0.14f * squash,
                ty = -9f * hop,
            )
        }

        DockFx.INNER_JELLY -> {
            val damp = (1f - t) * (1f - t)
            val w = sin(t * 3f * PI.toFloat())
            FxTransform(
                scaleX = 1f + 0.17f * w * damp,
                scaleY = 1f - 0.15f * w * damp,
            )
        }

        DockFx.INNER_SPIN -> FxTransform(
            rotation = 360f * t,
            scaleX = easeOutBack(t, 1.4f),
            scaleY = easeOutBack(t, 1.4f),
        )

        DockFx.INNER_BEAT -> {
            val a = bump(t, 0.06f, 0.30f) * 0.18f
            val b = bump(t, 0.42f, 0.70f) * 0.12f
            FxTransform(scaleX = 1f + a + b, scaleY = 1f + a + b)
        }

        DockFx.INNER_WAVE -> {
            val w = sin(t * 2f * PI.toFloat())
            FxTransform(ty = -3.5f * w, rotation = 2.5f * w)
        }

        DockFx.INNER_SHINE -> FxTransform()
        DockFx.INNER_RING -> FxTransform()
        DockFx.INNER_BUBBLES -> FxTransform()
        DockFx.INNER_STARS -> FxTransform(
            scaleX = 1f + 0.08f * sin(PI.toFloat() * t),
            scaleY = 1f + 0.08f * sin(PI.toFloat() * t),
        )

        DockFx.INNER_POP -> {
            val s = when {
                t < 0.42f -> 0.6f + (1.15f - 0.6f) * easeOutCubic(t / 0.42f)
                else -> 1.15f - 0.15f * easeOutCubic((t - 0.42f) / 0.58f)
            }
            FxTransform(scaleX = s, scaleY = s)
        }

        DockFx.INNER_WIGGLE -> FxTransform(rotation = 9f * sin(t * 4f * PI.toFloat()) * (1f - t))

        DockFx.INNER_FLIP -> {
            // 用 scaleX 走 1 → -1 → 1：负数会镜像，配合中段压到 0，
            // 就是一次绕 Y 轴翻面的观感（Compose 的绘制作用域没有真正的 3D 旋转）
            val r = PI.toFloat() * t
            FxTransform(scaleX = cos(r), scaleY = 1f - 0.06f * sin(r))
        }

        DockFx.INNER_GRADFLOW -> FxTransform()

        DockFx.INNER_LABELUP -> {
            val up = if (t < 0.45f) sin(t / 0.45f * PI.toFloat() / 2f) else 1f - (t - 0.45f) / 0.55f
            FxTransform(ty = -5f * up, scaleX = 1f + 0.04f * up, scaleY = 1f + 0.04f * up)
        }

        DockFx.INNER_MAGNET -> FxTransform(
            scaleX = 1f + 0.16f * pressed,
            scaleY = 1f - 0.10f * pressed,
            ty = 1.5f * pressed,
        )

        DockFx.INNER_DOTTRAIL -> FxTransform()
        DockFx.INNER_RIPPLE -> FxTransform()
        DockFx.INNER_ELASTIC -> {
            val s = easeOutBack(t, 2.2f)
            FxTransform(scaleX = s, scaleY = s)
        }

        DockFx.INNER_FIREWORK -> FxTransform(
            scaleX = 1f + 0.10f * sin(PI.toFloat() * t.coerceIn(0f, 1f)),
            scaleY = 1f + 0.10f * sin(PI.toFloat() * t.coerceIn(0f, 1f)),
        )

        else -> FxTransform()
    }
}

/** 作用域内的绘制：光环、扫光、粒子都在这一步补上。 */
private fun DrawScope.drawInnerOverlay(fx: DockFx, t: Float, accent: Color, flowX: Float) {
    if (t >= 1f && fx != DockFx.INNER_GRADFLOW) return
    val base = size.minDimension / 2f
    val c = center

    when (fx) {
        DockFx.INNER_SHINE -> {
            // 一条 45° 的白色高光，从左下扫到右上
            val x = -size.width * 0.6f + t * size.width * 2.2f
            val band = Size(size.width * 0.42f, size.height * 1.6f)
            rotate(20f, pivot = c) {
                drawRect(
                    brush = Brush.horizontalGradient(
                        listOf(Color.Transparent, Color.White.copy(alpha = 0.55f * (1f - t)), Color.Transparent),
                        startX = x,
                        endX = x + band.width,
                    ),
                    topLeft = Offset(x, c.y - band.height / 2f),
                    size = band,
                )
            }
        }

        DockFx.INNER_RING -> {
            val r = base * (0.9f + t * 1.05f)
            val a = (1f - t) * 0.55f
            drawCircle(
                color = accent.copy(alpha = a),
                radius = r,
                center = c,
                style = Stroke(width = (2.2f * (1f - t)).dp.toPx().coerceAtLeast(0.6f)),
            )
            drawCircle(
                brush = Brush.radialGradient(
                    listOf(accent.copy(alpha = a * 0.55f), Color.Transparent),
                    center = c,
                    radius = r,
                ),
                radius = r,
                center = c,
            )
        }

        DockFx.INNER_RIPPLE -> {
            repeat(2) { i ->
                val tt = (t - i * 0.16f).coerceIn(0f, 1f)
                if (tt <= 0f || tt >= 1f) return@repeat
                drawCircle(
                    color = accent.copy(alpha = (1f - tt) * (if (i == 0) 0.5f else 0.3f)),
                    radius = base * (0.8f + tt * 1.25f),
                    center = c,
                    style = Stroke(width = (2f * (1f - tt)).dp.toPx().coerceAtLeast(0.5f)),
                )
            }
        }

        DockFx.INNER_BUBBLES -> {
            repeat(6) { i ->
                val seed = i * 1.37f
                val tt = (t * 1.15f - i * 0.07f).coerceIn(0f, 1f)
                if (tt <= 0f) return@repeat
                val rise = base * 2.6f * tt
                val sway = sin(seed + tt * 5.5f) * base * 0.35f
                val r = base * (0.14f + 0.06f * ((i * 37) % 5) / 5f)
                drawCircle(
                    color = accent.copy(alpha = (1f - tt) * 0.6f),
                    radius = r,
                    center = Offset(c.x + sway, c.y - rise + base * 0.6f),
                    style = Stroke(width = 1.2f.dp.toPx()),
                )
            }
        }

        DockFx.INNER_STARS -> {
            repeat(12) { i ->
                val ang = i * (2f * PI.toFloat() / 12f) + 0.3f
                val dist = base * (0.5f + 1.5f * easeOutCubic(t))
                val a = (1f - t) * 0.85f
                val x = c.x + cos(ang) * dist
                val y = c.y + sin(ang) * dist
                val len = base * 0.34f * (1f - t * 0.5f)
                drawLine(
                    color = accent.copy(alpha = a),
                    start = Offset(x, y),
                    end = Offset(x - cos(ang) * len, y - sin(ang) * len),
                    strokeWidth = 1.8f.dp.toPx(),
                )
                drawCircle(accent.copy(alpha = a), radius = 1.6f.dp.toPx(), center = Offset(x, y))
            }
        }

        DockFx.INNER_FIREWORK -> {
            repeat(18) { i ->
                val ang = i * (2f * PI.toFloat() / 18f)
                val sp = 0.55f + (i % 5) * 0.12f
                val dist = base * 2.2f * sp * easeOutCubic(t)
                val a = (1f - t) * (0.9f - 0.08f * (i % 4))
                val x = c.x + cos(ang) * dist
                val y = c.y + sin(ang) * dist + t * t * base * 0.9f // 一点重力，落下来
                drawCircle(
                    color = accent.copy(alpha = a),
                    radius = (1.4f + 1.2f * (1f - t)).dp.toPx(),
                    center = Offset(x, y),
                )
                // 拖尾
                drawLine(
                    color = accent.copy(alpha = a * 0.5f),
                    start = Offset(x, y),
                    end = Offset(x - cos(ang) * dist * 0.16f, y - sin(ang) * dist * 0.16f),
                    strokeWidth = 1.2f.dp.toPx(),
                )
            }
        }

        DockFx.INNER_DOTTRAIL -> {
            // 圆点从左边滑到图标正下方，再缩小消失
            val slide = easeOutCubic(t)
            val x = c.x - size.width * 0.9f * (1f - slide)
            val y = size.height - 1.dp.toPx()
            val r = 2.4f.dp.toPx() * (1f - t * 0.5f)
            drawCircle(accent.copy(alpha = (1f - t) * 0.9f), radius = r, center = Offset(x, y))
        }

        DockFx.INNER_GRADFLOW -> {
            // 一条橙→粉的渐变带在图标上来回流动，常驻
            val w = size.width * 0.9f
            val x = c.x - w / 2f + flowX * size.width * 0.8f
            drawRect(
                brush = Brush.horizontalGradient(
                    listOf(Color.Transparent, accent.copy(alpha = 0.38f), Color(0xFFF76FB0).copy(alpha = 0.30f), Color.Transparent),
                    startX = x,
                    endX = x + w,
                ),
                topLeft = Offset(0f, 0f),
                size = size,
            )
        }

        else -> Unit
    }
}

/** 每套的时长：粒子类要长一点，纯位移类短一点，都在 260–520ms。 */
private fun durationFor(fx: DockFx?): Int = when (fx) {
    null -> 420
    DockFx.INNER_BOUNCE, DockFx.INNER_ELASTIC -> 520
    DockFx.INNER_FIREWORK, DockFx.INNER_STARS, DockFx.INNER_BUBBLES -> 620
    DockFx.INNER_GRADFLOW, DockFx.INNER_MAGNET -> 400
    else -> 380
}

private fun easingFor(fx: DockFx?): androidx.compose.animation.core.Easing =
    if (fx == DockFx.INNER_ELASTIC || fx == DockFx.INNER_BOUNCE) {
        FastOutSlowInEasing
    } else {
        FastOutSlowInEasing
    }

// ───────────── 曲线工具 ─────────────

private fun easeOutCubic(x: Float): Float {
    val t = x.coerceIn(0f, 1f)
    return 1f - (1f - t) * (1f - t) * (1f - t)
}

/** 冲过头再回来的曲线，[k] 越大冲得越远。 */
private fun easeOutBack(x: Float, k: Float): Float {
    val t = x.coerceIn(0f, 1f) - 1f
    return 1f + (k + 1f) * t * t * t + k * t * t
}

/** 在 [from]..[to] 区间里鼓一个正半波，区间外为 0。 */
private fun bump(t: Float, from: Float, to: Float): Float {
    if (t <= from || t >= to) return 0f
    val x = (t - from) / (to - from)
    return sin(x * PI.toFloat())
}

/** 给绘制层用的伪随机，避免每次重组都换一套粒子位置。 */
internal fun stableSeed(i: Int, salt: Int = 0): Float =
    Random(i * 7919 + salt * 104729).nextFloat()
