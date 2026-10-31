package dev.herdr.core.herdr

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PiTranscriptParserTest {

    private val sample = listOf(
        """{"type":"session","id":"s1"}""",
        """{"type":"model_change","id":"m1"}""",
        """{"type":"message","timestamp":100,"message":{"role":"system","content":[{"type":"text","text":"sys"}]}}""",
        """{"type":"message","timestamp":101,"message":{"role":"user","content":[{"type":"text","text":"帮我看看"}]}}""",
        """{"type":"message","timestamp":102,"message":{"role":"assistant","content":[{"type":"thinking","thinking":"先看目录"},{"type":"toolCall","name":"bash","arguments":{"cmd":"ls"}}]}}""",
        """{"type":"message","timestamp":103,"message":{"role":"toolResult","content":[{"type":"text","text":"a.txt\nb.txt"}]}}""",
        """{"type":"message","timestamp":104,"message":{"role":"assistant","content":[{"type":"text","text":"目录里有 a.txt 和 b.txt"}]}}""",
        "not-json",
        """{"type":"message","message":{"role":"assistant","content":"纯字符串内容"}}""",
    )

    @Test
    fun parses_user_assistant_thinking_tool_and_result() {
        val msgs = PiTranscriptParser.parse(sample)
        assertEquals(
            listOf(
                ChatMessage.Kind.USER,
                ChatMessage.Kind.THINKING,
                ChatMessage.Kind.TOOL_CALL,
                ChatMessage.Kind.TOOL_RESULT,
                ChatMessage.Kind.ASSISTANT,
                ChatMessage.Kind.ASSISTANT,
            ),
            msgs.map { it.kind },
        )
        assertEquals("帮我看看", msgs[0].text)
        assertEquals("bash", msgs[2].toolName)
        assertTrue(msgs[2].text.contains("ls"))
        assertEquals(101L, msgs[0].timestamp)
    }

    @Test
    fun ignores_unknown_and_broken_lines() {
        val msgs = PiTranscriptParser.parse(
            listOf("", "{", """{"type":"unknown_thing"}""", """{"type":"message"}"""),
        )
        assertTrue(msgs.isEmpty())
    }

    @Test
    fun keeps_last_messages_when_trimmed() {
        val many = (1..10).map {
            """{"type":"message","message":{"role":"user","content":[{"type":"text","text":"m$it"}]}}"""
        }
        val msgs = PiTranscriptParser.parse(many)
        assertEquals(10, msgs.size)
        assertEquals("m1", msgs.first().text)
    }
}
