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
import me.diksu.ultra.data.susfs.SusfsConfigException
import me.diksu.ultra.data.susfs.SusfsProcSnapshot
import me.diksu.ultra.data.susfs.SusfsRepository
import me.diksu.ultra.data.susfs.SusfsRule
import me.diksu.ultra.data.susfs.SusfsStatus

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
                    SusfsCommands.loadStatus(),
                    repository.load(),
                    SusfsCommands.readProc(),
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

    /** 导入：解析 → 存盘 → 按新配置下发。解析失败只提示，不改现状。 */
    fun importConfig(text: String) {
        val parsed = try {
            repository.preview(text)
        } catch (e: SusfsConfigException) {
            _state.update { it.copy(message = "导入失败：${e.message}") }
            return
        }
        commit(parsed, _state.value.config)
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
            _state.update {
                it.copy(
                    busy = false,
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
