package io.github.luxiang0412.kelpie.core.data

import org.junit.Assert.assertEquals
import org.junit.Test

/** 回填起点：小于上限的文件必须从头读（否则会丢掉首条 user 消息，导致对话缺上下文）。 */
class TranscriptTailerStartTest {

    @Test
    fun `small file starts at zero so the first user message survives`() {
        // 真机样本：366_647 字节的 Claude 会话，首条 user 在 offset 671
        assertEquals(0L, TranscriptTailer.initialStart(366_647L))
    }

    @Test
    fun `large file starts at the backfill window`() {
        val size = 5L * 1024 * 1024
        assertEquals(size - TranscriptTailer.DEFAULT_BACKFILL, TranscriptTailer.initialStart(size))
    }

    @Test
    fun `exact window size still starts at zero`() {
        val size = TranscriptTailer.DEFAULT_BACKFILL.toLong()
        assertEquals(0L, TranscriptTailer.initialStart(size))
    }

    private fun reader(bytes: ByteArray): (Long, ByteArray) -> Int = { at, buf ->
        val n = minOf(buf.size.toLong(), bytes.size - at).toInt()
        if (n <= 0) 0 else { System.arraycopy(bytes, at.toInt(), buf, 0, n); n }
    }

    @Test
    fun `line start is found past a very long line`() {
        // 真机所见：回填起点落在一行几百 KB 的工具输出中间；以前只探 8KB，直接跳到文件末尾、对话全空
        val long = ByteArray(300 * 1024) { 'x'.code.toByte() }
        val bytes = long + "\n{\"next\":1}\n".toByteArray()
        assertEquals(long.size + 1L, TranscriptTailer.findLineStart(1000, bytes.size.toLong(), reader(bytes), step = 8 * 1024))
    }

    @Test
    fun `newline right at the offset and no newline at all`() {
        val bytes = "ab\ncd".toByteArray()
        assertEquals(3L, TranscriptTailer.findLineStart(2, bytes.size.toLong(), reader(bytes)))
        assertEquals(bytes.size.toLong(), TranscriptTailer.findLineStart(3, bytes.size.toLong(), reader(bytes)))
    }

    @Test
    fun `gives up after the limit`() {
        val bytes = ByteArray(100) { 'x'.code.toByte() } + "\n".toByteArray()
        assertEquals(bytes.size.toLong(), TranscriptTailer.findLineStart(0, bytes.size.toLong(), reader(bytes), step = 16, limit = 50))
    }
}
