package me.weishu.kernelsu.shizuku

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * 响应旧版 Shizuku 客户端的 Binder 请求广播，与官方 Manager 对齐。
 */
class ShizukuReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if ("rikka.shizuku.intent.action.REQUEST_BINDER" == intent.action) {
            ShellBinderRequestHandler.handleRequest(context, intent)
        }
    }
}
