package me.diksu.ultra.foxsu.ui.component

import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import me.diksu.ultra.R
import me.diksu.ultra.foxsu.data.repository.SettingsRepositoryImpl
import me.diksu.ultra.foxsu.ui.UiMode
import me.diksu.ultra.foxsu.ui.theme.ColorMode

/**
 * 顶栏的界面风格入口：点开是一张列表，直接选 Miuix / Material / ColorUI，不需要来回循环。
 */
@Composable
fun UiModeSwitchButton(modifier: Modifier = Modifier) {
    var expanded by remember { mutableStateOf(false) }

    IconButton(
        modifier = modifier,
        onClick = { expanded = true },
    ) {
        Icon(
            imageVector = Icons.Filled.Palette,
            contentDescription = stringResource(R.string.fx_settings_ui_mode),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(22.dp),
        )
    }

    if (expanded) {
        StylePickerDialog(
            current = UiMode.fromValue(remember { SettingsRepositoryImpl() }.uiMode),
            onDismiss = { expanded = false },
        )
    }
}

/**
 * 与设置页的切换保持一致：Miuix/Material 之间的 Monet 状态需要同步，否则会出现配色错乱。
 */
internal fun applyUiMode(next: UiMode) {
    val repo = SettingsRepositoryImpl()
    val old = UiMode.fromValue(repo.uiMode)
    if (old == next) return
    val currentThemeMode = repo.themeMode

    val newThemeMode = when {
        old == UiMode.Material && next == UiMode.Miuix -> {
            val colorMode = ColorMode.fromValue(currentThemeMode)
            val baseMode = if (colorMode == ColorMode.DARK_AMOLED) 2 else currentThemeMode
            if (repo.miuixMonet && !colorMode.isMonet) {
                ColorMode.fromValue(baseMode).toMonetMode()
            } else if (!repo.miuixMonet && colorMode.isMonet) {
                ColorMode.fromValue(baseMode).toNonMonetMode()
            } else baseMode
        }

        old == UiMode.Miuix && next == UiMode.Material -> {
            val colorMode = ColorMode.fromValue(currentThemeMode)
            if (colorMode.isMonet) colorMode.toNonMonetMode() else currentThemeMode
        }

        else -> currentThemeMode
    }

    repo.uiMode = next.value
    if (newThemeMode != currentThemeMode) repo.themeMode = newThemeMode
}
