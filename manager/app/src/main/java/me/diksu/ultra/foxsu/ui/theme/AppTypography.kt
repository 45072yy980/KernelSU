package me.diksu.ultra.foxsu.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import java.io.File
import me.diksu.ultra.R

/**
 * 全局字体：
 * 0 系统默认 / 1 思源宋体 / 2 JetBrains 等宽 / 3 霞鹜文楷 / 4 用户导入的字体文件。
 * 内置字体随 APK 打包（res/font），中英文都有完整字形；
 * 除系统默认外，其余都基于同一套 Typography 只替换 fontFamily，字号行高不变。
 */
fun resolveFontFamily(style: Int, fontPath: String? = null): FontFamily? = when {
    // 文件被删、被清缓存清掉、或只写了一半时，Font() 不会立刻报错，
    // 而是拖到排版期才炸/静默回退——先做存在性与大小校验，退回系统字族。
    style == 4 && fontPath != null -> {
        val f = File(fontPath)
        if (f.isFile && f.length() > 1024L) {
            runCatching { FontFamily(Font(f)) }.getOrNull()
        } else {
            null
        }
    }
    style == 1 -> FontFamily(Font(R.font.fx_noto_serif_sc))
    style == 2 -> FontFamily(Font(R.font.fx_jetbrains_mono))
    style == 3 -> FontFamily(Font(R.font.fx_lxgw_wenkai))
    else -> null
}

fun typographyWithFont(style: Int, fontPath: String? = null): Typography {
    val family = resolveFontFamily(style, fontPath) ?: return Typography

    val base = Typography
    return Typography(
        displayLarge = base.displayLarge.copy(fontFamily = family),
        displayMedium = base.displayMedium.copy(fontFamily = family),
        displaySmall = base.displaySmall.copy(fontFamily = family),
        headlineLarge = base.headlineLarge.copy(fontFamily = family),
        headlineMedium = base.headlineMedium.copy(fontFamily = family),
        headlineSmall = base.headlineSmall.copy(fontFamily = family),
        titleLarge = base.titleLarge.copy(fontFamily = family),
        titleMedium = base.titleMedium.copy(fontFamily = family),
        titleSmall = base.titleSmall.copy(fontFamily = family),
        bodyLarge = base.bodyLarge.copy(fontFamily = family),
        bodyMedium = base.bodyMedium.copy(fontFamily = family),
        bodySmall = base.bodySmall.copy(fontFamily = family),
        labelLarge = base.labelLarge.copy(fontFamily = family),
        labelMedium = base.labelMedium.copy(fontFamily = family),
        labelSmall = base.labelSmall.copy(fontFamily = family),
    )
}

/** 主题里调用：字体文件只在路径变化时重新加载。 */
@Composable
fun rememberTypography(style: Int, fontPath: String?): Typography =
    remember(style, fontPath) { typographyWithFont(style, fontPath) }


/**
 * 玻璃模式的文字：不带任何阴影，保持干净清晰。
 * 可读性交给文字颜色本身（根据卡面明暗自动切换白字/墨字），
 * 不再靠投影去救被壁纸亮部吃掉的白字——那会让字发灰、发脏。
 */
fun Typography.withoutShadow(): Typography {
    fun TextStyle.c(): TextStyle = copy(shadow = null)
    return Typography(
        displayLarge = displayLarge.c(),
        displayMedium = displayMedium.c(),
        displaySmall = displaySmall.c(),
        headlineLarge = headlineLarge.c(),
        headlineMedium = headlineMedium.c(),
        headlineSmall = headlineSmall.c(),
        titleLarge = titleLarge.c(),
        titleMedium = titleMedium.c(),
        titleSmall = titleSmall.c(),
        bodyLarge = bodyLarge.c(),
        bodyMedium = bodyMedium.c(),
        bodySmall = bodySmall.c(),
        labelLarge = labelLarge.c(),
        labelMedium = labelMedium.c(),
        labelSmall = labelSmall.c(),
    )
}

/** 旧的投影版本：保留接口但统一去掉阴影，避免别处仍在加影子。 */
@Deprecated("Glass mode no longer uses text shadows", ReplaceWith("withoutShadow()"))
fun Typography.withSoftShadow(): Typography = withoutShadow()
