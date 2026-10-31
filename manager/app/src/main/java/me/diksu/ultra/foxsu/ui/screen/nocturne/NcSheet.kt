package me.diksu.ultra.foxsu.ui.screen.nocturne

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.launch

/**
 * 底部弹层容器 .sheet —— 严格对照设计稿：
 * · r30/30/0/0
 * · 渐变底 sheetTop → sheetBottom
 * · 顶部 violet 35% 描边
 * · 顶部 320px 紫色光晕 radial-gradient(at 50% -30%, sheetTopGlow, transparent 70%)
 * · grab 指示条
 * · 出场：translateY 30 → 0，420ms，cubic-bezier(.2,1.05,.35,1)；遮罩 250ms
 *
 * 落地要点：
 * 1. 光晕是**背景层**，用 matchParentSize 绘制，不参与测量 —— 否则会撑出 150dp 空白把内容顶下去；
 * 2. 面板本身是一个全屏 Dialog，才能盖住底栏 / 状态栏并拿到真实 systemBars inset；
 * 3. 内容区可滚动，最高占屏 90%，超出后滚动而不是被裁掉。
 */
@Composable
fun NcBottomSheet(
    onDismiss: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    val t = ncTokens()
    var visible by remember { mutableStateOf(true) }
    val reveal = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val scroll = rememberScrollState()

    LaunchedEffect(Unit) {
        reveal.animateTo(1f, tween(420, easing = CubicBezierEasing(0.2f, 1.05f, 0.35f, 1f)))
    }

    // 先收回再销毁，避免弹层「啪」地消失；收起过程中重复点击直接忽略
    val close: () -> Unit = {
        if (visible && reveal.value >= 1f) {
            scope.launch {
                reveal.animateTo(0f, tween(200, easing = CubicBezierEasing(0.4f, 0f, 1f, 1f)))
                visible = false
                onDismiss()
            }
        }
    }

    if (!visible) return

    Dialog(
        onDismissRequest = close,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = false,
        ),
    ) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val sheetMaxHeight = maxHeight * 0.9f

            // 遮罩 .ovl
            Box(
                Modifier
                    .fillMaxSize()
                    .background(t.scrim.copy(alpha = t.scrim.alpha * reveal.value))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = close,
                    )
            )

            Box(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .heightIn(max = sheetMaxHeight)
                    .offset(y = (30 * (1f - reveal.value)).dp)
                    .clip(RoundedCornerShape(topStart = 30.dp, topEnd = 30.dp))
                    .background(Brush.verticalGradient(listOf(t.sheetTop, t.sheetBottom)))
                    .border(
                        1.dp,
                        t.sheetBorder,
                        RoundedCornerShape(topStart = 30.dp, topEnd = 30.dp),
                    ),
            ) {
                // 顶部紫色光晕：铺满面板但只在上沿着色，不占布局空间
                Canvas(Modifier.matchParentSize()) {
                    drawRect(
                        brush = Brush.radialGradient(
                            0.0f to t.sheetTopGlow,
                            0.7f to Color.Transparent,
                            1.0f to Color.Transparent,
                            center = Offset(size.width / 2f, -30f),
                            radius = 320f,
                        )
                    )
                }
                Column(Modifier.fillMaxWidth()) {
                    // grab
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .padding(top = 9.dp, bottom = 6.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Box(
                            Modifier
                                .size(36.dp, 4.dp)
                                .clip(CircleShape)
                                .background(t.grab)
                        )
                    }
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .verticalScroll(scroll)
                            .navigationBarsPadding()
                            .padding(start = 18.dp, end = 18.dp, top = 6.dp, bottom = 22.dp),
                        content = content,
                    )
                }
            }
        }
    }
}
