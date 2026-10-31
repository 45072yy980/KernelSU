package me.diksu.ultra.foxsu.ui.util

import android.content.ComponentName
import android.content.pm.PackageManager
import me.diksu.ultra.R
import me.diksu.ultra.foxsu.data.settings.CustomizationStore
import me.diksu.ultra.ksuApp

/**
 * 桌面启动器图标切换。
 *
 * 原理：清单里为每个形象声明一个 `activity-alias`，全部指向 `MainActivity`，
 * 同一时刻只启用一个。切图标 = 启用目标别名 + 停用其余别名，
 * Activity 本身始终启用，深链与返回栈都不受影响。
 *
 * 关于「切了不生效」：
 * 1. 旧实现把结果写进 `launcher_icon`，但读回时错拿了 `icon_style`（存的是 100..103 的
 *    图标形状），`takeIf { it in 0..4 }` 永远不成立 → 每次启动都回落 0。
 * 2. 系统在应用更新 / 被清理后会把别名状态还原成清单默认值，桌面于是又变回旧图标。
 *    为此新增 [sync]，每次进程启动时把「存储里的选择」和「系统里真正启用的别名」对一次账。
 * 3. 部分桌面（HyperOS / ColorOS 等）会缓存图标，切换后要等一次组件变更才会刷新，
 *    这里在启停之后再对目标别名显式 set 一次，制造这次回调。
 */
object LauncherIconManager {

    const val STYLE_FOXSU = 0
    const val STYLE_KERNELSU = 5

    /** 一套启动器图标：id 存进设置，alias 是清单组件，preview 是应用内预览图。 */
    data class Style(
        val id: Int,
        val label: String,
        val alias: String,
        val preview: Int,
    )

    private const val PKG = "me.diksu.ultra.foxsu.ui"

    /** 五套新狐狸 + 原始 KernelSU 图标。顺序即定制页的展示顺序。 */
    val styles: List<Style> = listOf(
        Style(0, "环尾", "$PKG.LauncherDefault", R.drawable.fx_fox_icon_1),
        Style(1, "圆环", "$PKG.LauncherFox2", R.drawable.fx_fox_icon_2),
        Style(2, "扬尾", "$PKG.LauncherFox3", R.drawable.fx_fox_icon_3),
        Style(3, "招手", "$PKG.LauncherFox4", R.drawable.fx_fox_icon_4),
        Style(4, "卷尾", "$PKG.LauncherFox5", R.drawable.fx_fox_icon_5),
        Style(5, "KernelSU", "$PKG.LauncherKernelSU", R.drawable.fx_fox_icon_1),
    )

    fun styleOf(id: Int): Style = styles.firstOrNull { it.id == id } ?: styles.first()

    fun previewOf(id: Int): Int = styleOf(id).preview

    /**
     * 切换图标，返回是否真的切成功。
     *
     * 失败不再被 try/catch 悄悄吞掉：以前失败时界面显示已切换、桌面纹丝不动，
     * 用户看到的就是「不跟随」。
     */
    fun apply(style: Int): Boolean = applyAlias(style)

    private fun applyAlias(style: Int): Boolean {
        val pm = ksuApp.packageManager
        val target = styleOf(style)
        val all = styles.map { it.alias }.distinct()

        // 先启用目标，保证任何时刻都有一个入口，桌面不会短暂「消失」
        if (!setEnabled(pm, target.alias, true)) return false
        all.filter { it != target.alias }.forEach { setEnabled(pm, it, false) }

        // 再 set 一次，制造一次组件变更让缓存图标的桌面刷新条目
        setEnabled(pm, target.alias, true)
        return isEnabled(pm, target.alias)
    }

    /** 把设置里保存的选择同步到系统；返回当前实际生效的 id。 */
    fun sync(): Int {
        val wanted = CustomizationStore.current.launcherIcon
        val pm = ksuApp.packageManager
        val target = styleOf(wanted)
        val exactlyOne = styles.count { isEnabled(pm, it.alias) } == 1
        if (!isEnabled(pm, target.alias) || !exactlyOne) applyAlias(wanted)
        return wanted
    }

    /** 当前系统里真正启用的别名对应的 id（用于界面显示真实状态）。 */
    fun currentId(): Int = currentAliasId() ?: 0

    private fun currentAliasId(): Int? {
        val pm = ksuApp.packageManager
        return styles.firstOrNull { isEnabled(pm, it.alias) }?.id
    }

    private fun setEnabled(pm: PackageManager, alias: String, enabled: Boolean): Boolean =
        runCatching {
            pm.setComponentEnabledSetting(
                ComponentName(ksuApp.packageName, alias),
                if (enabled) PackageManager.COMPONENT_ENABLED_STATE_ENABLED
                else PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                PackageManager.DONT_KILL_APP,
            )
            true
        }.getOrDefault(false)

    private fun isEnabled(pm: PackageManager, alias: String): Boolean = runCatching {
        pm.getComponentEnabledSetting(ComponentName(ksuApp.packageName, alias)) ==
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED
    }.getOrDefault(false)
}
