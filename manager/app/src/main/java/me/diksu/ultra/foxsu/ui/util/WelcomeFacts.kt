package me.diksu.ultra.foxsu.ui.util

import android.os.Build
import me.diksu.ultra.Natives

/**
 * 欢迎页要报给用户看的「这台设备的真实情况」。
 *
 * 首屏不放「我们有什么功能」的自夸，而是直接把设备型号、内核版本、已装模块数、
 * 已授权应用数摊开——这些东西一旦写死就会变成谎话，所以全部现场读。
 */
data class WelcomeFacts(
    val device: String,
    val kernel: String,
    val modules: Int,
    val su: Int,
    val root: Boolean,
) {
    companion object {
        fun fromBuild(): WelcomeFacts = WelcomeFacts(
            device = Build.MODEL?.trim().takeUnless { it.isNullOrEmpty() } ?: "Android",
            kernel = "—",
            modules = 0,
            su = 0,
            root = false,
        )
    }
}

/** 一次采集：内核版本 / 模块数 / 授权数 / root 是否可用。失败一律降级，不猜。 */
fun readWelcomeFacts(): WelcomeFacts {
    val base = WelcomeFacts.fromBuild()
    val root = runCatching { rootAvailable() }.getOrDefault(false)
    val kernel = runCatching {
        val v = Natives.version
        if (v <= 0) "—" else "0x%X".format(v)
    }.getOrDefault("—")
    val modules = runCatching { getModuleCount() }.getOrDefault(0)
    val su = runCatching { getSuperuserCount() }.getOrDefault(0)
    return base.copy(kernel = kernel, modules = modules, su = su, root = root)
}
