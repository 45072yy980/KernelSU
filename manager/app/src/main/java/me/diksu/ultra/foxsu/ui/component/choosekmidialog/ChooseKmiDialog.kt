package me.diksu.ultra.foxsu.ui.component.choosekmidialog

import androidx.compose.runtime.Composable
import me.diksu.ultra.foxsu.ui.LocalUiMode
import me.diksu.ultra.foxsu.ui.UiMode

@Composable
fun ChooseKmiDialog(
    show: Boolean,
    onDismissRequest: () -> Unit,
    onSelected: (String?) -> Unit
) {
    when (LocalUiMode.current) {
        UiMode.Miuix -> ChooseKmiDialogMiuix(show, onDismissRequest, onSelected)
        UiMode.Material, UiMode.Fresh, UiMode.Glass, UiMode.Nocturne -> ChooseKmiDialogMaterial(show, onDismissRequest, onSelected)
    }
}
