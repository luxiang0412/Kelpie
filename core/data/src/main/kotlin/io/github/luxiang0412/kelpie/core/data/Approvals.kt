package io.github.luxiang0412.kelpie.core.data

import io.github.luxiang0412.kelpie.core.herdr.t

import io.github.luxiang0412.kelpie.core.ssh.SshConnection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.schmizz.sshj.sftp.OpenMode
import org.json.JSONObject
import java.util.EnumSet

/**
 * L1 结构化审批（D5 契约的最小落地，M4）。
 *
 * 请求/回答都是**文件 IPC**（沙箱目录），不依赖屏幕读取：
 *   `<dir>/<request_id>.req`     由“请求方”（pi 的 bash 工具 / 未来的 Claude hook）写入
 *   `<dir>/<request_id>.answer`  由本 App 写入 `{request_id, nonce, decision}`
 *
 * 安全要点（与 D5 一致）：
 * - 回答必须带 **nonce**（防止重放/串单）；
 * - 过期请求不展示、不接受；
 * - 只写单文件，原子写（tmp + rename 语义由 SFTP 侧尽力而为）。
 */
class Approvals(
    private val conn: SshConnection,
    private val dir: String,
) {
    data class Request(
        val hostId: String,
        val requestId: String,
        val nonce: String,
        val question: String,
        val createdAtMs: Long,
        val expiresAtMs: Long,
        val path: String,
        /** 可选的结构化字段（schema_version ≥ 1 时请求方可以填）：哪个 pane、什么工具、什么命令。 */
        val paneId: String? = null,
        val tool: String? = null,
        val command: String? = null,
        val cwd: String? = null,
        val agent: String? = null,
    ) {
        val expired: Boolean get() = System.currentTimeMillis() > expiresAtMs
    }

    suspend fun poll(): List<Request> = withContext(Dispatchers.IO) {
        val sftp = conn.openSftp()
        try {
            if (runCatching { sftp.statExistence(dir) }.getOrNull() == null) return@withContext emptyList()
            val entries = sftp.ls(dir)
            // 已经回答过、但请求方还没来得及清理的请求：不再显示（否则会被重复回答）
            val answered = entries.map { it.name }.filter { it.endsWith(".answer") }.map { it.removeSuffix(".answer") }.toSet()
            entries.mapNotNull { e ->
                val name = e.name
                if (!name.endsWith(".req")) return@mapNotNull null
                if (name.removeSuffix(".req") in answered) return@mapNotNull null
                val full = "$dir/$name"
                val text = runCatching {
                    sftp.open(full, EnumSet.of(OpenMode.READ)).use { f ->
                        val size = f.length().toInt()
                        val buf = ByteArray(size)
                        var read = 0
                        while (read < size) {
                            val n = f.read(read.toLong(), buf, read, size - read)
                            if (n <= 0) break
                            read += n
                        }
                        String(buf, 0, read, Charsets.UTF_8)
                    }
                }.getOrNull() ?: return@mapNotNull null
                val o = runCatching { JSONObject(text) }.getOrNull() ?: return@mapNotNull null
                val id = o.optString("request_id").takeIf { it.isNotBlank() } ?: return@mapNotNull null
                val nonce = o.optString("nonce").takeIf { it.isNotBlank() } ?: return@mapNotNull null
                Request(
                    hostId = "",
                    requestId = id,
                    nonce = nonce,
                    question = o.optString("question", t("(无问题描述)", "(no description)")),
                    createdAtMs = o.optLong("created_at") * 1000,
                    expiresAtMs = o.optLong("expires_at") * 1000,
                    path = full,
                    paneId = o.optString("pane_id").takeIf { it.isNotBlank() },
                    tool = o.optString("tool").takeIf { it.isNotBlank() },
                    command = o.optString("command").takeIf { it.isNotBlank() },
                    cwd = o.optString("cwd").takeIf { it.isNotBlank() },
                    agent = o.optString("agent").takeIf { it.isNotBlank() },
                )
            }.sortedBy { it.createdAtMs }
        } finally {
            runCatching { sftp.close() }
        }
    }

    /** 写回答；decision = allow / deny。 */
    suspend fun answer(request: Request, allow: Boolean): Boolean = withContext(Dispatchers.IO) {
        val payload = JSONObject()
            .put("request_id", request.requestId)
            .put("nonce", request.nonce)
            .put("decision", if (allow) "allow" else "deny")
            .put("answered_at", System.currentTimeMillis() / 1000)
            .toString()
        val sftp = conn.openSftp()
        try {
            val target = "$dir/${request.requestId}.answer"
            val tmp = target + ".tmp"
            sftp.open(tmp, EnumSet.of(OpenMode.WRITE, OpenMode.CREAT, OpenMode.TRUNC)).use { f ->
                val bytes = (payload + "\n").toByteArray(Charsets.UTF_8)
                f.write(0, bytes, 0, bytes.size)
            }
            // 尽量原子：rename 到最终名
            runCatching { sftp.rename(tmp, target) }.onFailure {
                // 某些服务器不允许覆盖时，退化为直接写目标
                runCatching {
                    sftp.open(target, EnumSet.of(OpenMode.WRITE, OpenMode.CREAT, OpenMode.TRUNC)).use { f ->
                        val bytes = (payload + "\n").toByteArray(Charsets.UTF_8)
                        f.write(0, bytes, 0, bytes.size)
                    }
                }
            }
            true
        } catch (e: Exception) {
            false
        } finally {
            runCatching { sftp.close() }
        }
    }
}
