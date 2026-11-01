package dev.herdr.core.herdr

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Claude Code transcript 解析（依据 2026-11-01 真实 JSONL 的字段形状构造 fixture）。
 * 关键差异：tool_result 挂在 user 角色下、用 tool_use_id 关联、大量非消息事件需忽略。
 */
class ClaudeTranscriptParserTest {

    private val lines = listOf(
        """{"type":"mode","mode":"normal","sessionId":"s1"}""",
        """{"type":"api-request","uuid":"r1","sessionId":"s1"}""",
        """{"type":"user","uuid":"u1","parentUuid":null,"timestamp":"2026-11-01T07:37:10.681Z",
            "message":{"role":"user","content":"帮我看看构建"}}""",
        """{"type":"assistant","uuid":"a1","parentUuid":"u1","timestamp":"2026-11-01T07:37:12.000Z",
            "message":{"role":"assistant","content":[
              {"type":"thinking","thinking":"先确认构建命令"},
              {"type":"text","text":"我跑一下测试。"},
              {"type":"tool_use","id":"toolu_1","name":"Bash","input":{"command":"wc -l a.md"}}
            ]}}""",
        """{"type":"user","uuid":"u2","parentUuid":"a1","timestamp":"2026-11-01T07:37:14.000Z",
            "message":{"role":"user","content":[
              {"type":"tool_result","tool_use_id":"toolu_1","content":"195 a.md"}
            ]}}""",
        """{"type":"assistant","uuid":"a2","parentUuid":"u2","isSidechain":true,
            "message":{"role":"assistant","content":[{"type":"text","text":"子代理内容不应出现"}]}}""",
        """{"type":"assistant","uuid":"a3","parentUuid":"u2",
            "message":{"role":"assistant","content":[{"type":"tool_addition","x":1}]}}""",
    )

    @Test
    fun parsesCoreKinds_andSkipsNoise() {
        val msgs = ClaudeTranscriptParser.parse(lines)
        assertEquals(
            listOf(
                ChatMessage.Kind.USER,      // 帮我看看构建
                ChatMessage.Kind.THINKING,  // 先确认构建命令
                ChatMessage.Kind.ASSISTANT, // 我跑一下测试。
                ChatMessage.Kind.TOOL_CALL, // Bash
                ChatMessage.Kind.TOOL_RESULT, // 195 a.md
            ),
            msgs.map { it.kind },
        )
        assertTrue(msgs.none { it.text.contains("子代理内容") })
        assertTrue(msgs.none { it.text.contains("api-request") })
    }

    @Test
    fun toolResult_linksByToolUseId() {
        val msgs = ClaudeTranscriptParser.parse(lines)
        val call = msgs.first { it.kind == ChatMessage.Kind.TOOL_CALL }
        val result = msgs.first { it.kind == ChatMessage.Kind.TOOL_RESULT }
        assertEquals("Bash", call.toolName)
        assertEquals("toolu_1", call.toolCallId)
        assertEquals("toolu_1", result.toolCallId)
    }

    @Test
    fun keepsBranchIdAndTimestamp() {
        val msgs = ClaudeTranscriptParser.parse(lines)
        val assistant = msgs.first { it.kind == ChatMessage.Kind.ASSISTANT }
        assertEquals("a1", assistant.id)
        // 故意不输出 parentId：Claude 的 parentUuid 指向被跳过的非消息事件，
        // 交给 ConversationBranch 回溯会误删断点前的历史（真机已验证）
        assertNull(assistant.parentId)
        assertTrue((assistant.timestamp ?: 0) > 0)
    }

    @Test
    fun toolResultContentArrayIsJoined() {
        val msg = """{"type":"user","uuid":"u","message":{"role":"user","content":[
            {"type":"tool_result","tool_use_id":"t","content":[{"type":"text","text":"line1"},{"type":"text","text":"line2"}]}]}}"""
        val r = ClaudeTranscriptParser.parse(listOf(msg)).single()
        assertEquals("line1\nline2", r.text)
    }

    @Test
    fun unknownLinesDoNotThrow() {
        val weird = listOf("not json", "{}", """{"type":"assistant"}""", """{"type":"user","message":{}}""")
        assertTrue(ClaudeTranscriptParser.parse(weird).isEmpty())
    }

    @Test
    fun dispatcher_picksParserByPath() {
        assertEquals(TranscriptParser.Kind.CLAUDE, TranscriptParser.kindOf("/home/u/.claude/projects/-x/s.jsonl"))
        assertEquals(TranscriptParser.Kind.PI, TranscriptParser.kindOf("/home/u/.pi/agent/sessions/x/s.jsonl"))
        assertEquals(TranscriptParser.Kind.PI, TranscriptParser.kindOf(null))
        // 同一条 claude 行交给 dispatcher 也能解析出消息
        val out = TranscriptParser.parse("/home/u/.claude/projects/-x/s.jsonl", lines)
        assertEquals(5, out.size)
    }
}
