package me.weishu.kernelsu.susfs

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
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
 * Each rule is replayed independently inside a try/catch so that one stale
 * path (e.g. a file removed since last boot) cannot abort the rest.
 */
class SusfsBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                waitForBootCompleted()

                // Bail out silently if neither built-in SUSFS nor the LKM
                // answered the supercall — there is nothing to replay into.
                if (!SusfsCommands.loadStatus().loaded) return@launch

                replayToggles()
                replayPaths()
                replayMaps()
                replayKstats()
                replayRedirects()
            } finally {
                pending.finish()
            }
        }
    }

    // ── toggles + uname ───────────────────────────────────────────────────────
    private suspend fun replayToggles() {
        if (SusfsRepository.logEnabled) {
            runCatching { SusfsCommands.setLogEnabled(true) }
        }
        if (SusfsRepository.avcLogSpoofing) {
            runCatching { SusfsCommands.setAvcLogSpoofing(true) }
        }
        if (SusfsRepository.hideSusMntsForNonSuProcs) {
            runCatching { SusfsCommands.setHideSusMntsForNonSuProcs(true) }
        }
        val release = SusfsRepository.unameRelease
        val version = SusfsRepository.unameVersion
        if (release.isNotEmpty() || version.isNotEmpty()) {
            runCatching { SusfsCommands.setUname(release, version) }
        }
    }

    // ── hidden paths (plain + loop) ───────────────────────────────────────────
    private suspend fun replayPaths() {
        for (path in SusfsRepository.susPaths) {
            runCatching { SusfsCommands.addSusPath(path) }
        }
        for (path in SusfsRepository.susPathLoops) {
            runCatching { SusfsCommands.addSusPathLoop(path) }
        }
    }

    // ── sus-map rules ─────────────────────────────────────────────────────────
    private suspend fun replayMaps() {
        for (path in SusfsRepository.susMaps) {
            runCatching { SusfsCommands.addSusMap(path) }
        }
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
     */
    private suspend fun replayKstats() {
        for (path in SusfsRepository.susKstatPaths) {
            runCatching { SusfsCommands.updateSusKstat(path) }
        }
    }

    // ── open-redirect rules ────────────────────────────────────────────────────
    private suspend fun replayRedirects() {
        for (packed in SusfsRepository.susRedirects) {
            val (target, redirected, uidScheme) = SusfsRepository.unpackRedirect(packed)
            if (target.isEmpty() || redirected.isEmpty()) continue
            runCatching { SusfsCommands.addOpenRedirect(target, redirected, uidScheme) }
        }
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
