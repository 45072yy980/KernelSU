package me.diksu.ultra.foxsu.ui.component

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.draw.blur
import androidx.compose.ui.unit.dp
import me.diksu.ultra.ksuApp
import androidx.compose.runtime.Composable
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import me.diksu.ultra.foxsu.data.settings.Customization
import me.diksu.ultra.foxsu.data.settings.CustomizationStore
import java.io.File

/** 壁纸平均亮度缓存：玻璃卡面的文字颜色靠它决定黑白。 */
private val luminanceCache = mutableMapOf<String, Float>()

/** 系统壁纸（玻璃模式下未自定义背景时使用）。 */
private var systemWallpaperCache: ImageBitmap? = null
private var systemWallpaperLoaded = false
private var systemWallpaperLuminanceCache = -1f

fun systemWallpaperBitmap(): ImageBitmap? {
    if (!systemWallpaperLoaded) {
        systemWallpaperLoaded = true
        systemWallpaperCache = runCatching {
            val wm = android.app.WallpaperManager.getInstance(ksuApp)
            val drawable = wm.drawable
            if (drawable is android.graphics.drawable.BitmapDrawable) {
                drawable.bitmap.asImageBitmap()
            } else if (drawable != null) {
                val w = drawable.intrinsicWidth.coerceAtLeast(1)
                val h = drawable.intrinsicHeight.coerceAtLeast(1)
                val bmp = android.graphics.Bitmap.createBitmap(w, h, android.graphics.Bitmap.Config.ARGB_8888)
                val canvas = android.graphics.Canvas(bmp)
                drawable.setBounds(0, 0, w, h)
                drawable.draw(canvas)
                bmp.asImageBitmap()
            } else {
                null
            }
        }.getOrNull()
        systemWallpaperLuminanceCache = systemWallpaperCache?.let { bmp ->
            val pixels = bmp.toPixelMap()
            var sum = 0.0
            var count = 0
            val stepX = (bmp.width / 24).coerceAtLeast(1)
            val stepY = (bmp.height / 24).coerceAtLeast(1)
            var y = 0
            while (y < bmp.height) {
                var x = 0
                while (x < bmp.width) {
                    val c = pixels[x, y]
                    sum += (0.2126f * c.red + 0.7152f * c.green + 0.0722f * c.blue)
                    count++
                    x += stepX
                }
                y += stepY
            }
            if (count == 0) 1f else (sum / count).toFloat()
        } ?: -1f
    }
    return systemWallpaperCache
}

@Composable
fun rememberSystemWallpaper(): ImageBitmap? = remember { systemWallpaperBitmap() }

/** 玻璃模式的兜底壁纸：内置的日落图，保证任何设备都有背景可用。 */
@Composable
fun rememberDefaultGlassWallpaper(): ImageBitmap? = remember {
    runCatching {
        val res = ksuApp.resources
        val opts = android.graphics.BitmapFactory.Options().apply { inSampleSize = 2 }
        android.graphics.BitmapFactory.decodeResource(res, me.diksu.ultra.R.drawable.fx_glass_default, opts)
            ?.asImageBitmap()
    }.getOrNull()
}

/** 内置兜底壁纸的平均亮度，用于决定文字黑白（缓存一次）。 */
private var bundledLuminanceCache = -1f

private fun bundledGlassLuminance(): Float {
    if (bundledLuminanceCache >= 0f) return bundledLuminanceCache
    bundledLuminanceCache = runCatching {
        val opts = BitmapFactory.Options().apply { inSampleSize = 8 }
        val bmp = BitmapFactory.decodeResource(
            ksuApp.resources,
            me.diksu.ultra.R.drawable.fx_glass_default,
            opts,
        ) ?: return@runCatching -1f
        var sum = 0.0
        var count = 0
        val stepX = (bmp.width / 20).coerceAtLeast(1)
        val stepY = (bmp.height / 20).coerceAtLeast(1)
        var y = 0
        while (y < bmp.height) {
            var x = 0
            while (x < bmp.width) {
                val c = bmp.getPixel(x, y)
                val r = (c shr 16 and 0xFF) / 255f
                val gg = (c shr 8 and 0xFF) / 255f
                val bb = (c and 0xFF) / 255f
                sum += (0.2126f * r + 0.7152f * gg + 0.0722f * bb)
                count++
                x += stepX
            }
            y += stepY
        }
        bmp.recycle()
        if (count == 0) -1f else (sum / count).toFloat()
    }.getOrDefault(-1f)
    return bundledLuminanceCache
}

/** 玻璃模式下按优先级取背景：自定义玻璃壁纸 → 内置日落壁纸。 */
@Composable
fun rememberGlassBackdrop(): ImageBitmap? {
    val custom = CustomizationStore.current.glassWallpaper
    val customBitmap = rememberWallpaper(custom)
    val fallback = rememberDefaultGlassWallpaper()
    return customBitmap ?: fallback
}

/** 0f（纯黑）..1f（纯白），读取失败按亮色处理。 */
fun wallpaperLuminance(path: String?): Float {
    if (path == null) return 1f
    luminanceCache[path]?.let { return it }
    val value = runCatching {
        val opts = BitmapFactory.Options().apply { inSampleSize = 16 }
        val bmp = BitmapFactory.decodeFile(path, opts) ?: return@runCatching 1f
        var sum = 0.0
        var count = 0
        for (y in 0 until bmp.height step 2) {
            for (x in 0 until bmp.width step 2) {
                val c = bmp.getPixel(x, y)
                val r = (c shr 16 and 0xFF) / 255f
                val g = (c shr 8 and 0xFF) / 255f
                val b = (c and 0xFF) / 255f
                sum += (0.2126f * r + 0.7152f * g + 0.0722f * b)
                count++
            }
        }
        bmp.recycle()
        if (count == 0) 1f else (sum / count).toFloat()
    }.getOrDefault(1f)
    luminanceCache[path] = value
    return value
}

/** 玻璃卡面 + 深色壁纸（或夜间）时需要浅色文字。 */
@Composable
@ReadOnlyComposable
fun glassNeedsLightText(): Boolean {
    val custom = CustomizationStore.current
    if (!custom.cardGlass) return false
    // 自定义了不透明浅色卡片：文字按卡色走；其余情况玻璃统一是深色板 + 白字
    if (custom.cardColor != 0 && custom.cardAlpha >= 0.55f) {
        return Color(custom.cardColor).luminance() < 0.5f
    }
    return true
}

/** Loads the wallpaper with a sampled decode so a 4K photo does not blow up the heap. */
fun decodeWallpaper(path: String, maxSize: Int = 1600): ImageBitmap? = runCatching {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(path, bounds)
    var sample = 1
    while (bounds.outWidth / sample > maxSize || bounds.outHeight / sample > maxSize) {
        sample *= 2
    }
    val opts = BitmapFactory.Options().apply { inSampleSize = sample }
    BitmapFactory.decodeFile(path, opts)?.asImageBitmap()
}.getOrNull()

@Composable
fun rememberWallpaper(path: String?): ImageBitmap? = remember(path) {
    path?.takeIf { File(it).exists() }?.let { decodeWallpaper(it) }
}

/**
 * Draws the user wallpaper (zoom + vertical pan) and the dim scrim that keeps text readable.
 */
@Composable
fun WallpaperBackground(
    customization: Customization,
    modifier: Modifier = Modifier,
) {
    val customWallpaper = rememberWallpaper(customization.wallpaperUri)
    val glass = customization.cardGlass
    val bitmap = customWallpaper ?: if (glass) rememberGlassBackdrop() else null
    if (bitmap == null) return

    Box(modifier = modifier.fillMaxSize()) {
        Image(
            bitmap = bitmap,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxSize()
                .then(if (glass) Modifier.blur(72.dp) else Modifier)
                .graphicsLayer {
                    val scale = customization.wallpaperScale
                    scaleX = scale
                    scaleY = scale
                    translationX = customization.wallpaperOffsetX * size.width
                    translationY = customization.wallpaperOffsetY * size.height
                },
        )
        // 玻璃模式：整体暗雾 + 顶部更强的渐变，保证状态栏/标题区的白字清晰
        if (glass) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.22f)),
            )
            // 顶部压暗：标题栏与背景同材质后，靠这层渐变保证头部白字依然清晰
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            0f to Color.Black.copy(alpha = 0.52f),
                            0.22f to Color.Black.copy(alpha = 0.20f),
                            0.40f to Color.Transparent,
                        )
                    ),
            )
        }
        if (customization.wallpaperDim > 0f) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = customization.wallpaperDim)),
            )
        }
    }
}

/**
 * Paints the wallpaper (if any) behind [content]; used by every main screen so the chosen
 * background is not limited to the home tab.
 */
@Composable
fun WallpaperBackdrop(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val custom = CustomizationStore.current
    val globalWallpaper = custom.wallpaperUri != null && custom.wallpaperEnabled
    if (!globalWallpaper && !custom.cardGlass) {
        content()
        return
    }
    Box(modifier = modifier.fillMaxSize()) {
        if (globalWallpaper) {
            // 全局壁纸：全应用显示，保持清晰（不做模糊），叠一层薄雾让深色文字可读
            WallpaperBackground(customization = custom.copy(cardGlass = false))
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        if (isSystemInDarkTheme()) {
                            Color.Black.copy(alpha = 0.30f)
                        } else {
                            Color.White.copy(alpha = 0.20f)
                        }
                    ),
            )
        } else {
            // 玻璃模式的磨砂壁纸：自定义玻璃图优先，否则内置日落图
            WallpaperBackground(customization = custom.copy(wallpaperUri = custom.glassWallpaper))
        }
        content()
    }
}

/** True when the user picked a wallpaper, so scaffolds can go transparent. */
@Composable
@ReadOnlyComposable
fun hasCustomWallpaper(): Boolean {
    val custom = CustomizationStore.current
    val global = custom.wallpaperUri != null && custom.wallpaperEnabled
    return global || custom.cardGlass
}

/** Scaffold container that is transparent while a wallpaper is in use. */
@Composable
@ReadOnlyComposable
fun scaffoldBackgroundColor(fallback: Color): Color =
    if (hasCustomWallpaper()) Color.Transparent else fallback
