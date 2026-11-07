# UTS 视图

用来看起来像另一台内核：把 `uname(2)` 的返回值按 uid 换掉。

## 能改什么

| 字段 | 对应 `uname` 输出 |
|---|---|
| sysname | 内核名（Linux） |
| nodename | 主机名 |
| release | 内核版本（`uname -r`） |
| version | 构建信息 |
| machine | 架构（aarch64） |
| domainname | 域名 |

留空的字段不会被改写 —— 只填 release 时，`uname -s` 仍然是真实值。

## 三种模式

- **关闭**：完全不干预。
- **全局生效**：所有进程看到的都是伪装后的值。
- **仅指定应用**：只对勾选的应用生效，最多 64 个。

## 一定要知道的范围

**只改写 `uname(2)` 的返回值**，不影响：

- `/proc/sys/kernel/{ostype,osrelease,version,hostname}`
- `/sys` 下的同名信息
- 直接读这些文件的检测

也就是说，用 `uname -r` 看是伪装值，`cat /proc/sys/kernel/osrelease` 看还是真值。这是走系统调用钩子的必然结果：不动 namespace，就没有崩溃风险，但覆盖面比改 namespace 窄一档。

## 命令行

```bash
ksud uts get            # 打印当前配置（JSON）
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

`mode`：0 关闭、1 全局、2 仅指定应用；`field_mask` 是六个字段的位掩码（sysname=1、nodename=2、release=4、version=8、machine=16、domainname=32）。
