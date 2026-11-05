package io.github.luxiang0412.kelpie.core.data.runtime

import io.github.luxiang0412.kelpie.core.data.runtime.AskPicker.Outcome
import io.github.luxiang0412.kelpie.core.data.runtime.AskPicker.Reply
import io.github.luxiang0412.kelpie.core.data.runtime.AskPicker.Screen
import io.github.luxiang0412.kelpie.core.herdr.AskQuestion
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AskPickerTest {

    private fun q(question: String, header: String, multi: Boolean, vararg labels: String) =
        AskQuestion(question, header, multi, labels.map { AskQuestion.Option(it, "说明：$it") })

    private val qs3 = listOf(
        q("请选择权限方案：", "权限", false, "最小权限 (Recommended)", "完全权限"),
        q("请选择迁移方式：", "迁移", false, "渐进迁移", "一次性迁移"),
        q("请选择本次范围（可多选）：", "范围", true, "前端", "后端", "数据库"),
    )

    // ---------- 真机抓到的屏幕 ----------

    private val realQ1 = """
───────────────────────────────────────────────────────────────────────────────────────────────────────────────────────
←  ☐ 权限  ☐ 迁移  ☐ 范围  ✔ Submit  →

请选择权限方案：

❯ 1. 最小权限 (Recommended)
     仅授予完成任务所需的最少权限，风险更低，需要时再逐步放开。
  2. 完全权限
     授予全部权限，操作更顺畅，但风险更高。
  3. Type something.
───────────────────────────────────────────────────────────────────────────────────────────────────────────────────────
  4. Chat about this

Enter to select · Tab/Arrow keys to navigate · Esc to cancel
""".trimIndent()

    private val realMulti = """
←  ☒ 模块  ☐ 环境  ✔ Submit  →
请选择模块（可多选）：
  1. [ ] 用户模块
         涉及用户相关的功能与数据。
  2. [✔] 订单模块
         涉及订单流程与交易记录。
  3. [ ] 报表模块
         涉及统计分析与报表输出。
❯ 4. [✔] 另加 2 张日志表
     Next
───────────────────────────────────────────────
  5. Chat about this
Enter to select · Tab/Arrow keys to navigate · ctrl+g to edit in VS Code · Esc to cancel
""".trimIndent()

    private val realReview = """
←  ☒ 权限  ☒ 迁移  ☒ 范围  ✔ Submit  →
Review your answers
 ● 请选择权限方案：
   → 完全权限
 ● 请选择迁移方式：
   → 先导出备份，再删表
 ● 请选择本次范围（可多选）：
   → 前端, 数据库
Ready to submit your answers?
❯ 1. Submit answers
  2. Cancel
""".trimIndent()

    private val realSingle = """
 ☐ 确认
请确认是否继续：
❯ 1. 确认继续
     按当前方案继续执行。
  2. 暂不执行
     保持现状，先不执行任何操作。
  3. Type something.
──────────────────────────
  4. Chat about this
Enter to select · ↑/↓ to navigate · Esc to cancel
""".trimIndent()

    private val realIdle = """
───────────────────────────
❯
───────────────────────────
  ⏵⏵ auto mode on (shift+tab to cycle)
""".trimIndent()

    @Test
    fun parsesRealScreens() {
        val a = AskPicker.parse(realQ1) as Screen.Question
        assertEquals(listOf("权限", "迁移", "范围"), a.tabs.map { it.header })
        assertTrue(a.tabs.none { it.done })
        assertEquals("请选择权限方案：", a.title)
        assertEquals(listOf("最小权限 (Recommended)", "完全权限", "Type something.", "Chat about this"), a.rows.map { it.label })
        assertEquals(1, a.cursorRow)

        val m = AskPicker.parse(realMulti) as Screen.Question
        assertEquals(listOf(false, true, false, true), m.rows.take(4).map { it.checked })
        assertEquals(4, m.cursorRow)
        assertEquals(listOf(true, false), m.tabs.map { it.done })

        val r = AskPicker.parse(realReview) as Screen.Review
        assertEquals(listOf("请选择权限方案：" to "完全权限", "请选择迁移方式：" to "先导出备份，再删表", "请选择本次范围（可多选）：" to "前端, 数据库"), r.pairs)
        assertTrue(r.submitCursor)

        val s = AskPicker.parse(realSingle) as Screen.Question
        assertEquals(listOf("确认"), s.tabs.map { it.header })
        assertEquals(Screen.None, AskPicker.parse(realIdle))
    }

    @Test
    fun expectedAnswerMatchesTranscriptFormat() {
        val multi = q("请选择模块（可多选）：", "模块", true, "用户模块", "订单模块", "报表模块")
        assertEquals("订单模块, 另加 2 张日志表", AskPicker.expectedAnswer(multi, Reply(setOf(1), "另加 2 张日志表")))
        assertEquals("用户模块, 报表模块", AskPicker.expectedAnswer(multi, Reply(setOf(2, 0))))
        assertEquals("a b", AskPicker.oneLine(" a\n  b \n"))
    }

    // ---------- 模拟 Claude Code 2.1.318 的选择框 ----------

    /** 按实测规律响应按键的选择框；keys 记下发出的所有按键。 */
    private class FakePicker(val qs: List<AskQuestion>) : AskPicker.Terminal {
        var page = 0 // qs.size = Review
        var cursor = 0 // 0-based 行；多选时 m+1 = Next
        val picked = qs.map { mutableSetOf<Int>() }
        val typed = qs.map { StringBuilder() }
        var typing = false
        var closed = false
        val keys = mutableListOf<String>()
        var answers: Map<String, String>? = null
        /** 测试钩子：读屏之前改屏幕（模拟终端里有人同时操作等）。 */
        var tamper: (FakePicker) -> Unit = {}

        private fun m() = qs[page].options.size

        private fun answerOf(i: Int): String {
            val q = qs[i]
            val parts = q.options.indices.filter { it in picked[i] }.map { q.options[it].label }.toMutableList()
            if (m(i) in picked[i] && typed[i].isNotEmpty()) parts += typed[i].toString()
            return parts.joinToString(", ")
        }
        private fun m(i: Int) = qs[i].options.size

        private fun advance() {
            page++; cursor = 0; typing = false
            if (qs.size == 1) submit()
        }
        private fun submit() {
            answers = qs.indices.associate { qs[it].question to answerOf(it) }
            closed = true
        }

        override suspend fun key(k: String) {
            keys += "key:$k"
            if (closed) return
            if (page == qs.size) { if (k == "1") submit(); return }
            val q = qs[page]
            val m = m()
            if (typing && k.length == 1 && k[0].isDigit()) { typed[page].append(k); return }
            when {
                k.length == 1 && k[0].isDigit() -> {
                    val n = k.toInt() - 1
                    if (!q.multiSelect) {
                        if (n < m) { picked[page].clear(); picked[page] += n; advance() }
                        else if (n == m) { cursor = m; typing = true }
                    } else if (n <= m) {
                        if (n in picked[page]) picked[page] -= n else picked[page] += n
                    }
                }
                k == "Down" -> { cursor = (cursor + 1).coerceAtMost(if (q.multiSelect) m + 1 else m); typing = q.multiSelect && cursor == m && m in picked[page] }
                k == "Enter" -> when {
                    !q.multiSelect && cursor == m && typed[page].isNotEmpty() -> { picked[page].clear(); picked[page] += m; advance() }
                    !q.multiSelect -> { picked[page].clear(); picked[page] += cursor; advance() }
                    cursor == m + 1 -> { page++; cursor = 0; typing = false }
                    else -> if (cursor in picked[page]) picked[page] -= cursor else picked[page] += cursor
                }
            }
        }

        override suspend fun text(s: String) {
            keys += "text:$s"
            if (typing) typed[page].append(s)
        }

        override suspend fun read(): String {
            tamper(this)
            if (closed) return "───\n❯\n───\n  ⏵⏵ auto mode on"
            val sb = StringBuilder()
            if (qs.size > 1) sb.append("←  ").append(qs.indices.joinToString("  ") { (if (picked[it].isNotEmpty()) "☒ " else "☐ ") + qs[it].header }).append("  ✔ Submit  →\n")
            else sb.append(" ☐ ${qs[0].header}\n")
            if (page == qs.size) {
                sb.append("Review your answers\n")
                qs.indices.forEach { sb.append(" ● ${qs[it].question}\n   → ${answerOf(it)}\n") }
                sb.append("Ready to submit your answers?\n❯ 1. Submit answers\n  2. Cancel\n")
                return sb.toString()
            }
            val q = qs[page]
            val m = m()
            sb.append(q.question).append('\n')
            q.options.forEachIndexed { i, o ->
                val box = if (q.multiSelect) (if (i in picked[page]) "[✔] " else "[ ] ") else ""
                sb.append(if (cursor == i) "❯ " else "  ").append("${i + 1}. $box${o.label}\n     ${o.description}\n")
            }
            val typeLabel = typed[page].takeIf { it.isNotEmpty() }?.toString() ?: if (q.multiSelect) "Type something" else "Type something."
            val box = if (q.multiSelect) (if (m in picked[page]) "[✔] " else "[ ] ") else ""
            sb.append(if (cursor == m) "❯ " else "  ").append("${m + 1}. $box$typeLabel\n")
            if (q.multiSelect) sb.append(if (cursor == m + 1) "❯    " else "     ").append(if (page == qs.lastIndex) "Submit" else "Next").append('\n')
            sb.append("─────\n  ${m + 2}. Chat about this\nEnter to select · Tab/Arrow keys to navigate · Esc to cancel\n")
            return sb.toString()
        }
    }

    private fun run(p: FakePicker, replies: List<Reply>) = runBlocking { AskPicker.answer(p, p.qs, replies, stepTimeoutMs = 300, pollMs = 1) }

    @Test
    fun answersThreeQuestionsLikeTheSpike() {
        val p = FakePicker(qs3)
        val out = run(p, listOf(Reply(setOf(1)), Reply(text = "先导出备份，\n再删表"), Reply(setOf(0, 2))))
        assertEquals(Outcome.Submitted, out)
        assertEquals(mapOf("请选择权限方案：" to "完全权限", "请选择迁移方式：" to "先导出备份， 再删表", "请选择本次范围（可多选）：" to "前端, 数据库"), p.answers)
        // 和实测一致：单选数字直接跳题；多选勾完用 Down 到 Submit 再 Enter；Review 按 1；从不按 Esc / Tab
        assertEquals(listOf("key:2", "key:3", "text:先导出备份， 再删表", "key:Enter", "key:1", "key:3", "key:Down", "key:Down", "key:Down", "key:Down", "key:Enter", "key:1"), p.keys)
    }

    @Test
    fun multiSelectWithOwnText() {
        val qs = listOf(q("请选择模块（可多选）：", "模块", true, "用户模块", "订单模块", "报表模块"), q("请选择环境：", "环境", false, "测试环境", "生产环境"))
        val p = FakePicker(qs)
        assertEquals(Outcome.Submitted, run(p, listOf(Reply(setOf(1), "另加 2 张日志表"), Reply(setOf(1)))))
        assertEquals(mapOf("请选择模块（可多选）：" to "订单模块, 另加 2 张日志表", "请选择环境：" to "生产环境"), p.answers)
    }

    @Test
    fun singleQuestionSubmitsDirectly() {
        val p = FakePicker(listOf(q("请确认是否继续：", "确认", false, "确认继续", "暂不执行")))
        assertEquals(Outcome.Submitted, run(p, listOf(Reply(setOf(1)))))
        assertEquals(mapOf("请确认是否继续：" to "暂不执行"), p.answers)
        assertEquals(listOf("key:2"), p.keys)
    }

    @Test
    fun refusesWhenTerminalAlreadyTouched() {
        val p = FakePicker(qs3).apply { picked[0] += 0; page = 1 }
        val out = run(p, listOf(Reply(setOf(0)), Reply(setOf(0)), Reply(setOf(0))))
        assertEquals(false, (out as Outcome.Stopped).touched)
        assertTrue(p.keys.isEmpty())
    }

    @Test
    fun refusesDifferentQuestions() {
        val p = FakePicker(listOf(q("别的问题？", "别的", false, "a", "b")))
        val out = runBlocking { AskPicker.answer(p, listOf(q("请确认是否继续：", "确认", false, "a", "b")), listOf(Reply(setOf(0))), 300, 1) }
        assertTrue(out is Outcome.Stopped)
        assertTrue(p.keys.isEmpty())
        // 选项文字不一样也不按
        val p2 = FakePicker(listOf(q("请确认是否继续：", "确认", false, "确认继续", "暂不执行")))
        val out2 = runBlocking { AskPicker.answer(p2, listOf(q("请确认是否继续：", "确认", false, "确认继续", "先不执行")), listOf(Reply(setOf(0))), 300, 1) }
        assertTrue(out2 is Outcome.Stopped)
        assertTrue(p2.keys.isEmpty())
    }

    @Test
    fun noPickerOpen() {
        val p = FakePicker(qs3).apply { closed = true }
        val out = run(p, listOf(Reply(setOf(0)), Reply(setOf(0)), Reply(setOf(0)))) as Outcome.Stopped
        assertFalse(out.touched)
        assertTrue(p.keys.isEmpty())
    }

    @Test
    fun stopsBeforeSubmitWhenReviewDiffers() {
        // 有人在终端里同时改了第 1 题：确认页对不上，绝不按提交
        val p = FakePicker(qs3)
        p.tamper = { if (it.page == 3) { it.picked[0].clear(); it.picked[0] += 0 } }
        val out = run(p, listOf(Reply(setOf(1)), Reply(setOf(0)), Reply(setOf(0)))) as Outcome.Stopped
        assertTrue(out.touched)
        assertEquals(null, p.answers)
        assertFalse(p.keys.last() == "key:1" && p.page == 3)
    }

    @Test
    fun unsureWhenPickerStaysAfterSubmit() {
        val p = FakePicker(listOf(q("请确认是否继续：", "确认", false, "确认继续", "暂不执行")))
        p.tamper = { if (it.closed) it.closed = false } // 提交后屏幕还在
        assertTrue(run(p, listOf(Reply(setOf(0)))) is Outcome.Unsure)
    }

    @Test
    fun replyCompleteness() {
        val single = qs3[0]
        val multi = qs3[2]
        assertTrue(Reply(setOf(0)).isComplete(single))
        assertFalse(Reply(setOf(0), "x").isComplete(single))
        assertFalse(Reply(text = "  ").isComplete(single))
        assertTrue(Reply(text = "x").isComplete(multi))
        assertFalse(Reply().isComplete(multi))
    }
}
