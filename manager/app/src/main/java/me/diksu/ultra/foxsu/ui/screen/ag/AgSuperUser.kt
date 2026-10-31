package me.diksu.ultra.foxsu.ui.screen.ag

import me.diksu.ultra.foxsu.ui.component.bottombar.useSideDock
import android.content.pm.PackageManager
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.FilterList
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import me.diksu.ultra.foxsu.data.model.AppInfo
import me.diksu.ultra.foxsu.data.settings.CustomizationStore
import me.diksu.ultra.foxsu.ui.screen.superuser.GroupedApps
import me.diksu.ultra.foxsu.ui.screen.superuser.SuperUserActions
import me.diksu.ultra.foxsu.ui.screen.superuser.SuperUserUiState

/**
 * 晨曦玻璃 · 超级用户（设计稿 p-su）。
 * 搜索过滤 + 「已授权 · ROOT / 其他应用」分组 + App 行（真实应用图标，渐变底色衬）。
 */
@Composable
fun AgSuperUser(
    uiState: SuperUserUiState,
    actions: SuperUserActions,
    bottomInnerPadding: androidx.compose.ui.unit.Dp,
) {
    var query by remember { mutableStateOf("") }
    val context = LocalContext.current

    val granted = uiState.groupedApps.filter { it.anyAllowSu }
    val others = uiState.groupedApps.filter { !it.anyAllowSu }
    fun match(g: GroupedApps): Boolean =
        query.isBlank() || g.primary.label.contains(query, true) || g.primary.packageName.contains(query, true)

    val grantedList = if (query.isBlank()) granted else granted.filter(::match)
    val otherList = if (query.isBlank()) others else others.filter(::match)

    Column(
        Modifier
            .fillMaxSize()
            .agPageBg()
            .statusBarsPadding(),
    ) {
        // ── 固定头部：返回/过滤 + 标题 + 搜索（滚动内容从毛玻璃下透出）──
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(0.dp))
                .background(Ag.headSurface()).agHeadBlur()
                .padding(
                    start = if (useSideDock()) 106.dp else 18.dp,
                    end = 18.dp,
                ),
        ) {
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                AgIconBtn(AgTopIcons.AppGrid, onClick = actions.onClearSearch)
                Spacer(Modifier.weight(1f))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AgIconBtn(AgTopIcons.Chart, onClick = actions.onOpenSulog)
                    AgIconBtn(AgTopIcons.More, onClick = actions.onToggleShowSystemApps)
                }
            }
            Spacer(Modifier.height(10.dp))
            AgTitle("超级", "用户")
            AgSearch(value = query, onValueChange = { query = it }, hint = "搜索应用或包名…")
            Spacer(Modifier.height(14.dp))
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = if (useSideDock()) 106.dp else 18.dp,
                end = 18.dp, top = 4.dp, bottom = bottomInnerPadding + 110.dp),
        ) {
            if (grantedList.isNotEmpty()) {
                item { AgGHead("已授权 · ROOT") }
                items(grantedList.size, key = { grantedList[it].uid }) { i ->
                    AgAppRow(grantedList[i], context) { actions.onOpenProfile(grantedList[i]) }
                }
            }
            if (otherList.isNotEmpty()) {
                item { AgGHead("其他应用") }
                items(otherList.size, key = { otherList[it].uid + 100000 }) { i ->
                    AgAppRow(otherList[i], context) { actions.onOpenProfile(otherList[i]) }
                }
            }
            if (grantedList.isEmpty() && otherList.isEmpty()) {
                item {
                    Box(Modifier.fillMaxWidth().padding(top = 80.dp), contentAlignment = Alignment.Center) {
                        Text("没有匹配的应用", fontSize = 13.sp, color = Ag.Tx3, fontWeight = FontWeight.Medium)
                    }
                }
            }
        }
    }
}

/** App 行 .app：46dp 圆角 14 渐变底图标（真实应用图标）+ 名称/包名 + ROOT 徽标 */
@Composable
private fun AgAppRow(g: GroupedApps, context: android.content.Context, onClick: () -> Unit) {
    val app = g.primary
    val (c1, c2) = remember(app.packageName) { gradientFor(app.packageName) }
    val appInfo = remember(app.packageName) { app.packageInfo.applicationInfo }
    Box(
        Modifier
            .fillMaxWidth()
            .padding(bottom = 10.dp)
            .agClick(onClick = onClick)
            .clip(RoundedCornerShape(18.dp))
            .background(Ag.Card)
            .border(1.dp, Ag.Line, RoundedCornerShape(18.dp))
            .padding(12.dp, 14.dp, 12.dp, 14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(46.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Brush.linearGradient(listOf(c1, c2))),
                contentAlignment = Alignment.Center,
            ) {
                if (appInfo != null) {
                    // 真实应用图标（项目统一 AppIconImage，带磁盘缓存）
                    me.diksu.ultra.foxsu.ui.component.AppIconImage(
                        applicationInfo = appInfo,
                        label = app.label,
                        modifier = Modifier.size(46.dp),
                    )
                } else {
                    Text(
                        app.label.take(1).uppercase(),
                        fontSize = 19.sp, fontWeight = FontWeight.ExtraBold, color = Color.White,
                    )
                }
            }
            Spacer(Modifier.width(13.dp))
            Column(Modifier.weight(1f)) {
                Text(app.label, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Ag.Tx, maxLines = 1)
                Text(app.packageName, fontSize = 11.5.sp, color = Ag.Tx3, maxLines = 1)
            }
            if (g.anyAllowSu) {
                Box(
                    Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(Ag.Vio.copy(alpha = 0.078f))
                        .border(1.dp, Ag.Vio.copy(alpha = 0.22f), RoundedCornerShape(8.dp))
                        .padding(horizontal = 9.dp, vertical = 4.dp),
                ) {
                    Text("ROOT", fontSize = 10.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 0.6.sp, color = Ag.Vio)
                }
                Spacer(Modifier.width(8.dp))
            }
            Text("›", fontSize = 20.sp, color = Ag.Chev, fontWeight = FontWeight.Bold)
        }
    }
}
