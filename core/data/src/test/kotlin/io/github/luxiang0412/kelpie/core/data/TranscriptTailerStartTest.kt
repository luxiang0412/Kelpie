package io.github.luxiang0412.kelpie.core.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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
    fun `line end is found past a very long line`() {
        // 真机所见：回填起点落在一行几百 KB 的工具输出中间；以前只探 8KB，直接跳到文件末尾、对话全空
        val long = ByteArray(300 * 1024) { 'x'.code.toByte() }
        val bytes = long + "\n{\"next\":1}\n".toByteArray()
        assertEquals(long.size + 1L, TranscriptTailer.findLineEnd(1000, bytes.size.toLong(), reader(bytes), step = 8 * 1024))
    }

    @Test
    fun `newline as the last byte is distinguished from no newline`() {
        val bytes = "ab\ncd".toByteArray()
        assertEquals(3L, TranscriptTailer.findLineEnd(2, bytes.size.toLong(), reader(bytes)))
        assertNull(TranscriptTailer.findLineEnd(3, bytes.size.toLong(), reader(bytes)))
        val done = "ab\ncd\n".toByteArray()
        assertEquals(done.size.toLong(), TranscriptTailer.findLineEnd(3, done.size.toLong(), reader(done)))
    }

    @Test
    fun `line end has no search limit`() {
        // 真机样本：711MB 的 Claude 会话里有两行 2.19MB，比单次读取窗口（2MB）还长
        val bytes = ByteArray(3 * 1024 * 1024) { 'x'.code.toByte() } + "\n".toByteArray()
        assertEquals(bytes.size.toLong(), TranscriptTailer.findLineEnd(0, bytes.size.toLong(), reader(bytes)))
    }

    private val mb = 1024L * 1024

    @Test
    fun `no cursor backfills the tail`() {
        assertEquals(TranscriptTailer.Plan(700 * mb - TranscriptTailer.DEFAULT_BACKFILL, reset = true),
            TranscriptTailer.plan(null, null, "h", 700 * mb))
    }

    @Test
    fun `small lag continues from the cursor`() {
        assertEquals(TranscriptTailer.Plan(695 * mb, reset = false),
            TranscriptTailer.plan(695 * mb, "h", "h", 700 * mb))
    }

    @Test
    fun `far behind jumps to the tail instead of crawling 2MB at a time`() {
        // 真机所见：游标停在 288MB、文件 711MB，每 5 秒追 2MB，打开对话十几分钟都看不到最新消息
        assertEquals(TranscriptTailer.Plan(711 * mb - TranscriptTailer.DEFAULT_BACKFILL, reset = true),
            TranscriptTailer.plan(288 * mb, "h", "h", 711 * mb))
    }

    @Test
    fun `replaced or truncated file backfills the tail, not from byte zero`() {
        val tail = TranscriptTailer.Plan(700 * mb - TranscriptTailer.DEFAULT_BACKFILL, reset = true)
        assertEquals(tail, TranscriptTailer.plan(699 * mb, "old", "new", 700 * mb))
        assertEquals(tail, TranscriptTailer.plan(800 * mb, "h", "h", 700 * mb))
    }

    @Test
    fun `replaced small file is reread from the start`() {
        assertEquals(TranscriptTailer.Plan(0, reset = true), TranscriptTailer.plan(5000, "old", "new", 6000))
    }

    @Test
    fun `oversize line from a Claude tool result attaches to its tool card`() {
        val head = """{"parentUuid":"a","type":"user","message":{"role":"user","content":[{"tool_use_id":"toolu_01ABC","type":"tool_result","content":"xxxx"""
        val m = TranscriptTailer.oversizeNotice(head, 18L * 1024 * 1024)
        assertEquals(io.github.luxiang0412.kelpie.core.herdr.ChatMessage.Kind.TOOL_RESULT, m.kind)
        assertEquals("toolu_01ABC", m.toolCallId)
        assertTrue(m.text, m.text.contains("18.0"))
    }

    @Test
    fun `oversize line from Codex uses call_id`() {
        val head = """{"type":"response_item","payload":{"type":"function_call_output","call_id":"call_9","output":"yyy"""
        assertEquals("call_9", TranscriptTailer.oversizeNotice(head, 17L * 1024 * 1024).toolCallId)
    }

    @Test
    fun `oversize line without a tool id becomes a notice`() {
        val m = TranscriptTailer.oversizeNotice("""{"type":"user","message":{"content":[{"type":"image","source":{"data":"iVBOR""", 20L * 1024 * 1024)
        assertEquals(io.github.luxiang0412.kelpie.core.herdr.ChatMessage.Kind.NOTICE, m.kind)
        assertNull(m.toolCallId)
    }
}
