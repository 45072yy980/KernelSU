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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import me.diksu.ultra.foxsu.data.settings.CustomizationStore
import me.diksu.ultra.foxsu.ui.component.dockfx.DockFx
import me.diksu.ultra.foxsu.ui.component.dockfx.DockFxScope
import me.diksu.ultra.foxsu.ui.component.dockfx.DockInnerFxCore
import me.diksu.ultra.foxsu.ui.component.dockfx.dockRingFxWith

/**
 * 底栏特效选择器。
 *
 * 两页：栏内 20 套、外圈 20 套。各自可单选，0 表示关闭，所以两边可以自由叠加——
 * 「边框呼吸 + 图标弹跳」这种组合是原本的 40 选 1 做不到的。
 *
 * 每张卡片都有一个小预览：选中的那套每 1.6 秒自动播一次，其余点一下播一次，
 * 这样一屏 20 张不会同时跑动画（那是 20 份逐帧绘制，低端机会掉帧）。
 */
@Composable
fun AgDockFx(onBack: () -> Unit) {
    val custom = CustomizationStore.current
    var tab by remember { mutableIntStateOf(0) }

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
                .padding(start = 18.dp, end = 18.dp)
                .padding(top = 10.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AgIconBtn(AgTopIcons.Back, onClick = onBack, tint = Ag.Tx)
            Spacer(Modifier.width(12.dp))
            Column {
                Text("底栏特效", fontSize = 17.sp, fontWeight = FontWeight.ExtraBold, color = Ag.Tx)
                Text(
                    "栏内 ${if (custom.dockFxInner == 0) "关闭" else "第 ${custom.dockFxInner} 套"} · " +
                        "外圈 ${if (custom.dockFxRing == 0) "关闭" else "第 ${custom.dockFxRing} 套"}",
                    fontSize = 11.sp, color = Ag.Tx3,
                )
            }
        }

        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 40.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(2) }) {
                Column {
                    AgCard {
                        AgToggleRow(
                            "底栏点击特效",
                            if (custom.dockFxOn) {
                                "点图标时播放下面选中的那套；关掉后只保留最基础的按压回缩"
                            } else {
                                "已关闭。栏内与外圈都不会播放，选择仍会记住"
                            },
                            Ag.Mag, AgSettingsIcons.Paw,
                            trailing = {
                                AgToggle(checked = custom.dockFxOn, onCheckedChange = {
                                    CustomizationStore.setDockFxOn(!custom.dockFxOn)
                                }, small = true)
                            },
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                    // 分段：栏内 / 外圈
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(Ag.Vio.copy(alpha = 0.06f))
                            .border(1.dp, Ag.Line, RoundedCornerShape(12.dp))
                            .padding(3.dp),
                        horizontalArrangement = Arrangement.spacedBy(3.dp),
                    ) {
                        listOf("栏内 20 套", "外圈 20 套").forEachIndexed { i, label ->
                            val on = tab == i
                            Box(
                                Modifier
                                    .weight(1f)
                                    .height(36.dp)
                                    .clip(RoundedCornerShape(9.dp))
                                    .then(if (on) Modifier.background(Ag.Grad) else Modifier)
                                    .agClick { tab = i },
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    label,
                                    fontSize = 12.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (on) Color.White else Ag.Tx2,
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    Text(
                        if (tab == 0) {
                            "作用在图标与选中胶囊上。建议配「弹跳落地」或「果冻抖动」，和胶囊的滑动是同一条时间线。"
                        } else {
                            "作用在整条底栏的边框上，可以叠在栏内特效之上。常驻类会一直跑，交互类只在切换时播一次。"
                        },
                        fontSize = 11.5.sp, lineHeight = 17.sp, color = Ag.Tx3,
                        modifier = Modifier.padding(horizontal = 4.dp),
                    )
                    Spacer(Modifier.height(4.dp))
                }
            }

            item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(2) }) {
                FxNoneCard(
                    scope = if (tab == 0) DockFxScope.INNER else DockFxScope.RING,
                    selected = if (tab == 0) custom.dockFxInner == 0 else custom.dockFxRing == 0,
                    onClick = {
                        if (tab == 0) CustomizationStore.setDockFxInner(0)
                        else CustomizationStore.setDockFxRing(0)
                    },
                )
            }

            val list = if (tab == 0) DockFx.inners else DockFx.rings
            items(list) { fx ->
                val index = list.indexOf(fx) + 1
                val selected = if (tab == 0) custom.dockFxInner == index else custom.dockFxRing == index
                FxCard(
                    fx = fx,
                    selected = selected,
                    onClick = {
                        if (tab == 0) CustomizationStore.setDockFxInner(index)
                        else CustomizationStore.setDockFxRing(index)
                    },
                )
            }
        }
    }
}

/** 「不使用」这一项单独占一行，点它等于把该作用域关掉。 */
@Composable
private fun FxNoneCard(scope: DockFxScope, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Ag.Card)
            .border(if (selected) 1.6.dp else 1.dp, if (selected) Ag.Mag else Ag.Line, RoundedCornerShape(14.dp))
            .agClick(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(34.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(Ag.Vio.copy(alpha = 0.08f)),
            contentAlignment = Alignment.Center,
        ) {
            Text("—", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Ag.Tx3)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                if (scope == DockFxScope.INNER) "不使用栏内特效" else "不使用外圈特效",
                fontSize = 13.5.sp, fontWeight = FontWeight.Bold, color = Ag.Tx,
            )
            Text(
                if (scope == DockFxScope.INNER) "图标只做最基础的按压回缩" else "边框保持原样，不加任何装饰",
                fontSize = 11.sp, color = Ag.Tx3,
            )
        }
        if (selected) {
            Text("已选", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Ag.Mag)
        }
    }
}

/**
 * 一张特效卡：上面是迷你底栏预览，下面是名字与分类。
 *
 * 预览用真实的渲染器（[DockInnerFxCore] / [dockRingFxWith]），不是另画一套示意图——
 * 示意图和真机对不上是这类选择器最常见的坑。
 */
@Composable
private fun FxCard(fx: DockFx, selected: Boolean, onClick: () -> Unit) {
    var tick by remember(fx) { mutableIntStateOf(0) }
    var pressed by remember(fx) { mutableStateOf(false) }
    val accent = Ag.Mag

    // 选中项常驻自播；其余只在点击时播一次
    LaunchedEffect(selected, fx) {
        if (!selected) return@LaunchedEffect
        while (true) {
            delay(1500)
            tick++
        }
    }
    LaunchedEffect(tick) {
        pressed = true
        delay(70)
        pressed = false
    }

    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Ag.Card)
            .border(if (selected) 1.6.dp else 1.dp, if (selected) Ag.Mag else Ag.Line, RoundedCornerShape(14.dp))
            .agClick {
                tick++
                onClick()
            }
            .padding(10.dp),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(74.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(Ag.Vio.copy(alpha = 0.05f)),
            contentAlignment = Alignment.Center,
        ) {
            if (fx.scope == DockFxScope.INNER) {
                // 栏内：只放图标，外面套一圈假胶囊当语境
                Box(
                    Modifier
                        .size(width = 84.dp, height = 40.dp)
                        .clip(RoundedCornerShape(50))
                        .background(Ag.Card)
                        .border(1.dp, Ag.Line, RoundedCornerShape(50)),
                    contentAlignment = Alignment.Center,
                ) {
                    DockInnerFxCore(
                        fx = fx,
                        pressed = pressed,
                        accent = accent,
                        iconSize = 20.dp,
                    ) {
                        Icon(
                            AgSettingsIcons.Paw,
                            contentDescription = null,
                            tint = accent,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
            } else {
                // 外圈：预览条本身就是底栏，特效画在它的边框上
                Box(
                    Modifier
                        .width(116.dp)
                        .height(42.dp)
                        .dockRingFxWith(
                            fx = fx,
                            accent = accent,
                            accent2 = Ag.Teal,
                            trigger = tick,
                            anchor = 0.36f,
                            capsule = true,
                        )
                        .clip(RoundedCornerShape(50))
                        .background(Ag.Card)
                        .border(1.dp, Ag.Line, RoundedCornerShape(50)),
                    contentAlignment = Alignment.Center,
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        repeat(3) { i ->
                            Box(
                                Modifier
                                    .size(16.dp)
                                    .clip(RoundedCornerShape(50))
                                    .background(if (i == 1) accent.copy(alpha = 0.22f) else Ag.Line.copy(alpha = 0.6f)),
                            )
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                fx.label,
                fontSize = 12.5.sp,
                fontWeight = FontWeight.Bold,
                color = Ag.Tx,
                maxLines = 1,
                modifier = Modifier.weight(1f),
            )
            if (selected) {
                Text("✓", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Ag.Mag)
            }
        }
        Spacer(Modifier.height(2.dp))
        Text(fx.tag, fontSize = 10.5.sp, color = Ag.Tx3, maxLines = 1)
    }
}
