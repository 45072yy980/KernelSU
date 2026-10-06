package me.weishu.kernelsu.data.repository

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import androidx.core.content.edit
import com.materialkolor.PaletteStyle
import com.materialkolor.dynamiccolor.ColorSpec
import com.topjohnwu.superuser.ShellUtils
import me.weishu.kernelsu.Natives
import me.weishu.kernelsu.ksuApp
import me.weishu.kernelsu.magica.BootCompletedReceiver
import me.weishu.kernelsu.ui.UiMode
import me.weishu.kernelsu.ui.screen.modulerepo.RepoSort
import me.weishu.kernelsu.ui.util.execKsud
import me.weishu.kernelsu.ui.util.getFeaturePersistValue
import me.weishu.kernelsu.ui.util.getFeatureStatus
import java.security.SecureRandom

private const val SETTINGS_PREFS = "settings"
private const val KEY_USE_SOFT_REBOOT = "soft_reboot"
private const val KEY_PARTITION_GUARD = "partition_guard"
private const val KEY_RUNTIME_GUARD = "runtime_partition_guard"
private const val KEY_HOME_CARD_BLUR = "home_card_blur"
private const val KEY_DISABLE_PAGER_SWIPE = "disable_pager_swipe"
private const val KEY_SIMPLE_MODE = "simple_mode"
private const val KEY_SHOW_MORE_MODULE_INFO = "show_more_module_info"
private const val KEY_PAGER_MODE_MIGRATED = "pager_mode_migrated_to_native"

/**
 * One-time move off the cross-axis interceptor default.
 *
 * Builds before this one shipped `1` (cross-axis) as the default for
 * `pager_interception_mode`, and the first read of the setting wrote that value
 * into the prefs even when the user never opened the screen. Those devices are
 * stuck on the interceptor, whose vertical-drag handling is what made the home
 * list refuse to scroll when a drag started on a card.
 *
 * A stored `1` is ambiguous: it is either that old default or a deliberate
 * choice, and there is no way to tell them apart after the fact. So the reset
 * runs exactly once -- the first time this version reads the setting -- and
 * only when the stored value is `1`. Anyone who had picked cross-axis on
 * purpose can pick it again; anyone who had picked the other two is untouched.
 */
private fun migratePagerInterceptionMode() {
    val prefs = settingsPrefs()
    if (prefs.getBoolean(KEY_PAGER_MODE_MIGRATED, false)) {
        return
    }
    prefs.edit {
        if (prefs.getInt("pager_interception_mode", 0) == 1) {
            putInt("pager_interception_mode", 0)
        }
        putBoolean(KEY_PAGER_MODE_MIGRATED, true)
    }
}

private fun settingsPrefs() =
    ksuApp.getSharedPreferences(SETTINGS_PREFS, Context.MODE_PRIVATE)

/**
 * Prefix for the settings that only mean something to one UI mode.
 *
 * The three modes each draw their own colour scheme and their own blur, and a
 * value picked in one of them says nothing about the other two. They used to
 * share these keys, so switching modes carried a setting across and the screen
 * that owned it changed underneath. Each mode now keeps its own copy.
 *
 * The prefix is derived from the stored mode rather than from any in-memory
 * state, so a read never depends on who is asking. `ui_mode` is never itself
 * prefixed: it has to be one shared value for the app to know which set of the
 * rest to use.
 */
private fun uiPrefix(): String = when (settingsPrefs().getString("ui_mode", UiMode.DEFAULT_VALUE)) {
    UiMode.Material.value -> "material_"
    UiMode.MiuixStock.value -> "miuix_stock_"
    else -> "miuix_"
}

/** Namespace a mode-specific key for the mode that is currently selected. */
private fun modeKey(key: String) = uiPrefix() + key

/** Prefer soft reboot: always in jailbreak mode, or when the setting is enabled. */
fun isSoftRebootPreferred(): Boolean =
    Natives.isLateLoadMode || settingsPrefs().getBoolean(KEY_USE_SOFT_REBOOT, false)

/**
 * Whether the system-partition guard should run for a module install.
 *
 * It is always on in jailbreak (late-load) mode - that session cannot undo a
 * real partition write - and optional otherwise, following the user setting.
 */
fun isPartitionGuardEnabled(): Boolean =
    Natives.isLateLoadMode || settingsPrefs().getBoolean(KEY_PARTITION_GUARD, false)


fun setPartitionGuardEnabled(enabled: Boolean) {
    // commit() for the same reason as setRuntimeGuardEnabled(): the settings
    // screen re-reads this right after the call and an async apply() could make
    // the parent switch snap back.
    settingsPrefs().edit().putBoolean(KEY_PARTITION_GUARD, enabled).commit()
    // Turning the top-level protection off also disables the runtime layer: the
    // child switch is only meaningful while the parent is on.
    if (!enabled) {
        setRuntimeGuardEnabled(false)
    }
}

/**
 * The runtime (kernel-side) guard, an extra opt-in on top of the install-time
 * scan. It is OFF by default in every mode - including jailbreak (late-load) -
 * because it hooks very hot syscalls (write/writev) and a bug there can panic
 * the kernel. Users enable it deliberately.
 */
fun isRuntimeGuardEnabled(): Boolean =
    settingsPrefs().getBoolean(KEY_RUNTIME_GUARD, false)

fun setRuntimeGuardEnabled(enabled: Boolean) {
    // commit(), not apply(): the settings screen reads the value back right after
    // this returns, so an async write could make the switch snap back.
    settingsPrefs().edit().putBoolean(KEY_RUNTIME_GUARD, enabled).commit()
    // NOTE: pushing the value to the kernel is deliberately left to the caller
    // (the settings screen's LaunchedEffect). Pushing here too would fire the
    // same "ksud feature set" twice per toggle.
}

/**
 * Hand the runtime-guard state to the kernel, where the actual live-write
 * interception happens. Safe to call repeatedly; does nothing if the kernel
 * does not support the feature (e.g. an older LKM).
 */
fun syncRuntimeGuardToKernel(enabled: Boolean) {
    execKsud("feature set partition_guard_runtime ${if (enabled) 1 else 0}", newShell = true)
}

/**
 * Hide-root helpers that the jailbreak workflow benefits from. All three default
 * to OFF, so a plain install behaves exactly like upstream until the user opts in
 * from Basic settings.
 */


/** Render the "working" status card on the home page with a blurred (frosted) background. */
fun isHomeCardBlurEnabled(): Boolean =
    settingsPrefs().getBoolean(modeKey(KEY_HOME_CARD_BLUR), false)

fun setHomeCardBlurEnabled(enabled: Boolean) {
    settingsPrefs().edit().putBoolean(modeKey(KEY_HOME_CARD_BLUR), enabled).commit()
}

/** Stop the home pager from following a left/right swipe; navigation stays on the bottom bar. */
fun isPagerSwipeDisabled(): Boolean =
    settingsPrefs().getBoolean(KEY_DISABLE_PAGER_SWIPE, false)

fun setPagerSwipeDisabled(enabled: Boolean) {
    settingsPrefs().edit().putBoolean(KEY_DISABLE_PAGER_SWIPE, enabled).commit()
}

class SettingsRepositoryImpl : SettingsRepository {

    private companion object {
        private const val INTENT_TOKEN_KEY = "intent_token"
        private val secureRandom = SecureRandom()
    }

    private val prefs by lazy {
        settingsPrefs()
    }

    override var uiMode: String
        get() = prefs.getString("ui_mode", UiMode.DEFAULT_VALUE) ?: UiMode.DEFAULT_VALUE
        set(value) = prefs.edit { putString("ui_mode", value) }

    override var checkUpdate: Boolean
        get() = prefs.getBoolean("check_update", true)
        set(value) = prefs.edit { putBoolean("check_update", value) }

    override var checkModuleUpdate: Boolean
        get() = prefs.getBoolean("module_check_update", true)
        set(value) = prefs.edit { putBoolean("module_check_update", value) }

    // Light, not "follow the system": the mode is settled by whether a picture is behind the pages
    // (see withWallpaperMode), and an app with no picture of its own is the light one.
    override var themeMode: Int
        get() = prefs.getInt(modeKey("color_mode"), 1)
        set(value) = prefs.edit { putInt(modeKey("color_mode"), value) }

    override var miuixMonet: Boolean
        get() = prefs.getBoolean(modeKey("miuix_monet"), false)
        set(value) = prefs.edit { putBoolean(modeKey("miuix_monet"), value) }

    override var keyColor: Int
        get() = prefs.getInt(modeKey("key_color"), 0)
        set(value) = prefs.edit { putInt(modeKey("key_color"), value) }

    override var colorStyle: String
        get() = prefs.getString(modeKey("color_style"), PaletteStyle.TonalSpot.name) ?: PaletteStyle.TonalSpot.name
        set(value) = prefs.edit { putString(modeKey("color_style"), value) }

    override var colorSpec: String
        get() = prefs.getString(modeKey("color_spec"), ColorSpec.SpecVersion.SPEC_2025.name) ?: ColorSpec.SpecVersion.SPEC_2025.name
        set(value) = prefs.edit { putString(modeKey("color_spec"), value) }

    override var enablePredictiveBack: Boolean
        get() = prefs.getBoolean("enable_predictive_back", false)
        set(value) = prefs.edit { putBoolean("enable_predictive_back", value) }

    override var enableSwipeDismiss: Boolean
        get() = prefs.getBoolean("enable_swipe_dismiss", true)
        set(value) = prefs.edit { putBoolean("enable_swipe_dismiss", value) }

    override var pagerInterceptionMode: Int
        // Default 0 (native pager gestures). The cross-axis interceptor, which used
        // to be the default, swallows a vertical drag that starts on a card: the
        // home list then refuses to scroll until the finger leaves the card. It was
        // inherited from the baseline and is a poor default; it stays available in
        // settings for anyone who wants the anti-mis-touch behaviour.
        get() {
            migratePagerInterceptionMode()
            return prefs.getInt("pager_interception_mode", 0)
        }
        set(value) = prefs.edit { putInt("pager_interception_mode", value.coerceIn(0, 2)) }

    override var enableBlur: Boolean
        get() = prefs.getBoolean(modeKey("enable_blur"), false)
        set(value) = prefs.edit { putBoolean(modeKey("enable_blur"), value) }

    override var enableFloatingBottomBar: Boolean
        get() = prefs.getBoolean(modeKey("enable_floating_bottom_bar"), false)
        set(value) = prefs.edit { putBoolean(modeKey("enable_floating_bottom_bar"), value) }

    override var enableFloatingBottomBarBlur: Boolean
        get() = prefs.getBoolean(modeKey("enable_floating_bottom_bar_blur"), false)
        set(value) = prefs.edit { putBoolean(modeKey("enable_floating_bottom_bar_blur"), value) }

    override var enableNavigationBadge: Boolean
        get() = prefs.getBoolean("enable_navigation_badge", true)
        set(value) = prefs.edit { putBoolean("enable_navigation_badge", value) }

    override var navigationRailExpanded: Boolean
        get() = prefs.getBoolean("nav_rail_expanded", false)
        set(value) = prefs.edit { putBoolean("nav_rail_expanded", value) }

    override var pageScale: Float
        get() = prefs.getFloat("page_scale", 1.0f)
        set(value) = prefs.edit { putFloat("page_scale", value) }

    override var moduleDescriptionMaxLines: Int
        get() = prefs.getInt("module_description_max_lines", 4)
        set(value) = prefs.edit { putInt("module_description_max_lines", value) }

    override var enableWebDebugging: Boolean
        get() = prefs.getBoolean("enable_web_debugging", false)
        set(value) = prefs.edit { putBoolean("enable_web_debugging", value) }

    override var moduleSortEnabledFirst: Boolean
        get() = prefs.getBoolean("module_sort_enabled_first", false)
        set(value) = prefs.edit { putBoolean("module_sort_enabled_first", value) }

    override var moduleSortActionFirst: Boolean
        get() = prefs.getBoolean("module_sort_action_first", false)
        set(value) = prefs.edit { putBoolean("module_sort_action_first", value) }

    override var moduleRepoSortOrder: Int
        get() = prefs.getInt("module_repo_sort_order", RepoSort.UPDATED.ordinal)
        set(value) = prefs.edit { putInt("module_repo_sort_order", value) }

    override var superuserShowSystemApps: Boolean
        get() = prefs.getBoolean("show_system_apps", false)
        set(value) = prefs.edit { putBoolean("show_system_apps", value) }

    override var superuserShowOnlyPrimaryUserApps: Boolean
        get() = prefs.getBoolean("show_only_primary_user_apps", false)
        set(value) = prefs.edit { putBoolean("show_only_primary_user_apps", value) }

    override var superuserSortOption: Int
        get() = prefs.getInt("superuser_sort_option", 0)
        set(value) = prefs.edit { putInt("superuser_sort_option", value) }

    override var suLogFilters: Set<String>?
        get() = prefs.getStringSet("sulog_filters", null)?.toSet()
        set(filters) = prefs.edit { putStringSet("sulog_filters", filters) }

    override var autoJailbreak: Boolean
        get() = prefs.getBoolean("auto_jailbreak", false)
        set(value) {
            runCatching {
                ksuApp.packageManager.setComponentEnabledSetting(
        ComponentName(ksuApp, com.mngr.app.magica.BootCompletedReceiver::class.java),
                    if (value) PackageManager.COMPONENT_ENABLED_STATE_ENABLED else PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                    PackageManager.DONT_KILL_APP
                )
            }.onFailure {
                Log.e("Settings", "failed to change boot receiver state to $value", it)
            }
            prefs.edit {
                putBoolean("auto_jailbreak", value)
            }
        }

    override var useSoftReboot: Boolean
        get() = prefs.getBoolean(KEY_USE_SOFT_REBOOT, false)
        set(value) = prefs.edit { putBoolean(KEY_USE_SOFT_REBOOT, value) }
    override var homeCardBlur: Boolean
        get() = prefs.getBoolean(modeKey(KEY_HOME_CARD_BLUR), false)
        set(value) = prefs.edit { putBoolean(modeKey(KEY_HOME_CARD_BLUR), value) }
    override var disablePagerSwipe: Boolean
        get() = prefs.getBoolean(KEY_DISABLE_PAGER_SWIPE, false)
        set(value) = prefs.edit { putBoolean(KEY_DISABLE_PAGER_SWIPE, value) }
    override var simpleMode: Boolean
        get() = prefs.getBoolean(modeKey(KEY_SIMPLE_MODE), false)
        set(value) = prefs.edit { putBoolean(modeKey(KEY_SIMPLE_MODE), value) }
    override var showMoreModuleInfo: Boolean
        get() = prefs.getBoolean(KEY_SHOW_MORE_MODULE_INFO, false)
        set(value) = prefs.edit { putBoolean(KEY_SHOW_MORE_MODULE_INFO, value) }

    override val intentToken: String
        get() {
        val existing = prefs.getString(INTENT_TOKEN_KEY, null)
        if (!existing.isNullOrBlank()) return existing
        val token = ByteArray(32).also(secureRandom::nextBytes)
            .joinToString(separator = "") { "%02x".format(it) }
        prefs.edit { putString(INTENT_TOKEN_KEY, token) }
        return token
    }

    override suspend fun getSuCompatStatus(): String = getFeatureStatus("su_compat")

    override suspend fun getSuCompatPersistValue(): Long? = getFeaturePersistValue("su_compat")

    override fun isSuEnabled(): Boolean = Natives.isSuEnabled()

    override fun setSuEnabled(enabled: Boolean): Boolean = Natives.setSuEnabled(enabled)

    override fun setSuCompatModePref(mode: Int) = prefs.edit { putInt("su_compat_mode", mode) }

    override fun getSuCompatModePref(): Int = prefs.getInt("su_compat_mode", 0)

    override suspend fun getKernelUmountStatus(): String = getFeatureStatus("kernel_umount")

    override fun isKernelUmountEnabled(): Boolean = Natives.isKernelUmountEnabled()

    override fun setKernelUmountEnabled(enabled: Boolean): Boolean = Natives.setKernelUmountEnabled(enabled)

    override suspend fun getSelinuxHideStatus(): String = getFeatureStatus("selinux_hide")

    override fun isSelinuxHideEnabled(): Boolean = Natives.isSelinuxHideEnabled()

    override fun setSelinuxHideEnabled(enabled: Boolean): Int = Natives.setSelinuxHideEnabled(enabled)

    override suspend fun getSulogStatus(): String = getFeatureStatus("sulog")

    override suspend fun getSulogPersistValue(): Long? = getFeaturePersistValue("sulog")

    override fun setSulogEnabled(enabled: Boolean): Boolean = execKsud("feature set sulog ${if (enabled) 1 else 0}", true)

    override suspend fun getAdbRootStatus(): String = getFeatureStatus("adb_root")

    override suspend fun getAdbRootPersistValue(): Long? = getFeaturePersistValue("adb_root")

    override fun setAdbRootEnabled(enabled: Boolean): Boolean =
        if (execKsud("feature set adb_root ${if (enabled) 1 else 0}", true)) {
            ShellUtils.fastCmd("setprop ctl.restart adbd")
            true
        } else {
            false
        }

    override fun isDefaultUmountModules(): Boolean = Natives.isDefaultUmountModules()

    override fun setDefaultUmountModules(enabled: Boolean): Boolean = Natives.setDefaultUmountModules(enabled)

    override fun isLkmMode(): Boolean = Natives.isLkmMode

    override fun execKsudFeatureSave() {
        execKsud("feature save", true)
    }
}
