# UTS 视图

让指定应用看到的「内核信息」变成另一台机器 —— 按 uid 伪装 `uname(2)`。

## 能改什么

| 字段 | 对应 `uname` 输出 | 例子 |
|---|---|---|
| sysname | 内核名 | `Linux` |
| nodename | 主机名 | `localhost` |
| release | 内核版本 | `5.15.0-android13` |
| version | 构建信息 | `#1 SMP PREEMPT ...` |
| machine | 架构 | `aarch64` |
| domainname | 域名 | *（通常留空）* |

留空的字段不会被改写：只填 release 时，`uname -s` 仍然是真实值。

## 三种模式

- **关闭**：完全不干预。
- **全局生效**：所有进程都看到伪装值。
- **仅指定应用**：只对勾选的应用生效，最多 64 个。

## 用法

1. 选「仅指定应用」。
2. 依次填要改的字段（留空 = 不改）。
3. 搜索并勾选目标应用。
4. 点「应用配置」。

改完立刻生效，不需要重启应用（已经在跑的进程下次调用 `uname` 就能看到）。

## 边界：它只影响 `uname(2)`

| 读法 | 是否被伪装 |
|---|---|
| `uname -r`、`getconf`、Java 的 `os.version` | ✅ |
| `cat /proc/sys/kernel/osrelease` | ❌ 真值 |
| `cat /proc/version` | ❌ 真值 |
| `/sys` 下的同名信息 | ❌ 真值 |

这是**故意的取舍**：改 `uts_namespace` 能让 `/proc` 也一起变，但要动 namespace，
崩溃风险高；用 syscall 钩子只改返回值，代价是覆盖面窄一档。

::: tip 检测怎么绕过
如果对方同时读 `uname` 和 `/proc`，读 `/proc` 的那一半会露馅。需要在两方面都自洽时，
配合 [SUSFS](/guide/susfs) 的伪 uname，或者干脆不看这类检测。
:::

## 命令行

```bash
ksud uts get
ksud uts set < config.json
```

```json
{
  "mode": 2,
  "field_mask": 4,
  "target_uids": [10123],
  "release": "5.15.0-android13"
}
```

`mode`：0 关闭 / 1 全局 / 2 仅指定应用。
`field_mask` 是位掩码：sysname=1、nodename=2、release=4、version=8、machine=16、domainname=32。

## 排查

| 现象 | 原因 |
|---|---|
| `uname -r` 没变 | 应用不在目标列表，或 mode 不是 1/2 |
| 所有应用都变了 | mode 是「全局生效」 |
| 字段变了但截断 | 每个字段最长 64 字节，超了会被截断 |
| 页面显示「不支持」 | 内核模块没加载（越狱未激活） |
