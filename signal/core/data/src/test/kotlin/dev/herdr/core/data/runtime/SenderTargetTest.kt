package dev.herdr.core.data.runtime

import dev.herdr.core.data.attention.AgentKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SenderTargetTest {
    @Test fun targetRoundTrip() {
        val k = AgentKey("3f2a-host", "w8:p3")
        assertEquals("3f2a-host/w8:p3", Sender.target(k))
        assertEquals(k, Sender.targetKey(Sender.target(k)))
    }

    @Test fun legacyTargetHasNoHost() {
        assertNull(Sender.targetKey("w8:p3")) // 0.2.0 及以前只存 paneId
        assertNull(Sender.targetKey("/w8:p3"))
        assertNull(Sender.targetKey("h/"))
    }
}
