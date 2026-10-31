package me.diksu.ultra.foxsu.ui.screen.nocturne

import me.diksu.ultra.foxsu.ui.component.bottombar.useSideDock
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import me.diksu.ultra.R
import me.diksu.ultra.foxsu.data.settings.CustomizationStore
import me.diksu.ultra.foxsu.data.model.Module
import me.diksu.ultra.foxsu.ui.LocalUiMode
import me.diksu.ultra.foxsu.ui.screen.module.ModuleCardActions

/**
 * 模块 —— 严格对照视觉稿 ③ 模块：
 * MODULES / 模块 + 两个圆钮（作者、下载）→ 搜索模块 → 列表 / 空态（104 光球 + 文案）→ 右下 56 FAB。
 */
@Composable
fun NocturneModuleScreen(
    modules: List<Module>,
    bottomInnerPadding: Dp,
    onToggle: (Module) -> Unit,
    onAddModule: () -> Unit,
    onRefresh: () -> Unit,
    onOpenRepo: () -> Unit,
    onExecute: (Module) -> Unit = {},
    onUninstall: (Module) -> Unit = {},
) {
    val t = ncTokens()
    var query by remember { mutableStateOf("") }
    var showStylePicker by remember { mutableStateOf(false) }
    val list = remember(modules, query) {
        if (query.isBlank()) modules
        else modules.filter {
            it.name.contains(query, true) || it.description.contains(query, true)
        }
    }

    if (showStylePicker) {
        me.diksu.ultra.foxsu.ui.screen.glass.GlassStylePickerDialog(
            current = LocalUiMode.current,
            onDismiss = { showStylePicker = false },
        )
    }
    Box(Modifier.fillMaxSize()) {
        NcGlowBackground(t)
        Column(Modifier.fillMaxSize().padding(top = ncStatusTop() + 2.dp)) {
            Box(Modifier.padding(start = if (useSideDock()) 96.dp else 18.dp, end = 18.dp)) {
                NcHeader(kicker = "MODULES", title = "模块") {
                    // 原本这里还有一颗 user 图标，但没有任何落点（页面没有作者入口），
                    // 按下去只会没反应。留下能真正干活的两颗。
                    NcIconButton(iconRes = R.drawable.fx_nc_palette, onClick = { showStylePicker = true })
                    NcIconButton(iconRes = R.drawable.fx_nc_download, onClick = onOpenRepo)
                }
            }
            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = if (useSideDock()) 96.dp else 18.dp, end = 18.dp, bottom = bottomInnerPadding + 100.dp),
            ) {
                item {
                    NcSearchField(query, { query = it }, "搜索模块", R.drawable.fx_nc_search_glyph)
                    Spacer(Modifier.height(12.dp))
                }
                if (list.isEmpty()) {
                    item {
                        Box(Modifier.fillMaxWidth().height(420.dp), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                NcEmptyOrb(R.drawable.fx_nc_grid)
                                Spacer(Modifier.height(18.dp))
                                Text("还没有安装模块", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = t.tx)
                                Spacer(Modifier.height(8.dp))
                                Text("点击右下角按钮，刷入 zip 模块包", fontSize = 11.sp, color = t.tx3)
                            }
                        }
                    }
                } else {
                    item { NcSectionLabel("INSTALLED · 已安装 ${list.size}") }
                    items(list, key = { it.id }) { m ->
                        ModuleRow(m, onToggle, onExecute, onUninstall)
                        Spacer(Modifier.height(7.dp))
                    }
                }
            }
        }

        // FAB 56 violet 渐变
        NcFab(
            iconRes = R.drawable.fx_nc_plus,
            onClick = onAddModule,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 22.dp, bottom = bottomInnerPadding + 40.dp),
        )
    }
}

/** 模块行：图标盒 + 名称 + 描述 + 版本 + 开关。 */
@Composable
private fun ModuleRow(
    m: Module,
    onToggle: (Module) -> Unit,
    onExecute: (Module) -> Unit = {},
    onUninstall: (Module) -> Unit = {},
) {
    val t = ncTokens()
    var actionsOpen by rememberSaveable(m.id) { mutableStateOf(false) }
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(t.s1)
            .border(1.dp, t.line, RoundedCornerShape(20.dp))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) { actionsOpen = !actionsOpen }
            .padding(horizontal = 15.dp, vertical = 11.dp),
    ) {
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        NcIconBox(
            if (m.enabled) R.drawable.fx_nc_grid else R.drawable.fx_nc_chip,
            if (m.enabled) NcAccentTone.Violet else NcAccentTone.Default,
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    m.name,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = t.tx,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                Spacer(Modifier.width(6.dp))
                NcBadge("v${m.version}", t.tx3, 0.06f)
                if (m.update) {
                    Spacer(Modifier.width(5.dp))
                    NcBadge("可更新", t.mint, 0.12f)
                }
            }
            if (m.description.isNotBlank()) {
                Spacer(Modifier.height(3.dp))
                Text(
                    m.description,
                    fontSize = 10.5.sp,
                    color = t.rowDesc,
                    lineHeight = 15.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Spacer(Modifier.width(10.dp))
        NcSwitch(checked = m.enabled, onCheckedChange = { onToggle(m) })
    }
        AnimatedVisibility(visible = actionsOpen) {
            ModuleCardActions(
                runnable = m.hasActionScript && m.enabled && !m.remove && !m.update,
                reason = when {
                    !m.hasActionScript -> stringResource(R.string.fx_module_action_sheet_no_action)
                    m.remove -> stringResource(R.string.fx_module_action_sheet_removing)
                    !m.enabled -> stringResource(R.string.fx_module_action_sheet_disabled)
                    m.update -> stringResource(R.string.fx_module_action_sheet_pending)
                    else -> null
                },
                accent = t.vio,
                neutralBg = t.fill,
                neutralFg = t.tx2,
                pendingRemoval = m.remove,
                onExecute = { actionsOpen = false; onExecute(m) },
                onUninstall = { actionsOpen = false; onUninstall(m) },
            )
        }
    }
}
