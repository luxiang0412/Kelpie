package dev.herdr.feature.board.markdown

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** GFM 表格（真机 Claude 输出里最常见的格式缺口，I-99）。 */
class MarkdownTableTest {

    private fun cells(row: List<List<MdSpan>>) = row.map { spans ->
        spans.joinToString("") { (it as? MdSpan.Text)?.text ?: "" }
    }

    @Test
    fun `基本两列表格：首行表头，其余为数据行`() {
        val src = """
            | 部分 | 内容 |
            | --- | --- |
            | 调研 | 895 行 |
            | 记录 | DECISIONS.md |
        """.trimIndent()
        val block = MarkdownParser.parse(src).single()
        assertTrue(block is MdBlock.Table)
        block as MdBlock.Table
        assertEquals(listOf("部分", "内容"), cells(block.header))
        assertEquals(2, block.rows.size)
        assertEquals(listOf("调研", "895 行"), cells(block.rows[0]))
        assertEquals(listOf("记录", "DECISIONS.md"), cells(block.rows[1]))
    }

    @Test
    fun `对齐标记与无首尾竖线都能识别`() {
        val src = """
            名称 | 数量
            :---|---:
            a | 1
        """.trimIndent()
        val block = MarkdownParser.parse(src).single() as MdBlock.Table
        assertEquals(listOf("名称", "数量"), cells(block.header))
        assertEquals(listOf("a", "1"), cells(block.rows[0]))
    }

    @Test
    fun `单元格支持行内代码与加粗`() {
        val src = """
            | 文件 | 说明 |
            | --- | --- |
            | `Hands.md` | **重要** |
        """.trimIndent()
        val block = MarkdownParser.parse(src).single() as MdBlock.Table
        val first = block.rows[0][0]
        assertTrue(first.any { it is MdSpan.Code })
        val second = block.rows[0][1]
        assertTrue(second.any { it is MdSpan.Bold })
    }

    @Test
    fun `列数不齐时按表头裁剪或补空，不抛异常`() {
        val src = """
            | a | b | c |
            | --- | --- | --- |
            | 1 |
            | 1 | 2 | 3 | 4 |
        """.trimIndent()
        val block = MarkdownParser.parse(src).single() as MdBlock.Table
        assertEquals(3, block.rows[0].size)
        assertEquals(3, block.rows[1].size)
    }

    @Test
    fun `只有竖线没有分隔行时仍是普通段落`() {
        val src = "用 a | b 表示或，这一行不是表格"
        val blocks = MarkdownParser.parse(src)
        assertTrue(blocks.single() is MdBlock.Paragraph)
    }

    @Test
    fun `纯文本投影保留表格内容`() {
        val src = """
            | 名称 | 数量 |
            | --- | --- |
            | a | 1 |
        """.trimIndent()
        val text = MarkdownParser.toPlainText(src)
        assertTrue(text.contains("名称"))
        assertTrue(text.contains("a | 1"))
    }
}
