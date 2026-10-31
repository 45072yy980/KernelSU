package me.diksu.ultra.foxsu.ui.screen.ag

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Dashboard
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.sp
import me.diksu.ultra.foxsu.ui.screen.appprofile.AppProfileActions
import me.diksu.ultra.foxsu.ui.screen.appprofile.AppProfileUiState

/**
 * 晨曦玻璃 · App Profile（设计稿 p-profile）。
 * 应用卡（渐变图标 + UID 徽标）→ 超级用户开关 / Profile 选择 → 模板分组。全部真实动作。
 */
@Composable
fun AgAppProfile(
    state: AppProfileUiState,
    actions: AppProfileActions,
) {
    val app = state.appGroup?.primary
    val profile = state.profile
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
            AgIconBtn(AgTopIcons.Back, onClick = actions.onBack)
            Spacer(Modifier.weight(1f))
            AgIconBtn(AgTopIcons.More, onClick = actions.onManageTemplate)
        }
        AgTitle("App ", "Profile")

        // 应用卡（渐变色轨 + 渐变图标 + UID 徽标）
        val (c1, c2) = gradientFor(state.packageName)
        Box(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(
                    Brush.linearGradient(listOf(Ag.Vio.copy(alpha = 1.0f), Color(0xFFEC4899)))
                )
                .padding(2.dp),
        ) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(22.dp))
                    .background(Ag.CardSolid)
                    .padding(16.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val appInfo = remember(state.packageName) { app?.packageInfo?.applicationInfo }
                    Box(
                        Modifier
                            .size(46.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(Brush.linearGradient(listOf(c1, c2))),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (appInfo != null) {
                            me.diksu.ultra.foxsu.ui.component.AppIconImage(
                                applicationInfo = appInfo,
                                label = app?.label ?: state.packageName,
                                modifier = Modifier.size(46.dp),
                            )
                        } else {
                            Text(
                                (app?.label ?: state.packageName).take(1).uppercase(),
                                fontSize = 19.sp, fontWeight = FontWeight.ExtraBold, color = Color.White,
                            )
                        }
                    }
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(app?.label ?: state.packageName, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold, color = Ag.Tx, maxLines = 1)
                        Text(app?.packageName ?: state.packageName, fontSize = 11.5.sp, color = Ag.Tx3, maxLines = 1)
                    }
                    Spacer(Modifier.width(8.dp))
                    Box(
                        Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(Ag.Vio.copy(alpha = 0.078f))
                            .border(1.dp, Ag.Vio.copy(alpha = 0.22f), RoundedCornerShape(8.dp))
                            .padding(horizontal = 9.dp, vertical = 4.dp),
                    ) {
                        Text("UID ${state.uid}", fontSize = 10.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 0.6.sp, color = Ag.Vio)
                    }
                }
            }
        }
        Spacer(Modifier.height(14.dp))

        // 权限卡
        AgCard(Modifier.fillMaxWidth(), contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 6.dp)) {
            AgRow(
                "超级用户",
                "允许该应用获取 Root 权限",
                Ag.Vio, AgSettingsIcons.Shield,
                trailing = {
                    AgToggle(
                        checked = profile?.allowSu == true,
                        onCheckedChange = {
                            profile?.let { actions.onProfileChange(it.copy(allowSu = !it.allowSu)) }
                        },
                        small = true,
                    )
                },
            )
            AgRow(
                "App Profile",
                "控制该应用的自定义 Profile 策略",
                Ag.Mag, AgSettingsIcons.Modules,
                trailing = {
                    Text(
                        if (profile?.rootUseDefault == false) "自定义 ⇅" else "默认 ⇅",
                        fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Ag.Vio,
                    )
                },
                onClick = {
                    profile?.let { actions.onProfileChange(it.copy(rootUseDefault = !it.rootUseDefault)) }
                },
            )
        }

        // 模板分组
        AgGHead("模板")
        AgCard(Modifier.fillMaxWidth(), contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 6.dp)) {
            AgRow(
                "使用本地模板", "从已保存的模板快速套用",
                Ag.Teal, AgSettingsIcons.Export, showChev = true, onClick = actions.onManageTemplate,
            )
            AgRow(
                "导出当前配置", "保存为可分享的 Profile 模板",
                Ag.Amber, AgSettingsIcons.Moon, showChev = true, onClick = actions.onManageTemplate,
            )
        }
        Spacer(Modifier.height(140.dp))
    }
}
