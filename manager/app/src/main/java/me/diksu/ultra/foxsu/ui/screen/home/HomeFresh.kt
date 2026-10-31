package me.diksu.ultra.foxsu.ui.screen.home

import me.diksu.ultra.foxsu.ui.screen.ag.particleOutline
import me.diksu.ultra.foxsu.ui.component.bottombar.useSideDock
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.Autorenew
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Cached
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeveloperBoard
import androidx.compose.material.icons.filled.DeveloperMode
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.SettingsBackupRestore
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.filled.Tag
import androidx.compose.material.icons.filled.Usb
import androidx.compose.material.icons.filled.VolunteerActivism
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.animateColorAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import kotlin.math.roundToInt
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.diksu.ultra.BuildConfig
import me.diksu.ultra.Natives
import me.diksu.ultra.foxsu.data.settings.Customization
import me.diksu.ultra.foxsu.data.settings.CustomizationStore
import me.diksu.ultra.foxsu.data.settings.HERO_SLOT
import me.diksu.ultra.foxsu.data.repository.SettingsRepositoryImpl
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringArrayResource
import me.diksu.ultra.R
import me.diksu.ultra.foxsu.ui.component.bouncyClick
import me.diksu.ultra.foxsu.ui.LocalUiMode
import me.diksu.ultra.foxsu.ui.UiMode
import me.diksu.ultra.foxsu.ui.component.CardAppearanceDialog
import me.diksu.ultra.foxsu.ui.component.GlassSheen
import me.diksu.ultra.foxsu.ui.navigation3.LocalNavigator
import me.diksu.ultra.foxsu.ui.navigation3.Route
import me.diksu.ultra.foxsu.ui.component.CardSurface
import me.diksu.ultra.foxsu.ui.component.KsuIsValid
import me.diksu.ultra.foxsu.ui.component.cardContentColor
import me.diksu.ultra.foxsu.ui.component.freshCardBorder
import me.diksu.ultra.foxsu.ui.component.glassNeedsLightText
import me.diksu.ultra.foxsu.ui.component.freshCardColor
import me.diksu.ultra.foxsu.ui.component.cardNeedsLightText
import me.diksu.ultra.foxsu.ui.component.frostedChromeColor
import me.diksu.ultra.foxsu.ui.component.hasWallpaperBackdrop
import me.diksu.ultra.foxsu.ui.component.isNightScheme
import me.diksu.ultra.foxsu.ui.theme.LaunchTint
import me.diksu.ultra.foxsu.ui.theme.UiPalette
import me.diksu.ultra.foxsu.ui.component.resolveCardSurface
import me.diksu.ultra.foxsu.ui.component.supportingTextAlpha
import me.diksu.ultra.foxsu.ui.component.bottombar.NavigationBadgeState
import me.diksu.ultra.foxsu.ui.component.rememberWallpaper
import me.diksu.ultra.foxsu.ui.component.rebootlistpopup.RebootListOption
import me.diksu.ultra.foxsu.ui.component.rebootlistpopup.getRebootListOption
import me.diksu.ultra.foxsu.ui.component.rebootlistpopup.rememberRebootAction
import me.diksu.ultra.foxsu.ui.uiModeLabelRes
import me.diksu.ultra.foxsu.ui.component.ImageCropDialog
import me.diksu.ultra.foxsu.ui.util.savePickedImage

/** Resolved colours for one card: global settings with optional per-card override. */
/** Things worth telling the user about, all derived from real state. */
internal enum class FreshIssue {
    RootMissing,
    KernelOutdated,
    ManagerOutdated,
    SafeMode,
    NotGki,
    SelinuxPermissive,
    UpdateAvailable,
    LkmUpdate,
}

internal fun HomeUiState.freshIssues(): List<FreshIssue> = buildList {
    if (ksuVersion != null && !isRootAvailable) add(FreshIssue.RootMissing)
    if (requiresNewKernel) add(FreshIssue.KernelOutdated)
    if (requiresNewManager) add(FreshIssue.ManagerOutdated)
    if (isSafeMode) add(FreshIssue.SafeMode)
    if (showGkiWarning) add(FreshIssue.NotGki)
    if (isSELinuxPermissive) add(FreshIssue.SelinuxPermissive)
    if (hasUpdate) add(FreshIssue.UpdateAvailable)
    if (showLkmUpdate) add(FreshIssue.LkmUpdate)
}

@Composable
fun HomePagerFresh(
    state: HomeUiState,
    actions: HomeActions,
    bottomInnerPadding: Dp,
    navigationBadge: NavigationBadgeState,
    onOpenTab: (Int) -> Unit,
) {
    val issues = state.freshIssues()
    // 侧边抽屉（v5）时给列表整体让出左侧，避免被浮层胶囊压住
    val sideDock = useSideDock()
    var showStylePicker by remember { mutableStateOf(false) }
    var showHeroMenu by remember { mutableStateOf(false) }
    var kpiMenuIndex by remember { mutableStateOf(-1) }
    var kpiPickIndex by remember { mutableStateOf(0) }
    var pendingCrop by remember { mutableStateOf<PendingCrop?>(null) }
    val currentUiMode = LocalUiMode.current

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val heroPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val saved = withContext(Dispatchers.IO) {
                savePickedImage(context, uri, "hero_raw.jpg", maxSide = 2000)
            }
            if (saved == null) {
                Toast.makeText(
                    context,
                    R.string.fx_customization_wallpaper_failed,
                    Toast.LENGTH_SHORT,
                ).show()
            } else {
                pendingCrop = PendingCrop(saved, 2.6f, 0, 0)
            }
        }
    }

    val kpiPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val index = kpiPickIndex
        scope.launch {
            val saved = withContext(Dispatchers.IO) {
                savePickedImage(context, uri, "kpi_raw_$index.jpg", maxSide = 2000)
            }
            if (saved == null) {
                Toast.makeText(
                    context,
                    R.string.fx_customization_wallpaper_failed,
                    Toast.LENGTH_SHORT,
                ).show()
            } else {
                pendingCrop = PendingCrop(saved, 1.08f, 1, index)
            }
        }
    }

    val custom = CustomizationStore.current
    val night = isNightScheme()
    val cardColor = if (custom.cardColor == 0 || night) {
        freshCardColor().let { it.copy(alpha = it.alpha * custom.cardAlpha) }
    } else {
        Color(custom.cardColor).copy(alpha = custom.cardAlpha)
    }
    val cardContent = if (custom.cardColor == 0) {
        MaterialTheme.colorScheme.onSurface
    } else if (cardColor.luminance() > 0.55f) {
        Color(0xFF141A14)
    } else {
        Color.White
    }
    val cardAccent = if (custom.cardColor == 0) {
        MaterialTheme.colorScheme.primary
    } else {
        cardContent
    }

    // 顶部大卡 + 四张卡各自可覆盖卡面颜色与透明度
    val heroSurface = resolveCardSurface(HERO_SLOT)
    val surface1 = resolveCardSurface(HERO_SLOT + 1)
    val surface2 = resolveCardSurface(HERO_SLOT + 2)
    val surface3 = resolveCardSurface(HERO_SLOT + 3)
    val surface4 = resolveCardSurface(HERO_SLOT + 4)

    // 顶部大卡可以单独铺一张图片；有图时文字转为白色，靠暗化蒙层保证可读
    val heroCustom = if (custom.heroWallpaperUri != null) custom else null
    val heroBitmap = rememberWallpaper(custom.heroWallpaperUri)
    val heroOn = heroBitmap != null
    val heroContent = if (heroOn) Color.White else heroSurface.content

    val navigationBarPadding = if (!Natives.isFullFeatured()) {
        WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    } else {
        0.dp
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // 标题栏与下方内容同一套磨砂材质：不再用不透明的 surface 白条，
        // 有壁纸时铺同款雾面，让头部和内容连贯成一张玻璃画布
        // 玻璃/夜间模式下标题栏是深色雾面，强制使用浅色文字，
        // 避免沿用浅色主题的深色 onSurface 造成“深底深字”
        CompositionLocalProvider(
            LocalContentColor provides if (cardNeedsLightText()) {
                Color.White
            } else {
                MaterialTheme.colorScheme.onSurface
            }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    // 顶栏是 Column 里的固定区，不会盖住列表，所以直接透明——
                    // 之前这里铺的是不透明 surface，在浅色主题下就是一条明显的白块。
                    .background(Color.Transparent)
                    .padding(
                        start = 16.dp,
                        end = 16.dp,
                        top = WindowInsets.systemBars.asPaddingValues().calculateTopPadding() + 8.dp,
                        bottom = 10.dp,
                    ),
            ) {
                FreshHeader(onOpenTheme = { showStylePicker = true })
            }
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            contentPadding = PaddingValues(
                start = if (sideDock) 96.dp else 0.dp,
                top = 14.dp,
                bottom = bottomInnerPadding + navigationBarPadding + 16.dp,
            ),
        ) {
            item {
            Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                if (issues.isNotEmpty()) {
                    FreshIssueList(
                        issues = issues,
                        onInstall = actions.onInstallClick,
                        canInstallKernel = state.canInstallKernelUpdate,
                        containerColor = cardColor,
                        contentColor = cardContent,
                        latestVersionCode = state.latestVersionInfo.versionCode,
                        currentVersion = BuildConfig.VERSION_NAME,
                        onLongPress = { showHeroMenu = true },
                    )
                    Spacer(Modifier.height(12.dp))
                }

                // 状态卡：运行模式 + 版本 + 刷入入口（原「运行模式 / 刷入镜像」合并在此）
                FreshStatusCard(
                    state = state,
                    onInstall = actions.onInstallClick,
                    onJailbreak = actions.onJailbreakClick,
                    onCheckUpdate = actions.onCheckUpdate,
                    onRefreshImage = actions.onRefreshImage,
                    containerColor = if (heroOn) Color.Transparent else heroSurface.container,
                    contentColor = heroContent,
                    hero = heroCustom,
                    heroBitmap = heroBitmap,
                    cardBorder = heroSurface.border,
                    onLongPress = { showHeroMenu = true },
                )

                Spacer(Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    FreshKpiCard(
                        modifier = Modifier.weight(1f),
                        iconRes = R.drawable.fx_ic_d_phone,
                        title = stringResource(R.string.fx_home_device_model),
                        value = state.systemInfo.deviceModel,
                        caption = stringResource(R.string.fx_fresh_home_device_sub),
                        containerColor = surface1.container,
                        contentColor = surface1.content,
                        accentColor = surface1.accent,
                        cardBorder = surface1.border,
                        imageUri = if (custom.cardGlass) null else custom.kpiImages.getOrNull(0),
                        imageAlpha = custom.kpiAlphas.getOrNull(0) ?: 0.7f,
                        onLongPress = { kpiMenuIndex = 0 },
                        onClick = null,
                    )
                    FreshKpiCard(
                        modifier = Modifier.weight(1f),
                        iconRes = R.drawable.fx_ic_d_palette,
                        title = stringResource(R.string.fx_settings_ui_mode),
                        value = stringResource(uiModeLabelRes(currentUiMode)),
                        caption = stringResource(R.string.fx_fresh_home_style_switch),
                        containerColor = surface2.container,
                        contentColor = surface2.content,
                        accentColor = surface2.accent,
                        cardBorder = surface2.border,
                        imageUri = if (custom.cardGlass) null else custom.kpiImages.getOrNull(1),
                        imageAlpha = custom.kpiAlphas.getOrNull(1) ?: 0.7f,
                        onLongPress = { kpiMenuIndex = 1 },
                        onClick = { showStylePicker = true },
                    )
                }

                Spacer(Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    FreshKpiCard(
                        modifier = Modifier.weight(1f),
                        iconRes = R.drawable.fx_ic_d_shield,
                        title = stringResource(R.string.fx_superuser),
                        value = navigationBadge.superuserCount.toString(),
                        caption = stringResource(R.string.fx_fresh_home_superuser_sub),
                        containerColor = surface3.container,
                        contentColor = surface3.content,
                        accentColor = surface3.accent,
                        cardBorder = surface3.border,
                        imageUri = if (custom.cardGlass) null else custom.kpiImages.getOrNull(2),
                        imageAlpha = custom.kpiAlphas.getOrNull(2) ?: 0.7f,
                        onLongPress = { kpiMenuIndex = 2 },
                        onClick = { onOpenTab(1) },
                    )
                    FreshKpiCard(
                        modifier = Modifier.weight(1f),
                        iconRes = R.drawable.fx_ic_d_grid,
                        title = stringResource(R.string.fx_module),
                        value = navigationBadge.moduleEnabledCount.toString(),
                        caption = stringResource(R.string.fx_fresh_home_module_sub),
                        containerColor = surface4.container,
                        contentColor = surface4.content,
                        accentColor = surface4.accent,
                        cardBorder = surface4.border,
                        imageUri = if (custom.cardGlass) null else custom.kpiImages.getOrNull(3),
                        imageAlpha = custom.kpiAlphas.getOrNull(3) ?: 0.7f,
                        onLongPress = { kpiMenuIndex = 3 },
                        onClick = { onOpenTab(2) },
                    )
                }

                Spacer(Modifier.height(12.dp))

                FreshSecurityGroup(
                    systemInfo = state.systemInfo,
                    containerColor = cardColor,
                    contentColor = cardContent,
                )

                Spacer(Modifier.height(12.dp))

                FreshSupportGroup(
                    onOpenUrl = actions.onOpenUrl,
                    containerColor = cardColor,
                    contentColor = cardContent,
                )

                Spacer(Modifier.height(12.dp))

                val (kc1, kt1) = rainbowPair(5)
                FreshKernelCard(
                    iconRes = R.drawable.fx_ic_d_chip,
                    title = stringResource(R.string.fx_home_kernel),
                    value = state.systemInfo.kernelVersion,
                    containerColor = kc1,
                    contentColor = kt1,
                )

                Spacer(Modifier.height(10.dp))

                val (kc2, kt2) = rainbowPair(0)
                FreshKernelCard(
                    iconRes = R.drawable.fx_ic_d_chip,
                    title = stringResource(R.string.fx_home_fingerprint),
                    value = state.systemInfo.fingerprint,
                    containerColor = kc2,
                    contentColor = kt2,
                )

            }
        }
        }
    }

    pendingCrop?.let { crop ->
        ImageCropDialog(
            sourcePath = crop.path,
            aspectRatio = crop.aspect,
            outputName = if (crop.kind == 0) "hero_card.jpg" else "kpi_card_${crop.index}.jpg",
            onConfirm = { cropped ->
                if (crop.kind == 0) {
                    CustomizationStore.setHeroWallpaper(cropped)
                } else {
                    CustomizationStore.setKpiImage(crop.index, cropped)
                }
                pendingCrop = null
            },
            onDismiss = { pendingCrop = null },
        )
    }

    if (showHeroMenu) {
        CardAppearanceDialog(
            slot = HERO_SLOT,
            title = stringResource(R.string.fx_fresh_home_hero_title),
            description = stringResource(R.string.fx_fresh_home_hero_desc),
            hasImage = heroOn,
            imageAlpha = custom.heroDim,
            imageAlphaRange = 0f..0.8f,
            imageAlphaLabel = stringResource(R.string.fx_customization_dim),
            onImageAlphaChange = CustomizationStore::setHeroDim,
            onPickImage = {
                showHeroMenu = false
                heroPicker.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                )
            },
            onRemoveImage = {
                showHeroMenu = false
                CustomizationStore.setHeroWallpaper(null)
            },
            onDismiss = { showHeroMenu = false },
        )
    }

    val kpiIndex = kpiMenuIndex
    if (kpiIndex >= 0) {
        CardAppearanceDialog(
            slot = kpiIndex + 1,
            title = stringResource(R.string.fx_fresh_home_kpi_bg_title),
            description = stringResource(R.string.fx_fresh_home_kpi_bg_desc),
            hasImage = custom.kpiImages.getOrNull(kpiIndex) != null,
            imageAlpha = custom.kpiAlphas.getOrNull(kpiIndex) ?: 0.7f,
            imageAlphaRange = 0.2f..1f,
            imageAlphaLabel = stringResource(R.string.fx_fresh_home_kpi_alpha),
            onImageAlphaChange = { CustomizationStore.setKpiAlpha(kpiIndex, it) },
            onPickImage = {
                kpiPickIndex = kpiIndex
                kpiMenuIndex = -1
                kpiPicker.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                )
            },
            onRemoveImage = {
                CustomizationStore.setKpiImage(kpiIndex, null)
                kpiMenuIndex = -1
            },
            onDismiss = { kpiMenuIndex = -1 },
        )
    }

    if (showStylePicker) {
        FreshStylePickerDialog(
            current = currentUiMode,
            onDismiss = { showStylePicker = false },
        )
    }
}

@Composable
private fun FreshStylePickerDialog(
    current: UiMode,
    onDismiss: () -> Unit,
) {
    me.diksu.ultra.foxsu.ui.component.StylePickerDialog(current = current, onDismiss = onDismiss)
}

@Composable
private fun FreshHeader(onOpenTheme: () -> Unit) {
    val navigator = LocalNavigator.current
    var showRebootDialog by remember { mutableStateOf(false) }

    val phrases = stringArrayResource(R.array.fx_home_witty_phrases)
    var phraseIndex by remember { mutableStateOf((0 until phrases.size).random()) }
    LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(9000)
            phraseIndex = (phraseIndex + 1) % phrases.size
        }
    }

    // 参考图：左侧深墨圆底 + 白色狐标，右侧白色圆按钮
    val night = isNightScheme()
    val logoBg = if (night) Color(0xFF2A2A30) else Color(0xFF23232A)
    val glyphTint = Color.White
    val buttonBg = if (night) Color(0xFF2A2A30) else Color.White
    val buttonTint = if (night) Color.White else Color(0xFF1B1B1F)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // 深色圆底（统一 46dp）+ 彩色全局狐狸
        Surface(
            shape = CircleShape,
            color = logoBg,
            shadowElevation = 0.dp,
            modifier = Modifier.size(46.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                // Pages 的界面图标改为启动器图标（与桌面一致）
                me.diksu.ultra.foxsu.ui.component.LauncherMark(
                    modifier = Modifier.size(36.dp),
                    cornerPercent = 28,
                )
            }
        }

        Spacer(Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            val custom = CustomizationStore.current
            val customTitle = custom.homeTitle
            // 定制 → 标题栏 选过颜色就用它；没设过跟随全局强调色（副标题仍跟随底色）
            val baseText = LocalContentColor.current
            val titleColor = me.diksu.ultra.foxsu.ui.theme.TitleInk.solid(
                me.diksu.ultra.foxsu.ui.theme.currentAccent.primary
            )
            Text(
                text = customTitle.ifBlank { stringResource(R.string.app_name) },
                fontSize = 24.sp,
                lineHeight = 28.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = (-0.4).sp,
                color = titleColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            androidx.compose.animation.Crossfade(targetState = phraseIndex, label = "witty") { index ->
                Text(
                    text = phrases.getOrElse(index) { "" },
                    fontSize = 11.5.sp,
                    letterSpacing = 0.1.sp,
                    color = baseText.copy(alpha = if (cardNeedsLightText()) 0.72f else 0.55f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        Spacer(Modifier.width(10.dp))

        // 白色胶囊：作者 + 电源两个圆图标并排（与之前一致的胶囊形态）
        Surface(
            shape = RoundedCornerShape(50),
            color = buttonBg,
            shadowElevation = 0.dp,
            modifier = Modifier.height(44.dp),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // 主题：直接调起皮肤选择，省得为了换个主题先进设置
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .bouncyClick { onOpenTheme() },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Filled.Palette,
                        contentDescription = "主题",
                        tint = buttonTint,
                        modifier = Modifier.size(20.dp),
                    )
                }
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .bouncyClick { navigator.push(Route.Author) },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        painter = painterResource(R.drawable.fx_ic_author),
                        contentDescription = stringResource(R.string.fx_author_title),
                        tint = buttonTint,
                        modifier = Modifier.size(21.dp),
                    )
                }
                KsuIsValid {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .bouncyClick { showRebootDialog = true },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.fx_ic_power),
                            contentDescription = stringResource(R.string.fx_reboot),
                            tint = buttonTint,
                            modifier = Modifier.size(21.dp),
                        )
                    }
                }
            }
        }
    }

    if (showRebootDialog) {
        FreshRebootDialog(onDismiss = { showRebootDialog = false })
    }
}

/** 3 x 3 grid of power options, replacing the old dropdown list. */
@Composable
private fun FreshRebootDialog(onDismiss: () -> Unit) {
    val allOptions = getRebootListOption()
    val onReboot = rememberRebootAction()
    val night = isNightScheme()
    val glass = CustomizationStore.current.cardGlass
    val glassLightText = glassNeedsLightText()
    val sheetColor = when {
        // 玻璃 + 深色壁纸：用深色磨砂面板，白字才看得见
        glass && glassLightText -> Color(0xFF1C1C1E).copy(alpha = 0.94f)
        glass -> Color.White.copy(alpha = 0.90f)
        night -> freshCardColor().copy(alpha = 0.92f)
        else -> Color.White
    }

    // 重启 / 软重启走滑动，其余做成等大的胶囊行
    val slideOptions = allOptions.filter { it.reason == "" || it.reason == "soft_reboot" }
    val rowOptions = allOptions.filter { it.reason != "" && it.reason != "soft_reboot" }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(30.dp),
            color = sheetColor,
            border = BorderStroke(1.dp, Color.White.copy(alpha = if (night) 0.12f else 0.55f)),
            shadowElevation = 0.dp,
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 18.dp, vertical = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                // 参考图：浅紫圆底 + 薰衣草色图标
                Surface(
                    shape = CircleShape,
                    color = if (night) Color(0xFF262437) else UiPalette.HeroSurface,
                    contentColor = if (night) Color(0xFFA99DF0) else UiPalette.HeroAccent,
                    modifier = Modifier.size(56.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            painter = painterResource(R.drawable.fx_ic_power),
                            contentDescription = null,
                            modifier = Modifier.size(24.dp),
                        )
                    }
                }
                Spacer(Modifier.height(10.dp))
                Text(
                    text = stringResource(R.string.fx_reboot),
                    fontSize = 19.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.fx_fresh_reboot_slide_hint),
                    fontSize = 11.5.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                )
                Spacer(Modifier.height(16.dp))

                rowOptions.chunked(2).forEachIndexed { rowIndex, rowChunk ->
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        rowChunk.forEachIndexed { colIndex, option ->
                            val pair = if (night) {
                                UiPalette.RebootTonesDark[(rowIndex * 2 + colIndex) % UiPalette.RebootTonesDark.size]
                            } else {
                                UiPalette.RebootTones[(rowIndex * 2 + colIndex) % UiPalette.RebootTones.size]
                            }
                            RebootRow(
                                option = option,
                                containerColor = pair.first,
                                contentColor = pair.second,
                                onClick = {
                                    onDismiss()
                                    onReboot(option.reason)
                                },
                                modifier = Modifier.weight(1f),
                            )
                        }
                        if (rowChunk.size == 1) Spacer(Modifier.weight(1f))
                    }
                    Spacer(Modifier.height(10.dp))
                }

                Spacer(Modifier.height(2.dp))
                slideOptions.forEachIndexed { index, option ->
                    SlideToConfirm(
                        label = stringResource(option.labelRes),
                        containerColor = if (night) {
                            Color(0xFF24242E)
                        } else {
                            // 滑动确认条用主卡淡紫，和整体一套色
                            UiPalette.HeroSurface
                        },
                        onConfirm = {
                            onDismiss()
                            onReboot(option.reason)
                        },
                    )
                    Spacer(Modifier.height(10.dp))
                }

                Spacer(Modifier.height(2.dp))
                Text(
                    text = stringResource(R.string.fx_fresh_home_cancel),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .clickable(onClick = onDismiss)
                        .padding(vertical = 12.dp),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** 等高的胶囊行：圆形图标 + 文字（+ 箭头），用于其余重启方式。 */
@Composable
private fun RebootRow(
    option: RebootListOption,
    containerColor: Color,
    contentColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .heightIn(min = 58.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(containerColor)
            .clickable(onClick = onClick)
            .padding(horizontal = 11.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(
            shape = RoundedCornerShape(10.dp),
            color = Color.White.copy(alpha = if (isNightScheme()) 0.10f else 1f),
            contentColor = contentColor,
            modifier = Modifier.size(30.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    painter = painterResource(rebootIconRes(option.reason)),
                    contentDescription = null,
                    modifier = Modifier.size(15.dp),
                )
            }
        }
        Spacer(Modifier.width(8.dp))
        Text(
            text = stringResource(option.labelRes),
            fontSize = 11.sp,
            lineHeight = 14.sp,
            fontWeight = FontWeight.Bold,
            color = contentColor,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = contentColor.copy(alpha = 0.45f),
            modifier = Modifier.size(16.dp),
        )
    }
}

/** 滑动确认条：拖动时轨道逐渐染色，到底松手执行，未到底回弹。 */
@Composable
private fun SlideToConfirm(
    label: String,
    containerColor: Color,
    onConfirm: () -> Unit,
) {
    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
    val density = androidx.compose.ui.platform.LocalDensity.current
    var offsetPx by remember { mutableStateOf(0f) }
    var trackWidth by remember { mutableStateOf(0f) }
    val thumbSize = 46.dp
    val thumbPx = with(density) { thumbSize.toPx() }
    val maxOffset = (trackWidth - thumbPx).coerceAtLeast(0f)
    val progress = if (maxOffset > 0f) (offsetPx / maxOffset).coerceIn(0f, 1f) else 0f

    // 拖动进度动画：轨道自带七彩，滑动后逐渐转为确认色
    val animatedProgress by animateFloatAsState(progress, spring(stiffness = 700f), label = "slideFill")
    val confirmColor = androidx.compose.ui.graphics.lerp(
        containerColor,
        Color(0xFF3E9E5A),
        (animatedProgress * 0.85f).coerceIn(0f, 1f),
    )
    val thumbColor = androidx.compose.ui.graphics.lerp(
        Color(0xFF3E9E5A),
        Color(0xFF2E8B4F),
        (animatedProgress * 2f).coerceIn(0f, 1f),
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .clip(RoundedCornerShape(28.dp))
            .background(containerColor)
            .onSizeChanged { trackWidth = it.width.toFloat() },
        contentAlignment = Alignment.CenterStart,
    ) {
        // 进度填充层
        Box(
            modifier = Modifier
                .fillMaxWidth(animatedProgress.coerceIn(0f, 1f))
                .height(56.dp)
                .background(confirmColor),
        )
        Text(
            text = label,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface.copy(
                alpha = (0.5f * (1f - animatedProgress)).coerceAtLeast(0.12f)
            ),
        )
        Surface(
            shape = CircleShape,
            color = thumbColor,
            contentColor = Color.White,
            modifier = Modifier
                .padding(5.dp)
                .size(thumbSize)
                .offset { IntOffset(offsetPx.roundToInt(), 0) }
                .draggable(
                    orientation = Orientation.Horizontal,
                    state = rememberDraggableState { delta ->
                        offsetPx = (offsetPx + delta).coerceIn(0f, maxOffset)
                    },
                    onDragStopped = {
                        if (offsetPx >= maxOffset * 0.82f) {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onConfirm()
                        } else {
                            offsetPx = 0f
                        }
                    },
                ),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    modifier = Modifier.size(22.dp),
                )
            }
        }
    }
}

@Composable
private fun RebootGridCell(
    option: RebootListOption,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    containerColor: Color? = null,
    contentColor: Color? = null,
) {
    val tileBg = containerColor ?: MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
    val tileFg = contentColor ?: MaterialTheme.colorScheme.primary
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Surface(
            shape = CircleShape,
            color = tileBg,
            contentColor = tileFg,
            modifier = Modifier.size(48.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    painter = painterResource(rebootIconRes(option.reason)),
                    contentDescription = null,
                    modifier = Modifier.size(22.dp),
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(option.labelRes),
            style = MaterialTheme.typography.labelSmall,
            color = tileFg,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

private fun rebootIconRes(reason: String): Int = when (reason) {
    "userspace" -> R.drawable.fx_ic_d_refresh
    "soft_reboot" -> R.drawable.fx_ic_d_refresh
    "recovery" -> R.drawable.fx_ic_d_refresh
    "bootloader" -> R.drawable.fx_ic_d_boot
    "download" -> R.drawable.fx_ic_d_download
    "edl" -> R.drawable.fx_ic_d_tool
    else -> R.drawable.fx_ic_d_refresh
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FreshStatusCard(
    state: HomeUiState,
    onInstall: () -> Unit,
    onJailbreak: () -> Unit,
    onCheckUpdate: () -> Unit,
    onRefreshImage: () -> Unit,
    containerColor: Color,
    contentColor: Color,
    hero: Customization?,
    heroBitmap: ImageBitmap?,
    cardBorder: BorderStroke?,
    onLongPress: () -> Unit,
) {
    val ksuActive = state.ksuVersion != null
    val notInstalled = !ksuActive && state.kernelVersion.isGKI()
    val heroSt = state.heroState()

    // 状态图标跟随真实阶段：只有刷写成功（待重启 / 工作中）才是对勾，未安装 / 失败 / 不支持一律叉号
    val statusIcon = painterResource(
        when (heroSt.phase) {
            HeroPhase.WORKING, HeroPhase.PENDING_REBOOT -> R.drawable.fx_ic_home_status_ok
            HeroPhase.ATTENTION, HeroPhase.FLASHING -> R.drawable.fx_ic_home_status_warn
            else -> R.drawable.fx_ic_home_status_fail
        }
    )
    // 状态文字由真实运行态推导（刷写中 / 待重启 / 工作中 / 需注意 / 未安装 / 不支持）
    val statusTitle = when (heroSt.phase) {
        HeroPhase.NOT_INSTALLED -> stringResource(R.string.fx_home_not_installed)
        HeroPhase.FLASHING -> stringResource(R.string.fx_home_flashing_now)
        HeroPhase.PENDING_REBOOT -> stringResource(R.string.fx_home_pending_reboot)
        HeroPhase.WORKING -> stringResource(R.string.fx_home_working)
        HeroPhase.ATTENTION -> stringResource(R.string.fx_glass_status_issue)
        HeroPhase.FLASH_FAILED -> stringResource(R.string.fx_home_flash_failed)
        HeroPhase.UNSUPPORTED -> stringResource(R.string.fx_home_unsupported)
    }
    val statusSummary = when (heroSt.phase) {
        HeroPhase.WORKING -> stringResource(R.string.fx_home_working_version, "${state.ksuVersion}-${state.kernelUAPIVersion}")
        HeroPhase.NOT_INSTALLED -> stringResource(R.string.fx_home_click_to_install)
        HeroPhase.FLASHING -> stringResource(R.string.fx_home_flashing_now)
        HeroPhase.PENDING_REBOOT -> stringResource(R.string.fx_home_pending_reboot)
        HeroPhase.ATTENTION -> stringResource(R.string.fx_fresh_home_issue_count, state.freshIssues().size)
        HeroPhase.FLASH_FAILED -> stringResource(R.string.fx_home_flash_failed)
        HeroPhase.UNSUPPORTED -> stringResource(R.string.fx_home_unsupported_reason)
    }
    // 运行模式：仅在真实 LKM/GKI 时显示，非 GKI 内核不瞎写
    val workingMode = heroSt.modeLabel.orEmpty()

    // 运行模式 / 越狱模式标签配色：薄荷绿 + 琥珀，和卡片底色协调且不刺眼
    val night = isNightScheme()
    val lkmContainer = if (night) Color.White.copy(alpha = 0.14f) else Color.White.copy(alpha = 0.75f)
    val lkmContent = if (night) Color(0xFFCDBDF5) else UiPalette.HeroAccent
    val jailContainer = if (night) Color(0xFF2E2716) else UiPalette.KpiCream.first
    val jailContent = if (night) Color(0xFFEFD79A) else UiPalette.KpiCream.second

    val canJailbreak = !ksuActive && state.kernelVersion.isGKI() && state.isSELinuxPermissive
    val glassBackdrop = CustomizationStore.current.cardGlass
    val hasHero = !glassBackdrop && hero != null && heroBitmap != null
    // 状态徽章：参考图是薰衣草紫的细描边徽章，工作态用主卡强调紫
    val badgeTint = when {
        // 刷写已成功、等待重启：用琥珀色对勾，和错误红区分开
        heroSt.phase == HeroPhase.PENDING_REBOOT || heroSt.phase == HeroPhase.FLASHING -> Color(0xFFB07C10)
        !ksuActive -> MaterialTheme.colorScheme.error
        hasHero || isNightScheme() -> Color.White
        else -> UiPalette.StatusRose
    }

    // 参考设计稿：主卡默认走粉桃渐变（135° #FDE4E0 → #FBD8E4）；
    // 用户覆盖了卡面颜色、夜间、壁纸时退回纯色
    val heroGradient = if (!hasHero && !isNightScheme() &&
        containerColor.copy(alpha = 1f) == UiPalette.HeroSurface
    ) {
        Brush.linearGradient(listOf(UiPalette.HeroGradientStart, UiPalette.HeroGradientEnd))
    } else {
        null
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            // 清爽主题：粒子边用 HeroAccent，粉桃卡面上偏冷色点缀
            .particleOutline(
                shape = RoundedCornerShape(26.dp),
                accent = UiPalette.HeroAccent,
                bleed = 11.dp,
            )
            .clip(RoundedCornerShape(26.dp))
            .combinedClickable(
                onClick = { if (!state.isLateLoadMode) onInstall() },
                onLongClick = onLongPress,
            ),
        shape = RoundedCornerShape(26.dp),
        color = if (heroGradient != null) Color.Transparent else containerColor,
        contentColor = contentColor,
        border = if (hasHero) null else cardBorder,
        shadowElevation = 0.dp,
    ) {
        Box {
            if (heroGradient != null) {
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .background(heroGradient)
                )
            }
            GlassSheen(Modifier.matchParentSize())
            if (hero != null && heroBitmap != null) {
                FreshHeroBackdrop(hero = hero, bitmap = heroBitmap)
            }
            Row(
                modifier = Modifier.padding(start = 18.dp, top = 18.dp, bottom = 18.dp, end = 72.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = statusTitle,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = (-0.2).sp,
                            color = contentColor,
                        )
                        if (ksuActive && state.isLateLoadMode && workingMode.isEmpty()) {
                            Spacer(Modifier.width(8.dp))
                            FreshTag(
                                text = stringResource(R.string.fx_jailbreak_mode),
                                container = jailContainer,
                                content = jailContent,
                            )
                        }
                        if (ksuActive && state.isSafeMode) {
                            Spacer(Modifier.width(8.dp))
                            FreshTag(
                                text = stringResource(R.string.fx_safe_mode),
                                container = MaterialTheme.colorScheme.errorContainer,
                                content = MaterialTheme.colorScheme.onErrorContainer,
                            )
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = statusSummary,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            color = contentColor.copy(alpha = if (supportingTextAlpha() > 0.7f) 0.82f else 0.72f),
                        )
                        if (state.showCustomLkmBadge) {
                            Spacer(Modifier.width(8.dp))
                            FreshTag(
                                text = stringResource(R.string.fx_home_lkm_custom),
                                container = MaterialTheme.colorScheme.tertiaryContainer,
                                content = MaterialTheme.colorScheme.onTertiaryContainer,
                            )
                        }
                    }
                    if (workingMode.isNotEmpty()) {
                        Spacer(Modifier.height(10.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            FreshTag(
                                text = workingMode,
                                container = lkmContainer,
                                content = lkmContent,
                            )
                            // 越狱模式紧跟运行模式之后（仅在真实越狱模式时显示）
                            if (ksuActive && heroSt.jailbreakMode) {
                                Spacer(Modifier.width(8.dp))
                                FreshTag(
                                    text = stringResource(R.string.fx_jailbreak_mode),
                                    container = jailContainer,
                                    content = jailContent,
                                )
                            }
                            if (!state.isLateLoadMode) {
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = stringResource(R.string.fx_fresh_home_flash_hint),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = contentColor.copy(alpha = 0.72f),
                                )
                            }
                        }
                    }
                    // 版本号 + 更新入口：版本号随应用版本自动更新，点击可真实检查更新
                    Spacer(Modifier.height(10.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = stringResource(R.string.fx_glass_version_fmt, heroSt.versionName),
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold,
                            color = contentColor.copy(alpha = 0.85f),
                            modifier = Modifier.clickable { onCheckUpdate() },
                        )
                        if (heroSt.hasUpdate) {
                            Spacer(Modifier.width(8.dp))
                            FreshTag(
                                text = stringResource(R.string.fx_glass_tag_update, heroSt.latestVersionCode),
                                container = lkmContainer,
                                content = lkmContent,
                            )
                        }
                        Spacer(Modifier.width(10.dp))
                        Text(
                            text = stringResource(R.string.fx_home_check_update),
                            style = MaterialTheme.typography.labelSmall,
                            color = contentColor.copy(alpha = 0.7f),
                            modifier = Modifier.clickable { onCheckUpdate() },
                        )
                        Spacer(Modifier.width(10.dp))
                        // 刷新镜像：真实执行 ksud boot-patch -f
                        Text(
                            text = stringResource(R.string.fx_home_refresh_image),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = contentColor.copy(alpha = 0.85f),
                            modifier = Modifier.clickable(enabled = state.heroState().canFlash) {
                                onRefreshImage()
                            },
                        )
                    }
                    if (!hasHero) {
                        Spacer(Modifier.height(10.dp))
                        Text(
                            text = stringResource(R.string.fx_fresh_home_hero_hint),
                            style = MaterialTheme.typography.labelSmall,
                            color = contentColor.copy(alpha = 0.6f),
                        )
                    }
                    if (canJailbreak) {
                        Spacer(Modifier.height(12.dp))
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = MaterialTheme.colorScheme.error,
                            contentColor = MaterialTheme.colorScheme.onError,
                            modifier = Modifier
                                .clip(RoundedCornerShape(14.dp))
                                .clickable { onJailbreak() },
                        ) {
                            Text(
                                text = stringResource(R.string.fx_home_jailbreak),
                                style = MaterialTheme.typography.labelLarge,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                            )
                        }
                    }
                }
            }

            // 状态徽章：贴紧卡片右上角内侧（end 8dp），不与标题/版本行抢位置
            // 原位对勾 + 呼吸脉冲扩散环（不滑动）
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 10.dp, end = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                val pulseT = androidx.compose.animation.core.rememberInfiniteTransition(label = "fresh-seal")
                val pulse by pulseT.animateFloat(
                    initialValue = 0f,
                    targetValue = 1f,
                    animationSpec = androidx.compose.animation.core.infiniteRepeatable(
                        animation = androidx.compose.animation.core.tween(2000),
                        repeatMode = androidx.compose.animation.core.RepeatMode.Restart,
                    ),
                    label = "fresh-seal-pulse",
                )
                // 扩散环
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .graphicsLayer {
                            val k = 1f + pulse * 0.5f
                            scaleX = k
                            scaleY = k
                            alpha = (1f - pulse) * 0.45f
                        }
                        .border(2.dp, badgeTint.copy(alpha = 0.9f), CircleShape),
                )
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .shadow(elevation = 8.dp, shape = CircleShape, clip = false)
                        .clip(CircleShape)
                        .background(
                            if (hasHero) Color.White.copy(alpha = 0.22f)
                            else if (isNightScheme()) Color(0xFF2E2A38) else Color.White
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        painter = statusIcon,
                        contentDescription = null,
                        tint = badgeTint,
                        modifier = Modifier.size(26.dp),
                    )
                }
            }
        }
    }
}

/** Paints the optional picture behind the hero card, with the same zoom / pan model as the wallpaper. */
@Composable
private fun BoxScope.FreshHeroBackdrop(hero: Customization, bitmap: ImageBitmap?) {
    if (bitmap == null) return
    Image(
        bitmap = bitmap,
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = Modifier
            .matchParentSize()
            .graphicsLayer {
                scaleX = hero.heroScale
                scaleY = hero.heroScale
                translationX = hero.heroOffsetX * size.width
                translationY = hero.heroOffsetY * size.height
            },
    )
    if (hero.heroDim > 0f) {
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(Color.Black.copy(alpha = hero.heroDim)),
        )
    }
}

/** Long-press menu offering to pick / remove the hero card picture. */
@Composable
private fun FreshDialogRow(icon: ImageVector, text: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .background(MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.7f))
            .padding(horizontal = 14.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.width(12.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun FreshTag(text: String, container: Color, content: Color) {
    Surface(
        shape = RoundedCornerShape(9.dp),
        color = container,
        contentColor = content,
    ) {
        Text(
            text = text,
            fontSize = 10.5.sp,
            lineHeight = 13.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 11.dp, vertical = 4.dp),
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FreshKpiCard(
    iconRes: Int,
    title: String,
    value: String,
    caption: String,
    containerColor: Color,
    contentColor: Color,
    accentColor: Color,
    cardBorder: BorderStroke?,
    imageUri: String?,
    imageAlpha: Float,
    onLongPress: () -> Unit,
    onClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val bitmap = rememberWallpaper(imageUri)
    val showImage = bitmap != null && imageAlpha > 0.02f
    // 图片足够不透明时文字转白，否则沿用卡面配色
    val onPhoto = showImage && imageAlpha > 0.45f
    // 未选图片时的配色：有壁纸则统一用磨砂卡面，避免彩虹浅色块变成一张白卡
    val backdropMaterial = hasWallpaperBackdrop()
    val baseContainer = if (backdropMaterial && !showImage) freshCardColor() else containerColor
    val baseContent = if (backdropMaterial && !showImage) Color.White else contentColor
    val baseAccent = if (backdropMaterial && !showImage) Color.White else accentColor
    val baseBorder = if (backdropMaterial) freshCardBorder() else cardBorder
    val textColor = if (onPhoto) Color.White else baseContent
    val valueColor = if (onPhoto) Color.White else baseAccent

    Surface(
        modifier = modifier
            .height(150.dp)
            .clip(RoundedCornerShape(24.dp))
            .combinedClickable(
                onClick = { onClick?.invoke() },
                onLongClick = onLongPress,
            ),
        shape = RoundedCornerShape(24.dp),
        color = baseContainer,
        contentColor = textColor,
        border = baseBorder,
        shadowElevation = 0.dp,
    ) {
        Box {
            GlassSheen(Modifier.matchParentSize())
            if (bitmap != null && imageAlpha > 0.02f) {
                Image(
                    bitmap = bitmap,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .matchParentSize()
                        .graphicsLayer { alpha = imageAlpha },
                )
                if (onPhoto) {
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .background(Color.Black.copy(alpha = 0.32f)),
                    )
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 14.dp, vertical = 14.dp),
            ) {
                // 参考图：左上角一枚白底圆角图标块，图标用卡片自己的深色
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color.White.copy(
                        alpha = if (onPhoto) 0.24f else if (isNightScheme()) 0.10f else 0.66f
                    ),
                    contentColor = valueColor,
                    modifier = Modifier.size(40.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            painter = painterResource(iconRes),
                            contentDescription = null,
                            tint = valueColor,
                            modifier = Modifier.size(19.dp),
                        )
                    }
                }
                Spacer(Modifier.height(10.dp))
                // 标题：小号、半透明
                Text(
                    text = title,
                    fontSize = 12.sp,
                    lineHeight = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = valueColor.copy(alpha = 0.85f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(3.dp))
                // 值：大号粗体、卡片主色（参考图里是紫色 / 墨绿那种有色彩的粗字）
                Text(
                    text = value,
                    fontSize = 18.sp,
                    lineHeight = 22.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = (-0.3).sp,
                    color = valueColor,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.weight(1f))
                Text(
                    text = caption,
                    fontSize = 10.5.sp,
                    lineHeight = 13.sp,
                    color = textColor.copy(alpha = 0.65f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun FreshSecurityGroup(
    systemInfo: SystemInfo,
    containerColor: Color,
    contentColor: Color,
) {
    val selinuxDisplay = when (systemInfo.selinuxStatus) {
        "Enforcing" -> stringResource(R.string.fx_selinux_status_enforcing)
        "Permissive" -> stringResource(R.string.fx_selinux_status_permissive)
        "Disabled" -> stringResource(R.string.fx_selinux_status_disabled)
        else -> stringResource(R.string.fx_selinux_status_unknown)
    }
    val seccompDisplay = when (systemInfo.seccompStatus) {
        -1 -> stringResource(R.string.fx_seccomp_status_not_supported)
        0 -> stringResource(R.string.fx_seccomp_status_disabled)
        1 -> stringResource(R.string.fx_seccomp_status_strict)
        2 -> stringResource(R.string.fx_seccomp_status_filter)
        else -> stringResource(R.string.fx_seccomp_status_unknown)
    }

    val (c1, t1) = rainbowPair(1)
    val (c2, t2) = rainbowPair(2)
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        FreshPillTile(
            iconRes = R.drawable.fx_ic_d_lock,
            title = stringResource(R.string.fx_home_selinux_status),
            caption = selinuxDisplay,
            containerColor = c1,
            contentColor = t1,
            modifier = Modifier.weight(1f),
        )
        FreshPillTile(
            iconRes = R.drawable.fx_ic_d_tune,
            title = stringResource(R.string.fx_home_seccomp_status),
            caption = seccompDisplay,
            containerColor = c2,
            contentColor = t2,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun FreshSupportGroup(
    onOpenUrl: (String) -> Unit,
    containerColor: Color,
    contentColor: Color,
) {
    val learnMoreUrl = stringResource(R.string.fx_home_learn_kernelsu_url)
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        val (c3, t3) = rainbowPair(3)
        val (c4, t4) = rainbowPair(4)
        FreshPillTile(
            iconRes = R.drawable.fx_ic_d_heart,
            title = stringResource(R.string.fx_home_support_title),
            caption = stringResource(R.string.fx_home_support_content),
            accent = true,
            containerColor = c3,
            contentColor = t3,
            onClick = { onOpenUrl("https://patreon.com/weishu") },
            modifier = Modifier.weight(1f),
        )
        FreshPillTile(
            iconRes = R.drawable.fx_ic_d_book,
            title = stringResource(R.string.fx_home_learn_kernelsu),
            caption = stringResource(R.string.fx_home_click_to_learn_kernelsu),
            accent = true,
            containerColor = c4,
            contentColor = t4,
            onClick = { onOpenUrl(learnMoreUrl) },
            modifier = Modifier.weight(1f),
        )
    }
}

/** 胶囊卡：左侧圆形图标 + 标题/说明，对应参考图里的「SELinux 状态 / 支持开发」样式。 */
@Composable
private fun FreshPillTile(
    iconRes: Int,
    title: String,
    caption: String,
    containerColor: Color,
    contentColor: Color = MaterialTheme.colorScheme.onSurface,
    modifier: Modifier = Modifier,
    accent: Boolean = false,
    onClick: (() -> Unit)? = null,
) {
    val shape = UiPalette.PillShape
    // 图标跟着卡片自己的文字色走，并加一层同色淡底，和柔和卡面更搭
    val tint = if (accent) contentColor else contentColor.copy(alpha = 0.78f)
    val base = modifier
        .height(82.dp)
        .clip(shape)
        .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
    Surface(
        shape = shape,
        color = containerColor,
        contentColor = contentColor,
        border = freshCardBorder(),
        shadowElevation = 0.dp,
        modifier = base,
    ) {
        Box {
            GlassSheen(Modifier.matchParentSize())
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // 参考图：左侧白底圆（半透明白），图标是卡片深色
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(13.dp))
                    .background(Color.White.copy(alpha = if (isNightScheme()) 0.10f else 1f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(iconRes),
                    contentDescription = null,
                    tint = tint,
                    modifier = Modifier.size(19.dp),
                )
            }
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = contentColor,
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    text = caption,
                    fontSize = 10.5.sp,
                    lineHeight = 13.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    color = contentColor.copy(alpha = 0.62f),
                )
            }
        }
        }
    }
}


/** 次级卡片的配色：参考图里每张卡片一个柔和的马卡龙色，而不是同一张白卡。
 *  七彩开关打开时仍走七彩表；壁纸/玻璃模式让位给磨砂卡面。
 */
@Composable
private fun rainbowPair(index: Int): Pair<Color, Color> {
    val custom = CustomizationStore.current
    if (hasWallpaperBackdrop()) {
        return freshCardColor() to Color.White
    }
    val night = isNightScheme()
    if (custom.rainbowCards) {
        val (bg, fg) = if (night) LaunchTint.rainbowPairDark(index) else LaunchTint.rainbowPair(index)
        return Color(bg) to Color(fg)
    }
    // 默认：设计稿的柔和色板。槽位固定分配：
    // 0=指纹(淡紫) 1=SELinux(薄荷) 2=Seccomp(灰) 3=支持开发(蜜桃) 4=了解KSU(天蓝) 5=内核(玫瑰)
    val tones = if (night) {
        listOf(
            UiPalette.KpiLilacDark, UiPalette.PillMintDark, UiPalette.PillGrayDark,
            UiPalette.PillPeachDark, UiPalette.PillSkyDark, UiPalette.KpiRoseDark,
        )
    } else {
        listOf(
            UiPalette.KpiLilac, UiPalette.PillMint, UiPalette.PillGray,
            UiPalette.PillPeach, UiPalette.PillSky, UiPalette.KpiRose,
        )
    }
    val (bg, fg) = tones[index.mod(tones.size)]
    return bg to fg
}

/** 内核信息卡：左侧圆角图标块 + 标题/详情，对应参考图里的单行工具卡。 */
@Composable
private fun FreshKernelCard(
    iconRes: Int,
    title: String,
    value: String,
    containerColor: Color,
    contentColor: Color = MaterialTheme.colorScheme.onSurface,
) {
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = containerColor,
        contentColor = contentColor,
        border = freshCardBorder(),
        shadowElevation = 0.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Box {
            GlassSheen(Modifier.matchParentSize())
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(13.dp))
                    .background(Color.White.copy(alpha = if (isNightScheme()) 0.10f else 1f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(iconRes),
                    contentDescription = null,
                    tint = contentColor,
                    modifier = Modifier.size(20.dp),
                )
            }
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = contentColor,
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    text = value,
                    fontSize = 12.sp,
                    lineHeight = 16.sp,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    color = contentColor.copy(alpha = supportingTextAlpha()),
                )
            }
        }
        }
    }
}

@Composable
private fun FreshCardGroup(
    containerColor: Color,
    contentColor: Color,
    content: @Composable () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(22.dp),
        color = containerColor,
        contentColor = contentColor,
        border = freshCardBorder(),
        shadowElevation = 0.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column { content() }
    }
}

@Composable
private fun FreshDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(start = 56.dp, end = 16.dp),
        color = MaterialTheme.colorScheme.outlineVariant,
    )
}

@Composable
private fun FreshInfoRow(
    icon: ImageVector,
    label: String,
    value: String,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(22.dp),
        )
        Spacer(Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge,
                color = LocalContentColor.current,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.bodySmall,
                color = LocalContentColor.current.copy(alpha = supportingTextAlpha()),
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun FreshNavRow(
    icon: ImageVector,
    title: String,
    description: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(22.dp),
        )
        Spacer(Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = LocalContentColor.current,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = LocalContentColor.current.copy(alpha = supportingTextAlpha()),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.outline,
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FreshIssueList(
    issues: List<FreshIssue>,
    onInstall: () -> Unit,
    canInstallKernel: Boolean,
    containerColor: Color,
    contentColor: Color,
    latestVersionCode: Int,
    currentVersion: String,
    onLongPress: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        issues.forEach { issue ->
            val text = when (issue) {
                FreshIssue.RootMissing -> stringResource(R.string.fx_fresh_issue_root)
                FreshIssue.KernelOutdated -> stringResource(R.string.fx_fresh_issue_kernel_old)
                FreshIssue.ManagerOutdated -> stringResource(R.string.fx_fresh_issue_manager_old)
                FreshIssue.SafeMode -> stringResource(R.string.fx_fresh_issue_safe_mode)
                FreshIssue.NotGki -> stringResource(R.string.fx_fresh_issue_not_gki)
                FreshIssue.SelinuxPermissive -> stringResource(R.string.fx_fresh_issue_selinux)
                FreshIssue.UpdateAvailable -> stringResource(
                    R.string.fx_fresh_issue_update,
                    latestVersionCode,
                    currentVersion,
                )
                FreshIssue.LkmUpdate -> stringResource(R.string.fx_fresh_issue_lkm_update)
            }
            val actionable = canInstallKernel && (
                issue == FreshIssue.KernelOutdated ||
                    issue == FreshIssue.ManagerOutdated ||
                    issue == FreshIssue.UpdateAvailable ||
                    issue == FreshIssue.LkmUpdate
                )
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = containerColor,
                contentColor = contentColor,
                border = freshCardBorder(),
                shadowElevation = 0.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .combinedClickable(
                        onClick = { if (actionable) onInstall() },
                        onLongClick = onLongPress,
                    ),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.Filled.Warning,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = text,
                        style = MaterialTheme.typography.bodyMedium,
                        color = contentColor,
                        modifier = Modifier.weight(1f),
                    )
                    if (actionable) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }
        }
    }
}

/** 待裁剪的卡面图片：kind 0 = 顶部大卡，1 = KPI 卡（带索引）。 */
private data class PendingCrop(
    val path: String,
    val aspect: Float,
    val kind: Int,
    val index: Int,
)
