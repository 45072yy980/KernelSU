package me.diksu.ultra.foxsu.ui.util

import android.content.Context
import me.diksu.ultra.ksuApp

/**
 * 首次启动引导的持久化标记。
 * 只有「走完 / 跳过」才落盘；换一代引导就把 key 抬一版，老用户升级后能再看一次新设计。
 */
object OnboardingPrefs {
    private const val PREFS = "foxsu_onboarding"
    private const val KEY_SEEN = "seen_v3"

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun seen(context: Context): Boolean = runCatching {
        prefs(context).getBoolean(KEY_SEEN, false)
    }.getOrDefault(false)

    fun markSeen(context: Context) {
        runCatching { prefs(context).edit().putBoolean(KEY_SEEN, true).apply() }
    }

    /** ksuApp 可能尚未初始化（Application.onCreate 之前的调用），这里兜底。 */
    fun seen(): Boolean = runCatching { seen(ksuApp) }.getOrDefault(true)
}
