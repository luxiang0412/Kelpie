package io.github.luxiang0412.kelpie.core.data.runtime

import io.github.luxiang0412.kelpie.core.herdr.AskQuestion
import io.github.luxiang0412.kelpie.core.herdr.t
import kotlinx.coroutines.delay

/**
 * 在手机上回答 Claude Code 的 AskUserQuestion：替用户在终端选择框里按键，每一步读屏核对。
 *
 * 按键规律（Claude Code 2.1.318 实测，见 docs/history/run-log.md 2026-11-05）：
 * - 单选：按数字 = 选中并跳到下一题；Type something（编号 = 选项数 + 1）进入输入，打字后 Enter。
 * - 多选：数字只切换勾选、光标不动；自己写的要先勾上再把光标移到那一行打字；光标到 Next / Submit 行按 Enter 换题。
 *   Tab 的效果随光标位置变，不用。
 * - 多题最后有 Review 页（逐题列出回答），按 1 提交；只有 1 题时没有 Review，选完就提交。
 *
 * 原则：任何一步屏幕和预期对不上就停下，不盲按，不按 Esc（Esc 会取消整个提问）。
 * 多题时 Review 页逐题核对通过才提交；没提交之前终端里的部分选择对 Claude 没有影响。
 */
object AskPicker {

    /** 一道题的回答：选中的选项下标（单选只有一个）+ 自己写的文字（Type something）。 */
    data class Reply(val picked: Set<Int> = emptySet(), val text: String? = null) {
        val cleanText: String? get() = text?.let(::oneLine)?.takeIf { it.isNotEmpty() }
        fun isComplete(q: AskQuestion): Boolean =
            if (q.multiSelect) picked.isNotEmpty() || cleanText != null
            else (picked.size == 1) != (cleanText != null)
    }

    /** Claude 记下的回答文本：选项标签按选项顺序，自己写的在最后，用 ", " 连接（和 transcript 一致）。 */
    fun expectedAnswer(q: AskQuestion, r: Reply): String =
        (q.options.indices.filter { it in r.picked }.map { q.options[it].label } + listOfNotNull(r.cleanText)).joinToString(", ")

    /** 换行在输入框里会被当成 Enter，提前换成空格。 */
    fun oneLine(s: String): String = s.replace(Regex("""\s*[\r\n]+\s*"""), " ").trim()

    // ---------- 读屏 ----------

    data class Tab(val header: String, val done: Boolean)
    data class Row(val number: Int, val label: String, val checked: Boolean?, val cursor: Boolean)

    sealed interface Screen {
        /** 一道题：tabs 为空表示只有一道题且没有标签行。nextCursor = 光标停在 Next / Submit 行（多选）。 */
        data class Question(val tabs: List<Tab>, val title: String, val rows: List<Row>, val nextCursor: Boolean) : Screen {
            val cursorRow: Int? get() = rows.firstOrNull { it.cursor }?.number
        }
        data class Review(val pairs: List<Pair<String, String>>, val submitCursor: Boolean) : Screen
        data object None : Screen
    }

    private val TAB_BAR = Regex("""^\s*(?:←\s+)?[☐☒]\s""")
    private val TAB = Regex("""([☐☒])\s+(.+?)(?=\s{2,}[☐☒✔→]|\s*$)""")
    private val ROW = Regex("""^\s*(❯)?\s*(\d+)\.\s+(?:\[([ ✔])]\s+)?(.*?)\s*$""")
    private val NEXT = Regex("""^\s*(❯)?\s+(Next|Submit)\s*$""")
    private val FOOTER = Regex("""^\s*Enter to select""")

    fun parse(screen: String): Screen {
        val lines = screen.lines().map { it.trimEnd() }
        val bar = lines.indexOfLast { TAB_BAR.containsMatchIn(it) }
        val reviewAt = lines.indexOfLast { it.trim() == "Review your answers" }
        if (reviewAt > bar) return parseReview(lines.drop(reviewAt + 1))
        if (bar < 0) return Screen.None
        val tabs = TAB.findAll(lines[bar]).map { Tab(it.groupValues[2].trim(), it.groupValues[1] == "☒") }.toList()
        val rest = lines.drop(bar + 1)
        val footer = rest.indexOfFirst { FOOTER.containsMatchIn(it) }
        if (footer < 0) return Screen.None // 选择框已经关掉（只剩历史输出）或还没画完
        val body = rest.take(footer)
        val firstRow = body.indexOfFirst { ROW.matches(it) }
        if (firstRow < 0) return Screen.None
        val title = body.take(firstRow).filter { it.isNotBlank() && !it.trimStart().startsWith("─") }.joinToString("\n") { it.trim() }
        val rows = body.drop(firstRow).mapNotNull { l ->
            ROW.matchEntire(l)?.let { m ->
                Row(m.groupValues[2].toInt(), m.groupValues[4], m.groups[3]?.value?.let { it == "✔" }, m.groupValues[1].isNotEmpty())
            }
        }
        val nextCursor = body.any { NEXT.matchEntire(it)?.groupValues?.get(1)?.isNotEmpty() == true }
        return Screen.Question(tabs, title, rows, nextCursor)
    }

    private fun parseReview(lines: List<String>): Screen {
        val pairs = mutableListOf<Pair<String, StringBuilder>>()
        var inAnswer = false
        var submitCursor = false
        for (l in lines) {
            val s = l.trim()
            when {
                s.startsWith("● ") -> { pairs += s.removePrefix("● ").trim() to StringBuilder(); inAnswer = false }
                s.startsWith("→ ") && pairs.isNotEmpty() -> { pairs.last().second.append(s.removePrefix("→ ").trim()); inAnswer = true }
                s.startsWith("Ready to submit") -> inAnswer = false
                ROW.matches(l) -> {
                    val m = ROW.matchEntire(l)!!
                    if (m.groupValues[4].startsWith("Submit")) submitCursor = m.groupValues[1].isNotEmpty()
                }
                s.isNotEmpty() && inAnswer -> pairs.last().second.append(s) // 折行的回答
            }
        }
        if (lines.none { ROW.matchEntire(it)?.groupValues?.get(4)?.startsWith("Submit") == true }) return Screen.None
        return Screen.Review(pairs.map { it.first to it.second.toString() }, submitCursor)
    }

    /** 比较时去掉所有空白：终端折行、对齐空格都不算差异。 */
    fun same(a: String, b: String): Boolean = squash(a) == squash(b)
    private fun squash(s: String) = s.filterNot { it.isWhitespace() }

    // ---------- 按键 ----------

    interface Terminal {
        suspend fun read(): String
        suspend fun key(k: String)
        suspend fun text(s: String)
    }

    sealed interface Outcome {
        /** 提交后选择框关掉了。最终以 transcript 里出现 answers 为准。 */
        data object Submitted : Outcome
        /** 停下了，没有提交。touched = 终端里已经选了一部分。 */
        data class Stopped(val reason: String, val touched: Boolean) : Outcome
        /** 提交键已经按出去，但没看到选择框关掉。 */
        data class Unsure(val reason: String) : Outcome
    }

    private class Stop(val reason: String) : Exception(reason)

    suspend fun answer(
        term: Terminal,
        questions: List<AskQuestion>,
        replies: List<Reply>,
        stepTimeoutMs: Long = 4_000,
        pollMs: Long = 200,
    ): Outcome {
        require(questions.size == replies.size)
        questions.forEachIndexed { i, q -> require(replies[i].isComplete(q)) { "reply $i incomplete" } }
        var touched = false
        val n = questions.size

        suspend fun await(what: String, pred: (Screen) -> Boolean): Screen {
            val end = System.currentTimeMillis() + stepTimeoutMs
            var last: Screen
            do {
                last = parse(term.read())
                if (pred(last)) return last
                delay(pollMs)
            } while (System.currentTimeMillis() < end)
            throw Stop(what)
        }

        fun matches(s: Screen, q: AskQuestion): Boolean =
            s is Screen.Question && same(s.title, q.question) &&
                q.options.indices.all { i -> s.rows.getOrNull(i)?.let { it.number == i + 1 && same(it.label, q.options[i].label) } == true }

        try {
            // 0. 起点：选择框开着、停在第 1 题、和手机上看到的是同一组题、终端里还没人动过
            val first = parse(term.read())
            if (first !is Screen.Question) {
                return Outcome.Stopped(if (first is Screen.Review) t("终端里已经答到确认页了", "The terminal is already on the review page") else t("终端里没有看到这组选择题", "The question picker isn't open in the terminal"), false)
            }
            if (n > 1 && (first.tabs.size != n || questions.indices.any { i -> questions[i].header?.let { !same(it, first.tabs[i].header) } == true })) {
                return Outcome.Stopped(t("终端里的题目和手机上看到的不一样", "The terminal shows different questions"), false)
            }
            if (first.tabs.any { it.done }) return Outcome.Stopped(t("终端里已经有人答了一部分", "Someone already started answering in the terminal"), false)
            if (!matches(first, questions[0])) return Outcome.Stopped(t("终端里的题目和手机上看到的不一样", "The terminal shows different questions"), false)

            for (i in 0 until n) {
                val q = questions[i]
                val r = replies[i]
                val m = q.options.size
                val typeRow = m + 1
                val no = i + 1
                await(t("没有停在第 $no 题", "Didn't reach question $no")) { matches(it, q) }
                val text = r.cleanText
                if (!q.multiSelect) {
                    if (text == null) {
                        term.key("${r.picked.single() + 1}"); touched = true
                    } else {
                        term.key("$typeRow"); touched = true
                        await(t("第 $no 题没进入自己写答案", "Question $no: couldn't start typing")) { (it as? Screen.Question)?.cursorRow == typeRow }
                        term.text(text)
                        await(t("第 $no 题的文字没写进去", "Question $no: text didn't appear")) { s -> (s as? Screen.Question)?.rows?.firstOrNull { it.number == typeRow }?.let { same(it.label, text) } == true }
                        term.key("Enter")
                    }
                } else {
                    for (k in r.picked.sorted()) { term.key("${k + 1}"); touched = true }
                    if (text != null) {
                        term.key("$typeRow"); touched = true
                        // 光标移到 Type something 那一行才能打字（按数字只会勾选）
                        var s = await(t("第 $no 题没勾上", "Question $no: option didn't toggle")) { (it as? Screen.Question)?.rows?.firstOrNull { r2 -> r2.number == typeRow }?.checked == true }
                        var guard = 0
                        while ((s as Screen.Question).cursorRow != typeRow) {
                            if (++guard > m + 2) throw Stop(t("第 $no 题光标移不到自己写答案那一行", "Question $no: couldn't reach the text row"))
                            val before = s.cursorRow
                            term.key("Down")
                            s = await(t("第 $no 题光标没动", "Question $no: cursor didn't move")) { (it as? Screen.Question)?.let { q2 -> q2.cursorRow != before || q2.nextCursor } == true }
                        }
                        term.text(text)
                        await(t("第 $no 题的文字没写进去", "Question $no: text didn't appear")) { s2 -> (s2 as? Screen.Question)?.rows?.firstOrNull { it.number == typeRow }?.let { same(it.label, text) } == true }
                    }
                    // 勾选状态和手机上选的一致，再去 Next / Submit
                    val want = (0 until m).map { it in r.picked } + (text != null)
                    var s = await(t("第 $no 题的勾选和手机上选的不一致", "Question $no: checkboxes don't match")) { s2 ->
                        (s2 as? Screen.Question)?.let { q2 -> want.indices.all { k -> q2.rows.firstOrNull { it.number == k + 1 }?.checked == want[k] } } == true
                    } as Screen.Question
                    var guard = 0
                    while (!s.nextCursor) {
                        if (++guard > m + 3) throw Stop(t("第 $no 题光标移不到下一步", "Question $no: couldn't reach Next"))
                        val before = s.cursorRow
                        term.key("Down")
                        s = await(t("第 $no 题光标没动", "Question $no: cursor didn't move")) { (it as? Screen.Question)?.let { q2 -> q2.cursorRow != before || q2.nextCursor } == true } as Screen.Question
                    }
                    term.key("Enter")
                }
            }

            if (n > 1) {
                val rv = await(t("没有进入确认页", "Didn't reach the review page")) { it is Screen.Review } as Screen.Review
                val ok = rv.pairs.size == n && questions.indices.all { i ->
                    same(rv.pairs[i].first, questions[i].question) && same(rv.pairs[i].second, expectedAnswer(questions[i], replies[i]))
                }
                if (!ok) throw Stop(t("确认页上的回答和手机上选的不一致", "The review page doesn't match your answers"))
                if (!rv.submitCursor) throw Stop(t("确认页光标不在提交上", "The review cursor isn't on Submit"))
                term.key("1")
            }
        } catch (e: Stop) {
            return Outcome.Stopped(e.reason, touched)
        }
        // 提交键已经按出去：之后的任何意外都只能算“不确定”
        return try {
            await(t("提交后选择框没有关掉", "The picker didn't close after submitting")) { it is Screen.None }
            Outcome.Submitted
        } catch (e: Stop) {
            Outcome.Unsure(e.reason)
        }
    }
}
