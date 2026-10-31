package me.diksu.ultra.theme

import android.util.Log
import me.diksu.ultra.data.repository.currentThemeId

/**
 * The resolved theme of the moment, cached so composition never re-parses a package.
 *
 * Resolution reads `theme.json`, runs the validator and builds a 53-role palette plus a
 * 14-style typography set; at 60 fps that is not something to repeat per frame. The cache key
 * is `theme id + dark + amoled`, which is everything [ThemeResolver.resolve] depends on -
 * switching the device to dark or enabling AMOLED resolves once and then hits the cache.
 *
 * Call [invalidate] whenever the installed library or the selection changes; [ThemeStore]
 * does that for import, delete and selection.
 */
object ThemeRuntime {

    private const val TAG = "ThemeRuntime"

    private var cacheKey: String? = null
    private var cached: ResolvedTheme? = null

    /**
     * The theme the `Themed` style should render right now, or null when none is selected,
     * the package is gone, or resolution failed - in every one of those cases the caller must
     * fall back to the plain Miuix rendering, never to a half-built palette.
     */
    @Synchronized
    fun currentResolved(dark: Boolean, amoled: Boolean): ResolvedTheme? {
        val id = currentThemeId()
        if (id == null) {
            cacheKey = null
            cached = null
            return null
        }
        val key = "$id|$dark|$amoled"
        if (key == cacheKey) return cached
        val installed = ThemeStore.find(id)
        cached = installed?.let { theme ->
            runCatching { ThemeResolver.resolve(theme, dark, amoled) }
                .onFailure { Log.w(TAG, "could not resolve theme ${theme.id}", it) }
                .getOrNull()
        }
        cacheKey = key
        return cached
    }

    /** Drops the cached resolution; the next [currentResolved] reads the package again. */
    @Synchronized
    fun invalidate() {
        cacheKey = null
        cached = null
    }
}
