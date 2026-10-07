package me.weishu.kernelsu.shizuku

import android.content.Context
import android.content.pm.PackageInfo
import android.os.Parcel
import android.util.Log
import me.weishu.kernelsu.ksuApp
import me.weishu.kernelsu.ui.util.getRootShell
import rikka.parcelablelist.ParcelableListSlice
import rikka.shizuku.Shizuku
import rikka.shizuku.server.ServerConstants

/**
 * Shizuku 服务管理器（KernelSU Manager 内置版）。
 *
 * 管理内置 Shizuku Server 的生命周期：
 * - 通过 root 以 app_process 启动 [rikka.shizuku.server.ShizukuService]
 * - 授权界面由 [ShizukuPermissionActivity] 提供
 * - 授权记录存储于 /data/user_de/0/com.android.shell/shizuku.json
 *
 * 与 FolkPatch 版本的差异：
 * - 不内置 fpdrop 降权工具（无 native binary），server 以 root 运行时所有命令天然 root 权限
 * - root shell 使用 KernelSU 的 [getRootShell]
 * - 配置存储使用 KernelSU Application 的 SharedPreferences
 */
object ShizukuServiceManager {
    private const val TAG = "ShizukuMgr"

    /** 开关持久化 key */
    const val PREF_SHIZUKU_ENABLED = "shizuku_service_enabled"

    /** shizuku server 进程名（app_process --nice-name） */
    private const val SERVER_PROCESS_NAME = "shizuku_server"

    /** server 入口类 */
    private const val SERVER_CLASS = "rikka.shizuku.server.ShizukuService"

    private const val SERVER_START_LOG = "/data/local/tmp/shizuku_ksu.log"
    private const val FLAG_ALLOWED = 1 shl 1
    private const val FLAG_DENIED = 1 shl 2
    private const val MASK_PERMISSION = FLAG_ALLOWED or FLAG_DENIED

    /** 防并发启动：快速反复拨动开关时避免重复拉起多个 server */
    private val startLock = Any()

    private const val SERVER_START_TIMEOUT_MS = 10_000L
    private const val SERVER_POLL_INTERVAL_MS = 200L

    private fun prefs() = ksuApp.getSharedPreferences("shizuku_settings", Context.MODE_PRIVATE)

    // ==================== 启动诊断 ====================

    /**
     * Manager 侧启动诊断缓冲。
     *
     * 历史顽疾：server 进程秒崩时 shell 重定向的日志文件可能根本不生成
     * （su 路径不存在 / 嵌套 su 被拒 / 类被混淆等），面板里完全看不到原因。
     * 这里把每次启动尝试的命令、退出码、stderr 全部留在 manager 进程内，
     * 并持久化到 SharedPreferences，面板日志查看器无条件可读。
     */
    private val startDiagnostics = StringBuilder()

    @Synchronized
    private fun logDiag(msg: String) {
        Log.i(TAG, msg)
        startDiagnostics.append(msg).append('\n')
        // 内存缓冲最多保留 64KB，防止长期运行膨胀
        if (startDiagnostics.length > 64 * 1024) {
            startDiagnostics.delete(0, startDiagnostics.length - 32 * 1024)
        }
        // 异步持久化，供崩溃后/跨进程查看
        prefs().edit().putString("shizuku_start_diagnostics", startDiagnostics.toString()).apply()
    }

    /** 读取 manager 侧启动诊断（含历史持久化内容）。 */
    fun getStartDiagnostics(): String {
        val persisted = prefs().getString("shizuku_start_diagnostics", "").orEmpty()
        if (persisted.isNotBlank() && persisted != startDiagnostics.toString()) return persisted
        return startDiagnostics.toString()
    }

    fun isEnabled(): Boolean = prefs().getBoolean(PREF_SHIZUKU_ENABLED, false)

    fun setEnabled(enabled: Boolean) {
        prefs().edit().putBoolean(PREF_SHIZUKU_ENABLED, enabled).apply()
    }

    /** Shizuku Server 是否已建立可用 Binder。仅检查进程会把卡死进程误判为可用。 */
    fun isServerRunning(): Boolean {
        return try {
            Shizuku.pingBinder()
        } catch (t: Throwable) {
            false
        }
    }

    /** 通过 root 检查 shizuku_server 进程是否存活。 */
    private fun isServerProcessAlive(): Boolean {
        return try {
            val out = ArrayList<String>()
            val err = ArrayList<String>()
            val result = getRootShell()
                .newJob()
                .add("/system/bin/pidof $SERVER_PROCESS_NAME")
                .to(out, err)
                .exec()
            result.isSuccess && out.isNotEmpty()
        } catch (t: Throwable) {
            Log.e(TAG, "checkServerProcess failed", t)
            false
        }
    }

    private fun killServerProcess() {
        try {
            getRootShell().newJob()
                .add("kill -9 \$(/system/bin/pidof $SERVER_PROCESS_NAME) 2>/dev/null || true")
                .exec()
            Thread.sleep(200L)
        } catch (t: Throwable) {
            Log.w(TAG, "killServerProcess failed", t)
        }
    }

    private fun waitForBinder(timeoutMs: Long): Boolean {
        val deadline = System.currentTimeMillis() + timeoutMs
        do {
            if (isServerRunning()) return true
            Thread.sleep(SERVER_POLL_INTERVAL_MS)
        } while (System.currentTimeMillis() < deadline)
        return isServerRunning()
    }

    /**
     * 启动 Shizuku Server。
     *
     * 以 root 身份通过 app_process 运行内置 server（与官方 Shizuku root 模式一致）：
     * - 首选 root (uid 0)：命令具备 root 权限，app_process 处于全局 mount namespace，
     *   config（/data/user_de/0/com.android.shell/shizuku.json）读写正常；
     * - 降级到 shell (uid 2000)：用 -M 进入全局 namespace 以保证 config 可写。
     *
     * @return true 表示 server 已就绪（binder 可达）
     */
    fun start(context: Context): Boolean {
        synchronized(startLock) {
            return startInternal(context)
        }
    }

    private fun startInternal(context: Context): Boolean {
        return try {
            if (isServerRunning()) return true
            if (!waitForRoot(15_000L)) {
                logDiag("[start] failed: root not available within 15s")
                return false
            }
            // A process with no usable binder is a failed or stuck previous launch.
            killServerProcess()

            val apkPath = context.applicationInfo.sourceDir
            if (apkPath.isBlank()) {
                logDiag("[start] failed: apk path is blank")
                return false
            }
            val libraryPath = context.applicationInfo.nativeLibraryDir
            val inner = "/system/bin/setsid -d /system/bin/env CLASSPATH=\"$apkPath\" /system/bin/app_process " +
                "-Djava.class.path=\"$apkPath\" " +
                "-Dshizuku.library.path=\"$libraryPath\" " +
                "/system/bin --nice-name=$SERVER_PROCESS_NAME $SERVER_CLASS " +
                ">$SERVER_START_LOG 2>&1 </dev/null &"

            // 动态解析 su 路径：KernelSU 的 su 由内核 execve 钩子按文件名提供，
            // /system/bin/su 这个绝对路径在很多设备上并不存在，
            // 写死绝对路径是之前版本"日志文件都不生成"的根因之一。
            val suPath = resolveSuPath()
            logDiag("[start] resolved su path: ${suPath ?: "<none>"}")

            // 候选启动策略，逐一尝试：
            // 1. 直接在 root shell 中执行（root shell 本身已是 uid 0，无需嵌套 su）；
            // 2. 嵌套 su -c（与官方 Shizuku root 模式一致）；
            // 3-5. 降权 shell (uid 2000)，-M 进入全局 mount namespace 保证 config 可写。
            val candidates = buildList {
                add(inner)
                if (suPath != null) {
                    add("$suPath -c '$inner'")
                    add("$suPath 2000 -M -c '$inner'")
                    add("$suPath -M 2000 -c '$inner'")
                    add("$suPath 2000 -c '$inner'")
                }
            }
            for ((index, cmd) in candidates.withIndex()) {
                try {
                    getRootShell().newJob()
                        .add("/system/bin/rm -f $SERVER_START_LOG")
                        .exec()
                    val out = ArrayList<String>()
                    val err = ArrayList<String>()
                    val result = getRootShell().newJob().add(cmd).to(out, err).exec()
                    logDiag(
                        "[start] attempt #${index + 1} exit=${result.code} " +
                            "out=${out.joinToString()} err=${err.joinToString()}"
                    )
                    if (!result.isSuccess) continue
                    if (waitForBinder(SERVER_START_TIMEOUT_MS)) {
                        logDiag("[start] attempt #${index + 1} succeeded, binder is ready")
                        return true
                    }
                    // binder 未就绪：把 server 侧日志文件内容抓回来，秒崩也能看到原因
                    val serverLog = readLogFileViaRoot()
                    logDiag(
                        "[start] attempt #${index + 1} produced no usable binder; " +
                            "server log: ${serverLog.ifBlank { "<empty>" }}"
                    )
                    killServerProcess()
                } catch (t: Throwable) {
                    logDiag("[start] attempt #${index + 1} crashed: ${t.message}")
                    killServerProcess()
                }
            }
            logDiag("[start] failed: no launch strategy produced a usable binder")
            false
        } catch (t: Throwable) {
            logDiag("[start] failed with exception: ${t.message}")
            killServerProcess()
            false
        }
    }

    /** 在 root shell 里解析 su 的实际路径；找不到返回 null（直接用 root shell 启动）。 */
    private fun resolveSuPath(): String? {
        return try {
            val out = ArrayList<String>()
            getRootShell().newJob()
                .add("command -v su || which su")
                .to(out, null)
                .exec()
            out.firstOrNull { it.isNotBlank() }?.trim()
        } catch (t: Throwable) {
            Log.w(TAG, "resolveSuPath failed", t)
            null
        }
    }

    /** 等待 root 可用（开机早期 root shell 可能尚未就绪）。 */
    fun waitForRoot(timeoutMs: Long): Boolean {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            try {
                if (getRootShell().isRoot) return true
            } catch (t: Throwable) {
                Log.w(TAG, "root not ready", t)
            }
            try {
                Thread.sleep(500L)
            } catch (e: InterruptedException) {
                Thread.currentThread().interrupt()
                return false
            }
        }
        return try {
            getRootShell().isRoot
        } catch (t: Throwable) {
            false
        }
    }

    /** 停止 Shizuku Server。优先走 Manager 通道优雅退出，再以 root 强杀兜底。 */
    fun stop(): Boolean {
        return try {
            if (Shizuku.pingBinder()) {
                try {
                    Shizuku.exit()
                } catch (t: Throwable) {
                    Log.w(TAG, "Shizuku.exit() failed, falling back to kill", t)
                }
                if (waitStopped()) return true
            }
            getRootShell().newJob()
                .add("kill -9 \$(/system/bin/pidof $SERVER_PROCESS_NAME) 2>/dev/null || true")
                .exec()
            waitStopped()
        } catch (t: Throwable) {
            Log.e(TAG, "stop failed", t)
            false
        }
    }

    /** 轮询等待 server 完全停止，最多 5 秒。 */
    private fun waitStopped(): Boolean {
        repeat(20) {
            Thread.sleep(250L)
            if (!isServerRunning() && !isServerProcessAlive()) return true
        }
        Log.w(TAG, "stop timed out waiting for server to exit")
        return false
    }

    /** Returns Shizuku-compatible applications exposed by the embedded server. */
    fun getApplications(): List<PackageInfo>? {
        if (!isServerRunning()) return null
        val data = Parcel.obtain()
        val reply = Parcel.obtain()
        return try {
            data.writeInterfaceToken("moe.shizuku.server.IShizukuService")
            data.writeInt(-1)
            Shizuku.getBinder()!!.transact(ServerConstants.BINDER_TRANSACTION_getApplications, data, reply, 0)
            reply.readException()
            @Suppress("UNCHECKED_CAST")
            (ParcelableListSlice.CREATOR.createFromParcel(reply) as ParcelableListSlice<PackageInfo>).list.orEmpty()
        } catch (t: Throwable) {
            Log.e(TAG, "getApplications failed", t)
            null
        } finally {
            reply.recycle()
            data.recycle()
        }
    }

    fun isAllowed(uid: Int): Boolean {
        return try {
            (Shizuku.getFlagsForUid(uid, MASK_PERMISSION) and FLAG_ALLOWED) != 0
        } catch (t: Throwable) {
            Log.e(TAG, "isAllowed failed for uid $uid", t)
            false
        }
    }

    fun setAllowed(uid: Int, allowed: Boolean) {
        if (!isServerRunning()) {
            throw IllegalStateException("Shizuku service is not running")
        }
        Shizuku.updateFlagsForUid(uid, MASK_PERMISSION, if (allowed) FLAG_ALLOWED else 0)
    }

    /** server 是否以 root (uid 0) 运行。 */
    fun isRootServer(): Boolean {
        return try {
            Shizuku.pingBinder() && Shizuku.getUid() == 0
        } catch (t: Throwable) {
            false
        }
    }

    // ==================== Log reading ====================

    private const val SERVER_LOG_FILE = "/data/user_de/0/com.android.shell/shizuku_ksu.log"
    private const val SERVER_LOG_FILE_BACKUP = "/data/user_de/0/com.android.shell/shizuku_ksu.log.1"

    private val LOGCAT_TAGS = arrayOf(
        "Service", "ConfigManager", "ClientManager", "UserServiceManager",
        "ShizukuService", "Starter", "AppProcess",
    )

    fun getServerLog(): String {
        if (Shizuku.pingBinder()) {
            val viaBinder = getLogViaBinder()
            if (viaBinder != null) return viaBinder
        }
        return readLogFileViaRoot()
    }

    private fun getLogViaBinder(): String? {
        val data = Parcel.obtain()
        val reply = Parcel.obtain()
        return try {
            data.writeInterfaceToken("moe.shizuku.server.IShizukuService")
            Shizuku.getBinder()!!.transact(ServerConstants.BINDER_TRANSACTION_getLog, data, reply, 0)
            reply.readException()
            reply.readString().orEmpty()
        } catch (t: Throwable) {
            Log.e(TAG, "getLog via binder failed", t)
            null
        } finally {
            reply.recycle()
            data.recycle()
        }
    }

    /**
     * binder 不可达时的回退读取：manager 侧启动诊断 + server 持久化日志文件。
     * 诊断在最前面——进程秒崩时文件可能为空，诊断里一定有原因。
     */
    private fun readLogFileViaRoot(): String {
        val diag = getStartDiagnostics()
        val fileLog = try {
            val out = ArrayList<String>()
            val err = ArrayList<String>()
            getRootShell().newJob()
                .add(
                    "cat $SERVER_START_LOG 2>/dev/null; " +
                        "cat $SERVER_LOG_FILE_BACKUP 2>/dev/null; " +
                        "cat $SERVER_LOG_FILE 2>/dev/null"
                )
                .to(out, err)
                .exec()
            out.joinToString("\n")
        } catch (t: Throwable) {
            Log.e(TAG, "readLogFileViaRoot failed", t)
            ""
        }
        return buildString {
            if (diag.isNotBlank()) {
                append("===== 启动诊断（Manager 侧） =====\n")
                append(diag).append('\n')
            }
            if (fileLog.isNotBlank()) {
                append("===== 服务端日志 =====\n")
                append(fileLog)
            }
        }
    }

    fun getLogcat(): String {
        return try {
            val out = ArrayList<String>()
            val err = ArrayList<String>()
            val filter = LOGCAT_TAGS.joinToString(" ") { "$it:V" }
            getRootShell().newJob()
                .add("logcat -d -v time -t 2000 $filter *:S 2>/dev/null")
                .to(out, err)
                .exec()
            out.joinToString("\n")
        } catch (t: Throwable) {
            Log.e(TAG, "getLogcat failed", t)
            ""
        }
    }

    fun clearServerLog(): Boolean {
        startDiagnostics.clear()
        prefs().edit().remove("shizuku_start_diagnostics").apply()
        if (Shizuku.pingBinder()) {
            val data = Parcel.obtain()
            val reply = Parcel.obtain()
            try {
                data.writeInterfaceToken("moe.shizuku.server.IShizukuService")
                Shizuku.getBinder()!!.transact(ServerConstants.BINDER_TRANSACTION_clearLog, data, reply, 0)
                reply.readException()
                return true
            } catch (t: Throwable) {
                Log.e(TAG, "clearLog via binder failed", t)
            } finally {
                reply.recycle()
                data.recycle()
            }
        }
        return try {
            getRootShell().newJob()
                .add("rm -f $SERVER_LOG_FILE $SERVER_LOG_FILE_BACKUP")
                .exec()
                .isSuccess
        } catch (t: Throwable) {
            Log.e(TAG, "clearServerLog via root failed", t)
            false
        }
    }
}
