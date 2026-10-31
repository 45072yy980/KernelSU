# FoxSu 管理器 UI 移植方案（先方案、后动手）

> 目标：把 [`Cmry-mlc/FoxSu`](https://github.com/Cmry-mlc/FoxSu) 的管理器界面搬过来。
> 本文只做事实盘点和路线选择，**没有动一行代码**。
> 盘点基线：FoxSu `VERSION 3.0.54`（浅克隆 `--depth 1`）。

---

## 0. 结论先行

1. **两边的底座是同一个**：都源自 KernelSU，都用了同一套 UI 依赖（Miuix 0.9.4 / Material3 /
   materialkolor / appiconloader / commons-compress），Kotlin 2.4.20。所以"抄 UI"在技术上可行，
   不是换框架那种重写。
2. **但不能整包照搬**（选项 A）：FoxSu 的管理器是**另一个 App**（包名 `com.foxsu.manager`，
   315 个 UI 文件 / 8.3 万行），我们管理器里那批 FoxSu 没有的东西——越狱（late-load）流程、
   ghostlock/DirtyFrag、分区守卫的三级扫描设置与文案、MiuixStock 皮肤、自用签名与版本号规则——
   整包替换等于把这些全部重做一遍。
3. **路线已定：B（按皮肤移植）**，且范围缩小——`Miuix` / `Material` 两套我们本来就有，
   **只搬后三套：`Fresh` / `Glass` / `Nocturne`**。先搬"底栏 + 背景特效 + 配色/胶囊"这套与页面
   解耦的装饰层（最容易见效、也最容易回退），再逐套搬整页皮肤。我们的页面结构、越狱与守卫功能
   全部保留。
4. 许可证没问题：**两边都是 GPL-3.0**。移植要保留版权声明、在"关于"页写明 UI 来自 FoxSu。

---

## 1. 上游事实（源码级）

| 项 | 数值 |
|---|---|
| 许可证 | GPL-3.0（与我们相同） |
| 皮肤 | `UiMode`：Miuix / Material / **Fresh** / **Glass** / **Nocturne**（5 套，各自整站） |
| UI 代码量 | 315 个 `.kt`，约 8.3 万行（我们 290 个 / 5.96 万行） |
| 皮肤分派 | 全仓 26 处 `when (LocalUiMode.current)`（我们 52 处，因为三皮肤×更多页面） |
| 皮肤页面集 | `screen/ag` 24 文件（主皮肤）、`screen/nocturne` 14、`screen/editorial` 9、`screen/glass` 8 |
| 底栏系统 | `component/bottombar` 18 文件 / **5641 行**（21 胶囊样式 + 21 通栏结构 + 10 按钮造型） |
| 底栏特效 | `component/dockfx` **1133 行**（栏内 20 套 + 外圈 20 套） |
| 主题/配色 | `ui/theme` 16 文件 / 2009 行（含 `BgColorThemes` 528 行 = 20 套背景色主题） |
| 皮肤 token | `NcTokens` 491 行、`GlassPalette` 159、`NocturneMiuix` 197 |
| 每套皮肤体量 | Glass 8 文件 / 3418 行；Nocturne 14 文件 / 3781 行；Fresh = Material 变体（`HomeFresh` 1951 行 + 42 个文件里的 `isFreshStyle()` 分支） |
| 字符串 | `values/strings.xml` 740 行（我们 671），量级接近，合并可行 |

**页面骨架与我们同构**：`HomeScreen` 里也是 `state` + `actions` 参数 + `when (uiMode)` 选皮肤，
所以单页移植是"改 import + 适配 state 类型"，不是重写业务逻辑。

---

## 2. 两边差在哪（决定工作量的关键）

| 维度 | FoxSu | 我们 | 影响 |
|---|---|---|---|
| 皮肤 | 5 套：Miuix/Material/Fresh/Glass/Nocturne | 3 套：Miuix/MiuixStock/Material | 移植=加皮肤；`MiuixStock` 是我们独有的，FoxSu 没有对应物 |
| 数据层 | `me.weishu.kernelsu.data`（有自己的 settings/viewmodel 形状） | `me.diksu.ultra.data` | **131 个 UI 文件引用他们 data 层**，这部分必须适配而不是照搬 |
| 我们独有 | — | 越狱（late-load）、ghostlock/DirtyFrag、分区守卫三级扫描、自用签名 | 新皮肤里没有对应页面，需要保留现有实现 |
| 资源 | 1702 处 `R.string/drawable` 引用 | 自家 key | 需要合并字符串/图标，冲突时以我们的语义为准 |

---

## 3. 三种口径

| # | 做法 | 工作量 | 风险 | 收益 |
|---|---|---|---|---|
| **A 整包替换** | 用 FoxSu 的 manager 全量替换，再把越狱/守卫/签名接回来 | **极大**（等于重做我们的 fork 功能） | 高：我们的 jailbreak cpp、late-load 流程、守卫 UI 都要重写；两条线从此各走各的 | 外观 100% 一致 |
| **B 按皮肤移植**（推荐） | 逐套搬：底栏/特效 → Glass → Nocturne →（Editorial/Fresh 可选），我们的页面与功能不动 | **中高**（约 10–15 个 PR） | 中：每套皮肤是一组新页面，可分步上线、可单独回退 | 拿到 FoxSu 的核心观感与底栏/背景 DIY |
| **C 只搬装饰层** | 只搬 `bottombar` + `dockfx` + 背景特效 + 配色主题，页面结构不变 | **低中**（2–3 个 PR） | 低 | 底栏 21 种样式 + 特效 + 背景 DIY，但整页观感仍是我们的 |

**已定：B**（A 整包换会丢掉越狱/守卫，C 只搬装饰层达不到整页观感）。
B 的第一阶段就是 C，所以先做 M1 装饰层，再逐套搬整页。

---

## 4. 推荐路线（每步可独立上线 / 可回退）

### M1 装饰层（≈2–3 个 PR）
- 搬 `component/bottombar`（5641 行）+ `component/dockfx`（1133 行）+ `ui/theme/{BgColorThemes,GlassPalette,...}`；
- 皮肤选择器里加"底栏样式/底栏特效/背景色主题"三项设置（存进我们的 `SettingsRepositoryImpl`，
  用独立前缀，不污染现有 `miuix_*` 键）；
- 验收：三套老皮肤的默认外观**逐像素不变**；只有用户主动选样式才变。

### M2 Glass 皮肤（≈2–3 个 PR）
- 搬 `screen/glass`（8 文件 / 3418 行）+ `GlassPalette`（159 行）；接页面分派中我们有的那些；
- 验收：Glass 下首页/模块/设置/超管四个主页面可用；切回老皮肤无残留。

### M3 Nocturne 皮肤（≈3–4 个 PR）
- 搬 `screen/nocturne`（14 文件 / 3781 行）+ `NcTokens`（491 行）+ `NcUi`/`NcSheet` 基础设施；
- 这套页面最全（含主题弹窗、更新胶囊），工作量最大。

### M4 Fresh 皮肤（≈3–4 个 PR，**比名字看起来重**）
- Fresh 不是独立页面集，而是 **Material 的变体**：`HomeFresh` 1951 行 + 42 个文件里的
  `isFreshStyle()` 分支（`SearchBar`/`SegmentedList`/`TonalCard`/`ModuleMaterial`/
  `SettingsMaterial`/`SuperUserMaterial`/`AppProfileMaterial`…）；
- 也就是说它要**改我们现有的 Material 页面**（而不是新增文件），必须保证 Material 外观不变、
  只有选 Fresh 时才走白卡分支——这是三套里最容易碰坏老皮肤的一套，放最后做。

### M5 可选：DIY 能力
- 21 套主页背景特效、20 套底栏图标、卡面配图/配色、字体、标题、启动器图标——
  这些在 FoxSu 里散在 `AgCustomization`/`AgBgFxPicker`/`AgDockIcons` 等页面，
  建议放到 M2/M3 之后单独评估。

---

## 5. 移植规则（避免"搬完就烂"）

1. **不搬数据层**：只搬 `ui/**`。`ViewModel`/`state`/`repository` 一律适配我们的实现，
   FoxSu 的 `data` 包不进仓库——否则两套数据层会打架。
2. **一次一个文件，搬完就编译**：CI 是唯一编译器，禁止"搬 20 个文件再一起修"。
3. **我们的页面不套新皮肤**：越狱、分区守卫相关页面保持现有实现；新皮肤下这些页面
   走 Miuix 兜底（在 `when` 里显式写分支，不留空白）。
4. **保留版权**：文件头保留 FoxSu 的版权声明；`关于` 页与 README 注明
   "部分 UI 来自 FoxSu（GPL-3.0）"。
5. **默认外观不变**：所有新样式都是"用户主动选才生效"，默认值 = 现状。

---

## 6. 风险

| 风险 | 说明 | 对策 |
|---|---|---|
| 维护面爆炸 | 5 套皮肤 × 页面数，每次改功能要动 5 个 `when` | 只搬 2 套（Glass/Nocturne）；新皮肤页面各自独立文件，不共享分支 |
| 数据层不匹配 | 131 个 UI 文件引用他们的 data | 逐文件适配，先做状态映射层 |
| 资源冲突 | 1702 处 `R.*` 引用 | 冲突 key 以我们的语义为准，缺的补，命名加前缀 |
| 越狱页缺失 | FoxSu 没有 late-load 概念 | 新皮肤下越狱入口保持现有页面（显式兜底） |
| Fresh 改动面 | Fresh 是 Material 变体，要改现有 Material 页面 | 每个分支都用 `isFreshStyle()` 包住；Material 默认路径逐像素回归 |
| 上游漂移 | 我们和 FoxSu 会各自演进 | 记录移植基线 commit，后续合并手工进行 |
| 许可证 | 双方 GPL-3.0 | 保留声明 + 关于页注明，不分发闭源变体 |

---

## 7. 拍板结果与剩余问题

**已定**
1. 路线：**B 按皮肤移植**（不做 A 整包替换）。
2. 范围：**只搬 Fresh / Glass / Nocturne**；Miuix / Material 我们已有，不重复搬。
3. 顺序：M1 装饰层 → Glass → Nocturne → Fresh（Fresh 要改现有 Material 页面，风险最高，放最后）。

**还要你定的**
1. **先上 M1 装饰层吗**（底栏 21 种样式 + 40 种特效 + 20 套背景色主题）？这是最快看到"FoxSu 味"
   的一步，且与页面解耦、可单独回退。
2. **越狱/分区守卫页面**在新皮肤下保持现有 Miuix 外观，可以接受吗？（FoxSu 没有这些页面，
   硬要套新皮肤等于我们自己设计一套，成本另算。）
3. Glass / Nocturne 里，**主题弹窗、更新胶囊、作者页**这些次要页面要不要一起搬，还是先只搬
   首页/模块/设置/超管四个主页面？
