# 自定义主题 · 自由度方案（Themed 风格）

> 承接 [`theme-port-plan.md`](theme-port-plan.md) 的结论：主题包 = 第 4 个 UI 风格 `Themed`，
> 只在选中该风格时生效，老皮肤零改动。本文回答"这个风格里能放多自由"。
>
> 结论先给：**能改到很高，但有一条硬边界——Miuix 组件库只开放颜色和字体，不开放圆角/内边距。**

---

## 0. 硬边界（先说清楚，避免承诺做不到的事）

我读了 Miuix 0.9.4 的源码（`miuix-ui-android-0.9.4-sources.jar`）：

```kotlin
@Composable fun MiuixTheme(controller: ThemeController, textStyles: TextStyles, content: ...)
@Composable fun MiuixTheme(colors: Colors, textStyles: TextStyles, content: ...)
```

- `Colors` 有 **53 个颜色角色**（primary/onPrimary/…/surfaceContainerHighest/outline/dividerLine/windowDimming…）；
- `TextStyles` 有 **14 种样式**（main/paragraph/body1/body2/button/footnote1-2/headline1-2/subtitle/title1-4）；
- **没有 `shapes`、没有 `dimens`**：Miuix 内置组件的圆角、内边距、行高、开关尺寸都是写死在库里的。

所以自由度分成两类：

| 对象 | 能改什么 | 天花板 |
|---|---|---|
| Miuix 库渲染的组件（开关、列表项、对话框、Picker…） | 53 个颜色角色 + 14 种文本样式 | **圆角/内边距/行高改不了** |
| 我们自己绘制的部分（卡片、首页 hero、底栏外壳、页面背景、布局） | 颜色、圆角、间距、透明度、模糊、阴影、图片、布局结构 | 基本没有上限（受我们的实现进度约束） |

要突破这条边界只有两条路：① 给 Miuix 提 PR 或换组件库；② `Themed` 自绘一套组件（等于重写所有页面）。
两条都列在 §5 的"长期选项"里，**这一版不做、也不承诺**。

---

## 1. 自由度分层

| 层 | 能改什么 | 主要落点 | 成本 | 建议 |
|---|---|---|---|---|
| **L1 视觉** | 53 个颜色角色逐个指定（不是只给一个种子色）；14 种文本样式（字号/字重/行高/字距/字体文件）；日/夜/AMOLED 三套；页面背景图（每页可不同）+ 透明度/暗化/模糊 | `MiuixTheme(colors, textStyles)` + 我们自己的背景层 | 中 | **一期必做** |
| **L2 组件外观** | 我们自己画的组件 token 化：卡片圆角/边框/阴影/背景图、列表行高与密度、section 标题样式、底栏形状（悬浮/贴底/是否显示文字）、按钮圆角、徽标形状 | 新增 `LocalThemeTokens`（见 §2） | 中（机械但量大） | **二期** |
| **L3 布局** | 首页布局变体（classic / focus / dashboard / stats）、卡片顺序与显隐、模块列表 grid/list、设置分组样式、每页独立背景（surface） | 我们自己的页面 composable | 大 | **三期**（先做 2 个布局） |
| **L4 动效与感官** | 页面转场、按压反馈、触感、音效、视频背景、音乐 | 我们自己的动画层 | 中 | 四期，可选 |
| **L5 编辑器** | 应用内实时调参（颜色/字体/圆角/背景/布局）→ 直接预览 → 导出成主题包 | 新页面 | 大（≈一个完整功能页） | 可选，但**最提升 DIY 体验** |

> L1+L2 做完，主题包已经能做出"完全不像原版"的观感；L3 才是"布局也换"。

---

## 2. 架构：token 层 + 默认值（保证老皮肤零改动）

核心是一个**所有字段都有默认值**的 token 对象，默认值 = 当前硬编码值：

```kotlin
@Immutable
data class ThemeTokens(
    // L2：我们自己画的组件
    val cardRadius: Dp = 20.dp,
    val cardBorderWidth: Dp = 0.dp,
    val cardElevation: Dp = 0.dp,
    val rowMinHeight: Dp = 56.dp,
    val pagePadding: Dp = 12.dp,
    val sectionGap: Dp = 20.dp,
    val bottomBarStyle: BottomBarStyle = BottomBarStyle.Docked,
    val bottomBarShowLabels: Boolean = true,
    // L3：布局
    val homeLayout: HomeLayout = HomeLayout.Classic,
    val moduleListStyle: ListStyle = ListStyle.Cards,
    val sectionOrder: List<String> = DEFAULT_SECTION_ORDER,
    // 背景
    val surfaces: Map<String, SurfaceStyle> = emptyMap(),
    // …
)

val LocalThemeTokens = compositionLocalOf { ThemeTokens() }   // 默认 = 现状
```

- 三个老皮肤（Miuix / MiuixStock / Material）**不 Provide** 它 → 组件读到的永远是默认值 →
  渲染结果与现在逐像素一致（这点作为验收项）；
- `Themed` 才 Provide 解析后的 token；
- 组件迁移是**增量**的：今天把 `ModuleCard` 改成读 token，明天改 `SettingsRow`，
  没迁移的组件继续用默认值，不会出现"改一半就崩"。

配色与字体走库的入口，不进 token：

```kotlin
MiuixTheme(
    colors = resolvedColors,        // 53 个角色，主题可逐个覆盖
    textStyles = resolvedTextStyles // 14 种样式 + 自定义字体
) { CompositionLocalProvider(LocalThemeTokens provides tokens) { content() } }
```

---

## 3. 主题包 manifest（schema v1）

在 FolkPatch/FinaSU 的 `theme.json` 基础上做超集，**保持能读他们的包**：

```jsonc
{
  "schema": 1,
  "meta": { "name": "...", "author": "...", "version": "1", "type": "phone" },

  // L1：配色。两种写法二选一：
  //   seed：给一个种子色，我们按 M3 规则生成 53 个角色（简单）
  //   roles：逐角色指定（自由，缺的角色用 seed 生成补齐）
  "color": {
    "seed": "#4355B9",
    "roles": { "primary": "#4355B9", "surfaceContainer": "#F3F4FA", "outline": "#C5C6D0" },
    "dark": { "seed": "#AEB8FF", "roles": {} },
    "amoled": { "background": "#000000" }
  },

  // L1：字体
  "font": {
    "mode": "custom",              // system | app | custom
    "file": "font.ttf",
    "styles": {                    // 可只覆盖部分
      "title1": { "size": 22, "weight": 600, "lineHeight": 28 },
      "body1":  { "size": 16, "weight": 400 }
    }
  },

  // L2：我们自己组件的 token
  "shape": { "cardRadius": 24, "buttonRadius": 14, "rowMinHeight": 60, "pagePadding": 14 },
  "effect": { "cardShadow": "none|soft|strong", "blurBackground": 0.0..1.0 },

  // L3：布局
  "layout": { "home": "classic|focus|dashboard|stats", "moduleList": "cards|grid",
              "bottomBar": "docked|floating", "bottomBarLabels": true,
              "sectionOrder": ["status", "info", "features", "susfs", "shizuku", "tools"] },

  // 每页背景（图片 + 参数）
  "surfaces": {
    "home":     { "day": "bg_home_day.webp", "night": "bg_home_night.webp", "opacity": 1.0, "dim": 0.15, "blur": 0.0 },
    "settings": { "day": "bg_settings_day.webp", "opacity": 1.0, "dim": 0.0 }
  },

  // 兼容：FolkPatch 原字段放这里，我们能映射的映射，不能的在导入预览里列"未应用"
  "folksu": { "homeLayoutStyle": "focus", "isMusicEnabled": true, "musicFilename": "music.mp3" }
}
```

**规则**
- 任何字段缺省 → 用默认值；**未知字段忽略但记日志**，不报错；
- 字段值非法（比如颜色不是 `#RRGGBB`）→ 该字段回退默认 + 记一条"已忽略"；
- 一个坏字段永远不能让应用崩，最坏是"这个主题看起来像默认"；
- 素材预算：单文件 ≤ 8 MiB、总量 ≤ 64 MiB、最多 64 个条目（比上游更紧，防 zip 炸弹）。

---

## 4. 与 FolkPatch / FinaSU 的适配

| 他们的字段 | 我们的位置 | 说明 |
|---|---|---|
| `customColor` / `folksu_keyColor` | `color.seed` | 种子色 |
| `nightModeEnabled` / `nightModeFollowSys` | `color.mode` | 深浅 |
| `isBackgroundEnabled` / `backgroundOpacity` / `backgroundDim` / `backgroundBlur` | `surfaces.home.*` | 背景 |
| `background_home` / `background_kernel` … | `surfaces.*` | 按 stem 命名映射 |
| `isFontEnabled` / `fontMode` / `font.ttf` | `font.*` | 字体 |
| `homeLayoutStyle` | `layout.home` | 我们只支持自己的布局名，其余**列"未应用"** |
| 音乐/音效/振动/视频 | `folksu` 原样保留 | 一期不应用（L4 再做） |
| `isGridWorkingCardBackgroundEnabled` 等 60+ 字段 | `folksu` 原样保留 | 一期不应用，不假装 |

导入预览必须显示两栏：**将应用 N 项 / 未应用 M 项（列出名称）**——这是"不做假"的具体落实。

---

## 5. 分阶段任务与验收

### 一期（L1）：约 4 个任务，可并行
1. `ThemeTokens` + `LocalThemeTokens` 骨架（默认值 = 现状）+ 三个老皮肤的"零改动"回归用例；
2. manifest 解析器（schema v1 + 缺省/非法值回退 + 未知字段日志）；
3. 容器（AES+ZIP 读写、预算校验、导入/导出/删除）；
4. `Themed` 风格：`UiMode.Themed` + 配色/字体/背景注入 + 风格选择项。

**验收**：导入一个真实 FolkPatch 包 → 颜色/字体/背景生效；切回 Miuix/Material → 与改动前逐像素一致（截图对比）；坏包不崩、预览列出未应用项。

### 二期（L2）：组件逐个 token 化
卡片 / 列表行 / section 标题 / 底栏 / 按钮 / 徽标，每迁移一个就是一个小 PR + 一张截图对比。
**验收**：同一组件在 Miuix 与 Themed 下分别截图，Miuix 侧无变化、Themed 侧随主题变化。

### 三期（L3）：布局变体
先做 2 个首页布局（classic / focus）+ 模块列表 grid/list + 每页背景。
**验收**：主题切布局后首页结构真的变；切回默认布局无残留。

### 四期（L4）：动效/音效/视频（可选）
**五期（L5）**：应用内编辑器（可选，工作量最大，但最提升 DIY）

---

## 6. 风险

| 风险 | 说明 | 对策 |
|---|---|---|
| token 化碰很多组件 | 逐个改容易漏、容易把老皮肤改坏 | 默认值 = 现状 + Miuix 侧截图回归作为每个 PR 的验收 |
| Miuix 限制 | 圆角/行高改不了 | 已在 §0 说明；不承诺 |
| 主题包过大 | 壁纸/字体解码吃内存 | 预算上限 + 按需解码 + 失败回退 |
| 主题作者体验 | JSON 写错很难调 | schema 版本 + 校验提示 + （五期）编辑器 |
| 维护成本 | 每加一个组件都要想 token | 只对"我们自己画的"组件加 token，库组件不折腾 |

---

## 7. 需要你拍板

1. **自由度做到哪一层**：L1（视觉）／L1+L2（组件外观）／再加上 L3（布局）？
   我的建议：**一期做 L1，二期 L2，L3 看效果再定**——因为 L3 的工作量是 L1+L2 的两倍以上。
2. **接受"Miuix 库组件的圆角/行高改不了"这个限制吗**？
   不接受的话只剩两条路：换组件库（大工程，会动所有页面）或 `Themed` 全自绘（更大）。
3. **应用内主题编辑器（L5）做不做**？它能把"DIY 自由度"从"改 JSON"变成"拖滑块"，
   但成本约等于一个完整功能页。

---

## 8. 拍板结果（2026-10-31）

| 问题 | 决定 |
|---|---|
| 自由度做到哪一层 | **本期做 L1 + L2**；L3（布局变体）暂缓 |
| Miuix 库组件圆角/行高改不了 | **接受**，按 §0 的边界执行，不为此换库/全自绘 |
| 应用内编辑器 L5 | 暂不做；**等 L4 做完再评估** |

### 据此确定的路线

1. **本期（L1+L2）**
   - L1：`Themed` 风格 + 53 角色配色 + 14 样式字体 + 日/夜/AMOLED + 每页背景（透明/暗化/模糊）；
   - L2：`LocalThemeTokens` 生效，把**我们自己画的组件**逐个 token 化：卡片、列表行、section 标题、
     底栏、按钮、徽标、首页 hero。
   - 老皮肤（Miuix / MiuixStock / Material）必须**逐像素不变**，作为每个 PR 的硬验收。
2. **下一期（L4）**：转场、按压反馈、触感、音效、视频背景。
3. **再之后**：L5 编辑器，看 L4 的效果再定。
4. **L3 暂缓**：布局变体成本最高，等 L1+L2 的实际观感出来再判断值不值得。

### 本期不做（写清楚，避免误会）

- 不改 Miuix 库内置组件的圆角/内边距/行高（做不到，见 §0）；
- 不做首页布局切换（L3）；
- 不应用 FolkPatch 包里的音乐/音效/振动/视频字段（L4 再做），导入预览里如实标"未应用"；
- 不做应用内编辑器（L5）。
