package dev.herdr.core.data.runtime

import dev.herdr.core.herdr.ChatMessage
import dev.herdr.core.herdr.ChatMessage.Kind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PreviewsTest {
    @Test fun lastAssistantWins_pendingToolNamed() {
        val p = Previews.fromTranscript(listOf(
            ChatMessage(Kind.USER, "修一下"),
            ChatMessage(Kind.ASSISTANT, "我准备删除 build 目录，可以吗？"),
            ChatMessage(Kind.TOOL_CALL, "rm -rf build", toolName = "bash"),
        ))!!
        assertEquals("我准备删除 build 目录，可以吗？", p.text)
        assertEquals("bash", p.pendingTool)
    }

    @Test fun finishedToolIsNotPending() {
        val p = Previews.fromTranscript(listOf(
            ChatMessage(Kind.TOOL_CALL, "ls", toolName = "bash"),
            ChatMessage(Kind.TOOL_RESULT, "a b"),
            ChatMessage(Kind.ASSISTANT, "选 A 还是 B？"),
        ))!!
        assertNull(p.pendingTool)
    }

    @Test fun screenDropsFramesAndBlanks() {
        val p = Previews.fromScreen("╭────╮\n│    │\n\n  Allow edit to main.kt?\n  1. Yes\n  2. No\n╰────╯\n")!!
        assertEquals("  Allow edit to main.kt?\n  1. Yes\n  2. No", p.text)
    }
}
