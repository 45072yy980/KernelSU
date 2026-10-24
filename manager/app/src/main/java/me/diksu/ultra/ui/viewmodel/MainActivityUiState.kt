package me.diksu.ultra.ui.viewmodel

import androidx.compose.runtime.Immutable
import me.diksu.ultra.ui.UiMode
import me.diksu.ultra.ui.theme.AppSettings

@Immutable
data class MainActivityUiState(
    val appSettings: AppSettings,
    val pageScale: Float,
    val enableBlur: Boolean,
    val enableFloatingBottomBar: Boolean,
    val enableFloatingBottomBarBlur: Boolean,
    val enableNavigationBadge: Boolean,
    val enableSwipeDismiss: Boolean,
    val pagerInterceptionMode: Int,
    val moduleDescriptionMaxLines: Int = 4,
    val disablePagerSwipe: Boolean = false,
    val homeCardBlur: Boolean = false,
    val simpleMode: Boolean = false,
    val showMoreModuleInfo: Boolean = false,
    val uiMode: UiMode,
)
