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
                it.copy(isLoading = false, status = status, proc = proc)
            }
        }
    }

    /**
     * Run one mutating command and fold its output into the log panel.
     *
     * [after] runs only on a clean exit, which is how the callers refresh a
     * value that the command just changed.
     */
    private fun exec(label: String, after: (suspend () -> Unit)? = null, block: suspend () -> SusfsCommands.Result) {
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

    fun addSusPath(path: String) = exec("add-sus-path $path") { refresh() }
    fun addSusPathLoop(path: String) = exec("add-sus-path-loop $path") { refresh() }
    fun addSusMap(path: String) = exec("add-sus-map $path") { refresh() }
    fun addSusKstat(path: String) = exec("add-sus-kstat $path") { refresh() }
    fun updateSusKstat(path: String) = exec("update-sus-kstat $path") { refresh() }
    fun updateSusKstatFullClone(path: String) = exec("update-sus-kstat-full-clone $path") { refresh() }
    fun addOpenRedirect(target: String, redirected: String) =
        exec("add-open-redirect $target") { refresh() }
    fun setCmdline(path: String) = exec("set-cmdline $path") { refresh() }

    fun setUname(release: String, version: String) = exec("set-uname $release") {
        SusfsRepository.unameRelease = release
        SusfsRepository.unameVersion = version
        _uiState.update { it.copy(unameRelease = release, unameVersion = version) }
        refresh()
    }

    fun setLogEnabled(enabled: Boolean) = exec("enable-log $enabled") {
        SusfsRepository.logEnabled = enabled
        _uiState.update { it.copy(logEnabled = enabled) }
    }

    fun setAvcLogSpoofing(enabled: Boolean) = exec("enable-avc-log-spoofing $enabled") {
        SusfsRepository.avcLogSpoofing = enabled
        _uiState.update { it.copy(avcLogSpoofing = enabled) }
    }

    fun setHideSusMnts(enabled: Boolean) = exec("hide-sus-mnts-for-non-su-procs $enabled") {
        SusfsRepository.hideSusMntsForNonSuProcs = enabled
        _uiState.update { it.copy(hideSusMntsForNonSuProcs = enabled) }
    }
}