package me.diksu.ultra.foxsu.ui.screen.home

import androidx.compose.runtime.Immutable
import me.diksu.ultra.KernelVersion
import me.diksu.ultra.foxsu.ui.util.module.LatestVersionInfo

@Immutable
data class HomeUiState(
    val kernelVersion: KernelVersion,
    val ksuVersion: Int?,
    val managerUAPIVersion: Int,
    val kernelUAPIVersion: Int?,
    val lkmMode: Boolean?,
    val isLkmBundled: Boolean,
    val isManager: Boolean,
    val isManagerPrBuild: Boolean,
    val isKernelPrBuild: Boolean,
    val requiresNewKernel: Boolean,
    val requiresNewManager: Boolean,
    val isRootAvailable: Boolean,
    val isSafeMode: Boolean,
    val isLateLoadMode: Boolean,
    val checkUpdateEnabled: Boolean,
    val latestVersionInfo: LatestVersionInfo,
    val currentManagerVersionCode: Long,
    val systemInfo: SystemInfo,
) {
    val isSELinuxPermissive: Boolean
        get() = systemInfo.selinuxStatus == "Permissive"

    val showGkiWarning: Boolean
        get() = ksuVersion != null && lkmMode == false

    /**
     * 「内核模块需要更新」只在运行中的模块比管理器旧时成立。
     *
     * 原来写的是 `!=`，把「内核比管理器新」也算成需要更新——用户刷了更新的 LKM、
     * 管理器还没升级时也会被催着刷写，越刷越乱。真正常见的误报原因在版本号本身：
     * 内核侧是 30000+git 提交数、管理器侧是 VERSION 文件的 3.0.52→30052，两边
     * 永远不相等，于是怎么样都显示「需要更新」。版本号已在 kernel/Kbuild 里统一，
     * 这里再把判断收紧成「只认落后」。
     */
    val showLkmUpdate: Boolean
        get() = isManager &&
                lkmMode == true &&
                isLkmBundled &&
                ksuVersion != null &&
                ksuVersion.toLong() < currentManagerVersionCode &&
                !requiresNewKernel &&
                !requiresNewManager

    // Jailbreak mode runs on locked bootloaders, so flashing a boot image would brick the device.
    val canInstallKernelUpdate: Boolean
        get() = lkmMode == true && !isLateLoadMode

    val showCustomLkmBadge: Boolean
        get() = lkmMode == true && !isLkmBundled

    val showRootWarning: Boolean
        get() = ksuVersion != null && !isRootAvailable

    val showManagerPrBuildWarning: Boolean
        get() = isManager && isManagerPrBuild

    val showKernelPrBuildWarning: Boolean
        get() = isManager && !isManagerPrBuild && isKernelPrBuild

    val hasUpdate: Boolean
        get() = latestVersionInfo.versionCode > currentManagerVersionCode
}

@Immutable
data class HomeActions(
    val onInstallClick: () -> Unit,
    val onOpenUrl: (String) -> Unit,
    val onJailbreakClick: () -> Unit = {},
    /** 真实检查新版本：命中 GitHub Releases，有更新则弹更新说明并可跳转下载。 */
    val onCheckUpdate: () -> Unit = {},
    /** 刷新镜像：直接执行 ksud boot-patch -f 重新刷写当前分区（真实动作）。 */
    val onRefreshImage: () -> Unit = {},
)
