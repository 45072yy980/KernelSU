package me.diksu.ultra.foxsu.ui.screen.onboarding

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.Extension
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.lerp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.diksu.ultra.BuildConfig
import me.diksu.ultra.R
import me.diksu.ultra.foxsu.data.settings.CustomizationStore
import me.diksu.ultra.foxsu.ui.LocalUiMode
import me.diksu.ultra.foxsu.ui.UiMode
import me.diksu.ultra.foxsu.ui.component.applyUiMode
import me.diksu.ultra.foxsu.ui.component.bottombar.DockKitStyle
import me.diksu.ultra.foxsu.ui.component.bottombar.DockButton
import me.diksu.ultra.foxsu.ui.screen.ag.AgEngineIcons
import me.diksu.ultra.foxsu.ui.util.WelcomeFacts
import me.diksu.ultra.foxsu.ui.util.readWelcomeFacts
import kotlin.math.absoluteValue

private const val PAGE_COUNT = 5

private const val REPO_URL = "https://github.com/Cmry-mlc/FoxSu"
private const val WEBSITE_URL = "https://cmry-mlc.github.io/FoxSu/zh_CN/"

/**
 * 首次安装后的欢迎页。
 *
 * 上一版是「大标题 + 一句话 + 六行文字」的五页说明书，信息密度高但没有任何东西可看。
 * 这一版把要讲的东西做成能看的物件：
 *
 *  · 首屏直接报出这台设备的真实读数（型号 / 内核 / 模块数 / 授权数）；
 *  · 皮肤页有一台迷你手机，点卡片整机配色当场变；
 *  · 底栏页是十个真实造型的选择器，点一下底栏立刻变形，旁边两个开关真的写库；
 *  · 内核页三块读数来自设备本身，不是写死的文案。
 *
 * 所有可点的东西都走和设置页相同的入口，没有「演示用假开关」。
 */
@Composable
fun WelcomeOverlay(onFinish: () -> Unit) {
    Dialog(
        onDismissRequest = onFinish,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = false,
        ),
    ) {
        WelcomeContent(onFinish = onFinish)
    }
}

@Composable
private fun WelcomeContent(onFinish: () -> Unit) {
    val t = onbTokens(LocalUiMode.current)
    val pager = rememberPagerState(pageCount = { PAGE_COUNT })
    val scope = rememberCoroutineScope()
    val facts = rememberWelcomeFactsState()

    Box(
        Modifier
            .fillMaxSize()
            .background(t.bg),
    ) {
        AuroraBackdrop(t, Modifier.fillMaxSize())

        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp),
        ) {
            OnbTopBar(page = pager.currentPage, t = t, onSkip = onFinish)

            HorizontalPager(
                state = pager,
                modifier = Modifier.weight(1f),
                beyondViewportPageCount = 1,
                pageSpacing = 16.dp,
            ) { index ->
                val offset = (pager.currentPage - index + pager.currentPageOffsetFraction)
                    .absoluteValue
                    .coerceIn(0f, 1f)
                Box(
                    Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            alpha = lerp(1f, 0f, offset * 1.35f).coerceIn(0f, 1f)
                            translationY = offset * 30.dp.toPx()
                            val s = lerp(1f, 0.93f, offset)
                            scaleX = s
                            scaleY = s
                        },
                ) {
                    when (index) {
                        0 -> BrandPage(t, facts)
                        1 -> SkinPage(t)
                        2 -> DockPage(t)
                        3 -> KernelPage(t, facts)
                        else -> CustomPage(t)
                    }
                }
            }

            BottomBar(
                page = pager.currentPage,
                t = t,
                onSkip = onFinish,
                onNext = {
                    if (pager.currentPage == PAGE_COUNT - 1) onFinish()
                    else scope.launch { pager.animateScrollToPage(pager.currentPage + 1) }
                },
            )
        }
    }
}

/* ───────────────────────── 外壳 ───────────────────────── */

@Composable
private fun OnbTopBar(page: Int, t: OnbTokens, onSkip: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(top = 10.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(26.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(t.accent.copy(alpha = 0.14f))
                .border(1.dp, t.accent.copy(alpha = 0.22f), RoundedCornerShape(8.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Image(
                painter = painterResource(R.drawable.fx_foxsu_mark),
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.size(22.dp),
            )
        }
        Spacer(Modifier.width(8.dp))
        Column {
            Text(
                "FoxSu",
                fontSize = 13.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = (-0.2).sp,
                color = t.tx,
            )
            Text(
                "v${BuildConfig.VERSION_NAME}",
                fontSize = 8.sp,
                fontFamily = FontFamily.Monospace,
                color = t.tx3,
            )
        }
        Spacer(Modifier.weight(1f))
        Text(
            String.format("%02d", page + 1) + " / " + String.format("%02d", PAGE_COUNT),
            fontSize = 9.5.sp,
            letterSpacing = 1.4.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            color = t.tx3,
        )
        Spacer(Modifier.width(12.dp))
        Text(
            stringResource(R.string.fx_onboarding_skip),
            fontSize = 10.5.sp,
            fontWeight = FontWeight.Bold,
            color = t.tx2,
            modifier = Modifier
                .clip(RoundedCornerShape(99.dp))
                .clickable(onClick = onSkip)
                .padding(horizontal = 9.dp, vertical = 8.dp),
        )
    }
}

@Composable
private fun BottomBar(page: Int, t: OnbTokens, onSkip: () -> Unit, onNext: () -> Unit) {
    Column(Modifier.padding(top = 10.dp, bottom = 8.dp)) {
        Hairline(t)
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(5.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                repeat(PAGE_COUNT) { i ->
                    val active = i <= page
                    val w by animateFloatAsState(
                        targetValue = if (i == page) 22f else 10f,
                        animationSpec = tween(260),
                        label = "onb-seg",
                    )
                    Box(
                        Modifier
                            .height(3.dp)
                            .width(w.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(if (active) t.accent else t.line),
                    )
                }
            }
            Spacer(Modifier.weight(1f))
            val last = page == PAGE_COUNT - 1
            Row(
                Modifier
                    .height(46.dp)
                    .clip(RoundedCornerShape(15.dp))
                    .background(Brush.horizontalGradient(listOf(t.accent, t.accent2)))
                    .clickable(onClick = onNext)
                    .padding(horizontal = 20.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    stringResource(if (last) R.string.fx_onboarding_start else R.string.fx_onboarding_next),
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 0.8.sp,
                    color = Color.White,
                )
                Spacer(Modifier.width(8.dp))
                Icon(
                    Icons.AutoMirrored.Rounded.ArrowForward,
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.9f),
                    modifier = Modifier.size(16.dp),
                )
            }
        }
    }
}

/* ───────────────────────── 第一页：品牌 + 实时读数 ───────────────────────── */

@Composable
private fun BrandPage(t: OnbTokens, facts: WelcomeFacts) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(6.dp))

        Box(contentAlignment = Alignment.Center) {
            // 柔光托底：把方图边缘晕进背景
            Box(
                Modifier
                    .size(236.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.radialGradient(
                            listOf(
                                t.accent.copy(alpha = if (t.dark) 0.26f else 0.18f),
                                t.accent2.copy(alpha = 0.10f),
                                Color.Transparent,
                            ),
                        ),
                    ),
            )
            Image(
                painter = painterResource(R.drawable.fx_foxsu_mark),
                contentDescription = "FoxSu",
                contentScale = ContentScale.Fit,
                modifier = Modifier.size(176.dp),
            )
        }

        Spacer(Modifier.height(4.dp))
        // 彩色品牌字：三段渐变跟随全局强调色，左起「主色 → 中段 → 次强调」，
        // 换配色后首屏立刻变色，和其它页面是同一套颜色
        val brandAccent = me.diksu.ultra.foxsu.ui.theme.currentAccent
        Text(
            "FoxSu",
            fontSize = 42.sp,
            fontWeight = FontWeight.ExtraBold,
            letterSpacing = (-1.4).sp,
            style = TextStyle(
                brush = Brush.linearGradient(
                    listOf(brandAccent.primary, brandAccent.mid, brandAccent.secondary),
                ),
            ),
            modifier = Modifier.drawBehind {
                // 极淡的彩色投影，让字从浅底上"浮"起来
                drawRoundRect(
                    brush = Brush.verticalGradient(
                        listOf(brandAccent.primary.copy(alpha = 0.18f), Color.Transparent),
                    ),
                    topLeft = Offset(0f, size.height * 0.72f),
                    size = Size(size.width, size.height * 0.34f),
                    cornerRadius = CornerRadius(size.height * 0.16f),
                )
            },
        )
        Spacer(Modifier.height(6.dp))
        Text(
            stringResource(R.string.fx_onboarding_title_1),
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = t.accent,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            stringResource(R.string.fx_onboarding_body_1),
            fontSize = 12.sp,
            lineHeight = 19.sp,
            color = t.tx2,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 6.dp),
        )

        Spacer(Modifier.height(18.dp))
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FactTile(stringResource(R.string.fx_onb_fact_device), facts.device, t, Modifier.weight(1f))
            FactTile(stringResource(R.string.fx_onb_fact_kernel), facts.kernel, t, Modifier.weight(1f), accent = true)
        }
        Spacer(Modifier.height(8.dp))
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FactTile(stringResource(R.string.fx_onb_fact_modules), "${facts.modules}", t, Modifier.weight(1f), accent = true)
            FactTile(
                stringResource(R.string.fx_onb_fact_su),
                if (facts.root) "${facts.su}" else "—",
                t,
                Modifier.weight(1f),
            )
        }

        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            listOf(
                R.string.fx_onboarding_chip_1,
                R.string.fx_onboarding_chip_2,
                R.string.fx_onboarding_chip_3,
            ).forEach { res ->
                Text(
                    stringResource(res),
                    fontSize = 9.sp,
                    letterSpacing = 0.8.sp,
                    fontWeight = FontWeight.Bold,
                    color = t.tx2,
                    modifier = Modifier
                        .clip(RoundedCornerShape(99.dp))
                        .background(t.chipBg)
                        .border(1.dp, t.chipBd, RoundedCornerShape(99.dp))
                        .padding(horizontal = 10.dp, vertical = 5.dp),
                )
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}

/* ───────────────────────── 第二页：皮肤 ───────────────────────── */

@Composable
private fun SkinPage(t: OnbTokens) {
    val current = LocalUiMode.current
    var applied by remember { mutableStateOf(current) }
    val skin = OnbSkins.firstOrNull { it.mode == applied } ?: OnbSkins.first()

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            KickerMark(t, Icons.Rounded.Palette)
            Spacer(Modifier.width(8.dp))
            OnbKicker(stringResource(R.string.fx_onboarding_kicker_2), t)
        }
        Spacer(Modifier.height(10.dp))
        OnbTitle(stringResource(R.string.fx_onboarding_title_2), t)
        Spacer(Modifier.height(8.dp))
        OnbBody(stringResource(R.string.fx_onboarding_body_2), t)
        Spacer(Modifier.height(16.dp))

        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
        ) {
            MiniPhone(
                t = t,
                accent = t.accent,
                accent2 = t.accent2,
                screenBg = skin.bg,
                title = stringResource(R.string.fx_onb_mini_home),
                dock = DockButton.of(CustomizationStore.current.dockButton),
                dark = skin.dark,
                glassDock = false,
                selectedDock = 0,
                width = 188.dp,
                height = 384.dp,
            ) {
                MiniCard(t.accent, Icons.Rounded.Shield, true, "Termux", "已授权 · 永久", skin.dark)
                Spacer(Modifier.height(6.dp))
                MiniCard(t.accent, Icons.Rounded.Extension, false, "Zygisk Next", "已安装 · 4 个模块", skin.dark)
            }
        }

        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OnbSkins.forEach { item ->
                SkinChip(
                    item = item,
                    selected = item.mode == applied,
                    t = t,
                    modifier = Modifier.weight(1f),
                ) {
                    applied = item.mode
                    applyUiMode(item.mode)
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        Caption(stringResource(item_desc(applied)), t)
        Spacer(Modifier.height(10.dp))
    }
}

@Composable
private fun SkinChip(
    item: OnbSkin,
    selected: Boolean,
    t: OnbTokens,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Column(
        modifier
            .clip(RoundedCornerShape(13.dp))
            .background(if (selected) t.selBg else t.cardBg)
            .border(
                if (selected) 1.5.dp else 1.dp,
                if (selected) t.selBd else t.cardBd,
                RoundedCornerShape(13.dp),
            )
            .clickable(onClick = onClick)
            .padding(6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(30.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(item.bg),
        ) {
            Box(
                Modifier
                    .padding(5.dp)
                    .size(width = 14.dp, height = 5.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(t.accent),
            )
        }
        Spacer(Modifier.height(5.dp))
        Text(
            item.sub,
            fontSize = 8.5.sp,
            fontWeight = FontWeight.Bold,
            color = if (selected) t.accent else t.tx2,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

private fun item_desc(mode: UiMode): Int =
    (OnbSkins.firstOrNull { it.mode == mode } ?: OnbSkins.first()).descRes

/* ───────────────────────── 第三页：底栏工坊 ───────────────────────── */

@Composable
private fun DockPage(t: OnbTokens) {
    val store = CustomizationStore.current
    var style by remember { mutableIntStateOf(store.dockButton) }
    var autoHide by remember { mutableStateOf(store.dockAutoHide > 0) }
    var glass by remember { mutableStateOf(store.bottomBarLiquidGlass) }
    val dock = DockButton.of(style)

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            KickerMark(t, Icons.Rounded.Tune)
            Spacer(Modifier.width(8.dp))
            OnbKicker(stringResource(R.string.fx_onb_kicker_dock), t)
        }
        Spacer(Modifier.height(10.dp))
        OnbTitle(stringResource(R.string.fx_onb_title_dock), t)
        Spacer(Modifier.height(8.dp))
        OnbBody(stringResource(R.string.fx_onb_body_dock), t)
        Spacer(Modifier.height(14.dp))

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
            MiniPhone(
                t = t,
                accent = t.accent,
                accent2 = t.accent2,
                screenBg = t.bg,
                title = stringResource(R.string.fx_onb_mini_home),
                dock = dock,
                dark = t.dark,
                glassDock = glass,
                selectedDock = 0,
                width = 176.dp,
                height = 330.dp,
            ) {
                MiniCard(t.accent, Icons.Rounded.Shield, true, "Termux", "已授权 · 永久", t.dark)
                Spacer(Modifier.height(6.dp))
                MiniCard(t.accent, Icons.Rounded.Extension, false, "Zygisk Next", "已安装 · 4 个模块", t.dark)
            }
        }

        Spacer(Modifier.height(14.dp))
        // 十种造型：2 行 5 列
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            DockButton.all.chunked(5).forEach { row ->
                Row(Modifier.fillMaxWidth()) {
                    row.forEach { item ->
                        DockShapeCell(
                            style = item,
                            selected = item.ordinal == style,
                            t = t,
                            modifier = Modifier.weight(1f),
                        ) {
                            style = item.ordinal
                            CustomizationStore.setDockButton(item.ordinal)
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PillToggle(stringResource(R.string.fx_onb_dock_autohide), autoHide, t) {
                autoHide = !autoHide
                CustomizationStore.setDockAutoHide(if (autoHide) 8 else 0)
            }
            PillToggle(stringResource(R.string.fx_onb_dock_glass), glass, t) {
                glass = !glass
                CustomizationStore.setBottomBarLiquidGlass(glass)
            }
        }
        Spacer(Modifier.height(8.dp))
        Caption(stringResource(R.string.fx_onb_dock_note), t)
        Spacer(Modifier.height(10.dp))
    }
}

/* ───────────────────────── 第四页：内核 ───────────────────────── */

@Composable
private fun KernelPage(t: OnbTokens, facts: WelcomeFacts) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            KickerMark(t, Icons.Rounded.Bolt)
            Spacer(Modifier.width(8.dp))
            OnbKicker(stringResource(R.string.fx_onboarding_kicker_4), t)
        }
        Spacer(Modifier.height(10.dp))
        OnbTitle(stringResource(R.string.fx_onb_title_kernel), t)
        Spacer(Modifier.height(8.dp))
        OnbBody(stringResource(R.string.fx_onb_body_kernel), t)
        Spacer(Modifier.height(16.dp))

        KernelCard(
            icon = AgEngineIcons.Mount,
            title = "Magic Mount-rs",
            value = "v4.0.12",
            desc = stringResource(R.string.fx_onb_k_mm),
            t = t,
            accent = true,
        )
        Spacer(Modifier.height(9.dp))
        KernelCard(
            icon = Icons.Rounded.Shield,
            title = stringResource(R.string.fx_onb_k_su),
            value = if (facts.root) "${facts.su}" else "—",
            desc = stringResource(R.string.fx_onb_k_su_desc),
            t = t,
        )
        Spacer(Modifier.height(9.dp))
        KernelCard(
            icon = Icons.Rounded.Extension,
            title = stringResource(R.string.fx_onb_k_module),
            value = "${facts.modules}",
            desc = stringResource(R.string.fx_onb_k_module_desc),
            t = t,
        )

        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            listOf(
                R.string.fx_onb_tag_sulog,
                R.string.fx_onb_tag_profile,
                R.string.fx_onb_tag_flash,
                R.string.fx_onb_tag_soft,
            ).forEach { res ->
                Text(
                    stringResource(res),
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    color = t.tx2,
                    modifier = Modifier
                        .clip(RoundedCornerShape(99.dp))
                        .background(t.chipBg)
                        .border(1.dp, t.chipBd, RoundedCornerShape(99.dp))
                        .padding(horizontal = 9.dp, vertical = 5.dp),
                )
            }
        }

        Spacer(Modifier.height(12.dp))
        Caption(stringResource(R.string.fx_onb_kernel_note), t)
        Spacer(Modifier.height(10.dp))
    }
}

@Composable
private fun KernelCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    value: String,
    desc: String,
    t: OnbTokens,
    accent: Boolean = false,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(t.cardBg)
            .border(
                1.dp,
                if (accent) t.accent.copy(alpha = 0.30f) else t.cardBd,
                RoundedCornerShape(18.dp),
            )
            .padding(horizontal = 13.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(38.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(
                    if (accent) Brush.linearGradient(listOf(t.accent, t.accent2))
                    else Brush.linearGradient(
                        listOf(t.accent.copy(alpha = 0.16f), t.accent.copy(alpha = 0.08f)),
                    ),
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = if (accent) Color.White else t.accent,
                modifier = Modifier.size(19.dp),
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 13.5.sp, fontWeight = FontWeight.ExtraBold, color = t.tx)
            Spacer(Modifier.height(2.dp))
            Text(desc, fontSize = 10.sp, lineHeight = 14.sp, color = t.tx3)
        }
        Spacer(Modifier.width(10.dp))
        Text(
            value,
            fontSize = 16.sp,
            fontWeight = FontWeight.ExtraBold,
            fontFamily = FontFamily.Monospace,
            color = if (accent) t.accent else t.tx,
        )
    }
}

/* ───────────────────────── 第五页：定制与特效 ───────────────────────── */

/**
 * 第五页讲「这一版真正加了什么」。
 *
 * 旧版是仓库导流页（官网 / GitHub 两个按钮），信息已经过时——现在真正值得说的是
 * 定制中心里的那套东西：七套强调色、二十一胶囊样式、十种按钮形状、特效开关。
 * 每一项都用真实控件（滑块 / 开关 / 色块），不是示意图。
 */
@Composable
private fun CustomPage(t: OnbTokens) {
    val custom = CustomizationStore.current

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            KickerMark(t, Icons.Rounded.Tune)
            Spacer(Modifier.width(8.dp))
            OnbKicker("CUSTOMIZE · 定制中心", t)
        }
        Spacer(Modifier.height(10.dp))
        OnbTitle("从头到脚，都是你的", t)
        Spacer(Modifier.height(8.dp))
        OnbBody("颜色、胶囊、按钮、动效四个维度全部可调，改完立刻生效，不用重启。", t)
        Spacer(Modifier.height(16.dp))

        // ① 强调色：七块可点的真实色卡
        OnbMiniHead("① 全局强调色", "${me.diksu.ultra.foxsu.ui.theme.AccentPalettes.size} 套 + 自定义", t)
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            me.diksu.ultra.foxsu.ui.theme.AccentPalettes.forEachIndexed { i, p ->
                val on = custom.accentCustom == 0 && custom.accentPalette == i
                Box(
                    Modifier
                        .size(34.dp)
                        .clip(RoundedCornerShape(11.dp))
                        .background(Brush.linearGradient(listOf(p.primary, p.mid, p.secondary)))
                        .then(
                            if (on) Modifier.border(2.dp, t.tx, RoundedCornerShape(11.dp))
                            else Modifier.border(1.dp, t.cardBd, RoundedCornerShape(11.dp))
                        )
                        .clickable { CustomizationStore.setAccentPalette(i) },
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            "当前：${me.diksu.ultra.foxsu.ui.theme.currentAccent.label}　·　按钮、选中态、底栏指示器、标题渐变同时换色",
            fontSize = 9.5.sp, color = t.tx3,
        )

        Spacer(Modifier.height(16.dp))
        // ② 胶囊底栏：横向滑动的缩略条，点一下真的换
        OnbMiniHead("② 胶囊底栏", "21 套样式 · 左右滑动", t)
        Spacer(Modifier.height(8.dp))
        androidx.compose.foundation.lazy.LazyRow(
            horizontalArrangement = Arrangement.spacedBy(7.dp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 1.dp),
        ) {
            items(DockKitStyle.entries.size) { i ->
                val st = DockKitStyle.entries[i]
                val on = custom.dockStyle == st.key
                Column(
                    Modifier
                        .width(62.dp)
                        .clip(RoundedCornerShape(13.dp))
                        .background(if (on) t.accent.copy(alpha = 0.12f) else t.cardBg)
                        .border(
                            if (on) 1.6.dp else 1.dp,
                            if (on) t.accent.copy(alpha = 0.5f) else t.cardBd,
                            RoundedCornerShape(13.dp),
                        )
                        .clickable { CustomizationStore.setDockStyle(st.key) }
                        .padding(vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    DockPreviewBar(st, t, on)
                    Spacer(Modifier.height(5.dp))
                    Text(
                        st.title,
                        fontSize = 8.sp,
                        fontWeight = if (on) FontWeight.ExtraBold else FontWeight.Bold,
                        color = if (on) t.accent else t.tx3,
                        maxLines = 1,
                    )
                }
            }
        }

        Spacer(Modifier.height(16.dp))
        // ③ 按钮形状 + ④ 特效档位：两个小卡并排
        Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
            OnbMiniCard(t, Modifier.weight(1f)) {
                Text("③ 按钮形状", fontSize = 10.sp, fontWeight = FontWeight.ExtraBold, color = t.tx)
                Spacer(Modifier.height(3.dp))
                Text(
                    "10 种形态，横向滑动选",
                    fontSize = 8.5.sp, color = t.tx3,
                )
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    listOf("圆", "方", "键", "胶").forEachIndexed { i, tx ->
                        val on = custom.dockButton == i
                        Text(
                            tx,
                            fontSize = 8.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (on) Color.White else t.tx2,
                            modifier = Modifier
                                .clip(RoundedCornerShape(7.dp))
                                .background(if (on) t.accent else t.chipBg)
                                .clickable { CustomizationStore.setDockButton(i) }
                                .padding(horizontal = 7.dp, vertical = 4.dp),
                        )
                    }
                }
            }
            OnbMiniCard(t, Modifier.weight(1f)) {
                Text("④ 动效开关", fontSize = 10.sp, fontWeight = FontWeight.ExtraBold, color = t.tx)
                Spacer(Modifier.height(3.dp))
                Text(
                    when (custom.fxLevel) {
                        0 -> "已关闭：零动画，最省电"
                        1 -> "克制：角度减半、颗粒更少"
                        else -> "全开：粒子边 + 3D 倾斜"
                    },
                    fontSize = 8.5.sp, color = t.tx3, lineHeight = 12.sp,
                )
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    listOf("关", "轻", "全").forEachIndexed { i, tx ->
                        val on = custom.fxLevel == i
                        Text(
                            tx,
                            fontSize = 8.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (on) Color.White else t.tx2,
                            modifier = Modifier
                                .clip(RoundedCornerShape(7.dp))
                                .background(if (on) t.accent else t.chipBg)
                                .clickable { CustomizationStore.setFxLevel(i) }
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(16.dp))
        // ⑤ 品牌卡（保留仓库入口，但压缩成一条）
        Box(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(Brush.linearGradient(listOf(t.accent, t.accent2))),
        ) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 15.dp, vertical = 15.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier
                        .size(46.dp)
                        .clip(RoundedCornerShape(15.dp))
                        .background(Color.White.copy(alpha = 0.18f))
                        .border(1.dp, Color.White.copy(alpha = 0.32f), RoundedCornerShape(15.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    Image(
                        painter = painterResource(R.drawable.fx_foxsu_mark),
                        contentDescription = null,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.size(40.dp),
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        "FoxSu · v${BuildConfig.VERSION_NAME}",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color.White,
                    )
                    Spacer(Modifier.height(3.dp))
                    Text(
                        "GPL-3.0 · 开源 · 基于 KernelSU",
                        fontSize = 9.5.sp,
                        color = Color.White.copy(alpha = 0.82f),
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    RepoStat("21", "胶囊")
                    Spacer(Modifier.height(3.dp))
                    RepoStat("7", "配色")
                }
            }
        }

        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
            val uriHandler = LocalUriHandler.current
            SmallLink(t, "官网", Modifier.weight(1f)) { uriHandler.openUri(WEBSITE_URL) }
            SmallLink(t, "GitHub", Modifier.weight(1f)) { uriHandler.openUri(REPO_URL) }
        }
        Spacer(Modifier.height(10.dp))
    }
}

/** 底栏缩略小条：每种样式一个色块组合，扫一眼能分辨形状。 */
@Composable
private fun DockPreviewBar(style: DockKitStyle, t: OnbTokens, on: Boolean) {
    val ac = if (on) t.accent else t.tx3
    val ac2 = if (on) t.accent2 else t.tx3.copy(alpha = 0.6f)
    Box(
        Modifier.width(42.dp).height(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            repeat(4) { i ->
                val isCenter = i == 1
                Box(
                    Modifier
                        .size(if (isCenter) 9.dp else 7.dp)
                        .clip(
                            when (style) {
                                DockKitStyle.FILM -> RoundedCornerShape(2.dp)
                                DockKitStyle.TRACK -> RoundedCornerShape(2.dp)
                                DockKitStyle.STICKER -> RoundedCornerShape(3.dp)
                                else -> CircleShape
                            }
                        )
                        .background(
                            if (isCenter) Brush.linearGradient(listOf(ac, ac2))
                            else Brush.linearGradient(listOf(ac.copy(alpha = 0.45f), ac2.copy(alpha = 0.45f)))
                        ),
                )
            }
        }
    }
}

/** 品牌卡右侧的两格读数（胶囊套数 / 配色套数）。 */
@Composable
private fun RepoStat(value: String, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            value,
            fontSize = 15.sp,
            fontWeight = FontWeight.ExtraBold,
            color = Color.White,
            fontFamily = FontFamily.Monospace,
        )
        Spacer(Modifier.width(4.dp))
        Text(label, fontSize = 8.sp, color = Color.White.copy(alpha = 0.75f))
    }
}

@Composable
private fun OnbMiniHead(title: String, tail: String, t: OnbTokens) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(title, fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, color = t.tx)
        Spacer(Modifier.weight(1f))
        Text(tail, fontSize = 8.5.sp, color = t.tx3)
    }
}

@Composable
private fun OnbMiniCard(t: OnbTokens, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Column(
        modifier
            .clip(RoundedCornerShape(16.dp))
            .background(t.cardBg)
            .border(1.dp, t.cardBd, RoundedCornerShape(16.dp))
            .padding(horizontal = 11.dp, vertical = 11.dp),
    ) { content() }
}

@Composable
private fun SmallLink(t: OnbTokens, text: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Row(
        modifier
            .height(40.dp)
            .clip(RoundedCornerShape(13.dp))
            .background(t.chipBg)
            .border(1.dp, t.cardBd, RoundedCornerShape(13.dp))
            .clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Text(text, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = t.tx2)
    }
}

/* ───────────────────────── 实时数据 ───────────────────────── */

@Composable
private fun rememberWelcomeFactsState(): WelcomeFacts {
    var facts by remember { mutableStateOf(WelcomeFacts.fromBuild()) }
    LaunchedEffect(Unit) {
        facts = withContext(Dispatchers.IO) { readWelcomeFacts() }
    }
    return facts
}
