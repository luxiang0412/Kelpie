package io.github.luxiang0412.kelpie.core.herdr

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Codex rollout 解析。fixture 按 2026-11-03 在 .2 上统计的真实字段形状手写（不含真实内容），
 * 覆盖两代格式：老的 function_call exec_command，和 0.16x 的 custom_tool_call exec（JS 输入）。
 */
class CodexTranscriptParserTest {

    private val lines = listOf(
        """{"timestamp":"2026-11-02T14:48:00.048Z","type":"session_meta","payload":{"session_id":"s1","cwd":"/home/u/repo","originator":"codex-tui","source":"vscode"}}""",
        """{"timestamp":"2026-11-02T14:48:01.000Z","type":"response_item","payload":{"type":"message","role":"developer","content":[{"type":"input_text","text":"You are Codex"}]}}""",
        """{"timestamp":"2026-11-02T14:48:01.000Z","type":"response_item","payload":{"type":"message","role":"user","content":[{"type":"input_text","text":"# AGENTS.md instructions for /home/u/repo\n\n<INSTRUCTIONS>…</INSTRUCTIONS>"}]}}""",
        """{"timestamp":"2026-11-02T14:48:01.000Z","type":"response_item","payload":{"type":"message","role":"user","content":[{"type":"input_text","text":"<environment_context>\n  <cwd>/home/u/repo</cwd>\n</environment_context>"}]}}""",
        """{"timestamp":"2026-11-02T14:48:02.000Z","type":"response_item","payload":{"type":"message","id":"m1","role":"user","content":[{"type":"input_text","text":"跑一下测试"}]}}""",
        """{"timestamp":"2026-11-02T14:48:02.100Z","type":"event_msg","payload":{"type":"user_message","message":"跑一下测试"}}""",
        """{"timestamp":"2026-11-02T14:48:03.000Z","type":"response_item","payload":{"type":"reasoning","id":"rs1","summary":[],"encrypted_content":"gAAA"}}""",
        """{"timestamp":"2026-11-02T14:48:03.500Z","type":"response_item","payload":{"type":"reasoning","id":"rs2","summary":[{"type":"summary_text","text":"先看测试目录"}]}}""",
        """{"timestamp":"2026-11-02T14:48:04.000Z","type":"response_item","payload":{"type":"function_call","name":"exec_command","arguments":"{\"cmd\":\"ls tests\"}","call_id":"c1"}}""",
        """{"timestamp":"2026-11-02T14:48:04.500Z","type":"response_item","payload":{"type":"function_call_output","call_id":"c1","output":"Exit code: 0\nOutput:\na_test.py\n"}}""",
        """{"timestamp":"2026-11-02T14:48:05.000Z","type":"response_item","payload":{"type":"function_call","name":"write_stdin","arguments":"{\"session_id\":1,\"chars\":\"\",\"yield_time_ms\":1000}","call_id":"c2"}}""",
        """{"timestamp":"2026-11-02T14:48:05.500Z","type":"response_item","payload":{"type":"function_call_output","call_id":"c2","output":"still running"}}""",
        """{"timestamp":"2026-11-02T14:48:06.000Z","type":"response_item","payload":{"type":"custom_tool_call","name":"exec","call_id":"c3","input":"text(await tools.exec_command({cmd:\"pytest -q \\\"tests/a b.py\\\"\",\"max_output_tokens\":18000}));\n"}}""",
        """{"timestamp":"2026-11-02T14:48:07.000Z","type":"response_item","payload":{"type":"custom_tool_call_output","call_id":"c3","output":[{"type":"input_text","text":"Script completed"},{"type":"input_text","text":"1 passed"}]}}""",
        """{"timestamp":"2026-11-02T14:48:08.000Z","type":"response_item","payload":{"type":"custom_tool_call","name":"apply_patch","call_id":"c4","input":"*** Begin Patch\n*** Update File: src/app/main.py\n@@\n-a\n+b\n*** Add File: docs/notes.md\n+x\n*** End Patch"}}""",
        """{"timestamp":"2026-11-02T14:48:08.500Z","type":"response_item","payload":{"type":"custom_tool_call_output","call_id":"c4","output":"{\"output\":\"Success. Updated the following files:\\nM src/app/main.py\\n\",\"metadata\":{\"exit_code\":0}}"}}""",
        """{"timestamp":"2026-11-02T14:48:09.000Z","type":"response_item","payload":{"type":"web_search_call","action":{"type":"search","query":"pytest marks"}}}""",
        """{"timestamp":"2026-11-02T14:48:10.000Z","type":"response_item","payload":{"type":"message","id":"m2","role":"assistant","content":[{"type":"output_text","text":"测试全部通过。"}]}}""",
        """{"timestamp":"2026-11-02T14:48:10.100Z","type":"event_msg","payload":{"type":"agent_message","message":"测试全部通过。"}}""",
        """not json""",
    )

    @Test fun parsesConversation() {
        val m = CodexTranscriptParser.parse(lines)
        val kinds = m.map { it.kind }
        assertEquals(
            listOf(
                ChatMessage.Kind.USER, ChatMessage.Kind.THINKING,
                ChatMessage.Kind.TOOL_CALL, ChatMessage.Kind.TOOL_RESULT,
                ChatMessage.Kind.TOOL_CALL, ChatMessage.Kind.TOOL_RESULT,
                ChatMessage.Kind.TOOL_CALL, ChatMessage.Kind.TOOL_RESULT,
                ChatMessage.Kind.TOOL_CALL, ChatMessage.Kind.ASSISTANT,
            ),
            kinds,
        )
        assertEquals("跑一下测试", m[0].text)
        assertEquals("先看测试目录", m[1].text)
        assertEquals("ls tests", m[2].text)
        assertEquals("c1", m[3].toolCallId)
        assertEquals("pytest -q \"tests/a b.py\"", m[4].text)
        assertEquals("exec_command", m[4].toolName)
        assertEquals("Script completed\n1 passed", m[5].text)
        assertTrue(m[6].text, m[6].text.contains("main.py") && m[6].text.contains("notes.md"))
        assertEquals("Success. Updated the following files:\nM src/app/main.py\n", m[7].text)
        assertEquals("pytest marks", m[8].text)
        assertEquals("测试全部通过。", m[9].text)
        assertEquals(java.time.Instant.parse("2026-11-02T14:48:10.000Z").toEpochMilli(), m[9].timestamp)
    }

    @Test fun pollingStdinIsHiddenWithItsOutput() {
        val m = CodexTranscriptParser.parse(lines)
        assertFalse(m.any { it.toolCallId == "c2" })
        assertNull(CodexTranscriptParser.describe("write_stdin", """{"chars":""}"""))
        assertEquals("write_stdin" to "y\n", CodexTranscriptParser.describe("write_stdin", """{"chars":"y\n"}"""))
    }

    @Test fun shellVariants() {
        assertEquals("shell" to "ls -la", CodexTranscriptParser.describe("shell", """{"command":["bash","-lc","ls -la"]}"""))
        assertEquals("shell" to "git status", CodexTranscriptParser.describe("shell", """{"command":["git","status"]}"""))
        assertEquals("shell" to "pwd", CodexTranscriptParser.describe("shell_command", """{"command":"pwd","workdir":"/x"}"""))
        assertEquals("rg foo", CodexTranscriptParser.jsCommand("""const r = await tools.exec_command({"cmd":"rg foo","workdir":"/x"});"""))
        assertNull(CodexTranscriptParser.jsCommand("await tools.view_image({path:\"/a.png\"})"))
    }

    @Test fun injectedContextIsRecognised() {
        assertTrue(CodexTranscriptParser.isInjected("<user_action>\n<context>x</context>\n</user_action>"))
        assertTrue(CodexTranscriptParser.isInjected("<turn_aborted>\nThe user interrupted.\n</turn_aborted>"))
        assertFalse(CodexTranscriptParser.isInjected("<div> 这个标签为什么不渲染"))
        assertFalse(CodexTranscriptParser.isInjected("修一下 <b>粗体</b>"))
    }

    @Test fun routedByPath() {
        assertEquals(TranscriptParser.Kind.CODEX, TranscriptParser.kindOf("/home/u/.codex/sessions/2026/11/02/rollout-2026-11-02T22-48-00-x.jsonl"))
    }
}
