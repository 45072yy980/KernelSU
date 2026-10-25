/**
 * dsha-diksu-git 自测：用真实临时仓库跑一遍工具，不依赖网络与 DSH 运行时。
 * 运行：node test/run-tests.mjs
 */
import { mkdtemp, writeFile, readFile, mkdir, rm } from 'node:fs/promises'
import { tmpdir } from 'node:os'
import { join } from 'node:path'
import { execFileSync } from 'node:child_process'
import * as plugin from '../lib/index.js'

const root = await mkdtemp(join(tmpdir(), 'diksu-git-test-'))
const repo = join(root, 'repo')
const remote = join(root, 'remote.git')
const clone = join(root, 'clone')
const env = { ...process.env, DSH_GIT_TOKEN: 'test-token-not-real' }
process.env.DSH_GIT_TOKEN = 'test-token-not-real'

function sh(command, args, cwd = repo) {
  return execFileSync(command, args, { cwd, encoding: 'utf8', env, stdio: ['ignore', 'pipe', 'pipe'] }).trim()
}
function ok(condition, label) {
  if (!condition) throw new Error(`断言失败：${label}`)
  console.log(`  ✓ ${label}`)
}
function expectThrow(fn, label) {
  return fn().then(
    () => {
      throw new Error(`断言失败（应当抛错）：${label}`)
    },
    (error) => {
      console.log(`  ✓ ${label} → ${String(error.message).split('\n')[0]}`)
      return error
    },
  )
}

await mkdir(repo, { recursive: true })
await mkdir(remote, { recursive: true })
sh('git', ['init', '-b', 'main'])
sh('git', ['config', 'user.name', 'tester'])
sh('git', ['config', 'user.email', 'tester@localhost'])
await writeFile(join(repo, 'a.txt'), 'line1\nline2\nline3\n')
sh('git', ['add', '.'])
sh('git', ['commit', '-m', 'feat(core): 初始提交'])
sh('git', ['init', '--bare', remote])
sh('git', ['remote', 'add', 'origin', remote])

const tools = new Map()
plugin.apply(
  { tools: { register: (tool) => tools.set(tool.name, tool) }, logger: { info: () => {} } },
  {
    repoPath: repo,
    remote: 'origin',
    tokenEnv: 'DSH_GIT_TOKEN',
    apiBase: 'https://api.github.com',
    hostsPin: '',
    hostsFile: '/etc/hosts',
    authorName: 'dsh-agent',
    authorEmail: 'dsh-agent@localhost',
    maxAttempts: 1,
    retryDelayMs: 10,
    protectedBranches: 'main,master,develop,trunk,release/*,hotfix/*',
    maxDiffBytes: 200_000,
    auditMaxFileMB: 2,
  },
)
const call = (name, args = {}) => tools.get(name).execute(args, {})

console.log('1. git_status / git_push 设置上游')
let status = await call('git_status')
ok(status.branch === 'main', '当前分支 main')
ok(status.upstream === '', '初始没有上游')
let push = await call('git_push')
ok(push.ok === true, 'git_push 成功并设置上游')
status = await call('git_status')
ok(status.upstream === 'origin/main', '上游已设置为 origin/main')

console.log('2. git_diff / git_stage')
await writeFile(join(repo, 'a.txt'), 'line1\nline2 changed\nline3\n')
let diff = await call('git_diff')
ok(diff.files.length === 1 && diff.patch.includes('line2 changed'), '未暂存 diff 能看到改动')
await call('git_stage', { paths: ['a.txt'] })
diff = await call('git_diff', { staged: true })
ok(diff.patch.includes('line2 changed'), 'staged diff 能看到改动')
await call('git_stage', { paths: ['a.txt'], unstage: true })
ok((await call('git_diff', { staged: true })).files.length === 0, '取消暂存生效')

console.log('3. git_audit / git_commit 拦截 secret')
await writeFile(join(repo, 'secret.txt'), 'token = ghp_abcdefghijklmnopqrstuvwxyz0123456789\n')
await call('git_stage', { paths: ['secret.txt'] })
const audit = await call('git_audit', { scope: 'staged' })
ok(audit.ok === false && audit.findings.some((item) => item.includes('github-pat')), '审计识别 GitHub PAT')
await expectThrow(() => call('git_commit', { paths: ['secret.txt'], message: 'chore: 测试' }), '提交被审计拦截')
const afterAudit = await call('git_diff', { staged: true })
ok(afterAudit.files.length === 0, '高风险文件已被移出暂存区')
await rm(join(repo, 'secret.txt'))
await call('git_stage', { paths: ['a.txt'] })
const commit = await call('git_commit', { paths: ['a.txt'], message: 'fix(core): 修改第二行' })
ok(commit.committed === true && commit.hash.length >= 7, '正常提交成功')
ok(commit.audit.length === 0, '正常提交审计无告警')

console.log('4. git_log / git_show / git_blame')
const log = await call('git_log', { count: 5, withFiles: true })
ok(log.total === 2 && log.commits[0].includes('fix(core)'), 'git_log 返回最近提交')
const show = await call('git_show', { ref: 'HEAD', stat: true })
ok(show.subject.includes('fix(core)') && show.stat.includes('a.txt'), 'git_show 返回元信息与统计')
const blame = await call('git_blame', { path: 'a.txt', startLine: 1, endLine: 2 })
ok(blame.lines.length === 2 && blame.lines[1].includes('dsh-agent'), 'git_blame 返回行级信息')

console.log('5. git_branch')
await call('git_branch', { action: 'create', name: 'feature/x' })
await call('git_branch', { action: 'switch', name: 'feature/x' })
ok((await call('git_status')).branch === 'feature/x', '切换分支成功')
await call('git_branch', { action: 'switch', name: 'main' })
const deleted = await call('git_branch', { action: 'delete', name: 'feature/x' })
ok(deleted.backupRef.startsWith('refs/backup/dsha-diksu-git/'), '删除分支前写入备份 ref')
await expectThrow(() => call('git_branch', { action: 'delete', name: 'main' }), '受保护分支拒绝删除')

console.log('6. git_stash')
await writeFile(join(repo, 'a.txt'), 'line1\nstash me\nline3\n')
await call('git_stash', { action: 'save', message: '测试贮藏' })
ok((await call('git_status')).dirty.length === 0, '贮藏后工作区干净')
const stashList = await call('git_stash', { action: 'list' })
ok(stashList.stashes.length === 1, '贮藏列表有 1 条')
await call('git_stash', { action: 'pop' })
ok((await readFile(join(repo, 'a.txt'), 'utf8')).includes('stash me'), 'pop 恢复改动')
sh('git', ['checkout', '--', 'a.txt'])

console.log('7. git_sync fetch / pull')
sh('git', ['push', 'origin', 'main'])
sh('git', ['clone', '-b', 'main', remote, clone])
sh('git', ['config', 'user.name', 'tester'], clone)
sh('git', ['config', 'user.email', 'tester@localhost'], clone)
await writeFile(join(clone, 'b.txt'), 'from clone\n')
sh('git', ['add', '.'], clone)
sh('git', ['commit', '-m', 'docs: 克隆端提交'], clone)
sh('git', ['push', 'origin', 'main'], clone)
const fetched = await call('git_sync', { action: 'fetch', prune: true })
ok(fetched.behind === 1, 'fetch 后落后 1 个提交')
const pulled = await call('git_sync', { action: 'pull' })
ok(pulled.ok === true && pulled.behind === 0, 'pull --ff-only 成功')
ok((await readFile(join(repo, 'b.txt'), 'utf8')).includes('from clone'), 'pull 拿到远端文件')

console.log('8. git_tag / git_release_notes / git_push tag')
const tagged = await call('git_tag', { tag: 'v1.0.0-ultra', message: 'v1.0.0 测试' })
ok(tagged.versionCode === 10000, 'versionCode = 10000')
const tagPush = await call('git_push', { tag: 'v1.0.0-ultra' })
ok(tagPush.ok === true, 'tag 推送成功')
await writeFile(join(repo, 'c.txt'), 'release note\n')
sh('git', ['add', '.'])
sh('git', ['commit', '-m', 'feat(ui): 新增发布说明'])
const notes = await call('git_release_notes', { from: 'v1.0.0-ultra', to: 'HEAD', version: 'v1.1.0' })
ok(notes.markdown.includes('### 新功能') && notes.markdown.includes('新增发布说明'), '发布说明按类型分组')
const written = await call('git_release_notes', { from: 'v1.0.0-ultra', to: 'HEAD', version: 'v1.1.0', writeTo: 'RELEASE_NOTES.md' })
ok(written.path === 'RELEASE_NOTES.md' && (await readFile(join(repo, 'RELEASE_NOTES.md'), 'utf8')).includes('新增发布说明'), '发布说明写入文件')

console.log('9. 受保护分支强推拦截')
await expectThrow(() => call('git_push', { force: true }), '受保护分支 main 拒绝强推')

console.log('\n全部测试通过')
await rm(root, { recursive: true, force: true })
