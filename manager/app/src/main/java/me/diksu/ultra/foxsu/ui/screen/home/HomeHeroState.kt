package me.diksu.ultra.foxsu.ui.screen.home

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import me.diksu.ultra.BuildConfig
import me.diksu.ultra.KernelVersion
import me.diksu.ultra.ksuApp

/**
 * 全局刷写状态追踪器。
 *
 * 刷写页（FlashScreen / FlashEffect）在真实执行 boot-patch 时回写状态，
 * 主页 Hero 大卡片据此展示真实的「刷写中 / 已刷写待重启 / 刷写失败」，
 * 而不是一个永远不变的静态文案。
 */
enum class FlashPhase {
    IDLE,
    FLASHING,
    SUCCESS,
    FAILED,
}

object FlashTracker {
    private const val PREFS = "flash_tracker"
    private const val KEY_PHASE = "phase"
    private const val KEY_DETAIL = "detail"
    private const val KEY_TIME = "time"

    /** 刷写成功后的「待重启」记忆有效期：一天内没重启才认为它还有效。 */
    private const val SUCCESS_TTL_MS = 24 * 60 * 60 * 1000L

    /**
     * 不缓存 SharedPreferences 句柄：ksuApp 是 lateinit，若本对象在 Application 初始化完成前
     * 被读到，这里会安全地返回 null，下次访问仍能正常拿到真实句柄。
     */
    private fun prefs(): android.content.SharedPreferences? =
        runCatching { ksuApp.getSharedPreferences(PREFS, Context.MODE_PRIVATE) }.getOrNull()

    /**
     * 只恢复「刷写成功 / 待重启」这一种跨进程有意义的状态：
     * · FLASHING 恢复成 IDLE，避免进程被杀后永远转圈；
     * · FAILED 恢复成 IDLE，避免重启 App 后一直显示刷写失败；
     * · SUCCESS 超过 [SUCCESS_TTL_MS] 也丢弃，避免陈旧的「待重启」。
     */
    private fun loadPhase(): FlashPhase {
        val name = runCatching { prefs()?.getString(KEY_PHASE, null) }.getOrNull() ?: return FlashPhase.IDLE
        if (name != FlashPhase.SUCCESS.name) return FlashPhase.IDLE
        val savedAt = runCatching { prefs()?.getLong(KEY_TIME, 0L) }.getOrNull() ?: 0L
        val fresh = System.currentTimeMillis() - savedAt in 1..SUCCESS_TTL_MS
        return if (fresh) FlashPhase.SUCCESS else FlashPhase.IDLE
    }

    private fun loadDetail(): String =
        if (loadPhase() == FlashPhase.SUCCESS) {
            runCatching { prefs()?.getString(KEY_DETAIL, "") }.getOrNull().orEmpty()
        } else {
            ""
        }

    private fun persist() {
        runCatching {
            prefs()?.edit()
                ?.putString(KEY_PHASE, phase.name)
                ?.putString(KEY_DETAIL, detail)
                ?.putLong(KEY_TIME, System.currentTimeMillis())
                ?.apply()
        }
    }

    var phase by mutableStateOf(loadPhase())
        private set

    var detail by mutableStateOf(loadDetail())
        private set

    fun begin(detail: String = "") {
        phase = FlashPhase.FLASHING
        this.detail = detail
        persist()
    }

    fun succeed(detail: String = "") {
        phase = FlashPhase.SUCCESS
        this.detail = detail
        persist()
    }

    fun fail(detail: String = "") {
        phase = FlashPhase.FAILED
        this.detail = detail
        persist()
    }

    fun reset() {
        phase = FlashPhase.IDLE
        detail = ""
        persist()
    }
}

/**
 * Hero 大卡片的统一状态机。
 *
 * 所有主题（Fresh / Miuix / Editorial / Glass / Nocturne）共用同一套判定，
 * 保证同一台设备在不同主题下显示的状态语义完全一致。
 */
enum class HeroPhase {
    /** 未安装，且当前内核可刷写 */
    NOT_INSTALLED,

    /** 正在刷写 */
    FLASHING,

    /** 已刷写成功，等待重启生效 */
    PENDING_REBOOT,

    /** 已安装且一切正常 */
    WORKING,

    /** 已安装但存在告警 */
    ATTENTION,

    /** 刷写失败 */
    FLASH_FAILED,

    /** 当前内核不支持 */
    UNSUPPORTED,
}

data class HeroState(
    val phase: HeroPhase,
    val installed: Boolean,
    /** LKM / GKI；非 GKI 内核时为 null（不能瞎写 GKI） */
    val modeLabel: String?,
    val jailbreakMode: Boolean,
    val safeMode: Boolean,
    val customLkm: Boolean,
    val versionName: String,
    val hasUpdate: Boolean,
    val latestVersionCode: Int,
    /** 越狱模式下刷写会变砖，因此禁用刷写入口 */
    val canFlash: Boolean,
    val flashFailed: Boolean,
)

/**
 * 由真实运行态推导 Hero 状态。
 *
 * @param flashPhase 全局刷写阶段（由 [FlashTracker] 提供）
 */
fun HomeUiState.heroState(
    flashPhase: FlashPhase = FlashTracker.phase,
): HeroState {
    val issues = freshIssues()
    val phase = when {
        flashPhase == FlashPhase.FLASHING -> HeroPhase.FLASHING
        // 刷写失败但旧版本仍在运行 → 只是需要关注；只有没装上才算彻底未工作
        flashPhase == FlashPhase.FAILED && ksuVersion == null -> HeroPhase.FLASH_FAILED
        flashPhase == FlashPhase.SUCCESS && ksuVersion == null -> HeroPhase.PENDING_REBOOT
        ksuVersion == null ->
            if (kernelVersion.isGKI()) HeroPhase.NOT_INSTALLED else HeroPhase.UNSUPPORTED
        issues.isNotEmpty() -> HeroPhase.ATTENTION
        else -> HeroPhase.WORKING
    }

    return HeroState(
        phase = phase,
        installed = ksuVersion != null,
        // lkmMode 为 null 表示当前内核不是 GKI，此时不能显示 GKI
        modeLabel = when (lkmMode) {
            true -> "LKM"
            false -> "GKI"
            null -> null
        },
        jailbreakMode = isLateLoadMode,
        safeMode = isSafeMode,
        customLkm = showCustomLkmBadge,
        versionName = BuildConfig.VERSION_NAME,
        hasUpdate = hasUpdate,
        latestVersionCode = latestVersionInfo.versionCode,
        canFlash = !isLateLoadMode,
        flashFailed = flashPhase == FlashPhase.FAILED,
    )
}

/** 供各主题复用的真实内核版本展示值。 */
fun HomeUiState.displayKernelVersion(): String =
    systemInfo.kernelVersion.ifBlank { kernelVersion.toString() }

/** 供各主题复用的真实管理器版本展示值（跟随应用版本自动更新）。 */
fun HomeUiState.displayManagerVersion(): String = BuildConfig.VERSION_NAME
