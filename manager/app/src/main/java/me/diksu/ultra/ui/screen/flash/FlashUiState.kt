package me.diksu.ultra.ui.screen.flash

import androidx.annotation.StringRes
import androidx.compose.runtime.Immutable

@Immutable
data class FlashUiState(
    val text: String,
    val showRebootAction: Boolean,
    val flashingStatus: FlashingStatus,
    val showJailbreakWarning: Boolean,
    @param:StringRes val rebootLabelRes: Int,
    // True while running the bundled jailbreak exploit, so the screen can say
    // "jailbreaking" instead of "flashing".
    val isJailbreak: Boolean = false,
    // True while ksud is blocked on the volume keys (the partition guard's yellow
    // verdict, up to 60 seconds). The title then says "waiting" instead of
    // "flashing", so the prompt does not look like a frozen install.
    val waitingForKeys: Boolean = false,
)

@Immutable
data class FlashScreenActions(
    val onBack: () -> Unit,
    val onSaveLog: () -> Unit,
    val onReboot: () -> Unit,
    val onConfirmJailbreakWarning: () -> Unit,
    val onDismissJailbreakWarning: () -> Unit,
)
