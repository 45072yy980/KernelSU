package me.diksu.ultra.ui.screen.susfs

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.diksu.ultra.data.susfs.SusfsApplyReport
import me.diksu.ultra.data.susfs.SusfsCommands
import me.diksu.ultra.data.susfs.SusfsConfig
import me.diksu.ultra.data.susfs.SusfsConfigDiff
import me.diksu.ultra.data.susfs.SusfsConfigException
import me.diksu.ultra.data.susfs.SusfsProcSnapshot
import me.diksu.ultra.data.susfs.SusfsRepository
import me.diksu.ultra.data.susfs.SusfsRule
import me.diksu.ultra.data.susfs.SusfsStatus
import me.diksu.ultra.data.susfs.diffFrom

/**
 * 页面要显示的全部东西。
 *
 * [applied] 与 [draft] 是两份：编辑只动草稿，点「应用」才下发。这是照 FinaSU 的模型做的 ——
 * 一次改五个开关也只往内核写一次，而且随时能整体丢弃。
 */
data class SusfsUiState(
    val loading: Boolean = true,
    val busy: Boolean = false,
    val status: SusfsStatus = SusfsStatus(),
    /** 已经下发到内核的配置。 */
    val applied: SusfsConfig = SusfsConfig(),
    /** 正在编辑的草稿。 */
    val draft: SusfsConfig = SusfsConfig(),
    val proc: SusfsProcSnapshot = SusfsProcSnapshot(),
    val report: SusfsApplyReport? = null,
    val message: String? = null,
    val pendingImport: SusfsConfig? = null,
    val pendingDiff: SusfsConfigDiff? = null,
    val pendingReset: Boolean = false,
) {
    /** 有没有未应用的改动。 */
    val dirty: Boolean
        get() = draft.rules != applied.rules || draft.enabled != applied.enabled
}

/**
 * SuSFS 页面的状态与意图。
 *
 * 两条写入路径：
 * - 草稿路径：普通编辑只改 [SusfsUiState.draft]，用户点应用才 [applyDraft]
 * - 整份替换：导入 / 恢复默认走预览确认，结果同样落到草稿，再由用户应用
 */
class SusfsViewModel : ViewModel() {

    private val repository = SusfsRepository()
    private val _state = MutableStateFlow(SusfsUiState())
    val state = _state.asStateFlow()

    init {
        refresh()
    }

    /** 重新读状态与配置；草稿有未应用改动时保留，避免误丢用户的编辑。 */
    fun refresh() {
        viewModelScope.launch {
            _state.update { it.copy(loading = true) }
            val status = withContext(Dispatchers.IO) {
                runCatching { SusfsCommands.loadStatus() }.getOrDefault(SusfsStatus())
            }
            val config = withContext(Dispatchers.IO) {
                runCatching { repository.load() }.getOrDefault(SusfsConfig())
            }
            val proc = withContext(Dispatchers.IO) {
                runCatching { SusfsCommands.readProc() }.getOrDefault(SusfsProcSnapshot())
            }
            _state.update {
                it.copy(
                    loading = false,
                    status = status,
                    applied = config,
                    draft = if (it.dirty) it.draft else config,
                    proc = proc,
                )
            }
        }
    }

    // ── 草稿编辑 ──────────────────────────────────────────────────────────────

    fun setSwitch(key: String, value: Boolean) {
        _state.update { state ->
            state.copy(draft = state.draft.copy(enabled = state.draft.enabled + (key to value)))
        }
    }

    fun addRule(rule: SusfsRule) {
        _state.update { state ->
            if (state.draft.rules.any { it.key == rule.key }) {
                state.copy(message = "这条规则已经在列表里")
            } else {
                state.copy(
                    draft = state.draft.copy(rules = state.draft.rules + rule),
                    message = null,
                )
            }
        }
    }

    fun removeRule(rule: SusfsRule) {
        _state.update { state ->
            state.copy(draft = state.draft.copy(rules = state.draft.rules - rule))
        }
    }

    /** 丢弃未应用的改动，回到内核里那份。 */
    fun discardDraft() {
        _state.update { it.copy(draft = it.applied, message = null) }
    }

    /** 应用草稿：先 import 存，再 apply 下发。 */
    fun applyDraft() {
        val current = _state.value
        if (current.busy) return
        if (!current.dirty) {
            _state.update { it.copy(message = "没有待应用的改动") }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(busy = true, message = null) }
            val target = current.draft
            repository.save(target)
            val report = withContext(Dispatchers.IO) { repository.apply(target, current.applied) }
            val status = withContext(Dispatchers.IO) {
                runCatching { SusfsCommands.loadStatus() }.getOrDefault(current.status)
            }
            _state.update {
                it.copy(
                    busy = false,
                    status = status,
                    applied = target,
                    report = report,
                    message = report.summary(),
                )
            }
        }
    }

    // ── 整份替换：导入 / 恢复 ─────────────────────────────────────────────────

    /** 导入第一步：解析并算差异，弹预览，不落盘。 */
    fun importConfig(text: String) {
        val parsed = try {
            repository.preview(text)
        } catch (e: SusfsConfigException) {
            _state.update { it.copy(message = "导入失败：${e.message}") }
            return
        }
        val diff = parsed.diffFrom(_state.value.draft)
        _state.update { it.copy(pendingImport = parsed, pendingDiff = diff, message = null) }
    }

    fun confirmImport() {
        val target = _state.value.pendingImport ?: return
        _state.update {
            it.copy(pendingImport = null, pendingDiff = null, draft = target, message = null)
        }
    }

    fun cancelImport() {
        _state.update { it.copy(pendingImport = null, pendingDiff = null) }
    }

    /** 恢复：把 ksud 里记录的配置读回来，走同一条预览。 */
    fun restoreFromKernel() {
        if (_state.value.busy) return
        viewModelScope.launch {
            _state.update { it.copy(busy = true, message = null) }
            val result = withContext(Dispatchers.IO) { SusfsCommands.exportJson() }
            _state.update { it.copy(busy = false) }
            if (!result.ok) {
                reportMessage("读取内核配置失败：${result.output.take(120)}")
                return@launch
            }
            importConfig(result.output)
        }
    }

    /** 恢复默认：清空草稿里的规则与开关，用户点应用才生效。 */
    fun requestResetDefaults() {
        val current = _state.value.draft
        if (current.rules.isEmpty() && current.enabled.values.none { it }) {
            reportMessage("当前已经是默认配置")
            return
        }
        _state.update { it.copy(pendingReset = true) }
    }

    fun confirmResetDefaults() {
        _state.update { it.copy(pendingReset = false, draft = SusfsConfig(), message = null) }
    }

    fun cancelResetDefaults() {
        _state.update { it.copy(pendingReset = false) }
    }

    fun exportConfig(): String = _state.value.draft.toPrettyJson()

    /** 界面层自己产生的提示。 */
    fun reportMessage(text: String) {
        _state.update { it.copy(message = text) }
    }
}
