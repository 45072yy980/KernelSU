package me.weishu.kernelsu.ui.component.miuix

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import top.yukonga.miuix.kmp.blur.BlendColorEntry
import top.yukonga.miuix.kmp.blur.BlurColors
import top.yukonga.miuix.kmp.blur.LayerBackdrop
import top.yukonga.miuix.kmp.blur.textureBlur

/** 背景模糊半径。20 上下还看得出是同一张图，再大就糊成色块了。 */
private const val PaneBlurRadius = 20f

/**
 * 卡片自己那层压暗。页面上已经按背景暗度压过一次，这里再压一点，卡片才能从
 * 背景里浮出来——否则把背景暗度调到 0 时，卡片会和壁纸同亮度，只剩文字浮着。
 */
private const val CardTint = 0.20f

/**
 * 页面壁纸那一层被记录成的 backdrop。
 *
 * 卡片上的磨砂**不再自己按坐标去采样壁纸**：那条路要求「面板坐标 − 卡片坐标」
 * 和壁纸的裁剪方式完全一致，显示缩放、内边距、容器变换任何一处不同就会错位，
 * 错位的那块还会因为少压了一层暗而在背景上留下一个高亮块。改成记录整层背景后，
 * 位置由 Miuix 的 backdrop 自己做坐标变换，天然逐像素对齐。
 */
object PageBackdrop {
    val layer = mutableStateOf<LayerBackdrop?>(null)
}

/**
 * 磨砂卡片底板：取页面已经绘制好的背景层，模糊后垫在卡片下面。
 *
 * 在调用方的 Box 里使用，画在 Card 内部，这样按压反馈（倾斜/缩放）会带着它一起动。
 */
@Composable
fun BoxScope.FrostedCardBackground(
    shape: Shape,
    modifier: Modifier = Modifier,
) {
    val backdrop = PageBackdrop.layer.value ?: return
    Box(
        modifier = modifier
            .matchParentSize()
            .clip(shape)
            .textureBlur(
                backdrop = backdrop,
                shape = shape,
                blurRadius = PaneBlurRadius,
                colors = BlurColors(
                    blendColors = listOf(
                        BlendColorEntry(color = Color.Black.copy(alpha = CardTint)),
                    ),
                ),
                enabled = true,
            ),
    )
}
