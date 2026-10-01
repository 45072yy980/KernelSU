package me.weishu.kernelsu.ui.screen.home

import androidx.compose.foundation.layout.requiredSize
import me.weishu.kernelsu.ui.PanelMetrics
import kotlin.math.roundToInt
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.onSizeChanged
import me.weishu.kernelsu.ui.theme.LocalGlassNotice
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.blur
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.add
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.DeveloperBoard
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material.icons.filled.Tag
import androidx.compose.material.icons.filled.VolunteerActivism
import androidx.compose.material.icons.rounded.CheckCircleOutline
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import me.weishu.kernelsu.KernelVersion
import me.weishu.kernelsu.Natives
import me.weishu.kernelsu.R
import me.weishu.kernelsu.ui.component.WarningLevel
import me.weishu.kernelsu.ui.component.dialog.rememberConfirmDialog
import me.weishu.kernelsu.ui.component.miuix.WarningCard
import me.weishu.kernelsu.ui.component.statustag.StatusTag
import me.weishu.kernelsu.ui.theme.LocalHomeCardBlur
import me.weishu.kernelsu.ui.util.module.LatestVersionInfo
import me.weishu.kernelsu.ui.util.HomeWallpaperStore
import me.weishu.kernelsu.ui.util.rememberWallpaperSet
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme
import top.yukonga.miuix.kmp.utils.PressFeedbackType
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic

/**
 * Live alignment nudges for the frosted pane, dialled from the theme settings sliders and kept
 * across restarts. Read during composition so dragging a slider retriggers the pane's layout.
 */
object GlassNudge {
    private const val PREF = "glass_nudge"
    val x = mutableFloatStateOf(15f)
    val y = mutableFloatStateOf(375f)

    fun load(context: Context) {
        val sp = context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
        x.floatValue = sp.getFloat("x", 15f)
        y.floatValue = sp.getFloat("y", 375f)
    }
}

/**
 * A frosted pane that shows the wallpaper slice sitting under it.
 *
 * Sampling a backdrop would mean sampling the pane's own output -- the renderer
 * recurses and dies. So instead the pane draws a blurred copy of the whole
 * wallpaper, aligned to the home panel and then offset back until the slice
 * under the pane is the slice it shows. [PanelMetrics] gives the panel's size
 * and window position; the pane reports its own position through [onPositioned]
 * so the offset can be recomputed as the list scrolls.
 *
 * The [content] is drawn on top with no background of its own: callers are
 * expected to make their cards transparent while [enabled] is true.
 */
@Composable
private fun GlassPane(
    enabled: Boolean,
    wallpaper: ImageBitmap?,
    onPositioned: (Offset) -> Unit,
    cornerRadius: Dp,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    var paneWindowPos by remember { mutableStateOf(Offset.Zero) }
    // Composition reads: layout writes, and the blur offset has to follow.
    val panelSize = PanelMetrics.size.value
    val panelPos = PanelMetrics.pos.value
    val panePos = paneWindowPos
    val density = LocalDensity.current

    Box(
        modifier = modifier.onGloballyPositioned {
            paneWindowPos = it.positionInWindow()
            onPositioned(paneWindowPos)
        },
    ) {
        if (enabled && wallpaper != null && panelSize != IntSize.Zero && panePos != Offset.Zero) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .clip(RoundedCornerShape(cornerRadius)),
            ) {
                Image(
                    bitmap = wallpaper,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .requiredSize(
                            with(density) { (panelSize.width + 96).toDp() },
                            with(density) { panelSize.height.toDp() },
                        )
                        .blur(16.dp)
                        .offset {
                            IntOffset(
                                panelPos.x.roundToInt() - panePos.x.roundToInt()
                                    - 48
                                    + with(density) { GlassNudge.x.floatValue.dp.toPx() }.roundToInt(),
                                panelPos.y.roundToInt() - panePos.y.roundToInt()
                                    + with(density) { GlassNudge.y.floatValue.dp.toPx() }.roundToInt(),
                            )
                        },
                )
                // The same dimming the panel lays over the wallpaper, so the pane
                // belongs to the page instead of glowing out of it.
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .background(Color.Black.copy(alpha = 0.28f)),
                )
            }
        }
        content()
    }
}

@Composable
fun HomePagerMiuix(
    state: HomeUiState,
    actions: HomeActions,
    bottomInnerPadding: Dp,
) {
    // Shared by the notice pane and the working card: both show the same blurred
    // wallpaper, so both have to read the same bitmap.
    val wallpaperContext = LocalContext.current
    val wallpaperVersion = HomeWallpaperStore.version
    val noticeWallpaper = remember(wallpaperContext, wallpaperVersion) {
        HomeWallpaperStore.load(HomeWallpaperStore.file(wallpaperContext))
    }
    Scaffold(
        popupHost = { },
        contentWindowInsets = WindowInsets.systemBars.add(WindowInsets.displayCutout).only(WindowInsetsSides.Horizontal)
    ) { _ ->
        Box {
            LazyColumn(
                modifier = Modifier
                    .fillMaxHeight()
                    .scrollEndHaptic()
                    .overScrollVertical()
                    .padding(horizontal = 12.dp),
                contentPadding = WindowInsets.systemBars
                    .add(WindowInsets.displayCutout)
                    .only(WindowInsetsSides.Vertical)
                    .asPaddingValues(),
                overscrollEffect = null,
            ) {
                item {
                    Column(
                        modifier = Modifier.padding(top = 12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        // Everything above the working card: update, kernel/GKI notices,
                        // root warnings and the jailbreak guard banner. They get the same
                        // frosted treatment as the working card, as one pane, so a stack of
                        // notices reads as a single sheet of glass rather than a pile of
                        // tinted boxes. Nothing is drawn when none of them show.
                        val showsNotice =
                            (state.checkUpdateEnabled && state.hasUpdate) ||
                                state.showManagerPrBuildWarning ||
                                state.showKernelPrBuildWarning ||
                                state.showGkiWarning ||
                                state.requiresNewKernel ||
                                state.requiresNewManager ||
                                state.showLkmUpdate ||
                                state.showRootWarning ||
                                state.isLateLoadMode
                        val noticeBlur = LocalHomeCardBlur.current
                        GlassPane(
                            enabled = noticeBlur,
                            wallpaper = noticeWallpaper,
                            onPositioned = { },
                            cornerRadius = 16.dp,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            if (showsNotice) {
                                Column(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalArrangement = Arrangement.spacedBy(12.dp),
                                ) {
                                    // The cards go transparent only when the glass is really
                                    // painted; with the setting off or no wallpaper they keep
                                    // their usual tinted background.
                                    val glassed = noticeBlur && noticeWallpaper != null
                                    CompositionLocalProvider(LocalGlassNotice provides glassed) {
                                    if (state.checkUpdateEnabled) {
                                        UpdateCard(state = state, actions = actions)
                                    }
                                    if (state.showManagerPrBuildWarning) {
                                        WarningCard(stringResource(id = R.string.home_pr_build_warning), level = WarningLevel.Notice)
                                    } else if (state.showKernelPrBuildWarning) {
                                        WarningCard(stringResource(id = R.string.home_pr_kernel_warning), level = WarningLevel.Notice)
                                    }
                                    if (state.showGkiWarning) {
                                        WarningCard(stringResource(id = R.string.home_gki_warning), level = WarningLevel.Notice)
                                    }
                                    if (state.requiresNewKernel) {
                                        WarningCard(
                                            stringResource(
                                                id = if (state.lkmMode == true) R.string.require_kernel_version else R.string.require_kernel_version_gki
                                            ),
                                            onClick = if (state.lkmMode == true) actions.onInstallClick else null
                                        )
                                    }
                                    if (state.requiresNewManager) {
                                        WarningCard(
                                            stringResource(
                                                id = R.string.require_manager_version
                                            )
                                        )
                                    }
                                    if (state.showLkmUpdate) {
                                        WarningCard(
                                            message = stringResource(R.string.home_lkm_update_available),
                                            level = WarningLevel.Notice,
                                            onClick = actions.onInstallClick,
                                        )
                                    }
                                    if (state.showRootWarning) {
                                        WarningCard(stringResource(id = R.string.grant_root_failed))
                                    }
                                    if (state.isLateLoadMode) {
                                        JailbreakGuardCard(modifier = Modifier.fillMaxWidth())
                                    }
                                    }
                                }
                            }
                        }
                        StatusCard(
                            state = state,
                            actions = actions,
                        )
                        InfoCard(
                            systemInfo = state.systemInfo,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        SupportLinks(
                            onOpenUrl = actions.onOpenUrl,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Spacer(
                            Modifier.height(
                                bottomInnerPadding + if (!Natives.isFullFeatured())
                                    WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() else 0.dp
                            )
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun UpdateCard(
    state: HomeUiState,
    actions: HomeActions,
) {
    val newVersion = state.latestVersionInfo
    val title = stringResource(id = R.string.module_changelog)
    val updateText = stringResource(id = R.string.module_update)
    val updateDialog = rememberConfirmDialog(onConfirm = { actions.onOpenUrl(newVersion.downloadUrl) })

    AnimatedVisibility(
        visible = state.hasUpdate,
        enter = fadeIn() + expandVertically(),
        exit = shrinkVertically() + fadeOut()
    ) {
        WarningCard(
            message = stringResource(id = R.string.new_version_available, newVersion.versionCode),
            level = WarningLevel.Notice,
            onClick = {
                if (newVersion.changelog.isEmpty()) {
                    actions.onOpenUrl(newVersion.downloadUrl)
                } else {
                    updateDialog.showConfirm(
                        title = title,
                        content = newVersion.changelog,
                        markdown = true,
                        confirm = updateText
                    )
                }
            }
        )
    }
}

@Composable
private fun StatusCard(
    state: HomeUiState,
    actions: HomeActions,
) {
    Column {
        when {
            state.ksuVersion != null -> {
                val workingState = buildString {
                    if (state.isSafeMode) {
                        append(" [${stringResource(id = R.string.safe_mode)}]")
                    }
                    if (state.isLateLoadMode) {
                        append(" [${stringResource(id = R.string.jailbreak_mode)}]")
                    }
                }
                val workingMode = when (state.lkmMode) {
                    null -> null
                    true -> "LKM"
                    else -> "GKI"
                }
                val workingText = "${stringResource(id = R.string.home_working)}$workingState"

                // The status card may carry a picture of its own; re-read when it is replaced.
                val homeCardBlur = LocalHomeCardBlur.current
                val statusContext = LocalContext.current
                val statusVersion = HomeWallpaperStore.version
                val statusImage = remember(statusContext, statusVersion) {
                    HomeWallpaperStore.load(HomeWallpaperStore.statusFile(statusContext))
                }
                val homeWallpaper = remember(statusContext, statusVersion) {
                    HomeWallpaperStore.load(HomeWallpaperStore.file(statusContext))
                }
                // The frosted pane: the card has no picture of its own, the user asked for the
                // glass and there is a wallpaper to show through. Rather than sampling a backdrop
                // (which would mean sampling the card's own output and crashing the renderer), the
                // card draws a blurred copy of the whole wallpaper, aligned to the panel and then
                // offset back to the card, so the slice under the card is the slice it shows.
                val glassBackdrop = homeCardBlur && statusImage == null && homeWallpaper != null
                var cardWindowPos by remember { mutableStateOf(Offset.Zero) }
                // Over a picture the card is nothing but the picture, so the text carries the same
                // bright tint the info card uses; on the card's own colour it is plain black.
                val onPicture = statusImage != null || glassBackdrop
                val statusTitleColor =
                    if (onPicture) lerp(colorScheme.primary, Color.White, 0.65f) else colorScheme.onSurface
                val statusSubColor =
                    if (onPicture) lerp(colorScheme.primary, Color.White, 0.8f) else colorScheme.onSurfaceVariantSummary

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(IntrinsicSize.Min)
                        .onGloballyPositioned { coords ->
                            cardWindowPos = coords.positionInWindow()
                        },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.defaultColors(
                            // Transparent over a picture, with the theme's own tint laid on top of
                            // it below so the card's dark text stays readable on any photo.
                            // Transparent too when the frosted layer paints the background.
                            color = if (statusImage != null || glassBackdrop) {
                                Color.Transparent
                            } else {
                                // Off the theme, never a fixed green: with Monet off the app still has
                                // a key colour of its own, and a green card sat beside it as if it
                                // came from somewhere else. Deeper than the plain container, which is
                                // one of the colours the page's own gradient is made of — at that
                                // shade the card had no edge against the page at all.
                                lerp(colorScheme.primaryContainer, colorScheme.primary, 0.35f)
                            }
                        ),
                        onClick = {
                            if (!state.isLateLoadMode) {
                                actions.onInstallClick()
                            }
                        },
                        showIndication = !state.isLateLoadMode,
                        pressFeedbackType = PressFeedbackType.Tilt
                    ) {
                        Box {
                            if (glassBackdrop) {
                                val panelSize = PanelMetrics.size.value
                                val panelPos = PanelMetrics.pos.value
                                // Read in composition so scrolling retriggers layout.
                                val cardPos = cardWindowPos
                                if (panelSize != IntSize.Zero && cardPos != Offset.Zero) {
                                    val paneDensity = LocalDensity.current
                                    Box(
                                        modifier = Modifier
                                            .matchParentSize()
                                            .clip(RoundedCornerShape(16.dp)),
                                    ) {
                                        Image(
                                            bitmap = homeWallpaper,
                                            contentDescription = null,
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier
                                                .requiredSize(
                                                    with(paneDensity) { (panelSize.width + 96).toDp() },
                                                    with(paneDensity) { panelSize.height.toDp() },
                                                )
                                                .blur(16.dp)
                                                .offset {
                                                    IntOffset(
                                                        panelPos.x.roundToInt() - cardPos.x.roundToInt()
                                                            - 48
                                                            + with(paneDensity) { GlassNudge.x.floatValue.dp.toPx() }
                                                                .roundToInt(),
                                                        panelPos.y.roundToInt() - cardPos.y.roundToInt()
                                                            + with(paneDensity) { GlassNudge.y.floatValue.dp.toPx() }
                                                                .roundToInt(),
                                                    )
                                                },
                                        )
                                        // Same dimming the panel applies over the wallpaper, so the
                                        // pane matches the page rather than glowing out of it.
                                        Box(
                                            modifier = Modifier
                                                .matchParentSize()
                                                .background(Color.Black.copy(alpha = 0.28f)),
                                        )
                                    }
                                }
                            }
                            if (statusImage != null) {
                                Image(
                                    bitmap = statusImage,
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.matchParentSize(),
                                )
                            }
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .offset(27.dp, 31.dp),
                                contentAlignment = Alignment.BottomEnd
                            ) {
                                Icon(
                                    modifier = Modifier.size(110.dp),
                                    imageVector = Icons.Rounded.CheckCircleOutline,
                                    tint = colorScheme.primary.copy(alpha = 0.8f),
                                    contentDescription = null
                                )
                            }
                            if (workingMode != null) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(16.dp, 10.dp),
                                    contentAlignment = Alignment.BottomStart,
                                ) {
                                    Text(
                                        text = workingMode,
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = statusSubColor,
                                    )
                                }
                            }
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(16.dp, 14.dp),
                                contentAlignment = Alignment.TopStart,
                            ) {
                                Column {
                                    Text(
                                        text = workingText,
                                        fontSize = 22.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = statusTitleColor,
                                    )
                                    Spacer(Modifier.height(1.dp))
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = stringResource(
                                                R.string.home_working_version,
                                                "${state.ksuVersion}-${state.kernelUAPIVersion}"
                                            ),
                                            modifier = Modifier.weight(1f, fill = false),
                                            fontSize = 15.sp,
                                            color = statusSubColor,
                                        )
                                        if (state.showCustomLkmBadge) {
                                            Spacer(Modifier.width(8.dp))
                                            StatusTag(
                                                label = stringResource(R.string.home_lkm_custom),
                                                contentColor = colorScheme.onTertiaryContainer,
                                                backgroundColor = colorScheme.tertiaryContainer,
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            state.kernelVersion.isGKI() -> {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Card(
                        modifier = Modifier.weight(1f),
                        onClick = {
                            if (!state.isLateLoadMode) {
                                // Offer the exploit jailbreak or the ordinary install flow.
                                actions.onNotInstalledClick()
                            }
                        },
                        showIndication = !state.isLateLoadMode,
                        pressFeedbackType = PressFeedbackType.Tilt
                    ) {
                        BasicComponent(
                            title = stringResource(R.string.home_not_installed),
                            summary = stringResource(R.string.home_click_to_install),
                            startAction = {
                                Icon(
                                    Icons.Rounded.ErrorOutline,
                                    stringResource(R.string.home_not_installed),
                                    modifier = Modifier.padding(end = 6.dp),
                                    tint = colorScheme.onBackground,
                                )
                            },
                            endActions = {
                                if (state.isSELinuxPermissive) {
                                    TextButton(
                                        text = stringResource(R.string.home_jailbreak),
                                        onClick = actions.onJailbreakClick,
                                        colors = ButtonDefaults.textButtonColorsPrimary()
                                    )
                                }
                            }
                        )
                    }
                }
            }

            else -> {
                Card(
                    onClick = {
                        if (!state.isLateLoadMode) {
                            actions.onInstallClick()
                        }
                    },
                    showIndication = !state.isLateLoadMode,
                    pressFeedbackType = PressFeedbackType.Tilt
                ) {
                    BasicComponent(
                        title = stringResource(R.string.home_unsupported),
                        summary = stringResource(R.string.home_unsupported_reason),
                        startAction = {
                            Icon(
                                Icons.Rounded.ErrorOutline,
                                stringResource(R.string.home_unsupported),
                                modifier = Modifier.padding(end = 16.dp),
                                tint = colorScheme.onBackground,
                            )
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun JailbreakGuardCard(modifier: Modifier = Modifier) {
    // Shown while jailbreak (late-load) mode is running: the partition guard is
    // active, so this tells the user their system partitions are protected.
    // Sits inside the notice glass pane on the home screen, where the pane is the
    // background and this card has to keep off it.
    //
    // Over a picture the container tints are the wrong way round: onTertiaryContainer
    // is a dark brown meant for a pale container, and against a blurred photo it is
    // unreadable. Over the glass the card borrows the same bright tint and the same
    // type scale as every other card on the page.
    val glassed = LocalGlassNotice.current
    val onGlass = glassed
    val titleColor = if (onGlass) {
        lerp(colorScheme.primary, Color.White, 0.65f)
    } else {
        colorScheme.onTertiaryContainer
    }
    val summaryColor = if (onGlass) {
        lerp(colorScheme.primary, Color.White, 0.8f)
    } else {
        colorScheme.onTertiaryContainer.copy(alpha = 0.8f)
    }
    Card(
        modifier = modifier,
        colors = CardDefaults.defaultColors(
            color = if (onGlass) Color.Transparent else colorScheme.tertiaryContainer,
        ),
        showIndication = false,
        pressFeedbackType = PressFeedbackType.Sink,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Filled.Security,
                contentDescription = null,
                modifier = Modifier
                    .padding(end = 12.dp)
                    .size(28.dp),
                tint = if (onGlass) titleColor else colorScheme.onTertiaryContainer,
            )
            Column {
                Text(
                    text = stringResource(R.string.jailbreak_guard_running_title),
                    fontSize = MiuixTheme.textStyles.headline1.fontSize,
                    fontWeight = FontWeight.Medium,
                    color = titleColor,
                )
                Text(
                    text = stringResource(R.string.jailbreak_guard_running_summary),
                    fontSize = MiuixTheme.textStyles.body2.fontSize,
                    color = summaryColor,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
    }
}

@Composable
private fun SupportLinks(
    onOpenUrl: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val learnMoreUrl = stringResource(R.string.home_learn_kernelsu_url)

    // The cards' own colour follows the panel's opacity: at 0 they are nothing but their text,
    // sitting on the picture behind them. The status card above keeps its colour on purpose — it is
    // the one card that has to stay legible whatever the backdrop is.
    Card(
        modifier = modifier,
        colors = CardDefaults.defaultColors(
            color = colorScheme.surface.copy(alpha = HomeWallpaperStore.PANEL_FILL)
        ),
    ) {
        ArrowPreference(
            title = stringResource(R.string.home_support_title),
            summary = stringResource(R.string.home_support_content),
            startAction = {
                Icon(
                    imageVector = Icons.Filled.VolunteerActivism,
                    contentDescription = stringResource(R.string.home_support_title),
                    modifier = Modifier.padding(end = 6.dp),
                    tint = colorScheme.onBackground,
                )
            },
            onClick = { onOpenUrl("https://patreon.com/weishu") },
        )
        ArrowPreference(
            title = stringResource(R.string.home_learn_kernelsu),
            summary = stringResource(R.string.home_click_to_learn_kernelsu),
            startAction = {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.MenuBook,
                    contentDescription = stringResource(R.string.home_learn_kernelsu),
                    modifier = Modifier.padding(end = 6.dp),
                    tint = colorScheme.onBackground,
                )
            },
            onClick = { onOpenUrl(learnMoreUrl) },
        )
    }
}

@Composable
private fun InfoCard(
    systemInfo: SystemInfo,
    modifier: Modifier = Modifier,
) {
    val wallpaperSet = rememberWallpaperSet()

    @Composable
    fun InfoText(
        icon: ImageVector,
        title: String,
        content: String,
        bottomPadding: Dp = 24.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = bottomPadding),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                modifier = Modifier
                    .padding(end = 12.dp)
                    .size(24.dp),
                tint = colorScheme.onSurface,
            )
            Column {
                Text(
                    text = title,
                    fontSize = MiuixTheme.textStyles.headline1.fontSize,
                    fontWeight = FontWeight.Medium,
                    // As bright as white gets while still carrying the theme's hue: over a picture
                    // the page's own dark text vanished, and plainly white text reads as a different
                    // app. Only the text moves — the components keep the scheme they had.
                    // With no picture the page is the plain light one, and there the text is black.
                    color = if (wallpaperSet) lerp(colorScheme.primary, Color.White, 0.65f) else colorScheme.onSurface,
                )
                Text(
                    text = content,
                    fontSize = MiuixTheme.textStyles.body2.fontSize,
                    color = if (wallpaperSet) {
                        lerp(colorScheme.primary, Color.White, 0.8f)
                    } else colorScheme.onSurfaceVariantSummary,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
    }

    val selinuxDisplay = when (systemInfo.selinuxStatus) {
        "Enforcing" -> stringResource(R.string.selinux_status_enforcing)
        "Permissive" -> stringResource(R.string.selinux_status_permissive)
        "Disabled" -> stringResource(R.string.selinux_status_disabled)
        else -> stringResource(R.string.selinux_status_unknown)
    }
    val seccompDisplay = when (systemInfo.seccompStatus) {
        -1 -> stringResource(R.string.seccomp_status_not_supported)
        0 -> stringResource(R.string.seccomp_status_disabled)
        1 -> stringResource(R.string.seccomp_status_strict)
        2 -> stringResource(R.string.seccomp_status_filter)
        else -> stringResource(R.string.seccomp_status_unknown)
    }

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.defaultColors(
                color = colorScheme.surface.copy(alpha = HomeWallpaperStore.PANEL_FILL)
            ),
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                InfoText(
                    icon = Icons.Filled.Tag,
                    title = stringResource(R.string.home_manager_version),
                    content = systemInfo.managerVersion,
                )
                InfoText(
                    icon = Icons.Filled.DeveloperBoard,
                    title = stringResource(R.string.home_kernel),
                    content = systemInfo.kernelVersion,
                )
                InfoText(
                    icon = Icons.Filled.Smartphone,
                    title = stringResource(R.string.home_device_model),
                    content = systemInfo.deviceModel,
                )
                InfoText(
                    icon = Icons.Filled.Fingerprint,
                    title = stringResource(R.string.home_fingerprint),
                    content = systemInfo.fingerprint,
                    bottomPadding = 0.dp,
                )
            }
        }
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.defaultColors(
                color = colorScheme.surface.copy(alpha = HomeWallpaperStore.PANEL_FILL)
            ),
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                InfoText(
                    icon = Icons.Filled.Security,
                    title = stringResource(R.string.home_selinux_status),
                    content = selinuxDisplay,
                )
                InfoText(
                    icon = Icons.Filled.FilterList,
                    title = stringResource(R.string.home_seccomp_status),
                    content = seccompDisplay,
                    bottomPadding = 0.dp,
                )
            }
        }
    }
}

@Preview(name = "Activated")
@Composable
private fun StatusCardActivatedPreview() {
    StatusCard(
        state = previewHomeScreenState(ksuVersion = 12345, lkmMode = true),
        actions = HomeActions({}, {})
    )
}

@Preview(name = "Not Activated")
@Composable
private fun StatusCardNotActivatedPreview() {
    StatusCard(state = previewHomeScreenState(ksuVersion = null, lkmMode = null), actions = HomeActions({}, {}))
}

@Preview(name = "Permissive")
@Composable
private fun StatusCardPermissivePreview() {
    StatusCard(
        state = previewHomeScreenState(ksuVersion = null, lkmMode = null, selinuxStatus = "Permissive"),
        actions = HomeActions({}, {})
    )
}

@Preview(name = "Jailbreak")
@Composable
private fun StatusCardJailbreakPreview() {
    StatusCard(
        state = previewHomeScreenState(ksuVersion = 12345, lkmMode = true, isLateLoadMode = true),
        actions = HomeActions({}, {})
    )
}

private val previewSystemInfo = SystemInfo(
    kernelVersion = "6.12.23-android16-5-g123456789000-abogki123456789-4k",
    managerVersion = "3.0.0 (30000)",
    deviceModel = "Xiaomi 17 Pro Max",
    fingerprint = "Xiaomi/popsicle/popsicle:16/BQ2A.250705.001-BP2A.250605.031.A3/OS3.0.313.0.WPBCNXM:user/release-keys",
    selinuxStatus = "Enforcing",
    seccompStatus = 2
)

private val previewUriHandler = object : UriHandler {
    override fun openUri(uri: String) {}
}

@Composable
private fun HomeScreenPreviewContent(
    ksuVersion: Int?,
    lkmMode: Boolean?,
    isSafeMode: Boolean = false,
    isLateLoadMode: Boolean = false,
    selinuxStatus: String = "Enforcing",
) {
    CompositionLocalProvider(LocalUriHandler provides previewUriHandler) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            val actions = HomeActions({}, {})
            StatusCard(
                state = previewHomeScreenState(
                    ksuVersion = ksuVersion,
                    lkmMode = lkmMode,
                    isSafeMode = isSafeMode,
                    isLateLoadMode = isLateLoadMode,
                    selinuxStatus = selinuxStatus,
                ),
                actions = actions
            )
            InfoCard(
                systemInfo = previewSystemInfo.copy(selinuxStatus = selinuxStatus),
                modifier = Modifier.fillMaxWidth(),
            )
            SupportLinks(
                onOpenUrl = {},
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Preview(name = "Home Activated", showBackground = true)
@Composable
private fun HomeScreenActivatedPreview() {
    HomeScreenPreviewContent(ksuVersion = 12345, lkmMode = true)
}

@Preview(name = "Home Not Activated", showBackground = true)
@Composable
private fun HomeScreenNotActivatedPreview() {
    HomeScreenPreviewContent(ksuVersion = null, lkmMode = null)
}

@Preview(name = "Home Permissive", showBackground = true)
@Composable
private fun HomeScreenPermissivePreview() {
    HomeScreenPreviewContent(ksuVersion = null, lkmMode = null, selinuxStatus = "Permissive")
}

@Preview(name = "Home Jailbreak", showBackground = true)
@Composable
private fun HomeScreenJailbreakPreview() {
    HomeScreenPreviewContent(ksuVersion = 12345, lkmMode = true, isLateLoadMode = true)
}

private fun previewHomeScreenState(
    ksuVersion: Int?,
    lkmMode: Boolean?,
    isSafeMode: Boolean = false,
    isLateLoadMode: Boolean = false,
    selinuxStatus: String = "Enforcing",
) = HomeUiState(
    kernelVersion = KernelVersion(6, 1, 0),
    ksuVersion = ksuVersion,
    lkmMode = lkmMode,
    isLkmBundled = lkmMode == true,
    isManager = true,
    isManagerPrBuild = false,
    isKernelPrBuild = false,
    requiresNewKernel = false,
    requiresNewManager = false,
    isRootAvailable = ksuVersion != null,
    isSafeMode = isSafeMode,
    isLateLoadMode = isLateLoadMode,
    checkUpdateEnabled = false,
    latestVersionInfo = LatestVersionInfo(),
    currentManagerVersionCode = 10000,
    systemInfo = previewSystemInfo.copy(selinuxStatus = selinuxStatus),
    kernelUAPIVersion = 1,
    managerUAPIVersion = 1,
)
