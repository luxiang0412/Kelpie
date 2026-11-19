package io.github.luxiang0412.kelpie.core.markdown

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MarkdownParserTest {

    private fun item(text: String, vararg more: MdBlock) =
        ListItem(listOf(MdBlock.Paragraph(listOf(MdSpan.Text(text)))) + more)

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
        assertEquals(MdBlock.Bullets(listOf(item("a"), item("b"))), b[0])
        assertEquals(MdBlock.Ordered(3, listOf(item("x"), item("y"))), b[1])
    }

    @Test fun quote_block() {
        val b = MarkdownParser.parse("> 引用内容")
        assertEquals(MdBlock.Quote(listOf(listOf(MdSpan.Text("引用内容")))), b.single())
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

    // ---- 2026-11-20：按画板补的写法（例子照真实回复的写法编的）----

    @Test fun nestedBullets_twoSpaces() {
        val b = MarkdownParser.parse("- **测试**：通过\n- 新改动：\n  - 小结列了两个工具\n  - 日志没有报错\n- 回退：31 秒")
        val list = b.single() as MdBlock.Bullets
        assertEquals(3, list.items.size)
        assertEquals(MdBlock.Bullets(listOf(item("小结列了两个工具"), item("日志没有报错")), depth = 1), list.items[1].blocks[1])
    }

    @Test fun bulletsUnderOrdered_withTwoOrThreeSpaces() {
        for (pad in listOf("  ", "   ")) {
            val list = MarkdownParser.parse("1. 第一步\n${pad}- 细节 a\n${pad}- 细节 b\n2. 第二步").single() as MdBlock.Ordered
            assertEquals(2, list.items.size)
            assertEquals(MdBlock.Bullets(listOf(item("细节 a"), item("细节 b")), depth = 1), list.items[0].blocks[1])
        }
    }

    @Test fun deeperThanThreeLevels_mergesIntoThird() {
        val src = "- a\n  - b\n    - c\n      - d\n    - e"
        val l0 = MarkdownParser.parse(src).single() as MdBlock.Bullets
        val l1 = l0.items[0].blocks[1] as MdBlock.Bullets
        val l2 = l1.items[0].blocks[1] as MdBlock.Bullets
        assertEquals(2, l2.depth)
        assertEquals(listOf(item("c"), item("d"), item("e")), l2.items)
    }

    @Test fun continuationAndCodeFence_belongToTheItem() {
        val src = "1. 先搜请求 ID：\n   ```\n   docker logs api | grep req\n\n   sort -u\n   ```\n2. 再搜一遍。\n   注意：只保留 7 天。\n\n后面的段落"
        val b = MarkdownParser.parse(src)
        assertEquals(2, b.size)
        val list = b[0] as MdBlock.Ordered
        assertEquals(2, list.items.size)
        assertEquals(MdBlock.CodeFence(null, "docker logs api | grep req\n\nsort -u", true), list.items[0].blocks[1])
        assertEquals(MdBlock.Paragraph(listOf(MdSpan.Text("再搜一遍。\n注意：只保留 7 天。"))), list.items[1].blocks.single())
        assertEquals(MdBlock.Paragraph(listOf(MdSpan.Text("后面的段落"))), b[1])
    }

    @Test fun blankLineThenSibling_startsANewListLikeBefore() {
        val b = MarkdownParser.parse("1. a\n\n2. b")
        assertEquals(listOf(MdBlock.Ordered(1, listOf(item("a"))), MdBlock.Ordered(2, listOf(item("b")))), b)
    }

    @Test fun rules() {
        for (r in listOf("---", "***", "___", "- - -", "* * *")) {
            assertEquals(r, listOf(MdBlock.Paragraph(listOf(MdSpan.Text("上"))), MdBlock.Rule, MdBlock.Paragraph(listOf(MdSpan.Text("下")))), MarkdownParser.parse("上\n\n$r\n\n下"))
        }
        assertTrue(MarkdownParser.parse("--").single() is MdBlock.Paragraph)
    }

    @Test fun multiLineQuote_isOneBlock_emptyLineSplitsParagraphs() {
        val b = MarkdownParser.parse("> 离开电脑后？\n>\n> 我做了个 App。\n> · 按谁在等你排序")
        assertEquals(
            MdBlock.Quote(listOf(listOf(MdSpan.Text("离开电脑后？")), listOf(MdSpan.Text("我做了个 App。\n· 按谁在等你排序")))),
            b.single(),
        )
    }

    @Test fun nestedList_plainText() {
        assertEquals("• a\n  • b\n• c", MarkdownParser.toPlainText("- a\n  - b\n- c"))
    }
}
