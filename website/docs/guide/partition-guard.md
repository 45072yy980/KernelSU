# 分区保护

越狱本身不改分区，但**越狱之后拿到的 root 可以改**。所以安装模块前有一层静态扫描：
把模块里会被执行的脚本逐行读一遍，给出「会不会真正动分区」的结论。

## 三级判定

| 等级 | 判什么 | 处置 |
|---|---|---|
| 🟢 绿 | 没有触及真实分区（模块自己的 `$MODPATH/system` 是 overlay，不算） | 直接安装 |
| 🟡 黄 | 会改真实分区，或存在无法静态审计的动态执行（`eval`、`base64 -d`、`curl \| sh`） | 打印报告，**等用户按音量键**：音量+ 继续 / 音量- 取消 / 60 秒无操作取消 |
| 🔴 红 | 不可逆或影响引导链（直写块设备、格式化、刷镜像、写 boot/vbmeta、关 AVB） | **禁止安装** |

## 报告长什么样

每条命中都给 **文件:行号 + 原文片段 + 风险说明**：

```
🔴 严重（不可逆，禁止安装）
  1. service.sh:42  [R4] 写入引导分区
     dd if=... of=/dev/block/by-name/boot
     风险：写 boot / init_boot / vbmeta / super，会破坏引导链，设备可能无法开机
```

## 扫描范围

- 10 个固定脚本名（`customize.sh`、`install.sh`、`service.sh`、`post-fs-data.sh` …）
- 递归所有 `*.sh`
- 带 shebang 的可执行文件

单文件 2 MiB、最多 200 个脚本；**任一上限触发时降级为黄色** —— 没法完整审计就不许直接装。

## 覆盖的分区

```
/system  /system_ext  /system_dlkm
/vendor  /vendor_dlkm
/product /odm  /oem
/my_product  /my_stock  /my_carrier  /my_region  /my_bigball  /my_manifest
```

`/my_*` 是国产 ROM 常见的那批厂商分区。

## 开关

- 顶层开关在「设置」里。
- 越狱模式下**始终开启**（那个会话没法把分区还原）。
- 4.1.1 起 `ksud` 默认开启扫描，管理器关闭时显式传 `KSU_PARTITION_GUARD=0`。
