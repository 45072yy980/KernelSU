package me.weishu.kernelsu.ui.screen.home

import androidx.compose.runtime.Immutable
import me.weishu.kernelsu.KernelVersion
import me.weishu.kernelsu.ui.util.module.LatestVersionInfo

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
    /** The module providing Zygisk, when one is installed and enabled. Null hides the row. */
    val zygiskImplementation: String? = null,
    /** The installed metamodule, when there is one. Null hides the row. */
    val metaModuleImplementation: String? = null,
    /** The module providing an Xposed framework, when one is installed and enabled. */
    val xposedImplementation: String? = null,
    /**
     * The SuSFS version the kernel reports, when something answers the supercall.
     *
     * Null (or blank) hides the row. This is the only status row that is read
     * back through `ksud` rather than from a module file, because SuSFS lives in
     * the kernel — including when it arrived as the standalone LKM after boot.
     */
    val susfsVersion: String? = null,
) {
    val isSELinuxPermissive: Boolean
        get() = systemInfo.selinuxStatus == "Permissive"

    val showGkiWarning: Boolean
        get() = ksuVersion != null && lkmMode == false

    val showLkmUpdate: Boolean
        get() = isManager &&
                lkmMode == true &&
                isLkmBundled &&
                ksuVersion?.toLong() != currentManagerVersionCode &&
                !requiresNewKernel &&
                !requiresNewManager

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
    // Fires when the device is not rooted at all yet (so a plain late-load would
    // have no ksud to talk to).
    val onJailbreakExploitClick: () -> Unit = {},
    // Clicking the "not installed" card itself: offer the exploit jailbreak or the
    // ordinary install flow.
    val onNotInstalledClick: () -> Unit = {},
)
