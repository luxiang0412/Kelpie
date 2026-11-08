package io.github.luxiang0412.kelpie.core.data

import androidx.room.Room
import io.github.luxiang0412.kelpie.core.herdr.ChatMessage
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** 本地对话缓存：裁剪、过期清理、“更早的消息没有加载”标记随游标保存。 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class TranscriptStoreTest {
    private val db = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), BoardDatabase::class.java)
        .allowMainThreadQueries().build()
    private val store = TranscriptStore(db.transcriptDao())

    @After fun close() = db.close()

    private fun msgs(from: Int, n: Int) = (from until from + n).map { ChatMessage(ChatMessage.Kind.ASSISTANT, "m$it") }

    @Test
    fun `trim waits for the slack, then keeps the most recent`() = runBlocking {
        store.append("h", "p", msgs(0, 25))
        assertFalse(store.trim("h", "p", keep = 10, slack = 20))
        store.append("h", "p", msgs(25, 10))
        assertTrue(store.trim("h", "p", keep = 10, slack = 20))
        val left = store.observe("h", "p").first()
        assertEquals((25 until 35).map { "m$it" }, left.map { it.text })
        // 裁完继续追加，seq 接着往后排
        store.append("h", "p", msgs(35, 1))
        assertEquals("m35", store.observe("h", "p").first().last().text)
    }

    @Test
    fun `trim only touches its own conversation`() = runBlocking {
        store.append("h", "p", msgs(0, 40))
        store.append("h", "other", msgs(0, 40))
        store.trim("h", "p", keep = 10, slack = 5)
        assertEquals(10, store.observe("h", "p").first().size)
        assertEquals(40, store.observe("h", "other").first().size)
    }

    @Test
    fun `truncated flag is saved with the cursor`() = runBlocking {
        store.saveCursor("h", "p", 100, "hash", truncated = true)
        assertTrue(store.cursor("h", "p")!!.truncated)
        store.saveCursor("h", "p", 200, "hash", truncated = false)
        assertFalse(store.cursor("h", "p")!!.truncated)
    }

    @Test
    fun `stale conversations are pruned, recent ones kept`() = runBlocking {
        val now = 100L * 24 * 60 * 60 * 1000
        store.append("h", "old", msgs(0, 3))
        store.append("h", "new", msgs(0, 3))
        db.transcriptDao().saveCursor(TranscriptCursorEntity("h", "old", 1, null, now - TranscriptStore.STALE_AFTER_MS - 1))
        db.transcriptDao().saveCursor(TranscriptCursorEntity("h", "new", 1, null, now - 1000))
        assertEquals(1, store.pruneStale(nowMs = now))
        assertNull(store.cursor("h", "old"))
        assertTrue(store.observe("h", "old").first().isEmpty())
        assertNotNull(store.cursor("h", "new"))
        assertEquals(3, store.observe("h", "new").first().size)
    }

    @Test
    fun `notice kind survives the round trip`() = runBlocking {
        store.append("h", "p", listOf(ChatMessage(ChatMessage.Kind.NOTICE, "too large")))
        assertEquals(ChatMessage.Kind.NOTICE, store.observe("h", "p").first().single().kind)
    }
}
