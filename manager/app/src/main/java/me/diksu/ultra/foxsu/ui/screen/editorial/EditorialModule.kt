package me.diksu.ultra.foxsu.ui.screen.editorial

import me.diksu.ultra.foxsu.ui.component.bottombar.useSideDock
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import me.diksu.ultra.R
import me.diksu.ultra.foxsu.data.settings.CustomizationStore
import me.diksu.ultra.foxsu.ui.screen.module.ModuleCardActions
import me.diksu.ultra.foxsu.data.model.Module

private val modPairs = listOf(
    0xFF8EF58AL to 0xFF2ECC71L,
    0xFF7AA2FFL to 0xFF4D5CE5L,
    0xFFFF7AC8L to 0xFFE0409AL,
    0xFFFF7A59L to 0xFFFF4D6DL,
    0xFF5CE1E6L to 0xFF00B8D9L,
)

/**
 * FoxSu Editorial 模块页：masthead + 账目式模块行 + 获取更多（仓库 / 导入 zip）。
 */
@Composable
fun EditorialModule(
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
    val t = edTokens()
    val enabled = modules.count { it.enabled }
    val repoTitle = stringResource(R.string.fx_module_repos)
    val repoSub = stringResource(R.string.fx_glass_repo_sub)
    val addTitle = stringResource(R.string.fx_glass_add_module_hint)
    val addSub = stringResource(R.string.fx_glass_add_module_sub)
    val refreshTitle = stringResource(R.string.fx_glass_refresh_modules)
    val refreshSub = stringResource(R.string.fx_glass_refresh_modules_sub)
    val cleanTitle = stringResource(R.string.fx_glass_clean_modules)
    val cleanSub = stringResource(R.string.fx_glass_clean_modules_sub)
    val chevron: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit = {
        Text("›", fontSize = 13.sp, color = t.faint)
    }
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .then(edPageBg(t.paper))
            .statusBarsPadding(),
        contentPadding = PaddingValues(start = if (useSideDock()) 96.dp else 0.dp, bottom = bottomInnerPadding + 24.dp),
    ) {
        item {
            EdMasthead(
                brand = stringResource(R.string.fx_module),
                tag = "MODULES",
                left = "${modules.size} 个模块 · $enabled 个已启用",
                right = "LIST",
                brandSize = 28,
            )
        }

        if (modules.isNotEmpty()) {
            item { EdSection("01", "已安装", topPadding = 14.dp) }
            items(modules.size) { index ->
                val m = modules[index]
                val (c1, c2) = modPairs[Math.floorMod(index, modPairs.size)]
                var actionsOpen by rememberSaveable(m.id) { mutableStateOf(false) }
                Column(modifier = Modifier.fillMaxWidth().hairBottom(t.hair)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) { actionsOpen = !actionsOpen }
                        .padding(horizontal = 22.dp, vertical = 13.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(Brush.linearGradient(listOf(Color(c1), Color(c2)))),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = if (m.enabled) "✓" else "▤",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Black,
                            color = Color.White,
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
                                fontSize = 10.sp,
                                color = t.sub,
                                lineHeight = 15.sp,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(top = 3.dp),
                            )
                        }
                        Text(
                            text = "v${m.version}",
                            fontSize = 9.sp,
                            color = t.faint,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                    Spacer(Modifier.width(10.dp))
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = if (m.enabled) "ENABLED" else "DISABLED",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 1.sp,
                            color = if (m.enabled) t.lime else t.faint,
                        )
                        Spacer(Modifier.height(8.dp))
                        EdSwitch(checked = m.enabled, onToggle = { onToggle(m) })
                    }
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
                        neutralBg = t.ink.copy(alpha = 0.06f),
                        neutralFg = t.sub,
                        pendingRemoval = m.remove,
                        onExecute = { actionsOpen = false; onExecute(m) },
                        onUninstall = { actionsOpen = false; onUninstall(m) },
                        modifier = Modifier.padding(start = 22.dp, end = 22.dp, bottom = 13.dp),
                    )
                }
                }
            }
        }

        item { EdSection("02", "获取更多") }
        EdRow(
            iconRes = R.drawable.fx_ic_d_search, glyphBg = t.violet.copy(alpha = 0.10f), glyphColor = t.violet,
            title = repoTitle,
            subtitle = repoSub,
            trailing = chevron,
            onClick = onOpenRepo,
        )
        EdRow(
            iconRes = R.drawable.fx_ic_d_plus, glyphBg = t.cyan.copy(alpha = 0.10f), glyphColor = t.cyan,
            title = addTitle,
            subtitle = addSub,
            trailing = chevron,
            onClick = onAddModule,
        )
        item { EdSection("03", "维护") }
        EdRow(
            iconRes = R.drawable.fx_ic_d_refresh, glyphBg = t.violet.copy(alpha = 0.10f), glyphColor = t.violet,
            title = refreshTitle,
            subtitle = refreshSub,
            trailing = chevron,
            onClick = onRefresh,
        )
        EdRow(
            iconRes = R.drawable.fx_ic_d_trash, glyphBg = t.acc.copy(alpha = 0.10f), glyphColor = t.acc,
            title = cleanTitle,
            subtitle = cleanSub,
            trailing = chevron,
            onClick = onCleanModules,
        )
    }
}

/** EdRow 的 LazyListScope 版本（避免在 item 外调用） */
private fun androidx.compose.foundation.lazy.LazyListScope.EdRow(
    iconRes: Int? = null,
    glyph: String = "",
    glyphBg: Color,
    glyphColor: Color,
    title: String,
    subtitle: String,
    trailing: (@Composable androidx.compose.foundation.layout.RowScope.() -> Unit)? = null,
    onClick: (() -> Unit)? = null,
) {
    item {
        me.diksu.ultra.foxsu.ui.screen.editorial.EdRow(
            iconRes = iconRes,
            glyph = glyph,
            glyphBg = glyphBg,
            glyphColor = glyphColor,
            title = title,
            subtitle = subtitle,
            trailing = trailing,
            onClick = onClick,
        )
    }
}
