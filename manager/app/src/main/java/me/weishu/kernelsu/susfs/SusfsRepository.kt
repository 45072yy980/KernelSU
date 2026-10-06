package me.weishu.kernelsu.susfs

import android.content.Context
import me.weishu.kernelsu.ksuApp

/**
 * Persists the SuSFS screen's own settings.
 *
 * The kernel keeps none of this: the switches below are re-sent to the
 * module on demand, and what the user picked last is remembered here so the
 * screen comes back the way they left it. Stored in the app's private
 * SharedPreferences — nothing about SuSFS is written outside it.
 */
object SusfsRepository {
    private const val PREFS = "susfs_settings"
    private const val KEY_LOG = "log_enabled"
    private const val KEY_AVC = "avc_log_spoofing"
    private const val KEY_HIDE_MNTS = "hide_sus_mnts"
    private const val KEY_UNAME_RELEASE = "uname_release"
    private const val KEY_UNAME_VERSION = "uname_version"

    private fun prefs(context: Context = ksuApp) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    var logEnabled: Boolean
        get() = prefs().getBoolean(KEY_LOG, false)
        set(value) = prefs().edit().putBoolean(KEY_LOG, value).apply()

    var avcLogSpoofing: Boolean
        get() = prefs().getBoolean(KEY_AVC, false)
        set(value) = prefs().edit().putBoolean(KEY_AVC, value).apply()

    var hideSusMntsForNonSuProcs: Boolean
        get() = prefs().getBoolean(KEY_HIDE_MNTS, false)
        set(value) = prefs().edit().putBoolean(KEY_HIDE_MNTS, value).apply()

    var unameRelease: String
        get() = prefs().getString(KEY_UNAME_RELEASE, "").orEmpty()
        set(value) = prefs().edit().putString(KEY_UNAME_RELEASE, value).apply()

    var unameVersion: String
        get() = prefs().getString(KEY_UNAME_VERSION, "").orEmpty()
        set(value) = prefs().edit().putString(KEY_UNAME_VERSION, value).apply()
}