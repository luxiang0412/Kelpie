package dev.herdr.app

import dev.herdr.feature.board.BoardScreen
import dev.herdr.feature.board.HostsViewModel
import dev.herdr.feature.board.HerdrTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.herdr.core.data.HostConnectionManager
import dev.herdr.core.data.HostRepository
import dev.herdr.core.herdr.AgentStatus
import dev.herdr.core.herdr.BoardGrouper
import dev.herdr.core.herdr.Pane
import dev.herdr.core.herdr.WriteOutcome
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 看板 UI 状态测试（M2-A）：用可控 Fixture，不依赖真实远端 agent。
 *
 * 注意：MIUI 真机上 Compose instrumentation 会挂起（I-59），本组测试在 **模拟器** 上运行。
 */
@RunWith(AndroidJUnit4::class)
class BoardScreenTest {

    @get:Rule
    val rule = createAndroidComposeRule<TestHostActivity>()

    private fun res(id: Int): String =
        androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().targetContext.getString(id)

    private fun pane(id: String, st: AgentStatus, agent: String? = "pi", title: String? = null) =
        Pane(id, "term-$id", "w1", "w1:t1", agent, st, title ?: "任务-$id", "/repo/app-$id", 0)

    private fun host(
        kind: HostConnectionManager.ConnKind = HostConnectionManager.ConnKind.CONNECTED,
        label: String = "开发机",
    ) = HostsViewModel.HostState(
        cfg = HostRepository.HostConfig(id = "h1", label = label, host = "192.168.5.8", user = "luxiang", session = "test"),
        hasKey = true,
        connKind = kind,
        connText = when (kind) {
            HostConnectionManager.ConnKind.CONNECTED -> "已连接 · herdr 0.9.5 · protocol 22"
            HostConnectionManager.ConnKind.RECONNECTING -> "重连中（第 2 次，4s 后）"
            HostConnectionManager.ConnKind.FAILED -> "连接失败"
            HostConnectionManager.ConnKind.AWAITING_HOST_KEY -> "等待确认主机指纹"
            else -> "未连接"
        },
        lastError = if (kind == HostConnectionManager.ConnKind.FAILED) "认证失败" else null,
        paneCount = 0,
    )

    private fun state(
        panes: List<Pane>,
        hosts: List<HostsViewModel.HostState> = listOf(host()),
        stale: Boolean = false,
    ) = HostsViewModel.UiState(
        hosts = hosts,
        activeId = hosts.first().cfg.id,
        board = HostsViewModel.BoardView(
            hostId = hosts.first().cfg.id,
            panes = panes,
            groups = BoardGrouper.group(panes),
            counts = BoardGrouper.counts(panes),
            version = "0.9.5",
            protocol = 22,
            updatedAtMs = System.currentTimeMillis(),
            stale = stale,
        ),
    )

    private fun render(
        state: HostsViewModel.UiState,
        approvals: List<HostConnectionManager.ApprovalItem> = emptyList(),
        onApproval: (HostConnectionManager.ApprovalItem, Boolean) -> Unit = { _, _ -> },
        onSelectAgent: (Pane) -> Unit = {},
        onReconnect: () -> Unit = {},
    ) {
        rule.setContent {
            HerdrTheme {
                BoardScreen(
                    state = state,
                    approvals = approvals,
                    onApproval = onApproval,
                    onSelectHost = {},
                    onRefresh = {},
                    onReconnect = onReconnect,
                    onOpenHosts = {},
                    onSelectAgent = onSelectAgent,
                )
            }
        }
    }

    @Test
    fun blocked_isProminentAndFirst() {
        val panes = listOf(pane("w1:p1", AgentStatus.WORKING), pane("w1:p2", AgentStatus.BLOCKED))
        render(state(panes))
        // blocked 使用“需要你”文案（不只靠颜色）
        assertTrue(rule.onAllNodesWithText(res(dev.herdr.feature.board.R.string.board_status_blocked)).fetchSemanticsNodes().isNotEmpty())
        assertTrue(rule.onAllNodesWithText(res(dev.herdr.feature.board.R.string.board_section_blocked)).fetchSemanticsNodes().isNotEmpty())
        // 分组顺序：需要处理在运行中之前
        val blockedHeader = rule.onAllNodesWithText(res(dev.herdr.feature.board.R.string.board_section_blocked)).fetchSemanticsNodes().first()
        val workingHeader = rule.onAllNodesWithText(res(dev.herdr.feature.board.R.string.board_section_working)).fetchSemanticsNodes().first()
        assertEquals(true, blockedHeader.boundsInRoot.top <= workingHeader.boundsInRoot.top)
    }

    @Test
    fun doneAndIdle_areDistinctLabels() {
        render(state(listOf(pane("w1:p1", AgentStatus.DONE), pane("w1:p2", AgentStatus.IDLE))))
        // 状态词在合并语义的行内（TalkBack 一致朗读），需要 unmerged tree 才能单独断言
        // BOARD-DONE-001：已完成默认收起，因此「已完成」只作为分区标题出现一次；
        // 「空闲」行可见（idle 分区默认展开需用户操作，此处置为展开态断言分区存在）
        assertTrue(rule.onAllNodesWithText(res(dev.herdr.feature.board.R.string.board_section_done), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty())
        assertTrue(rule.onAllNodesWithText(res(dev.herdr.feature.board.R.string.board_status_idle), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty())
        // 「其他」分区默认展开由 allIdle_stillShowsRows 覆盖（同一渲染路径），此处不再重复断言标题节点
    }

    @Test
    fun allIdle_stillShowsRows() {
        render(state(listOf(pane("w1:p1", AgentStatus.IDLE), pane("w1:p2", AgentStatus.UNKNOWN))))
        // UX-02b：idle 与 unknown 不再混成「其他」，各自独立分区且默认展开
        assertTrue(rule.onAllNodesWithText(res(dev.herdr.feature.board.R.string.board_status_idle), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty())
        assertTrue(rule.onAllNodesWithText(res(dev.herdr.feature.board.R.string.board_status_unknown), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty())
        // 行是合并语义：用 contentDescription 断言两行都真实渲染
        assertTrue(rule.onAllNodesWithContentDescription("任务-w1:p1", substring = true).fetchSemanticsNodes().isNotEmpty())
        assertTrue(rule.onAllNodesWithContentDescription("任务-w1:p2", substring = true).fetchSemanticsNodes().isNotEmpty())
    }

    @Test
    fun offlineHost_showsCachedBannerAndRetry() {
        var retried = false
        render(
            state(
                listOf(pane("w1:p1", AgentStatus.WORKING)),
                hosts = listOf(host(HostConnectionManager.ConnKind.FAILED)),
                stale = true,
            ),
            onReconnect = { retried = true },
        )
        assertTrue(rule.onAllNodesWithText("连接失败").fetchSemanticsNodes().isNotEmpty())
        rule.onNodeWithText("重试").assertIsDisplayed().performClick()
        rule.runOnIdle { assertEquals(true, retried) }
    }

    @Test
    fun reconnecting_bannerIsShown() {
        render(
            state(
                listOf(pane("w1:p1", AgentStatus.WORKING)),
                hosts = listOf(host(HostConnectionManager.ConnKind.RECONNECTING)),
            ),
        )
        rule.onAllNodesWithText("重连中（第 2 次，4s 后）")[0].assertIsDisplayed()
    }

    @Test
    fun awaitingHostKey_bannerIsShown() {
        render(
            state(
                listOf(pane("w1:p1", AgentStatus.IDLE)),
                hosts = listOf(host(HostConnectionManager.ConnKind.AWAITING_HOST_KEY)),
            ),
        )
        rule.onAllNodesWithText("等待确认主机指纹", substring = true)[0].assertIsDisplayed()
    }

    @Test
    fun staleData_isLabelledAsCache() {
        render(
            state(listOf(pane("w1:p1", AgentStatus.BLOCKED)), stale = true),
        )
        rule.onNodeWithText(res(dev.herdr.feature.board.R.string.board_cache_banner_prefix), substring = true).assertExists()
    }

    @Test
    fun tappingAgent_opensConversation() {
        var opened: Pane? = null
        val p = pane("w1:p9", AgentStatus.WORKING)
        render(state(listOf(p)), onSelectAgent = { opened = it })
        // 行是合并语义：标题在 contentDescription 里，文本节点在 unmerged tree
        rule.onNodeWithContentDescription("任务-w1:p9", substring = true).performClick()
        rule.runOnIdle { assertEquals("w1:p9", opened?.paneId) }
    }

    @Test
    fun approvalCard_allowsDecision() {
        var decision: Boolean? = null
        val req = dev.herdr.core.data.Approvals.Request(
            hostId = "h1", requestId = "req-1234", nonce = "n1", question = "允许执行测试命令吗？",
            createdAtMs = System.currentTimeMillis(), expiresAtMs = System.currentTimeMillis() + 60_000, path = "/x.req",
        )
        render(
            state(listOf(pane("w1:p1", AgentStatus.BLOCKED))),
            approvals = listOf(HostConnectionManager.ApprovalItem("h1", "开发机", req)),
            onApproval = { _, allow -> decision = allow },
        )
        rule.onNodeWithText("允许执行测试命令吗？").assertIsDisplayed()
        rule.onNodeWithText("允许").performClick()
        rule.runOnIdle { assertEquals(true, decision) }
    }
}
