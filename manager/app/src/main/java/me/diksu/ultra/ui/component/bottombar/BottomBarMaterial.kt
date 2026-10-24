package me.diksu.ultra.ui.component.bottombar

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.outlined.Extension
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ShortNavigationBar
import androidx.compose.material3.ShortNavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import me.diksu.ultra.R
import me.diksu.ultra.ui.capability.NavEntry
import me.diksu.ultra.ui.capability.NavGate
import me.diksu.ultra.ui.capability.rememberNavGates
import me.diksu.ultra.ui.LocalMainPagerState

@Composable
fun BottomBarMaterial(navigationBadge: NavigationBadgeState) {
    val mainPagerState = LocalMainPagerState.current
    // 4.2：逐入口判定。不可用的条目只置灰自己，其余条目照常点击；整条导航不再降级。
    val gates = rememberNavGates()
    var pendingGate by remember { mutableStateOf<NavGate?>(null) }

    // 顺序与 NavEntry / Pager 页序一致：Home、SuperUser、Module、Setting。
    val items = listOf(
        Triple(R.string.home, Icons.Filled.Home, Icons.Outlined.Home),
        Triple(R.string.superuser, Icons.Filled.Shield, Icons.Outlined.Shield),
        Triple(R.string.module, Icons.Filled.Extension, Icons.Outlined.Extension),
        Triple(R.string.settings, Icons.Filled.Settings, Icons.Outlined.Settings)
    )

    ShortNavigationBar(
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        windowInsets = WindowInsets.systemBars.union(WindowInsets.displayCutout).only(
            WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom
        )
    ) {
        items.forEachIndexed { index, (label, selectedIcon, unselectedIcon) ->
            val selected = mainPagerState.selectedPage == index
            val gate = gates[NavEntry.entries[index]] ?: NavGate.Open
            val itemAlpha = if (gate.available) 1f else NavGateDisabledAlpha
            ShortNavigationBarItem(
                selected = selected,
                onClick = {
                    if (!gate.available) {
                        pendingGate = gate
                    } else if (!selected) {
                        mainPagerState.animateToPage(index)
                    }
                },
                icon = {
                    NavigationIconWithBadge(
                        icon = if (selected) selectedIcon else unselectedIcon,
                        contentDescription = stringResource(label),
                        badge = badgeFor(index, navigationBadge),
                        modifier = Modifier.alpha(itemAlpha),
                    )
                },
                label = {
                    Text(
                        stringResource(label),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.alpha(itemAlpha),
                    )
                }
            )
        }
    }

    NavGateDialog(
        gate = pendingGate,
        onDismiss = { pendingGate = null },
        onGoHome = {
            pendingGate = null
            mainPagerState.animateToPage(0)
        },
    )
}

@Composable
internal fun NavigationIconWithBadge(
    icon: ImageVector,
    contentDescription: String?,
    badge: NavBadge?,
    modifier: Modifier = Modifier,
) {
    if (badge != null) {
        BadgedBox(
            modifier = modifier,
            badge = {
                when (badge.tone) {
                    BadgeTone.Alert -> Badge {
                        Text(badge.count.toString())
                    }

                    BadgeTone.Accent -> Badge(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                    ) {
                        Text(badge.count.toString())
                    }
                }
            }
        ) {
            Icon(icon, contentDescription)
        }
    } else {
        Icon(icon, contentDescription, modifier = modifier)
    }
}
