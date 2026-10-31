package me.diksu.ultra.foxsu.ui.screen.ag

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.addOutline
import androidx.compose.ui.platform.LocalDensity
import kotlinx.coroutines.launch
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.composed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import me.diksu.ultra.foxsu.data.settings.CustomizationStore

/**
 * 主页大卡片的 3D 触摸特效：
 * - 手指按下后，卡片跟着触摸点做透视倾斜（最大 ±7°，cameraDistance 拉远避免棱角失真）；
 * - 触摸点处有一团径向反射流光跟着手指走；
 * - 松手后 rotation 用 Spring 阻尼回正，流光淡出。
 *
 * 只改 rotation/绘制，不消费点击以外的手势，卡片本身的功能不受影响。
 */
fun Modifier.tiltCardFx(): Modifier = tiltTransform()

private fun Modifier.tiltTransform(): Modifier = composed {
    // 档位：0 关闭（完全不动）/ 1 克制（角度减半）/ 2 全开
    val level = CustomizationStore.current.fxLevel
    if (level <= 0) return@composed this
    val maxTilt = if (level >= 2) 12f else 6f
    val rotX = remember { Animatable(0f) }
    val rotY = remember { Animatable(0f) }
    val glow = remember { Animatable(0f) }
    var light by remember { mutableStateOf(Offset(-999f, -999f)) }
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    this
        .graphicsLayer {
            rotationX = rotX.value
            rotationY = rotY.value
            // cameraDistance 拉大 = 透视更"远"，大角度下也不会出现明显的梯形畸变
            cameraDistance = 16f * density
        }
        .drawWithContent {
            drawContent()
            if (glow.value > 0.01f) {
                drawRect(
                    Brush.radialGradient(
                        0f to Color.White.copy(alpha = 0.26f * glow.value),
                        0.45f to Color.White.copy(alpha = 0.07f * glow.value),
                        1f to Color.Transparent,
                        center = light,
                        radius = size.width * 0.62f,
                    )
                )
            }
        }
        .pointerInput(Unit) {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                light = down.position
                scope.launch { glow.animateTo(1f, tween(160)) }
                while (true) {
                    val event = awaitPointerEvent()
                    val change = event.changes.firstOrNull() ?: break
                    if (change.changedToUp()) {
                        change.consume()
                        break
                    }
                    val pos = change.position
                    light = pos
                    val nx = (pos.x - size.width / 2f) / (size.width / 2f)
                    val ny = (pos.y - size.height / 2f) / (size.height / 2f)
                    val rx = (-ny * maxTilt).coerceIn(-maxTilt, maxTilt)
                    val ry = (nx * maxTilt).coerceIn(-maxTilt, maxTilt)
                    scope.launch {
                        rotY.snapTo(ry)
                        rotX.snapTo(rx)
                    }
                    change.consume()
                }
                scope.launch {
                    // 松手：Spring 阻尼回正 + 流光淡出
                    rotX.animateTo(0f, spring(dampingRatio = 0.5f, stiffness = 420f))
                    rotY.animateTo(0f, spring(dampingRatio = 0.5f, stiffness = 420f))
                    glow.animateTo(0f, tween(340))
                }
            }
        }
}

/**
 * 科幻粒子边：彩色光点沿圆角轮廓流动，拖尾逐颗变淡，
 * 尾段沿法线方向向外飘散进虚空（飘散距离随拖尾加深）。
 *
 * 有两处细节保证「粒子是贴着外形走的、而且能飘出去」：
 *   - 路径从 [inset] 处向内收一点，这样粒子中心正好压在描边中线上，
 *     视觉上像正沿着边缘跑，而不是被边缘切掉半个；
 *   - 飘散方向统一朝外，并且额外加大头部半径的亮芯，
 *     让小尺寸按钮（安装按钮）上也看得清。
 *
 * 调用方必须让本 Canvas 的绘制区域比实体轮廓大一圈（或不做 clip），
 * 否则飘出去的粒子会被父级裁掉。
 */
@Composable
fun ParticleEdgeOverlay(
    modifier: Modifier = Modifier,
    colorSeed: Int = 0,
    cornerRadius: Dp = 999.dp,
    speedMillis: Int = 2800,
    tailLength: Int = 16,
    dotMinRadius: Dp = 1.1.dp,
    dotMaxRadius: Dp = 2.7.dp,
) {
    val transition = rememberInfiniteTransition(label = "p-edge")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(speedMillis, easing = LinearEasing)),
        label = "p-edge-phase",
    )
    Canvas(modifier) { drawParticleEdge(phase, cornerRadius, colorSeed, tailLength, dotMinRadius, dotMaxRadius) }
}

/** 六色霓虹调色板，按 seed 轮转起点，多块同时出现时不会完全同步。 */
private val particlePalette = listOf(
    Color(0xFFFF7A5C), Color(0xFFFFD84D), Color(0xFF7FD896),
    Color(0xFFA88BFF), Color(0xFFEC4899), Color(0xFF38BDF8),
)

/**
 * 实际绘制。坐标系是「当前 DrawScope 的全尺寸」= 实体轮廓 + 四周 bleed。
 * [bleedPx] 由调用方通过 size 与真实轮廓的差值反推，这里以 6dp 兜底。
 */
private fun DrawScope.drawParticleEdge(
    phase: Float,
    cornerRadius: Dp,
    colorSeed: Int,
    tailLength: Int,
    dotMinRadius: Dp,
    dotMaxRadius: Dp,
) {
    // Canvas 通常比实体轮廓外扩了一圈，收回来才是实体边缘
    val bleed = minOf(6.dp.toPx(), size.width * 0.16f, size.height * 0.16f)
    val left = bleed
    val top = bleed
    val right = size.width - bleed
    val bottom = size.height - bleed
    val w = right - left
    val h = bottom - top
    if (w <= 1f || h <= 1f) return

    val r = if (cornerRadius >= 999.dp) minOf(w, h) / 2f
    else minOf(cornerRadius.toPx(), minOf(w, h) / 2f)

    val composePath = Path().apply {
        addRoundRect(RoundRect(left, top, right, bottom, CornerRadius(r, r)))
    }
    val pm = android.graphics.PathMeasure(composePath.asAndroidPath(), false)
    val len = pm.length
    if (len <= 0f) return

    val palette = particlePalette.let { p ->
        val s = ((colorSeed % p.size) + p.size) % p.size
        p.subList(s, p.size) + p.subList(0, s)
    }
    val overflow = 90f                 // 头部跑出终点后继续飞行的一段距离（像素）
    val head = phase * (len + overflow)
    val tail = tailLength
    val gap = (len / (tail * 6.5f)).coerceIn(4.5f, 11f)
    val driftMax = (minOf(w, h) * 0.26f).coerceIn(6.dp.toPx(), 13.dp.toPx())
    val pos = FloatArray(2)
    val tan = FloatArray(2)
    val headAlpha = (1f - (head - len).coerceAtLeast(0f) / overflow).coerceIn(0f, 1f)

    for (i in tail - 1 downTo 0) {
        val d = head - i * gap
        if (d < 0f || d > len) continue
        val t = i.toFloat() / tail
        pm.getPosTan(d, pos, tan)
        // 法线：路径顺时针走时 (-tanY, tanX) 指向外侧
        val nx = -tan[1]
        val ny = tan[0]
        // 越靠尾越往外飘，并且交替换边产生细碎感
        val drift = t * t * driftMax * (if (i % 3 == 0) 1.35f else 1f)
        val alpha = ((1f - t) * (1f - t) * headAlpha).coerceIn(0f, 1f)
        if (alpha < 0.02f) continue
        val rad = (dotMaxRadius * (1f - t) + dotMinRadius * t).toPx()
        drawCircle(
            color = palette[i % palette.size].copy(alpha = alpha * 0.9f),
            radius = rad,
            center = Offset(pos[0] + nx * drift, pos[1] + ny * drift),
        )
        // 飘散最外段补一层同色柔光，形成「入虚空」的淡出感
        if (t > 0.55f) {
            drawCircle(
                color = palette[i % palette.size].copy(alpha = alpha * 0.28f),
                radius = rad * 2.4f,
                center = Offset(pos[0] + nx * drift, pos[1] + ny * drift),
            )
        }
    }

    // 头部主光点：亮白芯 + 外圈弥散，做视觉焦点
    if (head <= len) {
        pm.getPosTan(head, pos, tan)
        val c = Offset(pos[0], pos[1])
        drawCircle(Color(0x88FFFFFF), radius = dotMaxRadius.toPx() * 2.2f, center = c)
        drawCircle(palette[0], radius = dotMaxRadius.toPx(), center = c)
    }
}

/**
 * 按压「深度内阴影」。
 *
 * 早期版本用四条矩形渐变模拟四边压暗，在 48dp 小按钮上会沿矩形边界留下
 * 一圈生硬的直角黑框，所以要换掉。现在只做两件事：
 *   1. 从边缘向内、多层「描边」叠加的圆角凹陷——每层都是 stroke，天然贴合圆角，
 *      且 alpha 逐层减半，叠加出向内衰减的软边；
 *   2. 整体压一层极淡的暗色，让按下时表面显得「陷进去」。
 * 形状跟着实际尺寸走（圆形按钮 = 圆形，矩形按钮 = 12dp 圆角），不再有方框。
 */
fun DrawScope.drawInnerShadow(alpha: Float) {
    if (alpha <= 0.01f) return
    val w = size.width
    val h = size.height
    if (w <= 0f || h <= 0f) return
    val radius = minOf(w, h) / 2f
    val shape = RoundRect(0f, 0f, w, h, CornerRadius(radius, radius))

    // 1) 整体极淡压暗，越靠边越暗（radial 反向：中心透明、外圈微暗）
    drawRect(
        Brush.radialGradient(
            0f to Color.Transparent,
            0.72f to Color.Black.copy(alpha = 0.06f * alpha),
            1f to Color.Black.copy(alpha = 0.16f * alpha),
            center = Offset(w / 2f, h * 0.42f),
            radius = maxOf(w, h) * 0.78f,
        )
    )

    // 2) 多层圆角描边，向内衰减成软内阴影
    val layers = 6
    val maxStroke = minOf(w, h) * 0.22f
    for (i in 0 until layers) {
        val t = i.toFloat() / (layers - 1)          // 0 = 最外层
        val stroke = (maxStroke * (1f - t * 0.82f)).coerceAtLeast(0.6f)
        val a = (0.13f * (1f - t) * (1f - t)) * alpha
        if (a < 0.004f) continue
        drawRoundRect(
            color = Color.Black.copy(alpha = a),
            topLeft = Offset(stroke / 2f, stroke / 2f),
            size = androidx.compose.ui.geometry.Size(
                (w - stroke).coerceAtLeast(0.1f),
                (h - stroke).coerceAtLeast(0.1f),
            ),
            cornerRadius = CornerRadius(
                (radius - stroke / 2f).coerceAtLeast(0f),
                (radius - stroke / 2f).coerceAtLeast(0f),
            ),
            style = androidx.compose.ui.graphics.drawscope.Stroke(width = stroke),
        )
    }

    // 3) 顶部一道极细高光，暗示按压处被挤出的小凸面
    drawRoundRect(
        brush = Brush.verticalGradient(
            0f to Color.White.copy(alpha = 0.10f * alpha),
            1f to Color.Transparent,
            startY = 0f,
            endY = h * 0.5f,
        ),
        size = androidx.compose.ui.geometry.Size(w, h * 0.5f),
        cornerRadius = CornerRadius(radius, radius),
    )
}

/**
 * 共享元素缩放：从 origin（触发按钮的屏幕矩形）放大到自身位置，progress 0→1；
 * 关闭时把 progress 动画回 0 即「缩回原按钮」。
 */
fun Modifier.zoomFromOrigin(origin: androidx.compose.ui.geometry.Rect, progress: Float): Modifier = graphicsLayer {
    if (progress >= 0.999f || origin.isEmpty) return@graphicsLayer
    val startScale = (origin.width / size.width).coerceIn(0.06f, 1f)
    val sc = startScale + (1f - startScale) * progress
    scaleX = sc
    scaleY = sc
    val selfCenter = Offset(size.width / 2f, size.height / 2f)
    translationX = (origin.center.x - selfCenter.x) * (1f - progress)
    translationY = (origin.center.y - selfCenter.y) * (1f - progress)
    alpha = (progress * 2.6f).coerceIn(0f, 1f)
}

/**
 * 卡片/按钮外轮廓的科幻粒子边。
 *
 * 与 [ParticleEdgeOverlay] 的区别：这里不额外占布局空间，直接在元素自身尺寸上
 * 「外扩一圈」作画。做三件事：
 *   1. 沿 [shape] 的轮廓描一条极细的渐变光带（1dp，跟着粒子方向旋转的扫描光）；
 *   2. 粒子沿轮廓流动，尾段向外飘出到 [bleed] 距离之外；
 *   3. 描边色由 [colors] 循环，多张卡片同时出现不会撞色。
 *
 * 注意：调用方的父级容器不要做 clip，否则飘出去的粒子会被裁掉；
 * 确实需要裁（例如列表里），把 [bleed] 设小一点即可。
 */
fun Modifier.particleOutline(
    shape: Shape = RoundedCornerShape(28.dp),
    colors: List<Color> = particlePalette,
    bleed: Dp = 10.dp,
    accent: Color? = null,
    running: Boolean = true,
): Modifier = composed {
    // 特效档位：0 关闭（连无限动画都不创建，省掉整帧重绘）/ 1 克制（颗粒少一半、速度再慢一档）/ 2 全开
    val fx = CustomizationStore.current.fxLevel
    if (!running || fx <= 0) return@composed this
    val slow = fx <= 1
    val transition = rememberInfiniteTransition(label = "p-outline")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(if (slow) 8600 else 6200, easing = LinearEasing)),
        label = "p-outline-phase",
    )
    val density = androidx.compose.ui.platform.LocalDensity.current
    val bleedPx = with(density) { bleed.toPx() }
    this.drawWithCache {
        val outline = shape.createOutline(size, layoutDirection, density)
        val path = Path().apply { addOutline(outline) }
        onDrawWithContent {
            drawContent()
            val pm = android.graphics.PathMeasure(path.asAndroidPath(), false)
            val len = pm.length
            if (len <= 0f) return@onDrawWithContent
            val pos = FloatArray(2)
            val tan = FloatArray(2)

            // 1) 极细轮廓光带：两段弧，跟着相位绕圈
            for (k in 0 until 3) {
                val start = ((phase + k / 3f) % 1f) * len
                val span = len * 0.16f
                val steps = 14
                val pts = ArrayList<Offset>(steps + 1)
                for (s in 0..steps) {
                    pm.getPosTan((start + span * s / steps) % len, pos, tan)
                    pts += Offset(pos[0], pos[1])
                }
                for (s in 1 until pts.size) {
                    val a = (1f - kotlin.math.abs(s / pts.size.toFloat() - 0.5f) * 2f).coerceIn(0f, 1f)
                    drawLine(
                        color = (accent ?: colors[k % colors.size]).copy(alpha = a * 0.55f),
                        start = pts[s - 1],
                        end = pts[s],
                        strokeWidth = 1.dp.toPx(),
                        cap = androidx.compose.ui.graphics.StrokeCap.Round,
                    )
                }
            }

            // 2) 流动粒子 + 尾段飘散
            val overflow = bleedPx * 6f
            val head = phase * (len + overflow)
            val tail = if (slow) 8 else 14
            val gap = (len / (tail * 7.5f)).coerceIn(5f, 13f)
            val driftMax = bleedPx * 1.15f
            val headAlpha = (1f - (head - len).coerceAtLeast(0f) / overflow).coerceIn(0f, 1f)
            for (i in tail - 1 downTo 0) {
                val d = head - i * gap
                if (d < 0f || d > len) continue
                val t = i.toFloat() / tail
                pm.getPosTan(d, pos, tan)
                val nx = -tan[1]
                val ny = tan[0]
                val drift = t * t * driftMax
                val alpha = ((1f - t) * (1f - t) * headAlpha).coerceIn(0f, 1f)
                if (alpha < 0.02f) continue
                val c = colors[i % colors.size]
                val rad = (2.5f - t * 1.4f).dp.toPx()
                drawCircle(c.copy(alpha = alpha * 0.9f), rad, Offset(pos[0] + nx * drift, pos[1] + ny * drift))
                if (t > 0.5f) {
                    drawCircle(
                        c.copy(alpha = alpha * 0.3f),
                        rad * 2.6f,
                        Offset(pos[0] + nx * drift, pos[1] + ny * drift),
                    )
                }
            }
            // 3) 头部亮芯
            if (head <= len) {
                pm.getPosTan(head, pos, tan)
                val c = Offset(pos[0], pos[1])
                drawCircle(Color(0x99FFFFFF), 5.2.dp.toPx(), c)
                drawCircle(accent ?: colors[0], 2.8.dp.toPx(), c)
            }
        }
    }
}
