/**
 * dsha-diksu-git · CI 与发版
 *
 * git_release_notes / ci_dispatch / ci_watch / ci_logs / release_publish。
 *
 * 1.1.0 在这块的问题（都已修）：
 * - ci_dispatch 只等 30 秒且用 head_sha 前 8 位模糊匹配，容易认成同 sha 的旧 run；
 * - ci_watch 的 sleep 不可取消；
 * - release_publish **先建 draft release 再下载产物**，中间任何失败都留下孤儿草稿；
 * - release_publish 调 `unzip`，而本机根本没有 unzip（只有 python3）。
 */
import { mkdtemp, readFile, readdir, rm, writeFile } from 'node:fs/promises'
import { tmpdir } from 'node:os'
import { basename, join, relative } from 'node:path'
import {
  api,
  buildReleaseNotes,
  currentBranch,
  extractZip,
  git,
  gitLine,
  mask,
  repoRoot,
  repoSlug,
  resolveToken,
  safePath,
  safeRevision,
  safeTagName,
  safeWorkflowName,
  sleep,
  writeRepoFile,
} from './core.js'

const DOWNLOAD_TIMEOUT_MS = 300_000

/** 取 token（拿不到就把完整配置指引抛出去）。 */
async function requireToken(config, credentials) {
  const hit = await resolveToken(config, credentials)
  return hit.value
}

/** 递归列出目录里的文件。 */
async function listFiles(dir) {
  const out = []
  const walk = async (current) => {
    for (const entry of await readdir(current, { withFileTypes: true })) {
      const full = join(current, entry.name)
      if (entry.isDirectory()) await walk(full)
      else if (entry.isFile()) out.push(full)
    }
  }
  await walk(dir)
  return out
}

/** 按 artifact 名 + 文件名给出发布资产名（沿用 release.yml 的改名规则）。 */
function assetName(artifact, file) {
  const name = basename(file)
  if (name.endsWith('.apk')) return name
  if (artifact === 'manager') return ''
  if (artifact.startsWith('ksud-')) {
    if (name === 'ksud') return artifact
    if (name === 'ksud.exe') return `${artifact}.exe`
    return ''
  }
  if (artifact.startsWith('ksuinit-')) {
    return name === 'ksuinit' ? artifact : ''
  }
  if (artifact.endsWith('-lkm')) {
    return name.endsWith('_kernelsu.ko') ? `lkm-${artifact.slice(0, -4)}_kernelsu.ko` : ''
  }
  return ''
}

/** 资产分类，用于汇总报告。 */
function assetKind(asset) {
  if (asset.endsWith('.apk')) return 'manager'
  if (asset.endsWith('_kernelsu.ko')) return 'lkm'
  if (asset.startsWith('ksud-')) return 'ksud'
  if (asset.startsWith('ksuinit-')) return 'ksuinit'
  return 'other'
}

async function downloadArtifact(config, token, url, destination, signal) {
  const response = await fetch(url, {
    headers: {
      Authorization: `Bearer ${token}`,
      Accept: 'application/vnd.github+json',
      'User-Agent': 'dsha-diksu-git',
    },
    redirect: 'follow',
    signal: signal ? AbortSignal.any([signal, AbortSignal.timeout(DOWNLOAD_TIMEOUT_MS)]) : AbortSignal.timeout(DOWNLOAD_TIMEOUT_MS),
  })
  if (!response.ok) {
    throw new Error(`下载产物失败 ${response.status} ${response.statusText}`)
  }
  const buffer = Buffer.from(await response.arrayBuffer())
  await writeFile(destination, buffer)
  return buffer.length
}

async function uploadAsset(config, token, uploadUrl, file, name, signal) {
  const buffer = await readFile(file)
  const response = await fetch(`${uploadUrl}?name=${encodeURIComponent(name)}`, {
    method: 'POST',
    headers: {
      Authorization: `Bearer ${token}`,
      Accept: 'application/vnd.github+json',
      'Content-Type': 'application/octet-stream',
      'Content-Length': String(buffer.length),
      'User-Agent': 'dsha-diksu-git',
    },
    body: buffer,
    signal: signal ? AbortSignal.any([signal, AbortSignal.timeout(DOWNLOAD_TIMEOUT_MS)]) : AbortSignal.timeout(DOWNLOAD_TIMEOUT_MS),
  })
  if (!response.ok) {
    const text = await response.text()
    throw new Error(`上传 ${name} 失败：${response.status} ${text.slice(0, 300)}`)
  }
  return response.json()
}

export function registerCiTools({ register, config, credentials, ctx }) {
  // 14. 发布说明
  register({
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
          version: { type: 'string', required: true },
          path: { type: 'string', required: true },
          commits: { type: 'number', required: true },
          markdown: { type: 'string', required: true },
          summary: { type: 'string', required: true },
        },
      },
      render: (_args, value) => [{ type: 'text', text: value.summary }],
    },
    async execute(args, exec) {
      exec.signal?.throwIfAborted?.()
      const to = safeRevision(args.to ?? 'HEAD', 'to')
      let from = ''
      if (typeof args.from === 'string' && args.from.trim().length > 0) {
        from = safeRevision(args.from, 'from')
      } else {
        from = await gitLine(config, ['describe', '--tags', '--abbrev=0', '--match', 'v[0-9]*', to])
      }
      const version = typeof args.version === 'string' && args.version.trim().length > 0 ? args.version.trim() : to
      const { markdown, commits } = await buildReleaseNotes(config, from, to, version)
      let path = ''
      if (typeof args.writeTo === 'string' && args.writeTo.trim().length > 0) {
        path = await writeRepoFile(config, args.writeTo.trim(), `${markdown}\n`)
      }
      return {
        from,
        to,
        version,
        path,
        commits: commits.length,
        markdown,
        summary: [
          `发布说明（${from ? `${from}..${to}` : to}，${commits.length} 个提交）`,
          path ? `已写入 ${path}` : '',
          '',
          markdown,
        ]
          .filter((line) => line !== '')
          .join('\n'),
      }
    },
  })

  // 15. 触发 workflow
  register({
    name: 'ci_dispatch',
    description:
      '触发 GitHub Actions 的 workflow_dispatch 并返回 run id。本项目的自用精简包就是 dispatch build-manager.yml 且 inputs.abi=arm64-v8a。',
    parameters: {
      workflow: { type: 'string', description: '工作流文件名（默认 build-manager.yml）' },
      ref: { type: 'string', description: '分支或 tag（默认当前分支）' },
      inputs: {
        type: 'object',
        additionalProperties: true,
        description: 'dispatch 输入，例如 {"abi":"arm64-v8a"}',
      },
      waitSeconds: { type: 'number', description: '最多等多少秒找 run（默认 90）' },
    },
    output: {
      schema: {
        type: 'object',
        additionalProperties: false,
        properties: {
          ok: { type: 'boolean', required: true },
          runId: { type: 'number' },
          runUrl: { type: 'string', required: true },
          headSha: { type: 'string', required: true },
          summary: { type: 'string', required: true },
        },
      },
      render: (_args, value) => [{ type: 'text', text: value.summary }],
    },
    async execute(args, exec) {
      exec.signal?.throwIfAborted?.()
      const token = await requireToken(config, credentials)
      const slug = await repoSlug(config)
      const file = safeWorkflowName(args.workflow ?? 'build-manager.yml')
      const ref = typeof args.ref === 'string' && args.ref.trim().length > 0 ? safeRevision(args.ref, 'ref') : await currentBranch(config)
      const headSha = await gitLine(config, ['rev-parse', ref])
      const startedAt = Date.now()
      await api(config, token, 'POST', `/repos/${slug}/actions/workflows/${file}/dispatches`, {
        ref,
        inputs: args.inputs ?? {},
      }, { signal: exec.signal })

      const waitMs = Math.max(10, Math.min(600, Math.trunc(args.waitSeconds ?? 90))) * 1000
      const deadline = Date.now() + waitMs
      let found = null
      while (Date.now() < deadline) {
        await sleep(3000, exec.signal)
        const runs = await api(config, token, 'GET', `/repos/${slug}/actions/runs?event=workflow_dispatch&branch=${encodeURIComponent(ref)}&per_page=30`, undefined, { signal: exec.signal })
        const list = runs.workflow_runs ?? []
        found = list.find((run) => {
          const created = Date.parse(run.created_at ?? '') || 0
          return run.head_sha === headSha && created >= startedAt - 60_000
        })
        if (found) break
      }
      if (!found) {
        return {
          ok: false,
          runUrl: '',
          headSha,
          summary: `已 dispatch ${file}（ref=${ref}，sha=${headSha.slice(0, 9)}），但 ${Math.round(waitMs / 1000)} 秒内没等到对应的 run。\n稍后用 ci_watch sha=${headSha.slice(0, 9)} 找它。`,
        }
      }
      return {
        ok: true,
        runId: found.id,
        runUrl: found.html_url ?? '',
        headSha,
        summary: `已触发 ${file}（ref=${ref}）\nrun ${found.id} · ${found.html_url ?? ''}\n用 ci_watch runId=${found.id} 跟结果`,
      }
    },
  })

  // 16. 等待 run
  register({
    name: 'ci_watch',
    description:
      '轮询一条 workflow run 直到结束，返回结论与失败的 job/步骤。本项目本地没有 Android SDK，编译验证**只能**靠 CI，所以这个工具是改完代码后的必经一步。',
    parameters: {
      runId: { type: 'number', description: 'run id（与 sha 二选一）' },
      sha: { type: 'string', description: '按提交 sha 找最新的 run' },
      timeoutSeconds: { type: 'number', description: '最长等待秒数（默认 1800）' },
      pollSeconds: { type: 'number', description: '轮询间隔秒数（默认 20）' },
    },
    output: {
      schema: {
        type: 'object',
        additionalProperties: false,
        properties: {
          status: { type: 'string', required: true },
          conclusion: { type: 'string', required: true },
          runId: { type: 'number' },
          runUrl: { type: 'string', required: true },
          failedJobs: { type: 'array', required: true, items: { type: 'string' } },
          summary: { type: 'string', required: true },
        },
      },
      render: (_args, value) => [{ type: 'text', text: value.summary }],
    },
    async execute(args, exec) {
      exec.signal?.throwIfAborted?.()
      // 参数先校验再谈 token：参数写错不该先弹「没有 token」
      if (args.runId !== undefined && (!Number.isFinite(args.runId) || !Number.isInteger(args.runId) || args.runId <= 0)) {
        throw new Error(`runId 必须是正整数（收到 ${args.runId}）；也可以改传 sha`)
      }
      let runId = args.runId === undefined ? 0 : args.runId
      const givenSha = typeof args.sha === 'string' && args.sha.trim().length > 0 ? args.sha.trim() : ''
      if (runId === 0 && givenSha.length === 0) {
        throw new Error('runId 与 sha 至少要给一个（ci_dispatch 的输出里有 runId；不给 sha 我不会替你猜 HEAD，避免盯错 run）')
      }
      const token = await requireToken(config, credentials)
      const slug = await repoSlug(config)
      if (runId === 0) {
        const sha = safeRevision(givenSha, 'sha')
        const runs = await api(config, token, 'GET', `/repos/${slug}/actions/runs?head_sha=${encodeURIComponent(sha)}&per_page=10`, undefined, { signal: exec.signal })
        const list = runs.workflow_runs ?? []
        if (list.length === 0) throw new Error(`找不到 sha=${sha} 的 workflow run（是不是还没 push？）`)
        runId = list[0].id
      }
      const timeoutMs = Math.max(30, Math.min(7200, Math.trunc(args.timeoutSeconds ?? 1800))) * 1000
      const pollMs = Math.max(5, Math.min(120, Math.trunc(args.pollSeconds ?? 20))) * 1000
      const deadline = Date.now() + timeoutMs
      let run = await api(config, token, 'GET', `/repos/${slug}/actions/runs/${runId}`, undefined, { signal: exec.signal })
      while (run.status !== 'completed' && Date.now() < deadline) {
        await sleep(pollMs, exec.signal)
        run = await api(config, token, 'GET', `/repos/${slug}/actions/runs/${runId}`, undefined, { signal: exec.signal })
      }
      const jobs = await api(config, token, 'GET', `/repos/${slug}/actions/runs/${runId}/jobs?per_page=100`, undefined, { signal: exec.signal })
      const failedJobs = (jobs.jobs ?? [])
        .filter((job) => job.conclusion !== null && job.conclusion !== 'success' && job.conclusion !== 'skipped')
        .map((job) => {
          const steps = (job.steps ?? []).filter((step) => step.conclusion === 'failure').map((step) => step.name)
          const detail = steps.length > 0 ? `（失败步骤：${steps.join(', ')}）` : ''
          return `${job.name} → ${job.conclusion}${detail}`
        })
      const conclusion = run.status === 'completed' ? (run.conclusion ?? 'unknown') : 'timeout'
      return {
        status: run.status,
        conclusion,
        runId,
        runUrl: run.html_url ?? '',
        failedJobs,
        summary: [
          `${run.name ?? 'run'} #${run.run_number ?? ''} · ${run.status}/${conclusion} · ${run.html_url ?? ''}`,
          run.status === 'completed' ? '' : `⚠️ 等了 ${Math.round(timeoutMs / 1000)} 秒还没结束，可以再 ci_watch 一次 runId=${runId}`,
          failedJobs.length > 0 ? `失败的 job：\n- ${failedJobs.join('\n- ')}` : run.status === 'completed' ? '所有 job 都通过了' : '',
        ]
          .filter((line) => line.length > 0)
          .join('\n'),
      }
    },
  })

  // 17. 拉日志
  register({
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
          job: { type: 'string', required: true },
          ok: { type: 'boolean', required: true },
          lines: { type: 'array', required: true, items: { type: 'string' } },
          errorLines: { type: 'array', required: true, items: { type: 'string' } },
          summary: { type: 'string', required: true },
        },
      },
      render: (_args, value) => [{ type: 'text', text: value.summary }],
    },
    async execute(args, exec) {
      exec.signal?.throwIfAborted?.()
      const token = await requireToken(config, credentials)
      const slug = await repoSlug(config)
      const runId = Math.trunc(args.runId)
      if (!Number.isFinite(runId) || runId <= 0) throw new Error('runId 必须是正整数')
      const jobs = await api(config, token, 'GET', `/repos/${slug}/actions/runs/${runId}/jobs?per_page=100`, undefined, { signal: exec.signal })
      const all = jobs.jobs ?? []
      if (all.length === 0) throw new Error(`run ${runId} 没有 job`)
      let target
      if (typeof args.job === 'string' && args.job.trim().length > 0) {
        const wanted = args.job.trim()
        target = all.find((job) => String(job.id) === wanted || job.name === wanted)
        if (!target) throw new Error(`run ${runId} 里没有 job "${wanted}"；可选：${all.map((job) => job.name).join(' / ')}`)
      } else {
        target = all.find((job) => job.conclusion === 'failure' || job.conclusion === 'cancelled') ?? all[0]
      }
      const response = await fetch(`${config.apiBase}/repos/${slug}/actions/jobs/${target.id}/logs`, {
        headers: { Authorization: `Bearer ${token}`, Accept: 'application/vnd.github+json', 'User-Agent': 'dsha-diksu-git' },
        redirect: 'follow',
        signal: exec.signal ? AbortSignal.any([exec.signal, AbortSignal.timeout(DOWNLOAD_TIMEOUT_MS)]) : AbortSignal.timeout(DOWNLOAD_TIMEOUT_MS),
      })
      if (!response.ok) throw new Error(`拉日志失败：${response.status} ${response.statusText}`)
      const text = await response.text()
      const cleaned = text
        .split('\n')
        .map((line) => line.replace(/^\d{4}-\d{2}-\d{2}T[\d:.]+Z /, ''))
        .map((line) => mask(line, token))
      const maxLines = Math.max(10, Math.min(2000, Math.trunc(args.maxLines ?? 120)))
      const tail = cleaned.slice(-maxLines)
      const errorLines = cleaned.filter((line) => /\berror\b|FAILED|failed|Error:/i.test(line)).slice(-40)
      return {
        job: target.name,
        ok: target.conclusion === 'success',
        lines: tail,
        errorLines,
        summary: [
          `job ${target.name} · ${target.conclusion ?? '进行中'}`,
          errorLines.length > 0 ? `错误行：\n${errorLines.join('\n')}` : '没有匹配到 error/failed 的行',
          `末尾 ${tail.length} 行：`,
          tail.join('\n'),
        ].join('\n'),
      }
    },
  })

  // 18. 建 release 并发资产
  register({
    name: 'release_publish',
    description:
      '从一条已完成的 Release run 里挑出全部产物（管理器 APK、8 个 ksud、3 个 ksuinit、16 个 LKM），建 release、传资产并发布。资产命名规则与本项目历次发布一致。未给 bodyFile 时自动按 conventional commit 生成发布说明。',
    parameters: {
      tag: { type: 'string', required: true, description: '已存在的 tag，例如 v4.2.0-ultra' },
      runId: { type: 'number', description: 'Release workflow 的 run id（提供产物）' },
      title: { type: 'string', description: '发布标题（默认与 tag 相同）' },
      bodyFile: { type: 'string', description: '发布说明 Markdown 文件路径（相对仓库）' },
      draft: { type: 'boolean', description: '只建草稿不发布（默认 false）' },
    },
    output: {
      schema: {
        type: 'object',
        additionalProperties: false,
        properties: {
          ok: { type: 'boolean', required: true },
          releaseId: { type: 'number' },
          url: { type: 'string', required: true },
          draft: { type: 'boolean', required: true },
          assets: { type: 'array', required: true, items: { type: 'string' } },
          skipped: { type: 'array', required: true, items: { type: 'string' } },
          summary: { type: 'string', required: true },
        },
      },
      render: (_args, value) => [{ type: 'text', text: value.summary }],
    },
    async execute(args, exec) {
      exec.signal?.throwIfAborted?.()
      const tag = safeTagName(String(args.tag ?? ''))
      if (!/^v\d+\.\d+\.\d+/.test(tag)) throw new Error(`tag 应该形如 v4.2.0-ultra，收到 ${tag}`)
      if (!Number.isFinite(args.runId) || args.runId <= 0) throw new Error('必须提供 Release workflow 的 runId')
      const runId = Math.trunc(args.runId)
      const token = await requireToken(config, credentials)
      const slug = await repoSlug(config)
      const workDir = await mkdtemp(join(tmpdir(), 'diksu-release-'))
      try {
        // 0) tag 必须真的存在
        const tagSha = await gitLine(config, ['rev-parse', '--verify', '--quiet', `refs/tags/${tag}^{commit}`])
        if (tagSha.length === 0) throw new Error(`本地没有 tag ${tag}；先 git_tag 再发布`)
        const existing = await api(config, token, 'GET', `/repos/${slug}/releases/tags/${encodeURIComponent(tag)}`, undefined, { signal: exec.signal }).catch(() => null)
        if (existing && existing.id) {
          throw new Error(`GitHub 上已经有 ${tag} 的 release（${existing.html_url ?? existing.id}）；要重发请先在网页上删掉它`)
        }

        // 1) 先把产物全部下齐、解开、改名（避免留下孤儿草稿）
        const artifacts = await api(config, token, 'GET', `/repos/${slug}/actions/runs/${runId}/artifacts?per_page=100`, undefined, { signal: exec.signal })
        const list = (artifacts.artifacts ?? []).filter((item) => !item.expired && item.name !== 'manager-gradle' && item.name !== 'mappings')
        if (list.length === 0) throw new Error(`run ${runId} 没有可用的 artifact（是不是已经过期了？）`)
        const ready = []
        const skipped = []
        for (const artifact of list) {
          const zipPath = join(workDir, `${artifact.name}.zip`)
          const dir = join(workDir, artifact.name)
          await downloadArtifact(config, token, artifact.archive_download_url, zipPath, exec.signal)
          await extractZip(zipPath, dir)
          for (const file of await listFiles(dir)) {
            const name = assetName(artifact.name, file)
            if (name.length === 0) {
              skipped.push(`${artifact.name}/${relative(dir, file)}`)
              continue
            }
            ready.push({ name, file, kind: assetKind(name) })
          }
        }
        if (ready.length === 0) throw new Error(`run ${runId} 的产物里没有可发布的文件（跳过 ${skipped.length} 个）`)

        // 2) 生成发布说明
        const title = typeof args.title === 'string' && args.title.trim().length > 0 ? args.title.trim() : tag
        let body = ''
        if (typeof args.bodyFile === 'string' && args.bodyFile.trim().length > 0) {
          const root = await repoRoot(config)
          body = await readFile(join(root, safePath(args.bodyFile.trim())), 'utf8')
        } else {
          const previous = await gitLine(config, ['describe', '--tags', '--abbrev=0', '--match', 'v[0-9]*', `${tag}^`])
          const notes = await buildReleaseNotes(config, previous, tag, tag)
          body = notes.markdown
        }

        // 3) 建 draft release（此时产物已经在本地了）
        const release = await api(config, token, 'POST', `/repos/${slug}/releases`, {
          tag_name: tag,
          name: title,
          body,
          draft: true,
          prerelease: false,
        }, { signal: exec.signal })
        const releaseId = release.id
        const uploadUrl = String(release.upload_url ?? '').replace(/\{.*\}$/, '')
        if (uploadUrl.length === 0) throw new Error('GitHub 返回的 upload_url 是空的')

        // 4) 逐个上传；中途失败就把刚建的草稿删掉，不留孤儿 release
        const assets = []
        try {
          for (const item of ready) {
            const uploaded = await uploadAsset(config, token, uploadUrl, item.file, item.name, exec.signal)
            assets.push(uploaded.name ?? item.name)
          }
        } catch (error) {
          const cleaned = await api(config, token, 'DELETE', `/repos/${slug}/releases/${releaseId}`, undefined, { signal: exec.signal })
            .then(() => '已删除刚建的草稿 release')
            .catch((cleanupError) => `草稿 release 清理失败：${cleanupError.message}（请到网页上删掉）`)
          throw new Error(`上传资产失败：${error.message}\n${cleaned}\n已上传：${assets.length} 个`)
        }

        // 5) 发布
        const draft = args.draft === true
        const published = draft
          ? release
          : await api(config, token, 'PATCH', `/repos/${slug}/releases/${releaseId}`, { draft: false, make_latest: 'true' }, { signal: exec.signal })
        const counts = assets.reduce((acc, name) => {
          const kind = assetKind(name)
          acc[kind] = (acc[kind] ?? 0) + 1
          return acc
        }, {})
        return {
          ok: true,
          releaseId,
          url: published.html_url ?? '',
          draft,
          assets,
          skipped,
          summary: [
            `${draft ? '已建草稿 release' : '已发布'} ${tag}：${assets.length} 个资产`,
            `  manager ${counts.manager ?? 0} · ksud ${counts.ksud ?? 0} · ksuinit ${counts.ksuinit ?? 0} · lkm ${counts.lkm ?? 0}`,
            published.html_url ?? '',
            skipped.length > 0 ? `跳过的文件：\n- ${skipped.join('\n- ')}` : '',
          ]
            .filter((line) => line.length > 0)
            .join('\n'),
        }
      } finally {
        await rm(workDir, { recursive: true, force: true }).catch(() => {})
      }
    },
  })
}
