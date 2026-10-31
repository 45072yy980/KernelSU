package me.diksu.ultra.foxsu.ui.screen.ag

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import me.diksu.ultra.foxsu.ui.component.bottombar.useSideDock

/**
 * 开源致谢。
 *
 * 这一页只做一件事：把 FoxSu 依赖的开源项目按「它在项目里承担什么」摆出来，
 * 而不是按字母序堆一长串。分成四组——根与内核、界面与视觉、能力组件、数据与规则，
 * 每组一个色相，组内条目共用这个色相，一眼能看出「这一排是同一类东西」。
 *
 * 每条都可以点，直接打开对应仓库。许可证就写在名字右边，不折叠、不藏起来：
 * 谁的东西、什么许可、用在哪，三件事应该在同一行里同时可见。
 */
@Composable
fun AgCredits(
    onBack: () -> Unit,
    onOpenUrl: (String) -> Unit,
) {
    val groups = creditGroups()
    val total = groups.sumOf { it.items.size }

    Column(
        Modifier
            .fillMaxSize()
            .agPageBg()
            .statusBarsPadding(),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .background(Ag.headSurface())
                .agHeadBlur()
                .padding(start = if (useSideDock()) 106.dp else 18.dp, end = 18.dp)
                .padding(top = 10.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AgIconBtn(AgTopIcons.Back, onClick = onBack, tint = Ag.Tx)
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 0.dp, bottom = 44.dp),
        ) {
            item {
                AgTitle("开源", "致谢", count = total.toString(), topMargin = 8.dp)
            }
            item {
                Spacer(Modifier.height(14.dp))
                CreditHero(total)
            }

            groups.forEach { group ->
                item {
                    GroupHead(group.title, group.caption, group.tint)
                    AgCard(contentPadding = PaddingValues(vertical = 4.dp)) {
                        group.items.forEachIndexed { index, credit ->
                            CreditRow(credit, group.tint, onOpenUrl)
                            if (index != group.items.lastIndex) {
                                Box(
                                    Modifier
                                        .fillMaxWidth()
                                        .padding(start = 60.dp, end = 14.dp)
                                        .height(1.dp)
                                        .background(Ag.Line),
                                )
                            }
                        }
                    }
                }
            }

            item {
                Spacer(Modifier.height(20.dp))
                CreditFooter()
            }
        }
    }
}

/** 顶部说明：为什么这一页存在，以及一共欠了多少人。 */
@Composable
private fun CreditHero(total: Int) {
    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(
                Brush.linearGradient(
                    listOf(Ag.Vio.copy(alpha = 0.14f), Ag.Mag.copy(alpha = 0.10f)),
                ),
            )
            .border(1.dp, Ag.Vio.copy(alpha = 0.18f), RoundedCornerShape(24.dp))
            .padding(horizontal = 18.dp, vertical = 17.dp),
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AgRic(Ag.Vio, AgSettingsIcons.Github, size = 34.dp)
                Spacer(Modifier.width(12.dp))
                Text(
                    "FoxSu 站在这些项目肩上",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Ag.Tx,
                )
            }
            Spacer(Modifier.height(11.dp))
            Text(
                "管理器里没有一行是从零长出来的。下面四组项目构成了它的根、它的界面、它的每一条能力。所有组件都按原许可证分发，点任意一条可以直接打开仓库。",
                fontSize = 12.sp,
                lineHeight = 19.sp,
                color = Ag.Tx2,
            )
            Spacer(Modifier.height(12.dp))
            Text(
                "$total 个项目 · 4 个分类 · GPL-3.0 / Apache-2.0 / BSD",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.3.sp,
                color = Ag.Vio.copy(alpha = 0.85f),
            )
        }
    }
}

/** 分组标题：色点 + 名称 + 一句归属说明。 */
@Composable
private fun GroupHead(title: String, caption: String, tint: Color) {
    Row(
        Modifier.padding(start = 6.dp, top = 22.dp, bottom = 10.dp, end = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(6.dp)
                .clip(CircleShape)
                .background(tint),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            title,
            fontSize = 12.sp,
            fontWeight = FontWeight.ExtraBold,
            letterSpacing = 1.sp,
            color = Ag.Tx3,
        )
        Spacer(Modifier.width(7.dp))
        Text(
            caption,
            fontSize = 10.5.sp,
            color = Ag.Tx3.copy(alpha = 0.72f),
            modifier = Modifier.weight(1f),
        )
    }
}

/** 条目：图标块 → 名称 + 职责 → 许可证。整行可点。 */
@Composable
private fun CreditRow(credit: Credit, tint: Color, onOpenUrl: (String) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .agClick { onOpenUrl(credit.url) }
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AgRic(tint, credit.icon, size = 34.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    credit.name,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = Ag.Tx,
                    modifier = Modifier.weight(1f, fill = false),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    credit.license,
                    fontSize = 9.5.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.2.sp,
                    color = tint,
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(tint.copy(alpha = if (Ag.isDark) 0.16f else 0.10f))
                        .padding(horizontal = 5.dp, vertical = 2.dp),
                )
            }
            Spacer(Modifier.height(3.dp))
            Text(
                credit.role,
                fontSize = 11.5.sp,
                lineHeight = 16.sp,
                color = Ag.Tx3,
            )
        }
    }
}

/** 页脚：许可证说明 + 那些没有单独成行的基础库。 */
@Composable
private fun CreditFooter() {
    AgCard(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp)) {
        Text(
            "许可证",
            fontSize = 13.sp,
            fontWeight = FontWeight.ExtraBold,
            color = Ag.Tx,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "FoxSu 以 GPL-3.0 分发。其中来自 KernelSU、susfs4ksu、KernelPatch、Magic Mount-rs、Zygisk Next 的部分同样以 GPL 授权，其传染性适用于本项目；其余组件按各自许可证保留原条款（Apache-2.0、MIT、BSD 等），未做任何删改。",
            fontSize = 11.5.sp,
            lineHeight = 18.sp,
            color = Ag.Tx2,
        )
        Spacer(Modifier.height(12.dp))
        Text(
            "此外还使用了 RikkaX、Protocol Buffers、kotlinx.coroutines、Commons Compress、AndroidX 等基础库，以及 KernelSU 社区的多语言翻译。它们的作者同样应该被写在这里。",
            fontSize = 11.5.sp,
            lineHeight = 18.sp,
            color = Ag.Tx3,
        )
        Spacer(Modifier.height(14.dp))
        Text(
            "如果你认为某个项目漏了，欢迎提 Issue 补上。",
            fontSize = 11.sp,
            color = Ag.Tx3.copy(alpha = 0.8f),
        )
    }
}

/** 一个开源条目。 */
private data class Credit(
    val name: String,
    val role: String,
    val license: String,
    val url: String,
    val icon: ImageVector,
)

/** 一组同源的项目。 */
private data class CreditGroup(
    val title: String,
    val caption: String,
    val tint: Color,
    val items: List<Credit>,
)

/**
 * 分组顺序即依赖顺序：先有根（内核与挂载），才有界面，才有那些能力组件，
 * 最后才是往里灌的数据。读下来正好是「这个东西是怎么长出来的」。
 */
@Composable
private fun creditGroups(): List<CreditGroup> = listOf(
    CreditGroup(
        title = "根与内核",
        caption = "没有它们就没有 root",
        tint = Ag.Vio,
        items = listOf(
            Credit(
                "KernelSU",
                "内核 root、模块体系与 ksud。FoxSu 是它的一个分支",
                "GPL-3.0",
                "https://github.com/tiann/KernelSU",
                AgSettingsIcons.Shield,
            ),
            Credit(
                "KernelPatch",
                "KPM 模块加载器背后的内核 supercall",
                "GPL-2.0",
                "https://github.com/bmax121/KernelPatch",
                AgHomeIcons.Cpu,
            ),
            Credit(
                "susfs4ksu",
                "路径隐藏与挂载伪装的内核补丁约定（上游 simonpunk 仓库已下线，此处指向社区镜像）",
                "GPL-3.0",
                "https://github.com/ShirkNeko/susfs4ksu",
                AgSettingsIcons.EyeOff,
            ),
            Credit(
                "Magic Mount-rs",
                "随管理器内置的元模块，接管系统分区挂载",
                "GPL-3.0",
                "https://github.com/Tools-cx-app/meta-magic_mount-rs",
                AgEngineIcons.Mount,
            ),
            Credit(
                "Zygisk Next",
                "随管理器内置的独立 Zygisk 实现，让 Zygisk 模块能跑",
                "GPL-3.0",
                "https://github.com/LSPosed/ZygiskNext",
                AgSettingsIcons.Puzzle,
            ),
            Credit(
                "Oh My Keymint",
                "随管理器内置的 KeyMint 实现，配合 keybox 提供密钥证明链",
                "Apache-2.0",
                "https://github.com/qwq233/OhMyKeymint",
                AgSettingsIcons.Shield,
            ),
        ),
    ),
    CreditGroup(
        title = "界面与视觉",
        caption = "所有皮肤的地基",
        tint = Ag.Teal,
        items = listOf(
            Credit(
                "Miuix",
                "Compose Multiplatform 组件库，多套皮肤的底座",
                "Apache-2.0",
                "https://github.com/compose-miuix-ui/miuix",
                AgSettingsIcons.Style,
            ),
            Credit(
                "Jetpack Compose",
                "Android 声明式 UI、动画与手势系统",
                "Apache-2.0",
                "https://github.com/androidx/androidx",
                AgHomeIcons.Layers,
            ),
            Credit(
                "MaterialKolor",
                "从壁纸取色，生成整套动态主题",
                "MIT",
                "https://github.com/jordond/materialkolor",
                AgSettingsIcons.Monet,
            ),
            Credit(
                "AppIconLoader",
                "以正确尺寸加载已安装应用的图标",
                "Apache-2.0",
                "https://github.com/zhanghai/AppIconLoader",
                AgTopIcons.AppGrid,
            ),
            Credit(
                "AndroidLiquidGlass",
                "液体玻璃绘制：镜头折射、内阴影与背景合成（ui/component/liquid 改编自该项目）",
                "Apache-2.0",
                "https://github.com/Kyant0/AndroidLiquidGlass",
                AgTopIcons.Theme,
            ),
        ),
    ),
    CreditGroup(
        title = "能力组件",
        caption = "root、网络与数据底座",
        tint = Ag.Amber,
        items = listOf(
            Credit(
                "libsu",
                "root shell 与守护进程，所有命令都从这里走",
                "Apache-2.0",
                "https://github.com/topjohnwu/libsu",
                AgSettingsIcons.Gear,
            ),
            Credit(
                "OkHttp",
                "下载去广告规则、检查更新",
                "Apache-2.0",
                "https://github.com/lysine-dev/okhttp",
                AgSettingsIcons.CheckUpdate,
            ),
            Credit(
                "kotlinx.coroutines",
                "协程调度，整套异步与动效的时间线",
                "Apache-2.0",
                "https://github.com/Kotlin/kotlinx.coroutines",
                AgSettingsIcons.Puzzle,
            ),
            Credit(
                "Protocol Buffers",
                "模块信息与应用列表的二进制序列化",
                "BSD-3-Clause",
                "https://github.com/protocolbuffers/protobuf",
                AgSettingsIcons.Modules,
            ),
            Credit(
                "RikkaX",
                "跨进程传递已安装应用列表（ParcelableList 模块）",
                "MIT",
                "https://github.com/RikkaApps/RikkaX",
                AgSettingsIcons.LocalTemplate,
            ),
            Credit(
                "commonmark-java",
                "模块说明页的 Markdown 渲染",
                "BSD-2-Clause",
                "https://github.com/commonmark/commonmark-java",
                AgHomeIcons.Book,
            ),
            Credit(
                "HiddenApiBypass",
                "绕过隐藏 API 限制，读取系统内部状态",
                "Apache-2.0",
                "https://github.com/LSPosed/AndroidHiddenApiBypass",
                AgSettingsIcons.Log,
            ),
            Credit(
                "Commons Compress · XZ",
                "模块包与镜像的压缩格式解包",
                "Apache-2.0",
                "https://github.com/apache/commons-compress",
                AgTopIcons.Import,
            ),
        ),
    ),
    CreditGroup(
        title = "数据与规则",
        caption = "去广告的拦截面",
        tint = Ag.Mag,
        items = listOf(
            Credit(
                "anti-AD",
                "国内 App 与运营商广告域名规则库，每日更新",
                "MIT",
                "https://github.com/privacy-protection-tools/anti-AD",
                AgHomeIcons.ShieldCheck,
            ),
            Credit(
                "AdGuard 中文过滤",
                "补充中文站点与 App 的广告域名",
                "GPL-3.0",
                "https://github.com/AdguardTeam/AdguardFilters",
                AgHomeIcons.Layers,
            ),
        ),
    ),
)
