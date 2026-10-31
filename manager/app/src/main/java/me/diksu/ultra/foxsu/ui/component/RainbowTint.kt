package me.diksu.ultra.foxsu.ui.component

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import me.diksu.ultra.foxsu.data.settings.CustomizationStore
import me.diksu.ultra.foxsu.ui.theme.LaunchTint

/**
 * 模块卡的七彩取色。
 *
 * 每张卡片拿 [seed]（通常是模块 id 的 hash）去取一套颜色，所以：
 * - 同一个模块在自己的生命周期里颜色稳定，不会每次重组都跳色；
 * - 不同模块之间尽量错开，整页看起来就是七彩的；
 * - [LaunchTint] 每次冷启动会重置 seed，重启后整套颜色重新随机。
 */
fun rainbowSlotFor(seed: Int): Int {
    // 用乘法散列把相邻的 id 打散，避免连着几个模块同色
    val mixed = seed * -0x61c88647
    return (mixed ushr 7) and 0x7fffffff
}

/** 返回 (容器色, 内容色)；关闭七彩 / 玻璃卡 / 铺壁纸时返回 null，交给调用方走原配色。 */
@Composable
fun rainbowTintFor(slot: Int): Pair<Color, Color>? {
    val custom = CustomizationStore.current
    if (!custom.rainbowCards || custom.cardGlass) return null
    if (hasWallpaperBackdrop()) return null
    val night = isNightScheme()
    val pair = if (night) LaunchTint.rainbowPairDark(slot) else LaunchTint.rainbowPair(slot)
    val alpha = if (night) 0.92f else 0.9f
    return Color(pair.first).copy(alpha = alpha) to Color(pair.second)
}

/** 图标色：与卡片同一套七彩，只取更饱和的那个做前景。 */
@Composable
fun rainbowAccentFor(slot: Int): Color? = rainbowTintFor(slot)?.second

/** 按模块 id 稳定地记住它的七彩槽位。 */
@Composable
fun rememberRainbowSlot(id: String): Int = remember(id) { rainbowSlotFor(id.hashCode()) }
