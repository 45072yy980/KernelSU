/**
 * dsh-go 原生界面：状态条 + 仓库面板 + 工具结果卡片。
 *
 * 只有装了 dsh-go 桥接的主机（手机本机引擎）才会走到这里；其它主机根本拿不到
 * `dshGo` 服务，`registerNative()` 会被 ctx.inject 跳过。
 *
 * 这里只读 git，不做任何写操作：点按要么让面板刷新，要么往输入框塞一句话，
 * 绝不自动执行提交 / 推送。
 */
import { aheadBehind, currentBranch, git, gitLine, parseStatusZ, repoRoot } from './core.js'

const STRIP_TEXT_MAX = 24
const POLL_MS = 20_000
const GIT_TIMEOUT_MS = 8_000

const zh = (caps) => !String(caps?.locale ?? '').startsWith('en')

const clip = (text, max) => {
  const value = String(text ?? '').replace(/\s+/g, ' ').trim()
  return value.length > max ? `${value.slice(0, max - 1)}…` : value
}

function lightStatus(config) {
  return git(config, ['status', '--porcelain=v1', '-z'], { timeoutMs: GIT_TIMEOUT_MS })
}

/**
 * 一次快照：分支、上游、tag、改动、最近提交。
 * 非仓库 / git 失败时返回 { ok: false }，调用方据此画空态，不抛错。
 */
async function snapshot(config) {
  let root
  try {
    root = await repoRoot(config)
  } catch {
    return { ok: false, reason: 'no-repo' }
  }
  const [branch, tracking, tag, statusResult, logText] = await Promise.all([
    currentBranch(config).catch(() => ''),
    aheadBehind(config).catch(() => ({ upstream: '', ahead: 0, behind: 0 })),
    gitLine(config, ['describe', '--tags', '--abbrev=0'], { timeoutMs: GIT_TIMEOUT_MS }).catch(() => ''),
    lightStatus(config).catch(() => ({ code: 1, stdout: '' })),
    gitLine(config, ['log', '-n', '3', '--no-color', '--pretty=format:%h%x1f%s'], { timeoutMs: GIT_TIMEOUT_MS }).catch(() => ''),
  ])
  const status = statusResult.code === 0 ? parseStatusZ(statusResult.stdout) : parseStatusZ('')
  const commits = String(logText ?? '')
    .split('\n')
    .filter((line) => line.includes('\x1f'))
    .map((line) => {
      const [hash, subject] = line.split('\x1f')
      return { hash, subject }
    })
  return {
    ok: true,
    root,
    branch: branch || 'HEAD',
    tracking: tracking.upstream,
    ahead: tracking.ahead,
    behind: tracking.behind,
    tag,
    staged: status.staged.length,
    unstaged: status.unstaged.length,
    untracked: status.untracked.length,
    conflicts: status.conflicts.length,
    dirty: status.dirty,
    commits,
  }
}

const totalChanges = (s) => s.staged + s.unstaged + s.untracked + s.conflicts

function stripText(s, caps) {
  if (!s.ok) return zh(caps) ? '未找到仓库' : 'No repo'
  const parts = [s.branch]
  const changes = totalChanges(s)
  parts.push(changes === 0 ? (zh(caps) ? '干净' : 'clean') : `${changes} ${zh(caps) ? '改动' : 'changed'}`)
  if (s.ahead > 0) parts.push(`↑${s.ahead}`)
  if (s.behind > 0) parts.push(`↓${s.behind}`)
  return clip(parts.join(' · '), STRIP_TEXT_MAX)
}

function panelData(s, caps) {
  if (!s.ok) {
    return {
      branch: '—',
      tracking: '—',
      tag: '—',
      changes: '—',
      files: zh(caps) ? '当前目录向上找不到 git 仓库。' : 'No git repository found from the working directory.',
      recent: zh(caps) ? '在插件配置里填 repoPath 就能指到别的仓库。' : 'Set repoPath in the plugin config to point elsewhere.',
    }
  }
  const changes = totalChanges(s)
  const changeText = changes === 0
    ? (zh(caps) ? '干净' : 'clean')
    : [
      `${changes} ${zh(caps) ? '个文件' : 'files'}`,
      s.staged > 0 ? `${s.staged} ${zh(caps) ? '已暂存' : 'staged'}` : '',
      s.conflicts > 0 ? `${s.conflicts} ${zh(caps) ? '冲突' : 'conflicts'}` : '',
    ].filter(Boolean).join(' · ')
  const trackingText = s.tracking
    ? [s.tracking, s.ahead > 0 ? `↑${s.ahead}` : '', s.behind > 0 ? `↓${s.behind}` : ''].filter(Boolean).join(' ')
    : (zh(caps) ? '没有上游' : 'no upstream')
  const files = changes === 0
    ? (zh(caps) ? '工作区是干净的，可以直接提交或推送。' : 'Working tree is clean.')
    : s.dirty.slice(0, 6).map((line) => clip(line, 72)).join('\n') + (s.dirty.length > 6 ? `\n+${s.dirty.length - 6}` : '')
  const recent = s.commits.length === 0
    ? (zh(caps) ? '还没有提交' : 'No commits yet')
    : s.commits.map((c) => `${c.hash} ${clip(c.subject, 40)}`).join('\n')
  return { branch: s.branch, tracking: trackingText, tag: s.tag || '—', changes: changeText, files, recent }
}

const VIEW = {
  type: 'stack',
  title: '仓库',
  children: [
    {
      type: 'keyValue',
      items: [
        { label: '分支', value: { $: 'data.branch' } },
        { label: '上游', value: { $: 'data.tracking' } },
        { label: 'Tag', value: { $: 'data.tag' } },
        { label: '改动', value: { $: 'data.changes' } },
      ],
    },
    { type: 'text', text: { $: 'data.files' } },
    { type: 'text', text: { $: 'data.recent' }, tone: 'muted' },
    {
      type: 'actions',
      items: [
        { label: '刷新', action: 'refresh', icon: 'refresh', style: 'plain' },
        { label: '审一遍', action: 'review', icon: 'diff', style: 'primary' },
        { label: '提交', action: 'commit', icon: 'save', style: 'plain' },
      ],
    },
  ],
}

const REVIEW_PROMPT = '先用 git_status 和 git_diff 把当前改动过一遍，只看不改；用三条以内说清改了什么、有什么风险。'
const COMMIT_PROMPT = '把当前改动提交上去：先跑 git_audit，再按约定式提交信息写清楚，commit 后告诉我 hash。'

/** 在 dsh-go 主机上注册原生贡献；非 dsh-go 主机直接不执行。 */
export function registerNative(ctx, config) {
  ctx.inject(['dshGo'], (ctx) => {
    ctx.dshGo.strip('repo', (scope, emit, caps) => {
      let stopped = false
      const tick = () => {
        snapshot(config)
          .then((s) => {
            if (!stopped) {
              emit({
                text: stripText(s, caps),
                icon: 'fork',
                tone: s.ok && totalChanges(s) === 0 ? 'good' : undefined,
              })
            }
          })
          .catch(() => {
            if (!stopped) emit({ text: zh(caps) ? 'Git 不可用' : 'Git unavailable', icon: 'fork', tone: 'warn' })
          })
      }
      tick()
      const timer = setInterval(tick, POLL_MS)
      timer.unref?.()
      return () => {
        stopped = true
        clearInterval(timer)
      }
    })

    ctx.dshGo.panel('status', {
      view: VIEW,
      data: (scope, emit, caps) => {
        let stopped = false
        const push = () => {
          snapshot(config)
            .then((s) => {
              if (!stopped) emit(panelData(s, caps))
            })
            .catch(() => {
              if (!stopped) emit(panelData({ ok: false }, caps))
            })
        }
        push()
        const timer = setInterval(push, POLL_MS)
        timer.unref?.()
        return () => {
          stopped = true
          clearInterval(timer)
        }
      },
      actions: {
        refresh: () => ({ refresh: true }),
        review: () => ({ insert: REVIEW_PROMPT, toast: '已写进输入框' }),
        commit: () => ({ insert: COMMIT_PROMPT }),
      },
    })
  })
}

// ────────────────────────────── 工具卡片 ──────────────────────────────

const LIMIT_BYTES = 7_500

function oneLine(text, max = 60) {
  return clip(text, max)
}

function firstLines(text, max = 8) {
  return String(text ?? '')
    .split('\n')
    .slice(0, max)
    .map((line) => clip(line, 88))
    .join('\n')
}

/**
 * 把工具结果画成原生卡片。返回 undefined 表示「这个工具没有卡片」，调用方退回纯文本。
 * 任何异常都在外层吞掉：卡片画不出来，工具结果也不能因此失败。
 */
export function cardFor(name, args, value) {
  if (value === null || typeof value !== 'object') return undefined
  switch (name) {
    case 'git_status': {
      const dirty = Array.isArray(value.dirty) ? value.dirty : []
      return {
        type: 'stack',
        title: '仓库状态',
        children: [
          {
            type: 'keyValue',
            items: [
              { label: '分支', value: String(value.branch ?? '—') },
              { label: 'Tag', value: String(value.tag || value.headTag || '—') },
              { label: '领先/落后', value: `↑${value.ahead ?? 0} ↓${value.behind ?? 0}` },
              { label: '改动', value: dirty.length === 0 ? '干净' : `${dirty.length} 个文件` },
            ],
          },
          ...(dirty.length > 0 ? [{ type: 'text', text: firstLines(dirty.join('\n'), 6) }] : []),
          { type: 'callout', text: oneLine(value.headSubject || '—', 80), tone: 'info' },
        ],
      }
    }
    case 'git_log': {
      const commits = Array.isArray(value.commits) ? value.commits : []
      if (commits.length === 0) return { type: 'callout', text: '没有匹配的提交', tone: 'info' }
      return {
        type: 'stack',
        title: `最近 ${commits.length} 条提交`,
        children: [
          {
            type: 'table',
            columns: ['提交', '说明'],
            rows: commits.slice(0, 12).map((line) => {
              const text = String(line)
              const match = /^(\S+)\s{2}(\S+)\s{2}(.+?)\s{2}(.*)$/.exec(text)
              if (match === null) return [oneLine(text, 30), oneLine(text, 56)]
              const [, hash, date, author, subject] = match
              return [hash, oneLine(`${subject} · ${author} ${date}`, 56)]
            }),
          },
        ],
      }
    }
    case 'git_diff': {
      const patch = String(value.patch ?? value.diff ?? '')
      const stat = String(value.stat ?? value.summary ?? '')
      return {
        type: 'stack',
        title: '改动',
        children: [
          { type: 'text', text: firstLines(patch || stat, 10) },
          ...(value.truncated === true ? [{ type: 'callout', text: '内容已截断', tone: 'warn' }] : []),
        ],
      }
    }
    case 'git_audit': {
      const findings = Array.isArray(value.findings) ? value.findings : []
      if (findings.length === 0) return { type: 'callout', text: '没有发现问题', tone: 'good' }
      return {
        type: 'stack',
        title: '审计',
        children: [
          {
            type: 'list',
            items: findings.slice(0, 12).map((f) => ({
              title: oneLine(typeof f === 'string' ? f : `${f.severity ?? ''} ${f.file ?? ''}`.trim(), 60),
              subtitle: typeof f === 'string' ? '' : oneLine(f.message ?? f.code ?? '', 72),
              tone: typeof f === 'object' && /high|error|critical/i.test(String(f.severity ?? '')) ? 'bad' : 'warn',
            })),
          },
        ],
      }
    }
    case 'git_commit': {
      const files = Array.isArray(value.files) ? value.files : []
      const findings = Array.isArray(value.auditFindings) ? value.auditFindings : []
      return {
        type: 'stack',
        title: '已提交',
        children: [
          { type: 'keyValue', items: [['提交', String(value.hash ?? '—').slice(0, 12)], ['说明', oneLine(value.subject ?? '', 60)]] },
          ...(files.length > 0 ? [{ type: 'text', text: firstLines(files.join('\n'), 6) }] : []),
          ...(findings.length > 0 ? [{ type: 'callout', text: `审计发现 ${findings.length} 处：${oneLine(findings[0], 48)}`, tone: 'warn' }] : []),
        ],
      }
    }
    case 'git_push': {
      const ok = value.ok === true
      return {
        type: 'stack',
        title: '推送',
        children: [
          {
            type: 'keyValue',
            items: [
              ['远端', `${value.remote ?? '—'} ${value.ref ?? ''}`.trim()],
              ['本地', String(value.localSha ?? '—').slice(0, 7)],
              ['远端', String(value.remoteSha ?? '—').slice(0, 7)],
              ['校验', value.verified === true ? '一致' : '未确认'],
            ],
          },
          { type: 'callout', text: oneLine(value.summary ?? '', 80), tone: ok ? 'good' : 'bad' },
        ],
      }
    }
    case 'ci_watch': {
      const failed = Array.isArray(value.failedJobs) ? value.failedJobs : []
      const conclusion = String(value.conclusion ?? '').trim()
      return {
        type: 'stack',
        title: `CI ${conclusion || value.status || ''}`.trim(),
        children: [
          {
            type: 'keyValue',
            items: [
              ['状态', String(value.status ?? '—')],
              ['结论', conclusion || '—'],
              ['Run', value.runId ? `#${value.runId}` : '—'],
            ],
          },
          ...(failed.length > 0
            ? [{ type: 'list', items: failed.slice(0, 6).map((name) => ({ title: oneLine(name, 48), tone: 'bad' })) }]
            : []),
          { type: 'callout', text: oneLine(value.summary ?? '', 90), tone: /success/i.test(conclusion) ? 'good' : failed.length > 0 ? 'bad' : 'info' },
        ],
      }
    }
    case 'ci_logs': {
      const lines = Array.isArray(value.errorLines) && value.errorLines.length > 0
        ? value.errorLines
        : Array.isArray(value.lines) ? value.lines : []
      return {
        type: 'stack',
        title: `CI 日志 · ${value.job ?? ''}`.trim(),
        children: [{ type: 'text', text: firstLines(lines.join('\n'), 10) }],
      }
    }
    case 'git_show':
      return {
        type: 'stack',
        title: String(value.hash ?? '').slice(0, 7) || '提交',
        children: [
          { type: 'text', text: oneLine(value.subject ?? '', 80) },
          { type: 'keyValue', items: [['作者', String(value.author ?? '—')], ['日期', String(value.date ?? '—')]] },
          { type: 'text', text: firstLines(value.stat ?? '', 8) },
        ],
      }
    case 'git_release_notes':
      return {
        type: 'stack',
        title: '发布说明',
        children: [
          {
            type: 'keyValue',
            items: [
              ['版本', String(value.version ?? '—')],
              ['范围', `${value.from ?? '–'}..${value.to ?? 'HEAD'}`],
              ['提交', String(value.commits ?? 0)],
            ],
          },
          { type: 'text', text: firstLines(value.markdown ?? '', 10) },
        ],
      }
    default:
      return undefined
  }
}

/**
 * 给 defineTool 的 output 加 presentationMeta：卡片画不出来时返回 {}，
 * 绝不让工具调用因为界面失败。
 */
export function withCard(name, output) {
  if (!output || typeof output !== 'object') return output
  return {
    ...output,
    presentationMeta(args, value) {
      try {
        const view = cardFor(name, args, value)
        if (view === undefined) return {}
        const text = JSON.stringify(view)
        if (text.length >= LIMIT_BYTES) return {}
        return { 'dsh-go/view': JSON.parse(text) }
      } catch {
        return {}
      }
    },
  }
}
