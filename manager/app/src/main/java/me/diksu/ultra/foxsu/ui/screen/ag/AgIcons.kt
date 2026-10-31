package me.diksu.ultra.foxsu.ui.screen.ag

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp

/**
 * 设计稿内联 SVG 图标 1:1 复刻（foxsu-ui-redesign.html v1.2.0）。
 *
 * 规格：viewBox 24×24，stroke-width 2.2（描边）、2.0（部分），
 * round cap + round join，currentColor。
 * 主页信息卡 .ric 图标 19px；顶栏 .icbtn 图标 18px；Dock .dit 图标 22px。
 *
 * 描边色统一填黑，实际颜色由 Icon(tint=) 整体着色覆盖。
 */
private fun svgIcon(
    name: String,
    blocks: List<SvgBlock>,
    width: Float = 2.2f,
): ImageVector =
    ImageVector.Builder(
        name = name,
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).apply {
        blocks.forEach { b ->
            val nodes = PathParser().parsePathString(b.d).toNodes()
            if (b.fill) {
                addPath(
                    pathData = nodes,
                    pathFillType = PathFillType.NonZero,
                    name = name,
                    fill = SolidColor(Color.Black.copy(alpha = b.alpha)),
                )
            } else {
                addPath(
                    pathData = nodes,
                    pathFillType = PathFillType.NonZero,
                    name = name,
                    fill = null,
                    stroke = SolidColor(Color.Black.copy(alpha = b.alpha)),
                    strokeLineWidth = b.width ?: width,
                    strokeLineCap = StrokeCap.Round,
                    strokeLineJoin = StrokeJoin.Round,
                )
            }
        }
    }.build()

/** SVG 路径块：d = 路径数据，fill = true 表示实心填充，alpha = 不透明度 */
private data class SvgBlock(
    val d: String,
    val fill: Boolean = false,
    val alpha: Float = 1f,
    val width: Float? = null,
)

/**
 * 从设计稿 SVG 直接生成 ImageVector：
 * 支持 <rect>（圆角矩形）与 <path>，保留 fill / opacity / stroke-width。
 */
private fun svgFromMarkup(name: String, markup: String, defaultWidth: Float = 2.2f): ImageVector {
    val blocks = mutableListOf<SvgBlock>()
    // <path d="..." .../>
    val pathRe = Regex("""<path\b([^>]*)/>|<path\b([^>]*)>(.*?)</path>""", RegexOption.DOT_MATCHES_ALL)
    for (m in pathRe.findAll(markup)) {
        val attrs = m.groupValues[1].ifEmpty { m.groupValues[2] }
        val d = Regex("""d="([^"]+)"""").find(attrs)?.groupValues?.get(1) ?: continue
        val fill = Regex("""fill="([^"]+)"""").find(attrs)?.groupValues?.get(1)
        val stroke = Regex("""stroke="([^"]+)"""").find(attrs)?.groupValues?.get(1)
        val op = Regex("""opacity="([^"]+)"""").find(attrs)?.groupValues?.get(1)?.toFloatOrNull() ?: 1f
        val sw = Regex("""stroke-width="([^"]+)"""").find(attrs)?.groupValues?.get(1)?.toFloatOrNull()
        val isFill = fill != null && fill != "none" && stroke == null
        blocks += SvgBlock(d, fill = isFill, alpha = op, width = sw)
    }
    // <rect x y width height rx>
    val rectRe = Regex("""<rect\b([^>]*)/>""")
    for (m in rectRe.findAll(markup)) {
        val attrs = m.groupValues[1]
        fun num(k: String) = Regex("""$k="([^"]+)"""").find(attrs)?.groupValues?.get(1)?.toFloatOrNull() ?: 0f
        val x = num("x"); val y = num("y")
        val w = num("width"); val h = num("height")
        val rx = num("rx")
        val op = Regex("""opacity="([^"]+)"""").find(attrs)?.groupValues?.get(1)?.toFloatOrNull() ?: 1f
        val sw = Regex("""stroke-width="([^"]+)"""").find(attrs)?.groupValues?.get(1)?.toFloatOrNull()
        val fill = Regex("""fill="([^"]+)"""").find(attrs)?.groupValues?.get(1)
        val stroke = Regex("""stroke="([^"]+)"""").find(attrs)?.groupValues?.get(1)
        val isFill = fill != null && fill != "none" && stroke == null
        val d = if (rx > 0f) {
            "M${x + rx} $y h${w - 2 * rx} a$rx $rx 0 0 1 $rx $rx v${h - 2 * rx} a$rx $rx 0 0 1 -$rx $rx h-${w - 2 * rx} a$rx $rx 0 0 1 -$rx -$rx v-${h - 2 * rx} a$rx $rx 0 0 1 $rx -$rx z"
        } else {
            "M$x $y h$w v$h h-$w z"
        }
        blocks += SvgBlock(d, fill = isFill, alpha = op, width = sw)
    }
    // <circle cx cy r>
    val circleRe = Regex("""<circle\b([^>]*)/>""")
    for (m in circleRe.findAll(markup)) {
        val attrs = m.groupValues[1]
        fun num(k: String) = Regex("""$k="([^"]+)"""").find(attrs)?.groupValues?.get(1)?.toFloatOrNull() ?: 0f
        val cx = num("cx"); val cy = num("cy"); val r = num("r")
        val op = Regex("""opacity="([^"]+)"""").find(attrs)?.groupValues?.get(1)?.toFloatOrNull() ?: 1f
        val fill = Regex("""fill="([^"]+)"""").find(attrs)?.groupValues?.get(1)
        val stroke = Regex("""stroke="([^"]+)"""").find(attrs)?.groupValues?.get(1)
        val isFill = fill != null && fill != "none" && stroke == null
        blocks += SvgBlock("M${cx - r} $cy a$r $r 0 1 0 ${2 * r} 0 a$r $r 0 1 0 -${2 * r} 0 z", fill = isFill, alpha = op)
    }
    return svgIcon(name, blocks, width = defaultWidth)
}

/* ══════════════════ 主页信息卡 .ric（19px） ══════════════════ */
object AgHomeIcons {

    /** 管理器版本：斜线刻度 */
    val Version = svgIcon("AgVersion", listOf(SvgBlock("M9 4L7 20M17 4l-2 16M4 9h17M3 15h17")))

    /** 内核版本：CPU 芯片 */
    val Cpu = svgIcon(
        "AgCpu",
        listOf(
            SvgBlock("M8 6h8a2 2 0 0 1 2 2v8a2 2 0 0 1-2 2H8a2 2 0 0 1-2-2V8a2 2 0 0 1 2-2z"),
            SvgBlock("M10 10h4v4h-4z"),
            SvgBlock("M9 2v3M15 2v3M9 19v3M15 19v3M2 9h3M2 15h3M19 9h3M19 15h3"),
        ),
    )

    /** 设备型号：手机 */
    val Phone = svgIcon(
        "AgPhone",
        listOf(
            SvgBlock("M9.5 2.5h5A2.5 2.5 0 0 1 17 5v14a2.5 2.5 0 0 1-2.5 2.5h-5A2.5 2.5 0 0 1 7 19V5a2.5 2.5 0 0 1 2.5-2.5z"),
            SvgBlock("M11 18.5h2"),
        ),
    )

    /** 系统指纹：指纹纹 */
    val Fingerprint = svgIcon(
        "AgFingerprint",
        listOf(
            SvgBlock("M12 11a3 3 0 0 0-3 3c0 2.5-.6 4.4-1.4 5.8"),
            SvgBlock("M12 7.5A6.5 6.5 0 0 0 5.5 14c0 2.3-.4 4.3-1 5.8"),
            SvgBlock("M18.5 14a6.5 6.5 0 0 0-4.2-6.1"),
            SvgBlock("M15 14a3 3 0 0 0-.5-1.7"),
            SvgBlock("M15 17.6c-.3 1.6-.7 3-1.3 4.2"),
        ),
    )

    /** SELinux 状态：盾牌 + 对勾 */
    val ShieldCheck = svgIcon(
        "AgShieldCheck",
        listOf(
            SvgBlock("M12 2.5l7.5 3.5v5.5c0 4.6-3.2 7.9-7.5 9.5-4.3-1.6-7.5-4.9-7.5-9.5V6z"),
            SvgBlock("M9 11.5l2.2 2.2L15.5 9.5"),
        ),
    )

    /** Seccomp 状态：分层横线（末行 55% 透明） */
    val Layers = svgIcon(
        "AgLayers",
        listOf(
            SvgBlock("M4 5h16M4 10h16M4 15h16"),
            SvgBlock("M4 20h8", alpha = 0.55f),
        ),
    )

    /** 支持开发：实心心形 */
    val Heart = svgIcon(
        "AgHeart",
        listOf(SvgBlock("M12 20.8S4.8 16.4 2.9 12.2A5.2 5.2 0 0 1 12 7.3a5.2 5.2 0 0 1 9.1 4.9C19.2 16.4 12 20.8 12 20.8z", fill = true)),
    )

    /** 了解 KernelSU：翻开的书 */
    val Book = svgIcon(
        "AgBook",
        listOf(
            SvgBlock("M12 6.5C10.5 5 8.4 4.5 4 4.5v14c4.4 0 6.5.5 8 2 1.5-1.5 3.6-2 8-2v-14c-4.4 0-6.5.5-8 2z"),
            SvgBlock("M12 6.5v14"),
        ),
    )
}

/* ══════════════════ 顶栏 .icbtn（18px） ══════════════════ */
object AgTopIcons {

    /** 关于作者：用户 */
    val User = svgIcon(
        "AgUser",
        listOf(
            SvgBlock("M12 4a4 4 0 1 1 0 8 4 4 0 0 1 0-8z"),
            SvgBlock("M4 21c1.5-4 5-6 8-6s6.5 2 8 6"),
        ),
    )

    /** 主题：调色板（打开界面风格选择） */
    val Theme = svgIcon(
        "AgTheme",
        listOf(
            SvgBlock(
                "M12 3.5a8.5 8.5 0 1 0 0 17c1.2 0 2-1 2-2.1 0-.6-.2-1.1-.6-1.5" +
                    "-.3-.3-.4-.7-.4-1.1 0-.9.7-1.6 1.6-1.6h2.4c2.3 0 4-1.9 4-4.2 0-3.6-4.1-6.5-9-6.5z"
            ),
            SvgBlock("M8 8.6a1.1 1.1 0 1 0 0 2.2 1.1 1.1 0 0 0 0-2.2z", fill = true),
            SvgBlock("M12 6.6a1.1 1.1 0 1 0 0 2.2 1.1 1.1 0 0 0 0-2.2z", fill = true),
            SvgBlock("M16 8.4a1.1 1.1 0 1 0 0 2.2 1.1 1.1 0 0 0 0-2.2z", fill = true),
        ),
    )

    /** 深浅模式：半月 */
    val DarkMode = svgIcon(
        "AgDarkMode",
        listOf(
            SvgBlock("M12 12a9 9 0 1 0 0-18 9 9 0 0 0 0 18z"),
            SvgBlock("M12 3a9 9 0 0 1 0 18z", fill = true),
        ),
    )

    /** 电源 */
    val Power = svgIcon(
        "AgPower",
        listOf(
            SvgBlock("M12 3v8"),
            SvgBlock("M6 6a8 8 0 1 0 12 0"),
        ),
    )

    /** 返回箭头 */
    val Back = svgIcon(
        "AgBack",
        listOf(
            SvgBlock("M19 12H5"),
            SvgBlock("M12 19l-7-7 7-7"),
        ),
    )

    /** 模块页：导入（箭头落入托盘） */
    val Import = svgIcon(
        "AgImport",
        listOf(
            SvgBlock("M12 4v11M7.5 10.5L12 15l4.5-4.5"),
            SvgBlock("M5 19h14"),
        ),
    )

    /** 超级用户页：App 列表图标 */
    val AppGrid = svgIcon(
        "AgAppGrid",
        listOf(
            SvgBlock("M6 4h5a2 2 0 0 1 2 2v5a2 2 0 0 1-2 2H6a2 2 0 0 1-2-2V6a2 2 0 0 1 2-2z"),
            SvgBlock("M15 11h4a2 2 0 0 1 2 2v6a2 2 0 0 1-2 2h-4a2 2 0 0 1-2-2v-6a2 2 0 0 1 2-2z"),
        ),
    )

    /** 超级用户页：统计（柱状图） */
    val Chart = svgIcon(
        "AgChart",
        listOf(
            SvgBlock("M7 20V8M17 16V4"),
            SvgBlock("M4 11l3-3 3 3M14 16l3-3 3 3"),
        ),
    )

    /** 更多（三点） */
    val More = svgIcon(
        "AgMore",
        listOf(
            SvgBlock("M5 12a1.8 1.8 0 1 0 0-.1z", fill = true),
            SvgBlock("M12 12a1.8 1.8 0 1 0 0-.1z", fill = true),
            SvgBlock("M19 12a1.8 1.8 0 1 0 0-.1z", fill = true),
        ),
    )
}

/* ══════════════════ 设置页 / 定制页 .ric ══════════════════ */
object AgSettingsIcons {

    /** 检查更新：箭头落入托盘 */
    val CheckUpdate = svgIcon(
        "AgCheckUpdate",
        listOf(
            SvgBlock("M12 4v11M7.5 10.5L12 15l4.5-4.5"),
            SvgBlock("M5 19h14"),
        ),
    )

    /** 检查模块更新 / App Profile 模板：双矩形 */
    val Modules = svgIcon(
        "AgModules",
        listOf(
            SvgBlock("M6 4h5a2 2 0 0 1 2 2v5a2 2 0 0 1-2 2H6a2 2 0 0 1-2-2V6a2 2 0 0 1 2-2z"),
            SvgBlock("M13 13h5a2 2 0 0 1 2 2v5a2 2 0 0 1-2 2h-5a2 2 0 0 1-2-2v-5a2 2 0 0 1 2-2z"),
            SvgBlock("M13 7.5h7M7.5 13v7"),
        ),
    )

    /** 界面风格：圆形 + 指针 */
    val Style = svgIcon(
        "AgStyle",
        listOf(
            SvgBlock("M12 12a8.5 8.5 0 1 0 0-17 8.5 8.5 0 0 0 0 17z"),
            SvgBlock("M12 8v5M12 16.2v.1"),
        ),
    )

    /** 定制功能：图片框 */
    val Customize = svgIcon(
        "AgCustomize",
        listOf(
            SvgBlock("M7 3h10a3 3 0 0 1 3 3v12a3 3 0 0 1-3 3H7a3 3 0 0 1-3-3V6a3 3 0 0 1 3-3z"),
            SvgBlock("M14.6 10a2.6 2.6 0 1 0 0-5.2 2.6 2.6 0 0 0 0 5.2z"),
            SvgBlock("M7.5 18c1-2.4 2.7-3.6 4.5-3.6s3.5 1.2 4.5 3.6"),
        ),
    )

    /** 超级用户：盾牌 */
    val Shield = svgIcon("AgShield", listOf(SvgBlock("M12 2l8 4v6c0 5-3.5 8.5-8 10-4.5-1.5-8-5-8-10V6z")))

    /** 卸载模块（内核级）：月牙 */
    val Moon = svgIcon(
        "AgMoon",
        listOf(SvgBlock("M20 12.5A8 8 0 1 1 11.5 4 6.2 6.2 0 0 0 20 12.5z")),
    )

    /** 模块自动更新：拼图 */
    val Puzzle = svgIcon(
        "AgPuzzle",
        listOf(SvgBlock("M10 4.5a1.8 1.8 0 1 1 3.6 0V6H17a1.5 1.5 0 0 1 1.5 1.5v3.2h-1.6a1.8 1.8 0 1 0 0 3.6h1.6V17A1.5 1.5 0 0 1 17 18.5h-3.3V17a1.8 1.8 0 1 0-3.6 0v1.5H6.5A1.5 1.5 0 0 1 5 17v-3.3h1.4a1.8 1.8 0 1 0 0-3.6H5V7.5A1.5 1.5 0 0 1 6.5 6H10V4.5z", width = 2f)),
        width = 2f,
    )

    /** 强制执行 App Profile / App Profile：人像 */
    val UserProfile = svgIcon(
        "AgUserProfile",
        listOf(
            SvgBlock("M12 8.5a4 4 0 1 0 0-8 4 4 0 0 0 0 8z"),
            SvgBlock("M5 20c1.4-3.6 4.4-5.5 7-5.5s5.6 1.9 7 5.5"),
        ),
    )

    /** 传统 SU 命令支持：齿轮 */
    val Gear = svgIcon(
        "AgGear",
        listOf(
            SvgBlock("M12 12a3.2 3.2 0 1 0 0-6.4 3.2 3.2 0 0 0 0 6.4z"),
            SvgBlock("M12 2.5v3M12 18.5v3M2.5 12h3M18.5 12h3M5.3 5.3l2.1 2.1M16.6 16.6l2.1 2.1M18.7 5.3l-2.1 2.1M7.4 16.6l-2.1 2.1"),
        ),
        width = 2f,
    )

    /** 隐藏 SELinux 修改：眼 + 斜线 */
    val EyeOff = svgIcon(
        "AgEyeOff",
        listOf(
            SvgBlock("M2 12s3.5-6.5 10-6.5S22 12 22 12s-3.5 6.5-10 6.5S2 12 2 12z"),
            SvgBlock("M14.8 12a2.8 2.8 0 1 1-5.6 0 2.8 2.8 0 0 1 5.6 0z"),
            SvgBlock("M4 20L20 4"),
        ),
    )

    /** SU 日志：文档横线 */
    val Log = svgIcon(
        "AgLog",
        listOf(SvgBlock("M4 5h16M4 10h16M4 15h10M4 20h6")),
    )

    /** 使用软重启代替重启：虚线电源 */
    val SoftReboot = svgIcon(
        "AgSoftReboot",
        listOf(
            SvgBlock("M12 3v8"),
            SvgBlock("M6 6a8 8 0 1 0 12 0"),
        ),
    )

    /** FoxSu Manager：盾牌 */
    val Manager = svgIcon("AgManager", listOf(SvgBlock("M12 2l8 4v6c0 5-3.5 8.5-8 10-4.5-1.5-8-5-8-10V6z")))

    /** GitHub 仓库：GitHub 标志 */
    val Github = svgIcon(
        "AgGithub",
        listOf(
            SvgBlock("M9 19c-4.5 1.5-4.5-2.5-6.5-3M15 21v-3.2c0-1 .1-1.4-.5-2 2.8-.3 5.5-1.4 5.5-6a4.6 4.6 0 0 0-1.3-3.2 4.3 4.3 0 0 0-.1-3.2s-1-.3-3.4 1.3a11.8 11.8 0 0 0-6.4 0C6.4 3.1 5.4 3.4 5.4 3.4a4.3 4.3 0 0 0-.1 3.2A4.6 4.6 0 0 0 4 9.8c0 4.6 2.7 5.7 5.5 6-.6.6-.6 1.2-.5 2V21"),
        ),
    )

    /** 加入频道：对话框 */
    val Chat = svgIcon(
        "AgChat",
        listOf(
            SvgBlock("M12 12a8.5 8.5 0 1 0 0-17 8.5 8.5 0 0 0 0 17z"),
            SvgBlock("M8 9.5h.1M8 14.5h.1M12 9.5h.1M12 14.5h.1M16 9.5h.1M16 14.5h.1"),
        ),
    )

    /** 使用本地模板：文档 */
    val LocalTemplate = svgIcon(
        "AgLocalTemplate",
        listOf(
            SvgBlock("M7 3h10a4 4 0 0 1 4 4v10a4 4 0 0 1-4 4H7a4 4 0 0 1-4-4V7a4 4 0 0 1 4-4z"),
            SvgBlock("M8 9h8M8 13h5"),
        ),
    )

    /** 导出当前配置：向下箭头 + 底线 */
    val Export = svgIcon(
        "AgExport",
        listOf(
            SvgBlock("M12 3v12M7 10l5 5 5-5"),
            SvgBlock("M4 21h16"),
        ),
    )

    /** 启用 Monet 颜色：三色圆 */
    val Monet = svgIcon(
        "AgMonet",
        listOf(
            SvgBlock("M8 9a5 5 0 1 0 0-.1z", fill = true, alpha = 0.85f),
            SvgBlock("M15.5 8a4 4 0 1 0 0-.1z", fill = true, alpha = 0.55f),
            SvgBlock("M12 15.5a5.5 5.5 0 1 0 0-.1z", fill = true, alpha = 0.7f),
        ),
    )

    /** 模糊：点阵 */
    val Blur = svgIcon(
        "AgBlur",
        listOf(
            SvgBlock("M6 6a1.3 1.3 0 1 0 0-.1z", fill = true),
            SvgBlock("M12 6a1.3 1.3 0 1 0 0-.1z", fill = true),
            SvgBlock("M18 6a1.3 1.3 0 1 0 0-.1z", fill = true),
            SvgBlock("M9 10a1.3 1.3 0 1 0 0-.1z", fill = true),
            SvgBlock("M15 10a1.3 1.3 0 1 0 0-.1z", fill = true),
            SvgBlock("M6 14a1.3 1.3 0 1 0 0-.1z", fill = true),
            SvgBlock("M12 14a1.3 1.3 0 1 0 0-.1z", fill = true),
            SvgBlock("M18 14a1.3 1.3 0 1 0 0-.1z", fill = true),
            SvgBlock("M9 18a1.3 1.3 0 1 0 0-.1z", fill = true),
            SvgBlock("M15 18a1.3 1.3 0 1 0 0-.1z", fill = true),
        ),
    )

    /** 悬浮底栏：胶囊 */
    val FloatingBar = svgIcon(
        "AgFloatingBar",
        listOf(
            SvgBlock("M6 14h12a3 3 0 0 1 3 3v0a3 3 0 0 1-3 3H6a3 3 0 0 1-3-3v0a3 3 0 0 1 3-3z"),
            SvgBlock("M7 17h5", alpha = 0.6f),
        ),
    )

    /** 水滴流动指示器：水滴 + 落地涟漪 */
    val WaterDrop = svgIcon(
        "AgWaterDrop",
        listOf(
            SvgBlock("M12 3.5c3.2 3.6 5.3 6.4 5.3 9a5.3 5.3 0 0 1-10.6 0c0-2.6 2.1-5.4 5.3-9z"),
            SvgBlock("M8.6 12.6a3.4 3.4 0 0 0 3.4 3.4", alpha = 0.55f),
        ),
    )

    /** 主页氛围：一枚爪印（掌垫 + 四趾） */
    val Paw = svgIcon(
        "AgPaw",
        listOf(
            SvgBlock("M12 12.6c2.5 0 4.4 1.8 4.4 3.7 0 2-2 3.3-4.4 3.3s-4.4-1.3-4.4-3.3c0-1.9 1.9-3.7 4.4-3.7z"),
            SvgBlock("M5.85 8a1.55 1.55 0 1 0 3.1 0a1.55 1.55 0 1 0 -3.1 0"),
            SvgBlock("M8.6 6.2a1.7 1.7 0 1 0 3.4 0a1.7 1.7 0 1 0 -3.4 0"),
            SvgBlock("M12 6.2a1.7 1.7 0 1 0 3.4 0a1.7 1.7 0 1 0 -3.4 0"),
            SvgBlock("M15.05 8a1.55 1.55 0 1 0 3.1 0a1.55 1.55 0 1 0 -3.1 0"),
        ),
    )

    /** 导航栏角标：计数框 */
    val Badge = svgIcon(
        "AgBadgeIcon",
        listOf(
            SvgBlock("M6.5 6h11a4 4 0 0 1 4 4v4a4 4 0 0 1-4 4h-11a4 4 0 0 1-4-4v-4a4 4 0 0 1 4-4z", width = 2f),
        ),
    )

    /** 预测性返回手势 */
    val PredictiveBack = svgIcon(
        "AgPredictiveBack",
        listOf(
            SvgBlock("M9 6l-6 6 6 6"),
            SvgBlock("M12 18h4M17.5 18h1M20.5 18h.5", alpha = 0.7f),
        ),
    )

    /** 横移返回手势：手指 */
    val SwipeBack = svgIcon(
        "AgSwipeBack",
        listOf(
            SvgBlock("M8 11V5.5a1.5 1.5 0 0 1 3 0V11"),
            SvgBlock("M11 11V4.5a1.5 1.5 0 0 1 3 0V11"),
            SvgBlock("M14 11V6a1.5 1.5 0 0 1 3 0v6.5c0 4-2.5 6.5-6 6.5-2.8 0-4.2-1.2-5.8-3.6L3.6 13c-.8-1.2.6-2.6 1.8-1.8L8 13"),
        ),
    )

    /** 页面切换手势：多矩形 */
    val PagerGesture = svgIcon(
        "AgPagerGesture",
        listOf(
            SvgBlock("M5.5 6h5a2.5 2.5 0 0 1 2.5 2.5v5a2.5 2.5 0 0 1-2.5 2.5h-5A2.5 2.5 0 0 1 3 13.5v-5A2.5 2.5 0 0 1 5.5 6z"),
            SvgBlock("M15.5 3h3a2.5 2.5 0 0 1 2.5 2.5v3a2.5 2.5 0 0 1-2.5 2.5h-3A2.5 2.5 0 0 1 13 8.5v-3A2.5 2.5 0 0 1 15.5 3z", alpha = 0.55f),
            SvgBlock("M15.5 14h3a2.5 2.5 0 0 1 2.5 2.5v3a2.5 2.5 0 0 1-2.5 2.5h-3A2.5 2.5 0 0 1 13 19.5v-3A2.5 2.5 0 0 1 15.5 14z", alpha = 0.55f),
        ),
    )

    /** 壁纸 / 图片 */
    val Wallpaper = svgIcon(
        "AgWallpaper",
        listOf(
            SvgBlock("M7 3h10a3 3 0 0 1 3 3v12a3 3 0 0 1-3 3H7a3 3 0 0 1-3-3V6a3 3 0 0 1 3-3z"),
            SvgBlock("M14.6 10a2.6 2.6 0 1 0 0-5.2 2.6 2.6 0 0 0 0 5.2z"),
            SvgBlock("M7.5 18c1-2.4 2.7-3.6 4.5-3.6s3.5 1.2 4.5 3.6"),
        ),
    )
}

/* ══════════════════ Dock 底栏图标 .dit（22px） ══════════════════
 * 与 bottombar/DockGlyphs.kt 保持同一份几何：形状以设计稿逐像素校准，
 * 两处若有改动必须同步（DockGlyphs 是底栏实际渲染用的那份）。
 */
object AgDockIcons {

    /** 主页：扁尖顶 + 外伸屋檐 + 方房身，底边圆角闭合 */
    val Home = svgIcon(
        "AgDockHome",
        listOf(
            SvgBlock("M2.6 12.6 12 4.3l9.4 8.3"),
            SvgBlock("M5.2 10.4v8.6a1.6 1.6 0 0 0 1.6 1.6h10.4a1.6 1.6 0 0 0 1.6-1.6v-8.6"),
        ),
    )

    /** 超级用户：顶部圆拱盾牌 + 内部对勾 */
    val SuperUser = svgIcon(
        "AgDockSu",
        listOf(
            SvgBlock("M4 7.9C5.7 6.1 8.7 4.6 12 4.6s6.3 1.5 8 3.3V13c0 4-3.7 6.6-8 8.1-4.3-1.5-8-4.1-8-8.1z"),
            SvgBlock("M8.9 12.6l2.3 2.3 4.4-4.4"),
        ),
    )

    /** 模块：拼图块（上凸、右凸、下凹、左凹） */
    val Modules = svgIcon(
        "AgDockMod",
        listOf(SvgBlock("M6 4.4h3.6a2.2 2.2 0 1 1 4.8 0H18a1.8 1.8 0 0 1 1.8 1.8v3.6a2.2 2.2 0 1 1 0 4.8v3.2a1.8 1.8 0 0 1-1.8 1.8h-3.6a2.2 2.2 0 1 0-4.8 0H6a1.8 1.8 0 0 1-1.8-1.8v-3.2a2.2 2.2 0 1 0 0-4.8V6.2A1.8 1.8 0 0 1 6 4.4z", width = 2f)),
        width = 2f,
    )

    /** 设置：太阳（中心圆 r3.9 + 八向短射线） */
    val Settings = svgIcon(
        "AgDockSet",
        listOf(
            SvgBlock("M12 8a4 4 0 1 1 0 8 4 4 0 1 1 0-8z"),
            SvgBlock("M12 2.8v3.2M12 18v3.2M2.8 12h3.2M18 12h3.2M5.6 5.6l2.2 2.2M16.2 16.2l2.2 2.2M18.4 5.6l-2.2 2.2M7.8 16.2l-2.2 2.2"),
        ),
        width = 2.1f,
    )
}

/**
 * 元模块用的图标。与 AgSettingsIcons 同一套画法（24×24、round cap、stroke 2.2），
 * 语义是「层叠的挂载层」。
 */
object AgEngineIcons {

    /** 元模块：三层叠放（挂载） */
    val Mount = svgIcon(
        "AgEngineMount",
        listOf(
            SvgBlock("M12 3.6 20 8l-8 4.4L4 8z"),
            SvgBlock("M4.4 12.2 12 16.4l7.6-4.2"),
            SvgBlock("M4.4 16.2 12 20.4l7.6-4.2"),
        ),
    )
}
