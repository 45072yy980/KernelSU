package me.diksu.ultra.foxsu.ui.screen.home

import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.diksu.ultra.R
import me.diksu.ultra.foxsu.data.settings.CustomizationStore
import me.diksu.ultra.foxsu.magica.MagicaService
import me.diksu.ultra.foxsu.ui.LocalMainPagerState
import me.diksu.ultra.foxsu.ui.LocalUiMode
import me.diksu.ultra.foxsu.ui.UiMode
import me.diksu.ultra.foxsu.ui.component.ambient.AmbientPawsLayer
import me.diksu.ultra.foxsu.ui.component.ambient.BackgroundFxLayer
import me.diksu.ultra.foxsu.ui.component.bottombar.NavigationBadgeState
import me.diksu.ultra.foxsu.ui.component.dialog.rememberConfirmDialog
import me.diksu.ultra.foxsu.ui.component.dialog.rememberLoadingDialog
import me.diksu.ultra.foxsu.ui.navigation3.Navigator
import me.diksu.ultra.foxsu.ui.navigation3.Route
import me.diksu.ultra.foxsu.ui.screen.flash.FlashIt
import me.diksu.ultra.foxsu.ui.util.LkmSelection
import me.diksu.ultra.foxsu.ui.util.checkNewVersion
import me.diksu.ultra.foxsu.ui.viewmodel.HomeViewModel
import kotlin.time.Duration.Companion.milliseconds

@Composable
fun HomePager(
    navigator: Navigator,
    bottomInnerPadding: Dp,
    isCurrentPage: Boolean = true,
    navigationBadge: NavigationBadgeState = NavigationBadgeState(),
) {
    val viewModel = viewModel<HomeViewModel>()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val uriHandler = LocalUriHandler.current
    val context = LocalContext.current
    val loadingDialog = rememberLoadingDialog()
    val scope = rememberCoroutineScope()
    var pendingUpdateUrl by remember { mutableStateOf("") }
    val updateDialog = rememberConfirmDialog(
        onConfirm = {
            val url = pendingUpdateUrl
            if (url.isNotEmpty()) uriHandler.openUri(url)
        },
    )
    val latestIsCurrentPage by rememberUpdatedState(isCurrentPage)
    val initialResumeHandled = rememberSaveable { mutableStateOf(false) }

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
        onJailbreakClick = {
            loadingDialog.showLoading()
            context.startService(Intent(context, MagicaService::class.java))
            // Manager will be force-stopped and restarted by late-load on success.
            // If that doesn't happen within timeout, jailbreak likely failed.
            scope.launch(Dispatchers.IO) {
                delay(30_000.milliseconds)
                withContext(Dispatchers.Main) {
                    loadingDialog.hide()
                    Toast.makeText(context, R.string.fx_jailbreak_timeout, Toast.LENGTH_LONG).show()
                }
            }
        },
        onCheckUpdate = {
            scope.launch {
                loadingDialog.showLoading()
                val latest = withContext(Dispatchers.IO) { checkNewVersion() }
                loadingDialog.hide()
                val current = uiState.currentManagerVersionCode
                if (latest.versionCode > current && latest.downloadUrl.isNotEmpty()) {
                    pendingUpdateUrl = latest.downloadUrl
                    updateDialog.showConfirm(
                        title = context.getString(R.string.fx_module_changelog),
                        content = latest.changelog.ifEmpty {
                            context.getString(R.string.fx_new_version_available, latest.versionCode)
                        },
                        markdown = true,
                        confirm = context.getString(R.string.fx_module_update),
                    )
                } else {
                    Toast.makeText(
                        context,
                        R.string.fx_home_no_update_found,
                        Toast.LENGTH_SHORT,
                    ).show()
                }
            }
        },
        // 刷新镜像：真实执行 ksud boot-patch -f（走标准刷写页，可看实时日志）
        onRefreshImage = {
            navigator.push(
                Route.Flash(
                    FlashIt.FlashBoot(
                        boot = null,
                        lkm = LkmSelection.KmiNone,
                        ota = false,
                    )
                )
            )
        },
    )

    val mainPagerState = LocalMainPagerState.current

    // 主页装饰层：总开关 + 选择器（0 = 猫爪，1..20 = 背景特效）。
    // 切到别的 Tab 时 HomePager 仍在组合树里，不判 isCurrentPage 会白白每帧重绘。
    val custom = CustomizationStore.current
    val fxOn = custom.bgFxOn && isCurrentPage
    val inset = if (bottomInnerPadding == Dp.Unspecified) 0.dp else bottomInnerPadding

    Box(Modifier.fillMaxSize()) {
        when (LocalUiMode.current) {
            UiMode.Miuix -> me.diksu.ultra.foxsu.ui.screen.ag.AgHome(
                state = uiState,
                actions = actions,
                onOpenTab = { page -> mainPagerState.animateToPage(page) },
                onOpenAuthor = { navigator.push(Route.Author) },
                bottomInnerPadding = bottomInnerPadding,
            )

            UiMode.Material -> me.diksu.ultra.foxsu.ui.screen.editorial.EditorialHome(
                state = uiState,
                actions = actions,
                bottomInnerPadding = bottomInnerPadding,
                navigationBadge = navigationBadge,
                onOpenTab = { page -> mainPagerState.animateToPage(page) },
                onOpenAuthor = { navigator.push(Route.Author) },
            )

            UiMode.Fresh -> HomePagerFresh(
                state = uiState,
                actions = actions,
                bottomInnerPadding = bottomInnerPadding,
                navigationBadge = navigationBadge,
                onOpenTab = { page -> mainPagerState.animateToPage(page) },
            )

            UiMode.Glass -> me.diksu.ultra.foxsu.ui.screen.glass.GlassHome(
                state = uiState,
                actions = actions,
                bottomInnerPadding = bottomInnerPadding,
                navigationBadge = navigationBadge,
                onOpenTab = { page -> mainPagerState.animateToPage(page) },
                onOpenAuthor = { navigator.push(Route.Author) },
            )

            UiMode.Nocturne -> me.diksu.ultra.foxsu.ui.screen.nocturne.NocturneHome(
                state = uiState,
                actions = actions,
                bottomInnerPadding = bottomInnerPadding,
                navigationBadge = navigationBadge,
                onOpenTab = { page -> mainPagerState.animateToPage(page) },
                onOpenAuthor = { navigator.push(Route.Author) },
            )
        }

        // 装饰层压在内容之上：五套皮肤的主页各自铺了不透明底色（agPageBg / Fresh 渐变 /
        // Glass 衬底），任何垫在下面的东西都看不见。两层都不接指针事件，所以不挡点击。
        //
        // 猫爪与 20 套背景特效共用一个槽位（互斥）：cat 位 = 0 时跑原来的爪印氛围，
        // 其余走背景特效。两者画法不同但语义一致——「这一页的氛围长什么样」。
        if (fxOn) {
            if (custom.bgFxStyle == 0) {
                AmbientPawsLayer(enabled = true, bottomInset = inset)
            } else {
                BackgroundFxLayer(style = custom.bgFxStyle, enabled = true)
            }
        }
    }
}
