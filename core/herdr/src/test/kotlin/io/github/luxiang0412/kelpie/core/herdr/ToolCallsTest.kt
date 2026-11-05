package io.github.luxiang0412.kelpie.core.herdr

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.After
import org.junit.Before
import org.junit.Test

class ToolCallsTest {

    private fun j(s: String) = Json.parseToJsonElement(s)

    @Test fun commonToolsSummarised() {
        assertEquals("ls -la", ToolCalls.summary("Bash", j("""{"command":"ls -la","description":"List"}""")))
        assertEquals("/a/b.kt", ToolCalls.summary("Edit", j("""{"file_path":"/a/b.kt","old_string":"x","new_string":"y"}""")))
        assertEquals("TODO", ToolCalls.summary("Grep", j("""{"pattern":"TODO","path":"src"}""")))
        assertEquals("Explore the parser", ToolCalls.summary("Task", j("""{"description":"Explore the parser","prompt":"long"}""")))
        assertEquals("Update todos (2)", ToolCalls.summary("TodoWrite", j("""{"todos":[{},{}]}""")))
        // 不认识的工具：第一个非空字符串字段
        assertEquals("hello", ToolCalls.summary("Mystery", j("""{"n":1,"msg":"hello"}""")))
        // 一个字符串都没有：退回原始 JSON
        assertEquals("""{"n":1}""", ToolCalls.summary("Mystery", j("""{"n":1}""")))
    }

    @Test fun longInputStillSummarised() {
        // 以前先截到 300 字符再解析 → 非法 JSON → 显示原始 JSON
        val cmd = "echo " + "x".repeat(1000)
        val s = ToolCalls.summary("Bash", j("""{"command":"$cmd"}"""))
        assertTrue(s.startsWith("echo xxx"))
        assertEquals(301, s.length)
    }

    private val ask = j(
        """{"questions":[
            {"question":"知识库的可见范围，K1 怎么控制？","header":"知识库权限","multiSelect":false,"options":[
              {"label":"先按助手绑定控制 (Recommended)","description":"不改权限包和下发逻辑"},
              {"label":"照计划接入权限包","description":"要改权限包结构"}]},
            {"question":"要不要写迁移？","header":"迁移","multiSelect":true,"options":[{"label":"写"},{"label":"不写（推荐）"}]}
        ]}""",
    )

    @Test fun askUserQuestionParsed() {
        val qs = ToolCalls.questions("AskUserQuestion", ask)
        assertEquals(2, qs.size)
        assertEquals("知识库权限", qs[0].header)
        assertFalse(qs[0].multiSelect)
        assertTrue(qs[1].multiSelect)
        assertEquals(listOf(true, false), qs[0].options.map { it.recommended })
        assertEquals("先按助手绑定控制", qs[0].options[0].plainLabel)
        assertEquals("不写", qs[1].options[1].plainLabel)
        assertTrue(qs[1].options[1].recommended)
        assertEquals("不改权限包和下发逻辑", qs[0].options[0].description)
        assertEquals("知识库的可见范围，K1 怎么控制？ (2 questions)", ToolCalls.summary("AskUserQuestion", ask))
        // 别的工具即使有 questions 字段也不当选择题
        assertTrue(ToolCalls.questions("Bash", ask).isEmpty())
    }

    @Test fun answersAndCacheRoundTrip() {
        val answers = ToolCalls.answers(j("""{"questions":[],"answers":{"要不要写迁移？":"写, 不写（推荐）"},"annotations":{}}"""))
        assertEquals(mapOf("要不要写迁移？" to "写, 不写（推荐）"), answers)
        assertNull(ToolCalls.answers(j("""{"stdout":"x"}""")))

        val qs = ToolCalls.questions("AskUserQuestion", ask)
        assertEquals(qs to answers, ToolCalls.decode(ToolCalls.encode(qs, answers)))
        assertEquals(qs to null, ToolCalls.decode(ToolCalls.encode(qs, null)))
        assertNull(ToolCalls.encode(emptyList(), null))
        assertEquals(emptyList<AskQuestion>() to null, ToolCalls.decode("not json"))
    }

    @Before fun english() = L10n.update(java.util.Locale.ENGLISH)
    @After fun restore() = L10n.update(java.util.Locale.getDefault())
}
