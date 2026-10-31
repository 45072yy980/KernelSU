package me.diksu.ultra.foxsu.ui.screen.settings

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import me.diksu.ultra.R
import me.diksu.ultra.foxsu.ui.LocalUiMode
import me.diksu.ultra.foxsu.ui.UiMode
import me.diksu.ultra.foxsu.ui.navigation3.Navigator
import me.diksu.ultra.foxsu.ui.navigation3.Route
import me.diksu.ultra.foxsu.ui.viewmodel.SettingsViewModel

@Composable
fun SettingPager(
    navigator: Navigator,
    bottomInnerPadding: Dp,
    isCurrentPage: Boolean = true,
) {
    val viewModel = viewModel<SettingsViewModel>()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val latestIsCurrentPage by rememberUpdatedState(isCurrentPage)
    val initialResumeHandled = rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(isCurrentPage) {
        if (isCurrentPage) {
            viewModel.refresh()
        }
    }

    LifecycleResumeEffect(Unit) {
        if (initialResumeHandled.value && latestIsCurrentPage) {
            viewModel.refresh()
        }
        initialResumeHandled.value = true
        onPauseOrDispose { }
    }

    val actions = SettingsScreenActions(
        onSetCheckUpdate = viewModel::setCheckUpdate,
        onSetCheckModuleUpdate = viewModel::setCheckModuleUpdate,
        onOpenTheme = { navigator.push(Route.ColorPalette) },
        onSetUiModeIndex = { index ->
            val mode = UiMode.entries.getOrNull(index) ?: UiMode.Miuix
            viewModel.setUiMode(mode.value)
        },
        onOpenProfileTemplate = { navigator.push(Route.AppProfileTemplate) },
        onSetSuCompatMode = viewModel::setSuCompatMode,
        onSetKernelUmountEnabled = viewModel::setKernelUmountEnabled,
        onSetSelinuxHideEnabled = viewModel::setSelinuxHideEnabled,
        onSetSulogEnabled = viewModel::setSulogEnabled,
        onSetAdbRootEnabled = viewModel::setAdbRootEnabled,
        onSetShellSuAdbOnlyEnabled = viewModel::setShellSuAdbOnlyEnabled,
        onSetDefaultUmountModules = viewModel::setDefaultUmountModules,
        onSetEnableWebDebugging = viewModel::setEnableWebDebugging,
        onSetAutoJailbreak = viewModel::setAutoJailbreak,
        onSetUseSoftReboot = viewModel::setUseSoftReboot,
        onOpenAbout = { navigator.push(Route.About) },
        onSetEnableBlur = viewModel::setEnableBlur,
        onSetEnableFloatingBottomBar = viewModel::setEnableFloatingBottomBar,
        onSetEnableNavigationBadge = viewModel::setEnableNavigationBadge,
        onSetEnablePredictiveBack = viewModel::setEnablePredictiveBack,
        onSetEnableSwipeDismiss = viewModel::setEnableSwipeDismiss,
        onSetPagerInterceptionMode = viewModel::setPagerInterceptionMode,
        onSetPageScale = viewModel::setPageScale,
        onSetModuleDescriptionMaxLines = viewModel::setModuleDescriptionMaxLines,
    )

    when (LocalUiMode.current) {
        UiMode.Miuix -> me.diksu.ultra.foxsu.ui.screen.ag.AgSettings(
            uiState = uiState,
            actions = actions,
            bottomInnerPadding = bottomInnerPadding,
            onOpenCustomization = { navigator.push(Route.Customization) },
            onOpenAuthor = { navigator.push(Route.Author) },
        )
        UiMode.Fresh -> SettingPagerMaterial(uiState, actions, bottomInnerPadding)

        UiMode.Material -> me.diksu.ultra.foxsu.ui.screen.editorial.EditorialSettings(
            uiState = uiState,
            actions = actions,
            bottomInnerPadding = bottomInnerPadding,
            onOpenCustomization = { navigator.push(Route.Customization) },
            onOpenSulog = { navigator.push(Route.Sulog) },
        )
        UiMode.Glass -> me.diksu.ultra.foxsu.ui.screen.glass.GlassSettingsList(
            uiState = uiState,
            actions = actions,
            bottomInnerPadding = bottomInnerPadding,
            onOpenCustomization = { navigator.push(Route.Customization) },
            onOpenSulog = { navigator.push(Route.Sulog) },
        )

        UiMode.Nocturne -> me.diksu.ultra.foxsu.ui.screen.nocturne.NocturneSettingsList(
            uiState = uiState,
            actions = actions,
            bottomInnerPadding = bottomInnerPadding,
            onOpenCustomization = { navigator.push(Route.Customization) },
            onOpenSulog = { navigator.push(Route.Sulog) },
        )
    }
}
