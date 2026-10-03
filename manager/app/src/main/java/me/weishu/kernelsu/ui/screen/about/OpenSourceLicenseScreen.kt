package me.weishu.kernelsu.ui.screen.about

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import me.weishu.kernelsu.R
import me.weishu.kernelsu.ui.LocalUiMode
import me.weishu.kernelsu.ui.UiMode
import me.weishu.kernelsu.ui.component.material.TopBarBackButton
import me.weishu.kernelsu.ui.navigation3.LocalNavigator
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.basic.Scaffold as MiuixScaffold
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.basic.Text as MiuixText
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme

/**
 * The third-party libraries this build links against, with the licence each is
 * under.
 *
 * The list is not written by hand: the AboutLibraries Gradle plugin walks the
 * resolved dependencies at build time and writes a JSON manifest into `res/raw`,
 * which is read back here. A library added in some unrelated commit therefore
 * shows up on its own, and nothing can quietly fall out of date.
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

@Composable
private fun OpenSourceLicenseScreenMiuix(libraries: List<Library>) {
    val navigator = LocalNavigator.current
    MiuixScaffold(
        topBar = {
            SmallTopAppBar(
                title = stringResource(id = R.string.open_source_license),
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
                .padding(horizontal = 16.dp),
        ) {
            items(libraries, key = { it.uniqueId }) { library ->
                Column(modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp)) {
                    MiuixText(
                        text = library.name,
                        color = colorScheme.onSurface,
                        style = MiuixTheme.textStyles.body1,
                    )
                    MiuixLibraryMeta(library)
                }
            }
        }
    }
}

@Composable
private fun MiuixLibraryMeta(library: Library) {
    val tint = colorScheme.onSurfaceVariantSummary
    library.artifactVersion?.takeIf { it.isNotBlank() }?.let { version ->
        MiuixText(text = version, color = tint, style = MiuixTheme.textStyles.footnote1)
    }
    library.licenses.joinToString(", ") { it.name }.takeIf { it.isNotBlank() }?.let { licences ->
        MiuixText(text = licences, color = tint, style = MiuixTheme.textStyles.footnote1)
    }
}

@Composable
private fun OpenSourceLicenseScreenMaterial(libraries: List<Library>) {
    val navigator = LocalNavigator.current
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
            items(libraries, key = { it.uniqueId }) { library ->
                Column(modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp)) {
                    Text(
                        text = library.name,
                        style = MaterialTheme.typography.titleSmall,
                    )
                    library.artifactVersion?.takeIf { it.isNotBlank() }?.let { version ->
                        Text(
                            text = version,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    library.licenses.joinToString(", ") { it.name }
                        .takeIf { it.isNotBlank() }
                        ?.let { licences ->
                            Text(
                                text = licences,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                }
            }
        }
    }
}