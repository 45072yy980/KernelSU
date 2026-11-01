package me.diksu.ultra.ui.screen.about

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.mikepenz.aboutlibraries.Libs
import com.mikepenz.aboutlibraries.entity.Library
import com.mikepenz.aboutlibraries.util.withJson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.diksu.ultra.R
import me.diksu.ultra.ui.LocalUiMode
import me.diksu.ultra.ui.UiMode
import me.diksu.ultra.ui.component.material.SegmentedColumn
import me.diksu.ultra.ui.component.material.SegmentedListItem
import me.diksu.ultra.ui.component.material.TopBarBackButton
import me.diksu.ultra.ui.navigation3.LocalNavigator
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme
import top.yukonga.miuix.kmp.basic.Scaffold as MiuixScaffold

/**
 * 本构建链接的第三方库及其许可证。
 *
 * 列表不是手写的：AboutLibraries 插件在构建期扫描依赖、把清单写进 `res/raw`，这里读回来。
 * 所以新增的依赖会自己出现，不会有"文档忘了更新"的问题。
 */
@Composable
fun OpenSourceLicenseScreen() {
    val context = LocalContext.current
    val libraries by produceState<List<Library>>(initialValue = emptyList(), context) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                Libs.Builder().withJson(context, R.raw.aboutlibraries).build().libraries
            }.getOrDefault(emptyList())
        }
    }
    when (LocalUiMode.current) {
        UiMode.Miuix, UiMode.MiuixStock -> OpenSourceLicenseScreenMiuix(libraries)
        UiMode.Material -> OpenSourceLicenseScreenMaterial(libraries)
    }
}

/** 一行副标题：版本与许可证拼在一起，缺哪个就不显示哪个。 */
private fun librarySummary(library: Library): String {
    val version = library.artifactVersion?.takeIf { it.isNotBlank() }
    val licences = library.licenses.joinToString(", ") { it.name }.takeIf { it.isNotBlank() }
    return listOfNotNull(version, licences).joinToString(" · ")
}

@Composable
private fun OpenSourceLicenseScreenMiuix(libraries: List<Library>) {
    val navigator = LocalNavigator.current
    val title = stringResource(R.string.open_source_license)
    val count = stringResource(R.string.open_source_license_count, libraries.size)
    MiuixScaffold(
        topBar = {
            SmallTopAppBar(
                title = title,
                navigationIcon = {
                    IconButton(onClick = { navigator.pop() }) {
                        Icon(
                            imageVector = MiuixIcons.Back,
                            contentDescription = null,
                            tint = colorScheme.onBackground,
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 12.dp),
        ) {
            item {
                SmallTitle(
                    text = count,
                    modifier = Modifier.padding(top = 12.dp),
                )
                Card(modifier = Modifier.padding(top = 12.dp).fillMaxWidth()) {
                    libraries.forEach { library ->
                        BasicComponent(
                            title = library.name,
                            summary = librarySummary(library),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun OpenSourceLicenseScreenMaterial(libraries: List<Library>) {
    val navigator = LocalNavigator.current
    val count = stringResource(R.string.open_source_license_count, libraries.size)
    androidx.compose.material3.Scaffold(
        topBar = {
            androidx.compose.material3.TopAppBar(
                title = { Text(stringResource(id = R.string.open_source_license)) },
                navigationIcon = {
                    TopBarBackButton(onClick = { navigator.pop() })
                },
            )
        },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
        ) {
            item {
                SegmentedColumn(
                    modifier = Modifier.padding(top = 12.dp),
                    title = count,
                    content = buildList<@Composable () -> Unit> {
                        libraries.forEach { library ->
                            add {
                                SegmentedListItem(
                                    headlineContent = { Text(library.name) },
                                    supportingContent = {
                                        val summary = librarySummary(library)
                                        if (summary.isNotEmpty()) {
                                            Text(
                                                text = summary,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                        }
                                    },
                                )
                            }
                        }
                    },
                )
            }
        }
    }
}
