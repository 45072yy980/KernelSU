package me.diksu.ultra.ui.component.uninstalldialog

import androidx.compose.runtime.Composable
import me.diksu.ultra.ui.LocalUiMode
import me.diksu.ultra.ui.UiMode

@Composable
fun UninstallDialog(
    show: Boolean,
    onDismissRequest: () -> Unit
) {
    when (LocalUiMode.current) {
        UiMode.Miuix, UiMode.MiuixStock -> UninstallDialogMiuix(show, onDismissRequest)
        UiMode.Material -> UninstallDialogMaterial(show, onDismissRequest)
    }
}
