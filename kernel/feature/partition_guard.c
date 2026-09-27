#include <linux/cred.h>
#include <linux/dcache.h>
#include <linux/fdtable.h>
#include <linux/file.h>
#include <linux/fs.h>
#include <linux/namei.h>
#include <linux/mount.h>
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

/*
 * Runtime system-partition write guard.
 *
 * The syscall dispatcher only routes *marked* processes here, so every hook
 * below sees exactly the set the user cares about: apps already granted root
 * plus the elevated (ksu-domain) root processes. Nothing else in the system is
 * ever touched, and when the guard is off each hook is a single branch.
 *
 * Four write paths are covered:
 *   openat / openat2 - opening a raw block device for writing
 *   mount            - remounting a filesystem read-write
 *   write / pwrite64 - writing through an already-open block-device fd
 */

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

/*
 * A path is "protected" when it names a raw block device. We only match the
 * block-device tree; files under /data, /sys and the like are left alone.
 */
static bool is_protected_block_path(const char *path)
{
    if (strncmp(path, "/dev/block", 10) != 0)
        return false;
    if (path[10] == '\0')
        return false;
    return true;
}

/* Write-intent check shared by openat and openat2. */
static bool flags_have_write_intent(unsigned long flags)
{
    return (flags & (O_WRONLY | O_RDWR | O_APPEND | O_CREAT | O_TRUNC)) != 0;
}

/*
 * Copy a NUL-terminated path from userspace into @buf. Returns false when the
 * pointer is bad or the copy faults, in which case the caller lets the syscall
 * stand rather than break a legitimate caller.
 */
static bool copy_path_from_user(const char __user *path_user, char *buf, size_t size)
{
    if (!path_user)
        return false;
    if (strncpy_from_user(buf, path_user, size - 1) < 0)
        return false;
    buf[size - 1] = '\0';
    return true;
}

/* openat(dfd, path, flags, mode): path = PARM2, flags = PARM3. */
long __nocfi ksu_hook_openat(int orig_nr, const struct pt_regs *regs)
{
    long ret = ksu_syscall_table[orig_nr](regs);
    char buf[160];

    if (likely(!ksu_partition_guard_enabled))
        return ret;
    if (ret < 0)
        return ret;
    if (!flags_have_write_intent((unsigned long)PT_REGS_PARM3(regs)))
        return ret;
    if (!copy_path_from_user((const char __user *)PT_REGS_PARM2(regs), buf, sizeof(buf)))
        return ret;
    if (is_protected_block_path(buf)) {
        ksu_close_fd((int)ret);
        pr_info("partition_guard: blocked write open of %s\n", buf);
        return -EACCES;
    }
    return ret;
}

#ifndef __NR_openat2
#define __NR_openat2 437
#endif

struct ksu_open_how {
    u64 flags;
    u64 mode;
    u64 resolve;
};

/* openat2(dfd, path, how, size): path = PARM2, how = PARM3. */
long __nocfi ksu_hook_openat2(int orig_nr, const struct pt_regs *regs)
{
    long ret = ksu_syscall_table[orig_nr](regs);
    char buf[160];
    struct ksu_open_how how;

    if (likely(!ksu_partition_guard_enabled))
        return ret;
    if (ret < 0)
        return ret;
    /* Read the open_how the caller passed; a short copy means a stale/small
     * struct, in which case we only trust what we got. */
    if (copy_from_user(&how, (const void __user *)PT_REGS_PARM3(regs), sizeof(how)) != 0)
        return ret;
    if (!flags_have_write_intent(how.flags))
        return ret;
    if (!copy_path_from_user((const char __user *)PT_REGS_PARM2(regs), buf, sizeof(buf)))
        return ret;
    if (is_protected_block_path(buf)) {
        ksu_close_fd((int)ret);
        pr_info("partition_guard: blocked write openat2 of %s\n", buf);
        return -EACCES;
    }
    return ret;
}

/* mount(source, target, type, flags, data): flags = PARM4. */
long __nocfi ksu_hook_mount(int orig_nr, const struct pt_regs *regs)
{
    long ret;
    unsigned long flags;

    if (likely(!ksu_partition_guard_enabled))
        return ksu_syscall_table[orig_nr](regs);

    flags = (unsigned long)PT_REGS_PARM4(regs);
    /*
     * A remount that clears MS_RDONLY turns a protected partition writable.
     * Reject that outright; every other mount call goes through untouched so
     * namespaces, tmpfs and bind mounts keep working.
     */
    if ((flags & MS_REMOUNT) && !(flags & MS_RDONLY)) {
        char src[160];
        char tgt[160];
        src[0] = '\0';
        tgt[0] = '\0';
        copy_path_from_user((const char __user *)PT_REGS_PARM1(regs), src, sizeof(src));
        copy_path_from_user((const char __user *)PT_REGS_PARM2(regs), tgt, sizeof(tgt));
        pr_info("partition_guard: blocked remount rw of %s (src %s)\n",
                tgt[0] ? tgt : "?", src[0] ? src : "?");
        return -EACCES;
    }

    ret = ksu_syscall_table[orig_nr](regs);
    return ret;
}

/*
 * Does the fd@fd refer to a raw block device? Used by the write hooks. Cheap
 * enough because it only runs for a pre-filtered fd (see ksu_block_fd_write).
 */
static bool fd_is_block_device(unsigned long fd)
{
    struct fd f;
    bool isblk = false;

    /* A real fd is far below this; the bound only guards against garbage. */
    if (fd >= (1UL << 20))
        return false;
    f = fdget((unsigned int)fd);
    if (f.file)
        isblk = S_ISBLK(file_inode(f.file)->i_mode);
    fdput(f);
    return isblk;
}

/*
 * Return true when this write must be blocked. To keep the hot path cheap we
 * only look up the fd after the guard is on and the fd number is plausible;
 * the block-device check itself is a single inode-mode test.
 */
static bool block_fd_write(unsigned long fd)
{
    if (likely(!ksu_partition_guard_enabled))
        return false;
    if (!fd_is_block_device(fd))
        return false;
    pr_info("partition_guard: blocked write to block device fd %lu\n", fd);
    return true;
}

/* write(fd, buf, count): fd = PARM1. */
long __nocfi ksu_hook_write(int orig_nr, const struct pt_regs *regs)
{
    if (block_fd_write((unsigned long)PT_REGS_PARM1(regs)))
        return -EACCES;
    return ksu_syscall_table[orig_nr](regs);
}

/* pwrite64(fd, buf, count, pos): fd = PARM1. */
long __nocfi ksu_hook_pwrite64(int orig_nr, const struct pt_regs *regs)
{
    if (block_fd_write((unsigned long)PT_REGS_PARM1(regs)))
        return -EACCES;
    return ksu_syscall_table[orig_nr](regs);
}

/* writev(fd, iov, iovcnt): fd = PARM1. */
long __nocfi ksu_hook_writev(int orig_nr, const struct pt_regs *regs)
{
    if (block_fd_write((unsigned long)PT_REGS_PARM1(regs)))
        return -EACCES;
    return ksu_syscall_table[orig_nr](regs);
}

#ifndef __NR_pwrite64
#define __NR_pwrite64 68
#endif
#ifndef __NR_writev
#define __NR_writev 20
#endif

void __init ksu_partition_guard_init(void)
{
    int ret = ksu_register_feature_handler(&partition_guard_handler);
    if (ret)
        pr_err("partition_guard: register feature failed: %d\n", ret);
    pr_info("partition_guard: init done (default off)\n");
}

void __exit ksu_partition_guard_exit(void)
{
    ksu_unregister_feature_handler(KSU_FEATURE_PARTITION_GUARD);
    pr_info("partition_guard: exit\n");
}
