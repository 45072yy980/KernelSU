package me.weishu.kernelsu.ui.screen.home

import android.content.Intent
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.Dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.weishu.kernelsu.R
import me.weishu.kernelsu.magica.MagicaService
import me.weishu.kernelsu.ui.LocalUiMode
import me.weishu.kernelsu.ui.UiMode
import me.weishu.kernelsu.ui.component.dialog.rememberConfirmDialog
import me.weishu.kernelsu.ui.component.dialog.rememberLoadingDialog
import me.weishu.kernelsu.ui.navigation3.Navigator
import me.weishu.kernelsu.ui.navigation3.Route
import me.weishu.kernelsu.ui.screen.flash.FlashIt
import me.weishu.kernelsu.ui.util.JailbreakExploit
import me.weishu.kernelsu.ui.viewmodel.HomeViewModel
import kotlin.time.Duration.Companion.milliseconds


@Composable
fun HomePager(
    navigator: Navigator,
    bottomInnerPadding: Dp,
    isCurrentPage: Boolean = true,
) {
    val viewModel = viewModel<HomeViewModel>()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val uriHandler = LocalUriHandler.current
    val context = LocalContext.current
    val loadingDialog = rememberLoadingDialog()
    val scope = rememberCoroutineScope()
    val latestIsCurrentPage by rememberUpdatedState(isCurrentPage)
    val initialResumeHandled = rememberSaveable { mutableStateOf(false) }

    // Ask the user how to proceed when the kernel is not installed yet: run the
    // bundled exploit to gain root and late-load right now, or go to the manual
    // installation flow. The exploit is only offered when the binary is bundled.
    val jailbreakPrompt = rememberConfirmDialog(
        onConfirm = {
            // Confirmed: open the live progress screen. It runs the bundled
            // exploit and streams its log the same way a module install does.
            if (!JailbreakExploit.isAvailable(context)) {
                Toast.makeText(context, R.string.jailbreak_exploit_unavailable, Toast.LENGTH_LONG).show()
            } else {
                navigator.push(Route.Flash(FlashIt.JailbreakExploit))
            }
        },
        onDismiss = {
            // Dismissed: fall back to the ordinary installation flow.
            navigator.push(Route.Install)
        },
    )

    var hasActivated by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(isCurrentPage) {
        if (isCurrentPage && !hasActivated) {
            hasActivated = true
            viewModel.refresh()
        }
    }

    LifecycleResumeEffect(Unit) {
        if (initialResumeHandled.value && latestIsCurrentPage) {
            viewModel.refresh()
        }
        initialResumeHandled.value = true
        onPauseOrDispose { }
    }

    val actions = HomeActions(
        onInstallClick = { navigator.push(Route.Install) },
        onOpenUrl = uriHandler::openUri,
        // The card's own click, or the small "jailbreak" button when SELinux is
        // permissive: ask whether to exploit-then-late-load or install manually.
        onNotInstalledClick = {
            jailbreakPrompt.showConfirm(
                title = context.getString(R.string.jailbreak_choose_title),
                content = context.getString(R.string.jailbreak_choose_message),
                confirm = context.getString(R.string.jailbreak_exploit_action),
                dismiss = context.getString(R.string.jailbreak_manual_action),
            )
        },
        onJailbreakClick = {
            // Immediate jailbreak: the device already has a working shell/ksud, so
            // just ask ksud to late-load via the magica service.
            loadingDialog.showLoading()
            context.startService(Intent(context, com.mngr.app.magica.MagicaService::class.java))
            // Manager will be force-stopped and restarted by late-load on success.
            // If that doesn't happen within timeout, jailbreak likely failed.
            scope.launch(Dispatchers.IO) {
                delay(30_000.milliseconds)
                withContext(Dispatchers.Main) {
                    loadingDialog.hide()
                    Toast.makeText(context, R.string.jailbreak_timeout, Toast.LENGTH_LONG).show()
                }
            }
        },
        onJailbreakExploitClick = {
            // Explicit request to exploit; skip the chooser and run it directly.
            jailbreakPrompt.showConfirm(
                title = context.getString(R.string.jailbreak_choose_title),
                content = context.getString(R.string.jailbreak_choose_message),
                confirm = context.getString(R.string.jailbreak_exploit_action),
                dismiss = context.getString(R.string.jailbreak_manual_action),
            )
        },
    )

    when (LocalUiMode.current) {
        UiMode.Miuix -> HomePagerMiuix(
            state = uiState,
            actions = actions,
            bottomInnerPadding = bottomInnerPadding,
        )

        UiMode.Material -> HomePagerMaterial(
            state = uiState,
            actions = actions,
            bottomInnerPadding = bottomInnerPadding,
        )
    }
}