package me.diksu.ultra.ui.capability

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.res.stringResource
import me.diksu.ultra.Natives
import me.diksu.ultra.R

/**
 * 一级导航的入口。顺序与 Pager 的页序一致：0 首页 / 1 超级用户 / 2 模块 / 3 设置。
 *
 * 4.1 的 [NavGate] 是"整条导航共用一个状态"，非 full-featured 时五个导航组件一起降级；
 * 4.2 起每个入口各判各的：首页与设置永远可用，只有依赖内核模块的入口才置灰。
 */
enum class NavEntry {
    Home,
    SuperUser,
    Module,
    Setting;

    /** 只有依赖内核模块的入口才需要 full-featured；首页与设置任何情况下都能进。 */
    val requiresFullFeatured: Boolean
        get() = this == SuperUser || this == Module
}

/**
 * 一级导航的可用性判定。
 *
 * 3.x 的五个导航组件都是同一句话：`if (!Natives.isFullFeatured()) return` ——
 * 内核模块没加载时整条导航直接消失，用户只剩首页一张"未安装"卡片，既不知道
 * 为什么，也不知道该去哪里处理。
 *
 * 4.1 改成"入口常驻、不可用时置灰"：导航照常显示，点进去给出**原因**和
 * **恢复入口**。这里只负责算原因，怎么画交给各皮肤。
 */
data class NavGate(
    val available: Boolean,
    /** 弹窗标题，例如"尚未提权"。 */
    val title: String,
    /** 为什么不可用，以及重启/提权后会发生什么。 */
    val reason: String,
    /** 恢复入口的按钮文案。 */
    val action: String,
) {
    companion object {
        val Open = NavGate(true, "", "", "")
    }
}

/**
 * 某个入口的可用性。
 *
 * 判定顺序是有讲究的：安全模式优先于越狱未提权（安全模式下就算提权也白搭），
 * 越狱未提权优先于"未加载"（越狱模式本来就没有内核模块，说"未加载"会误导）。
 */
@Composable
fun rememberNavGate(entry: NavEntry): NavGate {
    if (!entry.requiresFullFeatured || Natives.isFullFeatured()) return NavGate.Open

    if (Natives.isSafeMode) {
        return NavGate(
            available = false,
            title = stringResource(R.string.nav_gate_safe_mode_title),
            reason = stringResource(R.string.nav_gate_safe_mode_reason),
            action = stringResource(R.string.nav_gate_safe_mode_action),
        )
    }
    if (Natives.isLateLoadMode) {
        return NavGate(
            available = false,
            title = stringResource(R.string.nav_gate_late_load_title),
            reason = stringResource(R.string.nav_gate_late_load_reason),
            action = stringResource(R.string.nav_gate_late_load_action),
        )
    }
    return NavGate(
        available = false,
        title = stringResource(R.string.nav_gate_not_loaded_title),
        reason = stringResource(R.string.nav_gate_not_loaded_reason),
        action = stringResource(R.string.nav_gate_not_loaded_action),
    )
}

/**
 * 一次性取到全部入口的状态，按 [NavEntry] 索引。
 *
 * 导航组件一次要渲染四个入口，逐项调用 [rememberNavGate] 会重复做 JNI 判定；
 * 这里在顶部取一次，条目再按自己的 [NavEntry] 查表。
 */
@Composable
fun rememberNavGates(): Map<NavEntry, NavGate> {
    val home = rememberNavGate(NavEntry.Home)
    val superUser = rememberNavGate(NavEntry.SuperUser)
    val module = rememberNavGate(NavEntry.Module)
    val setting = rememberNavGate(NavEntry.Setting)
    return remember(home, superUser, module, setting) {
        mapOf(
            NavEntry.Home to home,
            NavEntry.SuperUser to superUser,
            NavEntry.Module to module,
            NavEntry.Setting to setting,
        )
    }
}
