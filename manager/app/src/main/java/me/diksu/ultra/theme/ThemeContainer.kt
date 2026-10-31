package me.diksu.ultra.theme

import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.DataInputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.zip.ZipEntry
import java.util.zip.ZipException
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import javax.crypto.Cipher
import javax.crypto.CipherInputStream
import javax.crypto.CipherOutputStream
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * The theme-package format shared by FolkPatch and FinaSU: a 16-byte IV followed by
 * AES-256-CBC(`java.util.zip` stream) of a zip that holds `theme.json` and the assets.
 *
 * ```text
 * <16 byte IV> + AES-256-CBC( ZIP )
 *   key = SHA-256("FolkPatchThemeSecretKey2025")
 * ```
 *
 * Everything here treats the file as untrusted input: entry names are checked for zip-slip,
 * the entry count and the decompressed sizes are capped while streaming (so a zip bomb is
 * stopped before it can fill the disk), and decryption failures are reported as such instead
 * of surfacing as a raw crypto exception. Nothing is written outside [extract]'s target
 * directory, and a failure leaves only the caller's temporary directory to delete.
 *
 * This file is pure JVM on purpose (no Android, no logging) so the parser can be unit-tested.
 */
object ThemeContainer {

    /** The passphrase both upstream projects use; changing it breaks compatibility. */
    const val PASSPHRASE = "FolkPatchThemeSecretKey2025"

    const val MANIFEST_FILE = "theme.json"
    const val IV_BYTES = 16

    /** Stricter than upstream on purpose (see `docs/theme-freedom-plan.md` §3). */
    const val MAX_ENTRIES = 64
    const val MAX_ENTRY_BYTES = 8L * 1024 * 1024
    const val MAX_TOTAL_BYTES = 64L * 1024 * 1024
    const val MAX_MANIFEST_BYTES = 1L * 1024 * 1024

    private const val BUFFER = 64 * 1024
    private const val CRYPTO = "AES/CBC/PKCS5Padding"
    private const val KEY_ALGORITHM = "AES"

    private val key: ByteArray by lazy {
        MessageDigest.getInstance("SHA-256").digest(PASSPHRASE.toByteArray(Charsets.UTF_8))
    }

    /** What came out of a successfully decoded archive. */
    data class Extracted(
        /** Relative entry name -> extracted file. */
        val files: Map<String, File>,
        val manifestText: String,
    )

    /**
     * Decrypts [input] and unpacks it below [destDir].
     *
     * A failure may leave partial files inside [destDir]; callers extract into a scratch
     * directory and delete it unless this returns normally.
     */
    fun extract(input: InputStream, destDir: File): Extracted {
        val iv = ByteArray(IV_BYTES)
        try {
            DataInputStream(input).readFully(iv)
        } catch (e: IOException) {
            throw ThemeContainerException.Unreadable("archive is shorter than the $IV_BYTES-byte IV", e)
        }

        val cipher = decryptCipher(iv)
        if (!destDir.exists() && !destDir.mkdirs()) {
            throw ThemeContainerException.Unreadable("cannot create the extraction directory")
        }
        val root = destDir.canonicalFile
        val rootPrefix = root.path + File.separator

        val files = LinkedHashMap<String, File>()
        var manifestText: String? = null
        var entryCount = 0
        var totalBytes = 0L

        val zip = ZipInputStream(BufferedInputStream(CipherInputStream(input, cipher), BUFFER))
        try {
            while (true) {
                val entry = try {
                    zip.nextEntry
                } catch (e: ZipException) {
                    throw ThemeContainerException.NotZip()
                } catch (e: IOException) {
                    throw ThemeContainerException.CryptoFailed("could not decrypt the archive", e)
                } ?: break

                if (entry.isDirectory) continue
                entryCount++
                if (entryCount > MAX_ENTRIES) {
                    throw ThemeContainerException.BudgetExceeded("more than $MAX_ENTRIES entries")
                }

                val name = safeName(entry.name) ?: throw ThemeContainerException.UnsafeEntry(entry.name)
                if (files.containsKey(name)) throw ThemeContainerException.DuplicateEntry(name)

                val isManifest = name.equals(MANIFEST_FILE, ignoreCase = true)
                val limit = if (isManifest) MAX_MANIFEST_BYTES else MAX_ENTRY_BYTES

                val target = File(root, name)
                val parent = target.parentFile
                if (parent != null) {
                    if (!parent.exists() && !parent.mkdirs()) {
                        throw ThemeContainerException.Unreadable("cannot create the directory for $name")
                    }
                    if (!parent.canonicalPath.startsWith(rootPrefix) && parent.canonicalPath != root.path) {
                        throw ThemeContainerException.UnsafeEntry(entry.name)
                    }
                }
                val canonical = target.canonicalPath
                if (!canonical.startsWith(rootPrefix)) {
                    throw ThemeContainerException.UnsafeEntry(entry.name)
                }

                val buffer = ByteArray(BUFFER)
                var entryBytes = 0L
                val sink = BufferedOutputStream(FileOutputStream(target), BUFFER)
                try {
                    while (true) {
                        val read = try {
                            zip.read(buffer)
                        } catch (e: ZipException) {
                            throw ThemeContainerException.NotZip()
                        } catch (e: IOException) {
                            throw ThemeContainerException.CryptoFailed("could not decrypt the archive", e)
                        }
                        if (read < 0) break
                        entryBytes += read
                        if (entryBytes > limit) {
                            throw ThemeContainerException.BudgetExceeded("$name is larger than $limit bytes")
                        }
                        totalBytes += read
                        if (totalBytes > MAX_TOTAL_BYTES) {
                            throw ThemeContainerException.BudgetExceeded("archive expands past $MAX_TOTAL_BYTES bytes")
                        }
                        sink.write(buffer, 0, read)
                    }
                } finally {
                    sink.close()
                }

                files[name] = target
                if (isManifest) {
                    // Written to the extraction directory like every other entry, then read
                    // back as UTF-8; the size cap above already bounded it.
                    manifestText = target.readText(Charsets.UTF_8)
                }
            }
        } finally {
            runCatching { zip.close() }
        }

        if (entryCount == 0) throw ThemeContainerException.NotZip()
        val manifest = manifestText ?: throw ThemeContainerException.MissingManifest()
        return Extracted(files, manifest)
    }

    /**
     * Writes [dir] back out in the format above (random IV, fresh zip, zip entries keep their
     * relative names). Used by the exporter; the same layout is what [extract] reads.
     */
    fun export(dir: File, out: OutputStream) {
        val iv = ByteArray(IV_BYTES)
        SecureRandom().nextBytes(iv)
        val cipher = Cipher.getInstance(CRYPTO).apply {
            init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, KEY_ALGORITHM), IvParameterSpec(iv))
        }
        out.write(iv)
        out.flush()

        CipherOutputStream(out, cipher).use { encrypted ->
            ZipOutputStream(BufferedOutputStream(encrypted, BUFFER)).use { zip ->
                val root = dir.canonicalFile
                val files = root.walkTopDown()
                    .filter { it.isFile }
                    .sortedBy { it.path }
                    .toList()
                for (file in files) {
                    val name = file.canonicalFile.path
                        .removePrefix(root.path + File.separator)
                        .replace(File.separatorChar, '/')
                    zip.putNextEntry(ZipEntry(name))
                    file.inputStream().use { it.copyTo(zip, BUFFER) }
                    zip.closeEntry()
                }
            }
        }
    }

    private fun decryptCipher(iv: ByteArray): Cipher = try {
        Cipher.getInstance(CRYPTO).apply {
            init(Cipher.DECRYPT_MODE, SecretKeySpec(key, KEY_ALGORITHM), IvParameterSpec(iv))
        }
    } catch (e: Exception) {
        throw ThemeContainerException.CryptoFailed("AES-CBC is not available", e)
    }

    /**
     * A relative entry name that can only land inside the target directory, or null.
     * Backslashes, absolute paths and any `.`/`..` segment are refused outright rather than
     * normalised - a theme has no business containing them.
     */
    private fun safeName(raw: String): String? {
        if (raw.isEmpty() || raw.length > 240) return null
        if (raw.startsWith('/') || raw.contains('\\') || raw.contains('\u0000')) return null
        val parts = raw.split('/')
        if (parts.any { it.isEmpty() || it == "." || it == ".." }) return null
        return raw
    }
}

/** Why an archive was refused. Every case is a normal import failure, never a crash. */
sealed class ThemeContainerException(message: String, cause: Throwable? = null) : Exception(message, cause) {
    /** The file is too short, unreadable, or its directory cannot be created. */
    class Unreadable(message: String, cause: Throwable? = null) : ThemeContainerException(message, cause)

    /** AES failed (wrong key, truncated ciphertext, padding error). */
    class CryptoFailed(message: String, cause: Throwable? = null) : ThemeContainerException(message, cause)

    /** Decryption produced something that is not a zip, or a zip with no entries. */
    class NotZip : ThemeContainerException("the archive does not contain a zip")

    /** Entry name would escape the target directory (zip slip). */
    class UnsafeEntry(val entryName: String) : ThemeContainerException("unsafe entry name")
    class DuplicateEntry(val entryName: String) : ThemeContainerException("duplicate entry")

    /** Entry count or decompressed size over budget. */
    class BudgetExceeded(message: String) : ThemeContainerException(message)

    /** A valid zip without `theme.json`. */
    class MissingManifest : ThemeContainerException("the archive has no ${ThemeContainer.MANIFEST_FILE}")
}
