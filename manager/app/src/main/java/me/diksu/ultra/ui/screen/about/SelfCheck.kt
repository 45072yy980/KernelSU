package me.diksu.ultra.ui.screen.about

import androidx.compose.runtime.Immutable
import me.diksu.ultra.BuildConfig
import me.diksu.ultra.Natives
import me.diksu.ultra.ksuApp
import me.diksu.ultra.ui.screen.home.sameBuild
import me.diksu.ultra.ui.util.getRootShell
import me.diksu.ultra.ui.util.install
import me.diksu.ultra.data.repository.isPartitionGuardEnabled
import me.diksu.ultra.ui.util.probeInstalledKsudVersion
import me.diksu.ultra.ui.util.susfsVersion
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** 一条自检结果的状态。 */
enum class CheckStatus { Ok, Warn, Unknown }

/** 自检项，用来在界面上取标题文案。 */
enum class CheckKind { Kernel, Ksud, Link, Uapi, Guard, Susfs }

/** 能一键修掉的动作。修不了的项不带动作，只如实报告。 */
enum class CheckFix { ReinstallKsud, RebuildLink }

@Immutable
data class CheckRow(
    val kind: CheckKind,
    val status: CheckStatus,
    /** 具体数值或原因，直接给人看。 */
    val detail: String,
    val fix: CheckFix? = null,
)

/**
 * 越狱模式下的运行自检。
 *
 * 只检查"能判断且判断有意义"的几项：内核模块、ksud 用户态、软链、UAPI、分区守卫、SuSFS。
 * 内核模块旧了没法在应用内修（要重新注入），所以那项只报告；ksud 与软链能修，给按钮。
 */
suspend fun runSelfCheck(): List<CheckRow> = withContext(Dispatchers.IO) {
    val rows = mutableListOf<CheckRow>()

    // 1. 内核模块 vs 管理器
    val kernel = runCatching { Natives.version }.getOrDefault(0)
    val managerCode = BuildConfig.VERSION_CODE.toLong()
    rows += if (kernel > 0 && kernel.toLong() == managerCode) {
        CheckRow(CheckKind.Kernel, CheckStatus.Ok, "已注入 $kernel")
    } else if (kernel > 0) {
        CheckRow(
            CheckKind.Kernel,
            CheckStatus.Warn,
            "已注入 $kernel，管理器 $managerCode（需要重新注入才能对齐）",
        )
    } else {
        CheckRow(CheckKind.Kernel, CheckStatus.Unknown, "内核没有上报版本")
    }

    // 2. ksud 用户态
    val probe = runCatching { probeInstalledKsudVersion() }.getOrNull()
    rows += when {
        probe?.version == null -> CheckRow(
            CheckKind.Ksud,
            CheckStatus.Unknown,
            probe?.detail?.take(120) ?: "读不到已安装的 ksud",
        )

        sameBuild(probe.version, BuildConfig.VERSION_NAME) ->
            CheckRow(CheckKind.Ksud, CheckStatus.Ok, probe.version)

        else -> CheckRow(
            CheckKind.Ksud,
            CheckStatus.Warn,
            "${probe.version}（管理器 ${BuildConfig.VERSION_NAME}）",
            fix = CheckFix.ReinstallKsud,
        )
    }

    // 3. bin/ksud 是否还指向真正的二进制
    val link = readLink()
    rows += when {
        link == null -> CheckRow(CheckKind.Link, CheckStatus.Unknown, "读不到软链")
        link == DAEMON_PATH -> CheckRow(CheckKind.Link, CheckStatus.Ok, link)
        else -> CheckRow(CheckKind.Link, CheckStatus.Warn, "$link → 应为 $DAEMON_PATH", fix = CheckFix.RebuildLink)
    }

    // 4. UAPI 对齐
    val kernelUapi = runCatching { Natives.kernelUAPIVersion }.getOrDefault(0)
    val managerUapi = runCatching { Natives.managerUAPIVersion }.getOrDefault(0)
    rows += if (kernelUapi == managerUapi) {
        CheckRow(CheckKind.Uapi, CheckStatus.Ok, "$kernelUapi")
    } else {
        CheckRow(CheckKind.Uapi, CheckStatus.Warn, "内核 $kernelUapi / 管理器 $managerUapi")
    }

    // 5. 分区守卫
    val guard = runCatching { isPartitionGuardEnabled() }.getOrNull()
    rows += when (guard) {
        true -> CheckRow(CheckKind.Guard, CheckStatus.Ok, "开启")
        false -> CheckRow(CheckKind.Guard, CheckStatus.Warn, "关闭")
        null -> CheckRow(CheckKind.Guard, CheckStatus.Unknown, "读取失败")
    }

    // 6. SuSFS
    val susfs = runCatching { susfsVersion() }.getOrNull()
    rows += if (susfs != null) {
        CheckRow(CheckKind.Susfs, CheckStatus.Ok, susfs)
    } else {
        CheckRow(CheckKind.Susfs, CheckStatus.Unknown, "内核没有响应")
    }

    rows
}

/** 执行一个修复动作；返回是否成功。 */
suspend fun runCheckFix(fix: CheckFix): Boolean = withContext(Dispatchers.IO) {
    when (fix) {
        CheckFix.ReinstallKsud -> runCatching { install() }.getOrDefault(false)
        CheckFix.RebuildLink -> runCatching {
            val out = ArrayList<String>()
            val err = ArrayList<String>()
            val cmd = "ln -sfn $DAEMON_PATH $LINK_PATH"
            getRootShell().newJob().add(cmd).to(out, err).exec().isSuccess
        }.getOrDefault(false)
    }
}

private fun readLink(): String? {
    val out = ArrayList<String>()
    val err = ArrayList<String>()
    val result = runCatching {
        getRootShell().newJob().add("readlink $LINK_PATH").to(out, err).exec()
    }.getOrNull() ?: return null
    if (!result.isSuccess) return null
    return out.firstOrNull { it.isNotBlank() }?.trim()
}

private const val DAEMON_PATH = "/data/adb/ksud"
private const val LINK_PATH = "/data/adb/ksu/bin/ksud"
