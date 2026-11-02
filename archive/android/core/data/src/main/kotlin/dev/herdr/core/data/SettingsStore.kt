package dev.herdr.core.data

import android.content.Context
import dev.herdr.core.ssh.SshConfig
import java.io.File

/**
 * 主机设置 + 私钥文件管理（M1 用 SharedPreferences + app 私有目录）。
 * 私钥只落在 filesDir，绝不出现在日志里。
 */
class SettingsStore(context: Context) {

    private val prefs = context.getSharedPreferences("herdr-settings", Context.MODE_PRIVATE)
    private val dir: File = File(context.filesDir, "keys").apply { mkdirs() }

    data class Host(
        val hostId: String = "default",
        val host: String = "",
        val port: Int = 22,
        val user: String = "",
        val session: String = "test",
        val herdrBin: String = "/home/luxiang/.local/bin/herdr",
    )

    fun host(): Host = Host(
        hostId = prefs.getString("hostId", "default") ?: "default",
        host = prefs.getString("host", "") ?: "",
        port = prefs.getInt("port", 22),
        user = prefs.getString("user", "") ?: "",
        session = prefs.getString("session", "test") ?: "test",
        herdrBin = prefs.getString("herdrBin", "/home/luxiang/.local/bin/herdr")
            ?: "/home/luxiang/.local/bin/herdr",
    )

    fun saveHost(h: Host) {
        prefs.edit()
            .putString("hostId", h.hostId)
            .putString("host", h.host)
            .putInt("port", h.port)
            .putString("user", h.user)
            .putString("session", h.session)
            .putString("herdrBin", h.herdrBin)
            .apply()
    }

    fun keyFile(): File = File(dir, "id_ed25519")

    fun hasKey(): Boolean = keyFile().exists() && keyFile().length() > 0

    /** 导入 OpenSSH 私钥（粘贴文本）。只做最小校验：必须是 PEM 私钥。 */
    fun importKey(text: String): Result<File> = runCatching {
        val trimmed = text.trim()
        require(trimmed.contains("PRIVATE KEY-----")) { "内容不像私钥（缺少 PRIVATE KEY 头）" }
        val f = keyFile()
        f.writeText(trimmed + "\n")
        runCatching { f.setReadable(false, false); f.setReadable(true, true) }
        f
    }

    fun clearKey() {
        keyFile().delete()
    }

    fun sshConfig(): SshConfig? {
        val h = host()
        if (h.host.isBlank() || h.user.isBlank()) return null
        if (!hasKey()) return null
        return SshConfig(
            hostId = h.hostId,
            host = h.host,
            port = h.port,
            user = h.user,
            herdrSession = h.session,
            herdrBin = h.herdrBin,
            keyFile = keyFile(),
        )
    }
}
