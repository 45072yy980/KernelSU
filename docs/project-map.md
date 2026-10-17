# 项目地图与架构分析（Project Map）

> 目的：给后续开发的一份"从哪进去、改哪里、哪些别碰"的索引。
> 内容以 **2026-10-17 远端 `origin/main` = `3580919c`** 的代码实测为准；
> 本地已 fast-forward 到该提交，并在其上带了一轮未提交的构建卫生修复（见第四节 4.4 / 4.6 / 4.7 与第七节）。
> 与 README / 历史文档冲突时以本文列出的源码位置为准，冲突本身也记在第四节。

---

## 一、这是什么

**DikSU「越狱模式」**：KernelSU 的一个 fork，目标是在**不解锁 Bootloader、不刷 boot/system** 的前提下，
把一个内核模块挂进正在运行的系统，从而拿到 KernelSU 的 root。

- 上游：`tiann/KernelSU`（GPL-3.0）
- 直接基线：`wuhudiao/DikSU`
- 本仓库：`45072yy980/KernelSU`，远端 `origin/main`

### 代码来源三分法（改代码前先判断自己在改哪一类）

| 来源 | 代表 | 处理原则 |
|---|---|---|
| 上游 KernelSU | `kernel/supercall/`、`kernel/selinux/`、`userspace/ksud/src/boot_patch.rs`、`userspace/ksuinit/` | 原则上不动，改了以后上游合并会冲突 |
| 基线 DikSU | `webui*.rs`、`apkparser.rs`、`lkm_image*.rs`、`module.rs`、Miuix 美化 UI、随机包名 | 谨慎，动了等于与基线分叉 |
| 本分支新增 | 越狱链路（`ghostlock/`、`magica/`、`late_load.rs`、`ksu_late_loaded`）、`partition_guard.c`、Shizuku 面板、UI 调整、版本号逻辑 | 优先整治 |

### 规模（实测）

| 部分 | 文件数 | 行数 |
|---|---|---|
| C（内核模块，含头文件） | 87 | 14,317 |
| Rust（`userspace/`） | 52 | 24,016 |
| Kotlin/Java（`manager/`） | 304 | 56,806 |
| Shizuku native（`cpp/rish` + `cpp/fpdrop`） | 9 | 723 |
| GitHub Actions | 12 个 workflow | — |
| GhostLock 漏洞利用体 | 605 KB（几乎全是偏移表） | — |

---

## 二、目录地图

### 2.1 内核模块 `kernel/`（编译成 `kernelsu.ko`）

| 目录 | 职责 | 关键文件 |
|---|---|---|
| `core/` | 模块入口与全局状态 | `init.c`——`ksu_late_loaded` 判定就在这里（`current->pid != 1`） |
| `supercall/` | 与用户态的 ioctl/supercall 协议 | `dispatch.c`（命令分发 + `KSU_GET_INFO_FLAG_LATE_LOAD` 上报）、`perm.c`、`supercall.c` |
| `hook/` | 系统调用与 LSM 钩子 | `syscall_hook_manager.c`（钩子总开关）、`lsm_hook.c`、`setuid_hook.c`、`tp_marker.c`、`arch/arm64` 与 `arch/riscv64`/`x86_64` 的 `patch_memory.c` |
| `feature/` | 可开关的功能实现 | `partition_guard.c`（本分支新增）、`selinux_hide.c`、`sucompat.c`、`sulog.c`、`kernel_umount.c`、`adb_root.c` |
| `policy/` | 授权与按应用配置 | `allowlist.c`、`app_profile.c`、`feature.c`（feature id ↔ handler 表） |
| `infra/` | 与内核打交道的底层设施 | `file_wrapper.c`、`symbol_resolver.c`、`seccomp_cache.c`、`su_mount_ns.c`、`event_queue.c` |
| `manager/` | 识别管理器 App | `apk_sign.c`（内嵌签名哈希）、`throne_tracker.c`、`pkg_observer.c`、`stealth.c` |
| `runtime/` | 与 ksud 的联动、boot 事件 | `ksud_integration.c`（late-load 分支）、`boot_event.c` |
| `selinux/` | sepolicy 规则注入 | `rules.c`、`sepolicy.c`、`selinux.c` |
| `include/uapi/` | **唯一的权威 uapi 头**（见 4.1） | `feature.h`、`supercall.h`、`ksu.h`、`app_profile.h`、`selinux.h`、`sulog.h` |
| 构建 | Kbuild + DDK | `Kbuild`（含 `KSU_VERSION` 公式）、`build-all.sh`、`setup.sh` |

### 2.2 用户态 `userspace/`

| 组件 | 说明 | 关键文件 |
|---|---|---|
| `ksud/` | 核心二进制，体积最大的一块 | `main.rs`（模块清单）、`cli.rs`（clap 子命令，~1100 行）、`late_load.rs`（147 行，加载 ko）、`magica.rs`（172 行，adb root 通道） |
| `ksud` WebUI | 内建 HTTP 服务 + 前端 | `webui.rs`（4078 行，92 处 `api/` 路由）、`webui_files.rs`（2042，双栏文件管理）、`webui_shell.rs`（PTY）、`webui_apps.rs`、`webui_keymint.rs`、`webui_spawn.rs`；静态资源 `assets/web/index.html`（4656 行）+ `ksu-bridge.js` |
| `ksud` 模块系统 | 安装/启用/挂载模块 | `module.rs`（1150 行）、`module_config.rs`、`metamodule.rs`、`lkm_image.rs`（3172 行，LKM 镜像与 BTF 处理） |
| `ksud` 其他 | 内核镜像、SUSFS、隐藏 | `boot_patch.rs`、`susfs/`、`stealth.rs`、`soft_reboot.rs`、`resetprop.rs`、`apkparser.rs`（不依赖 framework 解析 APK） |
| `ksud` 构建 | bindgen 指向 `-I../../kernel/include`，直接吃内核权威 uapi 头 | `build.rs` |
| `ksuinit/` | 极小 ELF：`main` → `init::init()` → `execve("/init")`，负责挂载 `/proc`/`/sys` 等并 `insmod`，**仅用于 boot-patch / LKM 正常路径**，越狱模式不走它 | `src/init.rs` |

### 2.3 管理器 App `manager/`

- 包名策略：`namespace = "me.weishu.kernelsu"`，`applicationId` 由 `KSU_PACKAGE_NAME` 决定（默认 `me.diksu.kernelsu`，PR 构建 `me.weishu.kernelsu.pr`）；
  Manifest 里所有组件都指向 `com.mngr.app.*` 的**转发类**（`Entry`、`ui/MainActivity`、`ui/WebUIActivity`、
  `magica/*`、`service/DownloadService`），真正的实现在 `me.weishu.kernelsu.*` 里被继承。
- 三套 UI 并存：`Miuix`（美化）/ `MiuixStock`（原版）/ `Material`，同一屏常见三个文件。
- 入口与基础设施：`KernelSUApplication.kt`、`Natives.kt`（JNI 声明）、`ui/MainActivity.kt`、`ui/navigation3/`（Routes/Navigator/IntentDispatcher）。

| 目录 | 职责 |
|---|---|
| `ui/screen/{home,module,modulerepo,superuser,settings,appprofile,flash,susfs,sulog,shizuku,...}` | 各屏，`home` 最大（`HomeMiuix.kt` 1093 行） |
| `ui/component/{miuix,material,liquid,profile,...}` | 组件层，毛玻璃/液体效果在 `miuix/effect`、`liquid` |
| `ui/util/` | `KsuCli.kt`（对 ksud 的 shell 封装 + root 检测）、`JailbreakExploit.kt`（跑 GhostLock）、`HomeWallpaper.kt`、`ManagerHider.kt` / `LauncherRenamer.kt`（随机包名） |
| `data/repository/` | `SettingsRepositoryImpl`（SharedPreferences 开关 + `ksud feature set`）、`SuperUserRepositoryImpl`、模块仓库 |
| `magica/` | `AppZygotePreload`（`ZygotePreload` 实现）、`MagicaService`、`BootCompletedReceiver` |
| `shizuku/` + `rikka/` + `moe/` | 内建 Shizuku server（42 个文件 / 约 5.4k 行，最近 24 个提交的主战场） |
| `src/main/cpp/` | `jni.cc`（JNI 入口 + fork/exec 链路）、`ksu.cc`、`adbroot.cc`、`ghostlock/`（漏洞利用）、`rish/`（PTY 交互库）、`fpdrop/`（降权工具） |
| `src/main/jniLibs/arm64-v8a/libksud.so` | 由 `justfile` 把交叉编译的 ksud 拷进来当作 lib 打包 |

---

## 三、运行时架构与关键链路

### 3.1 五层

```
管理器 App (Kotlin/Compose)
   ├─ JNI → libkernelsu.so (Natives.kt / jni.cc / ksu.cc) ── supercall ──┐
   ├─ shell → /data/adb/ksu/bin/ksud (KsuCli.kt)  ── supercall ──────────┤
   └─ WebView → ksud 内建 HTTP (localhost)  ─────────────────────────────┤
                                                                        ▼
                                                            kernelsu.ko（内核模块）
                                                                        │
                                    SELinux 规则 / syscall hooks / allowlist / feature 表
```

`ksud` 既能被 App 通过 shell 调用，也能起 HTTP 服务被 WebView 调用；两条路最终都落到 supercall。

### 3.2 越狱加载链路（本分支存在的理由）

**主路径：GhostLock 漏洞**

1. `ui/util/JailbreakExploit.kt` 从 `nativeLibraryDir` 执行 `libghostlock.so`（arm64 专用，自带 kernel 偏移表，`uname -r` 不匹配就拒绝运行）。
2. 环境变量 `GHOSTLOCK_HOME` / `TMPDIR` / `GHOSTLOCK_KSU_LOG` / `GHOSTLOCK_MANAGER` 交给利用体。
3. 利用体在 `cpp/ghostlock/core/main.c` 里拿 root，写内联脚本，在 `/data/app/**/lib/arm64/libksud.so` 里找到管理器自带的 ksud，
   然后 `ksud late-load --package-name <本 App 包名>`。
4. `ksud late_load.rs` → `insmod kernelsu.ko`，内核侧 `kernelsu_init()` 看到 `current->pid != 1` →
   `ksu_late_loaded = true`。
5. 之后管理器通过 `KSU_GET_INFO_FLAG_LATE_LOAD`（`supercall/dispatch.c`）知道当前是越狱模式，
   `Natives.isLateLoadMode` 驱动首页「工作中（越狱模式）」卡片、越狱守卫卡片、软重启默认值等。

**备用路径：App Zygote 预加载（Magica）**

`AppZygotePreload.doPreload()` → `System.loadLibrary("kernelsu")` → `forkDontCareAndExecKsud()` →
`jni.cc:384` 里 `fork` + `setuid(0)` + 再 `fork` + `execl(ksud, "late-load", "--magica", "5555", ...)`。
`BootCompletedReceiver` 在开机 / 自定义广播 `com.mngr.app.magica.LAUNCH` 时拉起 `MagicaService`（`isolatedProcess` + `useAppZygote`）。

> ⚠️ 这条路径假设 App Zygote 子进程可以直接 `setuid(0)` 成功，见第四节「待核实项」。

**late-load 下内核做的适配**（`kernel/core/init.c:146` 起）：跳过 kprobe 钩子、先 `apply_kernelsu_rules()` + `cache_sid()`、
把当前进程 `escape_to_root_for_init()` 抬到 ksu 域、加载 allowlist、最后 `setenforce(true)`。
退出路径也不同：`ksu_late_loaded` 时**不**调用 `ksu_ksud_exit()`（`init.c:202`）。

### 3.3 feature 开关（管理器 ↔ 内核）

- 内核侧表：`kernel/policy/feature.c`，id 定义在 `kernel/include/uapi/feature.h`。
- 当前 id：`su_compat=0, kernel_umount=1, sulog=2, adb_root=3, selinux_hide=4, partition_guard=5, partition_guard_runtime=6`。
- ksud 侧另有一份 `FeatureId` 枚举（`userspace/ksud/src/feature.rs`），并把值持久化到 `/data/adb/ksu/.feature_config`，加载时重放。
- 管理器里两类驱动方式：
  - **JNI 直连**（`Natives.isSelinuxHideEnabled` 等 → `ksu.cc` 用 `KSU_FEATURE_*` 常量）；
  - **shell 走名字**（`SettingsRepositoryImpl` 里 `execKsud("feature set partition_guard_runtime 1")`）。

### 3.4 分区保护 `kernel/feature/partition_guard.c`（335 行）

- 挂在内核 syscall dispatcher 上，因此**只对已 root/已标记进程生效**；关闭时每个钩子只多一个分支。
- 共 **6 个钩子 / 三类路径**（README 已校正为同一说法）：`openat`、`openat2`（写方式打开 `/dev/block/**`）、
  `mount`（系统分区 remount rw）、`write`、`pwrite64`、`writev`（经由已打开块设备 fd 写入）。
  注册在 `hook/syscall_hook_manager.c:166`。
- 保护目标：`/`、`/system`、`/system_ext`、`/system_dlkm`、`/vendor`、`/vendor_dlkm`、`/product`、`/odm`、
  `my_product/my_stock/my_carrier/my_region/my_bigball/my_manifest`。
- 双层开关：静态扫描 `partition_guard`(5) + 运行时拦截 `partition_guard_runtime`(6)，**运行时默认关闭**（挂在 write 热路径上）。
- 未覆盖：`ioctl`（`BLKFLSBUF`）、`io_uring`、`memfd`/`O_DIRECT` 等非常规写路径（文档已声明）。

### 3.5 模块安装与 WebUI

- 安装：`ksud module install` → 校验 zip → `module.rs` 解包到 `/data/adb/modules/<id>` → 越狱模式下由 `module.rs:621` 的
  `late_load` 判定拦截真正写分区的部分，并给脚本注入 `KSU_LATE_LOAD=1`。
- WebUI：`webui.rs` 起 HTTP 服务（默认走 ksud 自己），前端 `assets/web/index.html` 由 `assets.rs` 嵌入二进制（`rust-embed`）。
  入口藏在「计算器」外壳里，暗码触发（见 README）。

### 3.6 内置 Shizuku（当前最活跃的开发区）

把官方 Shizuku 的 server 端整套内建进管理器，不依赖外部 Shizuku App。最近 24 个提交里绝大多数围绕它。

| 组成 | 位置 | 说明 |
|---|---|---|
| 服务端 | `rikka/shizuku/server`（16 文件）+ `util`/`ktx` | Binder 服务，`ShizukuService.main()` 由 `app_process` 拉起 |
| 客户端库 | `rikka/rish`（7 文件） | `Rish`/`RishHost`/`RishTerminal` + native PTY |
| starter | `moe/shizuku/starter`（3 文件） | `ServiceStarter` 负责拉起 user service 进程 |
| native | `cpp/rish/`（8 文件 630 行） | `librish.so`：PTY 终端与进程托管，**缺失即 `UnsatisfiedLinkError`** |
| native | `cpp/fpdrop/fpdrop.c`（99 行） | 降权助手：`setgroups`/`setgid`/`setuid` + `capset` 清空 capability |
| 管理器侧 | `shizuku/ShizukuServiceManager.kt` | 启动/停止、部署 fpdrop、日志读取、`getShellOnly`/`setShellOnly` |
| UI | `ui/screen/shizuku/ShizukuScreen.kt` + `ShizukuLogScreen.kt` | 应用授权面板 + 独立日志页（Miuix / Material3 双实现） |
| 授权界面 | `shizuku/ShizukuPermissionActivity.kt` + `themes.xml` 的 `Theme.KernelSU.Permission` | 半透明浮动窗口 + **直接渲染居中卡片**（不用 Dialog/Popup，见下） |

启动链路：`ShizukuServiceManager.start()` → 等 root → `ensureFpDrop()`（把 assets 里的 fpdrop 释放到 `/data/local/tmp`）
→ 用 `/system/bin/su` 启动 `app_process`，命令行带 `-Dshizuku.library.path` 与 `-Dshizuku.manager.packageName`
→ 多个 su 候选策略依次尝试（与 FolkPatch 对齐）→ server 起来后通过 `getContentProviderExternal("<pkg>.shizuku")`
把 binder 投给管理器。

其他要点：

- 随机包名（隐藏管理器）靠启动参数把**实际包名**传进 server，覆盖 `ServerConstants.MANAGER_APPLICATION_ID`
  （已由 `final` 改为可变；`REQUEST_PERMISSION_ACTION` 同步更新）。配套的后缀改写与自检见 4.7。
- 授权弹窗不能用 `AlertDialog` / `OverlayDialog`：它们基于 Popup，在半透明浮动窗口（`windowIsFloating=true`）下
  根视图尺寸为 0，表现为"屏幕变暗但什么都不显示"。现在是 `Box(fillMaxSize + 半透明遮罩) + Surface(居中卡片)`，
  点遮罩取消、点卡片不穿透。
- `shellOnly` 分权：每个应用可选「Root 权限」或「降级为 Shell(uid 2000)」；标记存在 `ShizukuConfig.shellOnly`，
  服务端只在调用方是管理器时才允许读写（binder transact 10002 / 10003）。
- `manifests` 已声明 `moe.shizuku.manager.permission.API_V23`（**不能**再声明同名 `<permission>`，会重复安装失败）。

---

## 四、已核实的不一致与风险

### 4.1 uapi 头：死副本已删，但仍是"两份手工同步 + 一份硬编码"

| 位置 | 状态 |
|---|---|
| `kernel/include/uapi/` | ✅ 权威来源；内核用 `-I$(KSU_KERNEL_DIR)/include`，ksud 的 `build.rs` 用 `-I../../kernel/include` |
| `manager/app/src/main/cpp/uapi/` | ⚠️ 管理器 JNI 用的**手工副本**，id 5/6 已补齐，但仍需人工同步 |
| `uapi/`（仓库根） | ✅ 已删除（提交 `06569fd5`），`487266d3` 把 ksud 的 bindgen include 路径一并改到 `kernel/include` |

剩下的隐患：feature id 现在有**三处**表达——`kernel/include/uapi/feature.h`（权威）、
`manager/.../cpp/uapi/feature.h`（副本）、`userspace/ksud/src/feature.rs` 的 `FeatureId` 枚举（硬编码 0–6）。
后两处没有任何自动校验来对着权威头文件核对，新增第 7 个 feature 时最容易漏。

**建议**：让 `cpp/uapi/` 由 `kernel/include/uapi/` 在 CMake 阶段拷贝（或直接把 include 目录指向内核目录）；
给 `feature.rs` 加一个 build.rs 断言（解析 `feature.h` 的枚举值比对）。

### 4.2 文档与代码漂移（已修）

- README 已从"四条路径"改为"6 个系统调用钩子，分三类路径"，表格补上 `writev`；
  `docs/partition-guard.md` 里那句自相矛盾的"收敛到四条"也改成了 6 个钩子（提交 `06569fd5`）。
- 仍建议：`docs/partition-guard.md` 自称 v3.4.0 归档、引用已下线的 Release 与旧 APK 哈希，这条没动。

### 4.3 组件转发类容易漏

Manifest 里写的是 `com.mngr.app.magica.MagicaService` / `BootCompletedReceiver` / `AppZygotePreload`，
实际逻辑在 `me.weishu.kernelsu.magica.*`。**新增任何被 Manifest / ZygotePreload 直接点名的组件，都必须同时加转发类**，
否则越狱触发链路会静默失效（`BootCompletedReceiver` 里已经有 `rootAvailable()` 短路）。

### 4.4 版本号公式：两份实现 + 一层构建期校验

`manager/build.gradle.kts` 的 `getVersionCode()` 与 `kernel/Kbuild` 的 `KSU_VERSION` 各写一份
`major*10000 + minor*1000 + patch*100 + commits_after_tag`。提交 `56031456` 只加了两条"必须同步"的注释，
**没有任何机制保证**——注释拦不住人。当前 `v3.5.5-diksu-28` → `versionCode = 35528`。

已补一层校验：管理器侧把权重提成 `versionMajorWeight / versionMinorWeight / versionPatchWeight`，
新增的 `:checkVersionFormula`（同样挂 `preBuild`）从 `kernel/Kbuild` 里抠出 `KSU_VERSION :=` 那行的 `* N` 权重并比对，
不一致就构建失败并指出要改哪两处。这仍然不是"单一实现"，但至少改错一侧时会在构建期炸出来，而不是等到装到机器上才发现版本对不上。

### 4.5 待核实项（需要真机验证，不要当成结论）

1. **`jni.cc:401` 的 `setuid(0)`**：App Zygote 预加载子进程是否真有 `CAP_SETUID`？若没有，
   这条 Magica 路径实际上早已失效，只是被 GhostLock 主路径掩盖。
2. **`useAppZygote` + `isolatedProcess` 的组合**在 Android 14+ 上的行为变化。
3. GhostLock 支持的 kernel 列表（`cpp/ghostlock/kernels/*`）与目标设备 `uname -r` 的覆盖关系——不匹配时利用体会直接拒绝。
4. **fpdrop 从 `/data/local/tmp` 执行**：ksu 域（`u:r:ksu:s0`）下 exec tmpfs 文件是否被 SELinux 放行；
   不行就只能退回"以当前身份执行"，分权功能静默失效。
5. **`librish.so` 能否被 `System.loadLibrary("rish")` 加载**：CMake 目标名 `rish` → `lib/<abi>/librish.so`，
   与 `-Dshizuku.library.path` 一致，静态看没问题，仍需装机确认。
6. **授权弹窗在 `windowIsFloating=true` 下的全屏渲染**：改成直接渲染后理论上 `fillMaxSize()` 会撑到窗口上限，
   但这是"浮动窗口 + 内容自适应尺寸"的经典坑，必须真机看一眼（尤其是横屏/小屏/深色模式）。

### 4.6 构建卫生

`git status` 里本地 9 个文件的改动**全部是权限位**（`100755 → 100644`：`gradlew`、`installer.sh`、`build-all.sh` 等）。
远端 `origin/main` 这 9 个文件仍都是 `100755`，所以这是 **`/sdcard` 的 FUSE 挂载不支持可执行位**造成的地产物，**不要提交**。

**fpdrop 已经处理干净**（本节三项改动）：

- `git rm --cached manager/app/src/main/assets/fpdrop`，二进制不再入库 —— `06569fd5` 加的那条 `.gitignore` 到这时才真正生效；
- CMake 产物改为写进 `build/generated/fpdrop-assets`（目录由 `-DKSU_FPDROP_ASSET_DIR` 从 Gradle 传入），**不再落进源码树**；
- `merge*Assets` 显式依赖同名变体的 `externalNativeBuild<variant>`，保证全新 clone（CI 就是全新 clone）打包前 fpdrop 一定已生成。
  不接这条线的话会**静默**打出不含 fpdrop 的包 —— 这是"把二进制移出源码树"最容易踩的坑。

顺带修掉一个隐藏 bug：以前 fpdrop 对三个 ABI 都构建、都往同一个 `assets/fpdrop` 覆盖，最终是哪个架构由构建完成顺序决定，
有可能把 riscv64 / x86_64 的 ELF 塞进 arm64 的安装包，运行时 exec 直接失败。现在只编 `arm64-v8a`；
x86_64/riscv64 上没有该文件，`ensureFpDrop()` 会 catch 后按"不支持分权"降级，符合原设计。

### 4.7 隐藏管理器 + Shizuku：已修，清单收敛为单一来源

之前的问题（`authorities="${applicationId}.shizuku"` 与 `action="${applicationId}.intent.action.REQUEST_PERMISSION"`
没在 `ManagerHider.PACKAGE_NAME_SUFFIXES` 里，覆盖包名后服务端拼的串与 Manifest 对不上）已在 `63abf487` 修复：
两个后缀都补进了清单，隐藏管理器时一并原位改写。

`3580919c` 又加了一道构建期自检 `checkManagerHiderSuffixes`（挂在 `preBuild` 上）：扫描 `AndroidManifest.xml`
里所有 `${applicationId}<suffix>`，若某个后缀不在清单中就**构建失败**并给出修复指引。方向是对的，但它自带一份
手抄的 `expectedSuffixes` 副本，等于把"一处清单"变成"两处要同步"——守卫自己引入了新的同步点。

已改为**直接解析 `ManagerHider.kt`**：正则取出 `PACKAGE_NAME_SUFFIXES = listOf(...)` 块里的字符串字面量当清单，
`build.gradle.kts` 不再保有副本；解析不到就构建失败并提示"正则要跟着改"，不会静默放过。
现在校验的是 `Manifest ⊆ ManagerHider.kt`，两端都是真实来源。

---

## 五、构建与发布

| 目标 | 命令 | 备注 |
|---|---|---|
| ksud | `cargo build --release`（或 `just build_ksud` → `cross build --target aarch64-linux-android`） | edition 2024；`libc` 打了 git patch；bindgen 走 `-I../../kernel/include` |
| 管理器 | `cd manager && ./gradlew :app:assembleRelease` | AGP 9.4.1 / Kotlin 2.4.20 / compileSdk 37 / minSdk 31 / Java 21；ABI arm64+x86_64+riscv64；CMake 另产 `librish.so`、`libghostlock.so`、`fpdrop`（arm64-only，写进 `build/generated/fpdrop-assets` 后打进 assets） |
| 内核模块 | `cd kernel && make`（或 `build-all.sh` 产 8 个 KMI） | 需 DDK / 内核树 |
| 一键 | `just build_manager`（会先把 ksud 拷进 `jniLibs`） | |
| 构建期自检 | `checkManagerHiderSuffixes` + `:checkVersionFormula`（`preBuild` 依赖） | Manifest 出现未登记后缀、或管理器/内核两侧 versionCode 权重不一致即失败 |

CI（`.github/workflows/`）：`build-manager`、`build-lkm`、`ddk-lkm`、`ksud`、`ksud-extra`、`ksuinit`、`release`、
外加 `clang-format` / `rustfmt` / `clippy` / `shellcheck` / `deploy-website`。
`release.yml` 默认**不自动发版**（`workflow_dispatch` 且勾 `auto_publish` 才建 Release）；
`build-manager.yml` 现在也有 `workflow_dispatch`，可以手动重跑。

---

## 六、改东西时的入口索引

| 想改什么 | 先看这里 |
|---|---|
| 首页状态/卡片 | `ui/screen/home/HomeMiuix.kt`、`HomeMaterial.kt`、`HomeMiuixStock.kt`；状态来自 `Natives.isLateLoadMode` |
| 越狱触发流程 | `ui/util/JailbreakExploit.kt` → `cpp/ghostlock/core/main.c` → `ksud late_load.rs` → `kernel/core/init.c` |
| 越狱时的内核行为 | `kernel/core/init.c`、`kernel/runtime/ksud_integration.c`、`kernel/supercall/dispatch.c` |
| 分区保护 | `kernel/feature/partition_guard.c` + `hook/syscall_hook_manager.c` + `SettingsRepositoryImpl`/`OtherFeaturesScreen` |
| 新增一个内核 feature | ① `kernel/include/uapi/feature.h` ② `kernel/policy/feature.c` ③ `ksud/src/feature.rs` ④ `manager/.../cpp/uapi/feature.h`（副本） |
| 新 syscall 钩子 | `kernel/hook/syscall_hook_manager.c` + `kernel/hook/arch/*/syscall_hook.c` |
| 授权 / 应用配置 | `kernel/policy/allowlist.c`、`app_profile.c`、`ui/screen/superuser/`、`ui/component/profile/` |
| WebUI 接口 | `userspace/ksud/src/webui.rs` + `assets/web/index.html` |
| 模块安装 | `userspace/ksud/src/module.rs`、`metamodule.rs`、`ui/screen/install/` |
| 设置项 | `data/repository/SettingsRepositoryImpl.kt`（SharedPreferences key + `ksud feature set`）+ `ui/screen/settings/*` |
| 随机包名 / 重打包 | `ui/util/ManagerHider.kt`（设备内原位改写，清单在 `PACKAGE_NAME_SUFFIXES`）+ `build.gradle.kts` 的 `checkManagerHiderSuffixes`、`LauncherRenamer.kt`、`CalculatorLauncher.kt`、`repack_apk.py`（离线换 ksud + 重签） |
| 内置 Shizuku 服务 | `shizuku/ShizukuServiceManager.kt`（启动/部署/日志）→ `rikka/shizuku/server/ShizukuService.java`（Binder）→ `moe/shizuku/starter/ServiceStarter.java`（user service） |
| Shizuku 授权 / 分权 | `ShizukuServiceManager.getShellOnly/setShellOnly` → `ShizukuConfigManager`；弹窗在 `shizuku/ShizukuPermissionActivity.kt` + `res/values/themes.xml` 的 `Theme.KernelSU.Permission` |
| Shizuku native | `cpp/CMakeLists.txt`（`rish` / `fpdrop` 目标）、`cpp/rish/*`、`cpp/fpdrop/fpdrop.c` |
| Shizuku 面板 / 日志页 | `ui/screen/shizuku/ShizukuScreen.kt`、`ShizukuLogScreen.kt`、`ui/navigation3/Routes.kt`、`ui/MainActivity.kt` |
| 版本号 | `manager/build.gradle.kts` 的 `getVersionCode()` 与 `kernel/Kbuild` 的 `KSU_VERSION`（两份，需同步） |

---

## 七、建议的下一步（按性价比）

**已在本地提交（未推送）：** 4.6 的 fpdrop 入库与写源码树、4.7 的"清单变三处"、4.4 的公式裸奔
（后者只加了校验，公式仍是两份实现）。改动集中在 `manager/build.gradle.kts`、`manager/app/build.gradle.kts`、
`manager/app/src/main/cpp/CMakeLists.txt`、`manager/app/.gitignore`，外加一次 `git rm --cached`。

剩下这些还没动：

1. **补 CI 的编译门禁**（最值得做）：最近 12 个提交里有 4 个是"提交即坏 → 再补一个 fix"（漏 import、Miuix API 名错、
   bindgen include、弹窗渲染），说明 PR 上只跑 fmt/clippy，没有 `assembleDebug`。给 `build-manager.yml` 加一次 debug 构建能省掉整轮往返。
2. **收敛 uapi**：`cpp/uapi/` 改成构建期从 `kernel/include/uapi/` 拷贝；`feature.rs` 加与 `feature.h` 的一致性断言。
3. **版本号彻底收敛**：现在有两份实现 + 一层构建期校验，理想是构建时算一次、传给内核侧。
4. **真机验证 4.5 的六条**，尤其是 fpdrop 在 ksu 域下的执行权限和授权弹窗的渲染；确认 Magica 路径是否还有存在价值，若已死则删除。
5. **补测试空白**：整个仓库没有单元测试，随机包名/Shizuku 这类"字符串拼装"逻辑本可以用 JVM 单测覆盖（`ManagerHider` 的替换、`ServerConstants.override` 的联动）。

---

## 附录：近期提交时间线（`da267e12` → `3580919c`，共 12 个）

| 提交 | 摘要 |
|---|---|
| `cb110214` | 修内置 Shizuku 起不来（补 `librish.so`、fpdrop 源码化、随机包名透传、shellOnly 分权）+ 面板重构、新增日志页 |
| `69b9456d` / `cb7f3446` | CI：触发构建；`build-manager` 加 `workflow_dispatch` |
| `5f0e2c5e` | 补 `Modifier.size` import（前一提交漏了） |
| `3849d0de` | 授权弹窗改用半透明主题 `Theme.KernelSU.Permission` |
| `1d9fbe62` | 弃用 `AlertDialog`/`OverlayDialog`（浮动窗口下 Popup 尺寸为 0），改为直接渲染居中卡片 |
| `63abf487` | 隐藏管理器补 `.shizuku` / `.intent.action.REQUEST_PERMISSION` 改写；管理器 uapi `feature.h` 同步 id 5/6 |
| `35d47684` | Miuix 无 `onSurfaceVariant`，改用 `onSurfaceVariantSummary`（编译修复） |
| `06569fd5` | 删根目录死副本 `uapi/`、`assets/fpdrop` 加 `.gitignore`、校正分区保护钩子数量文档 |
| `56031456` | 版本号公式两侧加"必须同步"注释 |
| `487266d3` | ksud bindgen include 路径改到 `kernel/include`（配合 `uapi/` 删除） |
| `3580919c` | 新增 `checkManagerHiderSuffixes` 构建期自检（挂 `preBuild`） |
