package me.diksu.ultra.foxsu.ui.screen.nocturne

import me.diksu.ultra.foxsu.ui.component.bottombar.useSideDock
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import me.diksu.ultra.foxsu.ui.LocalUiMode
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import me.diksu.ultra.R
import me.diksu.ultra.foxsu.data.settings.CustomizationStore
import me.diksu.ultra.foxsu.ui.component.AppIconImage
import me.diksu.ultra.foxsu.ui.screen.superuser.GroupedApps

/**
 * 超级用户 —— 严格对照视觉稿 ② 超级用户：
 * ROOT ACCESS / 超级用户 + 两个圆钮 → 搜索框 → stat3（已授权 / 待确认 / 应用总数）→
 * AUTHORIZED · 已授权 分组 → PENDING · 未授权 分组。
 * 统计数字全部来自真实授权状态。
 */
@Composable
fun NocturneSuperUserScreen(
    granted: List<GroupedApps>,
    notGranted: List<GroupedApps>,
    grantedCount: Int,
    searchValue: String,
    onSearch: (String) -> Unit,
    bottomInnerPadding: Dp,
    onOpenApp: (GroupedApps) -> Unit,
) {
    val t = ncTokens()
    var showStylePicker by remember { mutableStateOf(false) }
    if (showStylePicker) {
        me.diksu.ultra.foxsu.ui.screen.glass.GlassStylePickerDialog(
            current = LocalUiMode.current,
            onDismiss = { showStylePicker = false },
        )
    }

    Box(Modifier.fillMaxSize()) {
        NcGlowBackground(t)
        Column(Modifier.fillMaxSize().padding(top = ncStatusTop() + 2.dp, bottom = bottomInnerPadding)) {
            Box(Modifier.padding(start = if (useSideDock()) 96.dp else 18.dp, end = 18.dp)) {
                NcHeader(kicker = "ROOT ACCESS", title = "超级用户") {
                    // user / doc 两颗原本是空实现（点了没有任何反应），
                    // 与其留假按钮，不如只保留真正可用的主题切换。
                    NcIconButton(iconRes = R.drawable.fx_nc_palette, onClick = { showStylePicker = true })
                }
            }
            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = if (useSideDock()) 96.dp else 18.dp, end = 18.dp, bottom = 86.dp),
            ) {
                item {
                    NcSearchField(searchValue, onSearch, "搜索应用或包名", R.drawable.fx_nc_search_glyph)
                    Spacer(Modifier.height(12.dp))
                }
                item {
                    NcStat3(
                        listOf(
                            Triple("已授权", grantedCount.toString(), t.mint),
                            Triple("待确认", notGranted.size.toString(), t.tx2),
                            Triple("应用总数", (grantedCount + notGranted.size).toString(), t.tx),
                        )
                    )
                    Spacer(Modifier.height(4.dp))
                }
                if (granted.isNotEmpty()) {
                    item { NcSectionLabel("AUTHORIZED · 已授权") }
                    item {
                        NcGroupCard {
                            granted.forEachIndexed { i, app ->
                                NcAppRow(
                                    app = app,
                                    badge = "ROOT",
                                    badgeColor = t.mint,
                                    showDivider = i > 0,
                                    onClick = { onOpenApp(app) },
                                )
                            }
                        }
                    }
                }
                if (notGranted.isNotEmpty()) {
                    item { NcSectionLabel("PENDING · 未授权") }
                    item {
                        NcGroupCard {
                            notGranted.forEachIndexed { i, app ->
                                NcAppRow(
                                    app = app,
                                    badge = "未授权",
                                    badgeColor = t.tx3,
                                    showDivider = i > 0,
                                    onClick = { onOpenApp(app) },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/** App 行：头像 36 r12 真实图标 + 名字 13/650 + 包名 mono 9.5 tx2 + 徽章。 */
@Composable
private fun NcAppRow(
    app: GroupedApps,
    badge: String,
    badgeColor: Color,
    showDivider: Boolean,
    onClick: () -> Unit,
) {
    val t = ncTokens()
    Column(Modifier.fillMaxWidth()) {
        if (showDivider) {
            Canvas(Modifier.fillMaxWidth().padding(start = 57.dp, end = 16.dp).height(1.dp)) {
                drawRect(t.line)
            }
        }
        Row(
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(horizontal = 15.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .border(1.dp, t.navBorder, RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center,
            ) {
                AppIconImage(
                    modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(12.dp)),
                    packageInfo = app.primary.packageInfo,
                    label = app.primary.label,
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = app.primary.label,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = t.tx,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    text = app.primary.displayIdentifier,
                    fontSize = 9.5.sp,
                    fontFamily = ncMono,
                    color = t.rowDescMono,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.width(10.dp))
            NcBadge(badge, badgeColor, if (badgeColor == t.tx3) 0.05f else 0.12f)
        }
    }
}
