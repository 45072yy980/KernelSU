package me.diksu.ultra.foxsu.ui.component.bottombar

import androidx.compose.foundation.pager.PagerState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.utils.PagerNavigationSpringSpec

/**
 * 在底栏图标上横滑 = 左右切换页面。
 *
 * 为什么不能靠主 Pager 自己接：四个 Tab 的页面内容全是 `verticalScroll` / `LazyColumn`，
 * 手指一落下，列表在第一帧就把「竖直方向可滚动」这件事登记走了。之后横滑时要等列表
 * 判定「这不是竖直滚动」才会让出，而各家皮肤的列表都带了回弹/吸顶效果，实际几乎不让。
 * 结果就是：页面能听懂点击、听不懂横滑——用户看到的就是「只能点图标切换」。
 *
 * 这里不去和列表抢，而是换一条不经过它的通路：
 *
 * - 挂在**底栏容器**上（图标之间、图标上、狐狸头像上都算），底栏这一圈没有滚动容器；
 * - 在 `Initial` 阶段（比所有 children 都早）记录每一次按下；
 * - 抬手时如果位移主要是横向（|dx| > |dy| 且 > 16dp），就 `animateToPage`，
 *   顺带把这次抬起标成已消费，避免同一次抬手又被图标 click 解释一遍；
 * - 竖直滑动一律放过，底栏上没有可滚内容，不会误伤。
 *
 * 直接用 [PagerState.animateToPage] 而不是 miuix 的 `springAnimateToPage`：
 * 走的是项目里已有的测试路径，不依赖库内部实现。
 */
internal fun Modifier.dockSwipeToPager(
    pagerState: PagerState,
    enabled: Boolean,
    scope: CoroutineScope,
): Modifier = if (!enabled) this else this.pointerInput(pagerState) {
    val threshold = 16.dp.toPx()
    val fastFling = 420.dp.toPx()
    var down: Offset? = null
    var move: Offset = Offset.Zero
    val tracker = VelocityTracker()
    var job: Job? = null

    awaitPointerEventScope {
        while (true) {
            val event = awaitPointerEvent(PointerEventPass.Initial)
            when (event.type) {
                PointerEventType.Press -> {
                    down = event.changes.firstOrNull()?.position
                    move = Offset.Zero
                    tracker.resetTracking()
                }

                PointerEventType.Move -> {
                    val origin = down ?: continue
                    val change = event.changes.firstOrNull() ?: continue
                    tracker.addPosition(change.uptimeMillis, change.position)
                    move = change.position - origin
                }

                PointerEventType.Release -> {
                    val origin = down
                    val change = event.changes.firstOrNull()
                    down = null
                    if (origin == null || change == null) continue

                    val dx = move.x
                    val dy = move.y
                    // 竖向为主：列表/页面自己处理，底栏不表态
                    if (kotlin.math.abs(dx) <= kotlin.math.abs(dy)) continue
                    val vx = runCatching { tracker.calculateVelocity().x }.getOrDefault(0f)
                    val far = kotlin.math.abs(dx) > threshold
                    val fast = kotlin.math.abs(vx) > fastFling
                    if (!far && !fast) continue

                    val target = (pagerState.currentPage + if (dx < 0) 1 else -1)
                        .coerceIn(0, pagerState.pageCount - 1)
                    if (target == pagerState.currentPage) continue

                    // 同一次抬手不要再触发图标 onClick（否则会「滑一下又点了一下」）
                    change.consume()
                    job?.cancel()
                    job = scope.launch {
                        // 与点图标走同一个弹簧：page 位置、PagerNavigationSpringSpec
                        pagerState.animateScrollToPage(
                            page = target,
                            animationSpec = PagerNavigationSpringSpec,
                        )
                    }
                }

                PointerEventType.Exit -> down = null
            }
        }
    }
}
