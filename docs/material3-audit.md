# Material 3 皮肤逐页巡检（v4.1 基线）

> 执行人：`material-audit`（团队任务 `task-3`）
> 基线：`main` @ `1322ecac`（工作区干净）
> 方法：静态审查 —— 路由可达性、`LocalUiMode` 皮肤分支、字符串/ViewModel 字段引用、空实现扫描、与 Miuix 实现的动作集对比。
> **限制**：本地没有 JDK / Gradle / Android SDK，**未编译、未上真机**。下文「已修」指代码改动完成；「待真机确认」指静态审查无法判定、必须装机点验的项。

---

## 一、结论摘要

- 覆盖 Material 页面/分支 **25 个**：已修 **3** 处，无需改 **22** 处；另有 5 项待真机确认（见第五节）。
- **`sulog` 与「应用配置模板」不是死代码**：4.1 只删掉了「主设置页」的入口，两者仍分别从「超级用户」页和「基础功能设置」页可达（证据见第三节）。
- 三套皮肤共用的 `BasicSettingsScreen.basicGroups` 里原本还留着一个「自动越狱」开关，它和 4.1 的「自动提权并越狱」是同一个偏好，摘要还写着已废弃的 Magica 路径 —— 经 Lead 决策已删除该行（见第二节 #5），`SettingsRepository.autoJailbreak` 与字符串资源按要求保留。
- 除上述一处外，Material 代码里已搜不到被删开关的字符串、路由或 ViewModel 字段引用。

---

## 二、逐页清单

| # | 页面 | Material 实现 | 发现的问题 | 处置 |
|---|---|---|---|---|
| 1 | 首页 | `home/HomeMaterial.kt` | 状态卡 / 更新卡 / 信息卡 / 支持链接与 `HomeMiuix` 一一对齐；无被删功能引用。`HomeActions.onJailbreakExploitClick` 全仓库无人使用（`HomeUiState.kt:83` 定义、`HomeScreen.kt:122` 传入），属死字段，不影响运行 | 无需改（字段在 `HomeUiState.kt`，不在本轮写入范围） |
| 2 | 模块 | `module/ModuleMaterial.kt` | 卡片、WebUI、动作脚本、卸载、排序、搜索齐全。`isSoftRebootPreferred()`（:261）读的是「软重启」**工具**偏好，该偏好 4.1 仍在「基础功能设置」提供，不是被删的设置项 | 无需改 |
| 3 | 超级用户 | `superuser/SuperUserMaterial.kt` | 顶部 sulog 图标（:112）是 sulog 页唯一入口；排序 / 过滤 / 搜索完整；无被删功能引用 | 无需改 |
| 4 | 主设置 | `settings/SettingsMaterial.kt` | 4.1 删块后遗留 **9 个未使用的图标 import**：`Article`、`Rule`、`Adb`、`Description`、`DeveloperMode`、`FlashOn`、`LayersClear`、`RestartAlt`、`VerifiedUser`（都是被删开关的图标）；无被删功能的字符串/路由/字段引用。另：缺「隐藏管理器」入口（只有 `SettingsMiuix.kt:248` 有，`SettingsMiuixStock` 也没有） | **已修**：清理 9 个残留 import；「隐藏管理器」与 MiuixStock 口径一致，本轮不动，建议单开任务 |
| 5 | 基础功能 | `settings/BasicSettingsScreen.kt`（`BasicSettingsMaterial`） | **仍保留「自动越狱」开关**（原 :174-181，`settings_auto_jailbreak`）。它与「自动提权并越狱」共用同一偏好 `autoJailbreak`（`AutoEscalation.enabled`，见 `jailbreak/AutoEscalation.kt:65`），摘要却仍写「自动使用 Magica 提权」，而 4.1 已断开 magica 路径；同一页上方还有「自动提权并越狱」入口（:291 / :439），等于重复且误导 | **已修**（Lead 决策）：删掉该 `BasicRow`，只保留 AutoEscalation 入口。`SettingsViewModel.setAutoJailbreak` 仍被 `SettingsScreenActions.onSetAutoJailbreak`（无人消费）引用，本轮未删，见 P0-1 备注 |
| 6 | SuSFS | `susfs/SusfsMaterial.kt` | **「open redirect」规则组的删除按钮是空实现**（原 :543 `MaterialRuleGroup("open redirect", redirects) { }`），列表里画了垃圾桶图标，点了没反应。另有 6 处硬编码中文（:207、:520、:522、:529、:532、:582） | **已修**：`MaterialRulesCard` 保留 packed 形式，删除接 `SusfsRepository.removeSusRedirect(...)`；硬编码字符串列入第六节 |
| 7 | Shizuku 面板 | `shizuku/ShizukuScreen.kt`（`ShizukuScreenMaterial`） | 状态卡 / 自启卡 / 授权列表 / 搜索 / 筛选与 Miuix 动作集完全一致（脚本比对无差集）。整屏中文硬编码约 25 处，属 4.0 历史妥协（handoff §6.2 已记录） | 无需改（本轮）；建议单开「Shizuku 抽资源」任务 |
| 8 | Shizuku 日志 | `shizuku/ShizukuLogScreen.kt`（`ShizukuLogScreenMaterial`） | 功能与 Miuix 一致；9 处硬编码中文（标题、刷新、复制全部、导出、清除、搜索占位、服务日志、暂无日志） | 无需改（本轮）；同上 |
| 9 | WebUI | `webui/WebUIMaterial.kt` + `webui/WebUIActivity.kt` | alert / confirm / prompt / 文件选择器四类事件齐全，与 `WebUIMiuix` 等价；`WebUIActivity` 的 Material 主题与加载页分支正常；模块卡片与快捷方式两条入口都走 `com.mngr.app.ui.WebUIActivity`（真实子类，非错包名） | 无需改 |
| 10 | 文件管理 | —— | **本分支没有独立文件管理页**：全仓库搜不到 FileManager 页面；与文件相关的只有 WebUI 的 `ShowFileChooser`（:128）和模块导入的 SAF 选择器，两者都已有 Material 处理 | 无需改（对象不存在） |
| 11 | KeyMint | `settings/KeymintScreen.kt`（`KeymintMaterial`） | 分组列表、开关、跳转齐全；无被删功能引用。共享代码里有中文硬编码（`"确定"`/`"取消"`/`"读不到这个文件"` 等），非 Material 独有 | 无需改（本轮） |
| 12 | SU 日志（sulog） | `sulog/SulogMaterial.kt` | **有入口，不是死代码**：`SuperUserMaterial.kt:112` → `Route.Sulog`，`MainActivity.kt:339` 已注册。页面状态/文件选择/筛选/详情完整；`unsupported` 时有说明卡，不会白屏 | 无需改（若产品决定下架，需同时删入口 + 路由，另开任务） |
| 13 | 应用配置模板 | `template/TemplateMaterial.kt` | **有入口，不是死代码**：`BasicSettingsScreen.kt:83` → `Route.AppProfileTemplate`，Material 分支 :310 渲染；导入/导出/新建/编辑齐全 | 无需改（同上） |
| 14 | 模板编辑器 | `templateeditor/TemplateEditorMaterial.kt` | **有入口**：`TemplateScreen.kt:119/131`（新建/编辑）与 `AppProfileScreen.kt:104`（只读查看）都 push `Route.TemplateEditor`；保存/删除/校验齐全 | 无需改 |
| 15 | 应用配置 | `appprofile/AppProfileMaterial.kt` | 动作集与 `AppProfileMiuix` 无差集（含 `onViewTemplate` / `onManageTemplate`）；Snackbar 走 Material 分支 | 无需改 |
| 16 | 关于 | `about/AboutMaterial.kt` | 版本胶囊、链接、许可入口正常 | 无需改 |
| 17 | 开源许可 | `about/OpenSourceLicenseScreen.kt`（Material 分支） | 列表与返回正常 | 无需改 |
| 18 | 主题配色 | `colorpalette/ColorPaletteScreenMaterial.kt` | 色板预览里的 `content = { }`（:574/:583）是纯色块占位，不是缺失内容 | 无需改 |
| 19 | 刷写 | `flash/FlashMaterial.kt` | 动作集与 Miuix 一致 | 无需改 |
| 20 | 安装 | `install/InstallMaterial.kt` | 动作集与 Miuix 一致 | 无需改 |
| 21 | 模块仓库 | `modulerepo/ModuleRepoMaterial.kt` | 动作集与 Miuix 一致；`onSearchStatusChange` 只服务 Miuix 的展开动画，Material 用 `SearchAppBar` 自管，不是缺陷 | 无需改 |
| 22 | 脚本运行 | `scriptrun/ScriptRunMaterial.kt` | 动作集与 Miuix 一致 | 无需改 |
| 23 | 模块动作 | `executemoduleaction/ExecuteModuleActionMaterial.kt` | 动作集与 Miuix 一致 | 无需改 |
| 24 | 其他功能 | `settings/OtherFeaturesScreen.kt`（`OtherFeaturesMaterial`） | 守卫 / 隐蔽 / KeyMint / Shizuku / 重载模块 / 软重启工具与 Miuix 一致；这里的「软重启」是工具按钮，不是 4.1 删掉的设置项 | 无需改 |
| 25 | 自动提权 | `settings/AutoEscalationScreen.kt`（`AutoEscalationMaterial`） | 开关 / 方式排序 / 立即尝试 / 上次结果都正常；与 #5 的旧开关重复（问题在 #5） | 无需改（等 #5 决策） |

---

## 三、专项：sulog / 应用配置模板 是不是死代码

结论：**都不是**。4.1 的提交 `4d109449` 只删了「主设置页」的入口，功能本身和二级入口都还在。

| 页面 | 路由 | 谁在 push | 结论 |
|---|---|---|---|
| SU 日志 | `Route.Sulog` | `SuperUserScreen.kt:76`（`onOpenSulog`）→ `SuperUserMaterial.kt:112` 顶栏图标 / `SuperUserMiuix.kt:134` 同位置 | 活入口（两套皮肤都有） |
| 应用配置模板 | `Route.AppProfileTemplate` | `BasicSettingsScreen.kt:83` → Material :318 / Miuix :461；另 `AppProfileScreen.kt:108` | 活入口 |
| 模板编辑器 | `Route.TemplateEditor` | `TemplateScreen.kt:115/127`、`AppProfileScreen.kt:104` | 活入口 |

> 如果 4.1 的本意是**下架**这两个功能，那当时只删了一半：需要同时删顶栏图标 / 基础功能入口 / 路由与 `MainActivity` 注册。本轮不动路由（`Routes.kt`、`MainActivity.kt` 不在写入范围），请 Lead 决定是「保留」还是「另开任务下架」。

---

## 四、专项：4.1 删除项在 Material 里的引用矩阵

| 被删项 | 字符串引用 | 路由 | ViewModel 字段 | 结论 |
|---|---|---|---|---|
| su compat | 无（仅 BasicSettings 合法使用） | 无 | 无 | 通过 |
| sulog 开关 | 无（`settings_sulog` 仅用于 sulog **查看页**标题/图标） | `Route.Sulog` 仍活 | 无 | 通过（查看页保留） |
| ADB root | 无 | 无 | 无 | 通过 |
| 内核卸载 | 无 | 无 | 无 | 通过 |
| SELinux 隐藏 | 无 | 无 | 无 | 通过 |
| Shell su 仅 ADB | 无 | 无 | 无 | 通过 |
| 默认卸载模块 | 无 | 无 | 无 | 通过 |
| Web 调试 | 无 | 无 | 无 | 通过 |
| 软重启（设置项） | 无（`reboot_soft` 是模块页的「重启」动作文案） | 无 | 无 | 通过 |
| 自动越狱 | ~~有~~（已删）：原 `BasicSettingsScreen.kt:176`，该 BasicRow 本轮已移除 | 无 | `autoJailbreak` 仍被 `AutoEscalation` 复用（保留） | **已修**（见第二节 #5） |
| 应用配置模板 | 有（功能仍在，见第三节） | `Route.AppProfileTemplate` 活 | `TemplateViewModel` 活 | 非删除项，保留 |

`SettingsMaterial.kt` 里只剩 4.1 删块时没清掉的 9 个 import，属残留引用，已在本轮清掉。

---

## 五、待真机确认

静态审查无法判定，需要装机（Material 皮肤）逐项点验：

1. **三皮肤切换 + 二级页返回**：主设置 → 基础功能 / 其他功能 / SuSFS → 返回，确认无空白页、无返回栈错乱（`dropUnlessResumed` + `navigator.pop()` 的组合只能静态看）。
2. **SuSFS 规则删除**：删除一条 path/loop/map/kstat/redirect 后列表即时刷新（`localTick`），重启后确实不再重放。
3. **WebUI 两条入口**：模块卡片点击、长按建快捷方式后从桌面进入，Material 下都正常加载（`WebUIActivity` 是独立 Activity，主题切换需实测）。
4. **Shizuku / Shizuku 日志**：Material 下返回、搜索、复制/导出日志、授权开关。
5. **大字体 / 深色模式**：Material 卡片（尤其 Home 状态卡、Susfs 规则卡）在最大字体下不截断、不重叠。

---

## 六、需要的字符串资源（交给 Lead 统一加，本轮不动 `values*/strings.xml`）

仅列**修复 SusfsMaterial 硬编码**所需的最小集合（其余页面的中文硬编码建议单独立项）：

| key | 中文 | English | 用在哪 |
|---|---|---|---|
| `susfs_status_loaded_short` | 已加载 | Loaded | `SusfsMaterial.kt:207` |
| `susfs_status_unloaded_short` | 未加载 | Not loaded | `SusfsMaterial.kt:207` |
| `susfs_rules_title`（`%1$d`） | 已保存的规则（%1$d） | Saved rules (%1$d) | `SusfsMaterial.kt:520` |
| `susfs_rules_summary` | 开机后自动重放；删除只影响下次开机，本次开机内规则仍然生效 | Replayed at boot; deleting only affects the next boot, rules stay active until then | `SusfsMaterial.kt:522` |
| `susfs_rule_paths` | 隐藏路径 | Hidden paths | `SusfsMaterial.kt:529`（也可复用 `susfs_add_path`） |
| `susfs_rule_loops` | 循环隐藏路径 | Hidden paths (mount-tracking) | `SusfsMaterial.kt:532`（也可复用 `susfs_add_path_loop`） |
| `susfs_rule_redirects` | 文件打开重定向 | Open redirects | `SusfsMaterial.kt:542` |
| `susfs_delete` | 删除 | Delete | `SusfsMaterial.kt:582` |

> Shizuku（约 25 处）与 Shizuku 日志（9 处）的中文硬编码是 4.0 遗留，抽资源需要一次性补 30+ 个 key，建议单开任务，不要塞进本轮。

---

## 七、本轮代码改动清单

| 文件 | 改动 | 原因 |
|---|---|---|
| `settings/SettingsMaterial.kt` | 删除 9 个未使用的图标 import | 4.1 删块残留，编译无影响但属「引用已删功能」的残渣 |
| `susfs/SusfsMaterial.kt` | `MaterialRulesCard` 的 open redirect 组接上删除回调；`MaterialRuleGroup` 增加 `display` 参数 | 删除按钮原本是空实现，点了没反应 |
| `settings/BasicSettingsScreen.kt` | 删除 `basicGroups` 里「自动越狱」的 `BasicRow`（原 174-181 行）；`FlashOn` import 保留（:298/:443 仍在用） | 与 AutoEscalation 共用同一偏好，重复且文案过期（4.1 已断开 Magica 路径） |

### 遗留说明（P0-1 备注）

- `SettingsViewModel.setAutoJailbreak` **没有删**：它仍被 `SettingsScreenActions.onSetAutoJailbreak`（`SettingsUiState.kt:89` 声明、`SettingsScreen.kt:81` 接线）引用，而该 action 已无任何页面消费。要彻底清掉需要同时改 `SettingsUiState.kt` / `SettingsScreen.kt` / `SettingsViewModel.kt` 三个文件，超出本轮批准的三处范围，留作后续小清理。
- `SettingsRepository.autoJailbreak` 与 `SettingsUiState.autoJailbreak` **保留**：`AutoEscalation.enabled` 仍用它做持久化。
- `settings_auto_jailbreak` / `settings_auto_jailbreak_summary` 两个字符串**保留**（20+ 语言目录都有，删了容易漏），当前已无引用。
