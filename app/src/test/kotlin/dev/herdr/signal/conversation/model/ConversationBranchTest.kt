package dev.herdr.signal.conversation.model

import dev.herdr.core.herdr.ChatMessage
import org.junit.Assert.assertEquals
import org.junit.Test

class ConversationBranchTest {

    private fun m(id: String, parent: String?, text: String = id) =
        ChatMessage(ChatMessage.Kind.ASSISTANT, text, id = id, parentId = parent)

    @Test
    fun linearChain_returnsAll() {
        val msgs = listOf(m("a", null), m("b", "a"), m("c", "b"))
        assertEquals(listOf("a", "b", "c"), ConversationBranch.effective(msgs).map { it.id })
    }

    @Test
    fun branchesOnlyLastLeaf() {
        val msgs = listOf(m("a", null), m("b", "a"), m("c", "b"), m("d", "a"))
        assertEquals(listOf("a", "d"), ConversationBranch.effective(msgs).map { it.id })
    }

    @Test
    fun messagesWithoutIds_stayLinear() {
        val msgs = listOf(
            ChatMessage(ChatMessage.Kind.USER, "1"),
            ChatMessage(ChatMessage.Kind.ASSISTANT, "2"),
        )
        assertEquals(2, ConversationBranch.effective(msgs).size)
    }

    @Test
    fun danglingParent_stopsWithoutThrowing() {
        val msgs = listOf(m("a", null), m("b", "missing"), m("c", "b"))
        assertEquals(listOf("b", "c"), ConversationBranch.effective(msgs).map { it.id })
    }

    @Test
    fun cycle_doesNotLoopForever() {
        val msgs = listOf(m("a", "c"), m("b", "a"), m("c", "b"))
        val out = ConversationBranch.effective(msgs)
        assertEquals(true, out.size <= 3)
    }
}
