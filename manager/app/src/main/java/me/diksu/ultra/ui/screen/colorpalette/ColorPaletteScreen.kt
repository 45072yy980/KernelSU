package me.diksu.ultra.ui.screen.colorpalette

import androidx.activity.compose.LocalActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.dropUnlessResumed
import androidx.lifecycle.viewmodel.compose.viewModel
import com.materialkolor.PaletteStyle
import com.materialkolor.dynamiccolor.ColorSpec
import me.diksu.ultra.KernelSUApplication
import me.diksu.ultra.ui.LocalUiMode
import me.diksu.ultra.ui.UiMode
import me.diksu.ultra.ui.navigation3.LocalNavigator
import me.diksu.ultra.ui.theme.ColorMode
import me.diksu.ultra.ui.viewmodel.SettingsViewModel

@Composable
fun ColorPaletteScreen() {
    val navigator = LocalNavigator.current
    val context = LocalContext.current
    val activity = LocalActivity.current
    val viewModel = viewModel<SettingsViewModel>()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val currentPaletteStyle = try {
        PaletteStyle.valueOf(uiState.colorStyle)
    } catch (_: Exception) {
        PaletteStyle.TonalSpot
    }
    val currentColorSpec = try {
        ColorSpec.SpecVersion.valueOf(uiState.colorSpec)
    } catch (_: Exception) {
        ColorSpec.SpecVersion.SPEC_2025
    }
    val state = ColorPaletteUiState(
        uiState = uiState,
        currentColorMode = ColorMode.fromValue(uiState.themeMode),
        currentPaletteStyle = currentPaletteStyle,
        currentColorSpec = currentColorSpec,
    )
    val actions = ColorPaletteScreenActions(
        onBack = dropUnlessResumed { navigator.pop() },
        onSetUiModeIndex = { index ->
            // Index into the same list the pickers render (UiMode.entries). The old
            // 0/1/else mapping silently turned any further style into Material.
            UiMode.entries.getOrNull(index)?.let { viewModel.setUiMode(it.value) }
        },
        onSetThemeMode = viewModel::setThemeMode,
        onSetMiuixMonet = viewModel::setMiuixMonet,
        onSetKeyColor = viewModel::setKeyColor,
        onSetColorMode = viewModel::setColorMode,
        onSetColorStyle = viewModel::setColorStyle,
        onSetColorSpec = viewModel::setColorSpec,
        onSetEnableBlur = viewModel::setEnableBlur,
        onSetHomeCardBlur = viewModel::setHomeCardBlur,
        onSetEnableFloatingBottomBar = viewModel::setEnableFloatingBottomBar,
        onSetEnableFloatingBottomBarBlur = viewModel::setEnableFloatingBottomBarBlur,
        onSetEnableNavigationBadge = viewModel::setEnableNavigationBadge,
        onSetEnablePredictiveBack = {
            viewModel.setEnablePredictiveBack(it)
            KernelSUApplication.setEnableOnBackInvokedCallback(context.applicationInfo, it)
            activity?.recreate()
        },
        onSetEnableSwipeDismiss = viewModel::setEnableSwipeDismiss,
        onSetPagerInterceptionMode = viewModel::setPagerInterceptionMode,
        onSetPageScale = viewModel::setPageScale,
        onSetModuleDescriptionMaxLines = viewModel::setModuleDescriptionMaxLines,
        onSetSimpleMode = viewModel::setSimpleMode,
        onSetShowMoreModuleInfo = viewModel::setShowMoreModuleInfo,
        onSetDisablePagerSwipe = viewModel::setDisablePagerSwipe,
    )

    when (LocalUiMode.current) {
        UiMode.Miuix, UiMode.Themed -> ColorPaletteScreenMiuix(state, actions)
        UiMode.MiuixStock -> ColorPaletteScreenMiuixStock(state, actions)
        UiMode.Material -> ColorPaletteScreenMaterial(state, actions)
    }
}
