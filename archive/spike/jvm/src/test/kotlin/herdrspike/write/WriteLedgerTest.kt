package herdrspike.write

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class WriteLedgerTest {

    private fun ledger() = WriteLedger { 1000L }

    @Test
    fun `timeout becomes Unknown and never auto-retries`() {
        val l = ledger()
        val rec = l.begin("agent.prompt", "w1:p1", "hello".toByteArray())
        l.markUnknown(rec.clientRequestId, "timeout after 5000ms")

        val after = l.get(rec.clientRequestId)!!
        assertTrue(after.outcome is WriteOutcome.Unknown)
        assertFalse(l.autoRetryAllowed(rec.clientRequestId))
        assertEquals(1, after.attempts)
    }

    @Test
    fun `explicit error is Rejected, not retried`() {
        val l = ledger()
        val rec = l.begin("agent.prompt", "w1:p1", "x".toByteArray())
        l.reject(rec.clientRequestId, "agent_blocked", "agent is blocked")
        assertTrue(l.get(rec.clientRequestId)!!.outcome is WriteOutcome.Rejected)
        assertFalse(l.autoRetryAllowed(rec.clientRequestId))
    }

    @Test
    fun `Unknown only upgrades with positive evidence`() {
        val l = ledger()
        val rec = l.begin("agent.prompt", "w1:p1", "x".toByteArray())
        l.markUnknown(rec.clientRequestId, "channel closed")

        l.applyEvidence(rec.clientRequestId, delivered = false, evidence = "transcript:not found")
        assertTrue(l.get(rec.clientRequestId)!!.outcome is WriteOutcome.Unknown)

        l.applyEvidence(rec.clientRequestId, delivered = true, evidence = "transcript:line-42")
        assertEquals(WriteOutcome.Accepted, l.get(rec.clientRequestId)!!.outcome)
    }

    @Test
    fun `manual resend uses a new id and keeps the old record`() {
        val l = ledger()
        val rec = l.begin("pane.send_keys", "w1:p1", "[\"1\"]".toByteArray())
        l.markUnknown(rec.clientRequestId, "network down")

        val again = l.manualResend(rec.clientRequestId)
        assertNotEquals(rec.clientRequestId, again.clientRequestId)
        assertEquals(1, again.attempts)
        assertTrue(l.get(rec.clientRequestId)!!.outcome is WriteOutcome.Unknown)
        assertEquals(2, l.all().size)
    }

    @Test
    fun `cannot resend while pending, cannot double-accept`() {
        val l = ledger()
        val rec = l.begin("pane.send_text", "w1:p1", "x".toByteArray())
        assertThrows<NotSentException> { l.manualResend(rec.clientRequestId) }
        l.accept(rec.clientRequestId)
        assertThrows<IllegalStateException> { l.accept(rec.clientRequestId) }
    }

    @Test
    fun `evidence only applies to Unknown`() {
        val l = ledger()
        val rec = l.begin("pane.send_text", "w1:p1", "x".toByteArray())
        assertThrows<IllegalStateException> {
            l.applyEvidence(rec.clientRequestId, delivered = true, evidence = "n/a")
        }
    }
}
