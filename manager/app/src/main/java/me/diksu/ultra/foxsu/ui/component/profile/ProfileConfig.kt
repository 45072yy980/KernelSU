package me.diksu.ultra.foxsu.ui.component.profile

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import me.diksu.ultra.Natives
import me.diksu.ultra.foxsu.ui.LocalUiMode
import me.diksu.ultra.foxsu.ui.UiMode

@Composable
fun AppProfileConfig(
    modifier: Modifier = Modifier,
    fixedName: Boolean,
    enabled: Boolean,
    profile: Natives.Profile,
    onProfileChange: (Natives.Profile) -> Unit,
) {
    when (LocalUiMode.current) {
        UiMode.Miuix -> AppProfileConfigMiuix(
            modifier = modifier,
            fixedName = fixedName,
            enabled = enabled,
            profile = profile,
            onProfileChange = onProfileChange
        )

        UiMode.Material, UiMode.Fresh, UiMode.Glass, UiMode.Nocturne -> AppProfileConfigMaterial(
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
        UiMode.Miuix -> RootProfileConfigMiuix(
            modifier = modifier,
            fixedName = fixedName,
            enabled = enabled,
            profile = profile,
            onProfileChange = onProfileChange
        )

        UiMode.Material, UiMode.Fresh, UiMode.Glass, UiMode.Nocturne -> RootProfileConfigMaterial(
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
        UiMode.Miuix -> TemplateConfigMiuix(
            modifier = modifier,
            profile = profile,
            onViewTemplate = onViewTemplate,
            onManageTemplate = onManageTemplate,
            onProfileChange = onProfileChange
        )

        UiMode.Material, UiMode.Fresh, UiMode.Glass, UiMode.Nocturne -> TemplateConfigMaterial(
            profile = profile,
            onViewTemplate = onViewTemplate,
            onManageTemplate = onManageTemplate,
            onProfileChange = onProfileChange
        )
    }
}
