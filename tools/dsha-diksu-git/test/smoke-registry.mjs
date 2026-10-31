#!/usr/bin/env node
/**
 * 用真实的 DSH defineTool + 输出 schema 校验跑一遍工具。
 *
 * 与 run-tests.mjs 的区别：那边测 core 的纯逻辑，这边测「注册以后的工具」——
 * 参数校验、返回值 schema、JSON 无损快照，都是 harness 真正会做的检查。
 *
 * 需要本目录能找到 @deepseek-ai/dsh-tools（已安装副本 /root/dsha-diksu-git 自带
 * node_modules 软链；仓库副本没有时请先跑 scripts/deploy.sh）。
 *
 *   node test/smoke-registry.mjs
 */
import assert from 'node:assert/strict'
import { mkdtemp, writeFile, rm, rename } from 'node:fs/promises'
import { renameSync } from 'node:fs'
import { homedir, tmpdir } from 'node:os'
import { join } from 'node:path'
import process from 'node:process'

const { validateJsonSchemaValue } = await import('@deepseek-ai/dsh-tools')
const { apply } = await import('../lib/index.js')

const results = []
let failed = 0

async function check(name, fn) {
  try {
    await fn()
    results.push(`  ✓ ${name}`)
  } catch (error) {
    failed += 1
    results.push(`  ✗ ${name}\n      ${String(error?.message ?? error).split('\n').join('\n      ')}`)
  }
}

const registry = new Map()
const routes = []
const logs = []
const ctx = {
  tools: {
    register(tool) {
      assert.equal(registry.has(tool.name), false, `工具重名：${tool.name}`)
      registry.set(tool.name, tool)
    },
  },
  get: () => undefined,
  logger: { info: (text) => logs.push(String(text)), warn: (text) => logs.push(String(text)) },
  effect(fn) {
    return fn()
  },
  // token 配置页用动态 inject 挂到 webServer 上，这里把它捕获下来供测试直接调用
  inject(names, callback) {
    const scoped = {
      webServer: {
        register(spec) {
          routes.push(spec)
          return () => {}
        },
      },
      connection: { authorizeIndex: () => true },
      effect(fn) {
        return fn()
      },
    }
    callback(scoped)
  },
}

/** 直接调用捕获到的路由 handler，模拟一次 HTTP 请求。 */
async function callRoute(method, body) {
  const route = routes[0]
  assert.ok(route, '没有注册 token 路由')
  const { EventEmitter } = await import('node:events')
  const req = new EventEmitter()
  req.method = method
  const out = { code: 0, body: '' }
  const res = {
    writeHead(code) {
      out.code = code
    },
    end(payload) {
      out.body = payload ?? ''
    },
  }
  const promise = route.handler(req, res)
  if (body !== undefined) req.emit('data', Buffer.from(JSON.stringify(body)))
  req.emit('end')
  await promise
  return { code: out.code, ...(out.body ? JSON.parse(out.body) : {}) }
}

// 用户机器上可能真的配了 token（~/.dsh/git-token、/sdcard/Download/...）。
// 有几条用例专门验证「没 token 时」的行为，测试期间先把这些文件挪开，结束后恢复。
const AMBIENT_TOKEN_FILES = [
  join(homedir(), '.dsh', 'git-token'),
  join(homedir(), '.dsh', 'github-token'),
  join(homedir(), '.config', 'dsha', 'git-token'),
  '/sdcard/Download/git-token.txt',
  '/sdcard/Download/git-token',
]
const stashedAmbient = []
for (const path of AMBIENT_TOKEN_FILES) {
  try {
    await rename(path, `${path}.smoke-bak`)
    stashedAmbient.push(path)
  } catch {
    // 不存在就不管
  }
}
process.on('exit', () => {
  for (const path of stashedAmbient) {
    try {
      renameSync(`${path}.smoke-bak`, path)
    } catch {
      // 尽力恢复
    }
  }
})

const repo = await mkdtemp(join(tmpdir(), 'diksu-smoke-'))
let bare = ''
const config = {
  repoPath: repo,
  remote: 'origin',
  token: '',
  tokenEnv: 'DSH_GIT_TOKEN',
  tokenFile: join(repo, 'no-token'),
  apiBase: 'https://api.github.com',
  hostsPin: '',
  hostsFile: '/etc/hosts',
  authorName: 'dsh-agent',
  authorEmail: 'dsh-agent@localhost',
  maxAttempts: 2,
  retryDelayMs: 10,
  protectedBranches: 'main,master',
  maxDiffBytes: 200000,
  auditMaxFileMB: 2,
}

apply(ctx, config)

/** 调工具并做 harness 同款校验：输出 schema + JSON 无损快照。 */
async function call(name, args = {}) {
  const tool = registry.get(name)
  assert.ok(tool, `没有注册 ${name}`)
  const value = await tool.execute(args, {})
  const detached = JSON.parse(JSON.stringify(value))
  const violations = validateJsonSchemaValue(tool.output.schema, detached, 'value')
  assert.deepEqual(violations, [], `${name} 输出不符合 schema：${violations.join('; ')}`)
  return value
}

const raw = async (args) => {
  const { run } = await import('../lib/core.js')
  const result = await run('git', ['-C', repo, ...args])
  assert.equal(result.code, 0, `git ${args.join(' ')} 失败：${result.stderr}`)
  return result
}

process.stdout.write(`dsha-diksu-git 注册与输出契约自测（node ${process.version}）\n`)

await check('注册了 19 个工具', () => {
  assert.equal(registry.size, 19, [...registry.keys()].join(','))
  for (const name of [
    'git_status', 'git_diff', 'git_log', 'git_show', 'git_stage', 'git_commit', 'git_push',
    'git_branch', 'git_stash', 'git_sync', 'git_audit', 'git_blame', 'git_tag',
    'git_release_notes', 'ci_dispatch', 'ci_watch', 'ci_logs', 'release_publish', 'git_auth',
  ]) {
    assert.ok(registry.has(name), `缺少 ${name}`)
  }
})

await check('每个工具都有 parameters / output.schema / render', () => {
  for (const [name, tool] of registry) {
    assert.equal(typeof tool.description, 'string', `${name} 没有 description`)
    assert.ok(tool.parameters && Object.keys(tool.parameters).length > 0, `${name} 没有 parameters`)
    assert.ok(tool.output?.schema, `${name} 没有 output.schema`)
    assert.equal(typeof tool.output.render, 'function', `${name} 没有 render`)
  }
})

await check('初始化临时仓库', () => raw(['init', '-q', '-b', 'main']))

await check('git_status 在空仓返回合法 JSON（历史上这里报 not lossless JSON）', async () => {
  const value = await call('git_status')
  assert.equal(value.repoPath, repo)
  assert.equal(typeof value.summary, 'string')
  assert.ok(Array.isArray(value.dirty))
})

await check('git_stage / git_commit 走完整校验链', async () => {
  await writeFile(join(repo, 'a.txt'), 'hello\n')
  const staged = await call('git_stage', { paths: ['a.txt'] })
  assert.deepEqual(staged.staged, ['A a.txt'])
  const commit = await call('git_commit', { message: 'feat: 初始提交', paths: ['.'] })
  assert.match(commit.hash, /^[0-9a-f]{9}$/)
  assert.ok(Array.isArray(commit.files) && commit.files.length > 0, JSON.stringify(commit.files))
})

await check('git_log / git_diff / git_show / git_blame 输出合法', async () => {
  const log = await call('git_log', { count: 5 })
  assert.equal(log.commits.length, 1)
  await writeFile(join(repo, 'a.txt'), 'hello world\n')
  const diff = await call('git_diff')
  assert.ok(diff.patch.includes('hello world'))
  const show = await call('git_show', {})
  assert.equal(typeof show.author, 'string')
  const blame = await call('git_blame', { path: 'a.txt' })
  assert.ok(blame.lines.length > 0)
})

await check('ref 参数注入被拒（--output=/tmp/x）', async () => {
  await assert.rejects(() => call('git_diff', { ref: '--output=/tmp/diksu-pwn.txt' }), /不合规/)
  await assert.rejects(() => call('git_show', { ref: '--output=/tmp/diksu-pwn.txt' }), /不合规/)
  await assert.rejects(() => call('git_log', { range: '--output=/tmp/diksu-pwn.txt' }), /不合规/)
  await assert.rejects(() => call('git_release_notes', { from: '--output=/tmp/diksu-pwn.txt' }), /不合规/)
})

await check('git_audit 拦住暂存区里的 secret，并真的移出索引', async () => {
  await writeFile(join(repo, 'leak.txt'), 'token = "ghp_abcdefghijklmnopqrstuvwxyz0123456789"\n')
  await call('git_stage', { paths: ['leak.txt'] })
  await assert.rejects(() => call('git_commit', { message: 'chore: 手滑提交密钥' }), /疑似敏感信息/)
  const still = await raw(['diff', '--cached', '--name-only'])
  // 只把命中的文件退回工作区，其余照旧留在索引里（用户改完再提交）
  assert.equal(still.stdout.includes('leak.txt'), false, `leak.txt 还在索引里：${still.stdout}`)
})

await check('git_audit worktree 能看见未跟踪文件', async () => {
  const audit = await call('git_audit', { scope: 'worktree' })
  assert.equal(audit.ok, false)
  assert.match(audit.findings.join('\n'), /leak\.txt/)
})

await check('git_tag / git_release_notes 输出合法', async () => {
  await rm(join(repo, 'leak.txt'), { force: true })
  const tag = await call('git_tag', { tag: 'v0.0.1-ultra', message: '测试' })
  assert.equal(tag.tag, 'v0.0.1-ultra')
  assert.equal(typeof tag.versionCode, 'number')
  const notes = await call('git_release_notes', { to: 'HEAD', version: 'v0.0.2-ultra' })
  assert.match(notes.markdown, /v0\.0\.2-ultra/)
})

await check('git_branch / git_stash 输出合法', async () => {
  const created = await call('git_branch', { action: 'create', name: 'feat/x' })
  assert.ok(created.branches.includes('feat/x'))
  await call('git_branch', { action: 'switch', name: 'feat/x' })
  await call('git_branch', { action: 'switch', name: 'main' })
  await writeFile(join(repo, 'b.txt'), 'dirty\n')
  const saved = await call('git_stash', { action: 'save', message: 'wip' })
  assert.equal(saved.stashes.length, 1)
  await call('git_stash', { action: 'pop', index: 0 })
})

await check('拒绝非法参数：stash 负序号 / blame 行号 / ci_watch id', async () => {
  const rejects = [
    ['git_stash', { action: 'drop', index: -1 }, /index 必须是不小于 0 的整数/],
    ['git_blame', { path: 'a.txt', startLine: 0 }, /startLine 必须是不小于 1 的整数/],
    ['git_blame', { path: 'a.txt', startLine: 5, endLine: 2 }, /endLine（2）不能小于 startLine（5）/],
    ['ci_watch', { runId: -3 }, /runId 必须是正整数/],
    ['ci_watch', {}, /runId 与 sha 至少要给一个/],
  ]
  for (const [name, args, pattern] of rejects) {
    const tool = registry.get(name)
    let text = ''
    try {
      await tool.execute(args, {})
      throw new Error(`${name}(${JSON.stringify(args)}) 居然执行成功了`)
    } catch (error) {
      text = String(error?.message ?? error)
    }
    assert.match(text, pattern, `${name}(${JSON.stringify(args)}) 报错不符：${text}`)
  }
})

await check('git_stash 展示可用的数字引用', async () => {
  // 改动已跟踪文件，git stash push 才会真的入栈（默认不含未跟踪文件）
  await writeFile(join(repo, 'a.txt'), 'stash one\n')
  await call('git_stash', { action: 'save', message: 'one' })
  await writeFile(join(repo, 'a.txt'), 'stash two\n')
  await call('git_stash', { action: 'save', message: 'two' })
  const listed = await call('git_stash', { action: 'list' })
  assert.equal(listed.stashes.length, 2, JSON.stringify(listed.stashes))
  assert.ok(listed.stashes.every((line) => /^stash@\{\d+\}  /.test(line)), listed.stashes.join(' | '))
  assert.ok(listed.stashes[0].startsWith('stash@{0}'), listed.stashes[0])
  assert.ok(listed.stashes[1].startsWith('stash@{1}'), listed.stashes[1])
  await call('git_stash', { action: 'drop', index: 1 })
  await call('git_stash', { action: 'pop', index: 0 })
})

await check('git_auth 只报 token 来源，不泄露值', async () => {
  const auth = await call('git_auth', {})
  assert.equal(auth.configured, false)
  assert.ok(auth.sources.length > 0)
  assert.match(auth.summary, /\.dsh|凭据|token/)
})

await check('git_auth 能从文件导入 token（不用重启、不进对话）', async () => {
  const srcFile = join(repo, 'incoming-token.txt')
  await writeFile(srcFile, 'ghp_importtest0123456789abcdefghijklmnopqrst\n')
  const imported = await call('git_auth', { action: 'import', path: srcFile })
  assert.equal(imported.configured, true)
  assert.match(imported.summary, /已从 .*incoming-token\.txt 导入 token/)
  // 导入后立刻应该能被 resolveToken 读到（不需要重启）
  const { resolveToken } = await import('../lib/core.js')
  const hit = await resolveToken(config, undefined)
  assert.equal(hit.value, 'ghp_importtest0123456789abcdefghijklmnopqrst')
  // 文件权限应是 600
  const { stat } = await import('node:fs/promises')
  const mode = (await stat(imported.tokenFile)).mode & 0o777
  assert.equal(mode, 0o600, `权限应为 600，实际 ${mode.toString(8)}`)
  // 清理
  const cleared = await call('git_auth', { action: 'clear' })
  assert.match(cleared.summary, /已删除/)
  await assert.rejects(() => resolveToken(config, undefined), /没有可用的 git token/)
})

await check('git_auth import 拒绝坏内容', async () => {
  const empty = join(repo, 'empty-token.txt')
  await writeFile(empty, '   \n')
  await assert.rejects(() => call('git_auth', { action: 'import', path: empty }), /是空的/)
  const short = join(repo, 'short-token.txt')
  await writeFile(short, 'abc123\n')
  await assert.rejects(() => call('git_auth', { action: 'import', path: short }), /太短/)
  const messy = join(repo, 'messy-token.txt')
  await writeFile(messy, 'ghp_abcdefghij0123456789 extra words here\n')
  await assert.rejects(() => call('git_auth', { action: 'import', path: messy }), /空白字符/)
  await assert.rejects(() => call('git_auth', { action: 'import', path: join(repo, 'nope.txt') }), /读不到/)
})

await check('git_push 用本地 bare 远端 + 配置里的 token 走通', async () => {
  bare = join(repo, '..', `diksu-remote-${Date.now()}.git`)
  const init = await import('../lib/core.js').then(({ run }) => run('git', ['init', '-q', '--bare', bare]))
  assert.equal(init.code, 0, init.stderr)
  await raw(['remote', 'add', 'origin', bare])
  // 这里用一个假 token：本地 file 远端不会真的走认证，但能验证 askpass 路径不炸
  const push = await registry.get('git_push').execute({ ref: 'main' }, {})
  assert.equal(push.ok, true, push.summary)
  assert.equal(push.verified, true)
})

await check('git_sync fetch 本地远端', async () => {
  const sync = await call('git_sync', { action: 'fetch' })
  assert.equal(sync.ok, true, sync.summary)
})

await check('设置页 API：GET 状态 / POST 保存 / POST 清除', async () => {
  const before = await callRoute('GET')
  assert.equal(before.configured, false)
  assert.match(before.file, /no-token$/)

  const saved = await callRoute('POST', { token: 'ghp_settingspage0123456789abcdefghijklmnop' })
  assert.equal(saved.saved, true)

  const after = await callRoute('GET')
  assert.equal(after.configured, true)
  assert.match(after.source, /no-token/)

  const bad = await callRoute('POST', { token: 'short' })
  assert.equal(bad.ok, false)
  assert.match(bad.error, /太短/)

  const cleared = await callRoute('POST', { clear: true })
  assert.equal(cleared.cleared, true)
  const final = await callRoute('GET')
  assert.equal(final.configured, false)
})

await check('需要 token 的工具在缺凭据时给可执行的错（不是 is not defined）', async () => {
  const cases = [
    ['ci_logs', {}],
    ['ci_dispatch', { workflow: 'build-manager.yml' }],
    ['ci_watch', {}],
    ['release_publish', { tag: 'v0.0.1-ultra', runId: 1 }],
  ]
  for (const [name, args] of cases) {
    const tool = registry.get(name)
    let text = ''
    try {
      await tool.execute(args, {})
      throw new Error(`${name} 在没 token 的情况下居然成功了`)
    } catch (error) {
      text = String(error?.message ?? error)
    }
    assert.equal(/is not defined|Cannot read|undefined is not/.test(text), false, `${name} 抛了实现错误：${text}`)
    assert.match(text, /token|workflow|runId|tag/, `${name} 的报错不可执行：${text}`)
  }
})

await check('清理', async () => {
  await rm(repo, { recursive: true, force: true })
  if (bare.length > 0) await rm(bare, { recursive: true, force: true })
})

const total = results.filter((line) => /^ {2}[✓✗]/.test(line)).length
process.stdout.write(results.join('\n') + '\n\n')
process.stdout.write(`共 ${total} 项，失败 ${failed} 项\n`)
if (logs.length > 0) process.stdout.write(`插件日志：${logs.join(' | ')}\n`)
process.exit(failed === 0 ? 0 : 1)
