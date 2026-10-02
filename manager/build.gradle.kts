plugins {
    alias(libs.plugins.agp.app) apply false
    alias(libs.plugins.kotlin) apply false
    alias(libs.plugins.compose.compiler) apply false
}

extra["androidMinSdkVersion"] = 31
extra["androidTargetSdkVersion"] = 37
extra["androidCompileSdkVersion"] = 37
extra["androidCompileSdkVersionMinor"] = 0
extra["androidBuildToolsVersion"] = "37.0.0"
extra["androidCompileNdkVersion"] = libs.versions.ndk.get()
extra["androidSourceCompatibility"] = JavaVersion.VERSION_21
extra["androidTargetCompatibility"] = JavaVersion.VERSION_21
extra["managerVersionCode"] = getVersionCode()
extra["managerVersionName"] = getVersionName()

fun getGitCommitCount(): Int {
    val process = Runtime.getRuntime().exec(arrayOf("git", "rev-list", "--count", "HEAD"))
    return process.inputStream.bufferedReader().use { it.readText().trim().toInt() }
}

fun getGitDescribe(): String {
    val process = Runtime.getRuntime().exec(arrayOf("git", "describe", "--tags", "--always"))
    return process.inputStream.bufferedReader().use { it.readText().trim() }
}

fun getVersionCode(): Int {
    // 换用 wuhudiao/DikSU 作为基线后，本仓库的 git 历史很浅（rev-list 只数到 10 左右），
    // 继续用 `30000 + commitCount` 会让 versionCode 反过来低于 v3.4.x 的 62668~62707，
    // 已装旧版的设备会 INSTALL_FAILED_VERSION_DOWNGRADE。这里改成固定的发布号。
    // 约定：34500=v3.5.0，之后 34501、34502… 依次递增；换大版本再用 34600/34700…
    return 34500
}
fun getVersionName(): String {
    return getGitDescribe()
}
