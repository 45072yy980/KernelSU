package me.weishu.kernelsu.ui.component.miuix

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.blur
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import me.weishu.kernelsu.ui.BackgroundDim
import me.weishu.kernelsu.ui.PanelMetrics
import kotlin.math.roundToInt

/** 模糊半径：8dp 会把照片糊成一片色块，16dp 还看得出是同一张图。 */
private val PaneBlur = 16.dp

/**
 * 卡片自己那层压暗。画面上已经按 [BackgroundDim] 压过一次，这里再压一点，
 * 卡片才能从背景里“浮”出来——不然把背景暗度调到 0 时，卡片会和壁纸同亮度、
 * 只剩文字在上面飘着。
 */
private const val CardTint = 0.20f

/**
 * 磨砂卡片底板：把页面那张壁纸取卡片所在的一块，模糊、压暗，垫在卡片内容下面。
 *
 * 三条规矩，都踩过坑：
 *
 * 1. **按面板原尺寸 1:1 画。** 页面上的壁纸就是按面板尺寸 Crop 的，尺寸一致才逐像素
 *    对得上。以前这里把宽高各加 96px 再横向 -48 偏移，等于把图放大约 9% 再用经验值
 *    补回来，永远补不齐，卡片边沿会露出一条没压暗的底。
 * 2. **偏移只用坐标差。** 面板原点在窗口里的位置减去卡片原点，就是图片该平移的量；
 *    不再叠“微调”常量——那套调参是给旧画法擦屁股的，画法一改就全是错的。
 * 3. **画在卡片里面。** 按压反馈是 3D 旋转，磨砂面必须跟着卡片一起转；把它放在
 *    Card 外面当静态底板，玻璃就会原地不动、和卡片明显脱节。
 *
 * 壁纸为空（没设壁纸、或开关关着）时什么都不画，调用方自己决定退回什么底色。
 */
@Composable
fun BoxScope.FrostedCardBackground(
    shape: Shape,
    cardPos: Offset,
    wallpaper: ImageBitmap?,
    modifier: Modifier = Modifier,
) {
    if (wallpaper == null) return
    val panelSize = PanelMetrics.size.value
    val panelPos = PanelMetrics.pos.value
    if (panelSize == IntSize.Zero) return

    val density = LocalDensity.current
    Box(
        modifier = modifier
            .matchParentSize()
            .clip(shape),
    ) {
        Box(
            modifier = Modifier
                .requiredSize(
                    with(density) { panelSize.width.toDp() },
                    with(density) { panelSize.height.toDp() },
                )
                .offset {
                    IntOffset(
                        panelPos.x.roundToInt() - cardPos.x.roundToInt(),
                        panelPos.y.roundToInt() - cardPos.y.roundToInt(),
                    )
                },
        ) {
            Image(
                bitmap = wallpaper,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .blur(PaneBlur),
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Color.Black.copy(
                            alpha = (BackgroundDim.value.floatValue + CardTint).coerceAtMost(1f),
                        )
                    ),
            )
        }
    }
}
