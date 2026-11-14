package me.diksu.ultra.i18n

import android.content.Context
import android.content.ContextWrapper
import android.content.res.Configuration
import android.content.res.Resources
import java.util.Locale

/**
 * 应用内语言：不跟随系统，也不依赖 per-app language API（那要 33+ 且要 appcompat）。
 *
 * 做法是在 `attachBaseContext` 里换一份 `Configuration`（语言）并用 [OverlayResources]
 * 盖住自定义语言包的词条。这样全项目 1400 多处 `stringResource(...)` 与
 * `context.getString(...)` 都不用改 —— 它们拿到的本来就是被包过的 Context。
 *
 * 选择值：
 * - [SYSTEM]：跟随系统，不做任何包装
 * - `locale:<tag>`：用内置语种（`zh-CN`、`ja`…）
 * - `pack:<id>`：用语言包，包里有 locale 就一并设上
 */
object AppLanguage {

    const val SYSTEM = "system"

    private const val PREFS = "app_language"
    private const val KEY_CHOICE = "choice"

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun choice(context: Context): String =
        prefs(context).getString(KEY_CHOICE, SYSTEM).orEmpty().ifEmpty { SYSTEM }

    fun setChoice(context: Context, choice: String) {
        prefs(context).edit().putString(KEY_CHOICE, choice).apply()
    }

    /** 当前选择对应的语言包；选了内置语种或跟随系统时为 null。 */
    fun currentPack(context: Context): LanguagePack? {
        val choice = choice(context)
        if (!choice.startsWith(PACK_PREFIX)) return null
        return LanguagePackStore(context).find(choice.removePrefix(PACK_PREFIX))
    }

    /** 当前生效的语言标签；跟随系统返回 null。 */
    fun currentLocaleTag(context: Context): String? {
        val choice = choice(context)
        return when {
            choice.startsWith(LOCALE_PREFIX) -> choice.removePrefix(LOCALE_PREFIX)
            choice.startsWith(PACK_PREFIX) -> currentPack(context)?.locale
            else -> null
        }
    }

    fun localeChoice(tag: String): String = LOCALE_PREFIX + tag
    fun packChoice(id: String): String = PACK_PREFIX + id

    /**
     * 包一层 Context：设定语言并叠加语言包词条。
     *
     * 跟随系统且没有语言包时原样返回，避免无谓的对象分配。
     */
    fun wrap(base: Context): Context {
        // 这里只能用传入的 base 读设置：Application 的 attachBaseContext 早于 ksuApp 赋值。
        val tag = currentLocaleTag(base)
        val pack = currentPack(base)
        if (tag == null && pack == null) return base
        val context = (base as? ContextWrapper)?.baseContext ?: base

        val localized: Context = if (tag != null) {
            runCatching {
                val locale = Locale.forLanguageTag(modernTag(tag))
                val config = Configuration(base.resources.configuration).apply {
                    setLocale(locale)
                    setLayoutDirection(locale)
                }
                context.createConfigurationContext(config)
            }.getOrDefault(base)
        } else {
            context
        }

        val strings = pack?.strings ?: return localized
        return OverlayContext(localized, strings)
    }

    /** 让 [OverlayResources] 的词条继续传给下一层包装。 */
    private class OverlayContext(
        base: Context,
        strings: Map<String, String>,
    ) : ContextWrapper(base) {
        private val overlaid by lazy { OverlayResources(base.resources, strings) }
        override fun getResources(): Resources = overlaid
    }

    /** 资源目录里的旧代码换成 BCP-47 现代代码，日期与 ICU 才认得。 */
    private fun modernTag(tag: String): String = when (tag) {
        "in" -> "id"
        "iw" -> "he"
        "tl" -> "fil"
        else -> tag
    }

    private const val LOCALE_PREFIX = "locale:"
    private const val PACK_PREFIX = "pack:"
}

/**
 * 先查语言包，再落回内置资源。
 *
 * 只覆盖 `getString`：项目里的文案全是 string，复数与字符串数组没有用到；
 * 真要用到再补，而不是先写一堆没人走的分支。
 */
internal class OverlayResources(
    base: Resources,
    private val overlay: Map<String, String>,
) : Resources(base.assets, base.displayMetrics, base.configuration) {

    override fun getString(id: Int): String = overlay[nameOf(id)] ?: super.getString(id)

    override fun getString(id: Int, vararg formatArgs: Any?): String {
        val custom = overlay[nameOf(id)] ?: return super.getString(id, *formatArgs)
        // 语言包里少写或多写占位符时 format 会抛；退回内置译文，不让界面因此崩掉。
        return runCatching { String.format(locale, custom, *formatArgs) }
            .getOrElse { super.getString(id, *formatArgs) }
    }

    private fun nameOf(id: Int): String =
        runCatching { getResourceEntryName(id) }.getOrNull().orEmpty()

    private val locale: Locale get() = configuration.locales[0] ?: Locale.getDefault()
}
