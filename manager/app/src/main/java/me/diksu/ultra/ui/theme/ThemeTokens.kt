package me.diksu.ultra.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Values for the parts of the UI **we** draw: cards, rows, page padding, the bottom bar.
 *
 * Miuix's own components keep their shapes and paddings inside the library (see
 * `docs/theme-freedom-plan.md` §0), so a theme can only move the numbers listed here.
 *
 * Every default below is the value the Miuix screens hard-code today, which is what makes
 * the token layer a no-op until something actually provides different tokens: the three
 * shipped skins never provide this local, and the `Themed` style provides [ThemeTokens]
 * untouched, so it renders exactly like [me.diksu.ultra.ui.UiMode.Miuix].
 *
 * Keep the defaults in sync with the components: if a screen changes its hard-coded 16.dp
 * to 18.dp, change it here as well, or the custom-theme style will drift from Miuix.
 *
 * @property cardRadius corner radius of the cards we draw ourselves (`WarningCard.kt:77`,
 *   `HomeMiuix.kt:360` and `:901` all use 16.dp).
 * @property cardBorderWidth no card draws a border today - `WarningCard` tints its fill instead.
 * @property cardElevation cards sit flat on the page; the one shadow in the app belongs to the
 *   jailbreak guard card (`HomeMiuix.kt:903`), not to the shared card look.
 * @property rowMinHeight single-line list row height. Miuix/M3 rows are library-owned and fixed
 *   at 56.dp; the token matters for rows we draw ourselves.
 * @property pagePadding horizontal page margin. `SettingsMiuix.kt:100` pads the list by 12.dp.
 * @property sectionGap space above a section title. `SmallTitle` in the settings page uses
 *   `padding(top = 12.dp)` (`SettingsMiuix.kt:107`, `:132`, `:172`).
 * @property bottomBarStyle docked or floating. Floating is opt-in, and the preference defaults
 *   to false (`SettingsRepositoryImpl.kt:201`), hence [BottomBarStyle.Docked].
 * @property bottomBarShowLabels the miuix bar draws a text label under every icon
 *   (`BottomBarMiuix.kt:152`).
 * @property sectionOrder ids of the settings sections, in the order the Miuix settings page
 *   renders them (`SettingsMiuix.kt:106`, `:131`, `:172`, `:228`, `:252`, `:299`).
 */
@Immutable
data class ThemeTokens(
    val cardRadius: Dp = 16.dp,
    val cardBorderWidth: Dp = 0.dp,
    val cardElevation: Dp = 0.dp,
    val rowMinHeight: Dp = 56.dp,
    val pagePadding: Dp = 12.dp,
    val sectionGap: Dp = 12.dp,
    val bottomBarStyle: BottomBarStyle = BottomBarStyle.Docked,
    val bottomBarShowLabels: Boolean = true,
    val sectionOrder: List<String> = DEFAULT_SECTION_ORDER,
)

/** How the navigation bar is drawn. Mirrors the `enableFloatingBottomBar` preference. */
enum class BottomBarStyle {
    /** Reserved strip along the bottom edge - the default. */
    Docked,

    /** Floating pill over the content, enabled per mode in the settings. */
    Floating,
}

/**
 * The settings sections in their current order. The Miuix page builds them as a fixed
 * sequence of items, so a theme can only reorder what this list names.
 */
val DEFAULT_SECTION_ORDER: List<String> = listOf(
    "general",
    "appearance",
    "features",
    "privacy",
    "about",
    "danger",
)

/**
 * Tokens for the current style.
 *
 * Default = the Miuix values, so a component may read this without caring which skin is
 * active: the three shipped styles never provide it and therefore always get the defaults,
 * which keeps their rendering byte-identical.
 */
val LocalThemeTokens = compositionLocalOf { ThemeTokens() }
