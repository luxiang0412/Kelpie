package herdrspike.trust

import java.io.File
import java.security.MessageDigest
import java.util.Base64

/**
 * 主机身份 = (host, port, ssh 算法)。算法名从 SSH wire-format key blob 的第一个
 * length-prefixed string 解析，而不是从 java.security.PublicKey.getAlgorithm() 猜。
 */
data class HostKeyId(val host: String, val port: Int, val algorithm: String) {
    override fun toString() = "$host:$port:$algorithm"
}

class HostKeyUnknownException(val id: HostKeyId, val fingerprint: String) :
    java.io.IOException("unknown host key $id $fingerprint")

class HostKeyMismatchException(val id: HostKeyId, val pinned: String, val presented: String) :
    java.io.IOException("host key mismatch $id pinned=$pinned presented=$presented")

object SshKeyBlob {
    /** SSH wire format: uint32 length + ASCII name. */
    fun algorithmName(encoded: ByteArray): String {
        require(encoded.size >= 4) { "key blob too short" }
        val len = ((encoded[0].toInt() and 0xff) shl 24) or
            ((encoded[1].toInt() and 0xff) shl 16) or
            ((encoded[2].toInt() and 0xff) shl 8) or
            (encoded[3].toInt() and 0xff)
        require(len in 1..(encoded.size - 4)) { "bad key blob length $len" }
        return String(encoded, 4, len, Charsets.US_ASCII)
    }

    /** Test helper: build a wire-format blob. */
    fun build(name: String, body: ByteArray = ByteArray(32) { 7 }): ByteArray {
        val n = name.toByteArray(Charsets.US_ASCII)
        val out = ByteArray(4 + n.size + body.size)
        out[0] = (n.size ushr 24).toByte(); out[1] = (n.size ushr 16).toByte()
        out[2] = (n.size ushr 8).toByte(); out[3] = n.size.toByte()
        n.copyInto(out, 4); body.copyInto(out, 4 + n.size)
        return out
    }
}

object Fingerprint {
    /** OpenSSH 风格：SHA256:<base64 无 padding>。 */
    fun sha256(encoded: ByteArray): String =
        "SHA256:" + Base64.getEncoder().withoutPadding()
            .encodeToString(MessageDigest.getInstance("SHA-256").digest(encoded))
}

/**
 * 显式信任库。只有用户在带外核对指纹后显式确认，才允许 pin。
 * 首次使用不写入任何东西（禁止 TOFU）。
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
    fun algorithmsFor(host: String, port: Int): List<String> =
        entries.keys.filter { it.startsWith("$host:$port:") }.map { it.substringAfterLast(':') }

    fun pin(id: HostKeyId, fingerprint: String) {
        entries[id.toString()] = fingerprint
        persist()
    }

    fun unpin(id: HostKeyId) {
        entries.remove(id.toString())
        persist()
    }

    fun size() = entries.size

    private fun persist() {
        file.parentFile?.mkdirs()
        val tmp = File(file.parentFile, file.name + ".tmp")
        tmp.writeText(entries.entries.joinToString("") { "${it.key} ${it.value}\n" })
        if (!tmp.renameTo(file)) {
            file.delete()
            tmp.renameTo(file)
        }
    }
}

/**
 * 校验逻辑（纯逻辑，可单测）。返回 true 表示可以继续认证，抛异常表示必须中止。
 */
class StrictHostKeyTrust(private val store: TrustStore) {

    fun verify(
        id: HostKeyId,
        encodedKey: ByteArray,
        onUnknown: (HostKeyUnknownException) -> Boolean,
    ): Boolean {
        val presented = Fingerprint.sha256(encodedKey)
        val pinned = store.get(id)
        if (pinned == null) {
            val ex = HostKeyUnknownException(id, presented)
            // 默认拒绝：回调没被显式返回 true，就抛异常中止。
            if (!onUnknown(ex)) throw ex
            store.pin(id, presented)
            return true
        }
        if (pinned == presented) return true
        throw HostKeyMismatchException(id, pinned, presented)
    }
}
