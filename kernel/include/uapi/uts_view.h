#ifndef __KSU_UAPI_UTS_VIEW_H
#define __KSU_UAPI_UTS_VIEW_H

#include <linux/types.h>

/* new_utsname 的每个字段是 __NEW_UTS_LEN + 1 = 65 字节。 */
#define KSU_UTS_NAME_LEN 65
#define KSU_UTS_MAX_TARGETS 64

#define KSU_UTS_MODE_DISABLED 0
/* 对所有进程生效 */
#define KSU_UTS_MODE_GLOBAL 1
/* 只对 target_uids 里的应用生效 */
#define KSU_UTS_MODE_SELECTED 2

#define KSU_UTS_FIELD_SYSNAME (1u << 0)
#define KSU_UTS_FIELD_NODENAME (1u << 1)
#define KSU_UTS_FIELD_RELEASE (1u << 2)
#define KSU_UTS_FIELD_VERSION (1u << 3)
#define KSU_UTS_FIELD_MACHINE (1u << 4)
#define KSU_UTS_FIELD_DOMAINNAME (1u << 5)
#define KSU_UTS_FIELD_VALID_MASK                                                                                       \
    (KSU_UTS_FIELD_SYSNAME | KSU_UTS_FIELD_NODENAME | KSU_UTS_FIELD_RELEASE | KSU_UTS_FIELD_VERSION |                  \
     KSU_UTS_FIELD_MACHINE | KSU_UTS_FIELD_DOMAINNAME)

/*
 * 一份 UTS 视图配置。field_mask 决定哪些字段生效，未置位的字段保持内核原值 ——
 * 这样用户只改 release 时不会把 sysname 也一起写坏。
 */
struct ksu_uts_config {
    __u32 mode;
    __u32 field_mask;
    __u32 target_count;
    __u32 reserved;
    __u32 target_uids[KSU_UTS_MAX_TARGETS];
    char sysname[KSU_UTS_NAME_LEN];
    char nodename[KSU_UTS_NAME_LEN];
    char release[KSU_UTS_NAME_LEN];
    char version[KSU_UTS_NAME_LEN];
    char machine[KSU_UTS_NAME_LEN];
    char domainname[KSU_UTS_NAME_LEN];
};

#endif /* __KSU_UAPI_UTS_VIEW_H */
