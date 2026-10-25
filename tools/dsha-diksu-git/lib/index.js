/**
 * dsha-diksu-git —— Diksu Ultra 专用 git / CI / 发布工具包
 *
 * 为什么需要它：这个项目的 git 操作有一堆"每次都手搓、每次都差点出错"的细节——
 * token 不能落盘、GitHub 直连不稳要钉 IP 重试、根目录的 APK 不能进仓库、
 * 提交作者固定、tag 与 versionCode 有严格对应关系、发版要从 CI 产物里挑 28 个资产。
 * 这里把这些固定动作做成工具，模型只需要说"推上去""发版"。
 *
 * 1.1.0 起参考了社区 Git 插件（dsh-git-tools / dsh-plugin-git-workflow /
 * dsh-gitflow / dsh-plugin-git-workbench / dsh-branch-cleaner / dsh-git-manager）
 * 的成熟做法，补齐了 diff / log / show / stage / branch / stash / sync / audit /
 * blame / CI 日志 / release notes 这些日常动作，同时保留本项目自己的发布链路。
 *
 * 安全模型（重点）：
 * - **token 只从环境变量读取**，插件配置里只有变量名（默认 `DSH_GIT_TOKEN`）；
 * - 传 token 给 git 用的是临时 GIT_ASKPASS 脚本，脚本内容是 `echo "$DSH_GIT_TOKEN"`，
 *   文件里没有明文，用完立即删除；
 * - 所有工具输出都会把 token 替换成 `***` 再返回，避免被日志/上下文记录；
 * - 缺 token 时直接报错并说明怎么设置，不会退化成匿名请求；
 * - 路径参数只接受仓库内相对路径，拒绝绝对路径与 `..`；
 * - main/master/develop/trunk/release/* 等受保护分支禁止强推与删除（可显式放行）。
 */
import { defineTool } from '@deepseek-ai/dsh-tools'
import z from '@deepseek-ai/schemastery'
import { execFile } from 'node:child_process'
import { mkdtemp, readFile, writeFile, unlink, chmod, readdir, stat as fsStat, access } from 'node:fs/promises'
import { tmpdir } from 'node:os'
import { join, resolve } from 'node:path'

export const name = 'dsha-diksu-git'
export const inject = ['tools']

export const Config = z.object({
  repoPath: z.string().default(process.cwd()).description('仓库根目录'),
  remote: z.string().default('origin').description('推送/拉取用的远端名'),
  tokenEnv: z.string().default('DSH_GIT_TOKEN').description('读取 token 的环境变量名（唯一推荐入口）'),
  apiBase: z.string().default('https://api.github.com').description('GitHub API 根地址'),
  hostsPin: z.string().default('').description('形如 "20.27.177.113 github.com"；留空表示不改 hosts'),
  hostsFile: z.string().default('/etc/hosts').description('hosts 文件路径'),
  authorName: z.string().default('dsh-agent').description('提交作者名'),
  authorEmail: z.string().default('dsh-agent@localhost').description('提交作者邮箱'),
  maxAttempts: z.number().default(12).description('推送/拉取/API 的最大尝试次数'),
  retryDelayMs: z.number().default(6000).description('重试间隔（毫秒）'),
  protectedBranches: z
    .string()
    .default('main,master,develop,trunk,release/*,hotfix/*')
    .description('受保护分支，逗号分隔，支持末尾 * 前缀匹配'),
  maxDiffBytes: z.number().default(200_000).description('diff / patch 输出的字节上限（超出截断）'),
  auditMaxFileMB: z.number().default(2).description('提交审计：单文件超过多少 MB 视为大文件'),
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
        maxBuffer: 64 * 1024 * 1024,
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

/** 取一条 git 输出，失败抛错。 */
async function gitOrThrow(config, args, label) {
  const result = await git(config, args)
  if (result.code !== 0) {
    throw new Error(`${label || 'git 命令'}失败：${(result.stderr || result.stdout).trim()}`)
  }
  return result.stdout
}

/** 路径参数校验：只接受仓库内相对路径，拒绝绝对路径、盘符与 .. 穿越。 */
function safePath(input) {
  if (typeof input !== 'string' || input.trim().length === 0) throw new Error('路径不能为空')
  if (input.includes('\0')) throw new Error('路径不能包含 NUL')
  if (input.startsWith('/') || /^[A-Za-z]:[\\/]/.test(input)) {
    throw new Error(`只接受仓库内相对路径（不要绝对路径）：${input}`)
  }
  const normalized = input.replace(/\\/g, '/')
  if (normalized.split('/').includes('..')) throw new Error(`路径不能包含 ..：${input}`)
  return normalized
}

/** 批量校验路径参数。 */
function safePaths(paths) {
  if (!Array.isArray(paths)) return []
  return paths.map((item) => safePath(item))
}

/** 分支名校验：拒绝空白、..、~^:?*[\\、以 - 开头等 git 不接受的形式。 */
function safeBranchName(name) {
  if (typeof name !== 'string' || name.trim().length === 0) throw new Error('分支名不能为空')
  const value = name.trim()
  if (value.startsWith('-') || /[\s~^:?*\[\\]/.test(value) || value.includes('..') || value.endsWith('/') || value.endsWith('.lock')) {
    throw new Error(`分支名不合规：${name}`)
  }
  return value
}

/** 版本/范围参数校验：允许 HEAD~1、v1..HEAD 这类 revision 语法，但拒绝空白与选项注入。 */
function safeRevision(rev, label = 'ref') {
  if (typeof rev !== 'string' || rev.trim().length === 0) throw new Error(`${label} 不能为空`)
  const value = rev.trim()
  if (value.startsWith('-') || /\s/.test(value) || value.includes('\0')) throw new Error(`${label} 不合规：${rev}`)
  return value
}

/** 判断分支是否受保护。 */
function isProtectedBranch(config, branch) {
  const patterns = String(config.protectedBranches ?? '')
    .split(',')
    .map((item) => item.trim())
    .filter(Boolean)
  return patterns.some((pattern) =>
    pattern.endsWith('*') ? branch.startsWith(pattern.slice(0, -1)) : branch === pattern,
  )
}

/** 按字节截断文本，返回是否截断。 */
function truncateText(text, maxBytes) {
  const buffer = Buffer.from(text, 'utf8')
  if (buffer.length <= maxBytes) return { text, truncated: false }
  return { text: buffer.subarray(0, maxBytes).toString('utf8') + '\n…（已截断）', truncated: true }
}

/** 临时 askpass：脚本本身只回显环境变量，token 走子进程环境，磁盘上没有明文。 */
async function withAskpass(token, fn) {
  const dir = await mkdtemp(join(tmpdir(), 'diksu-git-'))
  const script = join(dir, 'askpass.sh')
  await writeFile(
    script,
    '#!/bin/sh\ncase "$1" in\n  *Username*) echo "x-access-token" ;;\n  *Password*) echo "$DSH_GIT_TOKEN" ;;\n  *) echo "" ;;\nesac\n',
    'utf8',
  )
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

/** 带 token / hostsPin / 重试的 git 网络动作。 */
async function networkGit(config, token, label, buildArgs, options = {}) {
  return withAskpass(token, (askEnv) => {
    const env = { ...process.env, ...askEnv }
    return withHostsPin(config, () =>
      retry(config, label, async () => {
        const result = await git(config, buildArgs(), { ...options, env })
        if (result.code === 0) return { ok: true, result }
        const detail = mask((result.stderr || result.stdout).trim().split('\n').slice(-1)[0] ?? '', token)
        return { ok: false, detail }
      }),
    )
  })
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

/** 当前分支名（detached 时返回 HEAD）。 */
async function currentBranch(config) {
  return (await gitLine(config, ['rev-parse', '--abbrev-ref', 'HEAD'])) || 'HEAD'
}

/** 上游分支名（没有则空串）。 */
async function upstreamBranch(config) {
  return gitLine(config, ['rev-parse', '--abbrev-ref', '--symbolic-full-name', '@{upstream}'])
}

/** 领先/落后上游；没有上游时都返回 0。 */
async function aheadBehind(config) {
  const upstream = await upstreamBranch(config)
  if (!upstream) return { upstream: '', ahead: 0, behind: 0 }
  const counts = await gitLine(config, ['rev-list', '--left-right', '--count', `${upstream}...HEAD`])
  const [behindText, aheadText] = counts.split(/\s+/)
  return {
    upstream,
    ahead: Number.parseInt(aheadText ?? '0', 10) || 0,
    behind: Number.parseInt(behindText ?? '0', 10) || 0,
  }
}

/** 解析 porcelain 状态，拆成 staged / unstaged / untracked / conflicts。 */
function parseStatus(text) {
  const staged = []
  const unstaged = []
  const untracked = []
  const conflicts = []
  const conflictCodes = new Set(['DD', 'AU', 'UD', 'UA', 'DU', 'AA', 'UU'])
  for (const line of text.split('\n')) {
    if (line.length < 3) continue
    const x = line[0]
    const y = line[1]
    const path = line.slice(3).trim()
    if (x === '?' && y === '?') {
      untracked.push(path)
      continue
    }
    if (conflictCodes.has(`${x}${y}`)) {
      conflicts.push(path)
      continue
    }
    if (x !== ' ' && x !== '?') staged.push(`${x} ${path}`)
    if (y !== ' ' && y !== '?') unstaged.push(`${y} ${path}`)
  }
  return { staged, unstaged, untracked, conflicts }
}

/** 读取 .git 目录，判断是否有进行中的 merge / rebase / cherry-pick 等。 */
async function inProgressOperations(config) {
  const gitDir = await gitLine(config, ['rev-parse', '--git-dir'])
  if (!gitDir) return []
  const root = resolve(config.repoPath, gitDir)
  const checks = [
    ['merge', 'MERGE_HEAD'],
    ['rebase', 'rebase-merge'],
    ['rebase', 'rebase-apply'],
    ['cherry-pick', 'CHERRY_PICK_HEAD'],
    ['revert', 'REVERT_HEAD'],
    ['bisect', 'BISECT_LOG'],
  ]
  const found = []
  for (const [label, file] of checks) {
    try {
      await access(join(root, file))
      if (!found.includes(label)) found.push(label)
    } catch {
      // 不存在就是没在进行
    }
  }
  return found
}

/** 按版本号公式算 versionCode：major*10000 + minor*1000 + patch*100 + tag 之后（--first-parent）的提交数。 */
async function versionInfo(config) {
  const tag = await gitLine(config, ['describe', '--tags', '--abbrev=0', '--match', 'v[0-9]*'])
  const headTag = await gitLine(config, ['describe', '--tags', '--exact-match', '--match', 'v[0-9]*'])
  if (tag.length === 0) return { tag: '', headTag: '', versionCode: null, commitsAfterTag: null }
  const match = /^v(\d+)\.(\d+)\.(\d+)/.exec(tag)
  if (match === null) return { tag, headTag, versionCode: null, commitsAfterTag: null }
  const [, major, minor, patch] = match.map(Number)
  const countText = await gitLine(config, ['rev-list', '--count', '--first-parent', `${tag}..HEAD`])
  const commitsAfterTag = Number.parseInt(countText, 10)
  const versionCode = major * 10000 + minor * 1000 + patch * 100 + (Number.isNaN(commitsAfterTag) ? 0 : commitsAfterTag)
  return {
    tag,
    headTag,
    versionCode,
    commitsAfterTag: Number.isNaN(commitsAfterTag) ? null : commitsAfterTag,
  }
}

/** 取 diff 的文件列表（name-status 文本）。 */
async function diffNameStatus(config, args) {
  const result = await git(config, ['diff', '--name-status', '--no-color', ...args])
  return result.code === 0 ? result.stdout.trim().split('\n').filter(Boolean) : []
}

// ────────────────────────────── 提交审计 ──────────────────────────────

/** 敏感信息规则：只报位置，不回显命中内容，避免把 secret 再写进对话。 */
const AUDIT_RULES = [
  { id: 'github-pat', severity: 'high', re: /\b(?:gh[pousr]_[A-Za-z0-9]{20,}|github_pat_[A-Za-z0-9_]{20,})\b/g },
  { id: 'aws-access-key', severity: 'high', re: /\bAKIA[0-9A-Z]{16}\b/g },
  { id: 'private-key', severity: 'high', re: /-----BEGIN (?:RSA |EC |OPENSSH |DSA |PGP )?PRIVATE KEY-----/g },
  { id: 'slack-token', severity: 'high', re: /\bxox[baprs]-[A-Za-z0-9-]{10,}\b/g },
  { id: 'jwt', severity: 'medium', re: /\beyJ[A-Za-z0-9_-]{10,}\.[A-Za-z0-9_-]{10,}\.[A-Za-z0-9_-]{10,}\b/g },
  {
    id: 'generic-secret',
    severity: 'medium',
    re: /(?:api[_-]?key|secret|token|passwd|password)\s*[:=]\s*['"][^'"\n]{12,}['"]/gi,
  },
]

/**
 * 审计一段 diff：识别新增行里的 secret、冲突标记、APK 与大文件。
 * 返回 findings（给模型看）和 highPaths（提交前要撤销暂存的路径）。
 */
function auditDiff(diffText, options = {}) {
  const { maxFileMB = 2, largeFiles = [] } = options
  const findings = []
  const highPaths = new Set()
  let currentPath = ''
  let newLine = 0
  for (const line of diffText.split('\n')) {
    if (line.startsWith('+++ b/')) {
      currentPath = line.slice(6)
      continue
    }
    if (line.startsWith('+++ ')) {
      currentPath = line.slice(4)
      continue
    }
    const hunk = /^@@ -\d+(?:,\d+)? \+(\d+)(?:,\d+)? @@/.exec(line)
    if (hunk) {
      newLine = Number.parseInt(hunk[1], 10)
      continue
    }
    if (!line.startsWith('+') || line.startsWith('+++')) continue
    const content = line.slice(1)
    for (const rule of AUDIT_RULES) {
      rule.re.lastIndex = 0
      if (rule.re.test(content)) {
        findings.push(`${rule.severity === 'high' ? 'HIGH' : 'WARN'} ${rule.id}: ${currentPath}:${newLine}`)
        if (rule.severity === 'high') highPaths.add(currentPath)
      }
    }
    if (/^(<{7}|={7}|>{7})( |$)/.test(content)) {
      findings.push(`HIGH conflict-marker: ${currentPath}:${newLine}`)
      highPaths.add(currentPath)
    }
    newLine += 1
  }
  for (const file of largeFiles) {
    if (file.sizeMB > maxFileMB) {
      findings.push(`WARN large-file: ${file.path} (${file.sizeMB.toFixed(1)} MB > ${maxFileMB} MB)`)
    }
  }
  return { findings, highPaths: [...highPaths] }
}

/** 取暂存区文件大小（超过阈值的才返回）。 */
async function stagedLargeFiles(config, maxFileMB) {
  const names = (await gitLine(config, ['diff', '--cached', '--name-only']))
    .split('\n')
    .map((line) => line.trim())
    .filter(Boolean)
  const result = []
  for (const path of names.slice(0, 300)) {
    const sizeText = await gitLine(config, ['cat-file', '-s', `:${path}`])
    const bytes = Number.parseInt(sizeText, 10)
    if (!Number.isNaN(bytes) && bytes > maxFileMB * 1024 * 1024) {
      result.push({ path, sizeMB: bytes / 1024 / 1024 })
    }
  }
  return result
}

/** 工作区文件大小（超过阈值的才返回）。 */
async function worktreeLargeFiles(config, maxFileMB) {
  const names = (await gitLine(config, ['diff', '--name-only']))
    .split('\n')
    .map((line) => line.trim())
    .filter(Boolean)
  const result = []
  for (const path of names.slice(0, 300)) {
    try {
      const info = await fsStat(join(config.repoPath, path))
      if (info.size > maxFileMB * 1024 * 1024) result.push({ path, sizeMB: info.size / 1024 / 1024 })
    } catch {
      // 文件可能已被删除
    }
  }
  return result
}

/** 生成审计 diff：staged / worktree / 指定范围。 */
async function auditSource(config, scope, range) {
  if (scope === 'worktree') {
    const diff = await gitLine(config, ['diff', '--no-color', '--no-ext-diff'])
    return { diff, largeFiles: await worktreeLargeFiles(config, config.auditMaxFileMB) }
  }
  if (scope === 'range') {
    if (!range) throw new Error('scope=range 时必须提供 range，例如 v4.0.0..HEAD')
    const diff = await gitLine(config, ['diff', '--no-color', '--no-ext-diff', range])
    return { diff, largeFiles: [] }
  }
  const diff = await gitLine(config, ['diff', '--cached', '--no-color', '--no-ext-diff'])
  return { diff, largeFiles: await stagedLargeFiles(config, config.auditMaxFileMB) }
}

// ────────────────────────────── 发布说明 ──────────────────────────────

const COMMIT_GROUPS = [
  ['feat', '新功能'],
  ['fix', '修复'],
  ['perf', '性能'],
  ['refactor', '重构'],
  ['docs', '文档'],
  ['test', '测试'],
  ['build', '构建'],
  ['ci', 'CI'],
  ['chore', '杂项'],
  ['style', '样式'],
]

/** 读取 from..to 的提交，按 conventional commit 前缀分组。 */
async function collectReleaseNotes(config, from, to) {
  const range = from && from.length > 0 ? `${from}..${to}` : to
  const output = await gitOrThrow(
    config,
    ['log', '--no-color', '--date=short', '--pretty=format:%h%x1f%ad%x1f%an%x1f%s', range],
    'git log',
  )
  const groups = new Map()
  const commits = []
  for (const line of output.split('\n')) {
    if (!line.trim()) continue
    const [short, date, author, subject] = line.split('\x1f')
    commits.push({ short, date, author, subject })
    const match = /^(\w+)(?:\([^)]+\))?!?:\s*(.+)$/.exec(subject ?? '')
    const type = match ? match[1].toLowerCase() : 'other'
    const title = match ? match[2] : subject
    if (!groups.has(type)) groups.set(type, [])
    groups.get(type).push(`- ${title} (${short})`)
  }
  return { commits, groups }
}

/** 生成 Markdown 发布说明。 */
async function buildReleaseNotes(config, from, to, version) {
  const { commits, groups } = await collectReleaseNotes(config, from, to)
  const lines = [`## ${version || to}`, '']
  let wrote = false
  for (const [type, title] of COMMIT_GROUPS) {
    const items = groups.get(type)
    if (!items || items.length === 0) continue
    lines.push(`### ${title}`, '', ...items, '')
    wrote = true
  }
  const other = groups.get('other')
  if (other && other.length > 0) {
    lines.push('### 其他', '', ...other, '')
    wrote = true
  }
  if (!wrote) lines.push('本次没有可归类的提交。', '')
  lines.push(`**完整变更**：${commits.length} 个提交${from ? `（${from}..${to}）` : ''}`, '')
  return { markdown: lines.join('\n'), commits }
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
        '读取仓库状态：当前分支、HEAD、上游与领先/落后、最近 tag 与 versionCode、已暂存/未暂存/未跟踪/冲突文件、进行中的 merge/rebase、未跟踪的 APK 产物。提交或推送前先看一眼。',
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
            headSubject: { type: 'string', required: true },
            upstream: { type: 'string', required: true },
            tag: { type: 'string', required: true },
            headTag: { type: 'string', required: true },
            versionCode: { type: 'number' },
            ahead: { type: 'number', required: true },
            behind: { type: 'number', required: true },
            staged: { type: 'array', required: true, items: { type: 'string' } },
            unstaged: { type: 'array', required: true, items: { type: 'string' } },
            untracked: { type: 'array', required: true, items: { type: 'string' } },
            conflicts: { type: 'array', required: true, items: { type: 'string' } },
            dirty: { type: 'array', required: true, items: { type: 'string' } },
            apkLeftovers: { type: 'array', required: true, items: { type: 'string' } },
            inProgress: { type: 'array', required: true, items: { type: 'string' } },
            summary: { type: 'string', required: true },
          },
        },
        render: (_args, value) => [{ type: 'text', text: value.summary }],
      },
      async execute(args, exec) {
        exec.signal?.throwIfAborted?.()
        if (args.fetch === true) {
          const { value: token } = resolveToken(config)
          await networkGit(config, token, 'git fetch', () => ['fetch', config.remote, '--prune'], {
            timeoutMs: 240_000,
          })
        }
        const branch = await currentBranch(config)
        const head = await gitLine(config, ['rev-parse', '--short=9', 'HEAD'])
        const headSubject = await gitLine(config, ['log', '-1', '--pretty=%s'])
        const version = await versionInfo(config)
        const status = await git(config, ['status', '--porcelain'])
        const parsed = parseStatus(status.stdout)
        const dirty = status.stdout
          .split('\n')
          .map((line) => line.trimEnd())
          .filter((line) => line.length > 0)
        const apkLeftovers = dirty
          .map((line) => line.slice(3).trim())
          .filter((path) => /\.apk$/i.test(path))
        const { upstream, ahead, behind } = await aheadBehind(config)
        const inProgress = await inProgressOperations(config)
        const summary = [
          `分支 ${branch} @ ${head}${headSubject ? ` · ${headSubject}` : ''}`,
          version.tag.length > 0
            ? `tag ${version.tag}${version.headTag ? '（HEAD 已打 tag）' : ''} · versionCode ${version.versionCode}（tag 后 ${version.commitsAfterTag} 个提交）`
            : '尚无版本 tag',
          upstream ? `上游 ${upstream} · 领先 ${ahead} / 落后 ${behind}` : '当前分支没有上游',
          parsed.staged.length + parsed.unstaged.length + parsed.untracked.length + parsed.conflicts.length === 0
            ? '工作区干净'
            : `已暂存 ${parsed.staged.length} · 未暂存 ${parsed.unstaged.length} · 未跟踪 ${parsed.untracked.length} · 冲突 ${parsed.conflicts.length}`,
          inProgress.length > 0 ? `⚠️ 进行中的操作：${inProgress.join(', ')}` : '',
          apkLeftovers.length === 0 ? '' : `⚠️ 根目录有 APK 产物（不要提交）：${apkLeftovers.join(', ')}`,
        ]
          .filter((line) => line.length > 0)
          .join('\n')
        return {
          branch,
          head,
          headSubject,
          upstream,
          tag: version.tag,
          headTag: version.headTag,
          ...(version.versionCode === null ? {} : { versionCode: version.versionCode }),
          ahead,
          behind,
          staged: parsed.staged,
          unstaged: parsed.unstaged,
          untracked: parsed.untracked,
          conflicts: parsed.conflicts,
          dirty,
          apkLeftovers,
          inProgress,
          summary,
        }
      },
    }),
  )

  // 2. 查看 diff
  register(
    defineTool({
      name: 'git_diff',
      description:
        '查看改动：默认未暂存 diff，staged=true 看已暂存，ref 可指定任意范围（如 HEAD~1、main...HEAD、v4.0.0..HEAD）。支持路径过滤、只看统计、截断保护。不会修改索引。',
      parameters: {
        staged: { type: 'boolean', description: '看已暂存的改动（--cached）' },
        ref: { type: 'string', description: '对比范围或提交，例如 HEAD~1 / main...HEAD / v4.0.0..HEAD' },
        paths: { type: 'array', description: '只看这些路径（仓库内相对路径）', items: { type: 'string' } },
        statOnly: { type: 'boolean', description: '只返回统计（--stat），不返回补丁' },
        contextLines: { type: 'number', description: '上下文行数（默认 git 的 3）' },
        maxBytes: { type: 'number', description: '补丁字节上限（默认取配置 maxDiffBytes）' },
      },
      output: {
        schema: {
          type: 'object',
          additionalProperties: false,
          properties: {
            files: { type: 'array', required: true, items: { type: 'string' } },
            stat: { type: 'string', required: true },
            patch: { type: 'string', required: true },
            truncated: { type: 'boolean', required: true },
            summary: { type: 'string', required: true },
          },
        },
        render: (_args, value) => [{ type: 'text', text: value.summary }],
      },
      async execute(args, exec) {
        exec.signal?.throwIfAborted?.()
        const paths = safePaths(args.paths)
        const base = []
        if (args.staged === true) base.push('--cached')
        if (typeof args.ref === 'string' && args.ref.trim().length > 0) base.push(args.ref.trim())
        if (typeof args.contextLines === 'number' && args.contextLines >= 0) base.push(`-U${Math.trunc(args.contextLines)}`)
        const pathArgs = paths.length > 0 ? ['--', ...paths] : []
        const files = await diffNameStatus(config, [...base, ...pathArgs])
        const stat = (await git(config, ['diff', '--stat', '--no-color', ...base, ...pathArgs])).stdout.trim()
        let patch = ''
        let truncated = false
        if (args.statOnly !== true) {
          const raw = await git(config, ['diff', '--no-color', '--no-ext-diff', ...base, ...pathArgs])
          if (raw.code !== 0) throw new Error(`git diff 失败：${(raw.stderr || raw.stdout).trim()}`)
          const limited = truncateText(raw.stdout, args.maxBytes ?? config.maxDiffBytes)
          patch = limited.text
          truncated = limited.truncated
        }
        const label = args.staged === true ? '已暂存' : args.ref ? `范围 ${args.ref}` : '未暂存'
        const summary = [
          `${label}改动：${files.length} 个文件${truncated ? '（补丁已截断）' : ''}`,
          stat || '（没有改动）',
        ].join('\n')
        return { files, stat, patch, truncated, summary }
      },
    }),
  )

  // 3. 提交历史
  register(
    defineTool({
      name: 'git_log',
      description:
        '读取提交历史：count 控制条数，range 支持 v4.0.0..HEAD 这类区间，paths/author/grep 过滤，withFiles 附带每个提交改动的文件。写发布说明、查回归时用。',
      parameters: {
        count: { type: 'number', description: '最多返回多少条（默认 20，上限 200）' },
        range: { type: 'string', description: '提交范围，例如 v4.0.0..HEAD' },
        paths: { type: 'array', description: '只看这些路径', items: { type: 'string' } },
        author: { type: 'string', description: '按作者过滤' },
        grep: { type: 'string', description: '按提交信息过滤' },
        withFiles: { type: 'boolean', description: '附带每个提交的改动文件（--name-status）' },
      },
      output: {
        schema: {
          type: 'object',
          additionalProperties: false,
          properties: {
            commits: { type: 'array', required: true, items: { type: 'string' } },
            total: { type: 'number', required: true },
            summary: { type: 'string', required: true },
          },
        },
        render: (_args, value) => [{ type: 'text', text: value.summary }],
      },
      async execute(args, exec) {
        exec.signal?.throwIfAborted?.()
        const count = Math.min(200, Math.max(1, Math.trunc(args.count ?? 20)))
        const paths = safePaths(args.paths)
        const logArgs = ['log', '--no-color', '--date=short', `-n${count}`, '--pretty=format:%h%x1f%ad%x1f%an%x1f%s']
        if (typeof args.range === 'string' && args.range.trim().length > 0) logArgs.push(args.range.trim())
        if (typeof args.author === 'string' && args.author.trim().length > 0) logArgs.push(`--author=${args.author.trim()}`)
        if (typeof args.grep === 'string' && args.grep.trim().length > 0) logArgs.push(`--grep=${args.grep.trim()}`)
        if (args.withFiles === true) logArgs.push('--name-status')
        if (paths.length > 0) logArgs.push('--', ...paths)
        const result = await git(config, logArgs)
        if (result.code !== 0) throw new Error(`git log 失败：${(result.stderr || result.stdout).trim()}`)
        const commits = []
        let current = ''
        for (const line of result.stdout.split('\n')) {
          if (line.includes('\x1f')) {
            if (current) commits.push(current)
            const [short, date, author, subject] = line.split('\x1f')
            current = `${short}  ${date}  ${author}  ${subject}`
          } else if (line.trim().length > 0 && current) {
            current += `\n  ${line.trim()}`
          }
        }
        if (current) commits.push(current)
        return {
          commits,
          total: commits.length,
          summary: commits.length === 0 ? '没有匹配的提交' : `最近 ${commits.length} 条提交：\n${commits.join('\n')}`,
        }
      },
    }),
  )

  // 4. 查看单个提交
  register(
    defineTool({
      name: 'git_show',
      description:
        '查看一个提交 / tag 的详情：元信息、改动统计，patch=true 时附完整补丁（带截断）。排查某次改动、写 release notes 时用。',
      parameters: {
        ref: { type: 'string', description: '提交或 tag（默认 HEAD）' },
        stat: { type: 'boolean', description: '返回改动统计（默认 true）' },
        patch: { type: 'boolean', description: '返回补丁正文（默认 false）' },
        paths: { type: 'array', description: '只看这些路径', items: { type: 'string' } },
        maxBytes: { type: 'number', description: '补丁字节上限' },
      },
      output: {
        schema: {
          type: 'object',
          additionalProperties: false,
          properties: {
            ref: { type: 'string', required: true },
            hash: { type: 'string', required: true },
            author: { type: 'string', required: true },
            date: { type: 'string', required: true },
            subject: { type: 'string', required: true },
            stat: { type: 'string', required: true },
            patch: { type: 'string', required: true },
            truncated: { type: 'boolean', required: true },
            summary: { type: 'string', required: true },
          },
        },
        render: (_args, value) => [{ type: 'text', text: value.summary }],
      },
      async execute(args, exec) {
        exec.signal?.throwIfAborted?.()
        const ref = typeof args.ref === 'string' && args.ref.trim().length > 0 ? args.ref.trim() : 'HEAD'
        const paths = safePaths(args.paths)
        const meta = await git(config, ['show', '-s', '--no-color', '--date=iso-strict', '--pretty=format:%H%x1f%h%x1f%an%x1f%ad%x1f%s', ref])
        if (meta.code !== 0) throw new Error(`找不到提交 ${ref}：${(meta.stderr || meta.stdout).trim()}`)
        const [hash, short, author, date, subject] = meta.stdout.trim().split('\x1f')
        const pathArgs = paths.length > 0 ? ['--', ...paths] : []
        const stat =
          args.stat === false
            ? ''
            : (await git(config, ['show', '--stat', '--oneline', '--no-color', '--format=', ref, ...pathArgs])).stdout.trim()
        let patch = ''
        let truncated = false
        if (args.patch === true) {
          const raw = await git(config, ['show', '--no-color', '--no-ext-diff', '--format=', ref, ...pathArgs])
          const limited = truncateText(raw.stdout, args.maxBytes ?? config.maxDiffBytes)
          patch = limited.text
          truncated = limited.truncated
        }
        return {
          ref,
          hash,
          author,
          date,
          subject,
          stat,
          patch,
          truncated,
          summary: `${short} ${subject}\n${author} · ${date}\n${stat}${truncated ? '\n（补丁已截断）' : ''}`,
        }
      },
    }),
  )

  // 5. 暂存 / 取消暂存
  register(
    defineTool({
      name: 'git_stage',
      description:
        '暂存或取消暂存。all=true 表示全部改动（git add -A / git reset），否则按 paths 精确操作；unstage=true 取消暂存。不会提交。',
      parameters: {
        paths: { type: 'array', description: '仓库内相对路径；all=true 时忽略', items: { type: 'string' } },
        all: { type: 'boolean', description: '全部改动（默认 false）' },
        unstage: { type: 'boolean', description: '取消暂存（默认 false）' },
      },
      output: {
        schema: {
          type: 'object',
          additionalProperties: false,
          properties: {
            ok: { type: 'boolean', required: true },
            action: { type: 'string', required: true },
            files: { type: 'array', required: true, items: { type: 'string' } },
            summary: { type: 'string', required: true },
          },
        },
        render: (_args, value) => [{ type: 'text', text: value.summary }],
      },
      async execute(args, exec) {
        exec.signal?.throwIfAborted?.()
        const paths = safePaths(args.paths)
        const unstage = args.unstage === true
        let gitArgs
        if (args.all === true) {
          gitArgs = unstage ? ['reset'] : ['add', '-A']
        } else {
          if (paths.length === 0) throw new Error('paths 不能为空；要操作全部改动请传 all=true')
          gitArgs = unstage ? ['reset', '--', ...paths] : ['add', '--', ...paths]
        }
        const result = await git(config, gitArgs)
        if (result.code !== 0) throw new Error(`${unstage ? '取消暂存' : '暂存'}失败：${(result.stderr || result.stdout).trim()}`)
        const staged = (await gitLine(config, ['diff', '--cached', '--name-status']))
          .split('\n')
          .map((line) => line.trim())
          .filter(Boolean)
        return {
          ok: true,
          action: unstage ? 'unstage' : 'stage',
          files: staged,
          summary: `${unstage ? '已取消暂存' : '已暂存'}${args.all === true ? '全部改动' : `：${paths.join(', ')}`}\n当前暂存区：${staged.length} 个文件`,
        }
      },
    }),
  )

  // 6. 提交
  register(
    defineTool({
      name: 'git_commit',
      description:
        '按本项目的约定提交：固定作者 dsh-agent、提交前自动审计（secret / 冲突标记 / 大文件）、拒绝把 *.apk 提交进仓库（除非显式放行）、返回哈希与改动统计。传 paths=["."] 表示全部改动。',
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
        amend: { type: 'boolean', description: '修补上一次提交（--amend）' },
        signoff: { type: 'boolean', description: '追加 Signed-off-by（--signoff）' },
        skipAudit: { type: 'boolean', description: '跳过 secret/冲突标记审计（默认 false，不建议）' },
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
            audit: { type: 'array', required: true, items: { type: 'string' } },
            message: { type: 'string', required: true },
          },
        },
        render: (_args, value) => [{ type: 'text', text: value.message }],
      },
      async execute(args, exec) {
        exec.signal?.throwIfAborted?.()
        if (args.paths.length === 0) throw new Error('paths 不能为空；要提交全部改动请传 ["."]')
        const paths = safePaths(args.paths)
        const add = await git(config, ['add', '--', ...paths])
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
        if (stagedFiles.length === 0 && args.allowEmpty !== true && args.amend !== true) {
          return {
            committed: false,
            hash: await gitLine(config, ['rev-parse', '--short=9', 'HEAD']),
            files: [],
            stat: '',
            audit: [],
            message: '没有可提交的改动（工作区干净）',
          }
        }

        // 提交前审计：只报位置，不回显 secret。
        let audit = []
        if (args.skipAudit !== true && stagedFiles.length > 0) {
          const source = await auditSource(config, 'staged')
          const report = auditDiff(source.diff, {
            maxFileMB: config.auditMaxFileMB,
            largeFiles: source.largeFiles,
          })
          audit = report.findings
          if (report.highPaths.length > 0) {
            await git(config, ['reset', '--', ...report.highPaths])
            throw new Error(
              '提交审计发现高风险内容，已把这些文件移出暂存区：\n' +
                report.findings.filter((item) => item.startsWith('HIGH')).join('\n') +
                '\n请先处理（删除 secret / 解决冲突）再提交；确属误报可传 skipAudit=true。',
            )
          }
        }

        const commitArgs = ['commit', '-F', '-']
        if (args.amend === true) commitArgs.push('--amend')
        if (args.signoff === true) commitArgs.push('--signoff')
        if (stagedFiles.length === 0) commitArgs.push('--allow-empty')
        const commit = await git(config, commitArgs, { withAuthor: true, input: args.message })
        if (commit.code !== 0) throw new Error(`git commit 失败：${commit.stderr.trim() || commit.stdout.trim()}`)

        const hash = await gitLine(config, ['rev-parse', '--short=9', 'HEAD'])
        const stat = await gitLine(config, ['show', '--stat', '--oneline', '--format=', 'HEAD'])
        const subject = await gitLine(config, ['log', '-1', '--pretty=%s'])
        return {
          committed: true,
          hash,
          files: stagedFiles,
          stat,
          audit,
          message:
            `已提交 ${hash}：${subject}\n${stat}` +
            (audit.length > 0 ? `\n审计提示：\n- ${audit.join('\n- ')}` : ''),
        }
      },
    }),
  )

  // 7. 推送
  register(
    defineTool({
      name: 'git_push',
      description:
        '推送分支或 tag。token 从环境变量读取（临时 askpass，不落盘），可钉 hosts 并自动重试；推送后用 ls-remote 校验远端确实指向本地提交。受保护分支默认拒绝强推。',
      parameters: {
        ref: { type: 'string', description: '要推送的分支名（默认当前分支）' },
        tag: { type: 'string', description: '要推送的 tag 名（给了就只推这个 tag）' },
        force: { type: 'boolean', description: '强制推送（改写历史或移动 tag 时才用）' },
        allowProtected: { type: 'boolean', description: '允许对受保护分支强推（默认 false，危险）' },
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
        const branch = await currentBranch(config)
        const isTag = args.tag !== undefined && args.tag.length > 0
        if (isTag && !/^[A-Za-z0-9._\/-]+$/.test(args.tag)) throw new Error(`tag 名不合规：${args.tag}`)
        const refName = typeof args.ref === 'string' && args.ref.length > 0 ? safeBranchName(args.ref) : branch
        const target = isTag ? `refs/tags/${args.tag}` : refName
        const targetBranch = isTag ? '' : target
        if (args.force === true && !isTag && isProtectedBranch(config, targetBranch) && args.allowProtected !== true) {
          throw new Error(
            `拒绝强推受保护分支 ${targetBranch}。确需改写历史请传 allowProtected=true，并先确认远端备份。`,
          )
        }
        const upstream = isTag ? '' : await upstreamBranch(config)
        const outcome = await networkGit(
          config,
          token,
          'git push',
          () => {
            const pushArgs = ['push']
            if (args.force === true) pushArgs.push('--force')
            if (!isTag && !upstream && (args.ref === undefined || args.ref === branch)) pushArgs.push('--set-upstream')
            pushArgs.push(config.remote, target)
            return pushArgs
          },
          { timeoutMs: 240_000 },
        )
        if (!outcome.ok) throw new Error(mask(String(outcome.detail), token))

        const check = await networkGit(config, token, 'git ls-remote', () => ['ls-remote', config.remote, target], {
          timeoutMs: 120_000,
        })
        const remoteSha = check.ok ? check.result.stdout.trim().split(/\s+/)[0] ?? '' : ''
        // 附注 tag 的 ls-remote 指向 tag 对象，不是 commit；两个都算对。
        const tagObjectSha = isTag ? await gitLine(config, ['rev-parse', target]) : ''
        const expected = isTag ? await gitLine(config, ['rev-parse', `${target}^{commit}`]) : localSha
        const ok =
          remoteSha === expected ||
          (tagObjectSha.length > 0 && remoteSha === tagObjectSha) ||
          remoteSha.startsWith(expected)
        return {
          ok,
          remoteSha,
          localSha: expected,
          message: ok
            ? `已推送 ${target} → 远端 ${remoteSha.slice(0, 9)}${!isTag && !upstream ? '（已设置上游）' : ''}`
            : `推送命令成功，但远端 ${target} = ${remoteSha || '(空)'}，本地 = ${expected}，请复查`,
        }
      },
    }),
  )

  // 8. 分支管理
  register(
    defineTool({
      name: 'git_branch',
      description:
        '分支管理：list 列出本地分支（含上游与领先/落后）、create 新建、switch 切换、delete 删除（默认 -d，force 用 -D；删除前自动备份 ref）。main/master/develop/trunk/release/* 等受保护分支不可删除。',
      parameters: {
        action: {
          type: 'string',
          required: true,
          enum: ['list', 'create', 'switch', 'delete'],
          description: '要执行的动作',
        },
        name: { type: 'string', description: '分支名（create/switch/delete 必填）' },
        from: { type: 'string', description: 'create 时的起点（默认 HEAD）' },
        force: { type: 'boolean', description: 'delete 时强制删除未合并分支（-D）' },
      },
      output: {
        schema: {
          type: 'object',
          additionalProperties: false,
          properties: {
            action: { type: 'string', required: true },
            current: { type: 'string', required: true },
            branches: { type: 'array', required: true, items: { type: 'string' } },
            backupRef: { type: 'string', required: true },
            summary: { type: 'string', required: true },
          },
        },
        render: (_args, value) => [{ type: 'text', text: value.summary }],
      },
      async execute(args, exec) {
        exec.signal?.throwIfAborted?.()
        const action = args.action
        const name = action === 'list' ? '' : safeBranchName(args.name ?? '')
        let backupRef = ''
        if (action === 'create') {
          const createArgs = ['branch', name]
          if (typeof args.from === 'string' && args.from.trim().length > 0) createArgs.push(safeRevision(args.from, 'from'))
          await gitOrThrow(config, createArgs, 'git branch')
        } else if (action === 'switch') {
          await gitOrThrow(config, ['switch', name], 'git switch')
        } else if (action === 'delete') {
          const current = await currentBranch(config)
          if (name === current) throw new Error(`不能删除当前所在分支 ${name}`)
          if (isProtectedBranch(config, name)) throw new Error(`受保护分支不能删除：${name}`)
          const sha = await gitLine(config, ['rev-parse', name])
          if (!sha) throw new Error(`分支不存在：${name}`)
          const stamp = new Date().toISOString().replace(/[-:.TZ]/g, '').slice(0, 14)
          backupRef = `refs/backup/dsha-diksu-git/${stamp}/${name}`
          await gitOrThrow(config, ['update-ref', backupRef, sha], 'git update-ref')
          await gitOrThrow(config, ['branch', args.force === true ? '-D' : '-d', name], 'git branch -d')
        }
        const branches = (await gitLine(config, ['branch', '--no-color', '-vv']))
          .split('\n')
          .map((line) => line.trimEnd())
          .filter(Boolean)
        const current = await currentBranch(config)
        const summary =
          action === 'list'
            ? `当前分支 ${current}，共 ${branches.length} 个本地分支：\n${branches.join('\n')}`
            : `${action} ${name} 完成（当前 ${current}）${backupRef ? `\n备份：${backupRef}` : ''}`
        return { action, current, branches, backupRef, summary }
      },
    }),
  )

  // 9. 贮藏
  register(
    defineTool({
      name: 'git_stash',
      description:
        '贮藏管理：list 列出、save 保存（可含未跟踪文件）、apply 应用、pop 应用并删除、drop 删除。index 默认 0（stash@{0}）。',
      parameters: {
        action: {
          type: 'string',
          required: true,
          enum: ['list', 'save', 'apply', 'pop', 'drop'],
          description: '要执行的动作',
        },
        message: { type: 'string', description: 'save 时的说明' },
        index: { type: 'number', description: 'stash 序号（默认 0）' },
        includeUntracked: { type: 'boolean', description: 'save 时包含未跟踪文件（-u）' },
      },
      output: {
        schema: {
          type: 'object',
          additionalProperties: false,
          properties: {
            action: { type: 'string', required: true },
            stashes: { type: 'array', required: true, items: { type: 'string' } },
            summary: { type: 'string', required: true },
          },
        },
        render: (_args, value) => [{ type: 'text', text: value.summary }],
      },
      async execute(args, exec) {
        exec.signal?.throwIfAborted?.()
        const index = Math.max(0, Math.trunc(args.index ?? 0))
        const ref = `stash@{${index}}`
        if (args.action === 'save') {
          const saveArgs = ['stash', 'push']
          if (args.includeUntracked === true) saveArgs.push('-u')
          if (typeof args.message === 'string' && args.message.trim().length > 0) saveArgs.push('-m', args.message.trim())
          const result = await git(config, saveArgs)
          if (result.code !== 0) throw new Error(`git stash 失败：${(result.stderr || result.stdout).trim()}`)
        } else if (args.action === 'apply' || args.action === 'pop') {
          await gitOrThrow(config, ['stash', args.action, ref], `git stash ${args.action}`)
        } else if (args.action === 'drop') {
          await gitOrThrow(config, ['stash', 'drop', ref], 'git stash drop')
        }
        const stashes = (await gitLine(config, ['stash', 'list', '--date=iso', '--pretty=format:%gd%x1f%h%x1f%ad%x1f%s']))
          .split('\n')
          .filter(Boolean)
          .map((line) => line.split('\x1f').join('  '))
        return {
          action: args.action,
          stashes,
          summary:
            args.action === 'list'
              ? stashes.length === 0
                ? '没有贮藏'
                : `共 ${stashes.length} 条贮藏：\n${stashes.join('\n')}`
              : `${args.action} ${args.action === 'save' ? '' : ref} 完成；当前 ${stashes.length} 条贮藏`,
        }
      },
    }),
  )

  // 10. 同步（fetch / pull）
  register(
    defineTool({
      name: 'git_sync',
      description:
        '与远端同步：fetch 只抓取（可 prune），pull 抓取并快进合并（默认 --ff-only，rebase=true 用 --rebase）。工作区不干净时拒绝 pull，避免覆盖本地改动。',
      parameters: {
        action: { type: 'string', required: true, enum: ['fetch', 'pull'], description: 'fetch 或 pull' },
        remote: { type: 'string', description: '远端名（默认配置的 remote）' },
        branch: { type: 'string', description: 'pull 的分支（默认当前分支）' },
        prune: { type: 'boolean', description: 'fetch 时清理已删除的远端分支' },
        rebase: { type: 'boolean', description: 'pull 时用 rebase 而不是 ff-only' },
        allowDirty: { type: 'boolean', description: '允许在工作区有改动时 pull（默认 false，危险）' },
      },
      output: {
        schema: {
          type: 'object',
          additionalProperties: false,
          properties: {
            action: { type: 'string', required: true },
            ok: { type: 'boolean', required: true },
            ahead: { type: 'number', required: true },
            behind: { type: 'number', required: true },
            summary: { type: 'string', required: true },
          },
        },
        render: (_args, value) => [{ type: 'text', text: value.summary }],
      },
      async execute(args, exec) {
        exec.signal?.throwIfAborted?.()
        const { value: token } = resolveToken(config)
        const remote = typeof args.remote === 'string' && args.remote.trim().length > 0 ? safeRevision(args.remote, 'remote') : config.remote
        if (args.action === 'pull') {
          const status = await git(config, ['status', '--porcelain'])
          if (status.stdout.trim().length > 0 && args.allowDirty !== true) {
            throw new Error('工作区有未提交改动，拒绝 pull。请先提交或贮藏，确需继续传 allowDirty=true。')
          }
          const branch = typeof args.branch === 'string' && args.branch.trim().length > 0 ? safeBranchName(args.branch) : await currentBranch(config)
          const pullArgs = ['pull']
          if (args.rebase === true) pullArgs.push('--rebase')
          else pullArgs.push('--ff-only')
          pullArgs.push(remote, branch)
          const result = await networkGit(config, token, 'git pull', () => pullArgs, { timeoutMs: 300_000 })
          if (!result.ok) throw new Error(mask(String(result.detail), token))
        } else {
          const fetchArgs = ['fetch', remote]
          if (args.prune === true) fetchArgs.push('--prune')
          const result = await networkGit(config, token, 'git fetch', () => fetchArgs, { timeoutMs: 240_000 })
          if (!result.ok) throw new Error(mask(String(result.detail), token))
        }
        const { upstream, ahead, behind } = await aheadBehind(config)
        return {
          action: args.action,
          ok: true,
          ahead,
          behind,
          summary: `${args.action} 完成${upstream ? ` · 上游 ${upstream} · 领先 ${ahead} / 落后 ${behind}` : ''}`,
        }
      },
    }),
  )

  // 11. 提交审计
  register(
    defineTool({
      name: 'git_audit',
      description:
        '提交前审计：扫描暂存区 / 工作区 / 指定范围的新增行，识别 GitHub PAT、AWS key、私钥、JWT、通用 secret、冲突标记、APK 与大文件。只报位置不回显内容，适合提交前自查。',
      parameters: {
        scope: {
          type: 'string',
          enum: ['staged', 'worktree', 'range'],
          description: '审计范围（默认 staged）',
        },
        range: { type: 'string', description: 'scope=range 时的范围，例如 v4.0.0..HEAD' },
        paths: { type: 'array', description: '只看这些路径', items: { type: 'string' } },
        maxFileMB: { type: 'number', description: '大文件阈值（默认取配置 auditMaxFileMB）' },
      },
      output: {
        schema: {
          type: 'object',
          additionalProperties: false,
          properties: {
            ok: { type: 'boolean', required: true },
            scope: { type: 'string', required: true },
            findings: { type: 'array', required: true, items: { type: 'string' } },
            summary: { type: 'string', required: true },
          },
        },
        render: (_args, value) => [{ type: 'text', text: value.summary }],
      },
      async execute(args, exec) {
        exec.signal?.throwIfAborted?.()
        const scope = args.scope ?? 'staged'
        const source = await auditSource(config, scope, args.range)
        let diff = source.diff
        const paths = safePaths(args.paths)
        if (paths.length > 0) {
          const filtered = await git(config, [
            'diff',
            '--no-color',
            '--no-ext-diff',
            ...(scope === 'staged' ? ['--cached'] : []),
            ...(scope === 'range' ? [args.range] : []),
            '--',
            ...paths,
          ])
          diff = filtered.stdout
        }
        const report = auditDiff(diff, {
          maxFileMB: args.maxFileMB ?? config.auditMaxFileMB,
          largeFiles: source.largeFiles,
        })
        const ok = !report.findings.some((item) => item.startsWith('HIGH'))
        return {
          ok,
          scope,
          findings: report.findings,
          summary:
            report.findings.length === 0
              ? `审计通过：${scope} 没有发现问题`
              : `${ok ? '审计有提示' : '审计未通过'}（${scope}）：\n- ${report.findings.join('\n- ')}`,
        }
      },
    }),
  )

  // 12. blame
  register(
    defineTool({
      name: 'git_blame',
      description:
        '逐行追溯文件：返回指定行范围（默认整个文件，最多 200 行）的提交、作者、日期与内容。排查"这行是谁改的"时用。',
      parameters: {
        path: { type: 'string', required: true, description: '仓库内相对路径' },
        startLine: { type: 'number', description: '起始行（1-based）' },
        endLine: { type: 'number', description: '结束行（含）' },
        maxLines: { type: 'number', description: '最多返回多少行（默认 200）' },
      },
      output: {
        schema: {
          type: 'object',
          additionalProperties: false,
          properties: {
            path: { type: 'string', required: true },
            lines: { type: 'array', required: true, items: { type: 'string' } },
            summary: { type: 'string', required: true },
          },
        },
        render: (_args, value) => [{ type: 'text', text: value.summary }],
      },
      async execute(args, exec) {
        exec.signal?.throwIfAborted?.()
        const path = safePath(args.path)
        const blameArgs = ['blame', '--date=short', '--line-porcelain']
        if (typeof args.startLine === 'number' && args.startLine > 0) {
          const end = typeof args.endLine === 'number' && args.endLine >= args.startLine ? args.endLine : args.startLine
          blameArgs.push('-L', `${Math.trunc(args.startLine)},${Math.trunc(end)}`)
        }
        blameArgs.push('--', path)
        const result = await git(config, blameArgs)
        if (result.code !== 0) throw new Error(`git blame 失败：${(result.stderr || result.stdout).trim()}`)
        const limit = Math.min(500, Math.max(1, Math.trunc(args.maxLines ?? 200)))
        const lines = []
        let hash = ''
        let author = ''
        let date = ''
        for (const line of result.stdout.split('\n')) {
          const header = /^([0-9a-f]{40}) \d+ \d+/.exec(line)
          if (header) {
            hash = header[1].slice(0, 9)
            continue
          }
          if (line.startsWith('author ')) {
            author = line.slice(7)
            continue
          }
          if (line.startsWith('author-time ')) {
            const seconds = Number.parseInt(line.slice(12), 10)
            if (!Number.isNaN(seconds)) date = new Date(seconds * 1000).toISOString().slice(0, 10)
            continue
          }
          if (line.startsWith('\t')) {
            lines.push(`${hash}  ${date}  ${author}  ${line.slice(1)}`)
            if (lines.length >= limit) break
          }
        }
        return {
          path,
          lines,
          summary: `${path} 的 blame（${lines.length} 行）：\n${lines.join('\n')}`,
        }
      },
    }),
  )

  // 13. 打 tag
  register(
    defineTool({
      name: 'git_tag',
      description:
        '在 HEAD 上创建（或强制移动）tag，并算出对应的 versionCode。本项目发版必须打 vX.Y.Z-ultra 形式的 tag，且 tag 要打在最终 HEAD 上。message 非空时创建附注 tag。',
      parameters: {
        tag: { type: 'string', required: true, description: '形如 v4.2.0-ultra' },
        force: { type: 'boolean', description: '已存在时强制移动（默认 false）' },
        message: { type: 'string', description: '附注 tag 的说明（可选）' },
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
        if (!/^v\d+\.\d+\.\d+/.test(args.tag) || !/^[A-Za-z0-9._\/-]+$/.test(args.tag)) {
          throw new Error(`tag 名不合规：${args.tag}（本项目用 vX.Y.Z-ultra，版本公式依赖这个前缀）`)
        }
        const tagArgs = ['tag']
        if (args.force === true) tagArgs.push('-f')
        if (typeof args.message === 'string' && args.message.trim().length > 0) {
          tagArgs.push('-a', '-m', args.message.trim())
        }
        tagArgs.push(args.tag)
        const result = await git(config, tagArgs)
        if (result.code !== 0) throw new Error(`git tag 失败：${result.stderr.trim()}`)
        const commit = await gitLine(config, ['rev-parse', 'HEAD'])
        const version = await versionInfo(config)
        return {
          created: true,
          tag: args.tag,
          commit,
          ...(version.versionCode === null ? {} : { versionCode: version.versionCode }),
          message:
            `已打 tag ${args.tag} @ ${commit.slice(0, 9)}；当前 versionCode = ${version.versionCode}` +
            '（tag 后 0 个提交时最干净）。别忘了 git_push 推 tag。',
        }
      },
    }),
  )

  // 14. 发布说明
  register(
    defineTool({
      name: 'git_release_notes',
      description:
        '按 conventional commit 前缀（feat/fix/perf/...）把两个 ref 之间的提交整理成 Markdown 发布说明，可写入仓库内文件供 release_publish 的 bodyFile 使用。',
      parameters: {
        from: { type: 'string', description: '起始 ref（默认最近一个 v* tag）' },
        to: { type: 'string', description: '结束 ref（默认 HEAD）' },
        version: { type: 'string', description: '标题里的版本号（默认 to）' },
        writeTo: { type: 'string', description: '写入仓库内的相对路径（可选）' },
      },
      output: {
        schema: {
          type: 'object',
          additionalProperties: false,
          properties: {
            from: { type: 'string', required: true },
            to: { type: 'string', required: true },
            markdown: { type: 'string', required: true },
            path: { type: 'string', required: true },
            summary: { type: 'string', required: true },
          },
        },
        render: (_args, value) => [{ type: 'text', text: value.summary }],
      },
      async execute(args, exec) {
        exec.signal?.throwIfAborted?.()
        const to = typeof args.to === 'string' && args.to.trim().length > 0 ? args.to.trim() : 'HEAD'
        const from =
          typeof args.from === 'string' && args.from.trim().length > 0
            ? args.from.trim()
            : await gitLine(config, ['describe', '--tags', '--abbrev=0', '--match', 'v[0-9]*'])
        const { markdown, commits } = await buildReleaseNotes(config, from, to, args.version ?? to)
        let path = ''
        if (typeof args.writeTo === 'string' && args.writeTo.trim().length > 0) {
          path = safePath(args.writeTo)
          await writeFile(join(config.repoPath, path), markdown, 'utf8')
        }
        return {
          from,
          to,
          markdown,
          path,
          summary: `发布说明 ${from || '(全部)'}..${to}：${commits.length} 个提交${path ? `，已写入 ${path}` : ''}\n\n${markdown}`,
        }
      },
    }),
  )

  // 15. 触发 CI
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
        const ref = args.ref ?? (await currentBranch(config))
        const repo = await repoSlug(config)
        await api(config, token, 'POST', `/repos/${repo}/actions/workflows/${workflow}/dispatches`, {
          ref,
          inputs: args.inputs ?? {},
        })
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
          ...(runId === undefined ? {} : { runId }),
          runUrl,
          message:
            runId === undefined
              ? `已触发 ${workflow} @ ${ref}，但没找到对应的 run（可在 Actions 页面确认）`
              : `已触发 ${workflow} @ ${ref} → run ${runId}\n${runUrl}`,
        }
      },
    }),
  )

  // 16. 等 CI
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

  // 17. CI 日志
  register(
    defineTool({
      name: 'ci_logs',
      description:
        '拉取一条 workflow run 的 job 日志（默认挑失败的 job），返回末尾若干行和命中 error/failed 的行。CI 失败时先看这个再改代码。',
      parameters: {
        runId: { type: 'number', required: true, description: 'workflow run id' },
        job: { type: 'string', description: 'job 名称或 id（默认第一个失败的 job）' },
        maxLines: { type: 'number', description: '最多返回多少行（默认 120）' },
      },
      output: {
        schema: {
          type: 'object',
          additionalProperties: false,
          properties: {
            runId: { type: 'number', required: true },
            job: { type: 'string', required: true },
            lines: { type: 'array', required: true, items: { type: 'string' } },
            summary: { type: 'string', required: true },
          },
        },
        render: (_args, value) => [{ type: 'text', text: value.summary }],
      },
      async execute(args, exec) {
        exec.signal?.throwIfAborted?.()
        const { value: token } = resolveToken(config)
        const repo = await repoSlug(config)
        const jobs = await api(config, token, 'GET', `/repos/${repo}/actions/runs/${args.runId}/jobs`)
        const list = jobs.jobs ?? []
        let target = list[0]
        if (typeof args.job === 'string' && args.job.trim().length > 0) {
          const needle = args.job.trim()
          target = list.find((job) => String(job.id) === needle || job.name === needle)
          if (!target) throw new Error(`找不到 job：${needle}`)
        } else {
          target = list.find((job) => job.conclusion && !['success', 'skipped'].includes(job.conclusion)) ?? list[0]
        }
        if (!target) throw new Error('这条 run 没有 job')
        const response = await fetch(`${config.apiBase}/repos/${repo}/actions/jobs/${target.id}/logs`, {
          headers: { Authorization: `Bearer ${token}`, Accept: 'application/vnd.github+json' },
          signal: AbortSignal.timeout(120_000),
        })
        if (!response.ok) throw new Error(`拉取日志失败：${response.status} ${response.statusText}`)
        const text = await response.text()
        const all = text.split('\n')
        const maxLines = Math.min(500, Math.max(20, Math.trunc(args.maxLines ?? 120)))
        const tail = all.slice(-maxLines).map((line) => line.replace(/\u001b\[[0-9;]*m/g, '').trimEnd())
        const errors = all
          .filter((line) => /error|failed|failure|fatal/i.test(line))
          .slice(-30)
          .map((line) => line.replace(/\u001b\[[0-9;]*m/g, '').trim())
        const lines = [...new Set([...errors, ...tail])].filter(Boolean)
        return {
          runId: args.runId,
          job: target.name,
          lines,
          summary: `run ${args.runId} · job ${target.name} [${target.conclusion ?? 'running'}] 日志末尾：\n${lines.slice(-80).join('\n')}`,
        }
      },
    }),
  )

  // 18. 发版
  register(
    defineTool({
      name: 'release_publish',
      description:
        '从一条已完成的 Release run 里挑出全部产物（管理器 APK、8 个 ksud、3 个 ksuinit、16 个 LKM），建 release、传资产并发布。资产命名规则与本项目历次发布一致。未给 bodyFile 时自动按 conventional commit 生成发布说明。',
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
        let body
        if (args.bodyFile !== undefined && args.bodyFile.length > 0) {
          body = await readFile(join(config.repoPath, safePath(args.bodyFile)), 'utf8')
        } else {
          const previous = await gitLine(config, ['describe', '--tags', '--abbrev=0', '--match', 'v[0-9]*', `${args.tag}^`])
          const generated = await buildReleaseNotes(config, previous, args.tag, args.tag)
          body = generated.markdown
        }

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
