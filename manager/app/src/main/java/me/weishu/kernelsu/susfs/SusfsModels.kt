package me.weishu.kernelsu.susfs

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
)
