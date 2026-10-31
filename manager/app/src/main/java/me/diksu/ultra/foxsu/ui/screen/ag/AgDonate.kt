package me.diksu.ultra.foxsu.ui.screen.ag

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
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
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BugReport
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import me.diksu.ultra.foxsu.data.settings.CustomizationStore
import me.diksu.ultra.foxsu.ui.component.ambient.AmbientPawsLayer
import me.diksu.ultra.foxsu.ui.component.ambient.BackgroundFxLayer
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import android.widget.Toast
import me.diksu.ultra.foxsu.ui.component.bottombar.useSideDock
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 支持 FoxSu —— 捐赠页。
 *
 * 这一页刻意不写「请赞助我」：先把「你已经在用的东西到底值多少」摆在最上面
 * （皮肤 / 底栏造型 / 广告数 0），再列出钱会花在哪、以及不花钱能做什么。
 *
 * 没有金额档位、没有线上支付 SDK：两个大按钮（微信赞赏 / 支付宝收款）各自
 * 打开一张全屏二维码查看器，内含「保存到相册」，扫完即走。
 *
 * 外壳内容之上盖一层猫爪／爱心氛围：氛围层必须压在内容上层——本页根布局铺了
 * `agPageBg()` 不透明底，垫在下面看不见。它不接指针事件，二维码和按钮照常可点。
 */
@Composable
fun AgDonate(
    onBack: () -> Unit,
    onOpenAuthor: () -> Unit = {},
) {
    val custom = CustomizationStore.current
    Box(Modifier.fillMaxSize()) {
        AgDonateContent(onBack = onBack, onOpenAuthor = onOpenAuthor)
        // 和主页同一个槽位：选中猫爪就是猫爪，选中某套背景特效就铺那套。
        // 二维码区域要能扫，所以特效在这里比主页再压淡一档。
        if (custom.bgFxOn) {
            if (custom.bgFxStyle == 0) {
                AmbientPawsLayer(
                    enabled = true,
                    count = 10,
                    // 捐赠页内容密度高（二维码、金额、按钮），粒子再压淡一点，别抢注意力
                    alphaScale = 0.85f,
                )
            } else {
                BackgroundFxLayer(
                    style = custom.bgFxStyle,
                    enabled = true,
                    alphaScale = 0.55f,
                )
            }
        }
    }
}

@Composable
private fun AgDonateContent(
    onBack: () -> Unit,
    onOpenAuthor: () -> Unit = {},
) {
    val context = LocalContext.current
    // 二维码查看器：null 关闭，其它为当前展示的码
    var viewer by remember { mutableStateOf<QrTarget?>(null) }

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
            Spacer(Modifier.weight(1f))
            AgIconBtn(AgTopIcons.User, onClick = onOpenAuthor, tint = Ag.Tx)
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 4.dp, bottom = 44.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item { DonateHero() }

            item {
                AgGHead("请作者喝一杯")
                // 两个入口：微信赞赏 / 支付宝收款。点开是全屏二维码查看器，
                // 长按或点保存可以存到相册，没有任何金额档位和线上支付
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    QrEntry(
                        label = "微信赞赏",
                        sub = "打开赞赏码",
                        tint = Color(0xFF07C160),
                        icon = Icons.Rounded.Favorite,
                        modifier = Modifier.weight(1f),
                    ) { viewer = QrTarget.WECHAT }
                    QrEntry(
                        label = "支付宝",
                        sub = "打开收款码",
                        tint = Color(0xFF1677FF),
                        icon = Icons.Rounded.Star,
                        modifier = Modifier.weight(1f),
                    ) { viewer = QrTarget.ALIPAY }
                }
                Spacer(Modifier.height(10.dp))
                Text(
                    "扫码是自愿的，项目永远免费开源（GPL-3.0）。二维码可以长按保存到相册。",
                    fontSize = 11.5.sp,
                    lineHeight = 17.sp,
                    color = Ag.Tx3,
                    modifier = Modifier.padding(horizontal = 4.dp),
                )
            }

            item {
                AgGHead("免责声明")
                AgCard(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp)) {
                    NoticeLine(
                        "本软件按「现状」（AS IS）提供，不附带任何明示或暗示的担保，包括但不限于适销性、特定用途适用性及不侵权的保证。",
                    )
                    NoticeLine(
                        "刷机、Root、安装模块均会修改设备系统，可能导致数据丢失、系统无法启动、保修失效或硬件损坏。是否使用及使用方式由你自行判断并承担全部风险。",
                    )
                    NoticeLine(
                        "请务必提前备份重要数据，并确认已了解自己设备的解锁与回滚流程。因使用本软件造成的任何直接或间接损失，开发者不承担任何责任。",
                    )
                    NoticeLine(
                        "本软件为纯本地工具，不含广告、不设后台服务、不上传任何个人数据；赞赏是完全自愿的，与功能使用无关。",
                        last = true,
                    )
                }
            }

            item {
                AgGHead("不花钱也能支持")
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    FreeTile(Icons.Rounded.Star, "Star 项目", Modifier.weight(1f)) {
                        Toast.makeText(context, "仓库地址：github.com/FoxSu", Toast.LENGTH_SHORT).show()
                    }
                    FreeTile(Icons.Rounded.Share, "推荐给朋友", Modifier.weight(1f)) {
                        Toast.makeText(context, "截图分享给需要的人就很够了", Toast.LENGTH_SHORT).show()
                    }
                }
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    FreeTile(Icons.Rounded.BugReport, "报告问题", Modifier.weight(1f)) {
                        Toast.makeText(context, "带上日志提 Issue，比捐款更有用", Toast.LENGTH_SHORT).show()
                    }
                    FreeTile(Icons.Rounded.Code, "参与开发", Modifier.weight(1f)) { onOpenAuthor() }
                }
            }

            item { DonateFooter() }
        }
    }

    viewer?.let { target ->
        QrViewerDialog(target) { viewer = null }
    }
}

/** 两个二维码资源，查看器和保存都认它。 */
private enum class QrTarget(val resId: Int, val title: String, val tip: String) {
    WECHAT(
        me.diksu.ultra.R.drawable.fx_donate_qr_wechat,
        "微信赞赏码",
        "打开微信「扫一扫」，扫描二维码赞赏",
    ),
    ALIPAY(
        me.diksu.ultra.R.drawable.fx_donate_qr_alipay,
        "支付宝收款码",
        "打开支付宝「扫一扫」，扫描二维码转账",
    ),
}

/** 捐赠页的两个大按钮：微信 / 支付宝。 */
@Composable
private fun QrEntry(
    label: String,
    sub: String,
    tint: Color,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Column(
        modifier
            .clip(RoundedCornerShape(18.dp))
            .background(tint.copy(alpha = if (Ag.isDark) 0.16f else 0.10f))
            .border(1.dp, tint.copy(alpha = 0.34f), RoundedCornerShape(18.dp))
            .agClick(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 14.dp),
    ) {
        Box(
            Modifier
                .size(38.dp)
                .clip(CircleShape)
                .background(tint.copy(alpha = 0.18f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, null, tint = tint, modifier = Modifier.size(19.dp))
        }
        Spacer(Modifier.height(9.dp))
        Text(label, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, color = Ag.Tx)
        Text(sub, fontSize = 10.5.sp, color = Ag.Tx3, modifier = Modifier.padding(top = 1.dp))
    }
}

/**
 * 二维码查看器：全屏 Dialog，白底大图 + 标题 + 操作提示。
 * 「保存到相册」把 res 里的图写进 Pictures/FoxSu，扫码不一定要存，
 * 但很多人习惯截图后再扫，直接给一个干净的保存入口省一步。
 */
@Composable
private fun QrViewerDialog(target: QrTarget, onDismiss: () -> Unit) {
    val context = LocalContext.current
    Dialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.72f))
                .clickable(
                    indication = null,
                    interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                    onClick = onDismiss,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                Modifier
                    .padding(horizontal = 34.dp)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(26.dp))
                    .background(Color.White)
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    target.title,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color(0xFF1B1B22),
                )
                Spacer(Modifier.height(4.dp))
                Text(target.tip, fontSize = 11.sp, color = Color(0xFF7A7A86))
                Spacer(Modifier.height(14.dp))
                Box(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(18.dp))
                        .background(Color.White)
                        .border(1.dp, Color(0x141B1B22), RoundedCornerShape(18.dp))
                        .padding(12.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Image(
                        painter = androidx.compose.ui.res.painterResource(target.resId),
                        contentDescription = target.title,
                        contentScale = androidx.compose.ui.layout.ContentScale.Fit,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                Spacer(Modifier.height(14.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                    // 保存到相册
                    Box(
                        Modifier
                            .weight(1f)
                            .height(42.dp)
                            .clip(RoundedCornerShape(13.dp))
                            .background(Ag.Grad)
                            .clickable {
                                runCatching {
                                    saveQrToGallery(context, target.resId)
                                    Toast.makeText(context, "已保存到相册 Pictures/FoxSu", Toast.LENGTH_SHORT).show()
                                }.onFailure {
                                    Toast.makeText(context, "保存失败：${it.message}", Toast.LENGTH_SHORT).show()
                                }
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("保存到相册", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    }
                    Box(
                        Modifier
                            .weight(1f)
                            .height(42.dp)
                            .clip(RoundedCornerShape(13.dp))
                            .border(1.dp, Color(0x1F1B1B22), RoundedCornerShape(13.dp))
                            .clickable(onClick = onDismiss),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("关闭", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color(0xFF55555F))
                    }
                }
            }
        }
    }
}

/** 把 drawable 里的二维码存进系统相册（Pictures/FoxSu）。 */
private fun saveQrToGallery(context: android.content.Context, resId: Int) {
    val bmp = android.graphics.BitmapFactory.decodeResource(context.resources, resId)
    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
        val values = android.content.ContentValues().apply {
            put(android.provider.MediaStore.Images.Media.DISPLAY_NAME, "foxsu_donate_${System.currentTimeMillis()}.png")
            put(android.provider.MediaStore.Images.Media.MIME_TYPE, "image/png")
            put(android.provider.MediaStore.Images.Media.RELATIVE_PATH, "Pictures/FoxSu")
        }
        val uri = context.contentResolver.insert(android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            ?: return
        context.contentResolver.openOutputStream(uri)?.use { os ->
            bmp.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, os)
        }
    } else {
        @Suppress("DEPRECATION")
        val dir = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_PICTURES)
        val sub = java.io.File(dir, "FoxSu").apply { mkdirs() }
        val f = java.io.File(sub, "foxsu_donate_${System.currentTimeMillis()}.png")
        java.io.FileOutputStream(f).use { os -> bmp.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, os) }
        android.media.MediaScannerConnection.scanFile(context, arrayOf(f.absolutePath), arrayOf("image/png"), null)
    }
}

/** 顶部品牌卡：渐变 + 光斑，数字是这一版真实具备的能力，不是占位。 */
@Composable
private fun DonateHero() {
    val accent = Ag.accent
    Box(
        Modifier
            .fillMaxWidth()
            .height(206.dp)
            .clip(RoundedCornerShape(26.dp))
            .background(Brush.linearGradient(listOf(accent.primary, accent.mid, accent.secondary)))
            .drawBehind {
                // 三个错位的白色光斑：整块渐变不至于是一条平涂的色带
                drawCircle(
                    color = Color.White.copy(alpha = 0.16f),
                    radius = size.width * 0.42f,
                    center = Offset(size.width * 0.88f, size.height * 0.05f),
                )
                drawCircle(
                    color = Color.White.copy(alpha = 0.10f),
                    radius = size.width * 0.26f,
                    center = Offset(size.width * 0.14f, size.height * 0.92f),
                )
                drawCircle(
                    color = Color.Black.copy(alpha = 0.06f),
                    radius = size.width * 0.34f,
                    center = Offset(size.width * 0.62f, size.height * 1.06f),
                )
            },
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .padding(20.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.18f))
                        .border(1.dp, Color.White.copy(alpha = 0.28f), CircleShape)
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                ) {
                    Text("GPL-3.0 · 开源", fontSize = 10.5.sp, fontWeight = FontWeight.Bold, color = Color.White)
                }
                Spacer(Modifier.width(8.dp))
                Box(
                    Modifier
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.18f))
                        .border(1.dp, Color.White.copy(alpha = 0.28f), CircleShape)
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                ) {
                    Text("无广告", fontSize = 10.5.sp, fontWeight = FontWeight.Bold, color = Color.White)
                }
            }

            Spacer(Modifier.weight(1f))

            Text(
                "支持 FoxSu",
                fontSize = 34.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = (-0.8).sp,
                color = Color.White,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "免费、开源、无内购，也不上传任何数据。\n如果它让你的手机好用了一点，可以考虑支持一下。",
                fontSize = 12.5.sp,
                lineHeight = 18.sp,
                color = Color.White.copy(alpha = 0.88f),
            )

            Spacer(Modifier.height(16.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                HeroStat("5", "套皮肤")
                HeroDivider()
                HeroStat("10", "种底栏")
                HeroDivider()
                HeroStat("0", "条广告")
            }
        }
    }
}

@Composable
private fun HeroStat(value: String, label: String) {
    Row(verticalAlignment = Alignment.Bottom) {
        Text(value, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold, color = Color.White)
        Spacer(Modifier.width(4.dp))
        Text(label, fontSize = 10.5.sp, color = Color.White.copy(alpha = 0.8f), modifier = Modifier.padding(bottom = 2.dp))
    }
}

@Composable
private fun HeroDivider() {
    Box(
        Modifier
            .padding(horizontal = 12.dp)
            .size(width = 1.dp, height = 12.dp)
            .background(Color.White.copy(alpha = 0.3f)),
    )
}




/** 免责声明的一条：小圆点 + 正文，最后一条不留底部间距。 */
@Composable
private fun NoticeLine(text: String, last: Boolean = false) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(bottom = if (last) 0.dp else 12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Box(
            Modifier
                .padding(top = 7.dp)
                .size(4.dp)
                .clip(CircleShape)
                .background(Ag.Tx3),
        )
        Spacer(Modifier.width(10.dp))
        Text(
            text,
            fontSize = 12.sp,
            lineHeight = 19.sp,
            color = Ag.Tx2,
            modifier = Modifier.weight(1f),
        )
    }
}

/** 「不花钱也能支持」的方块。 */
@Composable
private fun FreeTile(icon: ImageVector, label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Column(
        modifier
            .height(92.dp)
            .agClick(onClick = onClick)
            .clip(RoundedCornerShape(18.dp))
            .background(Ag.Card)
            .border(1.dp, Ag.Line, RoundedCornerShape(18.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            Modifier
                .size(32.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(Ag.Vio.copy(alpha = 0.10f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = Ag.Vio, modifier = Modifier.size(17.dp))
        }
        Spacer(Modifier.height(9.dp))
        Text(label, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Ag.Tx, maxLines = 1)
    }
}

/** 页脚：版本、签名、许可证，以及「这页为什么一直没接通」。 */
@Composable
private fun DonateFooter() {
    val stamp = remember { SimpleDateFormat("yyyy.MM", Locale.CHINA).format(Date()) }
    Column(
        Modifier
            .fillMaxWidth()
            .padding(top = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            "FoxSu · $stamp · GPL-3.0",
            fontSize = 11.sp,
            color = Ag.Tx3,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            "基于 KernelSU 二次开发，内核部分与上游同源。\n捐赠是自愿的，不解锁任何功能，也不影响更新。",
            fontSize = 10.5.sp,
            lineHeight = 16.sp,
            color = Ag.Tx3.copy(alpha = 0.8f),
            textAlign = TextAlign.Center,
        )
    }
}

