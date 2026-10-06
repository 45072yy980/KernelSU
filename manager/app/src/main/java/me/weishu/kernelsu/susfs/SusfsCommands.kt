package me.weishu.kernelsu.susfs

import com.topjohnwu.superuser.ShellUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.weishu.kernelsu.ui.util.getKsuDaemonPath
import me.weishu.kernelsu.ui.util.getRootShell

/**
 * The userspace side of SuSFS, for the manager.
 *
 * Every mutation goes through `ksud susfs …`, which is the only thing that
 * knows the `reboot(2)` supercall; the read-only state is taken straight from
 * the module's `/proc/susfs_*` nodes where it publishes them. Nothing here
 * talks to the kernel directly, so the same code works whether SuSFS is built
 * into the kernel or came in as the standalone `susfs_guard_lkm` module.
 */
object SusfsCommands {

    /** Result of a command: its combined output, and whether `ksud` exited clean. */
    data class Result(val ok: Boolean, val output: String)

    private const val PROC = "/proc"

    private fun ksud(args: String): Result {
        val shell = getRootShell()
        val result = shell.newJob()
            .add("${getKsuDaemonPath()} susfs $args")
            .to(mutableListOf<String>(), null)
            .exec()
        val out = result.out.joinToString("\n").trim()
        return Result(result.isSuccess, out.ifEmpty { result.err.joinToString("\n").trim() })
    }

    /** Run one `ksud susfs …` command. */
    suspend fun run(args: String): Result = withContext(Dispatchers.IO) { ksud(args) }

    /**
     * Whether the kernel answers the SuSFS supercall at all, and with what.
     *
     * `ksud susfs version` prints the load state on the first line and the
     * version on the second, which is why this is parsed rather than compared
     * to a single string.
     */
    suspend fun loadStatus(): SusfsStatus = withContext(Dispatchers.IO) {
        val version = ksud("version")
        if (!version.ok) {
            return@withContext SusfsStatus()
        }
        val lines = version.output.lines()
        val loaded = lines.firstOrNull()?.trim() == "1"
        val ver = lines.getOrNull(1)?.trim().orEmpty()
        if (!loaded || ver == "unsupport") {
            return@withContext SusfsStatus(loaded = false, version = ver)
        }
        val variant = ksud("variant").output.trim()
        val features = ksud("features").output
            .split(',', '\n')
            .map { it.trim() }
            .filter { it.isNotEmpty() }
        SusfsStatus(loaded = true, version = ver, variant = variant, features = features)
    }

    /**
     * Read the module's procfs nodes.
     *
     * A node that is absent (feature off, or `expose_proc=0`) reads as empty
     * rather than failing: the screen shows what is there and nothing more.
     */
    suspend fun readProc(): SusfsProcSnapshot = withContext(Dispatchers.IO) {
        val shell = getRootShell()
        fun node(name: String): String =
            ShellUtils.fastCmd(shell, "cat $PROC/$name 2>/dev/null").trim()
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

    // ── mutations ─────────────────────────────────────────────────────────────
    suspend fun addSusPath(path: String) = run("add-sus-path '$path'")
    suspend fun addSusPathLoop(path: String) = run("add-sus-path-loop '$path'")
    suspend fun addSusMap(path: String) = run("add-sus-map '$path'")
    suspend fun addSusKstat(path: String) = run("add-sus-kstat '$path'")
    suspend fun updateSusKstat(path: String) = run("update-sus-kstat '$path'")
    suspend fun updateSusKstatFullClone(path: String) = run("update-sus-kstat-full-clone '$path'")

    /**
     * Spoof a path's stat with caller-supplied values instead of re-stat(2)ing.
     *
     * All 13 fields are forwarded verbatim to `ksud susfs add-sus-kstat-statically`;
     * the kernel uses them as-is so this is the way to hand it a stat the file
     * itself would never produce. `target_ino` is still resolved from the path.
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
        "add-sus-kstat-statically '$path' $ino $dev $nlink $size " +
            "$atimeSec $atimeNsec $mtimeSec $mtimeNsec $ctimeSec $ctimeNsec $blocks $blksize"
    )

    suspend fun addOpenRedirect(target: String, redirected: String, uidScheme: Int = 0) =
        run("add-open-redirect '$target' '$redirected' $uidScheme")
    suspend fun setUname(release: String, version: String) =
        run("set-uname '$release' '$version'")
    suspend fun setCmdline(path: String) = run("set-cmdline '$path'")
    suspend fun setLogEnabled(enabled: Boolean) = run("enable-log ${if (enabled) 1 else 0}")
    suspend fun setAvcLogSpoofing(enabled: Boolean) =
        run("enable-avc-log-spoofing ${if (enabled) 1 else 0}")
    suspend fun setHideSusMntsForNonSuProcs(enabled: Boolean) =
        run("hide-sus-mnts-for-non-su-procs ${if (enabled) 1 else 0}")
}
