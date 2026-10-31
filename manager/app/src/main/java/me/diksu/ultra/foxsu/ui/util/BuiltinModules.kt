package me.diksu.ultra.foxsu.ui.util

import android.net.Uri
import com.topjohnwu.superuser.Shell
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.diksu.ultra.ksuApp
import java.io.File
import java.util.zip.ZipInputStream

/**
 * 随管理器内置的模块。
 *
 * 这里刻意不叫「功能」：Zygisk、Keymint 这类能力只能以模块形态存在——它们要在 post-fs-data /
 * zygote 之前拿到 root 的 mount namespace，管理器是普通 App，做不到（详见 Builtin Modules 设计说明）。
 * 管理器能做且应该做的是：把安装包带上，省掉「去 GitHub 找 release、下载、再刷入」这几步，
 * 并提供装 / 停 / 卸 / 打开模块自带界面这几个动作。真正的执行仍然归 ksud 与模块本身。
 */
data class BuiltinModule(
    val id: String,
    val name: String,
    /** APK assets 内的安装包路径。 */
    val asset: String,
    /** 一句话说明，列表卡用。 */
    val tagline: String,
    /** 详情页正文。 */
    val description: String,
    /** 元模块（接管挂载层）还是普通模块。 */
    val isMetaModule: Boolean,
    val sourceUrl: String,
    val license: String,
)

data class BuiltinStatus(
    val module: BuiltinModule,
    val installed: Boolean = false,
    val enabled: Boolean = false,
    val version: String = "",
    val versionCode: Int = 0,
    val author: String = "",
    val builtinVersion: String = "",
    val builtinCode: Int = 0,
    /** 有 update 标记 / modules_update 里有新副本：要重启才生效。 */
    val pendingReboot: Boolean = false,
    /** 模块自带 webroot：可以在管理器里打开它的原始界面。 */
    val hasWebUi: Boolean = false,
    /** 模块自带 action.sh（KernelSU 的操作按钮）。 */
    val hasAction: Boolean = false,
    /** 探测没跑通（没 root / 被 SELinux 拦），不做猜测。 */
    val probeOk: Boolean = false,
    val raw: String = "",
) {
    /** 内置包比设备上的新。 */
    val hasUpdate: Boolean get() = installed && versionCode > 0 && builtinCode > versionCode

    val stateLabel: String get() = when {
        !probeOk -> "未知"
        !installed -> "未安装"
        !enabled -> "已停用"
        pendingReboot -> "待重启"
        else -> "已生效"
    }

    /** 停用后模块不参与开机，开关是安全的；未安装时没有可切换的对象。 */
    val canToggle: Boolean get() = installed && probeOk
}

object BuiltinModules {

    val MAGIC_MOUNT = BuiltinModule(
        id = "magic_mount_rs",
        name = "Magic Mount-rs",
        asset = "builtin/magic_mount_rs-arm64.zip",
        tagline = "内核元模块 · 接管系统分区挂载",
        description = "用 Magic Mount 取代 ksud 的挂载实现，模块文件以更贴近原厂的方式呈现给系统。" +
            "同一时间只能有一颗元模块，装卸都要重启。",
        isMetaModule = true,
        sourceUrl = "https://github.com/Tools-cx-app/meta-magic_mount-rs",
        license = "GPL-3.0",
    )

    val OH_MY_KEYMINT = BuiltinModule(
        id = "oh_my_keymint",
        name = "Oh My Keymint",
        asset = "builtin/oh_my_keymint-arm64.zip",
        tagline = "自建 KeyMint 服务 · 让 Keystore 认新的密钥证明",
        description = "用自带的 keymint 守护进程替换系统 KeyMint，配合 keybox 让应用读到你想给的" +
            "密钥证明链。安装后开机自动拉起守护进程；keybox 与注入配置在 /data/misc/keystore/omk。",
        isMetaModule = false,
        sourceUrl = "https://github.com/qwq233/OhMyKeymint",
        license = "Apache-2.0",
    )

    val ZYGISK_NEXT = BuiltinModule(
        id = "zygisksu",
        name = "Zygisk Next",
        asset = "builtin/zygisk_next-arm64.zip",
        tagline = "独立 Zygisk 实现 · 让 Zygisk 模块能跑",
        description = "不依赖 Magisk 的 Zygisk 实现，为 Zygisk 模块提供注入能力。" +
            "装好后重启，Zygisk 模块才会被加载；与其它 Zygisk 实现互斥。",
        isMetaModule = false,
        sourceUrl = "https://github.com/LSPosed/ZygiskNext",
        license = "GPL-3.0",
    )

    val ALL: List<BuiltinModule> = listOf(MAGIC_MOUNT, OH_MY_KEYMINT, ZYGISK_NEXT)

    fun byId(id: String): BuiltinModule? = ALL.firstOrNull { it.id == id }

    private const val MODULE_DIR = "/data/adb/modules"
    private const val UPDATE_DIR = "/data/adb/modules_update"

    /** 一次 shell 读完所有内置模块的状态，避免每张卡都起一个进程。 */
    suspend fun probeAll(): List<BuiltinStatus> = withContext(Dispatchers.IO) {
        val script = buildString {
            ALL.forEach { m ->
                appendLine("echo '@@id=${m.id}'")
                appendLine("cat $MODULE_DIR/${m.id}/module.prop 2>/dev/null")
                appendLine("echo '@@flags=${m.id}'")
                appendLine("[ -f $MODULE_DIR/${m.id}/disable ] && echo disabled")
                appendLine("[ -e $MODULE_DIR/${m.id}/update ] && echo updating")
                appendLine("[ -d $UPDATE_DIR/${m.id} ] && echo pending")
                appendLine("[ -d $MODULE_DIR/${m.id}/webroot ] && echo webroot")
                appendLine("{ [ -f $MODULE_DIR/${m.id}/action.sh ] || [ -f $MODULE_DIR/${m.id}/webroot/action.sh ]; } && echo action")
                appendLine("true")
            }
            append("echo '@@end'")
        }
        val raw = runCatching { Shell.cmd(script).exec().out.joinToString("\n") }.getOrElse { "" }
        val probeOk = raw.contains("@@end")
        ALL.map { parse(it, raw, probeOk) }
    }

    suspend fun probe(module: BuiltinModule): BuiltinStatus =
        probeAll().first { it.module.id == module.id }

    private fun parse(module: BuiltinModule, raw: String, probeOk: Boolean): BuiltinStatus {
        val lines = raw.lines().map { it.trim() }

        // 段落以 @@id=<id> 开头，到下一个 @@id 为止（段内还有 @@flags 分隔符）。
        val start = lines.indexOfFirst { it == "@@id=${module.id}" }
        val body: List<String> = if (start < 0) {
            emptyList()
        } else {
            val rest = lines.drop(start + 1)
            val end = rest.indexOfFirst { it.startsWith("@@id=") }
            if (end < 0) rest else rest.take(end)
        }

        // 段内又分两截：module.prop 原文 + "@@flags" 之后的标记行。
        val flagIdx = body.indexOfFirst { it == "@@flags" || it == "@@flags=${module.id}" }
        val propLines = if (flagIdx >= 0) body.take(flagIdx) else body
        val flags = if (flagIdx >= 0) body.drop(flagIdx + 1).filter { it.isNotBlank() }.toSet() else emptySet()

        val prop = propLines
            .filter { it.contains('=') && !it.startsWith("#") }
            .associate { it.substringBefore('=').trim() to it.substringAfter('=').trim() }
        val installed = prop.isNotEmpty()
        val builtin = readBuiltinProp(module.asset)

        return BuiltinStatus(
            module = module,
            installed = installed,
            enabled = installed && "disabled" !in flags,
            version = prop["version"].orEmpty(),
            versionCode = prop["versionCode"]?.toIntOrNull() ?: 0,
            author = prop["author"].orEmpty(),
            builtinVersion = builtin["version"].orEmpty(),
            builtinCode = builtin["versionCode"]?.toIntOrNull() ?: 0,
            pendingReboot = "updating" in flags || "pending" in flags,
            hasWebUi = "webroot" in flags,
            hasAction = "action" in flags,
            probeOk = probeOk,
            raw = raw,
        )
    }

    /**
     * 安装内置包：把 assets 里的 zip 落到私有 cache，再交给 ksud 的标准 `module install`。
     * 自己在 App 里解压会漏掉符号链接、SELinux 上下文和 customize.sh 里的 hooks。
     */
    suspend fun install(module: BuiltinModule, onLine: (String) -> Unit = {}): Boolean =
        withContext(Dispatchers.IO) {
            runCatching {
                val zip = File(ksuApp.cacheDir, "${module.id}-builtin.zip")
                ksuApp.assets.open(module.asset).use { input ->
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

    /** 只写/删 `disable` 标记，立刻返回；真正生效在下次开机。 */
    suspend fun setEnabled(module: BuiltinModule, enabled: Boolean): Boolean =
        withContext(Dispatchers.IO) {
            runCatching { toggleModule(module.id, enabled) }.getOrDefault(false)
        }

    suspend fun uninstall(module: BuiltinModule): Boolean = withContext(Dispatchers.IO) {
        runCatching { uninstallModule(module.id) }.getOrDefault(false)
    }

    /** 从内置 zip 里读 module.prop，保证界面上的「内置版本」永远等于真正会装进去的那份。 */
    fun readBuiltinProp(asset: String): Map<String, String> = runCatching {
        var result: Map<String, String> = emptyMap()
        ksuApp.assets.open(asset).use { input ->
            ZipInputStream(input).use { zip ->
                var entry = zip.nextEntry
                while (entry != null) {
                    if (entry.name == "module.prop") {
                        result = zip.bufferedReader().readLines()
                            .filter { it.contains('=') }
                            .associate {
                                it.substringBefore('=').trim() to it.substringAfter('=').trim()
                            }
                        break
                    }
                    entry = zip.nextEntry
                }
            }
        }
        result
    }.getOrDefault(emptyMap())
}
