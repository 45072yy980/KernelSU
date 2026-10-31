/**
 * dsha-diksu-git · token 配置页（host 侧路由）
 *
 * 为什么需要它：DSH 官方的「设置 → 插件」只是容器，不给插件生成配置表单，
 * 所以插件自己得提供一个能填 token 的地方。这里给浏览器端一个最小 API：
 *
 *   GET  /__dsha/diksu-git/token   → { configured, source, file }（不回显 token）
 *   POST /__dsha/diksu-git/token   → { token: "..." } 保存；{ clear: true } 清除
 *
 * 落盘位置与运行时读取的是同一个文件（默认 ~/.dsh/git-token，chmod 600），
 * 所以保存后**立即生效**，不需要重启 harness。
 */
import { clearTokenFile, defaultTokenFile, resolveToken, saveTokenFile } from './core.js'

const ROUTE_PATH = '/__dsha/diksu-git/token'
const MAX_BODY_BYTES = 64 * 1024

/** 读请求体（限长，避免被塞大文件）。 */
function readBody(req) {
  return new Promise((resolve, reject) => {
    let size = 0
    const chunks = []
    req.on('data', (chunk) => {
      size += chunk.length
      if (size > MAX_BODY_BYTES) {
        reject(new Error('请求体过大'))
        req.destroy()
        return
      }
      chunks.push(chunk)
    })
    req.on('end', () => resolve(Buffer.concat(chunks).toString('utf8')))
    req.on('error', reject)
  })
}

/**
 * 把 token 配置路由挂到 harness 的 webServer 上。
 * 用动态 inject，避免 webServer 不存在时影响工具注册。
 */
export function registerTokenRoute(ctx, config, credentials) {
  ctx.inject(['webServer', 'connection'], (scoped) => {
    scoped.effect(
      () =>
        scoped.webServer.register({
          kind: 'exact',
          path: ROUTE_PATH,
          handler: async (req, res) => {
            // 必须鉴权：否则同网段任何人都能改 token
            if (!scoped.connection.authorizeIndex(req, res)) return
            const send = (code, payload) => {
              res.writeHead(code, {
                'content-type': 'application/json; charset=utf-8',
                'cache-control': 'no-store',
                'x-content-type-options': 'nosniff',
              })
              res.end(JSON.stringify(payload))
            }
            try {
              if (req.method === 'GET') {
                let configured = false
                let source = ''
                try {
                  const hit = await resolveToken(config, credentials)
                  configured = true
                  source = hit.source
                } catch {
                  configured = false
                }
                send(200, { ok: true, configured, source, file: defaultTokenFile(config) })
                return
              }
              if (req.method === 'POST') {
                const raw = await readBody(req)
                let body
                try {
                  body = raw.length > 0 ? JSON.parse(raw) : {}
                } catch {
                  send(400, { ok: false, error: '请求体不是合法 JSON' })
                  return
                }
                if (body?.clear === true) {
                  const cleared = await clearTokenFile(config)
                  send(200, { ok: true, cleared: cleared.removed, file: cleared.file })
                  return
                }
                const saved = await saveTokenFile(config, body?.token)
                send(200, { ok: true, saved: true, file: saved.file, bytes: saved.bytes })
                return
              }
              send(405, { ok: false, error: '只支持 GET / POST' })
            } catch (error) {
              send(400, { ok: false, error: String(error?.message ?? error) })
            }
          },
        }),
      'dsha-diksu-git:token-route',
    )
  })
}
