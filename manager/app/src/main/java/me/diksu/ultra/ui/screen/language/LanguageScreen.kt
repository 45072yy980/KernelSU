package me.diksu.ultra.ui.screen.language

import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import me.diksu.ultra.R
import me.diksu.ultra.i18n.AppLanguage
import me.diksu.ultra.i18n.LanguagePack
import me.diksu.ultra.i18n.LanguagePackStore
import me.diksu.ultra.ui.component.FeatureCard
import me.diksu.ultra.ui.component.FeatureMessage
import me.diksu.ultra.ui.component.FeatureRadioRow
import me.diksu.ultra.ui.component.FeatureScaffold
import me.diksu.ultra.ui.component.FeatureSectionTitle
import me.diksu.ultra.ui.component.FeatureText
import me.diksu.ultra.ui.navigation3.LocalNavigator

/**
 * 内置语种：只列管理器真的带了资源的那些。
 *
 * [tag] 是资源目录名转成的 BCP-47；[endonym] 用该语言自己的写法，
 * 这样找自己母语的人不用先看懂当前界面语言。
 */
private data class BuiltInLanguage(val tag: String, val endonym: String)

private val BUILT_IN = listOf(
    BuiltInLanguage("en", "English"),
    BuiltInLanguage("zh-CN", "简体中文"),
    BuiltInLanguage("zh-HK", "繁體中文（香港）"),
    BuiltInLanguage("zh-TW", "繁體中文（台灣）"),
    BuiltInLanguage("ja", "日本語"),
    BuiltInLanguage("ko", "한국어"),
    BuiltInLanguage("ru", "Русский"),
    BuiltInLanguage("uk", "Українська"),
    BuiltInLanguage("de", "Deutsch"),
    BuiltInLanguage("fr", "Français"),
    BuiltInLanguage("es", "Español"),
    BuiltInLanguage("it", "Italiano"),
    BuiltInLanguage("pt", "Português"),
    BuiltInLanguage("pt-BR", "Português (Brasil)"),
    BuiltInLanguage("nl", "Nederlands"),
    BuiltInLanguage("pl", "Polski"),
    BuiltInLanguage("tr", "Türkçe"),
    BuiltInLanguage("vi", "Tiếng Việt"),
    BuiltInLanguage("th", "ไทย"),
    BuiltInLanguage("id", "Bahasa Indonesia"),
    BuiltInLanguage("ms", "Bahasa Melayu"),
    BuiltInLanguage("fil", "Filipino"),
    BuiltInLanguage("tl", "Tagalog"),
    BuiltInLanguage("hi", "हिन्दी"),
    BuiltInLanguage("bn", "বাংলা"),
    BuiltInLanguage("bn-BD", "বাংলা (বাংলাদেশ)"),
    BuiltInLanguage("te", "తెలుగు"),
    BuiltInLanguage("kn", "ಕನ್ನಡ"),
    BuiltInLanguage("mr", "मराठी"),
    BuiltInLanguage("ar", "العربية"),
    BuiltInLanguage("fa", "فارسی"),
    BuiltInLanguage("he", "עברית"),
    BuiltInLanguage("hu", "Magyar"),
    BuiltInLanguage("ro", "Română"),
    BuiltInLanguage("bg", "Български"),
    BuiltInLanguage("hr", "Hrvatski"),
    BuiltInLanguage("sr", "Српски"),
    BuiltInLanguage("sl", "Slovenščina"),
    BuiltInLanguage("bs", "Bosanski"),
    BuiltInLanguage("da", "Dansk"),
    BuiltInLanguage("et", "Eesti"),
    BuiltInLanguage("lv", "Latviešu"),
    BuiltInLanguage("lt", "Lietuvių"),
    BuiltInLanguage("gl", "Galego"),
    BuiltInLanguage("km", "ខ្មែរ"),
    BuiltInLanguage("my", "မြန်မာ"),
    BuiltInLanguage("az", "Azərbaycan"),
)

@Composable
fun LanguageScreen() {
    val navigator = LocalNavigator.current
    val context = LocalContext.current
    val activity = LocalActivity.current
    val store = remember { LanguagePackStore(context) }

    var choice by remember { mutableStateOf(AppLanguage.choice(context)) }
    var packs by remember { mutableStateOf(store.list()) }
    var status by remember { mutableStateOf<String?>(null) }
    var statusIsError by remember { mutableStateOf(false) }
    // 列表很长，内置语种默认折叠，避免一进页面就是 45 行。
    var showAllBuiltIn by remember { mutableIntStateOf(0) }

    /** 切换后必须重建 Activity：资源在 attachBaseContext 时确定。 */
    fun apply(newChoice: String) {
        if (newChoice == choice) return
        AppLanguage.setChoice(context, newChoice)
        choice = newChoice
        status = null
        activity?.recreate()
    }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        store.import(uri).fold(
            onSuccess = {
                packs = store.list()
                statusIsError = false
                status = null
                // 导入成功直接切过去，省一步；语言包不对再点回「跟随系统」。
                apply(AppLanguage.packChoice(it.id))
            },
            onFailure = {
                statusIsError = true
                status = it.message ?: it::class.java.simpleName
            },
        )
    }

    FeatureScaffold(
        title = stringResource(R.string.language_title),
        onBack = { navigator.pop() },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.padding(horizontal = 12.dp),
            contentPadding = padding,
        ) {
            item {
                FeatureCard {
                    FeatureRadioRow(
                        title = stringResource(R.string.language_follow_system),
                        summary = systemLanguageName(),
                        selected = choice == AppLanguage.SYSTEM,
                        onClick = { apply(AppLanguage.SYSTEM) },
                    )
                }
            }

            item {
                val shown = if (showAllBuiltIn == 0) BUILT_IN.take(8) else BUILT_IN
                FeatureSectionTitle(stringResource(R.string.language_builtin))
                FeatureCard {
                    shown.forEach { language ->
                        FeatureRadioRow(
                            title = language.endonym,
                            summary = null,
                            selected = choice == AppLanguage.localeChoice(language.tag),
                            onClick = { apply(AppLanguage.localeChoice(language.tag)) },
                        )
                    }
                }
                FeatureActionButtonInline(
                    text = if (showAllBuiltIn == 0) {
                        stringResource(R.string.language_show_all, BUILT_IN.size)
                    } else {
                        stringResource(R.string.language_collapse)
                    },
                    onClick = { showAllBuiltIn = if (showAllBuiltIn == 0) 1 else 0 },
                )
            }

            item {
                FeatureSectionTitle(stringResource(R.string.language_packs))
                FeatureCard {
                    if (packs.isEmpty()) {
                        FeatureText(
                            text = stringResource(R.string.language_packs_empty),
                            muted = true,
                            size = 13,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                        )
                    } else {
                        packs.forEach { pack ->
                            FeatureRadioRow(
                                title = pack.name,
                                summary = packSummary(pack),
                                selected = choice == AppLanguage.packChoice(pack.id),
                                onClick = { apply(AppLanguage.packChoice(pack.id)) },
                            )
                        }
                    }
                }
            }

            item {
                FeatureCard {
                    FeatureActionButtonInline(
                        text = stringResource(R.string.language_import),
                        onClick = { importLauncher.launch(arrayOf("application/json", "text/plain", "*/*")) },
                    )
                    packs.forEach { pack ->
                        FeatureActionButtonInline(
                            text = stringResource(R.string.language_delete_pack, pack.name),
                            onClick = {
                                store.delete(pack.id)
                                // 删掉的正是当前选中的包，就退回跟随系统。
                                if (choice == AppLanguage.packChoice(pack.id)) {
                                    AppLanguage.setChoice(context, AppLanguage.SYSTEM)
                                    choice = AppLanguage.SYSTEM
                                    activity?.recreate()
                                }
                                packs = store.list()
                            },
                        )
                    }
                }
                status?.let { FeatureMessage(text = it, error = statusIsError) }
                FeatureText(
                    text = stringResource(R.string.language_pack_help),
                    muted = true,
                    size = 13,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                )
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

/** 单条可点文字，用在卡片底部当次要操作。 */
@Composable
private fun FeatureActionButtonInline(text: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FeatureText(text = text, size = 14)
    }
}

@Composable
private fun packSummary(pack: LanguagePack): String {
    val count = stringResource(R.string.language_pack_entries, pack.strings.size)
    val locale = pack.locale ?: return count
    return "$locale · $count"
}

@Composable
private fun systemLanguageName(): String {
    val locale = java.util.Locale.getDefault()
    return locale.getDisplayName(locale).replaceFirstChar { it.titlecase(locale) }
}
