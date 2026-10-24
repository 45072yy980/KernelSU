# 4.1 真机核验记录（Xiaomi 24129RT7CC / Android 16）

> 执行者：device-verifier（task-4）· 只读核验，未重启设备、未改任何设置、未写设备文件
> 记录时间：2026-10-24 16:46–18:05 UTC
> 仓库：`/sdcard/diksu`，HEAD `c86dc34d`，最近 tag `v4.1.0-ultra`（`efaacd24`）
> 桥：DSHA 3090，`BRIDGE_PROTOCOL=3`，凭据走 `-H @/root/.dsh/.bridge_headers`

本文件第一节只记录**本轮真实执行并拿到输出**的接口；被阻塞项与后续清单见后续章节。

---

## 第一节 本次实拿证据

所有命令均为只读 GET，未触发任何设备状态变更。原始输出按"命令 → 输出 → 结论"三段式记录。

### 1.1 桥自身版本 `/app/version`

```bash
curl -s -m 20 -H @/root/.dsh/.bridge_headers "http://127.0.0.1:3090/app/version"
```

输出（原文）：

```
BRIDGE_PROTOCOL=3
BRIDGE_AUTH=header-x-token
APP_VERSION=0.2.1-rc.2-20261022.2100-dsh0.2.1-rc.2
APP_CODE=190
HINT=端点清单见 /app/help；判版本请用 >= 而不是 ==
```

结论：桥协议 3，宿主 App 版本 `0.2.1-rc.2-20261022.2100`。**这只是 DSHA 客户端版本，与管理器/内核版本无关**，不能用来判定 `me.diksu.ultra` 的任何信息。

### 1.2 设备信息 `/app/device`

```bash
curl -s -m 20 -H @/root/.dsh/.bridge_headers "http://127.0.0.1:3090/app/device"
```

输出（原文）：

```
model=Xiaomi 24129RT7CC
android=16 (SDK 36)
battery=9% charging=true
network=wifi
screen=on
app_foreground=false
storage_free=247.90 GB total=479.35 GB
memory_free=1.64 GB total=11.06 GB
```

结论：

- 机型/系统与任务描述一致（`24129RT7CC` / Android 16 / SDK 36），本文件所有结论的设备前提成立。
- `battery=9% charging=true`：电量偏低但在充电。**这条直接排除了"完整重启验证"**——低电量重启有掉电中断风险，且重启会杀掉容器宿主（见第二节）。
- `screen=on` 且 `app_foreground=false`：屏幕亮着但 DSHA 不在前台，这解释了 1.6 的剪贴板失败。
- 存储余量 247.90 GB / 479.35 GB，内存 1.64 GB 可用 / 11.06 GB。

### 1.3 按包名查管理器 `/app/apps?q=diksu`

```bash
curl -s -m 25 -H @/root/.dsh/.bridge_headers "http://127.0.0.1:3090/app/apps?q=diksu"
```

输出（原文）：

```
[用户应用]
[系统应用]
显示 0 个；Android 返回 1 个可见应用。系统可能限制可见范围；结束应用前会另行核对完整清单。
```

结论：**"0 个"不能证明管理器未安装。** 同一接口在无过滤时只返回 1 个应用（见 1.4），说明这是 Android 包可见性（package visibility）限制，而不是设备上真的只有 1 个应用。因此：

- 不能用本接口判定 `me.diksu.ultra` 是否安装；
- 更不能用它判定 versionCode/versionName；
- 该结论属于"**未取得证据**"，不是"**已证伪**"。需要 `dumpsys package me.diksu.ultra` 或 `pm list packages` 才能定论（见第二节）。

### 1.4 全量应用列表 `/app/apps`（对照用）

```bash
curl -s -m 25 -H @/root/.dsh/.bridge_headers "http://127.0.0.1:3090/app/apps"
```

输出（原文）：

```
[用户应用]
dsh.android.client	DSHA uid=10560
[系统应用]
显示 1 个；Android 返回 1 个可见应用。系统可能限制可见范围；结束应用前会另行核对完整清单。
```

结论：可见范围只有 DSHA 自己（uid 10560）。与 1.3 互相印证——`q=diksu` 的 0 结果是可见性限制的产物。同时说明**本轮无法通过应用列表侧证管理器的安装状态**。

### 1.5 传感器 `/app/sensors`

```bash
curl -s -m 25 -H @/root/.dsh/.bridge_headers "http://127.0.0.1:3090/app/sensors"
```

输出（原文）：

```
DISABLED 传感器能力未开启（设备能力授权页可开）
```

结论：能力未开启，非设备故障。传感器与管理器核验无关，**不申请开启**。

### 1.6 位置 `/app/location`

```bash
curl -s -m 25 -H @/root/.dsh/.bridge_headers "http://127.0.0.1:3090/app/location"
```

输出（原文）：

```
DISABLED 位置能力未开启：请到 DSHA「设备能力授权」页勾选「允许 agent 读取位置」
```

结论：同上，能力未开启。与管理器核验无关，**不申请开启**。

### 1.7 剪贴板 `/app/clip`（只读）

```bash
curl -s -m 25 -H @/root/.dsh/.bridge_headers "http://127.0.0.1:3090/app/clip"
```

输出（原文）：

```
[APP_BACKGROUND] 系统限制：只有 App 在前台时才能读剪贴板，可先用 /app/notify 提醒用户打开 DSHA
```

结论：系统级限制（App 不在前台，与 1.2 的 `app_foreground=false` 一致）。剪贴板不是本任务证据源，**不为此去拉前台**。

### 1.8 证据小结

| # | 接口 | 结果 | 可用于判定 4.1？ |
|---|---|---|---|
| 1 | `/app/version` | 有数据（桥/客户端版本） | 否，与项目无关 |
| 2 | `/app/device` | 有数据（机型/系统/电量/存储） | 是（设备前提） |
| 3 | `/app/apps?q=diksu` | 0 命中（可见性限制） | **否，且不可反证未安装** |
| 4 | `/app/apps` | 1 个可见应用（DSHA 自己） | 否（解释 3 的 0 结果） |
| 5 | `/app/sensors` | `DISABLED` | 否 |
| 6 | `/app/location` | `DISABLED` | 否 |
| 7 | `/app/clip` | `[APP_BACKGROUND]` | 否 |

**净结论：本轮没有任何一条证据能直接判定"管理器已安装 / versionCode=41000 / 内核模块已加载 / SuSFS 已加载 / Shizuku 在跑"。** 这些项全部依赖设备 shell 通道或无障碍读屏，本轮均不可用（`ACCESSIBILITY_UNAVAILABLE` / `[DEVICE_CHANNEL_UNAVAILABLE]`）。当前唯一被确证的事实是设备型号、Android 版本与低电量状态。

---

> 结论：本轮真机动态核验按用户要求取消（功能已通过）；以上为只读证据留档，供后续需要时参考。
