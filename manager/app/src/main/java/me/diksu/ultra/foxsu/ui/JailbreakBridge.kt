package me.diksu.ultra.foxsu.ui

import android.content.Context
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.diksu.ultra.Natives
import me.diksu.ultra.R
import me.diksu.ultra.jailbreak.AutoEscalation
import me.diksu.ultra.ui.util.DirtyFragExploit
import me.diksu.ultra.ui.util.JailbreakExploit

/**
 * FoxSu 风格首页与「我们的」越狱实现之间**唯一**的桥。
 *
 * FoxSu 那套 `foxsu/magica/…` 是它自己的 late-load 触发链（空服务 + 开机接收器），
 * 我们 4.1 已经把同类 legacy 路径从自己的设置里拆掉了（见
 * `SettingsRepositoryImpl.autoJailbreak` 的注释）。新风格首页因此不能再用它，
 * 一律经这里落到我们的实现：
 *
 * - 展示态：直接读 [me.diksu.ultra.Natives]，FoxSu 侧不保留第二套判断；
 * - 点击动作：跑 [AutoEscalation]（ghostlock → DirtyFrag，顺序由用户在
 *   「自动提权并越狱」页设定），第一个成功即停，失败会给出真实原因。
 *
 * 之所以是"就地跑"而不是"跳我们的越狱页"：FoxSu 有独立的 NavDisplay 与 Route 表，
 * 我们的 `Route.Flash(FlashIt.JailbreakExploit)` 挂在我们的导航栈上，跨壳 push
 * 只会落到没在渲染的 NavDisplay 里。等 FoxSuHost 以后把我们的 navigator 透传下来，
 * 只需把 [runJailbreak] 的实现换成"打开我们的越狱页"，调用方一行都不用动。
 */
object JailbreakBridge {

    /** 一次越狱动作的结果；字段与我们的编排器保持一致。 */
    data class JailbreakResult(
        val success: Boolean,
        val method: String,
        val message: String,
    )

    /** 是否已经处在越狱（late-load）模式：只认我们内核上报的状态。 */
    val isLateLoadMode: Boolean
        get() = Natives.isLateLoadMode

    /** 是否安全模式：同样只认我们的 native 标志。 */
    val isSafeMode: Boolean
        get() = Natives.isSafeMode

    /** 我们随包内置的提权引擎是否至少有一个可用（目前仅 arm64-v8a 内置）。 */
    fun hasEngine(context: Context): Boolean =
        JailbreakExploit.isAvailable(context) || DirtyFragExploit.isAvailable(context)

    /** 本次会真正参与编排的引擎，顺序即回退顺序。 */
    fun availableMethods(context: Context): List<String> =
        AutoEscalation.order.filter { AutoEscalation.isAvailable(context, it) }

    /**
     * 一键越狱：按用户设定的顺序跑我们的提权链。
     *
     * 阻塞在 IO 线程；[onLog] 每产生一行日志回调一次，供对话框实时展示。
     * 成功时 ghostlock/DirtyFrag 自己会把 root 交给本应用的 `ksud late-load`，
     * 管理器随后由 late-load 重启，所以成功路径通常看不到"完成"之后的内容。
     */
    suspend fun runJailbreak(
        context: Context,
        onLog: (String) -> Unit = {},
    ): JailbreakResult = withContext(Dispatchers.IO) {
        // 对话框靠这个返回值收尾：任何异常都要变成一条失败结果，不能把组合作用域的
        // 协程抛崩（爆炸点可能在偏好读取或引擎启动，不该让 App 直接退出）。
        runCatching {
            // 手动点越狱说明用户认为链路应该能通：把开机自动尝试的失败计数清零。
            AutoEscalation.resetFailStreak()
            val outcome = AutoEscalation.run(context) { line -> onLog(line) }
            JailbreakResult(
                success = outcome.success,
                method = outcome.method,
                message = outcome.message,
            )
        }.getOrElse { throwable ->
            JailbreakResult(
                success = false,
                method = "",
                message = throwable.message ?: throwable.javaClass.simpleName,
            )
        }
    }
}

/**
 * 新风格首页的越狱对话框：实时日志 + 结果。
 *
 * 刻意不复用 `me.diksu.ultra.ui.component.dialog.JailbreakDialog`：那个选择器属于
 * 我们的 Miuix/Material 外壳，选项里的 ghostlock / DirtyFrag 各自要推到我们的
 * Flash 进度页，跨壳跳不过去。这里用同一套引擎的就地执行，界面只依赖 Material3，
 * 在 Fresh / Glass / Nocturne / Editorial 下观感一致。
 */
@Composable
fun FoxSuJailbreakDialog(
    show: Boolean,
    onDismiss: () -> Unit,
) {
    if (!show) return

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var running by remember { mutableStateOf(true) }
    var logLines by remember { mutableStateOf(emptyList<String>()) }
    var result by remember { mutableStateOf<JailbreakBridge.JailbreakResult?>(null) }

    val title = stringResource(R.string.home_jailbreak)
    val closeLabel = stringResource(R.string.close)
    val failedHint = stringResource(R.string.jailbreak_timeout)

    LaunchedEffect(Unit) {
        running = true
        logLines = emptyList()
        result = null
        val finished = JailbreakBridge.runJailbreak(context) { line ->
            // 日志回调运行在 IO 线程，状态更新投回主线程，避免跨线程改快照状态。
            scope.launch { logLines = (logLines + line).takeLast(80) }
        }
        result = finished
        running = false
    }

    AlertDialog(
        // 越狱过程中不允许返回键/点外部关闭：中途消失会让人以为根本没在跑。
        onDismissRequest = { if (!running) onDismiss() },
        title = { Text(title) },
        text = {
            Column(Modifier.fillMaxWidth()) {
                Text(
                    text = logLines.joinToString("\n").ifEmpty { "…" },
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 260.dp)
                        .verticalScroll(rememberScrollState()),
                )
                val finished = result
                if (finished != null) {
                    Spacer(Modifier.height(10.dp))
                    Text(
                        text = if (finished.success) {
                            finished.message
                        } else {
                            failedHint + "\n" + finished.message
                        },
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        },
        confirmButton = {
            if (!running) {
                TextButton(onClick = onDismiss) {
                    Text(closeLabel)
                }
            }
        },
    )
}
