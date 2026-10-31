package me.diksu.ultra.ui.component.profile

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import me.diksu.ultra.Natives
import me.diksu.ultra.ui.LocalUiMode
import me.diksu.ultra.ui.UiMode

@Composable
fun AppProfileConfig(
    modifier: Modifier = Modifier,
    fixedName: Boolean,
    enabled: Boolean,
    profile: Natives.Profile,
    onProfileChange: (Natives.Profile) -> Unit,
) {
    when (LocalUiMode.current) {
        UiMode.Miuix, UiMode.MiuixStock, UiMode.Themed -> AppProfileConfigMiuix(
            modifier = modifier,
            fixedName = fixedName,
            enabled = enabled,
            profile = profile,
            onProfileChange = onProfileChange
        )

        UiMode.Material -> AppProfileConfigMaterial(
            modifier = modifier,
            fixedName = fixedName,
            enabled = enabled,
            profile = profile,
            onProfileChange = onProfileChange
        )
    }
}

@Composable
fun RootProfileConfig(
    modifier: Modifier = Modifier,
    fixedName: Boolean,
    enabled: Boolean = true,
    profile: Natives.Profile,
    onProfileChange: (Natives.Profile) -> Unit,
) {
    when (LocalUiMode.current) {
        UiMode.Miuix, UiMode.MiuixStock, UiMode.Themed -> RootProfileConfigMiuix(
            modifier = modifier,
            fixedName = fixedName,
            enabled = enabled,
            profile = profile,
            onProfileChange = onProfileChange
        )

        UiMode.Material -> RootProfileConfigMaterial(
            modifier = modifier,
            enabled = enabled,
            profile = profile,
            onProfileChange = onProfileChange
        )
    }
}

@Composable
fun TemplateConfig(
    modifier: Modifier = Modifier,
    profile: Natives.Profile,
    onViewTemplate: (id: String) -> Unit = {},
    onManageTemplate: () -> Unit = {},
    onProfileChange: (Natives.Profile) -> Unit
) {
    when (LocalUiMode.current) {
        UiMode.Miuix, UiMode.MiuixStock, UiMode.Themed -> TemplateConfigMiuix(
            modifier = modifier,
            profile = profile,
            onViewTemplate = onViewTemplate,
            onManageTemplate = onManageTemplate,
            onProfileChange = onProfileChange
        )

        UiMode.Material -> TemplateConfigMaterial(
            profile = profile,
            onViewTemplate = onViewTemplate,
            onManageTemplate = onManageTemplate,
            onProfileChange = onProfileChange
        )
    }
}
