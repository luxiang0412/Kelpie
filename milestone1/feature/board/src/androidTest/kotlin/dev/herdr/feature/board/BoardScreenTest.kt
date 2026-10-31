package dev.herdr.feature.board

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.herdr.core.data.HostRepository
import dev.herdr.core.herdr.AgentStatus
import dev.herdr.core.herdr.BoardGrouper
import dev.herdr.core.herdr.Pane
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Compose UI 测试（M2 版，多主机）。
 *
 * 注意：在 MIUI 设备上 instrumentation 会挂起（ISSUES I-59），目前只在非 MIUI 设备/CI 上有效。
 */
@RunWith(AndroidJUnit4::class)
class BoardScreenTest {

    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private fun pane(id: String, st: AgentStatus, agent: String? = "pi") =
        Pane(id, "term-$id", "w1", "w1:t1", agent, st, "title-$id", "/repo", 0)

    private fun hostState(kind: dev.herdr.core.data.HostConnectionManager.ConnKind) = HostsViewModel.HostState(
        cfg = HostRepository.HostConfig(
            id = "default", label = "开发机", host = "192.168.5.8", user = "luxiang", session = "test",
        ),
        hasKey = true,
        connKind = kind,
        connText = when (kind) {
            dev.herdr.core.data.HostConnectionManager.ConnKind.CONNECTED -> "已连接 · herdr 0.9.5 · protocol 22"
            dev.herdr.core.data.HostConnectionManager.ConnKind.STALE -> "数据可能过期"
            else -> "未连接"
        },
    )

    private fun state(
        panes: List<Pane>,
        kind: dev.herdr.core.data.HostConnectionManager.ConnKind,
        stale: Boolean,
        hosts: List<HostsViewModel.HostState> = listOf(hostState(kind)),
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

    @Test
    fun board_rendersGroupsCountsAndPanes() {
        val panes = listOf(
            pane("w1:p1", AgentStatus.BLOCKED),
            pane("w1:p2", AgentStatus.WORKING),
            pane("w4:pA", AgentStatus.UNKNOWN, agent = null),
        )
        rule.setContent {
            HerdrTheme { BoardScreen(state(panes, dev.herdr.core.data.HostConnectionManager.ConnKind.CONNECTED, false), {}, {}, {}, {}, {}) }
        }
        rule.onNodeWithText("Herdr 看板").assertIsDisplayed()
        rule.onNodeWithText("等你处理").assertIsDisplayed()
        rule.onNodeWithText("工作中").assertIsDisplayed()
        rule.onNodeWithText("未知").assertIsDisplayed()
        rule.onNodeWithText("w4:pA").assertIsDisplayed()
        rule.onNodeWithText("已连接 · herdr 0.9.5 · protocol 22").assertIsDisplayed()
        rule.onNodeWithText("herdr 0.9.5 · protocol 22 · 3 个 pane").assertIsDisplayed()
    }

    @Test
    fun board_showsStaleBannerAndReconnect() {
        var reconnected = false
        rule.setContent {
            HerdrTheme {
                BoardScreen(
                    state(listOf(pane("w1:p1", AgentStatus.IDLE)), dev.herdr.core.data.HostConnectionManager.ConnKind.STALE, true),
                    onSelectHost = {}, onRefresh = {}, onReconnect = { reconnected = true },
                    onOpenHosts = {}, onSelectPane = {},
                )
            }
        }
        rule.onNodeWithText("重连").assertIsDisplayed().performClick()
        rule.runOnIdle { assertEquals(true, reconnected) }
    }

    @Test
    fun board_emptyStateOffersHostSettings() {
        var opened = false
        rule.setContent {
            HerdrTheme {
                BoardScreen(
                    state(emptyList(), dev.herdr.core.data.HostConnectionManager.ConnKind.IDLE, true),
                    onSelectHost = {}, onRefresh = {}, onReconnect = {},
                    onOpenHosts = { opened = true }, onSelectPane = {},
                )
            }
        }
        rule.onNodeWithText("还没有数据").assertIsDisplayed()
        rule.onNodeWithText("主机设置").assertIsDisplayed().performClick()
        rule.runOnIdle { assertEquals(true, opened) }
        assertEquals(0, rule.onAllNodesWithText("等你处理").fetchSemanticsNodes().size)
    }

    @Test
    fun multiHost_showsChipsAndSwitches() {
        val a = hostState(dev.herdr.core.data.HostConnectionManager.ConnKind.CONNECTED)
        val b = a.copy(
            cfg = a.cfg.copy(id = "dead", label = "不可达测试机"),
            connKind = dev.herdr.core.data.HostConnectionManager.ConnKind.FAILED,
            connText = "连接失败",
        )
        var selected: String? = null
        rule.setContent {
            HerdrTheme {
                BoardScreen(
                    state(listOf(pane("w1:p1", AgentStatus.IDLE)), dev.herdr.core.data.HostConnectionManager.ConnKind.CONNECTED, false, listOf(a, b)),
                    onSelectHost = { selected = it }, onRefresh = {}, onReconnect = {}, onOpenHosts = {}, onSelectPane = {},
                )
            }
        }
        rule.onNodeWithText("开发机").assertIsDisplayed()
        rule.onNodeWithText("不可达测试机").assertIsDisplayed().performClick()
        rule.runOnIdle { assertEquals("dead", selected) }
    }

    @Test
    fun paneDetail_rendersReadOnlyFields() {
        rule.setContent { HerdrTheme { PaneDetailScreen(pane("w4:pA", AgentStatus.DONE)) {} } }
        rule.onNodeWithText("Pane 详情").assertIsDisplayed()
        rule.onNodeWithText("w4:pA").assertIsDisplayed()
        rule.onNodeWithText("done").assertIsDisplayed()
    }
}
