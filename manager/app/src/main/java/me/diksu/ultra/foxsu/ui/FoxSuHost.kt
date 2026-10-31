package me.diksu.ultra.foxsu.ui

import androidx.compose.ui.platform.LocalContext
import top.yukonga.miuix.kmp.basic.TextButton as MiuixTextButton
import me.diksu.ultra.foxsu.ui.component.bottombar.useSideDock
import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.os.SystemClock
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerDefaults.flingBehavior
import androidx.compose.foundation.pager.PagerDefaults.pageNestedScrollConnection
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import me.diksu.ultra.R
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.animation.core.animateFloat
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.sp
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import me.diksu.ultra.foxsu.ui.util.LauncherIconManager
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.dropUnlessResumed
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import me.diksu.ultra.Natives
import kotlinx.coroutines.launch
import me.diksu.ultra.foxsu.data.settings.CustomizationStore
import me.diksu.ultra.foxsu.ui.component.LocalAppBackdrop
import me.diksu.ultra.foxsu.ui.component.WallpaperBackground
import me.diksu.ultra.foxsu.ui.component.bottombar.BottomBar
import me.diksu.ultra.foxsu.ui.component.bottombar.MainPagerState
import me.diksu.ultra.foxsu.ui.component.bottombar.NavigationBadgeState
import me.diksu.ultra.foxsu.ui.component.bottombar.SideRail
import me.diksu.ultra.foxsu.ui.component.bottombar.LocalDockReveal
import me.diksu.ultra.foxsu.ui.component.bottombar.dockRevealOnTouch
import me.diksu.ultra.foxsu.ui.component.bottombar.rememberDockRevealState
import me.diksu.ultra.foxsu.ui.component.hasCustomWallpaper
import me.diksu.ultra.foxsu.ui.component.scaffoldBackgroundColor
import me.diksu.ultra.foxsu.ui.component.bottombar.rememberMainPagerState
import me.diksu.ultra.foxsu.ui.component.bottombar.useNavigationRail
import me.diksu.ultra.foxsu.ui.navigation3.IntentDispatcher
import me.diksu.ultra.foxsu.ui.navigation3.LocalNavigator
import me.diksu.ultra.foxsu.ui.navigation3.Navigator
import me.diksu.ultra.foxsu.ui.navigation3.Route
import me.diksu.ultra.foxsu.ui.navigation3.rememberNavigator
import me.diksu.ultra.foxsu.ui.screen.about.AboutScreen
import me.diksu.ultra.foxsu.ui.screen.author.AuthorScreen
import me.diksu.ultra.foxsu.ui.screen.appprofile.AppProfileScreen
import me.diksu.ultra.foxsu.ui.screen.colorpalette.ColorPaletteScreen
import me.diksu.ultra.foxsu.ui.screen.executemoduleaction.ExecuteModuleActionScreen
import me.diksu.ultra.foxsu.ui.screen.flash.FlashScreen
import me.diksu.ultra.foxsu.ui.screen.home.HomePager
import me.diksu.ultra.foxsu.ui.screen.install.InstallScreen
import me.diksu.ultra.foxsu.ui.screen.module.ModulePager
import me.diksu.ultra.foxsu.ui.screen.onboarding.WelcomeOverlay
import me.diksu.ultra.foxsu.ui.screen.modulerepo.ModuleRepoDetailScreen
import me.diksu.ultra.foxsu.ui.screen.modulerepo.ModuleRepoScreen
import me.diksu.ultra.foxsu.ui.screen.scriptrun.ScriptRunScreen
import me.diksu.ultra.foxsu.ui.screen.settings.SettingPager
import me.diksu.ultra.foxsu.ui.screen.sulog.SulogScreen
import me.diksu.ultra.foxsu.ui.screen.superuser.SuperUserPager
import me.diksu.ultra.foxsu.ui.screen.template.AppProfileTemplateScreen
import me.diksu.ultra.foxsu.ui.screen.templateeditor.TemplateEditorScreen
import me.diksu.ultra.foxsu.ui.theme.KernelSUTheme
import me.diksu.ultra.foxsu.ui.theme.glassTokens
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.graphicsLayer
import me.diksu.ultra.foxsu.ui.theme.LocalColorMode
import me.diksu.ultra.foxsu.ui.theme.LocalEnableBlur
import me.diksu.ultra.foxsu.ui.theme.LocalEnableFloatingBottomBar
import me.diksu.ultra.foxsu.ui.theme.LocalEnableFloatingBottomBarBlur
import me.diksu.ultra.foxsu.ui.theme.LocalEnableNavigationBadge
import me.diksu.ultra.foxsu.ui.theme.LocalModuleDescriptionMaxLines
import me.diksu.ultra.foxsu.ui.util.getSuperuserCount
import me.diksu.ultra.foxsu.ui.util.OnboardingPrefs
import me.diksu.ultra.foxsu.ui.util.install
import me.diksu.ultra.foxsu.ui.util.rememberBlurBackdrop
import me.diksu.ultra.foxsu.ui.util.rememberContentReady
import me.diksu.ultra.foxsu.ui.viewmodel.MainActivityViewModel
import me.diksu.ultra.foxsu.ui.viewmodel.MainPagerConfig
import me.diksu.ultra.foxsu.ui.viewmodel.ModuleViewModel
import me.diksu.ultra.foxsu.ui.viewmodel.SuperUserViewModel
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop
import top.yukonga.miuix.kmp.nav.core.NavDisplay
import top.yukonga.miuix.kmp.nav.core.NavDisplayEffects
import top.yukonga.miuix.kmp.nav.core.rememberNavSystemCornerRadius
import top.yukonga.miuix.kmp.nav.transition.NavSwipeDirection
import top.yukonga.miuix.kmp.theme.ColorSchemeMode
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.ThemeController
import top.yukonga.miuix.kmp.utils.PagerGestureNestedScrollConnection
import top.yukonga.miuix.kmp.utils.PagerInterceptionMode
import top.yukonga.miuix.kmp.utils.PagerNavigationSpringSpec
import top.yukonga.miuix.kmp.utils.pagerGestureOverride

/**
 * FoxSu 的整套界面，作为我们管理器的第 4/5/6 个风格（Fresh / Glass / Nocturne）嵌入现有 Activity。
 *
 * 内容与 FoxSu 原 `MainActivity.setContent { ... }` 的主体一致，只做了四件事：
 * 1. 需要 Activity 的地方改用 [LocalContext] 取到的宿主（原来的 `this@MainActivity` / `window`）；
 * 2. 当前风格由参数 [mode] 决定（原先是 FoxSu 自己 `uiState.uiMode`），这样风格选择仍在我们这边；
 * 3. 自己创建 intent 通道，不与宿主的 Intent 分发共享状态；
 * 4. `enableEdgeToEdge` 在这里调用，宿主在 FoxSu 风格下不再设置一次。
 *
 * 不要在这里调用我们的 `KernelSUTheme` / `LocalUiMode`：正文里用的是 FoxSu 自己那一套
 * （`me.diksu.ultra.foxsu.ui.*`），两者同名但互不相干。
 */
@Composable
fun FoxSuHost(
    mode: UiMode,
    onOpenManagerSettings: () -> Unit = {},
) {
    val activity = LocalContext.current as ComponentActivity
    val intentChannel = remember { Channel<Intent>(capacity = Channel.BUFFERED) }
    // Overlay host: the FoxSu screen fills the window, the one entry back into our own
    // settings sits on top of it. This is the only piece of our UI that runs in these styles.
    Box(modifier = Modifier.fillMaxSize()) {
        val viewModel = viewModel<MainActivityViewModel>()
        val uiState by viewModel.uiState.collectAsStateWithLifecycle()
        val selectedMainPage by viewModel.selectedMainPage.collectAsStateWithLifecycle()
        val appSettings = uiState.appSettings
        val uiMode = mode
        val darkMode = appSettings.colorMode.isDark || (appSettings.colorMode.isSystem && isSystemInDarkTheme())

        DisposableEffect(darkMode) {
            activity.enableEdgeToEdge(
                statusBarStyle = SystemBarStyle.auto(
                    android.graphics.Color.TRANSPARENT,
                    android.graphics.Color.TRANSPARENT
                ) { darkMode },
                navigationBarStyle = SystemBarStyle.auto(
                    android.graphics.Color.TRANSPARENT,
                    android.graphics.Color.TRANSPARENT
                ) { darkMode },
            )
            activity.window.isNavigationBarContrastEnforced = false
            onDispose { }
        }

        val navigator = rememberNavigator(Route.Main)
        val systemDensity = LocalDensity.current
        val density = remember(systemDensity, uiState.pageScale) {
            Density(systemDensity.density * uiState.pageScale, systemDensity.fontScale)
        }

        CompositionLocalProvider(
            LocalNavigator provides navigator,
            LocalDensity provides density,
            LocalColorMode provides appSettings.colorMode.value,
            LocalEnableBlur provides uiState.enableBlur,
            LocalEnableFloatingBottomBar provides uiState.enableFloatingBottomBar,
            LocalEnableFloatingBottomBarBlur provides uiState.enableFloatingBottomBarBlur,
            LocalEnableNavigationBadge provides uiState.enableNavigationBadge,
            LocalModuleDescriptionMaxLines provides uiState.moduleDescriptionMaxLines,
            LocalUiMode provides uiMode,
        ) {
            KernelSUTheme(appSettings = appSettings, uiMode = uiMode) {
                IntentDispatcher(intentChannel = intentChannel)
                val swipeDismiss = if (uiState.enableSwipeDismiss) {
                    if (LocalLayoutDirection.current == androidx.compose.ui.unit.LayoutDirection.Rtl) {
                        NavSwipeDirection.RightToLeft
                    } else {
                        NavSwipeDirection.LeftToRight
                    }
                } else {
                    NavSwipeDirection.None
                }
                val flashRoute = navigator.current() as? Route.Flash
                var flashBackEnabled by remember(flashRoute) { mutableStateOf(false) }
                val scriptRunRoute = navigator.current() as? Route.ScriptRun
                var scriptRunBackEnabled by remember(scriptRunRoute) { mutableStateOf(true) }
                // Read here, not inside the NavDisplay builder: NavDisplay only rebuilds its entries
                // (and their swipeDismiss) when the builder lambda changes, so it must capture the value.
                val scriptRunSwipeDismiss = if (scriptRunBackEnabled) swipeDismiss else NavSwipeDirection.None
                val mainScreenEntry = @Composable {
                    MainScreen(
                        initialPage = selectedMainPage,
                        pagerInterceptionMode = uiState.pagerInterceptionMode,
                        onPageChanged = viewModel::setSelectedMainPage,
                    )
                }

                val navDisplay = @Composable {
                    NavDisplay(
                        backStack = navigator.backStack,
                        effects = NavDisplayEffects(cornerClipRadius = rememberNavSystemCornerRadius()),
                        onBack = {
                            when (val top = navigator.current()) {
                                is Route.TemplateEditor -> {
                                    if (!top.readOnly) {
                                        navigator.setResult("template_edit", true)
                                    } else {
                                        navigator.pop()
                                    }
                                }

                                else -> navigator.pop()
                            }
                        }) {
                        entry<Route.Main>(swipeDismiss = swipeDismiss) { mainScreenEntry() }
                        entry<Route.About>(swipeDismiss = swipeDismiss) { AboutScreen() }
                        entry<Route.Author>(swipeDismiss = swipeDismiss) { AuthorScreen() }
                        entry<Route.DockIcons>(swipeDismiss = swipeDismiss) {
                            me.diksu.ultra.foxsu.ui.screen.ag.AgDockIcons(
                                onBack = dropUnlessResumed { navigator.pop() },
                            )
                        }
                        entry<Route.DockFx>(swipeDismiss = swipeDismiss) {
                            me.diksu.ultra.foxsu.ui.screen.ag.AgDockFx(
                                onBack = dropUnlessResumed { navigator.pop() },
                            )
                        }
                        entry<Route.Credits>(swipeDismiss = swipeDismiss) {
                            val uriHandler = androidx.compose.ui.platform.LocalUriHandler.current
                            me.diksu.ultra.foxsu.ui.screen.ag.AgCredits(
                                onBack = dropUnlessResumed { navigator.pop() },
                                onOpenUrl = uriHandler::openUri,
                            )
                        }
                        entry<Route.Donate>(swipeDismiss = swipeDismiss) {
                            me.diksu.ultra.foxsu.ui.screen.ag.AgDonate(
                                onBack = dropUnlessResumed { navigator.pop() },
                                onOpenAuthor = dropUnlessResumed { navigator.push(Route.Author) },
                            )
                        }
                        entry<Route.AdBlock>(swipeDismiss = swipeDismiss) {
                            me.diksu.ultra.foxsu.ui.screen.ag.AgAdBlock(
                                onBack = dropUnlessResumed { navigator.pop() },
                            )
                        }
                        entry<Route.Kpm>(swipeDismiss = swipeDismiss) {
                            me.diksu.ultra.foxsu.ui.screen.ag.AgKpm(
                                onBack = dropUnlessResumed { navigator.pop() },
                            )
                        }
                        entry<Route.SusFs>(swipeDismiss = swipeDismiss) {
                            me.diksu.ultra.foxsu.ui.screen.ag.AgSusFs(
                                onBack = dropUnlessResumed { navigator.pop() },
                            )
                        }
                        entry<Route.Sulog>(swipeDismiss = swipeDismiss) { SulogScreen() }
                        entry<Route.ColorPalette>(swipeDismiss = swipeDismiss) { ColorPaletteScreen() }
                        entry<Route.MetaModule>(swipeDismiss = swipeDismiss) {
                            me.diksu.ultra.foxsu.ui.screen.ag.AgMetaModule(
                                onBack = dropUnlessResumed { navigator.pop() },
                            )
                        }
                        entry<Route.Builtins>(swipeDismiss = swipeDismiss) {
                            me.diksu.ultra.foxsu.ui.screen.ag.AgBuiltinModules(
                                onBack = dropUnlessResumed { navigator.pop() },
                                onOpenDetail = { id -> navigator.push(Route.BuiltinDetail(id)) },
                                onOpenMetaModule = { navigator.push(Route.MetaModule) },
                            )
                        }
                        entry<Route.BuiltinDetail>(swipeDismiss = swipeDismiss) { key ->
                            me.diksu.ultra.foxsu.ui.screen.ag.AgBuiltinDetail(
                                moduleId = key.id,
                                onBack = dropUnlessResumed { navigator.pop() },
                                onOpenWebUi = { id ->
                                    // 模块自带 webroot 时，用与模块列表同一个 WebUI 宿主打开，
                                    // 不另造一套页面：KSU 的 su 桥、文件选择、返回手势都在那边。
                                    activity.startActivity(
                                        Intent(activity, me.diksu.ultra.foxsu.ui.webui.WebUIActivity::class.java)
                                            .setData(
                                                me.diksu.ultra.foxsu.ui.util.module.Shortcut.buildShortcutUri(
                                                    id,
                                                    me.diksu.ultra.foxsu.ui.screen.module.ShortcutType.WebUI,
                                                )
                                            )
                                    )
                                },
                                onRunAction = { id ->
                                    navigator.push(Route.ExecuteModuleAction(id, fromShortcut = true))
                                },
                            )
                        }
                        entry<Route.Customization>(swipeDismiss = swipeDismiss) {
                            // 定制页已统一为 Redesign 的 AgCustomization：五套皮肤共用同一套
                            // 定制能力（卡片颜色/透明度/胶囊/图标/字体…），不再各皮肤各一套。
                            run {
                                val settingsVm: me.diksu.ultra.foxsu.ui.viewmodel.SettingsViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
                                me.diksu.ultra.foxsu.ui.screen.ag.AgCustomization(
                                    onBack = dropUnlessResumed { navigator.pop() },
                                    settingsActions = me.diksu.ultra.foxsu.ui.screen.settings.SettingsScreenActions(
                                        onSetCheckUpdate = settingsVm::setCheckUpdate,
                                        onSetCheckModuleUpdate = settingsVm::setCheckModuleUpdate,
                                        onOpenTheme = {},
                                        onSetUiModeIndex = {},
                                        onSetThemeMode = settingsVm::setThemeMode,
                                        onOpenProfileTemplate = {},
                                        onSetSuCompatMode = settingsVm::setSuCompatMode,
                                        onSetKernelUmountEnabled = settingsVm::setKernelUmountEnabled,
                                        onSetSelinuxHideEnabled = settingsVm::setSelinuxHideEnabled,
                                        onSetSulogEnabled = settingsVm::setSulogEnabled,
                                        onSetAdbRootEnabled = settingsVm::setAdbRootEnabled,
                                        onSetShellSuAdbOnlyEnabled = settingsVm::setShellSuAdbOnlyEnabled,
                                        onSetDefaultUmountModules = settingsVm::setDefaultUmountModules,
                                        onSetEnableWebDebugging = settingsVm::setEnableWebDebugging,
                                        onSetAutoJailbreak = settingsVm::setAutoJailbreak,
                                        onSetUseSoftReboot = settingsVm::setUseSoftReboot,
                                        onOpenAbout = {},
                                        onSetEnableBlur = settingsVm::setEnableBlur,
                                        onSetEnableFloatingBottomBar = settingsVm::setEnableFloatingBottomBar,
                                        onSetEnableNavigationBadge = settingsVm::setEnableNavigationBadge,
                                        onSetEnablePredictiveBack = settingsVm::setEnablePredictiveBack,
                                        onSetEnableSwipeDismiss = settingsVm::setEnableSwipeDismiss,
                                        onSetPagerInterceptionMode = settingsVm::setPagerInterceptionMode,
                                        onSetPageScale = settingsVm::setPageScale,
                                        onSetModuleDescriptionMaxLines = settingsVm::setModuleDescriptionMaxLines,
                                    ),
                                    onOpenBuiltin = dropUnlessResumed { navigator.push(Route.Builtins) },
                                    onOpenDonate = dropUnlessResumed { navigator.push(Route.Donate) },
                                )
                            }
                        }
                        entry<Route.AppProfileTemplate>(swipeDismiss = swipeDismiss) { AppProfileTemplateScreen() }
                        entry<Route.TemplateEditor>(swipeDismiss = swipeDismiss) { key -> TemplateEditorScreen(key.template, key.readOnly) }
                        entry<Route.AppProfile>(swipeDismiss = swipeDismiss) { key -> AppProfileScreen(key.uid) }
                        entry<Route.ModuleRepo>(swipeDismiss = swipeDismiss) { ModuleRepoScreen() }
                        entry<Route.ModuleRepoDetail>(swipeDismiss = swipeDismiss) { key -> ModuleRepoDetailScreen(key.module) }
                        entry<Route.Install>(swipeDismiss = swipeDismiss) { InstallScreen() }
                        entry<Route.Flash>(swipeDismiss = if (flashBackEnabled) swipeDismiss else NavSwipeDirection.None) { key ->
                            FlashScreen(
                                key.flashIt,
                                onBackEnabledChange = { enabled ->
                                    if (navigator.current() == key) flashBackEnabled = enabled
                                },
                            )
                        }
                        entry<Route.ExecuteModuleAction>(swipeDismiss = swipeDismiss) { key ->
                            ExecuteModuleActionScreen(
                                key.moduleId,
                                key.fromShortcut
                            )
                        }
                        entry<Route.ScriptRun>(swipeDismiss = scriptRunSwipeDismiss) { key ->
                            ScriptRunScreen(
                                key.arg,
                                onBackEnabledChange = { enabled ->
                                    if (navigator.current() == key) scriptRunBackEnabled = enabled
                                },
                            )
                        }
                        entry<Route.Home>(swipeDismiss = swipeDismiss) { mainScreenEntry() }
                        entry<Route.SuperUser>(swipeDismiss = swipeDismiss) { mainScreenEntry() }
                        entry<Route.Module>(swipeDismiss = swipeDismiss) { mainScreenEntry() }
                        entry<Route.Settings>(swipeDismiss = swipeDismiss) { mainScreenEntry() }
                    }
                }

                Box(modifier = Modifier.fillMaxSize()) {
                    // 背景：全局壁纸优先（所有界面生效）
                    val customNow = CustomizationStore.current
                    if (customNow.wallpaperUri != null && customNow.wallpaperEnabled) {
                        WallpaperBackground(customization = customNow)
                    }
                    // Glass 模式：纯色底 + 三层独立径向光斑（.screen::before），只模糊光晕层
                    // 选了壁纸时整层让位，否则不透明的 gt.bg 会把壁纸盖住
                    if (uiMode == UiMode.Glass && !hasCustomWallpaper()) {
                        val gt = glassTokens()
                        Box(modifier = Modifier.fillMaxSize().background(gt.bg))
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .graphicsLayer { alpha = if (gt.isDark) 0.45f else 0.5f }
                                .blur(64.dp)
                        ) {
                            gt.auraLayers(0).forEach { brush ->
                                Box(modifier = Modifier.fillMaxSize().background(brush))
                            }
                        }
                    }
                    when (uiMode) {
                        UiMode.Material -> androidx.compose.material3.Scaffold(
                            containerColor = scaffoldBackgroundColor(
                                me.diksu.ultra.foxsu.ui.screen.editorial.edTokens().paper
                            )
                        ) { navDisplay() }

                        UiMode.Miuix -> Scaffold(containerColor = androidx.compose.ui.graphics.Color.Transparent) { navDisplay() }

                        UiMode.Fresh -> androidx.compose.material3.Scaffold(
                            containerColor = scaffoldBackgroundColor(MaterialTheme.colorScheme.surface)
                        ) { navDisplay() }

                        UiMode.Glass -> androidx.compose.material3.Scaffold(
                            containerColor = androidx.compose.ui.graphics.Color.Transparent
                        ) { navDisplay() }

                        UiMode.Nocturne -> androidx.compose.material3.Scaffold(
                            containerColor = androidx.compose.ui.graphics.Color.Transparent
                        ) { navDisplay() }
                    }

                    // 首次安装引导：只在从未标记过的情况下出现一次（Dialog 承载，天然屏蔽底层交互）
                    var showWelcome by remember {
                        mutableStateOf(!OnboardingPrefs.seen(activity))
                    }
                    if (showWelcome) {
                        WelcomeOverlay(
                            onFinish = {
                                OnboardingPrefs.markSeen(activity)
                                showWelcome = false
                            },
                        )
                    }

                    // 启动画面只保留系统 splash 一个，不再叠加 Compose 启动层
                }
            }
        }
        // The only piece of our own UI that runs inside a FoxSu style: a way back into the
        // manager settings. It gets its own miuix palette so it reads in light and dark FoxSu
        // looks instead of borrowing the library's light-only default.
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 8.dp, end = 12.dp),
        ) {
            MiuixTheme(
                controller = ThemeController(
                    colorSchemeMode = if (isSystemInDarkTheme()) ColorSchemeMode.Dark else ColorSchemeMode.Light,
                ),
            ) {
                MiuixTextButton(
                    text = stringResource(R.string.foxsu_manager_settings),
                    onClick = onOpenManagerSettings,
                )
            }
        }
    }
}


@Composable
private fun MainScreenBackHandler(
    mainState: MainPagerState,
    navController: Navigator,
) {
    val isPagerBackHandlerEnabled by remember {
        derivedStateOf {
            navController.current() is Route.Main && navController.backStackSize() == 1 && mainState.selectedPage != 0
        }
    }

    val navEventState = rememberNavigationEventState(NavigationEventInfo.None)

    NavigationBackHandler(
        state = navEventState,
        isBackEnabled = isPagerBackHandlerEnabled,
        onBackCompleted = {
            mainState.animateToPage(0)
        }
    )
}
