package me.diksu.ultra.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.materialkolor.dynamiccolor.ColorSpec
import com.materialkolor.hct.Hct
import com.materialkolor.scheme.DynamicScheme
import com.materialkolor.scheme.SchemeTonalSpot
import top.yukonga.miuix.kmp.theme.Colors
import top.yukonga.miuix.kmp.theme.TextStyles
import top.yukonga.miuix.kmp.theme.darkColorScheme
import top.yukonga.miuix.kmp.theme.defaultTextStyles
import top.yukonga.miuix.kmp.theme.lightColorScheme
import java.io.File

/** A background for one page, already resolved to files on disk. Nothing is drawn yet. */
data class ResolvedSurface(
    val day: File?,
    val night: File?,
    val opacity: Double?,
    val dim: Double?,
    val blur: Double?,
)

/**
 * Everything the `Themed` style needs to render one installed theme in one light/dark state.
 *
 * [colors] is a complete Miuix palette (all 53 roles) and [textStyles] a complete 14-style
 * set, so a consumer never has to fill gaps - `docs/theme-freedom-plan.md` requires that a
 * broken or sparse theme can only ever look like the default, never like an uninitialised
 * surface.
 */
data class ResolvedTheme(
    val themeId: String,
    val name: String,
    val colors: Colors,
    val textStyles: TextStyles,
    val surfaces: Map<String, ResolvedSurface>,
    val fontFamily: FontFamily?,
    /**
     * False when the package says nothing about colours. The renderer then keeps the app's
     * own palette (so a font-only theme cannot repaint the app) and only takes typography.
     */
    val hasColors: Boolean,
    /** Fields the theme asked for but that had to be dropped; shown next to the applied ones. */
    val ignored: List<String>,
)

/**
 * Turns an [InstalledTheme] into concrete rendering values.
 *
 * Resolution is deliberately tolerant: the manifest was already validated field by field, and
 * anything that still fails here (an unreadable font file, a colour that does not parse) is
 * dropped and reported in [ResolvedTheme.ignored] while the remaining fields still apply.
 *
 * The colours are generated the same way Miuix generates its own dynamic palette - a material
 * kolor `SchemeTonalSpot` for the seed, mapped onto Miuix's 53 roles - so a theme that only
 * sets a seed produces exactly the palette the library itself would produce for that seed.
 * The mapping mirrors `mapMd3RolesToMiuixColorsCommon` of Miuix 0.9.4 (Apache-2.0), which is
 * internal to the library and therefore not callable from here.
 *
 * The palette style is fixed to TonalSpot and the contrast level to 0.0 on purpose: the
 * manifest has no field for either yet, and reading the app's own style would make the same
 * package look different from device to device.
 */
object ThemeResolver {

    private val STYLE_NAMES = listOf(
        "main", "paragraph", "body1", "body2", "button", "footnote1", "footnote2",
        "headline1", "headline2", "subtitle", "title1", "title2", "title3", "title4",
    )

    fun resolve(installed: InstalledTheme, dark: Boolean, amoled: Boolean): ResolvedTheme? {
        val dir = installed.dir
        val manifest = try {
            ThemeManifestParser.parse(File(dir, ThemeContainer.MANIFEST_FILE).readText(Charsets.UTF_8))
        } catch (e: Exception) {
            return null
        }
        val entries = dir.walkTopDown()
            .filter { it.isFile }
            .map { it.relativeTo(dir).path.replace(File.separatorChar, '/') }
            .toSet()
        val validation = ThemeValidator.validate(manifest, entries)
        val spec = validation.spec ?: return null

        val ignored = validation.ignored.toMutableList()

        // Colours -------------------------------------------------------------------------
        val lightSeed = parseColor(spec.seed)
        val darkSeed = parseColor(spec.darkSeed) ?: lightSeed
        val seed = if (dark) darkSeed else lightSeed
        // No seed at all: the library's own palette for that mode, which is exactly what the
        // rest of the app shows, then the per-role overrides land on top.
        val base = if (seed != null) colorsFromSeed(seed, dark) else if (dark) darkColorScheme() else lightColorScheme()
        val roles = if (dark && spec.darkRoles.isNotEmpty()) spec.darkRoles else spec.roles
        val withRoles = applyRoles(base, roles, ignored)
        val colors = if (dark && amoled) applyAmoled(withRoles, parseColor(spec.amoledBackground)) else withRoles

        // Typography ----------------------------------------------------------------------
        // "system" means keep the platform font even if the package ships a file.
        val wantsBundledFont = spec.fontMode != "system" && spec.fontFile != null
        val fontFile = spec.fontFile
            ?.takeIf { wantsBundledFont }
            ?.let { File(dir, it) }
            ?.takeIf { it.isFile }
        val fontFamily = fontFile?.let(::loadFontFamily)
        if (wantsBundledFont && fontFamily == null) {
            ignored += "font.file (could not be loaded as a font)"
        }
        val defaults = defaultTextStyles()
        val themedStyles = mutableMapOf<String, TextStyle>()
        for (name in STYLE_NAMES) {
            val baseStyle = defaults.styleOf(name)
            val overrides = spec.fontStyles[name]
            val withOverrides = if (overrides == null) {
                baseStyle
            } else {
                baseStyle.copy(
                    fontSize = overrides.size?.sp ?: baseStyle.fontSize,
                    fontWeight = overrides.weight?.let { FontWeight(it) } ?: baseStyle.fontWeight,
                    lineHeight = overrides.lineHeight?.sp ?: baseStyle.lineHeight,
                )
            }
            themedStyles[name] = if (fontFamily != null) {
                withOverrides.copy(fontFamily = fontFamily)
            } else {
                withOverrides
            }
        }
        val textStyles = TextStyles(
            main = themedStyles.getValue("main"),
            paragraph = themedStyles.getValue("paragraph"),
            body1 = themedStyles.getValue("body1"),
            body2 = themedStyles.getValue("body2"),
            button = themedStyles.getValue("button"),
            footnote1 = themedStyles.getValue("footnote1"),
            footnote2 = themedStyles.getValue("footnote2"),
            headline1 = themedStyles.getValue("headline1"),
            headline2 = themedStyles.getValue("headline2"),
            subtitle = themedStyles.getValue("subtitle"),
            title1 = themedStyles.getValue("title1"),
            title2 = themedStyles.getValue("title2"),
            title3 = themedStyles.getValue("title3"),
            title4 = themedStyles.getValue("title4"),
        )

        // Backgrounds ---------------------------------------------------------------------
        val surfaces = spec.surfaces.mapValues { (_, surface) ->
            ResolvedSurface(
                day = surface.day?.let { File(dir, it) }?.takeIf { it.isFile },
                night = surface.night?.let { File(dir, it) }?.takeIf { it.isFile },
                opacity = surface.opacity,
                dim = surface.dim,
                blur = surface.blur,
            )
        }

        return ResolvedTheme(
            themeId = installed.id,
            name = if (spec.name.isNotEmpty()) spec.name else installed.name,
            colors = colors,
            textStyles = textStyles,
            surfaces = surfaces,
            fontFamily = fontFamily,
            hasColors = spec.seed != null ||
                spec.darkSeed != null ||
                spec.roles.isNotEmpty() ||
                spec.darkRoles.isNotEmpty() ||
                spec.amoledBackground != null,
            ignored = ignored,
        )
    }

    /**
     * Loads the theme font, or null. The typeface is parsed eagerly: Compose loads it lazily,
     * so a corrupt `.ttf` would otherwise fail during layout instead of falling back here.
     */
    private fun loadFontFamily(file: File): FontFamily? = runCatching {
        android.graphics.Typeface.createFromFile(file)
        FontFamily(Font(file))
    }.getOrNull()

    /** `#RRGGBB` / `#AARRGGBB` -> Color; anything else is null (never throws). */
    fun parseColor(value: String?): Color? {
        if (value == null || !value.startsWith("#")) return null
        val hex = value.substring(1)
        if (hex.length != 6 && hex.length != 8) return null
        val numeric = hex.toLongOrNull(16) ?: return null
        val argb = if (hex.length == 6) numeric or 0xFF000000L else numeric
        return Color(argb.toInt())
    }

    // ---- palette -------------------------------------------------------------------------

    /**
     * Miuix's own seed -> palette path for the TonalSpot style (see the object comment for why
     * it is reproduced here).
     */
    private fun colorsFromSeed(seed: Color, dark: Boolean): Colors {
        val scheme = SchemeTonalSpot(
            sourceColorHct = Hct.fromInt(seed.toArgb()),
            isDark = dark,
            contrastLevel = 0.0,
            specVersion = ColorSpec.SpecVersion.SPEC_2021,
            platform = DynamicScheme.Platform.PHONE,
        )
        val roles = SchemeRoles(
            primary = Color(scheme.primary),
            onPrimary = Color(scheme.onPrimary),
            primaryFixed = Color(scheme.primaryFixed),
            onPrimaryFixed = Color(scheme.onPrimaryFixed),
            error = Color(scheme.error),
            onError = Color(scheme.onError),
            errorContainer = Color(scheme.errorContainer),
            onErrorContainer = Color(scheme.onErrorContainer),
            primaryContainer = Color(scheme.primaryContainer),
            onPrimaryContainer = Color(scheme.onPrimaryContainer),
            secondary = Color(scheme.secondary),
            onSecondary = Color(scheme.onSecondary),
            secondaryContainer = Color(scheme.secondaryContainer),
            onSecondaryContainer = Color(scheme.onSecondaryContainer),
            tertiaryContainer = Color(scheme.tertiaryContainer),
            onTertiaryContainer = Color(scheme.onTertiaryContainer),
            background = Color(scheme.background),
            onBackground = Color(scheme.onBackground),
            surface = Color(scheme.surface),
            onSurface = Color(scheme.onSurface),
            surfaceVariant = Color(scheme.surfaceVariant),
            surfaceContainer = Color(scheme.surfaceContainer),
            surfaceContainerHigh = Color(scheme.surfaceContainerHigh),
            surfaceContainerHighest = Color(scheme.surfaceContainerHighest),
            outline = Color(scheme.outline),
            outlineVariant = Color(scheme.outlineVariant),
            onSurfaceVariant = Color(scheme.onSurfaceVariant),
        )
        return mapRoles(roles, dark)
    }

    private data class SchemeRoles(
        val primary: Color,
        val onPrimary: Color,
        val primaryFixed: Color,
        val onPrimaryFixed: Color,
        val error: Color,
        val onError: Color,
        val errorContainer: Color,
        val onErrorContainer: Color,
        val primaryContainer: Color,
        val onPrimaryContainer: Color,
        val secondary: Color,
        val onSecondary: Color,
        val secondaryContainer: Color,
        val onSecondaryContainer: Color,
        val tertiaryContainer: Color,
        val onTertiaryContainer: Color,
        val background: Color,
        val onBackground: Color,
        val surface: Color,
        val onSurface: Color,
        val surfaceVariant: Color,
        val surfaceContainer: Color,
        val surfaceContainerHigh: Color,
        val surfaceContainerHighest: Color,
        val outline: Color,
        val outlineVariant: Color,
        val onSurfaceVariant: Color,
    )

    /** Mirrors Miuix 0.9.4's internal mapping, including its alpha flattening. */
    private fun mapRoles(roles: SchemeRoles, dark: Boolean): Colors {
        val baseSurface = roles.surface
        val baseSurfaceContainerHigh = roles.surfaceContainerHigh
        val onSurfaceSecondaryOpaque = ensureOpaqueOver(roles.onSurface.copy(alpha = 0.8f), baseSurface)
        val onSurfaceContainerHighOpaque =
            ensureOpaqueOver(roles.onSurface.copy(alpha = 0.8f), baseSurfaceContainerHigh)
        val sliderBackground = ensureOpaqueOver(roles.primary.copy(alpha = 0.2f), baseSurface)
        val disabledPrimaryOpaque = ensureOpaqueOver(roles.primary.copy(alpha = 0.38f), baseSurface)
        val disabledOnPrimaryOpaque = ensureOpaqueOver(roles.onPrimary.copy(alpha = 0.38f), disabledPrimaryOpaque)
        val disabledPrimaryButtonOpaque = ensureOpaqueOver(roles.primary.copy(alpha = 0.38f), baseSurface)
        val disabledOnPrimaryButtonOpaque =
            ensureOpaqueOver(roles.onPrimary.copy(alpha = 0.6f), disabledPrimaryButtonOpaque)
        val disabledPrimarySliderOpaque = ensureOpaqueOver(roles.primary.copy(alpha = 0.38f), baseSurface)
        val disabledSecondaryOpaque = ensureOpaqueOver(roles.outlineVariant.copy(alpha = 0.5f), baseSurface)
        val disabledOnSecondaryOpaque = ensureOpaqueOver(roles.onSurface.copy(alpha = 0.38f), disabledSecondaryOpaque)
        val disabledSecondaryVariantOpaque =
            ensureOpaqueOver(roles.surfaceContainerHigh.copy(alpha = 0.6f), baseSurface)
        val disabledOnSecondaryVariantOpaque =
            ensureOpaqueOver(roles.onSurface.copy(alpha = 0.38f), disabledSecondaryVariantOpaque)

        return Colors(
            primary = roles.primary,
            onPrimary = roles.onPrimary,
            primaryVariant = roles.primaryFixed,
            onPrimaryVariant = roles.onPrimaryFixed,
            error = roles.error,
            onError = roles.onError,
            errorContainer = roles.errorContainer,
            onErrorContainer = roles.onErrorContainer,
            disabledPrimary = disabledPrimaryOpaque,
            disabledOnPrimary = disabledOnPrimaryOpaque,
            disabledPrimaryButton = disabledPrimaryButtonOpaque,
            disabledOnPrimaryButton = disabledOnPrimaryButtonOpaque,
            disabledPrimarySlider = disabledPrimarySliderOpaque,
            primaryContainer = roles.primaryContainer,
            onPrimaryContainer = roles.onPrimaryContainer,
            secondary = roles.outlineVariant,
            onSecondary = roles.outline,
            secondaryVariant = roles.surfaceContainerHigh,
            onSecondaryVariant = roles.onSurface,
            disabledSecondary = disabledSecondaryOpaque,
            disabledOnSecondary = disabledOnSecondaryOpaque,
            disabledSecondaryVariant = disabledSecondaryVariantOpaque,
            disabledOnSecondaryVariant = disabledOnSecondaryVariantOpaque,
            secondaryContainer = roles.secondaryContainer,
            onSecondaryContainer = roles.onSecondaryContainer,
            secondaryContainerVariant = roles.surfaceContainerHighest,
            onSecondaryContainerVariant = roles.onSurfaceVariant,
            tertiaryContainer = roles.tertiaryContainer,
            onTertiaryContainer = roles.onTertiaryContainer,
            tertiaryContainerVariant = roles.onTertiaryContainer,
            background = roles.background,
            onBackground = roles.onBackground,
            onBackgroundVariant = roles.primary,
            surface = roles.surface,
            onSurface = roles.onSurface,
            surfaceVariant = roles.surfaceVariant,
            onSurfaceSecondary = onSurfaceSecondaryOpaque,
            onSurfaceVariantSummary = roles.onSurfaceVariant,
            onSurfaceVariantActions = roles.onSurfaceVariant,
            disabledOnSurface = roles.onSurface,
            surfaceContainer = roles.surfaceContainer,
            onSurfaceContainer = roles.onSurface,
            onSurfaceContainerVariant = roles.onSurfaceVariant,
            surfaceContainerHigh = roles.surfaceContainerHigh,
            onSurfaceContainerHigh = onSurfaceContainerHighOpaque,
            surfaceContainerHighest = roles.surfaceContainerHighest,
            onSurfaceContainerHighest = roles.onSurface,
            outline = roles.outline,
            dividerLine = roles.outlineVariant,
            windowDimming = if (dark) Color.Black.copy(alpha = 0.6f) else Color.Black.copy(alpha = 0.3f),
            sliderKeyPoint = roles.primary,
            sliderKeyPointForeground = roles.surfaceContainerHigh,
            sliderBackground = sliderBackground,
        )
    }

    /**
     * Applies the theme's per-role overrides. Unknown role names are reported and skipped, so
     * a typo in a package can never produce a transparent hole.
     */
    private fun applyRoles(
        base: Colors,
        roles: Map<String, String>,
        ignored: MutableList<String>,
    ): Colors {
        var colors = base
        for ((role, raw) in roles) {
            val color = parseColor(raw)
            if (color == null) {
                ignored += "color.roles.$role (not a colour)"
                continue
            }
            val next = colors.withRole(role, color)
            if (next === colors) {
                ignored += "color.roles.$role (unknown role)"
            }
            colors = next
        }
        return colors
    }

    /** `background` and `surface` go black for AMOLED; containers keep the generated tone. */
    private fun applyAmoled(base: Colors, background: Color?): Colors =
        if (background == null) base else base.copy(background = background, surface = background)

    private fun Colors.withRole(role: String, color: Color): Colors = when (role) {
        "primary" -> copy(primary = color)
        "onPrimary" -> copy(onPrimary = color)
        "primaryVariant" -> copy(primaryVariant = color)
        "onPrimaryVariant" -> copy(onPrimaryVariant = color)
        "error" -> copy(error = color)
        "onError" -> copy(onError = color)
        "errorContainer" -> copy(errorContainer = color)
        "onErrorContainer" -> copy(onErrorContainer = color)
        "disabledPrimary" -> copy(disabledPrimary = color)
        "disabledOnPrimary" -> copy(disabledOnPrimary = color)
        "disabledPrimaryButton" -> copy(disabledPrimaryButton = color)
        "disabledOnPrimaryButton" -> copy(disabledOnPrimaryButton = color)
        "disabledPrimarySlider" -> copy(disabledPrimarySlider = color)
        "primaryContainer" -> copy(primaryContainer = color)
        "onPrimaryContainer" -> copy(onPrimaryContainer = color)
        "secondary" -> copy(secondary = color)
        "onSecondary" -> copy(onSecondary = color)
        "secondaryVariant" -> copy(secondaryVariant = color)
        "onSecondaryVariant" -> copy(onSecondaryVariant = color)
        "disabledSecondary" -> copy(disabledSecondary = color)
        "disabledOnSecondary" -> copy(disabledOnSecondary = color)
        "disabledSecondaryVariant" -> copy(disabledSecondaryVariant = color)
        "disabledOnSecondaryVariant" -> copy(disabledOnSecondaryVariant = color)
        "secondaryContainer" -> copy(secondaryContainer = color)
        "onSecondaryContainer" -> copy(onSecondaryContainer = color)
        "secondaryContainerVariant" -> copy(secondaryContainerVariant = color)
        "onSecondaryContainerVariant" -> copy(onSecondaryContainerVariant = color)
        "tertiaryContainer" -> copy(tertiaryContainer = color)
        "onTertiaryContainer" -> copy(onTertiaryContainer = color)
        "tertiaryContainerVariant" -> copy(tertiaryContainerVariant = color)
        "background" -> copy(background = color)
        "onBackground" -> copy(onBackground = color)
        "onBackgroundVariant" -> copy(onBackgroundVariant = color)
        "surface" -> copy(surface = color)
        "onSurface" -> copy(onSurface = color)
        "surfaceVariant" -> copy(surfaceVariant = color)
        "onSurfaceSecondary" -> copy(onSurfaceSecondary = color)
        "onSurfaceVariantSummary" -> copy(onSurfaceVariantSummary = color)
        "onSurfaceVariantActions" -> copy(onSurfaceVariantActions = color)
        "disabledOnSurface" -> copy(disabledOnSurface = color)
        "surfaceContainer" -> copy(surfaceContainer = color)
        "onSurfaceContainer" -> copy(onSurfaceContainer = color)
        "onSurfaceContainerVariant" -> copy(onSurfaceContainerVariant = color)
        "surfaceContainerHigh" -> copy(surfaceContainerHigh = color)
        "onSurfaceContainerHigh" -> copy(onSurfaceContainerHigh = color)
        "surfaceContainerHighest" -> copy(surfaceContainerHighest = color)
        "onSurfaceContainerHighest" -> copy(onSurfaceContainerHighest = color)
        "outline" -> copy(outline = color)
        "dividerLine" -> copy(dividerLine = color)
        "windowDimming" -> copy(windowDimming = color)
        "sliderKeyPoint" -> copy(sliderKeyPoint = color)
        "sliderKeyPointForeground" -> copy(sliderKeyPointForeground = color)
        "sliderBackground" -> copy(sliderBackground = color)
        else -> this
    }

    private fun compositeOver(foreground: Color, background: Color): Color {
        val fa = foreground.alpha
        val ba = background.alpha
        val outA = fa + ba * (1f - fa)
        if (outA == 0f) return Color(0f, 0f, 0f, 0f)
        val r = (foreground.red * fa + background.red * ba * (1f - fa)) / outA
        val g = (foreground.green * fa + background.green * ba * (1f - fa)) / outA
        val b = (foreground.blue * fa + background.blue * ba * (1f - fa)) / outA
        return Color(r, g, b, outA)
    }

    private fun ensureOpaqueOver(foreground: Color, background: Color): Color {
        if (foreground.alpha >= 1f) return foreground
        val c = compositeOver(foreground, background)
        return Color(c.red, c.green, c.blue, 1f)
    }

    private fun TextStyles.styleOf(name: String): TextStyle = when (name) {
        "main" -> main
        "paragraph" -> paragraph
        "body1" -> body1
        "body2" -> body2
        "button" -> button
        "footnote1" -> footnote1
        "footnote2" -> footnote2
        "headline1" -> headline1
        "headline2" -> headline2
        "subtitle" -> subtitle
        "title1" -> title1
        "title2" -> title2
        "title3" -> title3
        else -> title4
    }
}
