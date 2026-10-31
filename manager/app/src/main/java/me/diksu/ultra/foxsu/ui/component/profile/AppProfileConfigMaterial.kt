package me.diksu.ultra.foxsu.ui.component.profile

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import me.diksu.ultra.Natives
import me.diksu.ultra.R
import me.diksu.ultra.foxsu.ui.component.material.SegmentedColumn
import me.diksu.ultra.foxsu.ui.component.material.SegmentedSwitchItem
import me.diksu.ultra.foxsu.ui.component.material.SegmentedTextField

@Composable
fun AppProfileConfigMaterial(
    modifier: Modifier = Modifier,
    fixedName: Boolean,
    enabled: Boolean,
    profile: Natives.Profile,
    onProfileChange: (Natives.Profile) -> Unit,
) {
    Column(modifier = modifier) {
        if (!fixedName) {
            SegmentedColumn(
                modifier = Modifier.padding(horizontal = 16.dp),
                content = listOf {
                    SegmentedTextField(
                        value = profile.name,
                        onValueChange = { onProfileChange(profile.copy(name = it)) },
                        label = stringResource(R.string.fx_profile_name),
                        readOnly = !enabled,
                        singleLine = true
                    )
                }
            )
        }

        SegmentedColumn(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            content = listOf {
                SegmentedSwitchItem(
                    title = stringResource(R.string.fx_profile_umount_modules),
                    summary = stringResource(R.string.fx_profile_umount_modules_summary),
                    checked = if (enabled) {
                        profile.umountModules
                    } else {
                        Natives.isDefaultUmountModules()
                    },
                    enabled = enabled,
                    onCheckedChange = {
                        onProfileChange(
                            profile.copy(
                                umountModules = it,
                                nonRootUseDefault = false
                            )
                        )
                    }
                )
            }
        )
    }
}
