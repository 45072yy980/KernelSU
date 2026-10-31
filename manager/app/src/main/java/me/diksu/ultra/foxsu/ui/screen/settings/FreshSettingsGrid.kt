package me.diksu.ultra.foxsu.ui.screen.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ColorLens
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.Usb
import androidx.compose.material.icons.filled.Wallpaper
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import me.diksu.ultra.R
import me.diksu.ultra.foxsu.ui.component.freshCardBorder
import me.diksu.ultra.foxsu.ui.UiMode
import me.diksu.ultra.foxsu.ui.component.freshCardColor

private class GridAction(
    val icon: ImageVector,
    val labelRes: Int,
    val onClick: () -> Unit,
)

/**
 * 3 x 3 quick-action grid shown at the top of the ColorUI settings page. It is a shortcut layer
 * only — the detailed switch list stays underneath, so nothing is lost.
 */
@Composable
fun FreshSettingsGrid(
    checkUpdate: Boolean,
    onOpenUiMode: () -> Unit,
    onOpenTheme: () -> Unit,
    onOpenCustomization: () -> Unit,
    onOpenProfile: () -> Unit,
    onOpenRepo: () -> Unit,
    onOpenLog: () -> Unit,
    onOpenInstall: () -> Unit,
    onOpenAbout: () -> Unit,
    onToggleCheckUpdate: () -> Unit,
) {
    val actions = listOf(
        GridAction(Icons.Filled.Wallpaper, R.string.fx_settings_grid_ui_mode, onOpenUiMode),
        GridAction(Icons.Filled.ColorLens, R.string.fx_settings_grid_custom, onOpenCustomization),
        GridAction(Icons.Filled.Description, R.string.fx_settings_grid_profile, onOpenProfile),
        GridAction(Icons.Filled.Extension, R.string.fx_settings_grid_repo, onOpenRepo),
        GridAction(Icons.Filled.Terminal, R.string.fx_settings_grid_log, onOpenLog),
        GridAction(Icons.Filled.Usb, R.string.fx_settings_grid_flash, onOpenInstall),
        GridAction(Icons.Filled.Info, R.string.fx_settings_grid_about, onOpenAbout),
        GridAction(Icons.Filled.SystemUpdate, R.string.fx_settings_grid_update, onToggleCheckUpdate),
    )

    Column(modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 14.dp)) {
        Text(
            text = stringResource(R.string.fx_settings_grid),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 4.dp, bottom = 10.dp),
        )

        actions.chunked(3).forEachIndexed { rowIndex, row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                row.forEach { action ->
                    val isUpdateTile = action.labelRes == R.string.fx_settings_grid_update
                    GridCell(
                        icon = action.icon,
                        label = stringResource(action.labelRes),
                        caption = if (isUpdateTile) {
                            stringResource(
                                if (checkUpdate) R.string.fx_settings_grid_on else R.string.fx_settings_grid_off
                            )
                        } else {
                            null
                        },
                        highlighted = isUpdateTile && checkUpdate,
                        onClick = action.onClick,
                        modifier = Modifier.weight(1f),
                    )
                }
                repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
            }
            if (rowIndex != 2) Spacer(Modifier.height(10.dp))
        }
    }
}

/** Small dialog for picking one of the three UI styles from the grid. */
@Composable
fun FreshUiModePickerDialog(
    currentValue: String,
    onSelectIndex: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(28.dp), color = freshCardColor()) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(
                    text = stringResource(R.string.fx_settings_ui_mode),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.height(12.dp))
                UiMode.entries.forEachIndexed { index, mode ->
                    val selected = mode.value == currentValue
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .clickable {
                                onSelectIndex(index)
                                onDismiss()
                            }
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = stringResource(modeLabelRes(mode)),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f),
                        )
                        if (selected) {
                            Icon(
                                imageVector = Icons.Filled.Check,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .clickable(onClick = onDismiss),
                ) {
                    Box(
                        modifier = Modifier.padding(vertical = 12.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = stringResource(R.string.fx_fresh_home_cancel),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

private fun modeLabelRes(mode: UiMode): Int = when (mode) {
    UiMode.Miuix -> R.string.fx_settings_ui_mode_miuix
    UiMode.Material -> R.string.fx_settings_ui_mode_material
    UiMode.Fresh -> R.string.fx_settings_ui_mode_fresh
    UiMode.Glass -> R.string.fx_settings_ui_mode_glass
    UiMode.Nocturne -> R.string.fx_settings_ui_mode_glass
}

@Composable
private fun GridCell(
    icon: ImageVector,
    label: String,
    caption: String?,
    highlighted: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val accent = MaterialTheme.colorScheme.primary
    Surface(
        modifier = modifier
            .height(92.dp)
            .clip(RoundedCornerShape(20.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(20.dp),
        color = freshCardColor(),
        contentColor = MaterialTheme.colorScheme.onSurface,
        border = freshCardBorder(),
        shadowElevation = 0.dp,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 6.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Surface(
                    shape = CircleShape,
                    color = accent.copy(alpha = if (highlighted) 0.18f else 0.10f),
                    contentColor = accent,
                    modifier = Modifier.size(36.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(imageVector = icon, contentDescription = null, modifier = Modifier.size(19.dp))
                    }
                }
            }
            Spacer(Modifier.height(7.dp))
            Text(
                text = label,
                fontSize = 12.sp,
                lineHeight = 15.sp,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (caption != null) {
                Text(
                    text = caption,
                    fontSize = 10.sp,
                    lineHeight = 13.sp,
                    color = if (highlighted) accent else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
        }
    }
}
