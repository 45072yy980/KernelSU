package me.diksu.ultra.foxsu.ui

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
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.PagerGestureNestedScrollConnection
import top.yukonga.miuix.kmp.utils.PagerInterceptionMode
import top.yukonga.miuix.kmp.utils.PagerNavigationSpringSpec
import top.yukonga.miuix.kmp.utils.pagerGestureOverride

class MainActivity : ComponentActivity() {

    private val intentChannel = Channel<Intent>(capacity = Channel.BUFFERED)
    private var contentReady = false
    private var splashStartedAt = 0L
    private val splashAnimationDurationMs = 500L


    @SuppressLint("UnusedMaterial3ScaffoldPaddingParameter")
    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        splashStartedAt = SystemClock.uptimeMillis()
        super.onCreate(savedInstanceState)
        splashScreen.setKeepOnScreenCondition {
            !contentReady || SystemClock.uptimeMillis() - splashStartedAt < splashAnimationDurationMs
        }

        val isManager = Natives.isManager
        if (isManager && Natives.kernelUAPIVersion == Natives.managerUAPIVersion) install()

        if (savedInstanceState == null) intent?.let { intentChannel.trySend(it) }

        // 启动时对一次账：系统可能在更新 / 清理后把 activity-alias 还原成清单默认值，
        // 那样桌面图标就不再跟随设置里的选择了。这里重新对齐一次（代价仅一次
        // getComponentEnabledSetting，已经一致时不做任何写入）。
        runCatching { LauncherIconManager.sync() }

        setContent {
            val viewModel = viewModel<MainActivityViewModel>()
            val uiState by viewModel.uiState.collectAsStateWithLifecycle()
            val selectedMainPage by viewModel.selectedMainPage.collectAsStateWithLifecycle()
            val appSettings = uiState.appSettings
            val uiMode = uiState.uiMode
            val darkMode = appSettings.colorMode.isDark || (appSettings.colorMode.isSystem && isSystemInDarkTheme())

            DisposableEffect(darkMode) {
                enableEdgeToEdge(
                    statusBarStyle = SystemBarStyle.auto(
                        android.graphics.Color.TRANSPARENT,
                        android.graphics.Color.TRANSPARENT
                    ) { darkMode },
                    navigationBarStyle = SystemBarStyle.auto(
                        android.graphics.Color.TRANSPARENT,
                        android.graphics.Color.TRANSPARENT
                    ) { darkMode },
                )
                window.isNavigationBarContrastEnforced = false
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
                                        startActivity(
                                            Intent(this@MainActivity, me.diksu.ultra.foxsu.ui.webui.WebUIActivity::class.java)
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
                            mutableStateOf(!OnboardingPrefs.seen(this@MainActivity))
                        }
                        if (showWelcome) {
                            WelcomeOverlay(
                                onFinish = {
                                    OnboardingPrefs.markSeen(this@MainActivity)
                                    showWelcome = false
                                },
                            )
                        }

                        // 启动画面只保留系统 splash 一个，不再叠加 Compose 启动层
                    }
                    SideEffect { contentReady = true }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        intentChannel.trySend(intent)
    }
}

/** 「滑动切换 Tab」的第 4 档：真正关闭。0/1/2 是 miuix 的三种拦截策略，都不能关。 */
private const val PAGER_MODE_OFF = 3

val LocalMainPagerState = staticCompositionLocalOf<MainPagerState> { error("LocalMainPagerState not provided") }

@SuppressLint("UnusedMaterial3ScaffoldPaddingParameter")
@Composable
fun MainScreen(
    initialPage: Int = 0,
    pagerInterceptionMode: Int = PagerInterceptionMode.Native.ordinal,
    onPageChanged: (Int) -> Unit = {},
) {
    val navController = LocalNavigator.current
    val enableBlur = LocalEnableBlur.current
    val enableFloatingBottomBar = LocalEnableFloatingBottomBar.current
    val enableFloatingBottomBarBlur = LocalEnableFloatingBottomBarBlur.current
    val useNavigationRail = useNavigationRail(enableFloatingBottomBar)
    val pagerState = rememberPagerState(initialPage = initialPage, pageCount = { MainPagerConfig.PAGE_COUNT })
    val mainPagerState = rememberMainPagerState(
        pagerState = pagerState,
        animatePageChanges = !useNavigationRail,
    )
    val isFullFeatured = Natives.isFullFeatured()
    // 第 4 档是真正的「关」。前 3 档对应 miuix 自己的拦截策略（原生 / 跨轴 / 点击暂停），
    // 没有一档是"不能滑"——所以关单独占一个 ordinal，不走 PagerInterceptionMode。
    val pagerOff = pagerInterceptionMode == PAGER_MODE_OFF
    val pagerMode = PagerInterceptionMode.entries.getOrElse(pagerInterceptionMode) {
        PagerInterceptionMode.Native
    }
    val interceptPagerGestures = !pagerOff && pagerMode == PagerInterceptionMode.CrossAxisInterceptor
    // Swiping between tabs works on every device, not only on full-featured kernels.
    // 关档时这里才是真的 false：之前是恒 true，所以「关」档形同虚设。
    val userScrollEnabled = !pagerOff

    val enableNavigationBadge = LocalEnableNavigationBadge.current
    val badgeEnabled = enableNavigationBadge && isFullFeatured
    val moduleViewModel = viewModel<ModuleViewModel>()
    val moduleUiState by moduleViewModel.uiState.collectAsStateWithLifecycle()

    val superUserViewModel = viewModel<SuperUserViewModel>()
    val grantedUidCount by remember(superUserViewModel) {
        superUserViewModel.uiState
            .map { state -> state.groupedApps.count { it.anyAllowSu } }
            .distinctUntilChanged()
    }.collectAsStateWithLifecycle(0)

    var startupPreloadStarted by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(isFullFeatured) {
        if (!isFullFeatured || startupPreloadStarted) {
            return@LaunchedEffect
        }

        moduleViewModel.initializePreferences()
        val moduleState = moduleViewModel.uiState.value
        if (!moduleState.hasLoaded) {
            if (!moduleState.isRefreshing) moduleViewModel.fetchModuleList()
            moduleViewModel.uiState.first { it.hasLoaded }
        }
        moduleViewModel.syncModuleUpdateInfo(moduleViewModel.uiState.value.modules)

        val superUserState = superUserViewModel.uiState.value
        if (!superUserState.hasLoaded) {
            superUserViewModel.initializePreferences()
            if (superUserState.isRefreshing) {
                superUserViewModel.uiState.first { it.hasLoaded }
            } else {
                superUserViewModel.loadAppList().join()
            }
        }

        startupPreloadStarted = true
    }

    // Loading the app list just for a badge is too expensive; read the kernel allowlist instead.
    var superuserCount by remember { mutableIntStateOf(0) }
    LaunchedEffect(badgeEnabled, grantedUidCount) {
        superuserCount = if (badgeEnabled) withContext(Dispatchers.IO) { getSuperuserCount() } else 0
    }

    val navigationBadge = if (badgeEnabled) {
        NavigationBadgeState(
            superuserCount = superuserCount,
            moduleEnabledCount = moduleUiState.modules.count { it.enabled },
            moduleUpdatableCount = moduleUiState.updateInfo.count { it.value.downloadUrl.isNotBlank() },
        )
    } else {
        NavigationBadgeState()
    }
    val uiMode = LocalUiMode.current
    val surfaceColor = when (uiMode) {
        UiMode.Material -> me.diksu.ultra.foxsu.ui.screen.editorial.edTokens().paper
        // Ag 皮肤（Miuix 通道）的页面底色是 160° 的 bg1→bg2 渐变，底部落到 bg2。
        // 这里必须用 bg2 而不是 MiuixTheme.surface：后者是纯白，底栏下方那段
        // "由透明渐变到页面色"的遮罩会以白色收尾，在 #F2F4FA 的页面上留下
        // 一条横贯全屏的白带（真机上就是胶囊下方那块白）。
        UiMode.Miuix -> me.diksu.ultra.foxsu.ui.screen.ag.Ag.Bg2
        UiMode.Fresh -> MaterialTheme.colorScheme.surface
        UiMode.Glass -> glassTokens().bg
        UiMode.Nocturne -> me.diksu.ultra.foxsu.ui.screen.nocturne.ncTokens().bg
    }
    val blurBackdrop = rememberBlurBackdrop(enableBlur)

    // 页面层只在这两种「真的要采样身后内容」的材质开启时才录进 RenderNode：
    // 液态玻璃（底栏折射）与实时模糊（底栏/顶栏磨砂）。默认都关，首帧依旧零开销。
    // （历史上这里恒为 true，导致所有皮肤首帧都要做一次全页录制 + 实时模糊采样，
    //  部分真机 GPU 直接崩溃，表现为启动页卡住后闪退。）
    val customNow = CustomizationStore.current
    val needsPageBackdrop = customNow.bottomBarLiquidGlass || enableBlur

    val backdrop = rememberLayerBackdrop {
        // ContentDrawScope 里的 drawRect 是成员函数，不需要 import
        drawRect(surfaceColor)
        drawContent()
    }

    // 胶囊读秒自动收起的共享状态：收起后点屏幕任意位置召回
    val dockReveal = rememberDockRevealState()

    val settledPage = mainPagerState.pagerState.settledPage
    LaunchedEffect(settledPage) {
        onPageChanged(settledPage)
    }

    val currentPage = mainPagerState.pagerState.currentPage
    LaunchedEffect(currentPage) {
        mainPagerState.syncPage()
    }

    MainScreenBackHandler(mainPagerState, navController)

    CompositionLocalProvider(
        LocalMainPagerState provides mainPagerState,
        LocalDockReveal provides dockReveal,
        // 底栏的液态玻璃需要采样这个 backdrop（Fresh 模式默认不启用 Miuix 的浮动底栏模糊，
        // 所以这里始终记录一份页面内容，开关打开就能立刻用）
        LocalAppBackdrop provides backdrop,
        // 底栏横滑跟着「滑动切换 Tab」走：关档时底栏也不再接管横滑
        me.diksu.ultra.foxsu.ui.component.bottombar.LocalDockSwipeEnabled provides !pagerOff,
    ) {
        val contentReady = rememberContentReady()
        // 页面沿点击方向「轻推 + 淡入」：由独立开关控制，不再依附水滴形变。
        // 两者审美取向不同——有人只想要色块不动、页面照常滑入。
        val pushOn = CustomizationStore.current.dockPagePush
        val pushDir = mainPagerState.lastDirection
        val pushAlpha = remember { Animatable(1f) }
        val pushX = remember { Animatable(0f) }
        LaunchedEffect(mainPagerState.selectedPage) {
            if (!pushOn || pushDir == 0) return@LaunchedEffect
            // 26dp 的推入量：14dp 在 6.7 寸屏上几乎看不出来，
            // 26dp 才够让「新页面从点击方向滑进来」这件事被眼睛捕捉到
            pushX.snapTo(26f * pushDir)
            pushAlpha.snapTo(0.55f)
            // 位移与淡入同步走完：stiffness 260 / 无回弹 ≈ 400ms 到位，
            // 和水滴指示器「前缘到站」是同一拍，两层动效不会各走各的。
            launch { pushAlpha.animateTo(1f, spring(dampingRatio = 1f, stiffness = 260f)) }
            pushX.animateTo(0f, spring(dampingRatio = 1f, stiffness = 260f))
        }
        val pushModifier = if (pushOn) {
            Modifier.graphicsLayer {
                translationX = pushX.value.dp.toPx()
                alpha = pushAlpha.value
            }
        } else {
            Modifier
        }
        val pagerContent = @Composable { bottomInnerPadding: Dp ->
            Box(
                modifier = (if (blurBackdrop != null) Modifier.layerBackdrop(blurBackdrop) else Modifier)
                    .dockRevealOnTouch(dockReveal)
                    .then(pushModifier)
            ) {
                HorizontalPager(
                    modifier = Modifier
                        .pagerGestureOverride(
                            pagerState = mainPagerState.pagerState,
                            mode = pagerMode,
                            enabled = userScrollEnabled,
                        )
                        .then(if (needsPageBackdrop) Modifier.layerBackdrop(backdrop) else Modifier),
                    state = mainPagerState.pagerState,
                    // 只多挂 1 页：3 页时左右两侧页面会被同时组合并持续跑各自的开场动画，
                    // 与「省电」的方向相反；1 页足够保证左右滑动不白屏。
                    beyondViewportPageCount = if (contentReady) 1 else 0,
                    overscrollEffect = null,
                    userScrollEnabled = userScrollEnabled && !interceptPagerGestures,
                    pageNestedScrollConnection = if (interceptPagerGestures) {
                        PagerGestureNestedScrollConnection
                    } else {
                        pageNestedScrollConnection(
                            state = mainPagerState.pagerState,
                            orientation = androidx.compose.foundation.gestures.Orientation.Horizontal,
                        )
                    },
                    flingBehavior = flingBehavior(
                        state = mainPagerState.pagerState,
                        snapAnimationSpec = PagerNavigationSpringSpec,
                    ),
                ) { page ->
                    val isCurrentPage = page == settledPage
                    when (page) {
                        0 -> if (contentReady || isCurrentPage) HomePager(navController, bottomInnerPadding, isCurrentPage, navigationBadge)
                        1 -> if (contentReady || isCurrentPage) SuperUserPager(navController, bottomInnerPadding, isCurrentPage)
                        2 -> if (contentReady || isCurrentPage) ModulePager(bottomInnerPadding, isCurrentPage)
                        3 -> if (contentReady || isCurrentPage) SettingPager(navController, bottomInnerPadding, isCurrentPage)
                    }
                }
            }
        }

        if (useNavigationRail) {
            val startInsets = WindowInsets.systemBars.union(WindowInsets.displayCutout)
                .only(WindowInsetsSides.Start)
            val navBarBottomPadding = WindowInsets.systemBars.asPaddingValues().calculateBottomPadding()

            when (uiMode) {
                UiMode.Material -> androidx.compose.material3.Scaffold(
                    containerColor = scaffoldBackgroundColor(MaterialTheme.colorScheme.surfaceContainer)
                ) {
                    Row {
                        SideRail(navigationBadge, Modifier.padding(end = 12.dp))
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .consumeWindowInsets(startInsets)
                        ) {
                            pagerContent(navBarBottomPadding)
                        }
                    }
                }

                UiMode.Miuix -> Scaffold { _ ->
                    Row {
                        SideRail(navigationBadge, Modifier.padding(end = 12.dp))
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .consumeWindowInsets(startInsets)
                        ) {
                            pagerContent(navBarBottomPadding)
                        }
                    }
                }

                UiMode.Fresh -> androidx.compose.material3.Scaffold(
                    containerColor = scaffoldBackgroundColor(MaterialTheme.colorScheme.surface)
                ) {
                    Row {
                        SideRail(navigationBadge, Modifier.padding(end = 12.dp))
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .consumeWindowInsets(startInsets)
                        ) {
                            pagerContent(navBarBottomPadding)
                        }
                    }
                }

                UiMode.Glass -> androidx.compose.material3.Scaffold(
                    containerColor = androidx.compose.ui.graphics.Color.Transparent
                ) {
                    Row {
                        SideRail(navigationBadge, Modifier.padding(end = 12.dp))
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .consumeWindowInsets(startInsets)
                        ) {
                            pagerContent(navBarBottomPadding)
                        }
                    }
                }

                UiMode.Nocturne -> androidx.compose.material3.Scaffold(
                    containerColor = androidx.compose.ui.graphics.Color.Transparent
                ) {
                    Row {
                        SideRail(navigationBadge, Modifier.padding(end = 12.dp))
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .consumeWindowInsets(startInsets)
                        ) {
                            pagerContent(navBarBottomPadding)
                        }
                    }
                }
            }
        } else {
            // 壁纸模式下这层"由透明渐变到页面色"的遮罩要整层让位：
            // 否则胶囊下方会出现一条与壁纸无关的纯色带。
            val dockScrim = if (hasCustomWallpaper()) Color.Transparent else surfaceColor
            val bottomBar = @Composable {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .dockRevealOnTouch(dockReveal)
                        // 胶囊下方必须是实底：设计稿里 .dock 是浮在不透明页面底上的，
                        // 之前这里完全透明，页面内容/壁纸会从胶囊四周和下方透出来，
                        // 看起来像"底栏是空的"。用页面色做一段由透明到实色的过渡，
                        // 既盖住透出的内容，又不会在浅色底上留下一条生硬的横线。
                        .background(
                            Brush.verticalGradient(
                                0f to Color.Transparent,
                                0.45f to dockScrim,
                                1f to dockScrim,
                            )
                        )
                ) {
                    BottomBar(
                        blurBackdrop = blurBackdrop,
                        backdrop = backdrop,
                        navigationBadge = navigationBadge,
                        modifier = Modifier.align(Alignment.BottomCenter),
                        onOpenDonate = { navController.push(Route.Donate) },
                    )
                }
            }

            // 所有皮肤共用同一套底栏宿主：胶囊样式是全局设置，
            // 所以侧边抽屉（v5）在其他皮肤下也必须走「浮层」而不是 bottomBar 槽位
            val sideDock = useSideDock()
            // 侧边胶囊可左右停靠（定制 → 底部导航栏 → 胶囊停靠）
            val sideDockRight = me.diksu.ultra.foxsu.ui.component.bottombar.sideDockOnRight
            val noBar: @Composable () -> Unit = {}
            val scaffoldColor = when (uiMode) {
                UiMode.Material -> scaffoldBackgroundColor(MaterialTheme.colorScheme.surfaceContainer)
                UiMode.Fresh -> scaffoldBackgroundColor(MaterialTheme.colorScheme.surface)
                else -> androidx.compose.ui.graphics.Color.Transparent
            }
            androidx.compose.material3.Scaffold(
                bottomBar = if (sideDock) noBar else bottomBar,
                containerColor = scaffoldColor,
            ) { innerPadding ->
                // 整块内容区都监听一次触摸：胶囊收起后，点任何地方（含侧边抽屉浮层区域）都能召回
                Box(modifier = Modifier.fillMaxSize().dockRevealOnTouch(dockReveal)) {
                    pagerContent(if (sideDock) 0.dp else innerPadding.calculateBottomPadding())
                    if (sideDock) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = if (sideDockRight) Alignment.CenterEnd else Alignment.CenterStart,
                        ) {
                            BottomBar(
                                blurBackdrop = blurBackdrop,
                                backdrop = backdrop,
                                navigationBadge = navigationBadge,
                                onOpenDonate = { navController.push(Route.Donate) },
                            )
                        }
                    }
                }
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
