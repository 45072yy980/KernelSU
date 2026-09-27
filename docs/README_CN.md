[English](README.md) | [Español](README_ES.md) | **简体中文** | [繁體中文](README_TW.md) | [日本語](README_JP.md) | [한국어](README_KR.md) | [Polski](README_PL.md) | [Português (Brasil)](README_PT-BR.md) | [Türkçe](README_TR.md) | [Русский](README_RU.md) | [Tiếng Việt](README_VI.md) | [Indonesia](README_ID.md) | [עברית](README_IW.md) | [हिंदी](README_IN.md) | [Italiano](README_IT.md)

# DikSU

<img src="https://kernelsu.org/logo.png" style="width: 96px;" alt="logo">

**DikSU** 是一个面向 Android 的、基于内核的 root 方案（KernelSU 的定制分支），
内置**越狱模式（Jailbreak / Magica）**：在设备**未解锁 Bootloader、不修改任何分区**的前提下，
借助已获得的临时权限，把 KernelSU 以内核模块的形式**晚期加载**进正在运行的系统。

[![Latest release](https://img.shields.io/github/v/release/45072yy980/KernelSU?label=Release&logo=github)](https://github.com/45072yy980/KernelSU/releases/latest)
[![Channel](https://img.shields.io/badge/Follow-Telegram-blue.svg?logo=telegram)](https://t.me/KernelSU)
[![License: GPL v2](https://img.shields.io/badge/License-GPL%20v2-orange.svg?logo=gnu)](https://www.gnu.org/licenses/old-licenses/gpl-2.0.en.html)
[![GitHub License](https://img.shields.io/github/license/tiann/KernelSU?logo=gnu)](/LICENSE)

## 特性

### 核心能力（继承自 KernelSU）

- 基于内核的 `su` 和权限管理，比用户态方案更稳定、更隐蔽。
- 基于 [metamodules](https://kernelsu.org/zh_CN/guide/metamodule.html) 的模块系统：可插拔的模块架构，支持 OverlayFS 等主流元模块。
- [App Profile](https://kernelsu.org/zh_CN/guide/app-profile.html)：把 Root 权限关进笼子里。

### 🌟 越狱模式（Jailbreak / Magica）—— 核心特色

面向**没有解锁 Bootloader**、但通过漏洞取得了临时权限（例如 `adb root`）的设备：

| 关键点 | 说明 |
|---|---|
| **全程不修改分区** | 不刷 `boot`、不动 `system`/`vendor`/`product`，所有数据都落在 `/data` |
| **无需解锁 BL** | 适合无法 / 不愿解锁 Bootloader 的用户 |
| **内存晚期加载** | 通过 `late-load` 把 `kernelsu.ko` 直接从内存注入正在运行的内核 |
| **重启即还原** | 不做任何持久化写入，重启后回到原厂状态，无痕可查 |

越狱模式要求 **SELinux 为 Permissive**，并且设备已获得临时 root（典型路径是 `adb root`）。

```text
Manager UI ──▶ AppZygotePreload (JNI) ──▶ ksud late-load --magica <port>
                                              │
                                              ├─ 1. enable_adb_root(port)
                                              ├─ 2. 通过 adb 连接回本机
                                              ├─ 3. 从内存加载 kernelsu.ko
                                              ├─ 4. 执行 post-fs-data / 元模块挂载等阶段
                                              └─ 5. service / boot-completed 收尾
```

### 🚀 一键越狱激活（内置漏洞利用）

设备**完全还没有 root** 时（内核未加载），主界面的「未安装」卡片点击后会弹出选择：

- **越狱激活（漏洞提权）** —— 直接使用内置的 [GhostLock](https://github.com/45072yy980/ghostlock-app)
  （CVE-2026-43499）漏洞利用拿到 uid 0，随后自动调用本管理器自己的 `ksud late-load`，
  就地进入越狱模式。**全程不修改任何分区。**
- **手动安装** —— 走原来的手动安装流程。

内置的漏洞利用以 `libghostlock.so` 形式随 APK 分发（仅 `arm64-v8a`），并会按 `uname -r`
匹配内核偏移表；匹配不到的内核会拒绝运行。它同样遵循「不动分区」的红线。

> ⚠️ 仅支持漏洞利用所覆盖的内核版本；其余设备仍可走手动安装。

### 🛡️ 系统分区保护（System Partition Guard）

越狱模式跑在内存里，**无法像重启那样还原一个被真实写入的分区**。因此在越狱模式下，
安装模块时 DikSU 会自动启用防护；非越狱模式下则由你自行决定是否开启：

- **越狱模式**：强制开启、**不可关闭** —— 晚期加载的会话无法撤销一次真实的分区写入。
- **非越狱模式**：可在 设置 → **其他功能** → **系统分区保护** 中自由开关。
  开启后，安装模块时 Manager 会向 ksud 传入 `KSU_PARTITION_GUARD=1`。

拦截规则（ksud 侧）：

- **会拦截**：脚本中真正触达块设备 / 真实分区的操作 ——
  `dd ... of=/dev/...`、`> /dev/block/...`、`tee /dev/block/...`、
  `mkfs`/`mke2fs`/`tune2fs`/`e2fsck`/`resize2fs`/`nandwrite`/`sgdisk`/`parted` 操作 `/dev/...`、
  `mount -o remount,rw /dev/...`、`blockdev --setrw /dev/...`、`fastboot flash` 等。
- **不会误伤**：仅包含 `system/` 等分区目录的普通模块（那是 OverlayFS 的**临时**覆盖，重启即还原）、
  写 `/sys`、写 `/data`、bind mount、注释里提到 flash 等，都会正常放行。

> ℹ️ 默认情况下该防护**只在越狱模式自动启用**；正常开机进入的 KernelSU 不受影响，
> 除非你在设置里主动打开它。
> 若确需强制安装，可使用 `ksud module install --force <zip>` 绕过。

### 其它定制

- **Manager 更名为 DikSU**，包名 `me.diksu.kernelsu`，独立签名链，与内核内的证书哈希严格对应。
- **Miuix 主题**：Material 与 Miuix 双 UI 风格，可自由切换。

## 兼容状态

DikSU 官方支持 GKI 2.0 的设备（内核版本 5.10 以上）；旧内核也是兼容的（最低 4.14+），不过需要自己编译内核。

WSA、ChromeOS 和运行在容器上的 Android 也可以与 DikSU 一起工作。

目前支持 `arm64-v8a` 和 `x86_64` 架构。

**越狱模式额外要求**：SELinux 为 Permissive，且可获取临时 root（如 `adb root`）。

> [!CAUTION]
> 最近的内核版本引入了一项破坏性更改，导致 KernelSU 在 `x86_64` 上运行失败，甚至可能引发内核恐慌 (kernel panic)！请查看网站获取更多信息！

## 使用方法

- [安装教程](https://kernelsu.org/zh_CN/guide/installation.html)（DikSU 支持其中的 LKM / 越狱模式两种路径）
- [如何构建？](https://kernelsu.org/zh_CN/guide/how-to-build.html)
- [官方网站](https://kernelsu.org/zh_CN/)
- **越狱模式**：在 Manager 主界面，当检测到 SELinux 为 Permissive 时会出现「越狱」入口，按提示操作即可。

## 与上游 KernelSU 的差异

1. **包名 / 名称**：`me.diksu.kernelsu`，显示名 `DikSU`。
2. **越狱模式（Magica）**：完整的内核晚期加载链路（Manager → JNI → ksud → 内核）。
3. **系统分区保护**：越狱模式强制开启、非越狱模式可开关，拦截会真实写入分区的模块。
4. **内置漏洞利用**：未 root 时可由「未安装」卡片直接提权并进入越狱（GhostLock / CVE-2026-43499）。
4. **签名链**：使用自建密钥与证书哈希，内核与 Manager 严格匹配。
5. **UI**：Miuix / Material 双主题，含若干界面优化。

## 参与翻译

中文（简体 / 繁体）为本项目的主要维护语言。如需新增语言支持，欢迎提交 PR。

## 讨论

- Telegram: [@KernelSU](https://t.me/KernelSU)

## 安全性

有关报告 KernelSU 安全漏洞的信息，请参阅 [SECURITY.md](/SECURITY.md)。

## 许可证

- 目录 `kernel` 下所有文件为 [GPL-2.0-only](https://www.gnu.org/licenses/old-licenses/gpl-2.0.en.html)。
- 除 `kernel` 目录的其他部分均为 [GPL-3.0-or-later](https://www.gnu.org/licenses/gpl-3.0.html)。

## 鸣谢

- [KernelSU](https://github.com/tiann/KernelSU)：本项目的上游与基础。
- [kernel-assisted-superuser](https://git.zx2c4.com/kernel-assisted-superuser/about/)：KernelSU 的灵感。
- [Magisk](https://github.com/topjohnwu/Magisk)：强大的 root 工具箱。
- [genuine](https://github.com/brevent/genuine/)：apk v2 签名验证。
- [Diamorphine](https://github.com/m0nad/Diamorphine)：一些 rootkit 技巧。