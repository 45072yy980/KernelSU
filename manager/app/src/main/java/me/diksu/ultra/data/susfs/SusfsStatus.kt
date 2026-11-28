package me.diksu.ultra.data.susfs

/**
 * 内核如何回答 SuSFS 超级调用。
 *
 * [features] 是内核报告的 `CONFIG_KSU_SUSFS_*` 键，原样保留；显示名由界面层映射，
 * 免得到处引 `R`。
 */
data class SusfsStatus(
    val loaded: Boolean = false,
    val version: String = "",
    val variant: String = "",
    val features: List<String> = emptyList(),
) {
    /** 能写才有意义：没加载就别让用户点开关。 */
    val writable: Boolean get() = loaded

    /**
     * 内核里跑的是独立的 `susfs_guard_lkm`，而不是内建进内核的那份。
     *
     * 两种实现说同一套 reboot 超级调用，但 LKM 多出一些能力（`susfs_map` /
     * `susfs_opath` 节点、priority-uid 过滤），并且它的 `/proc` 节点受
     * `expose_proc` 模块参数控制 —— 所以界面需要能区分，才能给出对得上的提示。
     */
    val isStandaloneLkm: Boolean
        get() = variant.equals("GKI-LKM", ignoreCase = true) ||
            variant.contains("LKM", ignoreCase = true)
}

/**
 * 模块发布的 `/proc/susfs_*` 节点原文。
 *
 * 格式由内核决定，这里只搬运不加工 —— 重新排版只会随上游变更而失效。
 *
 * [susfsMap] / [susfsOpath] 是独立 LKM（susfs4ksu-for-lkm）比内建实现多出来的两个
 * 节点：前者按 inode 隐藏 mmap 的 real file，后者是 `O_PATH` 打开隐藏路径的自检
 * 计数。内建实现没有它们时读出来是空串，界面按「这一项不存在」处理即可。
 */
data class SusfsProcSnapshot(
    val path: String = "",
    val hideMounts: String = "",
    val hideModules: String = "",
    val kstat: String = "",
    val openRedirect: String = "",
    val avcSpoof: String = "",
    val enableLog: String = "",
    val susfsMap: String = "",
    val susfsOpath: String = "",
) {
    val isEmpty: Boolean
        get() = listOf(
            path, hideMounts, hideModules, kstat, openRedirect, avcSpoof, enableLog,
            susfsMap, susfsOpath,
        ).all { it.isBlank() }
}

/**
 * SuSFS 能编译进去的全部能力，顺序即界面顺序。
 *
 * 与上游用户态工具、独立 `susfs_guard_lkm` 模块打印的键一致，所以内建 / LKM 两种模式下
 * 都适用。
 */
object SusfsFeatureCatalog {
    val ALL: List<String> = listOf(
        "CONFIG_KSU_SUSFS_SUS_PATH",
        "CONFIG_KSU_SUSFS_SUS_MOUNT",
        "CONFIG_KSU_SUSFS_SUS_KSTAT",
        "CONFIG_KSU_SUSFS_SUS_MAP",
        "CONFIG_KSU_SUSFS_SPOOF_UNAME",
        "CONFIG_KSU_SUSFS_SPOOF_CMDLINE_OR_BOOTCONFIG",
        "CONFIG_KSU_SUSFS_OPEN_REDIRECT",
        "CONFIG_KSU_SUSFS_ENABLE_LOG",
        "CONFIG_KSU_SUSFS_HIDE_KSU_SUSFS_SYMBOLS",
    )

    /** 界面按这张表逐行显示，没报告的键就是没编译进去。 */
    fun resolve(reported: List<String>): List<Pair<String, Boolean>> {
        val present = reported.map { it.trim() }.filter { it.isNotEmpty() }.toSet()
        return ALL.map { it to (it in present) }
    }
}
