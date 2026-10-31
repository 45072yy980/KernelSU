package me.diksu.ultra.foxsu.ui.screen.ag

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import me.diksu.ultra.foxsu.ui.theme.BgPalette
import me.diksu.ultra.foxsu.ui.theme.BgThemes

/**
 * 背景色选择器（内联版）：皮肤默认 + 20 套 Theme Kit 配色，左右滑动即切换。
 *
 * 一张卡里**左亮右暗**并排，是这里最关键的设计：主题包每套都有独立的亮色版和暗色版，
 * 而且暗色版不是反色（暖米色的暗版是暖棕底，雾灰的暗版是冷灰底）。
 * 如果只画当前模式那一版，用户切到深色会以为自己换了一套主题；
 * 并排摆出来，「这套在两档明暗下各长什么样」一眼看完，也不用点第二次。
 *
 * 两半都用当前强调色画按钮：强调色不在主题包里，是另一条正交的设置，
 * 预览里当然要按它现在真实的样子显示。
 */
@Composable
internal fun AgBgColorPicker(
    style: Int,
    onPick: (Int) -> Unit,
) {
    val dark = Ag.isDark
    AgSwipePicker(
        count = BgThemes.size + 1,
        initial = style,
        pageHeight = 188.dp,
        selected = style,
        onSettle = onPick,
        label = { index ->
            "%02d / %d · %s".format(index + 1, BgThemes.size + 1, bgTitle(index))
        },
        hint = { index ->
            if (index == 0) {
                "皮肤自带的紫雾配色，亮 / 暗各一套"
            } else {
                "亮色 / 暗色各一版 · 当前用${if (dark) "暗色" else "亮色"}版"
            }
        },
    ) { index, selected ->
        BgThemeStage(page = index, selected = selected)
    }
}

private fun bgTitle(page: Int): String =
    if (page == 0) "皮肤默认" else BgThemes[page - 1].name

/** 一页舞台：左亮右暗两半并排 + 顶部标签 + 底部名称。 */
@Composable
private fun BgThemeStage(page: Int, selected: Boolean) {
    val theme = BgThemes.getOrNull(page - 1)
    val light = theme?.light ?: DEFAULT_LIGHT
    val dark = theme?.dark ?: DEFAULT_DARK

    Box(Modifier.pickerStage(selected)) {
        Row(Modifier.fillMaxSize()) {
            MiniPanel(light, Modifier.weight(1f))
            MiniPanel(dark, Modifier.weight(1f))
        }

        // 中线：两半分界太糊的话，会看成一张渐变卡而不是两种模式
        Box(
            Modifier
                .align(Alignment.Center)
                .width(1.dp)
                .fillMaxHeight()
                .background(Color(0x33FFFFFF)),
        )

        // 「亮色 / 暗色」标签：这是整套选择器的语义核心，必须常驻而不是只在页脚提一句
        Row(
            Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .padding(9.dp),
        ) {
            ModeTag("亮色", Modifier.weight(1f), Alignment.CenterStart)
            ModeTag("暗色", Modifier.weight(1f), Alignment.CenterEnd)
        }

        // 卡内不再重复写名字：下面那行已经有「03 / 21 · 薄荷」，
        // 再压一条深色渐变只会挡住两半底部的色块，正是这张卡想给人看的东西
    }
}

/** 半页微缩界面：底色渐变 → 标题字 → 一张卡（标题条、正文条、强调按钮）→ 一排次级块。 */
@Composable
private fun MiniPanel(p: BgPalette, modifier: Modifier) {
    Column(
        modifier
            .fillMaxHeight()
            .background(Brush.linearGradient(listOf(p.bg, p.bg2)))
            .padding(start = 11.dp, end = 11.dp, top = 27.dp, bottom = 12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            "Aa",
            fontSize = 16.sp,
            fontWeight = FontWeight.ExtraBold,
            color = p.text,
        )
        Box(
            Modifier
                .fillMaxWidth()
                .weight(1f)
                .clip(RoundedCornerShape(10.dp))
                .background(p.surface)
                .padding(9.dp),
        ) {
            Column(Modifier.fillMaxSize()) {
                Box(
                    Modifier
                        .fillMaxWidth(0.62f)
                        .height(5.dp)
                        .clip(CircleShape)
                        .background(p.text.copy(alpha = 0.78f)),
                )
                Spacer(Modifier.height(6.dp))
                Box(
                    Modifier
                        .fillMaxWidth(0.92f)
                        .height(4.dp)
                        .clip(CircleShape)
                        .background(p.text2.copy(alpha = 0.55f)),
                )
                Spacer(Modifier.height(4.dp))
                Box(
                    Modifier
                        .fillMaxWidth(0.54f)
                        .height(4.dp)
                        .clip(CircleShape)
                        .background(p.text2.copy(alpha = 0.38f)),
                )
                Spacer(Modifier.weight(1f))
                Box(
                    Modifier
                        .width(50.dp)
                        .height(16.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Ag.Vio),
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            repeat(3) {
                Box(
                    Modifier
                        .weight(1f)
                        .height(11.dp)
                        .clip(RoundedCornerShape(5.dp))
                        .background(p.surface2),
                )
            }
        }
    }
}

@Composable
private fun ModeTag(
    text: String,
    modifier: Modifier,
    align: Alignment,
) {
    Box(modifier, contentAlignment = align) {
        Box(
            Modifier
                .clip(RoundedCornerShape(6.dp))
                .background(Color(0x59000000))
                .padding(horizontal = 6.dp, vertical = 2.dp),
        ) {
            Text(text, fontSize = 8.5.sp, fontWeight = FontWeight.Bold, color = Color.White)
        }
    }
}

/** 「皮肤默认」的亮色版：与 Ag.Bg1/Bg2/Card 的原始取值一致。 */
private val DEFAULT_LIGHT = BgPalette(
    bg = Color(0xFFF7F6FB),
    bg2 = Color(0xFFF2F4FA),
    surface = Color(0xFFFFFFFF),
    surface2 = Color(0xFFF2F4FA),
    line = Color(0x121E1946),
    text = Color(0xFF191826),
    text2 = Color(0xFF66647A),
    glow = Color(0x24A855F7),
)

/** 「皮肤默认」的暗色版。 */
private val DEFAULT_DARK = BgPalette(
    bg = Color(0xFF131221),
    bg2 = Color(0xFF1A192E),
    surface = Color(0xFF1E1D30),
    surface2 = Color(0xFF24233A),
    line = Color(0x17FFFFFF),
    text = Color(0xFFF2F1FA),
    text2 = Color(0xFFB2B0CC),
    glow = Color(0x0FA855F7),
)
