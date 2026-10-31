package me.diksu.ultra.foxsu.ui.screen.superuser

import me.diksu.ultra.foxsu.ui.component.bottombar.useSideDock
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.CheckableDropdownMenuItem
import androidx.compose.material3.DropdownMenuGroup
import androidx.compose.material3.DropdownMenuPopup
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.SelectableDropdownMenuItem
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import me.diksu.ultra.R
import me.diksu.ultra.foxsu.data.settings.CustomizationStore
import me.diksu.ultra.foxsu.data.model.AppInfo
import me.diksu.ultra.foxsu.ui.component.AppIconImage
import me.diksu.ultra.foxsu.ui.component.ScrollToTopOnChange
import me.diksu.ultra.foxsu.ui.component.material.ExpressiveScaffold
import me.diksu.ultra.foxsu.ui.component.material.SearchAppBar
import me.diksu.ultra.foxsu.ui.component.material.SegmentedColumn
import me.diksu.ultra.foxsu.ui.component.material.SegmentedItem
import me.diksu.ultra.foxsu.ui.component.material.SegmentedListItem
import me.diksu.ultra.foxsu.ui.component.statustag.StatusTag
import me.diksu.ultra.foxsu.ui.component.cardContentColor
import me.diksu.ultra.foxsu.ui.component.isNightScheme
import me.diksu.ultra.foxsu.ui.component.freshCardColor
import me.diksu.ultra.foxsu.ui.component.scaffoldBackgroundColor
import me.diksu.ultra.foxsu.ui.theme.UiPalette
import me.diksu.ultra.foxsu.ui.theme.isFreshStyle
import me.diksu.ultra.foxsu.ui.util.ownerNameForUid
import me.diksu.ultra.foxsu.ui.viewmodel.AppSortType

@Composable
fun SuperUserPagerMaterial(
    uiState: SuperUserUiState,
    actions: SuperUserActions,
    bottomInnerPadding: Dp,
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())
    val listState = rememberLazyListState()
    val searchListState = rememberLazyListState()
    val refreshTick = remember { mutableIntStateOf(0) }
    val pullToRefreshState = rememberPullToRefreshState()

    var localSearchText by remember { mutableStateOf(uiState.searchStatus.searchText) }
    LaunchedEffect(uiState.searchStatus.searchText) {
        localSearchText = uiState.searchStatus.searchText
    }

    val haptic = LocalHapticFeedback.current
    val snackbarHostState = remember { SnackbarHostState() }

    ExpressiveScaffold(
        containerColor = scaffoldBackgroundColor(if (isFreshStyle()) colorScheme.surface else colorScheme.surfaceContainer),
        topBar = {
            SearchAppBar(
                snackbarHostState = snackbarHostState,
                title = {
                    val titleText = stringResource(R.string.fx_superuser)
                    if (isFreshStyle()) {
                        Text(
                            text = titleText,
                            fontSize = 26.sp,
                            lineHeight = 32.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = me.diksu.ultra.foxsu.ui.theme.TitleInk.solid(
                                me.diksu.ultra.foxsu.ui.theme.currentAccent.primary
                            ),
                        )
                    } else {
                        Text(
                            text = titleText,
                            color = me.diksu.ultra.foxsu.ui.theme.TitleInk.solid(
                                me.diksu.ultra.foxsu.ui.theme.currentAccent.primary
                            ),
                        )
                    }
                },
                searchText = localSearchText,
                onSearchTextChange = {
                    localSearchText = it
                    actions.onSearchTextChange(it)
                },
                onClearClick = {
                    localSearchText = ""
                    actions.onClearSearch()
                },
                navigationIcon = {
                    IconButton(onClick = actions.onOpenSulog) {
                        Icon(
                            painter = painterResource(R.drawable.fx_ic_su_log),
                            contentDescription = stringResource(R.string.fx_settings_sulog)
                        )
                    }
                },
                actions = {
                    var showSortMenu by remember { mutableStateOf(false) }

                    IconButton(onClick = { showSortMenu = true }) {
                        Icon(
                            painter = painterResource(R.drawable.fx_ic_su_sort),
                            contentDescription = stringResource(R.string.fx_menu_sort)
                        )

                        DropdownMenuPopup(
                            expanded = showSortMenu,
                            onDismissRequest = { showSortMenu = false }
                        ) {
                            val sortEntries = listOf(
                                AppSortType.NAME to R.string.fx_sort_by_name,
                                AppSortType.PACKAGE_NAME to R.string.fx_sort_by_package_name,
                                AppSortType.INSTALL_TIME to R.string.fx_sort_by_install_time,
                                AppSortType.UPDATE_TIME to R.string.fx_sort_by_update_time,
                            )
                            val sortConfig = uiState.sortConfig

                            DropdownMenuGroup(shapes = MenuDefaults.groupShape(index = 0, count = 2)) {
                                sortEntries.onEachIndexed { index, (type, resId) ->
                                    SelectableDropdownMenuItem(
                                        text = { Text(stringResource(resId)) },
                                        selected = sortConfig.sortType == type,
                                        selectedLeadingIcon = {
                                            Icon(
                                                Icons.Filled.Check,
                                                contentDescription = null,
                                                modifier = Modifier.size(MenuDefaults.LeadingIconSize),
                                            )
                                        },
                                        onClick = {
                                            haptic.performHapticFeedback(HapticFeedbackType.VirtualKey)
                                            actions.onUpdateSortConfig(sortConfig.withType(type))
                                            showSortMenu = false
                                        },
                                        shapes = MenuDefaults.itemShape(
                                            index = index,
                                            count = sortEntries.size
                                        ),
                                    )
                                }
                            }

                            Spacer(Modifier.height(MenuDefaults.GroupSpacing))

                            DropdownMenuGroup(shapes = MenuDefaults.groupShape(index = 1, count = 2)) {
                                CheckableDropdownMenuItem(
                                    text = { Text(stringResource(R.string.fx_sort_reverse)) },
                                    checked = sortConfig.reversed,
                                    checkedLeadingIcon = {
                                        Icon(
                                            Icons.Filled.Check,
                                            modifier = Modifier.size(MenuDefaults.LeadingIconSize),
                                            contentDescription = null,
                                        )
                                    },
                                    onCheckedChange = {
                                        haptic.performHapticFeedback(HapticFeedbackType.VirtualKey)
                                        actions.onUpdateSortConfig(sortConfig.toggleReversed())
                                        showSortMenu = false
                                    },
                                    shapes = MenuDefaults.itemShape(
                                        index = 0,
                                        count = 1
                                    ),
                                )
                            }
                        }
                    }

                    var showDropdown by remember { mutableStateOf(false) }

                    IconButton(onClick = { showDropdown = true }) {
                        Icon(
                            painter = painterResource(R.drawable.fx_ic_su_filter),
                            contentDescription = stringResource(id = R.string.fx_settings)
                        )

                        DropdownMenuPopup(
                            expanded = showDropdown,
                            onDismissRequest = { showDropdown = false }
                        ) {
                            val filterCount = if (uiState.userIds.size > 1) 2 else 1
                            DropdownMenuGroup(shapes = MenuDefaults.groupShapes()) {
                                CheckableDropdownMenuItem(
                                    text = { Text(stringResource(R.string.fx_show_system_apps)) },
                                    checked = uiState.showSystemApps,
                                    checkedLeadingIcon = {
                                        Icon(
                                            Icons.Filled.Check,
                                            modifier = Modifier.size(MenuDefaults.LeadingIconSize),
                                            contentDescription = null,
                                        )
                                    },
                                    onCheckedChange = {
                                        haptic.performHapticFeedback(HapticFeedbackType.VirtualKey)
                                        actions.onToggleShowSystemApps()
                                        showDropdown = false
                                    },
                                    shapes = MenuDefaults.itemShape(index = 0, count = filterCount),
                                )
                                if (uiState.userIds.size > 1) {
                                    CheckableDropdownMenuItem(
                                        text = { Text(stringResource(R.string.fx_show_only_primary_user_apps)) },
                                        checked = uiState.showOnlyPrimaryUserApps,
                                        checkedLeadingIcon = {
                                            Icon(
                                                Icons.Filled.Check,
                                                modifier = Modifier.size(MenuDefaults.LeadingIconSize),
                                                contentDescription = null,
                                            )
                                        },
                                        onCheckedChange = {
                                            haptic.performHapticFeedback(HapticFeedbackType.VirtualKey)
                                            actions.onToggleShowOnlyPrimaryUserApps()
                                            showDropdown = false
                                        },
                                        shapes = MenuDefaults.itemShape(index = 1, count = filterCount),
                                    )
                                }
                            }
                        }
                    }
                },
                scrollBehavior = scrollBehavior,
                defaultContent = { bottomPadding, closeSearch ->
                    LaunchedEffect(localSearchText) {
                        searchListState.scrollToItem(0)
                    }
                    LazyColumn(
                        state = searchListState,
                        modifier = Modifier
                            .fillMaxSize()
                            .nestedScroll(scrollBehavior.nestedScrollConnection),
                        verticalArrangement = Arrangement.spacedBy(if (isFreshStyle()) 10.dp else 2.dp),
                        contentPadding = PaddingValues(
                            start = 16.dp,
                            end = 16.dp,
                            top = 14.dp,
                            bottom = 16.dp + bottomPadding
                        ),
                    ) {
                        if (uiState.recentlyInstalledResults.isNotEmpty()) {
                            item {
                                SegmentedColumn(
                                    title = stringResource(R.string.fx_recently_installed),
                                    content = uiState.recentlyInstalledResults.map { group ->
                                        @Composable {
                                            SearchGroupItem(
                                                group = group,
                                                closeSearch = closeSearch,
                                                onOpenProfile = actions.onOpenProfile,
                                            )
                                        }
                                    }
                                )
                            }
                        }
                    }
                },
                searchContent = { bottomPadding, closeSearch ->
                    LaunchedEffect(localSearchText) {
                        searchListState.scrollToItem(0)
                    }
                    LazyColumn(
                        state = searchListState,
                        modifier = Modifier
                            .fillMaxSize()
                            .nestedScroll(scrollBehavior.nestedScrollConnection),
                        verticalArrangement = Arrangement.spacedBy(if (isFreshStyle()) 10.dp else 2.dp),
                        contentPadding = PaddingValues(
                            start = 16.dp,
                            end = 16.dp,
                            top = 14.dp,
                            bottom = 16.dp + bottomPadding
                        ),
                    ) {
                        itemsIndexed(uiState.searchResults, key = { _, item -> item.uid }) { index, group ->
                            SegmentedItem(index = index, count = uiState.searchResults.size) {
                                SearchGroupItem(
                                    group = group,
                                    closeSearch = closeSearch,
                                    onOpenProfile = actions.onOpenProfile,
                                )
                            }
                        }
                    }
                }
            )
        },
        contentWindowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)
    ) { innerPadding ->
        PullToRefreshBox(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            isRefreshing = uiState.isRefreshing,
            onRefresh = {
                haptic.performHapticFeedback(HapticFeedbackType.VirtualKey)
                actions.onRefresh()
                refreshTick.intValue++
            },
            state = pullToRefreshState,
            indicator = {
                PullToRefreshDefaults.LoadingIndicator(
                    modifier = Modifier.align(Alignment.TopCenter),
                    isRefreshing = uiState.isRefreshing,
                    state = pullToRefreshState,
                )
            },
        ) {
            val expandedSearchUids = remember { mutableStateOf(setOf<Int>()) }

            val latestGroupedApps = rememberUpdatedState(uiState.groupedApps)
            val latestRefreshing = rememberUpdatedState(uiState.isRefreshing)
            ScrollToTopOnChange(
                listState,
                uiState.sortConfig,
                uiState.showSystemApps,
                uiState.showOnlyPrimaryUserApps,
                refreshTick.intValue,
                isBusy = { latestRefreshing.value },
            ) { latestGroupedApps.value }

            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .nestedScroll(scrollBehavior.nestedScrollConnection),
                verticalArrangement = Arrangement.spacedBy(if (isFreshStyle()) 10.dp else 2.dp),
                contentPadding = PaddingValues(
                    start = if (useSideDock()) 96.dp else 16.dp,
                    end = 16.dp,
                    top = 0.dp,
                    bottom = 16.dp + bottomInnerPadding
                ),
            ) {
                itemsIndexed(uiState.groupedApps, key = { _, item -> item.uid }) { index, group ->
                    val expanded = expandedSearchUids.value.contains(group.uid)
                    val onToggleExpand = {
                        if (group.apps.size > 1) {
                            expandedSearchUids.value = if (expandedSearchUids.value.contains(group.uid)) {
                                expandedSearchUids.value - group.uid
                            } else {
                                expandedSearchUids.value + group.uid
                            }
                        }
                    }
                    SegmentedItem(index = index, count = uiState.groupedApps.size) {
                        Column {
                            GroupItem(
                                group = group,
                                selected = expanded,
                                onToggleExpand = onToggleExpand,
                            ) {
                                actions.onOpenProfile(group)
                            }
                            AnimatedVisibility(
                                visible = expanded && group.apps.size > 1,
                                enter = expandVertically() + fadeIn(),
                                exit = shrinkVertically() + fadeOut()
                            ) {
                                Column {
                                    group.apps.forEach { app ->
                                        SimpleAppItem(app = app) {
                                            actions.onOpenProfile(group)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchGroupItem(
    group: GroupedApps,
    closeSearch: () -> Unit,
    onOpenProfile: (GroupedApps) -> Unit,
) {
    Column {
        GroupItem(
            group = group,
            selected = false,
            onToggleExpand = {},
        ) {
            closeSearch()
            onOpenProfile(group)
        }
        AnimatedVisibility(
            visible = group.apps.size > 1,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut()
        ) {
            Column {
                group.apps.forEach { app ->
                    SimpleAppItem(
                        app = app,
                        matched = group.matchedIdentifiers.contains(app.displayIdentifier),
                    ) {
                        closeSearch()
                        onOpenProfile(group)
                    }
                }
            }
        }
    }
}

@Composable
private fun SimpleAppItem(
    app: AppInfo,
    matched: Boolean = false,
    onNavigate: () -> Unit,
) {
    val rowContainer = if (matched) colorScheme.secondaryContainer else freshCardColor()
    val rowContent = if (matched) colorScheme.onSecondaryContainer else cardContentColor()

    ListItem(
        onClick = onNavigate,
        modifier = Modifier
            .padding(horizontal = 2.dp)
            .clip(RoundedCornerShape(20.dp)),
        colors = ListItemDefaults.colors(
            containerColor = rowContainer,
            contentColor = rowContent,
        ),
        content = { Text(app.label, overflow = TextOverflow.Ellipsis, maxLines = 1) },
        supportingContent = { Text(app.displayIdentifier, overflow = TextOverflow.Ellipsis, maxLines = 1) },
        leadingContent = {
            AppIconImage(
                packageInfo = app.packageInfo,
                label = app.label,
                modifier = Modifier
                    .size(40.dp)
                    .padding(start = 4.dp)
            )
        },
        trailingContent = {
            Icon(
                Icons.Filled.Remove,
                contentDescription = null,
                modifier = Modifier.padding(end = 4.dp)
            )
        }
    )
}

@Composable
private fun GroupItem(
    group: GroupedApps,
    selected: Boolean,
    onToggleExpand: () -> Unit,
    onClickPrimary: () -> Unit,
) {
    // 设计稿 .root：薄荷绿底白字胶囊
    val bg = if (isNightScheme()) androidx.compose.ui.graphics.Color(0xFF8FD6A4) else UiPalette.NavBadge
    val fg = if (isNightScheme()) androidx.compose.ui.graphics.Color(0xFF1F2F26) else androidx.compose.ui.graphics.Color.White
    val umountBg = colorScheme.tertiaryContainer
    val umountFg = colorScheme.onTertiaryContainer
    val customBg = colorScheme.secondaryContainer
    val customFg = colorScheme.onSecondaryContainer
    val otherBg = colorScheme.tertiary
    val otherFg = colorScheme.onTertiary

    val userId = group.uid / 100000
    val tags = remember(group.anyAllowSu, group.shouldUmount, group.anyCustom, userId) {
        buildList {
            if (group.anyAllowSu) add(StatusMeta("ROOT", bg, fg))
            if (group.shouldUmount) add(StatusMeta("UMOUNT", umountBg, umountFg))
            if (group.anyCustom) add(StatusMeta("CUSTOM", customBg, customFg))
            if (userId != 0) add(StatusMeta("USER $userId", otherBg, otherFg))
        }
    }
    val summaryText = if (group.apps.size > 1) {
        stringResource(R.string.fx_group_contains_apps, group.apps.size)
    } else {
        group.primary.displayIdentifier
    }
    SegmentedListItem(
        selected = selected,
        onClick = onClickPrimary,
        onLongClick = if (group.apps.size > 1) onToggleExpand else null,
        headlineContent = {
            Text(
                text = if (group.apps.size > 1) ownerNameForUid(group.uid) else group.primary.label,
                overflow = TextOverflow.Ellipsis,
                maxLines = 1
            )
        },
        supportingContent = {
            Text(
                text = summaryText,
                fontSize = 10.sp,
                lineHeight = 13.sp,
                color = colorScheme.onSurfaceVariant,
                overflow = TextOverflow.Ellipsis,
                maxLines = 1
            )
        },
        trailingContent = {
            if (tags.isNotEmpty()) {
                Column(
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    tags.forEach { tag ->
                        StatusTag(
                            label = tag.label,
                            backgroundColor = tag.bg,
                            contentColor = tag.fg
                        )
                    }
                }
            }
        },
        leadingContent = {
            // 设计稿 .uico：42dp、圆角13
            AppIconImage(
                packageInfo = group.primary.packageInfo,
                label = group.primary.label,
                modifier = Modifier
                    .size(42.dp)
                    .clip(RoundedCornerShape(13.dp))
            )
        },
    )
}
