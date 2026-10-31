package me.diksu.ultra.foxsu.ui.screen.editorial

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import me.diksu.ultra.foxsu.ui.screen.flash.FlashScreenActions
import me.diksu.ultra.foxsu.ui.screen.flash.FlashUiState
import me.diksu.ultra.foxsu.ui.screen.flash.FlashingStatus
import me.diksu.ultra.foxsu.ui.screen.about.AboutScreenActions
import me.diksu.ultra.foxsu.ui.screen.about.AboutUiState
import me.diksu.ultra.foxsu.ui.screen.sulog.SulogActions
import me.diksu.ultra.foxsu.ui.screen.sulog.SulogScreenState
import me.diksu.ultra.foxsu.ui.util.SulogEventFilter

/**
 * FoxSu Editorial · 刷写 / 重启页：masthead（返回 + 状态）+ 墨底控制台 + 底部重启按钮。
 */
@Composable
fun EditorialFlash(state: FlashUiState, actions: FlashScreenActions) {
    val t = edTokens()
    val scroll = rememberScrollState()
    val statusText = when (state.flashingStatus) {
        FlashingStatus.FLASHING -> "执行中"
        FlashingStatus.SUCCESS -> "已完成"
        FlashingStatus.FAILED -> "失败"
    }
    val statusColor = when (state.flashingStatus) {
        FlashingStatus.FLASHING -> t.amber
        FlashingStatus.SUCCESS -> t.lime
        FlashingStatus.FAILED -> t.acc
    }
    val titleRes = when (state.flashingStatus) {
        FlashingStatus.FLASHING -> R.string.fx_flashing
        FlashingStatus.SUCCESS -> R.string.fx_flash_success
        FlashingStatus.FAILED -> R.string.fx_flash_failed
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .then(edPageBg(t.paper))
            .statusBarsPadding(),
    ) {
        EdMasthead(
            brand = stringResource(titleRes),
            tag = statusText,
            left = "FLASH LOG · 刷写日志",
            right = "LIVE",
            brandSize = 26,
            showBack = true,
            onBack = actions.onBack,
            actions = listOf(EdMastAction(R.drawable.fx_ic_d_download, actions.onSaveLog)),
        )

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(start = 22.dp, end = 22.dp, top = 16.dp, bottom = 12.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(t.ink),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(scroll)
                    .padding(14.dp),
            ) {
                LaunchedEffect(state.text) { scroll.animateScrollTo(scroll.maxValue) }
                Text(
                    text = state.text,
                    fontSize = 10.5.sp,
                    lineHeight = 16.sp,
                    fontFamily = FontFamily.Monospace,
                    color = Color(0xFFC9F0A8),
                )
            }
            Text(
                text = "OUTPUT",
                fontSize = 8.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 2.sp,
                color = Color.White,
                modifier = Modifier
                    .offset(x = 12.dp, y = (-8).dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(t.acc)
                    .padding(horizontal = 8.dp, vertical = 2.dp),
            )
        }

        if (state.showRebootAction) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 22.dp, end = 22.dp, bottom = 20.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(t.ink)
                    .clickable(onClick = actions.onReboot)
                    .padding(vertical = 14.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = stringResource(state.rebootLabelRes),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 2.sp,
                    color = t.paper,
                )
            }
        } else {
            Spacer(Modifier.height(20.dp))
        }
    }
}

/**
 * FoxSu Editorial · SU 日志：搜索 + 事件过滤条 + 等宽日志行。
 */
@Composable
fun EditorialSulog(state: SulogScreenState, actions: SulogActions) {
    val t = edTokens()
    val scroll = rememberScrollState()
    val filters = SulogEventFilter.entries

    Column(
        modifier = Modifier
            .fillMaxSize()
            .then(edPageBg(t.paper))
            .statusBarsPadding(),
    ) {
        EdMasthead(
            brand = stringResource(R.string.fx_glass_sulog),
            tag = "LOG",
            left = "SU LOG · 授权记录",
            right = "${state.visibleEntries.size} 条",
            brandSize = 26,
            showBack = true,
            onBack = actions.onBack,
            actions = listOf(
                EdMastAction(R.drawable.fx_ic_d_refresh, actions.onRefresh),
                EdMastAction(R.drawable.fx_ic_d_trash, actions.onCleanFile),
            ),
        )

        EdSearchRow(state.searchText, "搜索应用 / 命令", actions.onSearchTextChange)

        // 事件过滤：横向标签行
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .hairBottom(t.hair)
                .padding(horizontal = 22.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            filters.forEach { filter ->
                val selected = filter in state.selectedFilters
                Text(
                    text = filter.name,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 0.5.sp,
                    color = if (selected) Color.White else t.sub,
                    maxLines = 1,
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(if (selected) t.acc else Color.Transparent)
                        .border(1.dp, if (selected) t.acc else t.hair, RoundedCornerShape(4.dp))
                        .clickable { actions.onToggleFilter(filter) }
                        .padding(horizontal = 7.dp, vertical = 4.dp),
                )
            }
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(scroll),
        ) {
            if (state.visibleEntries.isEmpty()) {
                Text(
                    text = if (state.isLoading) "读取中…" else "暂无记录",
                    fontSize = 12.sp,
                    color = t.sub,
                    modifier = Modifier.padding(start = 22.dp, top = 20.dp),
                )
            }
            state.visibleEntries.forEachIndexed { index, entry ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .hairBottom(t.hair)
                        .padding(horizontal = 22.dp, vertical = 11.dp),
                    verticalAlignment = Alignment.Top,
                ) {
                    Text(
                        text = "%03d".format(index + 1),
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Black,
                        color = t.faint,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.width(30.dp),
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = entry.eventType.name,
                                fontSize = 8.5.sp,
                                fontWeight = FontWeight.ExtraBold,
                                letterSpacing = 0.5.sp,
                                color = t.acc,
                            )
                            entry.timestampText?.let {
                                Text(
                                    text = " · $it",
                                    fontSize = 8.5.sp,
                                    color = t.faint,
                                    fontFamily = FontFamily.Monospace,
                                )
                            }
                        }
                        Text(
                            text = entry.rawLine,
                            fontSize = 9.5.sp,
                            color = t.ink2,
                            fontFamily = FontFamily.Monospace,
                            lineHeight = 14.sp,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(top = 3.dp),
                        )
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

/**
 * FoxSu Editorial · 关于：masthead + 应用信息 + 链接行。
 */
@Composable
fun EditorialAbout(state: AboutUiState, actions: AboutScreenActions) {
    val t = edTokens()
    Column(
        modifier = Modifier
            .fillMaxSize()
            .then(edPageBg(t.paper))
            .statusBarsPadding()
            .verticalScroll(rememberScrollState()),
    ) {
        EdMasthead(
            brand = state.title,
            tag = "ABOUT",
            left = "FoxSu · Kernel Management",
            right = "VOL.${state.versionName}",
            brandSize = 26,
            showBack = true,
            onBack = actions.onBack,
        )

        EdSection("01", "应用信息", topPadding = 18.dp)
        EdRow(iconRes = R.drawable.fx_ic_d_android, glyphBg = t.cyan.copy(alpha = 0.10f), glyphColor = t.cyan, title = state.appName, subtitle = "应用名称")
        EdRow(iconRes = R.drawable.fx_ic_d_tool, glyphBg = t.violet.copy(alpha = 0.10f), glyphColor = t.violet, title = state.versionName, subtitle = "版本号")
        EdRow(iconRes = R.drawable.fx_ic_d_shield2, glyphBg = t.amber.copy(alpha = 0.10f), glyphColor = t.amber, title = "基于 KernelSU", subtitle = "内核级 Root 方案")

        EdSection("02", "链接")
        state.links.forEachIndexed { index, link ->
            EdRow(
                iconRes = R.drawable.fx_ic_d_link,
                glyphBg = t.acc.copy(alpha = 0.10f),
                glyphColor = t.acc,
                title = link.fullText,
                subtitle = link.url,
                trailing = { Text("›", fontSize = 13.sp, color = t.faint) },
                onClick = { actions.onOpenLink(link.url) },
            )
        }
        Spacer(Modifier.height(30.dp))
    }
}
