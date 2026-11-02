package dev.herdr.signal.conversation.model

import dev.herdr.core.herdr.ChatMessage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Claude 的真机结构：消息之间夹着 api-request-blob / attachment 等非消息事件，
 * parentUuid 链在消息序列里是断的；重复的 assistant 消息（uuid 不同、tool_use.id 相同）。
 */
class ClaudeLinearRenderTest {

    private fun line(a: String) = a

    private val sample = listOf(
        line("""{"type":"user","uuid":"u1","message":{"role":"user","content":"跑一下 ls"}}"""),
        line("""{"type":"api-request-blob","message":{"content":[{"type":"tool_use","id":"toolu_A","name":"Bash"}]}}"""),
        line("""{"type":"assistant","uuid":"a1","parentUuid":"u1","message":{"role":"assistant","content":[{"type":"tool_use","id":"toolu_A","name":"Bash","input":{"command":"ls"}}]}}"""),
        // Claude 会把增长中的同一条消息再写一遍（uuid 不同，tool_use.id 相同）
        line("""{"type":"assistant","uuid":"a2","parentUuid":"a1","message":{"role":"assistant","content":[{"type":"tool_use","id":"toolu_A","name":"Bash","input":{"command":"ls -la"}}]}}"""),
        line("""{"type":"mode","sessionId":"s1"}"""),
        line("""{"type":"user","uuid":"u2","parentUuid":"a2","message":{"role":"user","content":[{"type":"tool_result","tool_use_id":"toolu_A","content":[{"type":"text","text":"total 0"}]}]}}"""),
        line("""{"type":"assistant","uuid":"a3","parentUuid":"u2","message":{"role":"assistant","content":[{"type":"text","text":"目录是空的"}]}}"""),
    )

    @Test
    fun `重复的 assistant 消息不会丢掉工具卡，且按 toolCallId 配对`() {
        val parsed = dev.herdr.core.herdr.TranscriptParser.parse(
            "/home/x/.claude/projects/-home-x-p/t.jsonl",
            sample,
        )
        val effective = ConversationBranch.effective(parsed)
        assertEquals("分支回溯不应丢弃工具消息", parsed.size, effective.size)
        val items = ConversationResolve.resolve(effective).map { ConversationResolve.toItem(it) }
        val tools = items.filterIsInstance<ConversationItem.Tool>()
        assertEquals(1, tools.size)
        assertTrue("结果必须按 toolCallId 挂到调用上", tools[0].pairedById)
        assertEquals("total 0", tools[0].result)
        // 参数摘要保留原始 input JSON（与真机一致），这里只断言取到的是「最后一次」的内容
        assertTrue(tools[0].argumentsSummary.contains("ls -la"))
    }
}
