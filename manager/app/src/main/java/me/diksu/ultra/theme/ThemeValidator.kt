package me.diksu.ultra.theme

import kotlinx.serialization.json.JsonElement

/**
 * What the importer can say about a package: which fields will be used, which were dropped
 * (with the reason), and anything that failed at archive level.
 *
 * `applied` / `ignored` entries are **field paths** (`color.seed`, `surfaces.home.day`,
 * `folksu.isMusicEnabled`), not sentences: the import preview localises them, the log keeps
 * them greppable. `ignored` always carries a reason in parentheses.
 */
data class ThemeValidation(
    val applied: List<String>,
    val ignored: List<String>,
    val errors: List<String>,
    val spec: ThemeSpec?,
)

/**
 * A manifest that passed validation. Values are already normalised: an invalid colour is
 * `null` here rather than the raw string, so a later applier never has to re-check.
 *
 * `layout` and `folksu` are **not** part of the applied set this version - the model keeps
 * them only so an export can write them back.
 */
data class ThemeSpec(
    val name: String,
    val author: String,
    val version: String,
    val type: String,
    val seed: String?,
    val roles: Map<String, String>,
    val darkSeed: String?,
    val darkRoles: Map<String, String>,
    val amoledBackground: String?,
    val fontMode: String?,
    val fontFile: String?,
    val fontStyles: Map<String, FontStyle>,
    val shape: Shape,
    val effect: Effect,
    val layout: Layout,
    val surfaces: Map<String, Surface>,
    val folksu: Map<String, JsonElement>,
) {
    data class FontStyle(val size: Double?, val weight: Int?, val lineHeight: Double?)

    data class Shape(
        val cardRadius: Double?,
        val buttonRadius: Double?,
        val rowMinHeight: Double?,
        val pagePadding: Double?,
    )

    data class Effect(val cardShadow: String?, val blurBackground: Double?)

    data class Layout(
        val home: String?,
        val moduleList: String?,
        val bottomBar: String?,
        val bottomBarLabels: Boolean?,
        val sectionOrder: List<String>?,
    )

    data class Surface(
        val day: String?,
        val night: String?,
        val opacity: Double?,
        val dim: Double?,
        val blur: Double?,
    )
}

/**
 * Per-field validation of a [ThemeManifest] against the assets actually present in the
 * package. Nothing here fails the import: a bad field is dropped and named in `ignored`,
 * the rest of the theme still applies - "worst case it looks default", never a crash and
 * never a half-applied claim.
 *
 * This file is pure JVM so it can be unit-tested; it does not read disk, it is handed the
 * list of entry names from [ThemeContainer].
 */
object ThemeValidator {

    private val COLOR = Regex("^#(?:[0-9a-fA-F]{6}|[0-9a-fA-F]{8})$")
    private val ROLE_NAME = Regex("^[A-Za-z][A-Za-z0-9]{0,39}$")
    private val SECTION_ID = Regex("^[a-z][a-z0-9_]{0,31}$")

    private val FONT_MODES = setOf("system", "app", "custom")
    private val CARD_SHADOWS = setOf("none", "soft", "strong")
    private val HOME_LAYOUTS = setOf("classic", "focus", "dashboard", "stats")
    private val MODULE_LISTS = setOf("cards", "grid")
    private val BOTTOM_BARS = setOf("docked", "floating")

    /** Surfaces we actually draw a background for; anything else is listed as ignored. */
    private val SUPPORTED_SURFACES = setOf("home", "settings", "modules", "repo", "about", "superuser")

    private const val NOT_APPLIED = "not applied in this version"

    fun validate(manifest: ThemeManifest, entryNames: Set<String>): ThemeValidation {
        val applied = mutableListOf<String>()
        val ignored = manifest.problems.toMutableList()
        val errors = mutableListOf<String>()

        val meta = manifest.meta
        val name = text(meta?.name, "meta.name", applied) ?: ""
        val author = text(meta?.author, "meta.author", applied) ?: ""
        val version = text(meta?.version, "meta.version", applied) ?: "1"
        val type = meta?.type?.let { raw ->
            if (raw == "phone" || raw == "tablet") {
                applied += "meta.type"
                raw
            } else {
                ignored += "meta.type (expected phone or tablet)"
                "phone"
            }
        } ?: "phone"

        val colorSection = manifest.color
        val seed = validColor(colorSection?.seed, "color.seed", applied, ignored)
        val roleColors = validRoles(colorSection?.roles, "color.roles", applied, ignored)
        val darkSeed = validColor(colorSection?.dark?.seed, "color.dark.seed", applied, ignored)
        val darkRoleColors = validRoles(colorSection?.dark?.roles, "color.dark.roles", applied, ignored)
        val amoled = validColor(colorSection?.amoled?.background, "color.amoled.background", applied, ignored)

        val fontSection = manifest.font
        val fontMode = allowedValue(fontSection?.mode, "font.mode", FONT_MODES, applied, ignored)
        val fontFile = asset(fontSection?.file, "font.file", entryNames, applied, ignored)
        if (fontMode == "custom" && fontFile == null) {
            ignored += "font.file (custom font without a usable file)"
        }
        val fontStyles = fontSection?.styles?.mapNotNull { (style, value) ->
            val size = range(value.size, "font.styles.$style.size", 8.0, 48.0, applied, ignored)
            val weight = intRange(value.weight, "font.styles.$style.weight", 100, 900, applied, ignored)
            val lineHeight = range(value.lineHeight, "font.styles.$style.lineHeight", 8.0, 72.0, applied, ignored)
            if (size == null && weight == null && lineHeight == null) {
                null
            } else {
                style to ThemeSpec.FontStyle(size, weight, lineHeight)
            }
        }?.toMap() ?: emptyMap()

        val shapeSection = manifest.shape
        val shapeSpec = ThemeSpec.Shape(
            cardRadius = range(shapeSection?.cardRadius, "shape.cardRadius", 0.0, 48.0, applied, ignored),
            buttonRadius = range(shapeSection?.buttonRadius, "shape.buttonRadius", 0.0, 48.0, applied, ignored),
            rowMinHeight = range(shapeSection?.rowMinHeight, "shape.rowMinHeight", 24.0, 120.0, applied, ignored),
            pagePadding = range(shapeSection?.pagePadding, "shape.pagePadding", 0.0, 48.0, applied, ignored),
        )

        val effectSection = manifest.effect
        val effectSpec = ThemeSpec.Effect(
            cardShadow = allowedValue(effectSection?.cardShadow, "effect.cardShadow", CARD_SHADOWS, applied, ignored),
            blurBackground = range(effectSection?.blurBackground, "effect.blurBackground", 0.0, 1.0, applied, ignored),
        )

        // L3 is not implemented yet: the layout fields are validated into the model but
        // reported as not applied, rather than promising an effect they do not have.
        val layoutSection = manifest.layout
        val layoutSpec = ThemeSpec.Layout(
            home = allowedValue(
                layoutSection?.home, "layout.home", HOME_LAYOUTS, applied, ignored, NOT_APPLIED,
            ),
            moduleList = allowedValue(
                layoutSection?.moduleList, "layout.moduleList", MODULE_LISTS, applied, ignored, NOT_APPLIED,
            ),
            bottomBar = allowedValue(
                layoutSection?.bottomBar, "layout.bottomBar", BOTTOM_BARS, applied, ignored, NOT_APPLIED,
            ),
            bottomBarLabels = layoutSection?.bottomBarLabels?.also {
                ignored += "layout.bottomBarLabels ($NOT_APPLIED)"
            },
            sectionOrder = layoutSection?.sectionOrder?.also {
                ignored += "layout.sectionOrder ($NOT_APPLIED)"
            }?.filter { SECTION_ID.matches(it) },
        )

        val surfaces = manifest.surfaces.mapNotNull { (surfaceName, surface) ->
            if (surfaceName !in SUPPORTED_SURFACES) {
                ignored += "surfaces.$surfaceName (unsupported surface)"
                return@mapNotNull null
            }
            val day = asset(surface.day, "surfaces.$surfaceName.day", entryNames, applied, ignored)
            val night = asset(surface.night, "surfaces.$surfaceName.night", entryNames, applied, ignored)
            val opacity = range(surface.opacity, "surfaces.$surfaceName.opacity", 0.0, 1.0, applied, ignored)
            val dim = range(surface.dim, "surfaces.$surfaceName.dim", 0.0, 1.0, applied, ignored)
            val blur = range(surface.blur, "surfaces.$surfaceName.blur", 0.0, 1.0, applied, ignored)
            if (day == null && night == null && opacity == null && dim == null && blur == null) {
                null
            } else {
                surfaceName to ThemeSpec.Surface(day, night, opacity, dim, blur)
            }
        }.toMap()

        for (key in manifest.folksu.keys) {
            ignored += "folksu.$key ($NOT_APPLIED)"
        }

        val spec = ThemeSpec(
            name = name,
            author = author,
            version = version,
            type = type,
            seed = seed,
            roles = roleColors,
            darkSeed = darkSeed,
            darkRoles = darkRoleColors,
            amoledBackground = amoled,
            fontMode = fontMode,
            fontFile = fontFile,
            fontStyles = fontStyles,
            shape = shapeSpec,
            effect = effectSpec,
            layout = layoutSpec,
            surfaces = surfaces,
            folksu = manifest.folksu,
        )
        return ThemeValidation(applied = applied, ignored = ignored, errors = errors, spec = spec)
    }

    private fun text(value: String?, path: String, applied: MutableList<String>): String? {
        if (value == null) return null
        val trimmed = value.trim()
        if (trimmed.isEmpty()) return null
        applied += path
        return trimmed
    }

    private fun validColor(
        value: String?,
        path: String,
        applied: MutableList<String>,
        ignored: MutableList<String>,
    ): String? {
        if (value == null) return null
        if (!COLOR.matches(value)) {
            ignored += "$path (expected #RRGGBB or #AARRGGBB)"
            return null
        }
        applied += path
        return value
    }

    private fun validRoles(
        map: Map<String, String>?,
        prefix: String,
        applied: MutableList<String>,
        ignored: MutableList<String>,
    ): Map<String, String> {
        if (map.isNullOrEmpty()) return emptyMap()
        val result = LinkedHashMap<String, String>()
        for ((role, value) in map) {
            val path = "$prefix.$role"
            if (!ROLE_NAME.matches(role)) {
                ignored += "$path (not a colour role name)"
                continue
            }
            if (!COLOR.matches(value)) {
                ignored += "$path (expected #RRGGBB or #AARRGGBB)"
                continue
            }
            applied += path
            result[role] = value
        }
        return result
    }

    private fun range(
        value: Double?,
        path: String,
        min: Double,
        max: Double,
        applied: MutableList<String>,
        ignored: MutableList<String>,
    ): Double? {
        if (value == null) return null
        if (value.isNaN() || value < min || value > max) {
            ignored += "$path (expected $min..$max)"
            return null
        }
        applied += path
        return value
    }

    private fun intRange(
        value: Int?,
        path: String,
        min: Int,
        max: Int,
        applied: MutableList<String>,
        ignored: MutableList<String>,
    ): Int? {
        if (value == null) return null
        if (value < min || value > max) {
            ignored += "$path (expected $min..$max)"
            return null
        }
        applied += path
        return value
    }

    private fun allowedValue(
        value: String?,
        path: String,
        allowed: Set<String>,
        applied: MutableList<String>,
        ignored: MutableList<String>,
        reason: String? = null,
    ): String? {
        if (value == null) return null
        if (value !in allowed) {
            ignored += "$path (expected one of ${allowed.joinToString("|")})"
            return null
        }
        if (reason == null) {
            applied += path
        } else {
            ignored += "$path ($reason)"
        }
        return value
    }

    private fun asset(
        value: String?,
        path: String,
        entryNames: Set<String>,
        applied: MutableList<String>,
        ignored: MutableList<String>,
    ): String? {
        if (value == null) return null
        if (value !in entryNames) {
            ignored += "$path (missing from the package)"
            return null
        }
        applied += path
        return value
    }
}
