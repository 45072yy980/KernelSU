package me.diksu.ultra.foxsu.ui.util

import android.net.Uri
import com.topjohnwu.superuser.Shell
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.diksu.ultra.ksuApp
import java.io.File
import java.util.zip.ZipInputStream

/**
 * 内置元模块（Magic Mount-rs）的状态与安装。
 *
 * 这不是一个「功能开关」，而是一颗真实的 KernelSU 元模块：装进 `/data/adb/modules/magic_mount_rs`，
 * 由 ksud 建 `/data/adb/metamodule` 符号链接，开机时用它自己那份 Rust 二进制接管所有模块的挂载。
 * 所以这里的每条状态都来自设备上的真实文件，没装就是没装，装坏了就是装坏了。
 *
 * 元模块的限制照抄 ksud 的规则，不在管理器里假装绕过：
 *  · 同一时间只能有一个元模块，换装要先卸载 + 重启；
 *  · 元模块永远开机生效，没有热安装；
 *  · 它接管的是挂载层，装错会让所有模块失效（安全模式能救回来）。
 */
enum class MetaState {
    /** 已安装、已链接、无待生效标记——开机时已经在用它挂载。 */
    ACTIVE,

    /** 已安装但被停用（`disable` 标记存在）。 */
    DISABLED,

    /** 已安装，但要重启才生效（`update` 标记 / modules_update 里有新副本）。 */
    PENDING,

    /** 设备上还没有装。 */
    NOT_INSTALLED,

    /** 已有另一个元模块占用，必须先换下来。 */
    BLOCKED,

    /** 探测没跑完（没 root / 被 SELinux 拦），不做任何猜测。 */
    UNKNOWN,
}

data class MetaStatus(
    val state: MetaState = MetaState.UNKNOWN,
    val installed: Boolean = false,
    val installedVersion: String = "",
    val installedCode: Int = 0,
    val builtinVersion: String = "",
    val builtinCode: Int = 0,
    val activeId: String = "",
    val configToml: String = "",
    val binaryOk: Boolean = false,
    val moduleCount: Int = 0,
    val probeOk: Boolean = false,
    val raw: String = "",
) {
    /** 内置的比设备上的新。 */
    val hasUpdate: Boolean get() = installed && installedCode > 0 && builtinCode > installedCode

    /** 设备上那颗能不能直接读——不能读就别让用户点「停用」。 */
    val canToggle: Boolean get() = installed && probeOk && state != MetaState.BLOCKED
}

object MetaModule {

    const val ID = "magic_mount_rs"

    /** 内置包路径（APK assets）。文件名带 ABI，因为 ksud 只按 ARCH 取一份二进制。 */
    private const val ASSET = "builtin/magic_mount_rs-arm64.zip"

    private const val MODULE_DIR = "/data/adb/modules/$ID"
    private const val UPDATE_DIR = "/data/adb/modules_update/$ID"
    private const val CONFIG_DIR = "/data/adb/magic_mount"
    private const val CONFIG = "$CONFIG_DIR/config.toml"

    /** 默认配置，和模块包里那份保持一致；「恢复默认」写的就是它。 */
    private val DEFAULT_CONFIG = """
        mountsource = "KSU"
        umount = false
        partitions = []
        extra_mount = []
    """.trimIndent()

    private const val PROBE = """
        echo '@@id'
        id -u 2>/dev/null
        echo '@@link'
        [ -L /data/adb/metamodule ] && readlink -f /data/adb/metamodule 2>/dev/null
        echo '@@prop'
        cat $MODULE_DIR/module.prop 2>/dev/null
        echo '@@flags'
        [ -f $MODULE_DIR/disable ] && echo disabled
        [ -e $MODULE_DIR/update ] && echo updating
        [ -d $UPDATE_DIR ] && echo pending
        [ -x $MODULE_DIR/bin/arm64-v8a/magic_mount_rs ] && echo binary
        echo '@@cfg'
        cat $CONFIG 2>/dev/null
        echo '@@mods'
        ls /data/adb/modules 2>/dev/null | wc -l
        echo '@@end'
    """

    suspend fun probe(): MetaStatus = withContext(Dispatchers.IO) {
        val raw = runCatching { Shell.cmd(PROBE).exec().out.joinToString("\n") }.getOrElse { "" }
        val s = parseSections(raw)
        val probeOk = raw.contains("@@end") && s["id"]?.firstOrNull() == "0"

        val builtin = readBuiltinProp()
        val link = s["link"].orEmpty().firstOrNull { it.isNotBlank() }.orEmpty()
        val activeId = link.substringAfterLast('/').takeIf { it.isNotBlank() && it != "adb" }.orEmpty()

        val prop = s["prop"].orEmpty()
            .filter { it.contains('=') }
            .associate { it.substringBefore('=').trim() to it.substringAfter('=').trim() }
        val flags = s["flags"].orEmpty().toSet()
        val installed = prop.isNotEmpty()
        val disabled = "disabled" in flags
        val pending = "updating" in flags || "pending" in flags

        val state = when {
            !probeOk -> MetaState.UNKNOWN
            activeId.isNotEmpty() && activeId != ID -> MetaState.BLOCKED
            !installed -> MetaState.NOT_INSTALLED
            pending -> MetaState.PENDING
            disabled -> MetaState.DISABLED
            activeId == ID -> MetaState.ACTIVE
            // 目录在、链接不在：装到一半或链接被手动删了，仍算待生效
            else -> MetaState.PENDING
        }

        MetaStatus(
            state = state,
            installed = installed,
            installedVersion = prop["version"].orEmpty(),
            installedCode = prop["versionCode"]?.toIntOrNull() ?: 0,
            builtinVersion = builtin["version"].orEmpty(),
            builtinCode = builtin["versionCode"]?.toIntOrNull() ?: 0,
            activeId = activeId,
            configToml = s["cfg"].orEmpty().joinToString("\n").trim(),
            binaryOk = "binary" in flags,
            moduleCount = s["mods"].orEmpty().firstOrNull()?.trim()?.toIntOrNull() ?: 0,
            probeOk = probeOk,
            raw = raw,
        )
    }

    /**
     * 装内置包。走 ksud 的标准 `module install`，元模块的 metainstall.sh / customize.sh 该跑的都跑，
     * 不在管理器里自己解压——自己解压就会漏掉符号链接和 SELinux 上下文。
     */
    suspend fun install(onLine: (String) -> Unit = {}): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val zip = File(ksuApp.cacheDir, "mmrs-builtin.zip")
            ksuApp.assets.open(ASSET).use { input ->
                zip.outputStream().use { out -> input.copyTo(out) }
            }
            val result = flashModule(Uri.fromFile(zip), onLine, onLine)
            zip.delete()
            result.code == 0
        }.getOrElse { e ->
            onLine("! ${e.message ?: "安装失败"}")
            false
        }
    }

    suspend fun uninstall(): Boolean = withContext(Dispatchers.IO) {
        runCatching { uninstallModule(ID) }.getOrDefault(false)
    }

    /** 只写/删 `disable` 标记，立刻返回；真正生效在下次开机。 */
    suspend fun setEnabled(enabled: Boolean): Boolean = withContext(Dispatchers.IO) {
        runCatching { toggleModule(ID, enabled) }.getOrDefault(false)
    }

    suspend fun writeConfig(text: String): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val tmp = File(ksuApp.cacheDir, "mmrs-config.toml")
            tmp.writeText(text.trimEnd() + "\n")
            val cmd = buildString {
                append("mkdir -p $CONFIG_DIR; ")
                append("cp '${tmp.absolutePath}' $CONFIG; ")
                append("chmod 600 $CONFIG; ")
                append("chown 0:0 $CONFIG 2>/dev/null; ")
                append("restorecon -R $CONFIG_DIR 2>/dev/null; ")
                append("true")
            }
            val ok = Shell.cmd(cmd).exec().isSuccess
            tmp.delete()
            ok
        }.getOrDefault(false)
    }

    fun defaultConfig(): String = DEFAULT_CONFIG

    /** 从内置 zip 里读 module.prop，保证界面上的「内置版本」永远等于真正会装进去的那份。 */
    private fun readBuiltinProp(): Map<String, String> = runCatching {
        var result: Map<String, String> = emptyMap()
        ksuApp.assets.open(ASSET).use { input ->
            ZipInputStream(input).use { zip ->
                var entry = zip.nextEntry
                while (entry != null) {
                    if (entry.name == "module.prop") {
                        result = zip.bufferedReader().readLines()
                            .filter { it.contains('=') }
                            .associate { it.substringBefore('=').trim() to it.substringAfter('=').trim() }
                        break
                    }
                    entry = zip.nextEntry
                }
            }
        }
        result
    }.getOrDefault(emptyMap())

    fun stateLabel(state: MetaState): String = when (state) {
        MetaState.ACTIVE -> "已生效"
        MetaState.DISABLED -> "已停用"
        MetaState.PENDING -> "待重启"
        MetaState.NOT_INSTALLED -> "未安装"
        MetaState.BLOCKED -> "被占用"
        MetaState.UNKNOWN -> "未知"
    }

    fun diagnosticText(status: MetaStatus): String = buildString {
        appendLine("FoxSu 元模块诊断")
        appendLine("状态：${stateLabel(status.state)}")
        appendLine("内置版本：${status.builtinVersion} (${status.builtinCode})")
        appendLine("已装版本：${status.installedVersion.ifEmpty { "—" }} (${status.installedCode})")
        appendLine("当前元模块：${status.activeId.ifEmpty { "无" }}")
        appendLine("二进制：${if (status.binaryOk) "存在" else "缺失"}")
        appendLine("模块总数：${status.moduleCount}")
        appendLine()
        appendLine("[config.toml]")
        appendLine(status.configToml.ifEmpty { "（空）" })
    }

    private fun parseSections(raw: String): Map<String, List<String>> {
        val map = mutableMapOf<String, MutableList<String>>()
        var current: String? = null
        raw.lineSequence().forEach { line ->
            val l = line.trim()
            when {
                l.startsWith("@@") -> {
                    val key = l.removePrefix("@@")
                    current = key
                    map.getOrPut(key) { mutableListOf() }
                }
                current != null -> map[current]?.add(l)
            }
        }
        return map
    }
}
