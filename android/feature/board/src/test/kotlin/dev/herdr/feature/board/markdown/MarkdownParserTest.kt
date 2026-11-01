package dev.herdr.feature.board.markdown

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MarkdownParserTest {

    @Test fun headings_byLevel() {
        val b = MarkdownParser.parse("# 一级\n\n### 三级标题")
        assertEquals(2, b.size)
        assertEquals(MdBlock.Heading(1, listOf(MdSpan.Text("一级"))), b[0])
        assertEquals(MdBlock.Heading(3, listOf(MdSpan.Text("三级标题"))), b[1])
    }

    @Test fun paragraph_mergesAdjacentLines() {
        val b = MarkdownParser.parse("第一行\n第二行\n\n新段落")
        assertEquals(2, b.size)
        assertEquals(MdBlock.Paragraph(listOf(MdSpan.Text("第一行\n第二行"))), b[0])
    }

    @Test fun bullets_and_ordered() {
        val b = MarkdownParser.parse("- a\n- b\n\n3. x\n4. y")
        assertEquals(MdBlock.Bullets(listOf(listOf(MdSpan.Text("a")), listOf(MdSpan.Text("b")))), b[0])
        assertEquals(MdBlock.Ordered(3, listOf(listOf(MdSpan.Text("x")), listOf(MdSpan.Text("y")))), b[1])
    }

    @Test fun quote_block() {
        val b = MarkdownParser.parse("> 引用内容")
        assertEquals(MdBlock.Quote(listOf(MdSpan.Text("引用内容"))), b.single())
    }

    @Test fun codeFence_keepsContentVerbatim() {
        val src = "```kotlin\nval x = 1\n// **not bold**\n```"
        val block = MarkdownParser.parse(src).single() as MdBlock.CodeFence
        assertEquals("kotlin", block.language)
        assertEquals("val x = 1\n// **not bold**", block.code)
        assertTrue(block.closed)
    }

    @Test fun codeFence_unclosed_isMarked() {
        val block = MarkdownParser.parse("```\nabc").single() as MdBlock.CodeFence
        assertEquals(false, block.closed)
        assertEquals("abc", block.code)
    }

    @Test fun inline_boldItalicCode() {
        val spans = MarkdownParser.parseInline("这是**粗**和*斜*还有`code`")
        assertEquals(
            listOf(
                MdSpan.Text("这是"),
                MdSpan.Bold(listOf(MdSpan.Text("粗"))),
                MdSpan.Text("和"),
                MdSpan.Italic(listOf(MdSpan.Text("斜"))),
                MdSpan.Text("还有"),
                MdSpan.Code("code"),
            ),
            spans,
        )
    }

    @Test fun link_safeAndUnsafe() {
        val safe = MarkdownParser.parseInline("[docs](https://example.com/a)")
        assertEquals(MdSpan.Link("docs", "https://example.com/a", safe = true), safe.single())

        val unsafe = MarkdownParser.parseInline("[x](javascript:alert(1))")
        assertEquals(MdSpan.Link("x", "javascript:alert(1)", safe = false), unsafe.single())

        assertTrue(MarkdownParser.isSafeUrl("http://example.com"))
        assertTrue(!MarkdownParser.isSafeUrl("intent://evil"))
        assertTrue(!MarkdownParser.isSafeUrl("  FILE:///etc/passwd"))
    }

    @Test fun unsafeScheme_renderedButNotClickable() {
        val block = MarkdownParser.parse("[a](file:///etc/passwd)").single() as MdBlock.Paragraph
        val link = block.spans.single() as MdSpan.Link
        assertTrue(!link.safe)
    }

    @Test fun unknownContent_doesNotThrow() {
        val weird = "| a | b |\n|---|---|\n![](img.png)\n<xml/>\n\u0000\n###"
        val b = MarkdownParser.parse(weird)
        assertTrue(b.isNotEmpty())
        assertTrue(MarkdownParser.toPlainText(weird).isNotEmpty())
    }

    @Test fun mixedCjkAndEnglish_plainTextProjection() {
        val src = "# Agent 输出\n\n结果 **已完成** with `git push`\n\n- 中文项\n- English item"
        val plain = MarkdownParser.toPlainText(src)
        assertEquals("Agent 输出\n结果 已完成 with git push\n• 中文项\n• English item", plain)
    }

    @Test fun largeInput_parsesUnderBudget() {
        val src = buildString {
            repeat(400) { i ->
                appendLine("## 段落 $i")
                appendLine("这是第 $i 段内容，包含 **bold** 与 `code` 以及 [link](https://e.com/$i)。")
                appendLine("```kotlin\nfun f$i() = $i\n```")
                appendLine()
            }
        }
        val start = System.nanoTime()
        val blocks = MarkdownParser.parse(src)
        val ms = (System.nanoTime() - start) / 1_000_000
        assertEquals(400 * 3, blocks.size)
        assertTrue("解析 400 段耗时 ${ms}ms", ms < 400)
    }
}
