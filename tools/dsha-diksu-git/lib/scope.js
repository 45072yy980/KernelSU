// 每次工具调用带着「这次该在哪个目录找仓库」的上下文。
//
// 插件是单例：19 个工具共用一份 config，而会话之间工作目录不同。用
// AsyncLocalStorage 把当次会话的目录传到 core.repoRoot()，比给每个工具加参数
// 改动小得多，也不会让并发调用互相串。
import { AsyncLocalStorage } from 'node:async_hooks'
import { isAbsolute } from 'node:path'

const store = new AsyncLocalStorage()

/** 当次调用的会话工作目录；不在会话里调用时为 undefined。 */
export function currentBaseDir() {
  const value = store.getStore()?.cwd
  return typeof value === 'string' && isAbsolute(value) ? value : undefined
}

/** 在这个目录上下文里运行 fn。 */
export function withBaseDir(cwd, fn) {
  return store.run({ cwd }, fn)
}

/** 从工具执行上下文里取会话工作目录。 */
export function sessionCwdOf(exec) {
  const cwd = exec?.agent?.session?.header?.cwd
  return typeof cwd === 'string' && isAbsolute(cwd) ? cwd : undefined
}

/** 从会话服务里按 sessionId 取工作目录（原生面板用，拿不到 agent 上下文）。 */
export function sessionCwdById(ctx, sessionId) {
  if (typeof sessionId !== 'string' || sessionId === '') return undefined
  try {
    const session = ctx.get('sessions')?.get?.(sessionId)
    const cwd = session?.header?.cwd
    return typeof cwd === 'string' && isAbsolute(cwd) ? cwd : undefined
  } catch {
    return undefined
  }
}
