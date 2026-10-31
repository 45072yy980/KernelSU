package me.diksu.ultra.theme

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull

/** The manifest is unreadable as a whole (not JSON, not an object, or a schema we do not know). */
class ThemeManifestException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * `theme.json` of a theme package, schema 1 - a superset of the FolkPatch/FinaSU file, so a
 * package made by either of them is still readable.
 *
 * Every field is optional: a theme may describe only the colours, only the font, or nothing
 * at all. Fields are **not** validated here - the parser only checks types, collects anything
 * it could not read into [problems] and never aborts on a single bad field. Semantic checks
 * (colour syntax, ranges, enum whitelists, asset existence) live in [ThemeValidator], which
 * turns the results into the `applied` / `ignored` lists the import preview shows.
 *
 * Unknown keys are ignored and reported; the whole `folksu` block is preserved verbatim so a
 * later version can map it without re-reading the package.
 *
 * @property problems paths that were present but unreadable, e.g. `color.seed (expected a
 *   string)` or `unknown: foo`. The validator merges these into its `ignored` list.
 */
data class ThemeManifest(
    val schema: Int,
    val meta: Meta?,
    val color: Color?,
    val font: Font?,
    val shape: Shape?,
    val effect: Effect?,
    val layout: Layout?,
    val surfaces: Map<String, Surface>,
    val folksu: Map<String, JsonElement>,
    val problems: List<String>,
) {
    data class Meta(val name: String?, val author: String?, val version: String?, val type: String?)

    data class Color(
        val seed: String?,
        val roles: Map<String, String>,
        val dark: Variant?,
        val amoled: Amoled?,
    )

    data class Variant(val seed: String?, val roles: Map<String, String>)

    data class Amoled(val background: String?)

    data class Font(val mode: String?, val file: String?, val styles: Map<String, FontStyle>)

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
 * Reads [ThemeManifest] from text. Pure JSON walking rather than kotlinx `@Serializable`
 * because a theme must survive bad fields: a `@Serializable` DTO would reject the document,
 * while here a broken field becomes one entry in [ThemeManifest.problems] and the rest loads.
 */
object ThemeManifestParser {

    const val SCHEMA_V1 = 1

    /** Hard limit on `layout.sectionOrder`; beyond that a package is not making sense. */
    const val MAX_SECTION_ORDER = 16

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = false
        explicitNulls = false
    }

    private val TOP_KEYS = setOf("schema", "meta", "color", "font", "shape", "effect", "layout", "surfaces", "folksu")
    private val META_KEYS = setOf("name", "author", "version", "type")
    private val COLOR_KEYS = setOf("seed", "roles", "dark", "amoled")
    private val VARIANT_KEYS = setOf("seed", "roles")
    private val AMOLED_KEYS = setOf("background")
    private val FONT_KEYS = setOf("mode", "file", "styles")
    private val FONT_STYLE_KEYS = setOf("size", "weight", "lineHeight")
    private val SHAPE_KEYS = setOf("cardRadius", "buttonRadius", "rowMinHeight", "pagePadding")
    private val EFFECT_KEYS = setOf("cardShadow", "blurBackground")
    private val LAYOUT_KEYS = setOf("home", "moduleList", "bottomBar", "bottomBarLabels", "sectionOrder")
    private val SURFACE_KEYS = setOf("day", "night", "opacity", "dim", "blur")

    fun parse(text: String): ThemeManifest {
        val root = try {
            json.parseToJsonElement(text)
        } catch (e: Exception) {
            throw ThemeManifestException("theme.json is not valid JSON", e)
        }
        val obj = root as? JsonObject
            ?: throw ThemeManifestException("theme.json must be a JSON object")

        val problems = mutableListOf<String>()
        obj.reportUnknown(TOP_KEYS, "", problems)

        val schema = obj.int("schema", "schema", problems) ?: SCHEMA_V1
        if (schema != SCHEMA_V1) {
            // Refuse rather than guess: a newer schema may change what a field means.
            throw ThemeManifestException("unsupported manifest schema $schema")
        }

        return ThemeManifest(
            schema = schema,
            meta = obj.obj("meta", "meta", problems)?.let { meta ->
                meta.reportUnknown(META_KEYS, "meta", problems)
                ThemeManifest.Meta(
                    name = meta.str("name", "meta.name", problems),
                    author = meta.str("author", "meta.author", problems),
                    version = meta.str("version", "meta.version", problems),
                    type = meta.str("type", "meta.type", problems),
                )
            },
            color = obj.obj("color", "color", problems)?.let { color ->
                color.reportUnknown(COLOR_KEYS, "color", problems)
                ThemeManifest.Color(
                    seed = color.str("seed", "color.seed", problems),
                    roles = color.stringMap("roles", "color.roles", problems),
                    dark = color.obj("dark", "color.dark", problems)?.let { variant ->
                        variant.reportUnknown(VARIANT_KEYS, "color.dark", problems)
                        ThemeManifest.Variant(
                            seed = variant.str("seed", "color.dark.seed", problems),
                            roles = variant.stringMap("roles", "color.dark.roles", problems),
                        )
                    },
                    amoled = color.obj("amoled", "color.amoled", problems)?.let { amoled ->
                        amoled.reportUnknown(AMOLED_KEYS, "color.amoled", problems)
                        ThemeManifest.Amoled(
                            background = amoled.str("background", "color.amoled.background", problems),
                        )
                    },
                )
            },
            font = obj.obj("font", "font", problems)?.let { font ->
                font.reportUnknown(FONT_KEYS, "font", problems)
                ThemeManifest.Font(
                    mode = font.str("mode", "font.mode", problems),
                    file = font.str("file", "font.file", problems),
                    styles = font.obj("styles", "font.styles", problems)?.let { styles ->
                        styles.mapValues { (style, element) ->
                            val styleObj = element as? JsonObject
                            if (styleObj == null) {
                                problems += "font.styles.$style (expected an object)"
                                ThemeManifest.FontStyle(null, null, null)
                            } else {
                                styleObj.reportUnknown(FONT_STYLE_KEYS, "font.styles.$style", problems)
                                ThemeManifest.FontStyle(
                                    size = styleObj.num("size", "font.styles.$style.size", problems),
                                    weight = styleObj.int("weight", "font.styles.$style.weight", problems),
                                    lineHeight = styleObj.num("lineHeight", "font.styles.$style.lineHeight", problems),
                                )
                            }
                        }
                    } ?: emptyMap(),
                )
            },
            shape = obj.obj("shape", "shape", problems)?.let { shape ->
                shape.reportUnknown(SHAPE_KEYS, "shape", problems)
                ThemeManifest.Shape(
                    cardRadius = shape.num("cardRadius", "shape.cardRadius", problems),
                    buttonRadius = shape.num("buttonRadius", "shape.buttonRadius", problems),
                    rowMinHeight = shape.num("rowMinHeight", "shape.rowMinHeight", problems),
                    pagePadding = shape.num("pagePadding", "shape.pagePadding", problems),
                )
            },
            effect = obj.obj("effect", "effect", problems)?.let { effect ->
                effect.reportUnknown(EFFECT_KEYS, "effect", problems)
                ThemeManifest.Effect(
                    cardShadow = effect.str("cardShadow", "effect.cardShadow", problems),
                    blurBackground = effect.num("blurBackground", "effect.blurBackground", problems),
                )
            },
            layout = obj.obj("layout", "layout", problems)?.let { layout ->
                layout.reportUnknown(LAYOUT_KEYS, "layout", problems)
                ThemeManifest.Layout(
                    home = layout.str("home", "layout.home", problems),
                    moduleList = layout.str("moduleList", "layout.moduleList", problems),
                    bottomBar = layout.str("bottomBar", "layout.bottomBar", problems),
                    bottomBarLabels = layout.bool("bottomBarLabels", "layout.bottomBarLabels", problems),
                    sectionOrder = layout.stringList("sectionOrder", "layout.sectionOrder", problems, MAX_SECTION_ORDER),
                )
            },
            surfaces = obj.obj("surfaces", "surfaces", problems)?.let { surfaces ->
                surfaces.mapValues { (name, element) ->
                    val surface = element as? JsonObject
                    if (surface == null) {
                        problems += "surfaces.$name (expected an object)"
                        ThemeManifest.Surface(null, null, null, null, null)
                    } else {
                        surface.reportUnknown(SURFACE_KEYS, "surfaces.$name", problems)
                        ThemeManifest.Surface(
                            day = surface.str("day", "surfaces.$name.day", problems),
                            night = surface.str("night", "surfaces.$name.night", problems),
                            opacity = surface.num("opacity", "surfaces.$name.opacity", problems),
                            dim = surface.num("dim", "surfaces.$name.dim", problems),
                            blur = surface.num("blur", "surfaces.$name.blur", problems),
                        )
                    }
                }
            } ?: emptyMap(),
            // Kept byte-for-byte as JSON so a later version can map it without a re-import.
            folksu = obj.obj("folksu", "folksu", problems)?.let { LinkedHashMap(it) } ?: emptyMap(),
            problems = problems.toList(),
        )
    }

    private fun JsonObject.reportUnknown(known: Set<String>, prefix: String, problems: MutableList<String>) {
        for (key in keys) {
            if (key !in known) problems += "unknown: ${if (prefix.isEmpty()) key else "$prefix.$key"}"
        }
    }

    private fun JsonObject.str(key: String, path: String, problems: MutableList<String>): String? {
        val element = this[key] ?: return null
        val primitive = element as? JsonPrimitive
        if (primitive == null || !primitive.isString) {
            problems += "$path (expected a string)"
            return null
        }
        return primitive.content
    }

    private fun JsonObject.num(key: String, path: String, problems: MutableList<String>): Double? {
        val element = this[key] ?: return null
        val primitive = element as? JsonPrimitive
        val value = if (primitive == null || primitive.isString) null else primitive.doubleOrNull
        if (value == null) {
            problems += "$path (expected a number)"
            return null
        }
        return value
    }

    private fun JsonObject.int(key: String, path: String, problems: MutableList<String>): Int? {
        val value = num(key, path, problems) ?: return null
        if (value % 1.0 != 0.0) {
            problems += "$path (expected an integer)"
            return null
        }
        return value.toInt()
    }

    private fun JsonObject.bool(key: String, path: String, problems: MutableList<String>): Boolean? {
        val element = this[key] ?: return null
        val primitive = element as? JsonPrimitive
        if (primitive == null || primitive.isString) {
            problems += "$path (expected true or false)"
            return null
        }
        val value = primitive.booleanOrNull
        if (value == null) {
            problems += "$path (expected true or false)"
            return null
        }
        return value
    }

    private fun JsonObject.obj(key: String, path: String, problems: MutableList<String>): JsonObject? {
        val element = this[key] ?: return null
        val value = element as? JsonObject
        if (value == null) problems += "$path (expected an object)"
        return value
    }

    private fun JsonObject.stringMap(key: String, path: String, problems: MutableList<String>): Map<String, String> {
        val obj = obj(key, path, problems) ?: return emptyMap()
        val result = LinkedHashMap<String, String>()
        for ((name, element) in obj) {
            val primitive = element as? JsonPrimitive
            if (primitive == null || !primitive.isString) {
                problems += "$path.$name (expected a string)"
            } else {
                result[name] = primitive.content
            }
        }
        return result
    }

    private fun JsonObject.stringList(
        key: String,
        path: String,
        problems: MutableList<String>,
        max: Int,
    ): List<String>? {
        val element = this[key] ?: return null
        val array = element as? JsonArray
        if (array == null) {
            problems += "$path (expected an array)"
            return null
        }
        if (array.size > max) {
            problems += "$path (more than $max items)"
            return null
        }
        val result = mutableListOf<String>()
        for ((index, item) in array.withIndex()) {
            val primitive = item as? JsonPrimitive
            if (primitive == null || !primitive.isString) {
                problems += "$path[$index] (expected a string)"
            } else {
                result += primitive.content
            }
        }
        return result
    }
}
