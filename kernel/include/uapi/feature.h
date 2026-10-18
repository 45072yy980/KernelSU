#ifndef __KSU_UAPI_FEATURE_H
#define __KSU_UAPI_FEATURE_H

enum ksu_feature_id {
    KSU_FEATURE_SU_COMPAT = 0,
    KSU_FEATURE_KERNEL_UMOUNT = 1,
    KSU_FEATURE_SULOG = 2,
    KSU_FEATURE_ADB_ROOT = 3,
    KSU_FEATURE_SELINUX_HIDE = 4,
    // 5 was webview_zygote_umount (v3.3.0), keep it unused so stale configs don't apply
    KSU_FEATURE_SHELL_SU_ADB_ONLY = 6,
    // DikSU: 系统分区写保护（7 = 总开关，8 = 运行时拦截，默认关闭）
    KSU_FEATURE_PARTITION_GUARD = 7,
    KSU_FEATURE_PARTITION_GUARD_RUNTIME = 8,

    KSU_FEATURE_MAX
};

#endif
