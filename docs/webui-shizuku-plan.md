# 网页端内置 Shizuku 管理方案（task-5）

> 状态：方案稿，**尚未改任何代码**。
> 本文档只描述实现方案；实际改动等 Lead 明确通知「task-1 已落地」后再开始。
> task-1 与 task-5 共用 `userspace/ksud/src/webui.rs`、`userspace/ksud/assets/web/index.html`，
> 在 task-1 落地前编辑这两个文件会互相覆盖。

## 1. 范围与结论

第一版只做三件事：

1. 看状态：`shizuku_server` 是否在跑、pid、运行身份、管理器包名/APK 路径。
2. 起停：用内置 `app_process` 命令启动；停止只结束 `shizuku_server` 进程。
3. 只读展示：开机自启开关与上次结果、授权应用数量与授权文件权限。

**授权写入不在第一版**：Shizuku 的授权写入要调 `Shizuku.updateFlagsForUid()` 的 binder，
ksud 侧没有 binder 通道；直接改 `shizuku.json` 也不会通知运行中的 server。网页端只显示
「授权请在 App 内修改」。

## 2. 从 ShizukuServiceManager 抄来的事实

来源：`manager/app/src/main/java/me/diksu/ultra/shizuku/ShizukuServiceManager.kt`
（下称「App 管理器」）。以下常量、命令、路径全部按原文件抄录，不重新发明。

### 2.1 常量

| 项 | 值 | 出处 |
|---|---|---|
| server 进程名 | `shizuku_server` | `SERVER_PROCESS_NAME` |
| server 入口类 | `rikka.shizuku.server.ShizukuService` | `SERVER_CLASS` |
| 启动日志 | `/data/local/tmp/shizuku_ksu.log` | `SERVER_START_LOG` |
| 启动等待 | 10 000 ms，轮询 200 ms | `SERVER_START_TIMEOUT_MS` / `SERVER_POLL_INTERVAL_MS` |
| 停止等待 | 20 × 250 ms = 5 000 ms | `waitStopped()` |
| 授权允许位 | `FLAG_ALLOWED = 1 shl 1`（2） | `FLAG_ALLOWED` |
| 授权拒绝位 | `FLAG_DENIED = 1 shl 2`（4） | `FLAG_DENIED` |

### 2.2 启动命令（原文）

App 管理器拼出的 `inner`（`ShizukuServiceManager.kt:207-212`）：

```sh
/system/bin/setsid -d /system/bin/env CLASSPATH="<APK>" /system/bin/app_process \
  -Djava.class.path="<APK>" \
  -Dshizuku.library.path="<NATIVE_LIB_DIR>" \
  -Dshizuku.manager.packageName="<PKG>" \
  /system/bin --nice-name=shizuku_server \
  rikka.shizuku.server.ShizukuService \
  >/data/local/tmp/shizuku_ksu.log 2>&1 </dev/null &
```

其中：

- `<APK>` = App 管理器的 `context.applicationInfo.sourceDir`；
- `<NATIVE_LIB_DIR>` = `context.applicationInfo.nativeLibraryDir`；
- `<PKG>` = `context.packageName`（隐藏/PR 包名场景由运行时传入，避免 server 找不到管理器）。

App 管理器随后把这串 `inner` 依次包进一组候选（`ShizukuServiceManager.kt:224-235`）：

1. `/system/bin/su -c '<inner>'`
2. `/system/bin/su 2000 -M -c '<inner>'`
3. `/system/bin/su -M 2000 -c '<inner>'`
4. `/system/bin/su 2000 -c '<inner>'`
5. `/system/bin/su - 2000 -c '<inner>'`
6. 动态解析到的 `suPath` 变体
7. 裸 `inner`

**ksud 侧的取舍**：ksud 本身以 root（euid 0）运行，不需要再套 `su`。因此只使用第 7 个候选
（裸 `inner`）；如果 euid 不是 0，直接报「需要 root」，不尝试 `su`（App 注释也提到嵌套 su 可能被拒）。
`fpdrop` 不在网页端部署：server 启动时 `ShizukuService.ensureFpDrop()` 会从管理器 APK 的
`assets/fpdrop` 自部署，App 管理器里的 `ensureFpDrop()` 只是提前兜底。

### 2.3 判活与停止（原文）

App 管理器：

```kotlin
// 判活：binder 可达才算「可用」
fun isServerRunning(): Boolean = try { Shizuku.pingBinder() } catch (t: Throwable) { false }

// 进程 pid：面板展示「进程活着但 binder 没起来」的半死状态
fun serverPid(): Int? {
    getRootShell().newJob().add("/system/bin/pidof $SERVER_PROCESS_NAME")...
}

// 停止：优先 binder 优雅退出，再 root 强杀
fun stop(): Boolean {
    if (Shizuku.pingBinder()) { Shizuku.exit(); if (waitStopped()) return true }
    getRootShell().newJob()
        .add("kill -9 \$(/system/bin/pidof $SERVER_PROCESS_NAME) 2>/dev/null || true")
        .exec()
    waitStopped()
}
```

**ksud 侧的限制**：ksud 没有 binder 通道，既不能 `pingBinder()` 也不能 `Shizuku.exit()`。所以：

- `running` 只能表示 **`pidof shizuku_server` 有进程**，不能表示 binder 一定可用；
- 停止只能 SIGKILL，不能优雅退出；
- App 的 `serverUid()` / `isRootServer()` 走 binder（`Shizuku.getUid()`）；ksud 只能读
  `/proc/<pid>/status` 的 `Uid:` 作为替代，两者在正常情况下应一致；
- 前端文案必须写清楚「binder 可用性请在 App 内确认」，不能把「进程活着」说成「服务可用」。

### 2.4 开机自启记录

`ShizukuAutostart.kt` 把结果写进 App 的 `shizuku_settings` SharedPreferences：

| key | 类型 | 含义 |
|---|---|---|
| `shizuku_service_enabled` | boolean | 自启开关 |
| `autostart_last_at` | long | 上次尝试时间（epoch ms） |
| `autostart_last_ok` | boolean | 上次是否成功 |
| `autostart_last_detail` | string | 失败原因/成功说明 |
| `autostart_last_source` | string | `boot` / `package-replaced` / `manual` / `enable-toggle` |

文件路径（`MODE_PRIVATE`，user 0）：

```
/data/user/0/<PKG>/shared_prefs/shizuku_settings.xml
```

`<PKG>` 取 `defs::DEFAULT_PACKAGE_NAME`（编译期与管理器 `applicationId` 一致）。
网页端只读这 5 个 key，**不写**、不返回原始 XML。

### 2.5 授权文件

路径（`ShizukuConfigManager.java:52`）：

```
/data/user_de/0/com.android.shell/shizuku.json
```

结构（`ShizukuConfig.java`）：

```json
{
  "version": 2,
  "packages": [
    { "uid": 10123, "flags": 2, "shellOnly": false, "packages": ["com.example.app"] }
  ]
}
```

- `flags & 2 != 0` → 已允许；`flags & 4 != 0` → 已拒绝；
- `shellOnly` → 该 uid 是否降权为 shell；
- server 会把文件权限归一化为 `0600`、owner `2000:2000`（`normalizeFileOwnership()`）。

**网页端只读取聚合值**：条数、允许/拒绝/仅 Shell 数量、文件 mode/uid/gid/size。
绝不把 `packages`、`uid`、`flags` 明细序列化到响应里（handoff 第九节：属敏感数据，不要整份打印）。

### 2.6 ksud 侧可复用的真实 helper

都在 `userspace/ksud/src/webui.rs` 或相邻模块，已存在，不需要新依赖：

| helper | 位置 | 用途 |
|---|---|---|
| `pm_query(&["-f", "-U"])` | `webui.rs`（`#[cfg(target_os = "android")]`） | `cmd package list packages` / `pm list packages`，输出带 APK 路径 |
| `parse_pm_lines()` | `webui.rs` | 解析 `package:<apk>=<name> uid:<n>` |
| `defs::DEFAULT_PACKAGE_NAME` | `defs.rs` | 管理器包名（编译期 `KSU_PACKAGE_NAME`） |
| `crate::utils::getprop()` | `utils.rs` | 读 `ro.product.cpu.abi` |
| `libc::kill()` | 已有 `libc` 依赖 | 按 pid 发 SIGKILL |
| `webui_files::require_root()` | `webui_files.rs` | 参考其 euid 检查方式；文案另写，避免「文件管理器」误导 |

**唯一需要替代 App 的地方**：App 用 `context.applicationInfo.sourceDir` / `nativeLibraryDir`，
ksud 没有 `Context`。方案：

- APK 路径：`pm_query(&["-f", "-U"])` 里找 `name == defs::DEFAULT_PACKAGE_NAME` 的那一行；
  失败再退回 `pm path <PKG>`，取 `package:` 后的路径。
- native lib 目录：优先 `cmd package dump <PKG>` 输出里的 `nativeLibraryDir=`；
  取不到时按 `apk.parent()/lib/<abi>` 派生，`<abi>` 从 `ro.product.cpu.abi` 映射
  （`arm64-v8a` → `arm64`，`armeabi-v7a` → `arm`，其余原样）。这一条必须在真机上确认一次。

## 3. 三条路由

统一响应包装沿用 `ApiResponse<T>`：

```json
{ "success": true, "data": { }, "error": null }
```

失败：

```json
{ "success": false, "data": null, "error": "可读的中文错误" }
```

所有路由都走 `handle_request()` 现有的 token 校验（cookie / `?k=` / 一次性 ticket），不新增绕过。
`POST` 启动/停止额外要求 euid 0。

### 3.1 `GET /api/shizuku/status`

**签名**

```text
GET /api/shizuku/status
请求体：无
响应：200 + ApiResponse<ShizukuStatus>
```

Rust：

```rust
#[cfg(target_os = "android")]
fn shizuku_status() -> Result<ShizukuStatus>;

// match 分支
("GET", "/api/shizuku/status") => {
    #[cfg(target_os = "android")]
    {
        match shizuku_status() {
            Ok(status) => json_response(stream, &ApiResponse::ok(status)),
            Err(e) => error_response(stream, 500, &format!("{e:#}")),
        }
    }
    #[cfg(not(target_os = "android"))]
    {
        error_response(stream, 500, "Not supported on this platform")
    }
}
```

**返回 JSON**

```json
{
  "success": true,
  "data": {
    "running": true,
    "pid": 23456,
    "pids": [23456],
    "uid": 0,
    "serverIsRoot": true,
    "binderReachable": null,
    "packageName": "me.diksu.ultra",
    "apkPath": "/data/app/~~ab12cd/me.diksu.ultra-xy34/base.apk",
    "autostart": {
      "readable": true,
      "enabled": true,
      "lastResult": "success",
      "lastTime": 1761300000000,
      "lastDetail": "第 1 次尝试成功",
      "lastSource": "boot"
    },
    "authorization": {
      "exists": true,
      "readable": true,
      "mode": "0600",
      "ownerUid": 2000,
      "ownerGid": 2000,
      "size": 412,
      "totalCount": 3,
      "allowedCount": 2,
      "deniedCount": 1,
      "shellOnlyCount": 1
    },
    "authorizedCount": 2
  },
  "error": null
}
```

**字段语义**

| 字段 | 类型 | 说明 |
|---|---|---|
| `running` | bool | `pidof shizuku_server` 是否返回至少一个 pid；**不代表 binder 可用** |
| `pid` | number \| null | 第一个 pid |
| `pids` | number[] | 全部 pid（正常只有 1 个） |
| `uid` | number \| null | 读 `/proc/<pid>/status` 的 `Uid:` 第一个值；读不到为 null |
| `serverIsRoot` | bool | `uid == 0` |
| `binderReachable` | null | 第一版恒为 null；ksud 无 binder 通道，预留字段 |
| `packageName` | string | `defs::DEFAULT_PACKAGE_NAME` |
| `apkPath` | string | 管理器 APK 路径；解析失败为空串 |
| `autostart.readable` | bool | App 的 `shizuku_settings.xml` 是否读到 |
| `autostart.enabled` | bool | `shizuku_service_enabled`；不可读时 false |
| `autostart.lastResult` | string | `never` / `success` / `failed` / `unknown` |
| `autostart.lastTime` | number \| null | epoch ms；没有记录为 null |
| `autostart.lastDetail` | string \| null | 截断到 200 字符 |
| `autostart.lastSource` | string \| null | 原始 source 值 |
| `authorization.exists` | bool | 授权文件是否存在 |
| `authorization.readable` | bool | 是否读到且能解析；权限不足/JSON 损坏为 false |
| `authorization.mode` | string \| null | 八进制权限，如 `"0600"` |
| `authorization.ownerUid` / `ownerGid` | number \| null | 文件 owner |
| `authorization.size` | number \| null | 文件字节数 |
| `authorization.totalCount` | number | `packages` 数组长度（仅数字） |
| `authorization.allowedCount` | number | `flags & 2` 的条数 |
| `authorization.deniedCount` | number | `flags & 4` 的条数 |
| `authorization.shellOnlyCount` | number | `shellOnly == true` 的条数 |
| `authorizedCount` | number | 等于 `allowedCount`，前端主展示用 |

`autostart.lastResult` 判定：

- `autostart_last_at <= 0` 或 key 不存在 → `never`；
- `autostart_last_ok == true` → `success`；
- 否则 → `failed`；
- prefs 读不到 → `unknown`（同时 `readable: false`）。

### 3.2 `POST /api/shizuku/start`

**签名**

```text
POST /api/shizuku/start
请求体：无（忽略；前端发送空 POST）
响应：200 + ApiResponse<ShizukuStartResult>；失败 500 + ApiResponse<null>
```

Rust：

```rust
#[cfg(target_os = "android")]
fn shizuku_start() -> Result<ShizukuStartResult>;

// match 分支
("POST", "/api/shizuku/start") => {
    #[cfg(target_os = "android")]
    {
        match require_shizuku_root().and_then(|()| shizuku_start()) {
            Ok(result) => json_response(stream, &ApiResponse::ok(result)),
            Err(e) => error_response(stream, 500, &format!("启动失败：{e:#}")),
        }
    }
    #[cfg(not(target_os = "android"))]
    {
        error_response(stream, 500, "Not supported on this platform")
    }
}
```

**返回 JSON（成功）**

```json
{
  "success": true,
  "data": {
    "started": true,
    "alreadyRunning": false,
    "pid": 23456,
    "pids": [23456],
    "packageName": "me.diksu.ultra",
    "apkPath": "/data/app/~~ab12cd/me.diksu.ultra-xy34/base.apk",
    "nativeLibraryDir": "/data/app/~~ab12cd/me.diksu.ultra-xy34/lib/arm64",
    "waitedMs": 1400
  },
  "error": null
}
```

已经运行：

```json
{
  "success": true,
  "data": {
    "started": true,
    "alreadyRunning": true,
    "pid": 23456,
    "pids": [23456],
    "packageName": "me.diksu.ultra",
    "apkPath": "/data/app/~~ab12cd/me.diksu.ultra-xy34/base.apk",
    "nativeLibraryDir": "/data/app/~~ab12cd/me.diksu.ultra-xy34/lib/arm64",
    "waitedMs": 0
  },
  "error": null
}
```

失败示例（HTTP 500）：

```json
{
  "success": false,
  "data": null,
  "error": "启动失败：10 秒内没有看到 shizuku_server 进程；日志尾部：<最多 2KB>"
}
```

**执行流程（对应 App 的 `startInternal`）**

1. `require_shizuku_root()`：euid 必须为 0，否则报「内置 Shizuku 管理需要 root 权限」。
2. 取 `SHIZUKU_OP_LOCK`（`try_lock`）；已有启动/停止在进行 → 报「另一个 Shizuku 操作正在进行」。
3. `pids = shizuku_pids()?`；非空 → 直接返回 `alreadyRunning: true`，不重复拉起。
4. `apk = manager_apk_path()?`；空 → 报「未找到管理器 APK」。
5. `libdir = manager_native_library_dir(&apk)`。
6. `rm -f /data/local/tmp/shizuku_ksu.log`（与 App 每次尝试前一致）。
7. 用**直接 argv** 复刻 `inner`（不经过 `sh -c`，避免任何拼接注入）：

   ```rust
   let log_file = std::fs::OpenOptions::new()
       .create(true)
       .truncate(true)
       .write(true)
       .open("/data/local/tmp/shizuku_ksu.log")?;
   let log_err = log_file.try_clone()?;

   let child = Command::new("/system/bin/setsid")
       .arg("-d")
       .arg("/system/bin/env")
       .arg(format!("CLASSPATH={apk}"))
       .arg("/system/bin/app_process")
       .arg(format!("-Djava.class.path={apk}"))
       .arg(format!("-Dshizuku.library.path={libdir}"))
       .arg(format!("-Dshizuku.manager.packageName={pkg}"))
       .arg("/system/bin")
       .arg("--nice-name=shizuku_server")
       .arg("rikka.shizuku.server.ShizukuService")
       .stdin(Stdio::null())
       .stdout(Stdio::from(log_file))
       .stderr(Stdio::from(log_err))
       .spawn()?;

   // setsid -d 会脱离会话；用独立线程回收退出状态，避免僵尸进程。
   std::thread::spawn(move || {
       let mut child = child;
       let _ = child.wait();
   });
   ```

   用独立线程 `child.wait()` 回收 `setsid` 的退出状态；`setsid -d` 负责脱离会话，
   `app_process` 的重定向等价于原命令的 `>... 2>&1 </dev/null &`。

8. 轮询 `shizuku_pids()`，每 200 ms 一次，最多 10 000 ms：
   - 看到 pid → 返回 `started: true`；
   - 超时 → 读 `/data/local/tmp/shizuku_ksu.log` 尾部最多 2KB（去掉控制字符），拼进错误。

> 注意：这里只等到「进程出现」，不等 binder 就绪（ksud 无法 ping binder）。前端在启动成功后
> 提示「进程已拉起，binder 就绪通常需要几秒，请在 App 内确认」，并延迟刷新一次状态。

### 3.3 `POST /api/shizuku/stop`

**签名**

```text
POST /api/shizuku/stop
请求体：无
响应：200 + ApiResponse<ShizukuStopResult>；失败 500 + ApiResponse<null>
```

Rust：

```rust
#[cfg(target_os = "android")]
fn shizuku_stop() -> Result<ShizukuStopResult>;

// match 分支
("POST", "/api/shizuku/stop") => {
    #[cfg(target_os = "android")]
    {
        match require_shizuku_root().and_then(|()| shizuku_stop()) {
            Ok(result) => json_response(stream, &ApiResponse::ok(result)),
            Err(e) => error_response(stream, 500, &format!("停止失败：{e:#}")),
        }
    }
    #[cfg(not(target_os = "android"))]
    {
        error_response(stream, 500, "Not supported on this platform")
    }
}
```

**返回 JSON（成功）**

```json
{
  "success": true,
  "data": {
    "stopped": true,
    "alreadyStopped": false,
    "killedPids": [23456],
    "waitedMs": 250
  },
  "error": null
}
```

本来就没运行：

```json
{
  "success": true,
  "data": {
    "stopped": true,
    "alreadyStopped": true,
    "killedPids": [],
    "waitedMs": 0
  },
  "error": null
}
```

失败示例（HTTP 500）：

```json
{
  "success": false,
  "data": null,
  "error": "停止失败：5 秒后 shizuku_server 仍在运行（pid 23456）"
}
```

**执行流程（对应 App 的 `stop()` / `killServerProcess()`）**

1. `require_shizuku_root()`。
2. 取 `SHIZUKU_OP_LOCK`（`try_lock`）。
3. `pids = shizuku_pids()?`；空 → 返回 `alreadyStopped: true`。
4. 对每个 pid：
   - 校验 `/proc/<pid>/comm` 去空白后等于 `shizuku_server`，或 `/proc/<pid>/cmdline`
     同时包含 `shizuku_server` 与 `rikka.shizuku.server.ShizukuService`；
   - 校验失败 → **不杀**，记录并报错（防 pid 复用误杀）；
   - 校验通过 → `libc::kill(pid as i32, libc::SIGKILL)`；`ESRCH` 当作已退出。
5. 轮询 `shizuku_pids()`，每 250 ms 一次，最多 5 000 ms：
   - 空 → `stopped: true`；
   - 超时 → 报「仍在运行」并带上剩余 pid。

**明确禁止**：`kill -9 $(pidof ...)` 这种整串、`killall`、`pkill -f`、`kill 0`、
`kill -<pgid>`。只按上面校验过的 pid 逐个 `libc::kill`，不碰进程组、不碰管理器 App、
不碰 Shizuku user-service 子进程。

### 3.4 非 Android / 错误约定

- 三条路由在 `#[cfg(not(target_os = "android"))]` 分支统一返回
  `500 "Not supported on this platform"`（照 `/api/features` 的写法）。
- `GET status` 在 Shizuku 未运行时必须返回 **200 + `running:false`**，不能 500。
- `POST start` 的「已经在运行」是成功（200），不是错误。
- `POST stop` 的「本来就没运行」是成功（200），不是错误。
- 所有错误必须带真实原因：启动失败带日志尾部；停止失败带剩余 pid；权限不足带 euid。

## 4. 后端实现细节

### 4.1 结构体（建议放在 `webui.rs` 现有 `FeatureState` 附近）

```rust
#[cfg(target_os = "android")]
#[derive(Serialize)]
#[serde(rename_all = "camelCase")]
struct ShizukuStatus {
    running: bool,
    pid: Option<u32>,
    pids: Vec<u32>,
    uid: Option<u32>,
    server_is_root: bool,
    binder_reachable: Option<bool>, // v1 恒 None
    package_name: String,
    apk_path: String,
    autostart: ShizukuAutostartStatus,
    authorization: ShizukuAuthorizationStatus,
    authorized_count: usize,
}

#[cfg(target_os = "android")]
#[derive(Serialize)]
#[serde(rename_all = "camelCase")]
struct ShizukuAutostartStatus {
    readable: bool,
    enabled: bool,
    last_result: String,
    last_time: Option<i64>,
    last_detail: Option<String>,
    last_source: Option<String>,
}

#[cfg(target_os = "android")]
#[derive(Serialize)]
#[serde(rename_all = "camelCase")]
struct ShizukuAuthorizationStatus {
    exists: bool,
    readable: bool,
    mode: Option<String>,
    owner_uid: Option<u32>,
    owner_gid: Option<u32>,
    size: Option<u64>,
    total_count: usize,
    allowed_count: usize,
    denied_count: usize,
    shell_only_count: usize,
}

#[cfg(target_os = "android")]
#[derive(Serialize)]
#[serde(rename_all = "camelCase")]
struct ShizukuStartResult {
    started: bool,
    already_running: bool,
    pid: Option<u32>,
    pids: Vec<u32>,
    package_name: String,
    apk_path: String,
    native_library_dir: String,
    waited_ms: u64,
}

#[cfg(target_os = "android")]
#[derive(Serialize)]
#[serde(rename_all = "camelCase")]
struct ShizukuStopResult {
    stopped: bool,
    already_stopped: bool,
    killed_pids: Vec<u32>,
    waited_ms: u64,
}
```

### 4.2 内部 helper

```rust
#[cfg(target_os = "android")]
fn require_shizuku_root() -> Result<()>;

#[cfg(target_os = "android")]
fn shizuku_pids() -> Result<Vec<u32>>;          // /system/bin/pidof shizuku_server；空=正常

#[cfg(target_os = "android")]
fn shizuku_process_uid(pid: u32) -> Option<u32>; // /proc/<pid>/status

#[cfg(target_os = "android")]
fn manager_apk_path() -> Result<String>;         // pm_query -f -U / pm path

#[cfg(target_os = "android")]
fn manager_native_library_dir(apk_path: &str) -> String; // pm dump / 派生

#[cfg(target_os = "android")]
fn read_shizuku_prefs() -> Option<ShizukuPrefs>;

#[cfg(target_os = "android")]
fn read_shizuku_authorization() -> ShizukuAuthorizationStatus;

#[cfg(target_os = "android")]
fn tail_log(path: &str, max_bytes: usize) -> String;
```

`shizuku_pids()`：

- `Command::new("/system/bin/pidof").arg("shizuku_server")`；
- exit code 1 + 空 stdout → `Ok(vec![])`；
- 解析 stdout 的空白分隔十进制 pid；非数字忽略；
- spawn 失败 → `Err`；
- 可选兜底：pidof 为空时扫描 `/proc/*/cmdline`，匹配
  `shizuku_server` 且包含 `rikka.shizuku.server.ShizukuService` 的进程。

`read_shizuku_authorization()`：

```rust
#[derive(serde::Deserialize, Default)]
#[serde(rename_all = "camelCase")]
struct ShizukuConfigFile {
    #[serde(default)]
    packages: Vec<ShizukuPackageEntry>,
}

#[derive(serde::Deserialize, Default)]
#[serde(rename_all = "camelCase")]
struct ShizukuPackageEntry {
    #[serde(default)]
    flags: i32,
    #[serde(default)]
    shell_only: bool,
}
```

只反序列化 `flags` / `shellOnly`，不反序列化 `uid` / `packages`，从类型层面保证不会把明细带出去。
文件 stat 用 `std::os::unix::fs::MetadataExt` 的 `mode() & 0o7777`、`uid()`、`gid()`、`len()`。

`read_shizuku_prefs()`：

内部结构（不序列化、不返回给前端）：

```rust
#[derive(Default)]
struct ShizukuPrefs {
    enabled: bool,
    last_at: i64,
    last_ok: bool,
    last_detail: String,
    last_source: String,
}
```

- 读 `/data/user/0/<PKG>/shared_prefs/shizuku_settings.xml`；
- 只取 2.4 表里的 5 个 key；
- 布尔/长整型读 `value="..."` 属性；字符串读 `<string name="...">...</string>` 内容并做
  `&amp; &lt; &gt; &quot; &apos;` 反转义；
- 任何读取/解析失败 → `None`，状态里 `autostart.readable=false`、`lastResult="unknown"`；
- `lastDetail` 截断到 200 字符（按 char 边界，避免切断 UTF-8）。

### 4.3 并发与超时

```rust
#[cfg(target_os = "android")]
static SHIZUKU_OP_LOCK: std::sync::Mutex<()> = std::sync::Mutex::new(());
```

- 启动/停止都用 `try_lock()`，拿不到立即返回「另一个 Shizuku 操作正在进行」；
- 启动轮询 10 s、停止轮询 5 s，与 App 的 `SERVER_START_TIMEOUT_MS` / `waitStopped()` 对齐；
- 每次操作结束释放锁；状态查询不需要锁。

### 4.4 插入位置

- 结构体/helper 放在 `webui.rs` 的 `get_features()` 附近，统一 `#[cfg(target_os = "android")]`；
- 路由分支放在 `/api/tools/reload-modules` 之后、`/api/reboot` 之前；
- 如果 task-1 的 SuSFS 路由已经占住这段，则紧跟 SuSFS 路由块之后，避免和 task-1 的 diff 交叉；
- 非 Android 分支照 `/api/features` 的写法；
- 不新增 Cargo 依赖：`serde` / `serde_json` / `libc` 都是现成的；XML 用手写属性解析，
  不引入 XML crate。

## 5. 前端区块

位置：`#page-settings` 里「工具」卡片之后、「WebUI」之前，新增一个 `内置 Shizuku` section。
沿用现有 `.section-title` / `.card` / `.pref-item` / `.fm-btn` 风格，不引入构建步骤。

### 5.1 DOM 结构

```html
<div class="section-title">内置 Shizuku</div>
<div class="card" id="shizukuCard">
  <div class="pref-item shizuku-row">
    <div class="pref-icon">
      <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"
           stroke-linecap="round" stroke-linejoin="round">
        <path d="M12 2l7 4v6c0 5-3 8-7 10-4-2-7-5-7-10V6z"/>
        <path d="M9 12l2 2 4-4"/>
      </svg>
    </div>
    <div class="pref-content">
      <div class="pref-title">
        <span class="shizuku-dot" id="shizukuDot"></span>
        <span id="shizukuStateText">检测中…</span>
      </div>
      <div class="pref-subtitle" id="shizukuStateDetail">正在读取 shizuku_server 状态</div>
    </div>
  </div>

  <div class="shizuku-actions">
    <button class="fm-btn primary" id="shizukuStartBtn" onclick="shizukuStart()">启动</button>
    <button class="fm-btn danger" id="shizukuStopBtn" onclick="shizukuStop()">停止</button>
    <button class="fm-btn" id="shizukuRefreshBtn" onclick="loadShizuku()">刷新</button>
  </div>
  <div class="shizuku-error" id="shizukuActionError"></div>

  <div class="pref-item shizuku-row">
    <div class="pref-content">
      <div class="pref-title">开机自启</div>
      <div class="pref-subtitle" id="shizukuAutostartText">读取中…</div>
    </div>
  </div>

  <div class="pref-item shizuku-row">
    <div class="pref-content">
      <div class="pref-title" id="shizukuAuthTitle">授权应用</div>
      <div class="pref-subtitle" id="shizukuAuthText">读取中…</div>
    </div>
  </div>

  <div class="pref-item shizuku-row">
    <div class="pref-content">
      <div class="pref-subtitle" id="shizukuAuthNote">
        授权请在 App 内修改；网页端只读展示，不写入授权文件。
      </div>
    </div>
  </div>

  <div class="pref-item shizuku-row">
    <div class="pref-content">
      <div class="pref-title">管理器</div>
      <div class="pref-subtitle shizuku-mono" id="shizukuManagerText">—</div>
    </div>
  </div>
</div>
```

### 5.2 CSS（加在现有 `.switch` 规则附近）

```css
.shizuku-row { cursor: default; }
.shizuku-row:active { background: transparent; }
.shizuku-dot {
  display: inline-block;
  width: 8px;
  height: 8px;
  margin-right: 8px;
  border-radius: 50%;
  background: var(--text-tertiary);
  vertical-align: middle;
}
.shizuku-dot.running { background: var(--success); }
.shizuku-dot.stopped { background: var(--text-tertiary); }
.shizuku-actions {
  display: flex;
  gap: 8px;
  padding: 12px 16px;
  border-bottom: 0.5px solid var(--border);
}
.shizuku-actions .fm-btn { flex: 1; min-height: 40px; }
.shizuku-error {
  display: none;
  padding: 0 16px 12px;
  color: var(--danger);
  font-size: 12px;
  line-height: 1.5;
  white-space: pre-wrap;
  word-break: break-word;
}
.shizuku-mono {
  font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
  font-size: 11px;
  word-break: break-all;
}
#shizukuAutostartText { white-space: pre-line; }
```

### 5.3 JS 函数

```js
let shizukuState = null;
let shizukuBusy = false;

function shizukuSetBusy(busy) { ... }       // 按 running 状态禁用启动/停止/刷新
function shizukuSetError(text) { ... }      // 显示/清空 #shizukuActionError
async function loadShizuku() { ... }        // GET /api/shizuku/status + render
function renderShizuku(status) { ... }      // 纯 textContent，不拼 innerHTML
async function shizukuStart() { ... }       // POST /api/shizuku/start
async function shizukuStop() { ... }        // POST /api/shizuku/stop（先 confirm）
```

关键行为：

- `loadSettings()` 末尾加 `loadShizuku();`；下拉刷新/页面激活走现有 `refreshCurrentPage()`，
  已经在 settings 页时自然再次调用 `loadSettings()`。
- 所有动态文本用 `textContent` 或 `escapeHtml()`；`lastDetail`、`apkPath`、错误消息都可能带特殊字符。
- `shizukuStart()` 成功后 `showToast('已启动（pid N）')`，并 `setTimeout(loadShizuku, 3000)` 再刷一次，
  让用户看到 pid；不假装 binder 已就绪。
- `shizukuStop()` 先 `confirm('停止 Shizuku 会中断正在使用它的应用，继续？')`。
- 失败时：
  - `showToast('启动失败：' + e.message)`；
  - 同时把完整错误写进 `#shizukuActionError`（`white-space: pre-wrap`），不能只弹「成功」。
- 按钮状态：
  - `running=true`：启动禁用、停止可用；
  - `running=false`：启动可用、停止禁用；
  - `shizukuBusy=true`：三个按钮都禁用，文案变「启动中… / 停止中…」。

### 5.4 文案

| 场景 | 主标题 | 副标题 |
|---|---|---|
| 读取中 | 检测中… | 正在读取 shizuku_server 状态 |
| 运行中 | 运行中 | `pid <pid> · root/shell；binder 可用性请在 App 内确认` |
| 已停止 | 已停止 | shizuku_server 未运行 |
| 读取失败 | 读取失败 | `<error>` |

开机自启：

- prefs 不可读：`无法读取（需在 App 内查看）`
- 已关闭：`已关闭（在 App 内修改）`
- 已开启 + 无记录：`已开启 · 本次安装后尚未尝试过自启`
- 已开启 + 成功：`已开启 · 上次自启成功 · <时间> · <来源>`
- 已开启 + 失败：`已开启 · 上次自启失败 · <时间> · <来源>` + 换行 + `lastDetail`
- 来源映射：`boot` → 开机，`package-replaced` → 应用更新，`manual` → 手动，`enable-toggle` → 打开开关时；
  未知值原样显示。

授权：

- 标题：`授权应用 <authorizedCount> 个`
- 文件不存在：`授权文件不存在（尚未授权任何应用）`
- 不可读：`授权文件不可读（需要 root）`
- 正常：`允许 <allowedCount> · 拒绝 <deniedCount> · 仅 Shell <shellOnlyCount>`
- 权限异常（mode != `0600` 或 ownerUid != 2000）：追加
  ` · 权限异常：<mode> / uid <ownerUid>`，用 danger 色。
- 固定说明：`授权请在 App 内修改；网页端只读展示，不写入授权文件。`

管理器行：`<packageName>` + 换行 + `<apkPath>`（mono、可换行；路径过长用 `title` 显示完整值）。

### 5.5 刷新策略

- 页面加载：`loadSettings()` 调用一次；
- 下拉刷新：现有 `refreshCurrentPage()` 已覆盖；
- 启动/停止后：立即 `loadShizuku()`；启动成功再延迟 3 秒补一次；
- 不做定时轮询，避免网页端空闲时反复读 `/proc` 和授权文件。

## 6. 安全边界

1. **授权文件只读聚合**
   - 只读 `/data/user_de/0/com.android.shell/shizuku.json`；
   - 响应只包含 `totalCount / allowedCount / deniedCount / shellOnlyCount` 与文件
     `mode / ownerUid / ownerGid / size`；
   - 不返回 `uid`、包名、`flags` 数组、原始 JSON、文件内容；
   - 解析失败只记「路径 + 错误类型」，不把内容写日志；错误响应也不带原文；
   - 前端不展示任何授权明细，只显示数量和「授权请在 App 内修改」。

2. **停止只杀 shizuku_server**
   - 目标集合严格等于 `/system/bin/pidof shizuku_server` 返回的 pid；
   - 每个 pid 先校验 `/proc/<pid>/comm` / `cmdline`，再 `libc::kill(pid, SIGKILL)`；
   - 禁止 `kill -9 $(pidof ...)`、`killall`、`pkill -f`、`kill 0`、`kill -<pgid>`；
   - 不杀管理器 App、不杀 `app_process` 父进程、不杀 Shizuku user-service 子进程；
   - 5 秒内没停干净就返回真实错误，不假装成功。

3. **启动不接受请求参数**
   - 类名固定 `rikka.shizuku.server.ShizukuService`；
   - 进程名固定 `shizuku_server`；
   - APK 路径、包名、native lib 目录全部由 ksud 在服务端解析；
   - 用 `Command` 直接传 argv，不经过 `sh -c`，没有命令注入面。

4. **权限**
   - 三条路由继承 WebUI token 校验（cookie / `?k=` / 一次性 ticket）；
   - `POST start/stop` 额外要求 euid 0；
   - `GET status` 允许非 root 读取，但授权文件读不到时只返回 `readable:false`，不报 500。

5. **日志**
   - `/data/local/tmp/shizuku_ksu.log` 只在启动失败时读尾部最多 2KB；
   - 去掉控制字符后放进错误消息；
   - 不读取、不打印 `shizuku.json` 的内容。

## 7. 第一版不做

| 不做 | 原因 |
|---|---|
| 授权写入 / 撤销 / 一键允许 | 需要 Shizuku binder（`Shizuku.updateFlagsForUid`），ksud 没有 binder 通道；直接改 JSON 不会通知运行中的 server，还可能被 server 覆盖 |
| 授权应用明细列表、搜索、筛选 | 安全边界：不返回 uid/包名/flags 明细 |
| 开机自启开关 | 需要写 App 私有 SharedPreferences；第一版只读展示，开关仍在 App 内改 |
| 「立即自启一次」 | 需要发广播/启动 App 组件，超出网页端权限边界 |
| 服务日志页 / 清空日志 | 第一版只在启动失败时回传有限日志尾部；完整日志仍回 App |
| 重启服务 / 自动拉起 / 守护进程 | 只做 start / stop |
| binder 级健康检查 | ksud 无 binder 通道；`running` 只代表进程存活 |
| 多用户 / 工作资料 | 只处理 user 0 |
| 隐藏管理器的随机包名适配 | 按 `defs::DEFAULT_PACKAGE_NAME` 处理；随机包名场景需另做实际包名解析 |
| 修改 Kotlin / Shizuku server | task-5 只改 `webui.rs` + `index.html` |
| 非 Android 平台实现 | 统一返回 `500 Not supported on this platform` |

## 8. 验收与自检

代码层：

- [ ] 三条路由都在 `webui.rs` 的 `match` 里有分支；非 Android 分支返回 500。
- [ ] JSON 字段名前后端一致（camelCase），`success/data/error` 包装一致。
- [ ] 没有 `unwrap()` 造成 panic 的路径；所有文件读取/命令执行都走 `Result`。
- [ ] `grep` 确认没有把 `me.weishu` / `me.diksu.kernelsu` 等旧包名带进来。
- [ ] 抽出的前端 `<script>` 用 `node --check` 通过。
- [ ] rustfmt 合规（4 空格、尾逗号、行宽）；本地没有 cargo/rustfmt 时靠 CI 的 clippy/rustfmt。
- [ ] `grep -n "kill(-"` / `killall` / `pkill` 确认停止实现没有整组 kill。

真机层（等 task-4 的通道恢复后）：

- [ ] `GET /api/shizuku/status` 的 `running` 与 `pidof shizuku_server` 一致；
- [ ] 网页端点「启动」→ `pidof` 出现 pid；几秒后 App 面板显示运行中；
- [ ] 网页端点「停止」→ pid 消失；App 面板显示已停止；
- [ ] 启动/停止后管理器 App 仍在运行，其他 `app_process` 进程不受影响；
- [ ] `authorizedCount` 与 App 面板「已授权」数量一致；
- [ ] 状态响应里搜不到授权文件的包名/uid 明细；
- [ ] 非 root 启动（如果存在这种入口）返回可读错误，不崩溃。

## 9. 风险与待确认

1. **APK 路径 / native lib 目录**：App 用 `context.applicationInfo`，ksud 只能用 `pm` 查询和派生。
   `cmd package dump` 的 `nativeLibraryDir=` 字段与 `apk.parent()/lib/<abi>` 的映射需要真机确认一次。
2. **`running` 语义**：进程存活 ≠ binder 可用。如果用户看到网页端「运行中」但 App 显示未运行，
   属于预期差异；前端文案已明确提示，必要时先停止再启动。
3. **启动返回时机**：只等到进程出现，不等到 binder 就绪；前端延迟 3 秒刷新，仍可能比 App 慢。
4. **App 私有 prefs 路径**：`/data/user/0/<PKG>/shared_prefs/shizuku_settings.xml` 依赖包名与
   `defs::DEFAULT_PACKAGE_NAME` 一致；PR 构建/随机包名场景读不到时显示「无法读取」。
5. **`setsid -d` 行为**：与 App 使用的命令一致；如果某些 ROM 的 toybox `setsid` 不支持 `-d`，
   启动会失败并在日志里体现，需要真机确认。
6. **授权文件延迟**：App 刚改完授权时，`shizuku.json` 可能还没落盘，网页端数量可能短暂旧值；
   刷新即可。

## 10. 实施顺序（等 Lead 通知后）

1. 确认 task-1 已落地：`git status` / `git diff` 看 `webui.rs`、`index.html` 的 SuSFS 改动已在工作区，
   且没有未解决的冲突。
2. 先在 `webui.rs` 加结构体/helper/三条路由，保持 `#[cfg(target_os = "android")]` 边界。
3. 再在 `index.html` 加 DOM/CSS/JS，`loadSettings()` 挂 `loadShizuku()`。
4. 本地静态检查：`node --check`、旧包名 grep、kill 方式 grep。
5. 触发 CI（`build-manager.yml`，`abi=arm64-v8a`）看编译/clippy/rustfmt；本容器没有 Android SDK，
   编译验证只能靠 CI。
6. 真机按第 8 节逐项验收。
