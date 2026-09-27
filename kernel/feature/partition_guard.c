#include <linux/cred.h>
#include <linux/dcache.h>
#include <linux/fs.h>
#include <linux/namei.h>
#include <linux/printk.h>
#include <linux/sched.h>
#include <linux/stat.h>
#include <linux/string.h>
#include <linux/types.h>
#include <linux/uaccess.h>

#include "arch.h" // IWYU pragma: keep
#include "feature/partition_guard.h"
#include "hook/syscall_hook.h"
#include "klog.h" // IWYU pragma: keep
#include "policy/feature.h"
#include "util.h"
#include "uapi/feature.h"

// Runtime system-partition write guard.
//
// The syscall dispatcher only routes *marked* processes here, so this hook sees
// exactly the set the user cares about: apps already granted root plus the
// elevated (ksu-domain) root processes. Nothing else in the system is touched.

static bool ksu_partition_guard_enabled = false;

bool ksu_partition_guard_is_enabled(void)
{
    return ksu_partition_guard_enabled;
}

void ksu_partition_guard_set(bool enabled)
{
    ksu_partition_guard_enabled = enabled;
    pr_info("partition_guard: %s\n", enabled ? "enabled" : "disabled");
}

static int partition_guard_feature_get(u64 *value)
{
    *value = ksu_partition_guard_enabled ? 1 : 0;
    return 0;
}

static int partition_guard_feature_set(u64 value)
{
    ksu_partition_guard_set(value != 0);
    return 0;
}

static const struct ksu_feature_handler partition_guard_handler = {
    .feature_id = KSU_FEATURE_PARTITION_GUARD,
    .name = "partition_guard",
    .get_handler = partition_guard_feature_get,
    .set_handler = partition_guard_feature_set,
};

// Paths we refuse to open for writing. These are the raw block devices a
// careless (or hostile) root tool would `dd` to, around which the whole
// jailbreak-mode story of "nothing on disk changes" is built.
//
// Note we deliberately do NOT match plain files under /data or /sys; only the
// block-device tree counts as "a real partition".
static bool is_protected_block_path(const char *path)
{
    // /dev/block/...  (by-name, by-num, sda*, mmcblk*, loop*, dm-*, etc.)
    if (strncmp(path, "/dev/block", 10) != 0)
        return false;
    // /dev/block itself and bare directories are not devices.
    if (path[10] == '\0')
        return false;
    return true;
}

// openat(dfd, path, flags, mode): path = PARM2, flags = PARM3.
long __nocfi ksu_hook_openat(int orig_nr, const struct pt_regs *regs)
{
    long ret = ksu_syscall_table[orig_nr](regs);
    const char __user *path_user;
    int flags;
    char buf[160];
    bool write_intent;

    if (likely(!ksu_partition_guard_enabled))
        return ret;

    // Only inspect opens that actually succeeded.
    if (ret < 0)
        return ret;

    path_user = (const char __user *)PT_REGS_PARM2(regs);
    flags = (int)PT_REGS_PARM3(regs);

    // Read-only opens are harmless (tools need to read partition tables).
    write_intent = (flags & (O_WRONLY | O_RDWR | O_APPEND | O_CREAT | O_TRUNC)) != 0;
    if (!write_intent)
        return ret;

    if (!path_user)
        return ret;

    // Best-effort: if we cannot read the path we let the open stand rather than
    // break a legitimate caller on a fault.
    if (strncpy_from_user(buf, path_user, sizeof(buf) - 1) < 0)
        return ret;
    buf[sizeof(buf) - 1] = '\0';

    if (is_protected_block_path(buf)) {
        ksu_close_fd((int)ret);
        pr_info("partition_guard: blocked write open of %s (flags=0x%x)\n", buf, flags);
        return -EACCES;
    }

    return ret;
}

void __init ksu_partition_guard_init(void)
{
    int ret = ksu_register_feature_handler(&partition_guard_handler);
    if (ret) {
        pr_err("partition_guard: register feature failed: %d\n", ret);
    }
    pr_info("partition_guard: init done (default off)\n");
}

void __exit ksu_partition_guard_exit(void)
{
    ksu_unregister_feature_handler(KSU_FEATURE_PARTITION_GUARD);
    pr_info("partition_guard: exit\n");
}
