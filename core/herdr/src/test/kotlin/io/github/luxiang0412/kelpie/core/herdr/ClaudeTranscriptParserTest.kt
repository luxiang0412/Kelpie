package io.github.luxiang0412.kelpie.core.herdr

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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

    @Test
    fun askUserQuestion_questionsAndAnswers() {
        // 形状取自 2026-11 真实会话：结构化回答在行级 toolUseResult.answers，tool_result 正文是拼好的一句话
        val q = "K1 怎么控制？"
        val out = ClaudeTranscriptParser.parse(listOf(
            """{"type":"assistant","uuid":"a","message":{"role":"assistant","content":[
                {"type":"tool_use","id":"toolu_q","name":"AskUserQuestion","input":{"questions":[{"question":"$q","header":"权限","multiSelect":false,
                  "options":[{"label":"先按助手绑定控制 (Recommended)","description":"${"很长的说明".repeat(60)}"},{"label":"照计划接入权限包","description":"d"}]}]}}]}}""",
            """{"type":"user","uuid":"u","message":{"role":"user","content":[
                {"type":"tool_result","tool_use_id":"toolu_q","content":"Your questions have been answered: \"$q\"=\"照计划接入权限包\". You can now continue with these answers in mind."}]},
              "toolUseResult":{"questions":[],"answers":{"$q":"照计划接入权限包"},"annotations":{}}}""",
        ))
        val call = out[0]
        assertEquals(q, call.text) // 摘要是问题本身，不是被截断的 JSON
        assertEquals(listOf("先按助手绑定控制 (Recommended)", "照计划接入权限包"), call.questions.single().options.map { it.label })
        assertEquals(mapOf(q to "照计划接入权限包"), out[1].answers)
        assertEquals("toolu_q", out[1].toolCallId)
    }

    @Test
    fun ordinaryToolResultHasNoAnswers() {
        val r = ClaudeTranscriptParser.parse(lines).first { it.kind == ChatMessage.Kind.TOOL_RESULT }
        assertNull(r.answers)
        assertEquals("wc -l a.md", ClaudeTranscriptParser.parse(lines).first { it.kind == ChatMessage.Kind.TOOL_CALL }.text)
    }

    @Test
    fun pastedContentTagsAreStripped() {
        val raw = "\n\n<pasted_content id=\"ca3c\">\n用 AskUserQuestion 问我\n问完等我。\n</pasted_content id=\"ca3c\">\n\n"
        assertEquals("用 AskUserQuestion 问我\n问完等我。", ClaudeTranscriptParser.unwrapPasted(raw))
        assertEquals("先看这段：\nabc\n就这些", ClaudeTranscriptParser.unwrapPasted("先看这段：\n<pasted_content id=\"x\">abc</pasted_content id=\"x\">\n就这些"))
        assertEquals("没有标签", ClaudeTranscriptParser.unwrapPasted("没有标签"))
        val line = """{"type":"user","uuid":"u","message":{"role":"user","content":[{"type":"text","text":${kotlinx.serialization.json.JsonPrimitive(raw)}}]}}"""
        assertEquals("用 AskUserQuestion 问我\n问完等我。", ClaudeTranscriptParser.parse(listOf(line)).single().text)
    }

    // ---- 2026-11-08 真机样本（xn-ontology 会话，正文有删减）----

    @Test
    fun `task notification becomes a notice, not a user bubble`() {
        val line = """{"type":"user","uuid":"n1","origin":{"kind":"task-notification","producer":"session-task"},"message":{"role":"user","content":"<task-notification>\n<task-id>bmrll6b1l</task-id>\n<summary>Monitor event: \"PR #480 CI check results\"</summary>\n<event>cli: pass\ndocs: pass</event>\nIf this event is something the user would act on now, send a PushNotification. Routine or benign output doesn't need one.\n</task-notification>"}}"""
        val m = ClaudeTranscriptParser.parse(listOf(line)).single()
        assertEquals(ChatMessage.Kind.NOTICE, m.kind)
        assertTrue(m.text, m.text.contains("Monitor event: \"PR #480 CI check results\""))
        assertTrue(m.text, m.text.contains("cli: pass\ndocs: pass"))
        assertFalse(m.text, m.text.contains("task-id") || m.text.contains("PushNotification"))
    }

    @Test
    fun `agent finished notification keeps only the summary, unescaped`() {
        val xml = "<task-notification>\n<task-id>a1005179794479806</task-id>\n<output-file>/tmp/x.output</output-file>\n<status>completed</status>\n<summary>Agent \"62 site: intelligent-build &amp; co\" finished</summary>\n<note>A task-notification fires each time…</note>\n<result>long report</result>\n</task-notification>"
        assertEquals(t("后台任务 · ", "Background task · ") + "Agent \"62 site: intelligent-build & co\" finished", ClaudeTranscriptParser.taskNotice(xml))
    }

    @Test
    fun `task notification is recognised even without origin`() {
        val line = """{"type":"user","uuid":"n2","message":{"role":"user","content":[{"type":"text","text":"<task-notification>\n<summary>Background command \"sleep\" completed</summary>\n</task-notification>"}]}}"""
        assertEquals(ChatMessage.Kind.NOTICE, ClaudeTranscriptParser.parse(listOf(line)).single().kind)
    }

    @Test
    fun `meta user lines are skipped`() {
        val lines = listOf(
            """{"type":"user","uuid":"m1","isMeta":true,"message":{"role":"user","content":"[Image: original 1080x2400, displayed at 900x2000. Multiply coordinates by 1.20 to map to original image.]"}}""",
            """{"type":"user","uuid":"m2","isMeta":true,"message":{"role":"user","content":[{"type":"text","text":"<local-command-caveat>The command below was run directly</local-command-caveat>"}]}}""",
            """{"type":"user","uuid":"h1","origin":{"kind":"human"},"message":{"role":"user","content":"可以，改吧"}}""",
        )
        val msgs = ClaudeTranscriptParser.parse(lines)
        assertEquals(listOf("可以，改吧"), msgs.map { it.text })
        assertEquals(ChatMessage.Kind.USER, msgs.single().kind)
    }

    @Test
    fun `tool_reference result says which tool was loaded`() {
        val line = """{"type":"user","uuid":"r1","message":{"role":"user","content":[{"type":"tool_result","tool_use_id":"toolu_016JX","content":[{"type":"tool_reference","tool_name":"Monitor"}]}]}}"""
        val m = ClaudeTranscriptParser.parse(listOf(line)).single()
        assertEquals(ChatMessage.Kind.TOOL_RESULT, m.kind)
        assertEquals("toolu_016JX", m.toolCallId)
        assertEquals(t("已加载 ", "Loaded ") + "Monitor", m.text)
    }

    @Test
    fun `empty tool result still closes the card`() {
        val line = """{"type":"user","uuid":"r2","message":{"role":"user","content":[{"type":"tool_result","tool_use_id":"toolu_2","content":""}]}}"""
        val m = ClaudeTranscriptParser.parse(listOf(line)).single()
        assertEquals("toolu_2", m.toolCallId)
        assertEquals(t("（无输出）", "(no output)"), m.text)
    }

    // ---- 2026-11-20：上下文压缩、终端里直接执行的命令（字段形状取自真实会话，内容是编的）----

    @Test
    fun `compaction becomes one notice and the summary is hidden`() {
        val out = ClaudeTranscriptParser.parse(
            listOf(
                """{"type":"assistant","uuid":"a1","message":{"role":"assistant","content":[{"type":"text","text":"做完了"}]}}""",
                """{"parentUuid":null,"logicalParentUuid":"a1","type":"system","subtype":"compact_boundary","content":"Conversation compacted","level":"info","compactMetadata":{"trigger":"auto","preTokens":366773},"timestamp":"2026-11-19T04:28:14.000Z","uuid":"b1"}""",
                """{"parentUuid":"b1","type":"user","isVisibleInTranscriptOnly":true,"isCompactSummary":true,"message":{"role":"user","content":"<artifact-content-authored-by-others/>\nThis session is being continued from a previous conversation.\n\nSummary:\n1. **Primary Request**"},"uuid":"u2"}""",
                """{"type":"system","subtype":"informational","content":"Compacted while idle","uuid":"i1"}""",
            ),
        )
        assertEquals(listOf(ChatMessage.Kind.ASSISTANT, ChatMessage.Kind.NOTICE), out.map { it.kind })
        assertEquals(t("上下文已压缩（自动）", "Context compacted (auto)"), out[1].text)
        assertEquals("b1", out[1].id)
    }

    @Test
    fun `manual compaction says so`() {
        val out = ClaudeTranscriptParser.parse(
            listOf("""{"type":"system","subtype":"compact_boundary","compactMetadata":{"trigger":"manual"},"uuid":"b1"}"""),
        )
        assertEquals(t("上下文已压缩（手动）", "Context compacted (manual)"), out.single().text)
    }

    private fun userLine(uuid: String, content: String, meta: Boolean = false) =
        """{"type":"user","uuid":"$uuid","isMeta":$meta,"message":{"role":"user","content":${kotlinx.serialization.json.JsonPrimitive(content)}}}"""

    @Test
    fun `slash command shows what the user typed, empty output is hidden`() {
        val out = ClaudeTranscriptParser.parse(
            listOf(
                userLine("c0", "<local-command-caveat>The command below was run directly…</local-command-caveat>", meta = true),
                userLine("c1", "<command-name>/exit</command-name>\n            <command-message>exit</command-message>\n            <command-args></command-args>"),
                userLine("c2", "<local-command-stdout>(no content)</local-command-stdout>"),
                userLine("c3", "<command-message>skill-creator</command-message>\n<command-name>/skill-creator</command-name>\n<command-args>写一个部署 skill</command-args>"),
            ),
        )
        assertEquals(listOf(ChatMessage.Kind.USER, ChatMessage.Kind.USER), out.map { it.kind })
        assertEquals("/exit", out[0].text)
        assertEquals("/skill-creator 写一个部署 skill", out[1].text)
    }

    @Test
    fun `slash command output becomes a notice without ansi codes`() {
        val out = ClaudeTranscriptParser.parse(
            listOf(userLine("c2", "<local-command-stdout>\u001B[1mCopied\u001B[22m to clipboard (28 lines)</local-command-stdout>")),
        )
        assertEquals(ChatMessage.Kind.NOTICE, out.single().kind)
        assertEquals(t("命令输出", "Command output") + "\nCopied to clipboard (28 lines)", out.single().text)
    }

    @Test
    fun `bang command is a user bubble and its output a notice`() {
        val out = ClaudeTranscriptParser.parse(
            listOf(
                userLine("b1", "<bash-input>ls -la</bash-input>"),
                userLine("b2", "<bash-stdout>a.txt\nb.txt</bash-stdout><bash-stderr></bash-stderr>"),
                userLine("b3", "<bash-input>true</bash-input>"),
                userLine("b4", "<bash-stdout>(Bash completed with no output)</bash-stdout><bash-stderr></bash-stderr>"),
            ),
        )
        assertEquals(listOf("! ls -la", t("命令输出", "Command output") + "\na.txt\nb.txt", "! true"), out.map { it.text })
    }

    @Test
    fun `pasted xml that merely mentions command tags stays a normal message`() {
        val text = "<note>看看这个</note> 里面有 <command-name>/x</command-name>"
        val out = ClaudeTranscriptParser.parse(listOf(userLine("p1", text)))
        assertEquals(text, out.single().text)
        assertEquals(ChatMessage.Kind.USER, out.single().kind)
    }
}
