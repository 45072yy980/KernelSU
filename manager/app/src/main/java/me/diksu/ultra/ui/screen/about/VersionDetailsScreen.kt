package me.diksu.ultra.ui.screen.about

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.diksu.ultra.Natives
import me.diksu.ultra.R
import me.diksu.ultra.ksuApp
import me.diksu.ultra.ui.LocalUiMode
import me.diksu.ultra.ui.UiMode
import me.diksu.ultra.ui.component.material.TopBarBackButton
import me.diksu.ultra.ui.navigation3.LocalNavigator
import me.diksu.ultra.ui.screen.home.getManagerVersion
import me.diksu.ultra.ui.util.KsudProbe
import me.diksu.ultra.ui.util.probeInstalledKsudVersion
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.basic.Text as MiuixText
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme
import top.yukonga.miuix.kmp.basic.Scaffold as MiuixScaffold

/**
 * Where the three versions are listed side by side: the manager APK, the kernel module that is
 * actually loaded, and the ksud userspace binary on disk.
 *
 * The home status card shows the kernel module's version only, and in jailbreak mode that number
 * cannot follow the manager: the module is injected when the exploit runs, while `ksud install`
 * only replaces the userspace binary. Putting all three here is what makes "updated ksud but the
 * number did not change" read as a deliberate difference rather than a stuck value.
 */
@Immutable
data class VersionDetails(
    val manager: String = "",
    val kernel: String? = null,
    val ksud: String? = null,
    /** 读不到 ksud 时的原因（试过哪些路径、各自失败在哪），读到了就是来源路径。 */
    val ksudDetail: String? = null,
    val mode: String = "",
)

@Composable
fun VersionDetailsScreen() {
    val details by produceState(initialValue = VersionDetails(), Unit) {
        value = withContext(Dispatchers.IO) { readVersionDetails() }
    }
    when (LocalUiMode.current) {
        UiMode.Miuix, UiMode.MiuixStock -> VersionDetailsScreenMiuix(details)
        UiMode.Material -> VersionDetailsScreenMaterial(details)
    }
}

/** Read-only snapshot; every call may touch root (ksud on disk), so it runs off the main thread. */
private fun readVersionDetails(): VersionDetails {
    val managerInfo = getManagerVersion(ksuApp)
    val probe = runCatching { probeInstalledKsudVersion() }
        .getOrElse { KsudProbe(null, "探测本身失败：${it.message ?: it::class.java.simpleName}") }
    val kernelVersion = runCatching { Natives.version }.getOrDefault(0)
    val mode = when {
        runCatching { Natives.isLateLoadMode }.getOrDefault(false) -> MODE_JAILBREAK
        runCatching { Natives.isLkmMode }.getOrDefault(false) -> MODE_LKM
        kernelVersion > 0 -> MODE_BUILTIN
        else -> MODE_NONE
    }
    return VersionDetails(
        manager = "${managerInfo.versionName} (${managerInfo.versionCode})",
        kernel = if (kernelVersion > 0) {
            "$kernelVersion (${Natives.kernelUAPIVersion})"
        } else {
            null
        },
        ksud = probe.version,
        ksudDetail = if (probe.version == null) probe.detail else null,
        mode = mode,
    )
}

private const val MODE_JAILBREAK = "jailbreak"
private const val MODE_LKM = "lkm"
private const val MODE_BUILTIN = "builtin"
private const val MODE_NONE = "none"

@Composable
private fun modeLabel(mode: String): String = when (mode) {
    MODE_JAILBREAK -> stringResource(R.string.jailbreak_mode)
    MODE_LKM -> "LKM"
    MODE_BUILTIN -> stringResource(R.string.version_details_mode_builtin)
    else -> stringResource(R.string.version_details_unknown)
}

@Composable
private fun versionRows(details: VersionDetails): List<Pair<String, String>> {
    val unknown = stringResource(R.string.version_details_unknown)
    return listOf(
        stringResource(R.string.version_details_manager) to details.manager,
        stringResource(R.string.version_details_kernel) to (details.kernel ?: unknown),
        stringResource(R.string.version_details_ksud) to (
                details.ksud ?: details.ksudDetail?.take(160)?.let { "$unknown（$it）" } ?: unknown
                ),
        stringResource(R.string.version_details_mode) to modeLabel(details.mode),
    )
}

@Composable
private fun VersionDetailsScreenMiuix(details: VersionDetails) {
    val navigator = LocalNavigator.current
    val rows = versionRows(details)
    MiuixScaffold(
        topBar = {
            SmallTopAppBar(
                title = stringResource(id = R.string.version_details_title),
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
            items(count = rows.size) { index ->
                val (label, value) = rows[index]
                Column(modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp)) {
                    MiuixText(
                        text = label,
                        color = colorScheme.onSurface,
                        style = MiuixTheme.textStyles.body1,
                    )
                    MiuixText(
                        text = value,
                        color = colorScheme.onSurfaceVariantSummary,
                        style = MiuixTheme.textStyles.footnote1,
                    )
                }
            }
            item {
                Spacer(Modifier.height(12.dp))
                MiuixText(
                    text = stringResource(R.string.version_details_note),
                    color = colorScheme.onSurfaceVariantSummary,
                    style = MiuixTheme.textStyles.footnote1,
                )
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun VersionDetailsScreenMaterial(details: VersionDetails) {
    val navigator = LocalNavigator.current
    val rows = versionRows(details)
    androidx.compose.material3.Scaffold(
        topBar = {
            androidx.compose.material3.TopAppBar(
                title = { Text(stringResource(id = R.string.version_details_title)) },
                navigationIcon = { TopBarBackButton(onClick = { navigator.pop() }) },
            )
        },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
        ) {
            items(count = rows.size) { index ->
                val (label, value) = rows[index]
                Column(modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp)) {
                    Text(text = label, style = MaterialTheme.typography.titleSmall)
                    Text(
                        text = value,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                        fontSize = 13.sp,
                    )
                }
            }
            item {
                Text(
                    text = stringResource(R.string.version_details_note),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 12.dp, bottom = 24.dp),
                )
            }
        }
    }
}
