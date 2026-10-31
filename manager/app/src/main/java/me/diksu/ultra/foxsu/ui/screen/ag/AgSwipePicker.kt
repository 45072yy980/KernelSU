package me.diksu.ultra.foxsu.ui.screen.ag

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

/**
 * 内联横向选择器的通用骨架：一页一项、整宽、滑到哪算哪。
 *
 * 背景特效（21 套）与背景色（21 套）都长这样，抽出来只留一份翻页逻辑：
 * 左右箭头、`settledPage` 落定才写设置、外部改动同步回来、页码与说明行。
 *
 * 为什么写 `settledPage` 而不是 `currentPage`：后者过页一半就变，
 * 一次快滑会连着写十几次设置，主页那边跟着重开十几次特效。
 *
 * 为什么不做二级页：网格缩略图逼用户先记住「第 7 套长什么样」再退回去比对；
 * 一屏一项、跟真机同比例，滑一下看下一项才是挑壁纸的手感。
 */
@Composable
internal fun AgSwipePicker(
    count: Int,
    initial: Int,
    pageHeight: Dp,
    selected: Int,
    onSettle: (Int) -> Unit,
    label: (Int) -> String,
    hint: (Int) -> String,
    page: @Composable (index: Int, isSelected: Boolean) -> Unit,
) {
    val pagerState = rememberPagerState(initialPage = initial, pageCount = { count })
    val settle by rememberUpdatedState(onSettle)
    val scope = rememberCoroutineScope()

    // 外部改了选择（重置、旧键值继承）时把 pager 拉回对应页；
    // 正在滑动时不打断，否则手指还在屏幕上就被拽回去
    LaunchedEffect(initial) {
        if (!pagerState.isScrollInProgress && pagerState.currentPage != initial) {
            pagerState.scrollToPage(initial)
        }
    }

    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.settledPage }.collect { index -> settle(index) }
    }

    Column {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .fillMaxWidth()
                .height(pageHeight),
            pageSpacing = 10.dp,
        ) { index ->
            page(index, index == selected)
        }

        Spacer(Modifier.height(9.dp))

        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PickerArrow("‹", enabled = pagerState.currentPage > 0) {
                scope.launch { pagerState.animateScrollToPage(pagerState.currentPage - 1) }
            }
            Column(
                Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    label(pagerState.currentPage),
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = if (pagerState.currentPage == selected) Ag.Vio else Ag.Tx,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    hint(pagerState.currentPage),
                    fontSize = 10.5.sp,
                    color = Ag.Tx3,
                    maxLines = 1,
                )
            }
            PickerArrow("›", enabled = pagerState.currentPage < count - 1) {
                scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) }
            }
        }
    }
}

/** 左右翻页的小方钮：二十来项全滑一遍太累，留一个精确到「下一项」的入口。 */
@Composable
private fun PickerArrow(glyph: String, enabled: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .size(32.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(if (enabled) Ag.Vio.copy(alpha = 0.10f) else Color.Transparent)
            .border(
                1.dp,
                if (enabled) Ag.Vio.copy(alpha = 0.30f) else Ag.Line.copy(alpha = 0.5f),
                RoundedCornerShape(10.dp),
            )
            .agClick(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            glyph,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            color = if (enabled) Ag.Tx else Ag.Tx3.copy(alpha = 0.45f),
        )
    }
}

/** 舞台卡片的外框：圆角 + 细边，选中时换成强调色描边。 */
@Composable
internal fun Modifier.pickerStage(selected: Boolean, radius: Dp = 16.dp): Modifier {
    val shape = RoundedCornerShape(radius)
    return this
        .fillMaxSize()
        .clip(shape)
        .border(
            if (selected) 1.6.dp else 1.dp,
            if (selected) Ag.Vio.copy(alpha = 0.55f) else Ag.Line,
            shape,
        )
}
