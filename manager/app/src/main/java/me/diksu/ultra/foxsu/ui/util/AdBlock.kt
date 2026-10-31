package me.diksu.ultra.foxsu.ui.util

import android.util.Log
import com.topjohnwu.superuser.Shell
import me.diksu.ultra.ksuApp
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * 系统级去广告（systemless hosts）。
 *
 * 原理：下载开源广告域名 hosts 列表 → 合并去重、套白名单 → 以 KernelSU 模块
 * （/data/adb/modules/foxsu_adblock/system/etc/hosts）的形式在开机时由 metamodule
 * bind-mount 覆盖 /system/etc/hosts。广告域名全部解析到 0.0.0.0，
 * 无 VPN、无后台进程、零内存占用。
 *
 * 规则源均为持续维护的开源仓库（2026-10 仍在更新）：
 *  - StevenBlack/hosts（聚合 AdAway/yoyo/URLHaus 等 14 个源）
 *  - AdAway 官方列表
 *  - neoHosts（国内广告 / 运营商劫持）
 * 支持自定义源 URL 与 GitHub 加速镜像。
 */
object AdBlock {

    private const val TAG = "AdBlock"
    private const val MODULE_DIR = "/data/adb/modules/foxsu_adblock"
    private const val WORK_DIR = "/data/adb/foxsu/adblock"

    data class Source(val id: String, val name: String, val desc: String, val url: String)

    /**
     * 规则源全部为国内来源（国外列表误杀国内 App 的比率高，且体积大）：
     *  - anti-AD：国内最权威的反广告域名库，每天更新，10 万+ 域名，覆盖国内 App / 运营商广告
     *  - AdGuard Chinese：AdGuard 官方中文过滤规则中的纯域名部分（国内站点广告网络）
     * 格式兼容 hosts / dnsmasq(address=/x/) / 纯域名 三种。
     */
    val SOURCES = listOf(
        Source(
            "antiad", "anti-AD 国内广告库", "10 万+ 国内 App / 运营商广告域名，每日更新（推荐）",
            "https://raw.githubusercontent.com/privacy-protection-tools/anti-AD/master/adblock-for-dnsmasq.conf",
        ),
        Source(
            "antiad_dom", "anti-AD 纯域名精简版", "同一库的纯域名列表，体积更小、解析更快",
            "https://raw.githubusercontent.com/privacy-protection-tools/anti-AD/master/anti-ad-domains.txt",
        ),
        Source(
            "adguard_cn", "AdGuard 中文过滤", "AdGuard 官方中文规则中的国内广告网络域名",
            "https://raw.githubusercontent.com/AdguardTeam/AdguardFilters/master/ChineseFilter/sections/adservers.txt",
        ),
    )

    /**
     * 手机厂商系统广告专项规则（内置，不走网络）。
     *
     * 这些域名是厂商系统应用（安全中心 / 应用商店 / 主题 / 天气 / 云服务推广位）的
     * 广告与统计通道，主流公开列表普遍不收录（怕误杀系统功能），所以这里按厂商分组
     * 内置。默认全部关闭，用户按自己的机型勾选。
     *
     * risk 说明：affected 是该组可能连带影响的功能，界面上直接标出来，
     * 避免用户屏蔽完发现应用商店打不开 / 云同步失灵。
     */
    data class VendorGroup(
        val id: String,
        val name: String,
        val desc: String,
        val affected: String,     // 副作用提示（空 = 基本无副作用）
        val domains: List<String>,
    )

    val VENDORS = listOf(
        VendorGroup(
            "xiaomi", "小米 / 红米（MIUI · HyperOS）",
            "安全中心推荐位、应用商店推广、天气/日历广告、MIUI 统计上报",
            "可能影响应用商店更新提示、系统流量校准",
            listOf(
                // 广告投放与统计
                "adv.sec.miui.com", "api.ad.xiaomi.com", "api.ad.miui.com",
                "test.ad.xiaomi.com", "sdkconfig.ad.xiaomi.com", "adinfo.ra1.xlmc.sec.miui.com",
                "api.ra1.xlmc.sandai.net", "xlmc.sec.miui.com", "api.tw06.xlmc.sec.miui.com",
                "data.mistat.xiaomi.com", "tracking.miui.com",
                "f3.mi-stat.gslb.mi-idc.com", "app01.nodes.gslb.mi-idc.com",
                "app02.nodes.gslb.mi-idc.com", "resolver.gslb.mi-idc.com",
                "info.analysis.kp.sec.miui.com", "flash.sec.miui.com",
                // 浏览器/下载/视频等内容推荐位
                "starfish.pandora.xiaomi.com", "mishop.pandora.xiaomi.com",
                "misc.pandora.xiaomi.com", "bss.pandora.xiaomi.com",
                "dvb.pandora.xiaomi.com", "mishop.cdn.pandora.xiaomi.com",
                "image.box.xiaomi.com", "cdn.fds.api.xiaomi.com", "cdn-thumb.fds.api.xiaomi.com",
                "o2o.api.xiaomi.com", "a0.app.xiaomi.com", "api.installer.xiaomi.com",
                "sousuo.xm.sjzhushou.com", "m.sjzhushou.com", "twin13a131.sandai.net",
                "etl-xlmc-ssl.sandai.net", "cn.app.chat.xiaomi.net", "mibind.chat.gslb.mi-idc.com",
                // 安全中心（avlyun 反病毒推广，不影响本地病毒库更新）
                "log.avlyun.sec.intl.miui.com", "update.avlyun.sec.miui.com",
                "ixav-cse.avlyun.com", "logupdate.avlyun.sec.miui.com",
                "miui-fxcse.avlyun.com", "sdkconf.avlyun.com", "miav-cse.avlyun.com",
                // 广告/推荐附属
                "pic.xcarimg.com", "yun.rili.cn", "zhwnlapi.etouch.cn", "i3.go2yd.com",
            ),
        ),
        VendorGroup(
            "huawei", "华为（EMUI）",
            "应用市场推广、华为广告平台、hiad 投放链路",
            "可能影响应用市场更新与云空间部分功能",
            listOf(
                "adx.huawei.com", "ads.huawei.com", "ad.hicloud.com",
                "metrics.data.hicloud.com", "metrics2.data.hicloud.com",
                "ads-drcn.platform.dbankcloud.cn", "hiad.huawei.com",
                "grs.hicloud.com.ads", "aw.dw.impact-ad.jp",
                "logservice.hicloud.com", "logbak.hicloud.com",
            ),
        ),
        VendorGroup(
            "honor", "荣耀（MagicOS）",
            "荣耀广告平台、应用市场推广、推送附带推荐",
            "可能影响应用市场更新提示",
            listOf(
                "ads-drcn.platform.hihonorcloud.com", "metrics-ad-drcn.dt.hihonorcloud.com",
                "tracking.ads.honor.com", "adx.hihonor.com", "o2o.hihonorcloud.com",
                "sdkconfig.ad.hihonor.com", "events.gcloud.hihonor.com",
            ),
        ),
        VendorGroup(
            "oppo", "OPPO / 一加 / realme（ColorOS）",
            "HeyTap 广告 SDK、应用商店推广、开屏与信息流",
            "可能影响软件商店更新、HeyTap 云服务",
            listOf(
                "adata.heytapmobi.com", "adx.heytapmobi.com", "adsfs.heytapimage.com",
                "adsfs-sdkconfig.heytapimage.com", "img-c.heytapimage.com",
                "iou.oppomobile.com", "cn.ad.oppomobile.com", "adx.oppomobile.com",
                "adv.oppomobile.com", "sdk.oppomobile.com",
                "g1.oppomobile.com", "ads.oppomobile.com", "api.ads.heytapmobi.com",
                "tracker.heytapmobi.com", "event.heytapmobi.com",
            ),
        ),
        VendorGroup(
            "vivo", "vivo / iQOO（OriginOS）",
            "vivo 广告联盟、应用商店推广、系统推荐位",
            "可能影响应用商店更新提示",
            listOf(
                "sdk.ads.vivo.com.cn", "adlogs.vivo.com.cn", "adx.vivo.com.cn",
                "adnet.vivo.com.cn", "ads.vivo.com.cn", "union.vivo.com.cn",
                "t.app.vivo.com.cn", "event.vivo.com.cn", "metrics.vivo.com.cn",
                "ad.vivo.com.cn", "api.ad.vivo.com.cn",
            ),
        ),
        VendorGroup(
            "meizu", "魅族（Flyme）",
            "Flyme 推送广告、应用商店推广、主题推荐",
            "可能影响应用商店更新",
            listOf(
                "adx.meizu.com", "ads.meizu.com", "api.ad.meizu.com",
                "push.meizu.com", "track.meizu.com", "stat.meizu.com",
                "appcenter.meizu.com", "openapi.meizu.com",
            ),
        ),
        VendorGroup(
            "samsung", "三星（One UI）",
            "三星广告平台（Samsung Ads）、应用商店推荐位",
            "可能影响 Galaxy Store 更新",
            listOf(
                "samsunganalytics.com", "analytics.samsung.com", "ads.samsung.com",
                "adx.samsung.com", "sdk-ad.samsung.com", "gads.samsung.com",
                "impression.samsung.com", "samsungads.com",
            ),
        ),
        VendorGroup(
            "adnet", "国内广告联盟（穿山甲 / 优量汇 / 快手 / 百青藤）",
            "App 开屏与信息流广告的 SDK 通道，命中后绝大多数 App 的开屏广告会一起消失",
            "极少数 App 可能因为广告加载失败而白屏或闪退",
            listOf(
                // 穿山甲（字节）
                "pangle.io", "img.pangle.cn", "sdk.pangle.cn", "api.pangle.cn",
                "log.pangle.cn", "ad.pangle.cn", "open.e.kuaishou.com",
                "pangolin-sdk-toutiao.com", "pangolin.snssdk.com", "ad.snssdk.com",
                "gromore.pangle.cn", "csjplatform.com",
                // 优量汇（腾讯）
                "gdt.qq.com", "mi.gdt.qq.com", "adsmind.gdtimg.com", "adx.gdt.qq.com",
                "pgdt.gtimg.cn", "e.qq.com", "l.qq.com", "c.gdtimg.com",
                // 快手联盟
                "adx.kuaishou.com", "ad.e.kuaishou.com", "adlogs.kuaishou.com",
                "sdk.e.kuaishou.com", "openad.kuaishou.com",
                // 百度百青藤
                "mobads.baidu.com", "cpro.baidu.com", "union.baidu.com",
                "pos.baidu.com", "cbjs.baidu.com", "s.baidu.com.bdstatic.com",
                // 拼多多/京东等联盟链接
                "ad.pinduoduo.com", "ads.jd.com",
            ),
        ),
    )

    data class Status(
        val installed: Boolean,   // 模块已安装
        val enabled: Boolean,     // 模块未被 disable
        val sourceId: String,
        val vendorIds: String,
        val entries: Int,         // 当前 hosts 条目数
        val updatedAt: String,    // 上次更新时间（epoch ms，0 = 未更新过）
        val whitelist: List<String>,
    )

    private fun sh(): Shell = getRootShell()

    private fun cmd(shell: Shell, vararg commands: String): Pair<Int, List<String>> {
        val out = ArrayList<String>()
        val err = ArrayList<String>()
        val r = shell.newJob().add(*commands).to(out, err).exec()
        return r.code to (out + err).filter { it.isNotBlank() }
    }

    private fun escape(s: String): String = "'" + s.replace("'", "'\\''") + "'"

    // ── 配置读写（存 /data/adb/foxsu/adblock/）────────────────────────────

    private fun cfg(shell: Shell, name: String): String {
        val (_, out) = cmd(shell, "[ -f $WORK_DIR/$name ] && cat $WORK_DIR/$name || true")
        return out.joinToString("\n").trim()
    }

    private fun setCfg(shell: Shell, name: String, value: String) {
        cmd(shell, "mkdir -p $WORK_DIR", "printf '%s' ${escape(value)} > $WORK_DIR/$name")
    }

    fun status(): Status {
        val shell = sh()
        val installed = cmd(shell, "[ -f $MODULE_DIR/module.prop ] && echo __ok__ || true").second.any { it.contains("__ok__") }
        val enabled = cmd(shell, "[ ! -f $MODULE_DIR/disable ] && echo __ok__ || true").second.any { it.contains("__ok__") }
        val entries = cfg(shell, "entries").toIntOrNull() ?: 0
        val updatedAt = cfg(shell, "updated_at")
        val wl = cfg(shell, "whitelist").split('\n').map { it.trim() }.filter { it.isNotEmpty() }
        return Status(
            installed, enabled,
            cfg(shell, "source").ifBlank { "antiad" },
            cfg(shell, "vendor"),
            entries, updatedAt, wl,
        )
    }

    // ── 下载与合并 ────────────────────────────────────────────────────────

    /** 应用侧直接 HTTP 下载（跟随跳转）；失败返回 null */
    fun download(url: String, useMirror: Boolean): ByteArray? {
        return try {
            val real = if (useMirror && url.contains("raw.githubusercontent.com"))
                "https://gh-proxy.com/$url" else url
            val conn = URL(real).openConnection() as HttpURLConnection
            conn.connectTimeout = 15000
            conn.readTimeout = 30000
            conn.instanceFollowRedirects = true
            if (conn.responseCode !in 200..299) {
                Log.w(TAG, "download $real → ${conn.responseCode}")
                return null
            }
            conn.inputStream.use { it.readBytes() }.also { conn.disconnect() }
        } catch (e: Throwable) {
            Log.e(TAG, "download", e)
            null
        }
    }

    /**
     * 合并多个 hosts 文本：
     * 去注释/空行/内联注释、统一 0.0.0.0、按域名去重、套白名单、剔除本机保留域。
     */
    fun merge(texts: List<String>, whitelist: Set<String>): Pair<String, Int> {
        val keep = Regex("^(localhost|localhost\\.localdomain|local)$")
        val seen = HashSet<String>()
        val out = StringBuilder(
            "# FoxSu AdBlock · systemless hosts\n# 由 FoxSu Manager 生成，请勿直接编辑\n\n")
        var count = 0
        for (raw in texts) {
            for (line0 in raw.lineSequence()) {
                var line = line0.trim()
                if (line.isEmpty() || line.startsWith("!") || line.startsWith("[")) continue
                // dnsmasq 格式：address=/example.com/
                if (line.startsWith("address=/")) {
                    line = line.removePrefix("address=/").trimEnd('/').trim()
                } else {
                    line = line.substringBefore('#').trim()
                }
                if (line.isEmpty()) continue
                // AdGuard 规则的例外语法 @@||domain^ 表示放行，直接跳过
                if (line.startsWith("@@")) continue
                val domain = line.split(Regex("[\\s^|/]+")).filter { it.isNotEmpty() }
                    .lastOrNull()?.lowercase()?.removePrefix("||") ?: continue
                if (!domain.contains('.')) continue
                if (domain !in seen) {
                    seen.add(domain)
                    if (whitelist.any { domain == it || domain.endsWith(".$it") }) continue
                    if (keep.matches(domain)) continue
                    out.append("0.0.0.0 ").append(domain).append('\n')
                    count++
                }
            }
        }
        return out.toString() to count
    }

    // ── 安装 / 开关 / 卸载 ────────────────────────────────────────────────

    data class InstallResult(val ok: Boolean, val message: String)

    fun install(
        sourceIds: List<String>,
        vendorIds: Set<String>,
        customUrls: List<String>,
        useMirror: Boolean,
        whitelist: Set<String>,
    ): InstallResult {
        val shell = sh()
        val texts = mutableListOf<String>()
        val urls = SOURCES.filter { it.id in sourceIds }.map { it.url } + customUrls.filter { it.isNotBlank() }
        val vendorText = VENDORS.filter { it.id in vendorIds }.flatMap { it.domains }.joinToString("\n") { "0.0.0.0 $it" }
        if (urls.isEmpty() && vendorText.isBlank()) return InstallResult(false, "未选择任何规则源")
        for (u in urls) {
            val data = download(u, useMirror) ?: return InstallResult(false, "下载失败：$u\n请检查网络或开启 GitHub 加速镜像")
            texts.add(String(data, Charsets.UTF_8))
        }
        if (vendorText.isNotBlank()) texts.add(vendorText)
        val (merged, count) = merge(texts, whitelist)
        // 只有厂商规则时条目数天然很少，阈值按是否有网络源区分
        if (urls.isNotEmpty() && count < 1000) return InstallResult(false, "合并后仅 $count 条，疑似下载不完整，已放弃安装")

        return try {
            val tmp = File.createTempFile("hosts", ".foxsu")
            tmp.writeText(merged)
            cmd(shell, "mkdir -p $MODULE_DIR/system/etc")
            val (c1, o1) = cmd(
                shell,
                "cp ${escape(tmp.absolutePath)} $MODULE_DIR/system/etc/hosts",
                "chmod 644 $MODULE_DIR/system/etc/hosts",
            )
            tmp.delete()
            if (c1 != 0) return InstallResult(false, "写入失败：${o1.joinToString()}")
            val prop = """
                id=foxsu_adblock
                name=FoxSu 去广告
                version=1.0
                versionCode=1
                author=FoxSu Manager
                description=Systemless hosts 广告屏蔽（由 FoxSu Manager 维护，规则源：开源 hosts 聚合）
            """.trimIndent()
            cmd(
                shell,
                "printf '%s' ${escape(prop)} > $MODULE_DIR/module.prop",
                "rm -f $MODULE_DIR/disable",
                "mkdir -p $WORK_DIR",
                "printf '%s' ${escape(sourceIds.joinToString(","))} > $WORK_DIR/source",
                "printf '%s' ${escape(vendorIds.joinToString(","))} > $WORK_DIR/vendor",
                "printf '%s' ${escape(System.currentTimeMillis().toString())} > $WORK_DIR/updated_at",
                "printf '%s' ${escape(count.toString())} > $WORK_DIR/entries",
                "printf '%s' ${escape(whitelist.joinToString("\n"))} > $WORK_DIR/whitelist",
            )
            InstallResult(true, "已安装 $count 条规则，重启后生效")
        } catch (e: Throwable) {
            Log.e(TAG, "install", e)
            InstallResult(false, "安装失败：${e.message}")
        }
    }

    fun setEnabled(enabled: Boolean): Boolean {
        val shell = sh()
        val r = if (enabled) cmd(shell, "rm -f $MODULE_DIR/disable") else cmd(shell, "touch $MODULE_DIR/disable")
        return r.first == 0
    }

    fun uninstall(): Boolean {
        val shell = sh()
        return cmd(shell, "rm -rf $MODULE_DIR").first == 0
    }

    /** 本机当前 /etc/hosts 的实际条目数（验证是否生效） */
    fun liveEntries(): Int {
        val (_, out) = cmd(sh(), "grep -c '^0\\.0\\.0\\.0' /etc/hosts 2>/dev/null || true")
        return out.firstOrNull()?.trim()?.toIntOrNull() ?: 0
    }

    fun setWhitelist(shell: Shell, domains: List<String>) {
        setCfg(shell, "whitelist", domains.joinToString("\n"))
    }
}
