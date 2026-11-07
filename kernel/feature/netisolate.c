#include <linux/compiler.h>
#include <linux/cred.h>
#include <linux/errno.h>
#include <linux/in.h>
#include <linux/in6.h>
#include <linux/kernel.h>
#include <linux/mutex.h>
#include <linux/net.h>
#include <linux/printk.h>
#include <linux/rcupdate.h>
#include <linux/sched.h>
#include <linux/slab.h>
#include <linux/socket.h>
#include <linux/string.h>
#include <linux/types.h>
#include <linux/uaccess.h>
#include <net/sock.h>

#include "feature/netisolate.h"
#include "hook/lsm_hook.h"
#include "uapi/netisolate.h"

/*
 * 网络隔离：按 uid 拒绝对外 connect(2)。
 *
 * 挂在 LSM 的 socket_connect 上（SELinux 一定注册了这个钩子，所以 hooks 链里必然有槽位）。
 * 只拦 AF_INET/AF_INET6 的 connect，AF_UNIX、AF_NETLINK 一律放行 —— 拦掉它们会让应用
 * 连本地 Binder/日志都发不出去，崩溃点在使用者手里，很难排查。
 *
 * 两个钩子：socket_connect 管 TCP 与已连接 UDP；socket_sendmsg 补上「不 connect 直接
 * sendto」的数据报（msg_name 非空才算，已连接的 send 走的是 socket 里记的地址）。
 * 与 FinaSU 的差异只剩 strict socket（连 socket_create 都拦）没做 —— 那个会让应用连
 * 本地服务都建不起来，收益远小于误伤。
 *
 * 热点路径：只做一次原子读；真正要查表时才进 RCU 读区，配置更新走「换指针 + synchronize_rcu」。
 */

static struct ksu_netisolate_config __rcu *ni_config;
static DEFINE_MUTEX(ni_config_lock);
static atomic_t ni_active = ATOMIC_INIT(0);
static bool ni_supported;
static bool ni_sendmsg_installed;
static int (*ni_connect_original)(struct socket *sock, struct sockaddr *address, int addrlen);
static int (*ni_sendmsg_original)(struct socket *sock, struct msghdr *msg, int size);

static int ksu_ni_socket_connect(struct socket *sock, struct sockaddr *address, int addrlen);
static int ksu_ni_socket_sendmsg(struct socket *sock, struct msghdr *msg, int size);

static struct ksu_lsm_hook ni_connect_hook =
    KSU_LSM_HOOK_INIT(socket_connect, "selinux_socket_connect", ksu_ni_socket_connect, 0);
static struct ksu_lsm_hook ni_sendmsg_hook =
    KSU_LSM_HOOK_INIT(socket_sendmsg, "selinux_socket_sendmsg", ksu_ni_socket_sendmsg, 0);

static int validate_config(const struct ksu_netisolate_config *cfg)
{
    u32 i, j;

    if (cfg->mode != KSU_NETISOLATE_MODE_DISABLED && cfg->mode != KSU_NETISOLATE_MODE_SELECTED)
        return -EINVAL;

    if (cfg->flags & ~KSU_NETISOLATE_FLAG_VALID_MASK)
        return -EINVAL;

    if (cfg->target_count > KSU_NETISOLATE_MAX_TARGETS)
        return -EINVAL;

    for (i = 0; i < cfg->target_count; i++) {
        /* 只允许隔离普通应用；系统 uid 一动就把手机自己断网了。 */
        if (cfg->target_uids[i] < 10000)
            return -EINVAL;

        for (j = 0; j < i; j++) {
            if (cfg->target_uids[j] == cfg->target_uids[i])
                return -EINVAL;
        }
    }

    return 0;
}

static bool uid_is_target(const struct ksu_netisolate_config *cfg, u32 uid)
{
    u32 i;

    for (i = 0; i < cfg->target_count; i++) {
        if (cfg->target_uids[i] == uid)
            return true;
    }

    return false;
}

/* 127.0.0.0/8 —— 用主机序比较，读起来才像网段。 */
static bool v4_is_loopback(u32 host)
{
    return (host & 0xff000000u) == 0x7f000000u;
}

static bool v4_is_private(u32 host)
{
    return (host & 0xff000000u) == 0x0a000000u || /* 10.0.0.0/8 */
           (host & 0xfff00000u) == 0xac100000u || /* 172.16.0.0/12 */
           (host & 0xffff0000u) == 0xc0a80000u || /* 192.168.0.0/16 */
           (host & 0xffc00000u) == 0x64400000u || /* 100.64.0.0/10，运营商级 NAT */
           (host & 0xffff0000u) == 0xa9fe0000u; /* 169.254.0.0/16，热点/自分配 */
}

static bool v6_is_loopback(const struct in6_addr *address)
{
    u32 i;

    for (i = 0; i < 15; i++) {
        if (address->s6_addr[i])
            return false;
    }

    return address->s6_addr[15] == 1;
}

static bool v6_is_v4mapped(const struct in6_addr *address)
{
    u32 i;

    for (i = 0; i < 10; i++) {
        if (address->s6_addr[i])
            return false;
    }

    return address->s6_addr[10] == 0xff && address->s6_addr[11] == 0xff;
}

static bool netisolate_v4_allowed(const struct ksu_netisolate_config *cfg, __be32 address)
{
    u32 host = ntohl(address);

    if (v4_is_loopback(host))
        return (cfg->flags & KSU_NETISOLATE_ALLOW_LOOPBACK) != 0;

    if (v4_is_private(host))
        return (cfg->flags & KSU_NETISOLATE_ALLOW_PRIVATE) != 0;

    return false;
}

static bool netisolate_v6_allowed(const struct ksu_netisolate_config *cfg, const struct in6_addr *address)
{
    __be32 mapped;

    if (v6_is_loopback(address))
        return (cfg->flags & KSU_NETISOLATE_ALLOW_LOOPBACK) != 0;

    /* ::ffff:a.b.c.d 走 IPv4 的判定，否则同一个地址在两种 socket 上结论不一致。 */
    if (v6_is_v4mapped(address)) {
        memcpy(&mapped, &address->s6_addr[12], sizeof(mapped));
        return netisolate_v4_allowed(cfg, mapped);
    }

    /* fc00::/7 唯一本地地址、fe80::/10 链路本地。 */
    if ((address->s6_addr[0] & 0xfe) == 0xfc || (address->s6_addr[0] == 0xfe && (address->s6_addr[1] & 0xc0) == 0x80))
        return (cfg->flags & KSU_NETISOLATE_ALLOW_PRIVATE) != 0;

    return false;
}

/*
 * 目标是「允许放行吗」。地址长度不够时放行 —— 宁可漏拦，也不要把协议栈里
 * 半截地址的调用打回去。
 */
static bool netisolate_address_allowed(const struct ksu_netisolate_config *cfg, u16 family,
                                       const struct sockaddr *address, int addrlen)
{
    if (family == AF_INET) {
        struct sockaddr_in sin;

        if (addrlen < (int)sizeof(sin))
            return true;

        memcpy(&sin, address, sizeof(sin));
        return netisolate_v4_allowed(cfg, sin.sin_addr.s_addr);
    }

    if (family == AF_INET6) {
        struct sockaddr_in6 sin6;

        if (addrlen < (int)sizeof(sin6))
            return true;

        memcpy(&sin6, address, sizeof(sin6));
        return netisolate_v6_allowed(cfg, &sin6.sin6_addr);
    }

    return true;
}

static int netisolate_call_original(struct socket *sock, struct sockaddr *address, int addrlen)
{
    int (*original)(struct socket *sock, struct sockaddr *address, int addrlen) = READ_ONCE(ni_connect_original);

    return original ? original(sock, address, addrlen) : 0;
}

static int ksu_ni_socket_connect(struct socket *sock, struct sockaddr *address, int addrlen)
{
    struct ksu_netisolate_config *config;
    u16 family;
    u32 uid;
    bool blocked = false;

    if (!atomic_read(&ni_active))
        return netisolate_call_original(sock, address, addrlen);

    if (!sock || !sock->sk || !address || addrlen <= 0)
        return netisolate_call_original(sock, address, addrlen);

    family = READ_ONCE(sock->sk->sk_family);
    if (family != AF_INET && family != AF_INET6)
        return netisolate_call_original(sock, address, addrlen);

    rcu_read_lock();
    config = rcu_dereference(ni_config);
    if (!config || config->mode != KSU_NETISOLATE_MODE_SELECTED) {
        rcu_read_unlock();
        return netisolate_call_original(sock, address, addrlen);
    }

    uid = current_uid().val;
    if (uid_is_target(config, uid) && !netisolate_address_allowed(config, family, address, addrlen))
        blocked = true;
    rcu_read_unlock();

    if (blocked) {
        pr_info_ratelimited("netisolate: blocked connect for uid %u\n", uid);
        return -EACCES;
    }

    return netisolate_call_original(sock, address, addrlen);
}

static int netisolate_call_sendmsg_original(struct socket *sock, struct msghdr *msg, int size)
{
    int (*original)(struct socket *sock, struct msghdr *msg, int size) = READ_ONCE(ni_sendmsg_original);

    return original ? original(sock, msg, size) : 0;
}

/*
 * 只处理「未连接的数据报」：msg_name 为空说明地址在 socket 里（connect 阶段已经拦过），
 * 再拦一次会把已放行的连接也打回去。
 */
static int ksu_ni_socket_sendmsg(struct socket *sock, struct msghdr *msg, int size)
{
    struct ksu_netisolate_config *config;
    u16 family;
    u32 uid;
    bool blocked = false;

    if (!atomic_read(&ni_active))
        return netisolate_call_sendmsg_original(sock, msg, size);

    if (!sock || !sock->sk || !msg || !msg->msg_name || msg->msg_namelen <= 0)
        return netisolate_call_sendmsg_original(sock, msg, size);

    family = READ_ONCE(sock->sk->sk_family);
    if (family != AF_INET && family != AF_INET6)
        return netisolate_call_sendmsg_original(sock, msg, size);

    rcu_read_lock();
    config = rcu_dereference(ni_config);
    if (!config || config->mode != KSU_NETISOLATE_MODE_SELECTED) {
        rcu_read_unlock();
        return netisolate_call_sendmsg_original(sock, msg, size);
    }

    uid = current_uid().val;
    if (uid_is_target(config, uid) &&
        !netisolate_address_allowed(config, family, (const struct sockaddr *)msg->msg_name, msg->msg_namelen))
        blocked = true;
    rcu_read_unlock();

    if (blocked) {
        pr_info_ratelimited("netisolate: blocked sendmsg for uid %u\n", uid);
        return -EACCES;
    }

    return netisolate_call_sendmsg_original(sock, msg, size);
}

int ksu_netisolate_get(struct ksu_netisolate_config __user *arg)
{
    struct ksu_netisolate_config snapshot;
    struct ksu_netisolate_config *config;

    if (!arg)
        return -EINVAL;

    memset(&snapshot, 0, sizeof(snapshot));

    rcu_read_lock();
    config = rcu_dereference(ni_config);
    if (config)
        memcpy(&snapshot, config, sizeof(snapshot));
    rcu_read_unlock();

    snapshot.supported = ni_supported ? 1u : 0u;

    if (copy_to_user(arg, &snapshot, sizeof(snapshot)))
        return -EFAULT;

    return 0;
}

int ksu_netisolate_set(struct ksu_netisolate_config __user *arg)
{
    struct ksu_netisolate_config incoming;
    struct ksu_netisolate_config *config, *old;
    int ret;

    if (!arg)
        return -EINVAL;

    if (copy_from_user(&incoming, arg, sizeof(incoming)))
        return -EFAULT;

    ret = validate_config(&incoming);
    if (ret)
        return ret;

    config = kzalloc(sizeof(*config), GFP_KERNEL);
    if (!config)
        return -ENOMEM;

    memcpy(config, &incoming, sizeof(*config));
    config->supported = ni_supported ? 1u : 0u;

    mutex_lock(&ni_config_lock);
    old = rcu_replace_pointer(ni_config, config, true);
    atomic_set(&ni_active, config->mode == KSU_NETISOLATE_MODE_SELECTED && config->target_count > 0 ? 1 : 0);
    mutex_unlock(&ni_config_lock);

    /* 读侧可能还在用旧配置，等它退出再释放。 */
    synchronize_rcu();
    kfree(old);

    pr_info("netisolate: mode=%u flags=0x%x targets=%u\n", config->mode, config->flags, config->target_count);

    return 0;
}

int ksu_netisolate_init(void)
{
    int ret;

    ni_config = NULL;
    ni_connect_original = NULL;
    ni_sendmsg_original = NULL;
    ni_sendmsg_installed = false;
    ni_supported = false;
    atomic_set(&ni_active, 0);

    ret = ksu_register_lsm_hook(&ni_connect_hook);
    if (ret) {
        pr_warn("netisolate: socket_connect hook unavailable: %d (feature disabled)\n", ret);
        return ret;
    }

    ni_connect_original = (int (*)(struct socket *, struct sockaddr *, int))ni_connect_hook.original;
    ni_supported = true;
    pr_info("netisolate: socket_connect hook installed\n");

    /* sendmsg 只是补 UDP sendto 的缺口：装不上就退回「只拦 connect」，不算不支持。 */
    ret = ksu_register_lsm_hook(&ni_sendmsg_hook);
    if (ret) {
        pr_warn("netisolate: socket_sendmsg hook unavailable: %d (connect-only)\n", ret);
    } else {
        ni_sendmsg_original = (int (*)(struct socket *, struct msghdr *, int))ni_sendmsg_hook.original;
        ni_sendmsg_installed = true;
        pr_info("netisolate: socket_sendmsg hook installed\n");
    }

    return 0;
}

void ksu_netisolate_exit(void)
{
    struct ksu_netisolate_config *old;

    atomic_set(&ni_active, 0);

    if (ni_sendmsg_installed)
        ksu_unregister_lsm_hook(&ni_sendmsg_hook);

    if (ni_supported)
        ksu_unregister_lsm_hook(&ni_connect_hook);

    ni_sendmsg_installed = false;
    ni_sendmsg_original = NULL;
    ni_supported = false;
    ni_connect_original = NULL;

    mutex_lock(&ni_config_lock);
    old = rcu_replace_pointer(ni_config, NULL, true);
    mutex_unlock(&ni_config_lock);

    synchronize_rcu();
    kfree(old);
}
