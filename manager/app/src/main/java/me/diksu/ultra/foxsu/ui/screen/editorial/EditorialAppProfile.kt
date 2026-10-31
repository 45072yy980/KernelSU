package me.diksu.ultra.foxsu.ui.screen.editorial

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
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

/**
 * FoxSu Editorial · App Profile —— 杂志式排版：
 * 通栏 masthead（返回 + 应用名 + UID）→ 应用信息行 → 超级用户开关行
 * → App Profile 模式行 → 模板/自定义配置区 → 共享 UID 应用清单。
 */
@Composable
fun EditorialAppProfile(
    state: AppProfileUiState,
    actions: AppProfileActions,
) {
    val t = edTokens()
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
    var showMode by remember { mutableStateOf(false) }
    val scroll = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .then(edPageBg(t.paper))
            .statusBarsPadding(),
    ) {
        EdMasthead(
            brand = primary.label,
            tag = if (isRootGranted) "ROOT" else "APP",
            left = state.packageName,
            right = "UID ${state.uid}",
            brandSize = 26,
            showBack = true,
            onBack = actions.onBack,
        )

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(scroll),
        ) {
            // 应用信息（无卡片，直接一行）
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .hairBottom(t.hair)
                    .padding(horizontal = 22.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AppIconImage(
                    modifier = Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)),
                    packageInfo = primary.packageInfo,
                    label = primary.label,
                )
                Spacer(Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "${primary.packageInfo.versionName} (${primary.packageInfo.longVersionCode})",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = t.ink,
                        maxLines = 1,
                    )
                    Text(
                        text = state.packageName,
                        fontSize = 9.5.sp,
                        color = t.sub,
                        fontFamily = FontFamily.Monospace,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 3.dp),
                    )
                    if (state.sharedUserId.isNotBlank()) {
                        Text(
                            text = state.sharedUserId,
                            fontSize = 9.sp,
                            color = t.faint,
                            fontFamily = FontFamily.Monospace,
                            maxLines = 1,
                            modifier = Modifier.padding(top = 2.dp),
                        )
                    }
                }
            }

            EdSection("01", "权限", topPadding = 18.dp)
            if (!isSpecialApp) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .hairBottom(t.hair)
                        .padding(horizontal = 22.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.fx_superuser),
                            fontSize = 14.5.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = t.ink,
                        )
                        Text(
                            text = if (isRootGranted) "已授权 · Root 可用" else "未授权 · 普通应用",
                            fontSize = 10.5.sp,
                            color = t.sub,
                            modifier = Modifier.padding(top = 3.dp),
                        )
                    }
                    EdSwitch(
                        checked = isRootGranted,
                        onToggle = { actions.onProfileChange(profile.copy(allowSu = !isRootGranted)) },
                    )
                }
            }

            EdSection("02", "配置")
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .hairBottom(t.hair)
                    .clickable { showMode = true }
                    .padding(horizontal = 22.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.fx_profile),
                    fontSize = 14.5.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = t.ink,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = mode.text,
                    fontSize = 11.sp,
                    color = t.sub,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(end = 8.dp),
                )
                Text("›", fontSize = 13.sp, color = t.faint)
            }

            when (mode) {
                Mode.Template -> Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(t.paper)
                        .padding(vertical = 4.dp),
                ) {
                    TemplateConfig(
                        profile = profile,
                        onViewTemplate = actions.onViewTemplate,
                        onManageTemplate = actions.onManageTemplate,
                        onProfileChange = actions.onProfileChange,
                    )
                }

                Mode.Custom -> Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(t.paper)
                        .padding(vertical = 4.dp),
                ) {
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

                Mode.Default -> EdRow(
                    iconRes = R.drawable.fx_ic_d_check,
                    glyphBg = t.lime.copy(alpha = 0.10f),
                    glyphColor = t.lime,
                    title = "默认配置",
                    subtitle = stringResource(R.string.fx_glass_profile_default_hint),
                )
            }

            if (state.isUidGroup) {
                EdSection("03", stringResource(R.string.fx_app_profile_affects_following_apps))
                state.appGroup.apps.forEachIndexed { index, app ->
                    if (app.packageName != state.packageName) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .hairBottom(t.hair)
                                .padding(horizontal = 22.dp, vertical = 11.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = "%02d".format(index + 1),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Black,
                                color = t.faint,
                                fontFamily = FontFamily.Monospace,
                                modifier = Modifier.width(24.dp),
                            )
                            AppIconImage(
                                modifier = Modifier.size(30.dp).clip(RoundedCornerShape(8.dp)),
                                packageInfo = app.packageInfo,
                                label = app.label,
                            )
                            Spacer(Modifier.width(11.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = app.label,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = t.ink,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    text = app.displayIdentifier,
                                    fontSize = 9.sp,
                                    color = t.sub,
                                    fontFamily = FontFamily.Monospace,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(28.dp))
        }
    }

    if (showMode) {
        EdProfileModeSheet(
            current = mode,
            onDismiss = { showMode = false },
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
                showMode = false
            },
        )
    }
}

/** 模式选择弹层（编辑风：编号 + 说明） */
@Composable
private fun EdProfileModeSheet(
    current: Mode,
    onDismiss: () -> Unit,
    onSelect: (Mode) -> Unit,
) {
    val t = edTokens()
    val desc = mapOf(
        Mode.Default to "使用全局默认配置",
        Mode.Template to "套用已保存的模板",
        Mode.Custom to "逐项自定义 uid / gid / 权限",
    )
    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(t.paper)
                .border(1.5.dp, t.ink, RoundedCornerShape(20.dp))
                .padding(top = 16.dp, bottom = 14.dp),
        ) {
            Text(
                text = "PROFILE · 配置方式",
                fontSize = 8.5.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 2.5.sp,
                color = t.acc,
                modifier = Modifier.padding(horizontal = 18.dp),
            )
            Text(
                text = stringResource(R.string.fx_profile),
                fontSize = 17.sp,
                fontWeight = FontWeight.Black,
                color = t.ink,
                modifier = Modifier.padding(start = 18.dp, end = 18.dp, top = 3.dp, bottom = 8.dp),
            )
            Mode.entries.forEachIndexed { index, option ->
                val selected = option == current
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .hairBottom(t.hair)
                        .clickable { onSelect(option) }
                        .padding(horizontal = 18.dp, vertical = 11.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "%02d".format(index + 1),
                        fontSize = 9.5.sp,
                        fontWeight = FontWeight.Black,
                        color = t.faint,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.width(22.dp),
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = option.text,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = if (selected) t.acc else t.ink,
                        )
                        Text(
                            text = desc[option] ?: "",
                            fontSize = 8.5.sp,
                            color = t.sub,
                            modifier = Modifier.padding(top = 1.dp),
                        )
                    }
                    if (selected) {
                        Box(
                            modifier = Modifier.size(7.dp).clip(CircleShape).background(t.acc),
                        )
                    }
                }
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 18.dp, end = 18.dp, top = 10.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .border(1.5.dp, t.ink, RoundedCornerShape(10.dp))
                    .clickable(onClick = onDismiss)
                    .padding(vertical = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = stringResource(android.R.string.cancel),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 2.sp,
                    color = t.ink,
                )
            }
        }
    }
}
