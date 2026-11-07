import { defineConfig } from 'vitepress'

export default defineConfig({
  locales: {
    root: {
      label: '简体中文',
      lang: 'zh-CN',
      description: '不用解锁 Bootloader 的 KernelSU 分支：靠漏洞把内核模块挂进正在运行的系统。',
      themeConfig: {
        nav: [{ text: '指南', link: '/guide/what-is-diksu' }],

        lastUpdatedText: '最后更新',

        sidebar: {
          '/guide/': [
            {
              text: '入门',
              items: [
                { text: 'DikSU 是什么', link: '/guide/what-is-diksu' },
                { text: '越狱模式', link: '/guide/jailbreak' },
              ],
            },
            {
              text: '特性',
              items: [
                { text: 'UTS 视图', link: '/guide/uts' },
                { text: '网络隔离', link: '/guide/netisolate' },
                { text: 'SUSFS', link: '/guide/susfs' },
              ],
            },
            {
              text: '其它',
              items: [{ text: '常见问题', link: '/guide/faq' }],
            },
          ],
        },

        socialLinks: [{ icon: 'github', link: 'https://github.com/45072yy980/KernelSU' }],

        footer: {
          message: 'GPL-3.0 授权',
          copyright: 'Copyright © DikSU',
        },

        editLink: {
          pattern: 'https://github.com/45072yy980/KernelSU/edit/main/website/docs/:path',
          text: '在 GitHub 上编辑此页',
        },
      },
    },
  },
})
