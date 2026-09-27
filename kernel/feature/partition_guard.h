#ifndef __KSU_H_PARTITION_GUARD
#define __KSU_H_PARTITION_GUARD
#include <asm/ptrace.h>
#include <linux/types.h>

/*
 * Runtime system-partition write guard.
 *
 * Unlike the install-time scan in ksud (which reads a module's scripts before
 * running them), this blocks the *actual* write at the syscall boundary: a
 * process that already holds root (and is therefore marked, i.e. routed through
 * KernelSU's syscall dispatcher) may not open a raw block device for writing.
 *
 * Design notes:
 *   - We hook __NR_openat, the modern entry point for open()/creat().
 *   - The syscall dispatcher only fires for marked processes, so non-root apps,
 *     system daemons and the kernel itself are never affected.
 *   - The guard is off by default and toggled from the Manager.
 */

void ksu_partition_guard_init(void);
void ksu_partition_guard_exit(void);

void ksu_partition_guard_set(bool enabled);
bool ksu_partition_guard_is_enabled(void);

long ksu_hook_openat(int orig_nr, const struct pt_regs *regs);

#endif /* __KSU_H_PARTITION_GUARD */
