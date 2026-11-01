package me.diksu.ultra.data.backup

import me.diksu.ultra.BuildConfig
import me.diksu.ultra.ksuApp
import me.diksu.ultra.ui.util.getRootShell
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

/**
 * 可备份的两类东西。
 *
 * 都直接打 `tar`，因为源都在 root 才能读的目录里，而且模块目录动辄几百 MB —— 在 Kotlin 里
 * 逐文件搬运既不快也容易漏掉权限位。
 *
 * 恢复后要重启才生效：允许列表是内核在 init 时读进内存的，`/data/adb/ksu/.allowlist` 换了
 * 文件不等于换了内存里那份。
 */
enum class BackupKind(
    val id: String,
    /** tar 的 -C 基准目录 */
    val base: String,
    val paths: List<String>,
) {
    Superuser(
        id = "superuser",
        base = "/data/adb/ksu",
        paths = listOf(".allowlist", "profile"),
    ),
    Modules(
        id = "modules",
        base = "/data/adb",
        paths = listOf("modules"),
    ),
}

/** 一份已经落盘的备份。 */
data class BackupEntry(
    val file: File,
    val kind: BackupKind,
    val createdAt: Long,
    val sizeBytes: Long,
    val appVersion: String,
) {
    val displayName: String
        get() = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(createdAt))
}

class BackupException(message: String) : Exception(message)

/**
 * 备份文件的存取。目录固定在 App 私有空间，导出/分享走系统的分享面板，不直接写用户存储。
 */
class BackupRepository {

    fun directory(): File =
        File(ksuApp.getExternalFilesDir(null) ?: ksuApp.filesDir, "backups").apply { mkdirs() }

    fun list(): List<BackupEntry> =
        directory().listFiles { f -> f.isFile && f.name.endsWith(".zip") }
            ?.mapNotNull { readEntry(it) }
            ?.sortedByDescending { it.createdAt }
            .orEmpty()

    /** 打一份新备份；失败抛 [BackupException]，调用方负责把原因显示出来。 */
    fun create(kind: BackupKind): BackupEntry {
        val dir = directory()
        val payload = File(ksuApp.cacheDir, "diksu-backup-payload.tar").apply { delete() }
        val tarCmd = buildString {
            append("tar -cf ").append(shellQuote(payload.absolutePath))
            append(" -C ").append(shellQuote(kind.base))
            kind.paths.forEach { append(' ').append(shellQuote(it)) }
        }
        val result = getRootShell().newJob().add(tarCmd).exec()
        try {
            if (!result.isSuccess) {
                throw BackupException("打包失败（tar 退出码 ${result.code}）")
            }
            if (!payload.exists() || payload.length() == 0L) {
                throw BackupException("打包结果为空：${kind.base} 下没有可备份的内容")
            }
            val createdAt = System.currentTimeMillis()
            val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date(createdAt))
            val out = File(dir, "${kind.id}-$stamp.zip")
            ZipOutputStream(out.outputStream().buffered()).use { zip ->
                zip.putNextEntry(ZipEntry(MANIFEST))
                zip.write(manifestOf(kind, createdAt, payload.length()).toString(2).toByteArray())
                zip.closeEntry()
                zip.putNextEntry(ZipEntry(PAYLOAD))
                payload.inputStream().buffered().use { it.copyTo(zip) }
                zip.closeEntry()
            }
            return BackupEntry(out, kind, createdAt, out.length(), BuildConfig.VERSION_NAME)
        } finally {
            payload.delete()
        }
    }

    /**
     * 把一份备份解回原处。
     *
     * 先解到缓存再 tar -x 回 /data/adb，最后 restorecon —— 少了这一步，模块文件会带着 cache
     * 的 SELinux 上下文落在目标目录里，重启后要么加载不了，要么被守卫判成异常。
     */
    fun restore(entry: BackupEntry) {
        val payload = File(ksuApp.cacheDir, "diksu-restore-payload.tar").apply { delete() }
        try {
            ZipFile(entry.file).use { zip ->
                val ze = zip.getEntry(PAYLOAD) ?: throw BackupException("备份里缺少 payload")
                zip.getInputStream(ze).use { input ->
                    payload.outputStream().buffered().use { input.copyTo(it) }
                }
            }
            val cmd = buildString {
                append("tar -xf ").append(shellQuote(payload.absolutePath))
                append(" -C ").append(shellQuote(entry.kind.base))
                append(" && restorecon -R ").append(shellQuote(entry.kind.base))
            }
            val result = getRootShell().newJob().add(cmd).exec()
            if (!result.isSuccess) {
                throw BackupException("解包失败（tar 退出码 ${result.code}）")
            }
        } finally {
            payload.delete()
        }
    }

    fun delete(entry: BackupEntry) {
        if (!entry.file.delete() && entry.file.exists()) {
            throw BackupException("删除失败：${entry.file.name}")
        }
    }

    private fun readEntry(file: File): BackupEntry? = runCatching {
        ZipFile(file).use { zip ->
            val ze = zip.getEntry(MANIFEST) ?: return null
            val json = zip.getInputStream(ze).bufferedReader().readText().let(::JSONObject)
            val kind = BackupKind.entries.firstOrNull { it.id == json.optString("kind") } ?: return null
            BackupEntry(
                file = file,
                kind = kind,
                createdAt = json.optLong("createdAt", file.lastModified()),
                sizeBytes = json.optLong("payloadBytes", file.length()),
                appVersion = json.optString("appVersion", ""),
            )
        }
    }.getOrNull()

    private fun manifestOf(kind: BackupKind, createdAt: Long, payloadBytes: Long) = JSONObject().apply {
        put("format", 1)
        put("kind", kind.id)
        put("createdAt", createdAt)
        put("payloadBytes", payloadBytes)
        put("appVersion", BuildConfig.VERSION_NAME)
        put("appVersionCode", BuildConfig.VERSION_CODE)
    }

    /** 路径里有空格也不该被 shell 拆开。 */
    private fun shellQuote(path: String): String = "'" + path.replace("'", "'\\''") + "'"

    companion object {
        private const val MANIFEST = "manifest.json"
        private const val PAYLOAD = "payload.tar"
    }
}
