package me.diksu.ultra.foxsu.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import me.diksu.ultra.foxsu.ui.LocalUiMode
import me.diksu.ultra.foxsu.ui.UiMode

/**
 * True when the "Fresh" UI style is active.
 *
 * The Fresh style reuses the Material based screens, so shared surfaces (list rows, cards,
 * top bars) ask this to swap in the white-card look of that style.
 */
@Composable
@ReadOnlyComposable
fun isFreshStyle(): Boolean = LocalUiMode.current == UiMode.Fresh
