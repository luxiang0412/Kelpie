package io.github.luxiang0412.kelpie.screenshot

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.captureRoboImage
import io.github.luxiang0412.kelpie.conversation.AnswerContent
import io.github.luxiang0412.kelpie.conversation.AskQuestionCard
import io.github.luxiang0412.kelpie.conversation.model.ConversationItem
import io.github.luxiang0412.kelpie.core.data.attention.AgentKey
import io.github.luxiang0412.kelpie.core.data.runtime.AnswerRecord
import io.github.luxiang0412.kelpie.core.data.runtime.AnswerState
import io.github.luxiang0412.kelpie.core.data.runtime.AskPicker
import io.github.luxiang0412.kelpie.core.designsystem.Kelpie
import io.github.luxiang0412.kelpie.core.designsystem.KelpieTheme
import io.github.luxiang0412.kelpie.core.designsystem.ThemeMode
import io.github.luxiang0412.kelpie.core.herdr.AskQuestion
import io.github.luxiang0412.kelpie.core.herdr.L10n
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** 在手机上回答选择题：答题页（单选 / 多选 + 自己写 / 确认页）和卡片上的进度与结果。 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h780dp-xxhdpi")
class AnswerScreenshotTest {
    @get:Rule val compose = createComposeRule()

    @Before fun zh() = L10n.update(java.util.Locale.SIMPLIFIED_CHINESE)
    @After fun restore() = L10n.update(java.util.Locale.getDefault())

    private val q1 = AskQuestion(
        question = "智能体工作台目前只开放给 FDE 和管理员，业务人员没有助手入口。知识库的可见范围，K1 怎么控制？",
        header = "知识库权限",
        options = listOf(
            AskQuestion.Option("先按助手绑定控制 (Recommended)", "能查哪些库 = 助手版本绑定的库 ∩ 已登记且启用的库。不改权限包和下发逻辑，K1 工作量小很多。"),
            AskQuestion.Option("照计划接入权限包", "权限包新增「外部知识库」资源类型，并下发到部门 / 用户，检索时按人过滤。"),
        ),
    )
    private val q2 = AskQuestion(
        question = "旧的 session 表要不要写迁移？", header = "迁移",
        options = listOf(AskQuestion.Option("写迁移，保留旧数据"), AskQuestion.Option("直接删表 (Recommended)")),
    )
    private val q3 = AskQuestion(
        question = "这一轮要一起做哪些？", header = "范围", multiSelect = true,
        options = listOf(
            AskQuestion.Option("迁移脚本", "把现有知识库的登记信息迁到新表。"),
            AskQuestion.Option("权限包编辑页", "管理端加「外部知识库」资源的勾选。"),
            AskQuestion.Option("E2E 测试 (Recommended)", "覆盖「绑定 → 检索」整条链路。"),
        ),
    )
    private val qs = listOf(q1, q2, q3)
    private val replies = listOf(
        AskPicker.Reply(setOf(0)),
        AskPicker.Reply(text = "先导出一份到 backups/，再删表。迁移等业务人员用上助手以后再说。"),
        AskPicker.Reply(setOf(0, 2)),
    )

    private fun page(name: String, at: Int, mode: ThemeMode = ThemeMode.Light) {
        compose.setContent {
            KelpieTheme(mode) { AnswerContent(qs, "ADR-041 重构交接 · Claude Code", {}, {}, initial = replies, initialPage = at) }
        }
        compose.onRoot().captureRoboImage("src/test/screenshots/$name.png")
    }

    @Test fun pageSingle() = page("answer_page_single", 0)
    @Test fun pageOwnText() = page("answer_page_own_text", 1)
    @Test fun pageMulti() = page("answer_page_multi", 2)
    @Test fun pageReview() = page("answer_page_review", 3)
    @Test fun pageReviewDark() = page("answer_page_review_dark", 3, ThemeMode.Dark)

    private fun tool(result: String? = null, answers: Map<String, String>? = null) =
        ConversationItem.Tool(0, "AskUserQuestion", q1.question, result, false, result != null, "call-1", listOf(q1, q3), answers)

    private fun rec(state: AnswerState) = AnswerRecord("r", AgentKey("h", "p"), "call-1", state, 1_793_846_520_000L)

    @Test
    @Config(qualifiers = "w360dp-h2400dp-xxhdpi")
    fun cardStates() {
        compose.setContent {
            KelpieTheme(ThemeMode.Light) {
                Column(Modifier.fillMaxWidth().background(Kelpie.colors.bg).padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    AskQuestionCard(tool(), onAnswer = {})
                    AskQuestionCard(tool(), rec(AnswerState.Sending), onAnswer = {})
                    AskQuestionCard(tool(), rec(AnswerState.Stopped("终端里已经有人答了一部分", touched = false)), onAnswer = {})
                    AskQuestionCard(tool(), rec(AnswerState.Unknown("提交后选择框没有关掉")), onAnswer = {})
                    AskQuestionCard(tool("answered", mapOf(q1.question to "照计划接入权限包", q3.question to "迁移脚本, E2E 测试 (Recommended)")), rec(AnswerState.Submitted(1_793_846_520_000L)))
                }
            }
        }
        compose.onRoot().captureRoboImage("src/test/screenshots/answer_card_states.png")
    }
}
