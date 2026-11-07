## 这一版新增

### UTS 视图
按 uid 伪装 `uname(2)` 的六个字段：关闭 / 全局 / 仅指定应用，最多 64 个目标。
只影响 `uname(2)`，不动 namespace —— 直接读 `/proc` 与 sysfs 仍是真值（界面里写明了）。
热路径走 RCU，多个进程同时 `uname` 不排队。

### 网络隔离
按 uid 拒绝应用对外连接：拦 `connect()` 与未连接数据报的 `sendmsg()`（UDP `sendto`），
可放行回环与私网/链路本地。`AF_UNIX`、`AF_NETLINK` 从不拦，`AF_PACKET` 不覆盖。
被拦调用返回 `EACCES`，关回「关闭」立即恢复。

### 管理器
- 两个特性页按 Shizuku 页的骨架重做：状态卡 + 小标题分组 + 单选行 + 带图标/搜索的应用选择
- 设置页「工具」组统一图标列；重载模块、软重启、隐藏软重启收进「维护」子页面
- 应用选择按内核上限截住（UTS 64 / 网络隔离 128），不再整份配置被 `-EINVAL` 拒掉

### 内核
- 同步上游 25 个提交（`96770dfb` → `730f6446`）
- 其中关键：6.6.144 的 `lsm_blob_sizes` 变动会让 `selinux_inode` 失效甚至 panic，
  改用 `vmlinux.btf` 查 `lbs_inode` 偏移
- SELinux hide 策略解析对齐、file_wrapper 引用计数修复、热点路径去噪

### 其它
- WebUI 新增 File / pm 接口与 BlobDownload，下载完成后可直接打开文件
- 备份与恢复（授权规则 / 模块）、版本详情与自检页
- CI：ksud 增加 x86_64 矩阵（bindgen 按平台 char 有符号性生成不同类型，只编 aarch64 会漏）
- 文档站换成 DikSU 自己的 14 页：https://45072yy980.github.io/KernelSU/

## 安装

下载 `arm64-v8a` 的 APK 直接安装。内核模块已打进 APK，越狱模式下由管理器自己注入，
不需要单独安装 `.ko`。

> 面向**未解锁 Bootloader** 的设备；重启后需要重新激活越狱。风险自负，详见文档站。
