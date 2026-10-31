package me.diksu.ultra.foxsu.ui.navigation3

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import kotlinx.serialization.Serializable
import me.diksu.ultra.foxsu.ui.screen.flash.FlashIt
import me.diksu.ultra.foxsu.ui.screen.modulerepo.RepoModuleArg
import me.diksu.ultra.foxsu.ui.screen.scriptrun.ScriptRunArg
import me.diksu.ultra.foxsu.ui.util.FlashItSerializer
import me.diksu.ultra.foxsu.ui.util.RepoModuleArgSerializer
import me.diksu.ultra.foxsu.ui.util.ScriptRunArgSerializer
import me.diksu.ultra.foxsu.ui.util.TemplateInfoSerializer
import me.diksu.ultra.foxsu.ui.viewmodel.TemplateViewModel
import top.yukonga.miuix.kmp.nav.core.NavKey

/**
 * Type-safe navigation keys for miuix-nav.
 * Each destination is a NavKey (data object/data class) and can be saved/restored in the back stack.
 */
@Serializable
sealed interface Route : NavKey, Parcelable {
    @Parcelize
    @Serializable
    data object Main : Route

    @Parcelize
    @Serializable
    data object Home : Route

    @Parcelize
    @Serializable
    data object SuperUser : Route

    @Parcelize
    @Serializable
    data object Module : Route

    @Parcelize
    @Serializable
    data object Settings : Route

    @Parcelize
    @Serializable
    data object About : Route

    @Parcelize
    @Serializable
    data object Author : Route

    /** 支持 FoxSu：赞赏码与免责声明。 */
    @Parcelize
    @Serializable
    data object Donate : Route

    /** 开源致谢：按「根 / 界面 / 能力 / 数据」四组列出全部上游项目与许可证。 */
    @Parcelize
    @Serializable
    data object Credits : Route

    /** 底栏特效选择器：栏内 20 套 + 外圈 20 套，可叠加。 */
    @Parcelize
    @Serializable
    data object DockFx : Route

    /** 底栏图标风格选择器：20 套造型语言，0 = 原生。 */
    @Parcelize
    @Serializable
    data object DockIcons : Route

    @Parcelize
    @Serializable
    data object Sulog : Route

    /** SusFs 管理：隐藏路径 / 隐藏挂载 / 伪装 / 开机自动应用（内核由用户自行刷入）。 */
    @Parcelize
    @Serializable
    data object SusFs : Route

    /** KPM 模块管理：加载 / 卸载 / 控制内核补丁模块（内置 kpatch，需 CONFIG_KPM=y 内核）。 */
    @Parcelize
    @Serializable
    data object Kpm : Route

    /** 去广告：systemless hosts（开源规则源 → KSU 模块 → 开机挂载）。 */
    @Parcelize
    @Serializable
    data object AdBlock : Route

    @Parcelize
    @Serializable
    data object ColorPalette : Route

    @Parcelize
    @Serializable
    data object Customization : Route

    /** 内核元模块：内置 Magic Mount-rs 的安装 / 启停 / 配置页。 */
    @Parcelize
    @Serializable
    data object MetaModule : Route

    /** 内置模块总览：随 APK 打包的 Magic Mount-rs / Oh My Keymint / Zygisk Next。 */
    @Parcelize
    @Serializable
    data object Builtins : Route

    /** 单颗内置模块的详情：装卸、启停、打开模块自带界面。 */
    @Parcelize
    @Serializable
    data class BuiltinDetail(val id: String) : Route

    @Parcelize
    @Serializable
    data object AppProfileTemplate : Route

    @Parcelize
    @Serializable
    data class TemplateEditor(
        @Serializable(with = TemplateInfoSerializer::class) val template: TemplateViewModel.TemplateInfo,
        val readOnly: Boolean
    ) : Route

    @Parcelize
    @Serializable
    data class AppProfile(val uid: Int) : Route

    @Parcelize
    @Serializable
    data object Install : Route

    @Parcelize
    @Serializable
    data class ModuleRepoDetail(@Serializable(with = RepoModuleArgSerializer::class) val module: RepoModuleArg) : Route

    @Parcelize
    @Serializable
    data object ModuleRepo : Route

    @Parcelize
    @Serializable
    data class Flash(@Serializable(with = FlashItSerializer::class) val flashIt: FlashIt) : Route

    @Parcelize
    @Serializable
    data class ExecuteModuleAction(val moduleId: String, val fromShortcut: Boolean = false) : Route

    @Parcelize
    @Serializable
    data class ScriptRun(@Serializable(with = ScriptRunArgSerializer::class) val arg: ScriptRunArg) : Route
}
