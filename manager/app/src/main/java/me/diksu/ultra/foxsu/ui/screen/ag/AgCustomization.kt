package me.diksu.ultra.foxsu.ui.screen.ag

import me.diksu.ultra.foxsu.ui.navigation3.Route
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Construction
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FormatSize
import androidx.compose.material.icons.rounded.Gesture
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.SmartButton
import androidx.compose.material.icons.rounded.TextFields
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Wallpaper
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.diksu.ultra.R
import me.diksu.ultra.foxsu.data.repository.SettingsRepositoryImpl
import me.diksu.ultra.foxsu.data.settings.CustomizationStore
import me.diksu.ultra.foxsu.ui.component.ambient.AmbientPawsLayer
import me.diksu.ultra.foxsu.ui.component.ambient.BackgroundFxLayer
import me.diksu.ultra.foxsu.ui.component.ImageCropDialog
import me.diksu.ultra.foxsu.ui.component.bottombar.DockButton
import me.diksu.ultra.foxsu.ui.component.bottombar.DockButtonSwatch
import me.diksu.ultra.foxsu.ui.navigation3.LocalNavigator
import me.diksu.ultra.foxsu.ui.screen.settings.SettingsScreenActions
import me.diksu.ultra.foxsu.ui.util.LauncherIconManager
import me.diksu.ultra.foxsu.ui.util.importFontFile
import me.diksu.ultra.foxsu.ui.theme.AccentPalettes
import me.diksu.ultra.foxsu.ui.util.savePickedImage
import java.io.File

/**
 * Dock 样式定义：设计稿 9 种 + 由其他皮肤原生底栏复刻的 4 种。
 * 复刻项让「换肤」不再改变操作习惯——样式改为全局可选。
 */
private val dockStyles = listOf(
    // Dock Kit v2.0：设计稿定稿的 7 套（48dp 栅格 + 中央狐狸卡位）
    "bubble" to "珊瑚气泡", "film" to "胶片孔",
    "neon" to "霓虹管", "frost" to "玻璃叠层",
    "focus" to "单点聚焦", "sticker" to "云朵贴纸",
    "track" to "滑轨卡片",
    "d0" to "原有胶囊", "v1" to "展开药丸", "v5" to "侧边抽屉",
    "v6" to "流光光标", "v7" to "描边霓虹", "v8" to "方块卡片",
    "v9" to "渐变实底", "v10" to "顶指示条", "v11" to "浮起圆片",
    "center" to "中央头像",
    "editorial" to "纸面墨线", "pages" to "白色药丸",
    "glass" to "液态药丸", "nocturne" to "暗夜霓虹",
)

/**
 * 定制功能 · 晨曦玻璃（Aurora Glass）— 按最终设计稿 p-custom 全量重构。
 * 所有开关/滑杆接真实设置（SettingsViewModel actions + CustomizationStore）。
 */
@Composable
internal fun AgCustomization(
    onBack: () -> Unit,
    settingsActions: SettingsScreenActions? = null,
    onOpenBuiltin: (() -> Unit)? = null,
    onOpenDonate: (() -> Unit)? = null,
) {
    val custom = CustomizationStore.current
    val navigator = LocalNavigator.current
    val repo = remember { SettingsRepositoryImpl() }
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    var pendingWallpaperCrop by remember { mutableStateOf<String?>(null) }
    var wallpaperOn by remember { mutableStateOf(custom.wallpaperEnabled) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val saved = withContext(Dispatchers.IO) {
                savePickedImage(context, uri, "wallpaper_raw.jpg", maxSide = 2400)
            }
            if (saved == null) {
                Toast.makeText(context, R.string.fx_customization_wallpaper_failed, Toast.LENGTH_SHORT).show()
            } else {
                pendingWallpaperCrop = saved
            }
        }
    }

    val fontPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val saved = withContext(Dispatchers.IO) { importFontFile(context, uri) }
            if (saved == null) {
                Toast.makeText(context, R.string.fx_customization_font_failed, Toast.LENGTH_SHORT).show()
            } else {
                CustomizationStore.setFontPath(saved)
                CustomizationStore.setFontStyle(4)
                Toast.makeText(context, R.string.fx_customization_font_applied, Toast.LENGTH_SHORT).show()
            }
        }
    }

    // 恢复默认后自增：让页面里所有 remember 出来的临时开关一起重建
    var resetTick by remember { androidx.compose.runtime.mutableIntStateOf(0) }

    pendingWallpaperCrop?.let { raw ->
        ImageCropDialog(
            sourcePath = raw,
            aspectRatio = 0.5625f,
            outputName = "wallpaper.jpg",
            onConfirm = { cropped ->
                CustomizationStore.setWallpaper(cropped)
                CustomizationStore.setWallpaperEnabled(true)
                wallpaperOn = true
                pendingWallpaperCrop = null
            },
            onDismiss = { pendingWallpaperCrop = null },
        )
    }

    // ── 固定顶栏（透明 + 无分隔线）──
    Column(
        Modifier
            .fillMaxSize()
            .agPageBg()
            .statusBarsPadding(),
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .background(Ag.headSurface()).agHeadBlur()
                .padding(horizontal = 18.dp),
        ) {
            Spacer(Modifier.height(10.dp))
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AgIconBtn(AgTopIcons.Back, onClick = onBack, tint = Ag.Tx)
            }
            Spacer(Modifier.height(14.dp))
        }

        // ── 内容滚动区 ──
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp),
        ) {
            androidx.compose.runtime.key(resetTick) {
            // 大标题（设计稿 margin-top:22px）
            Text(
                buildString { append("定制"); append("功能") },
                fontSize = 32.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = (-0.6).sp,
                style = androidx.compose.ui.text.TextStyle(brush = Ag.TitleGrad),
                modifier = Modifier.padding(start = 4.dp, top = 22.dp, bottom = 16.dp),
            )

            // ── 狐狸 Logo 头卡（.foxhero：280dp 渐变卡，狐狸完整居中，fox-hero.png 透明底）──
            Box(
                Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp)
                    .height(280.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(
                        if (Ag.isDark) {
                            Brush.verticalGradient(listOf(Color(0xFF2B2444), Color(0xFF3D2C52), Color(0xFF4A2F55)))
                        } else {
                            Brush.verticalGradient(listOf(Color(0xFFEAE4FF), Color(0xFFF6E7F8), Color(0xFFFFE7EE)))
                        }
                    ),
                contentAlignment = Alignment.Center,
            ) {
                // 头卡里的氛围层：画在狐狸图下面，否则爪印会糊在脸上。
                // 卡片底色是紫粉渐变，粒子压到 0.8 才不会显脏。
                // 选的是背景特效时这里也换过去——头卡是「我这套氛围长什么样」的第一眼，
                // 只放猫爪会让选了极光的人以为自己没选上。
                if (custom.bgFxStyle == 0) {
                    AmbientPawsLayer(
                        enabled = custom.bgFxOn,
                        count = 7,
                        alphaScale = 0.8f,
                    )
                } else {
                    BackgroundFxLayer(
                        style = custom.bgFxStyle,
                        enabled = custom.bgFxOn,
                        alphaScale = 0.6f,
                    )
                }
                Image(
                    painter = painterResource(R.drawable.fx_fox_hero),
                    contentDescription = "FoxSu",
                    modifier = Modifier
                        .height(240.dp)
                        .shadow(12.dp, RoundedCornerShape(40.dp), spotColor = Color(0x66B48CFF), ambientColor = Color(0x66B48CFF)),
                )
            }

            // ══════════ 快捷入口 ══════════
            // 两件事都不是「定制项」，是同一条走道尽头的两扇门，合并成一张卡就够。
            if (onOpenBuiltin != null || onOpenDonate != null) {
                AgGHead("快捷入口")
                AgCard(contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 2.dp)) {
                    if (onOpenBuiltin != null) {
                        AgRow(
                            "内置模块",
                            "三颗随包模块 · 安装、启停、打开模块界面",
                            Ag.Vio, AgEngineIcons.Mount,
                            showChev = true,
                            divider = onOpenDonate != null,
                            onClick = onOpenBuiltin,
                        )
                    }
                    if (onOpenDonate != null) {
                        AgRow(
                            "捐赠与鸣谢",
                            "免费开源 · 支付通道接入中",
                            Ag.Mag, Icons.Rounded.Favorite,
                            showChev = true,
                            divider = false,
                            onClick = onOpenDonate,
                        )
                    }
                }
            }

            // ══════════ 主题 ══════════
            // 明暗、底色、强调色是同一件事的三个旋钮：都只改「界面长什么样」，
            // 而且互相有约束——底色决定取哪一版明暗，强调色不能被底色顺手改掉。
            // 放在一个标题下，用户才看得懂这三张卡是一条链上的。
            AgGHead("主题")

            // 原「主题设置」页（ColorPalette）只做两件事：明暗模式 + 强调色。
            // 强调色这里已经有了，模式补进来之后那个入口就能整体去掉——
            // 各皮肤不必再各留一个只能换深浅色的旧页面。
            AgCard {
                AgSubLabel("明暗模式")
                val modes = listOf(
                    0 to "跟随系统",
                    1 to "浅色",
                    2 to "深色",
                    6 to "深色 OLED",
                )
                // Monet 档（3/4/5）按明暗归一到对应 chip 上，避免四个都不亮
                val raw = repo.themeMode
                val normalized = when (raw) {
                    3 -> 0
                    4 -> 1
                    5 -> 2
                    else -> raw
                }
                var picked by remember(raw) { mutableStateOf(normalized) }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    modes.forEach { (mode, label) ->
                        val on = picked == mode
                        Box(
                            Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (on) Ag.Vio.copy(alpha = 0.10f) else Ag.Line.copy(alpha = 0.35f))
                                .border(
                                    1.dp,
                                    if (on) Ag.Vio.copy(alpha = 0.42f) else Ag.Line,
                                    RoundedCornerShape(12.dp),
                                )
                                .agClick {
                                    picked = mode
                                    settingsActions?.onSetThemeMode?.invoke(mode)
                                }
                                .padding(vertical = 10.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                label,
                                fontSize = 11.5.sp,
                                fontWeight = if (on) FontWeight.ExtraBold else FontWeight.Bold,
                                color = if (on) Ag.Vio else Ag.Tx2,
                                maxLines = 1,
                            )
                        }
                    }
                }
                Spacer(Modifier.height(4.dp))
            }

            // 20 套 Theme Kit 配色，每套自带亮 / 暗两版。选择器就摆在这张卡里，
            // 一张卡左亮右暗并排——两版差别常常是「暖棕 vs 冷灰」而不是反色，
            // 只画当前模式那一版，用户切到深色会以为换了一套主题。
            AgCard {
                AgSubLabel("背景色 · 20 套，每套自带亮色与暗色两版")
                AgBgColorPicker(
                    style = custom.bgColorTheme,
                    onPick = { index ->
                        if (index != CustomizationStore.current.bgColorTheme) {
                            CustomizationStore.setBgColorTheme(index)
                        }
                    },
                )
                Spacer(Modifier.height(9.dp))
                Text(
                    "用哪一版由上面的外观模式决定：选深色就取这套的暗色版，选浅色取亮色版，" +
                        "跟随系统时随系统走。只换画布、卡片、描边与正文，强调色仍归下面那七套——" +
                        "两条选择器各管一半，换底色不会顺手把按钮颜色也改掉。\n" +
                        "目前作用于默认皮肤（晨曦玻璃）与它的底栏；Fresh、玻璃、暗夜、杂志四套各有整套配色语言，暂不跟随。",
                    fontSize = 11.sp, lineHeight = 16.sp, color = Ag.Tx3,
                )
            }

            AgCard {
                AgSubLabel("强调色 · 取自 FoxSu logo 的七彩")
                var accentPicker by remember { mutableStateOf(false) }
                LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    repeat(AccentPalettes.size) { i ->
                        item {
                            AgAccentChip(
                                palette = AccentPalettes[i],
                                selected = custom.accentCustom == 0 && custom.accentPalette == i,
                            ) { accentPicker = false; CustomizationStore.setAccentPalette(i) }
                        }
                    }
                    item {
                        // 自定义：只挑一个主色，另外两个渐变端由它推导
                        AgAccentCustomChip(
                            color = custom.accentCustom,
                            selected = custom.accentCustom != 0,
                            onClick = { accentPicker = !accentPicker },
                        )
                    }
                }
                if (accentPicker) {
                    AgTitlePicker(
                        initial = custom.accentCustom.takeIf { it != 0 } ?: Ag.Vio.toArgb(),
                        onPick = { CustomizationStore.setAccentCustom(it) },
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "当前：${hexOf(custom.accentCustom)}　拖动即时生效，点任意预设色可退出自定义",
                        fontSize = 11.sp, color = Ag.Tx2,
                    )
                }
                Spacer(Modifier.height(12.dp))
                Text(
                    "选一套主色，按钮、选中态、底栏指示器与渐变会同时换色，五套界面风格一并生效。",
                    fontSize = 11.5.sp, lineHeight = 17.sp, color = Ag.Tx2,
                )
                Spacer(Modifier.height(12.dp))
            }

            // ══════════ 文字与图标 ══════════
            // 字体、标题、桌面图标：都只换「长相」不换行为，且互相独立，
            // 放在一起是因为用户调完字体往往接着想改图标，来回翻页没必要。
            AgGHead("文字与图标")

            AgCard {
                AgSubLabel("字体 · 系统默认 / 四套内置 / 可导入")
                LazyRow(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                    item {
                        // 系统默认：fontFamily = null 即回到平台字体（选了别的字体后靠它退回）
                        AgFontChip("系统默认", "字A", null, custom.fontStyle == 0) {
                            CustomizationStore.setFontStyle(0)
                        }
                    }
                    item {
                        AgFontChip("思源宋体", "字A", FontFamily(Font(R.font.fx_noto_serif_sc)), custom.fontStyle == 1) {
                            CustomizationStore.setFontStyle(1)
                        }
                    }
                    item {
                        AgFontChip("等宽字体", "字A", FontFamily(Font(R.font.fx_jetbrains_mono)), custom.fontStyle == 2) {
                            CustomizationStore.setFontStyle(2)
                        }
                    }
                    item {
                        AgFontChip("霞鹜文楷", "字A", FontFamily(Font(R.font.fx_lxgw_wenkai)), custom.fontStyle == 3) {
                            CustomizationStore.setFontStyle(3)
                        }
                    }
                    if (custom.fontPath != null) {
                        item {
                            AgFontChip(
                                "自定义", File(custom.fontPath).name.take(2),
                                runCatching { FontFamily(Font(File(custom.fontPath))) }.getOrNull(),
                                custom.fontStyle == 4,
                            ) { CustomizationStore.setFontStyle(4) }
                        }
                    }
                }
                Spacer(Modifier.height(14.dp))
                AgButton(
                    primary = true,
                    text = "导入字体文件",
                    onClick = {
                        fontPicker.launch(
                            arrayOf("font/ttf", "font/otf", "application/x-font-ttf", "application/octet-stream")
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(6.dp))
            }

            AgCard {
                AgSubLabel("标题文字")
                AgTitleField(
                    value = custom.homeTitle,
                    onValueChange = CustomizationStore::setHomeTitle,
                    hint = "留空则显示 FoxSu",
                )
                if (custom.homeTitle.isNotBlank()) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "恢复默认",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = Ag.Vio,
                        modifier = Modifier
                            .agClick { CustomizationStore.setHomeTitle("") }
                            .padding(vertical = 6.dp, horizontal = 2.dp),
                    )
                }

                Spacer(Modifier.height(16.dp))
                AgSubLabel("标题颜色")
                AgTitleColorRow(
                    selected = custom.titleColor,
                    onPick = CustomizationStore::setTitleColor,
                )

                Spacer(Modifier.height(2.dp))
                Text(
                    "主页、超级用户、模块三处标题共用这个颜色；不选时跟随上面的全局强调色，换配色会一起变。",
                    fontSize = 11.sp, lineHeight = 17.sp, color = Ag.Tx2,
                )

                // 自定义取色：色相 / 饱和度 / 明度三条滑轨，拖动即实时生效
                var showTitlePicker by remember { mutableStateOf(false) }
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        if (showTitlePicker) "收起取色器" else "自定义颜色…",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = Ag.Vio,
                        modifier = Modifier
                            .agClick { showTitlePicker = !showTitlePicker }
                            .padding(vertical = 6.dp, horizontal = 2.dp),
                    )
                }
                if (showTitlePicker) {
                    AgTitlePicker(
                        initial = custom.titleColor,
                        onPick = CustomizationStore::setTitleColor,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "当前：${hexOf(custom.titleColor)}　·　五套界面风格同时生效",
                        fontSize = 11.sp,
                        color = Ag.Tx2,
                    )
                }
                Spacer(Modifier.height(10.dp))
            }

            AgCard {
                AgSubLabel("启动器图标")
                LazyRow(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                    LauncherIconManager.styles.forEach { style ->
                        item {
                            AgLauncherIconCard(
                                style = style,
                                selected = custom.launcherIcon == style.id,
                            ) {
                                CustomizationStore.setLauncherIcon(style.id)
                                val ok = LauncherIconManager.apply(style.id)
                                Toast.makeText(
                                    context,
                                    if (ok) "已切换到「${style.label}」，桌面若没变请回桌面再返回" else "桌面图标切换失败，请稍后重试",
                                    Toast.LENGTH_SHORT,
                                ).show()
                            }
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    "切换后桌面可能还显示旧图标，回桌面再进来即可刷新。",
                    fontSize = 11.sp, lineHeight = 16.sp, color = Ag.Tx2,
                )
                Spacer(Modifier.height(4.dp))
            }

            // ══════════ 主页 ══════════
            // 壁纸铺底、特效浮在上面：一张卡管「底」，一张卡管「动」，
            // 两者可以同时开，所以分成两张卡而不是硬塞成一张。
            AgGHead("主页")

            AgCard(contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 4.dp)) {
                AgSubLabel("壁纸")
                AgRow(
                    stringResource(R.string.fx_customization_wallpaper_enable),
                    stringResource(R.string.fx_customization_wallpaper_enable_sum),
                    Ag.Teal, AgSettingsIcons.Wallpaper,
                    trailing = {
                        AgToggle(checked = wallpaperOn, onCheckedChange = {
                            wallpaperOn = !wallpaperOn
                            CustomizationStore.setWallpaperEnabled(wallpaperOn)
                        }, small = true)
                    },
                )
                Row(modifier = Modifier.padding(bottom = 12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    AgPillAction(
                        text = stringResource(R.string.fx_customization_pick_image),
                        icon = AgSettingsIcons.Wallpaper,
                        primary = true,
                        modifier = Modifier.weight(1f),
                    ) {
                        picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    }
                    if (custom.wallpaperUri != null) {
                        AgPillAction(
                            text = stringResource(R.string.fx_customization_clear),
                            icon = Icons.Rounded.Refresh,
                            primary = false,
                            modifier = Modifier.weight(1f),
                        ) { CustomizationStore.setWallpaper(null) }
                    }
                }
            }

            AgCard {
                // 背景特效：一个总开关 + 就地左右滑动的 21 套选择器。
                // 猫爪也进了这个选择器（第 1 项），因为它和 20 套背景特效抢的是同一个槽位——
                // 同一时刻主页只需要一种氛围，分成两个开关只会让人以为「我关了一个怎么还有」。
                // 选择器不再进二级页：21 张缩略图排两列时，用户得先记住「第 7 套长什么样」
                // 再退回主页比对；滑动的每一页就是整屏真实比例，滑一下就换一套。
                AgToggleRow(
                    "背景特效",
                    if (custom.bgFxOn) {
                        "整屏铺满：主页、捐赠页与上面这张头卡同时显示选中的那一套，下面左右滑动即可更换"
                    } else {
                        "已关闭。选择仍会记住，重新打开立刻恢复；关闭后主页不再逐帧重绘这一层，最省电"
                    },
                    Ag.Mag, AgSettingsIcons.Paw,
                    trailing = {
                        AgToggle(checked = custom.bgFxOn, onCheckedChange = {
                            CustomizationStore.setBgFxOn(!custom.bgFxOn)
                        }, small = true)
                    },
                    divider = true,
                )
                Spacer(Modifier.height(14.dp))
                AgSubLabel("特效展示 · 左右滑动切换")
                AgBgFxPicker(
                    style = custom.bgFxStyle,
                    onPick = { index ->
                        // Pager 落定即写：滑到哪套就是哪套，不必再点一次确认。
                        // 总开关关着时只改选择、不强行打开——用户可能只是在挑，还没决定用。
                        if (index != CustomizationStore.current.bgFxStyle) {
                            CustomizationStore.setBgFxStyle(index)
                        }
                    },
                )
                if (!custom.bgFxOn) {
                    Spacer(Modifier.height(9.dp))
                    Text(
                        "背景特效已关闭，上面只是预览。打开开关后这一套才会铺到主页上。",
                        fontSize = 11.sp, lineHeight = 16.sp, color = Ag.Tx3,
                    )
                }
                Spacer(Modifier.height(6.dp))

            }

            // ══════════ 动效 ══════════
            // 三档统一控制「粒子多少 + 3D 倾斜角度」，不再为 3D 单开一个开关——
            // 同一个值挂两个控件，关掉一个另一个还亮着，最容易让人以为设置没生效。
            AgGHead("动效")

            AgCard {
                AgSubLabel("动效强度")
                // 三档：关 / 克制 / 全开。统一管粒子边、3D 触摸倾斜、底栏点击涟漪
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Ag.Vio.copy(alpha = 0.06f))
                        .border(1.dp, Ag.Line, RoundedCornerShape(12.dp))
                        .padding(3.dp),
                    horizontalArrangement = Arrangement.spacedBy(3.dp),
                ) {
                    listOf("关闭", "克制", "全开").forEachIndexed { i, label ->
                        val on = custom.fxLevel == i
                        Box(
                            Modifier
                                .weight(1f)
                                .height(34.dp)
                                .clip(RoundedCornerShape(9.dp))
                                .then(
                                    if (on) Modifier.background(Ag.Grad)
                                    else Modifier
                                )
                                .agClick(onClick = {
                                    CustomizationStore.setFxLevel(i)
                                }),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                label,
                                fontSize = 12.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (on) Color.White else Ag.Tx2,
                            )
                        }
                    }
                }
                Spacer(Modifier.height(9.dp))
                Text(
                    when (custom.fxLevel) {
                        0 -> "全部动效关闭：卡片不再流动粒子、触摸也不再 3D 倾斜。最省电，老机型建议选它。主页背景与底栏特效各有独立开关，不受这里影响。"
                        1 -> "克制档：粒子颗粒减半、流速放慢，3D 倾斜角度限制在 ±6°，日常够看又不打扰。"
                        else -> "全开：卡片外轮廓彩色粒子边 + 触摸 3D 透视倾斜，全部启用。3D 倾斜也归这一档管，不另设开关。"
                    },
                    fontSize = 11.5.sp, lineHeight = 17.sp, color = Ag.Tx2,
                )
                Spacer(Modifier.height(12.dp))

            }

            // ══════════ 底部导航栏 ══════════
            AgGHead("底部导航栏")
            AgCard {
                // ── 形态：悬浮胶囊还是贴底通栏 ──
                AgSubLabel("形态")
                AgToggleRow(
                    "传统底栏",
                    "关：悬浮胶囊（默认）　开：贴底通栏传统底栏",
                    Ag.Vio, AgSettingsIcons.FloatingBar,
                    trailing = {
                        AgToggle(checked = custom.classicDock, onCheckedChange = {
                            CustomizationStore.setClassicDock(!custom.classicDock)
                        }, small = true)
                    },
                )
                // 结构只在通栏形态下有意义，胶囊形态下藏起来，
                // 免得出现「选了结构却看不到任何变化」。
                if (custom.classicDock) {
                    Spacer(Modifier.height(10.dp))
                    val classicStyles = me.diksu.ultra.foxsu.ui.component.bottombar.ClassicDockStyle.entries
                    AgSubLabel("结构 · ${classicStyles.size} 套")
                    val classicScreenW = androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp.dp
                    val classicCardW = ((classicScreenW - 36.dp - 16.dp - 18.dp) / 4).coerceAtLeast(62.dp)
                    val classicListState = androidx.compose.foundation.lazy.rememberLazyListState()
                    androidx.compose.runtime.LaunchedEffect(custom.classicDockStyle) {
                        val target = (custom.classicDockStyle - 1).coerceIn(0, (classicStyles.size - 4).coerceAtLeast(0))
                        classicListState.animateScrollToItem(target)
                    }
                    androidx.compose.foundation.lazy.LazyRow(
                        state = classicListState,
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 2.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        items(classicStyles.size) { i ->
                            val s = classicStyles[i]
                            AgClassicStyleCard(
                                style = s,
                                selected = custom.classicDockStyle == s.id,
                                modifier = Modifier.width(classicCardW),
                            ) { CustomizationStore.setClassicDockStyle(s.id) }
                        }
                    }
                    Spacer(Modifier.height(7.dp))
                    Text(
                        me.diksu.ultra.foxsu.ui.component.bottombar.ClassicDockStyle
                            .fromId(custom.classicDockStyle).hint,
                        fontSize = 11.sp, lineHeight = 16.sp, color = Ag.Tx2,
                        modifier = Modifier.padding(start = 2.dp, bottom = 14.dp),
                    )
                }
                AgSubLabel("胶囊样式 · ${dockStyles.size} 套")
                // 横向滑动选择器：一屏 4 张小卡，其余的手指滑出来
                val screenW = androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp.dp
                val chipW = ((screenW - 36.dp - 16.dp - 18.dp) / 4).coerceAtLeast(64.dp)
                androidx.compose.foundation.lazy.LazyRow(
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 2.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    items(dockStyles.size) { i ->
                        val (code, name) = dockStyles[i]
                        AgDockStyleChip(
                            name = name,
                            code = code,
                            selected = custom.dockStyle == code,
                            modifier = Modifier.width(chipW),
                        ) { CustomizationStore.setDockStyle(code) }
                    }
                }
                Spacer(Modifier.height(6.dp))
                // 侧边抽屉的停靠方向：从原来的通用开关堆里单独拎出来，
                // 跟着胶囊选择器放——它只对 v5「侧边抽屉」这一套生效，逻辑上是选择器的一部分
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(13.dp))
                        .background(Ag.Vio.copy(alpha = 0.05f))
                        .padding(horizontal = 12.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("侧边胶囊停靠", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Ag.Tx)
                        Text(
                            "「侧边抽屉」样式下，胶囊贴屏幕左侧还是右侧",
                            fontSize = 10.5.sp, color = Ag.Tx3,
                            modifier = Modifier.padding(top = 1.dp),
                        )
                    }
                    Spacer(Modifier.width(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("左", "右").forEachIndexed { i, label ->
                            val on = custom.dockSide == i
                            Box(
                                Modifier
                                    .height(28.dp)
                                    .clip(RoundedCornerShape(99.dp))
                                    .background(if (on) Ag.Grad else androidx.compose.ui.graphics.SolidColor(Color.Transparent))
                                    .border(
                                        1.dp,
                                        if (on) Color.Transparent else Ag.Line,
                                        RoundedCornerShape(99.dp),
                                    )
                                    .agClick { CustomizationStore.setDockSide(i) }
                                    .padding(horizontal = 12.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    label,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (on) Color.White else Ag.Tx2,
                                )
                            }
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    "当前：${dockStyles.firstOrNull { it.first == custom.dockStyle }?.second ?: "原有胶囊"}" +
                        " · 左右滑动查看全部",
                    fontSize = 11.sp,
                    color = Ag.Tx3,
                    modifier = Modifier.padding(start = 2.dp, bottom = 12.dp),
                )
                val kitCenter = custom.dockStyle == "center" ||
                    me.diksu.ultra.foxsu.ui.component.bottombar.DockKitStyle.of(custom.dockStyle)?.hasCenter == true
                if (kitCenter) {
                    Text(
                        "中间那颗狐狸是捐赠入口，点一下打开，长按换图。",
                        fontSize = 11.sp,
                        lineHeight = 16.sp,
                        color = Ag.Tx3,
                        modifier = Modifier.padding(start = 2.dp, bottom = 12.dp),
                    )
                }
                Spacer(Modifier.height(16.dp))
                AgSubLabel("按钮形状 · ${DockButton.all.size} 套")
                // 横向滑动选择器：和下方胶囊样式选择器同一套交互——一屏 4 张、
                // 其余手指滑出来，选中项自动滚到可见位置，不再铺成两行 5 列
                val shapeScreenW = androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp.dp
                val shapeCardW = ((shapeScreenW - 36.dp - 16.dp - 18.dp) / 4).coerceAtLeast(62.dp)
                val shapeListState = androidx.compose.foundation.lazy.rememberLazyListState()
                androidx.compose.runtime.LaunchedEffect(custom.dockButton) {
                    val target = (custom.dockButton - 1).coerceIn(0, (DockButton.all.size - 4).coerceAtLeast(0))
                    shapeListState.animateScrollToItem(target)
                }
                androidx.compose.foundation.lazy.LazyRow(
                    state = shapeListState,
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 2.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    items(DockButton.all.size) { i ->
                        val btn = DockButton.all[i]
                        AgButtonShapeCard(
                            button = btn,
                            selected = custom.dockButton == btn.ordinal,
                            modifier = Modifier.width(shapeCardW),
                        ) { CustomizationStore.setDockButton(btn.ordinal) }
                    }
                }
                Spacer(Modifier.height(7.dp))
                Text(
                    DockButton.of(custom.dockButton).hint + "　·　左右滑动查看全部",
                    fontSize = 11.sp, color = Ag.Tx2,
                    modifier = Modifier.padding(start = 2.dp, bottom = 14.dp),
                )
                AgSubLabel("切换动效")
                // 水滴指示器（默认关）
                AgToggleRow(
                    "水滴流动指示器",
                    if (custom.dockWaterDrop) {
                        "切 Tab 时色块前缘先行、后缘慢半拍，拉长压扁后带回弹停稳（约 400ms）"
                    } else {
                        "开启后选中色块像水滴流到新的 Tab，带拉长、压扁与回弹（约 400ms）"
                    },
                    Ag.Mag, AgSettingsIcons.WaterDrop,
                    trailing = {
                        AgToggle(checked = custom.dockWaterDrop, onCheckedChange = {
                            CustomizationStore.setDockWaterDrop(!custom.dockWaterDrop)
                        }, small = true)
                    },
                )
                // 页面轻推（独立开关，默认开）
                AgToggleRow(
                    "页面轻推动效",
                    if (custom.dockPagePush) {
                        "切 Tab 时新页面沿切换方向推入并淡入，与色块到位同一拍"
                    } else {
                        "开启后切 Tab 时页面从切换方向滑进来，给切换一点空间感"
                    },
                    Ag.Vio, AgSettingsIcons.PagerGesture,
                    trailing = {
                        AgToggle(checked = custom.dockPagePush, onCheckedChange = {
                            CustomizationStore.setDockPagePush(!custom.dockPagePush)
                        }, small = true)
                    },
                )
                // 读秒自动收起
                AgToggleRow(
                    "读秒自动收起",
                    if (custom.dockAutoHide > 0) {
                        "${custom.dockAutoHide} 秒无操作后胶囊沉到屏幕下方，点一下屏幕滑回来"
                    } else {
                        "开启后倒计时结束，胶囊自动收到屏幕下方；点任意位置带弹跳滑回"
                    },
                    Ag.Teal, AgSettingsIcons.Blur,
                    trailing = {
                        // 注意：AgToggle 不回传新值，这里必须自己取反（之前写成 >0 → 8 / 0，
                        // 关闭态点它永远算出 0，于是「读秒自动收起」根本打不开）
                        AgToggle(checked = custom.dockAutoHide > 0, onCheckedChange = {
                            CustomizationStore.setDockAutoHide(if (custom.dockAutoHide > 0) 0 else 8)
                        }, small = true)
                    },
                )
                if (custom.dockAutoHide > 0) {
                    AgSliderRow(
                        label = "收起秒数",
                        sub = "3–30 秒；任何触摸或切换页面都会把读秒重置",
                        value = custom.dockAutoHide.toFloat(),
                        range = 3f..30f,
                        display = "${custom.dockAutoHide} 秒",
                        steps = 26,
                        onChange = { CustomizationStore.setDockAutoHide(it.toInt()) },
                    )
                }
                AgSubLabel("栏上特效 · 栏内 / 外圈可叠加")
                // 40 套选择器：栏内与外圈各自单选、可叠加，所以单独开一页
                AgRow(
                    "底栏特效 · 40 套",
                    if (custom.dockFxInner == 0 && custom.dockFxRing == 0) {
                        "当前未启用。栏内 20 套管图标，外圈 20 套管边框，每套都有实时预览"
                    } else {
                        "栏内第 ${custom.dockFxInner} 套 · 外圈第 ${custom.dockFxRing} 套，点进去可以换"
                    },
                    Ag.Mag, AgSettingsIcons.Paw,
                    showChev = true,
                    divider = true,
                    onClick = { navigator.push(Route.DockFx) },
                )
                AgRow(
                    "底栏图标 · 20 套",
                    if (custom.dockIconPack == 0) {
                        "当前为原生图标。20 套造型语言：建筑剖面 / 折纸 / 像素 / 印章 / 星轨…"
                    } else {
                        "第 ${custom.dockIconPack} 套 · ${me.diksu.ultra.foxsu.ui.component.bottombar.DockIconPacks.packs[custom.dockIconPack - 1].name}，点进去可以换"
                    },
                    Ag.Mag, AgSettingsIcons.Style,
                    showChev = true,
                    divider = false,
                    onClick = { navigator.push(Route.DockIcons) },
                )
                Spacer(Modifier.height(2.dp))
                AgSubLabel("材质与尺寸")
                AgToggleRow(
                    "沉浸浅感", "通透半透明底：更透彻并带玻璃质感高光",
                    Ag.Teal, AgSettingsIcons.FloatingBar,
                    trailing = {
                        AgToggle(checked = custom.bottomBarImmersive, onCheckedChange = {
                            CustomizationStore.setBottomBarImmersive(!custom.bottomBarImmersive)
                        }, small = true)
                    },
                )
                AgToggleRow(
                    "底栏液态玻璃", "真实采样身后内容：模糊 + 提亮 + 边缘折射（GPU 需要支持）",
                    Ag.Vio, AgSettingsIcons.Blur,
                    trailing = {
                        AgToggle(checked = custom.bottomBarLiquidGlass, onCheckedChange = {
                            CustomizationStore.setBottomBarLiquidGlass(!custom.bottomBarLiquidGlass)
                        }, small = true)
                    },
                )
                AgSliderRow(
                    label = "底栏不透明度",
                    value = custom.bottomBarAlpha,
                    range = 0.3f..1f,
                    display = "${(custom.bottomBarAlpha * 100).toInt()}%",
                    onChange = CustomizationStore::setBottomBarAlpha,
                )
                AgSliderRow(
                    label = "圆角",
                    value = custom.bottomBarRadius,
                    range = 12f..40f,
                    display = "${custom.bottomBarRadius.toInt()}px",
                    onChange = CustomizationStore::setBottomBarRadius,
                )
                AgSubLabel("其它")
                AgToggleRow(
                    "Editorial 使用胶囊底栏",
                    "关闭时 Editorial 主题保留原生纸面底栏（推荐：胶囊会打乱杂志版式）",
                    Ag.Amber, AgSettingsIcons.FloatingBar,
                    trailing = {
                        AgToggle(checked = custom.dockOnEditorial, onCheckedChange = {
                            CustomizationStore.setDockOnEditorial(!custom.dockOnEditorial)
                        }, small = true)
                    },
                )
                AgToggleRow(
                    "七彩底栏图标", "给底栏每个图标一套随机彩色",
                    Ag.Mag, Icons.Rounded.Palette,
                    trailing = {
                        AgToggle(checked = custom.navRainbow, onCheckedChange = {
                            CustomizationStore.setNavRainbow(!custom.navRainbow)
                        }, small = true)
                    },
                )
            }

            // ══════════ 显示与交互 ══════════
            // 三张卡都属于「不只是好看、会改变手感」的设置：显示比例、手势、列表密度。
            AgGHead("显示与交互")

            AgCard(contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 4.dp)) {
                AgSubLabel("显示")
                AgToggleRow(
                    "模糊", "底栏实时采样身后内容做高斯模糊；其它皮肤的顶栏/底栏模糊也由它控制",
                    Ag.Vio, AgSettingsIcons.Blur,
                    trailing = {
                        var on by remember { mutableStateOf(repo.enableBlur) }
                        AgToggle(checked = on, onCheckedChange = {
                            on = !on; settingsActions?.onSetEnableBlur?.invoke(on)
                        }, small = true)
                    },
                )
                AgToggleRow(
                    "导航栏角标", "在导航栏显示已授权应用和已启用模块数量",
                    Ag.Amber, AgSettingsIcons.Badge,
                    trailing = {
                        var on by remember { mutableStateOf(repo.enableNavigationBadge) }
                        AgToggle(checked = on, onCheckedChange = {
                            on = !on; settingsActions?.onSetEnableNavigationBadge?.invoke(on)
                        }, small = true)
                    },
                )
                AgSliderRow(
                    label = "界面缩放",
                    sub = "调整全局显示比例",
                    value = repo.pageScale,
                    range = 0.7f..1.3f,
                    display = "${(repo.pageScale * 100).toInt()}%",
                    onChange = { settingsActions?.onSetPageScale?.invoke(it) },
                )
            }

            AgCard(contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 4.dp)) {
                AgSubLabel("手势")
                AgToggleRow(
                    "预测性返回手势", "启用对预测性返回手势的支持",
                    Ag.Vio, AgSettingsIcons.PredictiveBack,
                    trailing = {
                        var on by remember { mutableStateOf(repo.enablePredictiveBack) }
                        AgToggle(checked = on, onCheckedChange = {
                            on = !on; settingsActions?.onSetEnablePredictiveBack?.invoke(on)
                        }, small = true)
                    },
                )
                AgToggleRow(
                    "横移返回手势", "从屏幕边缘横移返回上一页",
                    Ag.Mag, AgSettingsIcons.SwipeBack,
                    trailing = {
                        var on by remember { mutableStateOf(repo.enableSwipeDismiss) }
                        AgToggle(checked = on, onCheckedChange = {
                            on = !on; settingsActions?.onSetEnableSwipeDismiss?.invoke(on)
                        }, small = true)
                    },
                )
                // 左右滑动切换 Tab 的总开关。
                // 前三档是 miuix 的三套拦截策略，没有一档能真正关掉横滑；
                // 所以第 4 档「关」是自己加的：页面与底栏同时不再响应横滑。
                // 顺序固定为 0/1/2/3，不能重排——老用户存的是 0/1/2 的旧语义。
                AgToggleRow(
                    "滑动切换 Tab",
                    when (repo.pagerInterceptionMode) {
                        0 -> "原生 · 横滑交给系统判断，最轻，但长列表里偶尔要先停一下"
                        1 -> "跨轴拦截 · 接管列表上的横滑，长列表也跟手（推荐）"
                        2 -> "点击暂停 · 惯性滚动时点一下屏幕立刻停住，翻页走系统默认"
                        else -> "关 · 页面和底栏都不响应横滑，只能点图标切换"
                    },
                    Ag.Teal, AgSettingsIcons.PagerGesture,
                    trailing = {
                        var mode by remember { mutableStateOf(repo.pagerInterceptionMode) }
                        AgPick(
                            text = when (mode) {
                                0 -> "原生"; 1 -> "跨轴拦截"; 2 -> "点击暂停"; else -> "关"
                            } + " ⇅",
                            onClick = {
                                // 0 → 1（跨轴，默认）→ 2（点击暂停）→ 3（关）→ 0
                                val next = (mode + 1) % 4
                                mode = next
                                settingsActions?.onSetPagerInterceptionMode?.invoke(next)
                            },
                        )
                    },
                )
            }

            AgCard(contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 4.dp)) {
                AgSubLabel("模块列表")
                AgSliderRow(
                    label = "模块描述最大行数",
                    sub = "超过此行数的模块介绍将自动折叠，点击卡片可展开全文",
                    value = repo.moduleDescriptionMaxLines.toFloat(),
                    range = 1f..8f,
                    display = "${repo.moduleDescriptionMaxLines} 行",
                    steps = 6,
                    onChange = { settingsActions?.onSetModuleDescriptionMaxLines?.invoke(it.toInt()) },
                )
            }

            // ══════════ 底栏与手势失灵修复 ══════════
            // 底栏沉底（读秒收起）之后如果没被点回来，或者手势被切成「关」，
            // 用户会以为「整个导航坏了」——给一个不依赖底栏的恢复口。
            Spacer(Modifier.height(14.dp))
            AgCard {
                AgRow(
                    "底栏 / 手势失灵？",
                    "底栏一直不回来、或者滑不动页面时点这里：取消沉底、恢复滑动切换",
                    Ag.Amber, AgSettingsIcons.PagerGesture,
                    trailing = {
                        Box(
                            Modifier
                                .height(30.dp)
                                .clip(RoundedCornerShape(99.dp))
                                .background(Ag.Amber.copy(alpha = 0.16f))
                                .border(1.dp, Ag.Amber.copy(alpha = 0.45f), RoundedCornerShape(99.dp))
                                .agClick {
                                    // 两件事一起复位：底栏沉底 + 横滑被关
                                    CustomizationStore.setDockAutoHide(0)
                                    settingsActions?.onSetPagerInterceptionMode?.invoke(1)
                                    Toast.makeText(
                                        context,
                                        "已取消自动收起并恢复滑动切换，回主页即可看到底栏",
                                        Toast.LENGTH_SHORT,
                                    ).show()
                                }
                                .padding(horizontal = 12.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text("修复", fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, color = Ag.Amber)
                        }
                    },
                )
            }

            // ══════════ 恢复默认 ══════════
            Spacer(Modifier.height(18.dp))
            var confirmReset by remember { mutableStateOf(false) }
            Box(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(Ag.Danger.copy(alpha = if (Ag.isDark) 0.10f else 0.05f))
                    .border(1.dp, Ag.Danger.copy(alpha = 0.26f), RoundedCornerShape(18.dp))
                    .padding(horizontal = 14.dp, vertical = 13.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(Ag.Danger.copy(alpha = 0.12f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Rounded.Refresh, null, tint = Ag.Danger, modifier = Modifier.size(18.dp))
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            "恢复默认设置",
                            fontSize = 14.5.sp, fontWeight = FontWeight.ExtraBold, color = Ag.Tx,
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            "壁纸、标题、配色、字体、底栏与图标全部回到初始状态",
                            fontSize = 11.sp, lineHeight = 15.sp, color = Ag.Tx2,
                        )
                    }
                    Spacer(Modifier.width(10.dp))
                    Box(
                        Modifier
                            .height(34.dp)
                            .clip(RoundedCornerShape(99.dp))
                            .border(1.dp, Ag.Danger.copy(alpha = 0.45f), RoundedCornerShape(99.dp))
                            .agClick { confirmReset = true }
                            .padding(horizontal = 16.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("重置", fontSize = 12.5.sp, fontWeight = FontWeight.ExtraBold, color = Ag.Danger)
                    }
                }
            }
            if (confirmReset) {
                androidx.compose.material3.AlertDialog(
                    onDismissRequest = { confirmReset = false },
                    title = { Text("恢复默认设置？", fontWeight = FontWeight.ExtraBold) },
                    text = { Text("所有个性化设置会被清空，且无法撤销。桌面图标会回到第一套。") },
                    confirmButton = {
                        androidx.compose.material3.TextButton(onClick = {
                            confirmReset = false
                            // 1) 定制项：整份 prefs 清空（reset 内部 clear）
                            CustomizationStore.reset()
                            // 2) 同一页上属于「设置」的项也一并回默认，否则重置完开关还亮着
                            settingsActions?.onSetEnableBlur?.invoke(false)
                            settingsActions?.onSetEnableNavigationBadge?.invoke(true)
                            settingsActions?.onSetPageScale?.invoke(1f)
                            settingsActions?.onSetEnablePredictiveBack?.invoke(false)
                            settingsActions?.onSetEnableSwipeDismiss?.invoke(true)
                            settingsActions?.onSetPagerInterceptionMode?.invoke(1)
                            settingsActions?.onSetModuleDescriptionMaxLines?.invoke(4)
                            // 3) 桌面图标 + 自定义图
                            LauncherIconManager.apply(LauncherIconManager.STYLE_FOXSU)
                            resetTick++
                            Toast.makeText(context, "已恢复默认设置", Toast.LENGTH_SHORT).show()
                        }) { Text("恢复默认", color = Ag.Danger, fontWeight = FontWeight.Bold) }
                    },
                    dismissButton = {
                        androidx.compose.material3.TextButton(onClick = { confirmReset = false }) { Text("取消") }
                    },
                )
            }

            Spacer(Modifier.height(140.dp))
            }
        }
    }
}

/** 标题文字输入框：圆角 14、内衬底、与页面同色系的描边 */
@Composable
private fun AgTitleField(value: String, onValueChange: (String) -> Unit, hint: String) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(46.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(Ag.searchBg())
            .border(1.dp, Ag.Line, RoundedCornerShape(14.dp))
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        androidx.compose.foundation.text.BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            textStyle = androidx.compose.ui.text.TextStyle(
                fontSize = 14.sp,
                color = Ag.Tx,
                fontWeight = FontWeight.Bold,
            ),
            cursorBrush = androidx.compose.ui.graphics.SolidColor(Ag.Vio),
            modifier = Modifier.weight(1f),
            decorationBox = { inner ->
                Box {
                    if (value.isEmpty()) Text(hint, fontSize = 13.sp, color = Ag.Tx3)
                    inner()
                }
            },
        )
        if (value.isNotEmpty()) {
            Text(
                "✕",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = Ag.Tx3,
                modifier = Modifier.agClick { onValueChange("") }.padding(start = 8.dp, top = 4.dp, bottom = 4.dp),
            )
        }
    }
}

/** 标题颜色选择：跟随主题 + 7 套品牌色（与「全局强调色」同一份色卡） */
@Composable
private fun AgTitleColorRow(selected: Int, onPick: (Int) -> Unit) {
    val chips = buildList {
        add(Triple("跟随主题", 0, null as Color?))
        AccentPalettes.forEach { p ->
            add(Triple(p.label, p.primary.toArgb(), p.primary as Color?))
        }
    }
    LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        chips.forEach { (label, argb, solid) ->
            item {
            val on = selected == argb
            Column(
                Modifier.agClick { onPick(argb) }.width(58.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Box(
                    Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(solid?.let { androidx.compose.ui.graphics.SolidColor(it) } ?: Ag.Grad)
                        .border(if (on) 3.dp else 1.dp, if (on) Color.White else Ag.Line, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    if (on) {
                        Icon(Icons.Rounded.Check, null, tint = Color.White, modifier = Modifier.size(16.dp))
                    }
                }
                Text(
                    label,
                    fontSize = 10.sp,
                    fontWeight = if (on) FontWeight.ExtraBold else FontWeight.Bold,
                    color = if (on) Ag.Vio else Ag.Tx2,
                    maxLines = 1,
                )
            }
            }
        }
    }
}

/** 自定义取色器：色相 / 饱和度 / 明度三轨，任何时候拖动都即时回调 */
@Composable
private fun AgTitlePicker(initial: Int, onPick: (Int) -> Unit) {
    val start = if (initial == 0) me.diksu.ultra.foxsu.ui.theme.currentAccent.primary.toArgb() else initial
    // 只在「打开取色器」时取一次初值：若跟着每次回调重建，拖动时滑块会被重置成跳动
    val hsv0 = remember { FloatArray(3).also { android.graphics.Color.colorToHSV(start, it) } }
    var h by remember { mutableStateOf(hsv0[0]) }
    var sat by remember { mutableStateOf(hsv0[1]) }
    var val0 by remember { mutableStateOf(hsv0[2]) }

    fun colorOf(hh: Float, ss: Float, vv: Float): Color =
        Color(android.graphics.Color.HSVToColor(floatArrayOf(hh, ss, vv)))

    fun emit() = onPick(android.graphics.Color.HSVToColor(floatArrayOf(h, sat, val0)))

    Column(Modifier.padding(top = 8.dp)) {
        AgTuneBar(
            brush = Brush.horizontalGradient(
                listOf(
                    colorOf(0f, 1f, 1f), colorOf(60f, 1f, 1f), colorOf(120f, 1f, 1f),
                    colorOf(180f, 1f, 1f), colorOf(240f, 1f, 1f), colorOf(300f, 1f, 1f),
                    colorOf(359.9f, 1f, 1f),
                )
            ),
            frac = h / 360f,
        ) { f -> h = (f * 359.9f).coerceIn(0f, 359.9f); emit() }

        AgTuneBar(
            brush = Brush.horizontalGradient(listOf(colorOf(h, 0f, val0), colorOf(h, 1f, val0))),
            frac = sat,
        ) { f -> sat = f.coerceIn(0f, 1f); emit() }

        AgTuneBar(
            brush = Brush.horizontalGradient(
                listOf(Color.Black, colorOf(h, sat, 1f))
            ),
            frac = val0,
        ) { f -> val0 = f.coerceIn(0f, 1f); emit() }
    }
}

/** 取色滑轨：圆角轨道 + 白色描边圆钮（画在 drawBehind 里，避免 offset 换算） */
@Composable
private fun AgTuneBar(brush: Brush, frac: Float, onChange: (Float) -> Unit) {
    var widthPx by remember { mutableStateOf(1f) }
    val lineColor = Ag.Line
    Box(
        Modifier
            .fillMaxWidth()
            .padding(top = 10.dp)
            .height(24.dp)
            .onSizeChanged { widthPx = it.width.toFloat().coerceAtLeast(1f) }
            .pointerInput(Unit) {
                detectTapGestures { off ->
                    onChange((off.x / widthPx).coerceIn(0f, 1f))
                }
            }
            .pointerInput(Unit) {
                detectHorizontalDragGestures { change, _ ->
                    onChange((change.position.x / widthPx).coerceIn(0f, 1f))
                }
            },
        contentAlignment = Alignment.CenterStart,
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(12.dp)
                .clip(RoundedCornerShape(99.dp))
                .background(brush)
                .border(1.dp, Ag.Line, RoundedCornerShape(99.dp))
        )
        Spacer(
            Modifier
                .fillMaxWidth()
                .height(24.dp)
                .drawBehind {
                    val r = 8.dp.toPx()
                    val cx = (frac * size.width).coerceIn(r, size.width - r)
                    drawCircle(Color.White, r, androidx.compose.ui.geometry.Offset(cx, size.height / 2f))
                    drawCircle(
                        lineColor, r, androidx.compose.ui.geometry.Offset(cx, size.height / 2f),
                        style = androidx.compose.ui.graphics.drawscope.Stroke(1.dp.toPx()),
                    )
                }
        )
    }
}

/** 取色结果的十六进制文本，#RRGGBB */
private fun hexOf(argb: Int): String =
    if (argb == 0) "#跟随主题" else String.format("#%06X", argb and 0xFFFFFF)

/** 卡片内的小节标题：把同一张卡里的「图标风格 / 启动器图标」分开 */
@Composable
private fun AgSubLabel(text: String) {
    Text(
        text,
        fontSize = 12.5.sp,
        fontWeight = FontWeight.ExtraBold,
        color = Ag.Tx2,
        modifier = Modifier.padding(start = 2.dp, bottom = 10.dp),
    )
}

/** .dopt：底栏样式选择卡（横向滑动选择器里的紧凑小卡） */
@Composable
private fun AgDockStyleChip(name: String, code: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Column(
        modifier
            .agClick(onClick = onClick)
            .clip(RoundedCornerShape(14.dp))
            .background(if (selected) Ag.Vio.copy(alpha = 0.059f) else Color(0x8CFFFFFF))
            .border(
                1.4.dp,
                if (selected) Ag.Vio.copy(alpha = 0.502f) else Ag.Line,
                RoundedCornerShape(14.dp),
            )
            .padding(horizontal = 5.dp, vertical = 9.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        MiniDockPreview(code)
        Text(
            name,
            fontSize = 10.sp,
            maxLines = 1,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            fontWeight = FontWeight.ExtraBold,
            color = if (selected) Ag.Vio else Ag.Tx2,
        )
    }
}

/**
 * 迷你底栏预览：一颗 48×18 的缩比胶囊，里面 4 个格子 ——
 * 第 1 格是该样式的「选中代表元素」，其余是未选中点；带中央卡位的样式第 2 格换成狐狸圆。
 * 所有色块都带一圈 1dp 描边或足够深的底色，浅色背景下不会糊成一片白。
 */
@Composable
private fun MiniDockPreview(code: String) {
    val ac = Ag.accent
    val dark = Ag.isDark
    val grad = Brush.linearGradient(listOf(ac.primary, ac.mid, ac.secondary))
    val dotColor = Ag.Tx3.copy(alpha = 0.45f)
    val kitCenter = code == "center" || code in setOf("bubble", "film", "neon", "frost", "sticker")

    val pillBg: Color = when (code) {
        "film" -> if (dark) Color(0xFF0E0C15) else Color(0xFFF7F1E8)
        "neon" -> if (dark) Color(0xFF221D31) else Color(0xF2FFFFFF)
        "frost" -> if (dark) Color(0x8C3A344A) else Color(0xE6FFFFFF)
        "track" -> if (dark) Color(0xFF221F2C) else Color(0xFFF4EFFA)
        "editorial" -> if (dark) Color(0xFF1B1A20) else Color(0xFFFFFCF6)
        "nocturne" -> Color(0xFF0F1018)
        "v7", "v10" -> Color.Transparent
        else -> if (dark) Color(0xFF1F1E32) else Color.White
    }
    val pillBorder: Color = when (code) {
        "film" -> if (dark) Color(0x29FFD84D) else Color(0x14000000)
        "neon" -> ac.primary.copy(alpha = 0.30f)
        "frost" -> Color(0x59FFFFFF)
        "editorial" -> Color(0x231D1C22)
        "nocturne" -> Color(0x26FFFFFF)
        else -> Ag.Line.copy(alpha = 0.7f)
    }
    val pillRadius = when (code) {
        "film", "track" -> 5.dp
        "neon" -> 6.dp
        "frost" -> 7.dp
        else -> 9.dp
    }

    Box(
        Modifier
            .fillMaxWidth()
            .height(26.dp)
            .clip(RoundedCornerShape(9.dp))
            .background(Ag.TintInk),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            Modifier
                .padding(horizontal = 5.dp)
                .fillMaxWidth()
                .height(18.dp)
                .clip(RoundedCornerShape(pillRadius))
                .background(pillBg)
                .border(1.dp, pillBorder, RoundedCornerShape(pillRadius))
                .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            MiniSelected(code, grad, ac, dark)
            if (kitCenter) {
                Box(
                    Modifier
                        .size(9.dp)
                        .clip(CircleShape)
                        .background(grad)
                        .border(1.2.dp, if (dark) Color(0xFF22213A) else Color.White, CircleShape)
                )
            }
            repeat(if (kitCenter) 2 else 3) {
                Box(Modifier.size(4.dp).clip(CircleShape).background(dotColor))
            }
        }
    }
}

/** 选中代表元素：每套一个 14dp 以内的小色块，一眼能认出是哪套 */
@Composable
private fun MiniSelected(code: String, grad: Brush, ac: me.diksu.ultra.foxsu.ui.theme.AccentPalette, dark: Boolean) {
    val pill = RoundedCornerShape(99.dp)
    Box(contentAlignment = Alignment.Center) {
        when (code) {
            "v6", "v11", "v9" -> Box(Modifier.size(9.dp).clip(CircleShape).background(grad))
            "v7" -> Box(Modifier.size(9.dp).clip(CircleShape).border(1.6.dp, ac.primary, CircleShape))
            "v8" -> Box(
                Modifier.size(9.dp).clip(RoundedCornerShape(3.dp))
                    .background(if (dark) Color(0xFF2A2940) else Color.White)
                    .border(1.dp, Ag.Line, RoundedCornerShape(3.dp))
            )
            "v10" -> Box(Modifier.size(9.dp).clip(CircleShape).background(ac.primary.copy(alpha = 0.14f))) {
                Box(
                    Modifier
                        .align(Alignment.TopCenter)
                        .width(7.dp).height(1.6.dp)
                        .clip(pill).background(grad)
                )
            }
            "bubble" -> Box(Modifier.size(10.dp).clip(CircleShape).background(grad))
            "film" -> Box(Modifier.size(8.dp).clip(CircleShape).border(1.8.dp, if (dark) Color(0xFFFFD84D) else ac.primary, CircleShape))
            "neon" -> Box(
                Modifier.size(10.dp).clip(CircleShape).background(
                    Brush.radialGradient(
                        listOf(if (dark) ac.secondary else ac.primary, ac.secondary.copy(alpha = 0.2f), Color.Transparent)
                    )
                )
            )
            "frost" -> Box(Modifier.size(11.dp).clip(CircleShape).background(if (dark) Color(0xFF3A3448) else Color.White)) {
                Box(Modifier.size(4.dp).clip(CircleShape).background(ac.primary))
            }
            "focus" -> Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(width = 14.dp, height = 9.dp).clip(pill).background(ac.soft)) {
                    Box(Modifier.align(Alignment.Center).size(4.dp).clip(CircleShape).background(ac.primary))
                }
            }
            "sticker" -> Box(
                Modifier
                    .size(9.dp)
                    .graphicsLayer { rotationZ = -8f }
                    .clip(RoundedCornerShape(3.dp))
                    .background(Color(0xFFFFE7DF))
            ) {
                Box(Modifier.align(Alignment.Center).size(4.dp).clip(CircleShape).background(ac.primary))
            }
            "track" -> Box(
                Modifier.size(width = 12.dp, height = 10.dp).clip(RoundedCornerShape(3.dp))
                    .background(if (dark) Color(0xFF322D40) else Color.White)
            ) {
                Box(Modifier.align(Alignment.Center).size(4.dp).clip(CircleShape).background(ac.primary))
            }
            "editorial" -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.size(4.dp).clip(CircleShape).background(ac.primary))
                Spacer(Modifier.height(1.5.dp))
                Box(Modifier.size(width = 8.dp, height = 1.6.dp).clip(pill).background(ac.primary))
            }
            "glass" -> Box(Modifier.size(width = 13.dp, height = 9.dp).clip(pill).background(ac.soft)) {
                Box(
                    Modifier
                        .align(Alignment.Center)
                        .width(7.dp).height(1.6.dp)
                        .clip(pill).background(Brush.horizontalGradient(listOf(ac.primary, ac.secondary)))
                )
            }
            "nocturne" -> Box(Modifier.size(9.dp).clip(RoundedCornerShape(3.dp)).background(ac.primary.copy(alpha = 0.22f)))
            // d0 / v1 / pages / center：渐变实底
            else -> Box(Modifier.size(width = 12.dp, height = 9.dp).clip(pill).background(grad))
        }
    }
}

/** 强调色卡：一枚渐变圆片 + 名称，选中显示对勾 */
@Composable
private fun AgAccentChip(
    palette: me.diksu.ultra.foxsu.ui.theme.AccentPalette,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Column(
        Modifier.agClick(onClick = onClick).width(62.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        Box(
            Modifier
                .size(46.dp)
                .shadow(
                    if (selected) 8.dp else 2.dp,
                    CircleShape,
                    spotColor = palette.primary.copy(alpha = 0.45f),
                    ambientColor = palette.primary.copy(alpha = 0.18f),
                )
                .clip(CircleShape)
                .background(Brush.linearGradient(listOf(palette.primary, palette.mid, palette.secondary)))
                .border(if (selected) 3.dp else 0.dp, Color.White, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            if (selected) {
                Icon(
                    Icons.Rounded.Check,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
        Text(
            palette.label,
            fontSize = 11.sp,
            fontWeight = if (selected) FontWeight.ExtraBold else FontWeight.Bold,
            color = if (selected) Ag.Vio else Ag.Tx2,
            maxLines = 1,
        )
    }
}

/** .fchip：字体选择卡 */
@Composable
private fun AgFontChip(name: String, glyph: String, family: FontFamily?, selected: Boolean, onClick: () -> Unit) {
    Column(
        Modifier
            .agClick(onClick = onClick)
            .clip(RoundedCornerShape(16.dp))
            .background(if (selected) Ag.Vio.copy(alpha = 0.059f) else Color(0x8CFFFFFF))
            .border(1.4.dp, if (selected) Ag.Vio.copy(alpha = 0.502f) else Ag.Line, RoundedCornerShape(16.dp))
            .padding(14.dp, 14.dp, 14.dp, 11.dp)
            .width(96.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        Text(
            glyph,
            fontSize = 26.sp, fontWeight = FontWeight.ExtraBold,
            color = if (selected) Ag.Vio else Ag.Tx,
            fontFamily = family,
        )
        Text(name, fontSize = 11.5.sp, fontWeight = FontWeight.Bold, color = if (selected) Ag.Vio else Ag.Tx2)
    }
}

/** .icard：启动器图标卡（完整展示狐狸图，白底浅描边，选中紫框） */
@Composable
private fun AgLauncherIconCard(style: LauncherIconManager.Style, selected: Boolean, onClick: () -> Unit) {
    Column(
        Modifier
            .agClick(onClick = onClick)
            .clip(RoundedCornerShape(16.dp))
            .background(if (selected) Ag.Vio.copy(alpha = 0.059f) else Color(0x8CFFFFFF))
            .border(1.4.dp, if (selected) Ag.Vio.copy(alpha = 0.502f) else Ag.Line, RoundedCornerShape(16.dp))
            .padding(10.dp, 10.dp, 10.dp, 9.dp)
            .width(72.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        Box(Modifier.size(46.dp)) {
            Box(
                Modifier
                    .size(46.dp)
                    .shadow(
                        4.dp, RoundedCornerShape(14.dp),
                        spotColor = Ag.Vio.copy(alpha = 0.22f), ambientColor = Ag.Vio.copy(alpha = 0.12f),
                    )
                    .clip(RoundedCornerShape(14.dp)),
            ) {
                Image(
                    painter = painterResource(style.preview),
                    contentDescription = style.label,
                    contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            if (selected) {
                Box(
                    Modifier
                        .align(Alignment.BottomEnd)
                        .offset(x = 3.dp, y = 3.dp)
                        .size(17.dp)
                        .background(Color.White, CircleShape)
                        .padding(1.5.dp)
                        .background(Ag.Vio, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Rounded.Check, null, tint = Color.White, modifier = Modifier.size(10.dp))
                }
            }
        }
        Text(
            style.label,
            fontSize = 11.sp,
            fontWeight = if (selected) FontWeight.ExtraBold else FontWeight.Bold,
            color = if (selected) Ag.Vio else Ag.Tx2,
            maxLines = 1,
        )
    }
}

/** 自定义强调色卡：未选时是一圈彩虹环，选过之后显示当前色 */
@Composable
private fun AgAccentCustomChip(color: Int, selected: Boolean, onClick: () -> Unit) {
    Column(
        Modifier.agClick(onClick = onClick).width(62.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        Box(
            Modifier
                .size(46.dp)
                .clip(CircleShape)
                .background(if (selected) androidx.compose.ui.graphics.SolidColor(Color(color)) else Ag.Grad)
                .border(if (selected) 3.dp else 0.dp, Color.White, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            if (selected) {
                Icon(Icons.Rounded.Check, null, tint = Color.White, modifier = Modifier.size(20.dp))
            } else {
                // 未选：中间挖一枚白点，读起来像「+」号色板
                Box(Modifier.size(16.dp).background(Color.White, CircleShape))
            }
        }
        Text(
            if (selected) "自定义" else "更多",
            fontSize = 11.sp,
            fontWeight = if (selected) FontWeight.ExtraBold else FontWeight.Bold,
            color = if (selected) Ag.Vio else Ag.Tx2,
            maxLines = 1,
        )
    }
}

/**
 * 滑杆行 .sliderrow：t 14.5/700 + v 13/800 紫
 * 轨道自绘：8px 圆角矩形 rgba(30,25,70,.08)；thumb 20dp 渐变圆 + 3px 白边 + 紫色投影。
 */
@Composable
private fun AgSliderRow(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    display: String,
    onChange: (Float) -> Unit,
    sub: String? = null,
    steps: Int = 0,
) {
    val clamped = value.coerceIn(range.start, range.endInclusive)
    val span = range.endInclusive - range.start
    val frac = if (span <= 0f) 0f else ((clamped - range.start) / span).coerceIn(0f, 1f)
    val trackColor = if (Ag.isDark) Color(0x1FFFFFFF) else Color(0x141E1946)
    var trackWidth by remember { mutableStateOf(0.dp) }
    val density = androidx.compose.ui.platform.LocalDensity.current

    fun snap(raw: Float): Float {
        if (steps <= 0) return raw.coerceIn(range.start, range.endInclusive)
        val step = span / (steps + 1)
        return (range.start + Math.round((raw - range.start) / step) * step)
            .coerceIn(range.start, range.endInclusive)
    }

    Column(Modifier.padding(top = 14.dp, bottom = 14.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(label, fontSize = 14.5.sp, fontWeight = FontWeight.Bold, color = Ag.Tx)
                if (sub != null) {
                    Spacer(Modifier.height(1.dp))
                    Text(sub, fontSize = 11.5.sp, color = Ag.Tx2)
                }
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                Text(display, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, color = Ag.Vio)
                Text("›", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Ag.Tx3)
            }
        }
        Spacer(Modifier.height(8.dp))
        // 自绘滑杆：8px 轨道 + 20dp 渐变圆钮（3px 白边）
        Box(
            Modifier
                .fillMaxWidth()
                .height(20.dp)
                .onSizeChanged { trackWidth = with(density) { it.width.toDp() } }
                .pointerInput(range, steps) {
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        val w = size.width.toFloat().coerceAtLeast(1f)
                        onChange(snap(range.start + (down.position.x / w).coerceIn(0f, 1f) * span))
                        do {
                            val event = awaitPointerEvent()
                            event.changes.forEach { ch ->
                                onChange(snap(range.start + (ch.position.x / w).coerceIn(0f, 1f) * span))
                                ch.consume()
                            }
                        } while (event.changes.any { it.pressed })
                    }
                },
            contentAlignment = Alignment.CenterStart,
        ) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(trackColor)
            )
            Box(
                Modifier
                    .fillMaxWidth(frac)
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(Ag.Grad)
            )
            Box(
                Modifier
                    .offset(x = ((trackWidth - 20.dp) * frac).coerceAtLeast(0.dp))
                    .size(20.dp)
                    .shadow(4.dp, CircleShape, spotColor = Ag.Vio.copy(alpha = 0.4f), ambientColor = Ag.Vio.copy(alpha = 0.302f))
                    .clip(CircleShape)
                    .background(Ag.Grad)
                    .border(3.dp, Color.White, CircleShape),
            )
        }
    }
}

/** .pick：右侧可点值 */
@Composable
private fun AgPick(text: String, onClick: () -> Unit) {
    Text(
        text,
        fontSize = 13.sp,
        fontWeight = FontWeight.Bold,
        color = Ag.Vio,
        modifier = Modifier.agClick(onClick = onClick),
    )
}

/** 胶囊动作按钮（渐变实底 / 次级描边） */
@Composable
private fun AgPillAction(
    text: String,
    icon: ImageVector,
    primary: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(13.dp)
    Row(
        modifier
            .height(38.dp)
            .agClick(onClick = onClick)
            .then(
                if (primary) {
                    Modifier
                        .clip(shape)
                        .background(Ag.Grad, shape)
                } else {
                    Modifier
                        .background(Ag.Vio.copy(alpha = 0.071f), shape)
                        .border(1.dp, Ag.Vio.copy(alpha = 0.251f), shape)
                }
            )
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Icon(icon, null, tint = if (primary) Color.White else Ag.Vio, modifier = Modifier.size(15.dp))
        Spacer(Modifier.width(7.dp))
        Text(
            text,
            fontSize = 12.5.sp,
            fontWeight = FontWeight.Bold,
            color = if (primary) Color.White else Ag.Vio,
            maxLines = 1,
        )
    }
}

/**
 * 按钮形状选择卡（10 套）：小样按真实轮廓绘制，选中项描边 + 底色一起高亮。
 */
@Composable
private fun AgButtonShapeCard(
    button: DockButton,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val ac = Ag.accent
    Column(
        modifier
            .agClick(onClick = onClick)
            .clip(RoundedCornerShape(12.dp))
            .background(if (selected) Ag.Vio.copy(alpha = 0.059f) else Color(0x8CFFFFFF))
            .border(
                1.4.dp,
                if (selected) Ag.Vio.copy(alpha = 0.502f) else Ag.Line,
                RoundedCornerShape(12.dp),
            )
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        DockButtonSwatch(
            style = button,
            selected = selected,
            accent = ac.primary,
            accentMid = ac.mid,
            accentSecond = ac.secondary,
            dark = Ag.isDark,
            modifier = Modifier.fillMaxWidth().height(32.dp),
        )
        Text(
            button.label,
            fontSize = 9.5.sp,
            fontWeight = if (selected) FontWeight.ExtraBold else FontWeight.Bold,
            color = if (selected) Ag.Vio else Ag.Tx2,
            maxLines = 1,
        )
    }
}

/** 传统底栏结构卡：上方一条缩略栏，下方套名。 */
@Composable
private fun AgClassicStyleCard(
    style: me.diksu.ultra.foxsu.ui.component.bottombar.ClassicDockStyle,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val ac = Ag.accent
    Column(
        modifier
            .agClick(onClick = onClick)
            .clip(RoundedCornerShape(12.dp))
            .background(if (selected) Ag.Vio.copy(alpha = 0.059f) else Color(0x8CFFFFFF))
            .border(
                1.4.dp,
                if (selected) Ag.Vio.copy(alpha = 0.502f) else Ag.Line,
                RoundedCornerShape(12.dp),
            )
            .padding(vertical = 8.dp, horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        ClassicStyleSwatch(
            style = style,
            accent = ac.primary,
            accent2 = ac.secondary,
            dark = Ag.isDark,
            modifier = Modifier.fillMaxWidth().height(34.dp),
        )
        Text(
            style.title,
            fontSize = 9.5.sp,
            fontWeight = if (selected) FontWeight.ExtraBold else FontWeight.Bold,
            color = if (selected) Ag.Vio else Ag.Tx2,
            maxLines = 1,
        )
    }
}

/** 缩略图：把每套结构的「辨识特征」画出来，不做完整复刻。 */
@Composable
private fun ClassicStyleSwatch(
    style: me.diksu.ultra.foxsu.ui.component.bottombar.ClassicDockStyle,
    accent: Color,
    accent2: Color,
    dark: Boolean,
    modifier: Modifier = Modifier,
) {
    val surface = if (dark) Color(0xFF2A283D) else Color(0xFFF1EFF8)
    androidx.compose.foundation.Canvas(modifier) {
        val w = size.width
        val h = size.height
        val barTop = h * 0.18f
        val barH = h * 0.66f
        val slot = w / 4f

        // 底板
        when (style) {
            me.diksu.ultra.foxsu.ui.component.bottombar.ClassicDockStyle.SAW,
            me.diksu.ultra.foxsu.ui.component.bottombar.ClassicDockStyle.TOOTH -> {
                val teeth = if (style.id == 1) 6 else 8
                val p = w / teeth
                val d = h * 0.16f
                val path = androidx.compose.ui.graphics.Path().apply {
                    moveTo(0f, barTop + d)
                    var x = 0f
                    while (x < w) {
                        lineTo(x + p / 2f, barTop)
                        lineTo(x + p, barTop + d)
                        x += p
                    }
                    lineTo(w, barTop + barH)
                    lineTo(0f, barTop + barH)
                    close()
                }
                drawPath(path, surface)
            }
            me.diksu.ultra.foxsu.ui.component.bottombar.ClassicDockStyle.HONEY -> {
                for (i in 0 until 4) {
                    val cut = slot * 0.12f
                    val x0 = i * slot + 1f
                    val x1 = (i + 1) * slot - 1f
                    val cy = barTop + barH / 2f
                    val path = androidx.compose.ui.graphics.Path().apply {
                        moveTo(x0 + cut, barTop)
                        lineTo(x1 - cut, barTop)
                        lineTo(x1, cy)
                        lineTo(x1 - cut, barTop + barH)
                        lineTo(x0 + cut, barTop + barH)
                        lineTo(x0, cy)
                        close()
                    }
                    drawPath(path, if (i == 0) accent else surface)
                }
            }
            me.diksu.ultra.foxsu.ui.component.bottombar.ClassicDockStyle.CAPSULE -> {
                for (i in 0 until 4) {
                    val pad = if (i == 0) 1f else slot * 0.16f
                    drawRoundRect(
                        color = if (i == 0) accent else surface,
                        topLeft = androidx.compose.ui.geometry.Offset(i * slot + pad, barTop + barH * 0.18f),
                        size = androidx.compose.ui.geometry.Size(slot - pad * 2, barH * 0.64f),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(barH),
                    )
                }
            }
            me.diksu.ultra.foxsu.ui.component.bottombar.ClassicDockStyle.ARC -> {
                drawRoundRect(
                    color = surface,
                    topLeft = androidx.compose.ui.geometry.Offset(0f, barTop),
                    size = androidx.compose.ui.geometry.Size(w, barH * 1.15f),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(w * 0.18f, barH * 0.36f),
                )
            }
            me.diksu.ultra.foxsu.ui.component.bottombar.ClassicDockStyle.RELIEF -> {
                drawRoundRect(
                    color = surface,
                    topLeft = androidx.compose.ui.geometry.Offset(0f, barTop),
                    size = androidx.compose.ui.geometry.Size(w, barH),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(w * 0.22f, barH * 0.5f),
                )
            }
            me.diksu.ultra.foxsu.ui.component.bottombar.ClassicDockStyle.STACK -> {
                for (i in 0 until 4) {
                    val top = if (i == 0) barTop else barTop + barH * 0.14f
                    val hh = if (i == 0) barH else barH * 0.86f
                    drawRoundRect(
                        color = if (i == 0) accent else surface,
                        topLeft = androidx.compose.ui.geometry.Offset(i * slot + 1f, top),
                        size = androidx.compose.ui.geometry.Size(slot - 2f, hh),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(4f),
                    )
                }
            }
            me.diksu.ultra.foxsu.ui.component.bottombar.ClassicDockStyle.ASYM -> {
                drawRoundRect(
                    color = surface,
                    topLeft = androidx.compose.ui.geometry.Offset(0f, barTop),
                    size = androidx.compose.ui.geometry.Size(w, barH),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(w * 0.22f, barH * 0.55f),
                )
            }
            else -> {
                val r = when (style) {
                    me.diksu.ultra.foxsu.ui.component.bottombar.ClassicDockStyle.CLASSIC -> 0f
                    else -> barH * 0.26f
                }
                drawRoundRect(
                    color = surface,
                    topLeft = androidx.compose.ui.geometry.Offset(0f, barTop),
                    size = androidx.compose.ui.geometry.Size(w, barH),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(r, r),
                )
            }
        }

        // 四个 tab 的点 + 第一个高亮
        val cy = barTop + barH * 0.5f
        for (i in 0 until 4) {
            val x = slot * (i + 0.5f)
            if (i == 0) {
                when (style) {
                    me.diksu.ultra.foxsu.ui.component.bottombar.ClassicDockStyle.STAR -> {
                        val r = slot * 0.26f
                        val inner = r * 0.382f
                        val path = androidx.compose.ui.graphics.Path().apply {
                            for (k in 0 until 16) {
                                val a = Math.PI / 8.0 * k - Math.PI / 2.0
                                val rad = if (k % 2 == 0) r else inner
                                val px = x + (rad * Math.cos(a)).toFloat()
                                val py = cy + (rad * Math.sin(a)).toFloat()
                                if (k == 0) moveTo(px, py) else lineTo(px, py)
                            }
                            close()
                        }
                        drawPath(path, accent.copy(alpha = 0.35f))
                    }
                    me.diksu.ultra.foxsu.ui.component.bottombar.ClassicDockStyle.HALO -> {
                        drawCircle(color = accent.copy(alpha = 0.3f), radius = slot * 0.3f, center = androidx.compose.ui.geometry.Offset(x, cy))
                        drawCircle(color = accent, radius = slot * 0.1f, center = androidx.compose.ui.geometry.Offset(x, cy))
                    }
                    else -> Unit
                }
            }
            drawCircle(
                color = if (i == 0) accent else accent.copy(alpha = 0.28f),
                radius = slot * 0.09f,
                center = androidx.compose.ui.geometry.Offset(x, cy),
            )
        }

        // 右侧竖槽（温度计）
        if (style == me.diksu.ultra.foxsu.ui.component.bottombar.ClassicDockStyle.THERMO) {
            drawRoundRect(
                color = accent.copy(alpha = 0.18f),
                topLeft = androidx.compose.ui.geometry.Offset(w - 4f, barTop + 2f),
                size = androidx.compose.ui.geometry.Size(2.5f, barH - 4f),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(2f),
            )
            drawRoundRect(
                color = accent,
                topLeft = androidx.compose.ui.geometry.Offset(w - 4f, barTop + barH * 0.55f),
                size = androidx.compose.ui.geometry.Size(2.5f, barH * 0.45f - 2f),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(2f),
            )
        }
        // 顶部横轨（信号灯）
        if (style == me.diksu.ultra.foxsu.ui.component.bottombar.ClassicDockStyle.RAIL) {
            drawRoundRect(
                color = accent.copy(alpha = 0.18f),
                topLeft = androidx.compose.ui.geometry.Offset(3f, barTop + 2f),
                size = androidx.compose.ui.geometry.Size(w - 6f, 1.6f),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(1f),
            )
            drawCircle(color = accent, radius = 2.6f, center = androidx.compose.ui.geometry.Offset(slot * 0.5f, barTop + 2.8f))
        }
        // 中央竖缝（折叠屏）
        if (style == me.diksu.ultra.foxsu.ui.component.bottombar.ClassicDockStyle.FOLD) {
            drawRect(
                color = accent.copy(alpha = 0.25f),
                topLeft = androidx.compose.ui.geometry.Offset(w / 2f, barTop + 2f),
                size = androidx.compose.ui.geometry.Size(1.2f, barH - 4f),
            )
        }
        // 顶部冲孔（穿孔带）
        if (style == me.diksu.ultra.foxsu.ui.component.bottombar.ClassicDockStyle.PERF) {
            var x = 3f
            while (x < w - 2f) {
                drawCircle(color = accent.copy(alpha = 0.3f), radius = 1.1f, center = androidx.compose.ui.geometry.Offset(x, barTop + 2f))
                x += 5f
            }
        }
        // 左侧锚点（侧锚）
        if (style == me.diksu.ultra.foxsu.ui.component.bottombar.ClassicDockStyle.ANCHOR) {
            drawCircle(color = accent, radius = 2.4f, center = androidx.compose.ui.geometry.Offset(0.5f, cy))
            drawRect(
                color = accent.copy(alpha = 0.4f),
                topLeft = androidx.compose.ui.geometry.Offset(1.5f, cy - 0.8f),
                size = androidx.compose.ui.geometry.Size(slot * 0.44f, 1.6f),
            )
        }
    }
}
