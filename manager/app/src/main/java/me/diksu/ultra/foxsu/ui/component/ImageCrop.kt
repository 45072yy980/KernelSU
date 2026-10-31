package me.diksu.ultra.foxsu.ui.component

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.diksu.ultra.R
import me.diksu.ultra.foxsu.ui.util.saveCroppedBitmap
import java.io.File
import kotlin.math.max

/**
 * 卡面图片裁剪：拖动平移 + 双指/滑杆缩放，确认后按卡片比例裁出一张新图并落盘。
 * 结果是一个新文件，调用方只需记住返回的路径。
 */
@Composable
fun ImageCropDialog(
    sourcePath: String,
    aspectRatio: Float,
    outputName: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val bitmap = remember(sourcePath) {
        runCatching { BitmapFactory.decodeFile(sourcePath) }.getOrNull()
    }
    val imageBitmap = remember(bitmap) { bitmap?.asImageBitmap() }

    var zoom by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var boxSize by remember { mutableStateOf(IntSize.Zero) }
    var busy by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = { if (!busy) onDismiss() }) {
        Surface(shape = RoundedCornerShape(28.dp), color = freshCardColor()) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(
                    text = stringResource(R.string.fx_customization_crop_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.fx_customization_crop_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                )
                Spacer(Modifier.height(14.dp))

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(aspectRatio)
                        .clip(RoundedCornerShape(20.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                        .onSizeChanged { boxSize = it }
                        .pointerInput(Unit) {
                            detectTransformGestures { _, pan, gestureZoom, _ ->
                                zoom = (zoom * gestureZoom).coerceIn(1f, 4f)
                                offset += pan
                            }
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    if (imageBitmap != null) {
                        Image(
                            bitmap = imageBitmap,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .matchParentSize()
                                .graphicsLayer {
                                    scaleX = zoom
                                    scaleY = zoom
                                    translationX = offset.x
                                    translationY = offset.y
                                },
                        )
                    }
                }

                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = stringResource(R.string.fx_customization_crop_zoom),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                    )
                    Slider(
                        value = zoom,
                        onValueChange = { zoom = it },
                        valueRange = 1f..4f,
                        modifier = Modifier
                            .weight(1f)
                            .padding(start = 12.dp),
                    )
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(
                        onClick = { if (!busy) onDismiss() },
                        shape = RoundedCornerShape(50),
                    ) {
                        Text(stringResource(R.string.fx_fresh_home_cancel))
                    }
                    Spacer(Modifier.weight(1f))
                    FilledTonalButton(
                        onClick = {
                            if (busy || bitmap == null || boxSize == IntSize.Zero) return@FilledTonalButton
                            busy = true
                            scope.launch {
                                val path = withContext(Dispatchers.IO) {
                                    cropToFile(
                                        context = context,
                                        source = bitmap,
                                        boxW = boxSize.width.toFloat(),
                                        boxH = boxSize.height.toFloat(),
                                        zoom = zoom,
                                        pan = offset,
                                        fileName = outputName,
                                    )
                                }
                                busy = false
                                if (path != null) onConfirm(path) else onDismiss()
                            }
                        },
                        shape = RoundedCornerShape(50),
                    ) {
                        Text(stringResource(R.string.fx_customization_crop_apply))
                    }
                }
            }
        }
    }
}

/** 按屏幕上的缩放/平移换算出源图裁剪区域，再等比缩放到 1200px 宽保存。 */
private fun cropToFile(
    context: Context,
    source: Bitmap,
    boxW: Float,
    boxH: Float,
    zoom: Float,
    pan: Offset,
    fileName: String,
): String? = runCatching {
    val scale = max(boxW / source.width, boxH / source.height) * zoom
    val visibleW = (boxW / scale).coerceAtMost(source.width.toFloat())
    val visibleH = (boxH / scale).coerceAtMost(source.height.toFloat())

    val centerX = source.width / 2f - pan.x / scale
    val centerY = source.height / 2f - pan.y / scale

    val left = (centerX - visibleW / 2f).coerceIn(0f, (source.width - visibleW).coerceAtLeast(0f))
    val top = (centerY - visibleH / 2f).coerceIn(0f, (source.height - visibleH).coerceAtLeast(0f))

    val cropped = Bitmap.createBitmap(
        source,
        left.toInt(),
        top.toInt(),
        visibleW.toInt().coerceAtLeast(1),
        visibleH.toInt().coerceAtLeast(1),
    )
    val outW = 1200
    val outH = (outW / (boxW / boxH)).toInt().coerceAtLeast(1)
    val scaled = Bitmap.createScaledBitmap(cropped, outW, outH, true)
    if (scaled !== cropped) cropped.recycle()

    val file = saveCroppedBitmap(context, scaled, fileName)
    scaled.recycle()
    file
}.getOrNull()
