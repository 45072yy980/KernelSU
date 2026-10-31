# 自定义主题移植方案（FolkPatch / FinaSU → Diksu Ultra）

> 状态：**方案稿，等你拍板后再动代码**。
> 事实来源：`LyraVoid/FolkPatch`（main，2026-10-30）与 `LyraVoid/FinaSU`（master，2026-10-30），
> 均为我直接读源码确认，不是凭印象。

---

## 0. 结论先行

1. **两家的主题包是同一个格式**，可以互操作：AES-256-CBC 加密后再 ZIP，密钥是
   `SHA-256("FolkPatchThemeSecretKey2025")`，前 16 字节是 IV；包内固定有 `theme.json`，
   资源按"stem 命名"（`background_home`、`font`、`music` …）。**我们只要实现这个格式，
   就能直接导入网上现成的 FolkPatch/FinaSU 主题**，这是移植最大的价值点。
2. **不建议整包照搬**。FinaSU 的主题系统约 20 万字符 Kotlin（wallpaper/ + themestore/ 共 40+ 文件），
   FolkPatch 的 `ThemeConfig` 有 **60+ 字段**（多背景、每张工作卡单独背景、标题图、视频背景、
   音乐/音效/振动、每个 surface 的 dim/opacity）。全量移植等于在我们的管理器里再造一套 UI。
3. 建议**分三期**：一期只做"能导入/导出 + 能应用"的核心（背景图、毛玻璃参数、种子色、字体、深浅模式），
   二期补主题包里的高级项，三期才考虑主题商店。一期做完就已经能吃掉现有主题包的大部分效果。
4. 有一个**必须先决定的产品问题**：我们有三套皮肤（Miuix 美化 / Miuix 原版 / Material），
   而这两家的主题都是围绕**单一样式**设计的。见第 6 节，需要你选一种口径。

---

## 1. 上游事实（源码级）

| 项 | FolkPatch | FinaSU |
|---|---|---|
| 包名 | `me.bmax.apatch` | `me.weishu.kernelsu` |
| 主题入口 | `ui/screen/settings/appearance/AppearanceThemeSection.kt` | `ui/screen/wallpaper/WallpaperScreen.kt` |
| 配置模型 | `ui/theme/ThemeManager.kt` 的 `ThemeConfig`（60+ 字段） | `wallpaper/WallpaperConfig.kt` + `ThemeSettingsMapping.kt` |
| 读写 | `ui/theme/ThemeIO.kt` | `wallpaper/FptContainer.kt` + `ThemeImportService/ThemeExportService` |
| 校验 | 直接导入 | `wallpaper/ThemeValidation.kt`（枚举白名单 + 尺寸预算） |
| 主题商店 | `ui/screen/theme/ThemeStore.kt` + `util/ThemeDownloader.kt` | `data/themestore/ThemeDownloader.kt` |
| 特色 | 音乐/音效/振动、视频背景、每个工作卡单独背景 | 按 surface 分槽位（`SurfaceRegistry`）、壁纸对比度护栏 |

两边共用同一把密钥和同一套 `theme.json` 键名，FinaSU 在 FolkPatch 的基础上做了
"per-surface 背景"和"壁纸模式下的对比度护栏"。

---

## 2. 主题包格式（我们要兼容的那个）

```
<16 字节 IV> + AES-256-CBC( ZIP 流 )
   密钥 = SHA-256("FolkPatchThemeSecretKey2025".toByteArray())
   ZIP 内容：
     theme.json          # 配置，上限 1 MiB
     original.json       # 导入时的原始配置（可选）
     applied.json        # 实际生效的配置（可选）
     background_home.jpg / background_kernel.* / background_superuser.* / …   # 按 surface 命名的图
     font.ttf            # custom 字体的字重
     music.mp3 / sound_effect.* / video.mp4                                   # 可选
```

预算（照抄上游，防 zip 炸弹）：最多 128 个条目、配置 1 MiB、解开后总量 1 GiB。

`theme.json` 关键字段（做兼容必须认，未知字段直接忽略）：

| 组 | 字段 |
|---|---|
| 元信息 | `meta_name` / `meta_type`(phone\|tablet) / `meta_version` / `meta_author` / `meta_description` |
| 颜色 | `customColor`（20 个预设名）、`folksu_keyColor`（任意 ARGB，FinaSU 扩展）、`useSystemDynamicColor`、`colorGenerationMode`(classic\|custom)、`colorStandard`(MD3_2021\|M3E_2025)、`colorStyle`(TONAL_SPOT…)、`colorContrast`(STANDARD\|MEDIUM\|HIGH) |
| 深浅 | `nightModeEnabled`、`nightModeFollowSys` |
| 背景 | `isBackgroundEnabled`、`backgroundOpacity`、`backgroundBlur`、`backgroundDim`、`isDualBackgroundDimEnabled`、`backgroundDayDim`、`backgroundNightDim`、`isMultiBackgroundEnabled` |
| 字体 | `isFontEnabled`、`fontMode`(app\|system\|custom) |
| 布局 | `homeLayoutStyle`(kernelsu\|circle\|focus\|dashboard_ui\|stats\|default\|sign)、`statsTopLayout`(list\|grid) |
| 卡片背景 | `isGridWorkingCardBackgroundEnabled` + opacity/dim 系列、`isFocusCardBackgroundEnabled` 系列、`isDashboardCardBackgroundEnabled` 系列 |
| 标题图 | `isAdvancedTitleStyleEnabled`、title image 的 opacity/dim/offset |
| 媒体 | `isMusicEnabled`、`musicVolume`、`isAutoPlayEnabled`、`isLoopingEnabled`、`musicFilename`、`isSoundEffectEnabled`、`soundEffectFilename`、`soundEffectScope`、`isVideoBackgroundEnabled`、`videoVolume` |
| 其它 | `appLanguage`、`isListWorkingCardModeHidden` |

---

## 3. 我们这边的落点（已核过现有代码）

| 我们已有的 | 位置 | 与本方案的关系 |
|---|---|---|
| 主题引擎（ColorMode / keyColor / paletteStyle / colorSpec） | `manager/.../ui/theme/Theme.kt` | 主题包的颜色组直接映射到这四个字段，**不需要新引擎** |
| Material / Miuix 两套皮肤 | `ui/theme/MaterialTheme.kt`、`MiuixTheme.kt` | 颜色注入点已存在，改由主题包覆盖即可 |
| 主页壁纸 | `ui/util/HomeWallpaper.kt` | 主题包的 `background_*` 就是壁纸来源，复用现有渲染 |
| 设置项存储 | `data/repository/SettingsRepositoryImpl.kt` | 主题"当前生效配置"存这里，不新建第二套设置 |
| 网页端主题（`THEME`/Monet） | `userspace/ksud/assets/web/app.js` | 本期不动；网页端是独立的一套，后续可接同一个 theme.json |

需要新增（一期）：

```
manager/app/src/main/java/me/diksu/ultra/theme/          ← 新包
  ThemeContainer.kt      # AES-CBC + ZIP 读写（照抄 FptContainer 的预算与校验）
  ThemeManifest.kt       # theme.json 的 data class + 未知字段忽略
  ThemeValidator.kt      # 枚举白名单、尺寸预算、必需字段
  ThemeImporter.kt       # 导入：解密 → 校验 → 落盘 → 应用到 SettingsRepository
  ThemeExporter.kt       # 导出：读当前设置 → 生成 theme.json → 打包加密
  ThemeApplier.kt        # 把 manifest 映射到 keyColor/paletteStyle/壁纸/字体/深浅
ui/screen/theme/                                          ← 新界面
  ThemeScreen.kt         # 我的主题：列表 / 导入 / 应用 / 删除 / 导出
  ThemeScreenMaterial.kt # 三皮肤都要有（见第 6 节）
  ThemeScreenMiuix.kt
```

外部依赖：Android 自带 `javax.crypto` + `java.util.zip`，**不需要新增第三方库**。

---

## 4. 分期

### 一期（建议先做这个，约 3 个提交）
- 格式：读/写 `.fpt`（加密 zip），校验预算与枚举白名单；
- 导入/导出/删除/应用；导入后立刻生效并刷新界面；
- 应用范围：背景图（日/夜）、opacity/blur/dim、种子色、字体（custom/ttf）、深浅模式；
- 兼容未知字段：能吃掉 FolkPatch/FinaSU 的包，不认识的字段忽略并记日志，不报错。

**验收**：拿一个真实的 FolkPatch 主题包导入 → 背景/配色/字体/深浅都对；
导出的包能被 FolkPatch 或 FinaSU 导入（互操作验证）。

### 二期（看一期效果再定）
- 多背景（每个 surface 一张图）、工作卡/焦点卡/仪表盘卡背景；
- 标题图、视频背景；
- 音乐/音效/振动。

### 三期（可选）
- 主题商店（下载/预览/评分）：需要上游的接口地址与 CDN，**且要处理"从网络拿主题包"的安全问题**
  （加密密钥是公开的，等于任何包都能被解出来，只能靠校验兜底）。默认建议不做，或只做"从链接导入"。

---

## 5. 与三家皮肤的兼容（重点）

我们的管理器有三套皮肤，两家的主题都是"一套全局样式"。三种可行口径：

| 口径 | 做法 | 代价 |
|---|---|---|
| A. 只作用于 Miuix 美化版 | 主题只改 Miuix 皮肤的颜色/壁纸；Material 保持自己的 Monet | 最小改动；Material 用户用不了主题 |
| B. 三皮肤共享颜色，但布局各归各 | 主题包只提供"颜色 + 壁纸 + 字体 + 深浅"，布局字段（homeLayoutStyle 等）只对 Miuix 生效 | 推荐：三皮肤都能吃到配色，不会出现"切皮肤主题消失" |
| C. 主题包里的布局也映射到 Material | 需要给 Material 单独实现一套首页布局 | 工作量大，且我们的 Material 首页本来就与 Miuix 不同构 |

**建议 B**，并在导入预览里明确写"布局样式仅 Miuix 生效"——不做假。

---

## 6. 需要你拍板的三件事

1. **口径**：第 5 节选 A / B / C？（我建议 B）
2. **互操作**：是否要求"我们导出的包能被 FolkPatch/FinaSU 导入"？
   要求的话，`theme.json` 必须严格按它们的字段名写（多写字段它们会忽略，少写会丢效果）。
3. **主题商店**：三期做不做？做的话需要你提供可用的商店接口（我这边拿不到它们的线上地址，
   `ThemeDownloader` 里的 URL 是运行时传入的，不是写死的）。

---

## 7. 风险

- **加密密钥是公开常量**：这个"加密"只防手改，不防解包；导入时必须把校验做严
  （预算、枚举、必需的 meta 字段），否则一个恶意包就能塞超大字库/图片把应用撑爆。
- **盗版/版权**：主题包通常带作者信息（`meta_author`），我们只做本地导入/导出，
  不做分发，避免把别人的主题打包进仓库。
- **字体**：`fontMode=custom` 会加载外部 ttf，需要限制大小并校验是合法字体，加载失败要回退系统字体并说明。
- **三皮肤回归**：一期就要把"切皮肤后主题是否还在"写成验收项，避免又出现"某个皮肤下主题消失"的假完成。
