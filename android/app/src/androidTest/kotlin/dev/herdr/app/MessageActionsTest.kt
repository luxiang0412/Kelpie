package dev.herdr.app

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.herdr.core.herdr.AgentStatus
import dev.herdr.core.herdr.ChatMessage
import dev.herdr.core.herdr.Pane
import dev.herdr.core.data.HostConnectionManager
import dev.herdr.feature.board.ConversationScreen
import dev.herdr.feature.board.HerdrTheme
import dev.herdr.feature.board.HostsViewModel
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** UX-03d：Agent 消息末尾「复制 + 更多」常显，且复制/菜单可用。 */
@RunWith(AndroidJUnit4::class)
class MessageActionsTest {

    @get:Rule
    val rule = createAndroidComposeRule<TestHostActivity>()

    private fun res(id: Int): String =
        androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().targetContext.getString(id)

    private val pane = Pane(
        "w1:p1", "t", "w1", "w1:t1", "pi", AgentStatus.DONE, "任务", "/repo", 0,
        agentSessionPath = "/a.jsonl",
    )

    private fun render(vararg messages: ChatMessage) {
        rule.setContent {
            HerdrTheme {
                ConversationScreen(
                    pane = pane,
                    transcript = HostsViewModel.TranscriptState(path = "/a.jsonl", messages = messages.toList()),
                    output = "",
                    composer = HostConnectionManager.ComposerState(),
                    onTextChange = {},
                    onSend = {},
                    onDismissOutcome = {},
                    onReload = {},
                    onOpenTechSheet = {},
                    onBack = {},
                )
            }
        }
        rule.waitForIdle()
    }

    @Test
    fun assistantMessage_showsCopyAndMore() {
        render(ChatMessage(ChatMessage.Kind.ASSISTANT, "简短回复", id = "m1"))
        // 顶栏也有「更多」按钮，这里按消息操作条的语义定位
        rule.onNodeWithContentDescription("复制这条回复").assertExists()
        rule.onNodeWithContentDescription("更多操作").assertExists()
    }

    @Test
    fun moreMenu_offersOnlyImplementedAction() {
        render(ChatMessage(ChatMessage.Kind.ASSISTANT, "简短回复", id = "m1"))
        rule.onNodeWithContentDescription("更多操作").performClick()
        rule.onNodeWithText(res(dev.herdr.feature.board.R.string.action_copy_raw)).assertExists()
    }

    @Test
    fun copyMarksStateWithoutChangingLayout() {
        render(ChatMessage(ChatMessage.Kind.ASSISTANT, "简短回复", id = "m1"))
        rule.onNodeWithContentDescription("复制这条回复").performClick()
        rule.onNodeWithText(res(dev.herdr.feature.board.R.string.action_copied)).assertExists()
    }
}
