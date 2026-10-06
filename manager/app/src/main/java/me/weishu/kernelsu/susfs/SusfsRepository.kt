package me.weishu.kernelsu.susfs

import android.content.Context
import me.weishu.kernelsu.ksuApp

/**
 * Persists the SuSFS screen's own settings and rule lists.
 *
 * The kernel keeps nothing across reboots: toggles, uname spoofing, and
 * every path / kstat / redirect rule the user adds are held in RAM only.
 * This repository mirrors them in the app's private SharedPreferences so
 * [SusfsBootReceiver] can replay them after boot.
 *
 * Rule lists are stored as `Set<String>`; redirects are packed as
 * `target||redirected||uidScheme` because SharedPreferences has no struct
 * type. Nothing about SuSFS is written outside the app's private storage.
 */
object SusfsRepository {
    private const val PREFS = "susfs_settings"
    private const val KEY_LOG = "log_enabled"
    private const val KEY_AVC = "avc_log_spoofing"
    private const val KEY_HIDE_MNTS = "hide_sus_mnts"
    private const val KEY_UNAME_RELEASE = "uname_release"
    private const val KEY_UNAME_VERSION = "uname_version"
    private const val KEY_SUS_PATHS = "sus_paths"
    private const val KEY_SUS_PATH_LOOPS = "sus_path_loops"
    private const val KEY_SUS_MAPS = "sus_maps"
    private const val KEY_SUS_KSTAT_PATHS = "sus_kstat_paths"
    private const val KEY_SUS_REDIRECTS = "sus_redirects"
    private const val KEY_BOOT_RETRY_COUNT = "boot_retry_count"

    /** Separator used when packing a redirect rule into one string. */
    private const val REDIRECT_SEP = "||"

    private fun prefs(context: Context = ksuApp) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun getStringSet(key: String): Set<String> =
        prefs().getStringSet(key, null)?.toSet() ?: emptySet()

    private fun putStringSet(key: String, value: Set<String>) {
        prefs().edit().putStringSet(key, value).apply()
    }

    // ── toggles ──────────────────────────────────────────────────────────────
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

    // ── hidden-path rules ────────────────────────────────────────────────────
    var susPaths: Set<String>
        get() = getStringSet(KEY_SUS_PATHS)
        set(value) = putStringSet(KEY_SUS_PATHS, value)

    fun addSusPath(path: String) { susPaths = susPaths + path }
    fun removeSusPath(path: String) { susPaths = susPaths - path }

    var susPathLoops: Set<String>
        get() = getStringSet(KEY_SUS_PATH_LOOPS)
        set(value) = putStringSet(KEY_SUS_PATH_LOOPS, value)

    fun addSusPathLoop(path: String) { susPathLoops = susPathLoops + path }
    fun removeSusPathLoop(path: String) { susPathLoops = susPathLoops - path }

    // ── sus-map rules ─────────────────────────────────────────────────────────
    var susMaps: Set<String>
        get() = getStringSet(KEY_SUS_MAPS)
        set(value) = putStringSet(KEY_SUS_MAPS, value)

    fun addSusMap(path: String) { susMaps = susMaps + path }
    fun removeSusMap(path: String) { susMaps = susMaps - path }

    // ── kstat paths ──────────────────────────────────────────────────────────
    /**
     * Paths whose kstat spoofing should be re-applied after boot.
     *
     * Only `update-sus-kstat` (not `add-sus-kstat`) is persisted: the add
     * step is a transient "begin tracking" call that only makes sense right
     * before a bind mount, whereas `update` is the operation that actually
     * installs the spoof and can be replayed safely once the mount exists.
     */
    var susKstatPaths: Set<String>
        get() = getStringSet(KEY_SUS_KSTAT_PATHS)
        set(value) = putStringSet(KEY_SUS_KSTAT_PATHS, value)

    fun addSusKstatPath(path: String) { susKstatPaths = susKstatPaths + path }
    fun removeSusKstatPath(path: String) { susKstatPaths = susKstatPaths - path }

    // ── open-redirect rules ───────────────────────────────────────────────────
    /**
     * Redirect rules packed as `target||redirected||uidScheme`.
     *
     * Use [addSusRedirect] / [removeSusRedirect] with structured args rather
     * than manipulating the raw set directly.
     */
    var susRedirects: Set<String>
        get() = getStringSet(KEY_SUS_REDIRECTS)
        set(value) = putStringSet(KEY_SUS_REDIRECTS, value)

    fun addSusRedirect(target: String, redirected: String, uidScheme: Int) {
        susRedirects = susRedirects + packRedirect(target, redirected, uidScheme)
    }

    fun removeSusRedirect(target: String, redirected: String, uidScheme: Int) {
        susRedirects = susRedirects - packRedirect(target, redirected, uidScheme)
    }

    /** Parse a packed redirect string into (target, redirected, uidScheme). */
    fun unpackRedirect(packed: String): Triple<String, String, Int> {
        val parts = packed.split(REDIRECT_SEP)
        val target = parts.getOrNull(0).orEmpty()
        val redirected = parts.getOrNull(1).orEmpty()
        val uidScheme = parts.getOrNull(2)?.toIntOrNull() ?: 0
        return Triple(target, redirected, uidScheme)
    }

    private fun packRedirect(target: String, redirected: String, uidScheme: Int) =
        "$target$REDIRECT_SEP$redirected$REDIRECT_SEP$uidScheme"

    // ── boot replay retry ─────────────────────────────────────────────────────
    /**
     * How many consecutive boot replays have failed.
     *
     * [SusfsBootReceiver] increments this when any rule fails to replay, and
     * resets it to zero on a fully successful replay. Once it reaches
     * [MAX_BOOT_RETRIES] the receiver gives up rather than looping forever on
     * a configuration that cannot be applied (e.g. a path that no longer
     * exists, or a kernel that rejected the supercall).
     */
    var bootRetryCount: Int
        get() = prefs().getInt(KEY_BOOT_RETRY_COUNT, 0)
        set(value) = prefs().edit().putInt(KEY_BOOT_RETRY_COUNT, value).apply()

    const val MAX_BOOT_RETRIES = 3
}
