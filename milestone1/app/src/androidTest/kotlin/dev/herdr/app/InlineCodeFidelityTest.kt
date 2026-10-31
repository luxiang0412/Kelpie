package dev.herdr.app

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.printToString
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.herdr.feature.board.HerdrTheme
import dev.herdr.feature.board.markdown.MarkdownText
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * UX-01b：行内代码保真。
 * 渲染后只允许去掉 Markdown 反引号，**不得**插入任何额外空格（旧实现为视觉 padding 加了两个空格）。
 */
@RunWith(AndroidJUnit4::class)
class InlineCodeFidelityTest {

    @get:Rule
    val rule = createAndroidComposeRule<TestHostActivity>()

    private fun render(src: String, expected: String) {
        rule.setContent { HerdrTheme { MarkdownText(src) } }
        rule.waitUntil(10_000) {
            rule.onAllNodesWithText(expected, substring = true).fetchSemanticsNodes().isNotEmpty()
        }
        rule.waitForIdle()
    }

    @Test
    fun inlineCode_keepsExactText_noInjectedSpaces() {
        val src = "运行 `grep -rn unused` 于 `spike/` 目录"
        val expected = "运行 grep -rn unused 于 spike/ 目录"
        render(src, expected)
        val nodes = rule.onAllNodesWithText(expected, substring = true).fetchSemanticsNodes()
        assert(nodes.isNotEmpty()) {
            "渲染文本应等于 " + expected + "，实际语义树：\n" + rule.onRoot().printToString(maxDepth = 8)
        }
    }

    @Test
    fun cjkAdjacentInlineCode_notPadded() {
        val src = "中文`HANDOFF.md`紧贴中文"
        val expected = "中文HANDOFF.md紧贴中文"
        render(src, expected)
        val nodes = rule.onAllNodesWithText(expected, substring = true).fetchSemanticsNodes()
        assert(nodes.isNotEmpty()) { "中英文紧贴时不得插入空格：" + expected }
    }
}
