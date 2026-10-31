package me.diksu.ultra.foxsu.ui.component.bottombar

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp
import me.diksu.ultra.foxsu.data.settings.CustomizationStore


/**
 * 底栏图标 20 套：设计稿 `foxsu-dock-icons20` 的原样迁移。
 *
 * 每套是独立的造型语言（建筑剖面 / 折纸 / 像素 / 印章 …），不是同一图形的粗细变体。
 * 四支图标固定顺序 首页 / 超级用户 / 模块 / 设置，与 [BottomBarDestination] 一一对应。
 *
 * 路径数据原样保留 SVG 的 `d`，圆与矩形在生成阶段已折算成等效路径——
 * Compose 的 PathParser 不认 `<circle>`，运行时也不该再拼几何。
 * 描边色统一写黑，实际颜色由 `Icon(tint = ...)` 的 ColorFilter 覆盖。
 */
internal data class FxPath(
    val d: String,
    /** true 时用实心填充 */
    val fill: Boolean = false,
    /** false 表示这条只填充不描边（SVG 的 stroke="none"） */
    val stroke: Boolean = true,
    val alpha: Float = 1f,
    /** null 表示沿用本套的默认线宽 / 端点 / 拐角 */
    val width: Float? = null,
    val cap: StrokeCap? = null,
    val join: StrokeJoin? = null,
)

internal data class DockIconPack(
    val no: Int,
    val name: String,
    val tag: String,
    val desc: String,
    val stroke: Float,
    val cap: StrokeCap,
    val join: StrokeJoin,
    /** 顺序固定：Home / SuperUser / Module / Setting */
    val icons: List<List<FxPath>>,
)

private val pack01 = DockIconPack(
    no = 1, name = "建筑剖面", tag = "结构",
    desc = "把房屋画成侧剖面：屋顶斜线、楼层分隔、柱子与门窗都画出来。像一张建筑立面图纸。",
    stroke = 1.8f, cap = StrokeCap.Round, join = StrokeJoin.Round,
    icons = listOf(
        // Home
        listOf(
            FxPath("M2.5 21V11.5L12 4l9.5 7.5V21z"),
            FxPath("M6.5 21v-6.5h4V21M13.5 21v-6.5h4V21"),
            FxPath("M2.5 11.5L12 4"),
            FxPath("M12 4l9.5 7.5"),
            FxPath("M2.5 14.6h19"),
        ),
        // SuperUser
        listOf(
            FxPath("M12 2.6l8 2.9v7.2c0 5.3-3.4 8.6-8 9.7-4.6-1.1-8-4.4-8-9.7V5.5z"),
            FxPath("M4 9.2h16M4 14.8h16"),
            FxPath("M12 2.6v17.8"),
            FxPath("M8 5.4v14"),
        ),
        // Module
        listOf(
            FxPath("M3 3h18v18H3z"),
            FxPath("M3 9h18M3 15h18M9 3v18M15 3v18"),
            FxPath("M3 3l6 6M15 3l6 6M3 15l6 6M15 15l6 6", alpha = 0.45f),
        ),
        // Setting
        listOf(
            FxPath("M3.4 12a8.6 8.6 0 1 0 17.2 0a8.6 8.6 0 1 0 -17.2 0Z"),
            FxPath("M8.6 12a3.4 3.4 0 1 0 6.8 0a3.4 3.4 0 1 0 -6.8 0Z"),
            FxPath("M12 3.4V8m0 8v4.6M3.4 12H8m8 0h4.6"),
            FxPath("M5.9 5.9L9.2 9.2m5.6 5.6l3.3 3.3M5.9 18.1L9.2 14.9m5.6-5.7l3.3-3.3", alpha = 0.5f),
        ),
    ),
)

private val pack02 = DockIconPack(
    no = 2, name = "折纸", tag = "折角",
    desc = "全部由直线折角构成，没有一条曲线，靠折痕的明暗面（半透明填充）表现立体。",
    stroke = 1.9f, cap = StrokeCap.Round, join = StrokeJoin.Round,
    icons = listOf(
        // Home
        listOf(
            FxPath("M12 3.2L21 11v9.8h-6.4V14H9.4v6.8H3V11z"),
            FxPath("M12 3.2L12 16.5L21 11z", fill = true, stroke = false, alpha = 0.16f),
            FxPath("M12 16.5L3 11L9.4 14V20.8", alpha = 0.5f),
        ),
        // SuperUser
        listOf(
            FxPath("M12 2.8L20 6l-.6 8.2L12 21.4l-7.4-7.2L4 6z"),
            FxPath("M12 2.8L12 21.4L20 6z", fill = true, stroke = false, alpha = 0.14f),
            FxPath("M8.6 11.6l3 3.2l4-4.6"),
        ),
        // Module
        listOf(
            FxPath("M3 3l9 6l9-6v18l-9-6l-9 6z"),
            FxPath("M3 3l9 6l9-6"),
            FxPath("M12 9v12", alpha = 0.45f),
        ),
        // Setting
        listOf(
            FxPath("M12 2.6l2.2 3l3.6-.6l1 3.5l3.4 1.6l-1.6 3.3l1.6 3.3l-3.4 1.6l-1 3.5l-3.6-.6l-2.2 3l-2.2-3l-3.6.6l-1-3.5L1.8 17l1.6-3.3L1.8 10.4L5.2 8.8l1-3.5l3.6.6z"),
            FxPath("M12 8.6l2.6 1.6v3.6L12 15.4l-2.6-1.6V10.2z"),
        ),
    ),
)

private val pack03 = DockIconPack(
    no = 3, name = "地图俯视", tag = "俯视",
    desc = "从正上方看下去的视角：街道网格、地块分区、定位点。像地图 App 里的一个标注。",
    stroke = 1.9f, cap = StrokeCap.Round, join = StrokeJoin.Round,
    icons = listOf(
        // Home
        listOf(
            FxPath("M2 7.4l6-2.2l8 2.6l6-2.2v13.6l-6 2.2l-8-2.6l-6 2.2z"),
            FxPath("M8 5.2v15.4M16 7.8v15.4"),
            FxPath("M10.5 12a1.5 1.5 0 1 0 3 0a1.5 1.5 0 1 0 -3 0Z", fill = true, stroke = false),
        ),
        // SuperUser
        listOf(
            FxPath("M12 2.4c4.4 0 8 3.2 8 7.2c0 5.2-8 12-8 12s-8-6.8-8-12c0-4 3.6-7.2 8-7.2z"),
            FxPath("M4.4 9.6h15.2M4.6 14h14.8", alpha = 0.5f),
            FxPath("M12 2.4v9.2", alpha = 0.5f),
            FxPath("M9.6 9.6a2.4 2.4 0 1 0 4.8 0a2.4 2.4 0 1 0 -4.8 0Z", fill = true, stroke = false),
        ),
        // Module
        listOf(
            FxPath("M2.6 2.6h18.8v18.8H2.6z"),
            FxPath("M8.6 2.6v5.4M15.4 2.6v5.4M8.6 16v5.4M15.4 16v5.4M2.6 8.6h5.4M16 8.6h5.4M2.6 15.4h5.4M16 15.4h5.4"),
            FxPath("M8.6 8.6h6.8v6.8h-6.8Z", fill = true, stroke = false, alpha = 0.2f),
        ),
        // Setting
        listOf(
            FxPath("M12 2.8l3 2.4l3.8-.8l.8 3.8l2.4 3l-2.4 3l-.8 3.8l-3.8-.8l-3 2.4l-3-2.4l-3.8.8l-.8-3.8L2 12l2.4-3l.8-3.8l3.8.8z"),
            FxPath("M9.4 12a2.6 2.6 0 1 0 5.2 0a2.6 2.6 0 1 0 -5.2 0Z"),
            FxPath("M12 9.4v5.2M9.4 12h5.2", alpha = 0.55f),
        ),
    ),
)

private val pack04 = DockIconPack(
    no = 4, name = "像素 8-bit", tag = "像素",
    desc = "纯方块拼成，所有转角 90°，最小单位 2px。复古游戏机风格，极客感最强。",
    stroke = 2f, cap = StrokeCap.Butt, join = StrokeJoin.Miter,
    icons = listOf(
        // Home
        listOf(
            FxPath("M10 2h4v2h2v2h2v2h2v2h2v12H2V10h2V8h2V6h2V4h2z", join = StrokeJoin.Miter),
            FxPath("M9 22v-6h6v6", join = StrokeJoin.Miter),
            FxPath("M12 8h0", cap = StrokeCap.Butt),
        ),
        // SuperUser
        listOf(
            FxPath("M8 2h8v2h4v2h2v8h-2v4h-2v2h-2v2h-4v-2H8v-2H6v-4H4V6h2V4h2z", join = StrokeJoin.Miter),
            FxPath("M9 12h2v2h2v-2h2v-2h2v2h-2v2h-2v2h-2v-2H9z", fill = true, stroke = false),
        ),
        // Module
        listOf(
            FxPath("M2 2h9v9h-9Z"),
            FxPath("M13 2h9v9h-9Z"),
            FxPath("M2 13h9v9h-9Z"),
            FxPath("M13 13h9v9h-9Z"),
            FxPath("M14 14h3v3h-3Z", fill = true, stroke = false),
        ),
        // Setting
        listOf(
            FxPath("M9 2h6v2h2v2h2v2h2v2h1v4h-1v2h-2v2h-2v2h-2v2H9v-2H7v-2H5v-2H3v-2H2v-4h1V8h2V6h2V4h2z", join = StrokeJoin.Miter),
            FxPath("M9 9h6v6h-6Z", fill = true, stroke = false),
        ),
    ),
)

private val pack05 = DockIconPack(
    no = 5, name = "便签贴纸", tag = "贴纸",
    desc = "每个图标是一张带折角的便签纸，内部画粗线条简图。整体像一个贴纸，可爱且统一。",
    stroke = 2f, cap = StrokeCap.Round, join = StrokeJoin.Round,
    icons = listOf(
        // Home
        listOf(
            FxPath("M4 4.6A2.6 2.6 0 0 1 6.6 2h10.8A2.6 2.6 0 0 1 20 4.6v8.2L14.8 22H6.6A2.6 2.6 0 0 1 4 19.4z", width = 1.6f),
            FxPath("M20 12.8h-3.6A1.6 1.6 0 0 0 14.8 14.4V22", width = 1.6f),
            FxPath("M7.6 13.4l2.6-2.4l2.2 2l2-1.6", width = 2.4f),
        ),
        // SuperUser
        listOf(
            FxPath("M4 4.6A2.6 2.6 0 0 1 6.6 2h10.8A2.6 2.6 0 0 1 20 4.6V19.4A2.6 2.6 0 0 1 17.4 22H6.6A2.6 2.6 0 0 1 4 19.4z", width = 1.6f),
            FxPath("M12 6l4.4 1.7v3.9c0 2.8-1.9 5.3-4.4 6.2-2.5-.9-4.4-3.4-4.4-6.2V7.7z", width = 2.2f),
        ),
        // Module
        listOf(
            FxPath("M4 4.6A2.6 2.6 0 0 1 6.6 2h10.8A2.6 2.6 0 0 1 20 4.6V19.4A2.6 2.6 0 0 1 17.4 22H6.6A2.6 2.6 0 0 1 4 19.4z", width = 1.6f),
            FxPath("M8 8h3M13 8h3M8 12h3M13 12h3M8 16h3M13 16h3", width = 2.4f, cap = StrokeCap.Round),
        ),
        // Setting
        listOf(
            FxPath("M4 4.6A2.6 2.6 0 0 1 6.6 2h10.8A2.6 2.6 0 0 1 20 4.6V19.4A2.6 2.6 0 0 1 17.4 22H6.6A2.6 2.6 0 0 1 4 19.4z", width = 1.6f),
            FxPath("M9 12a3 3 0 1 0 6 0a3 3 0 1 0 -6 0Z", width = 2.2f),
            FxPath("M12 7.4v1.6m0 6v1.6M7.4 12h1.6m6 0h1.6", width = 2.2f),
        ),
    ),
)

private val pack06 = DockIconPack(
    no = 6, name = "分子结构", tag = "节点",
    desc = "用「节点 + 连线」表现图形：家庭是几个相连的球体、盾牌是中心节点+五个卫星节点。",
    stroke = 2f, cap = StrokeCap.Round, join = StrokeJoin.Round,
    icons = listOf(
        // Home
        listOf(
            FxPath("M10 4.6a2 2 0 1 0 4 0a2 2 0 1 0 -4 0Z"),
            FxPath("M2.6 16a2 2 0 1 0 4 0a2 2 0 1 0 -4 0Z"),
            FxPath("M17.4 16a2 2 0 1 0 4 0a2 2 0 1 0 -4 0Z"),
            FxPath("M9.8 13a2.2 2.2 0 1 0 4.4 0a2.2 2.2 0 1 0 -4.4 0Z"),
            FxPath("M10.4 20.4a1.6 1.6 0 1 0 3.2 0a1.6 1.6 0 1 0 -3.2 0Z"),
            FxPath("M12 6.6v4.2M10.6 14.4L6 15.4M13.4 14.4l4.6 1M12 15.2v3.6"),
        ),
        // SuperUser
        listOf(
            FxPath("M10 3.6a2 2 0 1 0 4 0a2 2 0 1 0 -4 0Z"),
            FxPath("M2.4 9.4a2 2 0 1 0 4 0a2 2 0 1 0 -4 0Z"),
            FxPath("M17.6 9.4a2 2 0 1 0 4 0a2 2 0 1 0 -4 0Z"),
            FxPath("M5.4 17a2 2 0 1 0 4 0a2 2 0 1 0 -4 0Z"),
            FxPath("M14.6 17a2 2 0 1 0 4 0a2 2 0 1 0 -4 0Z"),
            FxPath("M9.4 12.6a2.6 2.6 0 1 0 5.2 0a2.6 2.6 0 1 0 -5.2 0Z"),
            FxPath("M12 5.6v4.4M10.4 10.8L6.2 9.8M13.6 10.8l4.2-1M10.8 14.6L8.4 15.8M13.2 14.6l2.4 1.2"),
        ),
        // Module
        listOf(
            FxPath("M3.6 6a2.4 2.4 0 1 0 4.8 0a2.4 2.4 0 1 0 -4.8 0Z"),
            FxPath("M15.6 6a2.4 2.4 0 1 0 4.8 0a2.4 2.4 0 1 0 -4.8 0Z"),
            FxPath("M3.6 18a2.4 2.4 0 1 0 4.8 0a2.4 2.4 0 1 0 -4.8 0Z"),
            FxPath("M15.6 18a2.4 2.4 0 1 0 4.8 0a2.4 2.4 0 1 0 -4.8 0Z"),
            FxPath("M9.6 12a2.4 2.4 0 1 0 4.8 0a2.4 2.4 0 1 0 -4.8 0Z"),
            FxPath("M7.7 7.7l2.6 2.6M16.3 7.7l-2.6 2.6M7.7 16.3l2.6-2.6M16.3 16.3l-2.6-2.6"),
        ),
        // Setting
        listOf(
            FxPath("M10.1 3.8a1.9 1.9 0 1 0 3.8 0a1.9 1.9 0 1 0 -3.8 0Z"),
            FxPath("M10.1 20.2a1.9 1.9 0 1 0 3.8 0a1.9 1.9 0 1 0 -3.8 0Z"),
            FxPath("M1.9 12a1.9 1.9 0 1 0 3.8 0a1.9 1.9 0 1 0 -3.8 0Z"),
            FxPath("M18.3 12a1.9 1.9 0 1 0 3.8 0a1.9 1.9 0 1 0 -3.8 0Z"),
            FxPath("M4.3 6.2a1.9 1.9 0 1 0 3.8 0a1.9 1.9 0 1 0 -3.8 0Z"),
            FxPath("M15.9 6.2a1.9 1.9 0 1 0 3.8 0a1.9 1.9 0 1 0 -3.8 0Z"),
            FxPath("M4.3 17.8a1.9 1.9 0 1 0 3.8 0a1.9 1.9 0 1 0 -3.8 0Z"),
            FxPath("M15.9 17.8a1.9 1.9 0 1 0 3.8 0a1.9 1.9 0 1 0 -3.8 0Z"),
            FxPath("M8.8 12a3.2 3.2 0 1 0 6.4 0a3.2 3.2 0 1 0 -6.4 0Z"),
            FxPath("M12 5.7v3.1M12 15.2v3.1M5.7 12h3.1M15.2 12h3.1"),
        ),
    ),
)

private val pack07 = DockIconPack(
    no = 7, name = "仪表刻度", tag = "表盘",
    desc = "以半圆表盘为骨架：刻度线、指针、底座。四签分别是家（指针屋）、守护（半盾）、模块（四格表）、齿轮（同心表）。",
    stroke = 2f, cap = StrokeCap.Round, join = StrokeJoin.Round,
    icons = listOf(
        // Home
        listOf(
            FxPath("M2.4 18a9.6 9.6 0 0 1 19.2 0"),
            FxPath("M12 18V9.6"),
            FxPath("M12 9.6l3.6 2.6", alpha = 0.55f),
            FxPath("M4.4 13.2l1.8.8M8 8.4l1.1 1.6M16 8.4l-1.1 1.6M19.6 13.2l-1.8.8M12 6.4V4.6"),
            FxPath("M3 20.8h18"),
        ),
        // SuperUser
        listOf(
            FxPath("M12 3a9 9 0 0 1 9 9v6.4"),
            FxPath("M3 18.4V12a9 9 0 0 1 9-9"),
            FxPath("M12 12l-3.4 4.4"),
            FxPath("M10.6 12a1.4 1.4 0 1 0 2.8 0a1.4 1.4 0 1 0 -2.8 0Z", fill = true, stroke = false),
            FxPath("M5.4 15.6l1.6.9M7.6 9.4l1.4 1.2M16.4 9.4l-1.4 1.2M18.6 15.6l-1.6.9"),
            FxPath("M9.4 18.4h5.2"),
        ),
        // Module
        listOf(
            FxPath("M3 12a9 9 0 1 0 18 0a9 9 0 1 0 -18 0Z"),
            FxPath("M12 3v3M12 18v3M3 12h3M18 12h3M5.6 5.6l2.1 2.1M16.3 16.3l2.1 2.1M5.6 18.4l2.1-2.1M16.3 7.7l2.1-2.1"),
            FxPath("M12 12l3.4-3.4"),
            FxPath("M10.7 12a1.3 1.3 0 1 0 2.6 0a1.3 1.3 0 1 0 -2.6 0Z", fill = true, stroke = false),
        ),
        // Setting
        listOf(
            FxPath("M3 12a9 9 0 1 0 18 0a9 9 0 1 0 -18 0Z"),
            FxPath("M12 5.2v2.2M12 16.6v2.2M5.2 12h2.2M16.6 12h2.2M7.2 7.2l1.6 1.6M15.2 15.2l1.6 1.6M7.2 16.8l1.6-1.6M15.2 8.8l1.6-1.6"),
            FxPath("M9 12a3 3 0 1 0 6 0a3 3 0 1 0 -6 0Z"),
            FxPath("M12 12l1.8-1.8"),
        ),
    ),
)

private val pack08 = DockIconPack(
    no = 8, name = "圆环叠加", tag = "维恩图",
    desc = "多个圆环错位叠加构成图形，像维恩图。没有直线，全部是圆弧与它们的交叠。",
    stroke = 2f, cap = StrokeCap.Round, join = StrokeJoin.Round,
    icons = listOf(
        // Home
        listOf(
            FxPath("M6 9.6a6 6 0 1 0 12 0a6 6 0 1 0 -12 0Z"),
            FxPath("M6.6 13.4v8h10.8v-8"),
            FxPath("M9.4 21.4v-5.2h5.2v5.2"),
            FxPath("M10 9.6a2 2 0 1 0 4 0a2 2 0 1 0 -4 0Z", fill = true, stroke = false, alpha = 0.35f),
        ),
        // SuperUser
        listOf(
            FxPath("M3 12a9 9 0 1 0 18 0a9 9 0 1 0 -18 0Z"),
            FxPath("M6.4 12a5.6 5.6 0 1 0 11.2 0a5.6 5.6 0 1 0 -11.2 0Z"),
            FxPath("M12 3v18M3 12h18", alpha = 0.35f),
            FxPath("M7.6 12.4l2.6 2.6l4.4-5"),
        ),
        // Module
        listOf(
            FxPath("M3.4 9a5.6 5.6 0 1 0 11.2 0a5.6 5.6 0 1 0 -11.2 0Z"),
            FxPath("M9.4 9a5.6 5.6 0 1 0 11.2 0a5.6 5.6 0 1 0 -11.2 0Z"),
            FxPath("M3.4 15a5.6 5.6 0 1 0 11.2 0a5.6 5.6 0 1 0 -11.2 0Z"),
            FxPath("M9.4 15a5.6 5.6 0 1 0 11.2 0a5.6 5.6 0 1 0 -11.2 0Z"),
            FxPath("M12 9h0"),
        ),
        // Setting
        listOf(
            FxPath("M2.8 12a9.2 9.2 0 1 0 18.4 0a9.2 9.2 0 1 0 -18.4 0Z"),
            FxPath("M6.6 12a5.4 5.4 0 1 0 10.8 0a5.4 5.4 0 1 0 -10.8 0Z"),
            FxPath("M10.2 12a1.8 1.8 0 1 0 3.6 0a1.8 1.8 0 1 0 -3.6 0Z"),
            FxPath("M12 2.8l0 3.6M12 17.6v3.6M2.8 12h3.6M17.6 12h3.6M5.4 5.4l2.6 2.6M16 16l2.6 2.6M5.4 18.6l2.6-2.6M16 8l2.6-2.6", alpha = 0.45f),
        ),
    ),
)

private val pack09 = DockIconPack(
    no = 9, name = "断层切片", tag = "错位",
    desc = "一条斜切面把图形切成上下两块并轻微错位，形成断裂感，像地质断层面。",
    stroke = 2f, cap = StrokeCap.Round, join = StrokeJoin.Round,
    icons = listOf(
        // Home
        listOf(
            FxPath("M3 11L12 4l9 7"),
            FxPath("M3 11h7"),
            FxPath("M14 11h7"),
            FxPath("M4 21V13.4h5.2V21M14.8 21v-7.6H20V21"),
            FxPath("M9.4 11l1.4 2.4h2.4L14.6 11", alpha = 0.55f),
        ),
        // SuperUser
        listOf(
            FxPath("M12 3.4l7.2 2.8v6"),
            FxPath("M4.8 12.2v3.6c0 4 3 6.6 7.2 7.6"),
            FxPath("M19.2 12.2c0 4-3 6.6-7.2 7.6"),
            FxPath("M3.4 11.4l17.2-1.4", alpha = 0.6f, width = 1.2f),
            FxPath("M9.6 13.6l2.4 2.4l3.2-3.4"),
        ),
        // Module
        listOf(
            FxPath("M3 3h8.2v8.2H3z"),
            FxPath("M12.4 4.4h8.2v8.2h-8.2z"),
            FxPath("M2.4 12l19.2-1"),
            FxPath("M4.4 12.4h8.2V21h-8.2z"),
            FxPath("M13.8 13.8h8.2V22h-8.2z", alpha = 0.55f),
        ),
        // Setting
        listOf(
            FxPath("M3.2 12a8.8 8.8 0 1 0 17.6 0a8.8 8.8 0 1 0 -17.6 0Z"),
            FxPath("M2.6 11.2l18.8-1.6", width = 1.2f),
            FxPath("M12 3.2v6"),
            FxPath("M12 14.4v6.4"),
            FxPath("M9.4 9.6l2.6 1l2.6-1", alpha = 0.55f),
            FxPath("M9.6 16.4l2.4-1.4l2.4 1.4", alpha = 0.55f),
        ),
    ),
)

private val pack10 = DockIconPack(
    no = 10, name = "童画涂鸦", tag = "手绘",
    desc = "全部手绘：线条抖动、比例歪斜、边界不闭合，像小孩用蜡笔随手画的。有温度。",
    stroke = 2.1f, cap = StrokeCap.Round, join = StrokeJoin.Round,
    icons = listOf(
        // Home
        listOf(
            FxPath("M3.4 11.6c-.5-.4 1-1.6 2-2.4S9 6 10.4 5.2s2.4-1.4 3.2-.8 1.8 2 3 3.2 3.6 2.8 4.2 3.6-.4 1.4-.6 2.2.6 6 .2 6.8-1.8.6-3 .6-8.6.2-10 .2-2.4.6-3-.2-.4-5.6-.6-7-.4-1.6-.4-2.2z"),
            FxPath("M9.4 21.4c-.2-2 .2-4 0-5.6s-.4-.8.8-.8 3.4-.2 4 .2.4 1.6.6 3 0 2.8 0 3.4"),
            FxPath("M14.2 5.4l1.6-2.2M9.8 5.6L8.2 3.4"),
        ),
        // SuperUser
        listOf(
            FxPath("M12.4 3.2c2 .2 4.4 1.2 6.4 2.2.4.2.6.8.6 1.4 0 2.2.2 4.6-.2 6.6s-1.2 4-2.8 5.4-3.6 2.4-4.2 2.6-.8-.2-1.4-.6-2.6-1.6-3.8-3.2-1.6-3.8-1.8-6 .2-4.6.4-5.4.6-.8 1.2-1.2 2.8-1.6 4.4-1.8z"),
            FxPath("M8.6 11.4c.6-1.2 2.2-2 3.4-1.6s2 1.6 1.6 2.8-1.8 2-3 2-2.4-1.6-2-3.2z"),
            FxPath("M11.4 12.2l-.6 1.4"),
        ),
        // Module
        listOf(
            FxPath("M3.6 3.8c2-.4 4.4.2 6.4 0s1.6-.6 1.6.4-.2 4.4 0 5.6-.6 1.2-1.8 1.2-3.6.2-4.8 0-1.4.2-1.6-.8-.2-5 .2-6.4z"),
            FxPath("M14.2 4c1.8-.2 4 .2 5.6 0s1.4-.4 1.6.6 0 4.6-.2 5.6-.8 1-1.8 1-3.2.2-4.2 0-1.2.2-1.4-.8-.2-5 .4-6.4z"),
            FxPath("M4 14c1.8-.2 4.2.2 5.8 0s1.4.2 1.4 1.2 0 4.6-.2 5.6-.8 1-1.8 1-3.4.2-4.4 0-1.2.2-1.4-.8.2-5.6.6-7z"),
            FxPath("M14.4 14.2c1.8-.2 4.2.2 5.6 0s1.4-.2 1.4.8 0 4.8-.2 5.8-.8 1-1.8 1-3.2.2-4.2 0-1.4.2-1.4-.8-.2-5 .6-6.8z"),
        ),
        // Setting
        listOf(
            FxPath("M12 3.2c1 0 1.8 1.4 2.2 2.6s1-.6 2.4 0 2 1.6 2 2.6 1.4 1 1.4 2.4-1.2 1.8-1.6 2.8.6 1 0 2.4-1.4 1.8-2.4 2-1 1.4-2.4 1.4-1.8-1.2-2.8-1.6-1 .6-2.4 0-1.8-1.4-2-2.4-1.4-1-1.4-2.4 1.2-1.8 1.6-2.8-.6-1 0-2.4 1.4-1.8 2.4-2 1-1.4 2.4-1.4z"),
            FxPath("M8.6 12a3.4 3.4 0 1 0 6.8 0a3.4 3.4 0 1 0 -6.8 0Z"),
        ),
    ),
)

private val pack11 = DockIconPack(
    no = 11, name = "街区网格", tag = "街区",
    desc = "图标本身是一个街区地块，内部由小方块建筑构成，部分方块实心（已建/未建）。",
    stroke = 1.8f, cap = StrokeCap.Round, join = StrokeJoin.Round,
    icons = listOf(
        // Home
        listOf(
            FxPath("M3 21V9l9-6.4L21 9v12z", fill = true, stroke = false, alpha = 0.14f),
            FxPath("M3 21V9l9-6.4L21 9v12"),
            FxPath("M6.4 13h3.4v3.4h-3.4Z"),
            FxPath("M14.2 13h3.4v3.4h-3.4Z"),
            FxPath("M10.3 17.6h3.4v3.4h-3.4Z"),
        ),
        // SuperUser
        listOf(
            FxPath("M12 2.8l8 2.8v7c0 5.2-3.4 8.4-8 9.6-4.6-1.2-8-4.4-8-9.6v-7z"),
            FxPath("M7 7.4h3.2v3.2h-3.2Z"),
            FxPath("M13.8 7.4h3.2v3.2h-3.2Z"),
            FxPath("M7 12.6h3.2v3.2h-3.2Z"),
            FxPath("M13.8 12.6h3.2v3.2h-3.2Z"),
        ),
        // Module
        listOf(
            FxPath("M4 3h16 a1 1 0 0 1 1 1 v16 a1 1 0 0 1 -1 1 h-16 a1 1 0 0 1 -1 -1 v-16 a1 1 0 0 1 1 -1 Z"),
            FxPath("M5.6 5.6h5v5h-5Z", fill = true, stroke = false, alpha = 0.7f),
            FxPath("M13.4 5.6h5v5h-5Z"),
            FxPath("M5.6 13.4h5v5h-5Z"),
            FxPath("M13.4 13.4h5v5h-5Z", fill = true, stroke = false, alpha = 0.7f),
        ),
        // Setting
        listOf(
            FxPath("M3 12a9 9 0 1 0 18 0a9 9 0 1 0 -18 0Z"),
            FxPath("M8.4 8.4h3v3h-3Z"),
            FxPath("M12.6 8.4h3v3h-3Z"),
            FxPath("M8.4 12.6h3v3h-3Z"),
            FxPath("M12.6 12.6h3v3h-3Z", fill = true, stroke = false),
        ),
    ),
)

private val pack12 = DockIconPack(
    no = 12, name = "等高线", tag = "等高",
    desc = "三层不规则同心闭合曲线，中心实心。像登山地图上的等高线，纯有机曲线无直角。",
    stroke = 1.9f, cap = StrokeCap.Round, join = StrokeJoin.Round,
    icons = listOf(
        // Home
        listOf(
            FxPath("M12 6.4c3.4 0 6.2 2.2 6.2 5.4S15.4 18 12 18s-6.2-2.8-6.2-6.2 2.8-5.4 6.2-5.4z"),
            FxPath("M12 3.2c4.6 0 8.4 3.4 8.4 8.4S16.6 21 12 21s-8.4-3.4-8.4-9.4S7.4 3.2 12 3.2z", alpha = 0.55f),
            FxPath("M12 9.6c1.8 0 2.8 1 2.8 2.2s-1 2.4-2.8 2.4-2.8-1.2-2.8-2.4 1-2.2 2.8-2.2z", fill = true, stroke = false, alpha = 0.7f),
        ),
        // SuperUser
        listOf(
            FxPath("M12 3.4c4.4 0 7.4 3.2 7.4 7.4S16 20.6 12 21.6 4.6 15.2 4.6 10.8 7.6 3.4 12 3.4z"),
            FxPath("M12 6.6c2.8 0 4.8 2.2 4.8 4.6S14.6 16 12 16s-4.8-2.4-4.8-4.8S9.2 6.6 12 6.6z", alpha = 0.55f),
            FxPath("M12 9.6c1.4 0 2.4 1 2.4 1.8s-1 1.8-2.4 1.8-2.4-1-2.4-1.8 1-1.8 2.4-1.8z", fill = true, stroke = false, alpha = 0.7f),
        ),
        // Module
        listOf(
            FxPath("M8 4c3 0 5 2.6 5 5s-2 5-5 5-5-2.6-5-5 2-5 5-5z"),
            FxPath("M16 10c3 0 5 2.6 5 5s-2 5-5 5-5-2.6-5-5 2-5 5-5z"),
            FxPath("M16 4.6c2 0 3.4 1.8 3.4 3.4"),
            FxPath("M8 14.6c2 0 3.4 1.8 3.4 3.4", alpha = 0.5f),
            FxPath("M6.4 9a1.6 1.6 0 1 0 3.2 0a1.6 1.6 0 1 0 -3.2 0Z", fill = true, stroke = false),
            FxPath("M14.4 15a1.6 1.6 0 1 0 3.2 0a1.6 1.6 0 1 0 -3.2 0Z", fill = true, stroke = false),
        ),
        // Setting
        listOf(
            FxPath("M12 3c4.4 0 8.2 3.4 8.2 8.2S16.4 21 12 21s-8.2-4.6-8.2-9.8S7.6 3 12 3z"),
            FxPath("M12 6.6c2.8 0 5 2.4 5 5.4s-2.2 5.6-5 5.6-5-2.6-5-5.6 2.2-5.4 5-5.4z", alpha = 0.55f),
            FxPath("M12 9.8c1.6 0 2.8 1.2 2.8 2.4s-1.2 2.4-2.8 2.4-2.8-1.2-2.8-2.4 1.2-2.4 2.8-2.4z", fill = true, stroke = false, alpha = 0.75f),
        ),
    ),
)

private val pack13 = DockIconPack(
    no = 13, name = "声波", tag = "波形",
    desc = "用长度不同的竖线模拟声音波形，读起来像频谱。图形本身几乎不闭合，全靠节奏。",
    stroke = 2.2f, cap = StrokeCap.Round, join = StrokeJoin.Round,
    icons = listOf(
        // Home
        listOf(
            FxPath("M4 21V15M8 21V11M12 21V7M16 21V11M20 21V15"),
            FxPath("M4 11L12 3.6L20 11", cap = StrokeCap.Round),
            FxPath("M7 21v-3M11 21v-6M15 21v-6M19 21v-3", alpha = 0.4f),
        ),
        // SuperUser
        listOf(
            FxPath("M3 12a9 9 0 1 0 18 0a9 9 0 1 0 -18 0Z"),
            FxPath("M7 13.6v-3.2M9.6 16v-8M12 17.6V6.4M14.4 16v-8M17 13.6v-3.2"),
        ),
        // Module
        listOf(
            FxPath("M3.6 16.4v-4.2M7 18.6V9.4M10.4 20V4M13.8 18.6V9.4M17.2 16.4v-4.2"),
            FxPath("M3.6 6h13.6", alpha = 0.35f),
            FxPath("M20.4 4v16", alpha = 0.35f),
        ),
        // Setting
        listOf(
            FxPath("M12 2.6v18.8M2.6 12h18.8"),
            FxPath("M5.6 5.6v12.8M18.4 5.6v12.8", alpha = 0.55f),
            FxPath("M5.6 5.6h12.8M5.6 18.4h12.8", alpha = 0.55f),
            FxPath("M8.4 12a3.6 3.6 0 1 0 7.2 0a3.6 3.6 0 1 0 -7.2 0Z"),
        ),
    ),
)

private val pack14 = DockIconPack(
    no = 14, name = "积木拼块", tag = "拼装",
    desc = "每个图标是一个带圆头凸起的积木块，边缘有咬合缺口，像可以拼在一起的乐高。",
    stroke = 1.9f, cap = StrokeCap.Round, join = StrokeJoin.Round,
    icons = listOf(
        // Home
        listOf(
            FxPath("M3.4 9.4a2.2 2.2 0 0 1 2.2-2.2h2.2V5.6a2.2 2.2 0 0 1 4.4 0v1.6h4.2a2.2 2.2 0 0 1 2.2 2.2v4.2h1.6a2.2 2.2 0 0 1 0 4.4h-1.6v2a2.2 2.2 0 0 1-2.2 2.2H5.6a2.2 2.2 0 0 1-2.2-2.2V9.4z"),
            FxPath("M8.4 20.2v-5.4h7.2v5.4", alpha = 0.5f),
        ),
        // SuperUser
        listOf(
            FxPath("M12 2.8l7 2.8v6.2c0 4.4-2.8 7.6-7 9-4.2-1.4-7-4.6-7-9V5.6z"),
            FxPath("M10.6 6.6a1.4 1.4 0 1 0 2.8 0a1.4 1.4 0 1 0 -2.8 0Z", fill = true, stroke = false),
            FxPath("M6.2 11a1.4 1.4 0 1 0 2.8 0a1.4 1.4 0 1 0 -2.8 0Z", fill = true, stroke = false),
            FxPath("M15 11a1.4 1.4 0 1 0 2.8 0a1.4 1.4 0 1 0 -2.8 0Z", fill = true, stroke = false),
            FxPath("M8 16.4a1.4 1.4 0 1 0 2.8 0a1.4 1.4 0 1 0 -2.8 0Z", fill = true, stroke = false),
            FxPath("M13.2 16.4a1.4 1.4 0 1 0 2.8 0a1.4 1.4 0 1 0 -2.8 0Z", fill = true, stroke = false),
        ),
        // Module
        listOf(
            FxPath("M3.2 8.6a2 2 0 0 1 2-2h1V4.4a1.9 1.9 0 0 1 3.8 0v2.2h1.2a2 2 0 0 1 2-2h1V4.4a1.9 1.9 0 0 1 3.8 0v2.2h1.8a2 2 0 0 1 2 2v2.6h2.2a1.9 1.9 0 0 1 0 3.8h-2.2v3.4h2.2a1.9 1.9 0 0 1 0 3.8h-2.2"),
            FxPath("M3.2 8.6v10.8a2 2 0 0 0 2 2h2.2v2.2a1.9 1.9 0 0 0 3.8 0V21.4h2.4V21.4a1.9 1.9 0 0 0 3.8 0v-2.2", alpha = 0f),
        ),
        // Setting
        listOf(
            FxPath("M3 12a9 9 0 1 0 18 0a9 9 0 1 0 -18 0Z"),
            FxPath("M10.5 6.4a1.5 1.5 0 1 0 3 0a1.5 1.5 0 1 0 -3 0Z", fill = true, stroke = false),
            FxPath("M15.9 10.4a1.5 1.5 0 1 0 3 0a1.5 1.5 0 1 0 -3 0Z", fill = true, stroke = false),
            FxPath("M13.9 16.8a1.5 1.5 0 1 0 3 0a1.5 1.5 0 1 0 -3 0Z", fill = true, stroke = false),
            FxPath("M7.1 16.8a1.5 1.5 0 1 0 3 0a1.5 1.5 0 1 0 -3 0Z", fill = true, stroke = false),
            FxPath("M5.1 10.4a1.5 1.5 0 1 0 3 0a1.5 1.5 0 1 0 -3 0Z", fill = true, stroke = false),
            FxPath("M9.4 12a2.6 2.6 0 1 0 5.2 0a2.6 2.6 0 1 0 -5.2 0Z"),
        ),
    ),
)

private val pack15 = DockIconPack(
    no = 15, name = "印章", tag = "钢印",
    desc = "外框细线 + 内部大面积实心图形，像往文件上盖的印章。上沿还有一道压痕线。",
    stroke = 1.4f, cap = StrokeCap.Butt, join = StrokeJoin.Miter,
    icons = listOf(
        // Home
        listOf(
            FxPath("M4.6 2.6h14.8 a2 2 0 0 1 2 2 v14.8 a2 2 0 0 1 -2 2 h-14.8 a2 2 0 0 1 -2 -2 v-14.8 a2 2 0 0 1 2 -2 Z", width = 1.4f),
            FxPath("M6 12.6l6-5.2l6 5.2V18h-3.4v-4h-5.2v4H6z", fill = true, stroke = false),
            FxPath("M2.6 20.4h18.8", width = 1.4f),
        ),
        // SuperUser
        listOf(
            FxPath("M4.6 2.6h14.8 a2 2 0 0 1 2 2 v14.8 a2 2 0 0 1 -2 2 h-14.8 a2 2 0 0 1 -2 -2 v-14.8 a2 2 0 0 1 2 -2 Z", width = 1.4f),
            FxPath("M12 5.6l5 2v4.6c0 3-2.2 5.2-5 6.2-2.8-1-5-3.2-5-6.2V7.6z", fill = true, stroke = false),
            FxPath("M9.6 11.8l1.8 1.8l3.2-3.4", width = 1.8f),
        ),
        // Module
        listOf(
            FxPath("M4.6 2.6h14.8 a2 2 0 0 1 2 2 v14.8 a2 2 0 0 1 -2 2 h-14.8 a2 2 0 0 1 -2 -2 v-14.8 a2 2 0 0 1 2 -2 Z", width = 1.4f),
            FxPath("M4.6 4.6h6v6h-6zM13.4 4.6h6v6h-6zM4.6 13.4h6v6h-6zM13.4 13.4h6v6h-6z", fill = true, stroke = false),
        ),
        // Setting
        listOf(
            FxPath("M4.6 2.6h14.8 a2 2 0 0 1 2 2 v14.8 a2 2 0 0 1 -2 2 h-14.8 a2 2 0 0 1 -2 -2 v-14.8 a2 2 0 0 1 2 -2 Z", width = 1.4f),
            FxPath("M12 6.2l1.8 2.4h3l-.6 3l2.2 2l-2.2 2l.6 3h-3L12 20.8l-1.8-2.2h-3l.6-3l-2.2-2l2.2-2l-.6-3h3z", fill = true, stroke = false),
            FxPath("M10 13.4a2 2 0 1 0 4 0a2 2 0 1 0 -4 0Z", width = 1.6f),
        ),
    ),
)

private val pack16 = DockIconPack(
    no = 16, name = "公文夹", tag = "卡片",
    desc = "横向卡片造型：夹子、分隔线、内容条。四签分别是文件夹、档案、仪表卡、设置卡。",
    stroke = 1.9f, cap = StrokeCap.Round, join = StrokeJoin.Round,
    icons = listOf(
        // Home
        listOf(
            FxPath("M4.8 7.6h14.4 a2.4 2.4 0 0 1 2.4 2.4 v8.2 a2.4 2.4 0 0 1 -2.4 2.4 h-14.4 a2.4 2.4 0 0 1 -2.4 -2.4 v-8.2 a2.4 2.4 0 0 1 2.4 -2.4 Z"),
            FxPath("M2.4 11.4h19.2"),
            FxPath("M8.6 7.6V5.4a1.8 1.8 0 0 1 1.8-1.8h3.2a1.8 1.8 0 0 1 1.8 1.8v2.2"),
            FxPath("M8 15.4h4M8 18h7", cap = StrokeCap.Round),
        ),
        // SuperUser
        listOf(
            FxPath("M4.8 6.4h14.4 a2.4 2.4 0 0 1 2.4 2.4 v9.4 a2.4 2.4 0 0 1 -2.4 2.4 h-14.4 a2.4 2.4 0 0 1 -2.4 -2.4 v-9.4 a2.4 2.4 0 0 1 2.4 -2.4 Z"),
            FxPath("M7 10.4h6.4M7 13.6h10M7 16.8h7.4", cap = StrokeCap.Round),
            FxPath("M16.6 6.4V4a1.6 1.6 0 0 1 1.6-1.6h1.4A1.6 1.6 0 0 1 21.2 4v2.4"),
        ),
        // Module
        listOf(
            FxPath("M4.6 4.4h14.8 a2.2 2.2 0 0 1 2.2 2.2 v10.8 a2.2 2.2 0 0 1 -2.2 2.2 h-14.8 a2.2 2.2 0 0 1 -2.2 -2.2 v-10.8 a2.2 2.2 0 0 1 2.2 -2.2 Z"),
            FxPath("M2.4 8.2h19.2"),
            FxPath("M6.2 10.6h3 a1.2 1.2 0 0 1 1.2 1.2 v4 a1.2 1.2 0 0 1 -1.2 1.2 h-3 a1.2 1.2 0 0 1 -1.2 -1.2 v-4 a1.2 1.2 0 0 1 1.2 -1.2 Z"),
            FxPath("M12.8 11.6h6M12.8 14.2h6M12.8 16.8h3.6", cap = StrokeCap.Round),
        ),
        // Setting
        listOf(
            FxPath("M4.6 5.4h14.8 a2.2 2.2 0 0 1 2.2 2.2 v8.8 a2.2 2.2 0 0 1 -2.2 2.2 h-14.8 a2.2 2.2 0 0 1 -2.2 -2.2 v-8.8 a2.2 2.2 0 0 1 2.2 -2.2 Z"),
            FxPath("M8.8 12a3.2 3.2 0 1 0 6.4 0a3.2 3.2 0 1 0 -6.4 0Z"),
            FxPath("M12 8.8v1.4m0 3.6v1.4M8.8 12h1.4m3.6 0h1.4"),
            FxPath("M6 18.6v1.6a1.4 1.4 0 0 0 1.4 1.4h9.2a1.4 1.4 0 0 0 1.4-1.4v-1.6", alpha = 0.5f),
        ),
    ),
)

private val pack17 = DockIconPack(
    no = 17, name = "线框解构", tag = "解构",
    desc = "只保留结构线：透视线、对称轴、支撑线，其余全部删掉。最少的笔画，最清晰的结构。",
    stroke = 1.8f, cap = StrokeCap.Round, join = StrokeJoin.Round,
    icons = listOf(
        // Home
        listOf(
            FxPath("M3 21V10.4L12 3l9 7.4V21"),
            FxPath("M3 21h18"),
            FxPath("M9.4 21v-5.6h5.2V21"),
            FxPath("M12 3v7.4M3 10.4h18", alpha = 0.35f),
        ),
        // SuperUser
        listOf(
            FxPath("M12 3v18"),
            FxPath("M12 3l7.4 2.8v6.4c0 4.4-3 7.6-7.4 8.8"),
            FxPath("M12 3L4.6 5.8v6.4c0 4.4 3 7.6 7.4 8.8"),
            FxPath("M8.4 11.8l3.6 3.6l3.6-4"),
        ),
        // Module
        listOf(
            FxPath("M3 3h18v18H3z"),
            FxPath("M12 3v18M3 12h18"),
            FxPath("M12 12l9-9M12 21L3 12", alpha = 0.35f),
            FxPath("M10.4 12a1.6 1.6 0 1 0 3.2 0a1.6 1.6 0 1 0 -3.2 0Z", fill = true, stroke = false),
        ),
        // Setting
        listOf(
            FxPath("M3 12a9 9 0 1 0 18 0a9 9 0 1 0 -18 0Z"),
            FxPath("M8.6 12a3.4 3.4 0 1 0 6.8 0a3.4 3.4 0 1 0 -6.8 0Z"),
            FxPath("M12 3l-2.4 5.6M12 3l2.4 5.6M21 12l-6 2M21 12l-6-2M12 21l-2.4-5.6M12 21l2.4-5.6M3 12l6 2M3 12l6-2", alpha = 0.5f),
        ),
    ),
)

private val pack18 = DockIconPack(
    no = 18, name = "折扇", tag = "扇形",
    desc = "统一以扇形为骨架：扇骨放射 + 弧边，四签用不同的扇面分割和中心点区分。",
    stroke = 1.9f, cap = StrokeCap.Round, join = StrokeJoin.Round,
    icons = listOf(
        // Home
        listOf(
            FxPath("M12 21L3.4 8.6C6 6.4 9 5 12 5s6 1.4 8.6 3.6z"),
            FxPath("M12 21V5M12 21L6.6 6.2M12 21l5.4-14.8"),
            FxPath("M3.4 8.6C6.2 6.6 9 5.6 12 5.6s5.8 1 8.6 3", alpha = 0.5f),
        ),
        // SuperUser
        listOf(
            FxPath("M12 21L4 9.4C5.6 6.6 8.4 4.4 12 4.4s6.4 2.2 8 5z"),
            FxPath("M12 21V4.4M12 21L7 5.6M12 21l5-15.4"),
            FxPath("M10.4 8.4a1.6 1.6 0 1 0 3.2 0a1.6 1.6 0 1 0 -3.2 0Z", fill = true, stroke = false),
        ),
        // Module
        listOf(
            FxPath("M12 21L2.6 11.6A13.3 13.3 0 0 1 21.4 11.6z"),
            FxPath("M12 21V8.2M12 21L6 9.2M12 21l6-11.8M12 21L3.4 12.6M12 21l8.6-8.4"),
        ),
        // Setting
        listOf(
            FxPath("M12 21L3 10.4A13 13 0 0 1 21 10.4z"),
            FxPath("M12 21V8M12 21L6.4 9M12 21l5.6-12M12 21L3.6 11.6M12 21l8.4-9.4"),
            FxPath("M10.2 15.4a1.8 1.8 0 1 0 3.6 0a1.8 1.8 0 1 0 -3.6 0Z", fill = true, stroke = false),
        ),
    ),
)

private val pack19 = DockIconPack(
    no = 19, name = "涟漪", tag = "圆形",
    desc = "全部由同心圆/椭圆构成，中心和扩散圈。没有一条直线，最柔和的一套。",
    stroke = 1.9f, cap = StrokeCap.Round, join = StrokeJoin.Round,
    icons = listOf(
        // Home
        listOf(
            FxPath("M9.6 15.6a2.4 2.4 0 1 0 4.8 0a2.4 2.4 0 1 0 -4.8 0Z", fill = true, stroke = false),
            FxPath("M5.6 15.6a6.4 2.6 0 1 0 12.8 0a6.4 2.6 0 1 0 -12.8 0Z"),
            FxPath("M1.8 15.6a10.2 4.4 0 1 0 20.4 0a10.2 4.4 0 1 0 -20.4 0Z", alpha = 0.6f),
            FxPath("M7.4 12.4L12 4.6l4.6 7.8", alpha = 0.85f, cap = StrokeCap.Round),
        ),
        // SuperUser
        listOf(
            FxPath("M9.4 12a2.6 2.6 0 1 0 5.2 0a2.6 2.6 0 1 0 -5.2 0Z", fill = true, stroke = false),
            FxPath("M6.4 12a5.6 5.6 0 1 0 11.2 0a5.6 5.6 0 1 0 -11.2 0Z"),
            FxPath("M3.2 12a8.8 8.8 0 1 0 17.6 0a8.8 8.8 0 1 0 -17.6 0Z", alpha = 0.6f),
            FxPath("M12 2.4v2.6M12 19v2.6M2.4 12h2.6M19 12h2.6", alpha = 0.5f),
        ),
        // Module
        listOf(
            FxPath("M5 7a2 2 0 1 0 4 0a2 2 0 1 0 -4 0Z", fill = true, stroke = false),
            FxPath("M2.6 7a4.4 4.4 0 1 0 8.8 0a4.4 4.4 0 1 0 -8.8 0Z", alpha = 0.8f),
            FxPath("M15 17a2 2 0 1 0 4 0a2 2 0 1 0 -4 0Z", fill = true, stroke = false),
            FxPath("M12.6 17a4.4 4.4 0 1 0 8.8 0a4.4 4.4 0 1 0 -8.8 0Z", alpha = 0.8f),
            FxPath("M15.6 7a1.4 1.4 0 1 0 2.8 0a1.4 1.4 0 1 0 -2.8 0Z"),
            FxPath("M5.6 17a1.4 1.4 0 1 0 2.8 0a1.4 1.4 0 1 0 -2.8 0Z"),
        ),
        // Setting
        listOf(
            FxPath("M10 12a2 2 0 1 0 4 0a2 2 0 1 0 -4 0Z", fill = true, stroke = false),
            FxPath("M7.4 12a4.6 4.6 0 1 0 9.2 0a4.6 4.6 0 1 0 -9.2 0Z"),
            FxPath("M4.6 12a7.4 7.4 0 1 0 14.8 0a7.4 7.4 0 1 0 -14.8 0Z", alpha = 0.7f),
            FxPath("M2 12a10 10 0 1 0 20 0a10 10 0 1 0 -20 0Z", alpha = 0.4f),
        ),
    ),
)

private val pack20 = DockIconPack(
    no = 20, name = "星轨", tag = "轨迹",
    desc = "弧线轨迹 + 轨道上的星点，像天文观测图。四签用不同的轨道形状和星点数量区分。",
    stroke = 1.9f, cap = StrokeCap.Round, join = StrokeJoin.Round,
    icons = listOf(
        // Home
        listOf(
            FxPath("M3 19.6C3 12 7 5.6 12 5.6s9 6.4 9 14"),
            FxPath("M10 5.6a2 2 0 1 0 4 0a2 2 0 1 0 -4 0Z", fill = true, stroke = false),
            FxPath("M5.4 14.4c1.6-2 4-3.4 6.6-3.4s5 1.4 6.6 3.4", alpha = 0.5f),
            FxPath("M10.4 19.6a1.6 1.6 0 1 0 3.2 0a1.6 1.6 0 1 0 -3.2 0Z"),
            FxPath("M3 19.6h18", alpha = 0.35f),
        ),
        // SuperUser
        listOf(
            FxPath("M12 2.8l2.4 6.2l6.6.6l-5 4.4l1.6 6.4L12 16.8l-5.6 3.6l1.6-6.4l-5-4.4l6.6-.6z"),
            FxPath("M12 6.6l1.4 3.4l3.4.4l-2.6 2.2l.8 3.4L12 14.4l-3 1.6l.8-3.4l-2.6-2.2l3.4-.4z", fill = true, stroke = false, alpha = 0.35f),
        ),
        // Module
        listOf(
            FxPath("M4 4.6c2.6-1.6 5.4-2.4 8-2.4s5.4.8 8 2.4"),
            FxPath("M4 19.4c2.6 1.6 5.4 2.4 8 2.4s5.4-.8 8-2.4"),
            FxPath("M3 12h18", alpha = 0.35f),
            FxPath("M6.4 9a1.6 1.6 0 1 0 3.2 0a1.6 1.6 0 1 0 -3.2 0Z"),
            FxPath("M14.4 9a1.6 1.6 0 1 0 3.2 0a1.6 1.6 0 1 0 -3.2 0Z"),
            FxPath("M10.4 15.4a1.6 1.6 0 1 0 3.2 0a1.6 1.6 0 1 0 -3.2 0Z"),
        ),
        // Setting
        listOf(
            FxPath("M12 2.6l1.4 3.8l3.8.4l-2.8 2.6l.8 3.8L12 11l-3.2 2.2l.8-3.8L6.8 6.8l3.8-.4z", fill = true, stroke = false),
            FxPath("M6.2 13.6c1.8 2 3.6 3 5.8 3s4-1 5.8-3"),
            FxPath("M4 17.4c2.4 2.4 5 3.6 8 3.6s5.6-1.2 8-3.6", alpha = 0.55f),
        ),
    ),
)

internal object DockIconPacks {

    /** 0 表示原生图标（跟随皮肤，不覆盖） */
    val packs: List<DockIconPack> = listOf(
        pack01,
        pack02,
        pack03,
        pack04,
        pack05,
        pack06,
        pack07,
        pack08,
        pack09,
        pack10,
        pack11,
        pack12,
        pack13,
        pack14,
        pack15,
        pack16,
        pack17,
        pack18,
        pack19,
        pack20,
    )

    private fun build(pack: DockIconPack, slot: Int): ImageVector {
        val el = pack.icons[slot]
        return ImageVector.Builder(
            name = "DockIconPack${pack.no}-$slot",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).apply {
            el.forEachIndexed { i, p ->
                addPath(
                    pathData = PathParser().parsePathString(p.d).toNodes(),
                    pathFillType = PathFillType.NonZero,
                    name = "p$i",
                    fill = if (p.fill) SolidColor(Color.Black) else null,
                    stroke = if (p.stroke) SolidColor(Color.Black) else null,
                    strokeLineWidth = p.width ?: pack.stroke,
                    strokeLineCap = p.cap ?: pack.cap,
                    strokeLineJoin = p.join ?: pack.join,
                    fillAlpha = p.alpha,
                    strokeAlpha = p.alpha,
                )
            }
        }.build()
    }

    private val cache = HashMap<Int, ImageVector>()

    /** [index] 1..20；越界返回 null，调用方回落到原生图标 */
    fun vector(index: Int, slot: Int): ImageVector? {
        if (index !in 1..packs.size) return null
        val key = index * 8 + slot
        cache[key]?.let { return it }
        return build(packs[index - 1], slot).also { cache[key] = it }
    }
}
/** 四支图标在 [DockIconPack.icons] 里的下标，顺序与设计稿一致。 */
internal fun dockSlot(dest: BottomBarDestination): Int = when (dest) {
    BottomBarDestination.Home -> 0
    BottomBarDestination.SuperUser -> 1
    BottomBarDestination.Module -> 2
    BottomBarDestination.Setting -> 3
}

/**
 * 当前选中的图标风格；0 = 原生，返回 null 让调用方走皮肤自己的图标。
 *
 * 刻意返回 nullable 而不是「读设置 + 回落」二合一：DockKit 那套皮肤有自己的一套
 * [KitGlyphs]，回落目标不同，调用方必须自己决定回落给谁。
 */
@Composable
internal fun dockIconOverride(dest: BottomBarDestination): ImageVector? {
    val style = CustomizationStore.current.dockIconPack
    if (style == 0) return null
    return DockIconPacks.vector(style, dockSlot(dest))
}
