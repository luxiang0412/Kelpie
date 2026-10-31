package dev.herdr.feature.board

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.herdr.core.data.SettingsStore
import dev.herdr.core.herdr.AgentStatus
import dev.herdr.core.herdr.BoardGrouper
import dev.herdr.core.herdr.Pane
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BoardScreenTest {

    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private fun pane(id: String, st: AgentStatus, agent: String? = "pi") =
        Pane(id, "term-$id", "w1", "w1:t1", agent, st, "title-$id", "/repo", 0)

    private fun state(panes: List<Pane>, conn: BoardViewModel.ConnKind, stale: Boolean) =
        BoardViewModel.UiState(
            host = SettingsStore.Host(host = "h", user = "u", session = "test"),
            hasKey = true,
            connKind = conn,
            connText = when (conn) {
                BoardViewModel.ConnKind.CONNECTED -> "已连接 · herdr 0.9.5 · protocol 22"
                BoardViewModel.ConnKind.STALE -> "数据可能过期"
                else -> "未连接"
            },
            groups = BoardGrouper.group(panes),
            counts = BoardGrouper.counts(panes),
            panes = panes,
            version = "0.9.5",
            protocol = 22,
            updatedAtMs = System.currentTimeMillis(),
            stale = stale,
        )

    @Test
    fun board_rendersGroupsCountsAndPanes() {
        val panes = listOf(
            pane("w1:p1", AgentStatus.BLOCKED),
            pane("w1:p2", AgentStatus.WORKING),
            pane("w4:pA", AgentStatus.UNKNOWN, agent = null),
        )
        rule.setContent {
            HerdrTheme {
                BoardScreen(state(panes, BoardViewModel.ConnKind.CONNECTED, false), {}, {}, {}, {})
            }
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
        var reconnectClicked = false
        rule.setContent {
            HerdrTheme {
                BoardScreen(
                    state(listOf(pane("w1:p1", AgentStatus.IDLE)), BoardViewModel.ConnKind.STALE, true),
                    onRefresh = {},
                    onReconnect = { reconnectClicked = true },
                    onOpenHosts = {},
                    onSelectPane = {},
                )
            }
        }
        rule.onNodeWithText("重连").assertIsDisplayed().performClick()
        rule.runOnIdle { assertEquals(true, reconnectClicked) }
    }

    @Test
    fun board_emptyStateOffersHostSettings() {
        var opened = false
        rule.setContent {
            HerdrTheme {
                BoardScreen(
                    state(emptyList(), BoardViewModel.ConnKind.IDLE, true),
                    onRefresh = {},
                    onReconnect = {},
                    onOpenHosts = { opened = true },
                    onSelectPane = {},
                )
            }
        }
        rule.onNodeWithText("还没有数据").assertIsDisplayed()
        rule.onNodeWithText("打开主机设置").assertIsDisplayed().performClick()
        rule.runOnIdle { assertEquals(true, opened) }
        assertEquals(0, rule.onAllNodesWithText("等你处理").fetchSemanticsNodes().size)
    }

    @Test
    fun paneDetail_rendersReadOnlyFields() {
        rule.setContent {
            HerdrTheme { PaneDetailScreen(pane("w4:pA", AgentStatus.DONE)) {} }
        }
        rule.onNodeWithText("Pane 详情").assertIsDisplayed()
        rule.onNodeWithText("w4:pA").assertIsDisplayed()
        rule.onNodeWithText("done").assertIsDisplayed()
    }
}
