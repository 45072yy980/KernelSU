package me.weishu.kernelsu.ui.screen.about

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.dropUnlessResumed
import me.weishu.kernelsu.BuildConfig
import me.weishu.kernelsu.R
import me.weishu.kernelsu.ui.LocalUiMode
import me.weishu.kernelsu.ui.UiMode
import me.weishu.kernelsu.ui.navigation3.LocalNavigator
import me.weishu.kernelsu.ui.navigation3.Route

@Composable
fun AboutScreen() {
    val navigator = LocalNavigator.current
    val uriHandler = LocalUriHandler.current
    val htmlString = stringResource(
        id = R.string.about_source_code,
        "<b><a href=\"https://github.com/45072yy980/KernelSU\">GitHub</a></b>",
        "<b><a href=\"https://t.me/DIKSU66\">Telegram</a></b>"
    )
    // Where this build comes from, laid out the way a git log would say it: each line is one
    // repository or one person, newest first. A fork is hard to place without them — the manager
    // reports its own name and version, but not whose work it is standing on.
    val provenance = listOf(
        LinkInfo(
            fullText = stringResource(R.string.about_this_repo),
            url = "https://github.com/45072yy980/KernelSU",
        ),
        LinkInfo(
            fullText = stringResource(R.string.about_upstream_repo),
            url = "https://github.com/wuhudiao/DikSU",
        ),
        LinkInfo(
            fullText = stringResource(R.string.about_original_author),
            url = "https://github.com/wuhudiao",
        ),
    )
    val state = AboutUiState(
        title = stringResource(R.string.about),
        appName = stringResource(R.string.app_name),
        versionName = BuildConfig.VERSION_NAME,
        links = extractLinks(htmlString),
        provenance = provenance,
    )
    val actions = AboutScreenActions(
        onBack = dropUnlessResumed { navigator.pop() },
        onOpenLink = uriHandler::openUri,
        onOpenLicences = dropUnlessResumed { navigator.push(Route.OpenSourceLicense) },
    )

    when (LocalUiMode.current) {
        UiMode.Miuix, UiMode.MiuixStock -> AboutScreenMiuix(state, actions)
        UiMode.Material -> AboutScreenMaterial(state, actions)
    }
}
