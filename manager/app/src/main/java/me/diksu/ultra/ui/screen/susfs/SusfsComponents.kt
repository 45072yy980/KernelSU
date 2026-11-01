package me.diksu.ultra.ui.screen.susfs

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import me.diksu.ultra.ui.LocalUiMode
import me.diksu.ultra.ui.isMiuixFamily
import top.yukonga.miuix.kmp.basic.Card as MiuixCard
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 六页签共用的外观件。
 *
 * 页面逻辑只写一遍，皮肤差异收进这几个函数里 —— 旧实现是两份 600+ 行的平行代码，改一处
 * 就得记得改两处，这次不再重复那个错误。
 */
@Composable
private fun miuixSkin(): Boolean = LocalUiMode.current.isMiuixFamily

@Composable
fun SusfsSectionTitle(text: String) {
    if (miuixSkin()) {
        SmallTitle(text = text, modifier = Modifier.padding(top = 16.dp))
    } else {
        Text(
            text = text,
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.padding(top = 16.dp, bottom = 4.dp),
        )
    }
}

/** 一组行放进同一张卡里。 */
@Composable
fun SusfsCard(content: @Composable ColumnScope.() -> Unit) {
    if (miuixSkin()) {
        MiuixCard(modifier = Modifier.padding(top = 8.dp).fillMaxWidth()) {
            Column { content() }
        }
    } else {
        androidx.compose.material3.Card(
            modifier = Modifier.padding(top = 8.dp).fillMaxWidth(),
        ) {
            Column { content() }
        }
    }
}

@Composable
private fun mutedColor(): Color =
    if (miuixSkin()) MiuixTheme.colorScheme.onSurfaceVariantSummary else MaterialTheme.colorScheme.onSurfaceVariant

@Composable
private fun primaryColor(): Color =
    if (miuixSkin()) MiuixTheme.colorScheme.primary else MaterialTheme.colorScheme.primary

/** 标题 + 副标题 + 右侧可选动作；给了 [onClick] 就整行可点。 */
@Composable
fun SusfsRow(
    title: String,
    summary: String? = null,
    trailing: (@Composable () -> Unit)? = null,
    onClick: (() -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, fontSize = 16.sp)
            if (!summary.isNullOrBlank()) {
                Text(text = summary, fontSize = 13.sp, color = mutedColor())
            }
        }
        trailing?.invoke()
    }
}

/** 开关行。miuix 用它的 SwitchPreference，material 用自绘行 + Switch。 */
@Composable
fun SusfsSwitchRow(
    title: String,
    summary: String? = null,
    checked: Boolean,
    enabled: Boolean = true,
    onCheckedChange: (Boolean) -> Unit,
) {
    if (miuixSkin()) {
        SwitchPreference(
            title = title,
            summary = summary.orEmpty(),
            checked = checked,
            onCheckedChange = onCheckedChange,
        )
    } else {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = title, fontSize = 16.sp)
                if (!summary.isNullOrBlank()) {
                    Text(text = summary, fontSize = 13.sp, color = mutedColor())
                }
            }
            Switch(checked = checked, onCheckedChange = onCheckedChange, enabled = enabled)
        }
    }
}

/** 一条小徽标，用来显示"启用 / 未启用"这类状态。 */
@Composable
fun SusfsPill(text: String, highlighted: Boolean) {
    Text(
        text = text,
        fontSize = 12.sp,
        fontWeight = FontWeight.Medium,
        color = if (highlighted) primaryColor() else mutedColor(),
        modifier = Modifier
            .padding(start = 8.dp)
            .padding(horizontal = 8.dp, vertical = 2.dp),
    )
}

/** 顶部页签条：横向滚动的小胶囊，不依赖任何皮肤组件。 */
@Composable
fun SusfsTabBar(tabs: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        tabs.forEachIndexed { index, label ->
            val active = index == selected
            Text(
                text = label,
                fontSize = 14.sp,
                fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
                color = if (active) primaryColor() else mutedColor(),
                modifier = Modifier
                    .clickable { onSelect(index) }
                    .padding(horizontal = 12.dp, vertical = 6.dp),
            )
        }
    }
}

/** 页签内容统一的内边距。 */
@Composable
fun SusfsPageColumn(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp),
        content = content,
    )
}

/** 空列表时的占位，比留一片空白清楚。 */
@Composable
fun SusfsEmptyHint(text: String) {
    Text(
        text = text,
        fontSize = 13.sp,
        color = mutedColor(),
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
    )
}
