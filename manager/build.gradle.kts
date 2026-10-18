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

fun gitLines(vararg args: String): String {
    val process = Runtime.getRuntime().exec(arrayOf("git") + args)
    val out = process.inputStream.bufferedReader().use { it.readText().trim() }
    process.waitFor()
    return out
}

// The version lives in the tags, not in the commit count. This repository is a
// shallow fork -- `rev-list --count HEAD` sees a handful of commits, while the
// KernelSU history it was cut from has tens of thousands -- so an offset-based
// number would slide backwards and every install would be refused as a
// downgrade. A tag names the release explicitly and the commits after it are
// counted on top, which keeps the number moving forward without ever needing a
// hand-written constant:
//
//     v3.5.0-diksu             -> 3*10000 + 5*1000 + 0*100 = 35000
//     v3.5.0-diksu + 4 commits -> 35004
//     v3.5.1-diksu             -> 35100
//     v4.0.0                   -> 40000
//
// The kernel derives KSU_VERSION from this very same tag (see kernel/Kbuild),
// so the number the manager reports and the number the kernel reports agree.
private val VERSION_TAG_PATTERN = Regex("""^v(\d+)\.(\d+)\.(\d+)""")

fun getGitTag(): String = gitLines("describe", "--tags", "--abbrev=0", "--match", "v[0-9]*")

fun getGitCommitCount(): Int {
    return gitLines("rev-list", "--count", "HEAD").toInt()
}

// How many commits sit on top of the nearest version tag.
//
// --first-parent：只数本分支自己的提交。合并上游时那条支线会带进来成百上千个
// 上游提交，按全量计数会让 versionCode 直接跳进下一个小版本区间（例如 3.5.7 的
// 构建算出 38399），以后发 3.5.8 反而装不上去。
fun getCommitsSinceTag(): Int {
    val tag = getGitTag()
    if (tag.isEmpty()) return 0
    return gitLines("rev-list", "--count", "--first-parent", "$tag..HEAD").toInt()
}

// The short hash of HEAD, for the version name only.
fun getGitShortHash(): String = gitLines("rev-parse", "--short=9", "HEAD")

private fun parseVersionTag(): Triple<Int, Int, Int> {
    val tag = getGitTag()
    val m = VERSION_TAG_PATTERN.find(tag)
        ?: error("No version tag found. Tag the release first, e.g. `git tag v3.5.0-diksu`.")
    val (major, minor, patch) = m.destructured
    return Triple(major.toInt(), minor.toInt(), patch.toInt())
}

// versionCode 的权重。kernel/Kbuild 的 KSU_VERSION 用的是同一组数字 —— 两边语言不同，
// 没法共用一个实现，所以这里显式写出来，再由文件末尾的 checkVersionFormula 校验：
// 改了一侧没改另一侧，构建会直接失败。
val versionMajorWeight = 10000
val versionMinorWeight = 1000
val versionPatchWeight = 100

fun getVersionCode(): Int {
    val (major, minor, patch) = parseVersionTag()
    // ⚠️ 此公式必须与 kernel/Kbuild 中 KSU_VERSION 的计算保持一致：
    // major*10000 + minor*1000 + patch*100 + commits_after_tag
    return major * versionMajorWeight + minor * versionMinorWeight +
        patch * versionPatchWeight + getCommitsSinceTag()
}

fun getVersionName(): String {
    val (major, minor, patch) = parseVersionTag()
    return "$major.$minor.$patch-${getGitShortHash()}"
}

// Must come last: the version helpers read VERSION_TAG_PATTERN, which is only
// initialised where it is declared. Reading them from the top of the script
// would hand a null pattern to find() and fail the build.
extra["managerVersionCode"] = getVersionCode()
extra["managerVersionName"] = getVersionName()

// ── 构建期自检：versionCode 公式一致性 ──────────────────────────────────────
// 管理器（本文件）与内核（kernel/Kbuild 的 KSU_VERSION）各算一遍 versionCode。
// 两侧分叉时，管理器会认为内核版本对不上（状态卡报错、拒绝安装），而症状完全看不出
// 是"公式被改了"，所以在构建期直接拦住，并指出该改哪两处。
tasks.register("checkVersionFormula") {
    group = "verification"
    description = "校验 kernel/Kbuild 的 KSU_VERSION 权重与本文件的 versionCode 公式一致"

    val kbuildFile = file("../kernel/Kbuild")
    inputs.file(kbuildFile)

    doLast {
        if (!kbuildFile.isFile) {
            throw GradleException("找不到 ${kbuildFile.path}，无法校验 versionCode 公式")
        }
        val kbuildLine = kbuildFile.readLines().firstOrNull { it.contains("KSU_VERSION :=") }
            ?: throw GradleException("kernel/Kbuild 里找不到 `KSU_VERSION :=` 定义")

        val kernelWeights = Regex("""\*\s*(\d+)""")
            .findAll(kbuildLine)
            .map { it.groupValues[1].toInt() }
            .toList()
        val managerWeights = listOf(versionMajorWeight, versionMinorWeight, versionPatchWeight)

        if (kernelWeights != managerWeights) {
            throw GradleException(
                """
                |versionCode 公式分叉了：
                |  kernel/Kbuild (KSU_VERSION)  的权重: $kernelWeights
                |  manager/build.gradle.kts     的权重: $managerWeights
                |
                |期望两边都是: major*$versionMajorWeight + minor*$versionMinorWeight +
                |             patch*$versionPatchWeight + commits_after_tag
                |
                |改公式时两处必须同时改：
                |  1. kernel/Kbuild 的 KSU_VERSION 定义
                |  2. manager/build.gradle.kts 的 versionMajorWeight / versionMinorWeight / versionPatchWeight
                """.trimMargin()
            )
        }
        logger.lifecycle("versionCode 公式一致（权重 $managerWeights）")
    }
}
