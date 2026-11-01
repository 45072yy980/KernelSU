package me.diksu.ultra.data.susfs

/**
 * 失败原因分类。
 *
 * 内核与 ksud 的错误是一串英文/Errno，直接摊给用户没有意义；先归类，再由界面翻译成
 * 一句能照做的话。
 */
enum class SusfsErrorKind {
    /** 内核没有这个能力（编译期没开）。 */
    Capability,
    /** 静态 kstat 的参数有问题。 */
    StaticKstat,
    /** 本次开机改不了，重启后才生效。 */
    PendingReboot,
    /** 伪 uname 相关。 */
    Uts,
    /** 路径归属/跨文件系统挂载的问题。 */
    Ownership,
    /** 忙，稍后重试。 */
    Busy,
    /** 超时。 */
    Timeout,
    /** 参数不合法（重复、过长、非法字符）。 */
    Validation,
    /** 没有 root。 */
    Root,
    Unknown,
}

fun susfsErrorKind(message: String): SusfsErrorKind {
    val lower = message.lowercase()
    return when {
        "capability" in lower || "not supported" in lower || "unsupport" in lower -> SusfsErrorKind.Capability
        "static kstat" in lower -> SusfsErrorKind.StaticKstat
        "reboot" in lower || "empty rule table" in lower -> SusfsErrorKind.PendingReboot
        "uts" in lower || "uname" in lower -> SusfsErrorKind.Uts
        "ownership" in lower || "cross" in lower || "switch" in lower -> SusfsErrorKind.Ownership
        "busy" in lower -> SusfsErrorKind.Busy
        "timeout" in lower || "timed out" in lower -> SusfsErrorKind.Timeout
        "invalid" in lower || "duplicate" in lower || "too many" in lower ||
            "exists" in lower || "returned error -22" in lower -> SusfsErrorKind.Validation
        "root" in lower || "permission denied" in lower -> SusfsErrorKind.Root
        else -> SusfsErrorKind.Unknown
    }
}

/**
 * 一次应用的结果。
 *
 * 四类分开记：成功、失败（带原因）、跳过（重复等不必失败的）、待重启（内核没有删除接口，
 * 只能等下次开机不再重放）。以前只有"成功/失败"两档，把"等重启"也报成失败，用户会以为坏了。
 */
data class SusfsApplyReport(
    val applied: List<String> = emptyList(),
    val failed: Map<String, String> = emptyMap(),
    val skipped: Map<String, String> = emptyMap(),
    val pendingReboot: List<String> = emptyList(),
) {
    val isEmpty: Boolean
        get() = applied.isEmpty() && failed.isEmpty() && skipped.isEmpty() && pendingReboot.isEmpty()

    val isSuccess: Boolean get() = failed.isEmpty()

    /** 一行汇总，界面直接显示。 */
    fun summary(): String = buildString {
        append("成功 ${applied.size}")
        if (skipped.isNotEmpty()) append("，跳过 ${skipped.size}")
        if (failed.isNotEmpty()) append("，失败 ${failed.size}")
        if (pendingReboot.isNotEmpty()) append("，待重启 ${pendingReboot.size}")
    }
}

/** 把命令输出归一成一句可显示的原因。 */
internal fun commandReason(result: SusfsCommands.Result): String =
    result.output.ifBlank { "命令失败但没有输出" }.lineSequence().first().trim().take(200)
