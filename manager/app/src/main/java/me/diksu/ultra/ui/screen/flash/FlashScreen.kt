package me.diksu.ultra.ui.screen.flash

import android.content.Intent
import android.net.VpnService
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.dropUnlessResumed
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.diksu.ultra.Natives
import me.diksu.ultra.R
import me.diksu.ultra.data.repository.isSoftRebootPreferred
import me.diksu.ultra.ui.LocalUiMode
import me.diksu.ultra.ui.UiMode
import me.diksu.ultra.ui.navigation3.LocalNavigator
import me.diksu.ultra.ui.util.reboot

@Composable
fun FlashScreen(flashIt: FlashIt, onBackEnabledChange: (Boolean) -> Unit) {
    val navigator = LocalNavigator.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var text by rememberSaveable { mutableStateOf("") }
    val logContent = remember { StringBuilder() }
    var showRebootAction by rememberSaveable { mutableStateOf(false) }
    var flashingStatus by rememberSaveable { mutableStateOf(FlashingStatus.FLASHING) }
    val needJailbreakWarning = flashIt is FlashIt.FlashBoot && Natives.isLateLoadMode
    // Soft reboot keeps the jailbreak and still applies modules
    val softReboot = flashIt is FlashIt.FlashModules && isSoftRebootPreferred()
    var flashingEnabled by rememberSaveable { mutableStateOf(!needJailbreakWarning) }
    val uiMode = LocalUiMode.current
    val snackbarHost = remember { SnackbarHostState() }
    val backEnabled = flashingStatus != FlashingStatus.FLASHING
    // ksud prints "[音量+] 继续安装" only when the partition guard is asking the user
    // to decide, and it blocks there for up to 60 seconds. Reading it back from the
    // log keeps the plumbing to one line and needs no new callback.
    val waitingForKeys = flashingStatus == FlashingStatus.FLASHING && text.contains("音量+")

    SideEffect { onBackEnabledChange(backEnabled) }
    NavigationBackHandler(
        state = rememberNavigationEventState(NavigationEventInfo.None),
        isBackEnabled = !backEnabled,
        onBackCompleted = {},
    )

    fun showMessage(message: String) {
        scope.launch {
            if (uiMode == UiMode.Material) {
                snackbarHost.showSnackbar(message)
            } else {
                Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
            }
        }
    }

    // 热安装：ksud 安装完就把暂存目录换成正式目录（不留 update 标记），moduleNeedsReboot()
    // 因此不会置 showRebootAction。这里补一句明确提示，免得用户以为还要重启。
    val rebootPending by rememberUpdatedState(showRebootAction)
    LaunchedEffect(flashingStatus) {
        if (flashingStatus == FlashingStatus.SUCCESS &&
            flashIt is FlashIt.FlashModules &&
            !rebootPending
        ) {
            showMessage(context.getString(R.string.module_hot_install_done))
        }
    }

    // SynOpt 要先拿到 tun 才能投递报文。VpnService.prepare() 非空时必须由
    // Activity 弹系统授权框 —— 之前直接在 flashIt() 里报错，用户根本没有
    // 机会授权，看到的就是“需要先授权 VPN 通道”。
    var vpnConsentReady by remember { mutableStateOf(false) }
    val vpnConsentLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        vpnConsentReady = result.resultCode == android.app.Activity.RESULT_OK
        if (!vpnConsentReady) {
            logContent.append("VPN 授权被拒绝，SynOpt 无法投递报文\n")
            text = text + "VPN 授权被拒绝，SynOpt 无法投递报文\n"
            flashingStatus = FlashingStatus.FAILED
        }
    }

    LaunchedEffect(flashIt, flashingEnabled) {
        if (flashIt !is FlashIt.SynOpt || !flashingEnabled) return@LaunchedEffect
        if (vpnConsentReady) return@LaunchedEffect
        val intent: Intent? = VpnService.prepare(context)
        if (intent == null) {
            vpnConsentReady = true
        } else {
            vpnConsentLauncher.launch(intent)
        }
    }

    FlashEffect(
        flashIt = flashIt,
        text = text,
        logContent = logContent,
        onTextUpdate = { text = it },
        onShowRebootChange = { showRebootAction = it },
        onFlashingStatusChange = { flashingStatus = it },
        enabled = flashingEnabled && (flashIt !is FlashIt.SynOpt || vpnConsentReady),
    )

    val state = FlashUiState(
        text = text,
        showRebootAction = showRebootAction,
        flashingStatus = flashingStatus,
        showJailbreakWarning = needJailbreakWarning && !flashingEnabled,
        rebootLabelRes = if (softReboot) R.string.reboot_soft else R.string.reboot,
        isJailbreak = flashIt is FlashIt.JailbreakExploit ||
            flashIt is FlashIt.DirtyFrag ||
            flashIt is FlashIt.SynOpt,
        waitingForKeys = waitingForKeys,
    )
    val actions = FlashScreenActions(
        onBack = dropUnlessResumed { if (backEnabled) navigator.pop() },
        onSaveLog = saveLog(logContent, scope) { showMessage(it) },
        onReboot = {
            scope.launch {
                withContext(Dispatchers.IO) {
                    reboot(if (softReboot) "soft_reboot" else "")
                }
            }
        },
        onConfirmJailbreakWarning = { flashingEnabled = true },
        onDismissJailbreakWarning = dropUnlessResumed { navigator.pop() },
    )

    when (LocalUiMode.current) {
        UiMode.Miuix, UiMode.MiuixStock -> FlashScreenMiuix(state, actions)
        UiMode.Material -> FlashScreenMaterial(state, actions, snackbarHost)
    }
}
