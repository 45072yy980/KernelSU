package me.diksu.ultra.ui

import androidx.compose.runtime.staticCompositionLocalOf

/**
 * The look of the app.
 *
 * The first three are our own shell (`ui/MainActivity` + `ui/screen/…`). The last three are
 * FoxSu's styles: when one of them is selected our `MainActivity` hands the whole screen over
 * to `foxsu/ui/FoxSuHost`, so nothing from our own screens is composed in that case.
 *
 * The style lives in the shared `ui_mode` preference. Values are stable - they are persisted
 * and also written by FoxSu's own style switcher, so do not rename them.
 */
enum class UiMode(val value: String, val displayName: String) {
    Miuix("miuix", "miuix(美化版)"),
    MiuixStock("miuix_stock", "miuix(原版)"),
    Material("material", "Material 3"),

    /** FoxSu "Fresh" style. */
    Fresh("fresh", "Fresh"),

    /** FoxSu "Glass" style. */
    Glass("glass", "Glass"),

    /** FoxSu "Nocturne" style. */
    Nocturne("nocturne", "Nocturne");

    companion object {
        fun fromValue(value: String): UiMode = when (value) {
            Fresh.value -> Fresh
            Glass.value -> Glass
            Nocturne.value -> Nocturne
            Material.value -> Material
            MiuixStock.value -> MiuixStock
            else -> Miuix
        }

        /** Default stays the beautified Miuix shell - switching to FoxSu is opt-in. */
        val DEFAULT_VALUE = Miuix.value
    }
}

val LocalUiMode = staticCompositionLocalOf { UiMode.Miuix }

/**
 * True for every mode whose structure is the miuix one: our two miuix shells plus the three
 * FoxSu styles (FoxSu's screens are miuix-based as well). Material is the only one outside.
 */
val UiMode.isMiuixFamily: Boolean
    get() = this == UiMode.Miuix || this == UiMode.MiuixStock || isFoxSuStyle

/**
 * True for the three FoxSu styles. Those are rendered by `foxsu/ui/FoxSuHost`: our own
 * scaffold, pager and theme wrapper are skipped entirely, and so is our edge-to-edge setup.
 */
val UiMode.isFoxSuStyle: Boolean
    get() = this == UiMode.Fresh || this == UiMode.Glass || this == UiMode.Nocturne
