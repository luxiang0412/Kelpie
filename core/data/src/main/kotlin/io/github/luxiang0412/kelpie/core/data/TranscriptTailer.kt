package io.github.luxiang0412.kelpie.core.data

import io.github.luxiang0412.kelpie.core.herdr.JsonlChunker
import io.github.luxiang0412.kelpie.core.ssh.SshConnection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.schmizz.sshj.sftp.OpenMode
import net.schmizz.sshj.sftp.SFTPClient
import java.io.Closeable
import java.security.MessageDigest
import java.util.EnumSet

/**
 * transcript 的增量读取（M3，D25 三条硬约束的简化实现）。
 *
 * - **offset 续读**：cursor 里的 offset 是“已消费到的字节位置”，只在换行处推进（`JsonlChunker`）；
 * - **文件身份**：每次 sync 取文件头 64 字节的 hash；变化 → 视为被替换，清空重读；
 * - **截断**：size < offset → 清空重读；
 * - **句柄复用**：SFTP client 在 tailer 生命周期内复用，close 时释放；
 * - **有界读取**：单次最多读 [maxDeltaBytes]，避免长会话一次性拉爆。
 */
class TranscriptTailer(
    private val conn: SshConnection,
    private val store: TranscriptStore,
    private val hostId: String,
    private val path: String,
    private val backfillBytes: Int = DEFAULT_BACKFILL,
    private val maxDeltaBytes: Int = 2 * 1024 * 1024,
) : Closeable {

    private var sftp: SFTPClient? = null

    /** 首次回填是否从文件中间开始（更早的消息没有加载）—— 用于 UI 如实提示。 */
    var backfillTruncated: Boolean = false
        private set

    /** 返回本次新增的消息条数；异常向上抛（调用方决定是否显示错误）。 */
    suspend fun sync(): Int = withContext(Dispatchers.IO) {
        val client = sftp ?: conn.sharedSftp().also { sftp = it }
        val size = client.stat(path).size
        val cursor = store.cursor(hostId, path)
        var start = cursor?.offset ?: 0L
        val headHash = readHeadHash(client, size)
        var reset = false

        if (cursor == null) {
            // 首次：只回填尾部，避免把整个大文件拉下来
            start = initialStart(size, backfillBytes)
            // 从中间起读时必须跳到下一行行首，避免把半行 JSON 当数据
            if (start > 0) start = skipToLineStart(client, start, size)
            backfillTruncated = start > 0
            reset = true
        } else {
            if (size < start) reset = true
            if (!reset && cursor.headHash != null && headHash != null && cursor.headHash != headHash) {
                reset = true
            }
        }
        if (reset) {
            store.reset(hostId, path)
            if (cursor == null) {
                // 首次回填时 start 已定位到尾部
            } else {
                start = 0L
            }
        }
        if (size <= start) {
            store.saveCursor(hostId, path, start.coerceAtMost(size), headHash)
            return@withContext 0
        }

        val want = minOf(size - start, maxDeltaBytes.toLong()).toInt()
        val buf = ByteArray(want)
        var read = 0
        client.open(path, EnumSet.of(OpenMode.READ)).use { f ->
            while (read < want) {
                val n = f.read(start + read, buf, read, want - read)
                if (n <= 0) break
                read += n
            }
        }
        if (read <= 0) return@withContext 0

        val chunk = JsonlChunker.consume(start, buf, 0, read)
        val messages = io.github.luxiang0412.kelpie.core.herdr.TranscriptParser.parse(path, chunk.lines)
        val added = store.append(hostId, path, messages)
        store.saveCursor(hostId, path, chunk.nextOffset, headHash)
        added
    }

    companion object {
        /** 首次回填的起始偏移：默认只看尾部 [backfillBytes]，文件更小则从头读。 */
        fun initialStart(size: Long, backfillBytes: Int = DEFAULT_BACKFILL): Long =
            if (size <= backfillBytes) 0L else size - backfillBytes

        const val DEFAULT_BACKFILL: Int = 1024 * 1024
    }

    /** 从 [offset] 起找到下一个 '
' 之后的位置（最多探 8KB）。 */
    private fun skipToLineStart(client: SFTPClient, offset: Long, size: Long): Long {
        val probe = minOf(8L * 1024, size - offset).toInt()
        if (probe <= 0) return size
        val b = ByteArray(probe)
        var read = 0
        client.open(path, EnumSet.of(OpenMode.READ)).use { f ->
            while (read < probe) {
                val n = f.read(offset + read, b, read, probe - read)
                if (n <= 0) break
                read += n
            }
        }
        for (i in 0 until read) {
            if (b[i] == 0x0A.toByte()) return offset + i + 1
        }
        return size
    }

    private fun readHeadHash(client: SFTPClient, size: Long): String? {
        if (size <= 0) return null
        val n = minOf(64L, size).toInt()
        val b = ByteArray(n)
        var read = 0
        client.open(path, EnumSet.of(OpenMode.READ)).use { f ->
            while (read < n) {
                val got = f.read(read.toLong(), b, read, n - read)
                if (got <= 0) break
                read += got
            }
        }
        if (read <= 0) return null
        return MessageDigest.getInstance("SHA-256")
            .digest(if (read == n) b else b.copyOf(read))
            .joinToString("") { "%02x".format(it) }
    }

    /**
     * 只释放引用，**不关闭 SFTP 通道**：通道由 [SshConnection] 持有。
     * （实测关闭它会把整条 SSH 连接判死，导致每次退出对话都重连一次。）
     */
    override fun close() {
        sftp = null
    }
}
