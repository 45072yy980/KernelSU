# DikSU · 越狱模式

**一个面向未解锁 Bootloader 设备的 KernelSU 分支：不改 boot，不刷 system，靠漏洞把内核模块挂进去。**

- **GitHub**：https://github.com/45072yy980/KernelSU
- **基线（原作者）**：https://github.com/wuhudiao/DikSU
- **Telegram**：https://t.me/DIKSU66
- **QQ 交流群**：`864553367`

---

## 目录

- [一、这个分支做了什么](#一这个分支做了什么)
  - [1. 越狱模式（Jailbreak / Magica / late-load）](#1-越狱模式jailbreak--magica--late-load)
  - [2. 系统分区保护（Partition Guard）](#2-系统分区保护partition-guard)
  - [3. 版本号与发布](#3-版本号与发布)
- [二、来自基线的功能](#二来自基线的功能)
- [三、界面风格](#三界面风格)
- [四、构建](#四构建)
- [声明与致谢](#声明与致谢)
- [License](#license)

---

# 一、这个分支做了什么

## 1. 越狱模式（Jailbreak / Magica / late-load）

**这是本分支存在的理由。**

官方 KernelSU 与绝大多数内核方案都要求**解锁 Bootloader 并刷入 boot 镜像**。对很多设备来说这条路是堵死的：厂商不给解锁、解锁会清空数据、或者解锁后保修 / 部分功能（支付、银行类应用）直接受影响。

本分支提供了另一条路：**在不碰 boot、不碰 system 的前提下，把一个内核模块挂进正在运行的系统里。**

### 它是怎么工作的

整条链路藏在 Android 的 **App Zygote 预加载**里：

```
MagicaService / BootCompletedReceiver
        │  触发一次应用进程启动
        ▼
AppZygotePreload.doPreload()                 ← Android 的 ZygotePreload 接口
        │  System.loadLibrary("kernelsu")
        │  native: forkDontCareAndExecKsud(libksud.so, packageName)
        ▼
fork() 出一个子进程，随即 exec ksud
        │  ksud 利用设备上的提权漏洞拿到 root
        ▼
ksud  insmod kernelsu.ko                     ← 内核模块在这一刻被挂载
        │  此时 current->pid != 1，模块自己知道：
        ▼
ksu_late_loaded == true                      ← 进入"越狱模式"
```

关键在于最后一步。KernelSU 原本假定自己是在 **init 阶段**（`pid == 1`）被加载的，因此可以随意改动 SELinux 策略、接管挂载、注册各种钩子。**late load 破坏了这个假设**：模块挂载时系统已经跑起来很久了，SELinux 已经加载完毕，很多进程也已经带着旧的状态在跑。

本分支把这条链路整个理顺了：

| 子系统 | 在 late load 下做的适配 |
|---|---|
| `core/init.c` | 用 `current->pid != 1` 判定加载方式，并据此调整初始化流程 |
| `feature/selinux_hide.c` | 重新加载 sepolicy 前先确认加载器是否已经动过；按 late-load 场景重建隐藏规则 |
| `runtime/ksud_integration.c` | 调整与 ksud 的对接时序 |
| `supercall/dispatch.c` | 对管理器上报 `KSU_GET_INFO_FLAG_LATE_LOAD`，让 App 知道当前处于越狱模式 |

### 你在 App 里会看到什么

- 首页状态卡片显示 **「工作中（越狱模式）」**，而不是普通的「工作中」
- 下方有一张 **越狱守卫卡片**，说明当前状态下系统分区写入受保护、**重启即还原**
- 越狱模式下的模块安装会被拦截真正写入分区的部分，避免把系统改坏

### 为什么它值得存在

- **不改 boot / system**：全程只做内存里的加载，重启后系统回到完全原样的状态，不存在"刷坏了"的风险
- **不需要解锁 BL**：只要设备上存在可用的提权漏洞，就能拿到 root
- **可逆**：想退出，重启即可；想彻底不装，卸载管理器即可
- **代价**：越狱模式的生效依赖漏洞，漏洞被修补后旧设备仍可用、新设备可能不行；重启后需要重新触发一次

---

## 2. 系统分区保护（Partition Guard）

越狱模式本身不改分区，但**越狱之后拿到的 root 可以改**。于是本分支加了一层运行时的分区写保护。

代码在 `kernel/feature/partition_guard.c`。

### 它拦什么

只拦**真正会破坏系统分区的写入**，一共四条路径：

| 路径 | 拦截内容 |
|---|---|
| `openat` / `openat2` | 以**写模式**打开 `/dev/block/*` 原始块设备 |
| `mount` | 把系统分区**重挂为读写** |
| `write` / `pwrite64` | 通过**已经打开的**块设备 fd 写入数据 |

### 它不拦什么

**其余一切照旧**，包括：

- loop 设备、overlayfs、tmpfs、bind mount
- 命名空间相关的挂载
- `/data`、`/mnt`、`/sdcard` 下的任何操作
- 正常的模块挂载

这样设计的目的是：**模块系统、沙箱、日常使用完全不受影响**，只有"往系统分区里写"这件事会被拒绝。

### 覆盖的分区

```
/system  /system_ext  /system_dlkm
/vendor  /vendor_dlkm
/product /odm
/my_product  /my_stock  /my_carrier  /my_region  /my_bigball  /my_manifest
```

（`/my_*` 是国产 ROM 常见的一批厂商分区。）

### 开关

- 顶层开关在**管理器设置**里，默认**开启**
- 运行时拦截是**第二层开关**，**默认关闭**——因为它挂在 `write` 这种极热的系统调用上，开启前你需要在 UI 里显式确认
- 越狱模式下这两层同样有效

### 效果

拦截发生时，内核日志会打印：

```
partition_guard: blocked remount rw of /system (src /dev/block/sda1)
```

写入方拿到的是 `-EACCES`。**重启之后，一切回到分区原本的样子。**

---

## 3. 版本号与发布

版本号**不再手写**，而是从 git tag 推导，管理器与内核**用同一个公式**：

```
versionCode = major × 10000 + minor × 1000 + patch × 100 + tag 之后的提交数
```

| tag | versionCode |
|---|---|
| `v3.5.0-diksu` | `35000` |
| `v3.5.0-diksu` + 1 个提交 | `35001` |
| `v3.5.1-diksu` | `35100` |
| `v3.6.0-diksu` | `36000` |
| `v4.0.0` | `40000` |

- 管理器：`manager/build.gradle.kts`
- 内核：`kernel/Kbuild`（`KSU_VERSION`）

两边读同一个 tag，**因此不会再出现"状态卡片写的版本和关于页写的版本对不上"这种情况**。

发一个版本只需要 `git tag vX.Y.Z-diksu && git push --tags`，CI 会构建全部产物，发布由维护者手工确认（`release.yml` 的 `release` job 默认不自动跑）。

---

# 二、来自基线的功能

以下功能来自本项目所基于的 **`wuhudiao/DikSU`**，感谢原作者：

## 1. 网页端（WebUI）：原版没有的完整管理界面

原版 KernelSU 的网页端只用来承载模块自己的 WebUI 页面；DikSU 在 `ksud` 里直接内建了一个 HTTP 服务，把整套管理界面做进了浏览器，不依赖管理器 App 也能完成绝大部分操作。

代码全部位于 `userspace/ksud/src/webui*.rs` 与 `userspace/ksud/assets/web/`，前端约 4900 行、服务端约 8900 行，对外暴露 60+ 个 `/api/*` 接口。

- **首页与状态**：内核版本、设备型号、系统指纹、SELinux 状态、Seccomp 状态；功能开关直接在网页上切换，带内核能力检测
- **超级用户**：应用列表、按名称 / 包名搜索、授权 / 撤销 / 查看详情
- **模块管理**：在线与本地安装（实时进度、日志、中途取消）、模块列表与信息、打开模块内 WebUI
- **文件管理器（双栏）**：互相复制 / 移动、`chmod` / `chown`（支持递归）、新建 / 重命名 / 删除 / 搜索、压缩包解压与内部浏览、上传下载、带行号的文本编辑器、把文件直接装成模块
- **终端**：基于 PTY 的交互式 shell，可同时开 4 个会话，输出缓冲 256 KB
- **后台任务**：并发跑 8 个后台命令，stdout / stderr 分离轮询
- **应用与 APK 解析**：`apkparser.rs` 不依赖 Android framework，直接解析二进制 `AndroidManifest.xml` 与 `resources.arsc` 拿应用标签
- **keyMint 配置页**：守护进程状态、系统属性修正、应用路由、keybox 本地 / 远程管理、日志级别
- **一键配置隐藏应用列表**：内置 HMA-OSS 脚本，标准版与 Scene 版两种场景
- **入口：计算器**：网页端藏在一个普通外观的计算器里，用暗码触发；每次安装包名都不同

## 2. 管理器 APK 端

- **随机包名（隐藏管理器）**：在设备上重写 `AndroidManifest.xml`、`resources.arsc`、`lib/<abi>/libksud.so` 里的包名，**保持字节长度不变**，再用内置 `kernelsu.jks` 重签名
- **启动器随机包名**：给 `com.xxxxx.yyyyy` 形状的启动器每次安装生成新包名
- **一键配置隐藏应用列表**：设置里直接跑 HMA-OSS 脚本
- **keyMint 配置面板**：独立设置页，内容与网页端一致

---

# 三、界面风格

- **三档界面风格**：`miuix（美化版）` / `miuix（原版）` / `Material 3`，设置中自由切换
- **美化版 UI**：图片 / 视频壁纸背景、玻璃拟态导航与面板、可调背景暗度
- **本分支的调整**：越狱模式下的状态卡片、守卫卡片、提示卡片沿用同一套毛玻璃切片方案；桌面图标、禁止左右滑动、更新检测地址已按本分支重新对齐

---

# 四、构建

```bash
# 管理器 APK
cd manager && ./gradlew :app:assembleRelease

# 内核模块
cd kernel && make

# 用户态工具
cargo build --release
```

**打包发布**：打 tag 并推送即可，CI 会产出管理器 APK、各 KMI 的内核模块、`ksud`（8 个平台）、`ksuinit`（3 个架构）：

```bash
git tag v3.5.0-diksu
git push origin v3.5.0-diksu
```

CI 完成构建后，由维护者手工创建 Release 并上传产物。

---

# 声明与致谢

## 声明

**本项目是二创作品，基于以下项目：**

- **原项目**：[KernelSU](https://github.com/tiann/KernelSU)（作者 tiann），许可证 GPL-3.0
- **直接基线**：[wuhudiao/DikSU](https://github.com/wuhudiao/DikSU)（**作者 QQ：2847738211**）
  - 本分支的全部代码以该项目为基线
  - WebUI、随机包名、keyMint、三档 UI、美化界面等均来自原作者
  - **请支持原作者**：https://github.com/wuhudiao/DikSU

**本分支新增的部分：**

- **越狱模式（Jailbreak / Magica / late-load）** —— 面向未解锁 BL 设备的整套加载链路
- **系统分区保护（Partition Guard）** —— 运行时的系统分区写拦截
- 与之配套的管理器侧改动（状态卡片、守卫卡片、提示卡片、滑动控制、图标与更新检测地址）

## 关于 DeepSeek

**本分支的越狱模式、分区保护以及本轮全部修复，是在 DeepSeek 的协助下完成的。**

具体来说，DeepSeek 承担了：

- **越狱模式**（`AppZygotePreload` → `fork` → `exec ksud` → `insmod`，以及 late-load 下 SELinux、init、ksud 对接的整套适配）
- **系统分区保护**（`partition_guard.c` 的四条写入路径钩子、分区匹配、双层开关）
- **版本号体系**（管理器与内核共用的 tag 推导公式，替换掉原先会漂移的手写值）
- **发布与 CI**（多架构构建、产物打包与上传流程）
- **移植期的全部缺陷修复**（图标、滑动、毛玻璃、更新地址、误报的 PR 构建标志等）

## 致谢

- 感谢 [wuhudiao](https://github.com/wuhudiao/DikSU)（**QQ：2847738211**）—— 本项目的基线
- 感谢 [Aster](https://github.com/LyraVoid/Aster) —— 美化版 UI 的设计与实现
- 感谢 [KernelSU](https://github.com/tiann/KernelSU) 原项目及社区
- 感谢 **DeepSeek** —— 越狱模式、分区保护与全部修复的实现
- [Kernel-Assisted Superuser](https://git.zx2c4.com/kernel-assisted-superuser/about/)：The KernelSU idea.
- [Magisk](https://github.com/topjohnwu/Magisk)：The powerful root tool.
- [genuine](https://github.com/brevent/genuine/)：APK v2 signature validation.
- [Diamorphine](https://github.com/m0nad/Diamorphine)：Some rootkit skills.

---

# License

[GPL-3.0](LICENSE)，与 KernelSU 相同。
