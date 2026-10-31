package me.diksu.ultra.ui.screen.home

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
import me.diksu.ultra.R
import me.diksu.ultra.magica.MagicaService
import me.diksu.ultra.ui.LocalUiMode
import me.diksu.ultra.ui.UiMode
import me.diksu.ultra.ui.component.dialog.JailbreakDialog
import me.diksu.ultra.ui.component.dialog.rememberLoadingDialog
import me.diksu.ultra.ui.screen.flash.FlashIt
import me.diksu.ultra.ui.util.DirtyFragExploit
import me.diksu.ultra.ui.util.JailbreakExploit
import me.diksu.ultra.ui.navigation3.Navigator
import me.diksu.ultra.ui.navigation3.Route
import me.diksu.ultra.ui.viewmodel.HomeViewModel
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

    // Ask how to proceed when the kernel is not installed yet. This dialog has
    // two explicit buttons (exploit / manual install); tapping outside or the
    // back gesture only closes it and must NOT fall through to the install page.
    var jailbreakChooserShown by rememberSaveable { mutableStateOf(false) }

    JailbreakDialog(
        show = jailbreakChooserShown,
        onExploit = {
            jailbreakChooserShown = false
            if (!JailbreakExploit.isAvailable(context)) {
                Toast.makeText(context, R.string.jailbreak_exploit_unavailable, Toast.LENGTH_LONG).show()
            } else {
                navigator.push(Route.Flash(FlashIt.JailbreakExploit))
            }
        },
        onDirtyFrag = {
            jailbreakChooserShown = false
            // 第二条路：DirtyFrag 的利用链同样内置在本应用里，
            // 走同一个进度页，只是换一个二进制。
            if (!DirtyFragExploit.isAvailable(context)) {
                Toast.makeText(context, R.string.jailbreak_exploit_unavailable, Toast.LENGTH_LONG).show()
            } else {
                navigator.push(Route.Flash(FlashIt.DirtyFrag))
            }
        },
        onManualInstall = {
            jailbreakChooserShown = false
            navigator.push(Route.Install)
        },
        onCancel = {
            // Just close the chooser; do not navigate anywhere.
            jailbreakChooserShown = false
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
        // permissive: open the chooser (exploit vs manual install).
        onNotInstalledClick = { jailbreakChooserShown = true },
        onJailbreakClick = {
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
        onJailbreakExploitClick = { jailbreakChooserShown = true },
    )

    when (LocalUiMode.current) {
        UiMode.Miuix, UiMode.Themed -> HomePagerMiuix(
            state = uiState,
            actions = actions,
            bottomInnerPadding = bottomInnerPadding,
        )

        UiMode.MiuixStock -> HomePagerMiuixStock(
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
