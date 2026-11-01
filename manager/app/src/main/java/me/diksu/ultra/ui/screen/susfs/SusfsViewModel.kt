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

/** 页面要显示的全部东西。 */
data class SusfsUiState(
    val loading: Boolean = true,
    val busy: Boolean = false,
    val status: SusfsStatus = SusfsStatus(),
    val config: SusfsConfig = SusfsConfig(),
    val proc: SusfsProcSnapshot = SusfsProcSnapshot(),
    /** 最近一次应用的结果；null 表示还没应用过。 */
    val report: SusfsApplyReport? = null,
    /** 一行提示，成功或失败都用它。 */
    val message: String? = null,
    /** 待确认的导入配置；非 null 时界面弹预览对话框。 */
    val pendingImport: SusfsConfig? = null,
    val pendingDiff: SusfsConfigDiff? = null,
    /** 待确认的「恢复默认配置」。 */
    val pendingReset: Boolean = false,
)

/**
 * SuSFS 页面的状态与意图。
 *
 * 所有会改内核的动作都走同一条路：改配置 → 存盘 → 下发 → 用报告刷新界面。没有"点一下就
 * 直接调命令"的旁路，否则配置和内核会各说各话。
 */
class SusfsViewModel : ViewModel() {

    private val repository = SusfsRepository()
    private val _state = MutableStateFlow(SusfsUiState())
    val state = _state.asStateFlow()

    init {
        refresh()
    }

    /** 重新读状态、配置与 procfs。 */
    fun refresh() {
        viewModelScope.launch {
            _state.update { it.copy(loading = true) }
            val (status, config, proc) = withContext(Dispatchers.IO) {
                Triple(
                    runCatching { SusfsCommands.loadStatus() }.getOrDefault(SusfsStatus()),
                    runCatching { repository.load() }.getOrDefault(SusfsConfig()),
                    runCatching { SusfsCommands.readProc() }.getOrDefault(SusfsProcSnapshot()),
                )
            }
            _state.update {
                it.copy(loading = false, status = status, config = config, proc = proc)
            }
        }
    }

    /** 改开关：先落盘再下发，报告回填。 */
    fun setSwitch(key: String, value: Boolean) {
        val current = _state.value
        val next = current.config.copy(enabled = current.config.enabled + (key to value))
        commit(next, current.config)
    }

    fun addRule(rule: SusfsRule) {
        val current = _state.value
        if (current.config.rules.any { it.key == rule.key }) {
            _state.update { it.copy(message = "这条规则已经在列表里：${rule.displayForMessage()}") }
            return
        }
        commit(current.config.copy(rules = current.config.rules + rule), current.config)
    }

    fun removeRule(rule: SusfsRule) {
        val current = _state.value
        commit(current.config.copy(rules = current.config.rules - rule), current.config)
    }

    /**
     * 导入第一步：解析并算出差异，先给用户看，不落盘也不下发。
     *
     * 直接覆盖是危险的 —— 导入一份别人的配置会把你已有的规则全换掉，所以要有个能后悔的
     * 中间态（对齐 FinaSU 的 preview）。
     */
    fun importConfig(text: String) {
        val parsed = try {
            repository.preview(text)
        } catch (e: SusfsConfigException) {
            _state.update { it.copy(message = "导入失败：${e.message}") }
            return
        }
        val diff = parsed.diffFrom(_state.value.config)
        _state.update { it.copy(pendingImport = parsed, pendingDiff = diff, message = null) }
    }

    /** 导入第二步：用户确认后按目标配置下发。 */
    fun confirmImport() {
        val target = _state.value.pendingImport ?: return
        _state.update { it.copy(pendingImport = null, pendingDiff = null) }
        commit(target, _state.value.config)
    }

    fun cancelImport() {
        _state.update { it.copy(pendingImport = null, pendingDiff = null) }
    }

    /**
     * 恢复：把 ksud 里记录的上次成功应用读回来，走同一条导入预览。
     *
     * 这是"改坏了怎么回去"的入口 —— 配置文件可以随便试，内核里有上一份能回来。
     */
    fun restoreFromKernel() {
        if (_state.value.busy) return
        viewModelScope.launch {
            _state.update { it.copy(busy = true, message = null) }
            val result = withContext(Dispatchers.IO) { SusfsCommands.exportConfig() }
            _state.update { it.copy(busy = false) }
            if (!result.ok) {
                reportMessage("读取内核配置失败：${result.output.take(120)}")
                return@launch
            }
            importConfig(result.output)
        }
    }

    /**
     * 恢复默认配置：先请用户确认。
     *
     * 默认 = 没有任何规则、三个开关全关 —— 也就是刚装好时的状态。下发一份空配置同时会把
     * ksud 里记录的那份状态覆盖掉，所以「恢复上次应用」也回不到重置前了，确认框里写明。
     */
    fun requestResetDefaults() {
        if (_state.value.busy) return
        val current = _state.value.config
        if (current.rules.isEmpty() && current.enabled.values.none { it }) {
            reportMessage("当前已经是默认配置")
            return
        }
        _state.update { it.copy(pendingReset = true) }
    }

    fun confirmResetDefaults() {
        val current = _state.value.config
        _state.update { it.copy(pendingReset = false) }
        commit(SusfsConfig(), current)
    }

    fun cancelResetDefaults() {
        _state.update { it.copy(pendingReset = false) }
    }

    /** 导出当前配置（JSON 文本），由界面层决定写到哪。 */
    fun exportConfig(): String = repository.export()

    /** 界面层自己产生的提示（导入/导出结果等）。 */
    fun reportMessage(text: String) {
        _state.update { it.copy(message = text) }
    }

    private fun commit(next: SusfsConfig, previous: SusfsConfig) {
        if (_state.value.busy) return
        viewModelScope.launch {
            _state.update { it.copy(busy = true, message = null) }
            repository.save(next)
            val report = withContext(Dispatchers.IO) { repository.apply(next, previous) }
            // 应用后顺手刷新一次状态：能力列表、版本这些会随开关变化，不刷新会显示旧值。
            val status = withContext(Dispatchers.IO) { SusfsCommands.loadStatus() }
            _state.update {
                it.copy(
                    busy = false,
                    status = status,
                    config = next,
                    report = report,
                    message = report.summary(),
                )
            }
        }
    }
}

/** 消息里用的简短描述，避免把整段 JSON 塞进去。 */
private fun SusfsRule.displayForMessage(): String = when (this) {
    is SusfsRule.HiddenPath -> path
    is SusfsRule.HiddenLoop -> path
    is SusfsRule.SusMap -> path
    is SusfsRule.Kstat -> path
    is SusfsRule.Redirect -> "$target → $redirected"
    is SusfsRule.Uname -> release
    is SusfsRule.Cmdline -> sourcePath
}
