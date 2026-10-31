/**
 * dsha-diksu-git · 只读工具
 *
 * git_status / git_diff / git_log / git_show / git_audit / git_blame。
 * 这一组不修改仓库状态，唯一的外部副作用是 git_status(fetch=true) 会更新远端引用。
 */
import {
  auditDiff,
  auditSource,
  currentBranch,
  aheadBehind,
  diffNameStatus,
  git,
  gitLine,
  inProgressOperations,
  networkGitAuto,
  resolveToken,
  safeRemoteName,
  safePath,
  safePaths,
  safeRevision,
  statusSummary,
  truncateText,
  versionInfo,
  repoRoot,
} from './core.js'

export function registerReadTools({ register, config, credentials, ctx }) {
  // 1. 仓库状态
  register({
    name: 'git_status',
    description:
      '读取仓库状态：当前分支、HEAD、上游与领先/落后、最近 tag 与 versionCode、已暂存/未暂存/未跟踪/冲突文件、进行中的 merge/rebase、根目录 APK 产物、token 是否配置。提交或推送前先看一眼。',
    parameters: {
      fetch: { type: 'boolean', description: '是否先 fetch 远端（默认 false，网络不稳时省一次往返）' },
    },
    output: {
      schema: {
        type: 'object',
        additionalProperties: false,
        properties: {
          repoPath: { type: 'string', required: true },
          branch: { type: 'string', required: true },
          head: { type: 'string', required: true },
          headSubject: { type: 'string', required: true },
          upstream: { type: 'string', required: true },
          tag: { type: 'string', required: true },
          headTag: { type: 'string', required: true },
          versionCode: { type: 'number' },
          commitsAfterTag: { type: 'number' },
          ahead: { type: 'number', required: true },
          behind: { type: 'number', required: true },
          staged: { type: 'array', required: true, items: { type: 'string' } },
          unstaged: { type: 'array', required: true, items: { type: 'string' } },
          untracked: { type: 'array', required: true, items: { type: 'string' } },
          conflicts: { type: 'array', required: true, items: { type: 'string' } },
          dirty: { type: 'array', required: true, items: { type: 'string' } },
          apkLeftovers: { type: 'array', required: true, items: { type: 'string' } },
          inProgress: { type: 'array', required: true, items: { type: 'string' } },
          auth: { type: 'string', required: true },
          summary: { type: 'string', required: true },
        },
      },
      render: (_args, value) => [{ type: 'text', text: value.summary }],
    },
    async execute(args, exec) {
      exec.signal?.throwIfAborted?.()
      const root = await repoRoot(config)
      const warnings = []
      if (args.fetch === true) {
        const outcome = await networkGitAuto(
          config,
          credentials,
          'git fetch',
          () => ['fetch', safeRemoteName(config.remote), '--prune'],
          { timeoutMs: 240_000, signal: exec.signal },
        )
        warnings.push(...(outcome.warnings ?? []))
        if (!outcome.ok) {
          warnings.push(`fetch 失败：${outcome.detail}`)
          if (outcome.missingToken) warnings.push(outcome.missingToken)
        }
      }
      const branch = await currentBranch(config)
      const head = await gitLine(config, ['rev-parse', '--short=9', 'HEAD'])
      const headSubject = await gitLine(config, ['log', '-1', '--pretty=%s'])
      const version = await versionInfo(config)
      const parsed = await statusSummary(config)
      const apkLeftovers = parsed.entries
        .filter((entry) => !(entry.x === 'D' || entry.y === 'D'))
        .map((entry) => entry.path)
        .filter((path) => /\.apk$/i.test(path))
      const { upstream, ahead, behind } = await aheadBehind(config)
      const inProgress = await inProgressOperations(config)
      let auth = '未配置 token（推送/CI/发版会失败，用 git_auth 看怎么配）'
      try {
        const token = await resolveToken(config, credentials)
        auth = `token 已配置：${token.source}`
      } catch {
        // 保持上面的提示
      }
      const summary = [
        `仓库 ${root}`,
        head.length > 0 ? `分支 ${branch} @ ${head}${headSubject ? ` · ${headSubject}` : ''}` : `分支 ${branch} · 还没有任何提交`,
        version.tag.length > 0
          ? `tag ${version.tag}${version.headTag ? '（HEAD 已打 tag）' : ''} · versionCode ${version.versionCode ?? '—'}（tag 后 ${version.commitsAfterTag ?? 0} 个提交）`
          : '尚无版本 tag',
        upstream ? `上游 ${upstream} · 领先 ${ahead} / 落后 ${behind}` : '当前分支没有上游',
        parsed.staged.length + parsed.unstaged.length + parsed.untracked.length + parsed.conflicts.length === 0
          ? '工作区干净'
          : `已暂存 ${parsed.staged.length} · 未暂存 ${parsed.unstaged.length} · 未跟踪 ${parsed.untracked.length} · 冲突 ${parsed.conflicts.length}`,
        inProgress.length > 0 ? `⚠️ 进行中的操作：${inProgress.join(', ')}` : '',
        apkLeftovers.length === 0 ? '' : `⚠️ 工作区有 APK 产物（不要提交）：${apkLeftovers.join(', ')}`,
        warnings.length > 0 ? `⚠️ ${warnings.join('\n⚠️ ')}` : '',
      ]
        .filter((line) => line.length > 0)
        .join('\n')
      return {
        repoPath: root,
        branch,
        head,
        headSubject,
        upstream,
        tag: version.tag,
        headTag: version.headTag,
        ...(version.versionCode === null ? {} : { versionCode: version.versionCode }),
        ...(version.commitsAfterTag === null ? {} : { commitsAfterTag: version.commitsAfterTag }),
        ahead,
        behind,
        staged: parsed.staged,
        unstaged: parsed.unstaged,
        untracked: parsed.untracked,
        conflicts: parsed.conflicts,
        dirty: parsed.dirty,
        apkLeftovers,
        inProgress,
        auth,
        summary,
      }
    },
  })

  // 2. 查看 diff
  register({
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
      const ref = typeof args.ref === 'string' && args.ref.trim().length > 0 ? safeRevision(args.ref, 'ref') : ''
      if (ref.length > 0) base.push(ref)
      if (typeof args.contextLines === 'number' && Number.isFinite(args.contextLines) && args.contextLines >= 0) {
        base.push(`-U${Math.trunc(args.contextLines)}`)
      }
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
      const label = args.staged === true ? '已暂存' : ref ? `范围 ${ref}` : '未暂存'
      const summary = [
        `${label}：${files.length} 个文件改动${truncated ? '（补丁已截断）' : ''}`,
        stat || '（没有改动）',
      ].join('\n')
      return { files, stat, patch, truncated, summary }
    },
  })

  // 3. 提交历史
  register({
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
      if (typeof args.range === 'string' && args.range.trim().length > 0) logArgs.push(safeRevision(args.range, 'range'))
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
  })

  // 4. 查看单个提交
  register({
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
      const ref = typeof args.ref === 'string' && args.ref.trim().length > 0 ? safeRevision(args.ref, 'ref') : 'HEAD'
      const paths = safePaths(args.paths)
      const meta = await git(config, [
        'show',
        '-s',
        '--no-color',
        '--date=iso-strict',
        '--pretty=format:%H%x1f%h%x1f%an%x1f%ad%x1f%s',
        ref,
      ])
      if (meta.code !== 0) throw new Error(`找不到提交 ${ref}：${(meta.stderr || meta.stdout).trim()}`)
      const fields = meta.stdout.trim().split('\x1f')
      if (fields[0].length === 0) throw new Error(`读不到提交信息：${ref}（可能不是 commit，或仓库为空）`)
      const [hash, short, author, date, subject] = [0, 1, 2, 3, 4].map((index) => fields[index] ?? '')
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
  })

  // 5. 提交审计
  register({
    name: 'git_audit',
    description:
      '提交前审计：扫描暂存区 / 工作区（含未跟踪文件）/ 指定范围的新增行，识别 GitHub PAT、AWS key、私钥、JWT、通用 secret、冲突标记与大文件。只报位置不回显内容。',
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
          ...(scope === 'range' ? [safeRevision(args.range, 'range')] : []),
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
  })

  // 6. blame
  register({
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
      // 行号必须显式校验：负数/0 会被 git 当成「没有范围」，静默返回整个文件
      const start = args.startLine === undefined ? undefined : Math.trunc(Number(args.startLine))
      const end = args.endLine === undefined ? undefined : Math.trunc(Number(args.endLine))
      if (start !== undefined && (!Number.isFinite(start) || start < 1)) {
        throw new Error(`startLine 必须是不小于 1 的整数（收到 ${args.startLine}）`)
      }
      if (end !== undefined && (!Number.isFinite(end) || end < 1)) {
        throw new Error(`endLine 必须是不小于 1 的整数（收到 ${args.endLine}）`)
      }
      if (start !== undefined && end !== undefined && end < start) {
        throw new Error(`endLine（${end}）不能小于 startLine（${start}）`)
      }
      if (start !== undefined) blameArgs.push('-L', `${start},${end ?? start}`)
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
  })
}
