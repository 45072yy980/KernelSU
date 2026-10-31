package me.diksu.ultra.foxsu.ui.screen.superuser

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.compose.ui.unit.Dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import me.diksu.ultra.foxsu.ui.component.SearchStatus
import me.diksu.ultra.foxsu.ui.LocalUiMode
import me.diksu.ultra.foxsu.ui.UiMode
import me.diksu.ultra.foxsu.ui.navigation3.Navigator
import me.diksu.ultra.foxsu.ui.navigation3.Route
import me.diksu.ultra.foxsu.ui.viewmodel.SuperUserViewModel

@Composable
fun SuperUserPager(
    navigator: Navigator,
    bottomInnerPadding: Dp,
    isCurrentPage: Boolean = true,
) {
    val viewModel = viewModel<SuperUserViewModel>()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val latestIsCurrentPage by rememberUpdatedState(isCurrentPage)
    val initialResumeHandled = rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(isCurrentPage) {
        if (isCurrentPage) {
            val state = viewModel.uiState.value
            if (!state.hasLoaded && !state.isRefreshing) {
                viewModel.initializePreferences()
                viewModel.loadAppList()
            }
        } else if (!uiState.searchStatus.isCollapsed()) {
            viewModel.updateSearchStatus(uiState.searchStatus.copy(searchText = "", current = SearchStatus.Status.COLLAPSED))
        }
    }

    LifecycleResumeEffect(Unit) {
        if (initialResumeHandled.value && latestIsCurrentPage) {
            val state = viewModel.uiState.value
            if (!state.isRefreshing) {
                if (!state.hasLoaded) {
                    viewModel.initializePreferences()
                    viewModel.loadAppList()
                } else if (viewModel.isNeedRefresh) {
                    viewModel.loadAppList(resort = false)
                }
            }
        }
        initialResumeHandled.value = true
        onPauseOrDispose {}
    }

    val onSearchTextChange: (String) -> Unit = viewModel::updateSearchText
    val onToggleShowSystemApps: () -> Unit = {
        viewModel.toggleShowSystemApps()
    }
    val onToggleShowOnlyPrimaryUserApps: () -> Unit = {
        viewModel.toggleShowOnlyPrimaryUserApps()
    }
    val onOpenProfile: (GroupedApps) -> Unit = { group ->
        navigator.push(Route.AppProfile(group.uid))
        viewModel.markNeedRefresh()
    }
    val actions = SuperUserActions(
        onRefresh = { viewModel.loadAppList(force = true) },
        onOpenSulog = { navigator.push(Route.Sulog) },
        onSearchTextChange = onSearchTextChange,
        onSearchStatusChange = viewModel::updateSearchStatus,
        onClearSearch = { onSearchTextChange("") },
        onToggleShowSystemApps = onToggleShowSystemApps,
        onToggleShowOnlyPrimaryUserApps = onToggleShowOnlyPrimaryUserApps,
        onUpdateSortConfig = { viewModel.updateSortConfig(it) },
        onOpenProfile = onOpenProfile,
    )

    when (LocalUiMode.current) {
        UiMode.Miuix -> me.diksu.ultra.foxsu.ui.screen.ag.AgSuperUser(
            uiState = uiState,
            actions = actions,
            bottomInnerPadding = bottomInnerPadding,
        )

        UiMode.Fresh -> SuperUserPagerMaterial(
            uiState = uiState,
            actions = actions,
            bottomInnerPadding = bottomInnerPadding,
        )

        UiMode.Material -> {
            val apps =
                if (uiState.searchStatus.searchText.isBlank()) uiState.groupedApps else uiState.searchResults
            me.diksu.ultra.foxsu.ui.screen.editorial.EditorialSuperUser(
                granted = apps.filter { it.anyAllowSu },
                notGranted = apps.filterNot { it.anyAllowSu },
                grantedCount = apps.count { it.anyAllowSu },
                searchValue = uiState.searchStatus.searchText,
                onSearch = actions.onSearchTextChange,
                bottomInnerPadding = bottomInnerPadding,
                onOpenApp = actions.onOpenProfile,
            )
        }

        UiMode.Glass -> {
            val apps =
                if (uiState.searchStatus.searchText.isBlank()) uiState.groupedApps else uiState.searchResults
            me.diksu.ultra.foxsu.ui.screen.glass.GlassSuperUserList(
                granted = apps.filter { it.anyAllowSu },
                notGranted = apps.filterNot { it.anyAllowSu },
                grantedCount = apps.count { it.anyAllowSu },
                searchValue = uiState.searchStatus.searchText,
                onSearch = actions.onSearchTextChange,
                bottomInnerPadding = bottomInnerPadding,
                onOpenApp = actions.onOpenProfile,
            )
        }

        UiMode.Nocturne -> {
            val apps =
                if (uiState.searchStatus.searchText.isBlank()) uiState.groupedApps else uiState.searchResults
            me.diksu.ultra.foxsu.ui.screen.nocturne.NocturneSuperUserList(
                granted = apps.filter { it.anyAllowSu },
                notGranted = apps.filterNot { it.anyAllowSu },
                grantedCount = apps.count { it.anyAllowSu },
                searchValue = uiState.searchStatus.searchText,
                onSearch = actions.onSearchTextChange,
                bottomInnerPadding = bottomInnerPadding,
                onOpenApp = actions.onOpenProfile,
            )
        }
    }
}
