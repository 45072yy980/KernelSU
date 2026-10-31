package me.diksu.ultra.foxsu.ui.screen.ag

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.diksu.ultra.foxsu.ui.component.bottombar.useSideDock
import me.diksu.ultra.foxsu.ui.util.AdBlock
import me.diksu.ultra.foxsu.ui.util.getRootShell
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 去广告页（晨曦玻璃风格）。
 *
 * Systemless hosts 方案：下载开源 hosts 规则 → 生成 KernelSU 模块 → 开机挂载覆盖
 * /system/etc/hosts。无 VPN、无后台、零耗电。规则源 / 白名单 / 镜像加速均可配。
 */
@Composable
fun AgAdBlock(onBack: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()

    var status by remember { mutableStateOf<AdBlock.Status?>(null) }
    var selected by remember { mutableStateOf(setOf("antiad")) }
    var vendors by remember { mutableStateOf(setOf<String>()) }
    var customUrl by remember { mutableStateOf("") }
    var useMirror by remember { mutableStateOf(true) }
    var liveCount by remember { mutableStateOf(0) }
    var busy by remember { mutableStateOf(false) }

    var wlDialog by remember { mutableStateOf(false) }
    var wlText by remember { mutableStateOf("") }
    var urlDialog by remember { mutableStateOf(false) }
    var urlInput by remember { mutableStateOf("") }
    var outDialog by remember { mutableStateOf<String?>(null) }

    fun toast(msg: String) = Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()

    fun refresh() {
        scope.launch {
            val st = withContext(Dispatchers.IO) { AdBlock.status() }
            status = st
            selected = AdBlock.SOURCES.filter { it.id in st.sourceId.split(',') }.map { it.id }.toSet()
            if (selected.isEmpty()) selected = setOf("antiad")
            vendors = st.vendorIds.split(',').map { it.trim() }.filter { it.isNotEmpty() }.toSet()
            wlText = st.whitelist.joinToString("\n")
            liveCount = withContext(Dispatchers.IO) { AdBlock.liveEntries() }
        }
    }
    LaunchedEffect(Unit) { refresh() }

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
            item { AgTitle("去", "广告", topMargin = 12.dp) }

            // ── 状态卡 ──
            item {
                AgCard(Modifier.fillMaxWidth()) {
                    val st = status
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        AgRic(
                            when { st == null -> Ag.Amber; st.installed && st.enabled -> Ag.Teal; else -> Ag.Amber },
                            AgSettingsIcons.Shield,
                        )
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                when {
                                    st == null -> "读取中…"
                                    st.installed && st.enabled -> "去广告已启用"
                                    st.installed -> "已安装 · 已停用"
                                    else -> "未安装"
                                },
                                fontSize = 14.5.sp, fontWeight = FontWeight.Bold, color = Ag.Tx,
                            )
                            Spacer(Modifier.height(3.dp))
                            Text(
                                when {
                                    st == null -> "…"
                                    st.installed -> "规则 ${st.entries} 条 · 更新于 ${
                                        if (st.updatedAt.toLongOrNull() ?: 0L > 0)
                                            SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(Date(st.updatedAt.toLong()))
                                        else "—"
                                    } · 当前 /etc/hosts 生效 $liveCount 条"
                                    else -> "选择规则源后一键安装，重启生效。无 VPN、无后台、零耗电。"
                                },
                                fontSize = 12.sp, lineHeight = 18.sp, color = Ag.Tx2,
                            )
                        }
                    }
                }
            }

            // ── 规则源 ──
            item { AgGHead("规则源（可多选）") }
            item {
                AgCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp)) {
                    AdBlock.SOURCES.forEachIndexed { i, s ->
                        val on = s.id in selected
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable {
                                    selected = if (on) selected - s.id else selected + s.id
                                }
                                .padding(vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            AgRic(if (on) Ag.Vio else Ag.Tx2, AgSettingsIcons.Customize, size = 34.dp)
                            Spacer(Modifier.width(14.dp))
                            Column(Modifier.weight(1f)) {
                                Text(s.name, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Ag.Tx)
                                Text(s.desc, fontSize = 11.5.sp, color = Ag.Tx2)
                            }
                            Box(
                                Modifier
                                    .size(20.dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(if (on) Ag.Vio else Color.Transparent)
                                    .border(1.5.dp, if (on) Ag.Vio else Ag.Line, RoundedCornerShape(6.dp)),
                                contentAlignment = Alignment.Center,
                            ) {
                                if (on) Text("✓", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                        if (i < AdBlock.SOURCES.lastIndex) Box(Modifier.fillMaxWidth().height(1.dp).background(Ag.Line))
                    }
                }
            }

            // ── 手机厂商广告 ──
            item { AgGHead("手机厂商系统广告（内置，默认关闭）") }
            item {
                AgCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp)) {
                    AdBlock.VENDORS.forEachIndexed { i, v ->
                        val on = v.id in vendors
                        Column(Modifier.fillMaxWidth()) {
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .clickable { vendors = if (on) vendors - v.id else vendors + v.id }
                                    .padding(top = 14.dp, bottom = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                AgRic(if (on) Ag.Vio else Ag.Tx2, AgSettingsIcons.Shield, size = 34.dp)
                                Spacer(Modifier.width(14.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(v.name, fontSize = 13.5.sp, fontWeight = FontWeight.Bold, color = Ag.Tx)
                                    Text("${v.domains.size} 条 · ${v.desc}", fontSize = 11.sp, lineHeight = 16.sp, color = Ag.Tx2)
                                }
                                Box(
                                    Modifier
                                        .size(20.dp)
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(if (on) Ag.Vio else Color.Transparent)
                                        .border(1.5.dp, if (on) Ag.Vio else Ag.Line, RoundedCornerShape(6.dp)),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    if (on) Text("✓", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                            if (v.affected.isNotEmpty()) {
                                Text(
                                    "⚠ ${v.affected}",
                                    fontSize = 10.5.sp, lineHeight = 15.sp, color = Color(0xFFC98A2E),
                                    modifier = Modifier.padding(start = 48.dp, bottom = 10.dp),
                                )
                            } else Spacer(Modifier.height(8.dp))
                        }
                        if (i < AdBlock.VENDORS.lastIndex) Box(Modifier.fillMaxWidth().height(1.dp).background(Ag.Line))
                    }
                }
            }

            // ── 选项 ──
            item {
                AgCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp)) {
                    AgToggleRow(
                        "GitHub 加速镜像", "raw.githubusercontent.com 走代理镜像，国内网络建议开启",
                        Ag.Teal, AgSettingsIcons.CheckUpdate,
                        trailing = { AgToggle(checked = useMirror, onCheckedChange = { useMirror = !useMirror }) },
                    )
                    AgRow(
                        "自定义规则源", if (customUrl.isBlank()) "添加自己的 hosts URL（可选）" else customUrl,
                        Ag.Mag, AgSettingsIcons.Style, showChev = true, divider = false,
                        onClick = { urlInput = customUrl; urlDialog = true },
                    )
                }
            }

            // ── 白名单 ──
            item {
                AgCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp)) {
                    AgRow(
                        "白名单", if (status?.whitelist.isNullOrEmpty()) "某些 App 因误杀不可用？把域名加进来"
                        else "已加 ${status!!.whitelist.size} 个域名",
                        Ag.Amber, AgSettingsIcons.UserProfile, showChev = true, divider = false,
                        onClick = { wlDialog = true },
                    )
                }
            }

            // ── 操作 ──
            item {
                AgCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp)) {
                    AgRow(
                        if (busy) "正在下载并合并规则…" else "更新并安装", "下载规则 → 合并去重 → 生成模块（重启生效）",
                        Ag.Vio, AgSettingsIcons.CheckUpdate,
                        onClick = {
                            if (busy) return@AgRow
                            busy = true
                            scope.launch {
                                val r = withContext(Dispatchers.IO) {
                                    AdBlock.install(selected.toList(), vendors, listOf(customUrl), useMirror,
                                        status?.whitelist?.toSet() ?: emptySet())
                                }
                                busy = false
                                toast(if (r.ok) "安装成功" else "安装失败"); outDialog = r.message
                                refresh()
                            }
                        },
                    )
                    AgToggleRow(
                        "启用去广告", "停用后重启恢复系统原始 hosts（模块保留）",
                        Ag.Teal, AgSettingsIcons.Shield,
                        trailing = {
                            AgToggle(checked = status?.installed == true && status?.enabled == true, onCheckedChange = {
                                scope.launch {
                                    val on = !(status?.enabled ?: true)
                                    val ok = withContext(Dispatchers.IO) { AdBlock.setEnabled(on) }
                                    toast(if (ok) (if (on) "已启用，重启生效" else "已停用，重启生效") else "操作失败")
                                    refresh()
                                }
                            })
                        },
                    )
                    AgRow(
                        "卸载模块", "彻底移除去广告模块（重启生效）",
                        Color(0xFFE05555), AgSettingsIcons.Moon, divider = false,
                        onClick = {
                            scope.launch {
                                val ok = withContext(Dispatchers.IO) { AdBlock.uninstall() }
                                toast(if (ok) "已卸载，重启生效" else "卸载失败")
                                refresh()
                            }
                        },
                    )
                }
            }

            item {
                Text(
                    "网络规则来自国内开源仓库：anti-AD（每日更新）、AdGuard 中文过滤，不含国外列表。厂商广告为内置专项清单，按自己机型勾选；带 ⚠ 的分组会附带影响对应功能。屏蔽通过 hosts 域名解析实现，对 App 内嵌 HTTPS 广告同样有效。",
                    fontSize = 11.sp, lineHeight = 16.sp, color = Ag.Tx2.copy(alpha = 0.7f),
                    modifier = Modifier.padding(horizontal = 6.dp),
                )
            }
        }
    }

    // ── 白名单编辑 ──
    if (wlDialog) {
        AlertDialog(
            onDismissRequest = { wlDialog = false },
            title = { Text("白名单域名") },
            text = {
                Column {
                    Text("每行一个域名，匹配该域及其全部子域。改动后需重新「更新并安装」生效。",
                        fontSize = 12.sp, color = Ag.Tx2)
                    Spacer(Modifier.height(10.dp))
                    androidx.compose.foundation.text.BasicTextField(
                        value = wlText,
                        onValueChange = { wlText = it },
                        textStyle = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Ag.Line).padding(14.dp),
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    wlDialog = false
                    scope.launch {
                        withContext(Dispatchers.IO) {
                            val domains = wlText.lines().map { it.trim().lowercase() }.filter { it.isNotEmpty() }
                            AdBlock.setWhitelist(getRootShell(), domains)
                        }
                        toast("白名单已保存"); refresh()
                    }
                }) { Text("保存") }
            },
            dismissButton = { TextButton(onClick = { wlDialog = false }) { Text("取消") } },
        )
    }

    // ── 自定义源 ──
    if (urlDialog) {
        AlertDialog(
            onDismissRequest = { urlDialog = false },
            title = { Text("自定义 hosts 规则源") },
            text = {
                androidx.compose.foundation.text.BasicTextField(
                    value = urlInput,
                    onValueChange = { urlInput = it },
                    textStyle = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Ag.Line).padding(14.dp),
                )
            },
            confirmButton = {
                TextButton(onClick = { customUrl = urlInput.trim(); urlDialog = false; toast("已保存，安装时会一并下载") }) { Text("保存") }
            },
            dismissButton = { TextButton(onClick = { urlDialog = false }) { Text("取消") } },
        )
    }

    outDialog?.let { txt ->
        AlertDialog(
            onDismissRequest = { outDialog = null },
            title = { Text("去广告") },
            text = { Text(txt, fontSize = 12.5.sp, lineHeight = 19.sp) },
            confirmButton = { TextButton(onClick = { outDialog = null }) { Text("好") } },
        )
    }
}
