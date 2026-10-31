package me.diksu.ultra.theme

import android.net.Uri
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.diksu.ultra.data.repository.currentThemeId
import me.diksu.ultra.data.repository.setCurrentThemeId
import me.diksu.ultra.ksuApp
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.UUID

/** One theme that is installed under `filesDir/themes/<id>/`. */
data class InstalledTheme(
    val id: String,
    val name: String,
    val author: String,
    val version: String,
    val dir: File,
    /** Entry count of the package, for the "N assets" line in the list. */
    val assetCount: Int,
)

/**
 * Result of one import. The import preview is built from this: "will apply N / not applied M",
 * plus the reasons. [id] is null only when nothing was installed.
 */
data class ThemeImportResult(
    val applied: List<String> = emptyList(),
    val ignored: List<String> = emptyList(),
    val errors: List<String> = emptyList(),
    val id: String? = null,
    val dir: File? = null,
) {
    val ok: Boolean get() = id != null && errors.isEmpty()
}

/**
 * On-disk theme library plus the "current theme" pointer.
 *
 * Layout: `filesDir/themes/<id>/` holds the unpacked package (manifest and assets), so the
 * renderer never decrypts anything at startup. Imports are transactional: the package is
 * decrypted and validated in a scratch directory, and only a fully valid one is moved into
 * place - a failed import leaves both the library and the current selection untouched.
 *
 * The current selection is a plain shared-preference key (not one of the per-skin `uiPrefix`
 * keys): a theme is not a skin-local setting, and `docs/theme-acceptance.md` S2 keeps theme
 * state out of that namespace.
 */
object ThemeStore {

    private const val TAG = "ThemeStore"
    private const val DIR_NAME = "themes"
    private val ID_RULES = Regex("^[a-z0-9][a-z0-9-]{0,63}$")

    /** Root under `filesDir`; created on demand. */
    fun root(): File = File(ksuApp.filesDir, DIR_NAME)

    fun list(): List<InstalledTheme> {
        val root = root()
        val dirs = root.listFiles { file -> file.isDirectory } ?: return emptyList()
        return dirs.mapNotNull { dir -> readInstalled(dir) }.sortedBy { it.name.lowercase() }
    }

    fun find(id: String): InstalledTheme? {
        if (!ID_RULES.matches(id)) return null
        val dir = File(root(), id)
        return if (dir.isDirectory) readInstalled(dir) else null
    }

    /** Id of the theme the Themed style should load, or null when none is selected. */
    fun current(): InstalledTheme? = currentThemeId()?.let { find(it) }

    fun setCurrent(id: String?) {
        setCurrentThemeId(id)
    }

    /**
     * Imports [uri]. Never throws: every failure comes back in [ThemeImportResult.errors] so
     * the UI can explain it (bad zip, wrong key, over budget, unsafe path, bad manifest).
     */
    suspend fun import(uri: Uri): ThemeImportResult = withContext(Dispatchers.IO) {
        val root = root()
        if (!root.exists() && !root.mkdirs()) {
            return@withContext ThemeImportResult(errors = listOf("cannot create the theme directory"))
        }
        val scratch = File(root, ".import-${UUID.randomUUID()}")
        try {
            if (!scratch.mkdirs()) {
                return@withContext ThemeImportResult(errors = listOf("cannot create the import directory"))
            }
            val stream = ksuApp.contentResolver.openInputStream(uri)
                ?: return@withContext ThemeImportResult(errors = listOf("cannot read the selected file"))
            stream.use { input ->
                val extracted = ThemeContainer.extract(input, scratch)
                val manifest = try {
                    ThemeManifestParser.parse(extracted.manifestText)
                } catch (e: ThemeManifestException) {
                    Log.w(TAG, "import rejected: ${e.message}")
                    return@withContext ThemeImportResult(errors = listOfNotNull(e.message))
                }
                val validation = ThemeValidator.validate(manifest, extracted.files.keys)
                val spec = validation.spec
                    ?: return@withContext ThemeImportResult(
                        applied = validation.applied,
                        ignored = validation.ignored,
                        errors = listOf("the manifest produced no theme"),
                    )
                val id = themeId(spec.name, extracted.manifestText)
                val target = File(root, id)

                // Replace any previous copy only after everything above succeeded.
                if (target.exists() && !target.deleteRecursively()) {
                    return@withContext ThemeImportResult(
                        applied = validation.applied,
                        ignored = validation.ignored,
                        errors = listOf("cannot replace the existing theme $id"),
                    )
                }
                if (!scratch.renameTo(target)) {
                    return@withContext ThemeImportResult(
                        applied = validation.applied,
                        ignored = validation.ignored,
                        errors = listOf("cannot move the theme into place"),
                    )
                }
                Log.i(TAG, "imported $id: ${validation.applied.size} applied, ${validation.ignored.size} ignored")
                return@withContext ThemeImportResult(
                    applied = validation.applied,
                    ignored = validation.ignored,
                    errors = validation.errors,
                    id = id,
                    dir = target,
                )
            }
        } catch (e: ThemeContainerException) {
            Log.w(TAG, "import rejected: ${e.message}")
            ThemeImportResult(errors = listOf(describe(e)))
        } catch (e: Exception) {
            // Untrusted input must never take the app down.
            Log.w(TAG, "import failed", e)
            ThemeImportResult(errors = listOf(e.message ?: e.javaClass.simpleName))
        } finally {
            if (scratch.exists()) scratch.deleteRecursively()
        }
    }

    /** Deletes an installed theme; clears the selection when it was the current one. */
    suspend fun delete(id: String): Boolean = withContext(Dispatchers.IO) {
        if (!ID_RULES.matches(id)) return@withContext false
        val dir = File(root(), id)
        if (!dir.exists()) return@withContext false
        if (currentThemeId() == id) setCurrentThemeId(null)
        val deleted = dir.deleteRecursively()
        if (!deleted) Log.w(TAG, "could not delete $id")
        deleted
    }

    /**
     * Writes [id] back out as an encrypted package. Returns false when the theme is missing
     * or cannot be written; the caller owns [out] and is expected to close it.
     */
    suspend fun export(id: String, out: java.io.OutputStream): Boolean = withContext(Dispatchers.IO) {
        if (!ID_RULES.matches(id)) return@withContext false
        val dir = File(root(), id)
        if (!dir.isDirectory) return@withContext false
        try {
            ThemeContainer.export(dir, out)
            out.flush()
            true
        } catch (e: Exception) {
            Log.w(TAG, "export of $id failed", e)
            false
        }
    }

    private fun readInstalled(dir: File): InstalledTheme? {
        // The folder name is the id; a folder that no longer parses is skipped instead of
        // breaking the whole list.
        val manifestFile = File(dir, ThemeContainer.MANIFEST_FILE)
        if (!manifestFile.isFile) return null
        val manifest = try {
            ThemeManifestParser.parse(manifestFile.readText(Charsets.UTF_8))
        } catch (e: Exception) {
            Log.w(TAG, "skipping unreadable theme in ${dir.name}")
            return null
        }
        return InstalledTheme(
            id = dir.name,
            name = manifest.meta?.name?.trim().orEmpty().ifEmpty { dir.name },
            author = manifest.meta?.author?.trim().orEmpty(),
            version = manifest.meta?.version?.trim().orEmpty().ifEmpty { "1" },
            dir = dir,
            assetCount = dir.listFiles()?.count { it.isFile } ?: 0,
        )
    }

    /** Stable across re-imports: same manifest and name -> same folder. */
    private fun themeId(name: String, manifestText: String): String {
        val slug = name.lowercase()
            .map { if (it.isLetterOrDigit()) it else '-' }
            .joinToString("")
            .trim('-')
            .take(40)
            .ifEmpty { "theme" }
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(manifestText.toByteArray(Charsets.UTF_8))
        val suffix = digest.take(6).joinToString("") { "%02x".format(it) }
        return "$slug-$suffix"
    }

    private fun describe(e: ThemeContainerException): String = when (e) {
        is ThemeContainerException.Unreadable -> "the file could not be read (${e.message})"
        is ThemeContainerException.CryptoFailed -> "decryption failed - not a theme package, or corrupted"
        is ThemeContainerException.NotZip -> "decryption produced no zip - wrong key or corrupted file"
        is ThemeContainerException.UnsafeEntry -> "the package contains an unsafe path (${e.entryName})"
        is ThemeContainerException.DuplicateEntry -> "the package contains ${e.entryName} twice"
        is ThemeContainerException.BudgetExceeded -> e.message ?: "the package is too large"
        is ThemeContainerException.MissingManifest -> "the package has no ${ThemeContainer.MANIFEST_FILE}"
    }
}

/** Convenience for callers that already have a destination file. */
fun ThemeStore.exportToFile(id: String, file: File): Boolean = runCatching {
    file.parentFile?.mkdirs()
    FileOutputStream(file).use { out -> exportBlocking(id, out) }
}.getOrDefault(false)

/** Blocking variant used from the background thread of [ThemeStore.export]. */
private fun exportBlocking(id: String, out: java.io.OutputStream): Boolean {
    val dir = File(ThemeStore.root(), id)
    if (!dir.isDirectory) return false
    return runCatching { ThemeContainer.export(dir, out) }.getOrDefault(false)
}
