# 交接文档 · Diksu Ultra 4.1

> **写给下一个接手的人（或 Agent）**：这份文档记录"现在是什么状态、为什么是这样、下一步该做什么"。
> 基线（`wuhudiao/DikSU`）已停更，本分支自 4.0 起独立演进，**不必再为兼容基线而保留旧路径**。
>
> 最后更新：2026-10-25 · 4.2 开发轮进行中（**未发版、未跑 CI**，详见第十一节）
>
> 上一个发布基线：tag `v4.1.0-ultra` · versionCode `41000`
>
> 要直接交给下一个 Agent，用 [`docs/handoff-prompt.md`](handoff-prompt.md) 里那段「提示词正文」。

---

## 一、一眼看懂

| 项 | 值 |
|---|---|
| 项目名 | **Diksu Ultra**（4.0 起，之前叫 DikSU） |
| 定位 | 不解锁 Bootloader、不刷 boot/system，靠漏洞把内核模块挂进正在运行的系统 |
| 管理器包名 | `me.diksu.ultra`（PR 构建 `me.diksu.ultra.pr`） |
| 仓库 | `45072yy980/KernelSU`，`origin/main` |
| 基线 | `wuhudiao/DikSU`（已停更，仅作历史参考） |
| 上游 | `tiann/KernelSU`（仍会同步，但允许偏离） |
| 当前版本 | `v4.1.0-ultra` / versionCode `41000` |
| 发布页 | https://github.com/45072yy980/KernelSU/releases |

版本号公式（管理器与内核**必须一致**）：

```
versionCode = major × 10000 + minor × 1000 + patch × 100 + tag 之后的提交数（--first-parent）
```

- 管理器：`manager/build.gradle.kts` 的 `getVersionCode()`
- 内核：`kernel/Kbuild` 的 `KSU_VERSION`
- 两侧有构建期一致性校验（`checkVersionFormula`），改一边不改另一边会直接构建失败。
- **发版必须打 tag**：`vX.Y.Z-ultra`，且 tag 打在最终 HEAD 上（这样提交数为 0，versionCode 干净）。

---

## 二、代码地图（改之前先定位）

```
manager/                    Android 管理器（Kotlin/Compose，三套皮肤）
  app/src/main/java/me/diksu/ultra/
    ui/MainActivity.kt              导航壳、Pager、壁纸/玻璃层、路由注册
    ui/screen/home/                 首页（三皮肤：HomeMiuix / HomeMiuixStock / HomeMaterial）
    ui/screen/settings/             设置（主设置、基础功能、其他功能、KeyMint、自动提权）
    ui/screen/shizuku/              内置 Shizuku 面板 + 日志页
    ui/screen/susfs/                SuSFS 面板（SusfsScreen.kt = miuix，SusfsMaterial.kt = Material）
    ui/component/bottombar/         底栏/导航栏（含 4.1 新增 DisabledNavigation.kt）
    ui/component/dialog/            AppAlertDialog（4.0 起的皮肤一致弹窗）+ 既有 Confirm/Loading
    ui/capability/NavGate.kt        4.1 新增：导航可用性判定
    jailbreak/                      4.1 新增：AutoEscalation + AutoEscalationBootReceiver
    shizuku/                        ShizukuServiceManager、ShizukuAutostart、ShizukuBootReceiver
    susfs/                          SusfsRepository（规则持久化）、SusfsCommands、SusfsBootReceiver
    magica/                         基线的 app-zygote 注入路径（**4.1 起不再被自动提权使用**）
userspace/ksud/             Rust 用户态（root 侧真正干活的部分）
  src/webui.rs                    网页端 HTTP 服务 + API 路由
  assets/web/index.html           网页端全部前端（单文件，约 4700 行）
  src/module.rs                   模块列表/安装（含 4.0 新增的目录大小计算）
kernel/                     内核模块（C），Kbuild 里固定 KSU_PACKAGE_NAME
.github/workflows/          CI（build-manager / release / clippy / rustfmt / shellcheck）
```

### 三套皮肤的约定

| 皮肤 | 枚举 | 说明 |
|---|---|---|
| Miuix 美化版 | `UiMode.Miuix` | 默认；玻璃/壁纸/状态栏隐藏等只在皮肤里做 |
| Miuix 原版 | `UiMode.MiuixStock` | 同组件库，无玻璃 |
| Material 3 | `UiMode.Material` | **必须独立实现**，不允许再写"请切到 miuix"的占位 |

判断方式：`LocalUiMode.current.isMiuixFamily`（前两者为 true）。

---

## 三、4.0 / 4.1 做了什么（含为什么）

### 4.0（`v4.0.0-ultra`）

1. **更名 + 包名迁移**：`me.weishu.kernelsu` → `me.diksu.ultra`，317 个文件、AIDL、JNI 符号
   （`Java_me_weishu_kernelsu_*` → `Java_me_diksu_ultra_*`）、内核 `KSU_PACKAGE_NAME`、ksud 默认包名、
   ghostlock 探测路径全部同步。**包名变了 = 新应用，升级要先卸载旧管理器**（`/data/adb` 不受影响）。
2. **Shizuku**：面板重写（状态/自启/授权三段式 + 搜索筛选 + Material 实现）；
   **修掉开机自启根因**——自启原本挂在 `SusfsBootReceiver` 末尾，而它在 SUSFS 未加载时会提前
   `return`，导致自启几乎从不执行；现在独立成 `ShizukuBootReceiver`。
3. **SuSFS**：补齐 Material 3 实现（之前只有一句"请切到 miuix"）；新增规则清单（可删除，
   但明确写出"内核侧没有 remove 命令，删除只影响下次开机重放"）。
4. **弹窗统一**：`AppAlertDialog`，按皮肤渲染 miuix `WindowDialog` 或 Material `AlertDialog`。
5. **自动提权并越狱**：取代原来只会拉起空 `MagicaService` 的"自动越狱"。

### 4.1（`v4.1.0-ultra`）

1. **自动提权改用开机自启权限**：开关不再打开 magica 的 `BootCompletedReceiver`（那条路径会在
   **没有 root** 的情况下由 app zygote 直接 exec `ksud late-load`，等于往内核里硬注入）。
   现在只写偏好，由 `AutoEscalationBootReceiver`（`BOOT_COMPLETED`）在应用进程里跑随包提权链。
   新增开机守卫：`boot_id` 判重（每次开机只试一次）、连续失败 3 次暂停、安全模式跳过、
   手动"立即尝试"清零计数。
2. **导航入口置灰**：五个导航组件原本 `if (!Natives.isFullFeatured()) return`，现在入口常驻、
   整体置灰，点一下给出原因（安全模式 / 尚未提权 / 内核模块未加载）与"前往首页"恢复入口。
3. **Material 3 清理**：主设置页删掉已迁到「基础功能设置」的整块开关（su compat、sulog、
   ADB root、内核卸载、SELinux 隐藏、Shell su 仅 ADB、默认卸载模块、Web 调试、软重启），
   以及已废弃的"自动越狱"和"应用配置模板"。
4. **网页端**：新增「内核特性」区块（直接读写内核 supercall，8 个特性开关，不支持的置灰）
   与「工具」（重载模块、软重启）；后端 `/api/features` 补齐清单，新增 `/api/tools/reload-modules`。

---

## 四、已知问题与技术债（按优先级）

### P0 — 必须尽快处理

1. **PAT 已泄露**：本轮开发用的 GitHub token 在对话里出现过明文，**必须去 GitHub 设置里吊销并重新签发**。
   仓库里没有写入 token（推送用的是临时 askpass 文件 + `/etc/hosts` 固定 IP，用完即删），但 token 本身要换。
2. **网页端 SuSFS / Shizuku 面板缺失**：4.1 只补了内核特性开关，这两块仍是空白（见第五节）。

### P1 — 影响体验或正确性

3. **导航置灰只做了"整条置灰"**：`MainActivity.kt:457-470` 仍有
   `userScrollEnabled = isFullFeatured && !disablePagerSwipe` 与
   `badgeEnabled = enableNavigationBadge && isFullFeatured` —— 非 full-featured 时**翻页手势与徽标仍被关掉**。
   下一步应改成"仅不可用页面不可达"，而不是整条导航降级。
4. **自动提权未真机验证**：开机提权链路（ghostlock → DirtyFrag 回退）只过了编译与产物检查，
   需要在真机上验证：完整重启 → 无 root → 开机后自动提权成功/失败的行为、耗时、以及失败后是否
   干净退出（SELinux 是否恢复 enforcing）。
5. **Shizuku 自启未真机验证**：同上，需要确认 `ShizukuBootReceiver` 在真实开机流程里能拉起 server。
6. **SuSFS 规则删除是"半生效"**：内核没有 remove 命令，删除只影响下次开机重放；UI 已写明，
   但用户仍可能误解。若要真正即时生效，需要内核侧新增 remove 子命令（工作量大）。
7. **Material 3 只做了设置项清理**，没有做完整功能巡检：其他 Material 页面（模块、超级用户、
   文件、WebUI）仍可能有类似"引用了已删除功能"的问题，需要逐页过一遍。

### P1（补充）— 仓库卫生

7.1 **4.1 误提交过两个 10MB 自用 APK**：`.gitignore` 当时只写了 `/DikSU_*.apk`，而 4.0 改名后文件叫
   `DiksuUltra_*.apk`，匹配不到。已在 `aab99614` 移出索引并修正规则（`/Diksu*.apk`），
   但**历史里仍留着这两个 blob**（约 20MB）。要彻底清掉：
   `git filter-branch -f --index-filter 'git rm -r --cached --ignore-unmatch "DiksuUltra_*.apk"' <tag>..HEAD`
   → `git reflog expire --expire=now --all && git gc --prune=now`
   → force push 分支与 tag（tag 要重打，版本号会变，发版前想清楚）。

### P2 — 打磨

8. `docs/project-map.md` 是 3.5.7 时代的文档，部分路径/结论已过时（本文件为准）。
9. `docs/refactor-plan.md` 仍是"待办"状态，未开始。
10. 网页端视觉仍是基线风格，没有做过整体设计更新（4.1 只加了两个区块）。

---

## 五、下一步该做什么（建议顺序）

> **团队任务板已建好**：`task-1` 网页端 SuSFS 面板、`task-2` 导航置灰做细、
> `task-3` Material 3 全量巡检、`task-4` 真机验证、`task-5` 网页端 Shizuku 管理、
> `task-6` 网页端视觉重构。接手时先 `team_task_list` 看状态，认领后按任务描述里的验收标准做。

### 第 1 步：网页端 SuSFS 面板（性价比最高）

- **后端**：`userspace/ksud/src/webui.rs` 新增
  - `GET /api/susfs/status` → 复用 `crate::susfs`（ksud 已有 `load-status` / `read-proc` 等子命令），
    返回 `{loaded, version, variant, features[], log, avc, hide_mnts, rules:{paths,loops,maps,kstats,redirects}}`
  - `POST /api/susfs/toggle` → `{key, value}`，调 `enable-log` / `set-avc-log-spoofing` / `set-hide-sus-mnts`
  - `POST /api/susfs/add` / `POST /api/susfs/remove` → 对应 `add-sus-path` 等子命令
- **前端**：`userspace/ksud/assets/web/index.html` 新增「SuSFS」区块：状态卡 + 三个开关 + 规则列表（增删）。
- **验收**：网页端能看到与 App 一致的状态；开关能真实改变内核行为（`/proc/susfs_*` 可见变化）。

### 第 2 步：网页端内置 Shizuku 管理

- 后端需要能查/起停 Shizuku server：`pidof shizuku_server` 判活；启动沿用
  `ShizukuServiceManager` 里的 `app_process` 命令（`CLASSPATH=<apk> -Dshizuku.manager.packageName=<pkg>`）。
- 前端：状态 + 启动/停止 + 授权列表（`/data/user_de/0/com.android.shell/shizuku.json` 可读）。
- **注意**：授权写入要调 Shizuku binder，ksud 侧没有 binder 通道，**第一版只做只读 + 启停**，
  授权仍回 App 里改。

### 第 3 步：导航置灰做细

- 把 `NavGate` 从"整条可用/不可用"扩展成"每个入口一个状态"：
  `Home/Settings` 永远可用；`SuperUser/Module` 需要 full-featured。
- 五个导航组件只对不可用的**那个条目**置灰，其余正常点击；`MainActivity` 的
  `userScrollEnabled` / `badgeEnabled` 去掉 `isFullFeatured` 条件。

### 第 4 步：网页端整体视觉重构

- 现状：单文件 4700 行、`--glass-*` 一套变量、底部五个 tab。目标：与 App 的 Miuix 美化版观感对齐
  （更大的圆角、分组的 section、状态卡置顶、开关行统一）。
- 建议拆成 `index.html` + `app.css` + `app.js` 三个资产（`include_str!` 各自引入），否则单文件会继续膨胀。
- **风险**：网页端是纯手写 JS，改动后必须逐页点一遍（首页/超级用户/模块/设置/文件/keyMint）。

### 第 5 步：Material 3 全量巡检

- 逐页检查 Material 皮肤：模块页、超级用户页、WebUI、文件管理，确认没有引用已删除的功能、
  没有"点进去是空白/崩溃"的入口。
- 建议做法：在真机上把 `UiMode` 切到 Material，把每个入口点一遍，记录问题再改。

---

## 六、开发约定（务必遵守）

1. **三套皮肤都要实现**：不允许再出现"Material 只有一句提示"的实现。新增页面至少提供
   `XxxMiuix`（美化版 + 原版共用）与 `XxxMaterial` 两个实现。
2. **字符串走资源**：`values/strings.xml` + `values-zh-rCN/strings.xml`。本分支历史上有部分新面板
   直接写中文（Shizuku、自动提权），属于妥协，新代码请用资源。
3. **`stringResource` 不能写在 `onClick` 之类的非 @Composable lambda 里**——编译会报
   "Composable invocations can only happen from the context of a @Composable function"，要提前取到局部变量。
4. **Miuix 组件白名单**（项目里已验证可用）：`Card`、`SmallTitle`、`Text`、`Icon`、`TextButton`、
   `TextField`、`Switch`、`SwitchPreference`、`ArrowPreference`、`CheckboxPreference`、
   `OverlayDialog`、`OverlayDropdownPreference`、`InfiniteProgressIndicator`、`Scaffold`、`TopAppBar`。
   图标：`MiuixIcons.Back/Ok/Close/Search/...`，其余用 Material `Icons.Filled.*` 配 `tint`。
5. **提交信息**：中文、`type(scope): 摘要`，正文写清"为什么"（这个仓库的历史提交都是这个风格）。
   提交作者用 `git -c user.name="dsh-agent" -c user.email="dsh-agent@localhost"`。
6. **不要提交构建产物**：根目录 `DikSU_*.apk` / `DiksuUltra_*.apk` 已在 `.gitignore` 里，
   自用包放 `/sdcard/diksu/` 与 `/sdcard/Download/diksu_apks/`。
7. **版本号只在打 tag 时变**：日常提交会让 versionCode 递增（提交数计入），发版前打 tag 归零。

---

## 七、构建与发布

### 首选方式：用 `dsha-diksu-git` 插件

上一轮把这套动作做成了 DSH 插件（`/root/dsha-diksu-git`，已接进 `web` profile）：

| 工具 | 作用 |
|---|---|
| `git_status` | 分支/HEAD/tag/versionCode/改动/APK 残留/领先落后 |
| `git_commit` | 固定作者提交；**拒绝新增 APK**（删除放行） |
| `git_push` | 临时 askpass（token 只从环境变量读）+ 可选 hosts 钉 IP + 重试 + ls-remote 校验 |
| `git_tag` | 打 `vX.Y.Z-ultra` 并算出 versionCode |
| `ci_dispatch` / `ci_watch` | 触发 CI 并等到结束，回传失败 job/步骤 |
| `release_publish` | 从 Release run 挑 28 个资产建 release 并发布 |

**token 只走环境变量**：`export DSH_GIT_TOKEN=<PAT>`（其次 `GITHUB_TOKEN` / `GH_TOKEN`）。
插件用临时 `GIT_ASKPASS` 脚本（内容只有 `echo "$DSH_GIT_TOKEN"`）+ 子进程环境传递，
磁盘上没有明文，用完即删；所有输出会把 token 替换成 `***`。**不要再手搓 askpass 文件或把 token 贴进对话。**


### CI

| 工作流 | 触发 | 用途 |
|---|---|---|
| `build-manager.yml` | push `main`/`dev`/`ci`；`workflow_dispatch`（输入 `abi`） | 编译管理器 + repack + 产物 |
| `release.yml` | push tag `v*` | 全量产物（管理器 + ksud + ksuinit + 16 个 LKM） |
| `clippy.yml` / `rustfmt.yml` / `shellcheck.yml` | push | 静态检查 |

- **自用精简包**：`workflow_dispatch` 选 `build-manager.yml`，`abi=arm64-v8a`，产物只有 arm64。
- 产物上传条件已放宽到"非 PR 的所有分支"（否则工作分支跑 repack 会找不到 `manager-gradle`）。
- `Deploy Website` 一直失败：GitHub Pages 没开，属历史遗留，与代码无关。

### 发版流程（4.x 实际用过的）

1. `git tag -f vX.Y.Z-ultra` 打在最终 HEAD；
2. `git push origin main` + `git push -f origin refs/tags/vX.Y.Z-ultra`；
3. 等 `Release` workflow 完成；
4. 用 GitHub API 建 draft release，把 28 个资产传上去（管理器 APK 从 `manager` artifact 里取，
   ksud/ksuinit 重命名，LKM 命名 `lkm-<arch>-<kmi>_kernelsu.ko`）；
5. `PATCH` 发布（`draft=false, make_latest=true`）；
6. 再 dispatch 一次 arm64 自用构建，下载产物改名 `DiksuUltra_X.Y.Z-arm64_selfuse_<versionCode>.apk`。

### 推送网络

容器访问 GitHub 不稳定时，本项目历史做法是：写一个临时 `GIT_ASKPASS` 脚本（从环境变量读 token），
`/etc/hosts` 固定 `20.27.177.113 github.com`，push 完立即恢复 hosts 并删除脚本。
**不要把 token 写进仓库或长期留在磁盘上。**

---

## 八、踩过的坑（省你几小时）

| 现象 | 原因 | 处理 |
|---|---|---|
| 合并上游后 versionCode 跳到 38399 | 提交计数用了全量 `rev-list --count` | 改 `--first-parent`（`manager/build.gradle.kts`） |
| 管理器显示"未安装"但 su 能用 | 内核 `KSU_PACKAGE_NAME` 与 `applicationId` 不一致 | 两处必须同步（`kernel/Kbuild` + `manager/app/build.gradle.kts`） |
| 模块大小永远不显示 | 管理器进程读不了 `/data/adb/modules`（0700 root） | 由 ksud 在 root 侧算好，`module list` JSON 带 `size` |
| Miuix 模块卡片 ID/大小整块不显示 | `preinitWarning()?.let { }` 少一个收尾括号，把后续内容吞进 lambda | 补 `}` |
| 桌面端 ksud 编译报 `get_features` 重复定义 | `#[cfg]` 属性被插到新函数上方，导致两个 `get_features` 都失去 cfg | 新函数要插在 `#[cfg(...)]` **之前** |
| CI repack 报找不到 `manager-gradle` | 上传条件限定 main/dev | 放宽为 `github.event_name != 'pull_request'` |
| ShellCheck 一直红 | 扫到了设备端资产脚本 `manager/app/src/main/assets/hma-oss-config.sh` | 加进 `ignore_paths` |
| 改 `KSU_NAME` 后隐藏管理器异常 | 该字符串槽位固定 24 字节，隐藏时按同长度就地改写 | 新名字必须凑够 24 字节（零宽字符填充） |
| APK 文件名前缀还是 DikSU | `manager/gradle.properties` 的 `KSU_NAME` 没改 | 改 `KSU_NAME`（注意上面的 24 字节约束） |

---

## 九、安全与合规

- 本项目 GPL-3.0，二改分发需保持同协议并提供源码。
- 越狱链路依赖内核漏洞，**仅供自己在自己设备上使用**；不要把它包装成"通用 root 工具"分发。
- `ksu-signing.jks` 是仓库自带的签名（自用），发布正式版请换自己的 key。
- 内置 Shizuku 的授权记录在 `/data/user_de/0/com.android.shell/shizuku.json`，属敏感数据，
  网页端/日志里不要整份打印。

---

## 十、快速自检清单（改完代码后）

```bash
# 1. 包名/引用一致性（应无输出）
grep -rn "me\.weishu\|me\.diksu\.kernelsu" --include=*.kt --include=*.java --include=*.xml --include=*.rs --include=*.c . | grep -v "^./.git/"

# 2. 新增 Kotlin 文件括号平衡（粗查）
#    见历史脚本：去注释/字符串后统计 {} 与 ()

# 3. 版本公式两侧一致
grep -n "KSU_VERSION" kernel/Kbuild | head
grep -n "getVersionCode" manager/build.gradle.kts | head

# 4. 本地无法编译（容器无 Android SDK），一律靠 CI：
#    push 后看 build-manager / clippy / rustfmt 是否全绿
```

真机验证项（每次发版前至少过一遍）：

- [ ] 全新安装 → 首页状态正确 → 提权成功 → 模块列表有大小 → 开关可点
- [ ] 完整重启（越狱模式）→ 首页显示"未提权" → 导航置灰且点得出原因 → 手动提权恢复
- [ ] 设置页三套皮肤各点一遍，确认没有空白页/重复项
- [ ] 网页端：首页 / 超级用户 / 模块 / 设置（内核特性开关）/ 文件 各点一遍
- [ ] 内置 Shizuku：开关 → 启动 → 授权一个应用 → 重启后自动拉起

---

## 十一、4.2 开发轮进展（2026-10-25，未发版）

> **状态：CI 已全绿。** 本轮前半段因为 harness 里没有 `DSH_GIT_TOKEN`，一次 CI 都没跑；
> 后半段拿到 token 后推送并验证通过：
>
> | 工作流 | run | 结论 |
> |---|---|---|
> | Build Manager（push：管理器 APK + ksud + LKM + repack） | `40143631687` | ✅ success |
> | Build Manager（dispatch，`abi=arm64-v8a` 自用包） | `40144362274` | ✅ success |
> | Clippy check | `40143631600` | ✅ success |
> | Rustfmt check | `40143631581` | ✅ success（第一次 `40143337455` 红在 `uid_scheme` 换行，已修） |
>
> 结论：**Kotlin 与 Rust 都真实编译通过**——`webui.rs` 新增的 SuSFS/Shizuku 接口、导航置灰的
> 五个组件、Material 修复都在编译范围内。ShellCheck 未触发（它按 `*.sh` 过滤，本轮没改脚本）。

### 11.1 提交清单（按时间顺序）

| 提交 | 内容 |
|---|---|
| `1322ecac` | 修 `dsha-diksu-git`：无 tag 时返回显式 `undefined` 导致工具输出校验失败；README 补两条坑 |
| `8fe18adb` | 网页端 SuSFS 面板：4 条 API + 设置页卡片；开关/规则只报"已下发"，删除如实说明内核无 remove |
| `49b2d67b` | `kernel/Kbuild` 默认签名 size/hash 改为仓库自带 `ksu-signing.jks` 的实际值（799 / `b27c5a47…`） |
| `7e2d2977` | 导航置灰细化到每个入口：`NavEntry` + 5 个导航组件逐项判定 + MainActivity 去掉连带禁用 |
| `9ef3767c` | Material 巡检修 3 处 + `docs/material3-audit.md`（25 页逐行结论） |
| `04f25daf` | `docs/device-verification-4.1.md` + 网页端 Shizuku / 视觉重构两份方案 |
| `5addaf62` | 网页端内置 Shizuku 管理：3 条 API + 设置页卡片（只读 + 启停，授权仍回 App） |
| `af8dbdd1` | 本文件第十一节（4.2 进展） |
| `248c5b8a` | rustfmt：`uid_scheme` 链式换行（CI 唯一红点） |

### 11.2 环境问题（下一轮开工先处理）

1. **git token 仍是硬伤**：harness 进程环境里 `DSH_GIT_TOKEN` / `GITHUB_TOKEN` / `GH_TOKEN` 都没有。
   本轮后半段是用户把一个 PAT **直接贴在对话里**才推上去的——这个 token 等同已泄露，**必须吊销重发**。
   下一轮请务必在启动 harness 的环境里 `export DSH_GIT_TOKEN=<新 PAT>` 再重启，不要再走对话。
2. **插件 `repoPath`**：harness 的 cwd 是 `/root`，插件默认拿 `process.cwd()` 当仓库路径，
   导致所有 git 工具在 `/root` 上跑。已把 `repoPath: /sdcard/diksu` 写进
   `~/.dsh/profiles/web/cordis.patch.yml`，**重启 harness 后生效**（本轮重启后 `git_status` 已验证正常）。
3. **设备通道**：无障碍（`ACCESSIBILITY_UNAVAILABLE`）与设备 shell（`DEVICE_CHANNEL_UNAVAILABLE`）
   都未授权，`/app/ui/*` 与 `adb-shell` 用不了。真机核验按用户要求取消，只留了只读证据。
4. **github.com 直连不稳**：`curl https://github.com` 能回 200 但 body 传 16KB 就挂住，
   `api.github.com` 正常。推送时用插件的 `hostsPin` + 重试。

### 11.3 必须优先确认的风险

- **编译已过，运行未验**：Build Manager / Clippy / Rustfmt 全绿，编译级风险清零。
  剩下的都是运行期问题，按优先级：
  1. Shizuku 启停的真实行为（`setsid -d` 是否被目标 ROM 的 toybox 支持、`app_process` argv 顺序、
     `pidof shizuku_server` 是否存在；失败时看 `/data/local/tmp/shizuku_ksu.log`）；
  2. SuSFS 面板的开关/规则是否真作用于 `/proc/susfs_*`；
  3. 网页端新增的 402 行 JS 只能在浏览器里回归。
- **导航置灰的三个已知边界**（没解决，别当成已解决）：
  Pager 滑动仍可到达不可用页；`Natives.isFullFeatured()` 不是 Compose State，提权成功后要等重组；
  悬浮底栏拖到不可用项时内部高亮可能先移动再复位。
- **tag 不在 HEAD**：`v4.1.0-ultra` 之后已有 15 个提交，现在重编会得到 `41015` 而不是 `41000`。
  发版二选一：把 tag 移到最终 HEAD 再构建，或直接起 `4.1.1`。
  本轮自用包是 `DiksuUltra_4.1.0-arm64_selfuse_41014.apk`（`248c5b8a` 上构建，versionCode 41014）。
- **Material 6 处中文硬编码**待抽资源，8 个 key 已列在 `docs/material3-audit.md` 第六节。
- **`setAutoJailbreak` 是死代码**：BasicRow 已删，但 `SettingsViewModel` / `SettingsScreenActions`
  里还留着引用，要清需再动 2 个文件。

### 11.4 任务板状态

| 任务 | 状态 |
|---|---|
| task-1 网页端 SuSFS 面板 | ✅ 完成（`8fe18adb`） |
| task-2 导航置灰做细 | ✅ 完成（`7e2d2977`） |
| task-3 Material 3 巡检 | ✅ 完成（`9ef3767c`） |
| task-4 真机核验 | ⏹ 按用户要求取消，只留只读证据（`04f25daf`） |
| task-5 网页端 Shizuku 管理 | ✅ 完成（`5addaf62`） |
| task-6 网页端视觉重构 | 📋 只有方案（`docs/webui-redesign-plan.md`），**没动代码** |

task-6 没动代码是刻意的：它要重写 `index.html`（已 4986 行），在没有真机浏览器回归的前提下做，
风险远大于收益。下一轮建议单独开一轮，先拆文件再改样式，每步可回滚。
