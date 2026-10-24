package me.diksu.ultra.ui.component.bottombar

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.stringResource
import me.diksu.ultra.R
import me.diksu.ultra.data.repository.SettingsRepositoryImpl
import me.diksu.ultra.ui.capability.NavGate
import me.diksu.ultra.ui.capability.rememberNavGates
import me.diksu.ultra.ui.LocalMainPagerState
import top.yukonga.miuix.kmp.basic.NavigationRail
import top.yukonga.miuix.kmp.basic.NavigationRailItem
import top.yukonga.miuix.kmp.basic.NavigationRailValue
import top.yukonga.miuix.kmp.basic.rememberNavigationRailState
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun NavigationRailMiuixStock(
    navigationBadge: NavigationBadgeState,
    modifier: Modifier = Modifier,
) {
    val mainState = LocalMainPagerState.current
    // 4.2：逐入口判定。不可用的条目只置灰自己，其余条目照常点击；整条导航不再降级。
    val gates = rememberNavGates()
    var pendingGate by remember { mutableStateOf<NavGate?>(null) }
    val destinations = BottomBarDestination.entries

    val items = destinations.map { destination ->
        Pair(stringResource(destination.label), destination.icon)
    }
    val settingsRepo = remember { SettingsRepositoryImpl() }
    val state = rememberNavigationRailState(
        initialValue = if (settingsRepo.navigationRailExpanded) {
            NavigationRailValue.Expanded
        } else {
            NavigationRailValue.Collapsed
        },
    )
    LaunchedEffect(state.currentValue) {
        settingsRepo.navigationRailExpanded = state.isExpanded
    }

    NavigationRail(
        modifier = modifier,
        state = state,
        color = MiuixTheme.colorScheme.surface,
        expandContentDescription = stringResource(R.string.nav_rail_expand),
        collapseContentDescription = stringResource(R.string.nav_rail_collapse),
    ) {
        items.forEachIndexed { index, (label, icon) ->
            val gate = gates[destinations[index].navEntry] ?: NavGate.Open
            NavigationRailItem(
                modifier = Modifier.alpha(if (gate.available) 1f else NavGateDisabledAlpha),
                selected = mainState.selectedPage == index,
                onClick = {
                    if (gate.available) {
                        mainState.animateToPage(index)
                    } else {
                        pendingGate = gate
                    }
                },
                icon = icon,
                label = label,
                badge = navigationBadgeFor(index, navigationBadge),
            )
        }
    }

    NavGateDialog(
        gate = pendingGate,
        onDismiss = { pendingGate = null },
        onGoHome = {
            pendingGate = null
            mainState.animateToPage(0)
        },
    )
}
