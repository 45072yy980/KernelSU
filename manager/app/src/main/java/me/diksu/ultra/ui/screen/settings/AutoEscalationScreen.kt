package me.diksu.ultra.ui.screen.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.diksu.ultra.jailbreak.AutoEscalation
import me.diksu.ultra.ui.LocalUiMode
import me.diksu.ultra.ui.isMiuixFamily
import me.diksu.ultra.ui.navigation3.LocalNavigator
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import top.yukonga.miuix.kmp.basic.Card as MiuixCard
import top.yukonga.miuix.kmp.basic.Icon as MiuixIcon
import top.yukonga.miuix.kmp.basic.IconButton as MiuixIconButton
import top.yukonga.miuix.kmp.basic.Scaffold as MiuixScaffold
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Switch as MiuixSwitch
import top.yukonga.miuix.kmp.basic.Text as MiuixText
import top.yukonga.miuix.kmp.basic.TextButton as MiuixTextButton
import top.yukonga.miuix.kmp.basic.TopAppBar as MiuixTopAppBar
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme

/**
 * 自动提权并越狱。
 *
 * 3.x 只有设置页里一个布尔开关，背后是空的 magica 服务 —— 拨了不会发生任何
 * 事。这里把"开机自动提权"做成可编排的：选方式、定顺序、看结果。
 *
 * 交互上刻意把顺序做得可见：每个方式一行，上下箭头调整优先级，开关控制是否
 * 参与本次编排。第一个成功即停，失败自动尝试下一个 —— 顺序就是回退链。
 */
private val AccentOk = Color(0xFF2FA84F)
private val AccentWarn = Color(0xFFE53935)

@Composable
fun AutoEscalationScreen() {
    val navigator = LocalNavigator.current
    val onBack = { navigator.pop() }
    if (LocalUiMode.current.isMiuixFamily) {
        AutoEscalationMiuix(onBack)
    } else {
        AutoEscalationMaterial(onBack)
    }
}

// ── 共享状态 ────────────────────────────────────────────────────────────────

private class EscalationState(
    val enabled: Boolean,
    val order: List<String>,
    val last: AutoEscalation.LastAttempt?,
    val running: Boolean,
    val log: List<String>,
)

@Composable
private fun rememberEscalationState(): Pair<EscalationState, EscalationActions> {
    var enabled by remember { mutableStateOf(AutoEscalation.enabled) }
    var order by remember { mutableStateOf(AutoEscalation.order) }
    var last by remember { mutableStateOf(AutoEscalation.lastAttempt()) }
    var running by remember { mutableStateOf(false) }
    var log by remember { mutableStateOf(listOf<String>()) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    val actions = EscalationActions(
        onEnabledChange = { value ->
            enabled = value
            AutoEscalation.enabled = value
        },
        onToggleMethod = { method ->
            val next = if (method in order) order - method else order + method
            order = next
            AutoEscalation.order = next
        },
        onMove = { method, delta ->
            val index = order.indexOf(method)
            val target = index + delta
            if (index >= 0 && target in order.indices) {
                val mutable = order.toMutableList()
                val tmp = mutable[index]
                mutable[index] = mutable[target]
                mutable[target] = tmp
                order = mutable.toList()
                AutoEscalation.order = order
            }
        },
        onRun = {
            if (!running) {
                scope.launch {
                    running = true
                    // 手动重试意味着用户认为链路应该能通：把开机自动尝试的失败计数清零。
                    AutoEscalation.resetFailStreak()
                    log = listOf("准备开始…")
                    val outcome = withContext(Dispatchers.IO) {
                        AutoEscalation.run(context) { line ->
                            log = log + line
                        }
                    }
                    log = log + (if (outcome.success) "完成：${outcome.message}" else "失败：${outcome.message}")
                    last = AutoEscalation.lastAttempt()
                    running = false
                }
            }
        },
    )

    val state = EscalationState(
        enabled = enabled,
        order = order,
        last = last,
        running = running,
        log = log,
    )
    return state to actions
}

private class EscalationActions(
    val onEnabledChange: (Boolean) -> Unit,
    val onToggleMethod: (String) -> Unit,
    val onMove: (String, Int) -> Unit,
    val onRun: () -> Unit,
)

private fun formatTime(millis: Long): String =
    runCatching { SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(Date(millis)) }.getOrDefault("")

private fun lastAttemptText(last: AutoEscalation.LastAttempt?): String {
    if (last == null) return "尚未尝试过自动提权"
    val whenText = formatTime(last.atMillis)
    return if (last.success) {
        "上次成功 · ${last.method.ifEmpty { "已有 root" }} · $whenText"
    } else {
        "上次失败 · $whenText\n${last.detail}"
    }
}

// ── Miuix 皮肤 ──────────────────────────────────────────────────────────────

@Composable
private fun AutoEscalationMiuix(onBack: () -> Unit) {
    val (state, actions) = rememberEscalationState()

    MiuixScaffold(
        topBar = {
            MiuixTopAppBar(
                title = "自动提权并越狱",
                navigationIcon = {
                    MiuixIconButton(onClick = onBack) {
                        MiuixIcon(
                            imageVector = MiuixIcons.Back,
                            contentDescription = null,
                            tint = colorScheme.onSurface,
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.padding(horizontal = 12.dp),
            contentPadding = innerPadding,
        ) {
            item { Spacer(Modifier.height(12.dp)) }
            item {
                MiuixCard(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            MiuixText(
                                text = "开机自动提权",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Medium,
                                color = colorScheme.onSurface,
                            )
                            Spacer(Modifier.height(2.dp))
                            MiuixText(
                                text = "由普通开机自启权限触发，不需要预先有 root；拿到 root 后立即停止",
                                fontSize = 12.sp,
                                color = colorScheme.onSurfaceVariantSummary,
                            )
                        }
                        MiuixSwitch(checked = state.enabled, onCheckedChange = actions.onEnabledChange)
                    }
                }
            }

            item {
                SmallTitle(text = "提权方式与顺序", modifier = Modifier.padding(top = 12.dp))
                MiuixCard(modifier = Modifier.padding(top = 12.dp)) {
                    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
                        MiuixText(
                            text = "第一个失败会自动尝试下一个；上下箭头调整优先级",
                            fontSize = 11.sp,
                            color = colorScheme.onSurfaceVariantSummary,
                        )
                    }
                    AutoEscalation.ALL_METHODS.forEach { method ->
                        val index = state.order.indexOf(method)
                        val selected = index >= 0
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    if (selected) {
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(50))
                                                .background(colorScheme.primary.copy(alpha = 0.14f))
                                                .padding(horizontal = 6.dp, vertical = 1.dp),
                                        ) {
                                            MiuixText(
                                                text = "#${index + 1}",
                                                fontSize = 10.sp,
                                                color = colorScheme.primary,
                                                fontWeight = FontWeight.Medium,
                                            )
                                        }
                                        Spacer(Modifier.width(6.dp))
                                    }
                                    MiuixText(
                                        text = AutoEscalation.label(method),
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = colorScheme.onSurface,
                                    )
                                }
                                MiuixText(
                                    text = AutoEscalation.description(method),
                                    fontSize = 11.sp,
                                    color = colorScheme.onSurfaceVariantSummary,
                                )
                            }
                            MiuixIconButton(onClick = { actions.onMove(method, -1) }) {
                                MiuixIcon(
                                    imageVector = Icons.Filled.KeyboardArrowUp,
                                    contentDescription = "上移",
                                    tint = if (selected) colorScheme.onSurface else colorScheme.onSurfaceVariantSummary,
                                    modifier = Modifier.size(20.dp),
                                )
                            }
                            MiuixIconButton(onClick = { actions.onMove(method, 1) }) {
                                MiuixIcon(
                                    imageVector = Icons.Filled.KeyboardArrowDown,
                                    contentDescription = "下移",
                                    tint = if (selected) colorScheme.onSurface else colorScheme.onSurfaceVariantSummary,
                                    modifier = Modifier.size(20.dp),
                                )
                            }
                            MiuixSwitch(checked = selected, onCheckedChange = { actions.onToggleMethod(method) })
                        }
                    }
                }
            }

            item {
                SmallTitle(text = "立即尝试", modifier = Modifier.padding(top = 12.dp))
                MiuixCard(modifier = Modifier.padding(top = 12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            MiuixText(
                                text = "手动跑一次回退链",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Medium,
                                color = colorScheme.onSurface,
                            )
                            MiuixText(
                                text = lastAttemptText(state.last),
                                fontSize = 11.sp,
                                color = if (state.last?.success == false) AccentWarn else colorScheme.onSurfaceVariantSummary,
                            )
                        }
                        MiuixTextButton(
                            text = if (state.running) "执行中…" else "开始",
                            onClick = { actions.onRun() },
                        )
                    }
                    if (state.log.isNotEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 220.dp)
                                .padding(horizontal = 16.dp)
                                .padding(bottom = 12.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(colorScheme.onSurface.copy(alpha = 0.04f))
                                .padding(10.dp),
                        ) {
                            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                                state.log.forEach { line ->
                                    MiuixText(
                                        text = line,
                                        fontSize = 11.sp,
                                        fontFamily = FontFamily.Monospace,
                                        color = colorScheme.onSurfaceVariantSummary,
                                    )
                                }
                            }
                        }
                    }
                }
            }

            item {
                MiuixCard(modifier = Modifier.padding(top = 12.dp, bottom = 24.dp)) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        MiuixText(
                            text = "说明",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            color = colorScheme.onSurface,
                        )
                        MiuixText(
                            text = "越狱模式每次完整重启后都没有 root，所以自动提权只能在开机时从应用进程发起：" +
                                "不预先假设有 root，也不写系统分区。开机提权会延长开机时间，失败还可能留下痕迹，" +
                                "因此默认关闭；连续失败 3 次会自动暂停，直到你手动重试。",
                            fontSize = 11.sp,
                            color = colorScheme.onSurfaceVariantSummary,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                }
            }
        }
    }
}

// ── Material 3 皮肤 ────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AutoEscalationMaterial(onBack: () -> Unit) {
    val (state, actions) = rememberEscalationState()
    val scheme = MaterialTheme.colorScheme

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("自动提权并越狱") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(MiuixIcons.Back, contentDescription = null)
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = scheme.surfaceContainer),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("开机自动提权", style = MaterialTheme.typography.titleSmall)
                            Text(
                                text = "由普通开机自启权限触发，不需要预先有 root；拿到 root 后立即停止",
                                style = MaterialTheme.typography.bodySmall,
                                color = scheme.onSurfaceVariant,
                            )
                        }
                        Switch(checked = state.enabled, onCheckedChange = actions.onEnabledChange)
                    }
                }
            }

            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = scheme.surfaceContainer),
                ) {
                    Column {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text("提权方式与顺序", style = MaterialTheme.typography.titleSmall)
                            Text(
                                text = "第一个失败会自动尝试下一个；上下箭头调整优先级",
                                style = MaterialTheme.typography.bodySmall,
                                color = scheme.onSurfaceVariant,
                            )
                        }
                        HorizontalDivider(color = scheme.outlineVariant.copy(alpha = 0.5f))
                        AutoEscalation.ALL_METHODS.forEachIndexed { methodIndex, method ->
                            if (methodIndex > 0) {
                                HorizontalDivider(color = scheme.outlineVariant.copy(alpha = 0.5f))
                            }
                            val index = state.order.indexOf(method)
                            val selected = index >= 0
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        if (selected) {
                                            Box(
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(50))
                                                    .background(scheme.primary.copy(alpha = 0.14f))
                                                    .padding(horizontal = 6.dp, vertical = 1.dp),
                                            ) {
                                                Text(
                                                    text = "#${index + 1}",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = scheme.primary,
                                                )
                                            }
                                            Spacer(Modifier.width(6.dp))
                                        }
                                        Text(
                                            text = AutoEscalation.label(method),
                                            style = MaterialTheme.typography.bodyLarge,
                                        )
                                    }
                                    Text(
                                        text = AutoEscalation.description(method),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = scheme.onSurfaceVariant,
                                    )
                                }
                                IconButton(onClick = { actions.onMove(method, -1) }) {
                                    Icon(Icons.Filled.KeyboardArrowUp, contentDescription = "上移")
                                }
                                IconButton(onClick = { actions.onMove(method, 1) }) {
                                    Icon(Icons.Filled.KeyboardArrowDown, contentDescription = "下移")
                                }
                                Switch(checked = selected, onCheckedChange = { actions.onToggleMethod(method) })
                            }
                        }
                    }
                }
            }

            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = scheme.surfaceContainer),
                ) {
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("手动跑一次回退链", style = MaterialTheme.typography.titleSmall)
                                Text(
                                    text = lastAttemptText(state.last),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (state.last?.success == false) scheme.error else scheme.onSurfaceVariant,
                                )
                            }
                            TextButton(onClick = { actions.onRun() }) {
                                Text(if (state.running) "执行中…" else "开始")
                            }
                        }
                        if (state.log.isNotEmpty()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = 220.dp)
                                    .padding(horizontal = 16.dp)
                                    .padding(bottom = 12.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(scheme.surfaceContainerHighest)
                                    .padding(10.dp),
                            ) {
                                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                                    state.log.forEach { line ->
                                        Text(
                                            text = line,
                                            style = MaterialTheme.typography.bodySmall,
                                            fontFamily = FontFamily.Monospace,
                                            color = scheme.onSurfaceVariant,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = scheme.surfaceContainer),
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("说明", style = MaterialTheme.typography.titleSmall)
                        Text(
                            text = "越狱模式每次完整重启后都没有 root，所以自动提权只能在开机时从应用进程发起：" +
                                "不预先假设有 root，也不写系统分区。开机提权会延长开机时间，失败还可能留下痕迹，" +
                                "因此默认关闭；连续失败 3 次会自动暂停，直到你手动重试。",
                            style = MaterialTheme.typography.bodySmall,
                            color = scheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                }
            }
        }
    }
}
