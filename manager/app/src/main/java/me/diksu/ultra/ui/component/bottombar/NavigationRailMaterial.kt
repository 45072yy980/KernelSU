package me.diksu.ultra.ui.component.bottombar

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuOpen
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.outlined.Extension
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.WideNavigationRail
import androidx.compose.material3.WideNavigationRailDefaults
import androidx.compose.material3.WideNavigationRailItem
import androidx.compose.material3.WideNavigationRailValue
import androidx.compose.material3.rememberWideNavigationRailState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import me.diksu.ultra.R
import me.diksu.ultra.data.repository.SettingsRepositoryImpl
import me.diksu.ultra.ui.capability.NavEntry
import me.diksu.ultra.ui.capability.NavGate
import me.diksu.ultra.ui.capability.rememberNavGates
import me.diksu.ultra.ui.LocalMainPagerState

@Composable
fun NavigationRailMaterial(
    navigationBadge: NavigationBadgeState,
    modifier: Modifier = Modifier,
) {
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

    val settingsRepo = remember { SettingsRepositoryImpl() }
    val state = rememberWideNavigationRailState(
        initialValue = if (settingsRepo.navigationRailExpanded) {
            WideNavigationRailValue.Expanded
        } else {
            WideNavigationRailValue.Collapsed
        },
    )
    val scope = rememberCoroutineScope()
    val expanded = state.targetValue == WideNavigationRailValue.Expanded
    LaunchedEffect(state.targetValue) {
        settingsRepo.navigationRailExpanded =
            state.targetValue == WideNavigationRailValue.Expanded
    }

    WideNavigationRail(
        modifier = modifier.fillMaxHeight(),
        state = state,
        colors = WideNavigationRailDefaults.colors().copy(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
            contentColor = MaterialTheme.colorScheme.onSurface,
        ),
        windowInsets = WindowInsets.systemBars.union(WindowInsets.displayCutout).only(
            WindowInsetsSides.Start + WindowInsetsSides.Vertical
        ),
        contentPadding = PaddingValues(vertical = 20.dp),
        header = {
            IconButton(
                modifier = Modifier.padding(start = 24.dp),
                onClick = {
                    scope.launch {
                        if (expanded) state.collapse() else state.expand()
                    }
                },
            ) {
                Icon(
                    if (expanded) Icons.AutoMirrored.Filled.MenuOpen else Icons.Filled.Menu,
                    contentDescription = stringResource(
                        if (expanded) R.string.nav_rail_collapse else R.string.nav_rail_expand
                    )
                )
            }
        },
    ) {
        items.forEachIndexed { index, (label, selectedIcon, unselectedIcon) ->
            val selected = mainPagerState.selectedPage == index
            val gate = gates[NavEntry.entries[index]] ?: NavGate.Open
            val itemAlpha = if (gate.available) 1f else NavGateDisabledAlpha
            WideNavigationRailItem(
                railExpanded = expanded,
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
                label = { Text(stringResource(label), modifier = Modifier.alpha(itemAlpha)) }
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
