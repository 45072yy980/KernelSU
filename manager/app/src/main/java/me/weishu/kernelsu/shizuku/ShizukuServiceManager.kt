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

    private const val SERVER_START_LOG = "/data/local/tmp/shizuku_start.log"
    private const val FLAG_ALLOWED = 1 shl 1
    private const val FLAG_DENIED = 1 shl 2
    private const val MASK_PERMISSION = FLAG_ALLOWED or FLAG_DENIED

    /** 防并发启动：快速反复拨动开关时避免重复拉起多个 server */
    private val startLock = Any()

    private const val SERVER_START_TIMEOUT_MS = 10_000L
    private const val SERVER_POLL_INTERVAL_MS = 200L

    private fun prefs() = ksuApp.getSharedPreferences("shizuku_settings", Context.MODE_PRIVATE)

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
     * 以 root 身份通过 app_process 运行内置 server：
     * - root 直接跑 app_process 会触发 ART 的 dalvik-cache chown 检查（uid 0 被当作 zygote）并 Abort；
     * - 因此优先尝试 su 2000（shell 身份）启动，shell 持有 Shizuku 所需的 privileged 权限。
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
                Log.e(TAG, "start failed: root not available")
                return false
            }
            // A process with no usable binder is a failed or stuck previous launch.
            killServerProcess()

            val apkPath = context.applicationInfo.sourceDir
            if (apkPath.isBlank()) {
                Log.e(TAG, "start failed: apk path is blank")
                return false
            }
            val libraryPath = context.applicationInfo.nativeLibraryDir
            val inner = "/system/bin/setsid -d /system/bin/env CLASSPATH=\"$apkPath\" /system/bin/app_process " +
                "-Djava.class.path=\"$apkPath\" " +
                "-Dshizuku.library.path=\"$libraryPath\" " +
                "/system/bin --nice-name=$SERVER_PROCESS_NAME $SERVER_CLASS " +
                ">$SERVER_START_LOG 2>&1 </dev/null &"

            // 不同 su 实现的降权语法存在差异，逐一尝试。
            // 首选 shell (uid 2000) 启动：避免 root app_process 的 ART chown 检查。
            // 降级到 root 启动时命令具备 root 权限。
            val candidates = arrayOf(
                "/system/bin/su 2000 -M -c '$inner'",
                "/system/bin/su -M 2000 -c '$inner'",
                "/system/bin/su 2000 -c '$inner'",
                "/system/bin/su - 2000 -c '$inner'",
                "/system/bin/su -c '$inner'",
            )
            for (cmd in candidates) {
                try {
                    getRootShell().newJob()
                        .add("/system/bin/rm -f $SERVER_START_LOG")
                        .exec()
                    val out = ArrayList<String>()
                    val err = ArrayList<String>()
                    val result = getRootShell().newJob().add(cmd).to(out, err).exec()
                    if (!result.isSuccess) {
                        Log.w(TAG, "start command failed: out=${out.joinToString()} err=${err.joinToString()}")
                        continue
                    }
                    if (waitForBinder(SERVER_START_TIMEOUT_MS)) {
                        return true
                    }
                    Log.w(TAG, "start command did not produce a usable binder: out=${out.joinToString()} err=${err.joinToString()} cmd=$cmd")
                    killServerProcess()
                } catch (t: Throwable) {
                    Log.w(TAG, "start command crashed: $cmd", t)
                    killServerProcess()
                }
            }
            Log.e(TAG, "start failed: no launch strategy produced a usable binder")
            false
        } catch (t: Throwable) {
            Log.e(TAG, "start failed", t)
            killServerProcess()
            false
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

    private fun readLogFileViaRoot(): String {
        return try {
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
