package me.diksu.ultra.foxsu.ui.screen.ag

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.animation.animateContentSize

/**
 * 晨曦玻璃（Aurora Glass）设计令牌 — 严格对应交付文档 tokens/设计稿：
 * 135° 品牌渐变 #6C5CE7 → #A855F7(55%) → #EC4899；暖白雾彩底 + 毛玻璃卡。
 */
object Ag {
    // ---- 暗色模式判定（tokens.css L3 [data-mode="dark"]）----
    val isDark: Boolean @Composable get() = when (me.diksu.ultra.foxsu.ui.theme.LocalColorMode.current) {
        2, 5, 6 -> true   // DARK / MONET_DARK / DARK_AMOLED
        1, 4 -> false     // LIGHT / MONET_LIGHT
        else -> androidx.compose.foundation.isSystemInDarkTheme() // 跟随系统
    }

    // ---- 背景色主题（定制 → 背景色，20 套 × 亮暗；0 = 用回下面的皮肤自带配色）----
    /**
     * 当前主题的底色令牌；null 表示用户没选主题（第 1 项「默认」）。
     *
     * 只覆盖「底」这一层：画布、卡片、描边、正文与柔光。强调色 [accent] 不动——
     * 两条选择器各管一半，换背景色不会顺手把按钮颜色也改掉。
     * 亮暗版本由外观模式决定（见 [isDark]），而不是跟随系统。
     */
    private val pal: me.diksu.ultra.foxsu.ui.theme.BgPalette? @Composable
        get() = me.diksu.ultra.foxsu.ui.theme.currentBgPalette(isDark)

    // ---- 背景与画布 ----
    val Bg1: Color @Composable get() = pal?.bg ?: if (isDark) Color(0xFF131221) else Color(0xFFF7F6FB)
    val Bg2: Color @Composable get() = pal?.bg2 ?: if (isDark) Color(0xFF1A192E) else Color(0xFFF2F4FA)

    // ---- 表面（与背景同色系，靠透明度分层；tokens v1.2.0）----
    /** 卡片保留一层透明：主题的 surface 是实底色，原样铺满会把皮肤的玻璃质感抹平。 */
    val Card: Color @Composable get() =
        pal?.surface?.copy(alpha = if (isDark) 0.86f else 0.78f)
            ?: if (isDark) Color(0x8C1A192E) else Color(0x8CF7F6FB)

    val CardSolid: Color @Composable get() =
        pal?.surface ?: if (isDark) Color(0xFF1E1D30) else Color(0xFFF7F6FB) // 暗色实底提亮一档

    // ---- 描边 ----
    val Line: Color @Composable get() =
        pal?.line ?: if (isDark) Color(0x17FFFFFF) else Color(0x121E1946)

    val TintInk: Color @Composable get() =
        pal?.text?.copy(alpha = 0.05f) ?: if (isDark) Color(0x12FFFFFF) else Color(0x0D1E1946)

    // ---- 文字 ----
    val Tx: Color @Composable get() = pal?.text ?: if (isDark) Color(0xFFF2F1FA) else Color(0xFF191826)

    /**
     * 次文字往主文字混 22%。
     *
     * 主题包的 text-2 是给网页「小字说明」配的，放到 12.5sp 正文里，浅色几套
     * （柠檬黄 / 抹茶 / 燕麦）只有 3.7:1，达不到 AA 的 4.5:1。混这一档不动色相，
     * 只把最淡的六套抬到 4.9:1，其余本来够的几乎看不出变化。
     */
    val Tx2: Color @Composable get() =
        pal?.let { lerp(it.text2, it.text, 0.22f) } ?: if (isDark) Color(0xFFB2B0CC) else Color(0xFF66647A)

    /** 第三档文字：原皮肤是固定灰阶，主题里没有这一档，按「次文字 → 画布」插值推出来。 */
    val Tx3: Color @Composable get() =
        pal?.let { lerp(lerp(it.text2, it.text, 0.22f), it.bg, 0.34f) }
            ?: if (isDark) Color(0xFF7C7A98) else Color(0xFF9C9AAE)

    val Chev: Color @Composable get() =
        pal?.let { lerp(lerp(it.text2, it.text, 0.22f), it.bg, 0.58f) }
            ?: if (isDark) Color(0xFF5E5C7A) else Color(0xFFC6C4D6)

    // ---- 品牌（明暗一致；随「定制 → 全局强调色」的 7 套配色切换）----
    val accent: me.diksu.ultra.foxsu.ui.theme.AccentPalette
        get() = me.diksu.ultra.foxsu.ui.theme.currentAccent
    val Vio: Color get() = accent.primary
    val Vio2: Color get() = accent.mid
    val Mag: Color get() = accent.secondary
    val Teal = Color(0xFF14B8A6)
    val Amber = Color(0xFFF59E0B)
    val Danger = Color(0xFFDC2626)
    val Success = Color(0xFF10B981)

    val Grad: Brush
        get() = Brush.linearGradient(listOf(accent.primary, accent.mid, accent.secondary))

    /** 选中底衬：亮色用极淡同色相，暗色用低透明度同色相 */
    val Soft: Color
        @Composable get() = if (isDark) accent.softDark else accent.soft

    val TitleGrad = Grad

    /** 页面底色线性渐变（设计稿 linear-gradient(160deg, --bg1, --bg2)） */
    @Composable
    fun background(): Brush = Brush.linearGradient(
        listOf(Bg1, Bg2),
        start = androidx.compose.ui.geometry.Offset(0f, 0f),
        end = androidx.compose.ui.geometry.Offset(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY),
    )

    /** 固定顶栏底：设计稿 .headwrap 完全透明 + blur(14px)，无分隔线 */
    @Composable
    fun headSurface(): Color = Color.Transparent

    /** 搜索框底（.search rgba(30,25,70,.05) / 暗色 rgba(255,255,255,.06)） */
    @Composable
    fun searchBg(): Color =
        pal?.text?.copy(alpha = if (isDark) 0.06f else 0.05f)
            ?: if (isDark) Color(0x0FFFFFFF) else Color(0x0D1E1946)

    /** 开关 OFF 轨道（明暗一致）*/
    val ToggleOff = Color(0xFFD9D8E6)

    /** .ric 图标底透明度：v/m 10%，t/a 12% */
    fun ricAlpha(tint: Color): Float =
        if (tint == Teal || tint == Amber) 0.12f else 0.10f
}

/**
 * 设计稿 `.headwrap{background:transparent}`：顶栏是页面里的固定区（不是覆盖在列表上），
 * 内容不会穿透，所以直接保持完全透明——之前加的半透明白在彩色卡片页上会变成一条白块。
 */
@Composable
fun Modifier.agHeadBlur(): Modifier = this

/**
 * 页面背景：设计稿 .phone 的 linear-gradient(160deg,--bg1,--bg2)
 * + .phone::before 三向径向雾彩（紫 .14 / 品红 .09 / 青 .10）。
 * 玻璃卡之所以"透"，靠的就是这三团雾彩在背后发光。
 */
@Composable
fun Modifier.agPageBg(): Modifier {
    // 用户选了壁纸时页面背景必须让位：这层渐变原本是不透明底，
    // 会把 MainActivity 最底层画好的壁纸整片盖掉（其他主题能看见壁纸、
    // 只有 FoxSu Redesign 看不见，就是这里造成的）。
    if (me.diksu.ultra.foxsu.ui.component.hasWallpaperBackdrop()) return this
    val b1 = Ag.Bg1
    val b2 = Ag.Bg2
    // 三团雾彩：默认是品牌紫 / 品红 / 青；选了背景色主题后统一换成该套的 glow，
    // 否则暖米色底上飘着紫雾，看起来像没擦干净的图层。
    val themeGlow = me.diksu.ultra.foxsu.ui.theme.currentBgPalette(Ag.isDark)?.glow
    val fogA = themeGlow ?: Color(0x24A855F7)
    val fogB = themeGlow?.copy(alpha = themeGlow.alpha * 0.62f) ?: Color(0x17EC4899)
    val fogC = themeGlow?.copy(alpha = themeGlow.alpha * 0.72f) ?: Color(0x1A14B8A6)
    return this.drawBehind {
        // 底色：160deg 斜向渐变
        drawRect(
            brush = Brush.linearGradient(
                listOf(b1, b2),
                start = Offset(0f, 0f),
                end = Offset(size.width * 0.34f, size.height * 0.94f),
            )
        )
        // 紫色雾彩：at 110% -5%，rgba(168,85,247,.14)
        drawRect(
            brush = Brush.radialGradient(
                0f to fogA,
                0.65f to fogA.copy(alpha = 0f),
                1f to fogA.copy(alpha = 0f),
                center = Offset(size.width * 1.10f, -size.height * 0.05f),
                radius = size.width * 1.08f,
            )
        )
        // 品红雾彩：at -15% 25%，rgba(236,72,153,.09)
        drawRect(
            brush = Brush.radialGradient(
                0f to fogB,
                0.65f to fogB.copy(alpha = 0f),
                1f to fogB.copy(alpha = 0f),
                center = Offset(size.width * -0.15f, size.height * 0.25f),
                radius = size.width * 0.92f,
            )
        )
        // 青色雾彩：at 50% 110%，rgba(20,184,166,.10)
        drawRect(
            brush = Brush.radialGradient(
                0f to fogC,
                0.65f to fogC.copy(alpha = 0f),
                1f to fogC.copy(alpha = 0f),
                center = Offset(size.width * 0.50f, size.height * 1.10f),
                radius = size.width * 1.03f,
            )
        )
    }
}

/** 旧的纯色背景（保留兼容） */
@Composable
fun AgAuroraBackdrop(modifier: Modifier = Modifier) {
    Box(
        modifier
            .fillMaxWidth()
            .height(560.dp)
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(320.dp)
                .background(
                    Brush.radialGradient(
                        listOf(Color(0x24A855F7), Color.Transparent),
                        center = Offset(700f, -40f),
                        radius = 640f,
                    )
                )
        )
        Box(
            Modifier
                .fillMaxWidth()
                .height(300.dp)
                .background(
                    Brush.radialGradient(
                        listOf(Color(0x17EC4899), Color.Transparent),
                        center = Offset(30f, 260f),
                        radius = 560f,
                    )
                )
        )
    }
}

/** 按压 + 点击（统一触感反馈） */
@Composable
fun Modifier.agClick(pressScale: Float = 0.965f, enabled: Boolean = true, onClick: () -> Unit): Modifier {
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val s by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (pressed) pressScale else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "ag-click",
    )
    return this
        .graphicsLayer { scaleX = s; scaleY = s }
        .clickable(interactionSource = src, indication = null, enabled = enabled, onClick = onClick)
}

/** 毛玻璃感卡片（高斯模糊由背景雾彩透出，82–90% 白面）+ 轻影 0 2px 10px rgba(60,45,120,.04) */
@Composable
fun AgCard(
    modifier: Modifier = Modifier,
    radius: Dp = 24.dp,
    contentPadding: androidx.compose.foundation.layout.PaddingValues = androidx.compose.foundation.layout.PaddingValues(16.dp),
    onClick: (() -> Unit)? = null,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit,
) {
    val shape = RoundedCornerShape(radius)
    // 液态玻璃全局生效：开启后所有玻璃卡改为半透明白 + 亮描边（雾彩背景透出）
    val glass = me.diksu.ultra.foxsu.data.settings.CustomizationStore.current.cardGlass
    val glassCard = Ag.Card.copy(alpha = if (glass) 0.55f else Ag.Card.alpha)
    val glassLine = if (glass) Color(0x33FFFFFF) else Ag.Line
    val dark = Ag.isDark
    val shadowColor = if (dark) Color(0x40000000) else Color(0x0A3C2D78)
    val ambient = if (dark) Color(0x26000000) else Color(0x073C2D78)
    if (onClick != null) {
        Column(
            modifier
                .agClick(onClick = onClick)
                .shadow(2.dp, shape, spotColor = shadowColor, ambientColor = ambient)
                .clip(shape)
                .background(glassCard)
                .border(1.dp, glassLine, shape)
                .padding(contentPadding)
                .animateContentSize(),
            content = content,
        )
    } else {
        Column(
            modifier
                .shadow(2.dp, shape, spotColor = shadowColor, ambientColor = ambient)
                .clip(shape)
                .background(glassCard)
                .border(1.dp, glassLine, shape)
                .padding(contentPadding),
            content = content,
        )
    }
}

/** 信息行：.ric 色衬图标 + 标题/描述 + chev（行底 1px 分隔线，最后一行无线） */
@Composable
fun AgRow(
    title: String,
    desc: String,
    tint: Color,
    icon: ImageVector,
    showChev: Boolean = false,
    divider: Boolean = true,
    trailing: (@Composable androidx.compose.foundation.layout.RowScope.() -> Unit)? = null,
    onClick: (() -> Unit)? = null,
) {
    val base: Modifier = Modifier.fillMaxWidth()
    Column(
        (if (onClick != null) base.agClick(onClick = onClick) else base)
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AgRic(tint, icon)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(title, fontSize = 14.5.sp, fontWeight = FontWeight.Bold, color = Ag.Tx)
                if (desc.isNotEmpty()) {
                    Spacer(Modifier.height(3.dp))
                    Text(desc, fontSize = 12.5.sp, lineHeight = 19.sp, color = Ag.Tx2)
                }
            }
            trailing?.invoke(this)
            if (showChev) {
                Spacer(Modifier.width(6.dp))
                Text("›", fontSize = 20.sp, color = Ag.Chev, fontWeight = FontWeight.Bold)
            }
        }
        if (divider) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(Ag.Line)
            )
        }
    }
}

/**
 * 开关行 .trow：设计稿 padding 15px 0 + 1px 分隔线（末行无线），
 * 与 .row（padding 14px 0 + 14px gap）区分。
 */
@Composable
fun AgToggleRow(
    title: String,
    desc: String,
    tint: Color,
    icon: ImageVector,
    divider: Boolean = true,
    trailing: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit,
) {
    val base: Modifier = Modifier.fillMaxWidth()
    Column(base) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(vertical = 15.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AgRic(tint, icon)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(title, fontSize = 14.5.sp, fontWeight = FontWeight.Bold, color = Ag.Tx)
                if (desc.isNotEmpty()) {
                    Spacer(Modifier.height(2.dp))
                    Text(desc, fontSize = 12.5.sp, lineHeight = 18.sp, color = Ag.Tx2)
                }
            }
            trailing()
        }
        if (divider) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(Ag.Line)
            )
        }
    }
}

/**
 * 色衬图标块 .ric：40dp / 圆角 14 / 柔和浅色底 + 细描边 + 18px 线性图标。
 *
 * 参考定稿样式：方块底是「很淡的同色 + 一点点白」，不是透明的深色块；
 * 图标本身保持主题色。之前 38dp/圆角 12 在长列表里显得密，这里放大一档并压淡底色，
 * 深色模式下底色稍重一点，避免整片糊在卡面上。
 */
@Composable
fun AgRic(tint: Color, icon: ImageVector, size: Dp = 40.dp) {
    val bgAlpha = Ag.ricAlpha(tint) * (if (Ag.isDark) 1.5f else 1f)
    Box(
        Modifier
            .size(size)
            .clip(RoundedCornerShape(14.dp))
            .background(tint.copy(alpha = bgAlpha))
            .border(1.dp, tint.copy(alpha = if (Ag.isDark) 0.10f else 0.06f), RoundedCornerShape(14.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(18.dp))
    }
}

/** 顶栏 40dp 圆角玻璃图标按钮 .icbtn（18px 描边图标） */
@Composable
fun AgIconBtn(icon: ImageVector, onClick: () -> Unit, tint: Color = Ag.Tx2, modifier: Modifier = Modifier) {
    Box(
        modifier
            .size(40.dp)
            .agClick(onClick = onClick)
            .clip(RoundedCornerShape(14.dp))
            .background(Ag.Card)
            .border(1.dp, Ag.Line, RoundedCornerShape(14.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(18.dp))
    }
}

/** 品牌渐变主标题：32sp/800，em 部分渐变 */
@Composable
fun AgTitle(
    plain: String,
    em: String,
    modifier: Modifier = Modifier,
    count: String? = null,
    topMargin: Dp = 24.dp,
) {
    Row(
        modifier
            .fillMaxWidth()
            .padding(start = 4.dp, end = 4.dp, top = topMargin, bottom = 16.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        Text(
            buildString { append(plain); append(em) },
            fontSize = 32.sp,
            fontWeight = FontWeight.ExtraBold,
            letterSpacing = (-0.6).sp,
            // 自定义标题色优先，没设就跟随全局强调色（superuser / module / 主页一致）
            style = me.diksu.ultra.foxsu.ui.theme.TitleInk.style(),
        )
        if (count != null) {
            Spacer(Modifier.width(8.dp))
            Text(count, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Ag.Tx3, modifier = Modifier.padding(bottom = 6.dp))
        }
    }
}

/** 呼吸点徽标 .badge */
@Composable
fun AgBadge(text: String) {
    val t = rememberInfiniteTransition(label = "ag-badge")
    val p by t.animateFloat(0f, 1f, infiniteRepeatable(tween(2000), RepeatMode.Restart), label = "dot")
    Row(
        Modifier
            .clip(RoundedCornerShape(99.dp))
            .background(Color(0x1A10B981))
            .padding(horizontal = 12.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(7.dp)
                .graphicsLayer { shadowElevation = 0f }
                .background(Ag.Success, CircleShape)
                .border((2 + 3 * p).dp, Color(0x0D10B981), CircleShape),
        )
        Spacer(Modifier.width(6.dp))
        Text(text, fontSize = 12.5.sp, fontWeight = FontWeight.Bold, color = Color(0xFF0E9F6E))
    }
}

/** 信息胶囊 .chip */
@Composable
fun AgChip(text: String) {
    Box(
        Modifier
            .clip(RoundedCornerShape(99.dp))
            .background(Ag.TintInk)
            .border(1.dp, Ag.Line, RoundedCornerShape(99.dp))
            .padding(horizontal = 11.dp, vertical = 5.dp),
    ) {
        Text(text, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Ag.Tx2)
    }
}

/** 主/次操作按钮 .gbtn */
@Composable
fun AgButton(
    primary: Boolean,
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    particleEdge: Boolean = false,
) {
    val shape = RoundedCornerShape(12.dp)
    Box(
        modifier
            .height(38.dp)
            .particleOutline(
                shape = shape,
                bleed = if (enabled && particleEdge) 9.dp else 0.dp,
                accent = Ag.Vio,
                running = enabled && particleEdge,
            )
            .agClick(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (primary) {
            // 背景 + 阴影放在独立的裁剪层：粒子要飘出按钮外，根 Box 不能整体 clip
            Box(
                Modifier
                    .matchParentSize()
                    .shadow(6.dp, shape, spotColor = Ag.Vio.copy(alpha = 0.302f), ambientColor = Ag.Vio.copy(alpha = 0.302f))
                    .clip(shape)
                    .background(Ag.Grad, shape)
            )
            // 静态斜向高光（原来是一条 2.6s 循环流动的高光带：每一次循环都要重绘整颗按钮，
            //  在列表页里几十个按钮同时跑就是纯粹的耗电，肉眼观感几乎没有区别）
            Box(
                Modifier
                    .matchParentSize()
                    .clip(shape)
                    .background(
                        Brush.linearGradient(
                            0f to Color.White.copy(alpha = 0.18f),
                            0.45f to Color.Transparent,
                            1f to Color.White.copy(alpha = 0.07f),
                        ),
                    ),
            )
        } else {
            Box(
                Modifier
                    .matchParentSize()
                    .background(Ag.Vio.copy(alpha = 0.071f), shape)
                    .border(1.dp, Ag.Vio.copy(alpha = 0.251f), shape)
            )
        }
        Text(text, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = if (primary) Color.White else Ag.Vio)
    }
}

/** 渐变开关 .tgl（52×30 / sm 46×27） */
@Composable
fun AgToggle(checked: Boolean, onCheckedChange: () -> Unit, small: Boolean = false) {
    val w = if (small) 46.dp else 52.dp
    val h = if (small) 27.dp else 30.dp
    val knob = if (small) 21.dp else 24.dp
    val pad = 3.dp
    val travel = w - knob - pad * 2
    val off by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (checked) 1f else 0f,
        animationSpec = tween(250),
        label = "ag-tgl",
    )
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val press by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (pressed) 0.94f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium,
        ),
        label = "ag-tgl-press",
    )
    Box(
        Modifier
            .width(w)
            .height(h)
            .graphicsLayer { scaleX = press; scaleY = press }
            .clickable(interactionSource = src, indication = null, onClick = onCheckedChange)
            .clip(RoundedCornerShape(99.dp))
            .background(if (checked) Ag.Grad else androidx.compose.ui.graphics.SolidColor(Ag.ToggleOff)),
        contentAlignment = Alignment.CenterStart,
    ) {
        Box(
            Modifier
                .graphicsLayer { translationX = travel.toPx() * off }
                .padding(start = pad)
                .size(knob)
                .shadow(2.dp, CircleShape)
                .background(Color.White, CircleShape),
        )
    }
}

/** 搜索框 .search */
@Composable
fun AgSearch(value: String, onValueChange: (String) -> Unit, hint: String) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(46.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(Ag.searchBg())
            .border(1.dp, Ag.Line, RoundedCornerShape(16.dp))
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Rounded.Search, null, tint = Ag.Tx3, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(10.dp))
        androidx.compose.foundation.text.BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            textStyle = androidx.compose.ui.text.TextStyle(fontSize = 14.sp, color = Ag.Tx, fontWeight = FontWeight.Medium),
            modifier = Modifier.weight(1f),
            decorationBox = { inner ->
                Box {
                    if (value.isEmpty()) {
                        Text(hint, fontSize = 14.sp, color = Ag.Tx3)
                    }
                    inner()
                }
            },
        )
        if (value.isNotEmpty()) {
            Icon(
                Icons.Rounded.Close, null, tint = Ag.Tx3,
                modifier = Modifier
                    .size(16.dp)
                    .agClick { onValueChange("") },
            )
        }
    }
}

/** 小节标题 .ghead */
@Composable
fun AgGHead(text: String) {
    Text(
        text,
        fontSize = 12.sp,
        fontWeight = FontWeight.ExtraBold,
        letterSpacing = 1.sp,
        color = Ag.Tx3,
        modifier = Modifier.padding(start = 6.dp, top = 20.dp, bottom = 10.dp),
    )
}

/** 品牌渐变 FAB .fab（模块页右下角「刷入 zip」） */
@Composable
fun AgFab(onClick: () -> Unit, particleEdge: Boolean = true) {
    Box(
        Modifier
            .size(58.dp)
            .particleOutline(
                shape = RoundedCornerShape(20.dp),
                accent = Ag.Vio,
                bleed = if (particleEdge) 11.dp else 0.dp,
                running = particleEdge,
            )
            .agClick(pressScale = 0.9f, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        // 底与投影放子层并裁剪，粒子才能飘到外面去
        Box(
            Modifier
                .matchParentSize()
                .shadow(12.dp, RoundedCornerShape(20.dp), spotColor = Ag.Vio.copy(alpha = 0.4f))
                .clip(RoundedCornerShape(20.dp))
                .background(Ag.Grad),
        )
        Icon(Icons.Rounded.Add, null, tint = Color.White, modifier = Modifier.size(28.dp))
    }
}

/** 包名 → 稳定渐变色对（App 图标底衬，取自设计稿配色） */
fun gradientFor(seed: String): Pair<Color, Color> {
    val palettes = listOf(
        Color(0xFF22C1F2) to Color(0xFF1D7AF3),
        Color(0xFF2DD4BF) to Color(0xFF0D9488),
        Color(0xFF64748B) to Color(0xFF2B3648),
        Color(0xFF111827) to Color(0xFF1E3A8A),
        Color(0xFFA78BFA) to Color(0xFF7C3AED),
        Ag.Vio.copy(alpha = 1.0f) to Color(0xFFEC4899),
        Color(0xFFF59E0B) to Color(0xFFF97316),
        Color(0xFF10B981) to Color(0xFF34D399),
    )
    return palettes[kotlin.math.abs(seed.hashCode()) % palettes.size]
}
