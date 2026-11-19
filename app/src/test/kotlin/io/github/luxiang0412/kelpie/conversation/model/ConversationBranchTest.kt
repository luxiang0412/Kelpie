package io.github.luxiang0412.kelpie.conversation.model

import io.github.luxiang0412.kelpie.core.herdr.ChatMessage
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
    fun parentThatIsNotAMessage_continuesWithPreviousMessage() {
        // "x" 是被解析器跳过的 model_change / context_edit：接着回溯列表里的前一条，不再丢掉 "a"
        val msgs = listOf(m("a", null), m("b", "x"), m("c", "b"))
        assertEquals(listOf("a", "b", "c"), ConversationBranch.effective(msgs).map { it.id })
    }

    @Test
    fun allPartsOfAnEventAreKept() {
        // 一条 assistant 记录：思考 + 文字 + 工具调用，共用 id "b"
        val msgs = listOf(
            m("a", null, "问"),
            m("b", "a", "思考"),
            m("b", "a", "文字"),
            m("b", "a", "调用"),
            m("c", "b", "结果"),
        )
        assertEquals(listOf("问", "思考", "文字", "调用", "结果"), ConversationBranch.effective(msgs).map { it.text })
    }

    @Test
    fun piSessionAcrossModelChangeAndCompaction_keepsEverything() {
        val lines = listOf(
            """{"type":"session","id":"s","parentId":null}""",
            """{"type":"message","id":"u1","parentId":"s","message":{"role":"user","content":[{"type":"text","text":"开始"}]}}""",
            """{"type":"message","id":"a1","parentId":"u1","message":{"role":"assistant","content":[{"type":"thinking","thinking":"想一下"},{"type":"text","text":"先看看"},{"type":"toolCall","id":"t1","name":"bash","arguments":{"command":"ls"}}]}}""",
            """{"type":"message","id":"r1","parentId":"a1","message":{"role":"toolResult","toolCallId":"t1","content":[{"type":"text","text":"a.txt"}]}}""",
            """{"type":"model_change","id":"mc","parentId":"r1","modelId":"m2"}""",
            """{"type":"thinking_level_change","id":"tl","parentId":"mc","thinkingLevel":"high"}""",
            """{"type":"message","id":"u2","parentId":"tl","message":{"role":"user","content":[{"type":"text","text":"继续"}]}}""",
            """{"type":"compaction","id":"cp","parentId":"u2","summary":"## Goal\n…","firstKeptEntryId":"u2"}""",
            """{"type":"message","id":"a2","parentId":"cp","message":{"role":"assistant","content":[{"type":"text","text":"好了"}]}}""",
        )
        val out = ConversationBranch.effective(io.github.luxiang0412.kelpie.core.herdr.PiTranscriptParser.parse(lines))
        assertEquals(
            listOf("开始", "想一下", "先看看", "ls", "a.txt", "继续", io.github.luxiang0412.kelpie.core.herdr.t("上下文已压缩", "Context compacted"), "好了"),
            out.map { it.text },
        )
    }

    @Test
    fun abandonedBranchIsStillDropped() {
        // 回到 "a" 之后另起分支："b" 所在的旧分支不显示
        val msgs = listOf(m("a", null), m("b", "a"), m("c", "a"))
        assertEquals(listOf("a", "c"), ConversationBranch.effective(msgs).map { it.id })
    }

    @Test
    fun cycle_doesNotLoopForever() {
        val msgs = listOf(m("a", "c"), m("b", "a"), m("c", "b"))
        val out = ConversationBranch.effective(msgs)
        assertEquals(true, out.size <= 3)
    }
}
