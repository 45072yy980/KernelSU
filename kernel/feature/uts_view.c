#include <linux/compiler.h>
#include <linux/cred.h>
#include <linux/errno.h>
#include <linux/kernel.h>
#include <linux/mutex.h>
#include <linux/printk.h>
#include <linux/rcupdate.h>
#include <linux/slab.h>
#include <linux/ptrace.h>
#include <linux/sched.h>
#include <linux/string.h>
#include <linux/uaccess.h>
#include <linux/utsname.h>
#include <asm/unistd.h>

#include "arch.h"
#include "feature/uts_view.h"
#include "hook/syscall_hook.h"

/*
 * UTS 视图：把 uname(2) 的返回值按 uid 换成用户设定的内容。
 *
 * 实现方式是直接替换 syscall 表的 __NR_uname 表项，而不是走 tracepoint 分发器 ——
 * 分发器只对打过标记的进程生效，而伪装内核版本恰恰要覆盖所有普通应用。
 *
 * 与 FinaSU 的差异：它改的是 uts_namespace，连 /proc/sys/kernel 下的节点一起伪装；这里只影响
 * uname(2)，好处是不动 namespace、没有崩溃风险，代价是直接读 /proc 仍能看到真值。
 */

static struct ksu_uts_config __rcu *uts_config;
static DEFINE_MUTEX(uts_config_lock);
static syscall_fn_t uname_original;

static bool field_terminated(const char *field)
{
    return memchr(field, '\0', KSU_UTS_NAME_LEN) != NULL;
}

static int validate_config(const struct ksu_uts_config *cfg)
{
    u32 i, j;

    if (cfg->mode != KSU_UTS_MODE_DISABLED && cfg->mode != KSU_UTS_MODE_GLOBAL && cfg->mode != KSU_UTS_MODE_SELECTED)
        return -EINVAL;

    if (cfg->field_mask & ~KSU_UTS_FIELD_VALID_MASK)
        return -EINVAL;

    if (cfg->target_count > KSU_UTS_MAX_TARGETS)
        return -EINVAL;

    for (i = 0; i < cfg->target_count; i++) {
        /* 只允许给普通应用（uid >= 10000）设视图，系统 uid 一动就出事。 */
        if (cfg->target_uids[i] < 10000)
            return -EINVAL;
        for (j = 0; j < i; j++) {
            if (cfg->target_uids[j] == cfg->target_uids[i])
                return -EINVAL;
        }
    }

    if ((cfg->field_mask & KSU_UTS_FIELD_SYSNAME) && !field_terminated(cfg->sysname))
        return -EINVAL;
    if ((cfg->field_mask & KSU_UTS_FIELD_NODENAME) && !field_terminated(cfg->nodename))
        return -EINVAL;
    if ((cfg->field_mask & KSU_UTS_FIELD_RELEASE) && !field_terminated(cfg->release))
        return -EINVAL;
    if ((cfg->field_mask & KSU_UTS_FIELD_VERSION) && !field_terminated(cfg->version))
        return -EINVAL;
    if ((cfg->field_mask & KSU_UTS_FIELD_MACHINE) && !field_terminated(cfg->machine))
        return -EINVAL;
    if ((cfg->field_mask & KSU_UTS_FIELD_DOMAINNAME) && !field_terminated(cfg->domainname))
        return -EINVAL;

    return 0;
}

static bool uid_is_target(const struct ksu_uts_config *cfg, u32 uid)
{
    u32 i;

    for (i = 0; i < cfg->target_count; i++) {
        if (cfg->target_uids[i] == uid)
            return true;
    }
    return false;
}

int ksu_uts_view_get(struct ksu_uts_config __user *arg)
{
    struct ksu_uts_config snapshot;
    struct ksu_uts_config *config;

    if (!arg)
        return -EINVAL;

    memset(&snapshot, 0, sizeof(snapshot));

    rcu_read_lock();
    config = rcu_dereference(uts_config);
    if (config)
        memcpy(&snapshot, config, sizeof(snapshot));
    rcu_read_unlock();

    if (copy_to_user(arg, &snapshot, sizeof(snapshot)))
        return -EFAULT;

    return 0;
}

int ksu_uts_view_set(struct ksu_uts_config __user *arg)
{
    struct ksu_uts_config incoming;
    struct ksu_uts_config *config, *old;
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

    mutex_lock(&uts_config_lock);
    old = rcu_replace_pointer(uts_config, config, true);
    mutex_unlock(&uts_config_lock);

    synchronize_rcu();
    kfree(old);

    pr_info("uts_view: mode=%u mask=0x%x targets=%u\n", incoming.mode, incoming.field_mask, incoming.target_count);

    return 0;
}

/* 把配置里置位的字段写进 utsname 副本。 */
static void apply_fields(struct new_utsname *patched, const struct ksu_uts_config *cfg)
{
    if (cfg->field_mask & KSU_UTS_FIELD_SYSNAME)
        strscpy(patched->sysname, cfg->sysname, sizeof(patched->sysname));
    if (cfg->field_mask & KSU_UTS_FIELD_NODENAME)
        strscpy(patched->nodename, cfg->nodename, sizeof(patched->nodename));
    if (cfg->field_mask & KSU_UTS_FIELD_RELEASE)
        strscpy(patched->release, cfg->release, sizeof(patched->release));
    if (cfg->field_mask & KSU_UTS_FIELD_VERSION)
        strscpy(patched->version, cfg->version, sizeof(patched->version));
    if (cfg->field_mask & KSU_UTS_FIELD_MACHINE)
        strscpy(patched->machine, cfg->machine, sizeof(patched->machine));
    if (cfg->field_mask & KSU_UTS_FIELD_DOMAINNAME)
        strscpy(patched->domainname, cfg->domainname, sizeof(patched->domainname));
}

static long ksu_uts_uname(const struct pt_regs *regs)
{
    struct ksu_uts_config snapshot;
    struct ksu_uts_config *config;
    struct new_utsname patched;
    struct new_utsname __user *name_user;
    u32 mode, field_mask;
    bool targeted;
    long ret;

    if (unlikely(!uname_original))
        return -ENOSYS;

    ret = uname_original(regs);
    if (ret)
        return ret;

    name_user = (struct new_utsname __user *)PT_REGS_PARM1(regs);
    if (!name_user)
        return 0;

    rcu_read_lock();
    config = rcu_dereference(uts_config);
    if (!config) {
        rcu_read_unlock();
        return 0;
    }
    snapshot = *config;
    mode = snapshot.mode;
    field_mask = snapshot.field_mask;
    if (mode == KSU_UTS_MODE_SELECTED)
        targeted = uid_is_target(config, current_uid().val);
    else
        targeted = true;
    rcu_read_unlock();

    if (mode == KSU_UTS_MODE_DISABLED || field_mask == 0 || !targeted)
        return 0;

    /* 先把 syscall 已经写好的结果取回来，只改配置里置位的字段。 */
    if (copy_from_user(&patched, name_user, sizeof(patched)))
        return 0;

    apply_fields(&patched, &snapshot);

    if (copy_to_user(name_user, &patched, sizeof(patched)))
        return -EFAULT;

    return 0;
}

int ksu_uts_view_init(void)
{
    uts_config = NULL;
    uname_original = NULL;
    ksu_syscall_table_hook(__NR_uname, ksu_uts_uname, &uname_original);
    pr_info("uts_view: uname hook installed\n");
    return 0;
}

void ksu_uts_view_exit(void)
{
    struct ksu_uts_config *old;

    ksu_syscall_table_unhook(__NR_uname);
    uname_original = NULL;

    mutex_lock(&uts_config_lock);
    old = rcu_replace_pointer(uts_config, NULL, true);
    mutex_unlock(&uts_config_lock);

    synchronize_rcu();
    kfree(old);
}
