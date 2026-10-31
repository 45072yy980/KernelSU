package me.diksu.ultra.foxsu.ui.screen.module

import androidx.annotation.StringRes
import me.diksu.ultra.R
import me.diksu.ultra.foxsu.data.model.Module

/** Message for a module whose preinit actions will not run on the next boot, null otherwise. */
@StringRes
fun Module.preinitWarning(): Int? = when (preinit) {
    "no_space" -> R.string.fx_module_preinit_no_space
    "error" -> R.string.fx_module_preinit_error
    "bootloop" -> R.string.fx_module_preinit_bootloop
    "unsupported" -> R.string.fx_module_preinit_unsupported
    else -> null
}
