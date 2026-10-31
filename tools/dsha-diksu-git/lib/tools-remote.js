/**
 * dsha-diksu-git · 远端工具
 *
 * git_push / git_sync / git_auth。
 *
 * 这一组最容易踩的两个坑：
 * 1) 凭据 —— 1.1.0 只会读 process.env，用户按 README「export 一下」根本没用
 *    （Shell 设置里的变量只进 bash 子进程）。现在统一走 core 的 resolveToken：
 *    插件配置 → token 文件 → DSH 凭据服务 → 环境变量/.env，四层都能用；
 * 2) 结果 —— 1.1.0 的 git_push 把 `localSha` 填成本地期望值，其实那既不是远端
 *    实际哈希也没做校验。现在推完一定 ls-remote 复核，把本地/远端两个哈希分开报。
 */
import {
  aheadBehind,
  api,
  currentBranch,
  git,
  gitLine,
  isProtectedBranch,
  mask,
  missingTokenMessage,
  networkGitAuto,
  repoRoot,
  repoSlug,
  resolveToken,
  safeBranchName,
  safeRemoteName,
  safeTagName,
  statusSummary,
  tokenInventory,
  upstreamBranch,
} from './core.js'

/** remote URL 里可能内嵌了 https://user:token@host，展示时抹掉。 */
function sanitizeRemoteUrl(url) {
  return String(url ?? '').replace(/\/\/([^/@\s]+)@/, '//***@')
}

export function registerRemoteTools({ register, config, credentials, ctx }) {
  // 7. 推送
  register({
    name: 'git_push',
    description:
      '推送分支或 tag。token 从 DSH 凭据/.env/插件配置读取（不会出现在命令里），可钉 hosts 并自动重试；推送后用 ls-remote 校验远端确实指向本地提交。受保护分支默认拒绝强推。',
    parameters: {
      ref: { type: 'string', description: '要推送的分支名（默认当前分支）' },
      tag: { type: 'string', description: '要推送的 tag 名（给了就只推这个 tag）' },
      remote: { type: 'string', description: '远端名（默认配置的 remote）' },
      force: { type: 'boolean', description: '强制推送（改写历史或移动 tag 时才用）' },
      allowProtected: { type: 'boolean', description: '允许对受保护分支强推（默认 false，危险）' },
    },
    output: {
      schema: {
        type: 'object',
        additionalProperties: false,
        properties: {
          ok: { type: 'boolean', required: true },
          remote: { type: 'string', required: true },
          ref: { type: 'string', required: true },
          localSha: { type: 'string', required: true },
          remoteSha: { type: 'string', required: true },
          verified: { type: 'boolean', required: true },
          authSource: { type: 'string', required: true },
          summary: { type: 'string', required: true },
        },
      },
      render: (_args, value) => [{ type: 'text', text: value.summary }],
    },
    async execute(args, exec) {
      exec.signal?.throwIfAborted?.()
      const remote = safeRemoteName(args.remote ?? config.remote)
      const isTag = typeof args.tag === 'string' && args.tag.trim().length > 0
      let name = ''
      let kind = 'branch'
      let localSha = ''
      let expectSha = ''
      let pushSpec = ''
      if (isTag) {
        kind = 'tag'
        name = safeTagName(args.tag)
        localSha = await gitLine(config, ['rev-parse', '--verify', '--quiet', `refs/tags/${name}^{commit}`])
        if (localSha.length === 0) throw new Error(`本地没有 tag ${name}（先用 git_tag 打一个）`)
        // 附注 tag 在远端是 tag 对象，sha 与 commit 不同，复核要对 tag 对象
        expectSha = await gitLine(config, ['rev-parse', `refs/tags/${name}`])
        pushSpec = `refs/tags/${name}`
      } else {
        const fallback = await currentBranch(config)
        if (args.ref === undefined && fallback === 'HEAD') {
          throw new Error('当前 HEAD 没有指向任何分支（仓库可能是空的，或处于游离头状态）；先提交/切分支，或显式传 ref')
        }
        name = safeBranchName(args.ref ?? fallback)
        localSha = await gitLine(config, ['rev-parse', '--verify', '--quiet', `refs/heads/${name}`])
        if (localSha.length === 0) throw new Error(`本地没有分支 ${name}（用 git_branch action=list 看看有哪些）`)
        expectSha = localSha
        pushSpec = `refs/heads/${name}:refs/heads/${name}`
      }
      const force = args.force === true
      if (force && kind === 'branch' && isProtectedBranch(config, name) && args.allowProtected !== true) {
        throw new Error(`${name} 是受保护分支，拒绝强推（真的需要就传 allowProtected=true，并想清楚后果）`)
      }
      const pushArgs = ['push']
      if (force) pushArgs.push(kind === 'tag' ? '-f' : '--force-with-lease')
      if (kind === 'branch' && args.ref === undefined) {
        const upstream = await upstreamBranch(config)
        if (upstream.length === 0) pushArgs.push('--set-upstream')
      }
      pushArgs.push(remote, pushSpec)
      const outcome = await networkGitAuto(config, credentials, 'git push', () => pushArgs, {
        timeoutMs: 600_000,
        signal: exec.signal,
      })
      if (!outcome.ok) {
        const hint = outcome.missingToken ? `\n${outcome.missingToken}` : ''
        throw new Error(`推送 ${name} 到 ${remote} 失败：${outcome.detail}${hint}`)
      }
      // 推完复核远端引用，别只信 git 的退出码
      const verify = await networkGitAuto(
        config,
        credentials,
        'git ls-remote',
        () => ['ls-remote', remote, pushSpec.split(':')[0]],
        { timeoutMs: 120_000, signal: exec.signal },
      )
      let remoteSha = ''
      if (verify.ok) {
        const line = (verify.result.stdout ?? '').split('\n').find((row) => row.trim().length > 0) ?? ''
        remoteSha = line.split(/\s+/)[0] ?? ''
      }
      const verified = remoteSha.length > 0 && remoteSha === expectSha
      const warnings = [...new Set([...(outcome.warnings ?? []), ...(verify.warnings ?? [])])]
      return {
        ok: verified,
        remote,
        ref: `${kind === 'tag' ? 'refs/tags/' : 'refs/heads/'}${name}`,
        localSha,
        remoteSha,
        verified,
        authSource: outcome.authSource ?? '',
        summary: [
          verified
            ? `已推送并复核：${remote}/${name} → ${localSha.slice(0, 9)}`
            : `推送命令成功，但远端校验没通过（本地 ${localSha.slice(0, 9)}，远端 ${remoteSha.slice(0, 9) || '读不到'}）`,
          outcome.result ? (outcome.result.stderr || outcome.result.stdout).trim() : '',
          warnings.length > 0 ? `注意事项：\n- ${warnings.join('\n- ')}` : '',
        ]
          .filter((line) => line.length > 0)
          .join('\n'),
      }
    },
  })

  // 10. 同步
  register({
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
          ok: { type: 'boolean', required: true },
          action: { type: 'string', required: true },
          branch: { type: 'string', required: true },
          ahead: { type: 'number', required: true },
          behind: { type: 'number', required: true },
          dirty: { type: 'array', required: true, items: { type: 'string' } },
          authSource: { type: 'string', required: true },
          summary: { type: 'string', required: true },
        },
      },
      render: (_args, value) => [{ type: 'text', text: value.summary }],
    },
    async execute(args, exec) {
      exec.signal?.throwIfAborted?.()
      const action = args.action
      const remote = safeRemoteName(args.remote ?? config.remote)
      const before = await statusSummary(config)
      const branch = safeBranchName(args.branch ?? (await currentBranch(config)))
      const dirty = before.dirty
      if (action === 'pull' && dirty.length > 0 && args.allowDirty !== true) {
        throw new Error(
          `工作区有 ${dirty.length} 项改动，拒绝 pull（会盖掉本地改动）：\n- ${dirty.slice(0, 20).join('\n- ')}\n先 git_commit 或 git_stash action=save，或者确实要的话传 allowDirty=true`,
        )
      }
      const gitArgs = action === 'pull' ? ['pull', ...(args.rebase === true ? ['--rebase'] : ['--ff-only']), remote, branch] : ['fetch', remote, ...(args.prune === true ? ['--prune'] : [])]
      const outcome = await networkGitAuto(config, credentials, `git ${action}`, () => gitArgs, {
        timeoutMs: 600_000,
        signal: exec.signal,
      })
      if (!outcome.ok) {
        const hint = outcome.missingToken ? `\n${outcome.missingToken}` : ''
        throw new Error(`git ${action} ${remote} ${branch} 失败：${outcome.detail}${hint}`)
      }
      const after = await statusSummary(config)
      const { ahead, behind } = await aheadBehind(config)
      const output = (outcome.result.stderr || outcome.result.stdout).trim()
      return {
        ok: true,
        action,
        branch,
        ahead,
        behind,
        dirty: after.dirty,
        authSource: outcome.authSource ?? '',
        summary: [
          `git ${action} ${remote} ${branch} 完成（${outcome.authSource ?? '?'}）`,
          output,
          (outcome.warnings ?? []).length > 0 ? `注意事项：\n- ${outcome.warnings.join('\n- ')}` : '',
          action === 'pull' ? `现在：领先 ${ahead} / 落后 ${behind}` : '',
        ]
          .filter((line) => line.length > 0)
          .join('\n'),
      }
    },
  })

  // 19. 凭据诊断
  register({
    name: 'git_auth',
    description:
      '检查 git token 到底配在哪一层：列出插件配置 / token 文件 / DSH 凭据 / 环境变量的命中情况（只报是否配置，不回显值），并给出「怎么配上」的具体路径。check=true 时用 token 调一次 GitHub API 验证权限。',
    parameters: {
      check: { type: 'boolean', description: '是否用 token 实际调用一次 GitHub API 验证（默认 false，纯本地检查）' },
    },
    output: {
      schema: {
        type: 'object',
        additionalProperties: false,
        properties: {
          configured: { type: 'boolean', required: true },
          source: { type: 'string', required: true },
          sources: { type: 'array', required: true, items: { type: 'string' } },
          remoteUrl: { type: 'string', required: true },
          apiCheck: { type: 'string', required: true },
          summary: { type: 'string', required: true },
        },
      },
      render: (_args, value) => [{ type: 'text', text: value.summary }],
    },
    async execute(args, exec) {
      exec.signal?.throwIfAborted?.()
      const rows = await tokenInventory(config, credentials)
      const sources = rows.map((row) => `${row.configured ? '✓' : '✗'} ${row.source}`)
      let configured = false
      let source = ''
      try {
        const hit = await resolveToken(config, credentials)
        configured = true
        source = hit.source
      } catch {
        configured = false
      }
      const remote = safeRemoteName(config.remote)
      const remoteUrl = sanitizeRemoteUrl(await gitLine(config, ['remote', 'get-url', remote]))
      let apiCheck = '未执行（传 check=true 可以验证 token 权限）'
      if (args.check === true) {
        if (!configured) {
          apiCheck = '跳过：没有 token'
        } else {
          try {
            const token = (await resolveToken(config, credentials)).value
            const slug = await repoSlug(config)
            const repo = await api(config, token, 'GET', `/repos/${slug}`, undefined, { signal: exec.signal })
            apiCheck = `✓ 可以访问 ${repo.full_name ?? slug}（private=${repo.private === true}）`
          } catch (error) {
            apiCheck = `✗ ${mask(String(error?.message ?? error), '')}`
          }
        }
      }
      return {
        configured,
        source,
        sources,
        remoteUrl,
        apiCheck,
        summary: [
          configured ? `token 已配置：${source}` : 'token 未配置 —— 推送/CI/发版会失败',
          `remote ${remote}：${remoteUrl || '（没有配置远端）'}`,
          '各层命中情况：',
          ...sources.map((line) => `  ${line}`),
          `API 验证：${apiCheck}`,
          configured ? '' : `\n${missingTokenMessage(config)}`,
          `仓库根目录：${await repoRoot(config)}`,
        ]
          .filter((line) => line.length > 0)
          .join('\n'),
      }
    },
  })
}
