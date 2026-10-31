package me.diksu.ultra.foxsu.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import me.diksu.ultra.foxsu.data.settings.CustomizationStore

/**
 * 页面标题墨色。
 *
 * 三处大标题（主页 / 超级用户 / 模块）共用同一套规则：
 *
 * - 定制 → 标题栏 里设过自定义色：三个标题都用这个色，保持用户的选择；
 * - 没设过：跟随全局强调色（accent 的 primary → secondary 渐变），
 *   换配色或「更多」里自定义取色时，三个标题会一起变。
 *
 * 之前只有主页接了 titleColor，而且没设色时用的是各皮肤写死的墨色，
 * 于是超级用户 / 模块的标题永远是黑的，跟全局配色完全脱节。
 */
object TitleInk {

    /** 用户设过的自定义标题色（ARGB，0 = 未设置）。 */
    @Composable
    fun customColor(): Int = CustomizationStore.current.titleColor

    /** 纯色标题：自定义优先，否则用调用方给的兜底色（一般是各皮肤的正文色）。 */
    @Composable
    fun solid(fallback: Color): Color {
        val c = CustomizationStore.current.titleColor
        return if (c != 0) Color(c) else fallback
    }

    /** 渐变标题：自定义时退化成单色渐变，否则 accent 双色渐变。 */
    @Composable
    fun brush(): Brush {
        val c = CustomizationStore.current.titleColor
        return if (c != 0) {
            Brush.linearGradient(listOf(Color(c), Color(c)))
        } else {
            accentBrush2(currentAccent)
        }
    }

    /** 直接给 Text(style = ...) 用的样式。 */
    @Composable
    fun style(): TextStyle {
        val c = CustomizationStore.current.titleColor
        return if (c != 0) {
            TextStyle(color = Color(c))
        } else {
            TextStyle(brush = accentBrush2(currentAccent))
        }
    }
}
