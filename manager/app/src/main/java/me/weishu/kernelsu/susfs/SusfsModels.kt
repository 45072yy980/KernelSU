package me.weishu.kernelsu.susfs

import me.weishu.kernelsu.R

/**
 * One entry in the SuSFS hidden-path list.
 *
 * `flags` is what the kernel reports for the entry; only its presence matters
 * to the UI today, so it is carried through unread.
 */
data class SusfsPath(
    val path: String,
    val flags: Int = 0,
)

/**
 * What the running kernel says about SuSFS:
 *
 *  - [loaded]  — something answers the supercall at all
 *  - [version] — the version string it reports (e.g. `v2.3.0`)
 *  - [variant] — the build variant (e.g. `gki`)
 *  - [features] — the feature names compiled in, already split on commas
 */
data class SusfsStatus(
    val loaded: Boolean = false,
    val version: String = "",
    val variant: String = "",
    val features: List<String> = emptyList(),
)

/**
 * The raw text behind the `/proc/susfs_*` nodes the module exposes.
 *
 * These are read straight from procfs and shown verbatim: the module owns
 * their format, and re-formatting them here would only go stale.
 */
data class SusfsProcSnapshot(
    val path: String = "",
    val hideMounts: String = "",
    val hideModules: String = "",
    val kstat: String = "",
    val openRedirect: String = "",
    val avcSpoof: String = "",
    val enableLog: String = "",
)

/** Everything the SuSFS screen shows in one value. */
data class SusfsUiState(
    val isLoading: Boolean = true,
    val status: SusfsStatus = SusfsStatus(),
    val proc: SusfsProcSnapshot = SusfsProcSnapshot(),
    val logEnabled: Boolean = false,
    val avcLogSpoofing: Boolean = false,
    val hideSusMntsForNonSuProcs: Boolean = false,
    val unameRelease: String = "",
    val unameVersion: String = "",
    val error: String? = null,
    /** Last command's output, kept for the screen's log panel. */
    val lastOutput: String = "",
    /** The known features, each with whether the kernel reported it. */
    val features: List<SusfsFeature> = emptyList(),
)

/**
 * One entry of the kernel's enabled-feature list.
 *
 * [configKey] is what the kernel prints (`CONFIG_KSU_SUSFS_*`); the UI maps it
 * to a human name. [enabled] is whether that key came back in the list — a
 * feature that is compiled out, or whose build the module does not report, is
 * simply absent, which reads as disabled.
 */
data class SusfsFeature(
    val configKey: String,
    val nameRes: Int,
    val enabled: Boolean,
)

/**
 * The features SuSFS can be built with, in the order the panel lists them.
 *
 * Same set, and same `CONFIG_KSU_SUSFS_*` names, as the upstream userspace
 * tool reports — which is also what the standalone `susfs_guard_lkm` module
 * prints, so the list is correct in every mode.
 */
object SusfsFeatureCatalog {
    /** config key -> string resource holding its display name. */
    val ALL: List<Pair<String, Int>> = listOf(
        "CONFIG_KSU_SUSFS_SUS_PATH" to R.string.sus_path_feature_label,
        "CONFIG_KSU_SUSFS_SUS_MOUNT" to R.string.sus_mount_feature_label,
        "CONFIG_KSU_SUSFS_SUS_KSTAT" to R.string.sus_kstat_feature_label,
        "CONFIG_KSU_SUSFS_SUS_MAP" to R.string.sus_map_feature_label,
        "CONFIG_KSU_SUSFS_SPOOF_UNAME" to R.string.spoof_uname_feature_label,
        "CONFIG_KSU_SUSFS_SPOOF_CMDLINE_OR_BOOTCONFIG" to R.string.spoof_cmdline_feature_label,
        "CONFIG_KSU_SUSFS_OPEN_REDIRECT" to R.string.open_redirect_feature_label,
        "CONFIG_KSU_SUSFS_ENABLE_LOG" to R.string.enable_log_feature_label,
        "CONFIG_KSU_SUSFS_HIDE_KSU_SUSFS_SYMBOLS" to R.string.hide_symbols_feature_label,
    )

    /** Fold the raw key list the kernel reported into one row per known feature. */
    fun resolve(reported: List<String>): List<SusfsFeature> {
        val present = reported.map { it.trim() }.filter { it.isNotEmpty() }.toSet()
        return ALL.map { (key, nameRes) ->
            SusfsFeature(configKey = key, nameRes = nameRes, enabled = key in present)
        }
    }
}
