# dsha-diksu-git

Diksu Ultra 专用的 git / CI / 发布工具插件。**token 只从环境变量读取，不落盘、不进对话。**

1.1.0 起参考了插件市场里多个社区 Git 插件的成熟做法（见文末「借鉴的社区插件」），
把 agent 日常要用的 diff / log / show / stage / branch / stash / sync / audit / blame /
CI 日志 / release notes 补齐，同时保留本项目自己的 tag → versionCode → CI → release 链路。

## 安装（已完成，仅供参考）

- 插件目录：`/root/dsha-diksu-git`
- profile 接入：`~/.dsh/profiles/web/package.json`
  - `dependencies` 加 `"dsha-diksu-git": "link:/root/dsha-diksu-git"`
  - `dsh.profile.bundles` 末尾加 `"dsha-diksu-git"`
  - `node_modules/dsha-diksu-git` 软链到插件目录
- 生效：重启 harness（`patchReload: startup`）
- 校验：`dsh --profile web --dump-config | grep -A2 diksu-git`

## 必做：设置 token 环境变量

```bash
export DSH_GIT_TOKEN=<你的 GitHub PAT>
```

- 只读权限不够，需要 `repo`（私有仓库）或 `public_repo` + `workflow` 权限。
- 插件会依次尝试 `DSH_GIT_TOKEN` → `GITHUB_TOKEN` → `GH_TOKEN`，建议只用第一个。
- **不要**把 token 写进任何文件、不要贴进对话；插件报"没有可用的 git token"时就是没设置。

## 工具（18 个）

### 读取与审查

| 工具 | 作用 |
|---|---|
| `git_status` | 分支/HEAD/上游/领先落后、tag 与 versionCode、staged/unstaged/untracked/conflicts、进行中的 merge/rebase、APK 残留 |
| `git_diff` | 工作区 / 暂存区 / 任意 ref 范围的 diff；路径过滤、只看统计、按字节截断 |
| `git_log` | 提交历史；支持 range（`v4.0.0..HEAD`）、路径、作者、grep 过滤与 `withFiles` |
| `git_show` | 单个提交 / tag 的元信息、改动统计、可选完整补丁 |
| `git_blame` | 文件逐行追溯：提交、作者、日期、内容；支持行范围 |
| `git_audit` | 提交前审计：GitHub PAT / AWS key / 私钥 / JWT / 通用 secret / 冲突标记 / APK / 大文件 |

### 变更与提交

| 工具 | 作用 |
|---|---|
| `git_stage` | 精确暂存 / 取消暂存；`all=true` 操作全部改动 |
| `git_commit` | 固定作者提交；**提交前自动审计**，高风险内容直接移出暂存区；拒绝新增 APK；支持 amend / signoff / 空提交 |

### 分支、贮藏与同步

| 工具 | 作用 |
|---|---|
| `git_branch` | list / create / switch / delete；删除前自动写 `refs/backup/dsha-diksu-git/...` 备份；受保护分支拒删 |
| `git_stash` | list / save / apply / pop / drop；可包含未跟踪文件 |
| `git_sync` | fetch / pull；默认 `--ff-only`，工作区不干净拒绝 pull；带 token、hostsPin 与重试 |

### 发布与 CI

| 工具 | 作用 |
|---|---|
| `git_tag` | 在 HEAD 打 `vX.Y.Z-ultra`，算出 versionCode；支持附注 tag |
| `git_release_notes` | 按 conventional commit 分组生成 Markdown 发布说明，可写入仓库文件 |
| `ci_dispatch` | 触发 workflow_dispatch 并回传 run id（自用包：`abi=arm64-v8a`） |
| `ci_watch` | 轮询 run 到结束，返回失败 job/步骤 |
| `ci_logs` | 拉取失败 job 的日志末尾与 error 行，CI 失败先看这个 |
| `release_publish` | 从 Release run 取 28 个资产，建 release、上传、发布；未给 bodyFile 时自动生成发布说明 |
| `git_push` | 推分支或 tag；临时 askpass + hostsPin + 重试；推完 `ls-remote` 校验；受保护分支拒绝强推 |

## 借鉴的社区插件

| 社区插件 | 借鉴点 | 落到本插件 |
|---|---|---|
| `dsh-git-tools`（Yangi-252410） | 8 个 agent 工具：status / diff / log / stage / commit / fetch / pull / push；宿主进程执行 git | `git_diff`、`git_log`、`git_stage`、`git_sync`，以及 `git_push --set-upstream` |
| `dsh-plugin-git-workflow`（truelove-dreamer） | 结构化 porcelain 解析、路径校验、提交信息校验、纯逻辑可单测 | `parseStatus` 拆分四类状态、`safePath` / `safeBranchName`、`test/run-tests.mjs` |
| `dsh-gitflow`（lonelymoon87） | 不做隐式暂存、提交走审批、commit message 走 stdin、无破坏性命令 | `git_commit` 只用显式 paths、`git commit -F -`、破坏性操作全部显式开关 |
| `dsh-plugin-git-workbench`（Yaronluo） | 8 个工具 + 冲突/贮藏/工作树分组；提交前审查 | `git_stash`、`git_status` 的 conflicts/inProgress、`git_audit` |
| `dsh-branch-cleaner`（ramwin） | 默认只报告、删前备份 ref、受保护分支名单 | `git_branch delete` 先写 `refs/backup/...`，`protectedBranches` 配置 |
| `dsh-git-manager`（Dayi-Z） | 结构化 Git/GitHub 工具、PR/CI 徽章、提交图谱 | `ci_logs`、`git_release_notes`、`release_publish` 自动生成说明 |
| `dsh-github-flow`（ItQianChen） | Actions 运行排障、release 资产交付 | `ci_watch` / `ci_logs` / `release_publish` 的失败定位链路 |

## 配置项（profile 的 cordis.patch.yml 里覆盖）

| 键 | 默认 | 说明 |
|---|---|---|
| `repoPath` | 进程 cwd | 仓库根目录，建议显式写 `/sdcard/diksu` |
| `remote` | `origin` | 远端名 |
| `tokenEnv` | `DSH_GIT_TOKEN` | token 环境变量名 |
| `hostsPin` | 空 | 形如 `20.27.177.113 github.com`，网络不稳时用；用完自动还原 |
| `authorName` / `authorEmail` | `dsh-agent` / `dsh-agent@localhost` | 提交作者 |
| `protectedBranches` | `main,master,develop,trunk,release/*,hotfix/*` | 受保护分支，逗号分隔，支持 `*` 前缀匹配 |
| `maxDiffBytes` | `200000` | diff / patch 输出字节上限 |
| `auditMaxFileMB` | `2` | 提交审计的大文件阈值 |

覆盖示例（`~/.dsh/profiles/web/cordis.patch.yml`）：

```yaml
- id: diksu-git
  name: dsha-diksu-git
  config:
    repoPath: /sdcard/diksu
    hostsPin: 20.27.177.113 github.com
```

## 安全设计

- 传 token 用临时 `GIT_ASKPASS` 脚本，脚本内容是 `echo "$DSH_GIT_TOKEN"`，文件里没有明文，`finally` 里删除；
- 所有工具输出统一把 token 替换为 `***`；
- 缺 token 直接报错，不会退化成匿名请求；
- 路径参数只接受仓库内相对路径，拒绝绝对路径、`..` 与 NUL；分支名 / tag 名做 git 规则校验；
- 受保护分支禁止强推与删除（`allowProtected` / `protectedBranches` 显式放行）；
- 提交审计发现高风险内容时，把相关文件移出暂存区再报错，不会把 secret 写进提交；
- 不做 `git config --global` 写入，不改用户凭据存储。

## 自测

```bash
cd /root/dsha-diksu-git && npm test
```

测试会在临时目录创建真实 git 仓库与 bare 远端，覆盖 status / diff / stage / audit / commit /
log / show / blame / branch / stash / fetch / pull / tag / release notes / 受保护分支强推拦截。
