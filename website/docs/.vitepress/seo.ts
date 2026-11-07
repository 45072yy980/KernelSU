import type { PageData } from 'vitepress'

export const SITE_URL = 'https://45072yy980.github.io/KernelSU'

// Describes the project for search engines and AI agents. SoftwareSourceCode is used
// instead of SoftwareApplication, which Google flags as invalid without ratings.
const projectJsonLd = JSON.stringify({
    '@context': 'https://schema.org',
    '@type': 'SoftwareSourceCode',
    name: 'DikSU',
    description: '不用解锁 Bootloader 的 KernelSU 分支：靠漏洞把内核模块挂进正在运行的系统。',
    url: `${SITE_URL}/`,
    image: `${SITE_URL}/logo.svg`,
    codeRepository: 'https://github.com/45072yy980/KernelSU',
    license: ['https://www.gnu.org/licenses/gpl-2.0.html', 'https://www.gnu.org/licenses/gpl-3.0.html'],
    programmingLanguage: ['C', 'Rust', 'Kotlin'],
    runtimePlatform: 'Android',
    sameAs: ['https://github.com/45072yy980/KernelSU'],
})

export function transformPageData(pageData: PageData) {
    if (pageData.frontmatter.layout === 'home') {
        pageData.frontmatter.head = [
            ...(pageData.frontmatter.head ?? []),
            ['script', { type: 'application/ld+json' }, projectJsonLd],
        ]
    }
    // Page descriptions in frontmatter only feed llms.txt for now. Keep the site-wide
    // meta description so existing search snippets stay unchanged.
    return { description: '' }
}
