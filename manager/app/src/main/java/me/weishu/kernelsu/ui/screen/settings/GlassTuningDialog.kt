package me.weishu.kernelsu.ui.screen.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import me.weishu.kernelsu.R
import me.weishu.kernelsu.ui.screen.home.GlassNudge
import kotlin.math.roundToInt

/**
 * The sliders that place the frosted pane, for the case where the shipped nudge does not line the
 * pane up on this device.
 *
 * [GlassNudge] has carried live values since it was written, but nothing ever offered a way to
 * change them: the defaults were dialled in against one screen and saved to preferences, and a
 * device the values do not suit had no way to correct them. This is that way.
 *
 * Both sliders write straight into the state the pane reads, so the card behind this dialog moves
 * as the sliders do -- there is no preview to keep in step and no apply step to forget.
 */
@Composable
fun GlassTuningDialog(
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    var x by remember { mutableFloatStateOf(GlassNudge.x.floatValue) }
    var y by remember { mutableFloatStateOf(GlassNudge.y.floatValue) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.glass_tuning_title)) },
        text = {
            Column {
                Text(
                    text = stringResource(R.string.glass_tuning_summary),
                    style = MaterialTheme.typography.bodyMedium,
                )
                NudgeSlider(
                    label = stringResource(R.string.glass_tuning_x),
                    value = x,
                    range = -100f..200f,
                    onValueChange = {
                        x = it
                        GlassNudge.x.floatValue = it
                    },
                )
                NudgeSlider(
                    label = stringResource(R.string.glass_tuning_y),
                    value = y,
                    range = 0f..600f,
                    onValueChange = {
                        y = it
                        GlassNudge.y.floatValue = it
                    },
                )
                Text(
                    text = stringResource(
                        R.string.glass_tuning_current,
                        x.roundToInt(),
                        y.roundToInt(),
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                GlassNudge.save(context)
                onDismiss()
            }) {
                Text(stringResource(R.string.glass_tuning_save))
            }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(onClick = {
                    GlassNudge.reset(context)
                    x = GlassNudge.x.floatValue
                    y = GlassNudge.y.floatValue
                }) {
                    Text(stringResource(R.string.glass_tuning_reset))
                }
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.glass_tuning_cancel))
                }
            }
        },
    )
}

/** One labelled slider, with its value spelled out beside the name. */
@Composable
private fun NudgeSlider(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit,
) {
    Column(modifier = Modifier.padding(top = 12.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(text = label, style = MaterialTheme.typography.labelLarge)
            Text(text = "${value.roundToInt()}", style = MaterialTheme.typography.labelLarge)
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = range,
        )
    }
}
