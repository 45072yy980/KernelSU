package me.diksu.ultra.shizuku

import android.content.Context
import android.util.Log
import me.diksu.ultra.ksuApp

/**
 * 内置 Shizuku 的开机自启编排。
 *
 * ## 为什么要单独抽出来
 *
 * 3.x 把"开机启动 Shizuku"塞在 [me.diksu.ultra.susfs.SusfsBootReceiver] 的
 * 末尾，而那个接收器在 SUSFS 未加载时会 `return@launch` 直接结束 —— 于是只要
 * 用户没开 SUSFS（绝大多数人），开机自启就永远不会执行。这里把自启逻辑独立
 * 出来，只依赖"开关是否打开"这一个条件，并在每次尝试后记录结果，供面板展示。
 *
 * ## 重试节奏
 *
 * 开机早期 root 可能还没就绪（boot 补丁模式要等 kernel 侧的 su 起来；越狱模式
 * 更是要等用户手动提权），所以按 [BOOT_RETRY_DELAYS_MS] 逐步拉长间隔重试，
 * 总计约 7 分钟。每次尝试前都重新检查 binder，已经起来就直接算成功。
 */
object ShizukuAutostart {
    private const val TAG = "ShizukuAutostart"
    private const val PREFS = "shizuku_settings"

    private const val KEY_LAST_AT = "autostart_last_at"
    private const val KEY_LAST_OK = "autostart_last_ok"
    private const val KEY_LAST_DETAIL = "autostart_last_detail"
    private const val KEY_LAST_SOURCE = "autostart_last_source"

    /** 首次立即，之后逐步拉长；加起来约 7 分钟，足够熬过开机早期的抖动。 */
    private val BOOT_RETRY_DELAYS_MS = longArrayOf(0L, 15_000L, 45_000L, 120_000L, 240_000L)

    /** 每次尝试前愿意等 root 多久。第一次给足时间，后面直接复用已就绪的 root。 */
    private const val FIRST_ROOT_WAIT_MS = 60_000L
    private const val LATER_ROOT_WAIT_MS = 15_000L

    /** 最近一次自启尝试的结果。 */
    data class LastAttempt(
        val atMillis: Long,
        val success: Boolean,
        val detail: String,
        val source: String,
    )

    private fun prefs() = ksuApp.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun lastAttempt(): LastAttempt? {
        val at = prefs().getLong(KEY_LAST_AT, 0L)
        if (at <= 0L) return null
        return LastAttempt(
            atMillis = at,
            success = prefs().getBoolean(KEY_LAST_OK, false),
            detail = prefs().getString(KEY_LAST_DETAIL, "").orEmpty(),
            source = prefs().getString(KEY_LAST_SOURCE, "").orEmpty(),
        )
    }

    private fun record(source: String, success: Boolean, detail: String) {
        Log.i(TAG, "[$source] success=$success detail=$detail")
        prefs().edit()
            .putLong(KEY_LAST_AT, System.currentTimeMillis())
            .putBoolean(KEY_LAST_OK, success)
            .putString(KEY_LAST_DETAIL, detail)
            .putString(KEY_LAST_SOURCE, source)
            .apply()
    }

    /**
     * 执行一次自启（阻塞调用，务必在 IO 线程）。
     *
     * @param source 触发来源，仅用于诊断展示（boot / package-replaced / manual）。
     * @param maxAttempts 最多尝试几次；面板里的"立即测试"只跑 1 次。
     * @return 是否最终可用（binder 就绪）。
     */
    fun run(
        context: Context,
        source: String,
        maxAttempts: Int = BOOT_RETRY_DELAYS_MS.size,
    ): Boolean {
        if (!ShizukuServiceManager.isEnabled()) return false

        // 已经活着就不要重复拉起，但仍记一条成功，方便面板显示"本次开机已自启"。
        if (ShizukuServiceManager.isServerRunning()) {
            record(source, true, "服务已在运行")
            return true
        }

        var lastDetail = "尚未开始"
        val attempts = maxAttempts.coerceIn(1, BOOT_RETRY_DELAYS_MS.size)
        for (index in 0 until attempts) {
            val delay = BOOT_RETRY_DELAYS_MS.getOrElse(index) { BOOT_RETRY_DELAYS_MS.last() }
            if (delay > 0L) {
                runCatching { Thread.sleep(delay) }
            }
            if (ShizukuServiceManager.isServerRunning()) {
                record(source, true, "第 ${index + 1} 次检查时服务已在运行")
                return true
            }
            val rootWait = if (index == 0) FIRST_ROOT_WAIT_MS else LATER_ROOT_WAIT_MS
            if (!ShizukuServiceManager.waitForRoot(rootWait)) {
                lastDetail = if (index == attempts - 1) {
                    "等待 root 超时：越狱模式下需先完成一次提权"
                } else {
                    "第 ${index + 1} 次：等待 root 超时"
                }
                Log.w(TAG, "[$source] attempt ${index + 1}: root not ready")
                continue
            }
            val started = runCatching { ShizukuServiceManager.start(context) }.getOrDefault(false)
            if (started) {
                record(source, true, "第 ${index + 1} 次尝试成功")
                return true
            }
            lastDetail = "第 ${index + 1} 次启动失败，详见服务日志"
            Log.w(TAG, "[$source] attempt ${index + 1}: start() returned false")
        }
        record(source, false, lastDetail)
        return false
    }
}
