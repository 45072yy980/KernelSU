# 主题移植（L1+L2）验收规范

> **目的**：证明两件事——① 老皮肤（Miuix / MiuixStock / Material）没被碰过；② `Themed` 真的跟着主题包变。
> 方案与边界见 [`theme-freedom-plan.md`](theme-freedom-plan.md)（§0 Miuix 边界、§2 token 架构、§8 拍板：本期 L1+L2）。
> **本文只定义"怎么验"，不改方案。**

---

## 0. 先把"零回归"定义清楚

**总判据**：同一台设备、同一份设置、同一页面、同一系统主题下，`Miuix` / `MiuixStock` / `Material` 三个模式的渲染与基线一致。

**唯一允许的可见差异**：样式选择器多出第 4 项「自定义主题」（`UiMode.Themed`）——由 `UiMode.entries` 自动驱动。
除此之外任何像素差异、行为差异，都算不通过。

**为什么必须四层证据**（只截图会漏，只 grep 也会漏）：

| 证据层 | 覆盖的失效模式 | 覆盖不到 |
|---|---|---|
| 编译 | 新增枚举值后漏分支（52 处 `when`，编译器强制） | 带 `else` 的静默点、运行时行为 |
| 代码 diff | 老分支被"顺手改" | 语义等价但视觉变了 |
| 静态断言 / 单测 | `LocalThemeTokens` 泄漏、序列化、设置命名空间、token 默认值漂移 | 布局与绘制 |
| 截图 | 像素级回归 | 没走到的页面状态、异常路径 |

---

## 1. A 组：零回归（最重要）

> 最新一次实测（commit `8d9e3e8d`）的逐条结论见 **§1.0**；下表是判据与验法，两者配合使用。

| 编号 | 判据 | 怎么验 | 证据形式 | 谁来验 |
|---|---|---|---|---|
| A1 | **编译门禁**：52 处无 `else` 的 `when` 必须逐个显式分派；`assembleRelease` 必须绿 | `./gradlew :app:compileDebugKotlin`；失败日志按文件聚合，与 §6.2 扫描器输出比对 | 失败日志（文件:行）+ 最终绿构建 | **CI**（`assembleRelease`） |
| A2 | 52 处 `when` 所在文件里，**分支体不得改动**；允许的删除只有三类：① 分支条件行追加 `, UiMode.Themed`；② D3 的 `== UiMode.Miuix` → `isMiuixLook`；③ S3/S4 的索引映射改造。除此之外任何删除行都算改动 | `git diff <base>..<head> -- <含 when 的文件>` 取出全部 `-` 行逐行归类（条件行 / 上述三处例外 / 其余即不通过） | 归类清单 | **静态**（reviewer） |
| A3 | 谓词**逐点等价**：`isMiuixFamily` / `isMiuixLook` 对 Miuix、MiuixStock、Material 的返回值与改动前完全一致；23 处 `isMiuixFamily` 调用点不改语义 | §6.1 check 5（谓词定义文本必须是那 3 个 `this == UiMode.*`）+ PR 人工核对 §1.2 真值表；`git diff` 只允许追加 | 命令输出 + 评审记录 | **静态（PR 人工 + 代码审查）** |
| A4 | **4 个静默点**必须显式处理并有单测（§1.3） | 单测；`grep -A3 "onSetUiModeIndex"` 必须出现 `entries` | 单测输出 | **CI / 静态** |
| A5 | `LocalThemeTokens` 的**实际读取**不得出现在 `Miuix*` / `Material*` 分支 | `grep -rn "LocalThemeTokens\.current\|provides LocalThemeTokens" $SRC \| grep -v "/ui/theme/"` 必须为空 | 空输出 | **静态 / CI 卡口** |
| A6 | 每个 token 化组件：`ThemeTokens()` 默认值 == 迁移前那处字面量 | **不用跑测试**：§6.1 check 6 从 `ThemeTokens.kt` 读出 9 个默认值，与 §1.6 基线表逐行对拍；组件 diff 必须是「字面量搬进 token、不换值」 | 对拍输出 + diff | **静态（PR 人工）** |
| A7 | `uiPrefix()` 对前三个值仍返回 `material_` / `miuix_stock_` / `miuix_` | 单测三值 + diff 确认三行未改；**Themed 的前缀必须拍板**（见 S2） | 单测 + diff | **CI / 静态** |
| A8 | 老皮肤不读主题包、不因主题包存在改变启动路径 | review `MainActivity` 的 mode→theme 分支；`grep -rn "theme.json\|ThemeStore" $SRC \| grep -v "/theme/"` 只允许出现在设置入口与 Themed 分支 | review 记录 | **静态** |
| A9 | 三皮肤 × 6 页截图：裁剪后 `AE=0`（唯一例外：选择器第 4 项）；**且 Themed × 6 页 == 同页 Miuix 的 `AE=0`（L1a 核心断言）** | §1.7 执行手册 + §3.3 截图流程 | 成对 PNG + 差异报告 | **⏳ 待设备接入**（见 §1.7） |
| A10 | `fromValue` 四值 round-trip；`entries.size == 4` 且前三项顺序不变；不依赖 `ordinal` | §6.1 check 4（枚举项 + 四个 `.value ->`）+ check 7（`UiMode.*ordinal` 必须为空） | 命令输出 | **静态** |

### 1.0 A 组实测状态（commit `8d9e3e8d`，2026-10-31）

| 编号 | 结论 | 证据 |
|---|---|---|
| A1 | ✅ **过**（静态部分已验；构建由 CI 确认） | `uimode-scan.py`：`when 块 53 / 无 else 52 / 含 Themed 52` —— 52 个编译器强制点全部分派；唯一未含 Themed 的是 `uiPrefix`（带 `else`，按 S2 定案保持）。构建绿由 CI 的 Build Manager 确认（本地无 Android SDK，未复跑） |
| A2 | ✅ **过**（按本节口径） | 该范围内 49 行删除全部可归因：36 行分支条件扩展、1 行 D3 改 `isMiuixLook`、12 行 S3/S4 索引映射改造；**无一行改动分支体** |
| A3 | ✅ **过** | `isMiuixFamily` = 三个 `this ==`；`isMiuixLook` = Miuix + Themed；`UiMode.kt` 的 diff 仅同行扩写，三个老值的返回值未变（§1.2） |
| A4 | ✅ **过** | `SettingsScreen.kt:61-64`、`ColorPaletteScreen.kt:44-47` 均为 `UiMode.entries.getOrNull(index)?.let { … }` |
| A5 | ✅ **过** | `LocalThemeTokens` 全仓 4 处：定义 1（`ThemeTokens.kt:81`）、KDoc 2、`provides` 1（`MiuixTheme.kt:142`，且被 `if (LocalUiMode.current == UiMode.Themed)` 包住）；**`.current` 读取 0 处**（本期预期） |
| A6 | ✅ **过** | `ThemeTokens.kt` 9 个默认值与 §1.6 基线逐行一致：`16 / 0 / 0 / 56 / 12 / 12 / Docked / true / DEFAULT_SECTION_ORDER` |
| A7 | ✅ **过** | `uiPrefix()` 与父提交 `fe9c7a9e` **零差异**；Themed 走 `else -> "miuix_"`（S2 定案）；前三个前缀未变 |
| A8 | ✅ **过** | `grep -rn "theme.json\|ThemeStore\|ThemePackage" src \| grep -v "/ui/theme/"` 为空：主题包读取只存在于 `ui/theme/` |
| A9 | ⏳ **待设备接入** | 设备 shell 通道（adb-shell/root/Shizuku）未开，`pm install` 不可用、虚拟屏只能跑已装应用，无法装两个版本做对比。开跑清单与导航见 §1.7 |
| A10 | ✅ **过** | 枚举 4 项且 Themed 最后、`fromValue` 四值齐全、`UiMode.*ordinal` 零引用 |

> 复跑方式：§6.1 九条断言 + §6.2 扫描器。本次原始输出：九条断言全 PASS；扫描器 `53 / 52 / 52 / 0 / 9 / 12`。

### 1.1 结构事实（审计目标；数字用 §6.2 重跑）

| 事实 | 数字 | 说明 |
|---|---|---|
| 引用 `UiMode` 的文件 | 63 | |
| 含 `UiMode` 分支的 `when` 块 | 53 | 其中 52 个无 `else`（编译器强制），1 个带 `else`（`uiPrefix`） |
| 编译器强制的 `when` | 52 | 新增枚举值 → 编译错误点，逐个补分支 |
| `isMiuixFamily` 调用点 | 23 | 39 行引用 = 15 import + 1 定义 + 23 调用 |
| `UiMode.entries` 选择器 | 4 处 × 2 行 | 唯一允许的可见变化来源 |
| `== UiMode.Material` 决策点 | 7 | Themed 恒 false，天然安全（不必改） |
| `== UiMode.Miuix` 改造 | 7 → 0 | 7 处全部改成 `isMiuixLook`（见 §1.4） |
| `when` 已显式含 Themed | 52 / 53 | 唯一未含的是 `uiPrefix`（S2） |

### 1.2 谓词真值表（A3 判据落地）

**规则**：任何新增/修改的 `UiMode` 谓词，对三个老值的返回值必须与改动前相同。

| 谓词 | Miuix | MiuixStock | Material | Themed | 对老皮肤 |
|---|---|---|---|---|---|
| `isMiuixFamily` | true | true | false | true | ✅ 前三个与改前一致（等价于旧版） |
| `isMiuixLook`（新增） | true | false | false | true | ✅ 逐点等价于旧的 `== UiMode.Miuix` |
| `== UiMode.Miuix`（改造前） | true | false | false | false | 基线 |

> 单测：`listOf(Miuix, MiuixStock, Material, Themed).forEach { … }` 断言上表；任何一格变了就红。

### 1.3 四个"编译器管不到"的静默点（**逐条勾**）

| # | 位置 | 处理 | 不改的后果 | 现状 |
|---|---|---|---|---|
| S1 | `ui/UiMode.kt` `fromValue` | 加 `Themed.value -> Themed` | Themed 存得住读不回 → 重启变 Miuix | ✅ 已改 |
| S2 | `SettingsRepositoryImpl.kt:76` `uiPrefix` | **已定（2026-10-31）：共享 `miuix_`**，`else -> "miuix_"` 保持不变，**不得再改** | Themed 与 Miuix 共用壁纸/底栏等偏好：切过去保留、切回来不丢 | ✅ **已定，不得再改** |
| S3 | `SettingsScreen.kt:62` | `UiMode.entries.getOrNull(index)` | 第 4 项落到 Material → **选 Themed 实际切 Material** | ✅ 已改 |
| S4 | `ColorPaletteScreen.kt:45` | 同上 | 同上 | ✅ 已改 |

> S2 已定：**Themed 共享 `miuix_`**。两条边界写死：
> ① **主题包自己的背景/配色属于主题状态，单独存，不写进 `uiPrefix()` 这批 key**；
> ② `MiuixStock` 用的是独立前缀 `miuix_stock_`，所以「共享」只发生在 **Miuix 与 Themed** 之间。
> **回归项（A7）**：前三个前缀取值不得变（`material_` / `miuix_stock_` / `miuix_`），且不得新增 `Themed.value ->` 分支（§6.1 check 5）。

### 1.4 七个 `== UiMode.Miuix` 决策点（**逐条确认，不是改完就算**）

当前实现把这 7 处统一改成 `isMiuixLook`（对 Themed 返回 true）。对老皮肤逐点等价（✅）。

**已确认（2026-10-31）：D1–D6 全部按 Miuix 语义执行，无例外；D7 保留分支但加一条优先级例外。**
逐条勾选记录如下（这份表就是 PR 的 checklist，勾了才算完成）：

| # | 位置 | Themed 的行为 | 确认结论 |
|---|---|---|---|
| D1 | `MainActivity.kt:231` | 把"有壁纸"当深色/文本色输入（`withWallpaperMode`） | ✅ 照 Miuix 走 |
| D2 | `MainActivity.kt:257` | 隐藏系统状态栏 | ✅ 照 Miuix 走 |
| D3 | `BottomBar.kt:123` | 走非 split-pane 判定 | ✅ 照 Miuix 走 |
| D4 | `FlashUtils.kt:344` | 用 Miuix `OverlayDialog` | ✅ 照 Miuix 走 |
| D5 | `ModuleMiuix.kt:782` | 给动作图标上色 | ✅ 照 Miuix 走 |
| D6 | `ModuleRepoMiuix.kt:1302` | 同上 | ✅ 照 Miuix 走 |
| D7 | `theme/MiuixTheme.kt:89` | 有壁纸时提亮文本分支**保留**；**但主题显式给了 `roles.onSurface` / `onBackground` / `onSurfaceVariantSummary` 时，主题优先、跳过提亮**；没给才提亮 | ✅ 保留 + 例外 |

> **D7 例外是 B9 同一套优先级**：Themed 下 `主题 roles` > `主题 seed` > `设置里的 keyColor/Monet`。
> 也就是说：主题**显式**给了某个颜色角色就用它（不做任何"提亮/混色"补偿），没给才回落到 seed 生成、再回落到设置。
>
> 其余约束：**不得改 `isMiuixFamily`**（会波及 23 个调用点）；要改某处语义，用更精确的谓词，改回 `== UiMode.Miuix` 也可以。

### 1.5 选择器与枚举顺序（A10 详情）

| # | 位置 | 现状 | 加 Themed 后 |
|---|---|---|---|
| E1 | `SettingsMiuix.kt:142/151` | `items = UiMode.entries.map { displayName }` + `indexOfFirst { value == uiMode }` | 自动多第 4 项；`indexOfFirst` 正常 |
| E2 | `SettingsMiuixStock.kt:156/165` | 同上 | 同上 |
| E3 | `SettingsMaterial.kt:117/118` | 同上 | 同上 |
| E4 | `ColorPaletteScreenMiuix.kt:252/261` | 同上 | 同上 |

结论：选择器不用改（这是唯一允许的可见变化）；但 S3/S4 已改成 `entries` 派生，**新增枚举值不再需要手改索引**——这条要作为回归项保留（§6.1 check 2）。

### 1.6 token 默认值基线（A6 对拍用，不需要跑测试）

`ThemeTokens` 的默认值必须等于 Miuix 迁移前的硬编码值。下表是基线，`§6.1 check 6` 直接从
`ui/theme/ThemeTokens.kt` 读出来逐行对拍；**默认值一改，老皮肤就会漂**。

| 字段 | 默认值 | 迁移前出处（已抽样核对） |
|---|---|---|
| `cardRadius` | `16.dp` | `HomeMiuix.kt` `RoundedCornerShape(16.dp)`（卡片 / 守卫卡） |
| `cardBorderWidth` | `0.dp` | 现有卡片都不画边框（`WarningCard` 用填充色） |
| `cardElevation` | `0.dp` | 卡片平铺，唯一阴影属于越狱守卫卡、不是共享卡片样式 |
| `rowMinHeight` | `56.dp` | Miuix/M3 库行高固定 56.dp；token 只作用于自绘行 |
| `pagePadding` | `12.dp` | `SettingsMiuix.kt` `.padding(horizontal = 12.dp)` |
| `sectionGap` | `12.dp` | `SettingsMiuix.kt` `SmallTitle` `padding(top = 12.dp)` |
| `bottomBarStyle` | `BottomBarStyle.Docked` | `enableFloatingBottomBar` 默认 false |
| `bottomBarShowLabels` | `true` | `BottomBarMiuix` 每个图标下有文字标签 |
| `sectionOrder` | `general, appearance, features, privacy, about, danger` | `SettingsMiuix` 的分节顺序 |

> 现阶段的额外事实：`Themed` **目前原样提供 `ThemeTokens()`**（L2 还没接），所以 Themed 与 Miuix 逐像素一致——
> 这意味着 A9 的三皮肤截图里，Themed 可以拿来当「Miuix 无变化」的旁证。

> **TODO（L2 完成后）**：新增 CI step `./gradlew :app:testDebugUnitTest`，并把 A3（谓词真值表）、
> A6（token 默认值 golden）、A10（`fromValue` round-trip）升级成真单测。
> **本期明确不加测试基础设施**（会引入 Robolectric/JUnit 依赖与构建时间，属另一件事），
> 在此之前这三条按「PR 人工 + §6.1 grep」执行。

### 1.7 A9 执行手册（⏳ 待设备接入）

**现在为什么不能跑**：这台机器的设备 shell 通道（adb-shell / root / Shizuku）都未开启，`pm install` 走不通；
虚拟屏只能运行**已安装**的应用，装不了新包。等通道可用后按本手册一次跑完，不用再想。

**开跑前要准备好的东西**（缺一项就别开跑，否则截图没法对比）

| # | 准备项 | 具体要求 |
|---|---|---|
| P1 | 基线 APK | 父提交 `fe9c7a9e` 构建：`git worktree add /tmp/base fe9c7a9e` → `cd /tmp/base/manager && ./gradlew :app:assembleDebug` |
| P2 | 被测 APK | 当前 `8d9e3e8d` 构建；**同一签名、同一 ABI** |
| P3 | 设备 | 同一台；记录型号、`wm size`、`wm density`、Android 版本 |
| P4 | 系统设置 | `font_scale 1.0`、三个动画 scale 0、系统深浅固定、飞行模式（命令见 §3.3） |
| P5 | 页面入口 | 首页 / 模块 / 设置 / **模块仓库（模块页顶部入口）** / **关于（设置页底部）** / **主题页（设置 → 外观 → ColorPalette）** |
| P6 | 比对工具 | `AE` + 差异包围盒：ImageMagick `compare -metric AE` 或 Python+Pillow |
| P7 | 裁剪规则 | 裁掉状态栏与手势条；**把样式选择器那一行单独裁掉**（唯一允许的差异：多第 4 项） |

**核心断言（L1a）**

- `Themed` 目前原样 provide `ThemeTokens()`（§1.6 末），因此 **Themed × 6 页的截图必须与同页 Miuix 的截图 `AE=0`**。
- 这条比"三个老皮肤各自没变"更早暴露问题：一旦不为 0，说明 L1 注入链路动了不该动的东西，
  或者 Themed 意外走到了非 Miuix 的分支（例如 §1.4 的某个决策点）。
- **适用边界**：该断言只在 **L2 token 真正接上之前**成立。L2 落地后，它会自然变成
  "Themed 与 Miuix 的差异必须 ⊆ 已 token 化的组件包围盒"，那时改判据，不要放宽成"随便不一样"。

**执行顺序**

1. 装 P1 基线包 → 3 皮肤 × 6 页 = 18 张 → `baseline/`
2. 装 P2 被测包 → 同样 3 皮肤 × 6 页 = 18 张（先不截 Themed）→ `after/`
3. 逐对算 `AE`：`Miuix / MiuixStock / Material` 的 `baseline` vs `after` 必须全 0
4. 被测包里切到 `Themed` → 6 张 → 与 `after/` 里同页 Miuix 比，必须全 0
5. 填记录表；任何一格非 0，先判断是不是"选择器没裁干净"，再判断是不是真回归

**记录模板**

| 页面 | Miuix（基线 vs 后） | MiuixStock | Material | Themed vs Miuix（后） |
|---|---|---|---|---|
| 首页 | AE = ? | AE = ? | AE = ? | AE = ? |
| 模块 | | | | |
| 设置 | | | | |
| 模块仓库 | | | | |
| 关于 | | | | |
| 主题页 | | | | |

> 设备通道开通后，把这份表填满贴 PR，A9 才算结。

---

## 2. B 组：主题生效（L1）

前置：准备 2 个差异明显的测试包（`themeA` 深色 + 小圆角 + 衬线；`themeB` 浅色 + 大圆角 + 无衬线）。

| 编号 | 判据 | 怎么验 | 证据 | 谁来验 |
|---|---|---|---|---|
| B1 | `color.seed` 生效：主色/容器色随 seed 变，53 个角色都有值（无透明块、无纯黑块） | 导入 A → 首页/主题页截图；换 B 对比 | 两套截图 + 取色值 | 真机 |
| B2 | `color.roles` 逐角色覆盖：只覆盖 `primary` 时其余由 seed 补齐、不塌陷 | 只写 `primary` 的包，检查按钮/滑块/选中态 | 截图 + 日志 | 真机 |
| B3 | `dark` / `amoled`：切深色生效；AMOLED 背景像素为 `#000000` | 三模式各一张，取背景像素 | 截图 + 像素值 | 真机 |
| B4 | 字体：14 样式可分别覆盖字号/字重/行高；自定义 ttf 生效；未覆盖样式回落默认 | 只改 `title1`/`body1` 的包 | 截图（局部放大） | 真机 |
| B5 | 背景：每页可不同；`opacity`/`dim`/`blur` 三参数各自可辨 | 首页与设置页不同背景，逐参数改各一张 | 4 张截图 | 真机 |
| B6 | **只在 Themed 生效**：切回 Miuix/MiuixStock/Material 后颜色/字体/背景全部恢复 | 切回后按 A9 对比 `AE=0` | 截图 | 真机 |
| B7 | 持久化：杀进程、重启后仍是 Themed + 同一主题包 | `am force-stop` → 重开；再重启 | 两次截图 | 真机 |
| B8 | A→B 无残留：先 A 再 B，B 的截图里不得有 A 的像素 | 连续切换后与"直接进 B"对比 `AE=0` | 两组截图 | 真机 |
| B9 | 优先级**已定**：Themed 下 `主题 roles` > `主题 seed` > `设置里的 keyColor/Monet`；显式给了文字角色就跳过提亮（§1.4 D7） | 测 4 种组合：roles+keyColor / 仅 seed+keyColor / 仅 roles / 都没给 | 4 张截图 + 取色值 | **已定口径 + 真机** |

---

## 3. C 组：L2 token（组件逐个迁移）

### 3.1 组件矩阵（本期范围）

卡片 / 列表行 / section 标题 / 底栏 / 按钮 / 徽标 / 首页 hero —— 共 7 类。

每迁移一类交 **两组对比**：

| 对比 | 期望 | 判据 |
|---|---|---|
| C1：Miuix 迁移前 vs 迁移后 | 完全一致 | 裁剪后 `AE=0` |
| C2：Themed-A vs Themed-B | 该组件随主题变化 | 组件包围盒内变化像素 > 0，盒外 ≈ 0 |
| C3（推荐）：Themed-A vs Miuix | 有可见差异 | 证明 token 真走了 Themed 值 |

| 编号 | 判据 | 怎么验 | 证据 | 谁来验 |
|---|---|---|---|---|
| C1 | 老皮肤零变化（同 A9，按组件裁剪） | §3.3，只裁该组件 | 两张 PNG + `AE` | 真机 |
| C2 | Themed 下该组件跟随主题 | A/B 两包各一张 | 两张 PNG + 差异包围盒 | 真机 |
| C3 | token 默认值 == 旧字面量 | 单测 golden + diff | 单测输出 | CI（需 step） |
| C4 | 未迁移组件不崩、不闪烁、无 `CompositionLocal` 缺失 | 迁移 1 个后跑全页面 smoke（6 页） | 录屏 / 无 crash 日志 | 真机 |
| C5 | 同一 token 多处表现一致（如 `cardRadius` 同时作用于首页与设置卡片） | 两处同屏截图 | 截图 | 真机 |

### 3.3 统一截图流程（可复现）

**设备与设置（每次固定）**

```bash
DEV=<序列号>
adb -s $DEV shell settings put system font_scale 1.0
adb -s $DEV shell settings put global window_animation_scale 0
adb -s $DEV shell settings put global transition_animation_scale 0
adb -s $DEV shell settings put global animator_duration_scale 0
adb -s $DEV shell cmd uimode night no          # 需要深色时改 yes
adb -s $DEV shell svc wifi disable; adb -s $DEV shell svc data disable   # 去通知/网速干扰
```

**基线**：从主题 PR 的父提交构建，或直接用上一版已发布 APK。

```bash
git worktree add /tmp/base <父提交>
(cd /tmp/base/manager && ./gradlew :app:assembleDebug)
```

**每模式每页**：

```bash
snap() {
  adb -s $DEV shell am force-stop me.diksu.ultra
  adb -s $DEV shell am start -n me.diksu.ultra/.ui.MainActivity >/dev/null
  sleep 2
  adb -s $DEV exec-out screencap -p > "$1"
}
```

**页面集合（6 页）**：首页 / 模块 / 设置 / 模块仓库 / 关于 / 主题页。
**裁剪**：去掉状态栏与手势条（按设备实测固定值）；可滚动页面固定滚到顶部。
**比对**：`AE`（非零像素数）+ 差异包围盒；老皮肤要求 `AE=0`，唯一例外是选择器第 4 项（单独裁掉该行再比）。

---

## 4. D 组：失败路径（坏包不能崩）

准备 6 个最小样例包：坏 zip、缺 `theme.json`、超大、非法颜色、缺字体、`../` 路径。

| 编号 | 判据 | 怎么验 | 证据 | 谁来验 |
|---|---|---|---|---|
| D1 | 非 zip / 坏 zip / 缺 `theme.json` → 拒绝导入 + 说明原因 + **不崩溃** | 依次导入 3 个包，看对话框与 `logcat` 无 `FATAL` | 截图 + logcat | 真机（CI 覆盖解析器单测） |
| D2 | 超大：单文件 >8 MiB / 总量 >64 MiB / 条目 >64 → 拒绝并说明是哪一条 | 各构造一个超限包 | 提示截图 | CI（预算单测）+ 真机 |
| D3 | 非法颜色（`#GGGGGG`、`red`、`#12345`）→ 该字段回退默认，其余字段照常生效 | 只坏一个颜色字段 | 截图 + "已忽略"日志 | CI（解析器单测） |
| D4 | 缺字体文件 → 字体回退系统，**其他字段仍生效**（不能整包失败） | 引用不存在的 `font.ttf` | 截图 | 真机 |
| D5 | zip slip（条目含 `../`）→ 拒绝，且不得写到应用私有目录之外 | 构造 `../evil`，导入后检查路径不存在 | 文件系统检查 | CI（解压单测）+ 真机 |
| D6 | 未知字段 → 忽略 + 记日志，**不报错** | 加 `"foo": 1` | 日志 + 正常导入 | CI（解析器单测） |
| D7 | 导入失败不留半个主题：当前生效主题不变，列表不出现坏包 | 导入 D1/D2 后再看列表与当前主题 | 截图 | 真机 |
| D8 | 导入预览两栏：**将应用 N 项 / 未应用 M 项**，未应用项列名字 | 用真实 FolkPatch 包导入 | 预览截图 | 真机 |

> 硬要求：8 条都属"不崩 + 回退 + 如实告知"。**任何一条崩溃即阻断发布。**

---

## 5. 本期不验（写清楚，避免被当成遗漏）

| 项 | 为什么不验 | 何时再验 |
|---|---|---|
| L3 布局变体（首页布局、模块网格、section 顺序） | 本期不做（§8 拍板暂缓） | 下期定方案后 |
| L4 动效/音效/振动/视频背景 | 本期不做 | L4 期 |
| L5 应用内编辑器 | 本期不做 | L4 之后评估 |
| Miuix 库内置组件的圆角/内边距/行高 | 库不开放（§0），**改不了，不承诺** | 除非换库/自绘 |
| FolkPatch 音乐/音效/布局字段实际生效 | 本期只"原样保留 + 预览列未应用" | L4 / L3 |

---

## 6. 一键检查

### 6.1 `check-theme.sh`（9 条断言 + 2 条统计）

```bash
#!/usr/bin/env bash
set -u
SRC=manager/app/src/main/java
UI=$SRC/me/diksu/ultra/ui/UiMode.kt
fail=0
ok()  { echo "PASS  $1"; }
bad() { echo "FAIL  $1"; fail=1; }

# 1) LocalThemeTokens 的"实际读取"只允许在 theme 包（KDoc 里提到不算）
if grep -rn "LocalThemeTokens\.current\|provides LocalThemeTokens" "$SRC" | grep -qv "/ui/theme/"; then
  bad "LocalThemeTokens 泄漏到老分支"
else ok "LocalThemeTokens 未泄漏"; fi

# 2) 索引映射必须由 entries 派生（S3/S4 的回归项）
if grep -A3 "onSetUiModeIndex = {" "$SRC/me/diksu/ultra/ui/screen/settings/SettingsScreen.kt" \
   | grep -q "entries"; then
  ok "索引映射由 entries 派生"
else bad "索引映射又变回手写"; fi

# 3) 谓词：isMiuixFamily 含 Themed；isMiuixLook 存在
if grep -q "this == UiMode.Themed" "$UI" && grep -q "isMiuixLook" "$UI"; then
  ok "谓词已含 Themed"
else bad "谓词未含 Themed / 缺 isMiuixLook"; fi

# 4) 枚举 4 项 + fromValue 四个值都有分支
n=$(grep -cE '^[[:space:]]+[A-Za-z]+[(]"' "$UI")
missing=""
for v in MiuixStock Material Themed; do
  grep -q "${v}\.value -> " "$UI" || missing="$missing $v"
done
grep -qE "(Miuix\.value -> |else -> Miuix)" "$UI" || missing="$missing Miuix"
if [ "$n" -eq 4 ] && [ -z "$missing" ] && grep -q 'Themed("themed"' "$UI"; then
  ok "枚举 4 项、Themed 存在、fromValue 覆盖 4 值"
else bad "枚举/fromValue 不完整（entries=$n 缺分支:${missing:-无}）"; fi

# 5) uiPrefix：Themed 共享 miuix_（已定，不得再改）
PREFIX=$SRC/me/diksu/ultra/data/repository/SettingsRepositoryImpl.kt
if grep -A4 "private fun uiPrefix" "$PREFIX" | grep -q 'else -> "miuix_"' \
   && ! grep -q 'Themed.value -> "themed_"' "$PREFIX"; then
  ok "uiPrefix 共享 miuix_（已定口径）"
else bad "uiPrefix 被改动：Themed 必须走 else -> miuix_"; fi

# 6) 谓词定义（归一化后精确匹配，防止有人改回窄条件）
norm=$(tr -d ' \n' < "$UI")
case "$norm" in
  *"this==UiMode.Miuix||this==UiMode.MiuixStock||this==UiMode.Themed"*)
    ok "isMiuixFamily 定义正确" ;;
  *) bad "isMiuixFamily 定义被改动" ;;
esac
case "$norm" in
  *"this==UiMode.Miuix||this==UiMode.Themed"*)
    ok "isMiuixLook 定义正确" ;;
  *) bad "isMiuixLook 定义被改动" ;;
esac

# 7) token 默认值 == §1.6 基线（不用跑测试）
TOK=$SRC/me/diksu/ultra/ui/theme/ThemeTokens.kt
miss=""
for kv in cardRadius=16 cardBorderWidth=0 cardElevation=0 rowMinHeight=56 pagePadding=12 sectionGap=12; do
  f=${kv%%=*}; v=${kv##*=}
  grep -qE "val ${f}: Dp = ${v}\.dp" "$TOK" || miss="$miss $f"
done
grep -qE 'val bottomBarStyle: BottomBarStyle = BottomBarStyle\.Docked' "$TOK" || miss="$miss bottomBarStyle"
grep -qE 'val bottomBarShowLabels: Boolean = true' "$TOK" || miss="$miss bottomBarShowLabels"
if [ -z "$miss" ]; then ok "token 默认值 == §1.6 基线"; else bad "token 默认值漂移:$miss"; fi

# 8) 不依赖 UiMode 的 ordinal
if grep -rn "UiMode.*ordinal" "$SRC" >/dev/null; then
  bad "有代码依赖 UiMode 的 ordinal"
else ok "无 UiMode.ordinal 依赖"; fi

echo "—— 统计（与 §1.1 对账）——"
echo "when (LocalUiMode.current): $(grep -rn "when (LocalUiMode.current)" "$SRC" | wc -l)"
echo "when 块内已出现 Themed 的文件数: $(grep -rln "UiMode.Themed" "$SRC" | wc -l)"
exit $fail
```

### 6.2 `uimode-scan.py`（结构审计：输出 §1.1 的全部数字）

```python
#!/usr/bin/env python3
"""统计 UiMode 的 when 块 / 谓词 / 选择器调用点；输出即 §1.1 的数字。"""
import os, re, sys
ROOT = sys.argv[1] if len(sys.argv) > 1 else "manager/app/src/main/java"
whens, eq_miuix, looks, entries = [], [], [], []
for dp, _, fs in os.walk(ROOT):
    for fn in fs:
        if not fn.endswith(".kt"):
            continue
        p = os.path.join(dp, fn)
        src = open(p, encoding="utf-8").read()
        for m in re.finditer(r"\bwhen\s*\(", src):
            i = m.end() - 1; d = 0; j = i
            while j < len(src):
                if src[j] == "(": d += 1
                elif src[j] == ")":
                    d -= 1
                    if d == 0: break
                j += 1
            k = src.find("{", j)
            if k < 0: continue
            d = 0; end = k
            while end < len(src):
                if src[end] == "{": d += 1
                elif src[end] == "}":
                    d -= 1
                    if d == 0: break
                end += 1
            body = src[k:end + 1]
            if "UiMode." in body:
                whens.append((p, src[:m.start()].count("\n") + 1,
                              "Themed" in body,
                              bool(re.search(r"(^|\n)\s*else\s*->", body))))
        for i, l in enumerate(src.split("\n"), 1):
            if re.search(r"==\s*UiMode\.Miuix\b", l) and "UiMode.kt" not in p:
                eq_miuix.append((p, i))
            if "isMiuixLook" in l and not l.strip().startswith("import"):
                looks.append((p, i))
            if "UiMode.entries" in l:
                entries.append((p, i))
print(f"when 块(UiMode): {len(whens)}  无 else: {sum(1 for w in whens if not w[3])}"
      f"  含 Themed: {sum(1 for w in whens if w[2])}")
print(f"== UiMode.Miuix 剩余: {len(eq_miuix)}   isMiuixLook 引用: {len(looks)}"
      f"   UiMode.entries 使用: {len(entries)}")
for w in whens:
    if not w[2]:
        print(f"  ! 未含 Themed: {w[0]}:{w[1]}")
```

**期望**：`check-theme.sh` 九项全 PASS。`uimode-scan.py` 写作时输出：
`when 块 53（无 else 52，已含 Themed 52）；== UiMode.Miuix 剩余 0；isMiuixLook 引用 9 行；UiMode.entries 使用 12 行`。
唯一未含 Themed 的 `when` 是 `SettingsRepositoryImpl.kt:76`（`uiPrefix`），即 S2，属待拍板项。

---

## 7. 判定、归档与豁免

1. **通过**：A 组全绿 + B/C/D 对应条目有证据；**A 组任何一条不过 → 不允许合并**。
2. **证据放哪**：截图成对命名 `<模式>_<页面>_<前/后>.png`，与 `AE` 数字一起贴 PR；命令输出贴 PR 评论。
3. **豁免**：只有"选择器第 4 项"可豁免像素比对，且必须在 PR 里贴截图并注明范围。
4. **基线**：`git worktree` 构建父提交或固定用上一版 APK；**不要**用"看起来一样"当证据。
5. **与方案文档冲突时**：以 `theme-freedom-plan.md` 为准，并回来改本文。
