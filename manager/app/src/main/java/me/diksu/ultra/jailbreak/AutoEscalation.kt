package me.diksu.ultra.jailbreak

import android.content.Context
import android.util.Log
import me.diksu.ultra.data.repository.SettingsRepositoryImpl
import me.diksu.ultra.ksuApp
import me.diksu.ultra.ui.util.DirtyFragExploit
import me.diksu.ultra.ui.util.JailbreakExploit
import me.diksu.ultra.ui.util.rootAvailable

/**
 * 自动提权并越狱（4.0 重写）。
 *
 * 3.x 的"自动越狱"只有一个布尔开关，实现是开机时拉起一个 **空的**
 * [me.diksu.ultra.magica.MagicaService]（`onBind` 之外没有任何逻辑），
 * 于是开关拨了也不会发生任何事情。
 *
 * 4.0 改成真正的编排器：
 * - 用户可选**启用哪些提权方式**，并决定**尝试顺序**；
 * - 开机（或手动触发）时按顺序逐个尝试，第一个成功即停；
 * - 每次尝试都记录结果，面板里能看到"上一次是谁成功的、失败的原因是什么"。
 *
 * 目前可编排的方式：
 * - [METHOD_GHOSTLOCK]：ghostlock（CVE 链，按内核构建号定制，链条最短）
 * - [METHOD_DIRTYFRAG]：DirtyFrag（页缓存写，覆盖更广）
 *
 * 顺序保存在 [PREFS] 里，未列出的方式视为禁用。
 */
object AutoEscalation {
    private const val TAG = "AutoEscalation"

    /** 与 [SettingsRepositoryImpl.autoJailbreak] 共用同一份设置存储。 */
    private const val PREFS = "settings"

    const val METHOD_GHOSTLOCK = "ghostlock"
    const val METHOD_DIRTYFRAG = "dirtyfrag"

    private const val KEY_ORDER = "auto_escalation_order"
    private const val KEY_LAST_AT = "auto_escalation_last_at"
    private const val KEY_LAST_OK = "auto_escalation_last_ok"
    private const val KEY_LAST_METHOD = "auto_escalation_last_method"
    private const val KEY_LAST_DETAIL = "auto_escalation_last_detail"

    /** 全部已知方式，顺序即默认优先级。 */
    val ALL_METHODS: List<String> = listOf(METHOD_GHOSTLOCK, METHOD_DIRTYFRAG)

    fun label(method: String): String = when (method) {
        METHOD_GHOSTLOCK -> "ghostlock"
        METHOD_DIRTYFRAG -> "DirtyFrag"
        else -> method
    }

    fun description(method: String): String = when (method) {
        METHOD_GHOSTLOCK -> "按内核构建号定制的 CVE 链，链条最短，成功率最高"
        METHOD_DIRTYFRAG -> "页缓存写（IPsec ESP + splice），覆盖的内核版本更广"
        else -> ""
    }

    private fun prefs() = ksuApp.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** 总开关，与设置页里那个"自动提权并越狱"是同一个。 */
    var enabled: Boolean
        get() = SettingsRepositoryImpl().autoJailbreak
        set(value) {
            SettingsRepositoryImpl().autoJailbreak = value
        }

    /** 用户选中的方式，按尝试顺序排列；空表示一种都没选。 */
    var order: List<String>
        get() {
            val raw = prefs().getString(KEY_ORDER, null)
                ?: return ALL_METHODS
            val parsed = raw.split(",").map { it.trim() }.filter { it in ALL_METHODS }
            // 全都失效时退回默认顺序，避免用户升级后一个方式都不剩。
            return parsed.ifEmpty { ALL_METHODS }
        }
        set(value) {
            val cleaned = value.filter { it in ALL_METHODS }.distinct()
            prefs().edit().putString(KEY_ORDER, cleaned.joinToString(",")).apply()
        }

    data class LastAttempt(
        val atMillis: Long,
        val success: Boolean,
        val method: String,
        val detail: String,
    )

    fun lastAttempt(): LastAttempt? {
        val at = prefs().getLong(KEY_LAST_AT, 0L)
        if (at <= 0L) return null
        return LastAttempt(
            atMillis = at,
            success = prefs().getBoolean(KEY_LAST_OK, false),
            method = prefs().getString(KEY_LAST_METHOD, "").orEmpty(),
            detail = prefs().getString(KEY_LAST_DETAIL, "").orEmpty(),
        )
    }

    private fun record(success: Boolean, method: String, detail: String) {
        prefs().edit()
            .putLong(KEY_LAST_AT, System.currentTimeMillis())
            .putBoolean(KEY_LAST_OK, success)
            .putString(KEY_LAST_METHOD, method)
            .putString(KEY_LAST_DETAIL, detail)
            .apply()
    }

    /** 某个方式在这台设备上是否可用（二进制是否随包内置）。 */
    fun isAvailable(context: Context, method: String): Boolean = when (method) {
        METHOD_GHOSTLOCK -> JailbreakExploit.isAvailable(context)
        METHOD_DIRTYFRAG -> DirtyFragExploit.isAvailable(context)
        else -> false
    }

    data class Outcome(
        val success: Boolean,
        val method: String,
        val message: String,
    )

    /**
     * 按用户设定的顺序逐个尝试，第一个成功即返回。
     *
     * 阻塞调用，务必放在 IO 线程；`onLog` 会在每次尝试前后收到一行说明，
     * 便于面板实时显示"现在在试哪一种"。
     */
    fun run(context: Context, onLog: (String) -> Unit = {}): Outcome {
        val methods = order.filter { isAvailable(context, it) }
        if (methods.isEmpty()) {
            val message = "没有可用的提权方式（二进制未随包内置）"
            record(false, "", message)
            onLog(message)
            return Outcome(false, "", message)
        }
        if (runCatching { rootAvailable() }.getOrDefault(false)) {
            val message = "已经有 root，跳过提权"
            record(true, "", message)
            onLog(message)
            return Outcome(true, "", message)
        }

        onLog("开始自动提权，顺序：${methods.joinToString(" → ") { label(it) }}")
        var lastError = ""
        for ((index, method) in methods.withIndex()) {
            onLog("== [${index + 1}/${methods.size}] 尝试 ${label(method)} ==")
            val ok = try {
                when (method) {
                    METHOD_GHOSTLOCK -> JailbreakExploit.run(
                        context = context,
                        managerPackage = context.packageName,
                        onLog = onLog,
                    ).success

                    METHOD_DIRTYFRAG -> DirtyFragExploit.run(context) { line -> onLog(line) }.success
                    else -> false
                }
            } catch (t: Throwable) {
                Log.e(TAG, "method $method crashed", t)
                onLog("${label(method)} 异常：${t.message}")
                false
            }
            if (ok) {
                val message = "${label(method)} 成功"
                record(true, method, message)
                onLog(message)
                return Outcome(true, method, message)
            }
            lastError = "${label(method)} 失败"
            onLog("== ${label(method)} 失败，继续下一种 ==")
        }
        val message = "全部方式都失败：${methods.joinToString("、") { label(it) }}"
        record(false, methods.last(), message)
        onLog(message)
        return Outcome(false, methods.lastOrNull().orEmpty(), message.ifBlank { lastError })
    }
}
