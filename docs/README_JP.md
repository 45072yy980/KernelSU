[English](README.md) | [Español](README_ES.md) | [简体中文](README_CN.md) | [繁體中文](README_TW.md) | **日本語** | [한국어](README_KR.md) | [Polski](README_PL.md) | [Português (Brasil)](README_PT-BR.md) | [Türkçe](README_TR.md) | [Русский](README_RU.md) | [Tiếng Việt](README_VI.md) | [Indonesia](README_ID.md) | [עברית](README_IW.md) | [हिंदी](README_IN.md) | [Italiano](README_IT.md)

# DikSU

- GitHub：https://github.com/45072yy980/KernelSU
- Original baseline: https://github.com/wuhudiao/DikSU
- Telegram：https://t.me/DIKSU66
- QQ 交流群：`864553367`

---

## 一、网页端（WebUI）：原版没有的完整管理界面

原版 KernelSU 的网页端只用来承载模块自己的 WebUI 页面；DikSU 在 `ksud` 里直接内建了一个 HTTP 服务，把整套管理界面做进了浏览器，不依赖管理器 App 也能完成绝大部分操作。

代码全部位于 `userspace/ksud/src/webui*.rs` 与 `userspace/ksud/assets/web/`，前端约 4900 行、服务端约 8900 行，对外暴露 60+ 个 `/api/*` 接口。

### 1. 首页与状态
- 系统信息一屏可见：内核版本、设备型号、系统指纹、SELinux 状态（Enforcing 高亮）、Seccomp 状态（过滤 / 严格 / 已禁用 / 不支持）
- 功能开关直接在网页上切换，带内核能力检测，不支持的项自动置灰
- 内置检查更新，指向 DikSU 自己的 Releases（原版指向 KernelSU）
- 一键加入 QQ 群

### 2. 超级用户
- 应用列表、按名称 / 包名搜索
- 授权、撤销授权、查看单个应用的授权详情

### 3. 模块管理
- 在线安装 / 本地安装，带实时进度条、安装日志输出与**中途取消**
- 模块列表、模块信息、模块内 WebUI 打开
- 模块内应用的包名与图标读取

### 4. 文件管理器（双栏）
- 左右双栏浏览，互相复制 / 移动到对侧
- 权限与属主修改（`chmod` / `chown`，支持递归，root）
- 新建文件夹、新建文件、重命名、删除、搜索
- 压缩包支持：解压到对侧、加入压缩包、创建压缩包、**直接浏览压缩包内部**并增删改
- 上传、下载、文本编辑器（带行号）
- 「打开方式」、执行脚本、加入快速跳转与便捷执行入口
- 可直接把文件装成模块

### 5. 终端
- 基于 PTY 的交互式 shell，可同时开 4 个会话，输出缓冲 256 KB
- 命令历史、实时输出流

### 6. 后台任务（spawn）
- 并发跑 8 个后台命令，stdout / stderr 分离轮询
- 任务可随时关闭，未读输出上限 8 MB

### 7. 应用与 APK 解析
- 全量应用列表、单独查询某个应用的详情、提取 APK
- `apkparser.rs`：**不依赖 Android framework**，直接解析二进制 `AndroidManifest.xml` 与 `resources.arsc` 拿到应用标签，配合 `appicon.dex` / `applabel.dex` 取图标，这让网页端在纯 shell 环境下也能显示应用名和图标

### 8. keyMint 配置页
- keymint / injector 两个守护进程的状态显示与单独或一键重启
- 系统属性修正开关（写入 `/data/adb/service.d/omk-fixprops.sh`，开机把已解锁 / 可调试那面的属性改回正常机器的样子）
- 应用路由：选择哪些应用走 Oh My Keymint，改动对新请求立即生效，无需重启
- keybox 管理：本地选择设备上的 `keybox.xml`，或填 URL 远程下载替换，校验通过才生效
- 日志输出级别分别写入 `config.toml` 与 `injector.toml`

### 9. 一键配置隐藏应用列表
- 内置 HMA-OSS 配置脚本，把所有还没配过的第三方应用一次性加进隐藏范围，已配过的只补预设、不动其它字段，管理器自己也从这些应用里隐藏掉
- 标准版与 **Scene 版**两种场景（Scene 版另外把 Scene 排除在范围外，且不勾选「无障碍功能」预设）

### 10. 入口：计算器
网页端不是从管理器点开的，而是藏在一个计算器里：

- 内置 `calculator.apk`，安装后外观就是一个普通第三方计算器
- 在 `/data/adb/ksu/webui.trigger` 里输入暗码，按 `=` 即启动服务并打开页面
- 暗码与包名写在 `/data/adb/ksu/calculator.pkg`，每次安装的包名都不同，设备上没有任何东西能自行推算出来
- 浏览器关闭约 20 秒后服务自动退出

---

## 二、管理器 APK 端：原版没有的功能

### 1. 随机包名（隐藏管理器）
KernelSU 答复 Magisk 的 "Hide the App"。因为包名是烘焙进签名 APK 的，管理器无法像 Magisk 的 stub 那样直接换一个 APK，DikSU 选择**在设备上重写它自己**：

- 重写 `AndroidManifest.xml`（UTF-16LE，5 处：包名、动态广播权限、两个 authority、WebUI 的 taskAffinity）
- 重写 `resources.arsc`（UTF-16LE，包 chunk 的定宽名字字段）
- 重写 `lib/<abi>/libksud.so`（UTF-8，2 处 ksud 默认包名）
- 所有替换**保持字节长度不变**，两个文件里的偏移量一个都不动
- 用随包内置的 `kernelsu.jks` 重新签名。这点是必须的，因为 KernelSU 用 APK 签名证书来"加冕"管理器（`kernel/manager/apk_sign.c`，经 `throne_tracker` 调用）
- 支持 `arm64-v8a` / `armeabi-v7a` / `x86` / `x86_64` / `riscv64`
- 支持自定义应用名与图标，完成后自动打开新应用，也可还原原版

### 2. 启动器随机包名
给 `com.xxxxx.yyyyy` 形状的启动器 APK 每次安装都生成一个新包名，用 5 字母段保证长度与原包名一致，因而 `AndroidManifest.xml` 与 `resources.arsc` 里的偏移仍然有效；异常时直接退回安装未修改的原版。

### 3. 一键配置隐藏应用列表
设置里直接跑上面那套 HMA-OSS 脚本（标准 / Scene 两版），不需要开网页端。

### 4. keyMint 配置面板
一个独立设置页（约 1000 行），内容与网页端的 keyMint 页一致：守护进程状态、属性修正、应用路由、keybox 本地 / 远程、日志级别、重启。没装 `oh_my_keymint` 模块时会给出模块 id 与安装提示。

---

## 三、界面风格

- **三档界面风格**：`miuix(美化版)` / `miuix(原版)` / `Material 3`，在设置中自由切换；原版与 MD3 与官方初始布局一致
- **美化版 UI**：图片 / 视频壁纸背景、玻璃拟态导航与面板、可调背景暗度

---

---

## Jailbreak mode (Magica / late-load)

**For devices with a locked bootloader: no boot image, no system image, no flashing -- the kernel module is loaded into the running system through an exploit.**

Stock KernelSU asks you to unlock the bootloader and flash a boot image. On plenty of devices that road is closed. This fork takes another one.

### How it works

```
MagicaService / BootCompletedReceiver
        |  triggers an app process start
        v
AppZygotePreload.doPreload()                 <- Android's ZygotePreload interface
        |  System.loadLibrary("kernelsu")
        |  native: forkDontCareAndExecKsud(libksud.so, packageName)
        v
fork() a child, then exec ksud
        |  ksud escalates through a device-specific exploit
        v
ksud  insmod kernelsu.ko                     <- the module is loaded right here
        |  current->pid != 1, so the module knows:
        v
ksu_late_loaded == true                      <- we are in jailbreak mode
```

KernelSU assumes it was loaded during **init** (`pid == 1`), where it can rewrite SELinux policy, take over mounts and install hooks at leisure. **A late load breaks that assumption**: by the time the module appears the system has been up for a while, SELinux is loaded, and many processes are running with stale state.

This fork makes that path work end to end:

| Subsystem | What it does under late load |
|---|---|
| `core/init.c` | Detects the load style with `current->pid != 1` and adapts initialisation |
| `feature/selinux_hide.c` | Checks whether the loader already touched sepolicy, then rebuilds hiding rules for the late-load case |
| `runtime/ksud_integration.c` | Adjusts the handshake with ksud |
| `supercall/dispatch.c` | Reports `KSU_GET_INFO_FLAG_LATE_LOAD` so the Manager knows |

### What you see in the app

- The home status card reads **"Working (jailbreak mode)"**
- A **jailbreak guard card** below it explains that system-partition writes are protected and **a reboot restores everything**
- Module installs have the parts that would write to a system partition refused

### The trade-off

- Needs a **usable privilege-escalation exploit** on the device
- **You re-trigger it after every reboot**
- It never touches **boot or system**, so nothing can be bricked; uninstalling the Manager leaves nothing behind

---

## Partition guard

Jailbreak itself does not modify partitions -- but the root it grants can. So a runtime write guard sits on top of it, in `kernel/feature/partition_guard.c`.

### What it blocks

| Path | Blocked |
|---|---|
| `openat` / `openat2` | Opening `/dev/block/*` for **writing** |
| `mount` | **Remounting** a system partition read-write |
| `write` / `pwrite64` | Writing through an **already-open** block-device fd |

### What it leaves alone

Loop devices, overlayfs, tmpfs, bind mounts, namespaced mounts, anything under `/data`, `/mnt`, `/sdcard`, and normal module mounting -- **all untouched**.

### Partitions covered

```
/system  /system_ext  /system_dlkm
/vendor  /vendor_dlkm
/product /odm
/my_product  /my_stock  /my_carrier  /my_region  /my_bigball  /my_manifest
```

(`/my_*` are the vendor partitions common on Chinese ROMs.)

### Switches

- The top-level switch lives in **Manager settings** and defaults to **on**
- The runtime interception is a **second switch**, **off by default** -- it hooks `write`, so it has to be enabled deliberately from the UI

A blocked write logs `partition_guard: blocked ...` and returns `-EACCES`. **A reboot puts the partitions back exactly as they were.**

---

## Versioning

No hand-written version numbers. Both sides derive it from the git tag, with the **same formula**:

```
versionCode = major * 10000 + minor * 1000 + patch * 100 + commits since the tag
```

| tag | versionCode |
|---|---|
| `v3.5.0-diksu` | `35000` |
| `v3.5.0-diksu` + 1 commit | `35001` |
| `v3.5.1-diksu` | `35100` |
| `v4.0.0` | `40000` |

The Manager reads it in `manager/build.gradle.kts`, the kernel in `kernel/Kbuild` (`KSU_VERSION`). Same tag on both sides, so the status card and the about page can no longer disagree.

## 致谢

- 感谢 [Aster](https://github.com/LyraVoid/Aster) 贡献的美化版 UI 设计与实现
- 感谢 [KernelSU](https://github.com/tiann/KernelSU) 原项目及社区
- [Kernel-Assisted Superuser](https://git.zx2c4.com/kernel-assisted-superuser/about/): The KernelSU idea.
- [Magisk](https://github.com/topjohnwu/Magisk): The powerful root tool.
- [genuine](https://github.com/brevent/genuine/): APK v2 signature validation.
- [Diamorphine](https://github.com/m0nad/Diamorphine): Some rootkit skills.

## License

[GPL-3.0](/LICENSE)，与 KernelSU 相同。