package me.diksu.ultra.foxsu.ui.screen.glass

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import me.diksu.ultra.BuildConfig
import me.diksu.ultra.R
import me.diksu.ultra.foxsu.ui.theme.glassTokens

/**
 * FoxSu Glass · 作者页 —— 与 Glass 语言一致：
 *   圆形返回按钮 + 19px 标题
 *   英雄卡：渐变描边 + 白玻璃，74dp 头像带呼吸霓虹环，姓名/角色/简介
 *   信息分组：白玻璃行（chip + 标题/副标题 + ›）
 */
@Composable
fun GlassAuthor(onBack: () -> Unit, onOpenUrl: (String) -> Unit) {
    val t = glassTokens()

    Box(
        modifier = Modifier
            .fillMaxSize()
            // 详情页必须有实底：否则会透出导航栈上一页（玻璃卡会变成"半透明弹窗"）
            .background(t.bg),
    ) {
        // 顶部柔光只在页面内部，不干扰可读性
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(300.dp)
                .background(
                    Brush.verticalGradient(
                        listOf(
                            t.flame.copy(alpha = if (t.isDark) 0.10f else 0.07f),
                            Color.Transparent,
                        )
                    )
                ),
        )
    LazyColumn(
        modifier = Modifier
            .fillMaxSize(),
        contentPadding = PaddingValues(
            start = 13.dp,
            end = 13.dp,
            top = glassStatusBarTop() + 4.dp,
            bottom = 32.dp,
        ),
    ) {
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .glassCardSolid(99.dp)
                        .clickable(onClick = onBack),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        painter = painterResource(R.drawable.fx_ic_d_back),
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = t.ink2,
                    )
                }
                Spacer(Modifier.width(12.dp))
                Text(
                    text = stringResource(R.string.fx_author_title),
                    fontSize = 19.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = t.ink,
                    maxLines = 1,
                )
            }
        }

        // 英雄卡
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp)
                    .shadow(
                        10.dp,
                        RoundedCornerShape(26.dp),
                        ambientColor = t.glassShadow,
                        spotColor = t.glassShadow,
                    )
                    .clip(RoundedCornerShape(26.dp))
                    .background(if (t.isDark) Color(0xFF16161F) else Color(0xF7FFFFFF))
                    .border(1.dp, t.heroGrad, RoundedCornerShape(26.dp)),
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        // 头像 + 呼吸霓虹环
                        Box(contentAlignment = Alignment.Center) {
                            val hud = rememberInfiniteTransition(label = "avatar")
                            val breath by hud.animateFloat(
                                initialValue = 0f,
                                targetValue = 1f,
                                animationSpec = infiniteRepeatable(
                                    tween(2600, easing = LinearEasing),
                                    RepeatMode.Restart,
                                ),
                                label = "breath",
                            )
                            Box(
                                modifier = Modifier
                                    .size(74.dp)
                                    .graphicsLayer {
                                        val s = 1f + breath * 0.22f
                                        scaleX = s
                                        scaleY = s
                                        alpha = (1f - breath) * 0.5f
                                    }
                                    .border(
                                        1.5.dp,
                                        Brush.sweepGradient(listOf(t.flame, t.violet, t.cyan, t.flame)),
                                        CircleShape,
                                    ),
                            )
                            Box(
                                modifier = Modifier
                                    .size(74.dp)
                                    .shadow(
                                        14.dp,
                                        CircleShape,
                                        ambientColor = t.flame.copy(alpha = 0.28f),
                                        spotColor = t.violet.copy(alpha = 0.28f),
                                    )
                                    .clip(CircleShape)
                                    .background(if (t.isDark) Color(0xFF14141E) else Color.White)
                                    .border(1.dp, t.glassBorder, CircleShape),
                                contentAlignment = Alignment.Center,
                            ) {
                                // 头像位改用启动器图标，与桌面看到的图标一致
                                me.diksu.ultra.foxsu.ui.component.LauncherMark(
                                    modifier = Modifier.size(58.dp),
                                    cornerPercent = 28,
                                )
                            }
                        }
                        Spacer(Modifier.width(16.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.fx_author_name),
                                fontSize = 21.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = t.ink,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = stringResource(R.string.fx_author_role),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.ExtraBold,
                                letterSpacing = 2.sp,
                                style = TextStyle(brush = t.glineGrad),
                                maxLines = 1,
                                modifier = Modifier.padding(top = 4.dp),
                            )
                            Text(
                                text = "v${BuildConfig.VERSION_NAME}",
                                fontSize = 9.5.sp,
                                color = t.faint,
                                maxLines = 1,
                                modifier = Modifier.padding(top = 4.dp),
                            )
                        }
                    }
                    Spacer(Modifier.height(14.dp))
                    Text(
                        text = stringResource(R.string.fx_author_desc),
                        fontSize = 11.sp,
                        lineHeight = 17.sp,
                        color = t.sub,
                    )
                }
            }
        }

        item { GlassSectionHead("项目") }
        item {
            GlassWide(
                iconRes = R.drawable.fx_ic_d_grid,
                title = stringResource(R.string.fx_author_project),
                subtitle = stringResource(R.string.fx_author_project_value),
                accent = t.violet,
                solid = true,
                showChevron = true,
                onClick = { onOpenUrl("https://github.com/Cmry-mlc/FoxSu") },
            )
        }
        item { Spacer(Modifier.height(10.dp)) }
        item {
            GlassWide(
                iconRes = R.drawable.fx_ic_d_book,
                title = stringResource(R.string.fx_author_upstream),
                subtitle = stringResource(R.string.fx_author_upstream_value),
                accent = t.lemon,
                solid = true,
                showChevron = true,
                onClick = { onOpenUrl("https://github.com/tiann/KernelSU") },
            )
        }

        item { GlassSectionHead("联系") }
        item {
            GlassWide(
                iconRes = R.drawable.fx_ic_d_heart,
                title = stringResource(R.string.fx_author_contact),
                subtitle = stringResource(R.string.fx_author_contact_value),
                accent = t.flame,
                solid = true,
                showChevron = true,
                onClick = { onOpenUrl("https://github.com/Cmry-mlc/FoxSu/issues") },
            )
        }
        item { Spacer(Modifier.height(10.dp)) }
        item {
            GlassWide(
                iconRes = R.drawable.fx_ic_d_user,
                title = stringResource(R.string.fx_author_qq),
                subtitle = stringResource(R.string.fx_author_qq_value),
                accent = t.cyan,
                solid = true,
                showChevron = true,
                onClick = { onOpenUrl("https://pd.qq.com/s/8glxclfk6?b=9") },
            )
        }

        item { GlassSectionHead("版本") }
        item {
            GlassWide(
                iconRes = R.drawable.fx_ic_d_tool,
                title = stringResource(R.string.fx_author_version),
                subtitle = "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
                accent = t.mint,
                solid = true,
            )
        }
    }
    }
}
