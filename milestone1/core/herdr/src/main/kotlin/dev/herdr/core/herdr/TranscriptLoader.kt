package dev.herdr.core.herdr

import dev.herdr.core.ssh.SshConnection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.schmizz.sshj.sftp.OpenMode
import java.util.EnumSet

/**
 * 只读 transcript 加载（M2-2B）。
 *
 * 设计取舍（M2 先做“最近 N KB 快照”，不做增量 tail）：
 * - 一次 SFTP stat + 一次/多次 read，取文件末尾 [maxBytes]；
 * - 从中间开始读时丢掉第一条不完整的行；
 * - 只解析 pi 的 JSONL（D19/D35）；其他格式原样返回为 SYSTEM 提示；
 * - 不做缓存/持久化，退出即释放（M3 再引入增量 tailer + Room）。
 */
class TranscriptLoader {

    suspend fun load(
        conn: SshConnection,
        path: String,
        maxBytes: Int = 256 * 1024,
        maxMessages: Int = 80,
    ): List<ChatMessage> = withContext(Dispatchers.IO) {
        val sftp = conn.openSftp()
        try {
            val size = sftp.stat(path).size
            if (size <= 0) return@withContext emptyList()
            val start = maxOf(0L, size - maxBytes)
            val buf = ByteArray((size - start).toInt())
            sftp.open(path, EnumSet.of(OpenMode.READ)).use { f ->
                var read = 0
                while (read < buf.size) {
                    val n = f.read(start + read, buf, read, buf.size - read)
                    if (n <= 0) break
                    read += n
                }
                if (read < buf.size) {
                    // 文件在变长时可能短读；用实际读到的长度
                    val text = String(buf, 0, read, Charsets.UTF_8)
                    return@withContext parse(text, dropFirstLine = start > 0, maxMessages = maxMessages)
                }
            }
            parse(String(buf, Charsets.UTF_8), dropFirstLine = start > 0, maxMessages = maxMessages)
        } finally {
            runCatching { sftp.close() }
        }
    }

    private fun parse(text: String, dropFirstLine: Boolean, maxMessages: Int): List<ChatMessage> {
        var lines = text.split('\n')
        if (dropFirstLine && lines.isNotEmpty()) lines = lines.drop(1)
        val messages = PiTranscriptParser.parse(lines.filter { it.isNotBlank() })
        return if (messages.size <= maxMessages) messages else messages.takeLast(maxMessages)
    }
}
