package me.diksu.ultra.i18n

import android.content.Context
import android.net.Uri
import java.io.File

/**
 * 语言包在磁盘上的存取：`filesDir/language-packs/<id>.json`。
 *
 * 放在应用私有目录而不是外部存储：语言包会被内核/其它进程看不到，也不需要备份，
 * 但必须保证应用自己能随时读到、且卸载后不残留。
 */
class LanguagePackStore(private val context: Context) {

    private val dir: File
        get() = File(context.filesDir, DIR).apply { if (!exists()) mkdirs() }

    /** 读全部语言包；坏文件跳过，不因为一个文件坏了就让设置页打不开。 */
    fun list(): List<LanguagePack> = dir.listFiles { f -> f.extension == "json" }
        ?.sortedBy { it.name }
        ?.mapNotNull { file ->
            runCatching {
                LanguagePack.parse(
                    id = file.nameWithoutExtension,
                    text = file.readText(),
                    fallbackName = file.nameWithoutExtension,
                )
            }.getOrNull()
        }
        .orEmpty()

    fun find(id: String): LanguagePack? =
        runCatching { File(dir, "$id.json").takeIf { it.exists() }?.readText() }
            .getOrNull()
            ?.let { LanguagePack.parse(id, it, id) }

    /**
     * 从用户选中的文件导入。
     *
     * 返回导入结果：成功给出语言包，失败给出可读原因（设置页原样显示，不做二次包装）。
     */
    fun import(uri: Uri): Result<LanguagePack> = runCatching {
        val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            ?: error("无法读取该文件")
        if (bytes.size > LanguagePack.MAX_BYTES) {
            error("文件超过 ${LanguagePack.MAX_BYTES / 1024} KB，看起来不是语言包")
        }
        val text = bytes.toString(Charsets.UTF_8)
        val id = safeId(uri.lastPathSegment?.substringAfterLast('/')?.substringBeforeLast('.') ?: "pack")
        val name = id
        val pack = LanguagePack.parse(id, text, name) ?: error("没有找到任何词条")
        File(dir, "$id.json").writeText(text)
        pack
    }

    fun delete(id: String): Boolean = runCatching { File(dir, "$id.json").delete() }.getOrDefault(false)

    /** 文件名只留字母数字与短横线：语言包 id 会进文件名和 SharedPreferences。 */
    private fun safeId(raw: String): String {
        val cleaned = raw.filter { it.isLetterOrDigit() || it == '-' || it == '_' }.take(40)
        val base = cleaned.ifEmpty { "pack" }
        // 重名就加后缀，不覆盖用户已有的包。
        if (!File(dir, "$base.json").exists()) return base
        var index = 2
        while (File(dir, "$base-$index.json").exists()) index++
        return "$base-$index"
    }

    private companion object {
        const val DIR = "language-packs"
    }
}
