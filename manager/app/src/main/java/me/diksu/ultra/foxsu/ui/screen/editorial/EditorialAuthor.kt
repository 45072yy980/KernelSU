package me.diksu.ultra.foxsu.ui.screen.editorial

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import me.diksu.ultra.BuildConfig
import me.diksu.ultra.R

/**
 * FoxSu Editorial · 作者页：.ahero（大头像 + 姓名/角色 + 简介）+ 编号信息行。
 */
@Composable
fun EditorialAuthor(onBack: () -> Unit, onOpenUrl: (String) -> Unit) {
    val t = edTokens()
    Column(
        modifier = Modifier
            .fillMaxSize()
            .then(edPageBg(t.paper))
            .statusBarsPadding()
            .verticalScroll(rememberScrollState()),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .hairBottom(t.ink)
                .padding(start = 22.dp, end = 22.dp, top = 14.dp, bottom = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            EdCircleButton(R.drawable.fx_ic_d_back, onBack, size = 30)
            Spacer(Modifier.width(12.dp))
            Text(
                text = stringResource(R.string.fx_author_title),
                fontSize = 26.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = (-0.5).sp,
                color = t.ink,
            )
        }

        // .ahero
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .hairBottom(t.ink)
                .padding(horizontal = 22.dp, vertical = 16.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Box(
                modifier = Modifier
                    .size(74.dp)
                    .clip(CircleShape)
                    .background(t.acc)
                    .drawBehind {
                        drawCircle(
                            color = t.faint,
                            radius = size.minDimension / 2 + 7.dp.toPx(),
                            style = androidx.compose.ui.graphics.drawscope.Stroke(
                                width = 1.5.dp.toPx(),
                                pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(
                                    floatArrayOf(6f, 6f)
                                )
                            ),
                        )
                    },
                contentAlignment = Alignment.Center,
            ) {
                // 头像位改用启动器图标
                me.diksu.ultra.foxsu.ui.component.LauncherMark(
                    modifier = Modifier.fillMaxSize().padding(9.dp),
                    cornerPercent = 28,
                )
            }
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.fx_author_name),
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Black,
                    color = t.ink,
                    maxLines = 1,
                )
                Text(
                    text = stringResource(R.string.fx_author_role),
                    fontSize = 9.5.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 1.6.sp,
                    color = t.acc,
                    modifier = Modifier.padding(top = 4.dp),
                )
                Text(
                    text = stringResource(R.string.fx_author_desc),
                    fontSize = 10.sp,
                    lineHeight = 16.sp,
                    color = t.ink2,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }

        // 引言（.aquote）
        Text(
            text = "「保持免费开源，把 Root 做得更安静一点。」",
            fontSize = 10.5.sp,
            lineHeight = 18.sp,
            color = t.ink2,
            modifier = Modifier
                .padding(start = 22.dp, end = 22.dp, top = 14.dp)
                .drawBehind {
                    drawLine(
                        color = t.acc,
                        start = Offset(0f, 0f),
                        end = Offset(0f, size.height),
                        strokeWidth = 3.dp.toPx(),
                    )
                }
                .padding(start = 12.dp),
        )

        EdSection("01", "信息", topPadding = 18.dp)
        EdRow(iconRes = R.drawable.fx_ic_d_tool, glyphBg = t.violet.copy(alpha = 0.10f), glyphColor = t.violet, title = stringResource(R.string.fx_author_version), subtitle =
            "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
        EdRow(iconRes = R.drawable.fx_ic_d_terminal, glyphBg = t.cyan.copy(alpha = 0.10f), glyphColor = t.cyan, title = stringResource(R.string.fx_author_project), subtitle =
            stringResource(R.string.fx_author_project_value),
            trailing = { Text("›", fontSize = 13.sp, color = t.faint) },
            onClick = { onOpenUrl("https://github.com/Cmry-mlc/FoxSu") })
        EdRow(iconRes = R.drawable.fx_ic_d_heart, glyphBg = t.acc.copy(alpha = 0.10f), glyphColor = t.acc, title = stringResource(R.string.fx_author_contact), subtitle =
            stringResource(R.string.fx_author_contact_value),
            trailing = { Text("›", fontSize = 13.sp, color = t.faint) },
            onClick = { onOpenUrl("https://github.com/Cmry-mlc/FoxSu/issues") })
        EdRow(iconRes = R.drawable.fx_ic_d_user, glyphBg = t.amber.copy(alpha = 0.10f), glyphColor = t.amber, title = stringResource(R.string.fx_author_qq), subtitle =
            stringResource(R.string.fx_author_qq_value),
            trailing = { Text("›", fontSize = 13.sp, color = t.faint) },
            onClick = { onOpenUrl("https://pd.qq.com/s/8glxclfk6?b=9") })
        EdRow(iconRes = R.drawable.fx_ic_d_book, glyphBg = t.ink2.copy(alpha = 0.08f), glyphColor = t.ink2, title = stringResource(R.string.fx_author_upstream), subtitle =
            stringResource(R.string.fx_author_upstream_value),
            trailing = { Text("›", fontSize = 13.sp, color = t.faint) },
            onClick = { onOpenUrl("https://github.com/tiann/KernelSU") })
        Spacer(Modifier.height(28.dp))
    }
}
