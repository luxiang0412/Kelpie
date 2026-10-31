package dev.herdr.feature.board.conversation

import dev.herdr.core.herdr.ChatMessage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ConversationItemMapperTest {

    private fun call(id: String?, name: String = "bash") =
        ChatMessage(ChatMessage.Kind.TOOL_CALL, """{"cmd":"ls"}""", toolName = name, toolCallId = id)

    private fun result(id: String?, text: String = "file1\nfile2") =
        ChatMessage(ChatMessage.Kind.TOOL_RESULT, text, toolCallId = id)

    @Test
    fun pairsById_evenWhenNotAdjacent() {
        val items = ConversationItemMapper.map(
            listOf(
                call("a", "bash"),
                ChatMessage(ChatMessage.Kind.ASSISTANT, "我继续看看"),
                call("b", "read"),
                result("a", "out-a"),
                result("b", "out-b"),
            ),
        )
        val tools = items.filterIsInstance<ConversationItem.Tool>()
        assertEquals(2, tools.size)
        assertEquals("out-a", tools[0].result)
        assertEquals("out-b", tools[1].result)
        assertTrue(tools.all { it.pairedById })
    }

    @Test
    fun idMismatch_doesNotFalselyClaimPrecisePairing() {
        val items = ConversationItemMapper.map(listOf(call("a"), result("zzz", "out")))
        val tool = items.single() as ConversationItem.Tool
        assertEquals("out", tool.result)
        assertFalse(tool.pairedById)
    }

    @Test
    fun errorResult_isFlagged() {
        val items = ConversationItemMapper.map(
            listOf(call("e"), result("e", "Error: permission denied\nmore lines")),
        )
        assertTrue((items.single() as ConversationItem.Tool).resultError)
    }

    @Test
    fun multipleResults_doNotMergeIntoOneCall() {
        val items = ConversationItemMapper.map(
            listOf(call("1"), call("2"), result("1", "r1"), result("2", "r2")),
        )
        val tools = items.filterIsInstance<ConversationItem.Tool>()
        assertEquals(listOf("r1", "r2"), tools.map { it.result })
    }

    @Test
    fun untrackedResult_stillRenders() {
        val items = ConversationItemMapper.map(listOf(result(null, "orphan output")))
        val tool = items.single() as ConversationItem.Tool
        assertEquals("orphan output", tool.result)
        assertEquals("tool", tool.name)
    }

    @Test
    fun normalMessages_keepOrderAndIndex() {
        val items = ConversationItemMapper.map(
            listOf(
                ChatMessage(ChatMessage.Kind.USER, "hi"),
                ChatMessage(ChatMessage.Kind.THINKING, "想一想"),
                ChatMessage(ChatMessage.Kind.ASSISTANT, "你好"),
            ),
        )
        assertEquals(listOf(0, 1, 2), items.map { it.index })
    }

    @Test
    fun largeConversation_mapsWithoutQuadraticBlowup() {
        val msgs = ArrayList<ChatMessage>()
        repeat(2000) { i ->
            msgs += call("c$i")
            msgs += result("c$i", "out $i")
        }
        val start = System.nanoTime()
        val items = ConversationItemMapper.map(msgs)
        val ms = (System.nanoTime() - start) / 1_000_000
        assertEquals(2000, items.size)
        assertTrue("map 2000 组耗时 ${ms}ms", ms < 1500)
    }
}
