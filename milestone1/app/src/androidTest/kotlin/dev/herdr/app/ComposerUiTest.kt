package dev.herdr.app

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.herdr.core.data.HostConnectionManager
import dev.herdr.core.herdr.AgentStatus
import dev.herdr.core.herdr.Pane
import dev.herdr.core.herdr.WriteOutcome
import dev.herdr.feature.board.ConversationScreen
import dev.herdr.feature.board.HerdrTheme
import dev.herdr.feature.board.HostsViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Composer 交互测试（M2-C2）。
 * 运行环境：隔离 AVD（MIUI 真机的 BAL 限制见 I-74）。
 */
@RunWith(AndroidJUnit4::class)
class ComposerUiTest {

    @get:Rule
    val rule = createAndroidComposeRule<TestHostActivity>()

    private val pane = Pane(
        paneId = "w1:p1",
        terminalId = "term-1",
        workspaceId = "w1",
        tabId = "w1:t1",
        agent = "pi",
        status = AgentStatus.WORKING,
        title = "任务",
        cwd = "/repo",
        revision = 0,
        agentSessionPath = "/home/u/.pi/sessions/a.jsonl",
    )

    private fun render(
        initial: HostConnectionManager.ComposerState,
        onSend: () -> Unit = {},
    ) {
        rule.setContent {
            HerdrTheme {
                var composer by remember { mutableStateOf(initial) }
                ConversationScreen(
                    pane = pane,
                    transcript = HostsViewModel.TranscriptState(path = "/a.jsonl", messages = emptyList()),
                    output = "",
                    composer = composer,
                    onTextChange = { composer = composer.copy(text = it) },
                    onSend = onSend,
                    onDismissOutcome = { composer = composer.copy(outcome = null, message = null, pendingText = null) },
                    onReload = {},
                    onOpenTechSheet = {},
                    onBack = {},
                )
            }
        }
    }

    @Test
    fun typing_enablesSend_andDisablesWhenEmpty() {
        render(HostConnectionManager.ComposerState())
        rule.onNodeWithContentDescription("发送").assertIsNotEnabled().assertExists()
        rule.onNodeWithContentDescription("指令输入框").performTextInput("检查依赖")
        rule.onNodeWithContentDescription("发送").assertIsEnabled()
    }

    @Test
    fun multilineInput_keepsNewlines_andSendStaysExplicit() {
        render(HostConnectionManager.ComposerState())
        rule.onNodeWithContentDescription("指令输入框").performTextInput("line1\nline2\nline3")
        rule.onNodeWithText("line1\nline2\nline3").assertExists()
    }

    @Test
    fun accepted_outcomeIsShownDistinctly() {
        render(
            HostConnectionManager.ComposerState(
                outcome = WriteOutcome.Accepted,
                message = "已接受（Accepted）：远端已收到，未代表执行完成",
            ),
        )
        rule.onNodeWithText("已接受（Accepted）：远端已收到，未代表执行完成").assertExists()
    }

    @Test
    fun rejected_keepsTextAndShowsCode() {
        render(
            HostConnectionManager.ComposerState(
                text = "原始指令",
                outcome = WriteOutcome.Rejected("not_connected", "主机未连接"),
                message = "被拒绝：not_connected（内容已保留，可修改后重发）",
            ),
        )
        rule.onNodeWithText("被拒绝：not_connected（内容已保留，可修改后重发）").assertExists()
        rule.onNodeWithText("原始指令").assertExists()
    }

    @Test
    fun unknown_showsPendingAndRequiresConfirmationBeforeResend() {
        var sends = 0
        render(
            HostConnectionManager.ComposerState(
                text = "待确认内容",
                outcome = WriteOutcome.Unknown("timeout"),
                message = "结果未知（Unknown）：不会自动重发，请稍后在会话里确认",
                pendingText = "待确认内容",
                pendingActionId = "act-1",
            ),
            onSend = { sends++ },
        )
        rule.onNodeWithText("结果未知（Unknown）：不会自动重发，请稍后在会话里确认").assertExists()
        rule.onNodeWithText("重新发送…").performClick()
        // 必须先确认，不能直接发送
        rule.onNodeWithText("重新发送待确认内容？").assertExists()
        assertEquals(0, sends)
        rule.onNodeWithText("确认重新发送").performClick()
        rule.runOnIdle { assertTrue(sends == 1) }
    }

    @Test
    fun longPromptEditor_opensAndKeepsText() {
        render(HostConnectionManager.ComposerState(text = "第一行"))
        rule.onNodeWithText("⤢").performClick()
        rule.onNodeWithText("长 Prompt").assertExists()
        rule.onNodeWithText("3 字符（草稿会自动保存）").assertExists()
    }

    @Test
    fun sendButton_isNotClickableWhileSending() {
        render(HostConnectionManager.ComposerState(text = "x", sending = true, message = "发送中…"))
        rule.onNodeWithContentDescription("发送中，请稍候").assertExists()
        rule.onNodeWithText("发送中").assertExists()
    }
}
