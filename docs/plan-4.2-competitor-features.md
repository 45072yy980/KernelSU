# 4.2 借鉴竞品的功能方案（待确认）

> 起点：2026-11-01 与用户确认的 6 项候选。核对代码后发现 1 项已有实现，实际 5 项。
> 竞品参考：KernelSU Next、ReSukiSU、BakaSU、Magisk v29、APatch。

## 零、先说结论

| 项 | 状态 | 批次 |
|---|---|---|
| 5 模块 zip 的 XZ 支持 | **已支持**（`zip`/`zip-extensions` 都开了 `xz` feature），只需真机验一次 | — |
| 6 尊重系统"移除动画" | 待做，改动小 | A |
| 3 批量安装模块 | **已实现**（模块页 FAB：`EXTRA_ALLOW_MULTIPLE` + ClipData → 编号确认 → `flashModulesSequentially`，两套皮肤都有），只需验证 | — |
| 4 超级用户规则备份 / 恢复 | 待做 | B |
| 2 模块备份 / 恢复 | 待做 | B |
| 1 越狱自检 + 一键修复 | 待做，价值最高、改动最大 | C |

## 一、A 批（小改动，先落地）

### A1 尊重系统"移除动画"（Magisk v29 同款）

- 现状：`MainActivity` 的页面切换用的是 Navigation3 默认动画，系统里关掉动画后我们仍然有过渡。
- 做法：读 `Settings.Global.ANIMATOR_DURATION_SCALE`（0 表示系统要求无动画），为 0 时把
  页面切换 / Pager 的过渡换成 snap，其它动画组件同步降级。
- 涉及：`MainActivity.kt`（注入一个 `LocalAnimationsEnabled`），三套皮肤的 Pager 与
  `NavSwipeDirection` 附近；不改业务逻辑。
- 验收：设置里把"动画时长"设为 0.0x，进页面应该瞬切；恢复 1x 后行为跟现在一致。

### A2 批量安装模块（KSUN 同款）——撤销：已有实现

模块页右下角 FAB 就是入口（`ModuleMiuix.kt:379` / `ModuleMaterial.kt` 同款）：多选 zip →
`clipData` 逐个取出 → 多于一个时弹「将安装以下模块：1. … 2. … 3. …」→ 确认后进进度页，
由 `flashModulesSequentially()` 顺序安装、失败即停。**不需要新做**。

唯一还能补的是"外部多选分享"（文件管理器 share 多个 zip 时逐个发意图、逐个确认），
但内核侧分区守卫本来就是每个包按一次音量键，合并确认意义有限，暂不做。

### A2-旧（已撤销）


- 现状：`InstallScreen` 一次只接一个 zip（或仓库下载）。
- 做法：文件选择改为多选；逐个复用现有安装链路（分区守卫扫描 → `ksud module install` →
  装完一个再下一个），队列里显示 `i/n` 与当前模块名；某个失败就停下并保留已完成的结果。
- 涉及：`ui/screen/install/*`（3 皮肤）、`InstallUiState/ViewModel`、`data/registry/DownloadIntegrity.kt`
  的重哈希逻辑（每个包都要过）。
- 风险：分区守卫对每个包都会弹音量键确认，批量时要合并成一次提示，否则用户被连环弹窗淹没。

## 二、B 批（数据安全）

### B1 超级用户规则备份 / 恢复

- 导出：授权列表（uid、包名、授权状态、profile JSON）→ 打包 zip（含 `manifest.json` +
  版本号 + 导出时间）。
- 导入：校验版本与 schema，按包名匹配覆盖并提示差异数量，不做静默合并。
- 涉及：`SuperUser*` 三皮肤 + 新的 `data/backup/` 层；数据源是 `ksud` 的 allowlist 与
  `app_profile`（已有 `getAppProfile/setAppProfile` 原生接口可复用）。

### B2 模块备份 / 恢复

- 备份：把已装模块目录打包（可用 `ksud module list --json` 拿清单），保留 `module.prop`、
  `disable`、`skip_mount` 标记；库里可留多份，按时间列。
- 恢复：先走一遍分区守卫扫描，再解包回 `/data/adb/modules/<id>`，冲突时列出"覆盖/跳过"。
- 关键取舍：**不做**整机快照（体积不可控），只备份模块与其状态位。

## 三、C 批（差异化）

### C1 越狱自检 + 一键修复

首页卡片或新页面，逐项检查并给结论：

| 检查项 | 判据 | 可做的修复 |
|---|---|---|
| 内核模块版本 vs 管理器 | `Natives.version` vs `versionCode` | 提示需重新注入（不自动刷） |
| ksud 用户态版本 | `/data/adb/ksud --version` 与构建标识前缀比对 | 一键 `ksud install` |
| 软链一致性 | `/data/adb/ksu/bin/ksud` 指向是否过期 | 重建软链 |
| UAPI 对齐 | `managerUAPIVersion` vs `kernelUAPIVersion` | 只报告 |
| 分区保护 | 守卫开关 + 扫描结果 | 跳转设置 |
| SuSFS / Shizuku | 各面板的现有探测函数 | 跳转面板 |

- 全部复用已有函数（`probeInstalledKsudVersion`、`susfsVersion`、`Natives.*`），不引新依赖。
- 输出成"通过 / 需处理 / 做不到"，每项可展开看原始值 —— 就是版本详情页的加强版。

## 四、明确的非目标

- 多管理器兼容（ReSukiSU/BakaSU 那套）：内核路径不同，收益为零。
- KPM / KP 模块（APatch）：另一套内核模块体系，跟越狱链路冲突。
- 挂载模式切换（Magic Mount / OverlayFS）：我们有 Hybrid Mount，切回去等于重做挂载层。

## 五、验证方式

- 每批都跑 CI `build-manager.yml`（arm64 自用包），编译红当场修。
- 需真机确认的三件事：批量安装的守卫提示是否只弹一次；备份恢复后模块能否被识别；
  自检页每项判据在越狱机上是否都有值。
