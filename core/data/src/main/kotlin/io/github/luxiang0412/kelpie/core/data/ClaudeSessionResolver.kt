package io.github.luxiang0412.kelpie.core.data

import io.github.luxiang0412.kelpie.core.herdr.t

import io.github.luxiang0412.kelpie.core.herdr.ClaudeSessionLocator
import io.github.luxiang0412.kelpie.core.ssh.SshConnection
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
        if (cwd.isNullOrBlank()) return@withContext Result.Failure("no_cwd", t("pane 没有上报 cwd", "The pane didn't report a cwd"))
        if (expectedSessionId.isNullOrBlank()) {
            return@withContext Result.Failure("no_session_id", t("herdr 未提供会话 id，无法对账", "herdr didn't provide a session id"))
        }
        val slug = ClaudeSessionLocator.slugFor(cwd)
        val sftp = try {
            conn.sharedSftp()
        } catch (e: Throwable) {
            return@withContext Result.Failure("no_sftp", e.message ?: t("SFTP 不可用", "SFTP unavailable"))
        }
        // home 目录：优先用调用方给的，否则用 SFTP canonicalize(".") 取登录用户 home
        val home = homeDir.trim().ifEmpty {
            runCatching { sftp.canonicalize(".") }.getOrNull().orEmpty()
        }.trimEnd('/')
        if (home.isEmpty()) return@withContext Result.Failure("no_home", t("无法确定远端 home 目录", "Couldn't determine the remote home directory"))
        val dir = "$home/.claude/projects/$slug"
        // Claude 的会话文件以 session id 命名：先直接按 id 找。
        // 同一目录下跑着多个 Claude 时，“取最新”必然会配错，按 id 才可靠。
        val direct = "$dir/$expectedSessionId.jsonl"
        val directHead = runCatching { readHead(sftp, direct) }.getOrNull()
        if (directHead != null && ClaudeSessionLocator.sessionIdMatches(expectedSessionId, ClaudeSessionLocator.sessionIdOf(directHead))) {
            return@withContext Result.Found(direct, t("cwd→$slug，按 session id 直接定位", "cwd→$slug, located by session id"))
        }
        val candidates = try {
            sftp.ls(dir)
                .filter { it.isRegularFile && it.name.endsWith(".jsonl") }
                .map { ClaudeSessionLocator.Candidate("$dir/${it.name}", it.attributes.mtime * 1000L) }
        } catch (e: Throwable) {
            return@withContext Result.Failure("dir_missing", t("找不到目录 $dir", "Directory not found: $dir"))
        }
        if (candidates.isEmpty()) return@withContext Result.Failure("no_file", t("$dir 下没有 jsonl", "No jsonl files in $dir"))

        val latest = ClaudeSessionLocator.pickLatest(candidates, nowMs, windowMs)
            ?: return@withContext Result.Failure(
                "stale",
                t("最新的会话文件超出 ${windowMs / 3600_000}h 窗口，可能是旧会话", "Newest transcript is older than ${windowMs / 3600_000}h; may be a stale session"),
            )

        val head = try {
            readHead(sftp, latest.path)
        } catch (e: Throwable) {
            return@withContext Result.Failure("read_failed", e.message ?: t("读取失败", "Read failed"))
        }
        val actualId = ClaudeSessionLocator.sessionIdOf(head)
        if (!ClaudeSessionLocator.sessionIdMatches(expectedSessionId, actualId)) {
            return@withContext Result.Failure(
                "id_mismatch",
                t("会话文件 sessionId=$actualId 与 herdr 上报的 $expectedSessionId 不一致", "Transcript sessionId=$actualId doesn't match herdr's $expectedSessionId"),
            )
        }
        Result.Found(latest.path, t("cwd→$slug，取最新且 sessionId 匹配", "cwd→$slug, newest file with matching sessionId"))
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
