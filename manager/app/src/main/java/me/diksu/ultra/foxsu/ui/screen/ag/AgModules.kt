package me.diksu.ultra.foxsu.ui.screen.ag

import me.diksu.ultra.foxsu.ui.component.bottombar.useSideDock
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Web
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import me.diksu.ultra.foxsu.data.model.Module
import me.diksu.ultra.foxsu.data.settings.CustomizationStore
import me.diksu.ultra.foxsu.ui.screen.module.ModuleActions
import me.diksu.ultra.foxsu.ui.screen.module.ModuleUiState

/**
 * 晨曦玻璃 · 模块（设计稿 p-mod）。
 * 标题 + 已启用 n/m 计数联动、搜索过滤、左侧渐变色轨模块卡、开关（品牌渐变 ON）、
 * 执行/打开/卸载真实动作、FAB 导入 zip。
 */
@Composable
fun AgModules(
    uiState: ModuleUiState,
    actions: ModuleActions,
    bottomInnerPadding: androidx.compose.ui.unit.Dp,
) {
    var query by remember { mutableStateOf("") }
    val modules = if (query.isBlank()) uiState.modules else uiState.searchResults.ifEmpty {
        uiState.modules.filter { it.name.contains(query, true) || it.description.contains(query, true) }
    }
    val enabledCount = uiState.modules.count { it.enabled }
    val context = LocalContext.current
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        if (r.resultCode != android.app.Activity.RESULT_OK) return@rememberLauncherForActivityResult
        val uris = mutableListOf<android.net.Uri>()
        r.data?.data?.let { uris.add(it) }
        r.data?.clipData?.let { c -> for (i in 0 until c.itemCount) c.getItemAt(i)?.uri?.let { uris.add(it) } }
        if (uris.isNotEmpty()) actions.onOpenFlash(uris)
    }
    Box(Modifier.fillMaxSize().agPageBg()) {
    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding(),
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .background(Ag.headSurface()).agHeadBlur()
                .padding(
                    start = if (useSideDock()) 106.dp else 18.dp,
                    end = 18.dp,
                ),
        ) {
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                AgIconBtn(AgTopIcons.Import, onClick = actions.onRefresh)
                Spacer(Modifier.weight(1f))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    // 模块仓库入口：图标 + 文字胶囊（原来是纯图标按钮，看不出是干嘛的）
                    AgTopPill(icon = Icons.Rounded.Web, text = "仓库", onClick = actions.onOpenRepo)
                }
            }
            Spacer(Modifier.height(10.dp))
            // 标题 + 计数（与开关实时联动）；设计稿 margin-top:12px
            AgTitle("模", "块", count = "已启用 $enabledCount / ${uiState.modules.size}", topMargin = 12.dp)
            AgSearch(value = query, onValueChange = { query = it }, hint = "搜索模块…")
            Spacer(Modifier.height(14.dp))
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = if (useSideDock()) 106.dp else 18.dp,
                end = 18.dp, top = 4.dp, bottom = bottomInnerPadding + 130.dp),
        ) {
            if (modules.isEmpty()) {
                item {
                    Box(Modifier.fillMaxWidth().padding(top = 90.dp), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("还没有安装模块", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Ag.Tx)
                            Spacer(Modifier.height(6.dp))
                            Text("点击右下角按钮刷入 zip 模块包", fontSize = 12.sp, color = Ag.Tx3)
                        }
                    }
                }
            }
            items(modules.size, key = { modules[it].id }) { i ->
                val m = modules[i]
                AgModuleCard(
                    m = m,
                    onToggle = { actions.onToggleModule(m) },
                    onExecute = { actions.onExecuteModuleAction(m) },
                    onOpenWebUi = { actions.onOpenWebUi(m) },
                    onUninstall = {
                        if (m.remove) actions.onUndoUninstallModule(m) else actions.onRequestUninstallConfirmation(m)
                    },
                )
            }
        }
    }

    // FAB：右下角导入 zip（设计稿 .fab，同容器内不受滚动裁切）
    Box(
        Modifier
            .align(Alignment.BottomEnd)
            .padding(end = 22.dp, bottom = bottomInnerPadding + 64.dp)
    ) {
        AgFab(onClick = { picker.launch(Intent(Intent.ACTION_GET_CONTENT).apply { type = "application/zip" }) })
    }
    }
}

/**
 * 模块卡（v2）。
 *
 * 旧版按列表序号给每张卡发一套彩虹色轨（绿 / 紫粉 / 橙 / 青 / 品红 / 靛），底色却写死
 * 成 emerald 渐变——用户把强调色换成别的，整页颜色就互相打架，这就是"配色不符合主题"
 * 的来源。新版只认一个色：当前主题强调色。
 *
 * 状态不再靠黑色蒙版区分（暗色下会脏成一块），改成"用了多少主题色"：启用有渐变竖条、
 * 淡色晕染和同色描边，停用退回中性灰面、文字降一档。
 */
@Composable
private fun AgModuleCard(
    m: Module,
    onToggle: () -> Unit,
    onExecute: () -> Unit,
    onOpenWebUi: () -> Unit,
    onUninstall: () -> Unit,
) {
    val accent = Ag.Vio
    val accent2 = Ag.Mag
    val on = m.enabled
    val dark = Ag.isDark
    val shape = RoundedCornerShape(20.dp)

    val surface = if (on) {
        Brush.linearGradient(
            listOf(
                accent.copy(alpha = if (dark) 0.16f else 0.075f),
                if (dark) Color(0xF21E1D30) else Color(0xF7FFFFFF),
            )
        )
    } else {
        // 停用卡要比页面底(Ag.Bg1)深一档：去掉黑色蒙版之后，同色会让卡片边界消失。
        // 选了背景色主题时取那套的次级面板色，否则暖米底上会嵌一块冷灰
        androidx.compose.ui.graphics.SolidColor(
            me.diksu.ultra.foxsu.ui.theme.currentBgPalette(dark)?.surface2
                ?: if (dark) Color(0xFF1E1D30) else Color(0xFFF1F0F6)
        )
    }

    Box(
        Modifier
            .fillMaxWidth()
            .padding(bottom = 12.dp)
            .shadow(
                2.dp,
                shape,
                spotColor = accent.copy(alpha = if (dark) 0.20f else 0.05f),
                ambientColor = accent.copy(alpha = if (dark) 0.12f else 0.03f),
            )
            .clip(shape)
            .background(surface)
            .border(
                1.dp,
                if (on) accent.copy(alpha = if (dark) 0.24f else 0.14f) else Ag.Line,
                shape,
            ),
    ) {
        // 3dp 主题渐变竖条：比旧的 5dp 彩虹条克制，且跟着强调色走
        if (on) {
            Box(
                Modifier
                    .matchParentSize()
                    .drawBehind {
                        drawRect(
                            brush = Brush.verticalGradient(listOf(accent, accent2)),
                            size = androidx.compose.ui.geometry.Size(3.dp.toPx(), size.height),
                        )
                    },
            )
        }

        Column(Modifier.padding(start = 17.dp, end = 15.dp, top = 14.dp, bottom = 13.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AgModuleBadge(name = m.name, on = on)
                Spacer(Modifier.width(11.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        m.name,
                        fontSize = 15.5.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = if (on) Ag.Tx else Ag.Tx2,
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                        textDecoration = if (m.remove) androidx.compose.ui.text.style.TextDecoration.LineThrough else null,
                    )
                    Spacer(Modifier.height(3.dp))
                    // 版本与作者收成一行，省下一条行高给描述
                    Text(
                        buildString {
                            append('v')
                            append(m.version.ifBlank { "—" })
                            if (m.author.isNotBlank()) {
                                append("  ·  ")
                                append(m.author)
                            }
                        },
                        fontSize = 11.5.sp,
                        color = Ag.Tx3,
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    )
                }
                Spacer(Modifier.width(8.dp))
                AgToggle(checked = m.enabled, onCheckedChange = onToggle, small = true)
            }

            if (m.description.isNotBlank()) {
                Spacer(Modifier.height(11.dp))
                Text(
                    m.description,
                    fontSize = 12.5.sp,
                    lineHeight = 19.sp,
                    color = if (on) Ag.Tx2 else Ag.Tx3,
                    maxLines = 3,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                )
            }

            Spacer(Modifier.height(12.dp))
            Box(Modifier.fillMaxWidth().height(1.dp).background(Ag.Line))
            Spacer(Modifier.height(10.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (m.hasActionScript && !m.remove) {
                    AgCardAction(Icons.Rounded.PlayArrow, "执行", accent, onExecute)
                }
                if (m.hasWebUi && !m.remove) {
                    AgCardAction(Icons.Rounded.Web, "打开", accent, onOpenWebUi)
                }
                Spacer(Modifier.weight(1f))
                AgCardAction(
                    icon = if (m.remove) Icons.Rounded.Refresh else Icons.Rounded.Delete,
                    text = if (m.remove) "撤销" else "卸载",
                    tone = Ag.Danger,
                    onClick = onUninstall,
                )
            }
        }
    }
}

/** 顶栏图文字胶囊（模块仓库入口）：比卡内按钮多一圈描边，顶栏上才立得住 */
@Composable
private fun AgTopPill(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    text: String,
    onClick: () -> Unit,
) {
    val src = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val scale by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (pressed) 0.93f else 1f,
        animationSpec = androidx.compose.animation.core.spring(
            dampingRatio = androidx.compose.animation.core.Spring.DampingRatioMediumBouncy,
            stiffness = androidx.compose.animation.core.Spring.StiffnessMedium,
        ),
        label = "ag-top-pill",
    )
    Row(
        Modifier
            .height(34.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clickable(
                interactionSource = src,
                indication = null,
                onClick = onClick,
            )
            .clip(RoundedCornerShape(11.dp))
            .background(Ag.Vio.copy(alpha = if (Ag.isDark) 0.16f else 0.07f))
            .border(1.dp, Ag.Vio.copy(alpha = if (Ag.isDark) 0.30f else 0.20f), RoundedCornerShape(11.dp))
            .padding(horizontal = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(icon, null, tint = Ag.Vio, modifier = Modifier.size(14.dp))
        Text(text, fontSize = 12.5.sp, fontWeight = FontWeight.Bold, color = Ag.Vio)
    }
}

/** 模块名首字徽章：启用态吃主题渐变，停用态退回中性底，一眼能看出状态 */
@Composable
private fun AgModuleBadge(name: String, on: Boolean) {
    val shape = RoundedCornerShape(13.dp)
    Box(
        Modifier
            .size(42.dp)
            .clip(shape)
            .background(if (on) Ag.Grad else androidx.compose.ui.graphics.SolidColor(Ag.TintInk))
            .border(1.dp, if (on) Color.Transparent else Ag.Line, shape),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = name.trim().take(1).uppercase().ifBlank { "M" },
            fontSize = 17.sp,
            fontWeight = FontWeight.Black,
            color = if (on) Color.White else Ag.Tx3,
        )
    }
}

/** 卡内操作按钮：无描边的低饱和胶囊，按下轻微回弹（旧版每张卡套一圈粒子边，整页太吵） */
@Composable
private fun AgCardAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    text: String,
    tone: Color,
    onClick: () -> Unit,
) {
    val src = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val scale by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (pressed) 0.93f else 1f,
        animationSpec = androidx.compose.animation.core.spring(
            dampingRatio = androidx.compose.animation.core.Spring.DampingRatioMediumBouncy,
            stiffness = androidx.compose.animation.core.Spring.StiffnessMedium,
        ),
        label = "ag-mod-act",
    )
    Row(
        Modifier
            .height(31.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clickable(
                interactionSource = src,
                indication = null,
                onClick = onClick,
            )
            .clip(RoundedCornerShape(10.dp))
            .background(tone.copy(alpha = if (Ag.isDark) 0.16f else 0.075f))
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Icon(icon, null, tint = tone, modifier = Modifier.size(13.dp))
        Text(text, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = tone)
    }
}
