package me.weishu.kernelsu.ui.util

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * 内置越狱激活的第二条路：DirtyFrag（CVE-2026-43284）。
 *
 * 和 ghostlock 不同，DirtyFrag 的利用链（IpSec/AES-CBC 改页缓存、hook `libc++.so`、
 * 由 init 装模块）是一整个独立应用，没有任何对外接口，所以这里能做的适配是：
 * 把它的安装包准备好、把它拉起来，并保证本应用符合它挑 SU 管理器的要求。
 *
 * 那个要求是「SU 管理器自带 libksud.so」——本应用的 APK 里确实带
 * `lib/arm64-v8a/libksud.so`，所以 DirtyFrag 的 SU Manager 卡片里可以直接选它。
 * 选好之后 DirtyFrag 会通过 ksud 的 `late-load` 把 root 交给本应用，
 * 和 ghostlock 那条路最终到达的是同一个状态，区别只在用哪个漏洞进来。
 */
object DirtyFrag {

    /** DirtyFrag 的包名（`df.root`），也是它申请 root 时的身份。 */
    const val PACKAGE = "df.root"

    private const val API_LATEST = "https://api.github.com/repos/mitschud/DirtyFrag/releases/latest"

    fun isInstalled(context: Context): Boolean =
        runCatching { context.packageManager.getPackageInfo(PACKAGE, 0) }.isSuccess

    /** 打开 DirtyFrag 主界面；它没有对外 action，只能这样拉起来。 */
    fun launch(context: Context): Boolean = runCatching {
        val intent = context.packageManager.getLaunchIntentForPackage(PACKAGE) ?: return false
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
        true
    }.getOrDefault(false)

    /**
     * 下载最新 release 里的 APK 到缓存目录。
     *
     * 走 GitHub 的 latest 接口而不是写死版本号：作者更新很勤，写死的链接几周就过期了。
     */
    suspend fun downloadLatest(context: Context): File? = withContext(Dispatchers.IO) {
        runCatching {
            val json = (URL(API_LATEST).openConnection() as HttpURLConnection).run {
                connectTimeout = 15_000
                readTimeout = 30_000
                setRequestProperty("Accept", "application/vnd.github+json")
                inputStream.bufferedReader().use { it.readText() }
            }
            val assets = JSONObject(json).getJSONArray("assets")
            var apkUrl: String? = null
            for (i in 0 until assets.length()) {
                val asset = assets.getJSONObject(i)
                if (asset.optString("name").endsWith(".apk", ignoreCase = true)) {
                    apkUrl = asset.optString("browser_download_url")
                    break
                }
            }
            val url = apkUrl ?: return@runCatching null
            val target = File(context.cacheDir, "DirtyFrag.apk")
            URL(url).openStream().use { input ->
                target.outputStream().use { output -> input.copyTo(output) }
            }
            target
        }.getOrNull()
    }

    /** 把下载好的 APK 交给系统安装器（需要用户允许本应用安装未知来源应用）。 */
    fun install(context: Context, apk: File): Boolean = runCatching {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", apk)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
        true
    }.getOrDefault(false)
}
