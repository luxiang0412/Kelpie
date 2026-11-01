package dev.herdr.app

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.herdr.core.data.HostConnectionManager
import dev.herdr.core.data.HostRepository
import dev.herdr.core.herdr.AgentStatus
import dev.herdr.core.herdr.BoardGrouper
import dev.herdr.core.herdr.Pane
import dev.herdr.core.herdr.WriteOutcome
import dev.herdr.core.herdr.WriteRecord
import dev.herdr.feature.board.BoardScreen
import dev.herdr.feature.board.HerdrTheme
import dev.herdr.feature.board.HostsViewModel
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** UX-02c：工作台异常 Banner 的优先级与 Unknown 写操作入口。 */
@RunWith(AndroidJUnit4::class)
class BoardBannerTest {

    @get:Rule
    val rule = createAndroidComposeRule<TestHostActivity>()

    private fun res(id: Int): String =
        androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().targetContext.getString(id)

    private fun host(kind: HostConnectionManager.ConnKind, text: String) = HostsViewModel.HostState(
        cfg = HostRepository.HostConfig(id = "h1", label = "开发机", host = "192.168.5.8", user = "u", session = "test"),
        hasKey = true,
        connKind = kind,
        connText = text,
        paneCount = 1,
    )

    private fun state(kind: HostConnectionManager.ConnKind, text: String, stale: Boolean = false) =
        HostsViewModel.UiState(
            hosts = listOf(host(kind, text)),
            activeId = "h1",
            board = HostsViewModel.BoardView(
                hostId = "h1",
                panes = listOf(Pane("w1:p1", "t", "w1", "w1:t1", "pi", AgentStatus.WORKING, "任务", "/repo", 0)),
                groups = BoardGrouper.group(
                    listOf(Pane("w1:p1", "t", "w1", "w1:t1", "pi", AgentStatus.WORKING, "任务", "/repo", 0)),
                ),
                counts = emptyMap(),
                version = "0.9.5",
                protocol = 22,
                updatedAtMs = System.currentTimeMillis(),
                stale = stale,
            ),
        )

    private fun render(
        st: HostsViewModel.UiState,
        pending: List<WriteRecord> = emptyList(),
        onOpenPending: () -> Unit = {},
    ) {
        rule.setContent {
            HerdrTheme {
                var ui by remember { mutableStateOf(st) }
                BoardScreen(
                    state = ui,
                    approvals = emptyList(),
                    onApproval = { _, _ -> },
                    onSelectHost = {},
                    onRefresh = {},
                    onReconnect = {},
                    onOpenHosts = {},
                    onSelectAgent = {},
                    pendingWrites = pending,
                    onOpenPending = onOpenPending,
                )
            }
        }
    }

    private val unknown = WriteRecord(
        clientRequestId = "abcdef12-3456",
        method = "agent.prompt",
        target = "w1:p1",
        payloadSha256 = "deadbeefdeadbeef",
        sentAtMs = 1L,
        attempts = 1,
        outcome = WriteOutcome.Unknown("timeout"),
    )

    @Test
    fun unknownWrite_outranksTransportBanner_andOpensDetails() {
        var opened = false
        render(
            state(HostConnectionManager.ConnKind.RECONNECTING, "重连中（第 1 次，2s 后）"),
            pending = listOf(unknown),
            onOpenPending = { opened = true },
        )
        // Unknown 优先：文案必须说明“可能已执行”，且不得写成失败
        rule.onNodeWithText("有 1 个发送结果无法确认，远端可能已执行（不会自动重发）", substring = true).assertIsDisplayed()
        rule.onNodeWithText("查看").performClick()
        rule.runOnIdle { assertTrue(opened) }
    }

    @Test
    fun transportBanner_shownWhenNoUnknown() {
        render(state(HostConnectionManager.ConnKind.RECONNECTING, "重连中（第 1 次，2s 后）"))
        rule.onAllNodesWithText("重连中（第 1 次，2s 后）")[0].assertIsDisplayed()
    }

    @Test
    fun staleData_isLabelledAsCache_onBoardBanner() {
        render(state(HostConnectionManager.ConnKind.CONNECTED, "已连接", stale = true))
        rule.onNodeWithText(res(dev.herdr.feature.board.R.string.board_cache_banner_prefix), substring = true).assertIsDisplayed()
    }

    @Test
    fun healthyConnection_showsNoPersistentBanner() {
        render(state(HostConnectionManager.ConnKind.CONNECTED, "已连接 · herdr 0.9.5"))
        // 正常在线不应出现常驻横幅
        assertTrue(rule.onAllNodesWithText("已连接 · herdr 0.9.5").fetchSemanticsNodes().isEmpty())
    }

    @Test
    fun awaitingHostKey_isHighPriority() {
        render(state(HostConnectionManager.ConnKind.AWAITING_HOST_KEY, "等待确认主机指纹"))
        rule.onAllNodesWithText("等待确认主机指纹")[0].assertIsDisplayed()
    }
}
