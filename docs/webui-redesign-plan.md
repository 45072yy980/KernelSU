# 网页端视觉重构方案（task-6）

> **状态**：方案稿，等 Lead 放行后执行。
> **基线**：`HEAD 1322ecac`（2026-10-24）阅读，`index.html` 4763 行。**撰写期间 task-1 已合并（`49b2d67b`），当前 `index.html` 4986 行**；本文正文行号仍按 1322ecac 基线，执行前必须按 §2.6 重新测量。
> **前置**：task-5（Shizuku 管理）还没落地；**在 Lead 明确说“可以开始”之前，本方案不编辑 `index.html` / `assets.rs`**。
> **硬约束**：不跑 npm/vite、不引入构建步骤、不 commit/push、不碰 `manager/` 与 `kernel/`。
> **行号说明**：task-1 合并后 CSS/JS 边界整体偏移，定位时以函数名为准（文中同时给了函数名）。

---

## 0. 当前进度（撰写期间的变化）

| 时间点 | HEAD | `index.html` | `webui.rs` | 说明 |
|---|---|---:|---:|---|
| 方案阅读时 | `1322ecac` | 4763 行 | 4132 行 | task-1 / task-5 都未落地 |
| 当前 | `49b2d67b` | 4986 行 | 4288 行 | **task-1 已合并**：设置页新增 SuSFS 区块（`index.html:592` 起，`susfsCard`），`webui.rs` 新增 SuSFS 路由；task-5 仍未落地 |

因此：

- 本文 §1 的行号是 `1322ecac` 基线，用来解释“拆分前长什么样”；**实际拆分以执行时的文件为准**，先跑 §2.6 的边界测量脚本。
- task-1 的 SuSFS 区块正好落在设置页“内核特性”之后，和 §3.7 的规划一致；它的 `.pref-item` / `.fm-btn` / 内联样式会在阶段 4d 一并 token 化，**不要改它的请求逻辑**。
- task-5 落地后还会再改一次 `index.html` / `webui.rs`，拆分必须等它合并后再做（见 §7.2）。

## 结论先行

1. **拆分可行且收益明确**：基线（1322ecac）是 216 KB / 4763 行的单文件，CSS 41 KB（488 行）、DOM 26 KB（427 行）、JS 148 KB（3838 行）；task-1 合并后（49b2d67b）约 227 KB / 4986 行，CSS 41.5 KB、JS 158.8 KB。按 `index.html` + `app.css` + `app.js` 切开后，改样式不再碰 4000 行 JS，改逻辑也不再碰 490 行 CSS。
2. **`assets.rs` 不需要改**。它用 `RustEmbed` 嵌的是 `bin/` 下的可执行文件，不是网页资产；网页资产一直由 `webui.rs` 的 `include_str!` 嵌入。把 css/js 放进 `assets.rs` 反而会被 `ensure_binaries()` 当成二进制解压到 `/data/adb/ksud/`，是错误路径。详见 §2.4。
3. **“五个 tab”与现状有出入**：当前底栏只有 4 个 `.bottombar-item`（home/superuser/modules/settings），`文件` 是全屏子页 `#page-files`，`keyMint` 是另一个子页。本方案默认按“五个页面目标”回归（首页/超级用户/模块/设置/文件），是否真的把“文件”提升为第 5 个底栏项，见 §7.1 的 A/B 决策。
4. **视觉方向**：不是换一套皮肤，而是把网页端现有的 iOS 风格变量**收敛成一套语义 token**，再按 App Miuix 美化版的尺度重排：12px 页边距、20px 分组卡、16px 行内圆角、56px 开关行、状态卡置顶、分组 section、统一开关行。亮/暗/AMOLED 三套都要成立。
5. **执行顺序**：先拆不改样式（1 个提交）→ 只改 token（1 个提交）→ 外壳（1 个提交）→ 逐页调（每页 1 个提交）→ 截图与清理。每一步都能单独 `git revert`。

---

## 1. 现状评估

### 1.1 单文件结构

| 区块 | 行号 | 行数 | 体积 | 内容 |
|---|---:|---:|---:|---|
| `<head>` + `<style>` | 1–496 | 496 | — | meta、title、全部 CSS（`:root` 从第 10 行开始） |
| `<body>` DOM | 498–922 | 425 | 26 KB | 顶栏、6 个 `.page`、底栏、重启弹窗、toast、文件管理器/终端/编辑器 |
| `<script>` | 924–4761 | 3838 | 148 KB | 主题引擎、API、五个页面渲染、文件管理器、keyMint、终端、启动调用 |
| 合计 | 1–4763 | 4763 | 216 KB | 无外部 css/js，无构建步骤 |

关键事实：

- 235 个顶层函数，全部是全局函数；111 处内联 `onclick`；75 处 JS 模板里的内联 `style="..."`。**拆分后必须保持经典脚本（不能 `type="module"`）**，否则内联 `onclick` 找不到全局函数。
- 94 个唯一 DOM id，页面切换完全靠 `switchPage()` 手动 toggle class，没有路由/History。
- 样式与结构没有组件化：同一类“设置行”至少 3 种写法，状态卡 2 种写法，硬编码颜色 56 个（CSS）+ 27 个（JS）。

### 1.2 变量体系（`--glass-*` 与主题引擎）

当前 `:root` 定义了 40 个变量（第 10–50 行），`[data-theme="dark"]` 覆盖 28 个（52–80 行），AMOLED 再覆盖 7 个（82–89 行）。核心分组：

| 组 | 变量 | 现状 |
|---|---|---|
| 背景/表面 | `--bg-primary` `--bg-secondary` `--bg-tertiary` `--btn-bg` `--card-hover` | iOS 风格浅灰/白；暗色为纯黑/`#1c1c1e` |
| 文字 | `--text-primary` `--text-secondary` `--text-tertiary` | 层级清楚，但 tertiary 对比度偏低 |
| 主色 | `--accent` `--accent-light` `--on-accent` `--switch-active` | `--switch-active` 默认是绿色 `#34c759`，一旦用户选种子色又被 `themePalette()` 改成 accent（1134 行）——**默认与选色两种状态不一致** |
| 语义色 | `--success` `--warning` `--danger` 及 `*-bg` | 有文字色 + 浅底，但没有独立的“填充色”；`--success` 等同时被当文字和图标用，深色下对比度不足 |
| 玻璃 | `--glass-surface` `--glass-edge` `--glass-shadow` `--glass-filter` | 只在 `html.glass:not(.dock-bar) .bottombar`（93–100 行）用；顶栏用的是 `--blur` + `--topbar-bg`，不是同一套 |
| 尺寸 | `--radius` 16px、`--radius-sm` 12px（**未使用**）、`--control-height` 44px、`--control-radius` 10px | 圆角/控件高度没有分级 |
| 状态卡 | `--status-working` `--status-working-text` | 只有首页状态卡用；主题引擎会按种子色生成（1136–1137 行） |
| 安全区 | `--safe-top` `--safe-bottom` `--bottom-space` | 依赖 `--window-inset-*` 注入，未定义时走 `env()`，属正常 |

已确认的问题：

- **引用了但没定义**：`--card-bg`（318、336、339 行）、`--bg`（331、333 行）。`--card-bg` 导致超级用户列表行没有卡片底色、`icon-btn:active` 没有按压反馈；`--bg` 所在的是死代码（见下）。
- **定义了但没使用**：`--radius-sm`。
- **运行时变量**：`--i`、`--nav-index`、`--nav-light-x` 由 JS/内联样式写入，属正常，不要误删。
- **主题引擎写死的白名单**：`THEME_TONES`（1034 行）与 `THEME_WRITABLE`（1056 行）只覆盖 14 个变量；`applyTheme()` 只写这些名字（1270 行）。新增语义 token **必须引用这些旧变量**，不能另起一套写死的颜色，否则选种子色/切暗色时不会跟着变。
- **硬编码颜色**：CSS 里 56 个唯一 hex，JS 里 27 个；`#34c759`、`#007aff`、`#ff9500`、`#667eea` 等散落在状态标签、图标渐变、终端配色里。
- **重复/死样式**：`.app-item`、`.app-icon`、`.app-name`、`.icon-btn` 等在 236–242 行与 318–345 行各定义一次，后者覆盖前者且用了未定义的 `--card-bg`；318–345 行整段是已没有 DOM 的 `app-profile-*` 死代码。启发式扫描出 20 个 CSS 类在 DOM/JS 中完全未引用（`app-profile-*`、`app-tags`、`status-tag`、`fm-badge`、`module-icon` 等）。

### 1.3 导航与“五个 tab”的真实结构

底栏 DOM 在 839–858 行，只有 **4 个** `.bottombar-item`：

| 底栏项 | `data-page` | DOM 行 | 页面容器 | 渲染入口 |
|---|---|---|---:|---|
| 首页 | `home` | 841 | `#page-home`（541） | `loadHome()`（1560） |
| 超级用户 | `superuser` | 845 | `#page-superuser`（547） | `loadApps()`（1684）/ `renderApps()`（1699） |
| 模块 | `modules` | 849 | `#page-modules`（559） | `loadModules()`（1842） |
| 设置 | `settings` | 853 | `#page-settings`（573） | `loadSettings()`（2310）/ `loadFeatures()`（2323） |
| （文件，非底栏） | `files` | — | `#page-files`（699） | `fmOpenFileManager()`（2789）/ `fmBoot()`（2873） |
| （keyMint，非底栏） | `keymint` | — | `#page-keymint`（697） | `kmOpen()`（3967）/ `kmRender()`（3988） |

底栏的真实 DOM 形态（839–858 行，省略 SVG）：

```html
<div class="bottombar">
  <div class="bottombar-inner">
    <div class="bottombar-item active" data-page="home"      onclick="switchPage('home')">…</div>
    <div class="bottombar-item"        data-page="superuser" onclick="switchPage('superuser')">…</div>
    <div class="bottombar-item"        data-page="modules"   onclick="switchPage('modules')">…</div>
    <div class="bottombar-item"        data-page="settings"  onclick="switchPage('settings')">…</div>
  </div>
</div>
```

第 5 项并不存在；`#page-files` 与 `#page-keymint` 只是 `.page` 容器，不是底栏项。

- `pageTitles`（933 行）有 6 个键：home/superuser/modules/settings/files/keymint；`isSubPage()`（1500 行）把 files/keymint 当子页，`switchPage()` 会给 `body` 加 `fm-mode`，而 `body.fm-mode .bottombar { display:none }`（349 行）。
- 所以“文件”现在是**从设置页进入的全屏子页**，不是第 5 个 tab。任务书/交接文档写“底部五个 tab”，大概率是把“五个页面目标”写成了“五个 tab”。
- `switchPage()`（983–997 行）做 6 件事：切 `.page.active`、切底栏 `.active`、改标题、控制模块 FAB、切 `fm-mode`/`fm-files`、同步顶栏返回按钮。它没有历史栈，返回靠顶栏按钮手动 `switchPage('settings')`（1491–1498 行）。

### 1.4 各页面渲染方式

| 页面 | DOM 形态 | 数据加载 | 关键函数 | 备注 |
|---|---|---|---|---|
| 首页 | 空容器 `#homeContent`，全量 `innerHTML` | `/api/info`、`/api/features`、`/api/systeminfo` | `loadHome()` 1560 | 状态卡 + 系统信息 + 功能开关三段 |
| 超级用户 | 搜索框 + `#appList` 空容器 | `/api/apps`、`/api/apps/icon` | `loadApps()` 1684、`renderApps()` 1699 | 图标懒加载 `IntersectionObserver`；`appRow()` 2433 |
| 模块 | `#moduleList` 空容器 + FAB | `/api/modules` | `loadModules()` 1842 | 批量选择、安装进度、WebUI iframe 都在这一页 |
| 设置 | 静态 DOM（573–696） | `/api/features`、`/api/ui/theme` | `loadSettings()` 2310、`loadFeatures()` 2323 | task-1/5 的 SuSFS/Shizuku 区块会插在这里（以最终落点为准） |
| 文件 | 预置双栏 DOM（699–837） | `/api/fs/*` | `fmOpenFileManager()` 2789、`fmBoot()` 2873 | 全屏模式，底栏隐藏；双栏、选择、终端、编辑器全在这一块 |
| keyMint | 空容器 `#page-keymint` | `/api/keymint/*` | `kmOpen()` 3967、`kmRender()` 3988 | 设置页进入的子页 |

### 1.5 可复用的组件片段

| 片段 | 位置 | 现状 | 重构后 |
|---|---|---|---|
| `.card` | 155 行 | 16px 圆角、白底、轻阴影、12px 下边距 | 升级为 `.card` + `.section` 组合，圆角/阴影走 token |
| `.status-card` | 158–163 行 | 纯色底、20px 内边距、24px 标题 | 升级为 `.status-hero`：渐变、水印图标、22px 主文案、badge |
| `.section-title` | 171 行 | 13px、大写、letter-spacing | 改为 14px/700、中文不转大写、20px 段间距 |
| `.pref-item` | 172–181 行 | 图标 + 标题/副标题 + 箭头/开关 | 拆成 `.row`（箭头行）与 `.switch-row`（开关行） |
| `.switch` | 182–185 行 | 51×31、绿色/ accent 两套 | 统一 51×31、`--switch-active: var(--accent)` |
| `.module-item` | 190–213 行 | 44px 图标、动作按钮 44 高 | 行高 72、图标 44/12 圆角、按钮 48 高 |
| `.empty-state` | 296–299 行 | 64px 圆形图标 + 17px 标题 | 保留结构，走 token |
| `.modal-overlay` / `.modal` | 284–293 行 | 16px 顶部圆角、80vh | 28px 顶部圆角、grabber、统一 header |
| `.toast` | 294–295 行 | 顶部黑色胶囊 | 保留位置，圆角/字号 token 化 |
| `.bottombar` | 253–283 行 | 24px 圆角、56px 高、4 项 | 28px 圆角、60px 高、active pill、可容纳 5 项 |
| `.fm-btn` | 453–455 行 | 44px 高、10px 圆角 | 48px 高、14px 圆角（仅视觉） |
| `api()` / `fmApi()` | 935 / 2577 行 | 两个几乎相同的 JSON 请求函数 | 保留 `api()` 为主，`fmApi()` 可收敛（非本次必须） |
| `appRow()` | 2433 行 | 应用行 HTML 模板 | 改为调用统一 `uiRow()`/`uiSwitchRow()` |
| `uiIcon()` | 2468 行 | 19 个内联 SVG 图标表 | 保留，作为唯一图标源 |
| `escapeHtml()` / `fmHtml()` | 2316 / 2421 行 | 两套转义 | 统一到 `escapeHtml()`，新模板默认转义 |
| `themeSwitchRow()` | 1285 行 | 主题弹窗里的开关行 | 迁移到 `uiSwitchRow()` |
| `kmRender()` 内的 `row()`/`toggleRow()` | 3990–4003 行 | keyMint 自己的一套行构造器 | 迁移到统一行构造器 |

### 1.6 现状问题清单（按优先级）

**P0（不改会直接影响观感/正确性）**

1. `--card-bg` / `--bg` 未定义：超级用户行无卡片底色，`icon-btn` 按压无反馈（318、331–339 行）。
2. `.app-item` / `.app-icon` / `.app-name` 重复定义且互相覆盖：48px 图标 + 24px 间距 + 透明底，与页面其他列表不一致（236 vs 318）。
3. 开关激活色不一致：默认绿色、选种子色后变 accent（45、78、1135 行）。
4. `.section-title` 的 `text-transform: uppercase` + `letter-spacing` 是为拉丁文设计的，中文分组标题看起来松散（171 行）。

**P1（影响一致性/可维护性）**

5. 3 套开关行、2 套状态卡、行高 44/56/64 混用。
6. 56 个 CSS hex + 27 个 JS hex，暗色/AMOLED 容易漏改。
7. 20 个未引用 CSS 类、`app-profile-*` 整段死代码、`--radius-sm` 未使用。
8. `api()` 与 `fmApi()`、`escapeHtml()` 与 `fmHtml()` 重复。

**P2（打磨）**

9. 没有 `color-scheme` meta，暗色下原生滚动条/表单控件仍是亮色。
10. 没有 `:focus-visible` 样式，键盘/无障碍弱。
11. 75 处 JS 内联样式让 token 迁移不彻底；逐页迁移时顺手收敛。
12. 单文件 216 KB，每次改样式都触发整页重新解析；拆分后浏览器可分别缓存（本地服务仍建议 no-store，见 §7.4）。

---

## 2. 拆分方案

### 2.1 三个文件的边界

| 目标文件 | 来源 | 目标行数（约） | 放什么 | 不放什么 |
|---|---|---:|---|---|
| `userspace/ksud/assets/web/index.html` | 现有 1–496 的 head + 498–922 的 DOM | 430 | `<!DOCTYPE>`、meta、`<link rel="stylesheet" href="/ui/app.css">`、全部 DOM、`<script src="/ui/app.js" defer></script>` | 不写 `<style>`、不写业务 JS |
| `userspace/ksud/assets/web/app.css` | 现有 10–495 行（`<style>` 内全部内容） | 486 + token 层 | 变量、布局、组件、文件管理器、动画、媒体查询 | 不放任何 HTML/JS |
| `userspace/ksud/assets/web/app.js` | 现有 925–4760 行（`<script>` 内全部内容） | 3836 | 主题引擎、API、五个页面、文件管理器、keyMint、终端、启动调用 | 不放 CSS/HTML 字符串以外的构建逻辑 |
| `userspace/ksud/assets/web/ksu-bridge.js` | 现有文件 | 不变 | 模块 WebUI 的 bridge | 与本次拆分无关，不要动 |

拆分纪律：

- **纯搬移，不改一个字符**：第一步只做剪切/粘贴，不做格式化、不重排选择器、不改缩进。这样可以用 `diff` 证明行为未变。
- `app.css` 保持原有规则顺序（层叠/覆盖关系不变），不要按“好看”重新排序。
- `app.js` 保持全局函数声明；不要加 `type="module"`、不要包 IIFE、不要加 `'use strict'`（第一步不加，避免改变既有语义）。
- `index.html` 里所有 `id`、`class`、`data-*`、`onclick` 原样保留；第一步不改 DOM 结构。
- `app.js` 末尾的 4 行启动调用（4757–4760）保持在文件末尾；`defer` 保证 DOM 已解析。
- `app.js` 开头的 token 清理与 `pagehide` beacon（925–926 行）原样搬到文件顶部。

### 2.2 `index.html` 目标骨架

```html
<!DOCTYPE html>
<html lang="zh-CN">
<head>
<meta charset="UTF-8">
<meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no, viewport-fit=cover">
<meta name="screen-orientation" content="portrait">
<meta name="x5-orientation" content="portrait">
<meta name="color-scheme" content="light dark">
<title>DikSU</title>
<link rel="stylesheet" href="/ui/app.css">
</head>
<body>
<div id="app">
  <!-- 现有 499–922 行 DOM 原样搬入 -->
</div>
<script src="/ui/app.js" defer></script>
</body>
</html>
```

说明：

- `color-scheme` 是新增的 1 行 meta，属于无风险改进（暗色下原生控件跟随）。
- 不把 CSS 内联到 `<head>`：虽然能省一次请求，但会回到“单文件”老路，违背拆分目标。
- 不把 JS 放 `<head>`：`defer` 放尾部即可；不要用 `async`（会打乱启动顺序）。
- 如果担心首屏白闪，可在 `<html>` 上加一个极小的内联背景色兜底；本次不做，先观察实际观感。

### 2.3 `webui.rs` 的改动（确切函数名与行号）

当前 `userspace/ksud/src/webui.rs` 相关位置（基线 HEAD）：

| 位置 | 行号 | 作用 |
|---|---:|---|
| `const INDEX_HTML` | 22 | `include_str!("../assets/web/index.html")` |
| `const KSU_BRIDGE` | 26–30 | 拼接 `ksu-bridge.js`，只给模块 WebUI 用 |
| `fn send_response_with` | 210 | 统一响应头/体；已有 `extra_headers` 参数 |
| `fn serve_module_web` | 386 | `/modweb/*`，模块 WebUI 目录 |
| `fn inject_ksu_bridge` | 434 | 只注入模块 WebUI 的 HTML |
| `fn serve_module_asset_by_referer` | 447 | `/assets/*`、`/internal/*`，**依赖 Referer 里的 `/modweb/<id>`，不能复用** |
| `fn handle_request` | 1715 | 所有路由入口 |
| `let path = ...` | 1716 | 去掉 query 后的路径 |
| `let redeemed = ...` / token 校验 | 1718–1734 | 令牌校验，失败返回 403 |
| `match (req.method.as_str(), path)` | 1740 | 路由表 |
| `("GET", "/") \| ("GET", "/index.html")` | 1741 | 返回 `INDEX_HTML`，已带 `no-store` |
| `("GET", p) if p.starts_with("/modweb/")` | 1760 | 模块 WebUI |
| `("GET", p) if p.starts_with("/assets/") \|\| p.starts_with("/internal/")` | 1762 | 模块资产（需 Referer） |
| `fn mime_for_name` | 3208 | 扩展名 → MIME，可选用 |

**新增常量**（放在 `INDEX_HTML` 后面，22 行附近）：

```rust
const APP_CSS: &str = include_str!("../assets/web/app.css");
const APP_JS: &str = include_str!("../assets/web/app.js");

const STATIC_NO_STORE: &str =
    "Cache-Control: no-store, must-revalidate\r\nPragma: no-cache\r\n";
```

**新增一个小的静态资源服务函数**（放在 `handle_request` 前面，或 `send_response_with` 后面）：

```rust
fn serve_builtin_web_asset(stream: &mut TcpStream, path: &str) -> Option<Result<()>> {
    let (body, mime) = match path {
        "/ui/app.css" => (APP_CSS.as_bytes(), "text/css; charset=utf-8"),
        "/ui/app.js" => (APP_JS.as_bytes(), "text/javascript; charset=utf-8"),
        _ => return None,
    };
    Some(send_response_with(stream, 200, mime, body, STATIC_NO_STORE))
}
```

**在 `handle_request` 里提前返回**（在 `let path` 之后、`let redeemed` 之前，即 1716–1718 之间）：

```rust
let path = req.path.split('?').next().unwrap_or("/");

// 内置前端资产不参与令牌校验：它们不含用户数据，且避免 WebView 不落 cookie 时
// 首页能打开、css/js 却 403 的半坏状态。服务只监听 127.0.0.1。
if req.method == "GET" {
    if let Some(result) = serve_builtin_web_asset(stream, path) {
        return result;
    }
}

let redeemed = query_param(req, "t").is_some_and(|ticket| redeem_ticket(&ticket, peer));
```

**为什么不用现有 `/assets/` 路由**：`serve_module_asset_by_referer`（447 行）会强制读取 `Referer` 里的 `/modweb/<module-id>`，再拼到模块目录；主页面没有这个 Referer，直接复用会 404。用独立的 `/ui/` 前缀最干净，也不会和模块 WebUI 的 `/assets/` 混淆。

**路由表最终形态**：

| 路由 | 处理 | 说明 |
|---|---|---|
| `GET /` / `GET /index.html` | `INDEX_HTML` | 不变 |
| `GET /ui/app.css` | `APP_CSS` | 新增，提前返回 |
| `GET /ui/app.js` | `APP_JS` | 新增，提前返回 |
| `GET /modweb/*` | 模块 WebUI | 不变 |
| `GET /assets/*`、`/internal/*` | 模块资产（Referer） | 不变，**不要挂主页面资源** |
| `/api/*` | 现有 API | 不变 |

**关于令牌的取舍**：静态资源走免鉴权是刻意选择，理由：服务只绑 `127.0.0.1`（3520 行），css/js 不含数据；否则一旦 WebView 不保存 `Set-Cookie`（1747 行），页面会白屏。若 Lead 要求严格鉴权，备选方案是把 `<link>`/`<script>` 的 URL 在服务端注入 `?k=<token>`，但那会把令牌写进 DOM，不推荐。

**缓存**：HTML 已经 `no-store`（1744–1745 行）；css/js 也加同样的 `no-store`，避免改了 `app.css` 但 WebView 用旧缓存。代价是每次多两个 304/200 请求，本地服务可忽略。

### 2.4 `assets.rs` 的改动：0 行

这是本方案最重要的一条“反向结论”，避免后来人走错路：

- `assets.rs` 的 `Asset` 是 `#[derive(RustEmbed)] #[folder = "bin/aarch64"]`（39–57 行），嵌的是 `ksud`、`busybox`、`bootctl` 等**可执行文件**。
- `ensure_binaries()`（15–24 行）遍历 `Asset::iter()`，除了 `ksuinit`、`waitsys`、`*.ko` 之外**全部**写到 `BINARY_DIR` 并 `chmod`。如果把 `app.css`/`app.js` 放进 `assets.rs`，它们会被当成可执行文件落到 `/data/adb/ksud/app.css`，既浪费又语义错误。
- 网页资产一直由 `webui.rs:22` 的 `include_str!` 嵌入，这是正确位置；拆分后只需在同一位置再加两个 `include_str!`（见 §2.3）。
- 如果未来真要做“多文件网页资产包”，应该新增独立的 `#[derive(RustEmbed)] #[folder = "assets/web"] struct WebAsset;` 和 `get_web_asset()`，而不是复用 `bin/` 的 `Asset`；本次不需要。

### 2.5 拆分验收

1. 用 §2.6 的脚本测出边界，按行切出三份，再拼回去，`diff` 与原文件**逐字节一致**（除了新增的 `<link>`/`<script>` 两行和删掉的 `<style>`/`<script>` 包裹）。
2. `node --check userspace/ksud/assets/web/app.js` 通过。
3. CSS 花括号配平；`grep -n 'var(--card-bg)\|var(--bg)' app.css` 在第一步应保留原样（第二步再修）。
4. CI：`ci_dispatch` 触发 `build-manager.yml`（`abi=arm64-v8a`），`ci_watch` 到绿；本地没有 Android SDK，不能靠本地 cargo 判断。
5. 真机打开 WebUI：首页/超级用户/模块/设置/文件/keyMint 全部能加载，控制台无 JS 报错，截图与拆分前一致（允许 0 像素差异）。

### 2.6 拆分边界测量脚本（执行前必跑）

不要直接套用本文的历史行号。task-1 已合并、task-5 还会改，拆分前先跑：

```bash
python3 - <<'PY'
p = 'userspace/ksud/assets/web/index.html'
lines = open(p, encoding='utf-8').read().splitlines()
for i, line in enumerate(lines, 1):
    if line.strip() in ('<style>', '</style>', '<body>', '<script>', '</script>'):
        print(i, line.strip())
print('total', len(lines))
PY
```

当前 HEAD（`49b2d67b`）的实测值是：`<style>` 9、`</style>` 497、`<body>` 499、`<script>` 929、`</script>` 4984、总行数 4986。也就是说：

| 资产 | 当前切法（49b2d67b） | 基线切法（1322ecac） |
|---|---|---|
| `app.css` | 10–496 | 10–495 |
| DOM | 500–927 | 499–922 |
| `app.js` | 930–4983 | 925–4760 |

task-5 合并后再跑一次，以最新输出为准；本文其余行号仅用于解释结构，不作为剪切依据。

---

## 3. 视觉规范

### 3.1 与 App Miuix 美化版对齐的依据

从仓库里的 Compose 实现提取到的尺度（不是拍脑袋）：

| App 侧 | 位置 | 取值 |
|---|---|---|
| 页面左右内边距 | `SettingsMiuix.kt:99`、`SusfsScreen.kt:216` | 12dp |
| 卡片圆角 | `HomeMiuix.kt:360`、Miuix `CardDefaults.CornerRadius` | 16dp |
| 卡片内边距 | `SusfsScreen.kt:240–241` | 16dp，卡片间距 12dp |
| 分组标题 | Miuix `SmallTitle` | Subtitle：14sp、Bold |
| 正文字号 | Miuix 0.9.4 `TextStyles.kt` | Main 17、Body1 16、Body2 14、Footnote1 13、Footnote2 11、Title2 24、Title3 20 |
| 首页状态卡 | `HomeMiuix.kt:419–455` | 16dp 圆角、22sp SemiBold、primaryContainer→secondaryContainer 渐变 |

网页端的映射原则：

- **页面 12px、卡片内 16px、卡片间距 12px** 与 App 一致。
- App 的 Card 是“单卡”；网页的 `.card` 是“分组容器”（里面有多行），按同心圆角规则外圈取 **20px**、行内取 **16px**，视觉重量与 App 的 16dp 单卡一致。这是“更大的圆角”的落点，不是把每一行都做成 20px。
- 状态卡是页面第一块内容，圆角 **24px**，比普通卡片大一档，形成层级。
- 分组标题 14px/700，中文不转大写；段与段之间 20px，段内标题与卡片 8px。
- 开关行统一 56px 高、整行可点、右侧 51×31 开关；箭头行同高，右侧 18px chevron。

### 3.2 颜色 token（亮/暗两套 + AMOLED 覆盖）

> 下表是**默认 fallback 色板**。用户选择种子色后，`applyTheme()` 会用 HCT 重新生成 `--accent/--bg-*/--status-*`；新规则必须引用这些变量，不能写死 hex。

**基础色**

| 语义 token | 亮色 | 暗色 | 来源 | 用途 |
|---|---|---|---:|---|
| `--surface-page` | `#F5F5F7` | `#000000` | 别名 → `var(--bg-primary)` | 页面底色 |
| `--surface-card` | `#FFFFFF` | `#1C1C1E` | 别名 → `var(--bg-secondary)` | 卡片/弹窗 |
| `--surface-sunken` | `#F0F0F2` | `#2C2C2E` | 别名 → `var(--bg-tertiary)` | 搜索框/输入/图标底 |
| `--surface-control` | `#E9E9EE` | `#232325` | 别名 → `var(--btn-bg)` | 次级按钮 |
| `--surface-hover` | `#FAFAFA` | `#252527` | 别名 → `var(--card-hover)` | 按压/悬停 |
| `--text-1` | `#1A1A1C` | `#FFFFFF` | 别名 → `var(--text-primary)` | 标题/正文 |
| `--text-2` | `#6E6E73` | `#98989D` | 别名 → `var(--text-secondary)` | 副标题 |
| `--text-3` | `#86868B` | `#8E8E93` | 别名 → `var(--text-tertiary)` | 说明/占位 |
| `--line` | `#E3E3E8` | `#38383A` | 别名 → `var(--border)` | 分割线/描边 |
| `--fill-accent` | `#007AFF` | `#0A84FF` | 别名 → `var(--accent)` | 主色填充 |
| `--accent-soft` | `#E8F1FF` | `#14283D` | 别名 → `var(--accent-light)` | 选中底/浅色强调 |
| `--on-accent` | `#FFFFFF` | `#FFFFFF` | 旧变量，保留 | 主色上的字 |

**状态色**

| 语义 token | 亮色 | 暗色 | 来源 | 用途 |
|---|---|---|---:|---|
| `--success` | `#248A3D` | `#5CE07A` | 旧变量，直接改值 | 成功文字/图标 |
| `--success-fill` | `#34C759` | `#30D158` | 新增 | 成功圆点/填充 |
| `--success-soft` | `#E4F8E8` | `#12291A` | 别名 → `var(--success-bg)` | 成功浅底 |
| `--warning` | `#A05A00` | `#FFB340` | 旧变量，直接改值 | 警告文字 |
| `--warning-fill` | `#FF9500` | `#FF9F0A` | 新增 | 警告图标 |
| `--warning-soft` | `#FFF3E0` | `#2E2113` | 别名 → `var(--warning-bg)` | 警告浅底 |
| `--danger` | `#C4271E` | `#FF7A70` | 旧变量，直接改值 | 危险文字 |
| `--danger-fill` | `#FF3B30` | `#FF453A` | 新增 | 危险按钮/填充 |
| `--danger-soft` | `#FFE8E6` | `#2E1715` | 别名 → `var(--danger-bg)` | 危险浅底 |
| `--switch-off` | `#E9E9EB` | `#39393D` | 别名 → `var(--switch-bg)` | 开关关 |
| `--switch-on` | `var(--accent)` | `var(--accent)` | 别名 → `var(--switch-active)` | 开关开；**不再默认绿色** |
| `--status-hero-bg` | 由主题生成 | 由主题生成 | `linear-gradient(135deg, var(--status-working), var(--surface-card) 82%)` | 首页/状态卡渐变 |
| `--status-hero-text` | `#1A3825` | `#DFFAE4` | 别名 → `var(--status-working-text)` | 状态卡文字 |

**AMOLED 覆盖**（`[data-theme="dark"][data-amoled="1"]`）

| token | 值 |
|---|---:|
| `--surface-card` | `#0A0A0B` |
| `--surface-sunken` | `#141416` |
| `--surface-control` | `#191A1C` |
| `--line` | `#262629` |
| `--surface-hover` | `#141416` |
| `--topbar-bg` | `rgba(0,0,0,.85)` |
| `--bottombar-bg` | `rgba(10,10,11,.85)` |

补充规则：

- 旧变量（`--bg-primary` 等）继续作为**唯一可写源**；新语义 token 全部写成 `var(--旧变量)`，这样 `themePalette()` 不需要改。
- 新增的 `--success-fill`/`--warning-fill`/`--danger-fill` 只用于填充，不参与文字对比；文字一律用 `--success`/`--warning`/`--danger`。
- 所有 hex 只允许出现在 `:root`、`[data-theme="dark"]`、`[data-theme="dark"][data-amoled="1"]` 三个块里；组件规则里出现 hex 视为未完成。

### 3.3 圆角 / 间距 / 尺寸 token

| token | 值 | 用途 |
|---|---:|---|
| `--radius-hero` | 24px | 状态卡 |
| `--radius-card` | 20px | 分组卡、文件面板 |
| `--radius-row` | 16px | 行内按压区、图片 |
| `--radius-icon` | 12px | 模块/应用图标底 |
| `--radius-control` | 14px | 按钮、搜索框、输入框 |
| `--radius-sheet` | 28px | 底部弹窗/安装进度 |
| `--radius-badge` | 8px | badge/状态标签 |
| `--radius-pill` | 999px | 开关、toast |
| `--space-1` … `--space-8` | 4/8/12/16/20/24/32px | 4px 基准间距 |
| `--page-x` | 12px | 页面左右（对齐 App 12dp） |
| `--section-gap` | 20px | 分组之间 |
| `--section-title-gap` | 8px | 分组标题到卡片 |
| `--card-gap` | 12px | 卡片之间 |
| `--row-x` | 16px | 行左右内边距 |
| `--row-y` | 12px | 行上下内边距 |
| `--row-min-h` | 56px | 开关行/箭头行最小高度 |
| `--control-h` | 48px | 按钮/输入框（原 44，提高到 48） |
| `--tab-h` | 60px | 底栏内容高度 |
| `--tab-inset` | 12px | 底栏离屏幕边缘 |
| `--content-bottom` | `calc(96px + var(--safe-bottom))` | 页面底部留白，避免被浮动底栏挡住 |
| `--hit-min` | 44px | 最小触控目标 |

### 3.4 字号 / 字重 / 行高 token

| token | size / line-height / weight | 用途 | Miuix 对应 |
|---|---|---|---|
| `--type-display` | 24 / 30 / 600 | 状态卡大数字 | Title2 24 |
| `--type-status` | 22 / 28 / 600 | 首页状态主文案 | Home 22 SemiBold |
| `--type-title` | 20 / 26 / 600 | 顶栏标题 | Title3 20 |
| `--type-headline` | 17 / 24 / 600 | 行标题、按钮 | Main / Headline1 17 |
| `--type-body` | 16 / 24 / 400 | 正文 | Body1 16 |
| `--type-sub` | 14 / 20 / 400 | 副标题 | Body2 14 |
| `--type-footnote` | 13 / 18 / 400 | 说明、版本号 | Footnote1 13 |
| `--type-caption` | 11 / 14 / 500 | badge、tab 标签 | Footnote2 11 |
| `--type-section` | 14 / 20 / 700 | 分组标题 | Subtitle 14 Bold |
| `--type-button` | 16 / 20 / 600 | 主按钮 | Button 17（网页略收） |

规则：

- 中文正文最小 13px；11px 只用于 badge/tab 标签，不用于长文本。
- 数字（版本号、大小、百分比）用 `font-variant-numeric: tabular-nums`。
- 指纹、路径等长字符串保留 `word-break: break-all`，不因字号调整而溢出。
- 不再使用 `text-transform: uppercase` 处理中文分组标题。

### 3.5 阴影 / 描边 / 动效 token

| token | 亮色 | 暗色 | 用途 |
|---|---|---|---|
| `--shadow-card` | `0 1px 2px rgba(0,0,0,.04), 0 8px 20px rgba(0,0,0,.05)` | `0 1px 2px rgba(0,0,0,.5), 0 12px 28px rgba(0,0,0,.35)` | 普通卡 |
| `--shadow-hero` | `0 12px 30px rgba(0,0,0,.10)` | `0 12px 30px rgba(0,0,0,.45)` | 状态卡 |
| `--shadow-bar` | `0 8px 32px rgba(0,0,0,.18)` | `0 8px 32px rgba(0,0,0,.55)` | 浮动底栏/弹窗 |
| `--hairline` | `0.5px` | `0.5px` | 卡内分割线；跨设备显示为 1px 也可接受 |
| `--blur-bar` | `saturate(180%) blur(20px)` | 同左 | 顶栏/底栏玻璃 |
| `--ease-standard` | `cubic-bezier(.2,.8,.2,1)` | 同左 | 常规过渡 |
| `--ease-emphasized` | `cubic-bezier(.16,1,.3,1)` | 同左 | 弹窗/底栏 |
| `--dur-fast` | `140ms` | 同左 | 按压/开关 |
| `--dur-base` | `220ms` | 同左 | 页面/卡片 |
| `--dur-slow` | `320ms` | 同左 | 弹窗 |

保留 `prefers-reduced-motion: reduce` 的现有处理（440–444 行），新增动画也要挂进去。

### 3.6 组件规范

| 组件 | 规范 |
|---|---|
| **分组 section** | `.section { margin-top: var(--section-gap) }`，第一个 `margin-top: 0`；`.section-title` 14/700 `--text-2`，左右 4px，距卡片 8px；`.section .card { margin-bottom: 0 }` |
| **卡片** | `--surface-card`、`--radius-card`、`--shadow-card`、`1px solid var(--line)`；`overflow: hidden`；卡内行不再各自加外边距 |
| **状态卡（置顶）** | `.status-hero`：`--radius-hero`、`padding: 20px`、`min-height: 116px`、`--status-hero-bg`、`--status-hero-text`；右下 96px 水印图标（`currentColor`，opacity .12）；主文案 `--type-status`；副文案 `--type-sub`；badge 用 `--type-caption` + `--radius-badge`；变体 `.is-error` 用 `--danger-soft`/`--danger`，`.is-warn` 用 `--warning-soft`/`--warning` |
| **开关行** | `.switch-row`：`min-height: 56px`、`padding: 12px 16px`、`gap: 12px`；标题 17/600、副标题 14/400；整行可点；右侧 `.switch` 51×31；行间分割线左缩进 16px；禁用 `.is-disabled` 用 `opacity: .45; pointer-events: none`，并保留原因文案 |
| **箭头行** | 与开关行同高；右侧 18px chevron；图标 22px、`--text-1`；按压背景 `--surface-hover` |
| **模块行** | 最小 72px；图标 44×44、`--radius-icon` 12px、`--accent-soft` 底；名称 17/600、描述 14/400 单行省略、版本 13/400；动作按钮 48 高、14 圆角；批量选择条 sticky 顶部 |
| **按钮** | 主按钮 `--fill-accent`/`--on-accent`；次按钮 `--surface-control`/`--text-1`；危险按钮 `--danger-soft`/`--danger`；高度 48、圆角 14、字重 600；按压 `transform: scale(.98)`；禁用 `opacity: .45` |
| **底栏** | 浮动：左右 12、底部 `calc(12px + safe-bottom)`、高 60、`--radius-sheet` 28、`--shadow-bar`、玻璃模式沿用 `--glass-*`；每项 `flex:1`、图标 22、标签 11/500；active 用 `--accent-soft` 圆角胶囊 + `--accent` 文字；支持 4/5 项不挤压 |
| **顶栏** | 高 56（原 52）；标题 20/600；图标按钮 44×44 圆形；玻璃背景 `--topbar-bg` + `--blur-bar`；子页返回按钮沿用现有逻辑 |
| **搜索框** | 高 48、`--surface-sunken`、`--radius-control`、左图标 18、输入 16；距页面顶部 12 |
| **弹窗/底部 sheet** | `--radius-sheet` 顶部圆角；最大 80vh；header 56、标题 20/600；backdrop `rgba(0,0,0,.4)` + blur 8；关闭按钮 32 圆形 |
| **Toast** | 顶部 70；`rgba(0,0,0,.78)`；`--radius-pill`；14/500；2s 自动消失 |
| **空状态** | 64 圆形图标底、17/600 标题、14/400 描述；上下 48px |
| **badge/状态标签** | 11/600、padding 3×8、`--radius-badge` 8；成功/警告/危险/中性四种；用 `color-mix` 或半透明 `currentColor` 做底 |
| **开关** | 51×31 轨道、27 旋钮、`--switch-on: var(--accent)`；关闭 `--switch-off`；过渡 220ms `--ease-standard`；整行点击时开关本身不重复绑定 |

### 3.7 页面 section 顺序（状态卡置顶）

| 页面 | 从上到下 |
|---|---|
| 首页 | 状态卡（工作状态/版本/模式 badge）→ 系统信息 → 功能开关 |
| 超级用户 | 搜索框 → 应用列表卡（已授权在前）→ 空状态 |
| 模块 | 已安装模块（卡内行）→ 批量条（选择时）→ FAB；安装进度浮层 |
| 设置 | 通用（主题）→ 内核特性 → **SuSFS**（状态卡置顶）→ **Shizuku**（状态卡置顶）→ 工具 → WebUI → 文件 → 关于 |
| 文件 | 保持现有双栏：路径栏 → 列表 → 底部操作 sheet；仅 token 化 |
| keyMint | 状态卡（守护进程）→ 功能 → 重启；仅 token 化 |

> SuSFS/Shizuku 的最终落点以 task-1/task-5 合并后的代码为准；如果它们放在首页或其他页，**状态卡置顶**与 **section 分组**两条不变。

---

## 4. 迁移步骤（每步可独立回滚）

### 4.1 阶段 0：前置冻结（不写代码）

- 等 task-1、task-5 合并；确认 `git status` 里 `index.html`、`webui.rs` 没有未完成的改动。
- 记录基线 `HEAD`；用当前运行的 4.1 WebUI 截 before 图（见 §4.7）。
- 和 Lead 确认 §7.1 的“五个 tab”口径。
- **回滚**：无代码改动。

### 4.2 阶段 1：纯拆分（1 个提交）

**改动**

1. 新建 `app.css`：原样搬 `index.html` 10–495 行。
2. 新建 `app.js`：原样搬 925–4760 行。
3. `index.html`：删 `<style>`/`<script>`，加 `<link>`/`<script defer>`；DOM 原样。
4. `webui.rs`：加 `APP_CSS`/`APP_JS`/`STATIC_NO_STORE`、`serve_builtin_web_asset()`、`handle_request` 提前返回。
5. `assets.rs`：不动。

**验证**

- 脚本切分/回拼 `diff` 为零；`node --check app.js`；CSS 花括号配平。
- CI `build-manager.yml`（arm64-v8a）绿。
- 真机六页可打开、控制台无报错、截图与 before 一致。

**回滚**：`git revert <阶段1提交>`；`index.html` 恢复单文件，`webui.rs` 去掉两条路由。因为 css/js 是新增文件，revert 会一并删除。

### 4.3 阶段 2：只改 token（1 个提交）

**改动**

1. `app.css` 顶部加 `:root` token 层（§3.2–3.5）与语义别名；旧变量保留为源。
2. 修 `--card-bg`/`--bg` 未定义：`--card-bg` 改为 `var(--surface-card)` 或直接改用 `--surface-card`；删除死 `app-profile-*` 段。
3. 删除重复的 `.app-item`/`.app-icon`/`.app-name` 第二套（318–345 行）；保留第一套并改成卡片内行。
4. `--switch-active` 默认改为 `var(--accent)`；`--success`/`--warning`/`--danger` 改为文字色，新增 `*-fill`。
5. 合并重复的 `--radius-sm`（删除或用于 badge）；统一 `--control-h: 48px`。
6. `index.html` 只加 `<meta name="color-scheme">`（如果阶段 1 没加）。

**验证**

- 亮/暗/AMOLED 三套截图；选一个种子色（如红/蓝）确认 accent/背景/开关都跟着变。
- `grep` 确认组件规则里没有新增 hex；`var(--card-bg)`/`var(--bg)` 归零。
- 页面结构与阶段 1 一致（token 改动不应改变布局，只改变颜色/圆角/字号）。

**回滚**：`git revert <阶段2提交>`；token 块与变量替换一起回退，DOM/JS 不受影响。

### 4.4 阶段 3：外壳（1 个提交）

**改动**

- 顶栏、底栏、弹窗/sheet、toast、空状态、按钮基类、section 基类。
- 底栏 28 圆角/60 高/active pill；如果 §7.1 选 B，在这里加第 5 项。
- 全局 `:focus-visible`、`color-scheme`、`prefers-reduced-motion`。

**验证**

- 四个底栏项切换、玻璃开/关、悬浮/贴底（`dock-bar`）、安全区、键盘弹出。
- 重启弹窗、主题弹窗、安装进度浮层不被新圆角/层级破坏。

**回滚**：`git revert <阶段3提交>`；页面内容不受影响，只回到旧外壳。

### 4.5 阶段 4：逐页调（每页 1 个提交）

顺序与每页动作：

| 子阶段 | 页面 | 主要动作 | 独立验证 |
|---|---|---|---|
| 4a | 首页 | 状态卡 → `.status-hero`；系统信息/功能开关包 `.section`；`feature-item` → `.switch-row`；保留 999/1000 value-typed 逻辑 | 版本/badge/五个系统信息/每个开关；下拉刷新 |
| 4b | 超级用户 | 搜索框 token；`#appList` 包一层 `.card`；删除重复 app 样式；行高 64、图标 44；保留懒加载 | 搜索、授权/取消、图标、空状态 |
| 4c | 模块 | 模块行 72、动作按钮 48；批量条、FAB、安装进度、WebUI iframe | 启停/执行/打开/卸载/撤销/批量/安装 |
| 4d | 设置 + SuSFS + Shizuku | 分组 section；主题行/内核特性行统一；SuSFS/Shizuku 状态卡置顶、开关行统一；不改编排逻辑 | 主题持久化、特性开关、SuSFS 三开关/规则、Shizuku 启停/授权数 |
| 4e | 文件 | 只改 `.fm-*` 的颜色/圆角/字号；不改选择/滑动/终端/编辑器逻辑 | 双栏、选择、复制/移动/删除/重命名/解压/终端/编辑器 |
| 4f | keyMint | `kmRender` 的行改用统一 `.pref-item`/`.switch-row`；section 包装 | 守护进程状态、修属性、应用选择、日志级别、keybox、重启 |

每个子阶段单独提交、单独截图；任何一个页面出问题，只 revert 该页提交，不影响其他页。

**回滚**：`git revert <对应子阶段提交>`。

### 4.6 阶段 5：截图、清理、文档（1–2 个提交）

- 产出 `docs/webui-redesign/`：`README.md` + `before/` + `after/` 截图。
- 清理：未引用 CSS 类、`--radius-sm`、重复的 `fmApi()`（可选）、旧注释。
- 更新 `docs/handoff.md` 的网页端章节（可选，若 Lead 要求）。
- 最终跑一遍 §5 全量回归。

**回滚**：文档/截图独立提交，代码回滚不影响已生成的截图（截图会标注对应 commit）。

### 4.7 截图规范（阶段 5 的一部分）

- 设备：同一台手机、同一浏览器、同一视口（建议 390×844 CSS px 或设备原生 1080×2400）。
- 主题：亮色 + 暗色各一组；至少覆盖首页、设置、模块、文件四页；超级用户/keyMint 可选。
- 命名：`before/home-light.png`、`after/home-light.png` 等。
- 体积：单张 ≤300KB，整套 ≤2MB；优先保留 4 组关键页，不要把所有页面都塞进仓库。
- 截图必须来自真实 WebUI（带 token 的 `http://127.0.0.1:<port>/?k=...`），不接受手绘 mockup。

---

## 5. 回归清单

### 5.0 每次提交都要过

- [ ] `node --check userspace/ksud/assets/web/app.js` 退出码 0。
- [ ] CSS 花括号配平；无 `var(--未定义)`（`--i`/`--nav-index`/`--nav-light-x`/`--window-inset-*` 例外）。
- [ ] `git diff --check` 无空白错误。
- [ ] CI：`build-manager.yml`（`abi=arm64-v8a`）绿；`clippy`/`rustfmt` 工作流绿。
- [ ] 浏览器控制台 0 error、0 unhandled rejection；`pagehide` 时 `/api/close` 仍发出。
- [ ] 亮/暗/AMOLED × 360/390/430 宽度；玻璃开/关；悬浮底栏/贴底；安全区；键盘弹出。
- [ ] 旧 token 访问：过期地址仍显示 `FORBIDDEN_HTML`（403 页未被样式改动破坏）。

### 5.1 首页

- [ ] 状态卡：工作状态、版本号、LKM/GKI badge、越狱/正常 badge、点击 toast。
- [ ] 系统信息：内核版本、设备型号、系统指纹（长文本换行）、SELinux 颜色、Seccomp 文案。
- [ ] 功能开关：每个开关可切换；不支持的内核特性置灰并显示原因；999/1000 value-typed 关闭后能再打开。
- [ ] 开关失败时回滚 UI 并 toast 真实错误。
- [ ] 下拉刷新重新加载首页；不重复绑定事件。
- [ ] 顶栏主题/重启按钮正常。

### 5.2 超级用户

- [ ] 列表加载；已授权在前；搜索过滤（`filterApps`）；空状态。
- [ ] 图标懒加载、失败回退字母图标；滚动不卡顿。
- [ ] 开关授权/取消授权；toast；刷新后状态保持。
- [ ] 行高/图标/开关对齐；没有双重左右内边距；没有透明底/悬空分隔线。

### 5.3 模块

- [ ] 列表、启用/禁用、执行 action、打开 WebUI（iframe）、卸载/撤销。
- [ ] 批量选择/全选/取消/批量卸载；选择时底部操作条不遮挡。
- [ ] FAB 打开安装；文件选择→安装；流式控制台/进度/中止。
- [ ] 更新待重启标记、刚安装闪烁、待卸载删除线。
- [ ] `/modweb/` 与模块资产仍正常（拆分没动 `KSU_BRIDGE`）。

### 5.4 设置

- [ ] 主题：system/light/dark/amoled；种子色；style/spec；blur/float/glass；保存后刷新仍生效（`/api/ui/theme` + localStorage）。
- [ ] 内核特性开关；重载模块；软重启确认；WebUI 服务状态。
- [ ] 文件管理器入口、keyMint 入口、HMA 一键配置、版本信息、QQ 群。
- [ ] 分组顺序、section 间距、卡片圆角在亮/暗下一致。

### 5.5 文件

- [ ] 双栏加载、路径输入/回车、上一级、下拉刷新；两栏独立滚动。
- [ ] 点选/长按/滑动选择、全选/清空；复制/移动冲突弹窗；删除确认；重命名；新建文件夹/文件。
- [ ] 上传、解压、压缩、加入压缩包、chmod/chown、打开方式、APK 提取。
- [ ] 终端打开/输入/输出/关闭；编辑器打开/保存/关闭；图片缩放。
- [ ] 选择模式（装模块/选路径）的底部操作条；压缩包内 `fs-only` 按钮隐藏。
- [ ] `fm-mode` 隐藏底栏、顶栏返回设置；键盘 inset 不遮输入框。

### 5.6 keyMint

- [ ] 未安装提示；已安装状态、版本、启用状态。
- [ ] keymint/injector 守护进程状态点；修正系统属性开关；应用选择；日志级别；keybox 状态；重启按钮。

### 5.7 SuSFS（task-1 产出）

- [ ] 未加载：区块**仍然显示**、置灰、给出原因和“去首页提权/安装”的提示，不整块消失。
- [ ] 已加载：状态卡（版本/variant/features）置顶。
- [ ] 三个开关：log / avc log spoofing / hide sus mnts；切换后 `/proc/susfs_*` 有真实变化；失败显示真实错误。
- [ ] 规则列表：paths/loops/maps/kstats/redirects 分组；添加后出现；删除返回 `persistentOnly` 时把说明原样显示，不假装即时删除。
- [ ] 刷新按钮；非 Android/未加载不 500 白屏。

### 5.8 Shizuku（task-5 产出）

- [ ] 运行中/已停止 + pid；启动/停止后 pid 真实变化。
- [ ] 启动失败显示真实错误，不只弹“成功”。
- [ ] 自启状态、上次结果/时间。
- [ ] 授权应用数量；**不显示任何授权明细**（只报数量与文件权限）。
- [ ] 状态卡置顶；按钮在窄屏不换行溢出。

### 5.9 主题 / 响应式 / 安全

- [ ] 种子色：红/蓝/绿各选一次，accent、accent-soft、status 卡、开关全部跟随；暗色不发灰。
- [ ] AMOLED 纯黑下卡片边界仍可辨（靠 `--line` 而不是阴影）。
- [ ] 360px 窄屏：底栏 4/5 项不挤压；长模块名/包名省略；指纹换行。
- [ ] 玻璃模式：底栏 `--glass-*` 仍生效；`no-blur`/`dock-bar` 组合不破。
- [ ] 令牌：新开 `?t=` 链接正常；旧 token 403；`/ui/app.css`、`/ui/app.js` 正常加载且 `no-store`。
- [ ] 无新增外部请求/外链字体；所有图标仍来自 `uiIcon()` 内联 SVG。

---

## 6. 本次不做

1. **不改后端 API 语义/字段**：只在 `webui.rs` 加两条静态资源路由；SuSFS/Shizuku 的接口由 task-1/task-5 负责。
2. **不引入构建步骤**：不用 npm/vite/webpack/TypeScript/Sass/Tailwind/PostCSS；不生成 sourcemap，不做压缩。
3. **不重写文件管理器/终端/编辑器/keyMint 的逻辑**：只套视觉 token，不动选择、滑动、传输、解压、PTY 等代码。
4. **不新增页面/导航**：默认保留 4 个底栏项，“文件”仍是全屏子页；是否加第 5 个底栏项见 §7.1，未确认前不做。
5. **不做网页端 i18n**：现有中文硬编码保持，不新增 `strings` 机制。
6. **不做 Material 3 网页皮肤**：网页端只有一套“Miuix 美化版观感”，不复制 App 的三皮肤切换。
7. **不改造主题引擎**：保留现有 `THEME`/`themePalette()`/HCT 逻辑；只保证新 token 引用旧变量。不新增主题模式、不改 localStorage key。
8. **不做完整无障碍审计**：只给交互行补 `role="switch"`/`aria-checked`/`aria-disabled` 和 44px 触控；不做全站屏幕阅读器测试。
9. **不做首屏性能优化**：不预加载、不骨架屏、不懒加载 CSS/JS；拆分本身已经让缓存粒度变好。
10. **不动 `manager/`、`kernel/`、CI 配置、版本号**；不提交 APK/大图。
11. **不做 SuSFS/Shizuku 功能开发**：只在它们落地后做视觉接入与回归，不替它们设计交互逻辑。
12. **不删除现有功能入口**：即使看起来是旧样式，也先保留，逐页确认后再清理。

---

## 7. 风险与待决

### 7.1 “五个 tab”的口径（需要 Lead 拍板）

- **A（推荐，默认）**：底栏保持 4 项；把“文件”视为第 5 个页面目标，回归按首页/超级用户/模块/设置/文件覆盖。零导航风险，符合“视觉重构不改信息架构”的原则。
- **B**：把“文件”提升为第 5 个底栏项。需要改：
  - 底栏 DOM 加第 5 个 `.bottombar-item`（文件图标/“文件”）。
  - `isSubPage(page)`（1500 行）不能再把 `files` 当子页，否则 `fm-mode` 会隐藏底栏；拆成 `isFullscreenPage` 与 `isSubPage` 两套判断。
  - `fmSyncTopbar()`（1502 行）的返回按钮逻辑、`#page-files` 的高度/底部留白、`body.fm-files` 背景都要重算。
  - 回归双栏在底栏存在时的高度、滚动、键盘 inset。
  - 估计额外 0.5 天 + 文件页全量回归；建议单独任务，不混在视觉重构里。
- 无论 A/B，**keyMint 仍是设置页子页**，不占底栏。

### 7.2 task-1/task-5 并发冲突

- 两个任务都在改 `index.html`（单文件）和 `webui.rs`；task-6 的拆分提交会与它们冲突。
- 对策：**等它们合并后再开始**；开始后对 `index.html` 设“只读窗口”，不再并行改；拆分提交只动 `<style>`/`<script>` 包裹与 `webui.rs` 顶部常量 + 一个 `handle_request` 分支，冲突面控制在 3 个小 hunk。
- 如果必须并行：先做 `webui.rs` 路由（不改 `index.html`），等它们合并后再做三文件拆分；不要反过来。

### 7.3 token 与主题引擎的耦合

- `applyTheme()` 只写 `THEME_WRITABLE` 里的 14 个变量；新 token 若直接写 hex，选种子色/暗色会“半旧半新”。
- 对策：所有新语义 token 写成 `var(--旧变量)`；`--success-fill` 等新增填充色如果要跟随主题，需要加进 `THEME_TONES` 并由 `themePalette()` 生成（第二步做，改 JS 要单独验证）。
- 不改 `localStorage` 的 `ksu-theme-state` 结构，避免用户升级后主题丢失。

### 7.4 静态资源鉴权与缓存

- 方案默认 `/ui/*` 免 token（只绑 127.0.0.1，css/js 无数据）；如果安全评审要求鉴权，改用 cookie + 服务端注入 `?k=`，但要评估 token 进 DOM 的风险。
- `no-store` 避免旧缓存；如果后续要做缓存，需要给 URL 加版本号（例如 `?v=<ksud version>`），本次不做。

### 7.5 截图验收的前置

- 本地没有 Android SDK，`ksud` 只能靠 CI 构建；after 截图需要把 CI 产物部署到手机并重启 `ksud webui` 服务。
- 这属于设备操作，需 Lead/设备 owner 配合；**不要重启设备**（task-4 的红线）。只重启 webui 进程/服务，不动系统。
- 如果暂时不能部署，after 截图可以先在桌面浏览器 + mock API 下做视觉校对，但**最终验收必须是手机浏览器真实 WebUI**。

### 7.6 CSS 兼容性

- 现有代码已经用了 `color-mix()`（150 行），说明目标 WebView 较新；新代码可继续用，但关键颜色要有 fallback（先写 solid，再在 `@supports` 里用 `color-mix`）。
- `backdrop-filter` 需要 `-webkit-` 前缀，现有写法保留。
- 0.5px 边框在部分设备会渲染成 1px 或 0；关键分割线用 `1px solid var(--line)`，卡内可用 0.5px。

---

## 8. 交付物与估算

| 交付物 | 内容 |
|---|---|
| `docs/webui-redesign-plan.md` | 本文件（当前阶段唯一产出） |
| `userspace/ksud/assets/web/index.html` | 拆分后的结构文件（约 430 行） |
| `userspace/ksud/assets/web/app.css` | 样式 + token（约 500–600 行） |
| `userspace/ksud/assets/web/app.js` | 原逻辑（约 3836 行，逐页改动后可能 ±100 行） |
| `userspace/ksud/src/webui.rs` | 2 个常量 + 1 个 helper + 1 个提前返回分支 |
| `docs/webui-redesign/` | before/after 截图 + README（设备/视口/主题/commit） |

粗略估算（不含等 CI/等合并）：

| 阶段 | 估算 |
|---|---:|
| 阶段 1 拆分 + 路由 | 0.5 天 |
| 阶段 2 token | 0.5 天 |
| 阶段 3 外壳 | 0.5 天 |
| 阶段 4 逐页（6 页） | 1.5–2 天 |
| 阶段 5 截图/清理/回归 | 0.5–1 天 |
| 合计 | 3.5–4.5 天 |

---

## 9. 附：拆分后自检脚本（建议）

```bash
# 1) JS 语法
node --check userspace/ksud/assets/web/app.js

# 2) CSS 花括号配平（简单版）
python3 - <<'PY'
s = open('userspace/ksud/assets/web/app.css', encoding='utf-8').read()
print('braces', s.count('{'), s.count('}'))
PY

# 3) 未定义 CSS 变量（排除运行时变量）
python3 - <<'PY'
import re
s = open('userspace/ksud/assets/web/app.css', encoding='utf-8').read()
used = set(re.findall(r'var\((--[a-z0-9-]+)', s))
defined = set(re.findall(r'^\s*(--[a-z0-9-]+)\s*:', s, re.M))
runtime = {'--i', '--nav-index', '--nav-light-x', '--window-inset-top', '--window-inset-bottom'}
print('undefined:', sorted(used - defined - runtime))
PY

# 4) 组件规则里不应再有裸 hex（token 块除外，按行号人工确认）
grep -nE '#[0-9a-fA-F]{3,8}' userspace/ksud/assets/web/app.css | head -40
```

---

**待 Lead 确认的三件事**

1. §7.1 选 A 还是 B（是否真的加第 5 个底栏项）。
2. task-1/task-5 预计合并时间；合并后是否给 `index.html` 一个只读窗口。
3. after 截图是否允许部署 CI 构建的 ksud 到手机（只重启 webui，不重启设备）。
