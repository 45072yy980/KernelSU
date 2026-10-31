package me.diksu.ultra.foxsu.ui.screen.nocturne

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import me.diksu.ultra.ksuApp
import me.diksu.ultra.foxsu.ui.theme.isInDarkTheme
import java.util.Calendar

/**
 * Nocturne 外观设置 —— 真实持久化到 SharedPreferences。
 *
 * 模式：0 = Nocturne 暗夜 / 1 = Daylight 亮色 / 2 = 跟随系统
 * 强调色：对应 [NcAccents] 下标
 * 光晕强度：0 低 / 1 中 / 2 高
 * 跟随日出日落：18:30 - 06:30 强制暗夜，其余时间强制亮色
 */
object NcAppearance {
    private const val PREFS = "nocturne_appearance"
    private const val KEY_MODE = "nc_mode"
    private const val KEY_ACCENT = "nc_accent"
    private const val KEY_GLOW = "nc_glow"
    private const val KEY_SUNSET = "nc_sunset"

    private val prefs by lazy {
        ksuApp.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    }

    var mode by mutableIntStateOf(prefs.getInt(KEY_MODE, 1))
        private set
    var accentIndex by mutableIntStateOf(prefs.getInt(KEY_ACCENT, 0))
        private set
    var glowLevel by mutableIntStateOf(prefs.getInt(KEY_GLOW, 1))
        private set
    var followSunset by mutableStateOf(prefs.getBoolean(KEY_SUNSET, false))
        private set

    fun updateMode(value: Int) {
        mode = value.coerceIn(0, 2)
        prefs.edit().putInt(KEY_MODE, mode).apply()
    }

    /**
     * 当前是否处于暗夜外观。
     *
     * 全局明暗优先：设置里显式选了「浅色 / 暗色」时，Nocturne 用自己的亮色 / 暗夜配色表；
     * 选了「跟随系统」时，再回落到 Nocturne 自己的外观模式。
     */
    @Composable
    fun resolveDarkMode(): Boolean {
        when (me.diksu.ultra.foxsu.ui.theme.LocalColorMode.current) {
            1, 4 -> return false
            2, 5, 6 -> return true
        }
        if (followSunset) return isNightTime()
        return when (mode) {
            0 -> true
            1 -> false
            else -> isInDarkTheme()
        }
    }

    fun setAccent(index: Int) {
        accentIndex = index.coerceIn(NcAccents.indices)
        prefs.edit().putInt(KEY_ACCENT, accentIndex).apply()
    }

    fun setGlow(level: Int) {
        glowLevel = level.coerceIn(0, 2)
        prefs.edit().putInt(KEY_GLOW, glowLevel).apply()
    }

    fun updateFollowSunset(enabled: Boolean) {
        followSunset = enabled
        prefs.edit().putBoolean(KEY_SUNSET, enabled).apply()
    }

    /** 18:30 - 06:30 视为夜间。 */
    private fun isNightTime(): Boolean {
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        val minute = Calendar.getInstance().get(Calendar.MINUTE)
        val t = hour * 60 + minute
        return t >= 18 * 60 + 30 || t < 6 * 60 + 30
    }

    /** 模式显示名。 */
    fun modeLabel(): String = when (mode) {
        0 -> "Nocturne 暗夜"
        1 -> "Daylight 亮色"
        else -> "跟随系统"
    }

    fun glowLabel(): String = when (glowLevel) {
        0 -> "光晕强度 · 低"
        2 -> "光晕强度 · 高"
        else -> "光晕强度 · 中"
    }
}
