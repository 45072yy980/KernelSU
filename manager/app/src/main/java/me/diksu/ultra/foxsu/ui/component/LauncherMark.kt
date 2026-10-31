package me.diksu.ultra.foxsu.ui.component

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.foundation.shape.RoundedCornerShape
import me.diksu.ultra.foxsu.data.settings.CustomizationStore
import me.diksu.ultra.foxsu.ui.util.LauncherIconManager

/**
 * 桌面启动器图标的应用内等价物。
 *
 * 作者页头像、Pages / Glass 的界面图标都用它 —— 桌面看到的是哪只狐狸，界面里就是哪只。
 *
 * 现在每套图标是一张**满幅 512 插画**（[LauncherIconManager.previewOf]），
 * 圆角与遮罩由这里负责：
 * - 不再需要拼 `layer-list` 的两层 drawable，也就彻底避开了 `painterResource`
 *   只认 Vector / 位图、遇到 layer-list 直接抛异常导致首帧崩溃的坑（r11 事故）；
 * - 用普通位图 + `clip`，任何尺寸下缩放比例都正确（旧实现要按 108dp 手算缩放）。
 */
@Composable
fun LauncherMark(
    modifier: Modifier = Modifier,
    style: Int = CustomizationStore.current.launcherIcon,
    cornerPercent: Int = 26,
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(percent = cornerPercent))
            .fillMaxSize(),
    ) {
        Image(
            painter = painterResource(LauncherIconManager.previewOf(style)),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
    }
}
