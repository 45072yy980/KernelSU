package me.diksu.ultra.i18n

import org.json.JSONObject

/**
 * 一份自定义语言包：只覆盖它显式给出的词条，其余仍用内置译文。
 *
 * [strings] 的键是 strings.xml 里的资源名（如 `shizuku_start`），不是资源 id ——
 * 资源 id 每次编译都会变，写死在文件里下次更新就全错位了。
 *
 * @param id 文件名（去扩展名），同时作为持久化里的身份
 * @param locale 可选的基础语种（`zh-CN`、`ja`…）。给了就用它决定日期/数字格式
 *   与布局方向；不给则沿用当前语言。
 */
data class LanguagePack(
    val id: String,
    val name: String,
    val locale: String?,
    val strings: Map<String, String>,
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("name", name)
        locale?.let { put("locale", it) }
        put("strings", JSONObject(strings as Map<*, *>))
    }

    companion object {
        /** 文件大小上限：语言包只是词条表，超过这个数基本不是语言包。 */
        const val MAX_BYTES = 512 * 1024

        /** 单条词条上限，防止把整段文档塞进来。 */
        private const val MAX_VALUE_LEN = 2000

        /**
         * 解析语言包。容忍两种写法：
         * - `{"name": "...", "locale": "ja", "strings": {...}}`
         * - 直接给一张扁平表 `{"shizuku_start": "..."}`
         *
         * 返回 null 表示这不是一个可用的语言包（不是 JSON、没有词条）。
         */
        fun parse(id: String, text: String, fallbackName: String): LanguagePack? {
            val root = runCatching { JSONObject(text) }.getOrNull() ?: return null
            val nested = root.optJSONObject("strings")
            val source = nested ?: root
            val strings = buildMap {
                source.keys().forEach { key ->
                    // 扁平写法下 name/locale 也是键，跳过元数据。
                    if (nested == null && key in META_KEYS) return@forEach
                    val value = runCatching { source.optString(key) }.getOrNull().orEmpty()
                    if (value.isNotEmpty() && value.length <= MAX_VALUE_LEN) {
                        put(key, value)
                    }
                }
            }
            if (strings.isEmpty()) return null
            val name = root.optString("name").ifBlank { fallbackName }
            val locale = root.optString("locale").takeIf { it.isNotBlank() }
            return LanguagePack(id = id, name = name, locale = locale, strings = strings)
        }

        private val META_KEYS = setOf("name", "locale", "strings", "version", "author")
    }
}
