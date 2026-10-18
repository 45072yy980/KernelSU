@file:Suppress("UnstableApiUsage")

import com.google.protobuf.gradle.id
import org.gradle.api.tasks.Copy

plugins {
    alias(libs.plugins.agp.app)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.lsplugin.apksign)
    alias(libs.plugins.protobuf)
    alias(libs.plugins.aboutlibraries)
    alias(libs.plugins.refine)
    id("kotlin-parcelize")
}

val androidCompileSdkVersion = rootProject.extra["androidCompileSdkVersion"] as Int
val androidCompileSdkVersionMinor = rootProject.extra["androidCompileSdkVersionMinor"] as Int
val androidCompileNdkVersion = rootProject.extra["androidCompileNdkVersion"] as String
val androidBuildToolsVersion = rootProject.extra["androidBuildToolsVersion"] as String
val androidMinSdkVersion = rootProject.extra["androidMinSdkVersion"] as Int
val androidTargetSdkVersion = rootProject.extra["androidTargetSdkVersion"] as Int
val androidSourceCompatibility = rootProject.extra["androidSourceCompatibility"] as JavaVersion
val androidTargetCompatibility = rootProject.extra["androidTargetCompatibility"] as JavaVersion
val managerVersionCode = rootProject.extra["managerVersionCode"] as Int
val managerVersionName = rootProject.extra["managerVersionName"] as String

val isPrBuild = project.findProperty("IS_PR_BUILD")?.toString()?.toBoolean() ?: false
val defaultManagerPackageName = if (isPrBuild) "me.weishu.kernelsu.pr" else "me.diksu.kernelsu"
val defaultManagerName = if (isPrBuild) "KernelSU PR" else "KernelSU"
val managerPackageName = project.findProperty("KSU_PACKAGE_NAME")?.toString() ?: defaultManagerPackageName
val managerName = project.findProperty("KSU_NAME")?.toString() ?: defaultManagerName
val keystoreFileName = project.findProperty("KEYSTORE_FILE")?.toString() ?: "key.jks"
val keystorePassword = project.findProperty("KEYSTORE_PASSWORD")?.toString().orEmpty()
val keyAlias = project.findProperty("KEY_ALIAS")?.toString().orEmpty()
val keyPassword = project.findProperty("KEY_PASSWORD")?.toString().orEmpty()
val generatedKeystoreAssets = layout.buildDirectory.dir("generated/ksu-keystore-assets").get().asFile
// fpdrop 由 CMake 从 cpp/fpdrop/fpdrop.c 编出来，产物落在 build/ 下的生成 assets 目录，
// 不写源码树（写源码树会让每次构建都把工作区弄脏，且容易误提交二进制）。
val generatedFpdropAssets = layout.buildDirectory.dir("generated/fpdrop-assets").get().asFile

apksign {
    storeFileProperty = "KEYSTORE_FILE"
    storePasswordProperty = "KEYSTORE_PASSWORD"
    keyAliasProperty = "KEY_ALIAS"
    keyPasswordProperty = "KEY_PASSWORD"
}

protobuf {
    protoc {
        artifact = libs.protobuf.protoc.get().toString()
    }
    generateProtoTasks {
        ofNonTest().forEach { task ->
            task.builtins {
                id("java") {
                    option("lite")
                }
                id("kotlin") {
                    option("lite")
                }
            }
        }
    }
}

val baseCFlags = listOf(
    "-Wall", "-Qunused-arguments", "-fvisibility=hidden", "-fvisibility-inlines-hidden",
    "-fno-exceptions", "-fno-stack-protector", "-fomit-frame-pointer",
    "-Wno-builtin-macro-redefined", "-Wno-unused-value", "-D__FILE__=__FILE_NAME__"
)
val baseCppFlags = baseCFlags + "-fno-rtti"

android {
    namespace = "me.weishu.kernelsu"

    sourceSets.getByName("main").assets.srcDir(generatedKeystoreAssets)
    sourceSets.getByName("main").assets.srcDir(generatedFpdropAssets)

    buildTypes {
        debug {
            externalNativeBuild {
                cmake {
                    arguments += listOf("-DCMAKE_CXX_FLAGS_DEBUG=-Og", "-DCMAKE_C_FLAGS_DEBUG=-Og")
                }
            }
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            vcsInfo.include = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            externalNativeBuild {
                cmake {
                    arguments += "-DDEBUG_SYMBOLS_PATH=${layout.buildDirectory.get().asFile.absolutePath}/symbols"
                    arguments += "-DCMAKE_BUILD_TYPE=Release"

                    val releaseFlags = listOf(
                        "-flto", "-ffunction-sections", "-fdata-sections", "-Wl,--gc-sections",
                        "-fno-unwind-tables", "-fno-asynchronous-unwind-tables", "-Wl,--exclude-libs,ALL"
                    )
                    val configFlags = listOf("-Oz", "-DNDEBUG").joinToString(" ")

                    cppFlags += releaseFlags
                    cFlags += releaseFlags

                    arguments += listOf(
                        "-DCMAKE_CXX_FLAGS_RELEASE=$configFlags",
                        "-DCMAKE_C_FLAGS_RELEASE=$configFlags",
                        "-DCMAKE_SHARED_LINKER_FLAGS=-Wl,--gc-sections -Wl,--exclude-libs,ALL -Wl,--icf=all -s -Wl,--hash-style=sysv -Wl,-z,norelro"
                    )
                }
            }
        }
    }

    buildFeatures {
        aidl = true
        buildConfig = true
        resValues = true
        compose = true
        prefab = true
    }

    packaging {
        dex {
            useLegacyPackaging = true
        }
        jniLibs {
            useLegacyPackaging = true
            excludes += "lib/*/libandroidx.graphics.path.so"
        }
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
        }
    }

    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }

    androidResources {
        generateLocaleConfig = true
    }
    compileSdk {
        version =
            release(androidCompileSdkVersion) {
                minorApiLevel = androidCompileSdkVersionMinor
            }
    }
    buildToolsVersion = androidBuildToolsVersion
    ndkVersion = androidCompileNdkVersion

    defaultConfig {
        minSdk = androidMinSdkVersion
        targetSdk = androidTargetSdkVersion
        versionCode = managerVersionCode
        versionName = managerVersionName
        applicationId = managerPackageName

        buildConfigField("boolean", "IS_PR_BUILD", isPrBuild.toString())
        buildConfigField("String", "KSU_KEYSTORE_PASSWORD", "\"$keystorePassword\"")
        buildConfigField("String", "KSU_KEY_ALIAS", "\"$keyAlias\"")
        buildConfigField("String", "KSU_KEY_PASSWORD", "\"$keyPassword\"")
        resValue("string", "app_name", managerName)

        externalNativeBuild {
            cmake {
                arguments += "-DANDROID_STL=none"
                // fpdrop 的产物目录（见 cpp/CMakeLists.txt 的 POST_BUILD）
                arguments += "-DKSU_FPDROP_ASSET_DIR=${generatedFpdropAssets.absolutePath}"
                cFlags += baseCFlags + "-std=c2x"
                cppFlags += baseCppFlags + "-std=c++2b"
            }
        }

        ndk {
            // 默认三架构。自用精简包可以只带 arm64：
            //   ./gradlew :app:assembleRelease -PksuAbi=arm64-v8a
            // 也支持逗号分隔的多个 ABI。
            val abiOverride = (project.findProperty("ksuAbi") as String?)?.trim()
            abiFilters += if (abiOverride.isNullOrEmpty()) {
                listOf("arm64-v8a", "x86_64", "riscv64")
            } else {
                abiOverride.split(',').map { it.trim() }.filter { it.isNotEmpty() }
            }
        }
    }

    lint {
        abortOnError = true
        checkReleaseBuilds = false
    }

    compileOptions {
        sourceCompatibility = androidSourceCompatibility
        targetCompatibility = androidTargetCompatibility
    }
}

val copyKsuKeystore = tasks.register<Copy>("copyKsuKeystore") {
    from(rootProject.file(keystoreFileName))
    into(generatedKeystoreAssets)
    rename { "kernelsu.jks" }
}

tasks.matching { it.name.startsWith("merge") && it.name.endsWith("Assets") }.configureEach {
    dependsOn(copyKsuKeystore)
}

// fpdrop 也是"打包前必须已经在位"的生成资产：它由 CMake 的 fpdrop 目标写进
// generatedFpdropAssets，所以 mergeAssets 之前必须先跑完同名变体的 native 构建。
// 不接这条线的话，全新 clone（CI 就是全新 clone）会打出**不含 fpdrop** 的包，
// 分权功能静默失效——而这正是把二进制移出源码树后最容易踩的坑。
tasks.matching { it.name.startsWith("merge") && it.name.endsWith("Assets") }.configureEach {
    val variant = name.removePrefix("merge").removeSuffix("Assets")
    // mergeDebugAssets → native 的 debug 构建，mergeReleaseAssets → release；
    // androidTest 之类的变体没有对应的 native 任务，跳过。
    if (variant == "Debug" || variant == "Release") {
        // AGP 的 native 构建任务：锚点 `externalNativeBuild<变体>`，其下按 ABI 拆成
        // `buildCMake<变体>[abi]`。两个都挂上——万一某个版本重命名了锚点，另一个还在；
        // 多依赖没有副作用（native 构建本来就要跑）。两个都不存在时 dependsOn 的是空集合，
        // 不会让构建失败（但 fresh clone 的包可能缺 fpdrop，所以改名时要回来改这里）。
        dependsOn(
            tasks.matching {
                it.name == "externalNativeBuild$variant" || it.name.startsWith("buildCMake$variant")
            }
        )
    }
}

androidComponents {
    onVariants(selector().withBuildType("release")) {
        it.packaging.resources.excludes.addAll(listOf("META-INF/**", "kotlin/**", "**.bin"))
    }
}

// The launcher label carries zero-width padding on purpose — the hidden copy rewrites that very
// string slot in place, so its length is the room a custom name has. A file name must not carry it:
// the padding is invisible, so the artifact reads as `DikSU__v3.3.0-…` in every listing. It comes
// off here, and only here.
val archivePrefix = managerName.filterNot { it == '\u200b' || it.isWhitespace() }

base {
    archivesName.set("${archivePrefix}_${managerVersionName}_${managerVersionCode}")
}

dependencies {
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.core.splashscreen)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)

    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)

    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigationevent.compose)

    implementation(libs.com.github.topjohnwu.libsu.core)
    implementation(libs.com.github.topjohnwu.libsu.service)
    implementation(libs.com.github.topjohnwu.libsu.io)

    implementation(libs.dev.rikka.rikkax.parcelablelist)
    implementation(libs.dev.rikka.shizuku.api)
    implementation(libs.dev.rikka.shizuku.provider)
    implementation(libs.dev.rikka.hidden.compat)
    compileOnly(libs.dev.rikka.hidden.stub)
    implementation(libs.dev.rikka.refine.runtime)

    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.serialization.json)

    implementation(libs.commonmark)
    implementation(libs.commonmark.ext.gfm.tables)
    implementation(libs.commonmark.ext.gfm.strikethrough)
    implementation(libs.commonmark.ext.autolink)
    implementation(libs.commonmark.ext.task.list.items)

    implementation(libs.androidx.webkit)

    implementation(libs.lsposed.cxx)

    implementation(libs.hiddenapibypass)

    implementation(libs.miuix.ui)
    implementation(libs.miuix.icons)
    implementation(libs.miuix.nav)
    implementation(libs.miuix.preference)
    implementation(libs.miuix.blur)

    implementation(platform(libs.okhttp.bom))
    implementation(libs.okhttp)

    implementation(libs.material.kolor)

    implementation(libs.appiconloader)

    implementation(libs.commons.compress)
    implementation(libs.xz)
    implementation(libs.google.code.gson)
    implementation(libs.protobuf.kotlin.lite)

    // Renaming and re-signing the Manager on the device, so an install can pick its own
    // package name instead of the one baked in at build time.
    implementation(libs.apksig)
    implementation(libs.apkzlib)
    // Generates the licence manifest the open-source screen reads, and the screen itself.
    implementation(libs.aboutlibraries.core)
    implementation(libs.aboutlibraries.compose.m3)

    testImplementation(libs.junit)
}

aboutLibraries {
    library {
        // One entry per library even when several modules of it are pulled in.
        duplicationMode = com.mikepenz.aboutlibraries.plugin.DuplicateMode.MERGE
        duplicationRule = com.mikepenz.aboutlibraries.plugin.DuplicateRule.SIMPLE
    }
}

kotlin {
    compilerOptions {
        freeCompilerArgs.addAll(
            "-opt-in=androidx.compose.material3.ExperimentalMaterial3Api",
            "-opt-in=androidx.compose.material3.ExperimentalMaterial3ExpressiveApi",
        )
    }
}

// ── 构建期自检：ManagerHider 后缀覆盖检查 ────────────────────────────────────
// 隐藏管理器（随机包名）时，ManagerHider 按 PACKAGE_NAME_SUFFIXES 清单原位改写
// APK 中的包名字符串。若 Manifest 新增 ${applicationId} 注入点但后缀未加入清单，
// 隐藏管理器下该组件会失效（如 Shizuku provider 找不到、授权弹窗解析不到）。
// 本 task 在构建早期扫描 Manifest，发现未覆盖的后缀即失败，防止问题流入发布版。
tasks.register("checkManagerHiderSuffixes") {
    group = "verification"
    description = "检查 AndroidManifest 中所有 \${applicationId} 后缀是否都在 ManagerHider 清单中"

    val manifestFile = layout.projectDirectory.file("src/main/AndroidManifest.xml")
    val hiderFile = layout.projectDirectory.file(
        "src/main/java/me/weishu/kernelsu/ui/util/ManagerHider.kt"
    )
    inputs.file(manifestFile)
    inputs.file(hiderFile)

    doLast {
        val manifest = manifestFile.asFile.readText()
        val regex = Regex("""\$\{applicationId\}([\w.]*)""")
        val foundSuffixes = regex.findAll(manifest)
            .map { it.groupValues[1] }
            .toSet()

        // 清单只在 ManagerHider.kt 里维护一份，这里直接解析那份源码。
        // （早先此处手抄过一份副本，等于把"一处清单"变成"两处要同步"，比原来的问题更难维护。）
        val listBody = Regex("""PACKAGE_NAME_SUFFIXES\s*=\s*listOf\(([\s\S]*?)\n\s*\)""")
            .find(hiderFile.asFile.readText())
            ?.groupValues
            ?.get(1)
            ?: throw GradleException(
                "无法从 ${hiderFile.asFile.path} 解析出 PACKAGE_NAME_SUFFIXES = listOf(...)；" +
                    "如果那段代码改了写法，请同步更新本 task 的正则。"
            )
        val expectedSuffixes = Regex("\"([^\"]*)\"")
            .findAll(listBody)
            .map { it.groupValues[1] }
            .toSet()

        val missing = foundSuffixes - expectedSuffixes
        if (missing.isNotEmpty()) {
            throw GradleException(
                """
                |AndroidManifest.xml 中发现未在 ManagerHider.PACKAGE_NAME_SUFFIXES 中登记的 ${'$'}{applicationId} 后缀：
                |  ${missing.joinToString("\n  ")}
                |
                |隐藏管理器（随机包名）时，这些后缀对应的包名字符串不会被改写，
                |导致相关组件在隐藏管理器下失效。
                |
                |修复：把上面每个后缀加进 ManagerHider.kt 的 PACKAGE_NAME_SUFFIXES 清单
                |（那是唯一定义处，本 task 会自动读到新清单，不需要再改这里）。
                """.trimMargin()
            )
        }
        logger.lifecycle(
            "ManagerHider suffix check passed: ${foundSuffixes.size} in manifest, " +
                "${expectedSuffixes.size} registered, all covered."
        )
    }
}

// 构建早期执行，避免编译大半才发现
tasks.named("preBuild") {
    dependsOn("checkManagerHiderSuffixes")
    // 管理器与内核的 versionCode 公式各写一份，这里在构建期做一致性校验
    dependsOn(":checkVersionFormula")
}
