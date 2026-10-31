package me.diksu.ultra.foxsu.ui.component

import androidx.compose.runtime.Composable
import me.diksu.ultra.Natives

@Composable
fun KsuIsValid(
    content: @Composable () -> Unit
) {
    val isManager = Natives.isManager
    val ksuVersion = if (isManager) Natives.version else null

    if (ksuVersion != null) {
        content()
    }
}
