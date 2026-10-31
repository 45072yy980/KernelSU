package me.diksu.ultra.foxsu.ui.screen.appprofile

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import me.diksu.ultra.R

enum class Mode(@field:StringRes private val res: Int) {
    Default(R.string.fx_profile_default),
    Template(R.string.fx_profile_template),
    Custom(R.string.fx_profile_custom);

    val text: String
        @Composable get() = stringResource(res)
}
