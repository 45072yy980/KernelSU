package me.diksu.ultra.ui.component.choosekmidialog

import androidx.compose.runtime.Composable
import me.diksu.ultra.ui.LocalUiMode
import me.diksu.ultra.ui.UiMode

@Composable
fun ChooseKmiDialog(
    show: Boolean,
    onDismissRequest: () -> Unit,
    onSelected: (String?) -> Unit
) {
    when (LocalUiMode.current) {
        UiMode.Miuix, UiMode.MiuixStock, UiMode.Themed -> ChooseKmiDialogMiuix(show, onDismissRequest, onSelected)
        UiMode.Material -> ChooseKmiDialogMaterial(show, onDismissRequest, onSelected)
    }
}
