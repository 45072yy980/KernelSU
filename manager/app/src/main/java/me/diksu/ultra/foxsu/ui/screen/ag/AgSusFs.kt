package me.diksu.ultra.foxsu.ui.screen.ag

import androidx.compose.foundation.background
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import android.widget.Toast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.diksu.ultra.R
import me.diksu.ultra.foxsu.ui.component.bottombar.useSideDock
import me.diksu.ultra.foxsu.ui.util.SusFs
import me.diksu.ultra.foxsu.ui.util.getRootShell

/**
 * SusFs 管理页（晨曦玻璃风格）。
 *
 * 内核不归管理器管：用户自己刷带 susfs 补丁的内核，本页提供
 * 检测 → 配置（隐藏路径 / 隐藏挂载 / 伪装 / 日志）→ 应用（立即 / 开机自动）
 * 的完整闭环。检测不到内核或工具时给出引导，功能全部优雅降级。
 */
@Composable
fun AgSusFs(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var status by remember { mutableStateOf<SusFs.Status?>(null) }
    var paths by remember { mutableStateOf<List<String>>(emptyList()) }
    var mounts by remember { mutableStateOf<List<String>>(emptyList()) }
    var spoofUname by remember { mutableStateOf(false) }
    var spoofCmdline by remember { mutableStateOf(false) }
    var spoofConfig by remember { mutableStateOf(false) }
    var spoofData by remember { mutableStateOf(false) }
    var unameText by remember { mutableStateOf("") }
    var cmdlineText by remember { mutableStateOf("") }
    var bootAuto by remember { mutableStateOf(false) }

    // 对话框：添加条目 / 编辑伪装文本 / 显示输出
    var addKind by remember { mutableStateOf<String?>(null) }
    var addText by remember { mutableStateOf("") }
    var editKind by remember { mutableStateOf<String?>(null) }
    var editText by remember { mutableStateOf("") }
    var outDialog by remember { mutableStateOf<String?>(null) }

    fun refresh() {
        scope.launch {
            withContext(Dispatchers.IO) {
                val shell = getRootShell()
                val st = SusFs.detect()
                status = st
                paths = SusFs.readList(shell, "sus_path")
                mounts = SusFs.readList(shell, "sus_mount")
                spoofUname = SusFs.readOpt(shell, "spoof_uname")
                spoofCmdline = SusFs.readOpt(shell, "spoof_cmdline")
                spoofConfig = SusFs.readOpt(shell, "spoof_config")
                spoofData = SusFs.readOpt(shell, "spoof_data")
                unameText = SusFs.readOptText(shell, "spoof_uname")
                cmdlineText = SusFs.readOptText(shell, "spoof_cmdline")
                bootAuto = st.bootScriptInstalled
            }
        }
    }
    LaunchedEffect(Unit) { refresh() }

    fun toast(msg: String) = Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()

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
            item { AgTitle("Sus", "Fs", topMargin = 12.dp) }

            // ── 状态卡 ──
            item {
                AgCard(Modifier.fillMaxWidth()) {
                    val st = status
                    if (st == null) {
                        Text("检测中…", fontSize = 13.sp, color = Ag.Tx2)
                    } else {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            AgRic(if (st.kernelFound) Ag.Teal else Ag.Amber, AgSettingsIcons.Shield)
                            Spacer(Modifier.width(14.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    if (st.kernelFound) "SusFs 内核 · 已就绪" else "未检测到 SusFs 内核",
                                    fontSize = 14.5.sp, fontWeight = FontWeight.Bold, color = Ag.Tx,
                                )
                                Spacer(Modifier.height(3.dp))
                                Text(
                                    if (st.kernelFound)
                                        (st.version.ifBlank { "susfs 补丁已启用" }) + if (st.toolPath != null) "\n工具：$st.toolPath" else "\n未找到 susfs 工具（可先配置，工具就绪后应用）"
                                    else
                                        "当前内核没有 susfs 补丁。请自行刷入 SusFs 内核（如 GKI_KernelSU_SUSFS 社区构建），管理器刷完即识别。",
                                    fontSize = 12.sp, lineHeight = 18.sp, color = Ag.Tx2,
                                )
                            }
                        }
                    }
                }
            }

            // ── 隐藏路径 ──
            item {
                AgCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp)) {
                    AgRow(
                        "添加隐藏路径", "sus_path：对指定应用隐藏文件 / 目录",
                        Ag.Vio, AgSettingsIcons.Customize, divider = paths.isNotEmpty(),
                        onClick = { addKind = "sus_path"; addText = "" },
                    )
                    paths.forEach { p ->
                        AgRow(
                            p, "长按或点右侧删除", Ag.Teal, AgSettingsIcons.Moon, divider = true,
                            trailing = {
                                Text(
                                    "删", fontSize = 13.sp, color = Ag.Tx2, fontWeight = FontWeight.Bold,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(Ag.Line)
                                        .padding(horizontal = 12.dp, vertical = 6.dp),
                                )
                            },
                            onClick = {
                                scope.launch {
                                    withContext(Dispatchers.IO) {
                                        SusFs.removeEntry(getRootShell(), status?.toolPath, "sus_path", p)
                                    }
                                    refresh(); toast("已删除")
                                }
                            },
                        )
                    }
                    if (paths.isEmpty()) {
                        Text(
                            "还没有配置。点击「添加隐藏路径」开始，路径会保存并立即应用。",
                            fontSize = 11.5.sp, color = Ag.Tx2, modifier = Modifier.padding(vertical = 12.dp),
                        )
                    }
                }
            }

            // ── 隐藏挂载 ──
            item {
                AgCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp)) {
                    AgRow(
                        "添加隐藏挂载", "sus_mount：从 /proc/mounts 等挂载列表中隐藏",
                        Ag.Mag, AgSettingsIcons.Puzzle, divider = mounts.isNotEmpty(),
                        onClick = { addKind = "sus_mount"; addText = "" },
                    )
                    mounts.forEach { m ->
                        AgRow(
                            m, "点按删除", Ag.Amber, AgSettingsIcons.Moon, divider = true,
                            trailing = {
                                Text(
                                    "删", fontSize = 13.sp, color = Ag.Tx2, fontWeight = FontWeight.Bold,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(Ag.Line)
                                        .padding(horizontal = 12.dp, vertical = 6.dp),
                                )
                            },
                            onClick = {
                                scope.launch {
                                    withContext(Dispatchers.IO) {
                                        SusFs.removeEntry(getRootShell(), status?.toolPath, "sus_mount", m)
                                    }
                                    refresh(); toast("已删除")
                                }
                            },
                        )
                    }
                    if (mounts.isEmpty()) {
                        Text(
                            "模块挂载点被检测？把它加进来，检测工具就看不到了。",
                            fontSize = 11.5.sp, color = Ag.Tx2, modifier = Modifier.padding(vertical = 12.dp),
                        )
                    }
                }
            }

            // ── 伪装 ──
            item {
                AgCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp)) {
                    AgToggleRow(
                        "伪装 uname", "伪造内核版本字符串（uname -a）", Ag.Vio, AgSettingsIcons.Style,
                        trailing = {
                            AgToggle(checked = spoofUname, onCheckedChange = {
                                val on = !spoofUname
                                if (on) { editKind = "spoof_uname"; editText = unameText.ifBlank { "Linux version 5.10.157-android13-8 (build@host)" } }
                                else scope.launch { withContext(Dispatchers.IO) { SusFs.setOpt(getRootShell(), status?.toolPath, "spoof_uname", false) }; refresh() }
                            })
                        },
                    )
                    AgToggleRow(
                        "伪装 cmdline", "伪造 /proc/cmdline（隐藏 root 相关启动参数）", Ag.Teal, AgSettingsIcons.CheckUpdate,
                        trailing = {
                            AgToggle(checked = spoofCmdline, onCheckedChange = {
                                val on = !spoofCmdline
                                if (on) { editKind = "spoof_cmdline"; editText = cmdlineText.ifBlank { "androidboot.veritymode=enforcing" } }
                                else scope.launch { withContext(Dispatchers.IO) { SusFs.setOpt(getRootShell(), status?.toolPath, "spoof_cmdline", false) }; refresh() }
                            })
                        },
                    )
                    AgToggleRow(
                        "伪装 config", "对检测应用隐藏内核配置（/proc/config.gz）", Ag.Mag, AgSettingsIcons.Manager,
                        trailing = {
                            AgToggle(checked = spoofConfig, onCheckedChange = {
                                val on = !spoofConfig
                                scope.launch {
                                    val r = withContext(Dispatchers.IO) { SusFs.setOpt(getRootShell(), status?.toolPath, "spoof_config", on) }
                                    refresh(); toast(r.second)
                                }
                            })
                        },
                    )
                    AgToggleRow(
                        "伪装 data", "隐藏 /data 中被标记的 root 痕迹", Ag.Amber, AgSettingsIcons.UserProfile,
                        divider = false,
                        trailing = {
                            AgToggle(checked = spoofData, onCheckedChange = {
                                val on = !spoofData
                                scope.launch {
                                    val r = withContext(Dispatchers.IO) { SusFs.setOpt(getRootShell(), status?.toolPath, "spoof_data", on) }
                                    refresh(); toast(r.second)
                                }
                            })
                        },
                    )
                }
            }

            // ── 应用与日志 ──
            item {
                AgCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp)) {
                    AgRow(
                        "立即应用全部", "把所有配置一次性提交给内核", Ag.Vio, AgSettingsIcons.CheckUpdate,
                        onClick = {
                            scope.launch {
                                val r = withContext(Dispatchers.IO) { SusFs.applyAll(getRootShell(), status?.toolPath) }
                                toast(if (r.first > 0) "已应用 ${r.first} 项" else r.second)
                                r.second.takeIf { it.isNotBlank() }?.let { outDialog = it }
                            }
                        },
                    )
                    AgToggleRow(
                        "开机自动应用", "写入 post-fs-data 脚本，重启后自动恢复全部配置", Ag.Teal, AgSettingsIcons.SoftReboot,
                        trailing = {
                            AgToggle(checked = bootAuto, onCheckedChange = {
                                val on = !bootAuto
                                scope.launch {
                                    val ok = withContext(Dispatchers.IO) { SusFs.setBootScript(getRootShell(), on) }
                                    refresh(); toast(if (ok) (if (on) "已安装开机脚本" else "已移除开机脚本") else "写入失败")
                                }
                            })
                        },
                    )
                    AgRow(
                        "查看已启用特性", "show_enabled_features 原文输出", Ag.Mag, AgSettingsIcons.Log, divider = false,
                        onClick = {
                            scope.launch {
                                val s = withContext(Dispatchers.IO) { SusFs.showFeatures(getRootShell(), status?.toolPath) }
                                outDialog = s
                            }
                        },
                    )
                }
            }

            item {
                Text(
                    "SusFs 的内核补丁需要你自己刷入（susfs4ksu 项目 / 社区 GKI 构建）。FoxSu 只负责界面与配置，不改动你的内核。",
                    fontSize = 11.sp, lineHeight = 16.sp, color = Ag.Tx2.copy(alpha = 0.7f),
                    modifier = Modifier.padding(horizontal = 6.dp),
                )
            }
        }
    }

    // ── 添加条目对话框 ──
    if (addKind != null) {
        AlertDialog(
            onDismissRequest = { addKind = null },
            title = { Text(if (addKind == "sus_path") "添加隐藏路径" else "添加隐藏挂载") },
            text = {
                androidx.compose.foundation.text.BasicTextField(
                    value = addText,
                    onValueChange = { addText = it },
                    textStyle = MaterialTheme.typography.bodyMedium,
                    decorationBox = { inner ->
                        Column {
                            inner()
                            if (addText.isEmpty()) Text(
                                if (addKind == "sus_path") "/data/adb/xxx" else "/sbin/.magisk",
                                color = Color.Gray, fontSize = 13.sp,
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Ag.Line).padding(14.dp),
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val kind = addKind!!; val v = addText.trim(); addKind = null
                    if (v.isNotEmpty()) scope.launch {
                        val r = withContext(Dispatchers.IO) { SusFs.addEntry(getRootShell(), status?.toolPath, kind, "add_$kind", v) }
                        refresh(); toast(if (r.first) "已添加并应用" else "已保存（${r.second.take(40)}）")
                    }
                }) { Text("添加") }
            },
            dismissButton = { TextButton(onClick = { addKind = null }) { Text("取消") } },
        )
    }

    // ── 伪装文本编辑对话框 ──
    if (editKind != null) {
        AlertDialog(
            onDismissRequest = { editKind = null },
            title = { Text(if (editKind == "spoof_uname") "设置 uname 伪装内容" else "设置 cmdline 伪装内容") },
            text = {
                androidx.compose.foundation.text.BasicTextField(
                    value = editText,
                    onValueChange = { editText = it },
                    textStyle = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Ag.Line).padding(14.dp),
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val kind = editKind!!; val v = editText.trim(); editKind = null
                    scope.launch {
                        val r = withContext(Dispatchers.IO) { SusFs.setOpt(getRootShell(), status?.toolPath, kind, true, v) }
                        refresh(); toast(if (r.first) "已应用" else "已保存（工具就绪后生效）")
                    }
                }) { Text("启用") }
            },
            dismissButton = { TextButton(onClick = { editKind = null }) { Text("取消") } },
        )
    }

    // ── 输出查看 ──
    outDialog?.let { txt ->
        AlertDialog(
            onDismissRequest = { outDialog = null },
            title = { Text("SusFs 输出") },
            text = { Text(txt, fontSize = 12.5.sp, lineHeight = 19.sp) },
            confirmButton = { TextButton(onClick = { outDialog = null }) { Text("好") } },
        )
    }
}
