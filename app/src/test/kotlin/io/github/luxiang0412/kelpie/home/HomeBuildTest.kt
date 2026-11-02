package io.github.luxiang0412.kelpie.home

import io.github.luxiang0412.kelpie.core.data.Approvals
import io.github.luxiang0412.kelpie.core.data.attention.AgentItem
import io.github.luxiang0412.kelpie.core.data.attention.AgentKey
import io.github.luxiang0412.kelpie.core.data.attention.HomeState
import io.github.luxiang0412.kelpie.core.data.runtime.ApprovalItem
import io.github.luxiang0412.kelpie.core.herdr.AgentStatus
import io.github.luxiang0412.kelpie.core.herdr.Pane
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 首页的审批合并：一个 agent 多个并发请求、在“工作中”的 agent 收到请求、没有 pane 的旧格式请求。 */
class HomeBuildTest {
    private fun pane(id: String, status: AgentStatus) = Pane(
        paneId = id, terminalId = null, workspaceId = "w", tabId = "w:t", agent = "claude",
        status = status, title = "Claude Code", cwd = "/home/u/spike-sandbox", revision = 0,
    )

    private fun item(id: String, status: AgentStatus) =
        AgentItem(AgentKey("h", id), "H", pane(id, status), status, 0, false, null, false)

    private fun req(id: String, paneId: String?) = ApprovalItem(
        "h", "H",
        Approvals.Request(
            hostId = "h", requestId = id, nonce = "n", question = "q", createdAtMs = 0, expiresAtMs = Long.MAX_VALUE,
            path = "/x/$id.req", paneId = paneId, tool = "Bash", command = "pwd",
        ),
    )

    private fun state(needs: List<AgentItem> = emptyList(), working: List<AgentItem> = emptyList()) =
        HomeState(needs, working, emptyList(), emptyList(), emptyList(), noHosts = false, loading = false)

    private fun build(s: HomeState, approvals: List<ApprovalItem>) =
        HomeViewModel.build(s, emptyMap(), approvals, emptyMap(), emptyMap(), phoneOnline = true, now = 0)

    @Test fun parallelRequestsFromOneWorkingAgent() {
        val ui = build(state(working = listOf(item("p2", AgentStatus.WORKING))), listOf(req("a", "p2"), req("b", "p2")))
        assertEquals(2, ui.entries.size)
        assertTrue(ui.entries.all { it is AttentionEntry.Permission && it.item != null })
        assertEquals("同一个 agent 只算一次", 1, ui.waitingAgents)
        assertTrue("不再重复出现在工作中", ui.state.working.isEmpty())
    }

    @Test fun blockedAgentWithTwoRequestsKeepsBoth() {
        val ui = build(state(needs = listOf(item("p2", AgentStatus.BLOCKED))), listOf(req("a", "p2"), req("b", "p2")))
        assertEquals(listOf("a", "b"), ui.entries.map { (it as AttentionEntry.Permission).approval.request.requestId })
        assertEquals(1, ui.waitingAgents)
    }

    @Test fun legacyRequestWithoutPaneStandsAlone() {
        val ui = build(state(needs = listOf(item("p1", AgentStatus.BLOCKED))), listOf(req("x", null)))
        assertEquals(2, ui.entries.size)
        assertTrue(ui.entries[0] is AttentionEntry.Question)
        assertEquals(2, ui.waitingAgents)
    }
}
