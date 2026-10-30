package herdrspike.trust

import net.schmizz.sshj.common.Buffer
import net.schmizz.sshj.common.KeyType
import net.schmizz.sshj.transport.verification.HostKeyVerifier
import java.security.PublicKey

/**
 * sshj 适配层。**故意**不做 TOFU：未知主机抛 HostKeyUnknownException。
 *
 * 两个易踩的坑（实测踩到了）：
 * 1. `java.security.PublicKey.getEncoded()` 对 sshj 的 Ed25519 key 返回的是 **X.509**，
 *    不是 SSH wire blob；直接按 SSH wire 解析会得到荒谬的长度（本项目实测得到 808071173）。
 * 2. `SecurityUtils.getFingerprint()` 是 **MD5**，和 `ssh-keygen -lf` 的 SHA256 不是一回事。
 *
 * 正确做法：用 `Buffer.PlainBuffer().putPublicKey(key).compactData` 拿到 SSH wire blob，
 * 再对它做 SHA256 + Base64（无 padding），这样才和 OpenSSH 的指纹一致。
 */
class SshjHostKeyVerifier(
    private val trust: StrictHostKeyTrust,
    private val onUnknown: (HostKeyUnknownException) -> Boolean,
    private val allowedAlgorithms: List<String> = listOf(
        "ssh-ed25519",
        "ecdsa-sha2-nistp256",
        "rsa-sha2-512",
        "rsa-sha2-256",
    ),
) : HostKeyVerifier {

    override fun verify(host: String, port: Int, key: PublicKey): Boolean {
        val blob = sshWireBlob(key)
        val algorithm = algorithmOf(key, blob)
        return trust.verify(HostKeyId(host, port, algorithm), blob, onUnknown)
    }

    override fun findExistingAlgorithms(host: String?, port: Int): List<String> = allowedAlgorithms

    companion object {
        /** SSH wire-format 公钥 blob，与 `ssh-keygen -lf` 输入一致。 */
        fun sshWireBlob(key: PublicKey): ByteArray =
            try {
                Buffer.PlainBuffer().putPublicKey(key).compactData
            } catch (e: Exception) {
                // 兜底：测试用的假 key 无法写进 SSH buffer，退回 encoded。
                key.encoded ?: throw e
            }

        fun algorithmOf(key: PublicKey, blob: ByteArray): String =
            runCatching { SshKeyBlob.algorithmName(blob) }
                .getOrElse { runCatching { KeyType.fromKey(key).toString() }.getOrDefault("unknown") }
    }
}
