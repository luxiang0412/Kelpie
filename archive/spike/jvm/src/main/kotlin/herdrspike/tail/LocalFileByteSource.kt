package herdrspike.tail

import java.io.RandomAccessFile
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.BasicFileAttributes

/**
 * 本地文件实现，只用于单元测试和离线基准。
 * maxReadPerCall 用来模拟 SFTP 短读。
 */
class LocalFileByteSource(
    private val maxReadPerCall: Int = Int.MAX_VALUE,
) : ByteSource {

    var bytesRead: Long = 0
        private set

    override fun stat(path: String): FileStat? {
        val p = Path.of(path)
        if (!Files.exists(p)) return null
        val attrs = Files.readAttributes(p, BasicFileAttributes::class.java)
        return FileStat(
            size = attrs.size(),
            mtimeMs = attrs.lastModifiedTime().toMillis(),
            inode = inodeOf(p),
        )
    }

    override fun readAt(path: String, offset: Long, buf: ByteArray, off: Int, len: Int): Int {
        RandomAccessFile(path, "r").use { f ->
            f.seek(offset)
            val want = minOf(len.toLong(), maxReadPerCall.toLong()).toInt()
            val n = f.read(buf, off, want)
            if (n > 0) bytesRead += n
            return n
        }
    }

    override fun headBytes(path: String, maxBytes: Int): ByteArray {
        RandomAccessFile(path, "r").use { f ->
            val n = minOf(f.length(), maxBytes.toLong()).toInt()
            val buf = ByteArray(n)
            f.readFully(buf)
            return buf
        }
    }

    private fun inodeOf(p: Path): Long? = null // Windows 上 fileKey 不稳定，留 null 走 head 指纹
}

fun writeFile(path: Path, content: ByteArray, append: Boolean = false) {
    Files.createDirectories(path.parent)
    if (append) Files.write(path, content, java.nio.file.StandardOpenOption.APPEND)
    else Files.write(path, content, java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.TRUNCATE_EXISTING)
}
