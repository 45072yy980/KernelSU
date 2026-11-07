---
layout: home
title: DikSU
titleTemplate: 不用解锁 Bootloader 的 KernelSU

hero:
  name: DikSU
  text: 不用解锁 Bootloader 的 KernelSU
  tagline: 不碰 boot、不刷 system。靠漏洞把内核模块挂进正在运行的系统，重启后失效、需要重新激活。
  image:
    src: /hero.svg
    alt: 越狱流程示意
  actions:
    - theme: brand
      text: 五步开始
      link: /guide/install
    - theme: alt
      text: 它是什么
      link: /guide/what-is-diksu

features:
  - title: 免解锁越狱
    details: ghostlock 与 DirtyFrag 两条独立提权路径，任意一条跑通就进入越狱模式；失败不会写任何分区。
  - title: UTS 视图
    details: 按 uid 伪装 uname(2) 的六个字段，全局或指定应用；热路径走 RCU，不拖慢 uname。
  - title: 网络隔离
    details: 让选中的应用连不上外网：拦 connect 与未连接的 sendmsg，可放行回环与内网。
  - title: SUSFS 面板
    details: 路径隐藏、kstat 克隆、伪 uname、open 重定向，草稿式修改，支持导入导出。
  - title: 分区保护
    details: 安装前把脚本逐行读一遍，给出绿/黄/红三级结论；不可逆的写入直接拒绝。
  - title: 备份与维护
    details: 授权规则与模块可打包备份；重载模块、软重启、隐藏软重启收在一个入口。
---

## 它和官方 KernelSU 差在哪

| | 官方 KernelSU | DikSU |
|---|---|---|
| 内核模块怎么进去 | 解锁 + 刷改动过的 boot | 漏洞把模块挂进运行中的系统 |
| 重启后 | 依旧有 root | 回到未激活，需要重新提权 |
| 需要动分区 | 是（boot） | 否 |
| 适合 | 能解锁、要常驻 | 不能解锁、能接受重激活 |

三件套（内核模块 + `ksud` + 管理器 APK）与 KernelSU 一致，所以**模块生态是通用的**：绝大多数 Magisk 模块能直接装，改 `/system` 的模块需要 metamodule。

## 三种典型用法

::: tip 只想要一个 root
走一遍越狱激活，然后在「超级用户」里给需要的应用授权。不需要装任何模块。
:::

::: tip 想藏掉 root 痕迹
打开隐身模式（设置 → 其他功能），再配合 [UTS 视图](/guide/uts) 与 [SUSFS](/guide/susfs) 处理内核层面的特征。
:::

::: tip 想让某个应用断网
[网络隔离](/guide/netisolate) 里勾选它，关掉两个放行开关即可；不需要 VPN，也不占后台。
:::

<div class="diksu-actions">
  <a href="/KernelSU/guide/install">安装与首次启动 →</a>
  <a href="/KernelSU/guide/jailbreak">越狱模式怎么工作 →</a>
  <a href="/KernelSU/guide/cli">ksud 命令行 →</a>
  <a href="/KernelSU/guide/troubleshooting">出问题了 →</a>
</div>
