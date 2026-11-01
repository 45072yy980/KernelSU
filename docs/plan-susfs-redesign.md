# SuSFS 面板重构方案（参考 FinaSU）

> 目标：把 SuSFS 从「七张卡平铺的长页面」改成「一份可导入导出的配置 + 六个页签」。
> 参考实现：`LyraVoid/FinaSU`（`ui/screen/SusfsScreen.kt`、`data/susfs/SusfsPresentation.kt`）。

## 一、对标与差距

| 维度 | FinaSU | 我们（Diksu Ultra） |
|---|---|---|
| 信息架构 | 6 页签：概览 / 设置 / 路径 / 伪装 / 重定向 / 扩展 | 单页 7 张卡，2026-11-01 加了分组标题 |
| 配置单位 | 一份 JSON，可导入导出、带 preview 预览与 warnings | 只有本地 `SusfsRepository` 的规则列表，无整体导出 |
| 生效反馈 | `SusfsApplyReport`：applied / failures / skipped / deferred / pending_reboot | 成功/失败二元，等重启的情况混在失败里 |
| 错误提示 | `SusfsErrorKind` 分类后翻成人话（capability / UTS / ownership / busy / validation…） | 原样抛 Errno |
| 命令行 | 自家 ksud 有 `config`/`apply`/`export` 子命令 | **只有 add-\***：`susfs add-sus-path`、`add-sus-map`、`add-sus-kstat`…，没有删、没有导出、没有应用 |

## 二、结论：配置模型做在管理器侧，不动内核

命令行缺口是 `remove` / `export` / `apply`。补 Rust 要改 `userspace/ksud`，还要考虑老内核兼容，
收益不如把模型放在管理器：

- 规则的**真值**已经在 `data/susfs/SusfsRepository`（本地持久化 + 开机重放）；
- 应用 = 按配置逐条调用现有的 `SusfsCommands.add*`，收集每条的结果；
- 于是 `applied / failed / pending_reboot` 都能在 Kotlin 侧如实汇总，内核不需要新命令。

代价：`remove` 仍然做不到（内核侧没有删除接口），所以"删除规则"只能维持现状（删本地记录，
下次开机不再重放）。这一点必须写进 UI 说明，不能让用户以为立刻生效。

## 三、数据结构

`data/susfs/SusfsConfig.kt`：

```kotlin
data class SusfsConfig(
    val version: Int = 1,
    val toggles: Map<String, Boolean>,      // 日志 / AVC 伪装 / 隐藏挂载点 等
    val paths: List<String>,                 // add-sus-path
    val pathLoops: List<String>,             // add-sus-path-loop
    val maps: List<String>,                  // add-sus-map
    val kstats: List<String>,                // add-sus-kstat
    val redirects: List<Redirect>,           // target / redirected / uidScheme
    val uname: Uname?,                       // release + version
    val cmdline: String?,                    // 伪装的 cmdline 文件路径
)
```

- 校验沿用 FinaSU 的思路并本地化：绝对路径、不含 `//`、不含控制字符、长度上限、规则条数上限。
- 序列化用 `org.json`（项目里已有），schema 带 `version` 便于以后迁移。
- 导入流程：读文件 → 解析 → 与当前配置做 diff → 弹「将应用 N 项、跳过 M 项」→ 应用。

## 四、六个页签的映射（现有卡片 → 页签）

| 页签 | 现有内容 |
|---|---|
| 概览 | 状态卡（版本/变体/能力计数）+ 已保存规则计数 + 待重启提示 |
| 设置 | 功能启用状态、日志 / AVC 伪装 / 隐藏挂载点开关 |
| 路径 | 隐藏路径、循环隐藏路径、sus map |
| 伪装 | 伪 uname、伪 cmdline、kstat（跟踪 / 更新 / 完整克隆 / 手动 stat） |
| 重定向 | open redirect |
| 扩展 | 进程状态（/proc 模块状态）、上一条命令、导入导出入口 |

页签用 `HorizontalPager` + 顶部标签，每个页签各自 `rememberScrollState()`（照 FinaSU 的做法：
状态放在 pager 组合之外，避免滑动时被回收）。

## 五、落地顺序

1. `SusfsConfig` + 序列化/校验 + 单测（纯逻辑，先不接 UI）
2. 导入/导出（SAF）与 diff 预览
3. 应用执行器 + `SusfsApplyReport` 汇总
4. 页面改成 6 页签（先搬卡片，不动每张卡的内部）
5. 错误分类与提示文案

## 六、明确不做

- 不改 `userspace/ksud` 的 susfs 命令（见第二节）；
- 不做"删除规则立即生效"——内核没有这个接口，只在文案里说清。
