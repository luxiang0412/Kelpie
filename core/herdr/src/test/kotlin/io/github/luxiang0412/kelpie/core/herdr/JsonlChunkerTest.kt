package io.github.luxiang0412.kelpie.core.herdr

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class JsonlChunkerTest {

    private fun s(vararg lines: String) = lines.joinToString("") { it + "\n" }

    @Test
    fun consumesOnlyCompleteLines() {
        val bytes = "a\nb\npartial".toByteArray()
        val r = JsonlChunker.consume(0, bytes)
        assertEquals(listOf("a", "b"), r.lines)
        assertEquals("a\nb\n".length.toLong(), r.nextOffset)
        assertEquals("partial".length, r.pendingBytes)
    }

    @Test
    fun noNewlineMeansNothingConsumed() {
        val r = JsonlChunker.consume(100, "abc".toByteArray())
        assertTrue(r.lines.isEmpty())
        assertEquals(100, r.nextOffset)
        assertEquals(3, r.pendingBytes)
    }

    @Test
    fun utf8_multibyte_isNeverSplit() {
        val full = s("你好", "世界")
        val bytes = full.toByteArray(Charsets.UTF_8)
        // 切在“你”字的第 2 个字节处 —— 此时还没有换行，应全部留待下次
        val cut = "你".toByteArray(Charsets.UTF_8).size - 1
        val first = JsonlChunker.consume(0, bytes, 0, cut)
        assertTrue(first.lines.isEmpty())
        assertEquals(0, first.nextOffset)

        // 下一次从 0 重读整段（因为上次没消费），得到两行完整中文
        val second = JsonlChunker.consume(0, bytes)
        assertEquals(listOf("你好", "世界"), second.lines)
        assertEquals(bytes.size.toLong(), second.nextOffset)
    }

    @Test
    fun crlf_isTrimmedByParserNotChunker() {
        val bytes = "x\r\ny\r\n".toByteArray()
        val r = JsonlChunker.consume(0, bytes)
        assertEquals(listOf("x\r", "y\r"), r.lines)
        assertEquals(bytes.size.toLong(), r.nextOffset)
    }

    @Test
    fun canStartFromOffsetAndSkipPartialTail() {
        val all = s("one", "two").toByteArray()
        val head = JsonlChunker.consume(0, all, 0, "one\n".length)
        assertEquals(listOf("one"), head.lines)

        // 模拟第二次读：从 head.nextOffset 开始，只有半行
        val half = "thr".toByteArray()
        val r2 = JsonlChunker.consume(head.nextOffset, half)
        assertTrue(r2.lines.isEmpty())
        assertEquals(head.nextOffset, r2.nextOffset)

        // 补齐后，从 head.nextOffset 重新读整行
        val rest = "three\n".toByteArray()
        val r3 = JsonlChunker.consume(head.nextOffset, rest)
        assertEquals(listOf("three"), r3.lines)
        assertEquals(head.nextOffset + rest.size, r3.nextOffset)
    }

    @Test
    fun blankLinesAreDropped() {
        val r = JsonlChunker.consume(0, "\n\n\n".toByteArray())
        assertTrue(r.lines.isEmpty())
        assertEquals(3L, r.nextOffset)
    }
}
