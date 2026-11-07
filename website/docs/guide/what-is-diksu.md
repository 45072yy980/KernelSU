# DikSU 是什么

DikSU 是 KernelSU 的一个分支，面向**没有解锁 Bootloader**、也不想改 boot / system 分区的设备。

它沿用 KernelSU 的三件套：

| 组件 | 作用 |
|---|---|
| 内核模块 | 在内核里提供超级用户、模块挂载与各种特性 |
| `ksud` | 用户态守护进程与命令行 |
| 管理器 APK | 授权、模块、特性与诊断界面 |

区别在于**内核模块怎么进去**：官方 KernelSU 要求你解锁并刷入改动过的 boot 镜像；DikSU 用漏洞把模块挂进**正在运行**的系统，这一步叫越狱模式。

## 和 Magisk 的区别

- Magisk 改的是 boot 镜像（magisk init），DikSU 和 KernelSU 一样在内核里做，模块通过 overlay 挂载生效。
- Magisk 的隐藏靠随机化包名；DikSU 在管理器里内置隐身模式，内核侧对 `GET_INFO` 也不再承认自己是管理器。
- 共同点：绝大多数 Magisk 模块可以直接用；修改 `/system` 的模块需要 metamodule（例如 meta-overlayfs）。

## 适合谁

- 手机不能解锁（运营商定制、保修、企业设备）。
- 只想要 root 能力，不想动分区、不想每次 OTA 后重新刷 boot。
- 愿意接受：**越狱依赖漏洞，重启即失效，需要重新激活**；不同机型成功率不同。

## 不适合谁

- 需要 root 常驻、开机即有的场景（请用官方 KernelSU 刷 boot）。
- 已经在用官方 KernelSU 且一切正常 —— 没有必要换。

## 下一步

- [越狱模式怎么工作](/guide/jailbreak)
- [UTS 视图](/guide/uts) · [网络隔离](/guide/netisolate) · [SUSFS](/guide/susfs)
- [常见问题](/guide/faq)
