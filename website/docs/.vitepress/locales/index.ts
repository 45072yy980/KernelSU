import { defineConfig } from 'vitepress'

export default defineConfig({
  locales: {
    root: {
      label: '简体中文',
      lang: 'zh-CN',
      description: '不用解锁 Bootloader 的 KernelSU 分支：靠漏洞把内核模块挂进正在运行的系统。',
      themeConfig: {
        logo: '/logo.svg',
        siteTitle: 'DikSU',

        nav: [
          { text: '指南', link: '/guide/what-is-diksu', activeMatch: '/guide/' },
          { text: '下载', link: 'https://github.com/45072yy980/KernelSU/releases' },
        ],

        lastUpdatedText: '最后更新',

        outline: { level: [2, 3], label: '本页目录' },

        sidebar: {
          '/guide/': [
            {
              text: '入门',
              items: [
                { text: 'DikSU 是什么', link: '/guide/what-is-diksu' },
                { text: '安装与首次启动', link: '/guide/install' },
                { text: '越狱模式', link: '/guide/jailbreak' },
                { text: '管理器导览', link: '/guide/manager' },
              ],
            },
            {
              text: '能力',
              items: [
                { text: '超级用户与应用配置', link: '/guide/superuser' },
                { text: '模块', link: '/guide/modules' },
                { text: '分区保护', link: '/guide/partition-guard' },
                { text: '备份与维护', link: '/guide/backup' },
                { text: 'UTS 视图', link: '/guide/uts' },
                { text: '网络隔离', link: '/guide/netisolate' },
                { text: 'SUSFS', link: '/guide/susfs' },
              ],
            },
            {
              text: '参考',
              items: [
                { text: 'ksud 命令行', link: '/guide/cli' },
                { text: '诊断与求助', link: '/guide/troubleshooting' },
                { text: '常见问题', link: '/guide/faq' },
              ],
            },
          ],
        },

        socialLinks: [{ icon: 'github', link: 'https://github.com/45072yy980/KernelSU' }],

        footer: {
          message: 'GPL-3.0 授权 · 仅供学习研究，风险自负',
          copyright: 'Copyright © DikSU',
        },

        editLink: {
          pattern: 'https://github.com/45072yy980/KernelSU/edit/main/website/docs/:path',
          text: '在 GitHub 上编辑此页',
        },

        docFooter: { prev: '上一篇', next: '下一篇' },
        returnToTopLabel: '回到顶部',
        sidebarMenuLabel: '目录',
        darkModeSwitchLabel: '主题',
        lightModeSwitchTitle: '切换到亮色',
        darkModeSwitchTitle: '切换到暗色',
      },
    },
  },
})
