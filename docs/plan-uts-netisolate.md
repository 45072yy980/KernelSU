# 内核特性移植方案：UTS 视图 / 网络隔离（照 FinaSU）

> 来源：`LyraVoid/FinaSU` 的 `kernel/feature/uts_view.c`（518 行）与
> `kernel/feature/netisolate.c`（911 行），配套 `uapi` 与 `ksud` 子命令。
> 本文记录我们这边怎么落，以及与原实现的差异。

## 一、能力差距（已核实）

| 功能 | FinaSU | 我们 |
|---|---|---|
| UTS | 内核特性：六字段 + 按 uid 选择，走 uts_namespace + zygote 回调 | **没有**。只有 SuSFS 的 `set-uname`（release/version、全局） |
| 网络隔离 | 内核特性：按 uid 策略、strict socket / 回环 / 内网开关 | 已实现（2026-11-07，只拦 connect，差异见第三节末尾） |

## 二、UTS：用 syscall 钩子实现（不走 namespace）

我们的 `hook/syscall_hook_manager.c` 已经有通用的替换式钩子注册：
`ksu_register_syscall_hook(nr, fn)`，`fn(orig_nr, regs)` 返回即系统调用结果。

方案：
1. 新增 `kernel/feature/uts_view.c/.h`
   - 配置：`mode(global|selected)`、`field_mask`、六个字段（各 64 字节）、`target_uids[64]`
   - `ksu_uts_view_on_uname(struct pt_regs *regs)`：钩子内先调回原 `__arm64_sys_uname`，
     成功后再 `copy_to_user` 覆盖命中 uid 的字段
2. `hook/syscall_hook_manager.c` 注册 `__NR_uname`
3. `include/uapi/supercall.h` 加 `KSU_IOCTL_GET_UTS_CONFIG` / `SET_UTS_CONFIG`
4. `supercall/dispatch.c` 挂两个 handler
5. `userspace/ksud`：`uts get|set`，JSON 与 SuSFS 同样走 stdin
6. 管理器：UTS 页（模式切换、六字段、目标应用多选）

**与 FinaSU 的差异（必须写进界面说明）**：
只伪装 `uname(2)`，**不影响** `/proc/sys/kernel/{ostype,osrelease,version,hostname}` 与
sysfs 上的同名信息。FinaSU 改的是 uts_namespace，覆盖面更广；我们走 syscall 钩子，改动
小、风险低，但反检测能力弱一档。

## 三、网络隔离：LSM socket 钩子

我们的 `hook/lsm_hook.c` 已有 LSM 钩子框架，可挂 `socket_connect` / `socket_sendmsg`
（以及创建时的 `socket_create`）。

方案：
1. `kernel/feature/netisolate.c/.h`
   - 按 uid 的策略表（`strict_socket` / `allow_loopback` / `allow_private_network`）
   - 拒绝时返回 `-EPERM` 或对应 errno
2. uapi 加 `KSU_IOCTL_GET_NETISOLATE` / `SET_NETISOLATE`
3. ksud `netisolate get|set`，管理器一页（应用列表多选 + 三个开关）

**风险**：吞掉 socket 错误可能让部分应用崩溃（FinaSU 也是这么做，但它有真机长期验证）。
必须先在真机上小范围试，出问题要能一键关闭。

**落地情况（2026-11-07，`c935d0773`）**：照本方案实现，实际与上面的草案有两处收敛：

- **只挂 `socket_connect`**，没有动 `socket_create` / `sendmsg`。后两者会打断已建立的连接，
  副作用远大于收益；代价是不 connect 直接 `sendto` 的 UDP 拦不住，这条已写进管理器页面说明。
- **开关从三个收敛成两个**：放行回环、放行私网（含链路本地与运营商 NAT 段）。草案里的
  strict socket 不做。
- 目标 uid 强制 `>= 10000`，系统进程永远进不来；`AF_UNIX` / `AF_NETLINK` 一律放行。
- 拒绝时返回 `-EACCES`；钩子装不上（内核里没有 `selinux_socket_connect`）时 `supported=0`，
  配置写进去也不生效，管理器会显式提示而不是假装成功。
- 热点路径只有一次 `atomic_read`，要查表才进 RCU 读区；配置更新是换指针 + `synchronize_rcu`，
  不在 connect 路径上拿 mutex。

## 四、落地顺序与验证

1. UTS 内核部分（config + uname 钩子 + uapi + dispatch）→ CI 编译
2. ksud `uts` 命令 → CI 编译
3. 管理器 UTS 页 → CI 编译 + 真机验证
4. netisolate 同上

**验证边界**：本环境无 Android 设备，只能保证编译通过；运行时行为（开机、uname 结果、
断网是否影响系统进程）必须由真机确认。每步都可独立回滚。
