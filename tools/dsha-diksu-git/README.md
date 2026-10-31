# dsha-diksu-git

Diksu Ultra 专用的 git / CI / 发布工具插件，19 个工具。**2.0.0 全面重写**：修掉了 1.1.0 里
一批会把工具跑挂、把 secret 留在索引里、或者干脆注册不上的问题（见「2.0.0 修了什么」）。

## token 怎么设（重点，1.1.0 在这里踩过坑）

1.1.0 只从 `process.env` 读 token，而 **Shell 设置里 `export` 的变量只进 bash 工具的子进程，
不会回灌到 harness 进程** —— 所以「明明设了环境变量，插件还是说没有 token」。2.0.0 改成四层解析，
任选一种（都不需要把 token 贴进对话）：

| 顺序 | 位置 | 怎么设 |
|---|---|---|
| 1 | 插件配置 `token` | 设置 → 插件 → dsha-diksu-git → `token`（掩码保存，写不进读） |
| 2 | 插件配置 `tokenFile` + 默认文件 | `echo <PAT> > ~/.dsh/git-token && chmod 600 ~/.dsh/git-token` |
| 3 | DSH 凭据服务 | 在设置里给 `DSH_GIT_TOKEN` 存一个凭据（落到 `~/.dsh/.credentials.yaml`） |
| 4 | 环境变量 / `~/.dsh/.env` | 把 `DSH_GIT_TOKEN=<PAT>` 加进 `~/.dsh/.env`，然后**重启 DSH** |

- 环境变量名依次试 `DSH_GIT_TOKEN` → `GITHUB_TOKEN` → `GH_TOKEN` → `GITHUB_PAT`（可用 `tokenEnv` 改第一个）。
- 权限：私有仓库要 `repo`；只看 Actions / 传资产还要 `workflow`。
- `~/.dsh/.env` 是 DSH 官方支持的给 harness 进程注入环境变量的地方（启动时 load），比 `export` 靠谱。
- 不确定当前读到哪一层？直接调 **`git_auth`**：它只报「哪一层配了 / 来源 / 长度」，不返回值。
- 公开仓库和本地 file 远端**不需要 token**：没有 token 时插件会退回匿名访问并给出提示。

## 安装与部署

- 源码：本目录（`tools/dsha-diksu-git`）
- 实际加载：`/root/dsha-diksu-git`（profile 里 `"dsha-diksu-git": "link:/root/dsha-diksu-git"`）

`/sdcard` 是 fuse 文件系统，不能在仓库里建 `node_modules` 软链，也不能把 `/root` 改成指回仓库的软链
（Node 会按真实路径解析依赖，那样会找不到 `@deepseek-ai/dsh-tools`）。所以**仓库是源码、/root 是部署产物**：

```bash
bash tools/dsha-diksu-git/scripts/deploy.sh    # 同步 lib/test/package.json/README/cordis.patch.yml
```

生效要重启 harness（`patchReload: startup`）。校验：

```bash
dsh --profile web --dump-config | grep -A2 diksu-git
```

## 工具（19 个）

### 读取与审查

| 工具 | 作用 |
|---|---|
| `git_status` | 分支/HEAD/上游/领先落后、tag 与 versionCode、staged/unstaged/untracked/conflicts、进行中的 merge/rebase、APK 残留、当前 token 来源 |
| `git_diff` | 工作区 / 暂存区 / 任意 ref 范围的 diff；路径过滤、只看统计、按字节截断 |
| `git_log` | 提交历史；支持 range（`v4.0.0..HEAD`）、路径、作者、grep 过滤与 `withFiles` |
| `git_show` | 单个提交 / tag 的元信息、改动统计、可选完整补丁 |
| `git_blame` | 文件逐行追溯：提交、作者、日期、内容；支持行范围 |
| `git_audit` | 提交前审计：GitHub PAT / AWS key / 私钥 / JWT / 通用 secret / 冲突标记 / APK / 大文件；worktree 范围**包含未跟踪文件** |

### 变更与提交

| 工具 | 作用 |
|---|---|
| `git_stage` | 精确暂存 / 取消暂存；`all=true` 操作全部改动 |
| `git_commit` | 固定作者提交；**提交前逐文件审计**，高风险文件精确移出暂存区（含空格/中文/引号路径）；拒绝新增 APK；支持 amend / signoff / 空提交 |

### 分支、贮藏与同步

| 工具 | 作用 |
|---|---|
| `git_branch` | list / create / switch / delete；删除前自动写 `refs/diksu-backup/branches/...` 备份；受保护分支拒删 |
| `git_stash` | list / save / apply / pop / drop；显示 `stash@{0}` 而不是日期 |
| `git_sync` | fetch / pull；默认 `--ff-only`，工作区不干净拒绝 pull；带 token、hostsPin 与重试 |

### 远端、发布与 CI

| 工具 | 作用 |
|---|---|
| `git_push` | 推分支或 tag；临时 askpass + hostsPin + 重试；推完用 `ls-remote` 逐 sha 复核；受保护分支拒绝强推；拒绝 `+main` 这类 refspec 强推前缀 |
| `git_auth` | 凭据诊断：四层各自的 ✓/✗、当前来源、远端地址（已抹掉账号）、可选 `check=true` 调 API 验证 |
| `git_tag` | 在 HEAD 打 `vX.Y.Z-ultra`，算出 versionCode；支持附注 tag（自动注入作者身份） |
| `git_release_notes` | 按 conventional commit 分组生成 Markdown 发布说明，可写入仓库文件（自动建目录） |
| `ci_dispatch` | 触发 workflow_dispatch，按 `head_sha` + 创建时间精确匹配 run |
| `ci_watch` | 轮询 run 到结束（可取消），返回失败 job / 步骤 |
| `ci_logs` | 拉取失败 job 的日志末尾与 error 行，CI 失败先看这个 |
| `release_publish` | 先下齐并改名 28 个资产 → 生成说明 → 建 draft → 逐个上传 → 发布；失败不留孤儿草稿 |

## 配置项

`设置 → 插件 → dsha-diksu-git` 可直接改（写进 profile 的 `cordis.patch.yml`）。

| 键 | 默认 | 说明 |
|---|---|---|
| `repoPath` | 空（自动定位） | 仓库根目录。空 = 按 `DSH_WORKSPACE`/`DSH_PROJECT_ROOT`/`DSH_CWD`/当前目录向上找 `.git` |
| `remote` | `origin` | 远端名 |
| `token` | 空 | 直接填 PAT（掩码保存） |
| `tokenEnv` | `DSH_GIT_TOKEN` | 凭据引用名 / 环境变量名 |
| `tokenFile` | `~/.dsh/git-token` | token 文件（另试 `~/.dsh/github-token`、`~/.config/dsha/git-token`） |
| `apiBase` | `https://api.github.com` | GitHub API 根地址（企业版可改） |
| `hostsPin` | 空 | 形如 `20.27.177.113 github.com`，网络不稳时用；用完自动还原，失败会告警 |
| `hostsFile` | `/etc/hosts` | hosts 文件路径 |
| `authorName` / `authorEmail` | `dsh-agent` / `dsh-agent@localhost` | 提交 / tag 作者（`-c user.name/email`，不改全局配置） |
| `maxAttempts` | `3` | 网络动作最多尝试次数（只重试可恢复错误：超时、5xx、连接重置） |
| `retryDelayMs` | `3000` | 重试基础间隔（线性退避） |
| `protectedBranches` | `main,master,develop,trunk,release/*,hotfix/*` | 受保护分支，逗号分隔，`*` 结尾前缀匹配 |
| `maxDiffBytes` | `200000` | diff / patch 输出字节上限（按行边界截断） |
| `auditMaxFileMB` | `2` | 提交审计的大文件阈值 |

## 2.0.0 修了什么

**注册期就挂 / 看不懂的报错**

- `output.schema` 与 `parameters` 都按 DSH 的 value-schema 简写写对，`node test/schema-check.mjs` 静态检查；
- 所有返回值过 `lossless()`：NaN / Infinity / undefined 不再把 harness 的输出校验炸成 `value is not lossless JSON`（1.1.0 的 `git_status` 报错根因）；
- `repoPath` 默认值不再在模块加载时取 `process.cwd()`（那是 harness 的启动目录），改为调用时定位并校验 `.git`；
- `git_show` 字段缺失不再返回 `undefined`，而是空串 / 明确报错。

**安全**

- `git_diff` / `git_log` / `git_show` / `git_audit` / `git_release_notes` / `git_branch` / `git_tag` 的 ref/range/tag 全部走 `safeRevision` / `safeTagName`，`--output=/tmp/x` 这类选项注入直接拒绝；
- `safeBranchName` 拒绝 `+`（`+main` 是 refspec 强推前缀），`safePath` 拒绝绝对路径、`..`、NUL 与 pathspec 魔法；
- 提交审计改为**逐文件** `git diff --cached -U0 -- <path>`，路径来自 git 本身，空格 / 中文 / 引号路径也能精确退回索引，并复核「索引里真的没剩下」；
- 推送后用 `ls-remote` 精确比对 sha（附注 tag 用 tag 对象 sha），返回 `localSha` / `remoteSha` / `verified` 三个字段。

**可靠性**

- token 四层解析（见上），`git_auth` 可自查；
- 没有 token 时公开仓库 / 本地远端仍可用（匿名回退），只有真的被拒才报错；
- 重试分类：只重试超时 / 5xx / 连接类错误，401 / 受保护分支之类不再白等；
- `askpass` 临时目录用 `rm -r` 清理（1.1.0 用 `unlink(dir)` 永远失败，/tmp 越攒越多）；
- `hostsPin` 失败会告警而不是静默失效，且不会重复追加；
- `git status` 用 `--porcelain=v1 -z` 解析，重命名 / 冲突 / 未跟踪不再错位；
- 版本号公式 `major*10000+minor*1000+patch*100+提交数`，`commitsAfterTag` 明确返回；
- `release_publish` 先下齐产物再建 draft，本机没有 `unzip` 时自动退到 `python3 -m zipfile`。

**工程**

- 拆成 `core.js`（纯 Node，可单测）+ `tools-read/write/remote/ci.js`；
- 三层自测：`run-tests.mjs`（34 项纯逻辑 + 真实仓库集成）、`smoke-registry.mjs`（真实 `defineTool` + 输出 schema 校验）、`schema-check.mjs`（静态 schema 合规）。

## 自测

```bash
cd /root/dsha-diksu-git
node test/run-tests.mjs        # 纯逻辑 + 真实临时仓库（不需要 DSH）
node test/schema-check.mjs     # parameters / output.schema 是否合法
node test/smoke-registry.mjs   # 走真实 defineTool，校验参数与输出契约
```

## 安全设计

- 传 token 用临时 `GIT_ASKPASS` 脚本（内容是 `printf "%s" "$DSH_GIT_TOKEN"`），文件里没有明文，`finally` 里 `rm -rf` 目录；
- 工具输出、错误信息、`ci_logs` 日志统一把 token 替换成 `***`；
- 路径参数只接受仓库内相对路径；分支 / tag / 远端 / workflow 名都做规则校验；
- 受保护分支禁止强推与删除（`allowProtected` / `protectedBranches` 显式放行）；
- 审计发现高风险内容时把相关文件移出暂存区再报错，不会把 secret 写进提交；
- 不做 `git config --global` 写入，不改用户凭据存储。

## 借鉴的社区插件

| 社区插件 | 借鉴点 |
|---|---|
| `dsh-git-tools`（Yangi-252410） | status / diff / log / stage / fetch / pull / push 的工具划分与宿主进程执行 |
| `dsh-plugin-git-workflow`（truelove-dreamer） | 结构化 porcelain 解析、路径校验、纯逻辑可单测 |
| `dsh-gitflow`（lonelymoon87） | 不做隐式暂存、commit message 走 stdin、破坏性命令全部显式开关 |
| `dsh-plugin-git-workbench`（Yaronluo） | 冲突 / 贮藏 / 工作区分组；提交前审查 |
| `dsh-branch-cleaner`（ramwin） | 删前备份 ref、受保护分支名单 |
| `dsh-git-manager`（Dayi-Z） | CI 日志、release notes、发布链路 |
| `dsh-github-flow`（ItQianChen） | Actions 排障、release 资产交付 |
