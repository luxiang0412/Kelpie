package io.github.luxiang0412.kelpie.core.ssh

import org.bouncycastle.crypto.params.Ed25519PrivateKeyParameters
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.security.SecureRandom
import java.util.Base64

/**
 * 从 ed25519 的 32 字节种子还原出标准的 OpenSSH 私钥文本（`openssh-key-v1`，不加密）。
 * 配对二维码只带种子（省掉约 370 字节，终端里才放得下），手机上还原后和普通私钥一样导入。
 */
object OpenSshKeys {
    fun ed25519PemFromSeed(seed: ByteArray, comment: String = "kelpie", random: SecureRandom = SecureRandom()): String {
        require(seed.size == 32) { "ed25519 seed must be 32 bytes" }
        val pub = Ed25519PrivateKeyParameters(seed, 0).generatePublicKey().encoded
        val keyType = "ssh-ed25519".toByteArray()
        val pubBlob = buf { str(keyType); str(pub) }
        val check = random.nextInt()
        val priv = buf {
            writeInt(check); writeInt(check)
            str(keyType); str(pub); str(seed + pub); str(comment.toByteArray())
            var pad = 1
            while (size() % 8 != 0) writeByte(pad++)
        }
        val blob = buf {
            write("openssh-key-v1".toByteArray()); writeByte(0)
            str("none".toByteArray()); str("none".toByteArray()); str(ByteArray(0))
            writeInt(1); str(pubBlob); str(priv)
        }
        val b64 = Base64.getEncoder().encodeToString(blob).chunked(70).joinToString("\n")
        return "-----BEGIN OPENSSH PRIVATE KEY-----\n$b64\n-----END OPENSSH PRIVATE KEY-----\n"
    }

    /** 公钥的 authorized_keys 形式（`ssh-ed25519 AAAA…`），用于核对和测试。 */
    fun ed25519PublicLine(seed: ByteArray): String {
        val pub = Ed25519PrivateKeyParameters(seed, 0).generatePublicKey().encoded
        return "ssh-ed25519 " + Base64.getEncoder().encodeToString(buf { str("ssh-ed25519".toByteArray()); str(pub) })
    }

    private fun buf(block: DataOutputStream.() -> Unit): ByteArray =
        ByteArrayOutputStream().also { DataOutputStream(it).apply(block).flush() }.toByteArray()

    private fun DataOutputStream.str(b: ByteArray) { writeInt(b.size); write(b) }
}
