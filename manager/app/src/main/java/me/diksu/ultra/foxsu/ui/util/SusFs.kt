package me.diksu.ultra.foxsu.ui.util

import android.util.Log
import com.topjohnwu.superuser.Shell

/**
 * SusFs（susfs4ksu）功能接入层。
 *
 * 设计原则：内核不归管理器管——用户自己刷好带 susfs 补丁的内核，管理器只负责
 * 提供完整的控制界面。所有操作都通过 root shell 完成：
 *
 *  1. 内核侧检测：/proc/susfs 是否存在（susfs 内核补丁创建）
 *  2. 用户态工具：susfs 二进制（susfs4ksu 官方 userspace 工具，部分内核会附带），
 *     在常见路径中自动寻找；找不到时界面给出引导，所有列表操作仍可先配置，
 *     等工具就绪后一键应用
 *  3. 配置持久化：/data/adb/foxsu/susfs/ 下的列表文件，配合写好的开机脚本
 *     （post-fs-data.d）在每次启动后自动重新应用
 *
 * susfs 用户态工具支持的子命令（以 susfs4ksu README 为准）：
 *   add_sus_path / add_sus_mount / add_sus_loop_mount / add_sus_kstat /
 *   add_sus_memfd / spoof_uname / spoof_cmdline / spoof_config / spoof_data /
 *   enable_logs / disable_logs / clean_sus_path / clean_sus_mount /
 *   clean_sus_kstat / clean_sus_memfd / show_enabled_features / dump_kstat
 */
object SusFs {

    private const val TAG = "SusFs"
    private const val DIR = "/data/adb/foxsu/susfs"
    private const val BOOT_SCRIPT = "/data/adb/post-fs-data.d/foxsu_susfs.sh"

    /** 用户态 susfs 工具的常见安装位置 */
    private val TOOL_CANDIDATES = listOf(
        "/data/adb/ksu/bin/susfs",
        "/data/adb/ksu/bin/ksu_susfs",
        "/data/adb/susfs4ksu/susfs",
        "/data/adb/ap/bin/susfs",
        "/system/bin/susfs",
        "/system/xbin/susfs",
        "/vendor/bin/susfs",
    )

    /** 检测结果 */
    data class Status(
        val kernelFound: Boolean,
        val version: String,
        val toolPath: String?,
        val bootScriptInstalled: Boolean,
    )

    data class Entry(val value: String)

    private fun sh(): Shell = getRootShell()

    private fun cmd(shell: Shell, vararg commands: String): Pair<Int, List<String>> {
        val out = ArrayList<String>()
        val err = ArrayList<String>()
        val r = shell.newJob().add(*commands).to(out, err).exec()
        return r.code to (out + err).filter { it.isNotBlank() }
    }

    private fun escape(s: String): String =
        "'" + s.replace("'", "'\\''") + "'"

    // ── 检测 ─────────────────────────────────────────────────────────────

    fun detect(): Status {
        val shell = sh()
        var kernelFound = false
        var version = ""
        runCatching {
            val (code, out) = cmd(shell, "[ -e /proc/susfs ] && echo __ok__")
            kernelFound = code == 0 && out.any { it.contains("__ok__") }
            if (kernelFound) {
                // 内核会把用法/版本信息回显在 /proc/susfs 读取里
                val (_, info) = cmd(shell, "head -c 512 /proc/susfs")
                version = info.firstOrNull { it.contains("susfs", ignoreCase = true) } ?: ""
            }
        }
        val tool = TOOL_CANDIDATES.firstOrNull {
            cmd(shell, "[ -x $it ] && echo __ok__").second.any { s -> s.contains("__ok__") }
        }
        val boot = cmd(shell, "[ -f $BOOT_SCRIPT ] && echo __ok__").second.any { it.contains("__ok__") }
        return Status(kernelFound, version, tool, boot)
    }

    // ── 列表读写 ─────────────────────────────────────────────────────────

    private fun listFile(kind: String) = "$DIR/$kind.list"

    fun readList(shell: Shell, kind: String): List<String> {
        val (_, out) = cmd(shell, "[ -f ${listFile(kind)} ] && cat ${listFile(kind)} || true")
        return out.map { it.trim() }.filter { it.isNotEmpty() }
    }

    private fun writeList(shell: Shell, kind: String, items: List<String>) {
        val body = items.joinToString("\n")
        cmd(shell, "mkdir -p $DIR", "printf '%s\\n' ${escape(body)} > ${listFile(kind)}")
    }

    /** 立即向内核应用一条 susfs 命令（工具存在时） */
    fun applyNow(shell: Shell, tool: String?, sub: String, arg: String?): Pair<Boolean, String> {
        if (tool == null) return false to "未找到 susfs 用户态工具，条目已保存，工具就绪后可一键应用"
        val full = if (arg != null) "$tool $sub ${escape(arg)}" else "$tool $sub"
        val (code, out) = cmd(shell, full)
        return (code == 0) to out.joinToString("\n")
    }

    fun addEntry(shell: Shell, tool: String?, kind: String, sub: String, value: String): Pair<Boolean, String> {
        val items = readList(shell, kind).toMutableList()
        if (items.none { it.equals(value, ignoreCase = true) }) items.add(value.trim())
        writeList(shell, kind, items)
        return applyNow(shell, tool, sub, value.trim())
    }

    fun removeEntry(shell: Shell, tool: String?, kind: String, value: String): Boolean {
        val items = readList(shell, kind).filterNot { it.equals(value, ignoreCase = true) }
        writeList(shell, kind, items)
        return true
    }

    // ── 伪装项（uname / cmdline / config / data）─────────────────────────

    fun readOpt(shell: Shell, name: String): Boolean =
        cmd(shell, "[ -f $DIR/$name ] && echo __on__ || true").second.any { it.contains("__on__") }

    fun readOptText(shell: Shell, name: String): String {
        val (_, out) = cmd(shell, "[ -f $DIR/$name.txt ] && cat $DIR/$name.txt || true")
        return out.joinToString("\n").trim()
    }

    fun setOpt(shell: Shell, tool: String?, name: String, enabled: Boolean, arg: String? = null): Pair<Boolean, String> {
        return when {
            enabled && arg != null -> {
                cmd(shell, "mkdir -p $DIR", "printf '%s' ${escape(arg)} > $DIR/$name.txt")
                applyNow(shell, tool, "spoof_$name", arg)
            }
            enabled -> {
                cmd(shell, "mkdir -p $DIR", "touch $DIR/$name")
                applyNow(shell, tool, "spoof_$name", null)
            }
            else -> {
                cmd(shell, "rm -f $DIR/$name $DIR/$name.txt")
                // 取消伪装：clean 命令按条目清理，工具支持时生效
                if (tool != null) cmd(shell, "$tool clean_sus_kstat")
                true to "已关闭（重启内核后彻底失效）"
            }
        }
    }

    // ── 日志 ─────────────────────────────────────────────────────────────

    fun setLogs(shell: Shell, tool: String?, enabled: Boolean): Pair<Boolean, String> {
        if (tool == null) return false to "未找到 susfs 用户态工具"
        return applyNow(shell, tool, if (enabled) "enable_logs" else "disable_logs", null)
    }

    /** show_enabled_features 的原文输出 */
    fun showFeatures(shell: Shell, tool: String?): String {
        if (tool == null) return "未找到 susfs 用户态工具。\n\n请确认你刷入的 susfs 内核是否附带 susfs 工具，或手动放置到：\n" + TOOL_CANDIDATES.joinToString("\n")
        val (_, out) = cmd(shell, "$tool show_enabled_features")
        return out.joinToString("\n").ifBlank { "（无输出）" }
    }

    // ── 一键应用 + 开机脚本 ────────────────────────────────────────────────

    fun applyAll(shell: Shell, tool: String?): Pair<Int, String> {
        if (tool == null) return 0 to "未找到 susfs 用户态工具，无法向内核应用"
        var ok = 0
        val log = StringBuilder()
        readList(shell, "sus_path").forEach {
            val r = applyNow(shell, tool, "add_sus_path", it)
            if (r.first) ok++ else log.appendLine("sus_path $it → ${r.second}")
        }
        readList(shell, "sus_mount").forEach {
            val r = applyNow(shell, tool, "add_sus_mount", it)
            if (r.first) ok++ else log.appendLine("sus_mount $it → ${r.second}")
        }
        val uname = readOptText(shell, "spoof_uname")
        if (uname.isNotEmpty() && readOpt(shell, "spoof_uname")) {
            if (applyNow(shell, tool, "spoof_uname", uname).first) ok++
        }
        val cmdline = readOptText(shell, "spoof_cmdline")
        if (cmdline.isNotEmpty() && readOpt(shell, "spoof_cmdline")) {
            if (applyNow(shell, tool, "spoof_cmdline", cmdline).first) ok++
        }
        if (readOpt(shell, "spoof_config") && applyNow(shell, tool, "spoof_config", null).first) ok++
        if (readOpt(shell, "spoof_data") && applyNow(shell, tool, "spoof_data", null).first) ok++
        return ok to log.toString()
    }

    fun setBootScript(shell: Shell, install: Boolean): Boolean {
        return try {
            if (install) {
                val script = """
                    #!/system/bin/sh
                    # FoxSu SusFs 开机自动应用（由 FoxSu Manager 维护）
                    TOOL=""
                    for c in ${TOOL_CANDIDATES.joinToString(" ")}; do
                      [ -x "${'$'}c" ] && TOOL="${'$'}c" && break
                    done
                    [ -z "${'$'}TOOL" ] && exit 0
                    DIR=$DIR
                    while IFS= read -r p; do
                      [ -n "${'$'}p" ] && "${'$'}TOOL" add_sus_path "${'$'}p"
                    done < "${'$'}DIR/sus_path.list" 2>/dev/null
                    while IFS= read -r m; do
                      [ -n "${'$'}m" ] && "${'$'}TOOL" add_sus_mount "${'$'}m"
                    done < "${'$'}DIR/sus_mount.list" 2>/dev/null
                    [ -f "${'$'}DIR/spoof_uname.txt" ] && "${'$'}TOOL" spoof_uname "$(cat "${'$'}DIR/spoof_uname.txt")"
                    [ -f "${'$'}DIR/spoof_cmdline.txt" ] && "${'$'}TOOL" spoof_cmdline "$(cat "${'$'}DIR/spoof_cmdline.txt")"
                    [ -f "${'$'}DIR/spoof_config" ] && "${'$'}TOOL" spoof_config
                    [ -f "${'$'}DIR/spoof_data" ] && "${'$'}TOOL" spoof_data
                """.trimIndent()
                cmd(shell, "mkdir -p /data/adb/post-fs-data.d")
                val (_, out) = cmd(
                    shell,
                    "printf '%s' ${escape(script)} > $BOOT_SCRIPT",
                    "chmod 700 $BOOT_SCRIPT",
                )
                Log.i(TAG, "install boot script: $out")
            } else {
                cmd(shell, "rm -f $BOOT_SCRIPT")
            }
            true
        } catch (e: Throwable) {
            Log.e(TAG, "setBootScript", e)
            false
        }
    }
}
