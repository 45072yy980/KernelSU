package me.diksu.ultra.ui.screen.susfs

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Card
import androidx.compose.material3.Icon as MaterialIcon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import me.diksu.ultra.ui.LocalUiMode
import me.diksu.ultra.ui.component.material.SegmentedListItem
import me.diksu.ultra.ui.component.material.SegmentedSwitchItem
import me.diksu.ultra.ui.isMiuixFamily
import top.yukonga.miuix.kmp.basic.Card as MiuixCard
import top.yukonga.miuix.kmp.basic.Icon as MiuixIcon
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text as MiuixText
import top.yukonga.miuix.kmp.basic.TextField as MiuixTextField
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme as miuixColors

/**
 * 六页签共用的外观件。
 *
 * 两条硬规则：
 * 1. **皮肤组件不混用** —— miuix 下用 Material3 的 Text/AlertDialog 会取错颜色（深底深字）。
 * 2. **行用项目现成的组件** —— miuix 用 `ArrowPreference`（图标 + 右箭头 + 紧凑行高），
 *    material 用 `SegmentedListItem`。自绘 Row 永远对不上设置页的观感。
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
fun SusfsText(text: String, size: Int = 15, color: Color? = null, modifier: Modifier = Modifier) {
    if (miuixSkin()) {
        MiuixText(text = text, fontSize = size.sp, color = color ?: textColor(), modifier = modifier)
    } else {
        Text(text = text, fontSize = size.sp, color = color ?: textColor(), modifier = modifier)
    }
}

/**
 * 页面顶部的说明文字。
 *
 * 一整段长句压在卡片里很难读：miuix 的 Text 不吃行距参数，所以这里按「一行一句」排，
 * 句间用 padding 拉开；[muted] 为真时用次要色，把限制条件弱化成补充说明。
 */
@Composable
fun SusfsNote(text: String, muted: Boolean = false, modifier: Modifier = Modifier) {
    SusfsText(
        text = text,
        size = 13,
        color = if (muted) mutedColor() else textColor(),
        modifier = modifier.padding(bottom = 6.dp),
    )
}

/**
 * 一个分组：标题 + 一组行。
 *
 * 标题与卡片一起渲染，页面里不再单独调用分区标题 —— miuix 的 `SmallTitle` 与 material 的
 * 小标题不会和卡片错位。
 */
@Composable
fun SusfsCard(title: String? = null, content: @Composable ColumnScope.() -> Unit) {
    if (miuixSkin()) {
        if (title != null) {
            SmallTitle(text = title, modifier = Modifier.padding(top = 14.dp))
        }
        MiuixCard(modifier = Modifier.padding(top = 6.dp).fillMaxWidth()) {
            Column { content() }
        }
    } else {
        if (title != null) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = textColor(),
                modifier = Modifier.padding(top = 14.dp, bottom = 4.dp),
            )
        }
        Card(modifier = Modifier.padding(top = 4.dp).fillMaxWidth()) {
            Column { content() }
        }
    }
}

/** 标准行：图标 + 标题 + 副标题 + 右箭头，整行可点。 */
@Composable
fun SusfsRow(
    title: String,
    summary: String? = null,
    icon: ImageVector? = null,
    onClick: (() -> Unit)? = null,
) {
    if (miuixSkin()) {
        ArrowPreference(
            title = title,
            summary = summary.orEmpty(),
            startAction = if (icon != null) ({
                MiuixIcon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = miuixColors.onBackground,
                    modifier = Modifier.padding(end = 8.dp),
                )
            }) else null,
            onClick = onClick ?: {},
        )
    } else {
        val summaryText = summary?.takeIf { it.isNotBlank() }
        SegmentedListItem(
            onClick = onClick,
            headlineContent = { Text(title) },
            supportingContent = if (summaryText != null) ({ Text(summaryText) }) else null,
            leadingContent = if (icon != null) ({ MaterialIcon(icon, null) }) else null,
            trailingContent = {
                MaterialIcon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null)
            },
        )
    }
}

/** 开关行。 */
@Composable
fun SusfsSwitchRow(
    title: String,
    summary: String? = null,
    icon: ImageVector? = null,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    if (miuixSkin()) {
        SwitchPreference(
            title = title,
            summary = summary.orEmpty(),
            startAction = if (icon != null) ({
                MiuixIcon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = miuixColors.onBackground,
                    modifier = Modifier.padding(end = 8.dp),
                )
            }) else null,
            checked = checked,
            onCheckedChange = onCheckedChange,
        )
    } else {
        SegmentedSwitchItem(
            icon = icon ?: Icons.AutoMirrored.Filled.KeyboardArrowRight,
            title = title,
            summary = summary.orEmpty(),
            checked = checked,
            onCheckedChange = onCheckedChange,
        )
    }
}

/** 一条小徽标，用来显示"启用 / 未启用"。 */
@Composable
fun SusfsPill(text: String, highlighted: Boolean) {
    Row(modifier = Modifier.padding(end = 12.dp)) {
        SusfsText(
            text = text,
            size = 12,
            color = if (highlighted) primaryColor() else mutedColor(),
        )
    }
}

/** 行尾的可点文字（删除之类），点击区域包在 Row 上。 */
@Composable
fun SusfsTrailingAction(text: String, onClick: () -> Unit, danger: Boolean = false) {
    Row(
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(start = 12.dp, end = 4.dp),
    ) {
        SusfsText(text = text, size = 13, color = if (danger) errorColor() else primaryColor())
    }
}

/** 顶部页签条：横向滚动，选中靠颜色 + 下划线区分。 */
@Composable
fun SusfsTabBar(tabs: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        tabs.forEachIndexed { index, label ->
            val active = index == selected
            val color by animateColorAsState(
                targetValue = if (active) primaryColor() else mutedColor(),
                label = "susfs-tab-color",
            )
            Column(
                modifier = Modifier
                    .clickable { onSelect(index) }
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                SusfsText(text = label, size = 14, color = color)
                Row(
                    modifier = Modifier
                        .padding(top = 3.dp)
                        .width(16.dp)
                        .height(2.dp)
                        .background(
                            if (active) primaryColor() else Color.Transparent,
                            RoundedCornerShape(1.dp),
                        ),
                ) {}
            }
        }
    }
}

/** 空列表时的占位。 */
@Composable
fun SusfsEmptyHint(text: String) {
    Row(modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
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

/** 顶部提示条：未应用的改动、需要重启之类，右侧可带一个动作。 */
@Composable
fun SusfsNotice(
    text: String,
    actionText: String? = null,
    onAction: (() -> Unit)? = null,
    secondaryText: String? = null,
    onSecondary: (() -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp)
            .background(primaryColor().copy(alpha = 0.12f), RoundedCornerShape(10.dp))
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            SusfsText(text = text, size = 13)
        }
        if (secondaryText != null && onSecondary != null) {
            Row(modifier = Modifier.clickable(onClick = onSecondary).padding(start = 12.dp)) {
                SusfsText(text = secondaryText, size = 13, color = mutedColor())
            }
        }
        if (actionText != null && onAction != null) {
            Row(modifier = Modifier.clickable(onClick = onAction).padding(start = 12.dp)) {
                SusfsText(text = actionText, size = 13, color = primaryColor())
            }
        }
    }
}

/** 忙碌条。 */
@Composable
fun SusfsBusyBar() {
    LinearProgressIndicator(
        modifier = Modifier.fillMaxWidth().height(2.dp),
        color = primaryColor(),
    )
}
