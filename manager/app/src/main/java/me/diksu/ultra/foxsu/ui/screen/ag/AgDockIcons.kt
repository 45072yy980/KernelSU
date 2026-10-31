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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import me.diksu.ultra.foxsu.data.settings.CustomizationStore
import me.diksu.ultra.foxsu.ui.component.bottombar.DockIconPack
import me.diksu.ultra.foxsu.ui.component.bottombar.DockIconPacks

/**
 * 底栏图标风格选择器（20 套）。
 *
 * 每张卡里的四个图标就是真机上那四支，用同一份 [DockIconPacks] 数据渲染，
 * 不是另画的示意图——示意图和真机对不上是这类选择器最常见的坑。
 *
 * 0 = 原生：不覆盖任何图标，跟随当前皮肤（Miuix / 胶囊 / 通栏 / DockKit 各有一套）。
 */
@Composable
fun AgDockIcons(onBack: () -> Unit) {
    val selected = CustomizationStore.current.dockIconPack

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
                Text("底栏图标", fontSize = 17.sp, fontWeight = FontWeight.ExtraBold, color = Ag.Tx)
                Text(
                    if (selected == 0) "当前：原生（跟随皮肤）" else "当前：${selected.toString().padStart(2, '0')} · " +
                        DockIconPacks.packs[selected - 1].name,
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
                        Text(
                            "20 套造型语言",
                            fontSize = 13.5.sp, fontWeight = FontWeight.Bold, color = Ag.Tx,
                        )
                        Spacer(Modifier.height(5.dp))
                        Text(
                            "每套都是独立画法，不是同一支图的粗细变体。四支图标固定对应" +
                                "首页 / 超级用户 / 模块 / 设置，只换形状不换颜色——强调色、选中态、" +
                                "水滴胶囊全部照常工作。",
                            fontSize = 11.5.sp, lineHeight = 17.sp, color = Ag.Tx3,
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                    NativeIconCard(
                        selected = selected == 0,
                        onClick = { CustomizationStore.setDockIconPack(0) },
                    )
                    Spacer(Modifier.height(10.dp))
                }
            }

            items(DockIconPacks.packs) { pack ->
                IconPackCard(
                    pack = pack,
                    selected = selected == pack.no,
                    onClick = { CustomizationStore.setDockIconPack(pack.no) },
                )
            }
        }
    }
}

/** 原生一项单独占一行，点它等于取消覆盖。 */
@Composable
private fun NativeIconCard(selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Ag.Card)
            .border(
                if (selected) 1.6.dp else 1.dp,
                if (selected) Ag.Mag else Ag.Line,
                RoundedCornerShape(14.dp),
            )
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
            Text("原生图标", fontSize = 13.5.sp, fontWeight = FontWeight.Bold, color = Ag.Tx)
            Text(
                "不覆盖，跟随当前底栏皮肤自己那套画法",
                fontSize = 11.sp, color = Ag.Tx3,
            )
        }
        if (selected) Text("已选", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Ag.Mag)
    }
}

/**
 * 一张风格卡：上面一条迷你底栏（四支真图标 + 选中胶囊），下面是编号、名字与分类。
 */
@Composable
private fun IconPackCard(pack: DockIconPack, selected: Boolean, onClick: () -> Unit) {
    val icons: List<ImageVector?> = remember(pack.no) {
        (0..3).map { DockIconPacks.vector(pack.no, it) }
    }
    Column(
        Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(Ag.Card)
            .border(
                if (selected) 1.6.dp else 1.dp,
                if (selected) Ag.Mag else Ag.Line,
                RoundedCornerShape(16.dp),
            )
            .agClick(onClick = onClick)
            .padding(10.dp),
    ) {
        // 迷你底栏：底色比卡片再浅一档，选中项用强调色 + 胶囊托底
        Box(
            Modifier
                .fillMaxWidth()
                .height(52.dp)
                .clip(RoundedCornerShape(13.dp))
                .background(Ag.Vio.copy(alpha = 0.055f)),
            contentAlignment = Alignment.Center,
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                icons.forEachIndexed { i, vec ->
                    val on = i == 0
                    Box(
                        Modifier
                            .size(34.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .then(if (on) Modifier.background(Ag.Vio.copy(alpha = 0.16f)) else Modifier),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (vec != null) {
                            Icon(
                                imageVector = vec,
                                contentDescription = null,
                                tint = if (on) Ag.Mag else Ag.Tx3,
                                modifier = Modifier.size(19.dp),
                            )
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(9.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                pack.no.toString().padStart(2, '0'),
                fontSize = 10.5.sp,
                fontWeight = FontWeight.Bold,
                color = Ag.Mag,
            )
            Spacer(Modifier.width(5.dp))
            Text(
                pack.name,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = Ag.Tx,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Text(
                pack.tag,
                fontSize = 9.5.sp,
                color = Ag.Tx3,
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(Ag.Vio.copy(alpha = 0.07f))
                    .padding(horizontal = 5.dp, vertical = 2.dp),
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(
            pack.desc,
            fontSize = 10.5.sp,
            lineHeight = 15.sp,
            color = Ag.Tx3,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        if (selected) {
            Spacer(Modifier.height(6.dp))
            Text("已选", fontSize = 10.5.sp, fontWeight = FontWeight.Bold, color = Ag.Mag)
        }
    }
}
