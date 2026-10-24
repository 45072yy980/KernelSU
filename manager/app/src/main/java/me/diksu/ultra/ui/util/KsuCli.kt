package me.diksu.ultra.ui.util

import android.content.ContentResolver
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.os.Environment
import android.os.Parcelable
import android.os.SystemClock
import android.provider.OpenableColumns
import android.system.Os
import android.util.Log
import com.topjohnwu.superuser.CallbackList
import com.topjohnwu.superuser.Shell
import com.topjohnwu.superuser.ShellUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.parcelize.Parcelize
import me.diksu.ultra.data.repository.isPartitionGuardEnabled
import me.diksu.ultra.BuildConfig
import me.diksu.ultra.Natives
import me.diksu.ultra.core.tasks.BootKernelVersion
import me.diksu.ultra.core.tasks.ExtractImage
import me.diksu.ultra.core.tasks.ProbeResult
import me.diksu.ultra.core.utils.DataSourceChannel
import me.diksu.ultra.data.registry.DownloadVerificationException
import me.diksu.ultra.data.registry.RegistryDownloadStore
import me.diksu.ultra.data.registry.copyForInstall
import me.diksu.ultra.ksuApp
import okhttp3.OkHttpClient
import java.io.File
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import java.util.concurrent.TimeUnit
import java.util.zip.ZipFile

/**
 * @author weishu
 * @date 2023/1/1.
 */
private const val TAG = "KsuCli"

internal fun getKsuDaemonPath(): String {
    return ksuApp.applicationInfo.nativeLibraryDir + File.separator + "libksud.so"
}

data class FlashResult(val code: Int, val err: String, val showReboot: Boolean) {
    constructor(result: Shell.Result, showReboot: Boolean) : this(result.code, result.err.joinToString("\n"), showReboot)
    constructor(result: Shell.Result) : this(result, result.isSuccess)
}

object KsuCli {
    val SHELL: Shell = createRootShell()
    val GLOBAL_MNT_SHELL: Shell = createRootShell(true)
}

fun getRootShell(globalMnt: Boolean = false): Shell {
    return if (globalMnt) KsuCli.GLOBAL_MNT_SHELL else {
        KsuCli.SHELL
    }
}

inline fun <T> withNewRootShell(
    globalMnt: Boolean = false,
    block: Shell.() -> T
): T {
    return createRootShell(globalMnt).use(block)
}

fun Uri.getFileName(context: Context): String? {
    var fileName: String? = null
    val contentResolver: ContentResolver = context.contentResolver
    val cursor: Cursor? = contentResolver.query(this, null, null, null, null)
    cursor?.use {
        if (it.moveToFirst()) {
            fileName = it.getString(it.getColumnIndexOrThrow(OpenableColumns.DISPLAY_NAME))
        }
    }
    return fileName
}

fun createRootShell(globalMnt: Boolean = false): Shell {
    Shell.enableVerboseLogging = BuildConfig.DEBUG
    val builder = Shell.Builder.create()
    return try {
        if (globalMnt) {
            builder.build(getKsuDaemonPath(), "debug", "su", "-g")
        } else {
            builder.build(getKsuDaemonPath(), "debug", "su")
        }
    } catch (e: Throwable) {
        Log.w(TAG, "ksu failed: ", e)
        try {
            if (globalMnt) {
                builder.build("su", "-mm")
            } else {
                builder.build("su")
            }
        } catch (e: Throwable) {
            Log.e(TAG, "su failed: ", e)
            builder.build("sh")
        }
    }
}

fun execKsud(args: String, newShell: Boolean = false, globalMnt: Boolean = false): Boolean {
    return if (newShell) {
        withNewRootShell(globalMnt = globalMnt) {
            ShellUtils.fastCmdResult(this, "${getKsuDaemonPath()} $args")
        }
    } else {
        ShellUtils.fastCmdResult(getRootShell(globalMnt), "${getKsuDaemonPath()} $args")
    }
}

suspend fun getFeatureStatus(feature: String): String = withContext(Dispatchers.IO) {
    val shell = getRootShell()
    val out = shell.newJob()
        .add("${getKsuDaemonPath()} feature check $feature").to(ArrayList<String>(), null).exec().out
    out.firstOrNull()?.trim().orEmpty()
}

suspend fun getFeaturePersistValue(feature: String): Long? = readFeatureValue("--config $feature")

// Current value in kernel
suspend fun getFeatureValue(feature: String): Long? = readFeatureValue(feature)

private suspend fun readFeatureValue(args: String): Long? = withContext(Dispatchers.IO) {
    val shell = getRootShell()
    val out = shell.newJob()
        .add("${getKsuDaemonPath()} feature get $args").to(ArrayList<String>(), null).exec().out
    val valueLine = out.firstOrNull { it.trim().startsWith("Value:") } ?: return@withContext null
    valueLine.substringAfter("Value:").trim().toLongOrNull()
}

fun install() {
    val start = SystemClock.elapsedRealtime()
    val libadbroot = File(ksuApp.applicationInfo.nativeLibraryDir, "libadbroot.so").absolutePath
    val result = execKsud("install --libadbroot $libadbroot --data-path ${ksuApp.applicationInfo.deviceProtectedDataDir}", true)
    Log.w(TAG, "install result: $result, cost: ${SystemClock.elapsedRealtime() - start}ms")
}

fun listModules(): String {
    val shell = getRootShell()

    val out = shell.newJob()
        .add("${getKsuDaemonPath()} module list").to(ArrayList(), null).exec().out
    return out.joinToString("\n").ifBlank { "[]" }
}

fun getSuperuserCount(): Int {
    return Natives.getSuperuserCount()
}

fun toggleModule(id: String, enable: Boolean): Boolean {
    val cmd = if (enable) {
        "module enable $id"
    } else {
        "module disable $id"
    }
    val result = execKsud(cmd, true)
    Log.i(TAG, "$cmd result: $result")
    return result
}

fun undoUninstallModule(id: String): Boolean {
    val cmd = "module undo-uninstall $id"
    val result = execKsud(cmd, true)
    Log.i(TAG, "undo uninstall module $id result: $result")
    return result
}

fun uninstallModule(id: String): Boolean {
    val cmd = "module uninstall $id"
    val result = execKsud(cmd, true)
    Log.i(TAG, "uninstall module $id result: $result")
    return result
}

private fun flashWithIO(
    cmd: String,
    onStdout: (String) -> Unit,
    onStderr: (String) -> Unit
): Shell.Result {

    val stdoutCallback: CallbackList<String?> = object : CallbackList<String?>() {
        override fun onAddElement(s: String?) {
            onStdout(s ?: "")
        }
    }

    val stderrCallback: CallbackList<String?> = object : CallbackList<String?>() {
        override fun onAddElement(s: String?) {
            onStderr(s ?: "")
        }
    }

    return withNewRootShell {
        newJob().add(cmd).to(stdoutCallback, stderrCallback).exec()
    }
}

fun flashModule(
    uri: Uri,
    onStdout: (String) -> Unit,
    onStderr: (String) -> Unit
): FlashResult {
    // Registry downloads live in app-private storage and carry their expected size + SHA-256;
    // the exact copy handed to ksud is re-hashed right before installing.
    val storeRoot = File(ksuApp.filesDir, RegistryDownloadStore.DIR_NAME)
    val expected = if (uri.scheme == ContentResolver.SCHEME_FILE) {
        uri.path?.let { RegistryDownloadStore.expectedFor(storeRoot, File(it)) }
    } else {
        null
    }
    val file = File.createTempFile("module-", ".zip", ksuApp.cacheDir)
    try {
        try {
            val input = ksuApp.contentResolver.openInputStream(uri) ?: throw IOException("Cannot open $uri")
            input.use { copyForInstall(it, file, expected) }
        } catch (e: IOException) {
            Log.w("KernelSU", "module $uri not installed: ${e.message}")
            if (e is DownloadVerificationException) {
                // Corrupted or tampered after download: never keep it around for a retry.
                uri.path?.let { RegistryDownloadStore.delete(storeRoot, File(it)) }
            }
            val message = "! ${e.message ?: "Failed to read module"}"
            onStderr(message)
            return FlashResult(1, message, false)
        }
        val cmd = "module install ${file.absolutePath}"
        // Opt the install into the system-partition guard when it is enabled
        // (always in jailbreak mode, otherwise the user setting).
        val guardEnv = if (isPartitionGuardEnabled()) "KSU_PARTITION_GUARD=1 " else ""
        val result = flashWithIO("$guardEnv${getKsuDaemonPath()} $cmd", onStdout, onStderr)
        Log.i("KernelSU", "install module $uri result: $result")
        return FlashResult(result, result.isSuccess && moduleNeedsReboot(file))
    } finally {
        file.delete()
    }
}

private val MODULE_ID_REGEX = Regex("^[a-zA-Z][a-zA-Z0-9._-]+$")

// A hot installed module goes live without leaving the "update" flag behind
private fun moduleNeedsReboot(zip: File): Boolean {
    val id = runCatching {
        ZipFile(zip).use { archive ->
            val entry = archive.getEntry("module.prop") ?: return@use null
            archive.getInputStream(entry).bufferedReader().useLines { lines ->
                lines.map { it.trim() }.firstOrNull { it.startsWith("id=") }?.substringAfter('=')?.trim()
            }
        }
    }.getOrNull()
    if (id == null || !MODULE_ID_REGEX.matches(id)) return true
    val dir = "/data/adb/modules/$id"
    // Any failure to check counts as needing a reboot
    return !ShellUtils.fastCmdResult(getRootShell(), "[ -d $dir ] && [ ! -e $dir/update ]")
}

fun runModuleAction(
    moduleId: String, onStdout: (String) -> Unit, onStderr: (String) -> Unit
): Boolean {
    val stdoutCallback: CallbackList<String?> = object : CallbackList<String?>() {
        override fun onAddElement(s: String?) {
            onStdout(s ?: "")
        }
    }

    val stderrCallback: CallbackList<String?> = object : CallbackList<String?>() {
        override fun onAddElement(s: String?) {
            onStderr(s ?: "")
        }
    }

    val result = withNewRootShell(true) {
        newJob().add("${getKsuDaemonPath()} module action $moduleId")
            .to(stdoutCallback, stderrCallback).exec()
    }

    Log.i("KernelSU", "Module runAction result: $result")

    return result.isSuccess
}

/**
 * Runs a prepared one-shot script command (see ScriptCommand) in a new root shell in the
 * global mount namespace, like a module action. Returns the exit code.
 */
fun runOneshotScript(
    command: String, onStdout: (String) -> Unit, onStderr: (String) -> Unit
): Int {
    val stdoutCallback: CallbackList<String?> = object : CallbackList<String?>() {
        override fun onAddElement(s: String?) {
            onStdout(s ?: "")
        }
    }

    val stderrCallback: CallbackList<String?> = object : CallbackList<String?>() {
        override fun onAddElement(s: String?) {
            onStderr(s ?: "")
        }
    }

    val result = withNewRootShell(true) {
        newJob().add(command).to(stdoutCallback, stderrCallback).exec()
    }
    Log.i(TAG, "one-shot script exit code: ${result.code}")
    return result.code
}

/** Removes a script's extraction directory, including files the script created as root. */
fun removeAsRoot(path: String): Boolean {
    val escaped = "'" + path.replace("'", "'\\''") + "'"
    return ShellUtils.fastCmdResult(getRootShell(), "rm -rf $escaped")
}

fun restoreBoot(
    onStdout: (String) -> Unit, onStderr: (String) -> Unit
): FlashResult {
    val result = flashWithIO("${getKsuDaemonPath()} boot-restore -f", onStdout, onStderr)
    return FlashResult(result)
}

fun uninstallPermanently(
    onStdout: (String) -> Unit, onStderr: (String) -> Unit
): FlashResult {
    // The Manager can be running under a name of its own, so ask the app what it is called.
    val result = flashWithIO("${getKsuDaemonPath()} uninstall --package-name ${ksuApp.packageName}", onStdout, onStderr)
    return FlashResult(result)
}

@Parcelize
sealed class LkmSelection : Parcelable {
    @Parcelize
    data class LkmUri(val uri: Uri) : LkmSelection()

    @Parcelize
    data class KmiString(val value: String) : LkmSelection()

    @Parcelize
    data object KmiNone : LkmSelection()
}

private fun writeLkmFile(lkm: LkmSelection): File? {
    if (lkm !is LkmSelection.LkmUri) return null
    val file = File(ksuApp.cacheDir, "kernelsu-tmp-lkm.ko")
    ksuApp.contentResolver.openInputStream(lkm.uri)?.use { input ->
        file.outputStream().use { output -> input.copyTo(output) }
    }
    return file
}

// A single module name as accepted by ksud's --block-module parser.
private fun isValidBlockModuleName(name: String): Boolean =
    name.isNotEmpty() && name.length <= 64 &&
        name.all { it in 'a'..'z' || it in 'A'..'Z' || it in '0'..'9' || it == '_' }

/** Parse the comma-separated block-modules field into the non-empty names. */
fun parseBlockModules(input: String): List<String> =
    input.split(',').map { it.trim() }.filter { it.isNotEmpty() }

// The kernel copies the block_modules= value into a 256-byte buffer, leaving
// 255 bytes; a longer list would be silently truncated, leaving some modules
// unblocked. Reject it here too.
private const val MAX_BLOCK_MODULES_LEN = 255

/** Whether every name in the block-modules field is valid (empty is valid). */
fun isBlockModulesValid(input: String): Boolean {
    val names = parseBlockModules(input)
    return names.all(::isValidBlockModuleName) &&
        names.joinToString(",").length <= MAX_BLOCK_MODULES_LEN
}

private fun bootPatchFlags(
    allowShell: Boolean,
    enableAdb: Boolean,
    forceBackup: Boolean,
    blockModules: String,
): String = buildString {
    if (allowShell) append(" --allow-shell")
    if (enableAdb) append(" --enable-adbd")
    if (forceBackup) append(" --backup")
    // Names are validated in the UI before install; pass them through without
    // mutating. Drop the flag entirely (never inject) if anything slips through.
    val names = parseBlockModules(blockModules)
    if (names.isNotEmpty() && names.all(::isValidBlockModuleName)) {
        append(" --block-module ${names.joinToString(",")}")
    }
}

fun installBoot(
    bootUri: Uri?,
    lkm: LkmSelection,
    ota: Boolean,
    partition: String?,
    allowShell: Boolean,
    enableAdb: Boolean,
    forceBackup: Boolean,
    blockModules: String,
    onStdout: (String) -> Unit,
    onStderr: (String) -> Unit,
): FlashResult {
    val resolver = ksuApp.contentResolver

    val bootFile = bootUri?.let { uri ->
        with(resolver.openInputStream(uri)) {
            val bootFile = File(ksuApp.cacheDir, "boot.img")
            bootFile.outputStream().use { output ->
                this?.copyTo(output)
            }

            bootFile
        }
    }

    var cmd = "boot-patch"

    cmd += if (bootFile == null) {
        // no boot.img, use -f to flash
        " -f"
    } else {
        " -b ${bootFile.absolutePath}"
    }
    cmd += bootPatchFlags(allowShell, enableAdb, forceBackup, blockModules)

    if (ota) {
        cmd += " -u"
    }

    val lkmFile = writeLkmFile(lkm)
    if (lkmFile != null) {
        cmd += " -m ${lkmFile.absolutePath}"
    } else if (lkm is LkmSelection.KmiString) {
        cmd += " --kmi ${lkm.value}"
    }

    if (bootFile != null) {
        val downloadsDir =
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        cmd += " -o $downloadsDir"
    }

    partition?.let { part ->
        cmd += " --partition $part"
    }

    val result = flashWithIO("${getKsuDaemonPath()} $cmd", onStdout, onStderr)
    Log.i("KernelSU", "install boot result: ${result.isSuccess}")

    bootFile?.delete()
    lkmFile?.delete()

    // if boot uri is empty, it is direct install, when success, we should show reboot button
    val showReboot = bootUri == null && result.isSuccess // we create a temporary val here, to avoid calc showReboot double
    if (showReboot) { // because we decide do not update ksud when startActivity
        install() // install ksud here
    }
    return FlashResult(result, showReboot)
}

fun downloadBoot(
    url: String,
    partition: String,
    lkm: LkmSelection,
    allowShell: Boolean,
    enableAdb: Boolean,
    forceBackup: Boolean,
    blockModules: String,
    onStdout: (String) -> Unit,
    onStderr: (String) -> Unit,
): FlashResult {
    val bootFile = File(ksuApp.cacheDir, "download-boot.img")
    var probedKmi: String? = null
    try {
        onStdout("- Downloading and extracting boot image")
        val channel = DataSourceChannel(newDownloadClient(), url)
        val magic = readMagic(channel)
        val image = ExtractImage(bootFile, onStdout)
        // Extract the KMI here while the payload is open. ZipFile closes the
        // channel it is built on, so probe on a separate channel.
        val probeChannel = DataSourceChannel(newDownloadClient(), url)
        probedKmi = try {
            if (magic == "CrAU") {
                ExtractImage.probePayload(
                    probeChannel,
                    withKmi = lkm is LkmSelection.KmiNone,
                    onProgress = onStdout,
                ).kmi
            } else {
                ExtractImage.probe(
                    probeChannel,
                    withKmi = lkm is LkmSelection.KmiNone,
                    onProgress = onStdout,
                ).kmi
            }
        } finally {
            probeChannel.close()
        }
        if (magic == "CrAU") {
            image.consumePayload(channel, partition)
        } else {
            image.consume(channel, partition)
        }
    } catch (e: Exception) {
        bootFile.delete()
        return FlashResult(-1, e.message ?: "Download failed", false)
    }

    // init_boot/vendor_boot carry no kernel, so their KMI comes from the
    // payload's boot probe and must be passed explicitly. A remote download
    // is unrelated to this device, so ksud must not use the local kernel.
    val autoKmi = if (lkm is LkmSelection.KmiNone) {
        (probedKmi ?: BootKernelVersion.parseKmiFromBoot(bootFile))?.also {
            onStdout("- Auto detected KMI: $it")
        }
    } else {
        null
    }
    if (autoKmi == null && lkm is LkmSelection.KmiNone) {
        bootFile.delete()
        return FlashResult(-1, "Failed to determine KMI from the package", false)
    }

    var cmd = "${getKsuDaemonPath()} boot-patch -b ${bootFile.absolutePath}"
    cmd += bootPatchFlags(allowShell, enableAdb, forceBackup, blockModules)

    val lkmFile = writeLkmFile(lkm)
    if (lkmFile != null) {
        cmd += " -m ${lkmFile.absolutePath}"
    } else if (lkm is LkmSelection.KmiString) {
        cmd += " --kmi ${lkm.value}"
    }
    if (autoKmi != null) cmd += " --kmi $autoKmi"
    cmd += " --partition $partition"
    // ksud defaults to cwd, which is read-only in the su session; use Downloads.
    val downloadsDir =
        Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
    cmd += " -o $downloadsDir"

    val stdoutCallback: CallbackList<String?> = object : CallbackList<String?>() {
        override fun onAddElement(s: String?) {
            onStdout(s ?: "")
        }
    }
    val stderrCallback: CallbackList<String?> = object : CallbackList<String?>() {
        override fun onAddElement(s: String?) {
            onStderr(s ?: "")
        }
    }

    val result = Shell.getShell().newJob().add(cmd).to(stdoutCallback, stderrCallback).exec()
    lkmFile?.delete()
    bootFile.delete()
    return FlashResult(result, false)
}

suspend fun probeRemoteBootPartitions(url: String): ProbeResult = withContext(Dispatchers.IO) {
    Log.d(TAG, "probe start: $url")
    val channel = DataSourceChannel(newDownloadClient(), url)
    Log.d(TAG, "probe connected, size=${channel.size()}")
    val magic = readMagic(channel)
    Log.d(TAG, "probe magic: $magic")
    // Only list the partitions here; the KMI is extracted later when the
    // payload is downloaded for patching.
    val result = if (magic == "CrAU") {
        ExtractImage.probePayload(channel, withKmi = false)
    } else {
        ExtractImage.probe(channel, withKmi = false)
    }
    Log.d(TAG, "probe partitions: ${result.partitions}")
    result
}

private fun newDownloadClient(): OkHttpClient {
    return OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .writeTimeout(20, TimeUnit.SECONDS)
        .build()
}

private fun readMagic(channel: DataSourceChannel): String {
    val buffer = ByteBuffer.allocate(4)
    channel.read(buffer)
    channel.position(0)
    return String(buffer.array(), StandardCharsets.ISO_8859_1)
}

fun reboot(reason: String = "") {
    if (reason == "soft_reboot") {
        execKsud("soft-reboot", true, true)
        return
    }
    val shell = getRootShell()
    if (reason == "recovery") {
        // KEYCODE_POWER = 26, hide incorrect "Factory data reset" message
        ShellUtils.fastCmd(shell, "/system/bin/input keyevent 26")
    }
    ShellUtils.fastCmd(shell, "/system/bin/svc power reboot $reason || /system/bin/reboot $reason")
}

fun rootAvailable(): Boolean {
    val shell = getRootShell()
    return shell.isRoot
}

suspend fun getCurrentKmi(): String = withContext(Dispatchers.IO) {
    val shell = getRootShell()
    val cmd = "boot-info current-kmi"
    ShellUtils.fastCmd(shell, "${getKsuDaemonPath()} $cmd")
}

suspend fun getSupportedKmis(): List<String> = withContext(Dispatchers.IO) {
    val shell = getRootShell()
    val cmd = "boot-info supported-kmis"
    val out = shell.newJob().add("${getKsuDaemonPath()} $cmd").to(ArrayList(), null).exec().out
    out.filter { it.isNotBlank() }.map { it.trim() }
}

suspend fun isAbDevice(): Boolean = withContext(Dispatchers.IO) {
    val shell = getRootShell()
    val cmd = "boot-info is-ab-device"
    ShellUtils.fastCmd(shell, "${getKsuDaemonPath()} $cmd").trim().toBoolean()
}

suspend fun getDefaultPartition(): String = withContext(Dispatchers.IO) {
    val shell = getRootShell()
    if (shell.isRoot) {
        val cmd = "boot-info default-partition"
        ShellUtils.fastCmd(shell, "${getKsuDaemonPath()} $cmd").trim()
    } else {
        if (!Os.uname().release.contains("android12-")) "init_boot" else "boot"
    }
}

suspend fun getSlotSuffix(ota: Boolean): String = withContext(Dispatchers.IO) {
    val shell = getRootShell()
    val cmd = if (ota) {
        "boot-info slot-suffix --ota"
    } else {
        "boot-info slot-suffix"
    }
    ShellUtils.fastCmd(shell, "${getKsuDaemonPath()} $cmd").trim()
}

suspend fun getAvailablePartitions(): List<String> = withContext(Dispatchers.IO) {
    val shell = getRootShell()
    val cmd = "boot-info available-partitions"
    val out = shell.newJob().add("${getKsuDaemonPath()} $cmd").to(ArrayList(), null).exec().out
    out.filter { it.isNotBlank() }.map { it.trim() }
}

// Known vendor anti-root kernel modules that `--block-module` is meant for.
private val KNOWN_BLOCKABLE_MODULES = listOf(
    "vr", "vklp", "oplus_secure_guard", "oplus_secure_guard_new", "mkp"
)

// Return the known anti-root modules currently loaded on this device (per
// /proc/modules), so the install screen can pre-fill only the relevant ones.
suspend fun getLoadedBlockableModules(): List<String> = withContext(Dispatchers.IO) {
    val shell = getRootShell()
    val out = shell.newJob().add("cat /proc/modules").to(ArrayList(), null).exec().out
    val loaded = out.mapNotNull { it.trim().substringBefore(' ').takeIf(String::isNotBlank) }.toHashSet()
    KNOWN_BLOCKABLE_MODULES.filter { it in loaded }
}

fun hasMagisk(): Boolean {
    val shell = getRootShell(true)
    val result = shell.newJob().add("which magisk").exec()
    Log.i(TAG, "has magisk: ${result.isSuccess}")
    return result.isSuccess
}

fun isSepolicyValid(rules: String?): Boolean {
    if (rules == null) {
        return true
    }
    val shell = getRootShell()
    val result =
        shell.newJob().add("${getKsuDaemonPath()} sepolicy check '$rules'").to(ArrayList(), null)
            .exec()
    return result.isSuccess
}

fun getSepolicy(pkg: String): String {
    val shell = getRootShell()
    val result =
        shell.newJob().add("${getKsuDaemonPath()} profile get-sepolicy $pkg").to(ArrayList(), null)
            .exec()
    Log.i(TAG, "code: ${result.code}, out: ${result.out}, err: ${result.err}")
    return result.out.joinToString("\n")
}

fun setSepolicy(pkg: String, rules: String): Boolean {
    val shell = getRootShell()
    val result = shell.newJob().add("${getKsuDaemonPath()} profile set-sepolicy $pkg '$rules'")
        .to(ArrayList(), null).exec()
    Log.i(TAG, "set sepolicy result: ${result.code}")
    return result.isSuccess
}

fun listAppProfileTemplates(): List<String> {
    val shell = getRootShell()
    return shell.newJob().add("${getKsuDaemonPath()} profile list-templates").to(ArrayList(), null)
        .exec().out
}

fun getAppProfileTemplate(id: String): String {
    val shell = getRootShell()
    return shell.newJob().add("${getKsuDaemonPath()} profile get-template '${id}'")
        .to(ArrayList(), null).exec().out.joinToString("\n")
}

fun setAppProfileTemplate(id: String, template: String): Boolean {
    val shell = getRootShell()
    val escapedTemplate = template.replace("'", "'\\''")
    val cmd = """${getKsuDaemonPath()} profile set-template "$id" '$escapedTemplate'"""
    return shell.newJob().add(cmd)
        .to(ArrayList(), null).exec().isSuccess
}

fun deleteAppProfileTemplate(id: String): Boolean {
    val shell = getRootShell()
    return shell.newJob().add("${getKsuDaemonPath()} profile delete-template '${id}'")
        .to(ArrayList(), null).exec().isSuccess
}

fun forceStopApp(packageName: String, userId: Int? = null) {
    val shell = getRootShell()
    val userArg = userId?.let { " --user $it" } ?: ""
    val result = shell.newJob().add("am force-stop$userArg $packageName").exec()
    Log.i(TAG, "force stop $packageName result: $result")
}

fun launchApp(packageName: String, userId: Int? = null) {
    val shell = getRootShell()
    val userArg = userId?.let { " --user $it" } ?: ""
    val result =
        shell.newJob()
            .add("cmd package resolve-activity --brief$userArg $packageName | tail -n 1 | xargs cmd activity start-activity$userArg -n")
            .exec()
    Log.i(TAG, "launch $packageName result: $result")
}

fun restartApp(packageName: String, userId: Int? = null) {
    forceStopApp(packageName, userId)
    launchApp(packageName, userId)
}

/**
 * Whether this app should pretend it is not the manager.
 *
 * Two switches answer this, and either one is enough:
 *
 *   - the kernel's stealth mode (`ksud stealth get`), which is the thorough one: it stops
 *     GET_INFO from reporting the manager and late-load flags, so nothing outside can tell
 *     this device is rooted;
 *   - the file `/data/adb/ksu/hide_manager` owned by the web UI, which only fools this app —
 *     it hides the screens but leaves every other process able to detect KernelSU.
 *
 * Read through su because /data/adb is root-only. Every screen that asks whether this app is
 * the manager has to consult this, or the disguise only covers part of the app.
 */
fun isManagerHidden(): Boolean = isStealthEnabled() || runCatching {
    com.topjohnwu.superuser.ShellUtils.fastCmd(
        getRootShell(),
        "test -f /data/adb/ksu/hide_manager && echo 1",
    ).contains("1")
}.getOrDefault(false)

/**
 * Kernel stealth mode, read through ksud.
 *
 * Returns false when the kernel predates the command as well as when the switch is off —
 * `ksud stealth get` exits non-zero in the first case and prints nothing usable, which reads
 * the same as "not hidden". That is the safe direction: an old kernel simply has no disguise.
 */
fun isStealthEnabled(): Boolean = runCatching {
    ShellUtils.fastCmd(getRootShell(), "${getKsuDaemonPath()} stealth get").trim() == "1"
}.getOrDefault(false)

/**
 * Turn kernel stealth mode on or off.
 *
 * Returns whether the kernel reports the state the caller asked for, so a failure on an old
 * kernel is visible rather than silent.
 */
fun setStealthEnabled(enabled: Boolean): Boolean = runCatching {
    val want = if (enabled) "1" else "0"
    ShellUtils.fastCmd(getRootShell(), "${getKsuDaemonPath()} stealth set $want").trim() == want
}.getOrDefault(false)

/**
 * Whether the running kernel understands the stealth command at all.
 *
 * Used to hide the switch in settings rather than offer one that does nothing.
 */
fun isStealthSupported(): Boolean = runCatching {
    ShellUtils.fastCmd(getRootShell(), "${getKsuDaemonPath()} stealth get").trim() in setOf("0", "1")
}.getOrDefault(false)

/**
 * Drops the disguise, whatever state the app is in.
 *
 * The home screen's not-installed card calls this after five taps in a row: with the disguise on
 * there is no settings page to switch it off from, and the calculator that opens the web UI may
 * already be uninstalled — this is the way back that does not depend on either.
 *
 * There are two disguises and either one alone hides the app, so both are cleared. This used to
 * only delete /data/adb/ksu/hide_manager, which left the kernel switch on: GET_INFO kept hiding
 * the manager and the app stayed disguised however many times the card was tapped.
 */
fun clearManagerHidden() {
    runCatching { ShellUtils.fastCmd(getRootShell(), "rm -f /data/adb/ksu/hide_manager") }
    runCatching { setStealthEnabled(false) }
}

/**
 * Whether [typed] is the code the calculator was given.
 *
 * Same file the calculator watches, so there is one secret to remember rather than two. The
 * fallback matches the code the calculator is built with, for the case where the file was never
 * written — without it, a device that never set a code could not be un-disguised at all.
 */
fun matchesTriggerCode(typed: String): Boolean {
    val clean = typed.filter { it.isDigit() }
    if (clean.isEmpty()) {
        return false
    }
    return clean == triggerCode()
}

/**
 * The code the recovery prompt expects.
 *
 * Read from the file the calculator watches, so there is one secret rather than two. The
 * fallback is what the calculator is built with: a device whose file was never written would
 * otherwise have no way out at all.
 */
fun triggerCode(): String {
    val stored = runCatching {
        ShellUtils.fastCmd(getRootShell(), "cat /data/adb/ksu/webui.trigger 2>/dev/null")
    }.getOrDefault("").filter { it.isDigit() }
    return stored.ifEmpty { "1234" }
}

/**
 * The name of the module providing Zygisk, or null when nothing is providing it.
 *
 * There is no Zygisk in this project: the hooks live in a module, and which module that is
 * varies. Two are known by id, the same two every KernelSU fork looks for.
 *
 * A module that is disabled or queued for removal is skipped rather than reported — it is not
 * providing anything — and the prop is read for its human name, so the row shows what the module
 * calls itself instead of an id.
 */
fun zygiskImplementation(): String? {
    val ids = listOf("zygisksu", "rezygisk")
    val shell = getRootShell()
    for (id in ids) {
        val dir = "/data/adb/modules/$id"
        if (ShellUtils.fastCmd(shell, "test -f $dir/disable -o -f $dir/remove && echo y").trim() == "y") {
            continue
        }
        val name = ShellUtils.fastCmd(
            shell,
            "sed -n 's/^name=//p' $dir/module.prop 2>/dev/null | head -n 1",
        ).trim()
        if (name.isNotEmpty()) {
            return name
        }
    }
    return null
}

/**
 * The SuSFS version the kernel reports, or null when nothing answers.
 *
 * Read through `ksud susfs version`, which prints the load state on the first
 * line and the version on the second. Unlike the module rows above, this one
 * lives in the kernel: it is the same call whether SuSFS was built in or came
 * in as the standalone LKM after boot (jailbreak mode included).
 */
fun susfsVersion(): String? {
    val shell = getRootShell()
    val out = shell.newJob()
        .add("${getKsuDaemonPath()} susfs version")
        .to(mutableListOf<String>(), null)
        .exec().out
    if (out.firstOrNull()?.trim() != "1") {
        return null
    }
    val version = out.getOrNull(1)?.trim().orEmpty()
    return version.ifEmpty { null }
}

/**
 * The name of the installed metamodule, or null when there is none.
 *
 * The metamodule is reachable through its own symlink, which is what ksud maintains; reading the
 * link's target rather than scanning every module keeps this to one command.
 */
fun metaModuleImplementation(): String? {
    val name = runCatching {
        ShellUtils.fastCmd(
            getRootShell(),
            "sed -n 's/^name=//p' /data/adb/metamodule/module.prop 2>/dev/null | head -n 1",
        ).trim()
    }.getOrDefault("")
    return name.ifEmpty { null }
}

/**
 * Reload every module lifecycle script without rebooting.
 *
 * This runs the three boot stages ksud exposes to userspace, in the order the boot would:
 * `post-fs-data`, `services`, then `boot-completed`. It is the same sequence a soft reboot
 * performs, minus the reboot, so a module whose scripts were just edited can be picked up in
 * place. Returns the number of stages that completed without error, so the caller can tell a
 * clean run from a partial one.
 */
fun reloadModules(): Int = runCatching {
    val ksud = getKsuDaemonPath()
    val shell = getRootShell(globalMnt = true)
    var ok = 0
    for (stage in listOf("post-fs-data", "services", "boot-completed")) {
        val result = shell.newJob().add("$ksud $stage").exec()
        if (result.code == 0) ok++
    }
    ok
}.getOrDefault(0)

/**
 * Whether the kernel knows the `soft-reboot` command.
 *
 * `ksud --help` lists it on every build, so the command's presence is not evidence the kernel
 * will act on it. The manager only offers the button when the running kernel passes the uapi
 * version check the command itself performs.
 */
fun isSoftRebootSupported(): Boolean = runCatching {
    val out = ShellUtils.fastCmd(getRootShell(), "${getKsuDaemonPath()} soft-reboot --help")
    out.isNotEmpty() && !out.contains("unrecognized", ignoreCase = true)
}.getOrDefault(false)
/**
 * The name of the module providing an Xposed-compatible framework, or null when none is.
 *
 * As with Zygisk, the framework lives in a module rather than in the kernel, and which module that
 * is varies by how it was installed. The ids below cover the ones seen in the wild: LSPosed under
 * each of its packaging names, and the older Xposed installers. They are checked in order so the
 * most common answer comes back first.
 *
 * The daemon directory is checked as well, because LSPosed writes `/data/adb/lspd` whatever id its
 * module happens to use, and some builds ship under names not listed here. A disabled or
 * queued-for-removal module is skipped rather than reported, since it is not providing anything,
 * and the prop is read for its human name so the row shows what the module calls itself.
 */
fun xposedImplementation(): String? {
    val shell = getRootShell()
    val ids = listOf(
        "lsposed",
        "zygisk_lsposed",
        "riru-lsposed",
        "lsposed_mod",
        "xposed",
        "taichi",
    )
    for (id in ids) {
        val dir = "/data/adb/modules/$id"
        if (ShellUtils.fastCmd(shell, "test -f $dir/disable -o -f $dir/remove && echo y").trim() == "y") {
            continue
        }
        val name = ShellUtils.fastCmd(
            shell,
            "sed -n 's/^name=//p' $dir/module.prop 2>/dev/null | head -n 1",
        ).trim()
        if (name.isNotEmpty()) {
            return name
        }
    }
    // LSPosed keeps its daemon here regardless of the module id, so a build that ships under a
    // name not listed above is still found through it.
    val fromDaemon = runCatching {
        ShellUtils.fastCmd(
            shell,
            "sed -n 's/^name=//p' /data/adb/lspd/module.prop 2>/dev/null | head -n 1",
        ).trim()
    }.getOrDefault("")
    if (fromDaemon.isNotEmpty()) {
        return fromDaemon
    }
    return if (ShellUtils.fastCmd(shell, "test -d /data/adb/lspd && echo y").trim() == "y") {
        "LSPosed"
    } else {
        null
    }
}
