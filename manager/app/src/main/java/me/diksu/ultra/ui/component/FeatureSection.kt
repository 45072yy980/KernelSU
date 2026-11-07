package me.diksu.ultra.ui.component

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import me.diksu.ultra.ui.LocalUiMode
import me.diksu.ultra.ui.component.material.SegmentedRadioItem
import me.diksu.ultra.ui.component.material.SegmentedSwitchItem
import me.diksu.ultra.ui.isMiuixFamily
import top.yukonga.miuix.kmp.basic.Card as MiuixCard
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text as MiuixText
import top.yukonga.miuix.kmp.preference.RadioButtonLocation
import top.yukonga.miuix.kmp.preference.RadioButtonPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme as miuixColors

/**
 * 特性页的骨架件，照 Shizuku / SUSFS 页面那套：`SmallTitle` 分组 + 卡片 + 紧凑行。
 *
 * 之前 UTS 与网络隔离开关页直接拿设置页的 `ArrowPreference` 拼，行高、缩进、分组
 * 间距都跟别的页面不一样，看起来像半成品。
 */
@Composable
fun FeatureSectionTitle(text: String) {
    if (LocalUiMode.current.isMiuixFamily) {
        SmallTitle(text = text, modifier = Modifier.padding(top = 12.dp))
    } else {
        Text(
            text = text,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 4.dp, top = 16.dp, bottom = 2.dp),
        )
    }
}

@Composable
fun FeatureCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    if (LocalUiMode.current.isMiuixFamily) {
        MiuixCard(modifier = modifier.padding(top = 12.dp).fillMaxWidth()) {
            Column { content() }
        }
    } else {
        Card(modifier = modifier.padding(top = 8.dp).fillMaxWidth()) {
            Column { content() }
        }
    }
}

/** 单选行：miuix 用 RadioButtonPreference，Material 用 SegmentedRadioItem。 */
@Composable
fun FeatureRadioRow(
    title: String,
    summary: String?,
    selected: Boolean,
    onClick: () -> Unit,
) {
    if (LocalUiMode.current.isMiuixFamily) {
        RadioButtonPreference(
            title = title,
            summary = summary.orEmpty(),
            radioButtonLocation = RadioButtonLocation.End,
            selected = selected,
            onClick = onClick,
        )
    } else {
        SegmentedRadioItem(title = title, summary = summary, selected = selected, onClick = onClick)
    }
}

/** 开关行。 */
@Composable
fun FeatureSwitchRow(
    title: String,
    summary: String?,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    if (LocalUiMode.current.isMiuixFamily) {
        SwitchPreference(
            title = title,
            summary = summary.orEmpty(),
            checked = checked,
            onCheckedChange = onCheckedChange,
        )
    } else {
        SegmentedSwitchItem(title = title, summary = summary, checked = checked, onCheckedChange = onCheckedChange)
    }
}

/** 状态卡里的一行：左边标签、右边值。 */
@Composable
fun FeatureValueRow(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FeatureText(text = label, muted = true, modifier = Modifier.weight(1f))
        FeatureText(text = value, muted = false)
    }
}

/** 特性页里的普通文字：跟着皮肤走，字号可以按用途给。 */
@Composable
fun FeatureText(
    text: String,
    muted: Boolean = false,
    size: Int = 14,
    modifier: Modifier = Modifier,
) {
    if (LocalUiMode.current.isMiuixFamily) {
        MiuixText(
            text = text,
            fontSize = size.sp,
            color = if (muted) miuixColors.onSurfaceVariantSummary else miuixColors.onSurface,
            modifier = modifier,
        )
    } else {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = if (muted) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
            modifier = modifier,
        )
    }
}
