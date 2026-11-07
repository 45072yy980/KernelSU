#ifndef __KSU_UAPI_NETISOLATE_H
#define __KSU_UAPI_NETISOLATE_H

#include <linux/types.h>

#define KSU_NETISOLATE_MAX_TARGETS 128

#define KSU_NETISOLATE_MODE_DISABLED 0
/* 只对 target_uids 里的应用生效：它们的对外 connect 会被拒绝。 */
#define KSU_NETISOLATE_MODE_SELECTED 1

/* 放行回环地址（127.0.0.0/8、::1）。 */
#define KSU_NETISOLATE_ALLOW_LOOPBACK (1u << 0)
/* 放行私网/链路本地（10/8、172.16/12、192.168/16、100.64/10、169.254/16、fc00::/7、fe80::/10）。 */
#define KSU_NETISOLATE_ALLOW_PRIVATE (1u << 1)
#define KSU_NETISOLATE_FLAG_VALID_MASK (KSU_NETISOLATE_ALLOW_LOOPBACK | KSU_NETISOLATE_ALLOW_PRIVATE)

/*
 * 网络隔离配置。
 *
 * 主开关是 mode：关闭时钩子只做一次原子读就放行，跟没装一样。目标 uid 只允许普通应用
 * （>= 10000），系统进程永远不在名单里 —— 把 system_server 断网等于把手机变砖。
 */
struct ksu_netisolate_config {
    __u32 mode;
    __u32 flags;
    __u32 target_count;
    /* 输出字段：1 = 内核的 socket_connect 钩子已装上，0 = 这个内核不支持（写了也不生效）。 */
    __u32 supported;
    __u32 target_uids[KSU_NETISOLATE_MAX_TARGETS];
};

#endif /* __KSU_UAPI_NETISOLATE_H */
