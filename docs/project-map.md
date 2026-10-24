# 项目地图与架构分析（Project Map）

> 目的：给后续开发的一份"从哪进去、改哪里、哪些别碰"的索引。
> 内容以 **2026-10-17 远端 `origin/main` = `3580919c`** 的代码实测为准；本地已 fast-forward 到该提交，
> 并在其上带了一轮本地提交（构建卫生 4.4 / 4.6 / 4.7、uapi 收敛 4.1，均未推送，见第七节）。
> 第四节 4.5 的真机核实项已全部通过。
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

- 包名策略：`namespace = "me.diksu.ultra"`，`applicationId` 由 `KSU_PACKAGE_NAME` 决定（默认 `me.diksu.ultra`，PR 构建 `me.diksu.ultra.pr`）；
  Manifest 里所有组件都指向 `com.mngr.app.*` 的**转发类**（`Entry`、`ui/MainActivity`、`ui/WebUIActivity`、
  `magica/*`、`service/DownloadService`），真正的实现在 `me.diksu.ultra.*` 里被继承。
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

**备用路径二：DirtyFrag 漏洞（3.5.7 起内置）**

CVE-2026-43284 是另一条完全独立的提权路，和 ghostlock 不共用任何代码。它内置的方式和 ghostlock 一样：
源码在 `cpp/dirtyfrag/`，CMake 编成 `libdirtyfrag.so`（8 个 KMI 的 `dfroot-*.ko` 和 `splicehelper` 用
`.incbin` 内嵌，纯原生、无 Java 依赖）与 `libdirtyfrag_bootstrap.so`，两者都落在 `lib/arm64-v8a/`，
运行时从 `nativeLibraryDir` 执行。

链路：页缓存写（IPsec ESP + `splice`，`exp.c`）→ 改 `libc++.so` 让 `crash_dump64` 执行 shellcode →
`insmod dfroot.ko` → 模块关 SELinux enforcing、补 DEFEX、`call_usermodehelper` 拉起 bootstrap →
`ksud late-load --package-name <本 App>`。注意**顺序是「先有 root 才能 insmod」**，模块是成功后的载荷，
不是前提；模块 init 末尾 `return -ENODEV` 让内核立即卸载它，所以模块本身不常驻。

移植时只改了两处上游写死的东西：

1. `bootstrap` 的暂存目录：[`bootstrap.c`](../manager/app/src/main/cpp/dirtyfrag/bootstrap.c) 从 `argv[0]` 推导；
2. 模块里的 `bootstrap` 路径：[`lkm/dfroot.c`](../manager/app/src/main/cpp/dirtyfrag/lkm/dfroot.c) 改成 96 字节可写槽位
   （初值哨兵 `/data/user_de/0/DFROOT_STAGE/bootstrap`），`exp.c` 用 `--stage-dir` 在写入 page cache 前整块改写。

模块由 `.github/workflows/ddk-lkm.yml` 和 `kernelsu.ko` 在同一个 DDK 容器里、同一批 KMI 编出来，
并进现有 `aarch64-<kmi>-lkm` 产物，不额外增加 artifact；构建时会校验 ko 里带着哨兵。

失败出口统一走 `fail_close()`：写回 `/sys/fs/selinux/enforce=1` 并 `unlink("/dev/df")`，用户可以直接重试；
成功路径留给 ksud 切回 enforcing。真机验证（Xiaomi 24129RT7CC / Android 16 / 6.6.77，安全补丁 2026-06-01）通过。

和 ghostlock 的分工：ghostlock 按内核构建号定制、链条短，命中它的目标表时更稳；DirtyFrag 按 KMI 覆盖更广，
链更长、更容易被厂商 backport 修掉，作为第二条路保留。

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

### 4.1 uapi 头：只剩一份权威定义（副本已彻底删除）

| 位置 | 状态 |
|---|---|
| `kernel/include/uapi/` | ✅ **唯一的头文件来源**：内核用 `-I$(KSU_KERNEL_DIR)/include`，ksud 的 `build.rs` 用 `-I../../kernel/include`，管理器 CMake 用 `-I../../../../../kernel/include` |
| `manager/app/src/main/cpp/uapi/` | ✅ 已删除（6 个文件）。曾是手工副本，`63abf487` 就是人工补 id 5/6 的产物 |
| `uapi/`（仓库根） | ✅ 已删除（提交 `06569fd5`），`487266d3` 把 ksud 的 bindgen include 路径一并改到 `kernel/include` |

删副本前实测过：6 个头文件里 5 个与内核侧**逐字节相同**，`supercall.h` 只差一个行尾空行。
所以"指向内核目录"是零语义变更的替换，不是重构。

**仍存在的唯一重复：`userspace/ksud/src/feature.rs` 的 `FeatureId` 枚举（0–6 与 `feature.h` 手工对齐）。**
评估后**不打算自动化**，理由：

- 失效是"吵闹"的：内核加了 feature 而 ksud 没加 → `feature set <name>` 直接报未知名称；反之内核返回错误。
- 唯一静默失效的场景是**给已有 feature 重新编号**，而这本身就是破坏性变更（id 也被持久化进
  `/data/adb/ksu/.feature_config`，重编号会把存量配置全部指错），属于不该悄悄发生的事。
- 自动化的代价不小：要在 Gradle/Rust 里解析 `feature.h` 的宏名与 Rust 的驼峰名并做映射，
  等于为"跨语言改名映射"再引入一处需要维护的脆弱面——和刚删掉的那类同步点同构。

**何时该回头做**：一旦 ksud 与管理器不再同仓同版本发布，或开始按 feature 单独发补丁。

### 4.2 文档与代码漂移（已修）

- README 已从"四条路径"改为"6 个系统调用钩子，分三类路径"，表格补上 `writev`；
  `docs/partition-guard.md` 里那句自相矛盾的"收敛到四条"也改成了 6 个钩子（提交 `06569fd5`）。
- 仍建议：`docs/partition-guard.md` 自称 v3.4.0 归档、引用已下线的 Release 与旧 APK 哈希，这条没动。

### 4.3 组件转发类容易漏

Manifest 里写的是 `com.mngr.app.magica.MagicaService` / `BootCompletedReceiver` / `AppZygotePreload`，
实际逻辑在 `me.diksu.ultra.magica.*`。**新增任何被 Manifest / ZygotePreload 直接点名的组件，都必须同时加转发类**，
否则越狱触发链路会静默失效（`BootCompletedReceiver` 里已经有 `rootAvailable()` 短路）。

### 4.4 版本号公式：两份实现 + 一层构建期校验

`manager/build.gradle.kts` 的 `getVersionCode()` 与 `kernel/Kbuild` 的 `KSU_VERSION` 各写一份
`major*10000 + minor*1000 + patch*100 + commits_after_tag`。提交 `56031456` 只加了两条"必须同步"的注释，
**没有任何机制保证**——注释拦不住人。当前 `v3.5.5-diksu-28` → `versionCode = 35528`。

已补一层校验：管理器侧把权重提成 `versionMajorWeight / versionMinorWeight / versionPatchWeight`，
新增的 `:checkVersionFormula`（同样挂 `preBuild`）从 `kernel/Kbuild` 里抠出 `KSU_VERSION :=` 那行的 `* N` 权重并比对，
不一致就构建失败并指出要改哪两处。这仍然不是"单一实现"，但至少改错一侧时会在构建期炸出来，而不是等到装到机器上才发现版本对不上。

### 4.5 曾经的重点怀疑项：已在真机核实通过

这些是当初只看代码无法定论、必须上机才能确认的点。**2026-10-17 真机核实：全部正常，无需再查。**
保留清单是因为它们仍然是"改这块时要小心什么"的索引：

| # | 当初的怀疑 | 结论 |
|---|---|---|
| 1 | `jni.cc:401` 的 `setuid(0)`：App Zygote 子进程是否真有 `CAP_SETUID`（没有则 Magica 路径早就失效） | ✅ 可用 |
| 2 | `useAppZygote` + `isolatedProcess` 在 Android 14+ 上的行为变化 | ✅ 正常 |
| 3 | GhostLock 支持的 kernel 列表与目标设备 `uname -r` 的覆盖关系（不匹配会直接拒绝） | ✅ 覆盖 |
| 4 | fpdrop 从 `/data/local/tmp` 在 `u:r:ksu:s0` 域下能否 exec（不行则分权静默失效） | ✅ 可执行 |
| 5 | `librish.so` 能否被 `System.loadLibrary("rish")` 加载 | ✅ 加载正常 |
| 6 | 授权弹窗在 `windowIsFloating=true` 下 `fillMaxSize()` 是否真能撑满 | ✅ 渲染正常 |

注意 #1 的结论意味着 Magica 备用路径**是活的**，不能当死代码删；
#4 的结论意味着 Shizuku 分权（shellOnly）实际可用。

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

### 4.8 美化版主页两个"改过一次又改回去"的坑（3.5.7 前修掉）

**① `overScrollVertical()` 会让列表偶发滑不动。** Miuix 的 overscroll node 只在 placement pass 里刷新滚动范围，
列表一旦被 remeasure（配置变化、首帧）就留着旧范围并拒绝拖拽，表现是"有时候完全滑不动"。`ca087c71` 曾因此
去掉它，随后 `3cd07986` 又整份回退，问题回归。现在 `HomeMiuix.kt` 的 `LazyColumn` **刻意不挂**这个修饰符。
代价只是失去下拉回弹，`scrollEndHaptic` 不受影响。`HomeMiuixStock.kt` 目前仍挂着，若要一并修照着去掉即可。

**② 磨砂面的纵向余量才是坑；不要把磨砂面挪到 Card 外面。** `b50a5cf1` 给玻璃裁切加了 96px 纵向余量、
整体下移 48，结果按压（`PressFeedbackType.Tilt`）时边沿反而露出白条/黑条。基线是「裁切高度 = 面板高度、
纵向不做偏移」，退回去就正常；横向的 `+96` 与 `-48` 是基线本来就有的，不要动。三处玻璃（状态卡、守卫卡、
`component/miuix/WarningCard.kt`）已统一回基线几何。

另一条岔路别走：把磨砂面挪到 Card **外面**当静态底板，确实不会露边，但玻璃就不再跟着卡片一起倾斜，
按压手感明显变差（`5a83c9a0` 试过，已回退）。

**③ 设置页三套皮肤共用一套分组。** `SettingsMiuix.kt`、`SettingsMiuixStock.kt`、`SettingsMaterial.kt` 现在
都是：通用 / 外观 / 功能 / 隐私 / 关于 / 危险操作（卸载单独成组压最末）。新增条目要三处同改；
「隐藏管理器」入口目前只有美化版有，另外两套没有。

---

## 五、构建与发布

| 目标 | 命令 | 备注 |
|---|---|---|
| ksud | `cargo build --release`（或 `just build_ksud` → `cross build --target aarch64-linux-android`） | edition 2024；`libc` 打了 git patch；bindgen 走 `-I../../kernel/include` |
| 管理器 | `cd manager && ./gradlew :app:assembleRelease` | AGP 9.4.1 / Kotlin 2.4.20 / compileSdk 37 / minSdk 31 / Java 21；ABI arm64+x86_64+riscv64；CMake 另产 `librish.so`、`libghostlock.so`、`fpdrop`（arm64-only，写进 `build/generated/fpdrop-assets` 后打进 assets） |
| 内核模块 | `cd kernel && make`（或 `build-all.sh` 产 8 个 KMI） | 需 DDK / 内核树 |
| 一键 | `just build_manager`（会先把 ksud 拷进 `jniLibs`） | |
| 构建期自检 | `checkManagerHiderSuffixes` + `:checkVersionFormula`（`preBuild` 依赖） | Manifest 出现未登记后缀、或管理器/内核两侧 versionCode 权重不一致即失败 |
| 产物自检 | CI 在 `assembleRelease` 之后 `unzip -l` 核对 `assets/fpdrop` | `merge*Assets → native` 那条依赖匹配不到任务名时会静默"构建绿、包里没文件"，只有这一层能挡住 |

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
| 新增一个内核 feature | ① `kernel/include/uapi/feature.h`（唯一定义处，ksud 与管理器的 C++ 都直接 include 它）② `kernel/policy/feature.c` ③ `ksud/src/feature.rs` 的 `FeatureId`（**要手工对齐编号**，见 4.1） |
| 新 syscall 钩子 | `kernel/hook/syscall_hook_manager.c` + `kernel/hook/arch/*/syscall_hook.c` |
| 授权 / 应用配置 | `kernel/policy/allowlist.c`、`app_profile.c`、`ui/screen/superuser/`、`ui/component/profile/` |
| WebUI 接口 | `userspace/ksud/src/webui.rs` + `assets/web/index.html` |
| 模块安装 | `userspace/ksud/src/module.rs`、`metamodule.rs`、`ui/screen/install/` |
| 设置项 | `data/repository/SettingsRepositoryImpl.kt`（SharedPreferences key + `ksud feature set`）+ `ui/screen/settings/*`；三套皮肤的分组顺序要保持一致（见 4.8③） |
| 随机包名 / 重打包 | `ui/util/ManagerHider.kt`（设备内原位改写，清单在 `PACKAGE_NAME_SUFFIXES`）+ `build.gradle.kts` 的 `checkManagerHiderSuffixes`、`LauncherRenamer.kt`、`CalculatorLauncher.kt`、`repack_apk.py`（离线换 ksud + 重签） |
| 内置 Shizuku 服务 | `shizuku/ShizukuServiceManager.kt`（启动/部署/日志）→ `rikka/shizuku/server/ShizukuService.java`（Binder）→ `moe/shizuku/starter/ServiceStarter.java`（user service） |
| Shizuku 授权 / 分权 | `ShizukuServiceManager.getShellOnly/setShellOnly` → `ShizukuConfigManager`；弹窗在 `shizuku/ShizukuPermissionActivity.kt` + `res/values/themes.xml` 的 `Theme.KernelSU.Permission` |
| Shizuku native | `cpp/CMakeLists.txt`（`rish` / `fpdrop` 目标）、`cpp/rish/*`、`cpp/fpdrop/fpdrop.c` |
| Shizuku 面板 / 日志页 | `ui/screen/shizuku/ShizukuScreen.kt`、`ShizukuLogScreen.kt`、`ui/navigation3/Routes.kt`、`ui/MainActivity.kt` |
| 版本号 | `manager/build.gradle.kts` 的 `getVersionCode()` 与 `kernel/Kbuild` 的 `KSU_VERSION`（两份，需同步） |

---

## 七、下一步：做过什么、哪些**故意不做**

### 已推送、CI 已验证

- **4.6** fpdrop 不再入库、不再写源码树；顺带修掉"多 ABI 争抢同一个 assets 文件"的架构隐患
- **4.7** 后缀自检改为解析 `ManagerHider.kt`，清单收敛为单一来源
- **4.4** 新增 `:checkVersionFormula`，管理器与内核的 versionCode 权重分叉时构建失败
- **4.1** 删除 `manager/app/src/main/cpp/uapi/` 整个副本，管理器 C++ 直接吃 `kernel/include/uapi/`

改动文件：`manager/build.gradle.kts`、`manager/app/build.gradle.kts`、`manager/app/src/main/cpp/CMakeLists.txt`、
`manager/app/.gitignore`，外加 `git rm --cached`（fpdrop）与 `git rm -r`（uapi 6 个文件）。
这些已随 `717233b9` / `0da567bf` 推到 main，CI 第 113 次构建通过。

### 3.5.7 前的 UI 修复（本轮）

- **4.8①** 美化版主页 `LazyColumn` 去掉 `overScrollVertical()` —— 修"偶尔完全滑不动"
- **4.8②** 三处玻璃回退到基线的裁切几何（高度=面板高度、纵向不偏移）—— 修按压时边沿露白
- **4.8③** 三套皮肤设置页统一分组标题，卸载单独成组压到最末
- **五** CI 增加产物断言：`assembleRelease` 后核对 APK 里确有 `assets/fpdrop`

### 故意不做（附判断依据）

| 事项 | 结论 | 依据 |
|---|---|---|
| **CI 加 `assembleDebug` 门禁** | 当前**不做** | 开发模式是**直接 push 到 main**，没有 PR 流程，`pull_request` 触发器根本不触发；挂在 `push` 上只能"事后报红"，省不掉往返。等切到 PR 流程再补——那时只需在 `build-manager.yml` 加一个 `pull_request` 触发的 debug 构建 job |
| **`feature.rs` 与 `feature.h` 的一致性断言** | 当前**不做** | 失效基本是"吵闹"的（未知 feature 名直接报错）；唯一静默场景是给已有 feature 重新编号，而那会同时破坏已持久化的 `.feature_config`，属于不该悄悄发生的事。要自动化就得做跨语言名字映射，等于再引入一处脆弱同步点（详见 4.1） |
| **版本号公式彻底收敛成一处** | 低优先 | 已有构建期校验兜底（改错一侧会炸），再收敛收益有限 |

### 真正还剩的

1. **补测试空白**：整个仓库没有单元测试。`ManagerHider` 的字符串替换、`ServerConstants.override` 的联动这类纯逻辑
   本可以用 JVM 单测覆盖，而且不需要真机——属于投入小、长期收益明确的项。
2. 4.5 的六条真机项**已全部通过**，不再需要复查。

### 本轮的端到端验证（已完成）

`build-manager` 的触发条件是 **push 到 main 且路径命中 `manager/**`**，所以这批改动一推上去就会跑
`./gradlew clean assembleRelease`。CI 第 113 次构建**通过**，并已确认产物 APK 内含 `assets/fpdrop`。这一次构建同时证明了：

| 之前的"只能静态推断" | 结论 |
|---|---|
| 删除 `cpp/uapi/` 后，`uapi/ksu.h` 及内部 `"uapi/..."` 链能在 NDK 下解析 | ✅ 编译通过 |
| `-DKSU_FPDROP_ASSET_DIR` 传参 + CMake `if (ANDROID_ABI ...)` 块语法 | ✅ native 构建通过 |
| `merge*Assets → externalNativeBuild<变体>` 的 `dependsOn` 匹配到了真实任务名，且无任务环 | ✅ clean 构建下 fpdrop 按时生成并被打进包 |
| `checkVersionFormula` 在 CI 环境能读到 `../kernel/Kbuild` | ✅ preBuild 通过 |
| `checkManagerHiderSuffixes` 的新解析逻辑在 CI 上可用 | ✅ preBuild 通过 |

也就是说，"构建绿了但包里没 fpdrop"这个唯一残留风险已经被实际产物排除，不需要再人工盯。

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

**本地在此之上的提交（未推送）**

| 提交 | 摘要 |
|---|---|
| `717233b9` | build：fpdrop 移出源码树（含只编 arm64-v8a）、ManagerHider 自检改用单一清单、新增 `:checkVersionFormula` |
| `8329f601` | docs：新增本文 |
| `0da567bf` | build：删除 `manager/app/src/main/cpp/uapi/` 副本，管理器 C++ 改为直接 include `kernel/include/uapi/` |
