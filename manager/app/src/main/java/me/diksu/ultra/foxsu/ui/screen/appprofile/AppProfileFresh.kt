package me.diksu.ultra.foxsu.ui.screen.appprofile

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import me.diksu.ultra.R
import me.diksu.ultra.foxsu.ui.component.freshCardBorder
import me.diksu.ultra.foxsu.ui.component.freshCardColor
import me.diksu.ultra.foxsu.ui.component.statustag.StatusTag
import me.diksu.ultra.foxsu.ui.component.supportingTextAlpha
import me.diksu.ultra.foxsu.ui.viewmodel.SuperUserViewModel

private val FreshCardShape = RoundedCornerShape(20.dp)

/** Big info card at the top of the App Profile page (ColorUI style). */
@Composable
fun FreshAppHeader(
    appLabel: String,
    packageName: String,
    appIcon: @Composable () -> Unit,
    appUid: Int,
    sharedUserId: String,
    appVersionName: String,
    appVersionCode: Long,
    isUidGroup: Boolean,
    isSpecialApp: Boolean,
    affectedApps: List<SuperUserViewModel.AppInfo>,
    isRootGranted: Boolean,
    onRootChange: (Boolean) -> Unit,
    mode: Mode,
) {
    val userId = appUid / 100000
    val appId = appUid % 100000
    val cardColor = freshCardColor()
    val border = freshCardBorder()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .padding(top = 18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(FreshCardShape)
                .background(cardColor)
                .then(border?.let { Modifier.border(it, FreshCardShape) } ?: Modifier)
                .padding(16.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                appIcon()
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = appLabel,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    val sub = when {
                        isSpecialApp -> packageName
                        !isUidGroup -> if (appVersionName.isNotEmpty()) "$appVersionName ($appVersionCode) · $packageName" else packageName
                        sharedUserId.isNotEmpty() -> sharedUserId
                        else -> stringResource(R.string.fx_group_contains_apps, affectedApps.size)
                    }
                    Text(
                        text = sub,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = supportingTextAlpha()),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    if (userId != 0) {
                        StatusTag(label = "USER $userId", contentColor = MaterialTheme.colorScheme.onTertiaryContainer, backgroundColor = MaterialTheme.colorScheme.tertiaryContainer)
                        StatusTag(label = "UID $appId", contentColor = MaterialTheme.colorScheme.onTertiaryContainer, backgroundColor = MaterialTheme.colorScheme.tertiaryContainer)
                    } else {
                        StatusTag(label = "UID $appUid", contentColor = MaterialTheme.colorScheme.onTertiaryContainer, backgroundColor = MaterialTheme.colorScheme.tertiaryContainer)
                    }
                }
            }

            if (!isSpecialApp) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.fx_superuser),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = mode.text,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = supportingTextAlpha())
                        )
                    }
                    Switch(checked = isRootGranted, onCheckedChange = onRootChange)
                }
            }
        }
    }
}

/** Card-style three-option selector: 默认 / 模板 / 自定义 (ColorUI style). */
@Composable
fun FreshModeSelector(
    mode: Mode,
    hasTemplate: Boolean,
    onModeChange: (Mode) -> Unit,
) {
    Column(modifier = Modifier.padding(top = 4.dp)) {
        Text(
            text = stringResource(R.string.fx_fresh_profile_mode_title),
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
            modifier = Modifier.padding(start = 6.dp, bottom = 8.dp),
        )
    }
    val cardColor = freshCardColor()
    val border = freshCardBorder()
    val accent = MaterialTheme.colorScheme.primary

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .clip(FreshCardShape)
            .background(cardColor)
            .then(border?.let { Modifier.border(it, FreshCardShape) } ?: Modifier)
            .padding(6.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        val options = listOf(
            Mode.Default to stringResource(R.string.fx_profile_default),
            Mode.Template to stringResource(R.string.fx_profile_template),
            Mode.Custom to stringResource(R.string.fx_profile_custom),
        )
        options.forEach { (m, label) ->
            val selected = mode == m
            val enabled = m != Mode.Template || hasTemplate
            val bg: Color = if (selected) accent.copy(alpha = 0.14f) else Color.Transparent
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(bg)
                    .clickable(enabled = enabled) { onModeChange(m) }
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (!enabled) {
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    } else if (selected) {
                        accent
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (!enabled) {
                    Text(
                        text = "—",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    )
                } else if (selected) {
                    FreshDot()
                }
            }
        }
    }
}

@Composable
private fun FreshDot() {
    androidx.compose.foundation.layout.Box(
        modifier = Modifier
            .size(8.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(MaterialTheme.colorScheme.primary)
    )
}
