package me.diksu.ultra.foxsu.ui.screen.glass

import me.diksu.ultra.foxsu.ui.component.bottombar.useSideDock
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import me.diksu.ultra.R
import me.diksu.ultra.foxsu.data.settings.CustomizationStore
import me.diksu.ultra.foxsu.ui.screen.module.ModuleCardActions
import me.diksu.ultra.foxsu.data.model.Module
import me.diksu.ultra.foxsu.ui.LocalUiMode
import me.diksu.ultra.foxsu.ui.theme.glassTokens

/** 设计稿 .mod 图标 mico 的霓虹渐变轮换（lime/blue/pink/flame/cyan） */
private val modAccents = listOf(
    0xFF6EF08AL to 0xFF2ECC71L,
    0xFF7AA2FFL to 0xFF4D5CE5L,
    0xFFFF7AC8L to 0xFFE0409AL,
    0xFFFF7A59L to 0xFFFF4D6DL,
    0xFF5CE1E6L to 0xFF00B8D9L,
)

/**
 * Glass 模块页整页：cyan 狐头像顶栏（右侧搜索按钮）+ .mod 白玻璃卡列表
 * （46 渐变描边 mico + 版本行内已启用/已停用徽章 + tog）+ 快捷操作 + .addmod 虚线入口。
 */
@Composable
fun GlassModuleList(
    modules: List<Module>,
    bottomInnerPadding: Dp,
    onToggle: (Module) -> Unit,
    onAddModule: () -> Unit,
    onRefresh: () -> Unit,
    onOpenRepo: () -> Unit = {},
    onCleanModules: () -> Unit = onRefresh,
    onExecute: (Module) -> Unit = {},
    onUninstall: (Module) -> Unit = {},
) {
    val t = glassTokens()
    val enabledCount = modules.count { it.enabled }
    var showStylePicker by remember { mutableStateOf(false) }
    if (showStylePicker) {
        GlassStylePickerDialog(current = LocalUiMode.current, onDismiss = { showStylePicker = false })
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = if (useSideDock()) 96.dp else 13.dp, end = 13.dp, top = glassStatusBarTop() + 4.dp, bottom = bottomInnerPadding + 96.dp),
    ) {
        item {
            GlassTopBar(
                title = stringResource(R.string.fx_module),
                subtitle = stringResource(R.string.fx_glass_module_count, modules.size, enabledCount),
                titleSize = 19,
                accent = t.cyan,
                trailing = {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        GlassTopButton(iconRes = R.drawable.fx_ic_d_palette, onClick = { showStylePicker = true })
                        GlassTopButton(iconRes = R.drawable.fx_ic_d_search, onClick = onRefresh)
                    }
                },
            )
        }
        items(modules.size) { index ->
            val m = modules[index]
            val (a1, a2) = modAccents[Math.floorMod(index, modAccents.size)]
            var actionsOpen by rememberSaveable(m.id) { mutableStateOf(false) }
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 11.dp)
                    .glassCard(22.dp)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) { actionsOpen = !actionsOpen }
                    .padding(15.dp),
            ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // .mico：46 圆角15 白玻璃块 + 白描边 + 渐变 tint 图标
                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .shadow(3.dp, RoundedCornerShape(15.dp), spotColor = Color(0x1F5A5578))
                        .clip(RoundedCornerShape(15.dp))
                        .background(if (t.isDark) Color(0x0FFFFFFF) else Color(0xB3FFFFFF))
                        .border(1.dp, if (t.isDark) t.glassBorder else Color(0xE6FFFFFF), RoundedCornerShape(15.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        painter = painterResource(R.drawable.fx_ic_d_check),
                        contentDescription = null,
                        modifier = Modifier.size(22.dp),
                        tint = Color(a1),
                    )
                }
                Spacer(Modifier.width(13.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = m.name,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = t.ink,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (m.description.isNotBlank()) {
                        Text(
                            text = m.description,
                            fontSize = 10.5.sp,
                            color = t.sub,
                            lineHeight = 16.sp,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(top = 3.dp),
                        )
                    }
                    // .mver：版本号 + 已启用/已停用徽章（同一行，同设计稿）
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(top = 6.dp),
                    ) {
                        Text("v" + m.version, fontSize = 9.5.sp, color = t.faint, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = stringResource(if (m.enabled) R.string.fx_glass_mod_enabled else R.string.fx_glass_mod_disabled),
                            fontSize = 8.5.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = if (m.enabled) t.lime else t.faint,
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(if (m.enabled) t.lime.copy(alpha = 0.14f) else if (t.isDark) Color(0x0DFFFFFF) else Color(0x0A5A5578))
                                .border(
                                    1.dp,
                                    if (m.enabled) t.lime.copy(alpha = 0.32f) else t.glassBorder,
                                    RoundedCornerShape(6.dp),
                                )
                                .padding(horizontal = 8.dp, vertical = 2.dp),
                        )
                    }
                }
                Spacer(Modifier.width(10.dp))
                GlassToggle(checked = m.enabled, on1 = Color(a1), on2 = Color(a2), onClick = { onToggle(m) })
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
                    accent = t.violet,
                    neutralBg = if (t.isDark) Color(0x14FFFFFF) else Color(0x0D5A5578),
                    neutralFg = t.sub,
                    pendingRemoval = m.remove,
                    onExecute = { actionsOpen = false; onExecute(m) },
                    onUninstall = { actionsOpen = false; onUninstall(m) },
                )
            }
            }
        }
        item { GlassSectionHead(stringResource(R.string.fx_glass_quick_actions)) }
        item {
            GlassWide(
                modifier = Modifier.padding(bottom = 10.dp),
                iconRes = R.drawable.fx_ic_d_refresh,
                title = stringResource(R.string.fx_glass_refresh_modules),
                subtitle = stringResource(R.string.fx_glass_refresh_modules_sub),
                accent = t.violet,
                onClick = onRefresh,
            )
        }
        item {
            GlassWide(
                modifier = Modifier.padding(bottom = 14.dp),
                iconRes = R.drawable.fx_ic_d_trash,
                title = stringResource(R.string.fx_glass_clean_modules),
                subtitle = stringResource(R.string.fx_glass_clean_modules_sub),
                accent = t.flame,
                onClick = onCleanModules,
            )
        }
        item {
            // .addmod：1.5 虚线圆角22 —— 安装模块（选 zip）
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    // 随机七彩外圈：每次进入页面随机取一套霓虹色做 sweep 渐变描边
                    .rainbowOutline(RoundedCornerShape(22.dp), 1.5.dp)
                    .clip(RoundedCornerShape(22.dp))
                    .background(if (t.isDark) Color.Transparent else Color(0x66FFFFFF))
                    .clickable(onClick = onAddModule)
                    .padding(vertical = 16.dp)
                    .padding(bottom = 12.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    painter = painterResource(R.drawable.fx_ic_d_plus),
                    contentDescription = null,
                    modifier = Modifier.size(15.dp),
                    tint = t.sub,
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.fx_glass_add_module_hint),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = t.sub,
                )
            }
        }
        item {
            // 模块仓库：与安装胶囊同款（七彩描边虚线），上方留 12dp 间距
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .rainbowOutline(RoundedCornerShape(22.dp), 1.5.dp)
                    .clip(RoundedCornerShape(22.dp))
                    .background(if (t.isDark) Color.Transparent else Color(0x66FFFFFF))
                    .clickable(onClick = onOpenRepo)
                    .padding(vertical = 16.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    painter = painterResource(R.drawable.fx_ic_d_download),
                    contentDescription = null,
                    modifier = Modifier.size(15.dp),
                    tint = t.sub,
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.fx_module_repos),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = t.sub,
                )
            }
        }
    }
}

/** 设计稿 addmod 的七彩描边：随机取 5 色做 sweep 渐变，底部带同色柔光 */
@Composable
private fun Modifier.rainbowOutline(shape: RoundedCornerShape, width: androidx.compose.ui.unit.Dp): Modifier {
    val neon = listOf(
        Color(0xFFFF7A59), Color(0xFFFFB35C), Color(0xFFFFE45C), Color(0xFF6EF08A),
        Color(0xFF6EF0C0), Color(0xFF5CE1E6), Color(0xFF7AA2FF), Color(0xFFC77DFF), Color(0xFFFF7AC8),
    )
    val picked: List<Color> = remember { neon.shuffled().take(5) }
    val colors = picked + picked.first()
    return this
        .shadow(10.dp, shape, spotColor = picked.first().copy(alpha = 0.22f), ambientColor = picked[2].copy(alpha = 0.18f))
        .border(width, Brush.sweepGradient(colors), shape)
}
