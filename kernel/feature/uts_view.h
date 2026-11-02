#ifndef __KSU_FEATURE_UTS_VIEW_H
#define __KSU_FEATURE_UTS_VIEW_H

#include <linux/ptrace.h>
#include <linux/types.h>

#include "uapi/uts_view.h"

int ksu_uts_view_init(void);
void ksu_uts_view_exit(void);

/* 供 supercall 分发调用：读/写配置，参数是用户态指针。 */
int ksu_uts_view_get(struct ksu_uts_config __user *arg);
int ksu_uts_view_set(struct ksu_uts_config __user *arg);

#endif /* __KSU_FEATURE_UTS_VIEW_H */
