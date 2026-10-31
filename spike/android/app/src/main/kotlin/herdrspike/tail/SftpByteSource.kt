package herdrspike.tail

import net.schmizz.sshj.sftp.FileAttributes
import net.schmizz.sshj.sftp.SFTPClient

/**
 * 把 sshj 的 SFTP 适配成 ByteSource。
 *
 * 注意：SFTP 的 stat 通常**不返回 inode**（sshj FileAttributes 也没有），
 * 所以文件身份只能靠“前缀不变 + 长度不变短”来判定（见 DECISIONS D6 / ISSUES I-15）。
 */
class SftpByteSource(private val sftp: SFTPClient) : ByteSource {

    /** 句柄缓存：避免每次 readAt 都 open+close（各占 1 个往返）。 */
    private val handles = HashMap<String, net.schmizz.sshj.sftp.RemoteFile>()

    /** 最近一次 stat 的原始属性，便于记录平台差异。 */
    var lastAttributes: FileAttributes? = null
        private set
    var lastError: Exception? = null
        private set

    override fun stat(path: String): FileStat? = try {
        val a = sftp.stat(path)
        lastAttributes = a
        FileStat(size = a.size, mtimeMs = a.mtime * 1000, inode = null)
    } catch (e: Exception) {
        lastError = e
        null
    }

    override fun readAt(path: String, offset: Long, buf: ByteArray, off: Int, len: Int): Int {
        val f = handles.getOrPut(path) {
            sftp.open(path, java.util.EnumSet.of(net.schmizz.sshj.sftp.OpenMode.READ))
        }
        return f.read(offset, buf, off, len)
    }

    override fun invalidate(path: String) {
        handles.remove(path)?.let { runCatching { it.close() } }
    }

    override fun headBytes(path: String, maxBytes: Int): ByteArray =
        sftp.open(path, java.util.EnumSet.of(net.schmizz.sshj.sftp.OpenMode.READ)).use { f ->
            val n = minOf(sftp.stat(path).size, maxBytes.toLong()).toInt()
            val b = ByteArray(n)
            var read = 0
            while (read < n) {
                val got = f.read(read.toLong(), b, read, n - read)
                if (got <= 0) break
                read += got
            }
            if (read == n) b else b.copyOf(read)
        }
}
