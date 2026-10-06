package me.weishu.kernelsu.susfs

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Re-applies the user's SuSFS settings and rule lists after boot.
 *
 * The kernel module keeps nothing across reboots — every toggle, uname
 * spoof, hidden path, sus-map, kstat rule, and open-redirect is held in RAM
 * only. This receiver waits for `sys.boot_completed` (the same property the
 * module's service.sh polls) and then replays everything saved in
 * [SusfsRepository] back to the kernel via ksud.
 *
 * ## Application order (three-phase)
 *
 * 1. **Toggles + uname** — global switches first, so path hooks that depend
 *    on them see the right state from the start.
 * 2. **Path-like rules** — hidden paths (plain + loop), sus-maps, kstat
 *    paths, open-redirects. Each group is replayed in full before the next
 *    begins, matching the order FolkPatch uses for its pathhide/netisolate.
 * 3. **Retry accounting** — every rule is replayed independently inside a
 *    try/catch so one stale path cannot abort the rest. Failures are counted;
 *    if any rule failed, [SusfsRepository.bootRetryCount] is incremented and
 *    the next boot waits longer before retrying. After [MAX_BOOT_RETRIES]
 *    consecutive failures the counter is reset and replay is abandoned so a
 *    permanently-broken rule cannot loop forever.
 */
class SusfsBootReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "SusfsBootReceiver"

        /** Extra wait (seconds) when a previous boot replay failed. */
        private const val RETRY_EXTRA_WAIT_SECONDS = 30
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // If a previous replay failed, give the system extra time to
                // settle before trying again. Early-boot ksud can be flaky.
                val priorFailures = SusfsRepository.bootRetryCount
                if (priorFailures > 0) {
                    Log.w(TAG, "Prior boot replay failed ($priorFailures time(s)); waiting extra $RETRY_EXTRA_WAIT_SECONDSs")
                    if (priorFailures >= SusfsRepository.MAX_BOOT_RETRIES) {
                        Log.e(TAG, "Reached MAX_BOOT_RETRIES (${SusfsRepository.MAX_BOOT_RETRIES}); resetting counter and abandoning replay")
                        SusfsRepository.bootRetryCount = 0
                        return@launch
                    }
                    Thread.sleep(RETRY_EXTRA_WAIT_SECONDS * 1000L)
                }

                waitForBootCompleted()

                // Bail out silently if neither built-in SUSFS nor the LKM
                // answered the supercall — there is nothing to replay into.
                if (!SusfsCommands.loadStatus().loaded) {
                    Log.i(TAG, "SUSFS not loaded; skipping replay")
                    return@launch
                }

                // Phase 1: toggles + uname
                val toggleFailures = replayToggles()

                // Phase 2: path-like rules (each group independently)
                val pathFailures = replayPaths()
                val mapFailures = replayMaps()
                val kstatFailures = replayKstats()
                val redirectFailures = replayRedirects()

                val totalFailures = toggleFailures + pathFailures + mapFailures +
                    kstatFailures + redirectFailures

                // Phase 3: retry accounting
                if (totalFailures == 0) {
                    if (priorFailures > 0) {
                        Log.i(TAG, "Replay succeeded after $priorFailures prior failure(s); resetting retry counter")
                    }
                    SusfsRepository.bootRetryCount = 0
                } else {
                    val newCount = priorFailures + 1
                    SusfsRepository.bootRetryCount = newCount
                    Log.w(
                        TAG,
                        "Replay finished with $totalFailures failure(s) " +
                            "(toggles=$toggleFailures, paths=$pathFailures, " +
                            "maps=$mapFailures, kstats=$kstatFailures, " +
                            "redirects=$redirectFailures); retry count=$newCount"
                    )
                }

                // 内置 Shizuku：如果用户启用了，开机自动启动 server。
                // 放在 SUSFS 重放之后，确保 root 权限和系统都已就绪。
                startShizukuIfEnabled(context)
            } catch (t: Throwable) {
                Log.e(TAG, "Boot replay crashed", t)
            } finally {
                pending.finish()
            }
        }
    }

    /**
     * 开机自动启动内置 Shizuku server（如果用户启用了）。
     *
     * 最多重试 2 次，每次间隔 10 秒。Shizuku server 启动需要 app_process
     * 和 binder 就绪，开机早期可能需要多等一会儿。
     */
    private fun startShizukuIfEnabled(context: Context) {
        if (!me.weishu.kernelsu.shizuku.ShizukuServiceManager.isEnabled()) {
            return
        }
        Log.i(TAG, "Shizuku auto-start begin")
        for (attempt in 1..2) {
            if (attempt > 1) {
                Thread.sleep(10_000L)
            }
            try {
                if (me.weishu.kernelsu.shizuku.ShizukuServiceManager.isServerRunning() ||
                    me.weishu.kernelsu.shizuku.ShizukuServiceManager.start(context)
                ) {
                    Log.i(TAG, "Shizuku server auto-start succeeded on attempt $attempt")
                    return
                }
            } catch (t: Throwable) {
                Log.w(TAG, "Shizuku auto-start attempt $attempt failed", t)
            }
        }
        Log.w(TAG, "Shizuku server auto-start failed after all attempts")
    }

    // ── toggles + uname ───────────────────────────────────────────────────────
    /** @return number of commands that failed. */
    private suspend fun replayToggles(): Int {
        var failures = 0
        if (SusfsRepository.logEnabled) {
            runCatching { SusfsCommands.setLogEnabled(true) }
                .onFailure { failures++; Log.w(TAG, "setLogEnabled failed", it) }
        }
        if (SusfsRepository.avcLogSpoofing) {
            runCatching { SusfsCommands.setAvcLogSpoofing(true) }
                .onFailure { failures++; Log.w(TAG, "setAvcLogSpoofing failed", it) }
        }
        if (SusfsRepository.hideSusMntsForNonSuProcs) {
            runCatching { SusfsCommands.setHideSusMntsForNonSuProcs(true) }
                .onFailure { failures++; Log.w(TAG, "setHideSusMnts failed", it) }
        }
        val release = SusfsRepository.unameRelease
        val version = SusfsRepository.unameVersion
        if (release.isNotEmpty() || version.isNotEmpty()) {
            runCatching { SusfsCommands.setUname(release, version) }
                .onFailure { failures++; Log.w(TAG, "setUname failed", it) }
        }
        return failures
    }

    // ── hidden paths (plain + loop) ───────────────────────────────────────────
    /** @return number of paths that failed to add. */
    private suspend fun replayPaths(): Int {
        var failures = 0
        for (path in SusfsRepository.susPaths) {
            runCatching { SusfsCommands.addSusPath(path) }
                .onFailure { failures++; Log.w(TAG, "addSusPath failed: $path", it) }
        }
        for (path in SusfsRepository.susPathLoops) {
            runCatching { SusfsCommands.addSusPathLoop(path) }
                .onFailure { failures++; Log.w(TAG, "addSusPathLoop failed: $path", it) }
        }
        return failures
    }

    // ── sus-map rules ─────────────────────────────────────────────────────────
    /** @return number of maps that failed to add. */
    private suspend fun replayMaps(): Int {
        var failures = 0
        for (path in SusfsRepository.susMaps) {
            runCatching { SusfsCommands.addSusMap(path) }
                .onFailure { failures++; Log.w(TAG, "addSusMap failed: $path", it) }
        }
        return failures
    }

    // ── kstat spoofing ─────────────────────────────────────────────────────────
    /**
     * Replay kstat rules by calling `update-sus-kstat` on each saved path.
     *
     * `add-sus-kstat` (the "begin tracking" step) is intentionally not
     * replayed: it only makes sense immediately before a bind mount, which
     * has already happened by the time boot completes. `update-sus-kstat`
     * re-stats the (now bind-mounted) file and installs the spoof, which is
     * the idempotent operation we want after boot.
     *
     * @return number of kstat paths that failed to update.
     */
    private suspend fun replayKstats(): Int {
        var failures = 0
        for (path in SusfsRepository.susKstatPaths) {
            runCatching { SusfsCommands.updateSusKstat(path) }
                .onFailure { failures++; Log.w(TAG, "updateSusKstat failed: $path", it) }
        }
        return failures
    }

    // ── open-redirect rules ────────────────────────────────────────────────────
    /** @return number of redirects that failed to add. */
    private suspend fun replayRedirects(): Int {
        var failures = 0
        for (packed in SusfsRepository.susRedirects) {
            val (target, redirected, uidScheme) = SusfsRepository.unpackRedirect(packed)
            if (target.isEmpty() || redirected.isEmpty()) {
                Log.w(TAG, "Skipping malformed redirect entry: $packed")
                failures++
                continue
            }
            runCatching { SusfsCommands.addOpenRedirect(target, redirected, uidScheme) }
                .onFailure { failures++; Log.w(TAG, "addOpenRedirect failed: $target -> $redirected", it) }
        }
        return failures
    }

    // ── boot wait ──────────────────────────────────────────────────────────────
    private fun waitForBootCompleted() {
        repeat(180) {
            val completed = Runtime.getRuntime()
                .exec(arrayOf("getprop", "sys.boot_completed"))
                .inputStream.bufferedReader().readText().trim()
            if (completed == "1") return
            Thread.sleep(1000)
        }
    }
}
