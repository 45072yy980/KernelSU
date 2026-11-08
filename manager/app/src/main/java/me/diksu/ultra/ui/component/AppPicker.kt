package me.diksu.ultra.ui.component

import android.content.pm.ApplicationInfo
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import me.diksu.ultra.ui.LocalUiMode
import me.diksu.ultra.ui.component.material.SegmentedListItem
import me.diksu.ultra.ui.isMiuixFamily
import top.yukonga.miuix.kmp.basic.Icon as MiuixIcon
import top.yukonga.miuix.kmp.basic.InputField
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.basic.Search
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme

/** 一个可选中的应用：界面展示用 label，回写内核用 uid。 */
data class PickedApp(
    val packageName: String,
    val label: String,
    val uid: Int,
    val info: ApplicationInfo,
)

/**
 * 应用选择行：图标 + 名称 + 包名 + 右侧开关。
 *
 * 之前这两页只写了纯文字 + uid，跟应用里其它列表（超级用户、模块）完全不是一个观感，
 * 也认不出是哪个应用。这里统一走 `AppIconImage` + 本家组件。
 */
@Composable
fun AppToggleRow(
    app: PickedApp,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean = true,
) {
    if (LocalUiMode.current.isMiuixFamily) {
        SwitchPreference(
            title = app.label,
            summary = app.packageName,
            startAction = {
                AppIconImage(
                    modifier = Modifier.padding(end = 10.dp).size(36.dp),
                    applicationInfo = app.info,
                    label = app.label,
                )
            },
            checked = checked,
            enabled = enabled,
            onCheckedChange = onCheckedChange,
        )
    } else {
        SegmentedListItem(
            checked = checked,
            onCheckedChange = onCheckedChange,
            enabled = enabled,
            leadingContent = {
                AppIconImage(
                    modifier = Modifier.size(40.dp),
                    applicationInfo = app.info,
                    label = app.label,
                )
            },
            headlineContent = { Text(app.label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
            supportingContent = { Text(app.packageName, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        )
    }
}

/** 应用列表的搜索框：miuix 用原生 InputField，Material 用 OutlinedTextField。 */
@Composable
fun AppSearchField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
) {
    if (LocalUiMode.current.isMiuixFamily) {
        InputField(
            query = value,
            onQueryChange = onValueChange,
            label = label,
            leadingIcon = {
                MiuixIcon(
                    imageVector = MiuixIcons.Basic.Search,
                    contentDescription = null,
                    tint = colorScheme.onSurfaceContainerHigh,
                    modifier = Modifier.padding(start = 16.dp, end = 8.dp).size(22.dp),
                )
            },
            modifier = modifier,
            onSearch = { },
            expanded = false,
            onExpandedChange = { },
        )
    } else {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = modifier,
            singleLine = true,
            placeholder = { Text(label) },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
        )
    }
}
