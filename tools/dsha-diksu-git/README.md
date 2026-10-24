# dsha-diksu-git

Diksu Ultra 专用的 git / CI / 发布工具插件。**token 只从环境变量读取，不落盘、不进对话。**

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

## 工具

| 工具 | 作用 |
|---|---|
| `git_status` | 分支/HEAD/tag/versionCode/改动/APK 残留/领先落后 |
| `git_commit` | 固定作者提交；拒绝新增 APK（删除 APK 放行）；返回哈希与统计 |
| `git_push` | 推送分支或 tag；临时 askpass + 可选 hosts 钉 IP + 重试；推完 ls-remote 校验 |
| `git_tag` | 在 HEAD 打 `vX.Y.Z-ultra` 并算出 versionCode |
| `ci_dispatch` | 触发 workflow_dispatch 并回传 run id（自用包：`abi=arm64-v8a`） |
| `ci_watch` | 轮询 run 到结束，返回失败 job/步骤 |
| `release_publish` | 从 Release run 取 28 个资产，建 release、上传、发布 |

## 配置项（profile 的 cordis.patch.yml 里覆盖）

| 键 | 默认 | 说明 |
|---|---|---|
| `repoPath` | 进程 cwd | 仓库根目录，建议显式写 `/sdcard/diksu` |
| `remote` | `origin` | 远端名 |
| `tokenEnv` | `DSH_GIT_TOKEN` | token 环境变量名 |
| `hostsPin` | 空 | 形如 `20.27.177.113 github.com`，网络不稳时用；用完自动还原 |
| `authorName` / `authorEmail` | `dsh-agent` / `dsh-agent@localhost` | 提交作者 |

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
- 不做 `git config --global` 写入，不改用户凭据存储。

## 仓库内副本说明

本目录是插件的**源码副本**（不含 `node_modules` 软链），用于随仓库保存。
实际运行的是 `/root/dsha-diksu-git`，通过 profile 的 `link:` 依赖接入。

从零安装：

```bash
mkdir -p /root/dsha-diksu-git && cp -r tools/dsha-diksu-git/. /root/dsha-diksu-git/
ln -sfn /usr/local/lib/node_modules/@deepseek-ai/dsh/node_modules /root/dsha-diksu-git/node_modules
ln -sfn /root/dsha-diksu-git ~/.dsh/profiles/web/node_modules/dsha-diksu-git
# 并在 ~/.dsh/profiles/web/package.json 的 dependencies / bundles 里登记 dsha-diksu-git
export DSH_GIT_TOKEN=<PAT>
```

注意：`node_modules` 软链指向 dsh 安装目录，是为了让 `@deepseek-ai/dsh-tools` 可解析（本插件的唯一外部依赖）。
