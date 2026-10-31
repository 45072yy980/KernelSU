package me.diksu.ultra.foxsu.ui.screen.ag

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.Chat
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Tag
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import me.diksu.ultra.BuildConfig
import me.diksu.ultra.R

/**
 * 晨曦玻璃 · 关于作者（设计稿 p-author）。
 * 头像卡 → 感谢卡（绿）→ 五张彩色链接卡（版本/项目/反馈/QQ 频道/上游）。
 */
@Composable
fun AgAuthor(
    onBack: () -> Unit,
    onOpenUrl: (String) -> Unit,
    onOpenCredits: () -> Unit = {},
) {
    Column(
        Modifier
            .fillMaxSize()
            .agPageBg()
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 18.dp),
    ) {
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            AgIconBtn(AgTopIcons.Back, onClick = onBack)
        }
        AgTitle("关于", "作者", topMargin = 12.dp)

        // 头像卡
        Column(
            Modifier
                .fillMaxWidth()
                .padding(top = 4.dp, bottom = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                Modifier
                    .size(118.dp)
                    .clip(RoundedCornerShape(34.dp))
                    .background(Brush.linearGradient(listOf(Color(0xFFE4D6F8), Color(0xFFEFD8F0))))
                    .border(0.dp, Color.Transparent)
                    .padding(5.dp)
                    .clip(RoundedCornerShape(29.dp))
                    .background(Brush.verticalGradient(listOf(Color(0xFFBFD9EE), Color(0xFFE8DCE2), Color(0xFFD9E4EE)))),
                contentAlignment = Alignment.Center,
            ) {
                // 与桌面一致的启动器图标（自适应图标：白底 + 狐狸前景）
                me.diksu.ultra.foxsu.ui.component.LauncherMark(
                    modifier = Modifier.size(74.dp),
                    cornerPercent = 28,
                )
            }
            Spacer(Modifier.height(14.dp))
            Text("尘梦如烟", fontSize = 23.sp, fontWeight = FontWeight.ExtraBold, color = Ag.Tx)
            Spacer(Modifier.height(3.dp))
            Text("FoxSu 维护者", fontSize = 12.5.sp, color = Ag.Tx2)
        }

        // 感谢卡（绿）
        Box(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(Color(0xFFDFF2E3))
                .padding(13.dp, 14.dp),
        ) {
            Text(
                "感谢使用 FoxSu。这是基于 KernelSU 管理器深度定制的版本，为 KernelSU 管理器重新设计了界面风格。",
                fontSize = 13.sp, lineHeight = 23.sp, color = Color(0xFF1F6B35),
            )
        }
        Spacer(Modifier.height(12.dp))

        // 版本
        AgLinkCard(
            bg = Color(0xFFE4D9F6), bubble = Ag.Vio.copy(alpha = 1.0f), ink = Color(0xFF4C3A86),
            icon = Icons.Rounded.Tag, title = "版本", desc = "${BuildConfig.VERSION_CODE}（${BuildConfig.VERSION_NAME}）",
        ) { onOpenUrl("https://github.com/Cmry-mlc/FoxSu/releases") }
        // 项目
        AgLinkCard(
            bg = Color(0xFFDDEFE2), bubble = Color(0xFF2E7D46), ink = Color(0xFF22571F),
            icon = Icons.Rounded.Code, title = "项目", desc = "FoxSu · KernelSU 分支",
        ) { onOpenUrl("https://github.com/Cmry-mlc/FoxSu") }
        // 反馈
        AgLinkCard(
            bg = Color(0xFFFBEDD6), bubble = Color(0xFFB7791F), ink = Color(0xFF8A5A12),
            icon = Icons.Rounded.Favorite, title = "反馈", desc = "在 GitHub 提交 Issue",
        ) { onOpenUrl("https://github.com/Cmry-mlc/FoxSu/issues") }
        // QQ 频道
        AgLinkCard(
            bg = Color(0xFFFCE1EC), bubble = Color(0xFFBE3455), ink = Color(0xFF8C2B48),
            icon = Icons.Rounded.Chat, title = "QQ 频道", desc = "频道【FoxSu】交流与反馈",
        ) { onOpenUrl("https://pd.qq.com/s/8glxclfk6?b=9") }
        // 上游
        AgLinkCard(
            bg = Color(0xFFDCEBFB), bubble = Color(0xFF1D7AF3), ink = Color(0xFF1B4F91),
            icon = Icons.Rounded.Code, title = "上游项目", desc = "KernelSU · tiann/KernelSU",
        ) { onOpenUrl("https://github.com/tiann/KernelSU") }

        // 致谢入口：不放第 6 张同款彩色卡（那样像还有第六个链接），
        // 换成一张中性底的横条 + 四个叠在一起的色点，隐喻「一群人」。
        CreditEntry(onOpenCredits)

        Spacer(Modifier.height(24.dp))
    }
}

/** 彩色链接卡 .lcard */
@Composable
private fun AgLinkCard(
    bg: Color,
    bubble: Color,
    ink: Color,
    icon: ImageVector,
    title: String,
    desc: String,
    onClick: () -> Unit,
) {
    Box(
        Modifier
            .fillMaxWidth()
            .padding(bottom = 12.dp)
            .agClick(onClick = onClick)
            .clip(RoundedCornerShape(20.dp))
            .background(bg)
            .padding(13.dp, 13.dp, 14.dp, 13.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(bubble),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, null, tint = Color.White, modifier = Modifier.size(17.dp))
            }
            Spacer(Modifier.width(13.dp))
            Column(Modifier.weight(1f)) {
                Text(title, fontSize = 14.5.sp, fontWeight = FontWeight.ExtraBold, color = ink)
                Text(desc, fontSize = 12.5.sp, color = ink.copy(alpha = 0.85f), modifier = Modifier.padding(top = 2.dp))
            }
            Text("›", fontSize = 20.sp, color = ink.copy(alpha = 0.5f), fontWeight = FontWeight.Bold)
        }
    }
}

/**
 * 「开源致谢」入口。
 *
 * 左侧四个叠在一起的色点对应致谢页的四个分组（根 / 界面 / 能力 / 数据），
 * 比再放一张彩色链接卡更克制：它不是「又一个链接」，而是「一层人」。
 */
@Composable
private fun CreditEntry(onClick: () -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .padding(bottom = 12.dp)
            .agClick(onClick = onClick)
            .clip(RoundedCornerShape(20.dp))
            .background(Ag.Card)
            .border(1.dp, Ag.Line, RoundedCornerShape(20.dp))
            .padding(13.dp, 13.dp, 14.dp, 13.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.width(58.dp).height(26.dp)) {
                listOf(Ag.Vio, Ag.Teal, Ag.Amber, Ag.Mag).forEachIndexed { index, tint ->
                    Box(
                        Modifier
                            .offset(x = (index * 11).dp)
                            .size(26.dp)
                            .clip(CircleShape)
                            .background(tint.copy(alpha = if (Ag.isDark) 0.26f else 0.18f))
                            .border(1.dp, tint.copy(alpha = 0.38f), CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        Box(
                            Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(tint),
                        )
                    }
                }
            }
            Spacer(Modifier.width(13.dp))
            Column(Modifier.weight(1f)) {
                Text("开源致谢", fontSize = 14.5.sp, fontWeight = FontWeight.ExtraBold, color = Ag.Tx)
                Text(
                    "15 个项目 · 根 / 界面 / 能力 / 数据",
                    fontSize = 12.5.sp,
                    color = Ag.Tx3,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
            Text("›", fontSize = 20.sp, color = Ag.Chev, fontWeight = FontWeight.Bold)
        }
    }
}
