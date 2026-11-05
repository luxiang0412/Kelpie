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
import io.github.luxiang0412.kelpie.conversation.AskQuestionCard
import io.github.luxiang0412.kelpie.conversation.model.ConversationItem
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

/** 对话里 AskUserQuestion 卡片的各个状态（等你选 / 已选 / 打字回答 / 取消 / 多题多选）。 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h1200dp-xxhdpi")
class AskQuestionScreenshotTest {
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
        question = "这一轮要一起做哪些？", header = "范围", multiSelect = true,
        options = listOf(AskQuestion.Option("迁移脚本"), AskQuestion.Option("权限包编辑页"), AskQuestion.Option("E2E 测试（推荐）")),
    )

    private fun tool(qs: List<AskQuestion>, result: String? = null, answers: Map<String, String>? = null) =
        ConversationItem.Tool(0, "AskUserQuestion", qs.first().question, result, false, result != null, "t", qs, answers)

    private fun shot(name: String, mode: ThemeMode) {
        compose.setContent {
            KelpieTheme(mode) {
                Column(
                    Modifier.fillMaxWidth().background(Kelpie.colors.bg).padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    AskQuestionCard(tool(listOf(q1)))
                    AskQuestionCard(tool(listOf(q1), "answered", mapOf(q1.question to "先按助手绑定控制 (Recommended)")))
                    AskQuestionCard(tool(listOf(q1), "answered", mapOf(q1.question to "先按助手绑定，但给业务人员留只读入口")))
                    AskQuestionCard(tool(listOf(q1), "The user doesn't want to proceed with this tool use. The tool use was rejected."))
                    AskQuestionCard(tool(listOf(q1, q2), "answered", mapOf(q1.question to "照计划接入权限包", q2.question to "迁移脚本, E2E 测试（推荐）")))
                }
            }
        }
        compose.onRoot().captureRoboImage("src/test/screenshots/$name.png")
    }

    @Test fun statesLight() = shot("ask_question_states_light", ThemeMode.Light)
    @Test fun statesDark() = shot("ask_question_states_dark", ThemeMode.Dark)
}
