package me.diksu.ultra.ui.component.bottombar

import androidx.annotation.StringRes
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Cottage
import androidx.compose.material.icons.rounded.Extension
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import me.diksu.ultra.R
import me.diksu.ultra.ui.capability.NavEntry
import me.diksu.ultra.ui.capability.NavGate
import me.diksu.ultra.ui.capability.rememberNavGates
import me.diksu.ultra.ui.LocalMainPagerState
import me.diksu.ultra.ui.component.FloatingBottomBar
import me.diksu.ultra.ui.component.FloatingBottomBarItem
import me.diksu.ultra.ui.theme.LocalEnableFloatingBottomBar
import me.diksu.ultra.ui.theme.LocalEnableFloatingBottomBarBlur
import me.diksu.ultra.ui.util.BlurredBar
import top.yukonga.miuix.kmp.basic.Badge
import top.yukonga.miuix.kmp.basic.BadgedBox
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.NavigationBar
import top.yukonga.miuix.kmp.basic.NavigationBarItem
import top.yukonga.miuix.kmp.basic.NavigationItem
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.blur.Backdrop
import top.yukonga.miuix.kmp.blur.LayerBackdrop
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun BottomBarMiuix(
    blurBackdrop: LayerBackdrop?,
    backdrop: Backdrop,
    navigationBadge: NavigationBadgeState,
    modifier: Modifier,
) {
    val mainState = LocalMainPagerState.current
    // 4.2：逐入口判定。不可用的条目只置灰自己，其余条目照常点击；整条导航不再降级。
    val gates = rememberNavGates()
    var pendingGate by remember { mutableStateOf<NavGate?>(null) }
    val destinations = BottomBarDestination.entries

    val enableFloatingBottomBar = LocalEnableFloatingBottomBar.current
    val enableFloatingBottomBarBlur = LocalEnableFloatingBottomBarBlur.current

    val items = destinations.map { destination ->
        NavigationItem(
            label = stringResource(destination.label),
            icon = destination.icon,
        )
    }
    if (!enableFloatingBottomBar) {
        BlurredBar(blurBackdrop) {
            NavigationBar(
                modifier = modifier,
                color = if (blurBackdrop != null) Color.Transparent else MiuixTheme.colorScheme.surface,
                content = {
                    items.forEachIndexed { index, item ->
                        val gate = gates[destinations[index].navEntry] ?: NavGate.Open
                        NavigationBarItem(
                            modifier = Modifier
                                .weight(1f)
                                .alpha(if (gate.available) 1f else NavGateDisabledAlpha),
                            icon = item.icon,
                            label = item.label,
                            selected = mainState.selectedPage == index,
                            onClick = {
                                if (gate.available) {
                                    mainState.animateToPage(index)
                                } else {
                                    pendingGate = gate
                                }
                            },
                            badge = navigationBadgeFor(index, navigationBadge),
                        )
                    }
                }
            )
        }
    } else {
        val bottomPadding = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
            .let { inset -> if (inset != 0.dp) 8.dp + inset else 28.dp }
        FloatingBottomBar(
            modifier = modifier
                .pointerInput(Unit) {
                    detectTapGestures { }
                }
                .padding(start = 28.dp, end = 28.dp, bottom = bottomPadding),
            selectedIndex = mainState.selectedPage,
            // 悬浮栏支持横向拖动切换，拖动结束会直接回调这里；逐项判定不能只挂在 onClick 上。
            onSelected = { index ->
                val gate = gates[destinations[index].navEntry] ?: NavGate.Open
                if (gate.available) {
                    mainState.animateToPage(index)
                } else {
                    pendingGate = gate
                }
            },
            backdrop = backdrop,
            tabsCount = items.size,
            isBlurEnabled = enableFloatingBottomBarBlur,
        ) { activateTab ->
            items.forEachIndexed { index, item ->
                val gate = gates[destinations[index].navEntry] ?: NavGate.Open
                FloatingBottomBarItem(
                    selected = mainState.selectedPage == index,
                    onClick = {
                        if (gate.available) {
                            activateTab(index)
                        } else {
                            pendingGate = gate
                        }
                    },
                    modifier = Modifier
                        .defaultMinSize(minWidth = 76.dp)
                        .alpha(if (gate.available) 1f else NavGateDisabledAlpha),
                ) {
                    // Icon and label take LocalContentColor so the FloatingBottomBar backdrop copy
                    // can recolor them to the accent tone inside the indicator pill.
                    val badge = navigationBadgeFor(index, navigationBadge, floating = true)
                    val icon: @Composable () -> Unit = {
                        Icon(
                            imageVector = item.icon,
                            contentDescription = item.label,
                        )
                    }
                    if (badge != null) {
                        BadgedBox(badge = { badge() }) { icon() }
                    } else {
                        icon()
                    }
                    Text(
                        text = item.label,
                        fontSize = 11.sp,
                        lineHeight = 14.sp,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Visible
                    )
                }
            }
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

enum class BottomBarDestination(
    @get:StringRes val label: Int,
    val icon: ImageVector,
) {
    Home(R.string.home, Icons.Rounded.Cottage),
    SuperUser(R.string.superuser, Icons.Rounded.Security),
    Module(R.string.module, Icons.Rounded.Extension),
    Setting(R.string.settings, Icons.Rounded.Settings)
}

/** 底栏入口与导航门禁入口一一对应；两者顺序都与 Pager 页序一致。 */
internal val BottomBarDestination.navEntry: NavEntry
    get() = when (this) {
        BottomBarDestination.Home -> NavEntry.Home
        BottomBarDestination.SuperUser -> NavEntry.SuperUser
        BottomBarDestination.Module -> NavEntry.Module
        BottomBarDestination.Setting -> NavEntry.Setting
    }

internal fun navigationBadgeFor(
    index: Int,
    state: NavigationBadgeState,
    floating: Boolean = false,
): (@Composable () -> Unit)? {
    val badge = badgeFor(index, state) ?: return null
    return when (badge.tone) {
        BadgeTone.Alert -> {
            {
                Badge {
                    Text(badge.count.toString())
                }
            }
        }

        BadgeTone.Accent -> {
            {
                Badge(
                    containerColor = if (floating) MiuixTheme.colorScheme.primaryContainer else MiuixTheme.colorScheme.primary,
                    contentColor = if (floating) MiuixTheme.colorScheme.onPrimaryContainer else MiuixTheme.colorScheme.onPrimary,
                ) {
                    Text(badge.count.toString())
                }
            }
        }
    }
}
