package me.diksu.ultra.foxsu.ui.screen.glass

import me.diksu.ultra.foxsu.ui.component.bottombar.useSideDock
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import me.diksu.ultra.R
import me.diksu.ultra.foxsu.data.settings.CustomizationStore
import me.diksu.ultra.foxsu.ui.component.AppIconImage
import me.diksu.ultra.foxsu.ui.screen.superuser.GroupedApps
import me.diksu.ultra.foxsu.ui.LocalUiMode
import me.diksu.ultra.foxsu.ui.theme.glassTokens

/** 设计稿 .uico 的霓虹渐变对（135°） */
private val neonPairs = listOf(
    0xFFFF7A59L to 0xFFFF4D6DL,
    0xFFFFE45CL to 0xFFFFC300L,
    0xFFC77DFFL to 0xFF8A2BE2L,
    0xFF5CE1E6L to 0xFF00B8D9L,
    0xFF7AA2FFL to 0xFF4D5CE5L,
    0xFF8E8B9CL to 0xFF55525FL,
)

private fun neonFor(uid: Int): Pair<Color, Color> {
    val (a, b) = neonPairs[Math.floorMod(uid, neonPairs.size)]
    return Color(a) to Color(b)
}

/**
 * .urow：20 圆角白玻璃行 —— 40 圆角13 霓虹渐变方块（内嵌首字）+ 名称/包名 + 渐变 ROOT tag + .tog 开关
 */
@Composable
fun GlassSuperUserRow(app: GroupedApps, onClick: () -> Unit) {
    val t = glassTokens()
    val (c1, c2) = neonFor(app.uid)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 9.dp)
            .glassCard(20.dp)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // .uico：真实应用图标（40 圆角13 + 轻投影）
        AppIconImage(
            modifier = Modifier
                .size(40.dp)
                .shadow(
                    6.dp,
                    RoundedCornerShape(13.dp),
                    ambientColor = c1.copy(alpha = 0.22f),
                    spotColor = c1.copy(alpha = 0.22f),
                )
                .clip(RoundedCornerShape(13.dp)),
            packageInfo = app.primary.packageInfo,
            label = app.primary.label,
        )
        Spacer(Modifier.width(13.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = app.primary.label,
                fontSize = 13.5.sp,
                fontWeight = FontWeight.Bold,
                color = t.ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = app.primary.displayIdentifier,
                fontSize = 9.5.sp,
                color = t.faint,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
        // .uact：单色 ROOT tag + tog
        if (app.anyAllowSu) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(7.dp))
                    .background(t.rootTagBg)
                    .padding(horizontal = 8.dp, vertical = 4.dp),
            ) {
                Text(
                    text = "ROOT",
                    fontSize = 8.5.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 0.5.sp,
                    color = t.rootTagInk,
                    maxLines = 1,
                )
            }
            Spacer(Modifier.width(9.dp))
        }
        GlassToggle(checked = app.anyAllowSu, on1 = c1, on2 = c2)
    }
}

/** .tog：42×24 —— on 时霓虹渐变底 + 白圆点靠右；off 时浅灰底 + 白圆点 */
@Composable
fun GlassToggle(
    checked: Boolean,
    on1: Color = Color(0xFFFF7A59),
    on2: Color = Color(0xFFC77DFF),
    onClick: (() -> Unit)? = null,
) {
    val t = glassTokens()
    Box(
        modifier = Modifier
            .size(width = 42.dp, height = 24.dp)
            .clip(RoundedCornerShape(99.dp))
            .background(
                if (checked) Brush.horizontalGradient(listOf(on1, on2))
                else Brush.horizontalGradient(listOf(t.togOff, t.togOff))
            )
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(3.dp)
                .offset(x = if (checked) 18.dp else 0.dp)
                .size(18.dp)
                .shadow(if (checked) 2.dp else 0.dp, CircleShape)
                .clip(CircleShape)
                .background(if (checked) Color.White else Color(0xFFA9A6B6)),
        )
    }
}

/** .shead：分组标题 + 渐隐分割线 */
@Composable
fun GlassSectionHead(title: String) {
    val t = glassTokens()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 16.dp, bottom = 9.dp, start = 4.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            fontSize = 12.sp,
            fontWeight = FontWeight.ExtraBold,
            letterSpacing = 2.sp,
            color = t.sub,
        )
        Spacer(Modifier.width(10.dp))
        Box(
            modifier = Modifier
                .weight(1f)
                .height(1.dp)
                .background(
                    Brush.horizontalGradient(
                        listOf(
                            if (t.isDark) Color(0x1FFFFFFF) else Color(0x2E5A5578),
                            Color.Transparent
                        )
                    )
                ),
        )
    }
}

/**
 * 超级用户页整页（Glass）：头像顶栏（右侧 lime 已授权胶囊）+ 搜索条 + 已授权行 + 「未授权应用」分组
 */
@Composable
fun GlassSuperUserList(
    granted: List<GroupedApps>,
    notGranted: List<GroupedApps>,
    grantedCount: Int,
    searchValue: String,
    onSearch: (String) -> Unit,
    bottomInnerPadding: Dp,
    onOpenApp: (GroupedApps) -> Unit,
) {
    val t = glassTokens()
    var showStylePicker by remember { mutableStateOf(false) }
    if (showStylePicker) {
        GlassStylePickerDialog(current = LocalUiMode.current, onDismiss = { showStylePicker = false })
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = if (useSideDock()) 96.dp else 13.dp, end = 13.dp, top = glassStatusBarTop() + 4.dp, bottom = bottomInnerPadding + 88.dp),
    ) {
        item {
            GlassTopBar(
                title = stringResource(R.string.fx_superuser),
                subtitle = stringResource(R.string.fx_glass_superuser_sub),
                titleSize = 19,
                accent = t.amber,
                trailing = {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    GlassTopButton(iconRes = R.drawable.fx_ic_d_palette, onClick = { showStylePicker = true })
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(99.dp))
                            .background(t.lime.copy(alpha = 0.12f))
                            .border(1.dp, t.lime.copy(alpha = 0.28f), RoundedCornerShape(99.dp))
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.fx_glass_granted_count, grantedCount),
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = t.lime,
                            maxLines = 1,
                        )
                    }
                    }
                },
            )
        }
        item {
            // .search：胶囊玻璃搜索条
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 14.dp)
                    .clip(RoundedCornerShape(99.dp))
                    .background(if (t.isDark) Color(0x0AFFFFFF) else Color(0x99FFFFFF))
                    .border(1.dp, if (t.isDark) t.glassBorder else Color(0xCCFFFFFF), RoundedCornerShape(99.dp))
                    .padding(horizontal = 16.dp, vertical = 11.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    painter = painterResource(R.drawable.fx_ic_d_search),
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                    tint = t.faint,
                )
                Spacer(Modifier.width(10.dp))
                BasicTextField(
                    value = searchValue,
                    onValueChange = onSearch,
                    singleLine = true,
                    textStyle = TextStyle(fontSize = 11.5.sp, color = t.ink),
                    cursorBrush = Brush.verticalGradient(listOf(t.violet, t.violet)),
                    modifier = Modifier.weight(1f),
                    decorationBox = { inner ->
                        Box {
                            if (searchValue.isEmpty()) {
                                Text(
                                    text = stringResource(R.string.fx_glass_search_apps),
                                    fontSize = 11.5.sp,
                                    color = t.faint,
                                )
                            }
                            inner()
                        }
                    },
                )
            }
        }
        items(granted.size) { index ->
            GlassSuperUserRow(app = granted[index], onClick = { onOpenApp(granted[index]) })
        }
        if (notGranted.isNotEmpty()) {
            item { GlassSectionHead(stringResource(R.string.fx_glass_group_not_granted)) }
            items(notGranted.size) { index ->
                GlassSuperUserRow(app = notGranted[index], onClick = { onOpenApp(notGranted[index]) })
            }
        }
    }
}
