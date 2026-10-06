package me.weishu.kernelsu.susfs

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * State holder for the SuSFS screen.
 *
 * All kernel work happens in [SusfsCommands]; this only sequences it, feeds
 * the switches from last-known values and keeps the log panel's last line.
 */
class SusfsViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(
        SusfsUiState(
            logEnabled = SusfsRepository.logEnabled,
            avcLogSpoofing = SusfsRepository.avcLogSpoofing,
            hideSusMntsForNonSuProcs = SusfsRepository.hideSusMntsForNonSuProcs,
            unameRelease = SusfsRepository.unameRelease,
            unameVersion = SusfsRepository.unameVersion,
        ),
    )
    val uiState: StateFlow<SusfsUiState> = _uiState.asStateFlow()

    /** Read the kernel's state and the module's procfs nodes in one pass. */
    fun refresh() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            val status = SusfsCommands.loadStatus()
            val proc = if (status.loaded) SusfsCommands.readProc() else SusfsProcSnapshot()
            _uiState.update {
                it.copy(
                    isLoading = false,
                    status = status,
                    proc = proc,
                    // The panel lists every known feature whether or not the kernel
                    // reported it; one that is absent simply reads as disabled.
                    features = SusfsFeatureCatalog.resolve(status.features),
                )
            }
        }
    }

    /**
     * Run one mutating command and fold its output into the log panel.
     *
     * [block] is the command; [after] runs only on a clean exit, which is how
     * the callers refresh a value that the command just changed.
     */
    private fun exec(
        label: String,
        block: suspend () -> SusfsCommands.Result,
        after: (suspend () -> Unit)? = null,
    ) {
        viewModelScope.launch {
            val result = block()
            _uiState.update {
                it.copy(
                    error = if (result.ok) null else result.output.ifEmpty { label },
                    lastOutput = buildString {
                        append(label)
                        append(" → ")
                        append(if (result.ok) "ok" else "failed")
                        if (result.output.isNotEmpty()) {
                            append('\n')
                            append(result.output)
                        }
                    },
                )
            }
            if (result.ok) after?.invoke()
        }
    }

    fun addSusPath(path: String) {
        val normalized = SusfsCommands.normalizePath(path)
        if (normalized == null) {
            _uiState.update { it.copy(error = "路径无效：必须以 / 开头", lastOutput = "add-sus-path $path → invalid path") }
            return
        }
        exec("add-sus-path $normalized", { SusfsCommands.addSusPath(normalized) }) {
            SusfsRepository.addSusPath(normalized)
            refresh()
        }
    }

    fun addSusPathLoop(path: String) {
        val normalized = SusfsCommands.normalizePath(path)
        if (normalized == null) {
            _uiState.update { it.copy(error = "路径无效：必须以 / 开头", lastOutput = "add-sus-path-loop $path → invalid path") }
            return
        }
        exec("add-sus-path-loop $normalized", { SusfsCommands.addSusPathLoop(normalized) }) {
            SusfsRepository.addSusPathLoop(normalized)
            refresh()
        }
    }

    fun addSusMap(path: String) {
        val normalized = SusfsCommands.normalizePath(path)
        if (normalized == null) {
            _uiState.update { it.copy(error = "路径无效：必须以 / 开头", lastOutput = "add-sus-map $path → invalid path") }
            return
        }
        exec("add-sus-map $normalized", { SusfsCommands.addSusMap(normalized) }) {
            SusfsRepository.addSusMap(normalized)
            refresh()
        }
    }

    fun addSusKstat(path: String) {
        val normalized = SusfsCommands.normalizePath(path) ?: path
        exec("add-sus-kstat $normalized", { SusfsCommands.addSusKstat(normalized) }) { refresh() }
    }

    fun updateSusKstat(path: String) {
        val normalized = SusfsCommands.normalizePath(path) ?: path
        exec("update-sus-kstat $normalized", { SusfsCommands.updateSusKstat(normalized) }) {
            SusfsRepository.addSusKstatPath(normalized)
            refresh()
        }
    }

    fun updateSusKstatFullClone(path: String) {
        val normalized = SusfsCommands.normalizePath(path) ?: path
        exec("update-sus-kstat-full-clone $normalized", { SusfsCommands.updateSusKstatFullClone(normalized) }) {
            SusfsRepository.addSusKstatPath(normalized)
            refresh()
        }
    }

    /**
     * Spoof a path's stat with caller-supplied values.
     *
     * All fields are forwarded verbatim; see [SusfsCommands.addSusKstatStatically].
     */
    fun addSusKstatStatically(
        path: String,
        ino: Long,
        dev: Long,
        nlink: Long,
        size: Long,
        atimeSec: Long,
        atimeNsec: Long,
        mtimeSec: Long,
        mtimeNsec: Long,
        ctimeSec: Long,
        ctimeNsec: Long,
        blocks: Long,
        blksize: Long,
    ) {
        val normalized = SusfsCommands.normalizePath(path) ?: path
        exec(
            "add-sus-kstat-statically $normalized",
            {
                SusfsCommands.addSusKstatStatically(
                    path = normalized,
                    ino = ino,
                    dev = dev,
                    nlink = nlink,
                    size = size,
                    atimeSec = atimeSec,
                    atimeNsec = atimeNsec,
                    mtimeSec = mtimeSec,
                    mtimeNsec = mtimeNsec,
                    ctimeSec = ctimeSec,
                    ctimeNsec = ctimeNsec,
                    blocks = blocks,
                    blksize = blksize,
                )
            },
        ) { refresh() }
    }

    fun addOpenRedirect(target: String, redirected: String, uidScheme: Int = 0) {
        val normTarget = SusfsCommands.normalizePath(target)
        val normRedirected = SusfsCommands.normalizePath(redirected)
        if (normTarget == null || normRedirected == null) {
            _uiState.update { it.copy(error = "路径无效：必须以 / 开头", lastOutput = "add-open-redirect → invalid path") }
            return
        }
        exec(
            "add-open-redirect $normTarget (uid_scheme=$uidScheme)",
            { SusfsCommands.addOpenRedirect(normTarget, normRedirected, uidScheme) },
        ) {
            SusfsRepository.addSusRedirect(normTarget, normRedirected, uidScheme)
            refresh()
        }
    }

    fun setCmdline(path: String) =
        exec("set-cmdline $path", { SusfsCommands.setCmdline(path) }) { refresh() }

    fun setUname(release: String, version: String) =
        exec("set-uname $release", { SusfsCommands.setUname(release, version) }) {
            SusfsRepository.unameRelease = release
            SusfsRepository.unameVersion = version
            _uiState.update { it.copy(unameRelease = release, unameVersion = version) }
            refresh()
        }

    fun setLogEnabled(enabled: Boolean) =
        exec("enable-log $enabled", { SusfsCommands.setLogEnabled(enabled) }) {
            SusfsRepository.logEnabled = enabled
            _uiState.update { it.copy(logEnabled = enabled) }
        }

    fun setAvcLogSpoofing(enabled: Boolean) =
        exec("enable-avc-log-spoofing $enabled", { SusfsCommands.setAvcLogSpoofing(enabled) }) {
            SusfsRepository.avcLogSpoofing = enabled
            _uiState.update { it.copy(avcLogSpoofing = enabled) }
        }

    fun setHideSusMnts(enabled: Boolean) =
        exec("hide-sus-mnts-for-non-su-procs $enabled", { SusfsCommands.setHideSusMntsForNonSuProcs(enabled) }) {
            SusfsRepository.hideSusMntsForNonSuProcs = enabled
            _uiState.update { it.copy(hideSusMntsForNonSuProcs = enabled) }
        }
}
