package dev.herdr.core.data

import dev.herdr.core.herdr.CodexSessionLocator
import dev.herdr.core.herdr.t
import dev.herdr.core.ssh.SshConnection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.schmizz.sshj.sftp.OpenMode
import net.schmizz.sshj.sftp.SFTPClient
import java.util.EnumSet

/**
 * 按 cwd 在 `~/.codex/sessions` 里找 Codex 会话文件（规则见 [CodexSessionLocator]）。
 * **只读**：列最近两天的日期目录，读候选文件开头 8KB。
 */
class CodexSessionResolver {

    var windowMs: Long = 24 * 60 * 60 * 1000L

    suspend fun resolve(
        conn: SshConnection,
        cwd: String?,
        codexPanesWithSameCwd: Int,
        nowMs: Long = System.currentTimeMillis(),
    ): ClaudeSessionResolver.Result = withContext(Dispatchers.IO) {
        fun fail(code: String, zh: String, en: String) = ClaudeSessionResolver.Result.Failure(code, t(zh, en))
        if (cwd.isNullOrBlank()) return@withContext fail("no_cwd", "pane 没有上报 cwd", "The pane didn't report a cwd")
        if (codexPanesWithSameCwd > 1) {
            return@withContext fail("ambiguous", "同一目录下有 $codexPanesWithSameCwd 个 Codex，分不清是哪个会话", "$codexPanesWithSameCwd Codex panes share this directory; can't tell which transcript is this one")
        }
        val sftp = runCatching { conn.sharedSftp() }.getOrElse { return@withContext fail("no_sftp", it.message ?: "SFTP 不可用", it.message ?: "SFTP unavailable") }
        val home = runCatching { sftp.canonicalize(".") }.getOrNull().orEmpty().trimEnd('/')
        if (home.isEmpty()) return@withContext fail("no_home", "无法确定远端 home 目录", "Couldn't determine the remote home directory")
        val root = "$home/.codex/sessions"
        fun names(dir: String) = runCatching { sftp.ls(dir).filter { it.isDirectory }.map { it.name } }.getOrDefault(emptyList())
        val days = CodexSessionLocator.recentDayDirs(names(root), { y -> names("$root/$y") }, { y, m -> names("$root/$y/$m") })
        if (days.isEmpty()) return@withContext fail("dir_missing", "找不到 $root", "Not found: $root")

        val candidates = days.flatMap { d ->
            runCatching { sftp.ls("$root/$d") }.getOrDefault(emptyList())
                .filter { it.isRegularFile && it.name.startsWith("rollout-") && it.name.endsWith(".jsonl") }
                .map { "$root/$d/${it.name}" to it.attributes.mtime * 1000L }
        }
            .filter { (_, mt) -> mt >= nowMs - windowMs }
            .sortedByDescending { it.second }
            .take(40) // 只读最新的 40 个文件头
            .mapNotNull { (path, mt) ->
                val meta = runCatching { CodexSessionLocator.metaOf(readHead(sftp, path)) }.getOrNull() ?: return@mapNotNull null
                CodexSessionLocator.Candidate(path, mt, meta)
            }
        val hit = CodexSessionLocator.pick(candidates, cwd, nowMs, windowMs)
            ?: return@withContext fail("no_match", "最近一天里没有 $cwd 下的交互式 Codex 会话", "No interactive Codex session in $cwd during the last day")
        ClaudeSessionResolver.Result.Found(hit.path, t("按 cwd 找到最新的交互式会话", "Newest interactive session for this cwd"))
    }

    private fun readHead(sftp: SFTPClient, path: String): String {
        val buf = ByteArray(8 * 1024)
        val n = sftp.open(path, EnumSet.of(OpenMode.READ)).use { it.read(0, buf, 0, buf.size) }
        return if (n <= 0) "" else String(buf, 0, n, Charsets.UTF_8)
    }
}
