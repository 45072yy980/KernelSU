# Diksu Ultra · 越狱模式

**一个面向未解锁 Bootloader 设备的 KernelSU 分支：不改 boot，不刷 system，靠漏洞把内核模块挂进去。内置 ghostlock 与 DirtyFrag 两条提权路径，任何一条成功都能让系统进入越狱模式。**

> **4.0 起更名为 Diksu Ultra，管理器包名同步改为 `me.diksu.ultra`。**
> 基线（`wuhudiao/DikSU`）已确认停更，本分支不再对基线代码作兼容性保护，可以自由偏离。
> 包名变了意味着这是一个**新的应用**：需要先卸载旧管理器再装 4.0（`/data/adb` 下的模块、授权记录、配置都不受影响）。

- **GitHub**：https://github.com/45072yy980/KernelSU
- **基线（原作者）**：https://github.com/wuhudiao/DikSU
- **Telegram**：https://t.me/DIKSU66
- **QQ 交流群**：`864553367`

---

## 目录

- [一、这个分支做了什么](#一这个分支做了什么)
  - [1. 越狱模式：两条提权路径](#1-越狱模式两条提权路径)
  - [2. 系统分区保护（Partition Guard）](#2-系统分区保护partition-guard)
  - [3. SUSFS 与内置 Shizuku](#3-susfs-与内置-shizuku)
  - [4. 版本号与发布](#4-版本号与发布)
  - [5. 更新记录](#5-更新记录)
  - [6. 从 3.x 升级到 4.0](#6-从-3x-升级到-40)
- [二、来自基线的功能](#二来自基线的功能)
- [三、界面风格](#三界面风格)
- [四、构建](#四构建)
- [声明与致谢](#声明与致谢)
- [License](#license)

> 代码整治的排期与判据见 [`docs/refactor-plan.md`](docs/refactor-plan.md)（**待办，未开始**）。

---

# 一、这个分支做了什么

## 1. 越狱模式：两条提权路径

**这是本分支存在的理由。**

官方 KernelSU 与绝大多数内核方案都要求**解锁 Bootloader 并刷入 boot 镜像**。对很多设备来说这条路是堵死的：厂商不给解锁、解锁会清空数据、或者解锁后保修 / 部分功能（支付、银行类应用）直接受影响。

本分支提供了另一条路：**在不碰 boot、不碰 system 的前提下，把一个内核模块挂进正在运行的系统里。** 并且内置了**两条互相独立的提权路径**——ghostlock 与 DirtyFrag。任何一条跑通，收尾都是同一个：

```
提权成功
   │
   ▼
ksud late-load --package-name <本 App 包名>
   │
   ▼
ksud insmod kernelsu.ko            ← 内核模块在这一刻被挂载
   │  此时 current->pid != 1，模块自己知道：
   ▼
ksu_late_loaded == true            ← 进入"越狱模式"
```

关键在于最后一步。KernelSU 原本假定自己是在 **init 阶段**（`pid == 1`）被加载的，因此可以随意改动 SELinux 策略、接管挂载、注册各种钩子。**late load 破坏了这个假设**：模块挂载时系统已经跑起来很久了，SELinux 已经加载完毕，很多进程也已经带着旧的状态在跑。

### 两条路怎么选

| | ghostlock（CVE-2026-43499） | DirtyFrag（CVE-2026-43284） |
|---|---|---|
| 思路 | 直接拿到 root 代码执行 | 页缓存写 → 改 `libc++.so` → 内核模块 → bootstrap |
| 覆盖 | 按**内核构建号**定制，内置 53 个目标内核 | 按 **KMI**（android12-5.10 ~ android17-6.18），同 KMI 通用 |
| 链长 | 短：漏洞 → ksud | 长：页缓存写 → shellcode → insmod → bootstrap → ksud |
| 副作用 | 几乎没有；失败重启即净 | 中间会短暂关闭 SELinux enforcing；失败出口会自动恢复 |
| 何时用 | **首选**，命中目标表时最稳 | 目标表没覆盖，或 ghostlock 失败时的第二条路 |

两条路都**完整内置在管理器里**，不依赖任何外部 APK：

- **ghostlock**：`cpp/ghostlock/core/*.c` → `libghostlock.so`，自带各内核的偏移表，`uname -r` 不在表里就拒绝运行；
- **DirtyFrag**：`cpp/dirtyfrag/` → `libdirtyfrag.so`（8 个 KMI 的 `dfroot.ko` 与 `splicehelper` 用 `.incbin` 内嵌）+ `libdirtyfrag_bootstrap.so`。

### ghostlock 链路

```
越狱激活（ghostlock）
   │  执行 nativeLibraryDir/libghostlock.so
   ▼
利用 CVE-2026-43499 拿到 root 代码执行
   │  在 /data/app/**/lib/arm64/libksud.so 找到管理器自带的 ksud
   ▼
ksud late-load --package-name <本 App 包名>
```

### DirtyFrag 链路

```
越狱激活（DirtyFrag）
   │  生成 IPsec ESP 传输变换，执行 libdirtyfrag.so
   ▼
用 ESP + splice 把任意字节写进只读文件的 page cache
   │  改 libc++.so 几条指令，拉起 crash_dump64 触发 shellcode
   ▼
shellcode（root，vendor_modprobe 域）→ insmod dfroot.ko
   │  模块关 SELinux enforcing、绕 DEFEX，再 call_usermodehelper 拉起 bootstrap
   ▼
bootstrap → ksud late-load --package-name <本 App 包名>
```

有两点容易被误解，这里写清楚：

- **顺序是"先有 root 才能 insmod"**：模块是漏洞成功之后的载荷，不是提权的前提。没有第一步的 root 代码执行，`insmod` 必然失败。
- **模块不常驻**：`dfroot.ko` 的 init 末尾 `return -ENODEV`，内核随即卸载它；它的作用是关闭 SELinux enforcing、绕开 DEFEX、把 bootstrap 拉起来。
- **移植改动只有一处**：模块里原本写死 `df.root` 包名的 bootstrap 路径，改成了 96 字节可写槽位，由 `exp.c --stage-dir` 在写入前改成本应用的实际目录；模块本身由本项目的 DDK 与 `kernelsu.ko` 一起编译。

### late load 下的适配

本分支把 late-load 这条链路整个理顺了：

| 子系统 | 在 late load 下做的适配 |
|---|---|
| `core/init.c` | 用 `current->pid != 1` 判定加载方式，并据此调整初始化流程 |
| `feature/selinux_hide.c` | 重新加载 sepolicy 前先确认加载器是否已经动过；按 late-load 场景重建隐藏规则 |
| `runtime/ksud_integration.c` | 调整与 ksud 的对接时序 |
| `supercall/dispatch.c` | 对管理器上报 `KSU_GET_INFO_FLAG_LATE_LOAD`，让 App 知道当前处于越狱模式 |

### 你在 App 里会看到什么

- 首页状态卡片显示 **「工作中（越狱模式）」**，而不是普通的「工作中」
- 下方有一张 **越狱守卫卡片**，说明当前状态下系统分区写入受保护、**重启即还原**
- 越狱激活弹窗里两条路径并列：**ghostlock（推荐）** 与 **DirtyFrag**，各自带说明
- 越狱模式下的模块安装会被拦截真正写入分区的部分，避免把系统改坏

### 为什么它值得存在

- **不改 boot / system**：全程只做内存里的加载，重启后系统回到完全原样的状态，不存在"刷坏了"的风险
- **不需要解锁 BL**：只要设备上存在可用的提权漏洞，就能拿到 root
- **两条路互为备份**：ghostlock 覆盖不到的内核，还有 DirtyFrag 按 KMI 兜底
- **可逆**：想退出，重启即可；想彻底不装，卸载管理器即可
- **代价**：越狱模式的生效依赖漏洞，漏洞被修补后旧设备仍可用、新设备可能不行；重启后需要重新触发一次

---

## 2. 系统分区保护（Partition Guard）

越狱模式本身不改分区，但**越狱之后拿到的 root 可以改**。于是本分支加了一层运行时的分区写保护。

代码在 `kernel/feature/partition_guard.c`。

### 它拦什么

只拦**真正会破坏系统分区的写入**，共 6 个系统调用钩子，分三类路径：

| 路径 | 拦截内容 |
|---|---|
| `openat` / `openat2` | 以**写模式**打开 `/dev/block/*` 原始块设备 |
| `mount` | 把系统分区**重挂为读写** |
| `write` / `pwrite64` / `writev` | 通过**已经打开的**块设备 fd 写入数据 |

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

> 更细的设计说明（开关语义、已知边界、feature id、逐条测试方法）见
> [`docs/partition-guard.md`](docs/partition-guard.md)。

---

## 3. SUSFS 与内置 Shizuku

### SUSFS

- **ksud** 带 SuSFS 用户态客户端：规则列表持久化，开机时重放；
- **管理器** 有独立面板：特性状态列表、SUSFS 版本，完整中文本地化；
- 首页状态里能直接看到 SUSFS 是否可用及其版本。

### 内置 Shizuku

- 服务端按 FolkPatch 的实现重写，**随管理器内置**，不用另装 Shizuku；
- 完整管理面板与日志页：启停、授权管理、日志查看与清空；
- **fpdrop 分权**：`setuid/setgid` + 清空 capability 的降权可执行文件，按应用授权（含仅 shell 权限模式）；
- 授权弹窗直接渲染居中卡片（浮动窗口下系统 `Popup` 尺寸为 0 的老问题）；
- 隐藏管理器（随机包名）下会同步改写 Shizuku 的 provider / action，组件不会失效。

---

## 4. 版本号与发布

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
| `v4.0.0-ultra` | `40000` |

- 管理器：`manager/build.gradle.kts`
- 内核：`kernel/Kbuild`（`KSU_VERSION`）

两边读同一个 tag，**因此不会再出现"状态卡片写的版本和关于页写的版本对不上"这种情况**。

发一个版本只需要 `git tag vX.Y.Z-diksu && git push --tags`，CI 会构建全部产物，发布由维护者手工确认（`release.yml` 的 `release` job 默认不自动跑）。

---

## 5. 更新记录

### v4.1.0-ultra

这一版处理四件事：自动提权不再依赖"往内核里注入"，导航入口不再整条消失，Material 3 设置页清掉残留项，网页端补上本项目自己的设置。

#### 自动提权并越狱：改用开机自启权限
- 3.x/4.0 的开关会顺带打开 `com.mngr.app.magica.BootCompletedReceiver`，由 app zygote 的 preload 直接 exec `ksud late-load` —— 完整重启后越狱用户没有 root，这条路径等于"在没有权限的情况下往内核里注入模块"，既不会成功，失败还会留下痕迹。
- 4.1 断开这条链路：开关**只写偏好**，真正的提权由 `AutoEscalationBootReceiver` 用普通应用就有的 `RECEIVE_BOOT_COMPLETED` 权限触发，在应用进程里跑随包内置的提权链（和手动点"提权"同一条路），不预先假设有 root，也不写系统分区。
- 新增开机守卫：本次开机只尝试一次（按 `boot_id` 判重）、连续失败 3 次自动暂停、安全模式直接跳过；手动"立即尝试"会把失败计数清零，让下次开机重新有机会。

#### 导航入口置灰
- 五个导航组件原本都是 `if (!Natives.isFullFeatured()) return`：内核模块没加载时整条导航消失，只剩首页一张"未安装"卡片。
- 现在入口常驻、整体置灰，点一下弹出原因与恢复入口（安全模式 / 尚未提权 / 内核模块未加载三种判定，越狱未提权优先于"未加载"，避免误导），并可直接跳回首页处理。

#### Material 3 设置页清理
- 主设置页还留着已经迁到「基础功能设置」的整块开关：su compat、sulog、ADB root、内核卸载、SELinux 隐藏、Shell su 仅 ADB、默认卸载模块、Web 调试、软重启，以及已废弃的"自动越狱"和"应用配置模板"。
- 4.1 全部移除，三套皮肤口径一致：主设置页只保留「通用 / 外观 / 功能入口 / 关于 / 危险操作」，具体开关统一在基础功能设置里。

#### 网页端
- 新增「内核特性」区块：直接读写内核 supercall，列出并切换 su compat、内核级卸载、sulog、ADB Root、SELinux 隐藏、Shell su 仅 ADB、系统分区守卫、运行时分区守卫，不支持的特性会置灰并注明。
- 新增「工具」区块：重载模块（按 `post-fs-data` / `services` / `boot-completed` 三个阶段重跑）与软重启（保留当前 root 会话）。
- 后端 `/api/features` 补齐本分支的特性清单，并新增 `/api/tools/reload-modules`。

### v4.0.0-ultra

**这是更名后的第一个大版本。** 管理器包名从 `me.diksu.kernelsu` 改为 `me.diksu.ultra`，应用名改为 **Diksu Ultra**。

#### 更名与包名迁移
- 源码包、AIDL、JNI 符号（`Java_me_weishu_kernelsu_*` → `Java_me_diksu_ultra_*`）、内核 `KSU_PACKAGE_NAME`、ksud 默认包名、ghostlock 探测路径全部同步；
- 内核侧按新包名认领管理器，越狱与 boot 补丁两种模式都不受影响；
- 基线已停更，本项目不再为兼容基线而保留旧路径。

#### 内置 Shizuku
- **修掉"开机自启时灵时不灵"的根因**：自启原本挂在 SUSFS 开机重放的末尾，而那个接收器在 SUSFS 未加载（绝大多数人）或重试超限时会提前 `return`，整段自启被跳过。现在自启独立成 `ShizukuBootReceiver`，只依赖开关本身，并带 5 次递增间隔重试。
- 面板重写：运行结论 + 运行身份/进程 PID/授权数三段式状态卡；开机自启卡直接显示**上次自启的结果**（时间、触发来源、失败原因），并可"立即自启一次"验证链路；应用列表加搜索与筛选；未授权时不再显示无效的 Root 分权开关；Material 3 皮肤是独立实现而不是降级说明。

#### SuSFS
- **补齐 Material 3 实现**：3.x 在 Material 皮肤下只有一句"请切到 miuix"，等于面板不可用；现在状态、特性、开关、全部添加动作、procfs 快照、日志都在。
- 新增**规则清单**：列出已持久化的隐藏路径 / 循环路径 / sus map / kstat / open redirect，可逐条删除，并明确写出"内核侧没有 remove 命令，删除只影响下次开机重放"。
- miuix 皮肤同步获得规则清单。

#### 自动提权并越狱（原"自动越狱"）
- 3.x 的开关只会拉起一个**空的** `MagicaService`，拨了不会发生任何事。
- 现在由 `AutoEscalationBootReceiver` 在开机后按用户设定的**顺序**调用随包内置的提权链（ghostlock / DirtyFrag），第一个成功即停，失败自动尝试下一个。
- 新增设置页「自动提权并越狱」：总开关、参与方式与优先级（上下箭头调整）、立即跑一次并实时看日志、上次结果。

#### 弹窗一致性
- 新增 `AppAlertDialog`：同一套"标题 + 正文 + 取消/确定"，按当前皮肤渲染成 miuix 的 `WindowDialog` 或 Material 3 的 `AlertDialog`；
- SuSFS 的两个输入弹窗（含 13 字段的静态 kstat 表单）已迁移，输入框本身也跟随皮肤，不再在 miuix 弹窗里嵌 Material 输入框。

#### 借鉴 Aster 的界面改进
- 设置页「关于」行尾加版本胶囊（Aster 的 VersionBadge 思路）：不点进去就知道装的是哪一版；
- 面板状态卡采用"Aster 式"的结论优先布局（先回答"现在能不能用"，再给细节与主操作）；
- 规则清单与开机自启卡沿用 Aster 的"把限制写在卡片里"的做法，不假装操作已经立刻生效。

#### 构建
- CI 的产物上传不再限定 `main`/`dev`，工作分支也能跑完整 repack；
- 仓库卫生：根目录自用 APK 加入 `.gitignore`。

### v3.5.8-diksu

自 v3.5.7 以来共 **15 个提交**（含一次上游同步），主要是把 3.5.7 之后的同步内容补齐、修掉模块页两个老问题，并把设置页重新归类。

- **同步上游 tiann/KernelSU** —— 模块 preinit、模块热安装、`shell_su_adb_only`、模块黑名单、`lookup_symbols` ioctl、模块仓库接口等（40 个上游提交）。
- **修复"提权后仍显示未安装"** —— 内核构建时把管理器包名固定为本分支的 `me.diksu.ultra`，内核侧按包名 + v2 签名兜底识别，不再只认上游包名。
- **模块大小** —— 改由 ksud 在 root 侧算好、随 `module list` 返回（管理器进程读不了 `/data/adb`，之前永远算不出来），列表里常显为带图标的小胶囊；顺带修掉 Miuix 卡片 `preinitWarning()` 的 `let` 少一个收尾括号、把 ID / 更新地址 / 大小整块吞掉的 bug。
- **模块热安装** —— 安装完成且已热生效时不再提示重启，改为提示"已热安装"；是否热安装仍由模块在 `customize.sh` 里 `MODULE_HOT_INSTALL_REQUEST=true` 决定。
- **新增设置项** —— 模块黑名单（`/data/adb/ksu/block_modules`）、shell su 仅 adb。
- **界面** ——
  - 美化版（Miuix）隐藏系统状态栏，切到原版 / Material 自动恢复；
  - 主题页新增「简洁模式 / 显示更多模块信息 / 禁用 Pager 滑动」；
  - 基础功能页按 **Root 与 Shell、内核特性、模块与刷写、调试** 分组，模块更新检查也从「更新」区搬到这里；
  - 其他功能页按 **隐蔽与伪装、系统守护、工具** 分组。
- **构建** —— versionCode 的提交计数改用 `--first-parent`，合并上游后不会再跳进下一个小版本区间；自用包可只构建 arm64。

### v3.5.7-diksu

自 v3.5.5 以来共 **77 个提交、150 个文件**（42 fix / 13 feat / 7 ci / 8 docs / 2 build / 2 refactor）。

- **越狱激活第二条路：DirtyFrag（CVE-2026-43284）** —— 源码编译进管理器，8 个 KMI 的 `dfroot.ko` 与 `splicehelper` 全部内嵌，不依赖外部应用；模块路径改成可改写槽位，失败会自动恢复 SELinux enforcing 并清掉 `/dev/df` 标记。
- **SUSFS** —— ksud 用户态客户端 + 管理器面板，规则持久化并开机重放。
- **内置 Shizuku 重写** —— 服务端按 FolkPatch 实现重写，修好内置服务起不来的问题；补完整管理面板与日志页；授权弹窗改直接渲染。
- **美化版主页卡片对齐基线** —— 保留 110dp 出血圆环，磨砂背景与按压反馈跟随卡片；修复主页无法上滑。
- **设置整理** —— 维护工具并入「其他功能」；状态卡片模糊背景开关移入「主题设置」；Stock / Material 设置页统一分组标题。
- **修复** —— 隐藏管理器下 Shizuku provider / action 改写；`uapi` 只保留内核侧单一权威定义；版本号公式加同步校验；`fpdrop` 移出源码树、只编 arm64-v8a。
- 已知小问题：美化版状态卡按压变形时，卡片下方可能看到一块略小的亮色块，暂未定位，不影响功能。

### v3.5.5-diksu

- **维护工具页**：设置 → 维护工具。重载模块、软重启、带隐身的软重启，附彩色日志面板。
- **主界面 Xposed 框架行**：与 Zygisk 实现、元模块实现同一原理。
- **玻璃对齐**：设置 → 其他功能。磨砂卡片采样壁纸的偏移滑块，实时生效，可保存/重置。
- **全新图标**：桌面 / 启动页 / 关于页 / 模块页。
- **Miuix 美化版**：首页底部多留 24dp。
- 修复：关于页（Miuix 美化版）图标被裁切 → 完整显示。
- 修复：启动页图标浅灰方块在白屏上突兀 → 圆形裁切 + 圆内纯白。
- 修复：玻璃采样默认偏移 15/375 → 30/441。

---

## 6. 从 3.x 升级到 4.0

包名变了，Android 会把它当成另一个应用，所以升级是"重装"而不是"覆盖"：

1. 记住当前的管理器设置（界面模式、隐藏应用列表等）——这些存在应用私有目录，卸载会清掉；
2. 卸载旧管理器 `me.diksu.kernelsu`；
3. 安装 4.0（`me.diksu.ultra`）；
4. root、模块、超级用户授权记录都在 `/data/adb` 下，**不受影响**；如果是越狱模式，重新点一次提权即可。

如果你开了隐藏管理器（随机包名），旧包同样要先卸载再装新包。

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
- **本分支的调整**：越狱模式下的状态卡片、守卫卡片、提示卡片沿用同一套毛玻璃切片方案（3.5.7 按基线重新对齐：保留 110dp 出血圆环，磨砂背景与按压反馈跟随卡片）
- **设置页**：三类界面风格共用同一套分组标题；维护动作并入「其他功能」，状态卡片模糊背景开关在「主题设置」

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

**native 部分**：`libghostlock.so`、`libdirtyfrag.so`、`libdirtyfrag_bootstrap.so` 都由 CMake 随管理器一起编（需要 NDK）；
`dfroot.ko` 不在本地编，CI 用 DDK 与 `kernelsu.ko` 同批构建，按 KMI 一起发布。

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
- **DirtyFrag 提权路径** —— 移植自 [mitschud/DirtyFrag](https://github.com/mitschud/DirtyFrag)（上游为 [diabl0w/DFRoot](https://github.com/diabl0w/DFRoot)），源码内置、改为本应用可用的暂存路径；上游未声明许可证，此处仅作来源标注
- **ghostlock 提权路径** —— 来自 [45072yy980/ghostlock-app](https://github.com/45072yy980/ghostlock-app)

## 关于 DeepSeek

**本分支的越狱模式、分区保护以及本轮全部修复，是在 DeepSeek 的协助下完成的。**

具体来说，DeepSeek 承担了：

- **越狱模式**（`AppZygotePreload` → `fork` → `exec ksud` → `insmod`，以及 late-load 下 SELinux、init、ksud 对接的整套适配）
- **系统分区保护**（`partition_guard.c` 的六个系统调用钩子、分区匹配、双层开关）
- **版本号体系**（管理器与内核共用的 tag 推导公式，替换掉原先会漂移的手写值）
- **发布与 CI**（多架构构建、产物打包与上传流程）
- **移植期的全部缺陷修复**（图标、滑动、毛玻璃、更新地址、误报的 PR 构建标志等）
- **DirtyFrag 移植**（页缓存写链路、内核模块槽位改写、DDK 构建接入、失败收尾）
- **3.5.7 的界面整理**（主页卡片对齐、设置项重排、SUSFS 与 Shizuku 面板对接）

## 致谢

- 感谢 [wuhudiao](https://github.com/wuhudiao/DikSU)（**QQ：2847738211**）—— 本项目的基线
- 感谢 [Aster](https://github.com/LyraVoid/Aster) —— 美化版 UI 的设计与实现
- 感谢 [KernelSU](https://github.com/tiann/KernelSU) 原项目及社区
- 感谢 **DeepSeek** —— 越狱模式、分区保护与全部修复的实现
- 感谢 [mitschud](https://github.com/mitschud/DirtyFrag) 与 [diabl0w](https://github.com/diabl0w/DFRoot) —— DirtyFrag 利用链
- 感谢 [45072yy980](https://github.com/45072yy980/ghostlock-app) —— ghostlock 利用链
- [Kernel-Assisted Superuser](https://git.zx2c4.com/kernel-assisted-superuser/about/)：The KernelSU idea.
- [Magisk](https://github.com/topjohnwu/Magisk)：The powerful root tool.
- [genuine](https://github.com/brevent/genuine/)：APK v2 signature validation.
- [Diamorphine](https://github.com/m0nad/Diamorphine)：Some rootkit skills.

---

# License

[GPL-3.0](LICENSE)，与 KernelSU 相同。
