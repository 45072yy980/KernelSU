package me.diksu.ultra.foxsu.ui.util

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import me.diksu.ultra.BuildConfig
import me.diksu.ultra.foxsu.ui.util.module.LatestVersionInfo

/**
 * 全局更新状态。
 *
 * 主页刷新时会把 checkNewVersion() 的真实结果 [publish] 进来，
 * 标题栏的更新胶囊 / 各主题主页共用这一份数据，避免重复请求 GitHub。
 */
object UpdateCenter {
    var latest by mutableStateOf(LatestVersionInfo())
        private set

    /** 只有拿到可用下载地址且版本号大于当前安装版本，才算真的有更新。 */
    val hasUpdate: Boolean
        get() = latest.versionCode > BuildConfig.VERSION_CODE && latest.downloadUrl.isNotBlank()

    fun publish(info: LatestVersionInfo) {
        latest = info
    }
}
