package me.diksu.ultra.foxsu.ui.component.bottombar

import androidx.compose.runtime.Composable
import me.diksu.ultra.foxsu.data.settings.CustomizationStore
import me.diksu.ultra.foxsu.ui.LocalUiMode
import me.diksu.ultra.foxsu.ui.UiMode

/**
 * 当前皮肤是否使用 Redesign 的胶囊底栏。
 *
 * 胶囊（Dock）是 Redesign 的设计语言。Editorial 是杂志版式，自带通栏「纸面」tab 条，
 * 强行套胶囊会破坏版式，所以默认保留原生底栏；想统一观感时可在
 * 定制 → 底部导航栏 → 「Editorial 使用胶囊底栏」里打开。
 */
@Composable
fun useThemeDock(): Boolean = when (LocalUiMode.current) {
    UiMode.Material -> CustomizationStore.current.dockOnEditorial
    else -> true
}

/**
 * 是否处于「侧边抽屉」形态（dockStyle = v5）。
 * 页面留白必须跟着这个判断走：底栏没生效时还留 96dp 会凭空多出一条空隙。
 */
@Composable
fun useSideDock(): Boolean {
    val custom = CustomizationStore.current
    // 传统底栏是贴底通栏，和「侧边抽屉」互斥
    return !custom.classicDock &&
        useThemeDock() &&
        DockStyle.fromRaw(custom.dockStyle) == DockStyle.V5
}
