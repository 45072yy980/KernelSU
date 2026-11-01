package me.diksu.ultra.susfs

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import me.diksu.ultra.data.susfs.SusfsCommands
import me.diksu.ultra.data.susfs.SusfsRepository

/**
 * 开机后把保存的 SuSFS 配置重新下发。
 *
 * 内核模块不跨重启保留任何东西：开关、伪 uname、隐藏路径、map、kstat、重定向都只在内存里，
 * 所以每次开机都要按配置重放一遍。
 *
 * 重放由 [SusfsRepository.replayAfterBoot] 负责（配置 → 逐条下发 → 汇总报告），这里只管
 * 三件事：等 `sys.boot_completed`、失败重试计数、出错别让进程崩掉。
 *
 * 注意：内置 Shizuku 的自启**不在这里**（见 [me.diksu.ultra.shizuku.ShizukuBootReceiver]）。
 * 它曾经挂在本接收器末尾，SUSFS 未加载时会被一起跳过，表现为"开机自启时灵时不灵"。
 */
class SusfsBootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            try {
                val prior = prefs.getInt(KEY_RETRY, 0)
                if (prior >= MAX_RETRIES) {
                    Log.e(TAG, "连续 $MAX_RETRIES 次重放失败，重置计数并放弃本次")
                    prefs.edit().putInt(KEY_RETRY, 0).apply()
                    return@launch
                }
                if (prior > 0) {
                    Log.w(TAG, "上次重放失败（$prior 次），多等一会儿再试")
                    Thread.sleep(EXTRA_WAIT_MS)
                }

                waitForBootCompleted()

                // 没有实现响应超级调用，就没有可重放的对象。
                if (!SusfsCommands.loadStatus().loaded) {
                    Log.i(TAG, "SuSFS 未加载，跳过重放")
                    return@launch
                }

                val report = SusfsRepository(context).replayAfterBoot()
                prefs.edit().putInt(KEY_RETRY, if (report.isSuccess) 0 else prior + 1).apply()
                Log.i(TAG, "重放完成：${report.summary()}")
                report.failed.entries.take(5).forEach { (key, reason) ->
                    Log.w(TAG, "  失败 $key：$reason")
                }
            } catch (t: Throwable) {
                Log.e(TAG, "开机重放崩溃", t)
            } finally {
                pending.finish()
            }
        }
    }

    private fun waitForBootCompleted() {
        repeat(180) {
            val done = Runtime.getRuntime()
                .exec(arrayOf("getprop", "sys.boot_completed"))
                .inputStream.bufferedReader().readText().trim()
            if (done == "1") return
            Thread.sleep(1000)
        }
    }

    private companion object {
        private const val TAG = "SusfsBootReceiver"
        private const val PREFS = "susfs_boot"
        private const val KEY_RETRY = "retry_count"
        private const val MAX_RETRIES = 3
        private const val EXTRA_WAIT_MS = 30_000L
    }
}
