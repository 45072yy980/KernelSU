package me.diksu.ultra.foxsu.ui.component.bottombar

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.PagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import me.diksu.ultra.foxsu.data.settings.CustomizationStore
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.sign
import kotlin.math.sqrt

/**
 * 底栏的**滑动指示器**：选中项背后那块色块，从一个格子「走」到下一个格子。
 *
 * 这是底栏的基础行为，不是特效开关——Miuix 原版和 Material3 的 NavigationBar 都是这样：
 * 切换时色块平移过去，而不是在旧位置淡出、新位置淡入。
 *
 * 位置来源只有一处——**pager 自己的连续滚动位置**：
 *
 * ```
 * pageFloat = pagerState.currentPage + pagerState.currentPageOffsetFraction
 * ```
 *
 * - 手指拖动时 `offsetFraction` 实时变化，色块 `snapTo` 严格跟手，拖到哪它就在哪；
 * - 点图标 / 松手时色块走自己的 spring（stiffness 260 ≈ 400ms），
 *   比 pager 的收尾略慢一点，于是能看清它「流」过去，而不是瞬移到位。
 *
 * ## 水滴为什么要用「速度」驱动
 *
 * 第一版是**脉冲驱动**：切换瞬间把拉伸量 snap 到 1，再衰减回 0。看起来合理，
 * 实际几乎看不见——拉伸的峰值落在 t=0，那时色块还没开始动；等色块真正加速到最快，
 * 拉伸已经衰减掉大半了。**动的地方没形变，形变的地方没动**，两者错开了一整个相位。
 *
 * 现在改成按色块的**瞬时位移速度**驱动，用一个二阶系统跟随：
 *
 * ```
 * 目标拉伸 = 速度 / 320dp·s⁻¹            （0–1，方向单独记）
 * 跟随系统 = k 1500 / ζ 0.62             （≈120ms 跟上，落位时轻微超调形成「吸回」）
 * ```
 *
 * 于是拉伸曲线与位移曲线同相：起步时速度小、几乎不拉；中段最快、拉得最长；
 * 临近落位速度归零，形状自己收回并微微过冲一下——这才是液体被甩出去再停住的样子，
 * 不需要为「什么时候鼓包」写任何时间表。
 */
@Composable
internal fun DockSlideIndicator(
    pagerState: PagerState,
    count: Int,
    slotW: Dp,
    slotH: Dp,
    gap: Dp,
    plateW: Dp,
    plateH: Dp,
    shape: Shape,
    brush: Brush,
    highlight: Color,
    modifier: Modifier = Modifier,
) {
    if (count <= 1) return

    val waterDrop = CustomizationStore.current.dockWaterDrop
    val strideDp = (slotW + gap).value
    val insetX = ((slotW - plateW) / 2).value
    val insetY = ((slotH - plateH) / 2).value

    // 位置用 dp 值存（不是 px）：密度变化时不用重新换算，动画不会因为单位切换而跳
    val x = remember { Animatable(pagerState.currentPage * strideDp) }

    LaunchedEffect(pagerState, strideDp, count) {
        snapshotFlow {
            Triple(
                pagerState.isScrollInProgress,
                pagerState.currentPage,
                pagerState.currentPageOffsetFraction,
            )
        }.collectLatest { (scrolling, page, fraction) ->
            val max = (count - 1).toFloat()
            // pager 在动（手指拖动 or 自己收尾）时逐帧跟手。
            // 这里不能用「只有 dragging 才跟手」——松手后 pager 还要滑一段，
            // 那段时间里 fraction 每帧都在变，若走 animateTo 分支，
            // collectLatest 会每帧取消再重启一次弹簧，色块会一顿一顿地爬，反而更糟。
            // 水滴的「液体感」因此完全由速度驱动（见下方），不依赖这里的时间差。
            if (scrolling) {
                x.snapTo((page + fraction).coerceIn(0f, max) * strideDp)
            } else {
                x.animateTo(
                    page.coerceIn(0, count - 1) * strideDp,
                    spring(dampingRatio = 1f, stiffness = 260f),
                )
            }
        }
    }

    // ── 水滴：拉伸幅度（0 起，可轻微为负 = 落位吸回）+ 方向（只在有明显运动时更新）──
    val stretch = remember { mutableFloatStateOf(0f) }
    val flowDir = remember { mutableFloatStateOf(1f) }

    LaunchedEffect(waterDrop) {
        if (!waterDrop) {
            stretch.floatValue = 0f
            return@LaunchedEffect
        }
        var lastX = x.value
        var lastT = withFrameNanos { it }
        var vel = 0f
        while (true) {
            // 活跃段：逐帧采样位移求速度。
            // 一旦连续 3 帧「没有位移、形变和速度都归零」，就跳出到下面的挂起点——
            // 之前这里是死循环，静止时也每帧醒一次白跑，水滴开关常年开着会持续占一帧。
            var calm = 0
            while (calm < 3) {
                withFrameNanos { now ->
                    val dt = ((now - lastT) / 1_000_000_000f).coerceIn(1f / 240f, 1f / 20f)
                    lastT = now
                    val cur = x.value
                    val speed = (cur - lastX) / dt
                    lastX = cur

                    // 方向单独维护：幅度归零时速度的正负噪声不该让色块抖一下
                    if (abs(speed) > 60f) flowDir.floatValue = sign(speed)

                    // 320dp/s ≈ 一格切换中段的速度，取它当满幅参考。
                    // 定得太高（420+）会导致普通的一格切换只拉到四成，看不出「水流」。
                    // 减去 25dp/s 的死区：弹簧尾巴上那几个 dp/s 的残余速度会让色块
                    // 一直维持 1%~4% 的拉伸，肉眼是一阵几乎察觉不到的「呼吸」。
                    val target = ((abs(speed) - 25f) / 320f).coerceIn(0f, 1f)
                    val k = 1500f
                    val c = 2f * sqrt(k) * 0.62f
                    val acc = k * (target - stretch.floatValue) - c * vel
                    vel += acc * dt
                    // 上限 1.15 留给起步超调；下限 -0.10 让落位时「吸回」一下
                    stretch.floatValue = (stretch.floatValue + vel * dt).coerceIn(-0.10f, 1.15f)

                    calm = if (
                        abs(speed) < 2f &&
                        abs(stretch.floatValue) < 0.004f &&
                        abs(vel) < 2f
                    ) {
                        calm + 1
                    } else {
                        0
                    }
                }
            }

            // 静止段：把形变彻底收干净，然后等指示器真的动起来再唤醒。
            // snapshotFlow 只在 x 变化时发射，挂起期间不占任何帧。
            stretch.floatValue = 0f
            vel = 0f
            val rest = x.value
            snapshotFlow { x.value }.first { it != rest }
            lastX = x.value
            lastT = withFrameNanos { it }
        }
    }

    val mag = stretch.floatValue
    val dir = flowDir.floatValue

    // 越拉越长、越拉越扁：高度最多压到 0.84，宽度最多拉出 0.42 格
    val squash = 1f - 0.16f * abs(mag).coerceAtMost(1f)
    val tail = strideDp * 0.42f * mag * dir
    val left = x.value + insetX - max(tail, 0f)
    val width = (plateW.value + abs(tail)).dp

    // 拖尾段用 DstOut 擦成半透明：真实水滴被拉长时尾部是变薄变淡的，
    // 整块等不透明度的色块拉过去，会像一张贴纸被拽，而不是水。
    // 擦除量随拉伸幅度增长，静止时 tailPx = 0，这一步直接跳过。
    val tailPx = with(LocalDensity.current) { abs(tail).dp.toPx() }
    val tailAlpha = 0.72f * (abs(mag).coerceAtMost(1f))

    Box(modifier) {
        Box(
            modifier = Modifier
                .offset(x = left.dp, y = insetY.dp)
                .width(width)
                .height(plateH)
                .graphicsLayer {
                    scaleY = squash
                    // DstOut 必须在独立图层里做，否则会把底栏背景一起擦穿
                    compositingStrategy = CompositingStrategy.Offscreen
                }
                .clip(shape)
                .background(brush)
                .drawWithContent {
                    drawContent()
                    if (tailPx > 0.5f) {
                        // 0f 落在拖尾最末端（擦得最多），1f 落在靠主体一侧（完全不擦）；
                        // 向左流时整段翻到右侧，所以两个 alpha 直接对调
                        val start = if (dir > 0) 0f else size.width - tailPx
                        val end = if (dir > 0) tailPx else size.width
                        drawRect(
                            brush = Brush.horizontalGradient(
                                colorStops = arrayOf(
                                    0f to Color.Black.copy(alpha = if (dir > 0) tailAlpha else 0f),
                                    1f to Color.Black.copy(alpha = if (dir > 0) 0f else tailAlpha),
                                ),
                                startX = start,
                                endX = end,
                            ),
                            blendMode = BlendMode.DstOut,
                        )
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            // 顶部一道极细高光：反射亮边，鼓包时更明显
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .align(Alignment.TopCenter)
                    .background(highlight.copy(alpha = 0.20f + 0.30f * abs(mag).coerceAtMost(1f))),
            )
        }
    }
}

/**
 * 选中项图标的水滴落点回弹。
 *
 * 新选中项先被「砸」下去再带超调弹回；旧项被吸走一点再回位，
 * 避免它看起来完全无动于衷。返回值直接喂给 `graphicsLayer.scaleX/scaleY`。
 *
 * 未开启水滴时恒为 1f，调用方无需分支。
 */
@Composable
internal fun rememberDropIconScale(selected: Boolean): Float {
    val on = CustomizationStore.current.dockWaterDrop
    val impact = remember { Animatable(1f) }
    LaunchedEffect(selected, on) {
        if (!on) {
            impact.snapTo(1f)
            return@LaunchedEffect
        }
        if (selected) {
            // 等色块先流过来再落点：80ms 的延迟让「缩」和「色块抵达」大致同帧，
            // 否则图标在色块还没到的时候就自己缩完了，看着像两件事
            delay(80)
            // 先压到 0.78 再弹回，阻尼 0.42 让它明显越过 1 再稳住（≈400ms）
            impact.snapTo(0.78f)
            impact.animateTo(1f, spring(dampingRatio = 0.42f, stiffness = 240f))
        } else {
            // 旧项被「吸走」一点，让视线自然滑向新项
            impact.snapTo(0.90f)
            impact.animateTo(1f, spring(dampingRatio = 0.70f, stiffness = 420f))
        }
    }
    return impact.value
}

/**
 * 滑动指示器压住图标时，选中项的前景色。
 *
 * 色块是一整块渐变实底，原来「选中 = 强调色」的文字/图标会陷进去。
 * 亮色主题给白字；暗色主题的强调色本身偏亮，给一层近黑墨色才够对比。
 */
@Composable
internal fun slidePlateInk(dark: Boolean): Color = if (dark) Color(0xFF20182E) else Color.White
