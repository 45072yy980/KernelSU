package me.diksu.ultra.foxsu.ui.screen.nocturne

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import me.diksu.ultra.foxsu.data.model.Module
import me.diksu.ultra.foxsu.ui.component.bottombar.NavigationBadgeState
import me.diksu.ultra.foxsu.ui.screen.home.HomeActions
import me.diksu.ultra.foxsu.ui.screen.home.HomeUiState
import me.diksu.ultra.foxsu.ui.screen.settings.SettingsScreenActions
import me.diksu.ultra.foxsu.ui.screen.settings.SettingsUiState
import me.diksu.ultra.foxsu.ui.screen.superuser.GroupedApps

/**
 * Nocturne 各屏入口（签名与 Glass 各屏同构），实现分拆在同目录各文件中。
 */

@Composable
fun NocturneHome(
    state: HomeUiState,
    actions: HomeActions,
    bottomInnerPadding: Dp,
    navigationBadge: NavigationBadgeState,
    onOpenTab: (Int) -> Unit,
    onOpenAuthor: () -> Unit = {},
) = NocturneHomeScreen(state, actions, bottomInnerPadding, navigationBadge, onOpenTab, onOpenAuthor)

@Composable
fun NocturneSuperUserList(
    granted: List<GroupedApps>,
    notGranted: List<GroupedApps>,
    grantedCount: Int,
    searchValue: String,
    onSearch: (String) -> Unit,
    bottomInnerPadding: Dp,
    onOpenApp: (GroupedApps) -> Unit,
) = NocturneSuperUserScreen(granted, notGranted, grantedCount, searchValue, onSearch, bottomInnerPadding, onOpenApp)

@Composable
fun NocturneModuleList(
    modules: List<Module>,
    bottomInnerPadding: Dp,
    onToggle: (Module) -> Unit,
    onAddModule: () -> Unit,
    onRefresh: () -> Unit,
    onOpenRepo: () -> Unit,
    onExecute: (Module) -> Unit = {},
    onUninstall: (Module) -> Unit = {},
) = NocturneModuleScreen(
    modules, bottomInnerPadding, onToggle, onAddModule, onRefresh, onOpenRepo, onExecute, onUninstall,
)

@Composable
fun NocturneSettingsList(
    uiState: SettingsUiState,
    actions: SettingsScreenActions,
    bottomInnerPadding: Dp,
    onOpenCustomization: () -> Unit,
    onOpenSulog: () -> Unit,
) = NocturneSettingsScreen(uiState, actions, bottomInnerPadding, onOpenCustomization, onOpenSulog)

@Composable
fun NocturneAuthor(
    onBack: () -> Unit,
    onOpenUrl: (String) -> Unit,
) = NocturneAuthorScreen(onBack, onOpenUrl)

@Composable
fun NocturneTabBar(
    currentTab: Int,
    badge: NavigationBadgeState,
    onOpenTab: (Int) -> Unit,
    modifier: Modifier = Modifier,
) = NocturneTabBarScreen(currentTab, badge, onOpenTab, modifier)
