package me.diksu.ultra.data.backup

import android.os.Process
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
        val payload = File(ksuApp.cacheDir, "diksu-backup-payload.tar")
        // root 建的文件属主是 root：先由 root 删掉残留，打完再把属主改回本应用，
        // 否则 App 读不回自己的临时文件（实测 EACCES），也删不掉旧的那份。
        val uid = Process.myUid()
        val tarCmd = buildString {
            append("rm -f ").append(shellQuote(payload.absolutePath)).append(" && ")
            append("tar -cf ").append(shellQuote(payload.absolutePath))
            append(" -C ").append(shellQuote(kind.base))
            kind.paths.forEach { append(' ').append(shellQuote(it)) }
            append(" && chown ").append(uid).append(':').append(uid).append(' ')
            .append(shellQuote(payload.absolutePath))
            append(" && chmod 600 ").append(shellQuote(payload.absolutePath))
        }
        val out = ArrayList<String>()
        val err = ArrayList<String>()
        val result = getRootShell().newJob().add(tarCmd).to(out, err).exec()

        var tmp: File? = null
        try {
            if (!result.isSuccess) {
                throw BackupException("打包失败：${firstReason(out, err, result.code)}")
            }
            val payloadBytes = payload.length()
            if (!payload.exists() || payloadBytes == 0L) {
                throw BackupException("打包结果为空：${kind.base} 下没有可备份的内容")
            }

            val createdAt = System.currentTimeMillis()
            val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date(createdAt))
            val target = File(dir, "${kind.id}-$stamp.zip")
            // 先写临时文件，成功才改名：中途失败不能把「有清单、payload 是空」的坏包留在列表里。
            val staging = File(dir, "${kind.id}-$stamp.zip.tmp")
            tmp = staging
            ZipOutputStream(staging.outputStream().buffered()).use { zip ->
                zip.putNextEntry(ZipEntry(MANIFEST))
                zip.write(manifestOf(kind, createdAt, payloadBytes).toString(2).toByteArray())
                zip.closeEntry()
                zip.putNextEntry(ZipEntry(PAYLOAD))
                val copied = try {
                    payload.inputStream().buffered().use { stream -> stream.copyTo(zip) }
                } catch (e: Exception) {
                    throw BackupException("读回临时打包文件失败（${payload.length()} 字节）：${e.message ?: e::class.java.simpleName}")
                }
                zip.closeEntry()
                if (copied != payloadBytes) {
                    throw BackupException("写入备份时长度对不上：应为 $payloadBytes 字节，实际 $copied")
                }
            }
            if (!staging.renameTo(target)) {
                throw BackupException("备份文件改名失败：${target.name}")
            }
            tmp = null
            return BackupEntry(target, kind, createdAt, target.length(), BuildConfig.VERSION_NAME)
        } finally {
            tmp?.delete()
            payload.delete()
        }
    }

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
            val out = ArrayList<String>()
            val err = ArrayList<String>()
            val result = getRootShell().newJob().add(cmd).to(out, err).exec()
            if (!result.isSuccess) {
                throw BackupException("解包失败：${firstReason(out, err, result.code)}")
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
            // 旧版本失败时可能留下「有清单、payload 为空」的包：不列出来，免得用户以为能恢复。
            val payloadEntry = zip.getEntry(PAYLOAD)
            if (payloadEntry == null || payloadEntry.size <= 0L) return null
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

    /** 把 tar 的第一条抱怨原样带回界面：只报退出码，用户没法判断是权限、路径还是别的问题。 */
    private fun firstReason(out: List<String>, err: List<String>, code: Int): String =
        (err + out).firstOrNull { it.isNotBlank() }?.trim() ?: "退出码 $code"

    /** 路径里有空格也不该被 shell 拆开。 */
    private fun shellQuote(path: String): String = "'" + path.replace("'", "'\\''") + "'"

    companion object {
        private const val MANIFEST = "manifest.json"
        private const val PAYLOAD = "payload.tar"
    }
}
