#!/usr/bin/env node
/**
 * dsha-diksu-git 自测：只依赖 node 内建 + 本机 git，不装 DSH 也能跑。
 *
 *   node test/run-tests.mjs
 *
 * 覆盖两层：
 * 1) core.js 的纯函数与解析（参数校验、porcelain -z、审计规则、版本号、lossless、token 解析）；
 * 2) 临时 git 仓库上的真实调用（status / commit / tag / 发布说明 / 审计 / 定位仓库 / 分支 / stash）。
 */
import assert from 'node:assert/strict'
import { mkdtemp, writeFile, rm } from 'node:fs/promises'
import { tmpdir } from 'node:os'
import { join } from 'node:path'
import process from 'node:process'

import {
  auditDiff,
  auditSource,
  auditStagedFiles,
  buildReleaseNotes,
  git,
  gitLine,
  inProgressOperations,
  isProtectedBranch,
  lossless,
  mask,
  parseStatusZ,
  repoRoot,
  repoSlug,
  resolveToken,
  run,
  safeBranchName,
  safePath,
  safePaths,
  safeRemoteName,
  safeRevision,
  safeTagName,
  safeWorkflowName,
  statusSummary,
  truncateText,
  unquoteGitPath,
  versionCodeFor,
  versionInfo,
  writeRepoFile,
} from '../lib/core.js'

const results = []
let failed = 0

function test(name, fn) {
  try {
    fn()
    results.push(`  ✓ ${name}`)
  } catch (error) {
    failed += 1
    results.push(`  ✗ ${name}\n      ${String(error?.message ?? error).split('\n').join('\n      ')}`)
  }
}

async function testAsync(name, fn) {
  try {
    await fn()
    results.push(`  ✓ ${name}`)
  } catch (error) {
    failed += 1
    results.push(`  ✗ ${name}\n      ${String(error?.message ?? error).split('\n').join('\n      ')}`)
  }
}

function section(title) {
  results.push(`\n${title}`)
}

const throws = (fn, pattern) => assert.throws(fn, (error) => (pattern instanceof RegExp ? pattern.test(error.message) : true))

// ────────────────────────────── 参数校验 ──────────────────────────────

section('参数校验')

test('safePath 接受仓库内相对路径并规范化', () => {
  assert.equal(safePath('a/b.txt'), 'a/b.txt')
  assert.equal(safePath('./a/b.txt'), 'a/b.txt')
  assert.equal(safePath('a\\b'), 'a/b')
  assert.equal(safePath('.'), '.')
})

test('safePath 拒绝绝对路径/上跳/NUL', () => {
  throws(() => safePath('../etc/passwd'), /\.\./)
  throws(() => safePath('/etc/passwd'), /绝对/)
  throws(() => safePath('C:\\Windows\\x'), /绝对/)
  throws(() => safePath(''))
  throws(() => safePath('a\0b'))
  assert.deepEqual(safePaths(['a', 'b/c']), ['a', 'b/c'])
  throws(() => safePaths(['a', '../b']), /\.\./)
  assert.deepEqual(safePaths('not-an-array'), [])
})

test('safeBranchName 拒绝 git 非法分支名', () => {
  assert.equal(safeBranchName('feat/nested-1'), 'feat/nested-1')
  for (const bad of ['-x', 'a b', 'a..b', 'a~b', 'a^b', 'a:b', 'a?b', 'a*b', 'a[b', 'x.lock', 'a/', 'a@{1}']) {
    throws(() => safeBranchName(bad))
  }
})

test('safeRemoteName 只放行安全字符', () => {
  assert.equal(safeRemoteName('origin'), 'origin')
  assert.equal(safeRemoteName('upstream-2'), 'upstream-2')
  for (const bad of ['-x', '../y', 'a b', 'x;rm', 'a\0b', 'a/']) throws(() => safeRemoteName(bad))
})

test('safeRevision 拒绝选项注入', () => {
  assert.equal(safeRevision('HEAD~1'), 'HEAD~1')
  assert.equal(safeRevision('main...HEAD'), 'main...HEAD')
  assert.equal(safeRevision('@{upstream}'), '@{upstream}')
  for (const bad of ['--output=/tmp/x', '', 'a b', 'a\nb', '-x'.padEnd(600, 'a')]) throws(() => safeRevision(bad))
})

test('safeTagName / safeWorkflowName', () => {
  assert.equal(safeTagName('v4.2.0-ultra'), 'v4.2.0-ultra')
  throws(() => safeTagName('-v1'))
  throws(() => safeTagName('v1 x'))
  assert.equal(safeWorkflowName('build-manager.yml'), 'build-manager.yml')
  assert.equal(safeWorkflowName('release.yaml'), 'release.yaml')
  for (const bad of ['a.yml;rm -rf /', 'a.txt', 'sub/a.yml', '']) throws(() => safeWorkflowName(bad))
})

test('isProtectedBranch 支持通配前缀', () => {
  const config = { protectedBranches: 'main, master,release/*,hotfix/*' }
  assert.equal(isProtectedBranch(config, 'main'), true)
  assert.equal(isProtectedBranch(config, 'release/4.2'), true)
  assert.equal(isProtectedBranch(config, 'release'), false)
  assert.equal(isProtectedBranch(config, 'feat/x'), false)
})

test('truncateText 按字节截断并加标记', () => {
  const cut = truncateText('a'.repeat(50), 20)
  assert.equal(cut.truncated, true)
  assert.ok(cut.text.length < 50)
  assert.match(cut.text, /已截断/)
  assert.deepEqual(truncateText('short', 100), { text: 'short', truncated: false })
})

test('mask 只替换已知 token', () => {
  const token = 'ghp_abcdefghijklmnopqrstuvwxyz0123456789'
  const masked = mask(`token=${token}`, token)
  assert.ok(!masked.includes(token))
  assert.equal(mask('nothing', ''), 'nothing')
  assert.equal(mask('nothing', undefined), 'nothing')
})

// ────────────────────────────── 输出与解析 ──────────────────────────────

section('输出与解析')

test('lossless 消掉 NaN/Infinity/undefined，避免 DSH "not lossless JSON"', () => {
  assert.equal(lossless(Number.NaN), 0)
  assert.equal(lossless(Infinity), 0)
  assert.equal(lossless(undefined), undefined)
  const out = lossless({ a: Number.NaN, b: undefined, c: [1, undefined, Infinity], d: new Date(0) })
  assert.deepEqual(out, { a: 0, c: [1, null, 0], d: new Date(0).toISOString() })
  assert.equal(lossless(Buffer.from('hi')), 'hi')
  assert.doesNotThrow(() => JSON.parse(JSON.stringify(out)))
})

test('truncateText 按行截断，不会把一行切成两半', () => {
  const text = ['a'.repeat(50), 'b'.repeat(50), 'c'.repeat(50)].join('\n')
  const cut = truncateText(text, 120)
  assert.equal(cut.truncated, true)
  assert.ok(cut.text.startsWith('a'.repeat(50)), cut.text)
  for (const line of cut.text.split('\n').slice(0, -1)) {
    assert.ok(line === 'a'.repeat(50) || line === 'b'.repeat(50) || line === 'c'.repeat(50), `被切坏的半行：${line}`)
  }
  assert.equal(truncateText('short', 100).truncated, false)
})

test('versionCodeFor 与仓库约定一致', () => {
  assert.equal(versionCodeFor('v4.2.0-ultra', 5), 42005)
  assert.equal(versionCodeFor('v1.0.0', 0), 10000)
  assert.equal(versionCodeFor('4.2.0', 0), null)
  assert.equal(versionCodeFor('', 0), null)
  assert.equal(versionCodeFor('v4.2.0', Number.NaN), 42000)
})

test('parseStatusZ 解析状态/重命名/冲突/未跟踪', () => {
  const raw = ['M  staged.txt', ' M unstaged.txt', '?? untracked.txt', 'R  renamed-new.txt', 'renamed-old.txt', 'UU conflict.txt', ''].join('\0')
  const summary = parseStatusZ(raw)
  assert.ok(summary.dirty.length > 0)
  assert.deepEqual(summary.staged, ['M staged.txt', 'R renamed-new.txt ← renamed-old.txt'])
  assert.deepEqual(summary.unstaged, ['M unstaged.txt'])
  assert.deepEqual(summary.untracked, ['untracked.txt'])
  assert.deepEqual(summary.conflicts, ['conflict.txt'])
  assert.deepEqual(parseStatusZ(''), { staged: [], unstaged: [], untracked: [], conflicts: [], dirty: [], entries: [] })
})

test('auditDiff 命中 token/私钥/冲突标记，并报告大文件', () => {
  const diff = [
    'diff --git a/config.txt b/config.txt',
    '+++ b/config.txt',
    '@@ -0,0 +1,3 @@',
    '+token = "ghp_abcdefghijklmnopqrstuvwxyz0123456789"',
    'diff --git a/key.pem b/key.pem',
    '+++ b/key.pem',
    '@@ -0,0 +1 @@',
    '+-----BEGIN RSA PRIVATE KEY-----',
    'diff --git a/x.txt b/x.txt',
    '+++ b/x.txt',
    '@@ -0,0 +1 @@',
    '+<<<<<<< HEAD',
  ].join('\n')
  const report = auditDiff(diff, { maxFileMB: 2, largeFiles: [{ path: 'big.bin', sizeMB: 5 }] })
  const text = report.findings.join('\n')
  assert.match(text, /HIGH github-pat: config\.txt:1/)
  assert.match(text, /HIGH private-key: key\.pem:1/)
  assert.match(text, /HIGH conflict-marker: x\.txt:1/)
  assert.match(text, /WARN large-file: big\.bin/)
  assert.deepEqual(report.highPaths.sort(), ['config.txt', 'key.pem', 'x.txt'])
  assert.doesNotMatch(text, /ghp_abcdefghij/)
})

test('unquoteGitPath 还原空格 TAB 与 C 风格引号路径', () => {
  assert.equal(unquoteGitPath('se cret.txt\t'), 'se cret.txt')
  assert.equal(unquoteGitPath('plain.txt'), 'plain.txt')
  // "b/\346\226\207\344\273\266.txt" 是 core.quotePath 对「文档.txt」的编码
  assert.equal(unquoteGitPath('"b/\\346\\226\\207\\344\\273\\266.txt"'), 'b/文件.txt')
})

// ────────────────────────────── token 解析 ──────────────────────────────

section('token 解析（"设了环境变量却读不到"的核心）')

await testAsync('插件配置里的 token 优先', async () => {
  const hit = await resolveToken({ token: ' config-token ', tokenEnv: 'DSH_GIT_TOKEN', tokenFile: '~/nope' }, undefined)
  assert.equal(hit.value, 'config-token')
  assert.equal(hit.source, '插件配置 token')
})

await testAsync('token 文件次之', async () => {
  const dir = await mkdtemp(join(tmpdir(), 'diksu-token-'))
  const file = join(dir, 'token')
  await writeFile(file, 'file-token\n')
  try {
    const hit = await resolveToken({ token: '', tokenFile: file }, undefined)
    assert.equal(hit.value, 'file-token')
    assert.match(hit.source, /文件/)
  } finally {
    await rm(dir, { recursive: true, force: true })
  }
})

await testAsync('凭据服务（ctx.credentials）优先于进程环境', async () => {
  const credentials = {
    async resolve(name) {
      return name === 'DSH_GIT_TOKEN' ? { value: 'cred-token' } : undefined
    },
    async describe() {
      return { set: true }
    },
  }
  const hit = await resolveToken({ token: '', tokenFile: '~/nope', tokenEnv: 'DSH_GIT_TOKEN' }, credentials)
  assert.equal(hit.value, 'cred-token')
  assert.match(hit.source, /凭据/)
})

await testAsync('四层都没有时报错里带可照做的指引', async () => {
  await assert.rejects(
    () => resolveToken({ token: '', tokenFile: '~/nope', tokenEnv: 'DSH_GIT_TOKEN_UNSET_X' }, undefined),
    /DSH_GIT_TOKEN_UNSET_X|\.dsh/,
  )
})

// ────────────────────────────── 真实 git 仓库 ──────────────────────────────

section('真实仓库集成')

const probe = await run('git', ['--version'])
if (probe.code !== 0) {
  results.push('  ! 本机没有 git，跳过集成测试')
} else {
  const repo = await mkdtemp(join(tmpdir(), 'diksu-repo-'))
  const config = {
    repoPath: repo,
    remote: 'origin',
    authorName: 'dsh-agent',
    authorEmail: 'dsh-agent@localhost',
    protectedBranches: 'main,master',
  }
  const gitOk = async (args, options) => {
    const result = await git(config, args, options)
    assert.equal(result.code, 0, `git ${args.join(' ')} 失败：${result.stderr}`)
    return result
  }

  await testAsync('初始化临时仓库', async () => {
    const init = await run('git', ['init', '-q', '-b', 'main'], { cwd: repo })
    assert.equal(init.code, 0, init.stderr)
  })

  await testAsync('repoRoot 认得配置的 repoPath', async () => {
    assert.equal(await repoRoot(config), repo)
  })

  await testAsync('repoRoot 对非仓库目录报可执行的错', async () => {
    const empty = await mkdtemp(join(tmpdir(), 'diksu-empty-'))
    try {
      await assert.rejects(() => repoRoot({ ...config, repoPath: empty }), /不是 git 仓库/)
    } finally {
      await rm(empty, { recursive: true, force: true })
    }
  })

  await testAsync('statusSummary 能看见未跟踪文件', async () => {
    await writeFile(join(repo, 'a.txt'), 'hello\n')
    const summary = await statusSummary(config)
    assert.ok(summary.dirty.length > 0)
    assert.deepEqual(summary.untracked, ['a.txt'])
  })

  await testAsync('commit 注入作者身份并回读文件列表', async () => {
    await gitOk(['add', '-A'])
    const summary = await statusSummary(config)
    assert.deepEqual(summary.staged, ['A a.txt'])
    const commit = await git(config, ['commit', '-q', '-F', '-'], { withAuthor: true, input: 'feat(scope): 初始提交\n' })
    assert.equal(commit.code, 0, commit.stderr)
    const files = await gitLine(config, ['show', '--name-status', '--format=', 'HEAD'])
    assert.match(files, /A\s+a\.txt/)
    assert.equal(await gitLine(config, ['log', '-1', '--pretty=%an <%ae>']), 'dsh-agent <dsh-agent@localhost>')
  })

  await testAsync('versionInfo 无 tag 时返回 null 而不是 NaN', async () => {
    const info = await versionInfo(config)
    assert.equal(info.tag, '')
    assert.equal(info.versionCode, null)
    assert.equal(info.commitsAfterTag, null)
    assert.doesNotThrow(() => JSON.parse(JSON.stringify(lossless(info))))
  })

  await testAsync('打 tag 后 versionInfo 算出 versionCode', async () => {
    const tag = await git(config, ['tag', '-a', 'v4.2.0-ultra', '-m', 'release'], { withAuthor: true })
    assert.equal(tag.code, 0, tag.stderr)
    const info = await versionInfo(config)
    assert.equal(info.tag, 'v4.2.0-ultra')
    assert.equal(info.headTag, 'v4.2.0-ultra')
    assert.equal(info.versionCode, 42000)
    assert.equal(info.commitsAfterTag, 0)
  })

  await testAsync('发布说明按 conventional commit 分组', async () => {
    await writeFile(join(repo, 'b.txt'), 'b\n')
    await gitOk(['add', '-A'])
    await gitOk(['commit', '-q', '-F', '-'], { withAuthor: true, input: 'fix: 修掉一个 bug\n' })
    const notes = await buildReleaseNotes(config, 'v4.2.0-ultra', 'HEAD', 'v4.2.1-ultra')
    assert.match(notes.markdown, /## v4\.2\.1-ultra/)
    assert.match(notes.markdown, /修掉一个 bug/)
    assert.equal(notes.commits.length, 1)
    assert.equal(notes.commits[0].subject, 'fix: 修掉一个 bug')
  })

  await testAsync('审计范围包含未跟踪文件内容', async () => {
    await writeFile(join(repo, 'leak.txt'), 'token = "ghp_abcdefghijklmnopqrstuvwxyz0123456789"\n')
    const source = await auditSource(config, 'worktree')
    const report = auditDiff(source.diff, { maxFileMB: 2, largeFiles: source.largeFiles })
    assert.match(report.findings.join('\n'), /HIGH github-pat: leak\.txt:1/)
    await rm(join(repo, 'leak.txt'))
  })

  await testAsync('writeRepoFile 自动建父目录', async () => {
    const path = await writeRepoFile(config, 'docs/release/notes.md', '# hi\n')
    assert.equal(path, join(repo, 'docs/release/notes.md'))
    await assert.rejects(() => writeRepoFile(config, '../../escape.md', 'x'), /\.\./)
  })

  await testAsync('repoSlug 解析带点仓库名', async () => {
    await gitOk(['remote', 'add', 'origin', 'https://github.com/owner/repo.name.git'])
    assert.equal(await repoSlug(config), 'owner/repo.name')
  })

  await testAsync('含空格文件的暂存审计能精确退回索引', async () => {
    const name = 'se cret.txt'
    await writeFile(join(repo, name), 'token = "ghp_abcdefghijklmnopqrstuvwxyz0123456789"\n')
    await gitOk(['add', '-A'])
    const report = await auditStagedFiles(config, [name])
    assert.ok(report.highPaths.includes(name), JSON.stringify(report))
    const reset = await git(config, ['reset', '--', ...report.highPaths])
    assert.equal(reset.code, 0, reset.stderr)
    const still = await gitLine(config, ['diff', '--cached', '--name-only', '-z'])
    assert.ok(!still.split('\0').includes(name), `仍在索引：${still}`)
    await rm(join(repo, name), { force: true })
  })

  await testAsync('分支：创建/切换/列表', async () => {
    await gitOk(['branch', 'feat/x'])
    const list = await gitLine(config, ['for-each-ref', '--format=%(refname:short)', 'refs/heads/'])
    assert.match(list, /feat\/x/)
    await gitOk(['switch', '-q', 'feat/x'])
    assert.equal(await gitLine(config, ['rev-parse', '--abbrev-ref', 'HEAD']), 'feat/x')
    await gitOk(['switch', '-q', 'main'])
  })

  await testAsync('stash：保存/列表/取回', async () => {
    await writeFile(join(repo, 'a.txt'), 'hello again\n')
    const save = await git(config, ['stash', 'push', '-u', '-m', 'wip'], { withAuthor: true })
    assert.equal(save.code, 0, save.stderr)
    const list = await gitLine(config, ['stash', 'list', '--pretty=format:%gd%x1f%s'])
    assert.match(list, /wip/)
    const pop = await git(config, ['stash', 'pop'])
    assert.equal(pop.code, 0, pop.stderr)
  })

  await testAsync('inProgressOperations 干净仓库为空', async () => {
    assert.deepEqual(await inProgressOperations(config), [])
  })

  await testAsync('清理临时仓库', async () => {
    await rm(repo, { recursive: true, force: true })
  })
}

// ────────────────────────────── 结果 ──────────────────────────────

const total = results.filter((line) => /^ {2}[✓✗]/.test(line)).length
process.stdout.write(`dsha-diksu-git 自测（node ${process.version}）\n`)
process.stdout.write(results.join('\n') + '\n\n')
process.stdout.write(`共 ${total} 项，失败 ${failed} 项\n`)
process.exit(failed === 0 ? 0 : 1)
