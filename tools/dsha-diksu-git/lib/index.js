/**
 * dsha-diksu-git —— 给 DSH 用的 git 工具集（19 个工具）。
 *
 * 结构：
 * - `lib/core.js`       纯 Node 层：进程调用、参数校验、porcelain 解析、审计规则、
 *                       token 解析、重试分类、GitHub API、zip 解包。不依赖 DSH，可单测。
 * - `lib/tools-read.js` 只读：status / diff / log / show / audit / blame
 * - `lib/tools-write.js` 本地写：stage / commit / tag / branch / stash
 * - `lib/tools-remote.js` 远端：push / sync / auth（凭据诊断）
 * - `lib/tools-ci.js`   CI 与发版：release notes / dispatch / watch / logs / publish
 *
 * 这个文件只做三件事：声明配置、把 verify 过的执行结果包成 defineTool、注册。
 *
 * 2.0.0 相对 1.1.0 的关键修复：
 * - `repoPath` 默认值不再在模块加载时取 `process.cwd()`（那会是 harness 的启动目录
 *   /root，于是所有工具都在非仓库目录里跑，git_status 报 "value is not lossless JSON"）；
 *   现在默认空串 = 调用时自动定位（配置 → DSH_WORKSPACE/DSH_PROJECT_ROOT/DSH_CWD →
 *   当前目录向上找 .git），找不到就报一条能照着做的错。
 * - token 不再只读 `process.env`：插件配置（掩码保存）、token 文件、DSH 凭据服务
 *   （`ctx.credentials`，覆盖 ~/.dsh/.credentials.yaml）与 ~/.dsh/.env/进程环境四层都认。
 *   这就是「设了环境变量却读不到」的根因 —— Shell 设置里的变量只进 bash 子进程。
 * - 所有工具返回值统一过 `lossless()`：NaN/Infinity/undefined 不会再把 DSH 的
 *   输出校验炸成 "value is not lossless JSON"。
 * - 所有错误统一抹掉 token，并补上可执行提示。
 */
import z from '@deepseek-ai/schemastery'
import { defineTool } from '@deepseek-ai/dsh-tools'
import { lossless, mask, resolveToken } from './core.js'
import { registerReadTools } from './tools-read.js'
import { registerWriteTools } from './tools-write.js'
import { registerRemoteTools } from './tools-remote.js'
import { registerCiTools } from './tools-ci.js'
import { registerTokenRoute } from './token-route.js'

export const name = 'dsha-diksu-git'

export const inject = ['tools']

export const Config = z.object({
  repoPath: z
    .string()
    .default('')
    .description('仓库根目录的绝对路径；留空则调用时自动定位（DSH_WORKSPACE / DSH_PROJECT_ROOT / DSH_CWD / 当前目录向上查找）'),
  remote: z.string().default('origin').description('默认远端名'),
  token: z
    .string()
    .role('secret')
    .default('')
    .description('git token（PAT）。掩码保存，写入后不会回读；留空则走 tokenFile / DSH 凭据 / 环境变量'),
  tokenEnv: z
    .string()
    .role('credential-ref')
    .default('DSH_GIT_TOKEN')
    .description('token 的环境变量名/凭据引用名（默认 DSH_GIT_TOKEN，会自动尝试 GITHUB_TOKEN / GH_TOKEN / GITHUB_PAT）'),
  tokenFile: z.string().default('~/.dsh/git-token').description('token 文件路径（首行即 token，建议 chmod 600）'),
  apiBase: z.string().default('https://api.github.com').description('GitHub API 基地址'),
  hostsPin: z
    .string()
    .default('')
    .description('访问 GitHub 不稳定时临时钉 hosts，格式 "20.27.177.113 github.com"；动作结束后自动还原'),
  hostsFile: z.string().default('/etc/hosts').description('hosts 文件路径'),
  authorName: z.string().default('dsh-agent').description('提交/tag 作者名（-c user.name）'),
  authorEmail: z.string().default('dsh-agent@localhost').description('提交/tag 作者邮箱（-c user.email）'),
  maxAttempts: z.number().step(1).min(1).max(20).default(3).description('网络动作最多尝试次数'),
  retryDelayMs: z.number().step(1).min(0).default(3000).description('重试基础间隔（毫秒，按次数线性退避）'),
  protectedBranches: z
    .string()
    .default('main,master,develop,trunk,release/*,hotfix/*')
    .description('受保护分支（逗号分隔，* 结尾表示前缀匹配）；这些分支禁止强推与删除'),
  maxDiffBytes: z.number().step(1).min(1000).default(200000).description('diff/补丁输出上限（字节）'),
  auditMaxFileMB: z.number().step(1).min(1).default(2).description('提交审计的大文件阈值（MB）'),
})

/** 把错误变成「能照着做」的一句话，并抹掉 token。 */
async function explain(error, config, credentials) {
  if (error?.name === 'AbortError' || error?.code === 'ABORT_ERR') return '已取消'
  let text = String(error?.message ?? error ?? '未知错误')
  try {
    const hit = await resolveToken(config, credentials)
    text = mask(text, hit.value)
  } catch {
    // 没有 token 就没什么可抹的
  }
  if (/not a git repository|不是 git 仓库/i.test(text)) {
    text +=
      '\n提示：插件按「repoPath 配置 → DSH_WORKSPACE / DSH_PROJECT_ROOT / DSH_CWD → 当前目录向上查找 .git」定位仓库。' +
      '如果目标目录不是 git 仓库，请先在 设置 → 插件 → dsha-diksu-git 里填对 repoPath。'
  }
  if (/unable to auto-detect email address|Please tell me who you are/i.test(text)) {
    text += '\n提示：提交/tag 用的作者已由插件注入（authorName/authorEmail），如果你的 git 版本要求显式身份，请在插件配置里设置这两项。'
  }
  return text
}

export function apply(ctx, config) {
  const credentials = ctx.get?.('credentials')
  const registered = []

  const register = (spec) => {
    const tool = defineTool({
      name: spec.name,
      description: spec.description,
      parameters: spec.parameters,
      output: spec.output,
      async execute(args, exec) {
        try {
          return lossless(await spec.execute(args, exec))
        } catch (error) {
          throw new Error(await explain(error, config, credentials))
        }
      },
    })
    ctx.tools.register(tool)
    registered.push(tool.name)
  }

  const deps = { register, config, credentials, ctx }
  registerReadTools(deps)
  registerWriteTools(deps)
  registerRemoteTools(deps)
  registerCiTools(deps)

  // 浏览器端的 token 配置页（设置 → 插件 → Diksu Git 凭据）
  registerTokenRoute(ctx, config, credentials)

  ctx.logger?.info?.(`dsha-diksu-git: 已注册 ${registered.length} 个工具（${registered.join(', ')}）`)
  if (credentials === undefined) {
    ctx.logger?.warn?.('dsha-diksu-git: 没有找到 credentials 服务，token 只能从插件配置 / 文件 / 环境变量读取')
  }
}
