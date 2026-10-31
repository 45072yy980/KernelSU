package me.diksu.ultra.foxsu.ui.screen.editorial

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import me.diksu.ultra.R
import me.diksu.ultra.foxsu.data.model.RepoModule
import me.diksu.ultra.foxsu.ui.screen.modulerepo.ModuleRepoUiState
import me.diksu.ultra.foxsu.ui.screen.modulerepo.RepoSort

/**
 * FoxSu Editorial · 模块仓库：masthead + 排序标签 + 账目式模块清单。
 */
@Composable
fun EditorialModuleRepo(
    state: ModuleRepoUiState,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onSearchTextChange: (String) -> Unit,
    onOpenDetail: (RepoModule) -> Unit,
    onSetSortOrder: (RepoSort) -> Unit,
) {
    val t = edTokens()
    val modules = if (state.searchStatus.searchText.isBlank()) state.modules else state.searchResults
    val searchHint = stringResource(R.string.fx_glass_search_modules)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .then(edPageBg(t.paper))
            .statusBarsPadding(),
    ) {
        EdMasthead(
            brand = "模块仓库",
            tag = "REPO",
            left = "ONLINE · 社区模块索引",
            right = "${modules.size} 项",
            brandSize = 26,
            showBack = true,
            onBack = onBack,
            actions = listOf(EdMastAction(R.drawable.fx_ic_d_refresh, onRefresh)),
        )
        EdSearchRow(state.searchStatus.searchText, searchHint, onSearchTextChange)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .hairBottom(t.hair)
                .padding(horizontal = 22.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            listOf(
                RepoSort.UPDATED to "最近更新",
                RepoSort.NAME to "名称",
                RepoSort.STARS to "星标",
            ).forEach { (sort, label) ->
                val selected = state.sortOrder == sort
                Text(
                    text = label,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 0.5.sp,
                    color = if (selected) Color.White else t.sub,
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(if (selected) t.acc else Color.Transparent)
                        .border(1.dp, if (selected) t.acc else t.hair, RoundedCornerShape(4.dp))
                        .clickable { onSetSortOrder(sort) }
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                )
            }
        }

        LazyColumn(modifier = Modifier.weight(1f)) {
            if (modules.isEmpty()) {
                item {
                    Text(
                        text = if (state.isRefreshing) "加载中…" else "暂无模块",
                        fontSize = 12.sp,
                        color = t.sub,
                        modifier = Modifier.padding(start = 22.dp, top = 20.dp),
                    )
                }
            }
            items(modules.size) { index ->
                val m = modules[index]
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .hairBottom(t.hair)
                        .clickable { onOpenDetail(m) }
                        .padding(horizontal = 22.dp, vertical = 13.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "%02d".format(index + 1),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Black,
                        color = t.faint,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.width(28.dp),
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = m.moduleName,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = t.ink,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false),
                            )
                            if (m.metamodule) {
                                Text(
                                    text = "META",
                                    fontSize = 7.5.sp,
                                    fontWeight = FontWeight.Black,
                                    color = t.acc,
                                    modifier = Modifier
                                        .padding(start = 6.dp)
                                        .border(1.dp, t.acc, RoundedCornerShape(3.dp))
                                        .padding(horizontal = 4.dp, vertical = 1.dp),
                                )
                            }
                        }
                        Text(
                            text = m.summary.ifBlank { m.authors },
                            fontSize = 10.sp,
                            color = t.sub,
                            lineHeight = 15.sp,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(top = 3.dp),
                        )
                        Text(
                            text = "v${m.latestVersion} · ${m.latestReleaseTime}",
                            fontSize = 9.sp,
                            color = t.faint,
                            fontFamily = FontFamily.Monospace,
                            maxLines = 1,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                    m.stars?.let {
                        Text(
                            text = "★ $it",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = t.amber,
                            modifier = Modifier.padding(start = 8.dp),
                        )
                    }
                }
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}
