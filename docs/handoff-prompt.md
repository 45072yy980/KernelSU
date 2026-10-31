# 给下一任总工程师的交接提示词

> 用法：把下面 **「提示词正文」** 整段粘给接手的 Agent（或人），它就能在不知道历史的情况下正确开工。
> 本文档本身也是仓库的一部分，改了约定请同步更新这里。

---

## 提示词正文（复制从这里开始）

你现在接手 **Diksu Ultra**（`/sdcard/diksu`，GitHub `45072yy980/KernelSU`）。这是 KernelSU 的一个分支：
在**不解锁 Bootloader、不刷 boot/system** 的前提下，靠漏洞把内核模块挂进正在运行的系统。

### 0. 开工前必读（按顺序，别跳）

1. `docs/handoff.md` —— 现状、代码地图、已知问题、下一步排期、开发约定、踩过的坑。**这是唯一权威。**
2. `team_task_list` —— 任务板上已经排好 6 个任务（网页端 SuSFS/Shizuku/视觉重构、导航置灰做细、Material3 巡检、真机验证）。认领后再动手。
3. `README.md` 的「更新记录」—— 每个版本改了什么、为什么。

### 1. 硬约束（违反会直接出事）

**① 构建验证只能走 CI，本地绝对编译不了。**
容器里没有 JDK、没有 Gradle、没有 Android SDK，也没有 Rust 工具链的完整环境。任何"我改完了应该没问题"的想法都是错的，必须：

```
git_push（推分支） → ci_dispatch（workflow_dispatch，可带 abi=arm64-v8a） → ci_watch（等到完成看失败 job/步骤）
```

- 管理器 + 内核 + ksud 的编译都在 `build-manager.yml` 里；
- `clippy.yml` / `rustfmt.yml` / `shellcheck.yml` 也要绿；
- 工作分支也能跑完整 repack（上传条件已放宽到非 PR）；
- **改完 Rust 记得 rustfmt**：4.1 就因为在 `#[cfg]` 属性下方插函数导致 `get_features` 重复定义 + 两处格式不符，连挂两次 CI。

**② token 只能从环境变量走，不许出现在任何地方。**
- 插件 `dsha-diksu-git` 2.0.0 已装好（19 个工具）：`git_status` / `git_diff` / `git_log` / `git_show` / `git_blame` / `git_stage` / `git_commit` / `git_branch` / `git_stash` / `git_sync` / `git_push` / `git_auth` / `git_tag` / `git_release_notes` / `git_audit` / `ci_dispatch` / `ci_watch` / `ci_logs` / `release_publish`。
- token 走四层：插件配置 `token` → `~/.dsh/git-token`（或配置 `tokenFile`）→ DSH 凭据库（引用名 `DSH_GIT_TOKEN`）→ 环境变量 / `~/.dsh/.env`。**shell 里 `export` 到不了 harness 进程**，优先写 `~/.dsh/.env` 后重启，或用 `git_auth` 诊断。**不要在对话里贴 token。**
- 需要直连不稳时，插件支持 `hostsPin`（临时改 hosts 并自动还原），不要手搓 askpass 脚本。
- 插件源码随仓库保存了一份副本：`tools/dsha-diksu-git/`（安装方法见该目录 README）。
- 上一轮的 PAT 已在对话中出现过明文，**必须吊销重发**（见 handoff 的 P0-1）。

**③ 构建产物绝不进仓库。**
- 根目录 `DikSU_*.apk` / `DiksuUltra_*.apk` 已在 `.gitignore`；`git_commit` 会拒绝新增 APK（删除放行）。
- 4.1 曾经因为 `.gitignore` 只写了 `/DikSU_*.apk`、而文件已改名成 `DiksuUltra_*`，误提交了两个 10MB 自用包（`0af561b5`、`0005bd9f`）。已在 `aab99614` 移出索引，但**历史里还留着这两个 blob**；要彻底清掉见 handoff 第八节，需要 force push + 重打 tag，动手前先确认没人在用。
- 自用包放 `/sdcard/diksu/` 和 `/sdcard/Download/diksu_apks/`，用 `present` 交付。

**④ 提交约定。**
- 作者固定 `dsh-agent <dsh-agent@localhost>`（插件默认已带）；
- 信息中文，`type(scope): 摘要`，正文写"为什么"；
- 不要提交 `manager/gradle.properties` 里的 `KSU_NAME` 之外的品牌字符串改动——**`KSU_NAME` 必须正好 24 字节**（隐藏管理器按同长度就地改写），改名要用零宽字符补齐。

**⑤ 版本号只在打 tag 时归零。**
公式：`major*10000 + minor*1000 + patch*100 + tag 之后（--first-parent）的提交数`，管理器与内核各算一遍且有构建期校验。
发版：`git_tag`（打 `vX.Y.Z-ultra` 在最终 HEAD）→ `git_push`（分支 + tag）→ 等 `release.yml` → `release_publish`（自动挑 28 个资产）→ 再 dispatch 一次 arm64 自用包。

### 2. 这个项目的"性格"（做决策时按这个来）

- **宁可少做，不要做假。** 面板里写"删除只影响下次开机重放"，就不会让它看起来立刻生效；网页端没做的功能，就说没做。
- **三套皮肤要么都做，要么明说没做。** 不允许再出现"Material 只有一句请切到 miuix"。
- **状态要说人话。** 不可用就置灰并给原因与恢复入口，不要整条导航消失（这是 4.1 修的事）。
- **能自证的地方要自证。** 开机自启要显示"上次结果"，自动提权要显示"上次是谁成功的"，规则清单要能删。

### 3. 容易踩的坑（浓缩版，详细见 handoff 第八节）

| 现象 | 原因 |
|---|---|
| 管理器显示"未安装" | 内核 `KSU_PACKAGE_NAME` 与 `applicationId` 不一致，两处必须同步 |
| 模块大小不显示 | 管理器读不了 `/data/adb`（0700 root），必须由 ksud 算 |
| 桌面端 ksud 编译失败 | `#[cfg]` 属性被新函数抢走 |
| CI repack 找不到产物 | 上传条件被限定在 main/dev |
| 网页端改动不生效 | HTML 是 `include_str!` 进 ksud 的，改完要重新编 ksud |
| `stringResource` 编译报错 | 写进了 `onClick` 这类非 @Composable lambda，要提前取值 |

### 4. 第一件事建议做什么

先跑 `git_status` 看一眼（分支/版本/有没有 APK 残留），再 `team_task_list`，
从 **task-1（网页端 SuSFS 面板）** 开始——它性价比最高：ksud 侧子命令齐全，接口好写，
做完立刻能看出网页端"活"了。真机相关（task-4）留到有设备时再做，不要凭猜改。

（复制到这里结束）

---

## 为什么这么写

- **"构建只能走 CI"放在第 1 条**：这是本项目最容易翻车的地方。没有本地编译能力，任何"看起来对"的 Kotlin/Rust 改动都可能连挂三轮 CI；把 dispatch→watch 的闭环写死，接手的人就不会去试 `./gradlew`。
- **token 从环境变量走**：上一轮 PAT 出现在对话里，是真实的泄露路径。插件化之后，"用工具"本身就等于"不碰明文"。
- **把项目性格写进去**：这个仓库的历史提交里，凡是"看起来做了其实没做"的地方最后都返工了（SuSFS Material、自动越狱空服务、导航消失）。把"宁可少做不要做假"写成原则，比逐条列举更省事。
- **第一件事指定 task-1**：给一个明确、低风险、可验证的起点，避免接手的人从"重构网页端 UI"这种大坑开始。
