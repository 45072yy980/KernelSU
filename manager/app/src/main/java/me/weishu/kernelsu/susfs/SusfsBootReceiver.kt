package me.weishu.kernelsu.susfs

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Re-applies the user's SuSFS settings after boot.
 *
 * The kernel module keeps nothing across reboots, so every toggle the user
 * flipped in the panel must be re-sent once the module is loaded. This
 * receiver waits for `sys.boot_completed` (the same signal the module's
 * service.sh waits for) and then fires the saved settings at ksud.
 */
class SusfsBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                waitForBootCompleted()

                // Only replay if the module actually answered the supercall.
                if (!SusfsCommands.loadStatus().loaded) return@launch

                if (SusfsRepository.logEnabled) {
                    SusfsCommands.setLogEnabled(true)
                }
                if (SusfsRepository.avcLogSpoofing) {
                    SusfsCommands.setAvcLogSpoofing(true)
                }
                if (SusfsRepository.hideSusMntsForNonSuProcs) {
                    SusfsCommands.setHideSusMntsForNonSuProcs(true)
                }
                val release = SusfsRepository.unameRelease
                val version = SusfsRepository.unameVersion
                if (release.isNotEmpty() || version.isNotEmpty()) {
                    SusfsCommands.setUname(release, version)
                }
            } finally {
                pending.finish()
            }
        }
    }

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
