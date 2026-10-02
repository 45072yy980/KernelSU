/* SPDX-License-Identifier: GPL-2.0 */
#ifndef __KSU_H_STEALTH
#define __KSU_H_STEALTH

#include <linux/types.h>

/*
 * Stealth mode: when on, GET_INFO stops reporting the flags that tell a
 * caller "a manager is installed here". The kernel's own idea of who the
 * manager is does not change -- only what it admits to over the supercall.
 */
void ksu_stealth_load(void);
bool ksu_stealth_is_enabled(void);
int ksu_stealth_set(bool enabled);

#endif /* __KSU_H_STEALTH */
