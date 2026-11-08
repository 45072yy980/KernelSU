package me.diksu.ultra.ui.component.profile.dialogs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import me.diksu.ultra.ui.component.material.SegmentedCheckboxItem
import me.diksu.ultra.ui.component.material.SegmentedColumn
import top.yukonga.miuix.kmp.nav.gesture.WindowNavigationEventBridge

@Composable
fun <T> MultiSelectDialog(
    title: String,
    subtitle: String? = null,
    items: List<T>,
    selectedItems: Set<T>,
    itemTitle: (T) -> String,
    itemSubtitle: (T) -> String?,
    maxSelection: Int = Int.MAX_VALUE,
    onSelectionChange: (Set<T>) -> Unit,
    onDismiss: () -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    // 勾选先落在草稿上，按「应用」才回写；否则 Material 下每勾一下就落盘，
    // 关掉面板也撤不回来，和 miuix 皮肤的行为正好相反。
    var draft by remember(selectedItems) { mutableStateOf(selectedItems) }
    val filteredItems = remember(items, searchQuery) {
        if (searchQuery.isBlank()) {
            items
        } else {
            items.filter { item ->
                itemTitle(item).contains(searchQuery, ignoreCase = true) ||
                        itemSubtitle(item)?.contains(searchQuery, ignoreCase = true) == true
            }
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberBottomSheetState(initialValue = SheetValue.Hidden, enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded))
    ) {
        WindowNavigationEventBridge()
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleLarge
                    )
                    if (subtitle != null) {
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Filled.Close, contentDescription = null)
                }
            }

            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier.fillMaxWidth(),
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(Icons.Filled.Close, contentDescription = null)
                        }
                    }
                },
                singleLine = true
            )

            SegmentedColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false)
                    .verticalScroll(rememberScrollState()),
                content = filteredItems.map { item ->
                    {
                        SegmentedCheckboxItem(
                            title = itemTitle(item),
                            summary = itemSubtitle(item),
                            colors = ListItemDefaults.segmentedColors().copy(
                                containerColor = MaterialTheme.colorScheme.surface,
                                contentColor = Color.Unspecified
                            ),
                            checked = draft.contains(item),
                            onCheckedChange = { isChecked ->
                                val newSelection = draft.toMutableSet()
                                if (isChecked && newSelection.size < maxSelection) {
                                    newSelection.add(item)
                                } else if (!isChecked) {
                                    newSelection.remove(item)
                                }
                                draft = newSelection
                            }
                        )
                    }
                }
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = if (maxSelection == Int.MAX_VALUE) {
                        "${draft.size} 项"
                    } else {
                        "${draft.size} / $maxSelection"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = onDismiss) {
                    Text(stringResource(android.R.string.cancel))
                }
                Spacer(Modifier.width(8.dp))
                TextButton(
                    onClick = {
                        onSelectionChange(draft)
                        onDismiss()
                    },
                    enabled = draft != selectedItems,
                ) {
                    Text("应用")
                }
            }
        }
    }
}
