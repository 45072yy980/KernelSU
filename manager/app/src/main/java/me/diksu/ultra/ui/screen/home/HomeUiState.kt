package me.diksu.ultra.ui.screen.home

import androidx.compose.runtime.Immutable
import me.diksu.ultra.KernelVersion
import me.diksu.ultra.ui.util.module.LatestVersionInfo

/** Progress of the "manager and ksud disagree, update ksud" action. */
enum class KsudUpdateStatus { Idle, Running, Succeeded, Failed }

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
    /** State of the "update ksud" action offered by the status card. */
    val ksudUpdate: KsudUpdateStatus = KsudUpdateStatus.Idle,
) {
    val isSELinuxPermissive: Boolean
        get() = systemInfo.selinuxStatus == "Permissive"

    val showGkiWarning: Boolean
        get() = ksuVersion != null && lkmMode == false

    /**
     * The manager and the running ksud disagree about the version.
     *
     * Distinct from [showLkmUpdate]: that one also requires the LKM to be bundled and both
     * UAPI versions to line up, because it offers a kernel flash. This one only means the
     * userspace binary is stale, so the status card may offer `ksud install` instead.
     */
    val hasVersionMismatch: Boolean
        get() = isManager &&
                ksuVersion != null &&
                ksuVersion.toLong() != currentManagerVersionCode

    val showLkmUpdate: Boolean
        get() = isManager &&
                lkmMode == true &&
                isLkmBundled &&
                ksuVersion?.toLong() != currentManagerVersionCode &&
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
    // Fires when the device is not rooted at all yet (so a plain late-load would
    // have no ksud to talk to).
    val onJailbreakExploitClick: () -> Unit = {},
    // Clicking the "not installed" card itself: offer the exploit jailbreak or the
    // ordinary install flow.
    val onNotInstalledClick: () -> Unit = {},
    // Re-install the ksud userspace binary (see HomeViewModel.updateKsud).
    val onUpdateKsud: () -> Unit = {},
    // Leaves the "updated" / "failed" state so the next card tap starts clean.
    val onDismissKsudUpdate: () -> Unit = {},
)
