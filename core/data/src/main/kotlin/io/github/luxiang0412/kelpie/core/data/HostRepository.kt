package io.github.luxiang0412.kelpie.core.data

import io.github.luxiang0412.kelpie.core.herdr.t

import android.content.Context
import io.github.luxiang0412.kelpie.core.ssh.SshConfig
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

/**
 * 多主机配置。
 *
 * - 每台主机有自己的 id、标签、SSH 参数、herdr 会话名、私钥文件；
 * - 私钥落在 `filesDir/keys/<hostId>.key`（0600）；
 * - 配置序列化成 JSON 存在 SharedPreferences（只存非敏感字段）。
 */
class HostRepository(context: Context) {

    private val _hosts = kotlinx.coroutines.flow.MutableStateFlow<List<HostConfig>>(emptyList())
    /** 主机列表变化（增删改）时发射；UI 与连接层订阅它。 */
    val hosts: kotlinx.coroutines.flow.StateFlow<List<HostConfig>> get() = _hosts


    data class HostConfig(
        val id: String,
        val label: String,
        val host: String,
        val port: Int = 22,
        val user: String,
        val session: String = "test",
        val herdrBin: String = "/home/luxiang/.local/bin/herdr",
        /** 审批请求目录，和 hook 的 KELPIE_APPROVALS_DIR 一致（见 [ApprovalsPath]）。 */
        val approvalsDir: String = ApprovalsPath.DEFAULT,
    ) {
        fun toSshConfig(keyFile: File) = SshConfig(
            hostId = id,
            host = host,
            port = port,
            user = user,
            herdrSession = session,
            herdrBin = herdrBin,
            keyFile = keyFile,
        )
    }

    private val prefs = context.getSharedPreferences("herdr-hosts", Context.MODE_PRIVATE)
    private val keyDir: File = File(context.filesDir, "keys").apply { mkdirs() }

    init { _hosts.value = list() }

    fun list(): List<HostConfig> {
        val raw = prefs.getString(KEY_HOSTS, null) ?: return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                HostConfig(
                    id = o.getString("id"),
                    label = o.optString("label", o.getString("host")),
                    host = o.getString("host"),
                    port = o.optInt("port", 22),
                    user = o.getString("user"),
                    session = o.optString("session", "test"),
                    herdrBin = o.optString("herdrBin", DEFAULT_BIN),
                    approvalsDir = o.optString("approvalsDir", ApprovalsPath.DEFAULT),
                )
            }
        }.getOrDefault(emptyList())
    }

    fun get(id: String): HostConfig? = list().firstOrNull { it.id == id }

    fun upsert(cfg: HostConfig) {
        val next = list().filterNot { it.id == cfg.id } + cfg
        save(next)
    }

    fun remove(id: String) {
        save(list().filterNot { it.id == id })
        keyFile(id).delete()
        if (activeId() == id) prefs.edit().remove(KEY_ACTIVE).apply()
    }

    fun activeId(): String? = prefs.getString(KEY_ACTIVE, null)?.takeIf { id -> list().any { it.id == id } }

    fun setActive(id: String) = prefs.edit().putString(KEY_ACTIVE, id).apply()

    fun keyFile(hostId: String): File = File(keyDir, "$hostId.key")

    fun hasKey(hostId: String): Boolean = keyFile(hostId).let { it.exists() && it.length() > 0 }

    fun importKey(hostId: String, text: String): Result<File> = runCatching {
        val trimmed = text.trim()
        require(trimmed.contains("PRIVATE KEY-----")) { t("内容不像私钥（缺少 PRIVATE KEY 头）", "This doesn't look like a private key (no PRIVATE KEY header)") }
        val f = keyFile(hostId)
        f.writeText(trimmed + "\n")
        runCatching { f.setReadable(false, false); f.setReadable(true, true) }
        _hosts.value = list()
        f
    }

    fun deleteKey(hostId: String) { keyFile(hostId).delete() }

    fun sshConfig(id: String): SshConfig? {
        val cfg = get(id) ?: return null
        if (cfg.host.isBlank() || cfg.user.isBlank() || !hasKey(id)) return null
        return cfg.toSshConfig(keyFile(id))
    }

    fun newId(): String = UUID.randomUUID().toString().take(8)

    private fun save(hosts: List<HostConfig>) {
        val arr = JSONArray()
        hosts.forEach { h ->
            arr.put(
                JSONObject()
                    .put("id", h.id)
                    .put("label", h.label)
                    .put("host", h.host)
                    .put("port", h.port)
                    .put("user", h.user)
                    .put("session", h.session)
                    .put("herdrBin", h.herdrBin)
                    .put("approvalsDir", h.approvalsDir),
            )
        }
        prefs.edit().putString(KEY_HOSTS, arr.toString()).apply()
        _hosts.value = hosts
    }

    companion object {
        private const val KEY_HOSTS = "hosts"
        private const val KEY_ACTIVE = "active"
        private const val DEFAULT_BIN = "/home/luxiang/.local/bin/herdr"
    }
}
