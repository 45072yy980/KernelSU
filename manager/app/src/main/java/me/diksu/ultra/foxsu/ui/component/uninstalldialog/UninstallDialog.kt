package me.diksu.ultra.foxsu.ui.component.uninstalldialog

import androidx.compose.runtime.Composable
import me.diksu.ultra.foxsu.ui.LocalUiMode
import me.diksu.ultra.foxsu.ui.UiMode

@Composable
fun UninstallDialog(
    show: Boolean,
    onDismissRequest: () -> Unit
) {
    when (LocalUiMode.current) {
        UiMode.Miuix -> UninstallDialogMiuix(show, onDismissRequest)
        UiMode.Material, UiMode.Fresh, UiMode.Glass, UiMode.Nocturne -> UninstallDialogMaterial(show, onDismissRequest)
    }
}
