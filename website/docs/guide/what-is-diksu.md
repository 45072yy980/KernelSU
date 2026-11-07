# DikSU 是什么

DikSU 是 KernelSU 的一个分支，面向**没有解锁 Bootloader**、也不想改 boot / system 分区的设备。

它沿用 KernelSU 的三件套：

| 组件 | 位置 | 作用 |
|---|---|---|
| 内核模块 | 运行时注入 | 超级用户、模块挂载、UTS、网络隔离、SUSFS 的底座 |
| `ksud` | `/data/adb/ksud` | 用户态守护进程与命令行 |
| 管理器 APK | 桌面图标 | 授权、模块、特性配置与诊断 |

区别只在**内核模块怎么进去**：官方 KernelSU 要求解锁并刷入改动过的 boot 镜像；DikSU 用漏洞把模块挂进**正在运行**的系统。这套流程在本站统一叫[越狱模式](/guide/jailbreak)。

## 启动链

```
开机
 └─ 系统正常启动（boot 分区未被修改）
     └─ 你打开管理器，点「激活越狱」
         ├─ ghostlock  → 拿到内核写权限 → 注入 kernelsu.ko
         └─ DirtyFrag  → 同上，另一条漏洞链
             └─ ksu_late_loaded = true
                 └─ 管理器拿到 root，模块与特性全部可用
```

重启后回到第二步，需要重新激活。这是「不碰 boot」的代价，也是它能用在不许解锁设备上的原因。

## 和 Magisk / 官方 KernelSU 的区别

| | Magisk | 官方 KernelSU | DikSU |
|---|---|---|---|
| 实现层 | boot 镜像里的 init | 内核模块（编进内核或 LKM） | 内核模块（运行时注入） |
| 需要解锁 | 是 | 是 | **否** |
| root 是否常驻 | 是 | 是 | 否（重启失效） |
| 模块格式 | Magisk 模块 | Magisk 模块 | Magisk 模块 |
| 隐藏手段 | 随机包名 + Zygisk | 隐身模式 + SUSFS | 隐身模式 + SUSFS + UTS |

## 适合谁

- 手机不能解锁：运营商定制机、保修期内、企业设备。
- 只想要 root 能力，不想每次 OTA 之后重新刷 boot。
- 能接受「漏洞依赖内核版本，重启后要重新激活」。

## 不适合谁

- 需要 root 常驻、开机即生效的场景 —— 请用官方 KernelSU 刷 boot。
- 已经在用官方 KernelSU 且一切正常 —— 没有必要换。
- 内核版本不在两条漏洞链的区间内 —— 装上也激活不了。

## 下一步

- [安装与首次启动](/guide/install)
- [越狱模式](/guide/jailbreak)
- [管理器导览](/guide/manager)
