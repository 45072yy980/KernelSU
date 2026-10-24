package me.diksu.ultra.shizuku

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * 内置 Shizuku 的开机自启入口。
 *
 * 3.x 把这件事挂在 [me.diksu.ultra.susfs.SusfsBootReceiver] 的尾部，而那个
 * 接收器在 SUSFS 未加载 / 重放失败次数超限时会提前 return，导致自启被整段
 * 跳过 —— 这是"开机自启时好时坏、多数时候根本没启动"的根因。
 *
 * 现在自启独立成广播接收器：
 * - `BOOT_COMPLETED`：开机完成（用户解锁后才会派发，此时私有存储可读）；
 * - `MY_PACKAGE_REPLACED`：应用更新后补一次，避免更新后要手动开一次；
 * - [ACTION_START_NOW]：面板里的"立即自启"，走同一条路径，行为可预期。
 *
 * 等待与重试都交给 [ShizukuAutostart]，结果持久化后由面板读取展示。
 */
class ShizukuBootReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "ShizukuBootReceiver"

        /** 面板"立即自启"用的一次性动作。 */
        const val ACTION_START_NOW = "me.diksu.ultra.action.START_SHIZUKU"

        /** 开机等待 `sys.boot_completed` 的上限：60 秒足够，收到广播时通常已是 1。 */
        private const val BOOT_WAIT_SECONDS = 60
    }

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        if (action != Intent.ACTION_BOOT_COMPLETED &&
            action != Intent.ACTION_MY_PACKAGE_REPLACED &&
            action != ACTION_START_NOW
        ) {
            return
        }
        if (!ShizukuServiceManager.isEnabled()) {
            Log.i(TAG, "autostart disabled, skip ($action)")
            return
        }

        val pending = goAsync()
        val appContext = context.applicationContext
        CoroutineScope(Dispatchers.IO).launch {
            try {
                if (action == Intent.ACTION_BOOT_COMPLETED) {
                    waitForBootCompleted()
                }
                val source = when (action) {
                    Intent.ACTION_BOOT_COMPLETED -> "boot"
                    Intent.ACTION_MY_PACKAGE_REPLACED -> "package-replaced"
                    else -> "manual"
                }
                val maxAttempts = if (action == ACTION_START_NOW) 1 else 5
                ShizukuAutostart.run(appContext, source, maxAttempts)
            } catch (t: Throwable) {
                Log.e(TAG, "autostart crashed ($action)", t)
            } finally {
                runCatching { pending.finish() }
            }
        }
    }

    /** 等到系统真的启动完成，避免在 SystemServer 还没稳的时候去拉 app_process。 */
    private fun waitForBootCompleted() {
        repeat(BOOT_WAIT_SECONDS) {
            val completed = runCatching {
                Runtime.getRuntime()
                    .exec(arrayOf("getprop", "sys.boot_completed"))
                    .inputStream.bufferedReader().readText().trim()
            }.getOrDefault("")
            if (completed == "1") return
            runCatching { Thread.sleep(1_000L) }
        }
    }
}
