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
import androidx.compose.material3.OutlinedTextField
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
import top.yukonga.miuix.kmp.basic.Text as MiuixText
import top.yukonga.miuix.kmp.basic.TextField as MiuixTextField
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme as miuixColors

/**
 * 六页签共用的外观件。
 *
 * 关键点：**皮肤组件不能混用**。上一版在 miuix 皮肤下用 Material3 的 Text 和 AlertDialog，
 * 文字取到 Material 的深色 onSurface、卡片却是 miuix 的深底，整屏几乎看不见，弹窗配色也
 * 错乱。所以这里所有文字/输入/开关都按当前皮肤分支，页面只调用这些函数。
 */
@Composable
private fun miuixSkin(): Boolean = LocalUiMode.current.isMiuixFamily

@Composable
private fun textColor(): Color =
    if (miuixSkin()) miuixColors.onSurface else MaterialTheme.colorScheme.onSurface

@Composable
private fun mutedColor(): Color =
    if (miuixSkin()) miuixColors.onSurfaceVariantSummary else MaterialTheme.colorScheme.onSurfaceVariant

@Composable
private fun primaryColor(): Color =
    if (miuixSkin()) miuixColors.primary else MaterialTheme.colorScheme.primary

@Composable
private fun errorColor(): Color =
    if (miuixSkin()) miuixColors.error else MaterialTheme.colorScheme.error

/** 统一的文字：字号与颜色都跟着皮肤走。 */
@Composable
fun SusfsText(
    text: String,
    size: Int = 16,
    color: Color? = null,
) {
    if (miuixSkin()) {
        MiuixText(text = text, fontSize = size.sp, color = color ?: textColor())
    } else {
        Text(text = text, fontSize = size.sp, color = color ?: textColor())
    }
}

@Composable
fun SusfsSectionTitle(text: String) {
    if (miuixSkin()) {
        SmallTitle(text = text, modifier = Modifier.padding(top = 16.dp))
    } else {
        Text(
            text = text,
            style = MaterialTheme.typography.titleSmall,
            color = textColor(),
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
        androidx.compose.material3.Card(modifier = Modifier.padding(top = 8.dp).fillMaxWidth()) {
            Column { content() }
        }
    }
}

/** 标题 + 副标题 + 右侧可选动作；给了 [onClick] 才整行可点。 */
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
            SusfsText(text = title, size = 16)
            if (!summary.isNullOrBlank()) {
                SusfsText(text = summary, size = 13, color = mutedColor())
            }
        }
        trailing?.invoke()
    }
}

/** 开关行。miuix 用 SwitchPreference（自带排版），material 用自绘行 + Switch。 */
@Composable
fun SusfsSwitchRow(
    title: String,
    summary: String? = null,
    checked: Boolean,
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
                Text(text = title, fontSize = 16.sp, color = textColor())
                if (!summary.isNullOrBlank()) {
                    Text(text = summary, fontSize = 13.sp, color = mutedColor())
                }
            }
            Switch(checked = checked, onCheckedChange = onCheckedChange)
        }
    }
}

/** 一条小徽标，用来显示"启用 / 未启用"这类状态。 */
@Composable
fun SusfsPill(text: String, highlighted: Boolean) {
    SusfsText(
        text = text,
        size = 12,
        color = if (highlighted) primaryColor() else mutedColor(),
    )
}

/** 行尾的可点文字（添加 / 删除）。点击区域包在 Row 上，文字本身不必可点。 */
@Composable
fun SusfsTrailingAction(text: String, onClick: () -> Unit, danger: Boolean = false) {
    Row(
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(start = 12.dp),
    ) {
        SusfsText(
            text = text,
            size = 13,
            color = if (danger) errorColor() else primaryColor(),
        )
    }
}

/** 顶部页签条：横向滚动，选中靠颜色区分（与设置页的分组标题语言一致）。 */
@Composable
fun SusfsTabBar(tabs: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        tabs.forEachIndexed { index, label ->
            val active = index == selected
            Row(
                modifier = Modifier
                    .clickable { onSelect(index) }
                    .padding(horizontal = 12.dp, vertical = 6.dp),
            ) {
                SusfsText(
                    text = label,
                    size = 14,
                    color = if (active) primaryColor() else mutedColor(),
                )
            }
        }
    }
}

/** 空列表时的占位，比留一片空白清楚。 */
@Composable
fun SusfsEmptyHint(text: String) {
    Row(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
        SusfsText(text = text, size = 13, color = mutedColor())
    }
}

/** 输入框：miuix 用 TextField，material 用 OutlinedTextField。 */
@Composable
fun SusfsTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
) {
    if (miuixSkin()) {
        MiuixTextField(
            value = value,
            onValueChange = onValueChange,
            label = label,
            modifier = modifier,
        )
    } else {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            label = { Text(label) },
            singleLine = true,
            modifier = modifier,
        )
    }
}
