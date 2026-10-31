package herdrspike.tail

/** 文件 stat 结果。缺失 / 会话断时返回 null。 */
data class FileStat(val size: Long, val mtimeMs: Long, val inode: Long?)

/**
 * 文件身份：用于区分“同一文件继续变长”和“文件被替换”。
 *
 * 关键点：**不能**用“前 min(N, size) 字节的哈希”当身份——文件变大时这个哈希会变，
 * 于是正常追加会被误判成文件被替换（这是 v2 示例代码的缺陷之一）。
 * 正确做法是 inode/device（可得时），否则用“前缀保持不变”判断。
 */
class FileIdentity(
    val size: Long,
    val mtimeMs: Long,
    val inode: Long?,
    val headPrefix: ByteArray,
) {
    override fun toString() = "size=$size mtime=$mtimeMs inode=$inode head=${headPrefix.size}B"
}

interface ByteSource {
    fun stat(path: String): FileStat?
    fun readAt(path: String, offset: Long, buf: ByteArray, off: Int, len: Int): Int
    /** 读取文件开头最多 maxBytes 字节；不足时返回实际长度。 */
    fun headBytes(path: String, maxBytes: Int): ByteArray
    /** 身份变化时调用，用于丢弃缓存的句柄/状态。默认无操作。 */
    fun invalidate(path: String) {}
}

data class TailConfig(
    val chunkBytes: Int = 1 shl 20,           // 单次读上限 1 MiB
    val headBytes: Int = 512,                 // 身份指纹取前 512 字节
    val backfillMaxBytes: Long = 32L shl 20,  // 尾部回填上限 32 MiB
    val maxLineBytes: Int = 1 shl 20,         // 单行上限 1 MiB
    /**
     * 稳态下每隔多少次 poll 才重新读一次文件前缀（防“被更大文件替换”漏判）。
     * 0 = 禁用周期复检（只在可疑时读）。
     */
    val prefixRecheckPolls: Int = 5,
)

sealed interface TailResult {
    data object Empty : TailResult
    data object SessionLost : TailResult
    data class Reset(val reason: String) : TailResult
    data class Lines(val lines: List<String>, val invalidBytes: Long = 0) : TailResult
}

data class TailState(
    val offset: Long,
    val identity: FileIdentity?,
    val lastLineStart: Long,
    val lastLineEnd: Long,
    val lastLineSha: String?,
)

/**
 * 遵守调研 v2“SFTP Tailer 契约”的实现：
 * 字节级缓冲 + 增量解码、文件身份、截断/替换 Reset、偏移恢复、尾部回填、短读处理。
 */
class SftpTail(
    private val fs: ByteSource,
    private val path: String,
    private val cfg: TailConfig = TailConfig(),
) {
    var offset: Long = 0
        private set
    private var identity: FileIdentity? = null
    private var cachedPrefix: ByteArray? = null
    private var pollsSincePrefixCheck = 0
    /** 已读取内容的最后 64 字节，用于“续读重叠校验”（检测文件被替换，0 额外往返）。 */
    private val tailProbe = ByteArray(64)
    private var tailProbeLen = 0
    private val pending = java.io.ByteArrayOutputStream()
    private var invalidBytes: Long = 0
    private var lastLineStart: Long = 0
    private var lastLineEnd: Long = 0
    private var lastLineSha: String? = null
    private var lastEmittedEnd: Long = 0

    fun saveState(): TailState = TailState(offset, identity, lastLineStart, lastLineEnd, lastLineSha)

    /** 恢复偏移；调用方随后必须调用 verifyResume()。 */
    fun restoreState(s: TailState) {
        offset = s.offset
        identity = s.identity
        lastLineStart = s.lastLineStart
        lastLineEnd = s.lastLineEnd
        lastLineSha = s.lastLineSha
        lastEmittedEnd = s.lastLineEnd
        pending.reset()
    }

    /** 校验恢复点：重读 offset 之前的那一个完整行，哈希必须一致。 */
    fun verifyResume(): Boolean {
        val want = lastLineSha ?: return true
        val start = lastLineStart
        val end = lastLineEnd
        if (start < 0 || end <= start || end > offset) return false
        val len = (end - start).toInt()
        val buf = ByteArray(len)
        var read = 0
        while (read < len) {
            val n = fs.readAt(path, start + read, buf, read, len - read)
            if (n <= 0) return false
            read += n
        }
        return sha256(buf) == want
    }

    /**
     * 一次 poll 的 SFTP 往返：
     *   稳态 = stat(1) + read(1)（句柄由 ByteSource 缓存）
     *   可疑（变短 / mtime 倒退 / 周期性复检）= 额外 1 次 headBytes
     */
    fun poll(): TailResult {
        val st = fs.stat(path) ?: return TailResult.SessionLost
        val prev = identity

        // 变短直接判截断（先于身份检查，避免原因字符串误导）
        if (st.size < offset) {
            // 截断仍是同一个文件：保留句柄，只重置偏移
            reset("truncated size=${st.size} offset=$offset", dropHandle = false)
            return TailResult.Reset("truncated")
        }

        val suspicious = prev != null && (
            st.size < prev.size ||
                st.mtimeMs < prev.mtimeMs
            )
        pollsSincePrefixCheck++
        val dueForRecheck = cfg.prefixRecheckPolls > 0 && pollsSincePrefixCheck >= cfg.prefixRecheckPolls

        if (prev != null && (suspicious || dueForRecheck)) {
            pollsSincePrefixCheck = 0
            val prefix = cachedPrefix ?: fs.headBytes(path, cfg.headBytes).also { cachedPrefix = it }
            val same = isSameFile(prev, st, prefix)
            if (!same) {
                reset("identity-changed old=$prev new=size=${st.size},mtime=${st.mtimeMs}")
                return TailResult.Reset("identity-changed")
            }
        }

        identity = FileIdentity(st.size, st.mtimeMs, st.inode, cachedPrefix ?: ByteArray(0))
            .also { if (cachedPrefix == null) cachedPrefix = fs.headBytes(path, cfg.headBytes) }

        if (st.size == offset) return TailResult.Empty

        val lines = ArrayList<String>()
        val overlap = minOf(tailProbeLen, offset.toInt())
        var pos = offset - overlap
        var toDiscard = overlap
        while (pos < st.size) {
            val want = minOf(cfg.chunkBytes.toLong(), st.size - pos).toInt()
            val buf = ByteArray(want)
            var read = 0
            while (read < want) {                       // 短读必须循环
                val n = fs.readAt(path, pos + read, buf, read, want - read)
                if (n <= 0) break
                read += n
            }
            if (read == 0) break
            var off = 0
            if (toDiscard > 0) {
                // 续读重叠校验：新内容必须与上次读到的末尾一致，否则说明文件被替换
                val n = minOf(toDiscard, read)
                for (i in 0 until n) {
                    if (buf[i] != tailProbe[tailProbeLen - overlap + i]) {
                        reset("continuity-mismatch at offset=${pos + i}")
                        return TailResult.Reset("identity-changed")
                    }
                }
                toDiscard -= n
                off = n
            }
            if (read - off > 0) {
                pending.write(buf, off, read - off)
                rememberTail(buf, off, read - off)
            }
            pos += read
            offset = pos
            drainLines(lines)
        }
        val bad = invalidBytes
        invalidBytes = 0
        return if (lines.isEmpty() && bad == 0L) TailResult.Empty else TailResult.Lines(lines, bad)
    }

    /**
     * 从文件尾部向前按块回填最多 maxLines 行，读取字节数不超过 backfillMaxBytes。
     */
    fun backfillLastLines(maxLines: Int, maxBytes: Long = cfg.backfillMaxBytes): List<String> {
        val st = fs.stat(path) ?: return emptyList()
        var end = st.size
        var budget = maxBytes
        val chunks = ArrayDeque<ByteArray>()
        var newlines = 0
        while (end > 0 && newlines <= maxLines && budget > 0) {
            val want = minOf(cfg.chunkBytes.toLong(), end, budget).toInt()
            val start = end - want
            val buf = ByteArray(want)
            var read = 0
            while (read < want) {
                val n = fs.readAt(path, start + read, buf, read, want - read)
                if (n <= 0) break
                read += n
            }
            if (read == 0) break
            chunks.addFirst(buf.copyOf(read))
            newlines += buf.take(read).count { it == '\n'.code.toByte() }
            end = start
            budget -= read
        }
        val all = chunks.fold(ByteArray(0)) { acc, c -> acc + c }
        // 只有文件不是从 0 开始时，首行可能是半行，丢弃。
        val text = String(all, Charsets.UTF_8)
        var lines = text.split('\n').filter { it.isNotBlank() }
        if (end > 0 && lines.isNotEmpty()) lines = lines.drop(1)
        return lines.takeLast(maxLines)
    }

    private fun rememberTail(buf: ByteArray, off: Int, len: Int) {
        if (len >= tailProbe.size) {
            buf.copyInto(tailProbe, 0, off + len - tailProbe.size, off + len)
            tailProbeLen = tailProbe.size
        } else {
            val keep = minOf(tailProbeLen, tailProbe.size - len)
            tailProbe.copyInto(tailProbe, 0, tailProbeLen - keep, tailProbeLen)
            buf.copyInto(tailProbe, keep, off, off + len)
            tailProbeLen = keep + len
        }
    }

    private fun drainLines(out: MutableList<String>) {
        val bytes = pending.toByteArray()
        var start = 0
        for (i in bytes.indices) {
            if (bytes[i] == '\n'.code.toByte()) {
                val raw = bytes.copyOfRange(start, i)                       // 不含 \n
                val decoded = decodeLenient(raw)
                val line = decoded.trimEnd('\r')
                if (line.isNotBlank() && line.toByteArray(Charsets.UTF_8).size <= cfg.maxLineBytes) out += line
                lastLineStart = lastEmittedEnd
                lastLineEnd = lastEmittedEnd + (i + 1 - start)
                lastLineSha = sha256(bytes.copyOfRange(start, i + 1))       // 含 \n，和 verifyResume 一致
                lastEmittedEnd = lastLineEnd
                start = i + 1
            }
        }
        pending.reset()
        if (start < bytes.size) pending.write(bytes, start, bytes.size - start)
    }

    private fun decodeLenient(raw: ByteArray): String {
        val dec = Charsets.UTF_8.newDecoder()
            .onMalformedInput(java.nio.charset.CodingErrorAction.REPLACE)
            .onUnmappableCharacter(java.nio.charset.CodingErrorAction.REPLACE)
        val cb = dec.decode(java.nio.ByteBuffer.wrap(raw))
        val s = cb.toString()
        if (s.contains('\uFFFD')) invalidBytes += s.count { it == '\uFFFD' }.toLong()
        return s
    }

    /**
     * 同一文件的判定（只在可疑或周期复检时调用）：
     * 1) 两边都有 inode → 比 inode；
     * 2) 否则要求“旧前缀保持不变且文件没有变短”。
     * 局限：替换后的新文件若前 N 字节完全相同且 inode 不可得，就分辨不出来（SFTP 无 inode）。
     */
    private fun isSameFile(prev: FileIdentity, st: FileStat, prevPrefix: ByteArray): Boolean {
        if (prev.inode != null && st.inode != null) return prev.inode == st.inode
        if (st.size < prev.size) return false
        val need = prevPrefix.size
        if (need == 0) return true
        val cur = fs.headBytes(path, need)
        return cur.size >= need && cur.copyOf(need).contentEquals(prevPrefix)
    }

    private fun reset(reason: String, dropHandle: Boolean = true) {
        offset = 0
        pending.reset()
        identity = null
        cachedPrefix = null
        pollsSincePrefixCheck = 0
        tailProbeLen = 0
        if (dropHandle) fs.invalidate(path)
        lastLineStart = 0
        lastLineEnd = 0
        lastLineSha = null
        lastEmittedEnd = 0
        audit?.invoke(reason)
    }

    var audit: ((String) -> Unit)? = null

    private fun sha256(b: ByteArray): String =
        java.security.MessageDigest.getInstance("SHA-256").digest(b).joinToString("") { "%02x".format(it) }
}
