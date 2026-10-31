package me.diksu.ultra.foxsu.ui.component.bottombar

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput

/**
 * 胶囊「读秒自动收起」的共享状态。
 *
 * 倒计时由 [ThemeDockBar] 自己跑（它才知道当前秒数与切页事件），这里只保存
 * 「现在收起来了没有」和「被戳了几次」：
 * - 任何一次点屏幕都会 [poke]，胶囊立刻滑回来并把读秒归零重来；
 * - 切页也走 [poke]，刚换页时不会突然发现底栏不见了。
 */
@Stable
class DockRevealState {
    var hidden by mutableStateOf(false)
        internal set
    var pulse by mutableIntStateOf(0)
        private set

    fun poke() {
        hidden = false
        pulse++
    }
}

val LocalDockReveal = staticCompositionLocalOf { DockRevealState() }

@Composable
fun rememberDockRevealState(): DockRevealState = remember { DockRevealState() }

/**
 * 点一下屏幕就把收起的胶囊叫回来。
 *
 * 用 Initial 阶段「看一眼」指针事件，不消费、不拦截——滚动、点击、拖拽全部照旧，
 * 只是顺手把读秒归零并把胶囊滑回来。
 */
fun Modifier.dockRevealOnTouch(state: DockRevealState): Modifier = this.pointerInput(state) {
    awaitPointerEventScope {
        while (true) {
            val event = awaitPointerEvent(PointerEventPass.Initial)
            if (event.type == PointerEventType.Press) state.poke()
        }
    }
}
