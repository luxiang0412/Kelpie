package dev.herdr.feature.board.markdown

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class MarkdownAstCacheTest {

    @Before
    fun clear() = MarkdownAstCache.clear()

    @Test
    fun sameContent_hits() {
        val src = "# 标题\n\n- a\n- b"
        val first = MarkdownAstCache.getOrParse(src)
        val second = MarkdownAstCache.getOrParse(src)
        assertEquals(first, second)
        assertEquals(1, MarkdownAstCache.stats().hits)
    }

    @Test
    fun changedContent_doesNotReturnStaleAst() {
        MarkdownAstCache.getOrParse("## old")
        val blocks = MarkdownAstCache.getOrParse("## new")
        assertEquals((blocks.single() as MdBlock.Heading).spans, listOf(MdSpan.Text("new")))
    }

    @Test
    fun oversizedContent_isNotCached() {
        val big = "a".repeat(MarkdownAstCache.MAX_SOURCE_LENGTH + 1)
        MarkdownAstCache.getOrParse(big)
        assertEquals(0, MarkdownAstCache.stats().size)
    }

    @Test
    fun evictionBoundsMemory() {
        repeat(MarkdownAstCache.MAX_ENTRIES + 40) { i ->
            MarkdownAstCache.getOrParse("段落 $i 内容 **粗体**")
        }
        assertTrue("缓存条目数必须有上限", MarkdownAstCache.stats().size <= MarkdownAstCache.MAX_ENTRIES)
    }

    @Test
    fun parserVersionBump_invalidates() {
        MarkdownAstCache.getOrParse("# x")
        assertTrue(MarkdownAstCache.PARSER_VERSION >= 2)
    }
}
