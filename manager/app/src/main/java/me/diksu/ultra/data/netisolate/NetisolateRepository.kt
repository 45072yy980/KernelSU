package me.diksu.ultra.data.netisolate

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.diksu.ultra.ui.util.getKsuDaemonPath
import me.diksu.ultra.ui.util.getRootShell
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * 网络隔离配置，字段与内核 `ksu_netisolate_config` 一一对应。
 *
 * [mode] 是主开关，[flags] 决定回环与内网算不算“放行”。
 */
data class NetisolateConfig(
    val mode: Int = MODE_DISABLED,
    val flags: Int = 0,
    val targetUids: List<Int> = emptyList(),
    val supported: Boolean = true,
) {
    val active: Boolean get() = mode == MODE_SELECTED && targetUids.isNotEmpty()
    val allowLoopback: Boolean get() = flags and FLAG_ALLOW_LOOPBACK != 0
    val allowPrivate: Boolean get() = flags and FLAG_ALLOW_PRIVATE != 0

    fun withFlag(flag: Int, enabled: Boolean): NetisolateConfig =
        copy(flags = if (enabled) flags or flag else flags and flag.inv())

    fun toJson(): JSONObject = JSONObject().apply {
        put("mode", mode)
        put("flags", flags)
        put("target_uids", JSONArray(targetUids))
    }

    companion object {
        const val MODE_DISABLED = 0
        const val MODE_SELECTED = 1

        const val FLAG_ALLOW_LOOPBACK = 1
        const val FLAG_ALLOW_PRIVATE = 2

        fun parse(text: String): NetisolateConfig {
            val json = JSONObject(text)
            val uids = json.optJSONArray("target_uids")?.let { array ->
                (0 until array.length()).map { array.getInt(it) }
            }.orEmpty()
            return NetisolateConfig(
                mode = json.optInt("mode", MODE_DISABLED),
                flags = json.optInt("flags", 0),
                targetUids = uids,
                supported = json.optInt("supported", 1) != 0,
            )
        }
    }
}

/**
 * 通过 `ksud netisolate get|set` 读写内核里的网络隔离配置。
 *
 * 与其他特性一样走 stdin 传 JSON，不在应用私有目录落临时文件 —— root 读那边在
 * SELinux 下不可靠。
 */
class NetisolateRepository {

    suspend fun read(): NetisolateConfig? = withContext(Dispatchers.IO) {
        val result = runCatching { execute("netisolate get") }.getOrNull() ?: return@withContext null
        if (!result.first) return@withContext null
        runCatching { NetisolateConfig.parse(result.second) }.getOrNull()
    }

    suspend fun write(config: NetisolateConfig): String? = withContext(Dispatchers.IO) {
        val json = config.toJson().toString()
        val result = runCatching { execute("netisolate set", json) }.getOrNull()
            ?: return@withContext "命令执行失败"
        if (result.first) null else result.second.take(200)
    }

    /** 返回 (是否成功, 输出)。 */
    private fun execute(args: String, input: String? = null): Pair<Boolean, String> {
        val shell = getRootShell()
        val invocation = "${getKsuDaemonPath()} $args"
        val command = if (input == null) {
            invocation
        } else {
            "printf '%s' ${quote(input)} | $invocation"
        }
        val out = mutableListOf<String>()
        val err = mutableListOf<String>()
        val future = shell.newJob().add(command).to(out, err).enqueue()
        val result = runCatching { future.get(20, TimeUnit.SECONDS) }
            .getOrElse {
                future.cancel(false)
                return false to "命令超时"
            }
        val text = out.joinToString("\n").trim().ifEmpty { err.joinToString("\n").trim() }
        return result.isSuccess to text
    }

    private fun quote(value: String): String = "'" + value.replace("'", "'\\''") + "'"
}
