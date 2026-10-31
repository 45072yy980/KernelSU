package me.diksu.ultra.foxsu.ui.component.bottombar

import androidx.compose.foundation.pager.PagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import me.diksu.ultra.foxsu.data.settings.CustomizationStore
import me.diksu.ultra.foxsu.ui.theme.LocalEnableFloatingBottomBar
import me.diksu.ultra.foxsu.ui.LocalMainPagerState
import me.diksu.ultra.foxsu.ui.LocalUiMode
import me.diksu.ultra.foxsu.ui.UiMode
import me.diksu.ultra.foxsu.ui.util.shouldShowSplitPane
import top.yukonga.miuix.kmp.blur.Backdrop
import top.yukonga.miuix.kmp.blur.LayerBackdrop
import top.yukonga.miuix.kmp.utils.springAnimateToPage

/**
 * 底栏横滑是否放行。
 *
 * 底栏从 ThemeDockBar 一路嵌到 DockKitBar，三层回调签名里都没有这个位子；
 * 而它是「滑动切换 Tab」总开关的一部分——总开关选「关」时底栏也必须跟着哑掉，
 * 否则设置写着关、手指在胶囊上照样能翻页。用 CompositionLocal 直达最内层。
 */
val LocalDockSwipeEnabled = androidx.compose.runtime.compositionLocalOf { true }

class MainPagerState(
    val pagerState: PagerState,
    private val coroutineScope: CoroutineScope,
    private val animatePageChanges: Boolean,
) {
    var selectedPage by mutableIntStateOf(pagerState.currentPage)
        private set

    /**
     * 最近一次切换的方向：+1 = 往右（点右边那格），-1 = 往左。
     *
     * 只有「水滴」动效需要它——页面内容要沿着手指点击的方向轻推出去再淡入，
     * 反向推会让人觉得页面在跟自己作对。0 表示还没切换过。
     */
    var lastDirection by mutableIntStateOf(0)
        private set

    var isNavigating by mutableStateOf(false)
        private set

    private var navJob: Job? = null

    fun animateToPage(targetIndex: Int) {
        if (targetIndex == selectedPage) return

        navJob?.cancel()

        lastDirection = if (targetIndex > selectedPage) 1 else -1
        selectedPage = targetIndex
        isNavigating = true

        navJob = coroutineScope.launch {
            val myJob = coroutineContext.job
            try {
                if (animatePageChanges) {
                    pagerState.springAnimateToPage(targetIndex)
                } else {
                    pagerState.scrollToPage(targetIndex)
                }
            } finally {
                if (navJob == myJob) {
                    isNavigating = false
                    if (pagerState.currentPage != targetIndex) {
                        selectedPage = pagerState.currentPage
                    }
                }
            }
        }
    }

    fun syncPage() {
        if (!isNavigating && selectedPage != pagerState.currentPage) {
            selectedPage = pagerState.currentPage
        }
    }
}

@Composable
fun rememberMainPagerState(
    pagerState: PagerState,
    coroutineScope: CoroutineScope = rememberCoroutineScope(),
    animatePageChanges: Boolean = true,
): MainPagerState {
    return remember(pagerState, coroutineScope, animatePageChanges) {
        MainPagerState(pagerState, coroutineScope, animatePageChanges)
    }
}

@Immutable
data class NavigationBadgeState(
    val superuserCount: Int = 0,
    val moduleEnabledCount: Int = 0,
    val moduleUpdatableCount: Int = 0,
)

internal enum class BadgeTone { Alert, Accent }

@Immutable
internal data class NavBadge(val count: Int, val tone: BadgeTone)

internal fun badgeFor(index: Int, state: NavigationBadgeState): NavBadge? = when (index) {
    BottomBarDestination.SuperUser.ordinal ->
        state.superuserCount.takeIf { it > 0 }?.let { NavBadge(it, BadgeTone.Accent) }

    BottomBarDestination.Module.ordinal -> when {
        state.moduleUpdatableCount > 0 -> NavBadge(state.moduleUpdatableCount, BadgeTone.Alert)
        state.moduleEnabledCount > 0 -> NavBadge(state.moduleEnabledCount, BadgeTone.Accent)
        else -> null
    }

    else -> null
}

@Composable
fun useNavigationRail(enableFloatingBottomBar: Boolean): Boolean {
    return shouldShowSplitPane() && !(LocalUiMode.current == UiMode.Miuix && enableFloatingBottomBar)
}

@Composable
fun BottomBar(
    blurBackdrop: LayerBackdrop?,
    backdrop: Backdrop,
    navigationBadge: NavigationBadgeState,
    modifier: Modifier = Modifier,
    onOpenDonate: () -> Unit = {},
) {
    // 传统底栏开关优先：开 = 通栏传统底栏，关 = 胶囊（默认关）
    if (CustomizationStore.current.classicDock) {
        ClassicDockBar(AgDockThemes.redesign(), navigationBadge, modifier)
        return
    }

    // Editorial 默认保留自己的「纸面」通栏底栏：胶囊是 Redesign 的语言，
    // 套在杂志版式上会把栏高、描边、指示器全部搞乱（可在定制里手动打开胶囊）。
    if (!useThemeDock()) {
        val mainState = LocalMainPagerState.current
        me.diksu.ultra.foxsu.ui.screen.editorial.EdTabBar(
            currentTab = mainState.selectedPage,
            badge = navigationBadge,
            onOpenTab = { page -> mainState.animateToPage(page) },
        )
        return
    }

    // 胶囊配色统一走 Redesign 的晨曦玻璃：胶囊是「全局样式」，跨皮肤只该换内容，
    // 不该换底色。之前按皮肤取色会让 Nocturne 拿到近黑的 OLED 胶囊，
    // 切到浅色版面时就是一条突兀的黑条（用户报的「胶囊底色是黑的」）。
    ThemeDockBar(AgDockThemes.redesign(), navigationBadge, modifier, onOpenDonate)
}

@Composable
fun SideRail(
    navigationBadge: NavigationBadgeState,
    modifier: Modifier = Modifier,
) {
    when (LocalUiMode.current) {
        UiMode.Miuix -> NavigationRailMiuix(navigationBadge, modifier)
        UiMode.Material -> NavigationRailMaterial(navigationBadge, modifier)
        UiMode.Fresh -> NavigationRailMaterial(navigationBadge, modifier)
        UiMode.Glass -> NavigationRailMaterial(navigationBadge, modifier)
        UiMode.Nocturne -> NavigationRailMaterial(navigationBadge, modifier)
    }
}
