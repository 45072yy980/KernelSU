# 运行时系统分区写保护（Runtime Partition Guard）

> **归档说明**：本文原为预发布 `v3.4.0-diksu-guard` 的发行说明。该预发布已被
> 正式版 [`v3.4.1-diksu`](https://github.com/45072yy980/KernelSU/releases/tag/v3.4.1-diksu)
> 取代，Release 页面已下线；其中的技术内容（设计、钩子清单、开关语义、已知
> 边界与测试方法）仍然有效，故保留于此，供查阅与复现。
>
> 本文描述的是 **v3.4.0 时期** 的实现。当前版本的实际情况以代码
> `kernel/feature/partition_guard.c` 为准（钩子数量、挂载点清单与默认值可能
> 已有调整）。

---

## 设计

所有钩子都挂在 KernelSU 的 **syscall dispatcher** 上，因此**只对已获得 root 的
进程生效** —— 普通应用、系统守护进程、内核线程完全不受影响。

## 钩子清单

| 钩子 | 拦什么 |
|---|---|
| `openat` | 以写方式打开 `/dev/block/**` |
| `openat2` | 同上（另一种打开方式，防绕过） |
| `mount` | `MS_REMOUNT` 且未带 `MS_RDONLY`，**且目标是系统分区**（`/`、`/system`、`/vendor`、`/product`、`/odm`、`my_*` 等）。其余 remount（tmpfs / loop 镜像 / 沙箱）一律放行 |
| `write` | 经由**已打开的**块设备 fd 写入 |
| `pwrite64` | 同上（带偏移写入） |
| `writev` | 同上（向量写入） |

有了后三个，**`dd` 那种"先打开 fd 再反复写"的路径也拦得住**。

> 注：本文写于 v3.4.0。此后的实现把钩子收敛到四条与写入相关的路径
> （`openat` / `openat2` 写打开、`mount` 重挂读写、`write` 与 `pwrite64`），
> 逻辑不变。

## ⚠️ 默认关闭，需手动开启

本版改动了开关设计（见提交 `2fd3b72` / `9f95174`）：

- 「系统分区保护」开关下面**多出一个子开关「运行时防护（内核拦截）」**，
  只在父开关**打开**时才显示。
- **不管越狱还是非越狱，它默认都是关闭的**。因为它挂钩了 `write`/`writev`
  这类极高频系统调用，而这段代码**还没在真机上做过压力验证**；出于「不替用户
  承担未验证内核代码风险」的考虑，默认不开启。
- 越狱模式**不再强制开启**它（旧版曾经的强制逻辑已回退）。越狱模式「不碰分区」
  这条红线仍由「安装时静态扫描」+ 用户不使用写分区工具共同保证。

## 内核 feature

- 静态「系统分区保护」：feature `partition_guard`（id 5，安装时扫描）。
- 运行时拦截：**独立的** feature **`partition_guard_runtime`（id 6）**，
  管理器用 `ksud feature set partition_guard_runtime <0|1>` 驱动，ksud 持久化并在
  加载时重放；内核侧默认值为 0。

## 已知边界（务必了解）

- `mount` 钩子已收窄到**只拦系统分区**（日志 `allowing remount rw of non-system ...`）。
- `write` 系列钩子在开启时，会对每个写请求做一次 `fget_raw` + `S_ISBLK` 判断，
  这是换取"覆盖已打开 fd"的有意取舍。只读/非块设备 fd 直接放行。
- 仍未覆盖：`ioctl`（如 `BLKFLSBUF`）、`io_uring` 写、`memfd`/`O_DIRECT` 等非常规路径。
- 只影响被标记进程；`ksud` 安装模块写普通文件不受影响。

## 新增：三个「隐藏 root」开关（均默认关闭）

设置 → **基本设置**，三项互不影响，可单独开关：

### 1. 软重启前重置 PID 计数器

越狱用户只能用**软重启**（杀 zygote）加载内核模块，而软重启**不会重置内核 PID 计数器**
——计数器会一直往上爬，导致进程 PID 远大于开机时长应有的值，检测方一看就报越狱。
本开关在软重启前把 PID 计数器**滚回小值**，让重启后的框架拿到「像刚开机」的 PID 段。

- **已内置进 ksud**（Rust 原生实现），**无需安装第三方模块**。
- 首选路径：写 `ns_last_pid`（仅当内核编译了 `CONFIG_CHECKPOINT_RESTORE`）。
- 兜底路径：`fork` 循环推进计数器直到**回绕**（pid_max 通常 32768）
  并重新落到 ≥1700——与可信模块 `soft_restart_fix` 的做法一致。
  循环预算 **10 秒 / 至多 `pid_max + 4096` 次**，并在独立线程中执行，
  **绝不阻塞软重启**；即使超时，流程照常继续。
- 日志：`pid_reset: PID counter wrapped and reached 1700 (last_pid=..., N forks)`。

### 2. 卡片毛玻璃背景

主页**工作卡片，以及它上方的一整组提示卡片**（更新提示、内核/GKI 警告、root 警告、
越狱守护横幅）都可以选中毛玻璃背景。做法是把整张壁纸**模糊后按面板尺寸裁剪、
再按卡片位置对齐**贴上去 —— 卡片显示的，就是它身下那块壁纸，**不是一层
半透明遮罩**。

- 上方那组提示卡片**共用一整块玻璃**：多张叠在一起时读起来是一张玻璃板，
  而不是一堆色块；一张都不显示时玻璃也不画。
- 只在 **Miuix 界面 + 设置过主页壁纸** 时生效；Material 界面没有壁纸层，此项无效。
- 卡片自带图片时优先显示图片。
- 为避免"采样自己"导致渲染栈溢出（之前崩溃的原因），**完全不使用 backdrop**。

### 3. 禁用左右滑动切页

开启后主页不再响应左右滑动切页，底部导航照常可用（防误触）。

## 安装包（历史）

| 文件 | 说明 |
|---|---|
| `DikSU_v3.4.0-diksu-guard-1-gb06e7a3d_32704-release.apk` | 管理器 APK（含新 ksud、`libghostlock.so`、新桌面图标、三个新开关） |
| `kernelsu-lkm.zip` | 内核模块（arm64-v8a / x86_64 各 8 个 KMI，含全部 6 个钩子） |

支持 KMI：`android12-5.10`、`android13-5.10`、`android13-5.15`、`android14-5.15`、
`android14-6.1`、`android15-6.6`、`android16-6.12`、`android17-6.18`。

## 核验

- APK 签名证书 SHA-256：`b27c5a4787d3541bbe1aeaf316eccae0eb07d5795d485199f9c0d2f9aedea815`
  （与内核内嵌 EXPECTED_HASH 一致）。
- 内核模块中含新的 feature 名 `partition_guard_runtime`，以及 `init done (default off)`
  和四条拦截日志串：`blocked write open of`、`blocked write openat2 of`、
  `blocked remount rw of`、`blocked write to block device fd`。

## 如何测试

1. 打开「其他功能」页，打开「系统分区保护」→ 此时会出现
   「运行时防护（内核拦截）」子开关，**手动打开它**。
2. 用已授权的 root shell 依次尝试（预期全部 `Permission denied`）：
   - `dd if=/dev/zero of=/dev/block/by-name/…… bs=512 count=1`
   - `mount -o remount,rw /`
   - `exec 3<>/dev/block/by-name/……; echo x >&3`
3. 只读操作应照常：`dd if=/dev/block/by-name/…… of=/dev/null bs=512 count=1`。
4. `dmesg | grep partition_guard` 能看到对应拦截日志。
5. ⚠️ 首次开启后，建议先按 README 的「分层验证」观察一段时间再长期使用。
