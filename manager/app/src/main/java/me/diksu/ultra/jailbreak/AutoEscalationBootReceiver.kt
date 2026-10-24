package me.diksu.ultra.jailbreak

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import me.diksu.ultra.ui.util.rootAvailable

/**
 * 开机自动提权并越狱。
 *
 * 3.x 的开机"自动越狱"只是把 [com.mngr.app.magica.BootCompletedReceiver]
 * 打开，而它拉起的服务是空的 —— 开关拨了不会有任何效果。4.0 由本接收器
 * 直接按用户设定的顺序调用随包内置的提权链：
 *
 * 1. 等 `sys.boot_completed`（开机早期拉进程容易被系统回收）；
 * 2. 再等 [SETTLE_DELAY_MS]，让包管理器与 zygote 稳定下来；
 * 3. 已经有 root 就直接跳过，不做无谓尝试；
 * 4. 调 [AutoEscalation.run]，内部逐个尝试、第一个成功即停。
 *
 * 整个流程放在 `goAsync()` + IO 协程里，不阻塞广播派发。
 *
 * ## 为什么不需要 root
 *
 * 越狱用户完整重启之后是没有 root 的 —— 这正是"越狱"的前提。所以自启只用
 * 普通应用就有的 `RECEIVE_BOOT_COMPLETED` 权限把进程叫起来，提权由随包内置的
 * 提权链在**应用进程内**完成（和用户手动点"提权"走的是同一条路），不预先
 * 假设有 root，也不写任何系统分区。
 *
 * 4.1 同时断开了旧路径：`autoJailbreak` 不再顺带打开 magica 的 BootCompletedReceiver
 * （那个接收器会在没有 root 的情况下由 app zygote 直接 exec `ksud late-load`，
 * 属于"用 root 注入系统"的做法，失败还会留下痕迹）。
 */
class AutoEscalationBootReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "AutoEscalationBoot"
        private const val BOOT_WAIT_SECONDS = 120
        private const val SETTLE_DELAY_MS = 15_000L
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        if (!AutoEscalation.enabled) {
            Log.i(TAG, "auto escalation disabled, skip")
            return
        }
        val pending = goAsync()
        val appContext = context.applicationContext
        CoroutineScope(Dispatchers.IO).launch {
            try {
                waitForBootCompleted()
                Thread.sleep(SETTLE_DELAY_MS)
                if (runCatching { rootAvailable() }.getOrDefault(false)) {
                    Log.i(TAG, "already rooted, skip auto escalation")
                    return@launch
                }
                // 本次开机只尝试一次；连续失败到上限就停手，避免每次开机都去捅内核。
                val skipReason = AutoEscalation.bootSkipReason(appContext)
                if (skipReason != null) {
                    Log.i(TAG, "skip auto escalation: $skipReason")
                    return@launch
                }
                AutoEscalation.markBootAttempt()
                Log.i(TAG, "auto escalation begin")
                val outcome = AutoEscalation.run(appContext) { line -> Log.i(TAG, line) }
                Log.i(TAG, "auto escalation done: success=${outcome.success} method=${outcome.method}")
            } catch (t: Throwable) {
                Log.e(TAG, "auto escalation crashed", t)
            } finally {
                runCatching { pending.finish() }
            }
        }
    }

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
