# ksud 命令行

`ksud` 是内核模块的用户态搭档，路径 `/data/adb/ksud`。管理器里的操作大多最终落到它身上。
命令行适合排查和脚本化。

## 基础

```bash
ksud --version          # ksud 自身版本
ksud debug              # 调试信息
ksud kernel             # 内核相关信息
ksud boot-info          # 当前分区/槽位信息
```

## 模块

```bash
ksud module list                 # 列出已装模块
ksud module install <zip 路径>    # 安装模块（会走分区保护扫描）
ksud module uninstall <模块 id>
ksud post-fs-data                # 手动跑阶段脚本（等价于「重载模块」的一部分）
ksud services
ksud boot-completed
ksud soft-reboot                 # 软重启
```

## UTS 视图

```bash
ksud uts get
ksud uts set < config.json
```

## 网络隔离

```bash
ksud netisolate get
ksud netisolate set < config.json
```

## SUSFS

```bash
ksud susfs status       # 内核是否加载、版本、能力清单
ksud susfs import       # 从 stdin 读配置
ksud susfs export
ksud susfs preview
```

## 特性开关

```bash
ksud feature get su_compat
ksud feature set su_compat 1
```

可用 ID：`su_compat`、`kernel_umount`、`sulog`、`adb_root`、`selinux_hide`、`shell_su_adb_only`。

## 隐身与诊断

```bash
ksud stealth get
ksud stealth set 1
ksud sulogd             # 启动提权日志守护进程
```

## 注意

::: warning 大多数命令需要 root
在越狱模式下先确认会话仍然有效（`su -c 'ksud --version'`）。重启后需要重新激活。
:::
