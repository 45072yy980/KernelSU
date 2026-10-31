package me.diksu.ultra.foxsu.ui.component.ambient

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.dp
import me.diksu.ultra.foxsu.ui.theme.AccentPalette
import me.diksu.ultra.foxsu.ui.theme.currentAccent
import me.diksu.ultra.foxsu.ui.theme.isInDarkTheme
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin

/**
 * 主页背景特效：20 套整屏氛围（源自 FoxSu Background Kit v1.0）。
 *
 * 和 [AmbientPawsLayer] 的分工：
 * - 猫爪那层是「前景浮尘」——十几枚小图标从底部升起，画在内容之上也看不出违和；
 * - 这一层是「整屏氛围」——光斑、极光、雨滴、涡旋，铺满后把整页染色。
 *
 * 所以两者的透明度策略不同：这里所有颜色都乘 [alphaScale]（默认 0.72），
 * 因为设计稿原本是垫在内容**下面**的，而在 FoxSu 里五套皮肤各自铺了不透明底色，
 * 垫在下面根本看不见，只能压在内容之上——那就必须让到「能看见但不干扰阅读」的强度。
 *
 * 铺满的方式和 [AmbientPawsLayer] 一致：画布自己 `fillMaxSize()`。
 * 早先版本直接 `Canvas(modifier)`，调用方不传 modifier 时画布被量成 0×0，
 * 主页上等于什么都没画——「背景特效只在缩略图里可见」就是这么来的。
 *
 * 时间线沿用 [AmbientPawsLayer] 的做法：一个 `withFrameNanos` 循环推进帧号，
 * 粒子数据全是普通对象、只有帧号是 State，每帧只失效 draw 阶段，不触发重组。
 * 20 套里同时只会跑一套，切走时协程随组合一起取消，不留后台帧。
 */
@Composable
fun BackgroundFxLayer(
    style: Int,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    alphaScale: Float = 0.72f,
    playing: Boolean = true,
) {
    if (!enabled || style !in 1..FX_NAMES.size) return

    val accent = currentAccent
    val dark = isInDarkTheme()
    val frame = remember { mutableLongStateOf(0L) }
    val rnd = remember { FxRandom(7) }

    // 粒子表按套系一次性生成：位置、时长、延迟全部来自确定性随机，
    // 保证同一套每次进主页画面一致，不会闪一下换一批。
    val seeds = remember(style) { buildSeeds(style, rnd) }

    LaunchedEffect(playing, style) {
        if (!playing) return@LaunchedEffect
        val start = withFrameNanos { it }
        while (true) {
            withFrameNanos { frame.longValue = (it - start) / 1_000_000L }
        }
    }

    Canvas(modifier.fillMaxSize()) {
        frame.longValue
        // 静态预览取一个「画面已经铺开」的时刻，否则多数套系的第一帧是全透明的
        val t = if (playing) frame.longValue / 1000f else 3.4f
        drawFx(style, t, accent, alphaScale, seeds, dark)
    }
}

/** 套系名，索引 0 对应第 1 套。选择器页与设置摘要共用这一份。 */
val FX_NAMES = listOf(
    "呼吸光斑", "漂浮微粒", "极光流带", "波纹涟漪", "柔和网格",
    "斜向流光", "大气雾层", "光点连线", "液态渐变", "雨滴下落",
    "星芒微闪", "光晕环旋", "色彩漂移", "同心声波", "飘落花瓣",
    "斜纹缓扫", "视差光斑", "上下呼吸", "粒子涡旋", "晨昏暖光",
)

private const val TAU = (2 * PI).toFloat()

// ───────────────────────── 绘制调度 ─────────────────────────

private fun DrawScope.drawFx(
    style: Int,
    t: Float,
    pal: AccentPalette,
    k: Float,
    seeds: FloatArray,
    dark: Boolean,
) {
    val acc = pal.primary
    val acc2 = pal.secondary
    when (style) {
        1 -> fxBreathe(t, acc, k, seeds)
        2 -> fxDust(t, acc, k, seeds)
        3 -> fxAurora(t, acc, acc2, k)
        4 -> fxRipple(t, acc, k)
        5 -> fxGrid(t, acc, k)
        6 -> fxSweep(t, acc, k, dark)
        7 -> fxFog(t, acc, acc2, k)
        8 -> fxConstellation(t, acc, k)
        9 -> fxLiquid(t, acc, acc2, k)
        10 -> fxRain(t, acc, k, seeds)
        11 -> fxSparkle(t, acc, k, seeds)
        12 -> fxRings(t, acc, k)
        13 -> fxDrift(t, acc, acc2, k)
        14 -> fxSonar(t, acc, k)
        15 -> fxPetals(t, acc, acc2, k, seeds)
        16 -> fxStripes(t, acc, k)
        17 -> fxParallax(t, acc, acc2, k)
        18 -> fxBreatheUpDown(t, acc, acc2, k)
        19 -> fxSwirl(t, acc, k, seeds)
        20 -> fxDawn(t, acc, k)
    }
}

/** 循环相位：把 (t + delay) / dur 折回 0..1。 */
private fun ph(t: Float, dur: Float, delay: Float = 0f): Float {
    val p = (t + delay) / dur
    return p - floor(p)
}

/**
 * 一团柔光：把单位圆按 rx/ry 缩放成椭圆再填充径向渐变。
 *
 * 不能直接用 `drawOval` + `radialGradient`——渐变的半径是按绘制区域短边算的，
 * 长轴方向会提前衰减，椭圆边缘出现硬边。缩放坐标系才能让渐变与形状同步。
 */
private fun DrawScope.softOval(
    cx: Float,
    cy: Float,
    rx: Float,
    ry: Float,
    color: Color,
    alpha: Float,
) {
    if (alpha <= 0.001f || rx <= 0f || ry <= 0f) return
    translate(cx, cy) {
        scale(rx, ry, pivot = Offset.Zero) {
            drawCircle(
                brush = Brush.radialGradient(
                    listOf(color, Color.Transparent),
                    center = Offset.Zero,
                    radius = 1f,
                ),
                radius = 1f,
                center = Offset.Zero,
                alpha = alpha.coerceIn(0f, 1f),
            )
        }
    }
}

// ───────────────────────── 1..20 ─────────────────────────

/** 01 呼吸光斑：三团大柔光各自呼吸，错开 2.4s，永远不同步。 */
private fun DrawScope.fxBreathe(t: Float, acc: Color, k: Float, seeds: FloatArray) {
    val w = size.width
    val h = size.height
    val spots = arrayOf(
        floatArrayOf(0.06f, 0.02f, 0.34f, 0f, 1f),
        floatArrayOf(0.94f, 0.44f, 0.27f, 2.4f, 0.76f),
        floatArrayOf(0.38f, 1.0f, 0.38f, 4.6f, 0.64f),
    )
    spots.forEach { s ->
        val p = ph(t, 7f, s[3])
        val wave = sin(p * TAU - PI.toFloat() / 2)
        val grow = 1f + 0.20f * wave
        softOval(
            cx = w * s[0], cy = h * s[1],
            rx = w * s[2] * grow, ry = w * s[2] * grow,
            color = acc,
            alpha = (0.30f + 0.24f * wave) * s[4] * k,
        )
    }
}

/** 02 漂浮微粒：16 颗微尘自底部升起，边升边横向飘移，升到四成屏高散掉。 */
private fun DrawScope.fxDust(t: Float, acc: Color, k: Float, seeds: FloatArray) {
    val w = size.width
    val h = size.height
    val unit = density
    repeat(16) { i ->
        val o = i * 5
        val laneX = seeds[o]
        val delay = seeds[o + 1] * 9f
        val dur = 9f + seeds[o + 2] * 7f
        val dx = (seeds[o + 3] - 0.5f) * 40f * unit
        val sz = (3f + seeds[o + 4] * 4f) * unit
        val p = ph(t, dur, delay)

        val rise = h * 0.44f
        val y = h * 0.94f - rise * p
        val x = w * laneX + dx * p
        // 前 12% 淡入，85% 后淡出，中间保持
        val a = when {
            p < 0.12f -> p / 0.12f * 0.8f
            p > 0.85f -> (1f - p) / 0.15f * 0.5f
            else -> 0.62f
        } * k

        drawCircle(acc, radius = sz * 2.6f, center = Offset(x, y), alpha = a * 0.18f)
        drawCircle(acc, radius = sz / 2f, center = Offset(x, y), alpha = a)
    }
}

/** 03 极光流带：三条横向柔光带，倾斜 + 上下浮动 + 纵向呼吸。 */
private fun DrawScope.fxAurora(t: Float, acc: Color, acc2: Color, k: Float) {
    val w = size.width
    val h = size.height
    val bands = arrayOf(
        floatArrayOf(0.14f, 12f, 0f, 4f, 0.55f),
        floatArrayOf(0.46f, 15f, 4f, -3f, 0.42f),
        floatArrayOf(0.74f, 18f, 8f, 3.5f, 0.30f),
    )
    bands.forEach { b ->
        val p = ph(t, b[1], b[2])
        val wave = sin(p * TAU)
        val bandH = h * 0.16f
        rotate(b[3] + wave * 2.2f, Offset(w / 2f, h * b[0])) {
            translate(wave * w * 0.09f, wave * h * 0.03f) {
                drawRect(
                    brush = Brush.horizontalGradient(
                        listOf(
                            Color.Transparent,
                            acc.copy(alpha = 0.50f),
                            acc2.copy(alpha = 0.34f),
                            Color.Transparent,
                        ),
                        startX = 0f,
                        endX = w * 1.5f,
                    ),
                    topLeft = Offset(-w * 0.25f, h * b[0] - bandH * (0.5f + 0.12f * wave)),
                    size = Size(w * 1.5f, bandH * (1f + 0.25f * abs(wave))),
                    alpha = b[4] * k,
                )
            }
        }
    }
}

/** 04 波纹涟漪：四个同心圆环从下方圆点扩散，越远越淡。 */
private fun DrawScope.fxRipple(t: Float, acc: Color, k: Float) {
    val w = size.width
    val h = size.height
    val cx = w * 0.5f
    val cy = h * 0.62f
    val base = w * 0.10f
    repeat(4) { i ->
        val p = ph(t, 6f, i * 1.5f)
        val grow = 0.3f + 4.2f * p
        val a = (if (p < 0.12f) p / 0.12f * 0.6f else (1f - p) * 0.66f) * k
        drawCircle(
            color = acc,
            radius = base * grow,
            center = Offset(cx, cy),
            alpha = a.coerceIn(0f, 1f),
            style = Stroke(width = 1.4.dp.toPx()),
        )
    }
}

/** 05 柔和网格：26dp 方格，整体呼吸缩放，中间亮两头弱靠低透明度交代。 */
private fun DrawScope.fxGrid(t: Float, acc: Color, k: Float) {
    val w = size.width
    val h = size.height
    val step = 26.dp.toPx()
    val p = ph(t, 5f)
    val pulse = 0.5f + 0.5f * sin(p * TAU - PI.toFloat() / 2)
    val alpha = (0.30f + 0.28f * pulse) * k
    val grow = 1f + 0.03f * pulse

    val path = Path()
    val cx = w * 0.5f
    val cy = h * 0.6f
    // 以画面中心缩放：网格线坐标先减去中心再乘系数
    var x = -w * 0.5f * grow
    while (x <= w * 0.5f * grow) {
        path.moveTo(cx + x, 0f)
        path.lineTo(cx + x, h)
        x += step
    }
    var y = -h * 0.5f * grow
    while (y <= h * 0.5f * grow) {
        path.moveTo(0f, cy + y)
        path.lineTo(w, cy + y)
        y += step
    }
    drawPath(path, acc.copy(alpha = alpha.coerceIn(0f, 1f)), style = Stroke(1.dp.toPx()))
}

/**
 * 06 斜向流光：一条 105° 的斜带每 5.5 秒扫过一次，扫完停 45%。
 *
 * 设计稿用的是纯白 + 浅橙，那是垫在浅色底上的画法；这里压在内容之上，
 * 浅色皮肤下纯白完全看不见，所以浅色走「强调色提亮 35%」、深色才保留偏白的高光。
 */
private fun DrawScope.fxSweep(t: Float, acc: Color, k: Float, dark: Boolean) {
    val w = size.width
    val h = size.height
    val p = ph(t, 5.5f)
    val prog = if (p < 0.55f) p / 0.55f else 1f
    val shift = (-0.55f + 1.1f * prog) * w

    rotate(-15f, Offset(w / 2f, h / 2f)) {
        translate(shift, 0f) {
            drawRect(
                brush = Brush.horizontalGradient(
                    listOf(
                        Color.Transparent,
                        lerp(acc, Color.White, if (dark) 0.78f else 0.34f).copy(alpha = 0.46f),
                        acc.copy(alpha = 0.26f),
                        Color.Transparent,
                    ),
                    startX = w * 0.10f,
                    endX = w * 0.72f,
                ),
                topLeft = Offset(w * 0.10f, -h * 0.35f),
                size = Size(w * 0.62f, h * 1.7f),
                alpha = k * 1.2f,
            )
        }
    }
}

/** 07 大气雾层：两团大椭圆雾，16s / 22s 反向漂移，速度不同显得有纵深。 */
private fun DrawScope.fxFog(t: Float, acc: Color, acc2: Color, k: Float) {
    val w = size.width
    val h = size.height

    fun layer(dur: Float, reverse: Boolean, cx0: Float, cy0: Float, rx: Float, ry: Float, c: Color, a: Float) {
        val p = ph(t, dur)
        val wave = sin(p * TAU) * (if (reverse) -1f else 1f)
        softOval(
            cx = w * cx0 + wave * w * 0.08f,
            cy = h * cy0 - wave * h * 0.06f,
            rx = rx * (1f + 0.10f * wave),
            ry = ry * (1f + 0.10f * wave),
            color = c,
            alpha = a * k,
        )
    }

    layer(16f, false, 0.30f, 0.70f, w * 0.62f, h * 0.26f, acc, 0.44f)
    layer(22f, true, 0.75f, 0.30f, w * 0.52f, h * 0.22f, acc2, 0.36f)
}

/** 08 光点连线：12 个固定点位 + 16 条虚线，点闪烁、线缓慢流动。 */
private val CONSTELLATION_POINTS = arrayOf(
    floatArrayOf(0.16f, 0.18f), floatArrayOf(0.34f, 0.30f), floatArrayOf(0.58f, 0.14f),
    floatArrayOf(0.74f, 0.34f), floatArrayOf(0.86f, 0.62f), floatArrayOf(0.64f, 0.74f),
    floatArrayOf(0.40f, 0.58f), floatArrayOf(0.22f, 0.70f), floatArrayOf(0.10f, 0.44f),
    floatArrayOf(0.50f, 0.46f), floatArrayOf(0.80f, 0.84f), floatArrayOf(0.30f, 0.88f),
)

private val CONSTELLATION_LINKS = arrayOf(
    intArrayOf(0, 1), intArrayOf(1, 2), intArrayOf(2, 3), intArrayOf(3, 4),
    intArrayOf(4, 5), intArrayOf(5, 6), intArrayOf(6, 7), intArrayOf(7, 8),
    intArrayOf(8, 0), intArrayOf(1, 9), intArrayOf(9, 3), intArrayOf(9, 6),
    intArrayOf(6, 10), intArrayOf(10, 5), intArrayOf(7, 11), intArrayOf(11, 6),
)

private fun DrawScope.fxConstellation(t: Float, acc: Color, k: Float) {
    val w = size.width
    val h = size.height
    val dash = PathEffect.dashPathEffect(
        floatArrayOf(3.dp.toPx(), 4.dp.toPx()),
        phase = -t * 8f,
    )
    CONSTELLATION_LINKS.forEach { l ->
        val a = CONSTELLATION_POINTS[l[0]]
        val b = CONSTELLATION_POINTS[l[1]]
        drawLine(
            color = acc,
            start = Offset(w * a[0], h * a[1]),
            end = Offset(w * b[0], h * b[1]),
            strokeWidth = 0.8.dp.toPx(),
            alpha = 0.24f * k,
            pathEffect = dash,
        )
    }
    CONSTELLATION_POINTS.forEachIndexed { i, p ->
        val tw = 0.5f + 0.5f * sin((t / 3.6f + i * 0.12f) * TAU)
        drawCircle(
            color = acc,
            radius = (1.4f + 1.0f * tw).dp.toPx(),
            center = Offset(w * p[0], h * p[1]),
            alpha = (0.25f + 0.55f * tw) * k,
        )
        drawCircle(
            color = acc,
            radius = (4.5f + 3f * tw).dp.toPx(),
            center = Offset(w * p[0], h * p[1]),
            alpha = 0.10f * tw * k,
        )
    }
}

/** 09 液态渐变：三个大色团各自缓慢游走扩张，交集处颜色自然叠深。 */
private fun DrawScope.fxLiquid(t: Float, acc: Color, acc2: Color, k: Float) {
    val w = size.width
    val h = size.height
    val blobs = arrayOf(
        floatArrayOf(0.05f, 0.02f, 0.44f, 14f, 0f, 0f),
        floatArrayOf(0.94f, 0.24f, 0.38f, 14f, 5f, 1f),
        floatArrayOf(0.36f, 1.0f, 0.48f, 14f, 9f, 2f),
    )
    blobs.forEach { b ->
        val p = ph(t, b[3], b[4])
        val wave = sin(p * TAU)
        val wave2 = cos(p * TAU)
        val c = if (b[5].toInt() == 2) lerp(acc, acc2, 0.55f) else if (b[5].toInt() == 1) acc2 else acc
        softOval(
            cx = w * b[0] + wave * w * 0.06f,
            cy = h * b[1] + wave2 * h * 0.04f,
            rx = w * b[2] * (1f + 0.12f * wave2),
            ry = w * b[2] * (1f + 0.12f * wave2),
            color = c,
            alpha = 0.34f * k,
        )
    }
}

/** 10 雨滴下落：22 条细线，长短快慢随机，只在中间段可见。 */
private fun DrawScope.fxRain(t: Float, acc: Color, k: Float, seeds: FloatArray) {
    val w = size.width
    val h = size.height
    val len = 34.dp.toPx()
    repeat(22) { i ->
        val o = i * 3
        val laneX = seeds[o]
        val dur = 2.6f + seeds[o + 1] * 1.8f
        val delay = seeds[o + 2] * 4f
        val p = ph(t, dur, delay)
        val y = -h * 0.1f + p * h * 0.55f
        val a = when {
            p < 0.15f -> p / 0.15f * 0.55f
            else -> (1f - p) * 0.62f
        } * k
        drawLine(
            brush = Brush.verticalGradient(
                listOf(Color.Transparent, acc.copy(alpha = 0.7f)),
                startY = y,
                endY = y + len,
            ),
            start = Offset(w * laneX, y),
            end = Offset(w * laneX, y + len),
            strokeWidth = 1.4.dp.toPx(),
            cap = StrokeCap.Round,
            alpha = a.coerceIn(0f, 1f),
        )
    }
}

/** 11 星芒微闪：十枚十字星，亮点时旋转 45°，暗下去时缩到四成。 */
private fun DrawScope.fxSparkle(t: Float, acc: Color, k: Float, seeds: FloatArray) {
    val w = size.width
    val h = size.height
    repeat(10) { i ->
        val o = i * 3
        val cx = w * seeds[o]
        val cy = h * seeds[o + 1]
        val p = ph(t, 4f, i * 0.55f)
        val on = if (p < 0.45f) p / 0.45f else (1f - p) / 0.55f
        val rot = 45f * if (p < 0.45f) p / 0.45f else 1f
        val sz = (14f * (0.55f + 0.45f * on)).dp.toPx()
        val a = (on * 0.85f) * k
        rotate(rot, Offset(cx, cy)) {
            drawRect(acc, Offset(cx - sz / 2, cy - 0.8.dp.toPx()), Size(sz, 1.6.dp.toPx()), alpha = a)
            drawRect(acc, Offset(cx - 0.8.dp.toPx(), cy - sz / 2), Size(1.6.dp.toPx(), sz), alpha = a)
        }
    }
}

/** 12 光晕环旋：三层圆环反向旋转，每环挂一颗发光小点。 */
private fun DrawScope.fxRings(t: Float, acc: Color, k: Float) {
    val w = size.width
    val h = size.height
    val cx = w * 0.5f
    val cy = h * 0.5f
    val rings = arrayOf(
        floatArrayOf(0.28f, 22f, 0f, 1f),
        floatArrayOf(0.40f, 34f, -1f, 0.72f),
        floatArrayOf(0.54f, 52f, 0f, 0.46f),
    )
    rings.forEach { r ->
        val radius = w * r[0]
        drawCircle(
            color = acc,
            radius = radius,
            center = Offset(cx, cy),
            alpha = 0.16f * r[3] * k,
            style = Stroke(1.dp.toPx()),
        )
        val dir = if (r[2] < 0) -1f else 1f
        val ang = (360f * ph(t, r[1]) * dir - 90f) * PI.toFloat() / 180f
        val dot = Offset(cx + radius * cos(ang), cy + radius * sin(ang))
        drawCircle(acc, radius = 7.dp.toPx(), center = dot, alpha = 0.16f * r[3] * k)
        drawCircle(acc, radius = 2.4.dp.toPx(), center = dot, alpha = 0.85f * r[3] * k)
    }
}

/** 13 色彩漂移：两团主色/副色光斑在画面里游走，位置与色相一起缓慢过渡。 */
private fun DrawScope.fxDrift(t: Float, acc: Color, acc2: Color, k: Float) {
    val w = size.width
    val h = size.height
    val p = ph(t, 18f)
    val a = 0.5f + 0.5f * sin(p * TAU)
    val b = 0.5f + 0.5f * cos(p * TAU)

    softOval(
        cx = w * (0.18f + 0.64f * a),
        cy = h * (0.12f + 0.62f * b),
        rx = w * 0.72f, ry = h * 0.46f,
        color = lerp(acc, acc2, a),
        alpha = 0.30f * k,
    )
    softOval(
        cx = w * (0.88f - 0.62f * b),
        cy = h * (0.92f - 0.58f * a),
        rx = w * 0.62f, ry = h * 0.40f,
        color = lerp(acc2, acc, b),
        alpha = 0.24f * k,
    )
}

/** 14 同心声波：四圈柔光从上方中心向外推，像声呐。 */
private fun DrawScope.fxSonar(t: Float, acc: Color, k: Float) {
    val w = size.width
    val h = size.height
    val cx = w * 0.5f
    val cy = h * 0.34f
    val base = 30.dp.toPx()
    repeat(4) { i ->
        val p = ph(t, 5f, i * 1.25f)
        val grow = 0.4f + 3.2f * p
        val a = (1f - p) * 0.55f * k
        softOval(cx, cy, base * grow, base * grow, acc, a)
    }
}

/** 15 飘落花瓣：十片椭圆花瓣，旋转着斜落，左右摆幅各不相同。 */
private fun DrawScope.fxPetals(t: Float, acc: Color, acc2: Color, k: Float, seeds: FloatArray) {
    val w = size.width
    val h = size.height
    val unit = density
    repeat(10) { i ->
        val o = i * 4
        val laneX = seeds[o]
        val dur = 9f + seeds[o + 1] * 6f
        val delay = seeds[o + 2] * 10f
        val dx = (seeds[o + 3] - 0.5f) * 70f * unit
        val p = ph(t, dur, delay)
        val y = -h * 0.04f + p * h * 0.52f
        val x = w * laneX + sin(p * TAU) * dx
        val a = when {
            p < 0.10f -> p / 0.10f * 0.6f
            p > 0.82f -> (1f - p) / 0.18f * 0.55f
            else -> 0.62f
        } * k
        val sz = 6.5.dp.toPx()
        rotate(p * 400f, Offset(x, y)) {
            drawOval(
                brush = Brush.linearGradient(listOf(acc, acc2)),
                topLeft = Offset(x - sz, y - sz * 0.62f),
                size = Size(sz * 2f, sz * 1.24f),
                alpha = a,
            )
        }
    }
}

/** 16 斜纹缓扫：115° 斜纹整体平移，上中下三段透明度递减，模拟顶部亮到底部消失。 */
private fun DrawScope.fxStripes(t: Float, acc: Color, k: Float) {
    val w = size.width
    val h = size.height
    val step = 22.dp.toPx()
    val offset = (t * (220.dp.toPx() / 12f)) % step
    val slope = h * 0.46f

    for (band in 0..2) {
        val top = h * band / 3f
        val bottom = h * (band + 1) / 3f
        val alpha = (0.30f - band * 0.09f) * k
        clipRect(0f, top, w, bottom) {
            var x = -slope + offset
            while (x < w + slope) {
                drawLine(
                    color = acc,
                    start = Offset(x, top - h * 0.05f),
                    end = Offset(x + slope, bottom + h * 0.05f),
                    strokeWidth = 1.dp.toPx(),
                    alpha = alpha.coerceIn(0f, 1f),
                )
                x += step
            }
        }
    }
}

/** 17 视差光斑：两团大柔光缓慢游走 + 亮度呼吸，模拟有纵深的光照。 */
private fun DrawScope.fxParallax(t: Float, acc: Color, acc2: Color, k: Float) {
    val w = size.width
    val h = size.height
    val p1 = 0.5f + 0.5f * sin(t / 11f * TAU)
    val p2 = 0.5f + 0.5f * sin(t / 15f * TAU + 2.4f)

    softOval(
        cx = w * (0.08f + 0.10f * p1),
        cy = h * (0.06f + 0.08f * p2),
        rx = w * 0.62f, ry = w * 0.62f,
        color = acc,
        alpha = (0.26f + 0.12f * p1) * k,
    )
    softOval(
        cx = w * (0.94f - 0.10f * p2),
        cy = h * (0.94f - 0.08f * p1),
        rx = w * 0.56f, ry = w * 0.56f,
        color = acc2,
        alpha = (0.22f + 0.12f * p2) * k,
    )
}

/** 18 上下呼吸：顶部与底部各一团光，一亮一暗交替，像深呼吸。 */
private fun DrawScope.fxBreatheUpDown(t: Float, acc: Color, acc2: Color, k: Float) {
    val w = size.width
    val h = size.height
    val p = ph(t, 9f)
    val wave = 0.5f + 0.5f * sin(p * TAU - PI.toFloat() / 2)

    softOval(w * 0.5f, h * 0.18f, w * 0.78f, h * 0.56f, acc, (0.26f + 0.42f * wave) * k)
    softOval(w * 0.5f, h * 0.96f, w * 0.68f, h * 0.46f, acc2, (0.24f + 0.40f * (1f - wave)) * k)
}

/** 19 粒子涡旋：26 颗微尘绕中心画螺旋，半径 30dp ↔ 90dp 往返。 */
private fun DrawScope.fxSwirl(t: Float, acc: Color, k: Float, seeds: FloatArray) {
    val w = size.width
    val h = size.height
    val cx = w * 0.5f
    val cy = h * 0.5f
    val unit = density
    val rMin = 26f * unit
    val rMax = 82f * unit

    repeat(26) { i ->
        val o = i * 3
        val delay = seeds[o] * 6f
        val dur = 14f + seeds[o + 1] * 8f
        val spin = seeds[o + 2] * 360f
        val p = ph(t, dur, delay)
        val radius = rMin + (rMax - rMin) * (0.5f - 0.5f * cos(p * TAU))
        val ang = (spin + p * 360f) * PI.toFloat() / 180f
        val x = cx + radius * cos(ang)
        val y = cy + radius * sin(ang) * 0.82f
        val a = (0.22f + 0.34f * sin(p * TAU)) * k
        drawCircle(acc, radius = 2.2.dp.toPx(), center = Offset(x, y), alpha = a.coerceIn(0f, 1f))
        drawCircle(acc, radius = 6.dp.toPx(), center = Offset(x, y), alpha = (a * 0.18f).coerceIn(0f, 1f))
    }
}

/** 20 晨昏暖光：底部一层暖光缓缓升起又落下，像日出日落。 */
private fun DrawScope.fxDawn(t: Float, acc: Color, k: Float) {
    val w = size.width
    val h = size.height
    val p = ph(t, 14f)
    val wave = 0.5f + 0.5f * sin(p * TAU - PI.toFloat() / 2)

    val rise = h * (0.28f - 0.34f * wave)
    val top = h * 0.34f + rise
    drawRect(
        brush = Brush.verticalGradient(
            listOf(Color.Transparent, acc.copy(alpha = 0.16f), acc.copy(alpha = 0.46f)),
            startY = top,
            endY = h,
        ),
        topLeft = Offset(0f, top),
        size = Size(w, (h - top).coerceAtLeast(1f)),
        alpha = (0.55f + 0.45f * wave) * k,
    )
}

// ───────────────────────── 随机数 ─────────────────────────

/**
 * 确定性伪随机，和交付包里的 `makeRnd` 同一套公式：
 * 同一个 seed 永远得到同一组布局，粒子不会在每次重组时重新洗牌。
 */
private class FxRandom(private val seed: Int) {
    fun f(i: Int): Float {
        val x = sin((seed * 131 + i * 9301 + 49297) * 0.0001) * 233280.0
        return (x - floor(x)).toFloat()
    }
}

/**
 * 每套按需生成随机表。各套用到的字段数量不同，统一给 5 个浮点一组，
 * 由绘制侧按自己的语义解释（x、时长、延迟、横向偏移、尺寸）。
 */
private fun buildSeeds(style: Int, rnd: FxRandom): FloatArray = when (style) {
    2 -> FloatArray(16 * 5) { rnd.f(it) }
    10 -> FloatArray(22 * 3) { rnd.f(it + 500) }
    11 -> FloatArray(10 * 3) { rnd.f(it + 900) }
    15 -> FloatArray(10 * 4) { rnd.f(it + 1300) }
    19 -> FloatArray(26 * 3) { rnd.f(it + 1700) }
    else -> FloatArray(0)
}
