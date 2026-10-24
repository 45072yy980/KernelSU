package me.diksu.ultra.ui.component.miuix

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme

/**
 * 版本号胶囊（借鉴自 Aster 的 VersionBadge）。
 *
 * 设置页的每一行都应该在点进去之前就回答"现在是什么"；版本这种只读信息
 * 用一枚挂在行尾的胶囊表达，比多一行 summary 更省空间，也比塞进标题更清楚。
 */
@Composable
fun VersionBadge(version: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(colorScheme.primary.copy(alpha = 0.12f))
            .padding(horizontal = 10.dp, vertical = 3.dp),
    ) {
        Text(
            text = version,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = colorScheme.primary,
        )
    }
}
