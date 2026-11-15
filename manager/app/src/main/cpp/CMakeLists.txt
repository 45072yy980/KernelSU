# For more information about using CMake with Android Studio, read the
# documentation: https://d.android.com/studio/projects/add-native-code.html

cmake_minimum_required(VERSION 3.18.1)

project("kernelsu")

find_package(cxx REQUIRED CONFIG)
link_libraries(cxx::cxx)

add_library(kernelsu SHARED jni.cc ksu.cc synopt/synopt_spawn.cc)
# uapi 头只保留内核那一份（kernel/include/uapi/），仓库里不再存副本 ——
# 副本只能靠人肉同步，63abf487 之前就漏过 feature id 5/6。
# 从 cpp/ 往上 5 层是仓库根：cpp → main → src → app → manager → <repo>。
target_include_directories(kernelsu PRIVATE
        .
        "${CMAKE_CURRENT_SOURCE_DIR}/../../../../../kernel/include")
find_library(log-lib log)
target_link_libraries(kernelsu ${log-lib})

add_library(adbroot SHARED adbroot.cc)
target_link_libraries(adbroot ${log-lib})

# ---------------------------------------------------------------------------
# GhostLock exploit (CVE-2026-43499)
#
# A self-contained arm64 privilege-escalation binary bundled from
# https://github.com/45072yy980/ghostlock-app. It is built here as a position
# independent executable named libghostlock.so so Android packages it under
# lib/<abi>/ and the Manager can exec it from nativeLibraryDir, the same way
# libksud.so is handled. Only arm64-v8a (aarch64) is supported, matching the
# upstream project: it carries offsets for arm64 GKI kernels only.
# ---------------------------------------------------------------------------
if(ANDROID_ABI STREQUAL "arm64-v8a")
    file(GLOB_RECURSE GHOSTLOCK_CORE_SOURCES CONFIGURE_DEPENDS
            "${CMAKE_CURRENT_SOURCE_DIR}/ghostlock/core/*.c")

    add_executable(ghostlock ${GHOSTLOCK_CORE_SOURCES})

    target_compile_definitions(ghostlock PRIVATE
            TARGET_CONFIG_H="target.h"
            __ARM=1
            _GNU_SOURCE)

    target_include_directories(ghostlock PRIVATE
            "${CMAKE_CURRENT_SOURCE_DIR}/ghostlock/core"
            "${CMAKE_CURRENT_SOURCE_DIR}/ghostlock/kernels")

    target_compile_options(ghostlock PRIVATE
            $<$<COMPILE_LANGUAGE:C>:-std=gnu11;-O2;-Wall;-Wno-unused-parameter;-Wno-sign-compare;-Wno-unused-function;-Wno-unused-variable;-Wno-implicit-function-declaration>
            -fPIE)

    target_link_options(ghostlock PRIVATE -fPIE -pie)

    set_target_properties(ghostlock PROPERTIES
            OUTPUT_NAME "ghostlock"
            SUFFIX ".so"
            PREFIX "lib")
endif()

# ---------------------------------------------------------------------------
# DirtyFrag exploit (CVE-2026-43284)
#
# 和 ghostlock 一样是一个自带全部数据的可执行文件：8 个 KMI 的内核模块和
# splicehelper 都用 .incbin 内嵌在 exp.c 里，Android 把它当 libdirtyfrag.so
# 打进 lib/<abi>/，运行时由 Manager 从 nativeLibraryDir exec。
#
# bootstrap 单独编一个（模块加载后由内核 call_usermodehelper 执行），
# 它现在从 argv[0] 推导暂存目录，不再写死 df.root 的包名。
# ---------------------------------------------------------------------------
if(ANDROID_ABI STREQUAL "arm64-v8a")
    enable_language(ASM)

    set(DIRTYFRAG_DIR "${CMAKE_CURRENT_SOURCE_DIR}/dirtyfrag")
    file(GLOB DIRTYFRAG_KO_SOURCES "${DIRTYFRAG_DIR}/ko/*.ko")

    # .incbin 的路径是按编译器工作目录解析的，所以把 ko/ 和 splicehelper
    # 都摆到构建目录里，exp.c 里那些相对路径就能直接找到。
    add_custom_command(
            OUTPUT "${CMAKE_CURRENT_BINARY_DIR}/splicehelper"
            COMMAND ${CMAKE_COMMAND} -E copy_directory
                    "${DIRTYFRAG_DIR}/ko" "${CMAKE_CURRENT_BINARY_DIR}/ko"
            COMMAND ${CMAKE_C_COMPILER} --target=${CMAKE_C_COMPILER_TARGET}
                    --sysroot=${CMAKE_SYSROOT}
                    "${DIRTYFRAG_DIR}/splicehelper.c"
                    -o "${CMAKE_CURRENT_BINARY_DIR}/splicehelper"
                    -nodefaultlibs -nostartfiles -ffreestanding -static
            DEPENDS "${DIRTYFRAG_DIR}/splicehelper.c" ${DIRTYFRAG_KO_SOURCES}
            COMMENT "Building dirtyfrag blobs")
    add_custom_target(dirtyfrag_blobs DEPENDS "${CMAKE_CURRENT_BINARY_DIR}/splicehelper")

    add_executable(dirtyfrag
            "${DIRTYFRAG_DIR}/exp.c"
            "${DIRTYFRAG_DIR}/libcxx.S"
            "${DIRTYFRAG_DIR}/elf_parser.c")
    add_dependencies(dirtyfrag dirtyfrag_blobs)
    target_compile_definitions(dirtyfrag PRIVATE _GNU_SOURCE)
    target_compile_options(dirtyfrag PRIVATE
            $<$<COMPILE_LANGUAGE:C>:-std=gnu11;-O2;-Wall;-Wextra;-Wno-unused-parameter;-Wno-sign-compare;-fno-stack-protector;-fomit-frame-pointer>
            -I${DIRTYFRAG_DIR})
    target_link_options(dirtyfrag PRIVATE -fPIE -pie)
    set_target_properties(dirtyfrag PROPERTIES
            OUTPUT_NAME "dirtyfrag"
            SUFFIX ".so"
            PREFIX "lib")

    add_executable(dirtyfrag_bootstrap "${DIRTYFRAG_DIR}/bootstrap.c")
    target_compile_definitions(dirtyfrag_bootstrap PRIVATE _GNU_SOURCE)
    target_compile_options(dirtyfrag_bootstrap PRIVATE -std=gnu11 -O2 -Wall -Wno-unused-parameter)
    target_link_options(dirtyfrag_bootstrap PRIVATE -fPIE -pie)
    set_target_properties(dirtyfrag_bootstrap PROPERTIES
            OUTPUT_NAME "dirtyfrag_bootstrap"
            SUFFIX ".so"
            PREFIX "lib")
endif()

# ---------------------------------------------------------------------------
# SynOpt — CVE-2026-98432 (ireq_opt use-after-free)
#
# 和另外两条链不同，这条链**没有 root 可用**——它就是拿来拿 root 的。所以它
# 不能像 ghostlock / dirtyfrag 那样从 nativeLibraryDir exec 后自己搞定一切：
# 它需要一个 tun 描述符（VpnService 建的）才能在没有 CAP_NET_RAW 的情况下把
# 伪造报文投进内核。因此编成 libsynopt.so，由 Manager 用 posix_spawn 起，
# 并把 tun fd 复制到 fd 3 传进去。
#
# 只编 arm64-v8a：偏移表是按 6.6.118-f72a02f47f4b 单一构建导出的。
# ---------------------------------------------------------------------------
if(ANDROID_ABI STREQUAL "arm64-v8a")
    add_executable(synopt "${CMAKE_CURRENT_SOURCE_DIR}/synopt/exp.c")
    target_compile_definitions(synopt PRIVATE _GNU_SOURCE)
    target_compile_options(synopt PRIVATE
            $<$<COMPILE_LANGUAGE:C>:-std=gnu11;-O2;-Wall;-Wextra;-Wno-unused-parameter;-Wno-sign-compare>)
    target_include_directories(synopt PRIVATE "${CMAKE_CURRENT_SOURCE_DIR}/synopt")
    target_link_options(synopt PRIVATE -fPIE -pie)
    set_target_properties(synopt PROPERTIES
            OUTPUT_NAME "synopt"
            SUFFIX ".so"
            PREFIX "lib")
endif()

# ---------------------------------------------------------------------------
# Shizuku 内置服务所需 native 库
#
# librish.so — Shizuku server 执行 shell 命令的核心交互库（PTY 终端、
#   进程托管）。由 app_process 启动的 rikka.shizuku.server.ShizukuService
#   通过 System.loadLibrary("rish") 加载，是 Shizuku 能够执行命令的前提。
#   缺少此库会导致 server 启动后核心功能不可用（UnsatisfiedLinkError）。
#
# fpdrop — Shizuku 分权控制的 shell 级降权可执行文件（setuid/setgid +
#   清空 capability）。产物写进 Gradle 的生成 assets 目录
#   （KSU_FPDROP_ASSET_DIR，由 app/build.gradle.kts 传入），**不再落进源码树**，
#   以免每次构建都把工作区弄脏。manager 把它释放到 /data/local/tmp/fpdrop，
#   server 降权执行时以 `/data/local/tmp/fpdrop 2000 2000 -- <cmd...>` 调用。
# ---------------------------------------------------------------------------

add_library(rish SHARED
        rish/main.cpp
        rish/pts.cpp
        rish/rikka_rish_RishTerminal.cpp
        rish/rikka_rish_RishHost.cpp)
target_compile_options(rish PRIVATE -fno-exceptions -fno-rtti -ffunction-sections -fdata-sections)
target_link_libraries(rish ${log-lib})
if (NOT CMAKE_BUILD_TYPE STREQUAL "Debug")
    add_custom_command(TARGET rish POST_BUILD
            COMMAND ${CMAKE_STRIP} --remove-section=.comment "$<TARGET_FILE:rish>")
endif ()

# fpdrop 是给真机执行的降权工具，只编 arm64-v8a：assets 里的 fpdrop 是**单个**文件，
# 多 ABI 同时构建会各自 POST_BUILD 覆盖它，最后谁完成由调度决定 —— 有可能把
# riscv64 / x86_64 的 ELF 塞进 arm64 的安装包里，运行时 exec 直接失败。
if (ANDROID_ABI STREQUAL "arm64-v8a")
    # 由 Gradle 传入（见 app/build.gradle.kts 的 -DKSU_FPDROP_ASSET_DIR），
    # 兜底值只是让单独跑 cmake 的人也能构建。
    if (NOT DEFINED KSU_FPDROP_ASSET_DIR)
        set(KSU_FPDROP_ASSET_DIR "${CMAKE_CURRENT_SOURCE_DIR}/../../../build/generated/fpdrop-assets")
    endif()

    add_executable(fpdrop fpdrop/fpdrop.c)
    target_compile_options(fpdrop PRIVATE -ffunction-sections -fdata-sections -fno-unwind-tables)
    target_link_libraries(fpdrop ${log-lib})
    target_link_options(fpdrop PRIVATE "-Wl,--build-id=none" "-Wl,-s,-x,--gc-sections")
    add_custom_command(TARGET fpdrop POST_BUILD
            COMMAND ${CMAKE_COMMAND} -E make_directory "${KSU_FPDROP_ASSET_DIR}"
            COMMAND ${CMAKE_COMMAND} -E copy "$<TARGET_FILE:fpdrop>" "${KSU_FPDROP_ASSET_DIR}/fpdrop")
endif ()
