package me.diksu.ultra.ui.component.bottombar

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.selection.selectable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.platform.ViewConfiguration
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.fastCoerceIn
import java.time.LocalDateTime
import kotlinx.coroutines.delay
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import me.diksu.ultra.ui.capability.NavGate
import me.diksu.ultra.ui.capability.rememberNavGates
import me.diksu.ultra.ui.component.rebootlistpopup.RebootListPopupMiuix
import me.diksu.ultra.ui.LocalMainPagerState
import me.diksu.ultra.ui.component.iosIndicatorSpecular
import me.diksu.ultra.ui.component.liquid.InnerShadow
import me.diksu.ultra.ui.component.liquid.innerShadow
import me.diksu.ultra.ui.component.liquid.lens
import me.diksu.ultra.ui.component.liquid.vibrancy
import me.diksu.ultra.ui.component.miuix.animation.DampedDragAnimation
import me.diksu.ultra.ui.component.rememberGravityRotatedHighlight
import me.diksu.ultra.ui.theme.isInDarkTheme
import top.yukonga.miuix.kmp.utils.MiuixIndication
import top.yukonga.miuix.kmp.basic.BadgedBox
import top.yukonga.miuix.kmp.basic.NavigationRail
import top.yukonga.miuix.kmp.basic.NavigationRailDefaults
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.blur.LayerBackdrop
import top.yukonga.miuix.kmp.blur.blur
import top.yukonga.miuix.kmp.blur.drawBackdrop
import top.yukonga.miuix.kmp.blur.isRuntimeShaderSupported
import top.yukonga.miuix.kmp.shader.isRenderEffectSupported
import top.yukonga.miuix.kmp.theme.MiuixTheme
import kotlin.math.roundToInt

@Composable
fun NavigationRailMiuix(
    navigationBadge: NavigationBadgeState,
    backdrop: LayerBackdrop? = null,
    modifier: Modifier = Modifier,
) {
    val mainState = LocalMainPagerState.current
    // 4.2：逐入口判定。不可用的条目只置灰自己，其余条目照常点击；整条导航不再降级。
    val gates = rememberNavGates()
    var pendingGate by remember { mutableStateOf<NavGate?>(null) }
    val destinations = BottomBarDestination.entries

    val icons = destinations.map { it.icon }

    // The destinations belong at the bottom of the column: a rail that starts at the top leaves the
    // lower half of the screen empty and parks the last destination — settings — in the middle of
    // nowhere.
    //
    // The rail's own column is Arrangement.Top and scrolls, so it offers no slot to align with and a
    // weight would be measured against an unbounded height. They are therefore not the rail's content
    // any more: the rail is an empty container and the destinations are bottom-aligned by the Box
    // below, which needs no measurement and cannot drift when the window insets settle.
    val systemViewConfiguration = LocalViewConfiguration.current
    val railViewConfiguration = remember(systemViewConfiguration) {
        object : ViewConfiguration {
            override val longPressTimeoutMillis = 300L
            override val doubleTapTimeoutMillis =
                systemViewConfiguration.doubleTapTimeoutMillis
            override val doubleTapMinTimeMillis =
                systemViewConfiguration.doubleTapMinTimeMillis
            override val touchSlop = systemViewConfiguration.touchSlop
        }
    }

    BoxWithConstraints(modifier = modifier.fillMaxHeight()) {
        // A tapped destination is fed back with a block filled in the theme's `onBackground` colour —
        // over a picture that light block is exactly the box that appears the instant a destination is
        // tapped. Transparent, the tap still works and nothing is drawn on top of the rail.
        val noPressBlock = remember {
            MiuixIndication(color = Color.Transparent)
        }
        CompositionLocalProvider(LocalIndication provides noPressBlock) {
            // Bare container: it paints the rail's background and owns the window insets. The
            // destinations are not its content — see the overlay below.
            NavigationRail(
                // Fixed width, and therefore no expand/collapse button: the library draws that button
                // itself whenever the rail is given a state, and this one is icons in a single column.
                expanded = false,
                // The frame around the page is painted once, by the screen: the rail only draws what is
                // on top of it, or the layer underneath would show through twice.
                color = Color.Transparent,
                minWidth = 64.dp,
                // No straight divider of its own: the page panel's rounded border takes that role and
                // wraps the content instead of running past it at both ends.
                showDivider = false,
            ) {}

            // The destinations are bottom-aligned by the Box, not by a spacer of measured height.
            //
            // They used to sit inside the rail's scrollable column behind a spacer computed from
            // "container height - insets - measured items height". Every input of that sum changes
            // while the window insets settle on resume, and the measured one starts at zero after a
            // re-creation, so the group was pushed down and pulled back for a few frames - the jump
            // that was still visible. Alignment cannot drift: there is nothing to compute.
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .width(64.dp)
                    .windowInsetsPadding(WindowInsets.navigationBars.only(WindowInsetsSides.Bottom))
                    .padding(vertical = NavigationRailDefaults.VerticalPadding),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                CompositionLocalProvider(LocalViewConfiguration provides railViewConfiguration) {
                    RailItems(
                        icons = icons,
                        selectedIndex = mainState.selectedPage,
                        onSelect = { mainState.animateToPage(it) },
                        badgeFor = { index -> navigationBadgeFor(index, navigationBadge) },
                        gateFor = { index -> gates[destinations[index].navEntry] ?: NavGate.Open },
                        onGate = { gate -> pendingGate = gate },
                        backdrop = backdrop,
                        onItemsHeight = {},
                    )
                }
            }
        }

        // The reboot control belongs to the app, not to the page, and this strip is where the app's
        // own furniture lives — it also fills the stretch above the destinations that used to be
        // the expand button's.
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .windowInsetsPadding(WindowInsets.statusBars.only(WindowInsetsSides.Top))
                .fillMaxHeight(),
        ) {
            Box(modifier = Modifier.align(Alignment.TopCenter).padding(top = 8.dp)) {
                // Darker than the default: over a blurred photo the icon has to hold its own.
                RebootListPopupMiuix(iconTint = MiuixTheme.colorScheme.onSurface)
            }
            Column(
                modifier = Modifier.align(BiasAlignment(0f, -0.30f)),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                SceneClock()
                Spacer(modifier = Modifier.height(10.dp))
                RailBattery()
            }
        }
    }

    NavGateDialog(
        gate = pendingGate,
        onDismiss = { pendingGate = null },
        onGoHome = {
            pendingGate = null
            mainState.animateToPage(0)
        },
    )
}

/**
 * The destinations and the selection pill that slides between them.
 *
 * The pill is drawn once for the whole group instead of once per item so it can travel continuously
 * under a held finger; the items carry only their icons. A hold grows the pill ([DampedDragAnimation]
 * presses it), the drag walks it across the items, and the release snaps it to the destination under
 * the finger — a plain tap still selects, animated by the same spring.
 */
@Composable
private fun RailItems(
    icons: List<ImageVector>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    badgeFor: (Int) -> (@Composable () -> Unit)?,
    gateFor: (Int) -> NavGate,
    onGate: (NavGate) -> Unit,
    backdrop: LayerBackdrop?,
    onItemsHeight: (Dp) -> Unit,
) {
    val count = icons.size
    val currentSelectedIndex by rememberUpdatedState(selectedIndex)
    // 手势块以 count 为 key，闭包会一直活到下一次数量变化；用 rememberUpdatedState 保证
    // 提权状态变化后，拖动判定拿到的是最新的 gate，而不是首次组合时的旧 map。
    val currentGateFor by rememberUpdatedState(gateFor)
    val currentOnGate by rememberUpdatedState(onGate)
    val animationScope = rememberCoroutineScope()
    val dampedDrag = remember(animationScope, count) {
        DampedDragAnimation(
            animationScope = animationScope,
            initialValue = selectedIndex.toFloat(),
            valueRange = 0f..(count - 1).toFloat(),
            visibilityThreshold = 0.001f,
            initialScale = 1f,
            // Same growth the floating bar grows to: past a fifth again, the pill reads as picked up.
            pressedScale = 78f / 56f,
            canDrag = { true },
            // Driven by the gestures below rather than by the class's own modifier.
            onDragStarted = {},
            onDragStopped = {},
            onDragCancelled = {},
            onDrag = { _, _ -> },
        )
    }

    // A tap, a swipe between pages, or a released drag all land here: glide the pill to the page
    // that is now showing, with the press-and-release flourish on the way.
    LaunchedEffect(selectedIndex) {
        if (dampedDrag.value.roundToInt() != selectedIndex) {
            dampedDrag.animateToValue(selectedIndex.toFloat())
        }
    }

    val density = LocalDensity.current
    var itemHeightPx by remember { mutableStateOf(0f) }
    val iconCenterOffsetPx = with(density) {
        (
            NavigationRailDefaults.ItemVerticalPadding +
                NavigationRailDefaults.CollapsedIndicatorVerticalPadding +
                11.dp
            ).toPx()
    }
    val pillShape = remember { androidx.compose.foundation.shape.RoundedCornerShape(16.dp) }
    // The lens shader needs API 33; below it the pill falls back to the solid block it replaced.
    val glass = backdrop != null && isRenderEffectSupported() && isRuntimeShaderSupported()

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .onSizeChanged { size ->
                itemHeightPx = size.height.toFloat() / count
                onItemsHeight(with(density) { size.height.toDp() })
            }
            // Keep the gesture alive while selection changes; restarting it on selectedIndex
            // would cancel the active drag during the release callback.
            .pointerInput(count) {
                var dragPositionY = 0f
                var dragTargetIndex = currentSelectedIndex
                detectDragGesturesAfterLongPress(
                    onDragStart = { position ->
                        dragPositionY = position.y
                        dampedDrag.press()
                        if (itemHeightPx > 0f) {
                            dragTargetIndex =
                                (position.y / itemHeightPx).roundToInt().coerceIn(0, count - 1)
                            dampedDrag.updateValue(
                                dragTargetIndex.toFloat(),
                            )
                        }
                    },
                    onDrag = { change, _ ->
                        change.consume()
                        dragPositionY = change.position.y
                        if (itemHeightPx > 0f) {
                            dragTargetIndex =
                                (dragPositionY / itemHeightPx).roundToInt().coerceIn(0, count - 1)
                            dampedDrag.updateValue(
                                (dragPositionY / itemHeightPx).coerceIn(0f, (count - 1).toFloat()),
                            )
                        }
                    },
                    onDragEnd = {
                        val target = dragTargetIndex.coerceIn(0, count - 1)
                        val gate = currentGateFor(target)
                        if (gate.available) {
                            dampedDrag.updateValue(target.toFloat())
                            dampedDrag.release()
                            onSelect(target)
                        } else {
                            // 拖到不可用入口：药丸退回当前页，并给出原因，不切换页面。
                            dampedDrag.updateValue(currentSelectedIndex.toFloat())
                            dampedDrag.release()
                            currentOnGate(gate)
                        }
                    },
                    onDragCancel = {
                        dampedDrag.updateValue(currentSelectedIndex.toFloat())
                        dampedDrag.release()
                    },
                )
            },
    ) {
        // First in the box, so the icons draw over it: the pill sits behind, as the per-item block
        // it replaced did.
        SelectionPill(
            dampedDrag = dampedDrag,
            itemHeightPx = itemHeightPx,
            iconCenterOffsetPx = iconCenterOffsetPx,
            glass = glass,
            backdrop = backdrop,
            shape = pillShape,
            modifier = Modifier.align(Alignment.TopCenter),
        )
        Column(modifier = Modifier.fillMaxWidth()) {
            icons.forEachIndexed { index, icon ->
                val gate = gateFor(index)
                RailItem(
                    selected = index == selectedIndex,
                    onClick = { if (gate.available) onSelect(index) else onGate(gate) },
                    icon = icon,
                    badge = badgeFor(index),
                    dimmed = !gate.available,
                )
            }
        }
    }
}

/** One destination: icon over an empty label, sized so every item in the column is the same height. */
@Composable
private fun RailItem(
    selected: Boolean,
    onClick: () -> Unit,
    icon: ImageVector,
    badge: (@Composable () -> Unit)?,
    dimmed: Boolean,
) {
    val tint = MiuixTheme.colorScheme.onSurfaceContainer
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .selectable(
                selected = selected,
                onClick = onClick,
                role = Role.Tab,
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = NavigationRailDefaults.ItemVerticalPadding),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                modifier = Modifier
                    .padding(
                        horizontal = NavigationRailDefaults.ExpandedItemContentHorizontalPadding,
                        vertical = NavigationRailDefaults.CollapsedIndicatorVerticalPadding,
                    )
                    .alpha(if (dimmed) NavGateDisabledAlpha else 1f),
            ) {
                val iconContent: @Composable () -> Unit = {
                    Image(
                        // A shade under the library's 24dp: four icons in a column read heavy at full size.
                        modifier = Modifier.size(22.dp),
                        imageVector = icon,
                        // Decorative: the adjacent label already names the item; avoids TalkBack double-read.
                        contentDescription = null,
                        colorFilter = ColorFilter.tint(tint),
                    )
                }
                if (badge != null) {
                    BadgedBox(badge = { badge() }) { iconContent() }
                } else {
                    iconContent()
                }
            }
            Spacer(modifier = Modifier.height(NavigationRailDefaults.IconTextSpacing))
            Text(
                text = "",
                color = tint,
                fontSize = NavigationRailDefaults.LabelFontSize,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}

/**
 * The liquid-glass selection pill: one backdrop-refracting block that rides the held finger between
 * destinations, growing while the hold lasts and easing back once the destination is picked.
 */
@Composable
private fun SelectionPill(
    dampedDrag: DampedDragAnimation,
    itemHeightPx: Float,
    iconCenterOffsetPx: Float,
    glass: Boolean,
    backdrop: LayerBackdrop?,
    shape: androidx.compose.foundation.shape.RoundedCornerShape,
    modifier: Modifier = Modifier,
) {
    val isInDark = isInDarkTheme()
    val pillTint = MiuixTheme.colorScheme.surfaceContainerHigh
    val specular = rememberGravityRotatedHighlight(iosIndicatorSpecular, extraDegrees = 90f)

    Box(
        modifier = modifier
            // Hidden until the items have been measured, so the pill never flashes at rest.
            .alpha(if (itemHeightPx > 0f) 1f else 0f)
            .then(
                if (itemHeightPx > 0f) {
                    Modifier.graphicsLayer {
                        translationY =
                            dampedDrag.value * itemHeightPx + iconCenterOffsetPx - size.height / 2f
                    }
                } else {
                    Modifier
                },
            )
            .size(width = 52.dp, height = 32.dp)
            .dropShadow(
                shape = shape,
                shadow = Shadow(
                    radius = 8.dp,
                    color = Color.Black,
                    alpha = if (isInDark) 0.2f else 0.1f,
                ),
            )
            .then(
                if (glass && backdrop != null) {
                    Modifier
                        .drawBackdrop(
                            backdrop = backdrop,
                            shape = { shape },
                            effects = {
                                val progress = dampedDrag.pressProgress
                                vibrancy()
                                blur(4.dp.toPx(), 4.dp.toPx())
                                lens(
                                    // A shallow bend at rest so the block reads as glass before it is
                                    // ever touched; the hold deepens it the way the floating bar does.
                                    refractionHeight = 3.dp.toPx() + 7.dp.toPx() * progress,
                                    refractionAmount = 3.dp.toPx() + 11.dp.toPx() * progress,
                                    depthEffect = true,
                                    chromaticAberration = 0.5f,
                                )
                            },
                            highlight = {
                                specular.value.copy(alpha = 0.3f + 0.7f * dampedDrag.pressProgress)
                            },
                            layerBlock = {
                                scaleX = dampedDrag.scaleX
                                scaleY = dampedDrag.scaleY
                                // Squash across the travel and stretch along it: the pill leans the
                                // way it is going instead of sliding rigid.
                                val velocity = dampedDrag.velocity / 10f
                                scaleY /= 1f - (velocity * 0.75f).fastCoerceIn(-0.2f, 0.2f)
                                scaleX *= 1f - (velocity * 0.25f).fastCoerceIn(-0.2f, 0.2f)
                            },
                            onDrawSurface = {
                                drawRect(pillTint.copy(alpha = 0.55f))
                                val progress = dampedDrag.pressProgress
                                drawRect(
                                    color = if (!isInDark) Color.Black.copy(alpha = 0.1f)
                                    else Color.White.copy(alpha = 0.1f),
                                    alpha = 1f - progress,
                                )
                                drawRect(Color.Black.copy(alpha = 0.03f * progress))
                            },
                        )
                        .innerShadow(shape = shape) {
                            InnerShadow(
                                radius = 8.dp * dampedDrag.pressProgress,
                                color = Color.Black.copy(alpha = 0.15f),
                                alpha = dampedDrag.pressProgress,
                            )
                        }
                } else {
                    Modifier.graphicsLayer {
                        scaleX = dampedDrag.scaleX
                        scaleY = dampedDrag.scaleY
                        val velocity = dampedDrag.velocity / 10f
                        scaleY /= 1f - (velocity * 0.75f).fastCoerceIn(-0.2f, 0.2f)
                        scaleX *= 1f - (velocity * 0.25f).fastCoerceIn(-0.2f, 0.2f)
                    }.background(pillTint, shape)
                },
            ),
    )
}

/**
 * The time above the destinations: the hour over the minute, the date under both.
 *
 * It turns over on the minute, which is the only unit worth showing here — not a stopwatch.
 */
@Composable
private fun SceneClock(modifier: Modifier = Modifier) {
    var now by remember { mutableStateOf(LocalDateTime.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = LocalDateTime.now()
            delay(60_000L - System.currentTimeMillis() % 60_000L)
        }
    }

    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = "%02d".format(now.hour),
            fontSize = 30.sp,
            fontWeight = FontWeight.Medium,
            color = MiuixTheme.colorScheme.onBackground,
        )
        Text(
            text = "%02d".format(now.minute),
            fontSize = 30.sp,
            fontWeight = FontWeight.Medium,
            color = MiuixTheme.colorScheme.onBackground,
        )
        Text(
            text = "%d/%d".format(now.monthValue, now.dayOfMonth),
            fontSize = MiuixTheme.textStyles.body2.fontSize,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            modifier = Modifier.padding(top = 2.dp),
        )
    }
}

/**
 * The battery under the clock: a small cell glyph plus the percentage.
 *
 * It listens to the sticky `ACTION_BATTERY_CHANGED` broadcast instead of polling, so the rail
 * updates on its own when the level moves and costs nothing while the screen is idle. An
 * unreadable battery (very rare: some emulators) shows as `--` rather than a fake 0%.
 */
@Composable
private fun RailBattery(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var level by remember { mutableStateOf(-1) }
    var charging by remember { mutableStateOf(false) }

    DisposableEffect(context) {
        fun read(intent: Intent) {
            val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, 100).coerceAtLeast(1)
            val raw = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
            level = if (raw < 0) -1 else (raw * 100 / scale).coerceIn(0, 100)
            val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
            charging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                status == BatteryManager.BATTERY_STATUS_FULL
        }

        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                intent?.let(::read)
            }
        }
        // The sticky broadcast carries the current level immediately, so the first frame already
        // shows the real number instead of flashing "--".
        context.registerReceiver(receiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED))?.let(::read)
        onDispose {
            runCatching { context.unregisterReceiver(receiver) }
        }
    }

    val unknown = level < 0
    val low = !unknown && level <= 15
    val glyphColor = when {
        low -> MiuixTheme.colorScheme.error
        charging -> MiuixTheme.colorScheme.primary
        else -> MiuixTheme.colorScheme.onBackground
    }
    val outlineColor = MiuixTheme.colorScheme.onSurfaceVariantSummary

    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.size(width = 20.dp, height = 10.dp)) {
            val stroke = 1.dp.toPx()
            val capWidth = 1.8.dp.toPx()
            val bodyWidth = size.width - capWidth - stroke
            val bodyHeight = size.height
            val radius = CornerRadius(3.dp.toPx(), 3.dp.toPx())

            drawRoundRect(
                color = outlineColor,
                topLeft = Offset(stroke / 2f, stroke / 2f),
                size = Size(bodyWidth, bodyHeight - stroke),
                cornerRadius = radius,
                style = Stroke(width = stroke),
            )
            drawRoundRect(
                color = outlineColor,
                topLeft = Offset(bodyWidth + stroke, bodyHeight * 0.34f),
                size = Size(capWidth, bodyHeight * 0.32f),
                cornerRadius = CornerRadius(0.9f.dp.toPx(), 0.9f.dp.toPx()),
            )
            if (!unknown) {
                val innerWidth = (bodyWidth - stroke * 3f).coerceAtLeast(0f) * level / 100f
                if (innerWidth > 0f) {
                    drawRoundRect(
                        color = glyphColor,
                        topLeft = Offset(stroke * 1.5f, stroke * 1.5f),
                        size = Size(innerWidth, bodyHeight - stroke * 3f),
                        cornerRadius = CornerRadius(2.5f.dp.toPx(), 2.5f.dp.toPx()),
                    )
                }
            }
        }
    }
}
