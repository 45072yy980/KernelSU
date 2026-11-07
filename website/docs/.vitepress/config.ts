import { defineConfig } from 'vitepress'
import llmstxt from 'vitepress-plugin-llms'
import locales from './locales'
import { SITE_URL, transformPageData } from './seo'

// Translated docs are left out of llms.txt / llms-full.txt to avoid duplicated content.
// The plugin renames "<locale>/index.md" to "<locale>.md", so match both forms.
const translatedDocs = Object.keys(locales.locales)
    .filter(locale => locale !== 'root')
    .flatMap(locale => [`${locale}/**`, `${locale}.md`])

// The plugin already prefixes {description} with "> ".
const llmsTxtTemplate = `# {title}

{description}

{details}

## Official links

- Website: ${SITE_URL}
- Source code: https://github.com/45072yy980/KernelSU
- Downloads: https://github.com/45072yy980/KernelSU/releases

## Table of Contents

{toc}
`

export default defineConfig( {
    title: 'DikSU',
    lang: 'zh-CN',
    lastUpdated: true,
    description: '不用解锁 Bootloader 的 KernelSU 分支：靠漏洞把内核模块挂进正在运行的系统。',
    // 部署在 GitHub Pages 的子路径上，资源与站内链接都按这个前缀生成
    base: '/KernelSU/',
    locales: locales.locales,
    transformPageData,
    vite: {
        plugins: [
            llmstxt({
                domain: SITE_URL,
                // The hint is hidden text on the home page; leave the page content untouched.
                injectLLMHint: false,
                sidebar: locales.locales.root.themeConfig.sidebar,
                ignoreFilesPerOutput: {
                    llmsTxt: translatedDocs,
                    llmsFullTxt: translatedDocs,
                },
                customLLMsTxtTemplate: llmsTxtTemplate,
                customTemplateVariables: {
                    title: 'DikSU',
                    description: '不用解锁 Bootloader 的 KernelSU 分支：靠漏洞把内核模块挂进正在运行的系统。',
                    details: 'DikSU 沿用 KernelSU 的内核模块 + ksud + 管理器三件套，但不要求解锁 Bootloader：' +
                        '它用 ghostlock 或 DirtyFrag 把内核模块挂进正在运行的系统（越狱模式），重启后失效、需要重新激活。' +
                        '绝大多数 Magisk 模块可以直接使用；修改 /system 的模块需要 metamodule。',
                },
            }),
        ],
    },
    head: [['link', { rel: 'icon', type: 'image/svg+xml', href: '/KernelSU/logo.svg' }]],
    sitemap: {
        hostname: 'https://45072yy980.github.io'
    },
})
