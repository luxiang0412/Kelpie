package dev.herdr.core.data

import dev.herdr.core.herdr.ClaudeSessionLocator
import dev.herdr.core.ssh.SshConnection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.schmizz.sshj.sftp.OpenMode
import java.util.EnumSet

/**
 * 方案 C 的 IO 部分：按 cwd 定位 Claude 会话文件。
 *
 * **只读**：仅列举 `~/.claude/projects/<slug>/` 并读候选文件头部 8KB 做 sessionId 对账。
 * 任何一步失败都返回 `Failure`（带原因），由调用方降级为「终端尾部」，绝不猜别的会话。
 */
class ClaudeSessionResolver {

    sealed interface Result {
        data class Found(val path: String, val reason: String) : Result
        data class Failure(val code: String, val message: String) : Result
    }

    /** 候选文件必须在此时间窗内（默认 24h），避免挂到几天前的旧会话。 */
    var windowMs: Long = 24 * 60 * 60 * 1000L

    suspend fun resolve(
        conn: SshConnection,
        cwd: String?,
        expectedSessionId: String?,
        homeDir: String = "",
        nowMs: Long = System.currentTimeMillis(),
    ): Result = withContext(Dispatchers.IO) {
        if (cwd.isNullOrBlank()) return@withContext Result.Failure("no_cwd", "pane 没有上报 cwd")
        if (expectedSessionId.isNullOrBlank()) {
            return@withContext Result.Failure("no_session_id", "herdr 未提供会话 id，无法对账")
        }
        val slug = ClaudeSessionLocator.slugFor(cwd)
        val dir = "${homeDir.trimEnd('/')}/.claude/projects/$slug"
        val sftp = try {
            conn.sharedSftp()
        } catch (e: Throwable) {
            return@withContext Result.Failure("no_sftp", e.message ?: "SFTP 不可用")
        }
        val candidates = try {
            sftp.ls(dir)
                .filter { it.isRegularFile && it.name.endsWith(".jsonl") }
                .map { ClaudeSessionLocator.Candidate("$dir/${it.name}", it.attributes.mtime * 1000L) }
        } catch (e: Throwable) {
            return@withContext Result.Failure("dir_missing", "找不到目录 $dir")
        }
        if (candidates.isEmpty()) return@withContext Result.Failure("no_file", "$dir 下没有 jsonl")

        val latest = ClaudeSessionLocator.pickLatest(candidates, nowMs, windowMs)
            ?: return@withContext Result.Failure(
                "stale",
                "最新的会话文件超出 ${windowMs / 3600_000}h 窗口，可能是旧会话",
            )

        val head = try {
            readHead(sftp, latest.path)
        } catch (e: Throwable) {
            return@withContext Result.Failure("read_failed", e.message ?: "读取失败")
        }
        val actualId = ClaudeSessionLocator.sessionIdOf(head)
        if (!ClaudeSessionLocator.sessionIdMatches(expectedSessionId, actualId)) {
            return@withContext Result.Failure(
                "id_mismatch",
                "会话文件 sessionId=$actualId 与 herdr 上报的 $expectedSessionId 不一致",
            )
        }
        Result.Found(latest.path, "cwd→$slug，取最新且 sessionId 匹配")
    }

    private fun readHead(sftp: net.schmizz.sshj.sftp.SFTPClient, path: String): List<String> {
        val buf = ByteArray(8 * 1024)
        var read = 0
        sftp.open(path, EnumSet.of(OpenMode.READ)).use { f ->
            read = f.read(0, buf, 0, buf.size)
        }
        if (read <= 0) return emptyList()
        return String(buf, 0, read, Charsets.UTF_8).split('\n')
    }
}
