package me.diksu.ultra.foxsu.ui.screen.glass

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import me.diksu.ultra.R
import me.diksu.ultra.foxsu.ui.component.AppIconImage
import me.diksu.ultra.foxsu.ui.component.profile.AppProfileConfig
import me.diksu.ultra.foxsu.ui.component.profile.RootProfileConfig
import me.diksu.ultra.foxsu.ui.component.profile.TemplateConfig
import me.diksu.ultra.foxsu.ui.screen.appprofile.AppProfileActions
import me.diksu.ultra.foxsu.ui.screen.appprofile.AppProfileUiState
import me.diksu.ultra.foxsu.ui.screen.appprofile.Mode
import me.diksu.ultra.foxsu.ui.theme.glassTokens

/**
 * Glass 版 App Profile —— 与主页同一套视觉语言：
 *   圆形返回按钮 + 22px 大标题 + 包名副标题
 *   应用信息玻璃卡（图标 / 名称 / 版本 / 包名 / UID 胶囊）
 *   超级用户开关行
 *   App Profile 模式行（默认 / 模板 / 自定义，玻璃选择弹窗）
 *   模板与自定义配置区（复用既有配置组件，保证功能完整）
 */
@Composable
fun GlassAppProfileScreen(
    state: AppProfileUiState,
    actions: AppProfileActions,
) {
    val t = glassTokens()
    val profile = state.profile
    val primary = state.appGroup.primary
    val isSpecialApp = primary.special
    val isRootGranted = !isSpecialApp && profile.allowSu

    val initialRootMode = when {
        profile.rootUseDefault -> Mode.Default
        profile.rootTemplate != null -> Mode.Template
        else -> Mode.Custom
    }
    var rootMode by rememberSaveable(profile) { mutableStateOf(initialRootMode) }
    val nonRootMode = if (profile.nonRootUseDefault) Mode.Default else Mode.Custom
    val mode = if (isRootGranted) rootMode else nonRootMode

    var showModeDialog by remember { mutableStateOf(false) }
    val scroll = rememberScrollState()

    Box(
        modifier = Modifier
            .fillMaxSize()
            // 详情页需要自己的实底，否则会透出导航栈上一页（悬浮卡片感会变成"半透明弹窗"）
            .background(if (t.isDark) Color(0xFF0D0D14) else Color(0xFFEEF0F7)),
    ) {
    Column(modifier = Modifier.fillMaxSize()) {
        // ---- 顶栏 ----
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(start = 13.dp, end = 13.dp, top = 10.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .glassCard(99.dp)
                    .clickable(onClick = actions.onBack),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(R.drawable.fx_ic_d_back),
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = t.ink2,
                )
            }
        }
        Text(
            text = stringResource(R.string.fx_profile),
            fontSize = 30.sp,
            fontWeight = FontWeight.ExtraBold,
            color = t.ink,
            modifier = Modifier.padding(start = 15.dp, end = 15.dp, top = 4.dp, bottom = 12.dp),
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scroll)
                .padding(horizontal = 13.dp)
                .padding(bottom = 32.dp),
        ) {
            // ---- 应用信息卡 ----
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .glassCardSolid(22.dp)
                    .padding(15.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AppIconImage(
                    modifier = Modifier
                        .size(46.dp)
                        .clip(RoundedCornerShape(14.dp)),
                    packageInfo = primary.packageInfo,
                    label = primary.label,
                )
                Spacer(Modifier.width(13.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = primary.label,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = t.ink,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = "${primary.packageInfo.versionName} (${primary.packageInfo.longVersionCode})",
                        fontSize = 10.5.sp,
                        color = t.sub,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                    Text(
                        text = state.packageName,
                        fontSize = 10.sp,
                        color = t.faint,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 1.dp),
                    )
                }
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "UID ${state.uid}",
                    fontSize = 9.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = t.lime,
                    maxLines = 1,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(t.lime.copy(alpha = 0.12f))
                        .border(1.dp, t.lime.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                )
            }

            Spacer(Modifier.height(11.dp))

            // ---- 超级用户开关 ----
            if (!isSpecialApp) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .glassCardSolid(20.dp)
                        .padding(horizontal = 15.dp, vertical = 13.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(t.chipBg)
                            .border(1.dp, t.chipBorder, RoundedCornerShape(12.dp)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.fx_ic_d_shield),
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                            tint = t.violet,
                        )
                    }
                    Spacer(Modifier.width(13.dp))
                    Text(
                        text = stringResource(R.string.fx_superuser),
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = t.ink,
                        modifier = Modifier.weight(1f),
                    )
                    GlassToggle(
                        checked = isRootGranted,
                        onClick = { actions.onProfileChange(profile.copy(allowSu = !isRootGranted)) },
                    )
                }
                Spacer(Modifier.height(10.dp))
            }

            // ---- App Profile 模式 ----
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .glassCard(20.dp)
                    .clickable { showModeDialog = true }
                    .padding(horizontal = 15.dp, vertical = 13.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(t.chipBg)
                        .border(1.dp, t.chipBorder, RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        painter = painterResource(R.drawable.fx_ic_d_user),
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = t.cyan,
                    )
                }
                Spacer(Modifier.width(13.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.fx_profile),
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = t.ink,
                    )
                    Text(
                        text = mode.text,
                        fontSize = 10.sp,
                        color = t.faint,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
                Text("›", fontSize = 17.sp, color = t.faint)
            }

            // ---- 模式对应配置区 ----
            Spacer(Modifier.height(11.dp))
            when (mode) {
                Mode.Template -> Box(modifier = Modifier.glassCardSolid(20.dp)) {
                    Column(modifier = Modifier.padding(vertical = 4.dp)) {
                        TemplateConfig(
                            profile = profile,
                            onViewTemplate = actions.onViewTemplate,
                            onManageTemplate = actions.onManageTemplate,
                            onProfileChange = actions.onProfileChange,
                        )
                    }
                }

                Mode.Custom -> Box(modifier = Modifier.glassCardSolid(20.dp)) {
                    Column(modifier = Modifier.padding(vertical = 4.dp)) {
                        if (isRootGranted) {
                            RootProfileConfig(
                                fixedName = true,
                                enabled = true,
                                profile = profile,
                                onProfileChange = actions.onProfileChange,
                            )
                        } else {
                            AppProfileConfig(
                                fixedName = true,
                                enabled = true,
                                profile = profile,
                                onProfileChange = actions.onProfileChange,
                            )
                        }
                    }
                }

                Mode.Default -> Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .glassCardSolid(20.dp)
                        .padding(horizontal = 15.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        painter = painterResource(R.drawable.fx_ic_d_check),
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = t.lime,
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = stringResource(R.string.fx_glass_profile_default_hint),
                        fontSize = 11.5.sp,
                        color = t.sub,
                    )
                }
            }

            // ---- 共享 UID 的其它应用 ----
            if (state.isUidGroup) {
                Spacer(Modifier.height(14.dp))
                GlassSectionHead(stringResource(R.string.fx_app_profile_affects_following_apps))
                state.appGroup.apps.forEach { app ->
                    if (app.packageName != state.packageName) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 9.dp)
                                .glassCardSolid(18.dp)
                                .padding(horizontal = 13.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            AppIconImage(
                                modifier = Modifier
                                    .size(30.dp)
                                    .clip(RoundedCornerShape(9.dp)),
                                packageInfo = app.packageInfo,
                                label = app.label,
                            )
                            Spacer(Modifier.width(11.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = app.label,
                                    fontSize = 12.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = t.ink,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    text = app.displayIdentifier,
                                    fontSize = 9.5.sp,
                                    color = t.faint,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
    }

    if (showModeDialog) {
        GlassProfileModeDialog(
            current = mode,
            onDismiss = { showModeDialog = false },
            onSelect = { picked ->
                if (isRootGranted) {
                    rootMode = picked
                    if (picked == Mode.Default || picked == Mode.Custom) {
                        actions.onProfileChange(
                            profile.copy(rootUseDefault = picked == Mode.Default, rootTemplate = null)
                        )
                    }
                } else {
                    actions.onProfileChange(profile.copy(nonRootUseDefault = picked == Mode.Default))
                }
                showModeDialog = false
            },
        )
    }
}

/** Glass 版模式选择弹窗 */
@Composable
private fun GlassProfileModeDialog(
    current: Mode,
    onDismiss: () -> Unit,
    onSelect: (Mode) -> Unit,
) {
    val t = glassTokens()
    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .shadow(24.dp, RoundedCornerShape(26.dp), spotColor = t.glassShadow, ambientColor = t.glassShadow)
                .clip(RoundedCornerShape(26.dp))
                .background(if (t.isDark) Color(0xF216161F) else Color(0xF7FFFFFF))
                .border(1.dp, t.glassBorder, RoundedCornerShape(26.dp))
                .padding(18.dp),
        ) {
            Text(
                text = stringResource(R.string.fx_profile),
                fontSize = 16.sp,
                fontWeight = FontWeight.ExtraBold,
                color = t.ink,
                modifier = Modifier.padding(bottom = 12.dp),
            )
            Mode.entries.forEach { option ->
                val selected = option == current
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(if (selected) t.violet.copy(alpha = 0.10f) else Color.Transparent)
                        .clickable { onSelect(option) }
                        .padding(horizontal = 12.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = option.text,
                        fontSize = 13.5.sp,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                        color = if (selected) t.violet else t.ink2,
                        modifier = Modifier.weight(1f),
                    )
                    if (selected) {
                        Icon(
                            painter = painterResource(R.drawable.fx_ic_d_check),
                            contentDescription = null,
                            modifier = Modifier.size(15.dp),
                            tint = t.violet,
                        )
                    }
                }
            }
        }
    }
}
