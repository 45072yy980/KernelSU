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
fun getCommitsSinceTag(): Int {
    val tag = getGitTag()
    if (tag.isEmpty()) return 0
    return gitLines("rev-list", "--count", "$tag..HEAD").toInt()
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

fun getVersionCode(): Int {
    val (major, minor, patch) = parseVersionTag()
    return major * 10000 + minor * 1000 + patch * 100 + getCommitsSinceTag()
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
