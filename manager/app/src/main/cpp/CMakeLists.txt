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
