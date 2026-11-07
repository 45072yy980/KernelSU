---
layout: home
title: DikSU

hero:
  name: DikSU
  text: 不用解锁 Bootloader 的 KernelSU
  tagline: 靠漏洞把内核模块挂进正在运行的系统，内置 ghostlock 与 DirtyFrag 两条提权路径
  actions:
    - theme: brand
      text: 开始使用
      link: /guide/what-is-diksu
    - theme: alt
      text: 查看源码
      link: https://github.com/45072yy980/KernelSU

features:
  - title: 免解锁越狱
    details: 不改 boot、不刷 system。ghostlock 或 DirtyFrag 任意一条跑通，内核模块就挂进当前系统，进入越狱模式。
  - title: UTS 视图
    details: 按 uid 伪装 uname(2) 的六个字段，只影响指定的应用，全局或按应用都行。
  - title: 网络隔离
    details: 让选中的应用连不上外网：拦 connect 与未连接的 sendmsg，可以放行回环与内网。
  - title: SUSFS
    details: 内置面板里配置路径隐藏、kstat 克隆、伪 uname 与 open 重定向，支持导入导出。
  - title: 系统分区保护
    details: 安装与写入前先扫描分区，越狱模式下强制开启，避免把不可回滚的改动写进去。
  - title: 备份与维护
    details: 授权规则与模块可打包备份；重载模块、软重启与隐藏软重启收在一个入口里。
---
