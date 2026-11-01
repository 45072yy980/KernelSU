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

---

## 七、全面重构（2026-11-01 追加）

用户决定整套重写，不再在旧代码上打补丁。现状 2600 行，依赖干净（只被
`ui/screen/susfs` 两个文件引用），可以整体替换。

### 目标结构

```
data/susfs/
  SusfsStatus.kt        状态模型（loaded / version / variant / features / writable）
  SusfsStatusParser.kt  解析 ksud 输出（纯函数，可单测）
  SusfsRule.kt          规则模型（sealed：Path / Loop / Map / Kstat / Redirect / Uname / Cmdline）
  SusfsConfig.kt        一份配置 = 开关 + 规则；JSON 序列化、校验、diff
  SusfsApplyReport.kt   applied / failed / skipped / pendingReboot
  SusfsErrorKind.kt     错误分类 → 人话
  SusfsCommands.kt      薄封装：只负责调 ksud，不做判断
  SusfsRepository.kt    持久化（整份配置存 JSON）+ 导入导出 + apply
ui/screen/susfs/
  SusfsScreen.kt        六页签骨架 + pager + 页面级状态
  SusfsOverviewTab.kt   概览（版本/能力计数/规则计数/待重启）
  SusfsSettingsTab.kt   开关与功能启用状态
  SusfsPathsTab.kt      隐藏路径 / 循环路径 / map
  SusfsSpoofTab.kt      kstat（跟踪/更新/完整克隆/手动）、伪 uname、伪 cmdline
  SusfsRedirectTab.kt   open redirect
  SusfsExtensionsTab.kt 进程状态、上一条命令、导入导出入口
  SusfsEditors.kt       共用组件（规则行、输入弹窗、状态徽标），miuix + material 两套
susfs/
  SusfsViewModel.kt     意图 + 状态（沿用现有类名，避免改路由）
  SusfsBootReceiver.kt  开机重放（改成读整份配置）
```

### 迁移顺序（每步都能单独编译）

1. 新 `data/susfs` 模型 + 校验 + 单测（不接 UI，旧代码不受影响）
2. 新命令封装与 apply 引擎 + 报告
3. 新仓储读写整份配置；开机重放切到新仓储
4. 新 6 页签 UI，逐页搬逻辑
5. 删掉旧 `susfs/SusfsModels.kt`、`SusfsRepository.kt`、`SusfsCommands.kt` 与旧屏
6. 旧规则的迁移（首次启动把散存规则读进新配置）

### 硬约束

- 不动 `userspace/ksud` 的 susfs 命令；
- 内核没有删除接口，"删除"只影响下次开机，文案必须说清。

## 八、对齐 FinaSU 的修正（2026-11-01 晚）

自查发现的问题，均已修：

| 问题 | 后果 | 修法 |
|---|---|---|
| Rust `Apply.file` 写成了位置参数 | Kotlin 传 `--file` 必然失败，一直在走逐条回退 | 加 `#[arg(long)]` |
| 配置靠临时文件交给 root | SELinux 下 root 读应用私有目录不可靠 | 改走 stdin（`printf '%s' '<json>' \| ksud susfs apply`），对齐 FinaSU |
| 没有超时 | 一条卡住的命令会占死共享 shell | enqueue + futures，30s 超时 |
| 开关只写"真"的键 | 关掉开关后内核仍保持开 | 三个开关始终写出，显式下发 |
| 应用后不刷新状态 | 能力列表显示旧值 | 应用完立刻重新读状态 |
| 导入直接覆盖 | 覆盖别人的配置无法后悔 | 先算 diff、弹预览、确认后才下发 |
| 消息行被 fillMaxSize 挤出屏幕 | 提示看不见 | 内容区改 weight(1f)，消息固定底部 |
| 页面无水平内边距 | 卡片贴边 | 页面层加 12dp |

## 九、第二轮自查（2026-11-01 深夜）

实机截图暴露的问题：整屏文字近黑、弹窗配色错乱 —— 根因是**在 miuix 皮肤下用了 Material3 的
Text / AlertDialog / OutlinedTextField**：文字取到 Material 的 onSurface（浅色主题下是深色），
背景却是 miuix 的深色卡片。

修法：外观层全部按皮肤分支（SusfsText / SusfsSectionTitle / SusfsCard / SusfsRow /
SusfsSwitchRow / SusfsTextField / AppAlertDialog），页面只调用这些函数。

同时补上「恢复」：
- 恢复上次应用：把 ksud 里记录的配置读回来，走导入预览再应用
- 清空配置：置空并下发（内核规则仍要重启才失效，报告里标明）

## 十、收尾（2026-11-02）

SuSFS 面板到此收口。最终形态：

- **ksud**：`status / import / preview / apply / export`，配置与状态在 `/data/adb/ksu/susfs/`，
  先存后应用；报告四分类（applied / failed / skipped / pendingReboot）
- **管理器**：六页签 + 草稿模型（改草稿 → 顶部提示 → 应用/放弃）、可滑动 pager、每页独立滚动
- **行组件**：miuix 用 `ArrowPreference` / `SwitchPreference`，material 用 `SegmentedListItem` /
  `SegmentedSwitchItem`，与设置页同一套语言
- **功能**：隐藏路径 / 循环路径 / sus map / kstat 更新 / kstat 完整克隆 / 伪 uname（预填内核事实）/
  伪 cmdline / open redirect / 三个开关 / 导入导出 / 恢复上次应用 / 恢复默认

**未移植（明确放弃）**：
- ownership 切换：只有「内置 + susfs_guard_lkm 双实现」才需要，本仓库不存在这个状态
- schema1 备份迁移：我们没有 v1 配置
- 静态 kstat 的 13 字段手填表单：路径级 update 与完整克隆已覆盖日常用法，手填 stat 属于极端场景

**删除规则仍要重启生效**：SuSFS 内核 ABI 只有 ADD 系列命令，撤回只能等重启后不再重放；
这一点在报告里以 pendingReboot 明示。
