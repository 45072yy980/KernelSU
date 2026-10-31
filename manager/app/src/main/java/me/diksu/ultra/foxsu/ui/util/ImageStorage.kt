package me.diksu.ultra.foxsu.ui.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import java.io.File
import java.io.FileOutputStream

/**
 * Copies an image picked through the photo picker into the app's private storage, downscaled so
 * it stays cheap to decode later. Returns the absolute path, or null when the stream is unreadable.
 */
fun savePickedImage(
    context: Context,
    uri: Uri,
    fileName: String,
    maxSide: Int = 2000,
    quality: Int = 92,
): String? = runCatching {
    val bitmap = context.contentResolver.openInputStream(uri)?.use {
        BitmapFactory.decodeStream(it)
    } ?: return null

    val scaled = if (bitmap.width > maxSide || bitmap.height > maxSide) {
        val ratio = maxSide.toFloat() / maxOf(bitmap.width, bitmap.height)
        Bitmap.createScaledBitmap(
            bitmap,
            (bitmap.width * ratio).toInt().coerceAtLeast(1),
            (bitmap.height * ratio).toInt().coerceAtLeast(1),
            true,
        )
    } else {
        bitmap
    }

    val file = File(context.filesDir, fileName)
    FileOutputStream(file).use { out ->
        scaled.compress(Bitmap.CompressFormat.JPEG, quality, out)
    }
    if (scaled !== bitmap) scaled.recycle()
    bitmap.recycle()
    file.absolutePath
}.getOrNull()

/** Writes an already decoded bitmap into the app's private storage as a JPEG. */
fun saveCroppedBitmap(
    context: Context,
    bitmap: Bitmap,
    fileName: String,
    quality: Int = 92,
): String? = runCatching {
    val file = File(context.filesDir, fileName)
    FileOutputStream(file).use { out ->
        bitmap.compress(Bitmap.CompressFormat.JPEG, quality, out)
    }
    file.absolutePath
}.getOrNull()

/**
 * 把用户从系统文件选择器挑的字体（ttf/otf）复制到应用私有目录。
 * 复制一份可以避免原文件被删除或权限失效导致下次启动字体丢失。
 */
fun importFontFile(context: Context, uri: Uri): String? = runCatching {
    val dir = File(context.filesDir, "fonts").apply { mkdirs() }
    // 清掉之前导入的字体，避免堆积
    dir.listFiles()?.forEach { it.delete() }
    val file = File(dir, "custom.ttf")
    context.contentResolver.openInputStream(uri)?.use { input ->
        file.outputStream().use { output -> input.copyTo(output) }
    } ?: return null
    if (file.length() < 1024L) {
        file.delete()
        return null
    }
    file.absolutePath
}.getOrNull()
