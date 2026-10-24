package me.diksu.ultra.ui.capability

import androidx.compose.runtime.Composable
import me.diksu.ultra.Natives

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
 * 计算当前设备的导航可用性。
 *
 * 判定顺序是有讲究的：安全模式优先于越狱未提权（安全模式下就算提权也白搭），
 * 越狱未提权优先于"未加载"（越狱模式本来就没有内核模块，说"未加载"会误导）。
 */
@Composable
fun rememberNavGate(): NavGate {
    if (Natives.isFullFeatured()) return NavGate.Open

    if (Natives.isSafeMode) {
        return NavGate(
            available = false,
            title = "安全模式",
            reason = "设备以安全模式启动，内核模块与模块管理暂不可用。重启一次即可恢复正常。",
            action = "前往首页",
        )
    }
    if (Natives.isLateLoadMode) {
        return NavGate(
            available = false,
            title = "尚未提权",
            reason = "越狱模式每次完整重启后都没有 root，需要先在首页完成一次提权，" +
                "超级用户与模块管理才会恢复。",
            action = "前往首页提权",
        )
    }
    return NavGate(
        available = false,
        title = "内核模块未加载",
        reason = "管理器还没有被内核认领，超级用户与模块管理暂不可用。请到首页完成安装或提权。",
        action = "前往首页处理",
    )
}
