# For more information about using CMake with Android Studio, read the
# documentation: https://d.android.com/studio/projects/add-native-code.html

cmake_minimum_required(VERSION 3.18.1)

project("kernelsu")

find_package(cxx REQUIRED CONFIG)
link_libraries(cxx::cxx)

add_library(kernelsu SHARED jni.cc ksu.cc)
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
    set(DIRTYFRAG_BUILD "${CMAKE_CURRENT_BINARY_DIR}/dirtyfrag")
    set(DIRTYFRAG_SPLICEHELPER "${DIRTYFRAG_BUILD}/splicehelper")

    # splicehelper 是个独立的静态小二进制（不能带 libc），exp.c 用 .incbin 嵌它。
    add_custom_command(
            OUTPUT "${DIRTYFRAG_SPLICEHELPER}"
            COMMAND ${CMAKE_COMMAND} -E make_directory "${DIRTYFRAG_BUILD}"
            COMMAND ${CMAKE_C_COMPILER} --target=${CMAKE_C_COMPILER_TARGET}
                    --sysroot=${CMAKE_SYSROOT}
                    "${DIRTYFRAG_DIR}/splicehelper.c" -o "${DIRTYFRAG_SPLICEHELPER}"
                    -nodefaultlibs -nostartfiles -ffreestanding -static
            DEPENDS "${DIRTYFRAG_DIR}/splicehelper.c"
            COMMENT "Building dirtyfrag splicehelper")
    add_custom_target(dirtyfrag_splicehelper DEPENDS "${DIRTYFRAG_SPLICEHELPER}")

    add_executable(dirtyfrag
            "${DIRTYFRAG_DIR}/exp.c"
            "${DIRTYFRAG_DIR}/libcxx.S"
            "${DIRTYFRAG_DIR}/elf_parser.c")
    add_dependencies(dirtyfrag dirtyfrag_splicehelper)
    target_include_directories(dirtyfrag PRIVATE "${DIRTYFRAG_DIR}" "${DIRTYFRAG_BUILD}")
    target_compile_definitions(dirtyfrag PRIVATE _GNU_SOURCE)
    target_compile_options(dirtyfrag PRIVATE
            $<$<COMPILE_LANGUAGE:C>:-std=gnu11;-O2;-Wall;-Wextra;-Wno-unused-parameter;-Wno-sign-compare;-fno-stack-protector;-fomit-frame-pointer>
            -I${DIRTYFRAG_DIR}
            -I${DIRTYFRAG_BUILD}
            # .incbin 走的是集成汇编器的搜索路径，普通 -I 对它无效。
            -Wa,-I,${DIRTYFRAG_DIR}
            -Wa,-I,${DIRTYFRAG_BUILD})
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
