package io.github.luxiang0412.kelpie.core.markdown

/**
 * 轻量 Markdown 解析（M2-B 选型 B：自研最小解析器 + Compose 自定义渲染）。
 *
 * **支持的子集**（明确边界，不做完整 CommonMark）：
 *   - ATX 标题 `#`…`######`
 *   - 无序列表 `- ` / `* `（单层）
 *   - 有序列表 `1. `（单层）
 *   - 引用 `> `
 *   - 围栏代码块 ```lang
 *   - 行内：`code`、**bold**、*italic*、[text](url)
 *   - 段落（空行分隔）
 *   - GFM 表格（`| a | b |` + `|---|---|`，首行为表头；对齐标记忽略，单元格支持行内语法）
 *
 * 不支持（有意）：嵌套列表、图片、HTML、脚注、setext 标题、转义序列扩展。
 * 未知语法按普通文本渲染，绝不抛异常。
 */
sealed interface MdBlock {
    data class Heading(val level: Int, val spans: List<MdSpan>) : MdBlock
    data class Paragraph(val spans: List<MdSpan>) : MdBlock
    data class Bullets(val items: List<List<MdSpan>>) : MdBlock
    data class Ordered(val start: Int, val items: List<List<MdSpan>>) : MdBlock
    data class Quote(val spans: List<MdSpan>) : MdBlock
    data class CodeFence(val language: String?, val code: String, val closed: Boolean) : MdBlock
    /** GFM 表格：首行表头 + 至少一列；单元格已做行内解析。 */
    data class Table(val header: List<List<MdSpan>>, val rows: List<List<List<MdSpan>>>) : MdBlock
}

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
        val lines = source.replace("\r\n", "\n").replace('\r', '\n').split('\n')
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

                trimmed.startsWith("> ") || trimmed == ">" -> {
                    flushParagraph()
                    val text = trimmed.removePrefix(">").trimStart()
                    blocks += MdBlock.Quote(parseInline(text))
                    i++
                }

                isBullet(trimmed) -> {
                    flushParagraph()
                    val items = ArrayList<List<MdSpan>>()
                    while (i < lines.size && isBullet(lines[i].trimStart())) {
                        items += parseInline(lines[i].trimStart().substring(1).trimStart())
                        i++
                    }
                    blocks += MdBlock.Bullets(items)
                }

                isOrdered(trimmed) -> {
                    flushParagraph()
                    val items = ArrayList<List<MdSpan>>()
                    var start = orderedNumber(trimmed) ?: 1
                    while (i < lines.size && isOrdered(lines[i].trimStart())) {
                        val t = lines[i].trimStart()
                        if (items.isEmpty()) start = orderedNumber(t) ?: 1
                        items += parseInline(t.substringAfter('.').trimStart())
                        i++
                    }
                    blocks += MdBlock.Ordered(start, items)
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
        return t.split('|').map { parseInline(it.trim()) }
    }

    private fun isBullet(t: String) = t.startsWith("- ") || t.startsWith("* ")
    private fun isOrdered(t: String) = Regex("^\\d{1,3}\\.\\s+").containsMatchIn(t)
    private fun orderedNumber(t: String) = t.substringBefore('.').toIntOrNull()

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
    fun toPlainText(source: String): String = parse(source).joinToString("\n") { b ->
        when (b) {
            is MdBlock.Heading -> plain(b.spans)
            is MdBlock.Paragraph -> plain(b.spans)
            is MdBlock.Quote -> plain(b.spans)
            is MdBlock.Bullets -> b.items.joinToString("\n") { "• " + plain(it) }
            is MdBlock.Ordered -> b.items.mapIndexed { i, it -> "${b.start + i}. " + plain(it) }.joinToString("\n")
            is MdBlock.CodeFence -> b.code
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
