/**
 * dsha-diksu-git · 核心层
 *
 * 这里放所有「不需要 DSH 运行时」的东西：进程调用、路径/分支/版本校验、
 * porcelain 解析、提交审计规则、token 解析、重试分类、GitHub API、zip 解包。
 * 拆出来的目的很实在：`node test/run-tests.mjs` 在没有 @deepseek-ai/dsh-tools
 * 的机器上也能跑（仓库副本就是这种情况）。
 *
 * 安全模型：
 * - token 有多层来源（插件配置 > token 文件 > DSH 凭据服务 > 环境变量/.env），
 *   任何一层拿到都行；拿不到时抛一条**可执行**的说明，而不是退化成匿名请求；
 * - 传给 git 的 token 只存在于临时 GIT_ASKPASS 脚本的**子进程环境变量**里，
 *   脚本内容是 `echo "$DSH_GIT_TOKEN"`，磁盘上没有明文，用完连目录一起删；
 * - 所有返回给模型的文本都会把 token 替换成 `***`；
 * - 路径参数只接受仓库内相对路径；ref/branch/tag/remote/workflow 都过白名单校验，
 *   避免 `--output=/tmp/x` 这类参数注入；
 * - main/master/develop/trunk/release/* 等受保护分支禁止强推与删除（可显式放行）。
 */
import { execFile } from 'node:child_process'
import { access, chmod, mkdir, mkdtemp, readFile, readdir, rm, stat as fsStat, writeFile } from 'node:fs/promises'
import { homedir, tmpdir } from 'node:os'
import { dirname, isAbsolute, join, resolve } from 'node:path'

// ────────────────────────────── 常量 ──────────────────────────────

/** token 候选环境变量名（按优先级）。 */
export const DEFAULT_TOKEN_ENVS = ['DSH_GIT_TOKEN', 'GITHUB_TOKEN', 'GH_TOKEN', 'GITHUB_PAT']

/** token 候选文件（相对 ~ 展开）。 */
export const DEFAULT_TOKEN_FILES = ['.dsh/git-token', '.dsh/github-token', '.config/dsha/git-token']

/** git 网络操作里值得重试的错误特征（其余一律不重试，避免"推送被拒还重试 12 次"）。 */
const RETRYABLE_PATTERNS = [
  /could not resolve host/i,
  /couldn't resolve host/i,
  /connection (?:timed out|reset|refused|closed)/i,
  /operation timed out/i,
  /timeout/i,
  /early eof/i,
  /rpc failed/i,
  /index-pack failed/i,
  /unable to access/i,
  /the remote end hung up/i,
  /gnutls|tls|ssl/i,
  /temporary failure in name resolution/i,
  /network is unreachable/i,
  /(?:^|\D)(?:429|500|502|503|504)(?:\D|$)/,
]

/** 明确不可重试的错误特征（token 错误、权限、非快进）。 */
const NON_RETRYABLE_PATTERNS = [
  /authentication failed/i,
  /invalid (?:username or password|token|credentials)/i,
  /bad credentials/i,
  /permission denied/i,
  /403 forbidden/i,
  /401 unauthorized/i,
  /non-fast-forward/i,
  /\[rejected\]/i,
  /protected branch/i,
  /repository not found/i,
  /does not exist/i,
]

// ────────────────────────────── 进程 ──────────────────────────────

/** 可被 abort 打断的等待。 */
export function sleep(ms, signal) {
  return new Promise((resolvePromise, reject) => {
    if (signal?.aborted) {
      reject(signal.reason instanceof Error ? signal.reason : new Error('已取消'))
      return
    }
    const timer = setTimeout(() => {
      signal?.removeEventListener?.('abort', onAbort)
      resolvePromise()
    }, ms)
    const onAbort = () => {
      clearTimeout(timer)
      reject(signal?.reason instanceof Error ? signal.reason : new Error('已取消'))
    }
    signal?.addEventListener?.('abort', onAbort, { once: true })
  })
}

/**
 * 跑一条命令，永不因非零退出码抛异常（调用方自己判 code）。
 * @returns {Promise<{code:number, stdout:string, stderr:string, timedOut:boolean, aborted:boolean, error?:string}>}
 */
export function run(command, args, options = {}) {
  const { cwd, env, timeoutMs = 180_000, input, signal } = options
  return new Promise((resolveRun) => {
    let settled = false
    const finish = (payload) => {
      if (settled) return
      settled = true
      resolveRun(payload)
    }
    let child
    try {
      child = execFile(
        command,
        args,
        {
          cwd,
          env: env ?? process.env,
          timeout: timeoutMs,
          maxBuffer: 64 * 1024 * 1024,
          encoding: 'utf8',
          signal,
        },
        (error, stdout, stderr) => {
          const aborted = Boolean(error && (error.name === 'AbortError' || error.code === 'ABORT_ERR'))
          finish({
            code: error && typeof error.code === 'number' ? error.code : error ? 1 : 0,
            stdout: stdout ?? '',
            stderr: stderr ?? '',
            timedOut: Boolean(error && error.killed && !aborted),
            aborted,
            ...(error && typeof error.code === 'string' ? { error: error.code } : {}),
          })
        },
      )
    } catch (error) {
      finish({ code: 1, stdout: '', stderr: '', timedOut: false, aborted: false, error: String(error?.message ?? error) })
      return
    }
    if (input !== undefined) child.stdin?.end(input)
  })
}

// ────────────────────────────── 仓库定位 ──────────────────────────────

/** 已确认过的仓库根目录（避免每次 git 调用都多跑一次 rev-parse）。 */
const verifiedRepos = new Set()
/** 自动探测的结果，按 cwd 缓存。 */
const detectedRepos = new Map()

async function isGitRepo(dir) {
  const result = await run('git', ['-C', dir, 'rev-parse', '--git-dir'], { timeoutMs: 15_000 })
  return result.code === 0
}

/**
 * 解析仓库根目录。
 *
 * 1.1.0 的坑：`repoPath: z.string().default(process.cwd())` 在**模块加载时**求值，
 * 而插件加载时的 cwd 是 harness 的启动目录（本机是 /root），于是所有工具都在
 * 一个不是 git 仓库的目录里跑，git_status 直接报 "value is not lossless JSON"。
 * 现在改成调用时解析 + 明确报错。
 */
export async function repoRoot(config) {
  const configured = typeof config?.repoPath === 'string' ? config.repoPath.trim() : ''
  if (configured.length > 0) {
    const absolute = isAbsolute(configured) ? configured : resolve(configured)
    if (verifiedRepos.has(absolute)) return absolute
    if (!(await isGitRepo(absolute))) {
      throw new Error(
        `repoPath 不是 git 仓库：${absolute}\n` +
          '请在「设置 → 插件 → dsha-diksu-git」里把 repoPath 改成仓库根目录（例如 /sdcard/diksu），' +
          '或把环境变量 DSH_WORKSPACE 指向它。',
      )
    }
    verifiedRepos.add(absolute)
    return absolute
  }
  const cwd = process.cwd()
  if (detectedRepos.has(cwd)) {
    const cached = detectedRepos.get(cwd)
    if (cached === null) throw notARepoError(cwd)
    return cached
  }
  const seeds = ['DSH_WORKSPACE', 'DSH_PROJECT_ROOT', 'DSH_CWD']
    .map((key) => process.env[key])
    .filter((value) => typeof value === 'string' && value.length > 0)
  seeds.push(cwd)
  for (const seed of seeds) {
    let dir = resolve(seed)
    for (let depth = 0; depth < 12; depth += 1) {
      if (await isGitRepo(dir)) {
        detectedRepos.set(cwd, dir)
        return dir
      }
      const parent = dirname(dir)
      if (parent === dir) break
      dir = parent
    }
  }
  detectedRepos.set(cwd, null)
  throw notARepoError(cwd)
}

function notARepoError(cwd) {
  return new Error(
    `找不到 git 仓库（从 ${cwd} 向上找了 12 层）。\n` +
      '请在「设置 → 插件 → dsha-diksu-git」里把 repoPath 设成仓库根目录，或设置环境变量 DSH_WORKSPACE。',
  )
}

// ────────────────────────────── git ──────────────────────────────

/** git 封装：固定带 -C 仓库路径，可选带作者身份。 */
export async function git(config, args, options = {}) {
  const root = await repoRoot(config)
  const full = ['-C', root]
  if (options.withAuthor) {
    full.push(
      '-c',
      `user.name=${config.authorName || 'dsh-agent'}`,
      '-c',
      `user.email=${config.authorEmail || 'dsh-agent@localhost'}`,
    )
  }
  full.push(...args)
  return run('git', full, options)
}

/** 取一条 git 输出（失败给空串）。 */
export async function gitLine(config, args, options = {}) {
  const result = await git(config, args, options)
  return result.code === 0 ? result.stdout.trim() : ''
}

/** 取一条 git 输出，失败抛错。 */
export async function gitOrThrow(config, args, label, options = {}) {
  const result = await git(config, args, options)
  if (result.code !== 0) {
    throw new Error(`${label || 'git 命令'}失败：${(result.stderr || result.stdout).trim() || `退出码 ${result.code}`}`)
  }
  return result.stdout
}

// ────────────────────────────── 参数校验 ──────────────────────────────

/** 路径参数校验：只接受仓库内相对路径，拒绝绝对路径、盘符与 .. 穿越。 */
export function safePath(input) {
  if (typeof input !== 'string' || input.trim().length === 0) throw new Error('路径不能为空')
  if (input.includes('\0')) throw new Error('路径不能包含 NUL')
  if (input.startsWith('/') || /^[A-Za-z]:[\\/]/.test(input)) {
    throw new Error(`只接受仓库内相对路径（不要绝对路径）：${input}`)
  }
  const normalized = input.replace(/\\/g, '/').replace(/^\.\//, '')
  if (normalized.split('/').includes('..')) throw new Error(`路径不能包含 ..：${input}`)
  return normalized.length === 0 ? '.' : normalized
}

/** 批量校验路径参数。 */
export function safePaths(paths) {
  if (!Array.isArray(paths)) return []
  return paths.map((item) => safePath(item))
}

/** 分支名校验：拒绝空白、..、~^:?*[\\、以 - 开头等 git 不接受的形式。 */
export function safeBranchName(name) {
  if (typeof name !== 'string' || name.trim().length === 0) throw new Error('分支名不能为空')
  const value = name.trim()
  if (
    value.startsWith('-') ||
    value.includes('+') ||
    /[\s~^:?*\[\\]/.test(value) ||
    value.includes('..') ||
    value.includes('@{') ||
    value.endsWith('/') ||
    value.endsWith('.') ||
    value.endsWith('.lock')
  ) {
    throw new Error(`分支名不合规：${name}`)
  }
  return value
}

/** 远端名校验（允许 origin/myfork 这种带斜杠的写法）。 */
export function safeRemoteName(name) {
  if (typeof name !== 'string' || name.trim().length === 0) throw new Error('远端名不能为空')
  const value = name.trim()
  if (!/^[A-Za-z0-9._/-]+$/.test(value) || value.startsWith('-') || value.includes('..') || value.endsWith('/')) {
    throw new Error(`远端名不合规：${name}`)
  }
  return value
}

/**
 * revision 校验：允许 HEAD~1、v1..HEAD、main...HEAD、@{upstream} 这类语法，
 * 但拒绝空白、NUL 与以 `-` 开头（后者会被 git 当成选项 → 参数注入）。
 */
export function safeRevision(rev, label = 'ref') {
  if (typeof rev !== 'string' || rev.trim().length === 0) throw new Error(`${label} 不能为空`)
  const value = rev.trim()
  if (value.startsWith('-') || /\s/.test(value) || value.includes('\0')) throw new Error(`${label} 不合规：${rev}`)
  if (value.length > 512) throw new Error(`${label} 太长：${rev.slice(0, 32)}…`)
  return value
}

/** tag 名校验（本项目用 vX.Y.Z-ultra，但工具本身只要求是合法 tag）。 */
export function safeTagName(name) {
  if (typeof name !== 'string' || name.trim().length === 0) throw new Error('tag 名不能为空')
  const value = name.trim()
  if (
    value.startsWith('-') ||
    !/^[A-Za-z0-9._/-]+$/.test(value) ||
    value.includes('..') ||
    value.includes('@{') ||
    value.endsWith('/') ||
    value.endsWith('.lock')
  ) {
    throw new Error(`tag 名不合规：${name}`)
  }
  return value
}

/** workflow 文件名校验（只接受仓库内 .github/workflows 下的名字）。 */
export function safeWorkflowName(name) {
  if (typeof name !== 'string' || name.trim().length === 0) throw new Error('workflow 不能为空')
  const value = name.trim()
  if (!/^[A-Za-z0-9._-]+\.ya?ml$/.test(value)) throw new Error(`workflow 文件名不合规：${name}`)
  return value
}

/** 判断分支是否受保护。 */
export function isProtectedBranch(config, branch) {
  const patterns = String(config.protectedBranches ?? '')
    .split(',')
    .map((item) => item.trim())
    .filter(Boolean)
  return patterns.some((pattern) => (pattern.endsWith('*') ? branch.startsWith(pattern.slice(0, -1)) : branch === pattern))
}

/** 按字节截断文本，返回是否截断。 */
export function truncateText(text, maxBytes) {
  const buffer = Buffer.from(text, 'utf8')
  if (buffer.length <= maxBytes) return { text, truncated: false }
  // 按行切：字节切法会把一行 UTF-8 从中间截断，读起来像乱码
  const head = buffer.subarray(0, maxBytes).toString('utf8')
  const lastBreak = head.lastIndexOf('\n')
  const body = lastBreak > 0 ? head.slice(0, lastBreak) : head
  return { text: `${body}\n…（已截断，共 ${buffer.length} 字节）`, truncated: true }
}

/** 把 token 从任何输出里抹掉。 */
export function mask(text, token) {
  if (typeof text !== 'string' || text.length === 0) return text
  if (typeof token !== 'string' || token.length === 0) return text
  return text.split(token).join('***')
}

/**
 * 从 git 的 stderr/stdout 里取出「够判断原因」的尾部若干行。
 *
 * 1.1.0 只留最后一行，于是常见报错的因果被砍掉（`fatal: ...` 在前一行，
 * 留下的那行是 `and the repository exists.`）。这里保留最后 4 行，并抹掉 token。
 */
export function errorDetail(result, token, maxLines = 4) {
  const text = String(result?.stderr || result?.stdout || '').trim()
  if (text.length === 0) return `退出码 ${result?.code ?? 'unknown'}`
  const lines = text
    .split('\n')
    .map((line) => line.trimEnd())
    .filter((line) => line.trim().length > 0)
  return mask(lines.slice(-maxLines).join('\n'), token)
}

/** 递归 mask。 */
export function maskAll(value, token) {
  if (typeof value === 'string') return mask(value, token)
  if (Array.isArray(value)) return value.map((item) => maskAll(item, token))
  if (value && typeof value === 'object') {
    const out = {}
    for (const [key, inner] of Object.entries(value)) out[key] = maskAll(inner, token)
    return out
  }
  return value
}

/**
 * 让值变成「无损 JSON」：NaN/Infinity → 0，undefined 键丢掉，函数/符号丢掉。
 *
 * DSH 的 tools 运行时对输出做 JSON 快照校验，一旦出现 NaN/undefined 就抛
 * "value is not lossless JSON"，模型只能看到一个毫无信息的报错。统一在这里兜住。
 */
export function lossless(value) {
  if (value === null) return null
  if (typeof value === 'number') return Number.isFinite(value) ? value : 0
  if (typeof value === 'string' || typeof value === 'boolean') return value
  if (value === undefined || typeof value === 'function' || typeof value === 'symbol') return undefined
  if (value instanceof Date) return value.toISOString()
  if (Buffer.isBuffer(value)) return value.toString('utf8')
  if (Array.isArray(value)) {
    return value.map((item) => {
      const next = lossless(item)
      return next === undefined ? null : next
    })
  }
  if (typeof value === 'object') {
    const out = {}
    for (const [key, inner] of Object.entries(value)) {
      const next = lossless(inner)
      if (next !== undefined) out[key] = next
    }
    return out
  }
  return String(value)
}

// ────────────────────────────── token 解析 ──────────────────────────────

function expandHome(file) {
  const value = String(file ?? '').trim()
  if (value.startsWith('~/')) return join(homedir(), value.slice(2))
  return value
}

/** 哪些层级配置了 token（只报事实，不返回值）。 */
export async function tokenInventory(config, credentials) {
  const rows = []
  rows.push({ source: '插件配置 token', kind: 'config', configured: typeof config?.token === 'string' && config.token.trim().length > 0 })
  const file = expandHome(config?.tokenFile)
  if (file.length > 0) {
    rows.push({ source: `文件 ${file}`, kind: 'file', configured: await fileHasToken(file) })
  }
  for (const rel of DEFAULT_TOKEN_FILES) {
    const path = join(homedir(), rel)
    rows.push({ source: `文件 ${path}`, kind: 'file', configured: await fileHasToken(path) })
  }
  const names = [...new Set([config?.tokenEnv, ...DEFAULT_TOKEN_ENVS].filter((item) => typeof item === 'string' && item.length > 0))]
  for (const name of names) {
    const fromEnv = typeof process.env[name] === 'string' && process.env[name].trim().length > 0
    let fromStore = false
    if (credentials !== undefined) {
      try {
        const described = await credentials.describe?.(name)
        fromStore = described?.set === true
      } catch {
        fromStore = false
      }
    }
    rows.push({ source: `凭据/环境变量 ${name}`, kind: 'env', configured: fromEnv || fromStore })
  }
  return rows
}

async function fileHasToken(path) {
  try {
    const text = await readFile(path, 'utf8')
    return text.trim().length > 0
  } catch {
    return false
  }
}

/** 没有 token 时的统一说明（三层来源都讲清楚，避免用户去猜 export）。 */
export function missingTokenMessage(config) {
  const names = [...new Set([config?.tokenEnv, ...DEFAULT_TOKEN_ENVS].filter((item) => typeof item === 'string' && item.length > 0))]
  const ref = names[0] ?? 'DSH_GIT_TOKEN'
  return [
    `没有可用的 git token（试过：插件配置 token、token 文件、DSH 凭据服务和环境变量 ${names.join(' / ')}）。`,
    '任选一种方式配置（都不需要把 token 贴进对话）：',
    `  1) 写进 DSH 的 .env：把 ${ref}=<你的 PAT> 加进 ~/.dsh/.env，然后重启 DSH；`,
    '  2) 写进文件：echo <你的 PAT> > ~/.dsh/git-token && chmod 600 ~/.dsh/git-token；',
    '  3) 设置 → 插件 → dsha-diksu-git：把 token 填进 token 字段（掩码保存，不会回读）；',
    `  4) 写进 DSH 凭据库：在设置里给 ${ref} 存一个凭据（存进 ~/.dsh/.credentials.yaml）。`,
    '提示：在 Shell 设置里 export 只影响 bash 工具的子进程，不会进 harness 进程，所以对插件无效。',
  ].join('\n')
}

/**
 * 解析 token。
 *
 * 顺序：插件配置 `token` → `tokenFile` → 默认 token 文件 → DSH 凭据服务
 * （`ctx.credentials.resolve`，它自己会回落 .env/进程环境）→ process.env 直读。
 *
 * @param config 插件配置
 * @param credentials 可选的 ctx.credentials 服务
 * @returns {Promise<{value:string, source:string}>}
 * @throws 找不到时抛带完整操作指引的错误
 */
export async function resolveToken(config, credentials) {
  const direct = typeof config?.token === 'string' ? config.token.trim() : ''
  if (direct.length > 0) return { value: direct, source: '插件配置 token' }

  const customFile = expandHome(config?.tokenFile)
  for (const path of [customFile, ...DEFAULT_TOKEN_FILES.map((rel) => join(homedir(), rel))].filter(Boolean)) {
    try {
      const text = await readFile(path, 'utf8')
      const value = text.trim()
      if (value.length > 0) return { value, source: `文件 ${path}` }
    } catch {
      // 没有这个文件就继续
    }
  }

  const names = [...new Set([config?.tokenEnv, ...DEFAULT_TOKEN_ENVS].filter((item) => typeof item === 'string' && item.length > 0))]
  if (credentials !== undefined) {
    for (const name of names) {
      try {
        const hit = await credentials.resolve(name)
        const value = typeof hit?.value === 'string' ? hit.value.trim() : ''
        if (value.length > 0) return { value, source: `凭据/环境变量 ${name}` }
      } catch {
        // 凭据文件损坏等情况下继续尝试直读环境变量
      }
    }
  }
  for (const name of names) {
    const value = process.env[name]
    if (typeof value === 'string' && value.trim().length > 0) return { value: value.trim(), source: `环境变量 ${name}` }
  }
  throw new Error(missingTokenMessage(config))
}

// ────────────────────────────── askpass / hosts / 重试 ──────────────────────────────

/**
 * 临时 askpass：脚本本身只回显环境变量，token 走子进程环境，磁盘上没有明文。
 * 用户名固定 x-access-token（GitHub/GitLab 都认），密码才是 token。
 */
export async function withAskpass(token, fn) {
  const dir = await mkdtemp(join(tmpdir(), 'diksu-git-'))
  const script = join(dir, 'askpass.sh')
  try {
    await writeFile(
      script,
      '#!/bin/sh\ncase "$1" in\n  *[Uu]sername*) echo "x-access-token" ;;\n  *[Pp]assword*) printf "%s" "$DSH_GIT_TOKEN" ;;\n  *) echo "" ;;\nesac\n',
      'utf8',
    )
    await chmod(script, 0o700)
    return await fn({
      GIT_ASKPASS: script,
      GIT_ASKPASS_REQUIRE: 'force',
      GIT_TERMINAL_PROMPT: '0',
      GIT_CONFIG_COUNT: '1',
      GIT_CONFIG_KEY_0: 'credential.helper',
      GIT_CONFIG_VALUE_0: '',
      DSH_GIT_TOKEN: token,
    })
  } finally {
    // 1.1.0 用 unlink(dir) 删目录（必然失败），临时目录会一直堆在 /tmp。
    await rm(dir, { recursive: true, force: true }).catch(() => {})
  }
}

/**
 * 临时把 hosts 钉到指定 IP，结束后一定还原。
 *
 * 1.1.0 的问题：读写失败都静默忽略，用户以为生效其实没生效；而且每次都在文件末尾
 * 追加一行，内容会越积越多。现在：已存在就不重复写、失败把原因带回去。
 *
 * @param config 插件配置
 * @param fn 实际动作
 * @param warnings 收集告警的数组（可选）
 */
export async function withHostsPin(config, fn, warnings) {
  const pin = String(config?.hostsPin ?? '').trim()
  if (pin.length === 0) return fn()
  if (!/^\d{1,3}(?:\.\d{1,3}){3}\s+\S+$/.test(pin)) {
    warnings?.push(`hostsPin 格式不对（应形如 "20.27.177.113 github.com"），已忽略：${pin}`)
    return fn()
  }
  const file = config.hostsFile || '/etc/hosts'
  let original
  try {
    original = await readFile(file, 'utf8')
  } catch (error) {
    warnings?.push(`读不到 ${file}（${error?.code ?? error}），hostsPin 未生效`)
    return fn()
  }
  if (original.split('\n').some((line) => line.trim() === pin)) {
    warnings?.push(`hostsPin 已经存在于 ${file}，本次未重复写入（可能需要先清理旧记录）`)
    return fn()
  }
  try {
    await writeFile(file, `${original.replace(/\n?$/, '\n')}${pin}\n`, 'utf8')
  } catch (error) {
    warnings?.push(`写 ${file} 失败（${error?.code ?? error}），hostsPin 未生效：非 root 环境下改不了系统 hosts`)
    return fn()
  }
  try {
    return await fn()
  } finally {
    await writeFile(file, original, 'utf8').catch(() => {})
  }
}

/** 这个错误值得重试吗？ */
export function isRetryable(text, status) {
  const sample = String(text ?? '')
  if (NON_RETRYABLE_PATTERNS.some((re) => re.test(sample))) return false
  if (typeof status === 'number' && status >= 400 && status < 500 && status !== 429) return false
  if (typeof status === 'number' && (status === 429 || status >= 500)) return true
  return RETRYABLE_PATTERNS.some((re) => re.test(sample))
}

/**
 * 带重试地跑一个可能因网络失败的动作；不可重试的错误立刻返回。
 * @returns {Promise<{ok:boolean, result?:any, detail?:string, retries:number, retryable:boolean}>}
 */
export async function retry(config, label, attemptFn, options = {}) {
  const attempts = Math.max(1, Math.min(20, Math.trunc(config?.maxAttempts ?? 3)))
  const delay = Math.max(0, Math.trunc(config?.retryDelayMs ?? 3000))
  let last = ''
  let retryable = true
  for (let index = 1; index <= attempts; index += 1) {
    const outcome = await attemptFn(index)
    if (outcome?.ok) return { ...outcome, retries: index - 1 }
    last = outcome?.detail ?? '未知错误'
    retryable = options.retryable === undefined ? isRetryable(last, outcome?.status) : options.retryable
    if (!retryable || index === attempts) return { ok: false, detail: last, retries: index - 1, retryable }
    if (options.signal?.aborted) return { ok: false, detail: '已取消', retries: index - 1, retryable: true }
    await sleep(delay * index, options.signal)
  }
  return { ok: false, detail: `${label} 连续失败 ${attempts} 次：${last}`, retries: attempts - 1, retryable }
}

/** 带 token / hostsPin / 重试的 git 网络动作。 */
export async function networkGit(config, token, label, buildArgs, options = {}) {
  const warnings = []
  const { signal, ...rest } = options
  return withAskpass(token, (askEnv) => {
    const env = { ...process.env, ...askEnv }
    return withHostsPin(
      config,
      () =>
        retry(
          config,
          label,
          async () => {
            const result = await git(config, buildArgs(), { ...rest, env, signal })
            if (result.code === 0) return { ok: true, result }
            const detail = errorDetail(result, token)
            return { ok: false, detail, status: result.code }
          },
          { signal },
        ),
      warnings,
    ).then((outcome) => ({ ...outcome, warnings }))
  })
}

/**
 * 「有 token 走 token，没 token 就匿名试一次」的统一入口。
 *
 * 1.1.0 的所有网络动作都是直接 `resolveToken()`，没配 token 就整体失败 ——
 * 连公开仓库的 fetch 也做不了。公开仓库本来不需要凭据，所以这里退化成一次
 * 匿名调用（禁掉交互提示），只有真的被拒时才把完整的配置指引抛回去。
 */
export async function networkGitAuto(config, credentials, label, buildArgs, options = {}) {
  const { signal, timeoutMs = 240_000 } = options
  let auth
  try {
    auth = { ok: true, ...(await resolveToken(config, credentials)) }
  } catch (error) {
    auth = { ok: false, message: String(error?.message ?? error) }
  }
  if (auth.ok) {
    const outcome = await networkGit(config, auth.value, label, buildArgs, { signal, timeoutMs })
    return { ...outcome, authSource: auth.source }
  }
  const warnings = []
  const result = await withHostsPin(
    config,
    () =>
      git(config, buildArgs(), {
        env: { ...process.env, GIT_TERMINAL_PROMPT: '0' },
        timeoutMs,
        signal,
      }),
    warnings,
  )
  if (result.code === 0) {
    return {
      ok: true,
      result,
      retries: 0,
      warnings: [...warnings, '没有配置 token，已按匿名方式完成（公开仓库可以这样，私有仓库/推送会失败）'],
      authSource: '匿名',
      anonymous: true,
    }
  }
  const detail = errorDetail(result, '')
  return { ok: false, detail, retries: 0, warnings, authSource: '匿名', anonymous: true, missingToken: auth.message }
}

/** GitHub API 调用（带 5xx / 429 / 网络错误重试）。 */
export async function api(config, token, method, path, body, options = {}) {
  const { signal } = options
  const url = path.startsWith('http') ? path : `${config.apiBase}${path}`
  const attempts = Math.max(1, Math.min(6, Math.trunc(config?.maxAttempts ?? 3)))
  let lastMessage = ''
  for (let attempt = 1; attempt <= attempts; attempt += 1) {
    try {
      const response = await fetch(url, {
        method,
        headers: {
          Authorization: `Bearer ${token}`,
          Accept: 'application/vnd.github+json',
          'Content-Type': 'application/json',
          'User-Agent': 'dsha-diksu-git',
          'X-GitHub-Api-Version': '2022-11-28',
        },
        body: body === undefined ? undefined : JSON.stringify(body),
        signal: signal ? AbortSignal.any([signal, AbortSignal.timeout(120_000)]) : AbortSignal.timeout(120_000),
      })
      const text = await response.text()
      let parsed
      try {
        parsed = text.length > 0 ? JSON.parse(text) : {}
      } catch {
        parsed = { raw: text }
      }
      if (response.ok) return parsed
      lastMessage = `${response.status} ${parsed?.message ?? response.statusText}`
      if (!isRetryable(text, response.status) || attempt === attempts) {
        const error = new Error(`GitHub API ${method} ${path} → ${lastMessage}`)
        error.status = response.status
        throw error
      }
    } catch (error) {
      if (error?.status !== undefined) throw error
      lastMessage = String(error?.message ?? error)
      if (!isRetryable(lastMessage) || attempt === attempts) {
        throw new Error(`GitHub API ${method} ${path} → ${lastMessage}`)
      }
    }
    await sleep(Math.max(500, Math.trunc(config?.retryDelayMs ?? 3000)) * attempt, signal)
  }
  throw new Error(`GitHub API ${method} ${path} → ${lastMessage}`)
}

// ────────────────────────────── 仓库状态 ──────────────────────────────

/** 当前分支名（detached 时返回 HEAD）。 */
export async function currentBranch(config) {
  return (await gitLine(config, ['rev-parse', '--abbrev-ref', 'HEAD'])) || 'HEAD'
}

/** 上游分支名（没有则空串）。 */
export async function upstreamBranch(config) {
  return gitLine(config, ['rev-parse', '--abbrev-ref', '--symbolic-full-name', '@{upstream}'])
}

/** 领先/落后上游；没有上游时都返回 0。 */
export async function aheadBehind(config) {
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

const CONFLICT_CODES = new Set(['DD', 'AU', 'UD', 'UA', 'DU', 'AA', 'UU'])

/**
 * 解析 `git status --porcelain=v1 -z` 输出。
 *
 * 1.1.0 用文本行切分，遇到重命名（`R  old -> new`）和 core.quotePath 的引号转义
 * 会把路径解析错。`-z` 下路径不加引号、重命名/复制会多一段原始路径。
 *
 * @param raw NUL 分隔的原始输出
 * @returns {{staged:string[], unstaged:string[], untracked:string[], conflicts:string[], dirty:string[], entries:object[]}}
 */
export function parseStatusZ(raw) {
  const fields = String(raw ?? '').split('\0')
  const entries = []
  for (let index = 0; index < fields.length; index += 1) {
    const record = fields[index]
    if (record.length < 4) continue
    const x = record[0]
    const y = record[1]
    const path = record.slice(3)
    let origPath = ''
    if (x === 'R' || x === 'C' || y === 'R' || y === 'C') {
      origPath = fields[index + 1] ?? ''
      index += 1
    }
    entries.push({ x, y, path, origPath })
  }
  const staged = []
  const unstaged = []
  const untracked = []
  const conflicts = []
  const dirty = []
  for (const entry of entries) {
    const { x, y, path, origPath } = entry
    const display = origPath.length > 0 ? `${path} ← ${origPath}` : path
    if (x === '?' && y === '?') {
      untracked.push(path)
      dirty.push(`?? ${path}`)
      continue
    }
    if (CONFLICT_CODES.has(`${x}${y}`)) {
      conflicts.push(path)
      dirty.push(`${x}${y} ${display}`)
      continue
    }
    if (x !== ' ' && x !== '?') staged.push(`${x} ${display}`)
    if (y !== ' ' && y !== '?') unstaged.push(`${y} ${display}`)
    dirty.push(`${x}${y} ${display}`)
  }
  return { staged, unstaged, untracked, conflicts, dirty, entries }
}

/** 读取工作区状态（-z，重命名/引号安全）。 */
export async function statusSummary(config) {
  const result = await git(config, ['status', '--porcelain=v1', '-z', '--untracked-files=all'])
  return parseStatusZ(result.stdout)
}

/** 判断是否有进行中的 merge / rebase / cherry-pick 等。 */
export async function inProgressOperations(config) {
  const gitDir = await gitLine(config, ['rev-parse', '--absolute-git-dir'])
  if (!gitDir) return []
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
      await access(join(gitDir, file))
      if (!found.includes(label)) found.push(label)
    } catch {
      // 不存在就是没在进行
    }
  }
  return found
}

/** 版本号公式：major*10000 + minor*1000 + patch*100 + tag 之后（--first-parent）的提交数。 */
export function versionCodeFor(tag, commitsAfterTag) {
  const match = /^v(\d+)\.(\d+)\.(\d+)/.exec(String(tag ?? ''))
  if (match === null) return null
  const major = Number(match[1])
  const minor = Number(match[2])
  const patch = Number(match[3])
  if (![major, minor, patch].every((value) => Number.isFinite(value))) return null
  const extra = Number.isFinite(commitsAfterTag) ? commitsAfterTag : 0
  return major * 10000 + minor * 1000 + patch * 100 + extra
}

/** 最近 tag 与 versionCode。 */
export async function versionInfo(config) {
  const tag = await gitLine(config, ['describe', '--tags', '--abbrev=0', '--match', 'v[0-9]*'])
  const headTag = await gitLine(config, ['describe', '--tags', '--exact-match', '--match', 'v[0-9]*'])
  if (tag.length === 0) return { tag: '', headTag: '', versionCode: null, commitsAfterTag: null }
  const countText = await gitLine(config, ['rev-list', '--count', '--first-parent', `${tag}..HEAD`])
  const parsedCount = Number.parseInt(countText, 10)
  const commitsAfterTag = Number.isNaN(parsedCount) ? null : parsedCount
  return { tag, headTag, versionCode: versionCodeFor(tag, commitsAfterTag), commitsAfterTag }
}

/** 取 diff 的文件列表（name-status 文本）。 */
export async function diffNameStatus(config, args) {
  const result = await git(config, ['diff', '--name-status', '--no-color', ...args])
  if (result.code !== 0) throw new Error(`git diff --name-status 失败：${(result.stderr || result.stdout).trim()}`)
  return result.stdout.trim().split('\n').filter(Boolean)
}

// ────────────────────────────── 提交审计 ──────────────────────────────

/** 敏感信息规则：只报位置，不回显命中内容，避免把 secret 再写进对话。 */
export const AUDIT_RULES = [
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
 * 审计一段 diff：识别新增行里的 secret、冲突标记、大文件。
 * @returns {{findings:string[], highPaths:string[]}}
 */
/**
 * 还原 diff 头里的文件路径。
 *
 * git 会改写两处：含空格时 `+++ b/x y` 末尾补一个 TAB；非 ASCII / 控制字符走
 * `core.quotePath` 的 C 风格引号转义。直接 `line.slice(6)` 得到 `x y\t` 或带引号的串，
 * 后续 `git reset -- <path>` 匹配不上 —— 那时候「已移出暂存区」是假的，secret 还在索引里。
 */
export function unquoteGitPath(raw) {
  let path = String(raw ?? '')
  if (path.endsWith('\t')) path = path.slice(0, -1)
  if (path.length > 1 && path.startsWith('"') && path.endsWith('"')) {
    const body = path.slice(1, -1)
    const bytes = []
    for (let index = 0; index < body.length; index += 1) {
      const char = body[index]
      if (char !== '\\') {
        bytes.push(...Buffer.from(char, 'utf8'))
        continue
      }
      const next = body[index + 1]
      index += 1
      const simple = { a: 7, b: 8, t: 9, n: 10, v: 11, f: 12, r: 13, '"': 34, '\\': 92 }
      if (next in simple) {
        bytes.push(simple[next])
        continue
      }
      const octal = /^[0-7]{3}/.exec(body.slice(index))
      if (octal) {
        bytes.push(Number.parseInt(octal[0], 8))
        index += 2
        continue
      }
      bytes.push(...Buffer.from(next ?? '', 'utf8'))
    }
    return Buffer.from(bytes).toString('utf8')
  }
  return path
}

export function auditDiff(diffText, options = {}) {
  const { maxFileMB = 2, largeFiles = [], path: pathHint } = options
  const findings = []
  const highPaths = new Set()
  let currentPath = typeof pathHint === 'string' ? pathHint : ''
  let newLine = 0
  for (const line of String(diffText ?? '').split('\n')) {
    if (line.startsWith('+++ b/') || line.startsWith('+++ ')) {
      const raw = line.startsWith('+++ b/') ? line.slice(6) : line.slice(4)
      if (raw !== '/dev/null') currentPath = unquoteGitPath(raw)
      continue
    }
    if (line.startsWith('--- ')) continue
    const hunk = /^@@ -\d+(?:,\d+)? \+(\d+)(?:,\d+)? @@/.exec(line)
    if (hunk) {
      newLine = Number.parseInt(hunk[1], 10)
      continue
    }
    if (!line.startsWith('+')) continue
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

/**
 * 一次 `git cat-file --batch-check` 拿到多个对象大小。
 * 1.1.0 是每个文件起一个 git 进程（最多 300 次子进程），暂存区一大就非常慢。
 */
export async function objectSizes(config, specs) {
  const unique = [...new Set(specs.filter((item) => typeof item === 'string' && item.length > 0))]
  if (unique.length === 0) return new Map()
  const result = await git(config, ['cat-file', '--batch-check=%(objectname) %(objecttype) %(objectsize)'], {
    input: `${unique.join('\n')}\n`,
  })
  const map = new Map()
  for (const line of result.stdout.split('\n')) {
    if (!line.trim() || line.endsWith(' missing') || line.includes(' ambiguous')) continue
    const parts = line.trim().split(/\s+/)
    const size = Number.parseInt(parts[2] ?? '', 10)
    if (Number.isFinite(size)) map.set(parts[0], size)
  }
  // 用 spec → oid 对不上时（path 与 oid 不同）退回逐个 :path 查询
  if (map.size !== unique.length) {
    for (const spec of unique) {
      const single = await git(config, ['cat-file', '-s', spec])
      const size = Number.parseInt(single.stdout.trim(), 10)
      if (Number.isFinite(size)) map.set(spec, size)
    }
  }
  return map
}

/** 暂存区大文件（超过阈值的才返回）。 */
export async function stagedLargeFiles(config, maxFileMB) {
  const names = (await gitLine(config, ['diff', '--cached', '--name-only', '-z'])).split('\0').filter(Boolean)
  if (names.length === 0) return []
  const sizes = await objectSizes(
    config,
    names.slice(0, 400).map((path) => `:${path}`),
  )
  const result = []
  for (const path of names) {
    const bytes = sizes.get(`:${path}`)
    if (Number.isFinite(bytes) && bytes > maxFileMB * 1024 * 1024) result.push({ path, sizeMB: bytes / 1024 / 1024 })
  }
  return result
}

/** 工作区大文件（含未跟踪文件）。 */
export async function worktreeLargeFiles(config, maxFileMB, paths) {
  const root = await repoRoot(config)
  const names = paths ?? (await gitLine(config, ['diff', '--name-only', '-z'])).split('\0').filter(Boolean)
  const result = []
  for (const path of names.slice(0, 400)) {
    try {
      const info = await fsStat(join(root, path))
      if (info.isFile() && info.size > maxFileMB * 1024 * 1024) {
        result.push({ path, sizeMB: info.size / 1024 / 1024 })
      }
    } catch {
      // 文件可能已被删除
    }
  }
  return result
}

/** 给未跟踪文件造 diff，让 audit 能看到它们（1.1.0 完全漏掉未跟踪文件）。 */
async function untrackedDiff(config, paths) {
  const chunks = []
  for (const path of paths.slice(0, 50)) {
    const result = await git(config, ['diff', '--no-index', '--no-color', '--unified=0', '--', '/dev/null', path])
    // --no-index 有差异时退出码是 1，属于正常情况
    if (result.stdout.length > 0) chunks.push(result.stdout)
  }
  return chunks.join('')
}

/**
 * 生成审计 diff 与文件大小信息。
 * @param scope staged | worktree | range
 */
export async function auditSource(config, scope, range) {
  const maxFileMB = config.auditMaxFileMB
  if (scope === 'worktree') {
    const tracked = await gitLine(config, ['diff', '--no-color', '--no-ext-diff'])
    const status = await statusSummary(config)
    const untracked = await untrackedDiff(config, status.untracked)
    return {
      diff: `${tracked}\n${untracked}`,
      largeFiles: await worktreeLargeFiles(config, maxFileMB, [...status.untracked, ...status.unstaged.map((line) => line.slice(2).split(' ← ')[0])]),
    }
  }
  if (scope === 'range') {
    if (!range) throw new Error('scope=range 时必须提供 range，例如 v4.0.0..HEAD')
    const diff = await gitLine(config, ['diff', '--no-color', '--no-ext-diff', safeRevision(range, 'range')])
    return { diff, largeFiles: [] }
  }
  const diff = await gitLine(config, ['diff', '--cached', '--no-color', '--no-ext-diff'])
  return { diff, largeFiles: await stagedLargeFiles(config, maxFileMB) }
}

/**
 * 逐个文件审计暂存区。
 *
 * 提交前的安全拦截不能依赖「从 diff 文本里抠路径」：空格、引号、非 ASCII 都会让
 * 后续 `git reset -- <path>` 落空，于是工具声称已移出、密钥却还在索引里。
 * 这里的文件清单来自 `git diff --cached --name-only -z`，路径 100% 可回放。
 */
export async function auditStagedFiles(config, paths, maxFileMB = config.auditMaxFileMB) {
  const list = Array.isArray(paths) ? paths : []
  const sizes = list.length > 0 ? await objectSizes(config, list.map((path) => `:${path}`)) : new Map()
  const largeFiles = []
  for (const path of list) {
    const size = sizes.get(`:${path}`) ?? 0
    if (size > maxFileMB * 1024 * 1024) largeFiles.push({ path, sizeMB: size / (1024 * 1024) })
  }
  const findings = []
  const highPaths = new Set()
  for (const path of list) {
    const diff = await git(config, ['diff', '--cached', '--no-color', '--no-ext-diff', '-U0', '--', path])
    if (diff.code !== 0) throw new Error(`读取暂存内容失败（${path}）：${(diff.stderr || diff.stdout).trim()}`)
    const report = auditDiff(diff.stdout, { maxFileMB, largeFiles: [], path })
    findings.push(...report.findings)
    for (const hit of report.highPaths) highPaths.add(hit)
  }
  for (const file of largeFiles) {
    findings.push(`WARN large-file: ${file.path} (${file.sizeMB.toFixed(1)} MB > ${maxFileMB} MB)`)
  }
  return { findings, highPaths: [...highPaths] }
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
export async function collectReleaseNotes(config, from, to) {
  const version = safeRevision(to, 'to')
  const range = from && String(from).trim().length > 0 ? `${safeRevision(from, 'from')}..${version}` : version
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
export async function buildReleaseNotes(config, from, to, version) {
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

// ────────────────────────────── 远端 / 文件 ──────────────────────────────

/**
 * 从远端地址解析 owner/repo。
 * 1.1.0 的正则 `([^/.]+)` 会把带点的仓库名（foo.bar）解析错，也不支持带端口的地址。
 */
export async function repoSlug(config) {
  const url = await gitLine(config, ['remote', 'get-url', config.remote])
  if (url.length === 0) {
    throw new Error(`读不到远端 ${config.remote} 的地址：请先 git remote add / 或在插件配置里改 remote`)
  }
  const cleaned = url
    .replace(/^[A-Za-z][A-Za-z0-9+.-]*:\/\//, '')
    .replace(/^[^@/]+@/, '')
    .replace(/\.git$/, '')
    .replace(/\/$/, '')
  const parts = cleaned.split(/[:/]/).filter(Boolean)
  if (parts.length < 3) throw new Error(`无法从远端地址解析 owner/repo：${url}`)
  const repo = parts[parts.length - 1]
  const owner = parts[parts.length - 2]
  return `${owner}/${repo}`
}

/** 写入仓库内相对路径的文件（自动建父目录）。 */
export async function writeRepoFile(config, relativePath, content) {
  const root = await repoRoot(config)
  const target = resolve(root, safePath(relativePath))
  if (!target.startsWith(`${root}/`) && target !== root) throw new Error(`路径越界：${relativePath}`)
  await mkdir(dirname(target), { recursive: true })
  await writeFile(target, content, 'utf8')
  return target
}

/**
 * 解包 zip。
 *
 * 本机（Android 容器）**没有 unzip**，1.1.0 的 release_publish 会直接卡在这里。
 * 因此优先 unzip，退化到 python3 -m zipfile，两个都没有就给出明确提示。
 */
export async function extractZip(archive, destination) {
  await mkdir(destination, { recursive: true })
  const attempts = [
    ['unzip', ['-o', '-q', archive, '-d', destination]],
    ['python3', ['-m', 'zipfile', '-e', archive, destination]],
  ]
  const errors = []
  for (const [command, args] of attempts) {
    const result = await run(command, args, { timeoutMs: 300_000 })
    if (result.code === 0) return { command, warnings: [] }
    if (result.error === 'ENOENT') {
      errors.push(`${command} 不可用`)
    } else {
      errors.push(`${command} 失败：${(result.stderr || result.stdout).trim() || `退出码 ${result.code}`}`)
    }
  }
  throw new Error(`解包 ${archive} 失败（unzip 与 python3 都不可用/失败）：${errors.join('；')}`)
}
