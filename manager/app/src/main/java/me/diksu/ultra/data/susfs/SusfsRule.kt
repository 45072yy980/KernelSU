package me.diksu.ultra.data.susfs

/**
 * 一条 SuSFS 规则。
 *
 * `key` 是配置内的唯一标识：同一类规则的同一条路径只应出现一次，导入时据此去重，应用报告
 * 也用它指认是哪一条失败了。
 */
sealed interface SusfsRule {
    val key: String

    /** 隐藏一个路径。 */
    data class HiddenPath(val path: String) : SusfsRule {
        override val key: String get() = "path:$path"
    }

    /** 隐藏并跟随挂载（sus path loop）。 */
    data class HiddenLoop(val path: String) : SusfsRule {
        override val key: String get() = "loop:$path"
    }

    /** overlay / 挂载点到 map。 */
    data class SusMap(val path: String) : SusfsRule {
        override val key: String get() = "map:$path"
    }

    /** 跟踪某个路径的 stat。 */
    data class Kstat(val path: String) : SusfsRule {
        override val key: String get() = "kstat:$path"
    }

    /** open 重定向：把 [target] 打开成 [redirected]；uidScheme 0 表示不限 uid。 */
    data class Redirect(
        val target: String,
        val redirected: String,
        val uidScheme: Int = 0,
    ) : SusfsRule {
        override val key: String get() = "redirect:$target:$redirected:$uidScheme"
    }

    /** 伪 uname。只有一个，重复导入后来者覆盖。 */
    data class Uname(val release: String, val version: String) : SusfsRule {
        override val key: String get() = "uname"
    }

    /** 伪 cmdline / bootconfig，来源是设备上的一个文件。 */
    data class Cmdline(val sourcePath: String) : SusfsRule {
        override val key: String get() = "cmdline"
    }
}

/** 一条规则在配置里的人类可读描述，用于界面与报告；不含本地化文案。 */
val SusfsRule.display: String
    get() = when (this) {
        is SusfsRule.HiddenPath -> path
        is SusfsRule.HiddenLoop -> "$path (loop)"
        is SusfsRule.SusMap -> path
        is SusfsRule.Kstat -> path
        is SusfsRule.Redirect -> "$target → $redirected" + if (uidScheme != 0) " (uid $uidScheme)" else ""
        is SusfsRule.Uname -> "release=$release version=$version"
        is SusfsRule.Cmdline -> sourcePath
    }

/** 规则类型，用来分组展示和决定调哪条命令。 */
enum class SusfsRuleType { Path, Loop, Map, Kstat, Redirect, Uname, Cmdline }

val SusfsRule.type: SusfsRuleType
    get() = when (this) {
        is SusfsRule.HiddenPath -> SusfsRuleType.Path
        is SusfsRule.HiddenLoop -> SusfsRuleType.Loop
        is SusfsRule.SusMap -> SusfsRuleType.Map
        is SusfsRule.Kstat -> SusfsRuleType.Kstat
        is SusfsRule.Redirect -> SusfsRuleType.Redirect
        is SusfsRule.Uname -> SusfsRuleType.Uname
        is SusfsRule.Cmdline -> SusfsRuleType.Cmdline
    }
