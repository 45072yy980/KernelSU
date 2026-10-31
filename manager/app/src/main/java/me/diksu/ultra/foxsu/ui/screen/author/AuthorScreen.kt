package me.diksu.ultra.foxsu.ui.screen.author

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Public
import androidx.compose.material3.Icon
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.dropUnlessResumed
import me.diksu.ultra.BuildConfig
import me.diksu.ultra.R
import me.diksu.ultra.foxsu.ui.LocalUiMode
import me.diksu.ultra.foxsu.ui.UiMode
import me.diksu.ultra.foxsu.ui.component.freshCardBorder
import me.diksu.ultra.foxsu.ui.component.freshCardColor
import me.diksu.ultra.foxsu.ui.component.material.ExpressiveScaffold
import me.diksu.ultra.foxsu.ui.component.material.TopBarBackButton
import me.diksu.ultra.foxsu.ui.component.material.expressiveTopAppBarColors
import me.diksu.ultra.foxsu.ui.navigation3.LocalNavigator
import me.diksu.ultra.foxsu.ui.navigation3.Route
import me.diksu.ultra.foxsu.ui.theme.UiPalette
import me.diksu.ultra.foxsu.ui.component.isNightScheme
import androidx.compose.foundation.BorderStroke

/**
 * ColorUI 的「关于作者」页：头像 + 维护者信息，圆角头像卡与页面其他卡片同语言。
 */
@Composable
fun AuthorScreen() {
    val navigator = LocalNavigator.current
    val uriHandler = LocalUriHandler.current
    when (LocalUiMode.current) {
        UiMode.Material -> {
            me.diksu.ultra.foxsu.ui.screen.editorial.EditorialAuthor(
                onBack = dropUnlessResumed { navigator.pop() },
                onOpenUrl = uriHandler::openUri,
            )
            return
        }
        UiMode.Glass -> {
            me.diksu.ultra.foxsu.ui.screen.glass.GlassAuthor(
                onBack = dropUnlessResumed { navigator.pop() },
                onOpenUrl = uriHandler::openUri,
            )
            return
        }
        UiMode.Nocturne -> {
            me.diksu.ultra.foxsu.ui.screen.nocturne.NocturneAuthor(
                onBack = dropUnlessResumed { navigator.pop() },
                onOpenUrl = uriHandler::openUri,
            )
            return
        }
        UiMode.Miuix -> {
            me.diksu.ultra.foxsu.ui.screen.ag.AgAuthor(
                onBack = dropUnlessResumed { navigator.pop() },
                onOpenUrl = uriHandler::openUri,
                onOpenCredits = dropUnlessResumed { navigator.push(Route.Credits) },
            )
            return
        }
        else -> Unit
    }
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())

    ExpressiveScaffold(
        topBar = {
            LargeFlexibleTopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.fx_author_title),
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                },
                navigationIcon = { TopBarBackButton(onClick = dropUnlessResumed { navigator.pop() }) },
                colors = expressiveTopAppBarColors(containerColor = if (isNightScheme()) MaterialTheme.colorScheme.surface else Color.White),
                windowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
                scrollBehavior = scrollBehavior,
            )
        },
        containerColor = if (isNightScheme()) MaterialTheme.colorScheme.surface else Color.White,
        contentWindowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .nestedScroll(scrollBehavior.nestedScrollConnection)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(8.dp))

            // 头像：大圆角卡片 + 细边ring，比正圆更有质感
            Surface(
                shape = RoundedCornerShape(34.dp),
                color = if (isNightScheme()) UiPalette.HeroSurfaceDark else UiPalette.HeroSurface,
                border = BorderStroke(
                    1.dp,
                    (if (isNightScheme()) UiPalette.HeroAccentDark else UiPalette.HeroAccent).copy(alpha = 0.25f),
                ),
                modifier = Modifier.size(120.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Image(
                        painter = painterResource(R.drawable.fx_author_avatar),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(6.dp)
                            .clip(RoundedCornerShape(26.dp)),
                    )
                }
            }

            Spacer(Modifier.height(14.dp))
            Text(
                text = stringResource(R.string.fx_author_name),
                fontSize = 22.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.fx_author_role),
                fontSize = 12.5.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f),
            )

            Spacer(Modifier.height(22.dp))

            Surface(
                shape = UiPalette.CardShape,
                color = if (isNightScheme()) UiPalette.KpiMintDark.first else UiPalette.KpiMint.first,
                border = null,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = stringResource(R.string.fx_author_desc),
                    fontSize = 13.sp,
                    lineHeight = 20.sp,
                    textAlign = TextAlign.Start,
                    color = (if (isNightScheme()) UiPalette.KpiMintDark.second else UiPalette.KpiMint.second)
                        .copy(alpha = 0.9f),
                    modifier = Modifier.padding(18.dp),
                )
            }

            Spacer(Modifier.height(12.dp))

            InfoRow(
                icon = Icons.Filled.Info,
                label = stringResource(R.string.fx_author_version),
                value = "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
            )
            Spacer(Modifier.height(10.dp))
            InfoRow(
                icon = Icons.Filled.Code,
                label = stringResource(R.string.fx_author_project),
                value = stringResource(R.string.fx_author_project_value),
                onClick = { uriHandler.openUri("https://github.com/Cmry-mlc/FoxSu") },
            )
            Spacer(Modifier.height(10.dp))
            InfoRow(
                icon = Icons.Filled.Favorite,
                label = stringResource(R.string.fx_author_contact),
                value = stringResource(R.string.fx_author_contact_value),
                onClick = { uriHandler.openUri("https://github.com/Cmry-mlc/FoxSu/issues") },
            )
            Spacer(Modifier.height(10.dp))
            InfoRow(
                icon = Icons.Filled.Groups,
                label = stringResource(R.string.fx_author_qq),
                value = stringResource(R.string.fx_author_qq_value),
                onClick = { uriHandler.openUri("https://pd.qq.com/s/8glxclfk6?b=9") },
            )
            Spacer(Modifier.height(10.dp))
            InfoRow(
                icon = Icons.Filled.Public,
                label = stringResource(R.string.fx_author_upstream),
                value = stringResource(R.string.fx_author_upstream_value),
                onClick = { uriHandler.openUri("https://github.com/tiann/KernelSU") },
            )

            Spacer(Modifier.height(28.dp))
        }
    }
}

@Composable
private fun InfoRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String,
    onClick: (() -> Unit)? = null,
) {
    // 参考图配色：每行一个柔和马卡龙底，图标底板用半透明白
    val night = isNightScheme()
    val tones = if (night) {
        listOf(
            UiPalette.PillLilacDark, UiPalette.KpiMintDark,
            UiPalette.KpiCreamDark, UiPalette.KpiRoseDark, UiPalette.PillSkyDark,
        )
    } else {
        listOf(
            UiPalette.PillLilac, UiPalette.KpiMint,
            UiPalette.KpiCream, UiPalette.KpiRose, UiPalette.PillSky,
        )
    }
    val tone = tones[infoRowSlot(icon)]
    Surface(
        shape = UiPalette.CardShape,
        color = tone.first,
        contentColor = tone.second,
        border = null,
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = if (night) 0.10f else 0.66f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = tone.second,
                    modifier = Modifier.size(18.dp),
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = label,
                    fontSize = 11.5.sp,
                    color = tone.second.copy(alpha = 0.62f),
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = value,
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = tone.second,
                )
            }
            if (onClick != null) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = stringResource(R.string.fx_author_tap_hint),
                    tint = tone.second.copy(alpha = 0.38f),
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}

/** 按图标给每行分配一个固定的色槽，保证「版本 / 项目 / 联系 / QQ / 上游」各不相同。 */
private fun infoRowSlot(icon: androidx.compose.ui.graphics.vector.ImageVector): Int = when (icon) {
    Icons.Filled.Info -> 0
    Icons.Filled.Code -> 1
    Icons.Filled.Favorite -> 2
    Icons.Filled.Groups -> 3
    Icons.Filled.Public -> 4
    else -> 0
}
