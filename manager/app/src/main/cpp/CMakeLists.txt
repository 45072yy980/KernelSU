# For more information about using CMake with Android Studio, read the
# documentation: https://d.android.com/studio/projects/add-native-code.html

cmake_minimum_required(VERSION 3.18.1)

project("kernelsu")

find_package(cxx REQUIRED CONFIG)
link_libraries(cxx::cxx)

add_library(kernelsu SHARED jni.cc ksu.cc)
target_include_directories(kernelsu PRIVATE .)
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
# Shizuku 内置服务所需 native 库
#
# librish.so — Shizuku server 执行 shell 命令的核心交互库（PTY 终端、
#   进程托管）。由 app_process 启动的 rikka.shizuku.server.ShizukuService
#   通过 System.loadLibrary("rish") 加载，是 Shizuku 能够执行命令的前提。
#   缺少此库会导致 server 启动后核心功能不可用（UnsatisfiedLinkError）。
#
# fpdrop — Shizuku 分权控制的 shell 级降权可执行文件（setuid/setgid +
#   清空 capability）。编译产物拷贝到 src/main/assets/fpdrop，由 manager
#   释放到 /data/local/tmp/fpdrop，server 降权执行时以
#   `/data/local/tmp/fpdrop 2000 2000 -- <cmd...>` 调用。
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

add_executable(fpdrop fpdrop/fpdrop.c)
target_compile_options(fpdrop PRIVATE -ffunction-sections -fdata-sections -fno-unwind-tables)
target_link_libraries(fpdrop ${log-lib})
target_link_options(fpdrop PRIVATE "-Wl,--build-id=none" "-Wl,-s,-x,--gc-sections")
add_custom_command(TARGET fpdrop POST_BUILD
        COMMAND ${CMAKE_COMMAND} -E make_directory "${CMAKE_CURRENT_SOURCE_DIR}/../assets"
        COMMAND ${CMAKE_COMMAND} -E copy "$<TARGET_FILE:fpdrop>" "${CMAKE_CURRENT_SOURCE_DIR}/../assets/fpdrop")
