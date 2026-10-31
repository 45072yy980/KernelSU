package me.diksu.ultra.foxsu.ui.component.dockfx

/**
 * 底栏特效全家桶：栏内 20 套（作用在图标上）+ 外圈 20 套（作用在整条底栏的边框上）。
 *
 * 为什么分成两个作用域而不是 40 选 1：
 * 设计稿里这两组本来就是两层，可以叠加——外圈选「描边呼吸」配栏内选「弹跳落地」，
 * 出来的味道完全不同。做成两个选择器，可玩性比单选高一个量级，也不会互相打架。
 *
 * [ordinal] 直接落盘（SharedPreferences 存 Int），所以顺序绝对不能重排；
 * 新增只能追加在各自作用域的末尾，并把 [DockFxStore] 的越界回退做好。
 */
enum class DockFxScope { INNER, RING }

enum class DockFx(
    val scope: DockFxScope,
    val label: String,
    val tag: String,
    val desc: String,
) {
    // ───────────── 栏内 20 套：作用在图标（及选中胶囊）上 ─────────────

    INNER_SLIDE(
        DockFxScope.INNER, "滑动果冻胶囊", "滑动",
        "选中时图标从下往上浮起，横向先撑宽再回弹，像一颗果冻被推到位。",
    ),
    INNER_BOUNCE(
        DockFxScope.INNER, "弹跳落地", "弹跳",
        "图标先向上跳起再落下，触底时压扁一下，落点很实。",
    ),
    INNER_JELLY(
        DockFxScope.INNER, "果冻抖动", "果冻",
        "横竖交替拉伸，幅度逐次衰减，像用手指弹了一下果冻。",
    ),
    INNER_SPIN(
        DockFxScope.INNER, "旋转入场", "旋转",
        "图标转一整圈并带一点超调，同时从 0.6 放大到 1。",
    ),
    INNER_BEAT(
        DockFxScope.INNER, "心跳双脉冲", "脉冲",
        "连续两次「咚、咚」的放大，第二次幅度小一些，像心跳。",
    ),
    INNER_WAVE(
        DockFxScope.INNER, "波浪起伏", "文字",
        "图标与文字一起做一次上下正弦波动，相位从左往右传。",
    ),
    INNER_SHINE(
        DockFxScope.INNER, "光泽扫过", "光效",
        "一道斜向高光从图标左下扫到右上，扫完即隐。",
    ),
    INNER_RING(
        DockFxScope.INNER, "扩散光环", "光环",
        "从图标中心荡开一圈同色描边，扩到 1.9 倍后淡出。",
    ),
    INNER_BUBBLES(
        DockFxScope.INNER, "气泡上浮", "氛围",
        "五六颗小气泡从图标底部升起，边走边晃、到顶消散。",
    ),
    INNER_STARS(
        DockFxScope.INNER, "星屑迸发", "粒子",
        "十几粒星屑向四周迸开，越远越淡，像捏碎了一颗糖。",
    ),
    INNER_POP(
        DockFxScope.INNER, "胶囊膨胀", "弹性",
        "选中胶囊先从 0.6 撑到 1.15 再落回 1，图标跟着一起呼吸。",
    ),
    INNER_WIGGLE(
        DockFxScope.INNER, "左右摇摆", "俏皮",
        "图标左右各摇两下，幅度递减，像在点头打招呼。",
    ),
    INNER_FLIP(
        DockFxScope.INNER, "立体翻转", "翻转",
        "绕 Y 轴翻一整圈，中段缩到最薄，有真实的透视感。",
    ),
    INNER_GRADFLOW(
        DockFxScope.INNER, "渐变流转", "品牌",
        "一层橙色到粉色的渐变光带在图标上来回流动，循环不止。",
    ),
    INNER_LABELUP(
        DockFxScope.INNER, "文字弹射", "文字",
        "文字向上弹起再落回，图标同时轻轻一抬，强调切换成功。",
    ),
    INNER_MAGNET(
        DockFxScope.INNER, "磁吸拉伸", "滑动",
        "按下时图标朝手指方向被拉长，松手弹回，像被磁铁吸了一下。",
    ),
    INNER_DOTTRAIL(
        DockFxScope.INNER, "圆点滑轨", "滑动",
        "图标下方一颗小圆点从上一项滑到这一项，再缩回图标里。",
    ),
    INNER_RIPPLE(
        DockFxScope.INNER, "水波涟漪", "水波",
        "两圈同心涟漪先后荡开，第二圈更慢更淡。",
    ),
    INNER_ELASTIC(
        DockFxScope.INNER, "弹性超调", "弹性",
        "图标快速胀到 1.25 再回弹，落定时带一次轻微反向过冲。",
    ),
    INNER_FIREWORK(
        DockFxScope.INNER, "迷你烟花", "粒子",
        "一圈火星向外炸开，尾迹带拖尾，炸完留一点余光。",
    ),

    // ───────────── 外圈 20 套：作用在整条底栏的边框上 ─────────────

    RING_PULSE(
        DockFxScope.RING, "呼吸光晕", "常驻",
        "边框外一层柔光缓慢明暗，像在轻轻呼吸。",
    ),
    RING_ORBIT(
        DockFxScope.RING, "跑马光点", "常驻",
        "一颗亮球沿边框顺时针跑圈，位置实时跟着走。",
    ),
    RING_SCAN(
        DockFxScope.RING, "边框流光", "常驻",
        "一段高光沿边框循环流动，像扫描线扫过。",
    ),
    RING_RAINBOW(
        DockFxScope.RING, "彩虹描边", "常驻",
        "边框颜色沿角度铺满整条色相环，并缓慢转动。",
    ),
    RING_DOTLINE(
        DockFxScope.RING, "虚线旋转", "常驻",
        "虚线边框自己转动，节奏很慢，不抢注意力。",
    ),
    RING_HALOROT(
        DockFxScope.RING, "流动光晕", "常驻",
        "一圈径向光晕绕底栏旋转，亮部始终在移动。",
    ),
    RING_RIPPLE(
        DockFxScope.RING, "点击扩散波纹", "交互",
        "每次切换，一圈描边从底栏外缘向外扩散淡出。",
    ),
    RING_FLASH(
        DockFxScope.RING, "选中闪框", "交互",
        "切换瞬间整条边框闪亮一下再回落，强调「我在这」。",
    ),
    RING_BREATH(
        DockFxScope.RING, "描边呼吸", "常驻",
        "边框粗细与透明度一起呼吸，比光晕克制。",
    ),
    RING_PLASMA(
        DockFxScope.RING, "电流描边", "常驻",
        "颜色在橙白之间快速闪烁，边缘轻微抖动，像高压电。",
    ),
    RING_FOLLOW(
        DockFxScope.RING, "跟随光环", "交互",
        "一团柔光跟着选中项在底栏里游走，选中处始终被罩着。",
    ),
    RING_SWEEP(
        DockFxScope.RING, "旋转扫光", "交互",
        "切换时一道扇形高光绕边框扫过一圈，雷达扫面感。",
    ),
    RING_PING(
        DockFxScope.RING, "外圈冲击波", "交互",
        "边框猛地向外弹开再收回，有「砰」的一下手感。",
    ),
    RING_BLOOM(
        DockFxScope.RING, "选中光斑", "交互",
        "选中项背后透出一团柔光，从边框溢出一点点。",
    ),
    RING_ORBITDOT(
        DockFxScope.RING, "选中环绕", "交互",
        "一颗小光点绕选中项转一圈半后归位。",
    ),
    RING_GLINT(
        DockFxScope.RING, "光点掠过", "交互",
        "一颗亮点沿边框跑一小段就消失，只在切换瞬间出现。",
    ),
    RING_JELLY(
        DockFxScope.RING, "弹性外框", "交互",
        "整条底栏做一次果冻形变，横竖交替抖动，很有分量。",
    ),
    RING_SHADOW(
        DockFxScope.RING, "投影抬升", "交互",
        "切换时底栏整体向上轻抬，投影同时加深，像被托了一下。",
    ),
    RING_SPARK(
        DockFxScope.RING, "边缘火星", "交互",
        "边框顶上冒出几粒火星，向上飘一小段后熄灭。",
    ),
    RING_WAVE(
        DockFxScope.RING, "声波外扩", "交互",
        "两圈波纹连续向外扩散，第二圈更大更淡。",
    ),
    ;

    companion object {
        /** 选择器里的「关闭」用下标 0 表示，所以枚举下标要整体 +1 落盘。 */
        val inners: List<DockFx> = entries.filter { it.scope == DockFxScope.INNER }
        val rings: List<DockFx> = entries.filter { it.scope == DockFxScope.RING }

        /** 常驻循环的那几套：只有它们需要无限时间线，其余靠一次点击推进。 */
        val ambientRings: Set<DockFx> = setOf(
            RING_PULSE, RING_ORBIT, RING_SCAN, RING_RAINBOW,
            RING_DOTLINE, RING_HALOROT, RING_BREATH, RING_PLASMA,
        )

        fun innerByIndex(index: Int): DockFx? = inners.getOrNull(index - 1)

        fun ringByIndex(index: Int): DockFx? = rings.getOrNull(index - 1)
    }
}
