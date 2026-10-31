package me.diksu.ultra.foxsu.ui.screen.ag

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.diksu.ultra.foxsu.ui.component.bottombar.useSideDock
import me.diksu.ultra.foxsu.ui.util.Kpm
import me.diksu.ultra.foxsu.ui.util.getRootShell

/**
 * KPM 模块管理页（晨曦玻璃风格）。
 *
 * APK 内置 kpatch 二进制（KernelPatch 官方源码交叉编译），配合 CONFIG_KPM=y 的
 * 内核即可加载 / 卸载 / 控制 KPM 模块。superkey 自动探测，也可手动填写。
 */
@Composable
fun AgKpm(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var status by remember { mutableStateOf<Kpm.Status?>(null) }
    var userKey by remember { mutableStateOf("") }
    var outDialog by remember { mutableStateOf<String?>(null) }
    var ctlTarget by remember { mutableStateOf<String?>(null) }
    var ctlText by remember { mutableStateOf("") }
    var keyDialog by remember { mutableStateOf(false) }
    var keyInput by remember { mutableStateOf("") }
    var loadArgs by remember { mutableStateOf<String?>(null) } // "显示名:bytes" 的临时装载上下文
    var pendingLoad by remember { mutableStateOf<Pair<String, ByteArray>?>(null) }

    fun toast(msg: String) = Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()

    fun refresh() {
        scope.launch {
            status = withContext(Dispatchers.IO) { Kpm.detect(getRootShell(), userKey.takeIf { it.isNotBlank() }) }
        }
    }
    LaunchedEffect(Unit) { refresh() }

    val kpmPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            runCatching {
                val bytes = context.contentResolver.openInputStream(uri)!!.readBytes()
                val name = uri.lastPathSegment?.substringAfterLast('/') ?: "module.kpm"
                pendingLoad = name to bytes
                loadArgs = "" // 触发参数对话框
            }.onFailure { toast("读取失败：${it.message}") }
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .agPageBg()
            .statusBarsPadding(),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .background(Ag.headSurface())
                .agHeadBlur()
                .padding(start = if (useSideDock()) 106.dp else 18.dp, end = 18.dp)
                .padding(top = 10.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AgIconBtn(AgTopIcons.Back, onClick = onBack, tint = Ag.Tx)
            Spacer(Modifier.weight(1f))
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 4.dp, bottom = 44.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item { AgTitle("KP", "M", topMargin = 12.dp) }

            // ── 状态卡 ──
            item {
                AgCard(Modifier.fillMaxWidth()) {
                    val st = status
                    if (st == null) {
                        Text("探测中…", fontSize = 13.sp, color = Ag.Tx2)
                    } else {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            AgRic(if (st.kernelOk) Ag.Teal else Ag.Amber, AgSettingsIcons.Shield)
                            Spacer(Modifier.width(14.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    if (st.kernelOk) "KPM 内核 · 已连接" else "未连接到 KPM 内核",
                                    fontSize = 14.5.sp, fontWeight = FontWeight.Bold, color = Ag.Tx,
                                )
                                Spacer(Modifier.height(3.dp))
                                Text(
                                    if (st.kernelOk)
                                        "KernelPatch ${st.kpVersion.ifBlank { "?" }} · 内核 ${st.kernelVersion.ifBlank { "?" }}\nSuperKey 已验证 · 已加载 ${st.modules.size} 个模块"
                                    else
                                        "没有 CONFIG_KPM=y 的内核或 SuperKey 不对。请刷入 KPM 内核（SukiSU Ultra / KernelPatch 系），或在下方手动填写 SuperKey。",
                                    fontSize = 12.sp, lineHeight = 18.sp, color = Ag.Tx2,
                                )
                            }
                        }
                    }
                }
            }

            // ── SuperKey ──
            item {
                AgCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp)) {
                    AgRow(
                        "SuperKey", status?.key?.let { "当前：${it.take(2)}•••${it.takeLast(2)}" } ?: "自动探测失败？手动填写后重试",
                        Ag.Vio, AgSettingsIcons.UserProfile, showChev = true,
                        onClick = { keyInput = userKey; keyDialog = true },
                        divider = false,
                    )
                }
            }

            // ── 已加载模块 ──
            item {
                AgGHead("已加载模块")
            }
            item {
                AgCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp)) {
                    val st = status
                    if (st != null && st.kernelOk && st.modules.isNotEmpty()) {
                        st.modules.forEach { m ->
                            AgRow(
                                m, "点按查看信息", Ag.Teal, AgSettingsIcons.Puzzle, divider = true,
                                trailing = {
                                    Text(
                                        "卸载", fontSize = 13.sp, color = Color(0xFFE05555), fontWeight = FontWeight.Bold,
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(Ag.Line)
                                            .clickable {
                                                scope.launch {
                                                    val r = withContext(Dispatchers.IO) { Kpm.unload(getRootShell(), st.key ?: "", m) }
                                                    refresh(); toast(if (r.first) "已卸载" else "卸载失败"); outDialog = r.second
                                                }
                                            }
                                            .padding(horizontal = 12.dp, vertical = 6.dp),
                                    )
                                },
                                onClick = {
                                    scope.launch {
                                        val s = withContext(Dispatchers.IO) { Kpm.info(getRootShell(), st.key!!, m) }
                                        outDialog = s
                                    }
                                },
                            )
                        }
                    } else if (st != null && st.kernelOk) {
                        Text(
                            "还没有已加载的 KPM 模块。点下方「导入 .kpm 模块」加载一个。",
                            fontSize = 11.5.sp, color = Ag.Tx2, modifier = Modifier.padding(vertical = 12.dp),
                        )
                    } else {
                        Text(
                            "连接 KPM 内核后在这里管理模块。",
                            fontSize = 11.5.sp, color = Ag.Tx2, modifier = Modifier.padding(vertical = 12.dp),
                        )
                    }
                }
            }

            // ── 操作 ──
            item {
                AgCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp)) {
                    AgRow(
                        "导入 .kpm 模块", "从文件选择 .kpm 并加载（可填加载参数）",
                        Ag.Vio, AgSettingsIcons.Customize, showChev = true,
                        onClick = {
                            val st = status
                            if (st?.kernelOk != true) toast("先连接 KPM 内核")
                            else kpmPicker.launch(arrayOf("*/*"))
                        },
                    )
                    AgRow(
                        "模块控制（ctl0）", "对已加载模块发送控制命令", Ag.Mag, AgSettingsIcons.CheckUpdate, showChev = true,
                        onClick = {
                            val st = status
                            if (st?.kernelOk != true) toast("先连接 KPM 内核")
                            else if (st.modules.isEmpty()) toast("没有已加载的模块")
                            else { ctlTarget = st.modules.first(); ctlText = "" }
                        },
                    )
                    AgRow(
                        "重新探测", "刷新内核连接与模块列表", Ag.Teal, AgSettingsIcons.SoftReboot, divider = false,
                        onClick = { refresh(); toast("正在探测…") },
                    )
                }
            }

            item {
                Text(
                    "KPM 需要刷入 CONFIG_KPM=y 的内核（如 SukiSU Ultra）。本页内置 kpatch 工具，模块加载 / 卸载 / 控制全部经内核 supercall 完成。",
                    fontSize = 11.sp, lineHeight = 16.sp, color = Ag.Tx2.copy(alpha = 0.7f),
                    modifier = Modifier.padding(horizontal = 6.dp),
                )
            }
        }
    }

    // ── SuperKey 对话框 ──
    if (keyDialog) {
        AlertDialog(
            onDismissRequest = { keyDialog = false },
            title = { Text("设置 SuperKey") },
            text = {
                androidx.compose.foundation.text.BasicTextField(
                    value = keyInput,
                    onValueChange = { keyInput = it },
                    textStyle = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Ag.Line).padding(14.dp),
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    userKey = keyInput.trim(); keyDialog = false
                    scope.launch {
                        withContext(Dispatchers.IO) {
                            if (userKey.isNotBlank()) Kpm.saveKey(getRootShell(), userKey)
                        }
                        refresh(); toast("已保存，重新探测中")
                    }
                }) { Text("保存并探测") }
            },
            dismissButton = { TextButton(onClick = { keyDialog = false }) { Text("取消") } },
        )
    }

    // ── 加载参数对话框 ──
    if (loadArgs != null && pendingLoad != null) {
        AlertDialog(
            onDismissRequest = { loadArgs = null; pendingLoad = null },
            title = { Text("加载参数（可留空）") },
            text = {
                androidx.compose.foundation.text.BasicTextField(
                    value = ctlText,
                    onValueChange = { ctlText = it },
                    textStyle = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Ag.Line).padding(14.dp),
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val (name, bytes) = pendingLoad!!; val args = ctlText.trim(); loadArgs = null; pendingLoad = null
                    scope.launch {
                        val r = withContext(Dispatchers.IO) { Kpm.importAndLoad(getRootShell(), bytes, name, args.ifBlank { null }) }
                        refresh(); toast(r.first.toString()); outDialog = r.second
                    }
                }) { Text("加载") }
            },
            dismissButton = { TextButton(onClick = { loadArgs = null; pendingLoad = null }) { Text("取消") } },
        )
    }

    // ── ctl0 对话框 ──
    ctlTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { ctlTarget = null },
            title = { Text("控制模块：$target") },
            text = {
                Column {
                    Text("发送给模块的控制命令参数：", fontSize = 12.5.sp, color = Ag.Tx2)
                    Spacer(Modifier.height(10.dp))
                    androidx.compose.foundation.text.BasicTextField(
                        value = ctlText,
                        onValueChange = { ctlText = it },
                        textStyle = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Ag.Line).padding(14.dp),
                    )
                    Spacer(Modifier.height(8.dp))
                    Row {
                        status?.modules?.take(4)?.forEach { m ->
                            Text(
                                m, fontSize = 11.sp, color = Ag.Vio, fontWeight = FontWeight.Bold,
                                modifier = Modifier
                                    .padding(end = 8.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Ag.Line)
                                    .padding(horizontal = 8.dp, vertical = 4.dp),
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val t = ctlTarget!!; val args = ctlText.trim(); ctlTarget = null
                    if (args.isNotEmpty()) scope.launch {
                        val r = withContext(Dispatchers.IO) { Kpm.control(getRootShell(), status?.key ?: "", t, args) }
                        toast(if (r.first) "已执行" else "执行失败"); outDialog = r.second
                    }
                }) { Text("发送") }
            },
            dismissButton = { TextButton(onClick = { ctlTarget = null }) { Text("取消") } },
        )
    }

    // ── 输出查看 ──
    outDialog?.let { txt ->
        AlertDialog(
            onDismissRequest = { outDialog = null },
            title = { Text("KPM 输出") },
            text = { Text(txt, fontSize = 12.5.sp, lineHeight = 19.sp) },
            confirmButton = { TextButton(onClick = { outDialog = null }) { Text("好") } },
        )
    }
}
