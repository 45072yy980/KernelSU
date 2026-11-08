package me.diksu.ultra.ui.component

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import me.diksu.ultra.ui.LocalUiMode
import me.diksu.ultra.ui.component.material.SegmentedRadioItem
import me.diksu.ultra.ui.component.material.SegmentedSwitchItem
import me.diksu.ultra.ui.component.material.TopBarBackButton
import me.diksu.ultra.ui.isMiuixFamily
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card as MiuixCard
import top.yukonga.miuix.kmp.basic.Icon as MiuixIcon
import top.yukonga.miuix.kmp.basic.IconButton as MiuixIconButton
import top.yukonga.miuix.kmp.basic.InfiniteProgressIndicator
import top.yukonga.miuix.kmp.basic.Scaffold as MiuixScaffold
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.basic.Text as MiuixText
import top.yukonga.miuix.kmp.basic.TextButton as MiuixTextButton
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.preference.RadioButtonLocation
import top.yukonga.miuix.kmp.preference.RadioButtonPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme as miuixColors

/** 特性页共用的顶栏与页面骨架。 */
@Composable
fun FeatureScaffold(
    title: String,
    onBack: () -> Unit,
    onRefresh: (() -> Unit)? = null,
    refreshEnabled: Boolean = true,
    content: @Composable (PaddingValues) -> Unit,
) {
    if (LocalUiMode.current.isMiuixFamily) {
        MiuixScaffold(
            topBar = {
                SmallTopAppBar(
                    title = title,
                    navigationIcon = {
                        MiuixIconButton(onClick = onBack) {
                            MiuixIcon(
                                imageVector = MiuixIcons.Back,
                                contentDescription = null,
                                tint = miuixColors.onBackground,
                            )
                        }
                    },
                    actions = {
                        if (onRefresh != null) {
                            MiuixIconButton(onClick = { if (refreshEnabled) onRefresh() }) {
                                MiuixIcon(
                                    imageVector = Icons.Filled.Refresh,
                                    contentDescription = null,
                                    tint = miuixColors.onBackground.copy(
                                        alpha = if (refreshEnabled) 1f else 0.38f,
                                    ),
                                )
                            }
                        }
                    },
                )
            },
            content = content,
        )
    } else {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(title) },
                    navigationIcon = { TopBarBackButton(onClick = onBack) },
                    actions = {
                        if (onRefresh != null) {
                            androidx.compose.material3.IconButton(
                                onClick = onRefresh,
                                enabled = refreshEnabled,
                            ) {
                                Icon(Icons.Filled.Refresh, contentDescription = null)
                            }
                        }
                    },
                )
            },
            content = content,
        )
    }
}

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

@Composable
fun FeatureRadioRow(
    title: String,
    summary: String?,
    selected: Boolean,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    if (LocalUiMode.current.isMiuixFamily) {
        RadioButtonPreference(
            title = title,
            summary = summary.orEmpty(),
            radioButtonLocation = RadioButtonLocation.End,
            selected = selected,
            onClick = { if (enabled) onClick() },
        )
    } else {
        SegmentedRadioItem(
            title = title,
            summary = summary,
            selected = selected,
            enabled = enabled,
            onClick = onClick,
        )
    }
}

@Composable
fun FeatureSwitchRow(
    title: String,
    summary: String?,
    checked: Boolean,
    enabled: Boolean = true,
    onCheckedChange: (Boolean) -> Unit,
) {
    if (LocalUiMode.current.isMiuixFamily) {
        SwitchPreference(
            title = title,
            summary = summary.orEmpty(),
            checked = checked,
            enabled = enabled,
            onCheckedChange = onCheckedChange,
        )
    } else {
        SegmentedSwitchItem(
            title = title,
            summary = summary,
            checked = checked,
            enabled = enabled,
            onCheckedChange = onCheckedChange,
        )
    }
}

/** 页面主操作，不使用带右箭头的导航行充当提交按钮。 */
@Composable
fun FeatureActionButton(
    text: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
) {
    if (LocalUiMode.current.isMiuixFamily) {
        MiuixTextButton(
            text = text,
            onClick = onClick,
            enabled = enabled,
            modifier = modifier.fillMaxWidth(),
            colors = ButtonDefaults.textButtonColorsPrimary(),
        )
    } else {
        Button(onClick = onClick, enabled = enabled, modifier = modifier.fillMaxWidth()) {
            Text(text)
        }
    }
}

@Composable
fun FeatureProgress(visible: Boolean, modifier: Modifier = Modifier) {
    if (!visible) return
    Box(
        modifier = modifier.fillMaxWidth().padding(vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        if (LocalUiMode.current.isMiuixFamily) {
            InfiniteProgressIndicator()
        } else {
            CircularProgressIndicator()
        }
    }
}

@Composable
fun FeatureValueRow(label: String, value: String, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FeatureText(text = label, muted = true, modifier = Modifier.weight(1f))
        FeatureText(text = value)
    }
}

@Composable
fun FeatureMessage(text: String, error: Boolean = false, modifier: Modifier = Modifier) {
    FeatureText(
        text = text,
        size = 13,
        color = if (LocalUiMode.current.isMiuixFamily) {
            if (error) miuixColors.error else miuixColors.primary
        } else if (error) {
            MaterialTheme.colorScheme.error
        } else {
            MaterialTheme.colorScheme.primary
        },
        modifier = modifier.padding(horizontal = 16.dp, vertical = 8.dp),
    )
}

@Composable
fun FeatureText(
    text: String,
    muted: Boolean = false,
    size: Int = 14,
    modifier: Modifier = Modifier,
    color: Color? = null,
) {
    if (LocalUiMode.current.isMiuixFamily) {
        MiuixText(
            text = text,
            fontSize = size.sp,
            color = color ?: if (muted) miuixColors.onSurfaceVariantSummary else miuixColors.onSurface,
            modifier = modifier,
        )
    } else {
        Text(
            text = text,
            fontSize = size.sp,
            color = color ?: if (muted) {
                MaterialTheme.colorScheme.onSurfaceVariant
            } else {
                MaterialTheme.colorScheme.onSurface
            },
            modifier = modifier,
        )
    }
}
