package dev.herdr.core.herdr

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** M2-C2-V / I-88：未确认写操作必须跨进程可恢复，且绝不自动重放。 */
class WriteLedgerPersistTest {

    private class FakeSink : PendingWriteSink {
        var pending: List<WriteRecord> = emptyList()
        var changes = 0
        override fun onChanged(pending: List<WriteRecord>) {
            this.pending = pending
            changes++
        }
    }

    @Test
    fun pendingIsPersisted_acceptedIsRemoved() {
        val sink = FakeSink()
        val ledger = WriteLedger(sink = sink)
        val rec = ledger.begin("agent.prompt", "w7:p1", "hi".toByteArray())
        assertEquals(1, sink.pending.size)

        ledger.accept(rec.clientRequestId, "agent_prompted")
        assertTrue("确认后不应残留待确认记录", sink.pending.isEmpty())
    }

    @Test
    fun unknownIsPersisted_andSurvivesRestore() {
        val sink = FakeSink()
        val ledger = WriteLedger(sink = sink)
        val rec = ledger.begin("agent.prompt", "w7:p1", "hi".toByteArray())
        ledger.markUnknown(rec.clientRequestId, "timeout")

        assertEquals(1, sink.pending.size)
        assertTrue(sink.pending.single().outcome is WriteOutcome.Unknown)

        // 模拟进程重建：新账本 + 从持久层恢复
        val restored = WriteLedger(sink = FakeSink())
        restored.restore(sink.pending)
        val back = restored.get(rec.clientRequestId)!!
        assertTrue(back.outcome is WriteOutcome.Unknown)
        // 恢复不触发任何发送，也不会自动重试
        assertEquals(false, restored.autoRetryAllowed(rec.clientRequestId))
    }

    @Test
    fun rejectedIsNotPersistedAsPending() {
        val sink = FakeSink()
        val ledger = WriteLedger(sink = sink)
        val rec = ledger.begin("agent.prompt", "w7:p1", "hi".toByteArray())
        ledger.reject(rec.clientRequestId, "not_connected", "主机未连接")
        assertTrue(sink.pending.isEmpty())
    }

    @Test
    fun resendingSameText_usesNewActionId_andKeepsOldUnknown() {
        val sink = FakeSink()
        val ledger = WriteLedger(sink = sink)
        val first = ledger.begin("agent.prompt", "w7:p1", "same".toByteArray())
        ledger.markUnknown(first.clientRequestId, "timeout")

        val second = ledger.begin("agent.prompt", "w7:p1", "same".toByteArray())
        assertNotEquals(first.clientRequestId, second.clientRequestId)
        // 旧记录仍在（审计可追溯两次独立尝试），新记录带着同一 payload 指纹
        assertEquals(2, sink.pending.size)
        assertEquals(first.payloadSha256, second.payloadSha256)
        assertEquals(2, ledger.recent().count { it.method == "agent.prompt" })
    }

    @Test
    fun restoreIgnoresAcceptedRecords() {
        val sink = FakeSink()
        val ledger = WriteLedger(sink = sink)
        val rec = ledger.begin("agent.prompt", "w7:p1", "x".toByteArray())
        ledger.accept(rec.clientRequestId, "ok")

        val restored = WriteLedger()
        restored.restore(listOf(rec.copy(outcome = WriteOutcome.Accepted)))
        assertEquals(null, restored.get(rec.clientRequestId))
    }

    @Test
    fun pendingAtRestartBecomesUnknownNeverReplayed() {
        val l = WriteLedger()
        val rec = l.begin("agent.prompt", "h1/w1:p1", "hi".toByteArray())
        val restored = WriteLedger()
        restored.restore(listOf(rec), "closed while sending")
        assertEquals(WriteOutcome.Unknown("closed while sending"), restored.get(rec.clientRequestId)!!.outcome)
        assertEquals(false, restored.autoRetryAllowed(rec.clientRequestId))
    }

    @Test
    fun acknowledgedUnknownIsDroppedFromPersistence() {
        val sink = FakeSink()
        val l = WriteLedger(sink = sink)
        val a = l.begin("agent.prompt", "h1/w1:p1", "a".toByteArray())
        val b = l.begin("agent.prompt", "h1/w1:p2", "b".toByteArray())
        l.markUnknown(a.clientRequestId, "timeout")
        l.acknowledge(b.clientRequestId) // Pending：还在等网络结果，不能丢
        assertEquals(setOf(a.clientRequestId, b.clientRequestId), sink.pending.map { it.clientRequestId }.toSet())
        l.acknowledge(a.clientRequestId)
        assertEquals(listOf(b.clientRequestId), sink.pending.map { it.clientRequestId })
        assertEquals(null, l.get(a.clientRequestId))
    }
}
