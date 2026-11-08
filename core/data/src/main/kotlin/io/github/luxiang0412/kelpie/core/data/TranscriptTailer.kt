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
 * - **有界读取**：单次最多读 [maxDeltaBytes]，避免长会话一次性拉爆；
 * - **落后太多就跳到尾部**：游标离文件末尾超过 [catchUpBytes]（很久没打开 / 文件被替换），
 *   不再从旧位置每次 2MB 慢慢追（700MB 的会话要追几十分钟），而是清空、按首次打开只回填尾部；
 * - **超长行**：窗口里一个换行都没有时，把这一行完整读进来（≤ [maxLineBytes]），更长的跳过。
 */
class TranscriptTailer(
    private val conn: SshConnection,
    private val store: TranscriptStore,
    private val hostId: String,
    private val path: String,
    private val backfillBytes: Int = DEFAULT_BACKFILL,
    private val maxDeltaBytes: Int = 2 * 1024 * 1024,
    private val catchUpBytes: Long = DEFAULT_CATCH_UP,
    private val maxLineBytes: Int = DEFAULT_MAX_LINE,
) : Closeable {

    private var sftp: SFTPClient? = null

    /** 本地缓存是否从文件中间开始回填（更早的消息没有加载）—— 用于 UI 如实提示；随游标持久化。 */
    var backfillTruncated: Boolean = false
        private set

    /** 返回本次新增的消息条数；异常向上抛（调用方决定是否显示错误）。 */
    suspend fun sync(): Int = withContext(Dispatchers.IO) {
        val client = sftp ?: conn.sharedSftp().also { sftp = it }
        val size = client.stat(path).size
        val cursor = store.cursor(hostId, path)
        val headHash = readHeadHash(client, size)
        val plan = plan(cursor?.offset, cursor?.headHash, headHash, size, backfillBytes, catchUpBytes)
        var start = plan.start

        if (plan.reset) {
            // 从中间起读时必须跳到下一行行首，避免把半行 JSON 当数据
            if (start > 0) start = skipToLineStart(client, start, size)
            store.reset(hostId, path)
        }
        val truncated = if (plan.reset) start > 0 else cursor?.truncated == true
        backfillTruncated = truncated
        if (size <= start) {
            store.saveCursor(hostId, path, start.coerceAtMost(size), headHash, truncated)
            return@withContext 0
        }

        val want = minOf(size - start, maxDeltaBytes.toLong()).toInt()
        var buf = ByteArray(want)
        var read = readFully(client, start, buf)
        if (read <= 0) return@withContext 0

        var chunk = JsonlChunker.consume(start, buf, 0, read)
        if (chunk.lines.isEmpty() && read == maxDeltaBytes) {
            // 整个窗口里没有换行：一行比 maxDeltaBytes 还长（大段工具输出）。
            // 以前 offset 原地不动，之后每次都读同一段，永远追不上。
            val end = client.open(path, EnumSet.of(OpenMode.READ)).use { f ->
                findLineEnd(start + read, size, read = { at, b -> readFully(f, at, b) })
            } ?: return@withContext 0 // 这一行还没写完，下次再来
            if (end - start <= maxLineBytes) {
                buf = ByteArray((end - start).toInt())
                read = readFully(client, start, buf)
                chunk = JsonlChunker.consume(start, buf, 0, read)
            } else {
                // 超过上限的单行直接跳过（不解析），游标越过它
                store.saveCursor(hostId, path, end, headHash, truncated)
                return@withContext 0
            }
        }
        val messages = io.github.luxiang0412.kelpie.core.herdr.TranscriptParser.parse(path, chunk.lines)
        val added = store.append(hostId, path, messages)
        store.saveCursor(hostId, path, chunk.nextOffset, headHash, truncated)
        added
    }

    /** 本次 sync 从哪里读。[reset] = 清空本地缓存后从 [start] 重新回填（[start] 可能落在行中间）。 */
    data class Plan(val start: Long, val reset: Boolean)

    companion object {
        /** 首次回填的起始偏移：默认只看尾部 [backfillBytes]，文件更小则从头读。 */
        fun initialStart(size: Long, backfillBytes: Int = DEFAULT_BACKFILL): Long =
            if (size <= backfillBytes) 0L else size - backfillBytes

        const val DEFAULT_BACKFILL: Int = 1024 * 1024
        const val DEFAULT_CATCH_UP: Long = 8L * 1024 * 1024
        const val DEFAULT_MAX_LINE: Int = 16 * 1024 * 1024

        fun plan(
            cursorOffset: Long?,
            cursorHead: String?,
            head: String?,
            size: Long,
            backfillBytes: Int = DEFAULT_BACKFILL,
            catchUpBytes: Long = DEFAULT_CATCH_UP,
        ): Plan {
            val fresh = Plan(initialStart(size, backfillBytes), reset = true)
            if (cursorOffset == null) return fresh
            if (size < cursorOffset) return fresh // 截断
            if (cursorHead != null && head != null && cursorHead != head) return fresh // 被替换
            if (size - cursorOffset > catchUpBytes) return fresh // 落后太多
            return Plan(cursorOffset, reset = false)
        }

        /**
         * 从 [offset] 起找下一个换行，返回换行之后的位置；直到 [size] 都没有换行（行还没写完）返回 null。
         * 不设上限；能区分“换行恰好是最后一个字节”和“没有换行”。
         */
        fun findLineEnd(offset: Long, size: Long, read: (Long, ByteArray) -> Int, step: Int = 64 * 1024): Long? {
            var pos = offset
            while (pos < size) {
                val buf = ByteArray(minOf(step.toLong(), size - pos).toInt())
                val n = read(pos, buf)
                if (n <= 0) return null
                for (i in 0 until n) if (buf[i] == 0x0A.toByte()) return pos + i + 1
                pos += n
            }
            return null
        }
    }

    private fun readFully(client: SFTPClient, at: Long, buf: ByteArray): Int =
        client.open(path, EnumSet.of(OpenMode.READ)).use { f -> readFully(f, at, buf) }

    private fun readFully(f: net.schmizz.sshj.sftp.RemoteFile, at: Long, buf: ByteArray): Int {
        var got = 0
        while (got < buf.size) {
            val n = f.read(at + got, buf, got, buf.size - got)
            if (n <= 0) break
            got += n
        }
        return got
    }

    /**
     * 回填起点 → 下一行行首。不设上限：Claude / Pi 会话里常有几百 KB 甚至几 MB 的一行，
     * 以前只探 8KB（后来 2MB），起点落在这样的行里就直接跳到文件末尾，回填一条消息都没有。
     */
    private fun skipToLineStart(client: SFTPClient, offset: Long, size: Long): Long =
        client.open(path, EnumSet.of(OpenMode.READ)).use { f ->
            findLineEnd(offset, size, read = { at, buf -> readFully(f, at, buf) }) ?: size
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
