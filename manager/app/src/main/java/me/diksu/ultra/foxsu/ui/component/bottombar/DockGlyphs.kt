package me.diksu.ultra.foxsu.ui.component.bottombar

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp

/**
 * 胶囊底栏图标：品牌稿 `.dock .dit` 的 1:1 复刻。
 *
 * 规格：viewBox 24×24、描边语言（房子/盾 2.2、拼图/太阳 2.0）、round cap + round join。
 * 形状依据设计稿逐像素校准：
 *  - 房子：尖顶略扁、屋檐外伸且端点低于房身顶、房身垂直、底边圆角闭合（无门洞）
 *  - 盾牌：顶部为平滑圆拱（中间不塌），两侧垂直，底部收尖，内部标准对勾
 *  - 拼图：上凸 → 右凸 → 下凹 → 左凹（原实现四边全凸，真机上渲染成了花瓣）
 *  - 太阳：中心圆 r3.9 + 八向短射线（原实现圆偏小、射线偏内，视觉上糊成一团）
 *
 * 描边色统一写黑，实际颜色由 `Icon(tint = ...)` 的 ColorFilter 整体覆盖。
 */
internal object DockGlyphs {

    private fun outline(name: String, paths: List<String>, width: Float = 2.2f): ImageVector =
        ImageVector.Builder(
            name = name,
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).apply {
            paths.forEachIndexed { index, d ->
                addPath(
                    pathData = PathParser().parsePathString(d).toNodes(),
                    pathFillType = PathFillType.NonZero,
                    name = "$name-$index",
                    fill = null,
                    stroke = SolidColor(Color.Black),
                    strokeLineWidth = width,
                    strokeLineCap = StrokeCap.Round,
                    strokeLineJoin = StrokeJoin.Round,
                )
            }
        }.build()

    /** 主页：扁尖顶 + 外伸屋檐 + 方房身，底边圆角闭合 */
    val Home: ImageVector = outline(
        "DockHome",
        listOf(
            "M2.6 12.6 12 4.3l9.4 8.3",
            "M5.2 10.4v8.6a1.6 1.6 0 0 0 1.6 1.6h10.4a1.6 1.6 0 0 0 1.6-1.6v-8.6",
        ),
    )

    /**
     * 超级用户：盾牌 + 内部对勾。
     *
     * 旧稿的顶部弧只有 3.3 高、横向却铺满 16，画出来像一个压扁的碗（用户说的「有点扁」）。
     * 现在顶拱抬高到 ~4.5、两肩收窄到 14 宽、盾身竖直下落 4.2 后才收尖，
     * 视觉重心更立；对勾也随之下移到盾牌正中（y≈13）。
     */
    val SuperUser: ImageVector = outline(
        "DockSuperUser",
        listOf(
            "M5 8.4C5 5.4 8.2 3.9 12 3.9s7 1.5 7 4.5v4.2c0 4.2-3.3 6.9-7 8.4-3.7-1.5-7-4.2-7-8.4z",
            "M8.8 12.9l2.3 2.3 4.3-4.3",
        ),
        width = 2.1f,
    )

    /** 模块：拼图块（上凸、右凸、下凹、左凹） */
    val Module: ImageVector = outline(
        "DockModule",
        listOf(
            "M6 4.4h3.6a2.2 2.2 0 1 1 4.8 0H18a1.8 1.8 0 0 1 1.8 1.8v3.6a2.2 2.2 0 1 1 0 4.8v3.2a1.8 1.8 0 0 1-1.8 1.8h-3.6a2.2 2.2 0 1 0-4.8 0H6a1.8 1.8 0 0 1-1.8-1.8v-3.2a2.2 2.2 0 1 0 0-4.8V6.2A1.8 1.8 0 0 1 6 4.4z",
        ),
        width = 2f,
    )

    /** 设置：太阳（中心圆 r3.9 + 八向短射线） */
    val Settings: ImageVector = outline(
        "DockSettings",
        listOf(
            "M12 8a4 4 0 1 1 0 8 4 4 0 1 1 0-8z",
            "M12 2.8v3.2M12 18v3.2M2.8 12h3.2M18 12h3.2M5.6 5.6l2.2 2.2M16.2 16.2l2.2 2.2M18.4 5.6l-2.2 2.2M7.8 16.2l-2.2 2.2",
        ),
        width = 2.1f,
    )
}
