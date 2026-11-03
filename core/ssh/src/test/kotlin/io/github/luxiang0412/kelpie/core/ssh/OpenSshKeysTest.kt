package io.github.luxiang0412.kelpie.core.ssh

import net.schmizz.sshj.common.Buffer
import com.hierynomus.sshj.userauth.keyprovider.OpenSSHKeyV1KeyFile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Base64

class OpenSshKeysTest {
    // 一次性测试密钥：ssh-keygen -t ed25519 生成，种子由 kelpie_pair.py 的 ed25519_seed() 取出。没有装在任何地方。
    private val seed = Base64.getUrlDecoder().decode("S-tVtF_poCvXGkrrwx8ltqVeaIGTvqbGVhGmHQnhSao")
    private val expectedPub = "ssh-ed25519 AAAAC3NzaC1lZDI1NTE5AAAAIGTD7+0ITYyciNq1JrlSF+RzVVO0UaarO0HatdwLHZOm"

    @Test fun publicKeyMatchesSshKeygen() = assertEquals(expectedPub, OpenSshKeys.ed25519PublicLine(seed))

    @Test fun rebuiltPemLoadsInSshj() {
        val pem = OpenSshKeys.ed25519PemFromSeed(seed, "kelpie-test")
        assertTrue(pem.startsWith("-----BEGIN OPENSSH PRIVATE KEY-----\n"))
        val kf = OpenSSHKeyV1KeyFile().apply { init(pem, null) }
        val blob = Buffer.PlainBuffer().putPublicKey(kf.public).compactData
        assertEquals(expectedPub, "ssh-ed25519 " + Base64.getEncoder().encodeToString(blob))
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsWrongSeedLength() { OpenSshKeys.ed25519PemFromSeed(ByteArray(31)) }
}
