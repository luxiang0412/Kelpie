package herdrspike.trust

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.io.File
import java.nio.file.Files
import java.security.PublicKey
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class HostKeyTrustTest {

    private fun tmpStore(): TrustStore =
        TrustStore(Files.createTempDirectory("trust").resolve("known_hosts.spike").toFile())

    private fun ed25519(seed: Byte = 7) =
        SshKeyBlob.build("ssh-ed25519", ByteArray(32) { seed })

    @Test
    fun `F1 unknown host is rejected and nothing is pinned`() {
        val store = tmpStore()
        val trust = StrictHostKeyTrust(store)
        val id = HostKeyId("example.com", 22, "ssh-ed25519")

        assertThrows<HostKeyUnknownException> {
            trust.verify(id, ed25519()) { false }      // 用户没确认
        }
        assertEquals(0, store.size(), "TOFU 违规：未知主机被自动写入信任库")
    }

    @Test
    fun `F2 explicit approval pins the key`() {
        val store = tmpStore()
        val trust = StrictHostKeyTrust(store)
        val id = HostKeyId("example.com", 22, "ssh-ed25519")
        val blob = ed25519()

        assertTrue(trust.verify(id, blob) { true })
        assertEquals(1, store.size())
        assertEquals(Fingerprint.sha256(blob), store.get(id))
    }

    @Test
    fun `F3 pinned key is accepted without prompting`() {
        val store = tmpStore()
        val trust = StrictHostKeyTrust(store)
        val id = HostKeyId("example.com", 22, "ssh-ed25519")
        trust.verify(id, ed25519()) { true }

        var prompted = false
        assertTrue(trust.verify(id, ed25519()) { prompted = true; true })
        assertFalse(prompted, "已固定的主机不应再次询问")
    }

    @Test
    fun `F4 tampered pin raises mismatch and does not rewrite`() {
        val store = tmpStore()
        val trust = StrictHostKeyTrust(store)
        val id = HostKeyId("example.com", 22, "ssh-ed25519")
        store.pin(id, "SHA256:AAAA")

        val ex = assertThrows<HostKeyMismatchException> { trust.verify(id, ed25519()) { true } }
        assertEquals("SHA256:AAAA", ex.pinned)
        assertEquals("SHA256:AAAA", store.get(id))
    }

    @Test
    fun `F5 algorithm change is a different identity, never auto-trusted`() {
        val store = tmpStore()
        val trust = StrictHostKeyTrust(store)
        trust.verify(HostKeyId("h", 22, "ssh-ed25519"), ed25519()) { true }

        // 服务端只提供 rsa → 新的 (host,port,alg) 身份，必须重新显式确认
        assertThrows<HostKeyUnknownException> {
            trust.verify(HostKeyId("h", 22, "rsa-sha2-256"), SshKeyBlob.build("rsa-sha2-256")) { false }
        }
    }

    @Test
    fun `sshj adapter compiles against real HostKeyVerifier and fails closed`() {
        val store = tmpStore()
        val verifier = SshjHostKeyVerifier(StrictHostKeyTrust(store), onUnknown = { false })

        val key = object : PublicKey {
            override fun getAlgorithm() = "EdDSA"          // 和 SSH 算法名不同，验证我们解析 blob
            override fun getFormat() = "X.509"
            override fun getEncoded() = SshKeyBlob.build("ssh-ed25519")
        }

        assertThrows<HostKeyUnknownException> { verifier.verify("h", 22, key) }
        assertEquals(0, store.size())
        assertEquals(HostKeyId("h", 22, "ssh-ed25519"), HostKeyId("h", 22, SshKeyBlob.algorithmName(key.encoded)))
        assertTrue(verifier.findExistingAlgorithms("h", 22).contains("ssh-ed25519"))
        assertFalse(verifier.findExistingAlgorithms("h", 22).isEmpty())
    }

    @Test
    fun `store survives reload`() {
        val dir = Files.createTempDirectory("trust").toFile()
        val file = File(dir, "known_hosts.spike")
        val id = HostKeyId("h", 2222, "ssh-ed25519")
        TrustStore(file).pin(id, "SHA256:xyz")
        assertEquals("SHA256:xyz", TrustStore(file).get(id))
        assertTrue(file.readText().contains("h:2222:ssh-ed25519 SHA256:xyz"))
    }
}
