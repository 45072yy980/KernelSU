package me.weishu.kernelsu.ui.component

import androidx.compose.runtime.Composable
import me.weishu.kernelsu.Natives

@Composable
fun KsuIsValid(
    content: @Composable () -> Unit
) {
    // Natives.isManager is what GET_INFO reports, and stealth mode makes it report "no manager"
    // on purpose. That is the point of the disguise, but it would also hide every screen here
    // that merely needs root -- including the settings page holding the switch that turns stealth
    // back off, which would leave a shell as the only way out.
    //
    // So the check also accepts "the manager is hiding itself". The kernel still knows who the
    // manager is (is_manager() is untouched by stealth), and only the manager can read the
    // stealth flag, so this cannot be borrowed by an app that was never granted the role.
    val isManager = Natives.isManager || Natives.isStealthEnabled
    val ksuVersion = if (isManager) Natives.version else null

    if (ksuVersion != null) {
        content()
    }
}
