package me.diksu.ultra.foxsu.ui.util

import android.util.Log
import com.topjohnwu.superuser.Shell
import me.diksu.ultra.ksuApp
import java.io.File

/**
 * KPM（KernelPatch Module）加载器接入层。
 *
 * 「保证可用」的实现方式：APK 内置完整 kpatch 用户态二进制（jniLibs/arm64-v8a/libkpatch.so，
 * 由 KernelPatch 官方源码交叉编译，GPL-2.0），安装后通过 nativeLibraryDir 直接以 root 执行：
 *
 *   libkpatch.so <SUPERKEY> kpm load <path> [args]
 *   libkpatch.so <SUPERKEY> kpm unload <name>
 *   libkpatch.so <SUPERKEY> kpm list
 *   libkpatch.so <SUPERKEY> kpm ctl0 <name> <args>
 *   libkpatch.so <SUPERKEY> kpver / kver / hello
 *
 * 前提：设备刷入了 CONFIG_KPM=y 的内核（SukiSU Ultra / KernelPatch 系）。
 * SUPERKEY 自动从常见位置读取，读不到时允许用户在界面里填。
 */
object Kpm {

    private const val TAG = "Kpm"
    private const val DIR = "/data/adb/foxsu/kpm"

    /** superkey 常见存放位置（按顺序尝试） */
    private val KEY_CANDIDATES = listOf(
        "$DIR/key.txt",
        "/data/adb/ksu/superkey",
        "/data/adb/sukisu/superkey",
        "/data/adb/ap/.superkey",
    )

    private fun bin(): String =
        ksuApp.applicationInfo.nativeLibraryDir + File.separator + "libkpatch.so"

    private fun cmd(shell: Shell, vararg commands: String): Pair<Int, List<String>> {
        val out = ArrayList<String>()
        val err = ArrayList<String>()
        val r = shell.newJob().add(*commands).to(out, err).exec()
        return r.code to (out + err).filter { it.isNotBlank() }
    }

    private fun escape(s: String): String = "'" + s.replace("'", "'\\''") + "'"

    // ── superkey ─────────────────────────────────────────────────────────

    fun readKey(shell: Shell): String? {
        for (p in KEY_CANDIDATES) {
            val (_, out) = cmd(shell, "[ -r $p ] && head -c 128 $p || true")
            val v = out.joinToString("").trim()
            if (v.isNotEmpty()) return v
        }
        return null
    }

    fun saveKey(shell: Shell, key: String) {
        cmd(shell, "mkdir -p $DIR", "printf '%s' ${escape(key)} > $DIR/key.txt", "chmod 600 $DIR/key.txt")
    }

    // ── 检测 ─────────────────────────────────────────────────────────────

    data class Status(
        val kernelOk: Boolean,       // supercall 通了（KPM 内核在线）
        val kpVersion: String,
        val kernelVersion: String,
        val key: String?,            // 当前生效的 superkey
        val modules: List<String>,   // 已加载的 KPM 模块名
    )

    /** 依次用候选 key 探测；userKey 非空时优先尝试用户填的 */
    fun detect(shell: Shell, userKey: String? = null): Status {
        val kp = bin()
        val keys = mutableListOf<String>()
        userKey?.takeIf { it.isNotBlank() }?.let { keys.add(it) }
        readKey(shell)?.let { keys.add(it) }
        keys.add("supercall") // KernelPatch 打补丁时的默认 key

        var keyUsed: String? = null
        var kpVer = ""
        var kVer = ""
        var ok = false
        for (k in keys.distinct()) {
            val (code, out) = cmd(shell, "$kp ${escape(k)} hello")
            if (code == 0 && out.isNotEmpty()) {
                ok = true; keyUsed = k
                val (_, v1) = cmd(shell, "$kp ${escape(k)} kpver")
                kpVer = v1.joinToString("").trim()
                val (_, v2) = cmd(shell, "$kp ${escape(k)} kver")
                kVer = v2.joinToString("").trim()
                break
            }
        }
        val modules = if (ok) listModules(shell, keyUsed!!) else emptyList()
        return Status(ok, kpVer, kVer, keyUsed, modules)
    }

    // ── 模块管理 ─────────────────────────────────────────────────────────

    fun listModules(shell: Shell, key: String): List<String> {
        val (_, out) = cmd(shell, "${bin()} ${escape(key)} kpm list")
        val text = out.joinToString("\n")
        // kpm list 可能以逗号或换行分隔模块名
        return text.split(',', '\n')
            .map { it.replace(Regex("[\\[\\] ]"), "").trim() }
            .filter { it.isNotEmpty() && !it.contains("kpm", ignoreCase = true) || Regex("^[a-zA-Z0-9_\\-\\.]+$").matches(it.trim()) }
            .map { it.trim() }
            .distinct()
    }

    fun load(shell: Shell, key: String, path: String, args: String?): Pair<Boolean, String> {
        val kp = bin()
        val full = if (args.isNullOrBlank()) "$kp ${escape(key)} kpm load ${escape(path)}"
        else "$kp ${escape(key)} kpm load ${escape(path)} ${escape(args)}"
        val (code, out) = cmd(shell, full)
        Log.i(TAG, "kpm load $path → $code ${out.joinToString()}")
        return (code == 0) to out.joinToString("\n").ifBlank { if (code == 0) "加载成功" else "加载失败（code=$code）" }
    }

    fun unload(shell: Shell, key: String, name: String): Pair<Boolean, String> {
        val (code, out) = cmd(shell, "${bin()} ${escape(key)} kpm unload ${escape(name)}")
        return (code == 0) to out.joinToString("\n").ifBlank { if (code == 0) "已卸载" else "卸载失败（code=$code）" }
    }

    fun control(shell: Shell, key: String, name: String, args: String): Pair<Boolean, String> {
        val (code, out) = cmd(shell, "${bin()} ${escape(key)} kpm ctl0 ${escape(name)} ${escape(args)}")
        return (code == 0) to out.joinToString("\n").ifBlank { if (code == 0) "控制命令已执行" else "控制失败（code=$code）" }
    }

    fun info(shell: Shell, key: String, name: String): String {
        val (_, out) = cmd(shell, "${bin()} ${escape(key)} kpm info ${escape(name)}")
        return out.joinToString("\n").ifBlank { "（无输出）" }
    }

    /** 把 SAF 选中的 .kpm 落到 /data/adb/foxsu/kpm/ 下再加载 */
    fun importAndLoad(shell: Shell, bytes: ByteArray, displayName: String, args: String?): Pair<Boolean, String> {
        return try {
            cmd(shell, "mkdir -p $DIR")
            val tmp = File.createTempFile("kpm", ".tmp")
            tmp.writeBytes(bytes)
            val target = "$DIR/" + displayName.ifBlank { "module.kpm" }
            val (c1, o1) = cmd(shell, "cp ${escape(tmp.absolutePath)} ${escape(target)}", "chmod 644 ${escape(target)}")
            tmp.delete()
            if (c1 != 0) return false to o1.joinToString("\n")
            load(shell, readKey(shell) ?: "", target, args)
        } catch (e: Throwable) {
            Log.e(TAG, "importAndLoad", e)
            false to "导入失败：${e.message}"
        }
    }
}
