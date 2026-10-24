/**
 * dsha-diksu-git —— Diksu Ultra 专用 git / CI / 发布工具包
 *
 * 为什么需要它：这个项目的 git 操作有一堆"每次都手搓、每次都差点出错"的细节——
 * token 不能落盘、GitHub 直连不稳要钉 IP 重试、根目录的 APK 不能进仓库、
 * 提交作者固定、tag 与 versionCode 有严格对应关系、发版要从 CI 产物里挑 28 个资产。
 * 这里把这些固定动作做成工具，模型只需要说"推上去""发版"。
 *
 * 安全模型（重点）：
 * - **token 只从环境变量读取**，插件配置里只有变量名（默认 `DSH_GIT_TOKEN`）；
 * - 传 token 给 git 用的是临时 GIT_ASKPASS 脚本，脚本内容是 `echo "$DSH_GIT_TOKEN"`，
 *   文件里没有明文，用完立即删除；
 * - 所有工具输出都会把 token 替换成 `***` 再返回，避免被日志/上下文记录；
 * - 缺 token 时直接报错并说明怎么设置，不会退化成匿名请求。
 */
import { defineTool } from '@deepseek-ai/dsh-tools'
import z from '@deepseek-ai/schemastery'
import { execFile } from 'node:child_process'
import { mkdtemp, readFile, writeFile, unlink, chmod, readdir } from 'node:fs/promises'
import { tmpdir } from 'node:os'
import { join } from 'node:path'

export const name = 'dsha-diksu-git'
export const inject = ['tools']

export const Config = z.object({
  repoPath: z.string().default(process.cwd()).description('仓库根目录'),
  remote: z.string().default('origin').description('推送用的远端名'),
  tokenEnv: z.string().default('DSH_GIT_TOKEN').description('读取 token 的环境变量名（唯一推荐入口）'),
  apiBase: z.string().default('https://api.github.com').description('GitHub API 根地址'),
  hostsPin: z.string().default('').description('形如 "20.27.177.113 github.com"；留空表示不改 hosts'),
  hostsFile: z.string().default('/etc/hosts').description('hosts 文件路径'),
  authorName: z.string().default('dsh-agent').description('提交作者名'),
  authorEmail: z.string().default('dsh-agent@localhost').description('提交作者邮箱'),
  maxAttempts: z.number().default(12).description('推送/API 的最大尝试次数'),
  retryDelayMs: z.number().default(6000).description('重试间隔（毫秒）'),
})

// ────────────────────────────── 基础工具 ──────────────────────────────

const sleep = (ms) => new Promise((resolve) => setTimeout(resolve, ms))

/** 依次尝试多个环境变量名，拿到 token；拿不到就抛一条可执行的说明。 */
function resolveToken(config) {
  const names = [...new Set([config.tokenEnv, 'DSH_GIT_TOKEN', 'GITHUB_TOKEN', 'GH_TOKEN'])]
  for (const key of names) {
    const value = process.env[key]
    if (typeof value === 'string' && value.trim().length > 0) {
      return { key, value: value.trim() }
    }
  }
  throw new Error(
    `没有可用的 git token：请先设置环境变量 ${config.tokenEnv}（推荐）或 GITHUB_TOKEN / GH_TOKEN。\n` +
      '本插件不会把 token 写进任何文件，也不需要你把它贴进对话；' +
      '设置方法示例：export ' + config.tokenEnv + '=<你的 PAT>',
  )
}

/** 把 token 从任何输出里抹掉。 */
function mask(text, token) {
  if (typeof text !== 'string' || text.length === 0) return text
  if (typeof token !== 'string' || token.length === 0) return text
  return text.split(token).join('***')
}

function maskAll(value, token) {
  if (typeof value === 'string') return mask(value, token)
  if (Array.isArray(value)) return value.map((item) => maskAll(item, token))
  if (value && typeof value === 'object') {
    const out = {}
    for (const [k, v] of Object.entries(value)) out[k] = maskAll(v, token)
    return out
  }
  return value
}

/** 跑一条命令，永不因非零退出码抛异常（调用方自己判 code）。 */
function run(command, args, options = {}) {
  const { cwd, env, timeoutMs = 180_000, input } = options
  return new Promise((resolve) => {
    const child = execFile(
      command,
      args,
      {
        cwd,
        env: env ?? process.env,
        timeout: timeoutMs,
        maxBuffer: 32 * 1024 * 1024,
        encoding: 'utf8',
      },
      (error, stdout, stderr) => {
        resolve({
          code: error && typeof error.code === 'number' ? error.code : error ? 1 : 0,
          stdout: stdout ?? '',
          stderr: stderr ?? '',
          timedOut: Boolean(error && error.killed),
        })
      },
    )
    if (input !== undefined) {
      child.stdin?.end(input)
    }
  })
}

/** git 封装：固定带 -C 仓库路径，可选带作者身份。 */
async function git(config, args, options = {}) {
  const full = ['-C', config.repoPath]
  if (options.withAuthor) {
    full.push('-c', `user.name=${config.authorName}`, '-c', `user.email=${config.authorEmail}`)
  }
  full.push(...args)
  return run('git', full, options)
}

/** 取一条 git 输出（失败给空串）。 */
async function gitLine(config, args) {
  const result = await git(config, args)
  return result.code === 0 ? result.stdout.trim() : ''
}

/**
 * 临时 askpass：脚本本身只回显环境变量，token 走子进程环境，磁盘上没有明文。
 * 用完（含异常路径）一定删除。
 */
async function withAskpass(token, fn) {
  const dir = await mkdtemp(join(tmpdir(), 'diksu-git-'))
  const script = join(dir, 'askpass.sh')
  await writeFile(script, '#!/bin/sh\ncase "$1" in\n  *Username*) echo "x-access-token" ;;\n  *Password*) echo "$DSH_GIT_TOKEN" ;;\n  *) echo "" ;;\nesac\n', 'utf8')
  await chmod(script, 0o700)
  try {
    return await fn({
      GIT_ASKPASS: script,
      GIT_TERMINAL_PROMPT: '0',
      GIT_CONFIG_COUNT: '1',
      GIT_CONFIG_KEY_0: 'credential.helper',
      GIT_CONFIG_VALUE_0: '',
      DSH_GIT_TOKEN: token,
    })
  } finally {
    await unlink(script).catch(() => {})
    await unlink(dir).catch(() => {})
  }
}

/** 临时把 hosts 钉到指定 IP，结束后一定还原（网络不稳时这是最省事的办法）。 */
async function withHostsPin(config, fn) {
  const pin = (config.hostsPin ?? '').trim()
  if (pin.length === 0) return fn()
  let original
  try {
    original = await readFile(config.hostsFile, 'utf8')
  } catch {
    return fn()
  }
  try {
    await writeFile(config.hostsFile, `${original.replace(/\n?$/, '\n')}${pin}\n`, 'utf8')
  } catch {
    return fn()
  }
  try {
    return await fn()
  } finally {
    await writeFile(config.hostsFile, original, 'utf8').catch(() => {})
  }
}

/** 带重试地跑一个可能因网络失败的动作。 */
async function retry(config, label, attemptFn) {
  let last = ''
  const attempts = Math.max(1, config.maxAttempts)
  for (let i = 1; i <= attempts; i += 1) {
    const outcome = await attemptFn(i)
    if (outcome?.ok) return outcome
    last = outcome?.detail ?? '未知错误'
    if (i < attempts) await sleep(config.retryDelayMs)
  }
  return { ok: false, detail: `${label} 连续失败 ${attempts} 次：${last}` }
}

/** GitHub API 调用。 */
async function api(config, token, method, path, body) {
  const url = path.startsWith('http') ? path : `${config.apiBase}${path}`
  const response = await fetch(url, {
    method,
    headers: {
      Authorization: `Bearer ${token}`,
      Accept: 'application/vnd.github+json',
      'Content-Type': 'application/json',
      'User-Agent': 'dsha-diksu-git',
    },
    body: body === undefined ? undefined : JSON.stringify(body),
    signal: AbortSignal.timeout(120_000),
  })
  const text = await response.text()
  let parsed
  try {
    parsed = text.length > 0 ? JSON.parse(text) : {}
  } catch {
    parsed = { raw: text }
  }
  if (!response.ok) {
    const message = parsed?.message ?? response.statusText
    const error = new Error(`GitHub API ${method} ${path} → ${response.status} ${message}`)
    error.status = response.status
    throw error
  }
  return parsed
}

/** 按版本号公式算 versionCode：major*10000 + minor*1000 + patch*100 + tag 之后（--first-parent）的提交数。 */
async function versionInfo(config) {
  const tag = await gitLine(config, ['describe', '--tags', '--abbrev=0', '--match', 'v[0-9]*'])
  if (tag.length === 0) return { tag: '', versionCode: null, commitsAfterTag: null }
  const match = /^v(\d+)\.(\d+)\.(\d+)/.exec(tag)
  if (match === null) return { tag, versionCode: null, commitsAfterTag: null }
  const [, major, minor, patch] = match.map(Number)
  const countText = await gitLine(config, ['rev-list', '--count', '--first-parent', `${tag}..HEAD`])
  const commitsAfterTag = Number.parseInt(countText, 10)
  const versionCode = major * 10000 + minor * 1000 + patch * 100 + (Number.isNaN(commitsAfterTag) ? 0 : commitsAfterTag)
  return { tag, versionCode, commitsAfterTag: Number.isNaN(commitsAfterTag) ? null : commitsAfterTag }
}

// ────────────────────────────── 工具注册 ──────────────────────────────

export function apply(ctx, config) {
  const tools = []
  const register = (tool) => {
    ctx.tools.register(tool)
    tools.push(tool.name)
  }

  // 1. 仓库状态
  register(
    defineTool({
      name: 'git_status',
      description:
        '读取仓库状态：当前分支、HEAD、最近 tag 与 versionCode、工作区改动、未跟踪的 APK 产物、与远端的领先/落后。提交或推送前先看一眼。',
      parameters: {
        fetch: { type: 'boolean', description: '是否先 fetch 远端（默认 false，网络不稳时省一次往返）' },
      },
      output: {
        schema: {
          type: 'object',
          additionalProperties: false,
          properties: {
            branch: { type: 'string', required: true },
            head: { type: 'string', required: true },
            tag: { type: 'string', required: true },
            versionCode: { type: 'number' },
            dirty: { type: 'array', required: true, items: { type: 'string' } },
            apkLeftovers: { type: 'array', required: true, items: { type: 'string' } },
            ahead: { type: 'number', required: true },
            behind: { type: 'number', required: true },
            summary: { type: 'string', required: true },
          },
        },
        render: (_args, value) => [{ type: 'text', text: value.summary }],
      },
      async execute(args, exec) {
        exec.signal?.throwIfAborted?.()
        if (args.fetch === true) await git(config, ['fetch', config.remote, '--prune'])
        const branch = await gitLine(config, ['rev-parse', '--abbrev-ref', 'HEAD'])
        const head = await gitLine(config, ['rev-parse', '--short=9', 'HEAD'])
        const version = await versionInfo(config)
        const status = await git(config, ['status', '--porcelain'])
        const dirty = status.stdout
          .split('\n')
          .map((line) => line.trimEnd())
          .filter((line) => line.length > 0)
        const apkLeftovers = dirty
          .map((line) => line.slice(3).trim())
          .filter((path) => /\.apk$/i.test(path))
        const counts = await gitLine(config, ['rev-list', '--left-right', '--count', `${config.remote}/${branch}...HEAD`])
        const [behindText, aheadText] = counts.split(/\s+/)
        const ahead = Number.parseInt(aheadText ?? '0', 10) || 0
        const behind = Number.parseInt(behindText ?? '0', 10) || 0
        const summary = [
          `分支 ${branch} @ ${head}`,
          version.tag.length > 0
            ? `tag ${version.tag} · versionCode ${version.versionCode}（tag 后 ${version.commitsAfterTag} 个提交）`
            : '尚无版本 tag',
          `领先远端 ${ahead} / 落后 ${behind}`,
          dirty.length === 0 ? '工作区干净' : `工作区有 ${dirty.length} 项改动`,
          apkLeftovers.length === 0 ? '' : `⚠️ 根目录有 APK 产物（不要提交）：${apkLeftovers.join(', ')}`,
        ]
          .filter((line) => line.length > 0)
          .join('\n')
        return {
          branch,
          head,
          tag: version.tag,
          // 注意：不能写 `versionCode: null ?? undefined` —— 显式 undefined 会让工具输出
          // 校验报 "value is not lossless JSON"。没有 tag 时直接省略这个键。
          ...(version.versionCode === null ? {} : { versionCode: version.versionCode }),
          dirty,
          apkLeftovers,
          ahead,
          behind,
          summary,
        }
      },
    }),
  )

  // 2. 提交
  register(
    defineTool({
      name: 'git_commit',
      description:
        '按本项目的约定提交：固定作者 dsh-agent、拒绝把 *.apk 提交进仓库（除非显式放行）、返回哈希与改动统计。传 paths=["."] 表示全部改动。',
      parameters: {
        paths: {
          type: 'array',
          required: true,
          description: '要提交的路径列表；["."] 表示全部改动',
          items: { type: 'string' },
        },
        message: { type: 'string', required: true, description: '提交信息（中文，type(scope): 摘要）' },
        allowApk: { type: 'boolean', description: '显式放行 APK（默认 false；正常情况永远不要开）' },
        allowEmpty: { type: 'boolean', description: '没有改动也建一个空提交（默认 false）' },
      },
      output: {
        schema: {
          type: 'object',
          additionalProperties: false,
          properties: {
            committed: { type: 'boolean', required: true },
            hash: { type: 'string', required: true },
            files: { type: 'array', required: true, items: { type: 'string' } },
            stat: { type: 'string', required: true },
            message: { type: 'string', required: true },
          },
        },
        render: (_args, value) => [{ type: 'text', text: value.message }],
      },
      async execute(args, exec) {
        exec.signal?.throwIfAborted?.()
        if (args.paths.length === 0) throw new Error('paths 不能为空；要提交全部改动请传 ["."]')
        const add = await git(config, ['add', '--', ...args.paths])
        if (add.code !== 0) throw new Error(`git add 失败：${add.stderr.trim()}`)

        const staged = await git(config, ['diff', '--cached', '--name-status'])
        const stagedEntries = staged.stdout
          .split('\n')
          .map((line) => line.trim())
          .filter(Boolean)
          .map((line) => {
            const [statusText, ...rest] = line.split('\t')
            return { status: statusText.charAt(0), path: rest[rest.length - 1] ?? '' }
          })
        const stagedFiles = stagedEntries.map((entry) => entry.path).filter(Boolean)
        // 只有"新增/修改"APK 才算问题；把 APK 从索引里删掉（D）恰恰是我们要的。
        const apk = stagedEntries
          .filter((entry) => entry.status !== 'D' && /\.apk$/i.test(entry.path))
          .map((entry) => entry.path)
        if (apk.length > 0 && args.allowApk !== true) {
          await git(config, ['reset', '--', ...apk])
          throw new Error(
            `拒绝提交 APK 产物：${apk.join(', ')}。构建产物不进仓库（根目录 *.apk 已在 .gitignore 里，` +
              '如果被 add 进来通常是用了 git add -A 且 .gitignore 没覆盖到）。',
          )
        }
        if (stagedFiles.length === 0 && args.allowEmpty !== true) {
          return {
            committed: false,
            hash: await gitLine(config, ['rev-parse', '--short=9', 'HEAD']),
            files: [],
            stat: '',
            message: '没有可提交的改动（工作区干净）',
          }
        }

        const commitArgs = ['commit', '-m', args.message]
        if (stagedFiles.length === 0) commitArgs.push('--allow-empty')
        const commit = await git(config, commitArgs, { withAuthor: true })
        if (commit.code !== 0) throw new Error(`git commit 失败：${commit.stderr.trim() || commit.stdout.trim()}`)

        const hash = await gitLine(config, ['rev-parse', '--short=9', 'HEAD'])
        const stat = await gitLine(config, ['show', '--stat', '--oneline', '--format=', 'HEAD'])
        const subject = await gitLine(config, ['log', '-1', '--pretty=%s'])
        return {
          committed: true,
          hash,
          files: stagedFiles,
          stat,
          message: `已提交 ${hash}：${subject}\n${stat}`,
        }
      },
    }),
  )

  // 3. 推送
  register(
    defineTool({
      name: 'git_push',
      description:
        '推送分支或 tag。token 从环境变量读取（临时 askpass，不落盘），可钉 hosts 并自动重试；推送后用 ls-remote 校验远端确实指向本地提交。',
      parameters: {
        ref: { type: 'string', description: '要推送的分支名（默认当前分支）' },
        tag: { type: 'string', description: '要推送的 tag 名（给了就只推这个 tag）' },
        force: { type: 'boolean', description: '强制推送（改写历史或移动 tag 时才用）' },
      },
      output: {
        schema: {
          type: 'object',
          additionalProperties: false,
          properties: {
            ok: { type: 'boolean', required: true },
            remoteSha: { type: 'string', required: true },
            localSha: { type: 'string', required: true },
            message: { type: 'string', required: true },
          },
        },
        render: (_args, value) => [{ type: 'text', text: value.message }],
      },
      async execute(args, exec) {
        exec.signal?.throwIfAborted?.()
        const { value: token } = resolveToken(config)
        const localSha = await gitLine(config, ['rev-parse', 'HEAD'])
        const branch = await gitLine(config, ['rev-parse', '--abbrev-ref', 'HEAD'])
        const target = args.tag !== undefined && args.tag.length > 0 ? `refs/tags/${args.tag}` : args.ref ?? branch

        const outcome = await withAskpass(token, (askEnv) => {
          const env = { ...process.env, ...askEnv }
          return withHostsPin(config, () =>
            retry(config, 'git push', async () => {
              const pushArgs = ['push']
              if (args.force === true) pushArgs.push('--force')
              pushArgs.push(config.remote, target)
              const result = await git(config, pushArgs, { timeoutMs: 240_000, env })
              if (result.code === 0) return { ok: true }
              const detail = mask((result.stderr || result.stdout).trim().split('\n').slice(-1)[0] ?? '', token)
              return { ok: false, detail }
            }),
          )
        })

        if (!outcome.ok) {
          throw new Error(mask(String(outcome.detail), token))
        }

        // 校验远端引用
        const check = await withAskpass(token, (askEnv) => {
          const env = { ...process.env, ...askEnv }
          return withHostsPin(config, () => git(config, ['ls-remote', config.remote, target], { timeoutMs: 120_000, env }))
        })
        const remoteSha = check.stdout.trim().split(/\s+/)[0] ?? ''
        const expected = target.startsWith('refs/tags/') ? await gitLine(config, ['rev-parse', `${target}^{commit}`]) : localSha
        const ok = remoteSha.startsWith(expected) || remoteSha === expected
        return {
          ok,
          remoteSha,
          localSha: expected,
          message: ok
            ? `已推送 ${target} → 远端 ${remoteSha.slice(0, 9)}`
            : `推送命令成功，但远端 ${target} = ${remoteSha || '(空)'}，本地 = ${expected}，请复查`,
        }
      },
    }),
  )

  // 4. 打 tag
  register(
    defineTool({
      name: 'git_tag',
      description:
        '在 HEAD 上创建（或强制移动）tag，并算出对应的 versionCode。本项目发版必须打 vX.Y.Z-ultra 形式的 tag，且 tag 要打在最终 HEAD 上。',
      parameters: {
        tag: { type: 'string', required: true, description: '形如 v4.2.0-ultra' },
        force: { type: 'boolean', description: '已存在时强制移动（默认 false）' },
      },
      output: {
        schema: {
          type: 'object',
          additionalProperties: false,
          properties: {
            created: { type: 'boolean', required: true },
            tag: { type: 'string', required: true },
            commit: { type: 'string', required: true },
            versionCode: { type: 'number' },
            message: { type: 'string', required: true },
          },
        },
        render: (_args, value) => [{ type: 'text', text: value.message }],
      },
      async execute(args, exec) {
        exec.signal?.throwIfAborted?.()
        if (!/^v\d+\.\d+\.\d+/.test(args.tag)) {
          throw new Error(`tag 名不合规：${args.tag}（本项目用 vX.Y.Z-ultra，版本公式依赖这个前缀）`)
        }
        const tagArgs = ['tag']
        if (args.force === true) tagArgs.push('-f')
        tagArgs.push(args.tag)
        const result = await git(config, tagArgs)
        if (result.code !== 0) throw new Error(`git tag 失败：${result.stderr.trim()}`)
        const commit = await gitLine(config, ['rev-parse', 'HEAD'])
        const version = await versionInfo(config)
        return {
          created: true,
          tag: args.tag,
          commit,
          // 同上：versionCode 为 null 时省略键，避免非 lossless JSON。
          ...(version.versionCode === null ? {} : { versionCode: version.versionCode }),
          message:
            `已打 tag ${args.tag} @ ${commit.slice(0, 9)}；当前 versionCode = ${version.versionCode}` +
            '（tag 后 0 个提交时最干净）。别忘了 git_push 推 tag。',
        }
      },
    }),
  )

  // 5. 触发 CI
  register(
    defineTool({
      name: 'ci_dispatch',
      description:
        '触发 GitHub Actions 的 workflow_dispatch 并返回 run id。本项目的自用精简包就是 dispatch build-manager.yml 且 inputs.abi=arm64-v8a。',
      parameters: {
        workflow: { type: 'string', description: '工作流文件名（默认 build-manager.yml）' },
        ref: { type: 'string', description: '分支或 tag（默认当前分支）' },
        inputs: {
          type: 'object',
          description: 'dispatch 输入，例如 {"abi":"arm64-v8a"}',
          additionalProperties: true,
          properties: {},
        },
      },
      output: {
        schema: {
          type: 'object',
          additionalProperties: false,
          properties: {
            dispatched: { type: 'boolean', required: true },
            runId: { type: 'number' },
            runUrl: { type: 'string', required: true },
            message: { type: 'string', required: true },
          },
        },
        render: (_args, value) => [{ type: 'text', text: value.message }],
      },
      async execute(args, exec) {
        exec.signal?.throwIfAborted?.()
        const { value: token } = resolveToken(config)
        const workflow = args.workflow ?? 'build-manager.yml'
        const ref = args.ref ?? (await gitLine(config, ['rev-parse', '--abbrev-ref', 'HEAD']))
        const repo = await repoSlug(config)
        await api(config, token, 'POST', `/repos/${repo}/actions/workflows/${workflow}/dispatches`, {
          ref,
          inputs: args.inputs ?? {},
        })
        // dispatch 不返回 run id，稍等后按分支 + 事件类型找最新一条
        const headSha = await gitLine(config, ['rev-parse', 'HEAD'])
        let runId
        let runUrl = ''
        for (let i = 0; i < 10; i += 1) {
          await sleep(3000)
          const runs = await api(config, token, 'GET', `/repos/${repo}/actions/runs?per_page=10&event=workflow_dispatch`)
          const hit = (runs.workflow_runs ?? []).find(
            (run) => run.head_branch === ref && String(run.head_sha).startsWith(headSha.slice(0, 8)),
          )
          if (hit !== undefined) {
            runId = hit.id
            runUrl = hit.html_url
            break
          }
        }
        return {
          dispatched: true,
          runId,
          runUrl,
          message:
            runId === undefined
              ? `已触发 ${workflow} @ ${ref}，但没找到对应的 run（可在 Actions 页面确认）`
              : `已触发 ${workflow} @ ${ref} → run ${runId}\n${runUrl}`,
        }
      },
    }),
  )

  // 6. 等 CI
  register(
    defineTool({
      name: 'ci_watch',
      description:
        '轮询一条 workflow run 直到结束，返回结论与失败的 job/步骤。本项目本地没有 Android SDK，编译验证**只能**靠 CI，所以这个工具是改完代码后的必经一步。',
      parameters: {
        runId: { type: 'number', description: 'run id（与 sha 二选一）' },
        sha: { type: 'string', description: '按提交 sha 找最新的 run' },
        timeoutSeconds: { type: 'number', description: '最长等待秒数（默认 1800）' },
      },
      output: {
        schema: {
          type: 'object',
          additionalProperties: false,
          properties: {
            runId: { type: 'number', required: true },
            conclusion: { type: 'string', required: true },
            failed: { type: 'array', required: true, items: { type: 'string' } },
            message: { type: 'string', required: true },
          },
        },
        render: (_args, value) => [{ type: 'text', text: value.message }],
      },
      async execute(args, exec) {
        const { value: token } = resolveToken(config)
        const repo = await repoSlug(config)
        const deadline = Date.now() + Math.max(60, args.timeoutSeconds ?? 1800) * 1000
        let runId = args.runId
        if (runId === undefined) {
          if (args.sha === undefined) throw new Error('需要 runId 或 sha')
          const runs = await api(config, token, 'GET', `/repos/${repo}/actions/runs?per_page=20`)
          const hit = (runs.workflow_runs ?? []).find((run) => String(run.head_sha).startsWith(args.sha))
          if (hit === undefined) throw new Error(`找不到 sha ${args.sha} 对应的 run`)
          runId = hit.id
        }
        let conclusion = 'unknown'
        for (;;) {
          exec.signal?.throwIfAborted?.()
          const run = await api(config, token, 'GET', `/repos/${repo}/actions/runs/${runId}`)
          if (run.status === 'completed') {
            conclusion = run.conclusion ?? 'unknown'
            break
          }
          if (Date.now() > deadline) {
            conclusion = 'timeout'
            break
          }
          await sleep(30_000)
        }
        const failed = []
        if (conclusion !== 'success') {
          const jobs = await api(config, token, 'GET', `/repos/${repo}/actions/runs/${runId}/jobs`)
          for (const job of jobs.jobs ?? []) {
            if (job.conclusion === 'success' || job.conclusion === 'skipped' || job.conclusion === null) continue
            const steps = (job.steps ?? [])
              .filter((step) => step.conclusion !== 'success' && step.conclusion !== 'skipped' && step.conclusion !== null)
              .map((step) => step.name)
            failed.push(`${job.name} [${job.conclusion}]${steps.length > 0 ? ` → ${steps.join(' / ')}` : ''}`)
          }
        }
        return {
          runId,
          conclusion,
          failed,
          message:
            conclusion === 'success'
              ? `run ${runId} 成功`
              : `run ${runId} 结论 ${conclusion}${failed.length > 0 ? '\n失败项：\n- ' + failed.join('\n- ') : ''}`,
        }
      },
    }),
  )

  // 7. 发版
  register(
    defineTool({
      name: 'release_publish',
      description:
        '从一条已完成的 Release run 里挑出全部产物（管理器 APK、8 个 ksud、3 个 ksuinit、16 个 LKM），建 release、传资产并发布。资产命名规则与本项目历次发布一致。',
      parameters: {
        tag: { type: 'string', required: true, description: '已存在的 tag，例如 v4.2.0-ultra' },
        runId: { type: 'number', required: true, description: 'Release workflow 的 run id（提供产物）' },
        title: { type: 'string', description: '发布标题（默认与 tag 相同）' },
        bodyFile: { type: 'string', description: '发布说明 Markdown 文件路径（相对仓库）' },
        draft: { type: 'boolean', description: '只建草稿不发布（默认 false）' },
      },
      output: {
        schema: {
          type: 'object',
          additionalProperties: false,
          properties: {
            url: { type: 'string', required: true },
            assets: { type: 'array', required: true, items: { type: 'string' } },
            message: { type: 'string', required: true },
          },
        },
        render: (_args, value) => [{ type: 'text', text: value.message }],
      },
      async execute(args, exec) {
        exec.signal?.throwIfAborted?.()
        const { value: token } = resolveToken(config)
        const repo = await repoSlug(config)
        const body =
          args.bodyFile !== undefined && args.bodyFile.length > 0
            ? await readFile(join(config.repoPath, args.bodyFile), 'utf8')
            : `## ${args.title ?? args.tag}\n`

        const release = await api(config, token, 'POST', `/repos/${repo}/releases`, {
          tag_name: args.tag,
          name: args.title ?? args.tag,
          body,
          draft: true,
          prerelease: false,
        })

        const artifacts = await api(config, token, 'GET', `/repos/${repo}/actions/runs/${args.runId}/artifacts`)
        const workDir = await mkdtemp(join(tmpdir(), 'diksu-release-'))
        const uploaded = []
        for (const artifact of artifacts.artifacts ?? []) {
          const artifactName = artifact.name
          if (artifactName === 'manager-gradle' || artifactName === 'mappings') continue
          let assetName
          if (artifactName === 'manager') assetName = undefined
          else if (artifactName.startsWith('ksud-')) assetName = artifactName + (artifactName.includes('windows') ? '.exe' : '')
          else if (artifactName.startsWith('ksuinit-')) assetName = artifactName
          else if (artifactName.endsWith('-lkm')) assetName = `lkm-${artifactName.slice(0, -4)}_kernelsu.ko`
          else continue

          const zipPath = join(workDir, `${artifactName}.zip`)
          const download = await fetch(artifact.archive_download_url, {
            headers: { Authorization: `Bearer ${token}`, Accept: 'application/vnd.github+json' },
            signal: AbortSignal.timeout(300_000),
          })
          if (!download.ok) throw new Error(`下载产物 ${artifactName} 失败：${download.status}`)
          const buffer = Buffer.from(await download.arrayBuffer())
          await writeFile(zipPath, buffer)

          const extractDir = join(workDir, artifactName)
          await run('unzip', ['-o', '-q', zipPath, '-d', extractDir])
          const entries = await readdir(extractDir, { recursive: true, withFileTypes: true })
          const files = entries
            .filter((entry) => entry.isFile() && !entry.name.startsWith('.'))
            .map((entry) => join(entry.parentPath ?? entry.path, entry.name))
          const inner =
            files.find((file) => file.endsWith('.apk')) ??
            files.find((file) => ['ksud', 'ksud.exe', 'ksuinit', 'kernelsu.ko'].includes(file.split('/').pop())) ??
            files[0]
          if (inner === undefined) continue
          const finalName = assetName ?? inner.split('/').pop()

          const bytes = await readFile(inner)
          const uploadUrl = `${release.upload_url.split('{')[0]}?name=${encodeURIComponent(finalName)}`
          const response = await fetch(uploadUrl, {
            method: 'POST',
            headers: {
              Authorization: `Bearer ${token}`,
              Accept: 'application/vnd.github+json',
              'Content-Type': 'application/octet-stream',
            },
            body: bytes,
            signal: AbortSignal.timeout(300_000),
          })
          if (!response.ok) throw new Error(`上传 ${finalName} 失败：${response.status}`)
          uploaded.push(finalName)
        }

        if (args.draft !== true) {
          await api(config, token, 'PATCH', `/repos/${repo}/releases/${release.id}`, {
            draft: false,
            make_latest: 'true',
          })
        }
        const apk = uploaded.find((file) => file.endsWith('.apk')) ?? '(没有找到 APK)'
        return {
          url: release.html_url,
          assets: uploaded,
          message:
            `${args.draft === true ? '草稿' : '已发布'} ${args.title ?? args.tag}：${uploaded.length} 个资产\n` +
            `APK：${apk}\n${release.html_url}`,
        }
      },
    }),
  )

  ctx.logger?.info?.(`dsha-diksu-git: 已注册 ${tools.length} 个工具（${tools.join(', ')}）`)
}

/** 从远端地址解析 owner/repo。 */
async function repoSlug(config) {
  const url = await gitLine(config, ['remote', 'get-url', config.remote])
  const match = /github\.com[:/]([^/]+)\/([^/.]+)(?:\.git)?$/.exec(url)
  if (match === null) throw new Error(`无法从远端地址解析 owner/repo：${url}`)
  return `${match[1]}/${match[2]}`
}
