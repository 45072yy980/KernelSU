package me.diksu.ultra.ui

import androidx.annotation.StringRes
import androidx.compose.runtime.staticCompositionLocalOf
import me.diksu.ultra.R

enum class UiMode(val value: String, @param:StringRes val labelRes: Int) {
    Miuix("miuix", R.string.ui_mode_name_miuix),
    MiuixStock("miuix_stock", R.string.ui_mode_name_miuix_stock),
    Material("material", R.string.ui_mode_name_material),

    /**
     * Custom theme style. It reuses the Miuix structure and, for now, the Miuix look:
     * everything a theme may change is fed by `LocalThemeTokens`, and with no theme
     * loaded those tokens hold the values the Miuix screens already hard-code. So this
     * mode has to render exactly like [Miuix] - see `docs/theme-freedom-plan.md`.
     */
    Themed("themed", R.string.ui_mode_name_themed);

    companion object {
        fun fromValue(value: String): UiMode = when (value) {
            Themed.value -> Themed
            Material.value -> Material
            MiuixStock.value -> MiuixStock
            else -> Miuix
        }

        val DEFAULT_VALUE = Miuix.value
    }
}

val LocalUiMode = staticCompositionLocalOf { UiMode.Miuix }

/** True for the miuix variants: structural miuix code (scaffolds, components) applies to all three. */
val UiMode.isMiuixFamily: Boolean
    get() = this == UiMode.Miuix || this == UiMode.MiuixStock || this == UiMode.Themed

/**
 * True for the decorated miuix look - the wallpaper/glass variant and custom themes built on
 * top of it. [UiMode.MiuixStock] is the same components without that decoration, so it stays
 * out. Use this (not [isMiuixFamily]) at the places that deliberately tell 美化版 from 原版,
 * otherwise a custom theme would lose the wallpaper and the matching bar/tint.
 */
val UiMode.isMiuixLook: Boolean
    get() = this == UiMode.Miuix || this == UiMode.Themed
