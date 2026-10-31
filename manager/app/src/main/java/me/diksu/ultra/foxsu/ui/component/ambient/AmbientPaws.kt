package me.diksu.ultra.foxsu.ui.component.ambient

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.sin
import kotlin.random.Random

/**
 * 氛围层：猫爪、小爱心与四角星从底部缓缓升起。
 *
 * 为什么是「飘在最上层」而不是垫在内容下面：
 * 主页五套皮肤的根布局都各自铺了不透明底色（`agPageBg()`、Fresh 的渐变、Glass 的衬底），
 * 任何垫在下面的东西都看不见。所以这一层画在内容之上，靠「小尺寸 + 低不透明度 +
 * 不接任何指针事件」保证它只是气氛：不挡点击、不抢焦点，划过卡片时像一层浮尘。
 *
 * 三个使用位置共用同一套时间线，只调 [count]：
 * - 主页铺满整屏（12 枚）
 * - 捐赠页同为整屏，但内容更密，降到 10 枚免得和二维码抢注意力
 * - 定制页 280dp 头卡里只放 7 枚，且必须画在狐狸图下面，否则会糊在脸上
 *
 * [alphaScale] 用来适配不同底色：头卡是渐变紫粉，粒子要再压一点才不脏；
 * 主页接近纯色，保持 1 即可。
 *
 * 时间线全部由一个 `withFrameNanos` 驱动，粒子存在普通数组里，只有帧计数是 State。
 * 这样每帧只触发 draw 阶段重绘，不会引起重组，也不会因为十几枚粒子产生十几份快照开销。
 */
@Composable
fun AmbientPawsLayer(
    enabled: Boolean,
    modifier: Modifier = Modifier,
    bottomInset: Dp = 0.dp,
    count: Int = 12,
    alphaScale: Float = 1f,
) {
    if (!enabled) return

    val dark = isSystemInDarkTheme()
    val density = LocalDensity.current
    val bottomPx = with(density) { bottomInset.toPx() }
    val frame = remember { mutableLongStateOf(0L) }

    // 单位盒里的形状只建一次，每帧只做变换，不再分配 Path
    val pawPath = remember { buildPawPath() }
    val heartPath = remember { buildHeartPath() }
    val starPath = remember { buildStarPath() }

    val particles = remember(count, alphaScale) {
        Array(count) { Particle(Random.nextFloat(), Random.nextFloat(), alphaScale) }
    }

    // 暖橙偏珊瑚、爱心偏莓粉、星星偏淡金；深色下提亮，否则在深底上糊成一团
    val pawColor = if (dark) Color(0xFFFFB07A) else Color(0xFFE8874D)
    val heartColor = if (dark) Color(0xFFFF9BB4) else Color(0xFFEF7595)
    val starColor = if (dark) Color(0xFFFFE099) else Color(0xFFE8B85C)

    LaunchedEffect(enabled, bottomPx) {
        var last = withFrameNanos { it }
        while (true) {
            withFrameNanos { now ->
                val dt = ((now - last) / 1_000_000_000f).coerceIn(0f, 0.05f)
                last = now
                particles.forEach { it.step(dt) }
                frame.longValue = now
            }
        }
    }

    Canvas(modifier.fillMaxSize()) {
        // 在 draw 阶段读这个值 → 只让绘制失效，不触发重组
        frame.longValue

        val floor = size.height - bottomPx
        if (floor <= 0f) return@Canvas
        val unit = density.density

        particles.forEach { p ->
            if (p.phase < 0f) return@forEach
            val a = p.alpha()
            if (a <= 0.001f) return@forEach

            val y = floor - p.phase * (floor + 60.dp.toPx())
            val sway = sin(p.phase * TAU * p.swayFreq + p.swayPhase) * p.swayAmp * unit
            val x = p.laneX * size.width + sway
            // 越接近顶部越小一点：像离远了，也避免在天际线附近突兀地消失
            val s = p.sizeDp * unit * (0.86f + 0.14f * p.phase)

            withTransform({
                translate(x, y)
                rotate(p.rot, Offset.Zero)
                scale(s, s, Offset.Zero)
                translate(-0.5f, -0.5f)
            }) {
                drawPath(
                    path = when (p.kind) {
                        1 -> heartPath
                        2 -> starPath
                        else -> pawPath
                    },
                    color = when (p.kind) {
                        1 -> heartColor
                        2 -> starColor
                        else -> pawColor
                    }.copy(alpha = a * p.alphaMax),
                )
            }
        }
    }
}

private const val TAU = (2 * PI).toFloat()

/**
 * 单个漂浮物。字段全部可变，避免每帧分配新对象。
 *
 * [phase] 用法：< 0 表示还没出生（倒计时），0..1 是这一趟的进度，>1 回收重投。
 * [kind]：0 = 猫爪，1 = 爱心，2 = 四角星。
 */
private class Particle(seedLane: Float, seedPhase: Float, private val alphaScale: Float) {
    var kind = 0
    var phase = 0f
    var laneX = 0f
    var speed = 0f
    var sizeDp = 0f
    var swayAmp = 0f
    var swayFreq = 0f
    var swayPhase = 0f
    var rot = 0f
    var rotSpeed = 0f
    var alphaMax = 0f

    init {
        respawn()
        // 初次投放在生命周期中段：冷启动第一帧就该有几枚已经浮在半空，
        // 否则要干等三四秒才看到第一个，像没生效
        phase = seedPhase * 0.95f
        laneX = seedLane
    }

    fun step(dt: Float) {
        if (phase < 0f) {
            phase += dt
            return
        }
        phase += dt * speed
        rot += rotSpeed * dt
        if (phase >= 1f) respawn()
    }

    /** 透明度：淡入占 12%，尾部 30% 缓缓淡出，中间保持满亮度。 */
    fun alpha(): Float {
        if (phase < 0f || phase > 1f) return 0f
        val raw = when {
            phase < 0.12f -> phase / 0.12f
            phase > 0.70f -> (1f - phase) / 0.30f
            else -> 1f
        }
        val eased = raw * raw * (3f - 2f * raw)
        return eased
    }

    /**
     * 重新投一个。寿命 18–30 秒，够慢，一屏上同时能看到五六枚。
     * 比例 猫爪 6.5 : 爱心 2 : 星 1.5 —— 爪印是主体，爱心和闪光只是点缀。
     */
    fun respawn() {
        val r = Random.nextFloat()
        kind = when {
            r < 0.65f -> 0
            r < 0.85f -> 1
            else -> 2
        }
        speed = 1f / (18f + Random.nextFloat() * 12f)
        sizeDp = when (kind) {
            1 -> 10f + Random.nextFloat() * 7f
            2 -> 7f + Random.nextFloat() * 6f
            else -> 13f + Random.nextFloat() * 10f
        }
        swayAmp = 10f + Random.nextFloat() * 16f
        swayFreq = 0.35f + Random.nextFloat() * 0.55f
        swayPhase = Random.nextFloat() * TAU
        rot = when (kind) {
            1 -> (Random.nextFloat() - 0.5f) * 22f
            2 -> (Random.nextFloat() - 0.5f) * 30f
            else -> (Random.nextFloat() - 0.5f) * 16f
        }
        rotSpeed = (Random.nextFloat() - 0.5f) * 6f
        alphaMax = when (kind) {
            1 -> 0.34f + Random.nextFloat() * 0.18f
            2 -> 0.30f + Random.nextFloat() * 0.20f
            else -> 0.26f + Random.nextFloat() * 0.16f
        } * alphaScale
        laneX = 0.08f + Random.nextFloat() * 0.84f
        phase = -Random.nextFloat() * 2.5f
    }
}

/**
 * 猫爪：一枚圆鼓的掌垫 + 上方四枚小趾垫，趾垫间距拉得很开。
 *
 * 之前画的是犬科爪印（大掌垫 + 四趾紧贴成一排），看着像狗。猫的肉垫更圆、
 * 四趾更小、彼此离得更远，外侧两枚还明显低一截，这个比例才「猫」。
 * 0..1 单位盒，中心约在 (0.5, 0.5)。
 */
private fun buildPawPath(): Path {
    val p = Path()
    // 掌垫：接近圆的椭圆，底部略宽
    p.addOval(Rect(0.25f, 0.48f, 0.75f, 0.92f))
    // 四枚趾垫：外侧小、位置低；中间两枚略大、位置高
    val toes = listOf(
        floatArrayOf(0.175f, 0.395f, 0.078f),
        floatArrayOf(0.375f, 0.215f, 0.090f),
        floatArrayOf(0.625f, 0.215f, 0.090f),
        floatArrayOf(0.825f, 0.395f, 0.078f),
    )
    toes.forEach { t ->
        p.addOval(Rect(t[0] - t[2], t[1] - t[2], t[0] + t[2], t[1] + t[2]))
    }
    return p
}

/** 爱心：上下两个三次贝塞尔收成尖底，重心略偏上，看起来是「浮着」的。 */
private fun buildHeartPath(): Path {
    val p = Path()
    p.moveTo(0.5f, 0.87f)
    p.cubicTo(0.10f, 0.60f, 0.04f, 0.30f, 0.24f, 0.18f)
    p.cubicTo(0.37f, 0.10f, 0.48f, 0.17f, 0.50f, 0.28f)
    p.cubicTo(0.52f, 0.17f, 0.63f, 0.10f, 0.76f, 0.18f)
    p.cubicTo(0.96f, 0.30f, 0.90f, 0.60f, 0.50f, 0.87f)
    p.close()
    return p
}

/** 四角星：细长的菱形尖，像 ✦。尖角比圆角更「闪」，小尺寸下也不会糊。 */
private fun buildStarPath(): Path {
    val p = Path()
    p.moveTo(0.5f, 0.04f)
    p.lineTo(0.60f, 0.40f)
    p.lineTo(0.96f, 0.50f)
    p.lineTo(0.60f, 0.60f)
    p.lineTo(0.5f, 0.96f)
    p.lineTo(0.40f, 0.60f)
    p.lineTo(0.04f, 0.50f)
    p.lineTo(0.40f, 0.40f)
    p.close()
    return p
}
