package me.diksu.ultra.foxsu.ui.screen.ag

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import me.diksu.ultra.foxsu.ui.component.ambient.AmbientPawsLayer
import me.diksu.ultra.foxsu.ui.component.ambient.BackgroundFxLayer
import me.diksu.ultra.foxsu.ui.component.ambient.FX_NAMES

/**
 * 背景特效选择器（内联版）：猫爪 + 20 套整屏特效，左右滑动即切换。
 *
 * 为什么不再单独一页：21 张缩略图排成两列网格，用户得先记住「第 7 套长什么样」
 * 再退回去看主页，来回比对。滑动选择器一屏只放一套、占满宽度，就是主页的真实比例，
 * 滑一下直接看下一套——这才是「挑一张壁纸」的手感。
 *
 * 预览一律用真实渲染器（[BackgroundFxLayer] / [AmbientPawsLayer]），不是另画示意图：
 * 示意图和真机对不上是这类选择器最常见的坑。Pager 默认只组合当前页，
 * 所以同一时刻只有一套在逐帧绘制，滑动离开的那套协程随组合取消，不留在后台。
 */
@Composable
internal fun AgBgFxPicker(
    style: Int,
    onPick: (Int) -> Unit,
) {
    AgSwipePicker(
        count = FX_NAMES.size + 1,
        initial = style,
        pageHeight = 196.dp,
        selected = style,
        onSettle = onPick,
        label = { index ->
            "%02d / %d · %s".format(index + 1, FX_NAMES.size + 1, fxTitle(index))
        },
        hint = { index -> fxHint(index) },
    ) { index, selected ->
        BgFxStage(page = index, selected = selected)
    }
}

/** 一页舞台：真实渲染铺满 + 底部一条渐隐条承载名字，浅色卡面上也读得清。 */
@Composable
private fun BgFxStage(page: Int, selected: Boolean) {
    val previewBg = if (Ag.isDark) Color(0xFF171625) else Color(0xFFEFEDF7)
    Box(
        Modifier
            .pickerStage(selected)
            .background(previewBg),
    ) {
        if (page == 0) {
            AmbientPawsLayer(enabled = true, count = 9, alphaScale = 0.95f)
        } else {
            // 不传 alphaScale，用主页同一个默认值：预览浓一点会让用户以为真机也这么亮
            BackgroundFxLayer(
                style = page,
                enabled = true,
            )
        }

        // 底部渐隐：名字压在特效上，没有这层的话雨滴、星芒会从字里穿过去
        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(54.dp)
                .background(
                    Brush.verticalGradient(
                        listOf(Color.Transparent, Color(0x8A000000), Color(0xC4000000)),
                    )
                ),
        )

        Text(
            fxTitle(page),
            fontSize = 14.sp,
            fontWeight = FontWeight.ExtraBold,
            color = Color.White,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 14.dp, bottom = 11.dp),
        )

        Text(
            "第 %d 套".format(page + 1),
            fontSize = 9.5.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xB3FFFFFF),
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(11.dp),
        )
    }
}

private fun fxTitle(page: Int): String = if (page == 0) "猫爪氛围" else FX_NAMES[page - 1]

/** 一句话说明这套在干什么，比名字更好认——「光晕环旋」和「同心声波」光看名字分不清。 */
private fun fxHint(page: Int): String = when (page) {
    0 -> "小爪印与爱心从底部升起，最轻的一套"
    1 -> "三团大柔光各自呼吸，整屏缓慢明暗"
    2 -> "十六颗微尘自底部升起，边升边飘"
    3 -> "三条斜向光带浮动，像夜空极光"
    4 -> "同心圆环从画面中心向外扩散"
    5 -> "方格网整体呼吸缩放"
    6 -> "一条光带每几秒斜扫过一次"
    7 -> "两团大雾反向漂移，有纵深感"
    8 -> "光点之间虚线相连，线缓慢流动"
    9 -> "三个色团游走交叠，颜色自然叠深"
    10 -> "细雨斜落，只在中间段可见"
    11 -> "十字星明灭，亮起时旋转四十五度"
    12 -> "三层圆环反向旋转，各挂一颗光点"
    13 -> "主色副色光斑游走，色相缓慢过渡"
    14 -> "柔光声呐从上方一圈圈推开"
    15 -> "花瓣旋转着斜落，左右摆幅不同"
    16 -> "斜纹整体平移，上亮下暗"
    17 -> "两团大柔光错位游走，模拟纵深光照"
    18 -> "上下两端交替明暗，像深呼吸"
    19 -> "二十六颗微尘绕中心画螺旋"
    20 -> "底部暖光起伏，像日出日落"
    else -> ""
}
