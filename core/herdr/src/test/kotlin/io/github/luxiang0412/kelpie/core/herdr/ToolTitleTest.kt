package io.github.luxiang0412.kelpie.core.herdr

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** 工具卡片的标题：Claude Bash 的 description；没有时显示命令，去掉指向会话目录的 cd。 */
class ToolTitleTest {

    @Test
    fun `claude bash call carries its description as title`() {
        val line = """{"type":"assistant","uuid":"a1","message":{"role":"assistant","content":[{"type":"tool_use","id":"toolu_1","name":"Bash","input":{"command":"cd /home/luxiang/project/work/xn-ontology && gh pr checks 480","description":"Check PR #480 CI status"}}]}}"""
        val m = ClaudeTranscriptParser.parse(listOf(line)).single()
        assertEquals("Check PR #480 CI status", m.title)
        assertEquals("cd /home/luxiang/project/work/xn-ontology && gh pr checks 480", m.text)
    }

    @Test
    fun `other tools and blank descriptions have no title`() {
        val read = """{"type":"assistant","uuid":"a2","message":{"role":"assistant","content":[{"type":"tool_use","id":"t2","name":"Read","input":{"file_path":"/a.kt","description":"x"}}]}}"""
        val blank = """{"type":"assistant","uuid":"a3","message":{"role":"assistant","content":[{"type":"tool_use","id":"t3","name":"Bash","input":{"command":"ls","description":"  "}}]}}"""
        assertNull(ClaudeTranscriptParser.parse(listOf(read)).single().title)
        assertNull(ClaudeTranscriptParser.parse(listOf(blank)).single().title)
    }

    private val cwd = "/home/luxiang/project/work/xn-ontology"

    @Test
    fun `cd into the session directory is stripped`() {
        assertEquals("gh pr checks 480", ToolCalls.stripCwd("cd /home/luxiang/project/work/xn-ontology && gh pr checks 480", cwd))
        assertEquals("git status", ToolCalls.stripCwd("cd \"/home/luxiang/project/work/xn-ontology/\" ; git status", cwd))
        assertEquals("make", ToolCalls.stripCwd("cd ~/project/work/xn-ontology && make", cwd, home = "/home/luxiang"))
    }

    @Test
    fun `cd elsewhere, no cd, or nothing after it is kept`() {
        assertEquals("cd backend && uv run ruff check .", ToolCalls.stripCwd("cd backend && uv run ruff check .", cwd))
        assertEquals("cd /tmp && ls", ToolCalls.stripCwd("cd /tmp && ls", cwd))
        assertEquals("ls -la", ToolCalls.stripCwd("ls -la", cwd))
        assertEquals("cd /x && ls", ToolCalls.stripCwd("cd /x && ls", null))
    }

    @Test
    fun `monitor uses its description too`() {
        val line = """{"type":"assistant","uuid":"a4","message":{"role":"assistant","content":[{"type":"tool_use","id":"t4","name":"Monitor","input":{"description":"PR #480 CI check results","timeout_ms":1800000,"command":"cd /x; prev=\"\"; sleep 60"}}]}}"""
        assertEquals("PR #480 CI check results", ClaudeTranscriptParser.parse(listOf(line)).single().title)
    }
}
