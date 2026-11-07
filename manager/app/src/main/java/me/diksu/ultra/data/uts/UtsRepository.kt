package me.diksu.ultra.data.uts

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.diksu.ultra.ui.util.getKsuDaemonPath
import me.diksu.ultra.ui.util.getRootShell
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * UTS 视图配置，字段与内核 `ksu_uts_config` 一一对应。
 *
 * [fieldMask] 决定哪些字段生效：只改 release 时不要把 sysname 一起改掉，否则
 * `uname -s` 也会变成用户填的东西。
 */
data class UtsConfig(
    val mode: Int = MODE_DISABLED,
    val fieldMask: Int = 0,
    val targetUids: List<Int> = emptyList(),
    val sysname: String = "",
    val nodename: String = "",
    val release: String = "",
    val version: String = "",
    val machine: String = "",
    val domainname: String = "",
) {
    val enabled: Boolean get() = mode != MODE_DISABLED && fieldMask != 0
    val selectedMode: Boolean get() = mode == MODE_SELECTED

    fun withField(index: Int, value: String): UtsConfig {
        val bit = 1 shl index
        val mask = if (value.isNotEmpty()) fieldMask or bit else fieldMask and bit.inv()
        return when (index) {
            0 -> copy(sysname = value, fieldMask = mask)
            1 -> copy(nodename = value, fieldMask = mask)
            2 -> copy(release = value, fieldMask = mask)
            3 -> copy(version = value, fieldMask = mask)
            4 -> copy(machine = value, fieldMask = mask)
            else -> copy(domainname = value, fieldMask = mask)
        }
    }

    fun field(index: Int): String = when (index) {
        0 -> sysname
        1 -> nodename
        2 -> release
        3 -> version
        4 -> machine
        else -> domainname
    }

    fun toJson(): JSONObject = JSONObject().apply {
        put("mode", mode)
        put("field_mask", fieldMask)
        put("target_uids", JSONArray(targetUids))
        put("sysname", sysname)
        put("nodename", nodename)
        put("release", release)
        put("version", version)
        put("machine", machine)
        put("domainname", domainname)
    }

    companion object {
        const val MODE_DISABLED = 0
        const val MODE_GLOBAL = 1
        const val MODE_SELECTED = 2

        fun parse(text: String): UtsConfig {
            val json = JSONObject(text)
            val uids = json.optJSONArray("target_uids")?.let { array ->
                (0 until array.length()).map { array.getInt(it) }
            }.orEmpty()
            return UtsConfig(
                mode = json.optInt("mode", MODE_DISABLED),
                fieldMask = json.optInt("field_mask", 0),
                targetUids = uids,
                sysname = json.optString("sysname"),
                nodename = json.optString("nodename"),
                release = json.optString("release"),
                version = json.optString("version"),
                machine = json.optString("machine"),
                domainname = json.optString("domainname"),
            )
        }
    }
}

/**
 * 通过 `ksud uts get|set` 读写内核里的 UTS 视图配置。
 *
 * 读回来的是 JSON，写回去也走 stdin —— 与 SuSFS 那套协议保持一致，避免临时文件。
 */
class UtsRepository {

    suspend fun read(): UtsConfig? = withContext(Dispatchers.IO) {
        val result = runCatching { execute("uts get") }.getOrNull() ?: return@withContext null
        if (!result.first) return@withContext null
        runCatching { UtsConfig.parse(result.second) }.getOrNull()
    }

    suspend fun write(config: UtsConfig): String? = withContext(Dispatchers.IO) {
        val json = config.toJson().toString()
        val result = runCatching { execute("uts set", json) }.getOrNull()
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
