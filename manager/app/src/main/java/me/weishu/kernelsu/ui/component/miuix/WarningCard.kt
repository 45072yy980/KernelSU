package me.weishu.kernelsu.ui.component.miuix

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import me.weishu.kernelsu.ui.component.WarningLevel
import me.weishu.kernelsu.ui.theme.LocalGlassNotice
import me.weishu.kernelsu.ui.theme.isInDarkTheme
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme
import top.yukonga.miuix.kmp.theme.MiuixTheme.isDynamicColor
import top.yukonga.miuix.kmp.utils.PressFeedbackType

@Composable
fun WarningCard(
    message: String,
    modifier: Modifier = Modifier,
    level: WarningLevel = WarningLevel.Error,
    onClick: (() -> Unit)? = null,
    action: (@Composable () -> Unit)? = null,
) {
    // Inside a frosted pane the card must not paint its own tinted container:
    // the pane is the background, and a container on top of it would hide the
    // glass entirely.
    //
    // The text colour has to change with it. "onErrorContainer" and
    // "onTertiaryContainer" are near-black browns and reds, chosen to sit on
    // their own pale containers; over a blurred photo they disappear. Over the
    // glass the page uses the same bright tint every other card on the home
    // screen uses, so this one matches instead of inventing its own.
    if (LocalGlassNotice.current) {
        val textColor = lerp(colorScheme.primary, Color.White, 0.75f)
        Card(
            modifier = modifier,
            onClick = { onClick?.invoke() },
            colors = CardDefaults.defaultColors(
                color = Color.Transparent,
                contentColor = textColor,
            ),
            showIndication = onClick != null,
            pressFeedbackType = PressFeedbackType.Sink
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = message,
                    // The page's body size. Miuix's body2 is what every other home
                    // card uses for running text, so this matches instead of sitting
                    // at a one-off 14.sp that read smaller than everything around it.
                    fontSize = MiuixTheme.textStyles.body2.fontSize,
                    color = textColor,
                )
                action?.invoke()
            }
        }
        return
    }
    if (me.weishu.kernelsu.ui.LocalUiMode.current != me.weishu.kernelsu.ui.UiMode.Miuix) {
        // Stock miuix (and any shared surface): the plain card the official app ships.
        Card(
            modifier = modifier,
            onClick = { onClick?.invoke() },
            colors = CardDefaults.defaultColors(
                color = level.containerColor(),
                contentColor = level.contentColor(),
            ),
            showIndication = onClick != null,
            pressFeedbackType = PressFeedbackType.Sink
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = message,
                    fontSize = 14.sp
                )
                action?.invoke()
            }
        }
        return
    }
    // The banner rides the page's gradient the way the status card does: the container
    // colour starts it and the page's own background colour fades into it, so a notice
    // sits on the picture instead of beside it. The shadow is what keeps the edge.
    val cardShape = RoundedCornerShape(16.dp)
    val container = level.containerColor()
    Box(
        modifier = modifier
            .fillMaxWidth()
            .shadow(8.dp, cardShape, clip = true)
            .background(
                brush = Brush.verticalGradient(
                    listOf(container, lerp(container, colorScheme.background, 0.35f))
                ),
                shape = cardShape,
            )
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.defaultColors(
                color = Color.Transparent,
                contentColor = level.contentColor(),
            ),
            onClick = { onClick?.invoke() },
            showIndication = onClick != null,
            pressFeedbackType = PressFeedbackType.Sink
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = message,
                    fontSize = 14.sp
                )
                action?.invoke()
            }
        }
    }
}

@Composable
private fun WarningLevel.containerColor(): Color = when {
    isDynamicColor -> when (this) {
        WarningLevel.Error -> colorScheme.errorContainer
        WarningLevel.Notice -> colorScheme.tertiaryContainer
    }

    isInDarkTheme() -> when (this) {
        WarningLevel.Error -> Color(0xFF310808)
        WarningLevel.Notice -> Color(0xFF3E2F1B)
    }

    else -> when (this) {
        WarningLevel.Error -> Color(0xFFF8E2E2)
        WarningLevel.Notice -> Color(0xFFFFF0DB)
    }
}

@Composable
private fun WarningLevel.contentColor(): Color = when {
    isDynamicColor -> when (this) {
        WarningLevel.Error -> colorScheme.onErrorContainer
        WarningLevel.Notice -> colorScheme.onTertiaryContainer
    }

    else -> when (this) {
        WarningLevel.Error -> Color(0xFFF72727)
        WarningLevel.Notice -> Color(0xFFF5A623)
    }
}
