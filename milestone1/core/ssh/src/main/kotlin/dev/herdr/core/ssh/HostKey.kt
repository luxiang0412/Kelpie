package dev.herdr.core.ssh

import java.io.File
import java.security.MessageDigest
import java.util.Base64

/** 主机身份：(host, port, ssh 算法)。算法从 SSH wire blob 的首个 length-prefixed string 解析。 */
data class HostKeyId(val host: String, val port: Int, val algorithm: String) {
    override fun toString() = "$host:$port:$algorithm"
}

class HostKeyUnknownException(val id: HostKeyId, val fingerprint: String) :
    java.io.IOException("unknown host key $id $fingerprint")

class HostKeyMismatchException(val id: HostKeyId, val pinned: String, val presented: String) :
    java.io.IOException("host key mismatch $id pinned=$pinned presented=$presented")

object SshKeyBlob {
    fun algorithmName(encoded: ByteArray): String {
        require(encoded.size >= 4) { "key blob too short" }
        val len = ((encoded[0].toInt() and 0xff) shl 24) or ((encoded[1].toInt() and 0xff) shl 16) or
            ((encoded[2].toInt() and 0xff) shl 8) or (encoded[3].toInt() and 0xff)
        require(len in 1..(encoded.size - 4)) { "bad key blob length $len" }
        return String(encoded, 4, len, Charsets.US_ASCII)
    }
}

/** OpenSSH 风格指纹：SHA256:<base64 无 padding>（与 `ssh-keygen -lf` 一致）。 */
object Fingerprint {
    fun sha256(encoded: ByteArray): String =
        "SHA256:" + Base64.getEncoder().withoutPadding()
            .encodeToString(MessageDigest.getInstance("SHA-256").digest(encoded))
}

/**
 * 文件型信任库。只在用户显式确认后 pin（禁止 TOFU）。
 * 行格式：`host:port:algorithm SHA256:...`
 */
class TrustStore(private val file: File) {
    private val entries = LinkedHashMap<String, String>()

    init {
        if (file.exists()) {
            file.readLines().forEach { line ->
                if (line.isNotBlank()) {
                    val i = line.lastIndexOf(' ')
                    if (i > 0) entries[line.substring(0, i)] = line.substring(i + 1)
                }
            }
        }
    }

    fun get(id: HostKeyId): String? = entries[id.toString()]

    fun pin(id: HostKeyId, fingerprint: String) {
        entries[id.toString()] = fingerprint
        persist()
    }

    fun unpin(id: HostKeyId) {
        entries.remove(id.toString())
        persist()
    }

    fun all(): Map<String, String> = LinkedHashMap(entries)

    /** 删除某台主机（所有算法）的固定指纹。 */
    fun unpinHost(host: String, port: Int): Int {
        val prefix = "$host:$port:"
        val removed = entries.keys.filter { it.startsWith(prefix) }
        removed.forEach { entries.remove(it) }
        if (removed.isNotEmpty()) persist()
        return removed.size
    }

    private fun persist() {
        file.parentFile?.mkdirs()
        val tmp = File(file.parentFile, file.name + ".tmp")
        tmp.writeText(entries.entries.joinToString("") { "${it.key} ${it.value}\n" })
        if (!tmp.renameTo(file)) {
            file.delete()
            tmp.renameTo(file)
        }
        runCatching { file.setReadable(false, false); file.setReadable(true, true) }
    }
}

/** 校验逻辑：未知 → 交给回调；不确认就抛异常；不匹配直接抛。 */
class StrictHostKeyTrust(private val store: TrustStore) {
    fun verify(id: HostKeyId, encodedKey: ByteArray, onUnknown: (HostKeyUnknownException) -> Boolean): Boolean {
        val presented = Fingerprint.sha256(encodedKey)
        val pinned = store.get(id)
        android.util.Log.i("HostKeyTrust", "verify $id presented=$presented pinned=$pinned")
        if (pinned == null) {
            val ex = HostKeyUnknownException(id, presented)
            val accepted = onUnknown(ex)
            android.util.Log.i("HostKeyTrust", "unknown host -> user accepted=$accepted")
            if (!accepted) throw ex
            store.pin(id, presented)
            return true
        }
        if (pinned == presented) return true
        throw HostKeyMismatchException(id, pinned, presented)
    }
}
