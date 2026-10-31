# 系统分区保护 v2（4.1.1）：三级判定 + 音量键确认

> 本文是 4.1.1 的实现规范。**所有等级、规则、文案以本文为准**，实现时不要各自发挥。
> 旧实现见 `docs/partition-guard.md`（v3.4.0 的运行时拦截，4.1.1 起删除）。

---

## 0. 一句话

模块安装前**静态扫描**模块内所有会被执行的脚本，把"哪里要写分区、写了有什么后果"逐条列出来，
按 **绿 / 黄 / 红** 三级处置：绿色直接装，黄色让用户**按音量键**决定，红色**任何情况都不许装**。
运行时内核拦截（`partition_guard_runtime`，feature 8）**整块删除**。

---

## 1. 三级语义

| 等级 | 含义 | 处置 |
|---|---|---|
| 🟢 绿 | 没有触及真实分区 | 直接安装，报告里仍列出扫描范围（证明扫过） |
| 🟡 黄 | 会改真实分区，或存在无法静态审计的动态执行 | 打印完整报告，**等用户按音量键**：音量+ 继续 / 音量- 取消；60 秒无操作 = 取消 |
| 🔴 红 | 不可逆或影响引导链 | **禁止安装**，`--force` 也不能绕过；只打印报告并退出 |

**`--force` 的新语义**：只跳过 🟡 的按键确认，**永不**放行 🔴。
（旧行为是 `--force` 可以放行一切，本次收紧。）

**何时启用**：与现状一致——越狱（late-load）模式**始终启用**；普通模式由设置项
「系统分区保护」决定（安装时以 `KSU_PARTITION_GUARD=1` 传入）。

---

## 2. 扫描范围

必须覆盖"模块里的脚本会被执行到的所有位置"，而不是只看 10 个固定文件名：

1. 模块根目录固定脚本名（不论扩展名）：
   `customize.sh`、`install.sh`、`uninstall.sh`、`post-fs-data.sh`、`post-mount.sh`、
   `service.sh`、`boot-completed.sh`、`late-load.sh`、`metainstall.sh`、`metamount.sh`
2. 模块目录下**递归**所有 `*.sh`（含 `system/bin/*.sh`、`scripts/`、`bin/` 等子目录）
3. **其它可执行脚本**：有 shebang（`#!` 开头）且无扩展名的文件（很多模块直接放 `system/bin/foo`）
4. 单个文件上限 2 MiB，最多扫 200 个脚本（防 zip 炸弹）；超限时报告里注明"已截断"。

> 注意：`system/` 目录本身是 overlay，不碰真实分区；但 `system/` 里的**脚本**会被执行，
> 所以照样要扫。"写 `$MODPATH/system/...`" 属于 🟢，不是 🟡。

---

## 3. 规则表

### 🔴 红：不可逆 / 影响引导链

| # | 检测目标 | 匹配要点（示意，实现要更宽松） | 风险文案（原样打给用户） |
|---|---|---|---|
| R1 | 裸写块设备 | `dd ... of=/dev/block/...`、`> /dev/block/...`、`tee /dev/block/...`、`cp ... /dev/block/...`、`cat ... > /dev/block/...` | 直接把数据写进分区原始扇区，文件系统与校验值立刻损坏，**重启也修不回来**，只能重刷整机 |
| R2 | 格式化 / 抹除 | `mkfs*`、`mke2fs`、`wipefs`、`blkdiscard`、`sgdisk`、`parted`、`sfdisk`、`e2fsck -y` 指向 `/dev/` | 抹掉分区元数据，整分区数据不可恢复 |
| R3 | 刷镜像 | `fastboot flash`、`flash_image`、`nandwrite`、`flashcp`、`update_engine`、`payload.bin` 写入 | 覆盖 boot/system/vbmeta，**可能导致设备无法开机** |
| R4 | 写引导分区 | 任何写操作目标匹配 `/dev/block/.../(boot\|init_boot\|vendor_boot\|dtbo\|vbmeta\|super\|aboot\|sbl1)` | 破坏引导链，是变砖概率最高的一类操作 |
| R5 | 关闭 AVB / verity | `avbctl`、`disable-verity`、`set-avb`、`dm-verity ... disable` | 破坏 verified boot；OTA 会失败，部分机型直接拒绝启动 |
| R6 | 裸设备挂载写 | `mount … rw/remount … /dev/block/…`（只读挂载降为 Y6） | 绕过文件系统层直接改分区 |
| R7 | 无法解析的块设备写入 | 变量间接指向 `/dev/block`（如 `DEV=/dev/block/by-name/boot; dd of=$DEV`）且静态无法确定目标 | 无法审计目标，按最坏情况处理 |

### 🟡 黄：会改真实分区 / 无法静态审计

| # | 检测目标 | 匹配要点 | 风险文案 |
|---|---|---|---|
| Y1 | 系统分区 remount 读写 | `mount -o remount,rw <sys>`、`mount -o rw,remount <sys>`（两种参数顺序都要匹配） | 把只读系统分区改成可写；此后对它的修改**会真实落到分区**，重启不会还原 |
| Y2 | 直接改系统分区文件 | `sed -i`、`cp/mv/rm/ln/install`、`echo … >`、`chmod/chown` 作用于 `/system`、`/system_ext`、`/vendor`、`/product`、`/odm`、`/my_*` | 直接改系统文件；写坏会导致无法开机或 OTA 失败，通常只能重刷 |
| Y3 | 强制块设备可写 | `blockdev --setrw` | 让只读块设备接受写入 |
| Y4 | 混淆 / 动态执行 | `eval`、`base64 -d \| sh`、`xxd -r`、`printf '\x..' \| sh`、`sh -c "$(…)"` | 脚本内容被编码/拼接，**无法静态审计**，可能藏着任意写分区逻辑 |
| Y5 | 联网取回并执行 | `curl … \| sh`、`wget -O- … \| sh`、`curl -o x && sh x` | 实际执行的代码在安装时才会下载，**无法审计** |
| Y6 | 挂载裸块设备 | `mount … /dev/block/…`（可写方式见 R6，直接判红） | 只读挂载本身不能写，但脚本随后可能重挂可写、或把 fd 交给别的工具 |

> 判定优先级：同一行/同一文件同时命中红与黄时，**红优先**。
> 一个模块只要有一处 🔴 就是 🔴，不接受"其它都是绿的"来调和。

### 🟢 绿：放行（但要打印扫描摘要）

- 写模块自己的 overlay：`$MODPATH/system/…`、`./system/…`、`$MODPATH/…`
- 只读命令：`cat`、`grep`、`ls`、`stat`、`getprop`、`dumpsys`、`mount`（无 `remount`/`rw`）、`df`
- 应用/系统设置类：`pm`、`am`、`settings put`（不是分区写入）
- 普通文件读写：`/data/adb`、`/sdcard`、`/tmp`

**注意**：扫描器不能因为"模块写了 `system/` 文件"就报警——那是 overlay，正是模块的正常用法。

---

## 4. 报告格式（用户可见）

```
🔍 系统分区保护 · 扫描报告
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
模块：<id>  扫描：<N> 个脚本 / <M> KiB
结论：🔴 禁止安装   （或 🟡 需要确认 / 🟢 未发现风险）

🔴 严重（不可逆，禁止安装）
  1. service.sh:42   dd if=/dev/zero of=/dev/block/by-name/boot bs=1M
     风险：写引导分区（boot），会破坏引导链，设备可能无法开机
     → 只有重刷整机能恢复

🟡 需要确认（会改真实分区）
  1. customize.sh:17  mount -o remount,rw /system
     风险：把只读系统分区改成可写，之后对它的修改会真实落到分区，重启不还原

🟢 已检查
  ✓ 未发现真实分区写入（脚本 6 个，已全部扫描）
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
```

- 每条必须给：**文件:行号**、**原文片段**（截断 80 字符）、**风险说明**。
- 超过 20 条时只显示前 20 条，并注明"还有 N 处未显示"。
- 未启用保护时**不打印**（不打扰普通模式用户）。

---

## 5. 音量键确认协议

仅在 🟡 时触发：

```
🟡 检测到 3 处需要确认的操作（见上）
   [音量+] 继续安装      [音量-] 取消
   60 秒无操作将自动取消
```

实现要求：

1. 枚举 `/dev/input/event*`，逐个打开，`poll()` 带超时读取 `input_event`；
   识别 `EV_KEY`(1) 的 `KEY_VOLUMEUP`(115) / `KEY_VOLUMEDOWN`(114)，只看按下（value=1）。
2. **不要** `EVIOCGRAB`（不独占设备，系统音量照常工作）。
3. 超时 60 秒 → 取消（安全默认）。
4. 一个输入设备都打不开（无权限/无设备）→ 打印"读不到音量键"，取消，并提示
   `--force` 或改到终端安装。
5. 终止信号（Ctrl-C / kill）时按"取消"处理，不能留下半安装状态。

---

## 6. 运行时拦截：删除

以下全部移除（4.1.1）：

- `kernel/feature/partition_guard.c`、`partition_guard.h`（整文件删除）
- `kernel/Kbuild` 的 `feature/partition_guard.o`
- `kernel/hook/syscall_hook_manager.c` 的 include / `ksu_partition_guard_init()` / `_exit()`
- `kernel/include/uapi/feature.h` 的 `KSU_FEATURE_PARTITION_GUARD_RUNTIME = 8`
  （`KSU_FEATURE_PARTITION_GUARD = 7` 保留，静态扫描仍用它）
- `userspace/ksud/src/feature_id.rs` 的 `PartitionGuardRuntime`
  （`ALL` 从 8 项改 7 项；`from_u32` / `name` / `description` 对应分支删除）
- 管理器：`OtherFeaturesScreen` 的运行时子开关、`SettingsRepositoryImpl` 的
  `isRuntimeGuardEnabled` / `setRuntimeGuardEnabled` / `syncRuntimeGuardToKernel`、
  `KEY_RUNTIME_GUARD`、相关字符串
- 已保存的 feature 配置里若残留 id 8：加载时**静默忽略**，不得报错（`apply_config` 走
  `from_u32`，确认未知 id 被跳过即可）

---

## 7. 验收

| 项 | 标准 |
|---|---|
| 红 | 含 `dd of=/dev/block/by-name/boot` 的模块 → 必被拒，`--force` 也拒 |
| 黄 | 含 `mount -o remount,rw /system` 的模块 → 打印报告并等待按键；音量+ 继续、音量- 取消、超时取消 |
| 绿 | 普通模块（只写 `$MODPATH/system`）→ 直接安装，报告显示"未发现真实分区写入" |
| 报告 | 每条含 文件:行号 + 片段 + 风险说明 |
| 运行时 | 内核里搜不到 `partition_guard_runtime`；管理器无该子开关；老配置不报错 |
