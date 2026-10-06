package me.weishu.kernelsu.shizuku

import android.content.pm.ApplicationInfo
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material.icons.outlined.Timelapse
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import me.weishu.kernelsu.R
import me.weishu.kernelsu.ui.LocalUiMode
import me.weishu.kernelsu.ui.isMiuixFamily
import me.weishu.kernelsu.ui.theme.KernelSUTheme
import rikka.shizuku.Shizuku
import rikka.shizuku.ShizukuApiConstants
import top.yukonga.miuix.kmp.basic.Icon as MiuixIcon
import top.yukonga.miuix.kmp.basic.Text as MiuixText
import top.yukonga.miuix.kmp.basic.TextButton as MiuixTextButton
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Shizuku 授权确认界面。
 *
 * Shizuku Server（app_process 进程）在第三方应用请求权限时，
 * 通过广播拉起本界面，用户选择允许/仅一次/拒绝后，
 * 通过 Shizuku binder 将结果回传 Server。
 */
class ShizukuPermissionActivity : ComponentActivity() {

    companion object {
        private const val TAG = "ShizukuPerm"
    }

    private var uid = -1
    private var pid = -1
    private var requestCode = -1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val ai = if (Build.VERSION.SDK_INT >= 33) {
            intent.getParcelableExtra("applicationInfo", ApplicationInfo::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra("applicationInfo")
        }
        uid = intent.getIntExtra("uid", -1)
        pid = intent.getIntExtra("pid", -1)
        requestCode = intent.getIntExtra("requestCode", -1)
        Log.i(TAG, "onCreate: action=${intent.action} uid=$uid pid=$pid requestCode=$requestCode pkg=${ai?.packageName}")
        if (uid == -1 || pid == -1 || ai == null) {
            Log.w(TAG, "invalid request intent, finishing")
            finish()
            return
        }

        val label = try {
            ai.loadLabel(packageManager).toString()
        } catch (e: Exception) {
            ai.packageName
        }
        val icon = try {
            ai.loadIcon(packageManager)
        } catch (e: Exception) {
            null
        }

        setContent {
            KernelSUTheme {
                val uiMode = LocalUiMode.current
                if (uiMode.isMiuixFamily) {
                    PermissionDialogMiuix(
                        label = label,
                        icon = icon,
                        onAllow = { reply(allowed = true, onetime = false) },
                        onAllowOnce = { reply(allowed = true, onetime = true) },
                        onDeny = { reply(allowed = false, onetime = true) },
                        onDismiss = { reply(allowed = false, onetime = true) },
                    )
                } else {
                    PermissionDialogMaterial(
                        label = label,
                        icon = icon,
                        onAllow = { reply(allowed = true, onetime = false) },
                        onAllowOnce = { reply(allowed = true, onetime = true) },
                        onDeny = { reply(allowed = false, onetime = true) },
                        onDismiss = { reply(allowed = false, onetime = true) },
                    )
                }
            }
        }
    }

    private fun reply(allowed: Boolean, onetime: Boolean) {
        val data = Bundle()
        data.putBoolean(ShizukuApiConstants.REQUEST_PERMISSION_REPLY_ALLOWED, allowed)
        data.putBoolean(ShizukuApiConstants.REQUEST_PERMISSION_REPLY_IS_ONETIME, onetime)
        try {
            if (!waitForBinder()) {
                Log.e(TAG, "binder not available, cannot dispatch result")
                Toast.makeText(this, R.string.shizuku_permission_binder_timeout, Toast.LENGTH_LONG).show()
                finish()
                return
            }
            Shizuku.dispatchPermissionConfirmationResult(uid, pid, requestCode, data)
        } catch (t: Throwable) {
            Log.e(TAG, "dispatchPermissionConfirmationResult failed", t)
        }
        finish()
    }

    /** Server 可能在拉起弹窗后才完成 binder 投递，这里等待它就绪（最多 5 秒）。 */
    private fun waitForBinder(): Boolean {
        if (Shizuku.pingBinder()) return true
        val latch = CountDownLatch(1)
        val listener = object : Shizuku.OnBinderReceivedListener {
            override fun onBinderReceived() {
                Shizuku.removeBinderReceivedListener(this)
                latch.countDown()
            }
        }
        Shizuku.addBinderReceivedListenerSticky(listener)
        if (Shizuku.pingBinder()) {
            latch.countDown()
        }
        return try {
            latch.await(5, TimeUnit.SECONDS)
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
            false
        }
    }
}

// ── Miuix 版本 ──────────────────────────────────────────────────────────────

@Composable
private fun PermissionDialogMiuix(
    label: String,
    icon: Drawable?,
    onAllow: () -> Unit,
    onAllowOnce: () -> Unit,
    onDeny: () -> Unit,
    onDismiss: () -> Unit,
) {
    OverlayDialog(
        show = true,
        title = "Shizuku 权限请求",
        onDismissRequest = onDismiss,
        content = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (icon != null) {
                        Image(
                            painter = BitmapPainter(icon.toBitmap().asImageBitmap()),
                            contentDescription = null,
                            modifier = Modifier.size(48.dp),
                        )
                    } else {
                        MiuixIcon(
                            imageVector = Icons.Filled.WaterDrop,
                            contentDescription = null,
                            modifier = Modifier.size(48.dp),
                            tint = colorScheme.primary,
                        )
                    }
                    Spacer(Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        MiuixText(
                            text = label,
                            fontSize = 18,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Spacer(Modifier.height(2.dp))
                        MiuixText(
                            text = "请求使用 Shizuku 执行操作",
                            fontSize = 13,
                            color = colorScheme.onSurfaceVariant,
                        )
                    }
                }

                Spacer(Modifier.height(4.dp))

                PermissionOptionMiuix(
                    icon = Icons.Filled.Check,
                    title = "允许",
                    subtitle = "永久允许该应用使用 Shizuku",
                    container = colorScheme.primaryContainer,
                    content = colorScheme.onPrimaryContainer,
                    onClick = onAllow,
                )
                PermissionOptionMiuix(
                    icon = Icons.Outlined.Timelapse,
                    title = "仅一次",
                    subtitle = "本次允许，下次重新询问",
                    container = colorScheme.surfaceContainer,
                    content = colorScheme.onSurfaceVariant,
                    onClick = onAllowOnce,
                )
                PermissionOptionMiuix(
                    icon = Icons.Filled.Block,
                    title = "拒绝",
                    subtitle = "拒绝该应用使用 Shizuku",
                    container = colorScheme.surfaceContainer,
                    content = colorScheme.onSurfaceVariant,
                    onClick = onDeny,
                )
            }
        },
        action = {
            MiuixTextButton(onClick = onDismiss) {
                MiuixText("取消")
            }
        },
    )
}

@Composable
private fun PermissionOptionMiuix(
    icon: ImageVector,
    title: String,
    subtitle: String,
    container: Color,
    content: Color,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(container)
            .clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            MiuixIcon(
                imageVector = icon,
                contentDescription = null,
                tint = content,
                modifier = Modifier.size(24.dp),
            )
            Spacer(Modifier.width(16.dp))
            Column {
                MiuixText(
                    text = title,
                    fontSize = 15,
                    fontWeight = FontWeight.SemiBold,
                    color = content,
                )
                Spacer(Modifier.height(2.dp))
                MiuixText(
                    text = subtitle,
                    fontSize = 12,
                    color = content.copy(alpha = 0.8f),
                )
            }
        }
    }
}

// ── Material 版本 ────────────────────────────────────────────────────────────

@Composable
private fun PermissionDialogMaterial(
    label: String,
    icon: Drawable?,
    onAllow: () -> Unit,
    onAllowOnce: () -> Unit,
    onDeny: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (icon != null) {
                    Image(
                        painter = BitmapPainter(icon.toBitmap().asImageBitmap()),
                        contentDescription = null,
                        modifier = Modifier.size(48.dp),
                    )
                } else {
                    androidx.compose.material3.Icon(
                        imageVector = Icons.Filled.WaterDrop,
                        contentDescription = null,
                        modifier = Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
                Spacer(Modifier.width(16.dp))
                Text(
                    text = label,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "请求 Shizuku 权限",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(4.dp))
                PermissionOptionMaterial(
                    icon = Icons.Filled.Check,
                    title = "允许",
                    subtitle = "永久允许该应用使用 Shizuku",
                    container = MaterialTheme.colorScheme.primaryContainer,
                    content = MaterialTheme.colorScheme.onPrimaryContainer,
                    onClick = onAllow,
                )
                PermissionOptionMaterial(
                    icon = Icons.Outlined.Timelapse,
                    title = "仅一次",
                    subtitle = "本次允许，下次重新询问",
                    container = MaterialTheme.colorScheme.surfaceContainer,
                    content = MaterialTheme.colorScheme.onSurfaceVariant,
                    onClick = onAllowOnce,
                )
                PermissionOptionMaterial(
                    icon = Icons.Filled.Block,
                    title = "拒绝",
                    subtitle = "拒绝该应用使用 Shizuku",
                    container = MaterialTheme.colorScheme.surfaceContainer,
                    content = MaterialTheme.colorScheme.onSurfaceVariant,
                    onClick = onDeny,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDeny) {
                Text("取消")
            }
        },
    )
}

@Composable
private fun PermissionOptionMaterial(
    icon: ImageVector,
    title: String,
    subtitle: String,
    container: Color,
    content: Color,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.medium,
        color = container,
        contentColor = content,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            androidx.compose.material3.Icon(
                imageVector = icon,
                contentDescription = null,
                tint = content,
                modifier = Modifier.size(24.dp),
            )
            Spacer(Modifier.width(16.dp))
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = content.copy(alpha = 0.8f),
                )
            }
        }
    }
}
