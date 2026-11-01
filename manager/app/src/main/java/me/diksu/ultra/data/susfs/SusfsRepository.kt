package me.diksu.ultra.data.susfs

import android.content.Context
import android.util.Log
import me.diksu.ultra.ksuApp

/**
 * 一份 SuSFS 配置的读写与落地。
 *
 * 以前规则散在几个 StringSet 里、开关各占一个 bool，想整体分享或回滚得挨个拷；现在整份
 * 配置是一个 JSON 文档，存进同一个 prefs 键。旧格式在首次读取时自动迁移，用户无感。
 *
 * 落地（[apply]）只做两件事：把开关设成目标值、把规则逐条下发给 ksud，然后如实汇总结果。
 * 内核没有删除接口，所以"从配置里删掉的规则"只能标记为待重启，不能假装当场生效。
 */
class SusfsRepository(private val context: Context = ksuApp) {

    private fun prefs() = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** 读当前配置；没有就尝试从旧格式迁移。解析失败返回空配置，不抛给界面。 */
    fun load(): SusfsConfig {
        val raw = prefs().getString(KEY_CONFIG, null)
        if (raw != null) {
            return runCatching { SusfsConfig.parse(raw) }
                .getOrElse {
                    Log.w(TAG, "配置解析失败，按空配置处理：${it.message}")
                    SusfsConfig()
                }
        }
        val migrated = migrateLegacy()
        if (migrated.rules.isNotEmpty() || migrated.enabled.isNotEmpty()) {
            Log.i(TAG, "已从旧格式迁移：${migrated.rules.size} 条规则")
            save(migrated)
        }
        return migrated
    }

    fun save(config: SusfsConfig) {
        prefs().edit().putString(KEY_CONFIG, config.toJson().toString()).apply()
    }

    fun export(): String = load().toPrettyJson()

    /** 只解析不落地：导入前先给用户看一眼差异。 */
    fun preview(text: String): SusfsConfig = SusfsConfig.parse(text)

    fun diff(candidate: SusfsConfig): SusfsConfigDiff = candidate.diffFrom(load())

    /**
     * 把 [target] 落地到内核。
     *
     * [previous] 非 null 时只下发变化过的开关（重复设同一个值没有意义）；规则总是全量下发，
     * 因为内核不回读规则表，我们无法知道它当前有哪些。
     */
    suspend fun apply(target: SusfsConfig, previous: SusfsConfig? = null): SusfsApplyReport {
        val applied = mutableListOf<String>()
        val failed = mutableMapOf<String, String>()
        val skipped = mutableMapOf<String, String>()

        for (key in SusfsConfig.KNOWN_SWITCHES) {
            val want = target.enabled[key] ?: false
            val had = previous?.enabled?.get(key) ?: false
            if (previous != null && want == had) continue
            val result = when (key) {
                SWITCH_LOG -> SusfsCommands.setLogEnabled(want)
                SWITCH_AVC -> SusfsCommands.setAvcLogSpoofing(want)
                SWITCH_HIDE_MOUNTS -> SusfsCommands.setHideSusMntsForNonSuProcs(want)
                else -> continue
            }
            if (result.ok) applied += "switch:$key" else failed["switch:$key"] = commandReason(result)
        }

        for (rule in target.normalized().rules) {
            val result = dispatch(rule)
            val reason = if (result.ok) "" else commandReason(result)
            when {
                result.ok -> applied += rule.key
                // 重复添加之类的"参数不合法"不是故障，记跳过即可，不然一次全量应用会满屏红。
                susfsErrorKind(reason) == SusfsErrorKind.Validation -> skipped[rule.key] = reason
                else -> failed[rule.key] = reason
            }
        }

        // 从配置里删掉的规则：内核没有删除接口，只能等下次开机不再重放。
        val pending = previous?.let { prev ->
            val keep = target.rules.map { it.key }.toSet()
            prev.rules.filter { it.key !in keep }.map { it.key }
        }.orEmpty()

        return SusfsApplyReport(applied, failed, skipped, pending)
    }

    /** 开机重放：整份配置全量下发，不比较旧值。 */
    suspend fun replayAfterBoot(): SusfsApplyReport = apply(load(), previous = null)

    private suspend fun dispatch(rule: SusfsRule): SusfsCommands.Result = when (rule) {
        is SusfsRule.HiddenPath -> SusfsCommands.addSusPath(rule.path)
        is SusfsRule.HiddenLoop -> SusfsCommands.addSusPathLoop(rule.path)
        is SusfsRule.SusMap -> SusfsCommands.addSusMap(rule.path)
        // 只有 update 可以被安全重放：add 是"开始跟踪"，必须紧挨着 bind mount。
        is SusfsRule.Kstat -> SusfsCommands.updateSusKstat(rule.path)
        is SusfsRule.Redirect -> SusfsCommands.addOpenRedirect(rule.target, rule.redirected, rule.uidScheme)
        is SusfsRule.Uname -> SusfsCommands.setUname(rule.release, rule.version)
        is SusfsRule.Cmdline -> SusfsCommands.setCmdline(rule.sourcePath)
    }

    // ── 旧格式迁移 ────────────────────────────────────────────────────────────

    /**
     * 把 4.1 之前的散存格式读成一份配置。
     *
     * 键名与旧 [me.diksu.ultra.susfs.SusfsRepository] 一致，写死在这里是为了让旧实现能被
     * 整个删掉；迁移只需成功一次，之后以 JSON 为准。
     */
    private fun migrateLegacy(): SusfsConfig {
        val p = context.getSharedPreferences(LEGACY_PREFS, Context.MODE_PRIVATE)
        val rules = buildList {
            p.getStringSet("sus_paths", null)?.sorted()?.forEach { add(SusfsRule.HiddenPath(it)) }
            p.getStringSet("sus_path_loops", null)?.sorted()?.forEach { add(SusfsRule.HiddenLoop(it)) }
            p.getStringSet("sus_maps", null)?.sorted()?.forEach { add(SusfsRule.SusMap(it)) }
            p.getStringSet("sus_kstat_paths", null)?.sorted()?.forEach { add(SusfsRule.Kstat(it)) }
            p.getStringSet("sus_redirects", null)?.sorted()?.forEach { packed ->
                val parts = packed.split("||")
                val target = parts.getOrNull(0).orEmpty()
                val redirected = parts.getOrNull(1).orEmpty()
                if (target.isNotEmpty() && redirected.isNotEmpty()) {
                    add(SusfsRule.Redirect(target, redirected, parts.getOrNull(2)?.toIntOrNull() ?: 0))
                }
            }
            val release = p.getString("uname_release", "").orEmpty()
            val version = p.getString("uname_version", "").orEmpty()
            if (release.isNotEmpty() || version.isNotEmpty()) add(SusfsRule.Uname(release, version))
        }
        val enabled = buildMap {
            if (p.getBoolean("log_enabled", false)) put(SWITCH_LOG, true)
            if (p.getBoolean("avc_log_spoofing", false)) put(SWITCH_AVC, true)
            if (p.getBoolean("hide_sus_mnts", false)) put(SWITCH_HIDE_MOUNTS, true)
        }
        return SusfsConfig(enabled = enabled, rules = rules).normalized()
    }

    companion object {
        private const val TAG = "SusfsRepository"
        private const val PREFS = "susfs_config"
        private const val LEGACY_PREFS = "susfs_settings"
        private const val KEY_CONFIG = "config"

        const val SWITCH_LOG = "log"
        const val SWITCH_AVC = "avc_log_spoof"
        const val SWITCH_HIDE_MOUNTS = "hide_mounts"
    }
}
