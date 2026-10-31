package dev.herdr.feature.board.conversation

import dev.herdr.core.herdr.ChatMessage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** ToolCall↔ToolResult 关联语义（M2-C1 P0：不做位置猜测）。 */
class ConversationPairingTest {

    private var seq = 0
    private fun msg(
        kind: ChatMessage.Kind,
        text: String,
        toolCallId: String? = null,
        name: String? = null,
        id: String = "m${seq++}",
        parentId: String? = null,
    ) = ChatMessage(kind, text, toolName = name, toolCallId = toolCallId, id = id, parentId = parentId)

    private fun call(id: String?, name: String = "bash", mid: String = "m${seq++}") =
        msg(ChatMessage.Kind.TOOL_CALL, """{"cmd":"ls"}""", toolCallId = id, name = name, id = mid)

    private fun result(id: String?, text: String = "out", mid: String = "m${seq++}") =
        msg(ChatMessage.Kind.TOOL_RESULT, text, toolCallId = id, id = mid)

    @Test
    fun pairsById_evenWhenNotAdjacent() {
        val items = ConversationItemMapper.map(
            listOf(call("a", "bash"), msg(ChatMessage.Kind.ASSISTANT, "中间消息"), call("b", "read"), result("a", "out-a"), result("b", "out-b")),
        )
        val tools = items.filterIsInstance<ConversationItem.Tool>()
        assertEquals(2, tools.size)
        assertEquals("out-a", tools[0].result)
        assertEquals("out-b", tools[1].result)
        assertTrue(tools.all { it.pairedById })
    }

    @Test
    fun unmatchedResultId_isStandalone_notSilentlyPaired() {
        val items = ConversationItemMapper.map(listOf(call("a"), result("zzz", "out")))
        val tools = items.filterIsInstance<ConversationItem.Tool>()
        assertEquals(2, tools.size)
        // 调用没有结果
        assertNull(tools[0].result)
        assertFalse(tools[0].pairedById)
        // 结果独立渲染、不挂到任何调用上
        assertEquals("out", tools[1].result)
        assertEquals("", tools[1].argumentsSummary)
        assertFalse(tools[1].pairedById)
    }

    @Test
    fun resultWithoutAnyId_neverAttachesToNeighbouringCall() {
        val items = ConversationItemMapper.map(listOf(call("a"), result(null, "orphan")))
        val tools = items.filterIsInstance<ConversationItem.Tool>()
        assertEquals(2, tools.size)
        assertNull(tools[0].result)
        assertEquals("orphan", tools[1].result)
        assertFalse(tools[1].pairedById)
    }

    @Test
    fun resultBeforeCall_pairsAtCallPosition() {
        val items = ConversationItemMapper.map(listOf(result("x", "early"), msg(ChatMessage.Kind.USER, "hi"), call("x")))
        val tools = items.filterIsInstance<ConversationItem.Tool>()
        assertEquals(1, tools.size)
        assertEquals("early", tools.single().result)
        assertTrue(tools.single().pairedById)
        // 条目落在调用位置（消息序号 2）
        assertEquals(2, tools.single().index)
    }

    @Test
    fun parallelCalls_keepSeparateResults() {
        val items = ConversationItemMapper.map(listOf(call("1"), call("2"), result("2", "r2"), result("1", "r1")))
        val tools = items.filterIsInstance<ConversationItem.Tool>()
        assertEquals(listOf("r1", "r2"), tools.map { it.result })
    }

    @Test
    fun duplicateResult_usesLatest() {
        val items = ConversationItemMapper.map(listOf(call("d"), result("d", "v1", mid = "r1"), result("d", "v2", mid = "r2")))
        val tools = items.filterIsInstance<ConversationItem.Tool>()
        assertEquals(1, tools.size)
        assertEquals("v2", tools.single().result)
    }

    @Test
    fun duplicateEventId_isDropped() {
        val items = ConversationItemMapper.map(
            listOf(msg(ChatMessage.Kind.USER, "hi", id = "same"), msg(ChatMessage.Kind.USER, "hi", id = "same")),
        )
        assertEquals(1, items.size)
    }

    @Test
    fun errorResult_isFlagged() {
        val items = ConversationItemMapper.map(listOf(call("e"), result("e", "Error: permission denied")))
        assertTrue((items.single() as ConversationItem.Tool).resultError)
    }

    @Test
    fun branchSwitch_doesNotLeakResultsAcrossBranches() {
        val root = msg(ChatMessage.Kind.USER, "root", id = "root")
        val toolA = call("t1", mid = "a1")
        val resultA = result("t1", "out-a", mid = "a2")
        val branchA = listOf(root, toolA, resultA)

        // 新分支：root → b1（toolCall t2，无结果）
        val toolB = msg(ChatMessage.Kind.TOOL_CALL, "{}", toolCallId = "t2", name = "read", id = "b1", parentId = "root")
        val full = branchA + toolB

        val effective = ConversationBranch.effective(full)
        assertEquals(listOf("root", "b1"), effective.map { it.id })

        val items = ConversationItemMapper.map(effective)
        val tools = items.filterIsInstance<ConversationItem.Tool>()
        assertEquals(1, tools.size)
        assertNull(tools.single().result)
    }
}
