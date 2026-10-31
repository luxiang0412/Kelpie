package dev.herdr.core.herdr

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BoardGrouperTest {

    private fun pane(id: String, st: AgentStatus, agent: String? = "pi") =
        Pane(id, "t", "w1", "w1:t1", agent, st, "title", "/tmp", 0)

    @Test
    fun `groups follow priority order and drop empty ones`() {
        val panes = listOf(
            pane("w1:p1", AgentStatus.IDLE),
            pane("w1:p2", AgentStatus.BLOCKED),
            pane("w1:p3", AgentStatus.WORKING),
            pane("w1:p4", AgentStatus.DONE),
        )
        val groups = BoardGrouper.group(panes)
        assertEquals(
            listOf(AgentStatus.BLOCKED, AgentStatus.WORKING, AgentStatus.DONE, AgentStatus.IDLE),
            groups.map { it.status },
        )
        assertEquals(1, groups.first { it.status == AgentStatus.BLOCKED }.panes.size)
    }

    @Test
    fun `unknown status is kept and grouped last`() {
        val groups = BoardGrouper.group(listOf(pane("w1:pX", AgentStatus.UNKNOWN)))
        assertEquals(AgentStatus.UNKNOWN, groups.last().status)
    }

    @Test
    fun `counts only include non zero`() {
        val c = BoardGrouper.counts(
            listOf(pane("a", AgentStatus.WORKING), pane("b", AgentStatus.WORKING), pane("c", AgentStatus.DONE)),
        )
        assertEquals(2, c[AgentStatus.WORKING])
        assertEquals(1, c[AgentStatus.DONE])
        assertNull(c[AgentStatus.BLOCKED])
    }

    @Test
    fun `status wire mapping covers done not idle`() {
        assertEquals(AgentStatus.DONE, AgentStatus.fromWire("done"))
        assertEquals(AgentStatus.IDLE, AgentStatus.fromWire("idle"))
        assertEquals(AgentStatus.UNKNOWN, AgentStatus.fromWire("something-new"))
        assertEquals(AgentStatus.UNKNOWN, AgentStatus.fromWire(null))
    }

    @Test
    fun `pane ids are opaque strings (base36 safe)`() {
        val groups = BoardGrouper.group(listOf(pane("w4:pA", AgentStatus.IDLE), pane("w4:p9", AgentStatus.IDLE)))
        assertEquals(listOf("w4:p9", "w4:pA"), groups.first().panes.map { it.paneId })
        assertTrue(groups.first().panes.all { it.paneId.startsWith("w4:p") })
    }
}
