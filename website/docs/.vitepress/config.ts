import { defineConfig, SiteConfig } from 'vitepress'
import llmstxt from 'vitepress-plugin-llms'
import locales from './locales'
import { SITE_URL, transformPageData } from './seo'
import { readdir, writeFile } from 'fs/promises'
import { resolve } from 'path'

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
                    details: 'KernelSU runs inside the Linux kernel and grants root access to permitted apps directly from kernel space. ' +
                        'It consists of a kernel module (loaded as an LKM or built into a GKI kernel), the userspace daemon `ksud`, and the KernelSU manager app. ' +
                        'Most Magisk modules work on KernelSU; modules that modify `/system` require a metamodule such as meta-overlayfs.',
                },
            }),
        ],
    },
    head: [
        ['script', {
            async: 'async',
            src: 'https://pagead2.googlesyndication.com/pagead/js/adsbygoogle.js?client=ca-pub-2610070972052494',
            crossorigin: 'anonymous',
        }],
    ],
    sitemap: {
        hostname: 'https://45072yy980.github.io'
    },
    buildEnd: async (config: SiteConfig) => {
        const templateDir = resolve(config.outDir, 'templates');
        const templateList = resolve(templateDir, "index.json");
        let files = [];
        try {
            files = await readdir(templateDir);
            files = files.filter(file => !file.startsWith('.'));
        } catch(e) {
            // ignore
        }
        await writeFile(templateList, JSON.stringify(files));
    }
})
