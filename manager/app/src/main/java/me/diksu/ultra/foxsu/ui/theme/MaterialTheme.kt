package me.diksu.ultra.foxsu.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.core.view.WindowInsetsControllerCompat
import me.diksu.ultra.foxsu.ui.webui.MonetColorsProvider
import me.diksu.ultra.foxsu.data.settings.CustomizationStore
import me.diksu.ultra.foxsu.ui.theme.rememberTypography

@Composable
fun MaterialKernelSUTheme(
    appSettings: AppSettings,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val systemDarkTheme = isSystemInDarkTheme()
    val darkTheme = appSettings.colorMode.isDark || (appSettings.colorMode.isSystem && systemDarkTheme)
    val amoledMode = appSettings.colorMode.isAmoled
    // 全局强调色（定制 → 全局强调色）优先于主题自带主色：
    // 7 套配色是品牌级选择，五套皮肤必须一致，否则换肤时按钮颜色会跳变。
    val accent = me.diksu.ultra.foxsu.ui.theme.currentAccent

    val colorScheme = rememberKernelSUColorScheme(
        seedColor = accent.primary,
        isDark = darkTheme,
        isAmoled = amoledMode,
        paletteStyle = appSettings.paletteStyle,
        colorSpec = appSettings.colorSpec,
    )

    LaunchedEffect(darkTheme) {
        val window = (context as? Activity)?.window ?: return@LaunchedEffect
        WindowInsetsControllerCompat(window, window.decorView).apply {
            isAppearanceLightStatusBars = !darkTheme
            isAppearanceLightNavigationBars = !darkTheme
        }
    }

    val animatedColorScheme = colorScheme.animateAsState()

    val typography = rememberTypography(
        CustomizationStore.current.fontStyle,
        CustomizationStore.current.fontPath,
    )

    MaterialExpressiveTheme(
        colorScheme = animatedColorScheme,
        motionScheme = MotionScheme.expressive(),
        typography = typography,
        content = {
            MonetColorsProvider.UpdateCss(colorScheme)
            // Compose 的 Text 默认取 LocalTextStyle，而 Material3 的 MaterialTheme 只提供
            // LocalTypography、并不设置 LocalTextStyle。于是「定制 → 字体」只对显式写了
            // MaterialTheme.typography.* 的组件生效，界面里大量裸 Text(...) 依旧走平台默认字，
            // 看起来就是字体开关没反应。这里把选中的字族写进默认文字样式，覆盖全部文字。
            CompositionLocalProvider(LocalTextStyle provides typography.defaultTextStyle) {
                content()
            }
        }
    )
}

/**
 * 默认文字样式：只换字族，字号/行高/颜色保持 Compose 原样，避免顺带改掉既有版式。
 * 没选字体时 fontFamily 为 null，等价于不覆盖。
 */
internal val Typography.defaultTextStyle: TextStyle
    get() = if (bodyMedium.fontFamily == null) {
        TextStyle.Default
    } else {
        TextStyle.Default.copy(fontFamily = bodyMedium.fontFamily)
    }
