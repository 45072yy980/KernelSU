package me.diksu.ultra.data.susfs

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.diksu.ultra.ui.util.getKsuDaemonPath
import me.diksu.ultra.ui.util.getRootShell

/**
 * 管理器这一侧的 SuSFS 通道。
 *
 * 所有写操作都走 `ksud susfs …` —— 只有它知道 `reboot(2)` 超级调用；只读状态优先用
 * `ksud susfs status`，`/proc/susfs_*` 只作补充展示。这里不做判断、不吞错：调用方拿
 * 到 [Result] 自己决定，失败原因原样带回去。
 */
object SusfsCommands {

    data class Result(val ok: Boolean, val output: String)

    private const val PROC = "/proc"

    /**
     * 归一化路径（与 FolkPatch 的 pathhide 一致）：去首尾空白、必须绝对、合并重复斜杠、
     * 去掉结尾斜杠。不合法返回 null。
     */
    fun normalizePath(input: String): String? {
        val trimmed = input.trim()
        if (trimmed.isEmpty() || !trimmed.startsWith('/')) return null
        val sb = StringBuilder(trimmed.length)
        var prevSlash = false
        for (ch in trimmed) {
            if (ch == '/') {
                if (!prevSlash) sb.append(ch)
                prevSlash = true
            } else {
                sb.append(ch)
                prevSlash = false
            }
        }
        while (sb.length > 1 && sb.endsWith('/')) sb.setLength(sb.length - 1)
        return if (sb.isEmpty()) null else sb.toString()
    }

    /** 单引号包裹，路径里有空格也不会被 shell 拆开。 */
    private fun q(value: String): String = "'" + value.replace("'", "'\\''") + "'"

    private fun ksud(args: String): Result {
        val shell = getRootShell()
        val result = shell.newJob()
            .add("${getKsuDaemonPath()} susfs $args")
            .to(mutableListOf<String>(), null)
            .exec()
        val out = result.out.joinToString("\n").trim()
        // 退出码 0 但没有输出时，err 里通常什么也没有；保留 ok 语义即可。
        return Result(result.isSuccess, out.ifEmpty { result.err.joinToString("\n").trim() })
    }

    suspend fun run(args: String): Result = withContext(Dispatchers.IO) { ksud(args) }

    /**
     * 读运行状态。
     *
     * `ksud susfs version` 第一行是加载状态、第二行是版本号，所以是解析而不是字符串比较；
     * 未加载时不再去读 variant/features，省两次 root 调用。
     */
    suspend fun loadStatus(): SusfsStatus = withContext(Dispatchers.IO) {
        val version = ksud("version")
        if (!version.ok) return@withContext SusfsStatus()
        val lines = version.output.lines()
        val loaded = lines.firstOrNull()?.trim() == "1"
        val ver = lines.getOrNull(1)?.trim().orEmpty()
        if (!loaded || ver == "unsupport") return@withContext SusfsStatus(loaded = false, version = ver)
        val variant = ksud("variant").output.trim()
        val features = ksud("features").output.split(',', '\n').map { it.trim() }.filter { it.isNotEmpty() }
        SusfsStatus(loaded = true, version = ver, variant = variant, features = features)
    }

    /** 模块在 procfs 上发布的原文，读不到就是空串（功能关掉时本来也没有节点）。 */
    suspend fun readProc(): SusfsProcSnapshot = withContext(Dispatchers.IO) {
        val shell = getRootShell()
        fun node(name: String): String {
            val out = mutableListOf<String>()
            shell.newJob().add("cat $PROC/$name 2>/dev/null").to(out, null).exec()
            return out.joinToString("\n").trim()
        }
        SusfsProcSnapshot(
            path = node("susfs_path"),
            hideMounts = node("susfs_hide_mounts"),
            hideModules = node("susfs_hide_modules"),
            kstat = node("susfs_kstat"),
            openRedirect = node("susfs_open_redirect"),
            avcSpoof = node("susfs_avc_spoof"),
            enableLog = node("susfs_enable_log"),
        )
    }

    // ── 开关 ──────────────────────────────────────────────────────────────────
    suspend fun setLogEnabled(enabled: Boolean) = run("enable-log ${if (enabled) 1 else 0}")
    suspend fun setAvcLogSpoofing(enabled: Boolean) = run("enable-avc-log-spoofing ${if (enabled) 1 else 0}")
    suspend fun setHideSusMntsForNonSuProcs(enabled: Boolean) = run("hide-sus-mnts-for-non-su-procs ${if (enabled) 1 else 0}")

    // ── 规则 ──────────────────────────────────────────────────────────────────
    suspend fun addSusPath(path: String) = run("add-sus-path ${q(path)}")
    suspend fun addSusPathLoop(path: String) = run("add-sus-path-loop ${q(path)}")
    suspend fun addSusMap(path: String) = run("add-sus-map ${q(path)}")
    suspend fun addSusKstat(path: String) = run("add-sus-kstat ${q(path)}")
    suspend fun updateSusKstat(path: String) = run("update-sus-kstat ${q(path)}")
    suspend fun updateSusKstatFullClone(path: String) = run("update-sus-kstat-full-clone ${q(path)}")
    suspend fun addOpenRedirect(target: String, redirected: String, uidScheme: Int) =
        run("add-open-redirect ${q(target)} ${q(redirected)} $uidScheme")
    suspend fun setUname(release: String, version: String) = run("set-uname ${q(release)} ${q(version)}")
    suspend fun setCmdline(sourcePath: String) = run("set-cmdline ${q(sourcePath)}")

    /**
     * 用调用方给的 13 个字段伪造 stat，而不是重新 stat(2)。
     *
     * 内核原样采用这些值，所以这是给它一份"文件本身永远产生不出来"的 stat 的唯一途径；
     * `target_ino` 仍由路径解析。
     */
    suspend fun addSusKstatStatically(
        path: String,
        ino: Long,
        dev: Long,
        nlink: Long,
        size: Long,
        atimeSec: Long,
        atimeNsec: Long = 0,
        mtimeSec: Long,
        mtimeNsec: Long = 0,
        ctimeSec: Long,
        ctimeNsec: Long = 0,
        blocks: Long = 0,
        blksize: Long = 0,
    ) = run(
        "add-sus-kstat-statically ${q(path)} $ino $dev $nlink $size " +
            "$atimeSec $atimeNsec $mtimeSec $mtimeNsec $ctimeSec $ctimeNsec $blocks $blksize",
    )
}
