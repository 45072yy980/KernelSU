/**
 * 静态检查：19 个工具的 parameters / output.schema 是否符合 DSH 的 JSON Schema 子集。
 *
 * parameters 与 output.schema 用的都是 DSH 的「value schema 简写」：对象级 required 不合法，
 * 必填要写在属性上（`required: true`）；object 节点必须显式写 additionalProperties。
 *
 * 这一步能提前抓到「注册期就炸」的错误，例如：
 * - 在对象上写 required 数组（简写只认属性级 `required: true`）
 * - 嵌套 object 没写 `additionalProperties`（参数编译器强制要求显式布尔）
 * - 用了 `minimum` / `pattern` / `format` 之类不被支持的 keyword
 *
 * 需要能找到 @deepseek-ai/dsh-tools（在已部署的 /root/dsha-diksu-git 里跑）：
 *   node test/schema-check.mjs
 */
import { parameterSchemaSpecToJsonSchema, valueSchemaSpecToJsonSchema } from '@deepseek-ai/dsh-tools'
import { registerReadTools } from '../lib/tools-read.js'
import { registerWriteTools } from '../lib/tools-write.js'
import { registerRemoteTools } from '../lib/tools-remote.js'
import { registerCiTools } from '../lib/tools-ci.js'

const specs = []
const register = (spec) => specs.push(spec)
const config = {
  repoPath: '/tmp',
  remote: 'origin',
  token: '',
  tokenEnv: 'DSH_GIT_TOKEN',
  tokenFile: '/tmp/none',
  apiBase: 'https://api.github.com',
  hostsPin: '',
  hostsFile: '/etc/hosts',
  authorName: 'dsh-agent',
  authorEmail: 'dsh-agent@localhost',
  maxAttempts: 1,
  retryDelayMs: 1,
  protectedBranches: 'main',
  maxDiffBytes: 1000,
  auditMaxFileMB: 1,
}
const deps = { register, config, credentials: undefined, ctx: { get: () => undefined, logger: { info() {}, warn() {} } } }

registerReadTools(deps)
registerWriteTools(deps)
registerRemoteTools(deps)
registerCiTools(deps)

let bad = 0
for (const spec of specs) {
  const problems = []
  try {
    parameterSchemaSpecToJsonSchema(spec.parameters)
  } catch (error) {
    problems.push(`parameters: ${error.message}`)
  }
  try {
    valueSchemaSpecToJsonSchema(spec.output.schema)
  } catch (error) {
    problems.push(`output: ${error.message}`)
  }
  if (problems.length > 0) {
    bad += 1
    console.log(`✗ ${spec.name}`)
    for (const problem of problems) console.log(`   ${problem}`)
  }
}

console.log(`共 ${specs.length} 个工具，${bad === 0 ? 'schema 全部合规' : `${bad} 个不合规`}`)
process.exit(bad === 0 ? 0 : 1)
