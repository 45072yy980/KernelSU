#ifndef __KSU_FEATURE_NETISOLATE_H
#define __KSU_FEATURE_NETISOLATE_H

#include <linux/types.h>

#include "uapi/netisolate.h"

int ksu_netisolate_init(void);
void ksu_netisolate_exit(void);

/* 供 supercall 分发调用：读/写配置，参数是用户态指针。 */
int ksu_netisolate_get(struct ksu_netisolate_config __user *arg);
int ksu_netisolate_set(struct ksu_netisolate_config __user *arg);

#endif /* __KSU_FEATURE_NETISOLATE_H */
