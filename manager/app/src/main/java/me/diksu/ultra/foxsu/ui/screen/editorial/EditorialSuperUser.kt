package me.diksu.ultra.foxsu.ui.screen.editorial

import me.diksu.ultra.foxsu.ui.component.bottombar.useSideDock
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
import me.diksu.ultra.foxsu.ui.component.AppIconImage
import me.diksu.ultra.foxsu.ui.screen.superuser.GroupedApps
import me.diksu.ultra.foxsu.ui.theme.glassTokens

@Composable
private fun EdSuperUserRow(
    index: Int,
    app: GroupedApps,
    onClick: () -> Unit,
) {
    val t = edTokens()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .hairBottom(t.hair)
            .clickable(onClick = onClick)
            .padding(horizontal = 22.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "%02d".format(index + 1),
            fontSize = 12.sp,
            fontWeight = FontWeight.Black,
            color = t.faint,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.width(26.dp),
        )
        // 真实应用图标（38 圆角11）
        AppIconImage(
            modifier = Modifier
                .size(38.dp)
                .clip(RoundedCornerShape(11.dp)),
            packageInfo = app.primary.packageInfo,
            label = app.primary.label,
        )
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = app.primary.label,
                fontSize = 14.sp,
                fontWeight = FontWeight.ExtraBold,
                color = t.ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = app.primary.displayIdentifier,
                fontSize = 9.5.sp,
                color = t.sub,
                fontFamily = FontFamily.Monospace,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
        Spacer(Modifier.width(10.dp))
        if (app.anyAllowSu) {
            Text(
                text = "● ROOT",
                fontSize = 8.5.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 1.sp,
                color = t.lime,
                modifier = Modifier.padding(end = 10.dp),
            )
        } else {
            Text(
                text = "○ NONE",
                fontSize = 8.5.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 1.sp,
                color = t.faint,
                modifier = Modifier.padding(end = 10.dp),
            )
        }
        EdSwitch(checked = app.anyAllowSu)
    }
}

/**
 * FoxSu Editorial 超级用户页：masthead + 统计带 + 已授权/未授权目录列表。
 */
@Composable
fun EditorialSuperUser(
    granted: List<GroupedApps>,
    notGranted: List<GroupedApps>,
    grantedCount: Int,
    searchValue: String,
    onSearch: (String) -> Unit,
    bottomInnerPadding: Dp,
    onOpenApp: (GroupedApps) -> Unit,
) {
    val t = edTokens()
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .then(edPageBg(t.paper))
            .statusBarsPadding(),
        contentPadding = PaddingValues(start = if (useSideDock()) 96.dp else 0.dp, bottom = bottomInnerPadding + 30.dp),
    ) {
        item {
            EdMasthead(
                brand = stringResource(R.string.fx_superuser),
                tag = "INDEX",
                left = "已授权 $grantedCount · 待定 ${notGranted.size}",
                right = "按时间排序",
                brandSize = 28,
            )
        }
        item {
            EdStatBand(
                cells = listOf(
                    Triple(grantedCount.toString(), "已授权", t.lime),
                    Triple(notGranted.size.toString(), "未授权", null),
                    Triple("0", "已拒绝", t.acc),
                    Triple("24h", "日志记录", null),
                ),
            )
        }
        item { EdSearchRow(searchValue, stringResource(R.string.fx_glass_search_apps), onSearch) }

        if (granted.isNotEmpty()) {
            item { EdSection("01", "已授权") }
            items(granted.size) { index ->
                EdSuperUserRow(index = index, app = granted[index], onClick = { onOpenApp(granted[index]) })
            }
        }
        if (notGranted.isNotEmpty()) {
            item { EdSection("02", "未授权") }
            items(notGranted.size) { index ->
                EdSuperUserRow(index = index, app = notGranted[index], onClick = { onOpenApp(notGranted[index]) })
            }
        }
    }
}
