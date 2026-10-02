// SPDX-License-Identifier: GPL-2.0
/*
 * Stealth mode (隐身模式)
 *
 * When on, GET_INFO stops reporting the flags that describe the manager:
 *
 *   KSU_GET_INFO_FLAG_MANAGER    -- "the manager app is installed"
 *   KSU_GET_INFO_FLAG_LATE_LOAD  -- "the module was loaded after boot"
 *
 * The second one matters here more than upstream. This fork ships a jailbreak
 * mode that loads kernelsu.ko long after init, which is exactly what
 * LATE_LOAD advertises; leaving it set would give away the very thing the
 * mode exists to hide. Upstream has no such flag, so the idea was borrowed
 * from 7kimisu (GPL-3.0) and extended to cover both bits.
 *
 * Two things this deliberately does NOT hide:
 *
 *   - The kernel still knows who the manager is. is_manager() is untouched,
 *     so the manager app keeps its privileges and can switch this back off.
 *     Only the answer given over the supercall changes.
 *
 *   - LKM and BUNDLED are still reported. They describe how the module was
 *     loaded (as a module, bundled with its manager), not who is listening,
 *     and callers use them to pick the right behaviour.
 *
 * State lives in /data/adb/ksu/stealth as a single character, '0' or '1'.
 * That directory is created by ksud and always exists; nothing here needs to
 * mkdir. Keeping the state on disk means uninstalling and reinstalling the
 * manager app does not turn the disguise off.
 */
#include <linux/cred.h>
#include <linux/err.h>
#include <linux/errno.h>
#include <linux/fcntl.h>
#include <linux/file.h>
#include <linux/fs.h>
#include <linux/types.h>

#include "klog.h" // IWYU pragma: keep
#include "ksu.h"
#include "manager/stealth.h"

#define KSU_STEALTH_PATH "/data/adb/ksu/stealth"

static bool ksu_stealth_enabled;
static bool ksu_stealth_loaded;

static int ksu_stealth_write(const char *data, size_t len)
{
    const struct cred *saved;
    struct file *fp;
    loff_t off = 0;

    saved = override_creds(ksu_cred);
    fp = filp_open(KSU_STEALTH_PATH, O_WRONLY | O_CREAT | O_TRUNC, 0644);
    if (IS_ERR(fp)) {
        revert_creds(saved);
        return PTR_ERR(fp);
    }

    if (kernel_write(fp, data, len, &off) != (ssize_t)len) {
        pr_warn("stealth: short write to %s\n", KSU_STEALTH_PATH);
        filp_close(fp, NULL);
        revert_creds(saved);
        return -EIO;
    }

    filp_close(fp, NULL);
    revert_creds(saved);
    return 0;
}

void ksu_stealth_load(void)
{
    struct file *fp;
    char c = '0';
    loff_t pos = 0;

    ksu_stealth_loaded = true;

    fp = filp_open(KSU_STEALTH_PATH, O_RDONLY, 0);
    if (IS_ERR(fp)) {
        /* No file yet: the switch has never been touched, so it is off. */
        ksu_stealth_enabled = false;
        return;
    }

    ksu_stealth_enabled = (kernel_read(fp, &c, 1, &pos) == 1 && c == '1');
    filp_close(fp, NULL);

    pr_info("stealth: loaded, %s\n",
        ksu_stealth_enabled ? "enabled" : "disabled");
}

bool ksu_stealth_is_enabled(void)
{
    if (!ksu_stealth_loaded)
        ksu_stealth_load();

    return ksu_stealth_enabled;
}

int ksu_stealth_set(bool enabled)
{
    char c = enabled ? '1' : '0';
    int ret;

    ret = ksu_stealth_write(&c, 1);
    if (ret)
        return ret;

    ksu_stealth_enabled = enabled;
    ksu_stealth_loaded = true;

    pr_info("stealth: set to %d\n", enabled ? 1 : 0);
    return 0;
}