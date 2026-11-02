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
}
