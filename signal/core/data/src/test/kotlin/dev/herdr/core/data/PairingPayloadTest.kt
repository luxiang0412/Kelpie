package dev.herdr.core.data

import dev.herdr.core.ssh.HostKeyId
import dev.herdr.core.ssh.TrustStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.Base64

class PairingPayloadTest {
    private val fp = "SHA256:" + "A".repeat(43)
    private val key = "-----BEGIN OPENSSH PRIVATE KEY-----\nabc\n-----END OPENSSH PRIVATE KEY-----\n"

    private fun link(json: String) = "herdr-signal://pair?d=" + Base64.getUrlEncoder().withoutPadding().encodeToString(json.toByteArray())

    private fun ok(extra: String = "") = link(
        """{"v":1,"n":"MS-7E07","h":"10.0.0.2","p":2222,"u":"lx","s":"test","b":"/opt/herdr","k":{"ssh-ed25519":"$fp"},"key":${kotlinx.serialization.json.JsonPrimitive(key)},"exp":1000$extra}""",
    )

    @Test fun parsesAllFields() {
        val p = PairingPayload.parse(ok(), nowSec = 900).getOrThrow()
        assertEquals("MS-7E07", p.label); assertEquals("10.0.0.2", p.host); assertEquals(2222, p.port)
        assertEquals("lx", p.user); assertEquals("test", p.session); assertEquals("/opt/herdr", p.herdrBin)
        assertEquals(mapOf("ssh-ed25519" to fp), p.hostKeys); assertEquals(key.trim(), p.privateKey)
        assertTrue("toString 不能带私钥", !p.toString().contains("PRIVATE"))
    }

    @Test fun expiredWithinSkewStillAccepted() = assertTrue(PairingPayload.parse(ok(), nowSec = 1000 + 299).isSuccess)
    @Test fun expiredRejected() = assertTrue(PairingPayload.parse(ok(), nowSec = 1000 + 301).isFailure)
    @Test fun notOurs() = assertTrue(PairingPayload.parse("https://example.com/?d=xx").isFailure)
    @Test fun garbage() = assertTrue(PairingPayload.parse("herdr-signal://pair?d=%%%").isFailure)

    @Test fun badFingerprintsDropped_noneLeftRejected() {
        val l = link("""{"v":1,"h":"h","u":"u","k":{"ssh-ed25519":"MD5:aa","BAD ALG":"$fp"}}""")
        assertTrue(PairingPayload.parse(l).isFailure)
    }

    @Test fun defaults() {
        val p = PairingPayload.parse(link("""{"v":1,"h":"h","u":"u","k":{"ssh-rsa":"$fp"}}""")).getOrThrow()
        assertEquals(22, p.port); assertEquals("default", p.session); assertEquals("/home/u/.local/bin/herdr", p.herdrBin)
        assertEquals("h", p.label); assertNull(p.privateKey)
    }

    @Test fun whitespaceInHostRejected() =
        assertTrue(PairingPayload.parse(link("""{"v":1,"h":"a b","u":"u","k":{"ssh-rsa":"$fp"}}""")).isFailure)

    @Test fun conflictingPinnedFingerprintDetected() {
        val f = File.createTempFile("trust", ".txt").apply { delete(); deleteOnExit() }
        val trust = TrustStore(f)
        val p = PairingPayload.parse(ok(), nowSec = 0).getOrThrow()
        assertTrue(Pairing.conflicts(trust, p).isEmpty())
        trust.pin(HostKeyId("10.0.0.2", 2222, "ssh-ed25519"), "SHA256:" + "B".repeat(43))
        assertEquals(setOf("ssh-ed25519"), Pairing.conflicts(trust, p).keys)
    }
}
