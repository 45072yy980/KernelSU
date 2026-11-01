package me.diksu.ultra.data.susfs

import org.json.JSONArray
import org.json.JSONObject

/**
 * 一份完整的 SuSFS 配置：开关 + 规则。
 *
 * 这是导入导出的单位，也是本地持久化的单位 —— 以前规则散在几个 SharedPreferences 列表里，
 * 想整体分享或回滚就得挨个拷，现在一份 JSON 说清楚。
 */
data class SusfsConfig(
    val version: Int = CURRENT_VERSION,
    /** 开关状态：键见 [KNOWN_SWITCHES]，缺省为 false。 */
    val enabled: Map<String, Boolean> = emptyMap(),
    val rules: List<SusfsRule> = emptyList(),
) {
    /** 同一 key 只保留最后一条：导入时"后来者覆盖"的语义。 */
    fun normalized(): SusfsConfig = copy(rules = rules.distinctBy { it.key })

    fun toJson(): JSONObject = JSONObject().apply {
        put("version", version)
        // 三个开关始终写出：关掉也要显式下发，否则内核会保持上一次的开启状态。
        put(
            "enabled",
            JSONObject().apply {
                KNOWN_SWITCHES.forEach { key -> put(key, enabled[key] ?: false) }
            },
        )
        put(
            "rules",
            JSONArray().also { array ->
                normalized().rules.forEach { array.put(ruleToJson(it)) }
            },
        )
    }

    fun toPrettyJson(): String = toJson().toString(2)

    companion object {
        /** 配置格式版本：改结构时加一，并在 [parse] 里兼容旧版。 */
        const val CURRENT_VERSION = 1

        /** 配置文件大小上限，与 ksud 侧的入参上限保持一致。 */
        const val MAX_BYTES = 128 * 1024

        /** 认识的开关。不认识的键在导入时丢弃，避免把别人的配置原样塞进来。 */
        val KNOWN_SWITCHES = listOf("log", "avc_log_spoof", "hide_mounts")

        /**
         * 解析一份配置。格式不对时抛 [SusfsConfigException] —— 调用方把原因显示给用户，
         * 不要静默吞掉：导入失败最常见的理由是选错了文件。
         */
        fun parse(text: String): SusfsConfig {
            // stdin 传给 ksud 时是命令行参数，太大就撑爆上限（也是防御性上限）。
            if (text.toByteArray().size > MAX_BYTES) {
                throw SusfsConfigException("配置过大：上限 ${MAX_BYTES / 1024} KiB")
            }
            val root = try {
                JSONObject(text)
            } catch (e: Exception) {
                throw SusfsConfigException("不是合法的 JSON：${e.message ?: "解析失败"}")
            }
            val version = root.optInt("version", 0)
            if (version <= 0 || version > CURRENT_VERSION) {
                throw SusfsConfigException("配置版本 $version 不支持（当前 $CURRENT_VERSION）")
            }
            val enabled = root.optJSONObject("enabled")?.let { obj ->
                buildMap {
                    obj.keys().forEach { key ->
                        if (key in KNOWN_SWITCHES) put(key, obj.optBoolean(key))
                    }
                }
            }.orEmpty()

            val rulesJson = root.optJSONArray("rules") ?: JSONArray()
            val rules = (0 until rulesJson.length()).mapNotNull { index ->
                ruleFromJson(rulesJson.optJSONObject(index))
            }
            val config = SusfsConfig(version, enabled, rules).normalized()
            val problems = SusfsConfigValidator.validate(config)
            if (problems.isNotEmpty()) {
                throw SusfsConfigException(problems.joinToString("；"))
            }
            return config
        }
    }
}

class SusfsConfigException(message: String) : Exception(message)

/**
 * 校验。规则是往内核里塞的路径和字符串，坏一条就可能在应用时把整批带崩，所以在导入时
 * 先挡下来，而不是等到 apply 才报错。
 */
object SusfsConfigValidator {
    const val MAX_RULES = 256
    private const val MAX_PATH_BYTES = 256

    /** 绝对路径、不含空段与 `.`/`..`、不含空白与控制字符、长度有限。 */
    fun validPath(path: String): Boolean {
        if (!path.startsWith("/") || path == "/") return false
        if (path.toByteArray(Charsets.UTF_8).size >= MAX_PATH_BYTES) return false
        if (path.contains("//")) return false
        if (path.any { it.isWhitespace() || it.isISOControl() }) return false
        return path.split('/').none { it == "." || it == ".." }
    }

    /** 返回问题列表；空表示通过。 */
    fun validate(config: SusfsConfig): List<String> {
        val problems = mutableListOf<String>()
        if (config.rules.size > MAX_RULES) {
            problems += "规则太多：${config.rules.size} 条，上限 $MAX_RULES"
        }
        config.rules.forEach { rule ->
            when (rule) {
                is SusfsRule.HiddenPath -> if (!validPath(rule.path)) problems += "路径不合法：${rule.path}"
                is SusfsRule.HiddenLoop -> if (!validPath(rule.path)) problems += "路径不合法：${rule.path}"
                is SusfsRule.SusMap -> if (!validPath(rule.path)) problems += "路径不合法：${rule.path}"
                is SusfsRule.Kstat -> if (!validPath(rule.path)) problems += "路径不合法：${rule.path}"
                is SusfsRule.KstatClone -> if (!validPath(rule.path)) problems += "路径不合法：${rule.path}"
                is SusfsRule.Cmdline -> if (!validPath(rule.sourcePath)) problems += "路径不合法：${rule.sourcePath}"
                is SusfsRule.Redirect -> {
                    if (!validPath(rule.target)) problems += "重定向源不合法：${rule.target}"
                    if (!validPath(rule.redirected)) problems += "重定向目标不合法：${rule.redirected}"
                    if (rule.uidScheme < 0) problems += "uidScheme 不能为负：${rule.uidScheme}"
                }
                is SusfsRule.Uname -> {
                    if (rule.release.isBlank()) problems += "release 不能为空"
                    if (rule.release.any { it.isISOControl() } || rule.version.any { it.isISOControl() }) {
                        problems += "uname 字段含控制字符"
                    }
                }
            }
        }
        return problems
    }
}

/** 新旧配置的差异，用于导入前的预览。 */
data class SusfsConfigDiff(
    val added: List<SusfsRule>,
    val removed: List<SusfsRule>,
    val switchesChanged: Map<String, Boolean>,
) {
    val isEmpty: Boolean get() = added.isEmpty() && removed.isEmpty() && switchesChanged.isEmpty()
}

fun SusfsConfig.diffFrom(current: SusfsConfig): SusfsConfigDiff {
    val mine = normalized().rules.associateBy { it.key }
    val theirs = current.normalized().rules.associateBy { it.key }
    val switches = buildMap {
        (enabled.keys + current.enabled.keys).forEach { key ->
            val after = enabled[key] ?: false
            val before = current.enabled[key] ?: false
            if (after != before) put(key, after)
        }
    }
    return SusfsConfigDiff(
        added = mine.values.filter { it.key !in theirs },
        removed = theirs.values.filter { it.key !in mine },
        switchesChanged = switches,
    )
}

// ── JSON 编解码 ────────────────────────────────────────────────────────────────

private fun ruleToJson(rule: SusfsRule): JSONObject = JSONObject().apply {
    when (rule) {
        is SusfsRule.HiddenPath -> { put("type", "path"); put("path", rule.path) }
        is SusfsRule.HiddenLoop -> { put("type", "loop"); put("path", rule.path) }
        is SusfsRule.SusMap -> { put("type", "map"); put("path", rule.path) }
        is SusfsRule.Kstat -> { put("type", "kstat"); put("path", rule.path) }
        is SusfsRule.KstatClone -> { put("type", "kstat_clone"); put("path", rule.path) }
        is SusfsRule.Cmdline -> { put("type", "cmdline"); put("path", rule.sourcePath) }
        is SusfsRule.Redirect -> {
            put("type", "redirect")
            put("target", rule.target)
            put("redirected", rule.redirected)
            put("uidScheme", rule.uidScheme)
        }
        is SusfsRule.Uname -> {
            put("type", "uname")
            put("release", rule.release)
            put("version", rule.version)
        }
    }
}

/** 不认识的类型返回 null：别人的新版本配置里有我们没实现的东西时，跳过而不是整份失败。 */
private fun ruleFromJson(obj: JSONObject?): SusfsRule? {
    if (obj == null) return null
    val path = obj.optString("path")
    return when (obj.optString("type")) {
        "path" -> SusfsRule.HiddenPath(path)
        "loop" -> SusfsRule.HiddenLoop(path)
        "map" -> SusfsRule.SusMap(path)
        "kstat" -> SusfsRule.Kstat(path)
        "kstat_clone" -> SusfsRule.KstatClone(path)
        "cmdline" -> SusfsRule.Cmdline(path)
        "redirect" -> SusfsRule.Redirect(
            target = obj.optString("target"),
            redirected = obj.optString("redirected"),
            uidScheme = obj.optInt("uidScheme", 0),
        )
        "uname" -> SusfsRule.Uname(
            release = obj.optString("release"),
            version = obj.optString("version"),
        )
        else -> null
    }
}
