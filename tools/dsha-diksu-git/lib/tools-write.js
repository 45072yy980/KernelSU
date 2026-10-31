/**
 * dsha-diksu-git · 写入工具
 *
 * git_stage / git_commit / git_tag / git_branch / git_stash。
 * 这一组会改动索引、引用和提交历史，所以每一步都带前置校验：
 * 保护分支、APK 产物、敏感信息审计、破坏性操作都要显式开关。
 */
import {
  auditStagedFiles,
  git,
  gitLine,
  isProtectedBranch,
  safeBranchName,
  safePaths,
  safeRevision,
  safeTagName,
  statusSummary,
  versionCodeFor,
  versionInfo,
} from './core.js'

/** 从 porcelain 结果里取「已暂存」的纯路径。 */
function stagedPaths(status) {
  return status.entries.filter((entry) => entry.x !== ' ' && entry.x !== '?').map((entry) => entry.path)
}

/** 把暂存区里指定的路径退回工作区（不丢改动）。 */
async function unstage(config, paths) {
  const args = paths.length > 0 ? ['reset', '--', ...paths] : ['reset']
  const result = await git(config, args)
  return result.code === 0
}

export function registerWriteTools({ register, config, credentials, ctx }) {
  // 5. 暂存 / 取消暂存
  register({
    name: 'git_stage',
    description: '暂存或取消暂存。all=true 表示全部改动（git add -A / git reset），否则按 paths 精确操作；unstage=true 取消暂存。不会提交。',
    parameters: {
      all: { type: 'boolean', description: '全部改动（默认 false）' },
      paths: { type: 'array', description: '仓库内相对路径；all=true 时忽略', items: { type: 'string' } },
      unstage: { type: 'boolean', description: '取消暂存（默认 false）' },
    },
    output: {
      schema: {
        type: 'object',
        additionalProperties: false,
        properties: {
          staged: { type: 'array', required: true, items: { type: 'string' } },
          unstaged: { type: 'array', required: true, items: { type: 'string' } },
          summary: { type: 'string', required: true },
        },
      },
      render: (_args, value) => [{ type: 'text', text: value.summary }],
    },
    async execute(args, exec) {
      exec.signal?.throwIfAborted?.()
      const paths = safePaths(args.paths)
      if (args.all !== true && paths.length === 0) {
        throw new Error('要么传 all=true，要么用 paths 指定要操作的文件')
      }
      const unstaging = args.unstage === true
      const gitArgs = unstaging ? ['reset'] : ['add', '-A']
      if (paths.length > 0) gitArgs.push('--', ...paths)
      const result = await git(config, gitArgs)
      if (result.code !== 0) throw new Error(`git ${gitArgs.join(' ')} 失败：${(result.stderr || result.stdout).trim()}`)
      const status = await statusSummary(config)
      return {
        staged: status.staged,
        unstaged: status.unstaged,
        summary:
          status.staged.length === 0
            ? '当前暂存区是空的'
            : `暂存区 ${status.staged.length} 项：\n${status.staged.join('\n')}\n未暂存 ${status.unstaged.length} 项`,
      }
    },
  })

  // 6. 提交
  register({
    name: 'git_commit',
    description:
      '按本项目的约定提交：固定作者 dsh-agent、提交前自动审计（secret / 冲突标记 / 大文件）、拒绝把 *.apk 提交进仓库（除非显式放行）、返回哈希与改动统计。传 paths=["."] 表示全部改动。',
    parameters: {
      message: { type: 'string', required: true, description: '提交信息（中文，type(scope): 摘要）' },
      paths: { type: 'array', description: '只提交这些路径；["."] 表示全部改动', items: { type: 'string' } },
      allowEmpty: { type: 'boolean', description: '没有改动也建一个空提交（默认 false）' },
      amend: { type: 'boolean', description: '修补上一次提交（--amend）' },
      signoff: { type: 'boolean', description: '追加 Signed-off-by（--signoff）' },
      skipAudit: { type: 'boolean', description: '跳过 secret/冲突标记审计（默认 false，不建议）' },
      allowApk: { type: 'boolean', description: '显式放行 APK（默认 false；正常情况永远不要开）' },
    },
    output: {
      schema: {
        type: 'object',
        additionalProperties: false,
        properties: {
          hash: { type: 'string', required: true },
          subject: { type: 'string', required: true },
          files: { type: 'array', required: true, items: { type: 'string' } },
          auditFindings: { type: 'array', required: true, items: { type: 'string' } },
          summary: { type: 'string', required: true },
        },
      },
      render: (_args, value) => [{ type: 'text', text: value.summary }],
    },
    async execute(args, exec) {
      exec.signal?.throwIfAborted?.()
      const message = String(args.message ?? '').trim()
      if (message.length === 0) throw new Error('提交信息不能为空')
      const paths = safePaths(args.paths)
      const stageArgs = paths.length > 0 ? ['add', '-A', '--', ...paths] : ['add', '-A']
      const staged = await git(config, stageArgs)
      if (staged.code !== 0) throw new Error(`暂存失败：${(staged.stderr || staged.stdout).trim()}`)

      const status = await statusSummary(config)
      const ready = stagedPaths(status)
      if (ready.length === 0 && args.allowEmpty !== true && args.amend !== true) {
        throw new Error('没有可以提交的改动（确实要空提交就传 allowEmpty=true）')
      }

      // APK 永远不提交进仓库
      if (args.allowApk !== true) {
        const apks = ready.filter((path) => /\.apk$/i.test(path))
        if (apks.length > 0) {
          await unstage(config, apks)
          throw new Error(
            `拒绝提交 APK 产物：${apks.join(', ')}\n已把它们退回未暂存；真要提交请显式传 allowApk=true`,
          )
        }
      }

      let findings = []
      if (args.skipAudit !== true) {
        // 逐文件审计：文件清单来自 git 自己，路径含空格/非 ASCII 也能精确退回
        const report = await auditStagedFiles(config, ready)
        findings = report.findings
        const high = findings.filter((item) => item.startsWith('HIGH'))
        if (high.length > 0) {
          await unstage(config, report.highPaths)
          let stillStaged = []
          const left = await gitLine(config, ['diff', '--cached', '--name-only', '-z'])
          stillStaged = left.split('\0').filter(Boolean).filter((path) => report.highPaths.includes(path))
          if (stillStaged.length > 0) {
            // 重命名等场景下按新路径 reset 不足以撤销，整块退回，绝不留下「假移出」
            await unstage(config, [])
            stillStaged = []
          }
          throw new Error(
            `审计发现疑似敏感信息，已把这些文件移出暂存区：\n- ${high.join('\n- ')}\n` +
              (stillStaged.length > 0 ? `⚠️ 仍有文件留在索引里：${stillStaged.join(', ')}\n` : '') +
              '确认是误报后可以传 skipAudit=true 再提交',
          )
        }
      }

      const commitArgs = ['commit', '-F', '-']
      if (args.amend === true) commitArgs.push('--amend')
      if (args.allowEmpty === true && ready.length === 0) commitArgs.push('--allow-empty')
      if (args.signoff === true) commitArgs.push('--signoff')
      const committed = await git(config, commitArgs, { withAuthor: true, input: message, timeoutMs: 120_000 })
      if (committed.code !== 0) {
        throw new Error(`提交失败：${(committed.stderr || committed.stdout).trim()}`)
      }
      const hash = await gitLine(config, ['rev-parse', '--short=9', 'HEAD'])
      const subject = await gitLine(config, ['log', '-1', '--pretty=%s'])
      const files = (await gitLine(config, ['show', '--name-status', '--format=', 'HEAD']))
        .split('\n')
        .filter(Boolean)
      return {
        hash,
        subject,
        files,
        auditFindings: findings,
        summary: [`已提交 ${hash} ${subject}`, files.length > 0 ? files.join('\n') : '', findings.length > 0 ? `审计提示：\n- ${findings.join('\n- ')}` : '']
          .filter((line) => line.length > 0)
          .join('\n'),
      }
    },
  })

  // 13. 打 tag
  register({
    name: 'git_tag',
    description:
      '在 HEAD 上创建（或强制移动）tag，并算出对应的 versionCode。本项目发版必须打 vX.Y.Z-ultra 形式的 tag，且 tag 要打在最终 HEAD 上。message 非空时创建附注 tag。',
    parameters: {
      tag: { type: 'string', required: true, description: '形如 v4.2.0-ultra' },
      message: { type: 'string', description: '附注 tag 的说明（可选）' },
      target: { type: 'string', description: '打在哪个提交上（默认 HEAD）' },
      force: { type: 'boolean', description: '已存在时强制移动（默认 false）' },
    },
    output: {
      schema: {
        type: 'object',
        additionalProperties: false,
        properties: {
          tag: { type: 'string', required: true },
          target: { type: 'string', required: true },
          versionCode: { type: 'number' },
          summary: { type: 'string', required: true },
        },
      },
      render: (_args, value) => [{ type: 'text', text: value.summary }],
    },
    async execute(args, exec) {
      exec.signal?.throwIfAborted?.()
      const tag = safeTagName(args.tag)
      const target = typeof args.target === 'string' && args.target.trim().length > 0 ? safeRevision(args.target, 'target') : 'HEAD'
      const existing = await gitLine(config, ['tag', '--list', tag])
      if (existing.length > 0 && args.force !== true) {
        throw new Error(`tag ${tag} 已存在；要移动它就传 force=true（会改写已发布的 tag，慎用）`)
      }
      const message = typeof args.message === 'string' ? args.message.trim() : ''
      const tagArgs = message.length > 0
        ? ['tag', '-a', tag, '-m', message, ...(args.force === true ? ['-f'] : []), target]
        : ['tag', tag, ...(args.force === true ? ['-f'] : []), target]
      const created = await git(config, tagArgs, { withAuthor: true })
      if (created.code !== 0) throw new Error(`打 tag 失败：${(created.stderr || created.stdout).trim()}`)
      const after = await versionInfo(config)
      const versionCode = after.tag === tag ? after.versionCode : versionCodeFor(tag, 0)
      return {
        tag,
        target,
        ...(versionCode === null ? {} : { versionCode }),
        summary: [
          `已创建 tag ${tag} → ${target}${versionCode === null ? '' : `（versionCode ${versionCode}）`}`,
          tag.startsWith('v') ? '推送：用 git_push 的 tag 参数，或 git push origin ' + tag : '注意：非 vX.Y.Z 形式，versionCode 不会推导出来',
        ].join('\n'),
      }
    },
  })

  // 8. 分支管理
  register({
    name: 'git_branch',
    description:
      '分支管理：list 列出本地分支（含上游与领先/落后）、create 新建、switch 切换、delete 删除（默认 -d，force 用 -D；删除前自动备份 ref）。main/master/develop/trunk/release/* 等受保护分支不可删除。',
    parameters: {
      action: { type: 'string', required: true, enum: ['list', 'create', 'switch', 'delete'], description: '要执行的动作' },
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
          branches: { type: 'array', required: true, items: { type: 'string' } },
          summary: { type: 'string', required: true },
        },
      },
      render: (_args, value) => [{ type: 'text', text: value.summary }],
    },
    async execute(args, exec) {
      exec.signal?.throwIfAborted?.()
      const action = args.action
      if (action === 'list') {
        const raw = await gitLine(config, [
          'for-each-ref',
          '--format=%(refname:short)%09%(objectname:short)%09%(upstream:short)%09%(HEAD)',
          'refs/heads',
        ])
        const branches = raw
          .split('\n')
          .filter(Boolean)
          .map((line) => {
            const [name, sha, upstream, head] = line.split('\t')
            const current = head === '*' ? '* ' : '  '
            return `${current}${name}  ${sha}${upstream ? `  ↑${upstream}` : ''}`
          })
        return {
          action,
          branches: raw.split('\n').filter(Boolean),
          summary: branches.length === 0 ? '没有本地分支' : `本地分支：\n${branches.join('\n')}`,
        }
      }

      const name = safeBranchName(args.name)
      if (action === 'create') {
        const from = typeof args.from === 'string' && args.from.trim().length > 0 ? safeRevision(args.from, 'from') : 'HEAD'
        const result = await git(config, ['branch', name, from])
        if (result.code !== 0) throw new Error(`新建分支失败：${(result.stderr || result.stdout).trim()}`)
        return { action, branches: [name], summary: `已从 ${from} 新建分支 ${name}（当前仍在原分支；需要切换用 action=switch）` }
      }
      if (action === 'switch') {
        const exists = await gitLine(config, ['rev-parse', '--verify', '--quiet', `refs/heads/${name}`])
        if (exists.length === 0) throw new Error(`本地没有分支 ${name}；先 git_branch action=create，或者用 git_sync 拉一次`)
        const current = await gitLine(config, ['rev-parse', '--abbrev-ref', 'HEAD'])
        if (current === name) return { action, branches: [name], summary: `已经在 ${name} 上了` }
        const result = await git(config, ['switch', name])
        if (result.code !== 0) throw new Error(`切换分支失败：${(result.stderr || result.stdout).trim()}`)
        return { action, branches: [name], summary: `已从 ${current} 切到 ${name}` }
      }

      // delete
      if (isProtectedBranch(config, name)) {
        throw new Error(`${name} 是受保护分支（protectedBranches），拒绝删除`)
      }
      const current = await gitLine(config, ['rev-parse', '--abbrev-ref', 'HEAD'])
      if (current === name) throw new Error(`当前就在 ${name} 上，先切到别的分支再删`)
      const exists = await gitLine(config, ['rev-parse', '--verify', '--quiet', `refs/heads/${name}`])
      if (exists.length === 0) throw new Error(`没有分支 ${name}`)
      const stamp = new Date().toISOString().replace(/[:.]/g, '-')
      const backup = `refs/diksu-backup/branches/${stamp}/${name}`
      const saved = await git(config, ['update-ref', backup, `refs/heads/${name}`])
      if (saved.code !== 0) throw new Error(`备份分支引用失败：${(saved.stderr || saved.stdout).trim()}`)
      const flags = args.force === true ? ['-D'] : ['-d']
      const result = await git(config, ['branch', ...flags, name])
      if (result.code !== 0) {
        throw new Error(
          `${(result.stderr || result.stdout).trim()}\n如果确认放弃这个分支的未合并提交，传 force=true 再删一次（备份 ref 已建：${backup}）`,
        )
      }
      return {
        action,
        branches: [name],
        summary: `已删除分支 ${name}\n备份引用：${backup}\n恢复：git branch ${name} ${backup}`,
      }
    },
  })

  // 9. 贮藏
  register({
    name: 'git_stash',
    description: '贮藏管理：list 列出、save 保存（可含未跟踪文件）、apply 应用、pop 应用并删除、drop 删除。index 默认 0（stash@{0}）。',
    parameters: {
      action: { type: 'string', required: true, enum: ['list', 'save', 'apply', 'pop', 'drop'], description: '要执行的动作' },
      message: { type: 'string', description: 'save 时的说明' },
      includeUntracked: { type: 'boolean', description: 'save 时包含未跟踪文件（-u）' },
      index: { type: 'number', description: 'stash 序号（默认 0）' },
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
      const action = args.action
      const describe = async () => {
        // 展示用数字序号：%gd 在 --date=short 下会给出 stash@{2026-10-31}，
        // 同一天多条贮藏时这个引用完全相同，根本没法照着敲。
        const raw = await gitLine(config, ['stash', 'list', '--date=short', '--pretty=format:%h%x1f%ad%x1f%s'])
        return raw
          .split('\n')
          .filter(Boolean)
          .map((line, position) => {
            const [hash, date, subject] = line.split('\x1f')
            return `stash@{${position}}  ${hash}  ${date}  ${subject}`
          })
      }
      if (action === 'list') {
        const stashes = await describe()
        return { action, stashes, summary: stashes.length === 0 ? '没有贮藏' : `贮藏：\n${stashes.join('\n')}` }
      }
      if (action === 'save') {
        const stashArgs = ['stash', 'push']
        if (args.includeUntracked === true) stashArgs.push('-u')
        if (typeof args.message === 'string' && args.message.trim().length > 0) stashArgs.push('-m', args.message.trim())
        const result = await git(config, stashArgs)
        const stashes = await describe()
        return {
          action,
          stashes,
          summary: `${(result.stdout || result.stderr).trim()}\n${stashes.length === 0 ? '' : `当前贮藏：\n${stashes.join('\n')}`}`.trim(),
        }
      }
      let index = 0
      if (args.index !== undefined) {
        if (!Number.isInteger(args.index) || args.index < 0) {
          throw new Error(`index 必须是不小于 0 的整数（收到 ${args.index}；0 是最新的一条贮藏）`)
        }
        index = args.index
      }
      const ref = `stash@{${index}}`
      const exists = await gitLine(config, ['rev-parse', '--verify', '--quiet', ref])
      if (exists.length === 0) throw new Error(`没有 ${ref}（先用 action=list 看看有哪些）`)
      const verb = action === 'apply' ? 'apply' : action === 'pop' ? 'pop' : 'drop'
      const result = await git(config, ['stash', verb, ref])
      if (result.code !== 0) throw new Error(`git stash ${verb} 失败：${(result.stderr || result.stdout).trim()}`)
      const stashes = await describe()
      return {
        action,
        stashes,
        summary: `${(result.stdout || result.stderr).trim() || `已处理 ${ref}`}\n${stashes.length === 0 ? '现在没有贮藏了' : `剩余贮藏：\n${stashes.join('\n')}`}`,
      }
    },
  })
}
