package io.github.luxiang0412.kelpie.core.markdown

/**
 * 轻量 Markdown 解析（M2-B 选型 B：自研最小解析器 + Compose 自定义渲染）。
 *
 * **支持的子集**（明确边界，不做完整 CommonMark）：
 *   - ATX 标题 `#`…`######`
 *   - 无序列表 `- ` / `* `、有序列表 `1. `，可以嵌套，可以混用；比上一项的标记缩进更多的行都归这一项
 *     （子列表、续行、代码块）。画三层：第三层以下的子列表并进第三层，不再缩进（手机宽度有限）
 *   - 分隔线 `---` / `***` / `___`
 *   - 引用 `> `：连续的引用行合成一块，空的 `>` 行分段
 *   - 围栏代码块 ```lang
 *   - 行内：`code`、**bold**、*italic*、[text](url)
 *   - 段落（空行分隔）
 *   - GFM 表格（`| a | b |` + `|---|---|`，首行为表头；对齐标记忽略，单元格支持行内语法；
 *     单元格里的 `<br>` 当换行，其他 HTML 照原文显示）
 *
 * 不支持（有意）：图片、HTML、脚注、setext 标题、任务列表、转义序列扩展、引用里的列表。
 * 依据见 docs/markdown-rendering.md（2026-11-20 按真实回复统计后补了嵌套列表等）。
 * 未知语法按普通文本渲染，绝不抛异常。
 */
sealed interface MdBlock {
    data class Heading(val level: Int, val spans: List<MdSpan>) : MdBlock
    data class Paragraph(val spans: List<MdSpan>) : MdBlock
    /** [depth]：列表嵌套层级，0 = 最外层；决定无序列表的标记（• ◦ ▪）。 */
    data class Bullets(val items: List<ListItem>, val depth: Int = 0) : MdBlock
    data class Ordered(val start: Int, val items: List<ListItem>, val depth: Int = 0) : MdBlock
    /** 一段引用：每个元素是一段（空的 `>` 行分段），段内多行用换行连接。 */
    data class Quote(val paragraphs: List<List<MdSpan>>) : MdBlock
    data object Rule : MdBlock
    data class CodeFence(val language: String?, val code: String, val closed: Boolean) : MdBlock
    /** GFM 表格：首行表头 + 至少一列；单元格已做行内解析。 */
    data class Table(val header: List<List<MdSpan>>, val rows: List<List<List<MdSpan>>>) : MdBlock
}

/** 列表的一项：第一块通常是这一项的文字，后面是子列表、续行、代码块。 */
data class ListItem(val blocks: List<MdBlock>)

sealed interface MdSpan {
    data class Text(val text: String) : MdSpan
    data class Bold(val spans: List<MdSpan>) : MdSpan
    data class Italic(val spans: List<MdSpan>) : MdSpan
    data class Code(val text: String) : MdSpan
    data class Link(val text: String, val url: String, val safe: Boolean) : MdSpan
}

object MarkdownParser {

    /** 只允许 http(s) 链接参与点击；其他协议（javascript:, file:, intent: …）降级为纯文本。 */
    fun isSafeUrl(url: String): Boolean {
        val u = url.trim().lowercase()
        return u.startsWith("https://") || u.startsWith("http://")
    }

    fun parse(source: String): List<MdBlock> {
        val lines = source.replace("\r\n", "\n").replace('\r', '\n').split('\n').map(::expandLeadingTabs)
        return parseBlocks(lines, depth = 0)
    }

    /** 行首的 tab 按 4 个空格算（缩进判断只看空格）。 */
    private fun expandLeadingTabs(line: String): String {
        if (!line.startsWith('\t') && !line.startsWith(' ')) return line
        val lead = line.takeWhile { it == ' ' || it == '\t' }
        return if ('\t' !in lead) line else lead.replace("\t", "    ") + line.substring(lead.length)
    }

    private fun indentOf(line: String): Int = line.length - line.trimStart(' ').length

    /** [depth]：这一层里出现的列表算第几层（列表项的内容按 depth + 1 解析）。 */
    private fun parseBlocks(lines: List<String>, depth: Int): List<MdBlock> {
        val blocks = ArrayList<MdBlock>()
        val paragraph = ArrayList<String>()
        var i = 0

        fun flushParagraph() {
            if (paragraph.isNotEmpty()) {
                blocks += MdBlock.Paragraph(parseInline(paragraph.joinToString("\n")))
                paragraph.clear()
            }
        }

        while (i < lines.size) {
            val raw = lines[i]
            val line = raw.trimEnd()
            val trimmed = line.trimStart()

            // 围栏代码块
            if (trimmed.startsWith("```")) {
                flushParagraph()
                val lang = trimmed.removePrefix("```").trim().takeIf { it.isNotEmpty() }
                val code = StringBuilder()
                var closed = false
                i++
                while (i < lines.size) {
                    val cur = lines[i]
                    if (cur.trimStart().startsWith("```")) {
                        closed = true; i++
                        break
                    }
                    if (code.isNotEmpty()) code.append('\n')
                    code.append(cur)
                    i++
                }
                blocks += MdBlock.CodeFence(lang, code.toString(), closed)
                continue
            }

            when {
                trimmed.isEmpty() -> { flushParagraph(); i++ }

                Regex("^#{1,6}\\s+").containsMatchIn(trimmed) -> {
                    flushParagraph()
                    val level = trimmed.takeWhile { it == '#' }.length.coerceIn(1, 6)
                    val text = trimmed.drop(level).trimStart()
                    blocks += MdBlock.Heading(level, parseInline(text))
                    i++
                }

                // 必须在列表之前判断：`- - -`、`* * *` 也是分隔线
                RULE.matches(trimmed) -> { flushParagraph(); blocks += MdBlock.Rule; i++ }

                isQuoteLine(trimmed) -> {
                    flushParagraph()
                    val paragraphs = ArrayList<List<MdSpan>>()
                    val cur = ArrayList<String>()
                    fun endParagraph() {
                        if (cur.isNotEmpty()) { paragraphs += parseInline(cur.joinToString("\n")); cur.clear() }
                    }
                    while (i < lines.size && isQuoteLine(lines[i].trim())) {
                        val text = lines[i].trim().removePrefix(">").trimStart()
                        if (text.isEmpty()) endParagraph() else cur += text
                        i++
                    }
                    endParagraph()
                    if (paragraphs.isNotEmpty()) blocks += MdBlock.Quote(paragraphs)
                }

                marker(line) != null -> {
                    flushParagraph()
                    val (list, next) = parseList(lines, i, depth)
                    blocks += list
                    i = next
                }

                isTableStart(lines, i) -> {
                    flushParagraph()
                    val header = splitRow(lines[i])
                    i += 2 // 跳过表头与分隔行
                    val rows = ArrayList<List<List<MdSpan>>>()
                    while (i < lines.size && lines[i].contains('|') && lines[i].isNotBlank()) {
                        val cells = splitRow(lines[i])
                        // 补齐/裁剪到表头列数，避免错列
                        rows += List(header.size) { idx -> cells.getOrNull(idx).orEmpty() }
                        i++
                    }
                    blocks += MdBlock.Table(header, rows)
                }

                else -> { paragraph += line; i++ }
            }
        }
        flushParagraph()
        return blocks
    }

    private val RULE = Regex("""^(?:(?:-\s*){3,}|(?:\*\s*){3,}|(?:_\s*){3,})$""")

    private fun isQuoteLine(trimmed: String) = trimmed.startsWith("> ") || trimmed == ">"

    /** 列表标记行。[contentCol]：这一项文字开始的列，续行缩进到这里就去掉这么多空格。 */
    private class Marker(val indent: Int, val ordered: Boolean, val number: Int, val contentCol: Int, val content: String)

    private val ORDERED = Regex("""^(\d{1,3})\.(\s+)""")

    private fun marker(line: String): Marker? {
        val indent = indentOf(line)
        val t = line.substring(indent)
        if (t.startsWith("- ") || t.startsWith("* ")) {
            val rest = t.substring(1)
            val gap = rest.length - rest.trimStart().length
            return Marker(indent, ordered = false, number = 0, contentCol = indent + 1 + gap.coerceIn(1, 4), content = rest.trimStart())
        }
        val m = ORDERED.find(t) ?: return null
        val gap = m.groupValues[2].length.coerceIn(1, 4)
        return Marker(indent, ordered = true, number = m.groupValues[1].toInt(), contentCol = indent + m.groupValues[1].length + 1 + gap, content = t.substring(m.value.length))
    }

    /**
     * 从 [start] 起解析一个列表，返回 (列表块, 下一行)。
     *  - 同一种标记、缩进不超过第一项的行 → 下一项；
     *  - 缩进比这一项的标记多的行 → 归这一项（去掉最多 contentCol 个空格后递归解析）；
     *    围栏代码块里的行不看缩进，直到围栏结束；
     *  - 空行：后面第一个非空行缩进更多 → 空行归这一项；否则列表结束（空行隔开的下一项另起一个列表，和以前一样）。
     */
    private fun parseList(lines: List<String>, start: Int, depth: Int): Pair<MdBlock, Int> {
        val first = marker(lines[start].trimEnd())!!
        val items = ArrayList<ListItem>()
        var i = start
        var ended = false
        while (i < lines.size && !ended) {
            val m = marker(lines[i].trimEnd())
            if (m == null || m.ordered != first.ordered || m.indent > first.indent) break
            val body = ArrayList<String>()
            body += m.content
            i++
            var inFence = false
            while (i < lines.size) {
                val line = lines[i].trimEnd()
                val ind = indentOf(line)
                if (inFence) {
                    body += line.drop(minOf(ind, m.contentCol))
                    if (line.trimStart().startsWith("```")) inFence = false
                    i++
                    continue
                }
                if (line.isEmpty()) {
                    var j = i + 1
                    while (j < lines.size && lines[j].isBlank()) j++
                    if (j < lines.size && indentOf(lines[j]) > m.indent) { body += ""; i++; continue }
                    ended = true
                    break
                }
                if (ind <= m.indent) break
                val dedented = line.drop(minOf(ind, m.contentCol))
                if (dedented.trimStart().startsWith("```")) inFence = true
                body += dedented
                i++
            }
            val blocks = parseBlocks(body, depth + 1)
            if (depth < MAX_LIST_DEPTH) {
                items += ListItem(blocks)
            } else {
                // 第三层：子列表不再缩进，并到这一层后面
                items += ListItem(blocks.filter { it !is MdBlock.Bullets && it !is MdBlock.Ordered })
                blocks.forEach { b ->
                    when (b) {
                        is MdBlock.Bullets -> items += b.items
                        is MdBlock.Ordered -> items += b.items
                        else -> Unit
                    }
                }
            }
        }
        val block = if (first.ordered) MdBlock.Ordered(first.number, items, depth) else MdBlock.Bullets(items, depth)
        return block to i
    }

    /** 画几层缩进：0、1、2 三层。 */
    private const val MAX_LIST_DEPTH = 2

    /** `|` 分隔行：允许 `|` / `:` 对齐标记，单元格只允许 `-` 与 `:`。 */
    private val tableSeparator = Regex("""^\s*\|?\s*:?-+:?\s*(\|\s*:?-+:?\s*)*\|?\s*$""")

    private fun isTableStart(lines: List<String>, i: Int): Boolean {
        if (i + 1 >= lines.size) return false
        val head = lines[i]
        if (!head.contains('|') || head.isBlank()) return false
        val sep = lines[i + 1]
        if (!sep.contains('|')) return false
        return tableSeparator.matches(sep.trim())
    }

    /** 拆单元格：去掉首尾竖线后按 `|` 切分，逐格做行内解析。 */
    private fun splitRow(raw: String): List<List<MdSpan>> {
        var t = raw.trim()
        if (t.startsWith("|")) t = t.substring(1)
        if (t.endsWith("|")) t = t.dropLast(1)
        return t.split('|').map { breakLines(parseInline(it.trim())) }
    }

    private val BR = Regex("""<br\s*/?>""", RegexOption.IGNORE_CASE)

    /** 表格单元格没法直接换行，Claude 会写 `<br>`：文字里的当换行，行内代码里的不动。 */
    private fun breakLines(spans: List<MdSpan>): List<MdSpan> = spans.map { s ->
        when (s) {
            is MdSpan.Text -> if (s.text.contains('<')) MdSpan.Text(BR.replace(s.text, "\n")) else s
            is MdSpan.Bold -> MdSpan.Bold(breakLines(s.spans))
            is MdSpan.Italic -> MdSpan.Italic(breakLines(s.spans))
            else -> s
        }
    }


    /** 行内解析：单层强调，避免递归带来的复杂度和性能风险。 */
    fun parseInline(text: String): List<MdSpan> {
        val out = ArrayList<MdSpan>()
        val buf = StringBuilder()
        var i = 0

        fun flush() {
            if (buf.isNotEmpty()) { out += MdSpan.Text(buf.toString()); buf.setLength(0) }
        }

        while (i < text.length) {
            val c = text[i]
            when {
                c == '`' -> {
                    val end = text.indexOf('`', i + 1)
                    if (end > i + 1) {
                        flush(); out += MdSpan.Code(text.substring(i + 1, end)); i = end + 1
                    } else { buf.append(c); i++ }
                }
                c == '*' && i + 1 < text.length && text[i + 1] == '*' -> {
                    val end = text.indexOf("**", i + 2)
                    if (end > i + 2) {
                        flush(); out += MdSpan.Bold(parseInline(text.substring(i + 2, end))); i = end + 2
                    } else { buf.append(c); i++ }
                }
                c == '*' -> {
                    val end = text.indexOf('*', i + 1)
                    if (end > i + 1) {
                        flush(); out += MdSpan.Italic(parseInline(text.substring(i + 1, end))); i = end + 1
                    } else { buf.append(c); i++ }
                }
                c == '[' -> {
                    val close = text.indexOf(']', i + 1)
                    val open = if (close > 0) text.indexOf('(', close) else -1
                    val end = if (open > 0) matchingParen(text, open) else -1
                    if (close > i + 1 && open == close + 1 && end > open) {
                        val label = text.substring(i + 1, close)
                        val url = text.substring(open + 1, end).trim()
                        flush()
                        out += MdSpan.Link(label, url, isSafeUrl(url))
                        i = end + 1
                    } else { buf.append(c); i++ }
                }
                else -> { buf.append(c); i++ }
            }
        }
        flush()
        return out
    }

    /** 从 open 位置的 '(' 找到配对的 ')'，支持 URL 内含括号（如 wiki 链接）。 */
    private fun matchingParen(text: String, open: Int): Int {
        var depth = 0
        var i = open
        while (i < text.length) {
            when (text[i]) {
                '(' -> depth++
                ')' -> {
                    depth--
                    if (depth == 0) return i
                }
            }
            i++
        }
        return -1
    }

    /** 纯文本投影（无障碍朗读、通知摘要用；不含 Markdown 标记）。 */
    fun toPlainText(source: String): String = plainBlocks(parse(source), indent = "")

    private fun plainBlocks(blocks: List<MdBlock>, indent: String): String = blocks.joinToString("\n") { b ->
        when (b) {
            is MdBlock.Heading -> plain(b.spans)
            is MdBlock.Paragraph -> plain(b.spans)
            is MdBlock.Quote -> b.paragraphs.joinToString("\n") { plain(it) }
            is MdBlock.Bullets -> b.items.joinToString("\n") { indent + "• " + plainBlocks(it.blocks, "$indent  ") }
            is MdBlock.Ordered -> b.items.mapIndexed { i, it -> indent + "${b.start + i}. " + plainBlocks(it.blocks, "$indent  ") }.joinToString("\n")
            is MdBlock.CodeFence -> b.code
            MdBlock.Rule -> ""
            // 表格投影成 `a | b` 的行文本（无障碍朗读/摘要用，不丢内容）
            is MdBlock.Table -> buildString {
                append(b.header.joinToString(" | ") { plain(it) })
                b.rows.forEach { row ->
                    append(0x0A.toChar())
                    append(row.joinToString(" | ") { plain(it) })
                }
            }
        }
    }

    private fun plain(spans: List<MdSpan>): String = spans.joinToString("") { s ->
        when (s) {
            is MdSpan.Text -> s.text
            is MdSpan.Bold -> plain(s.spans)
            is MdSpan.Italic -> plain(s.spans)
            is MdSpan.Code -> s.text
            is MdSpan.Link -> s.text
        }
    }
}
