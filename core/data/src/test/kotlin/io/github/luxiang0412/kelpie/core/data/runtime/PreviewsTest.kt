package io.github.luxiang0412.kelpie.core.data.runtime

import io.github.luxiang0412.kelpie.core.herdr.ChatMessage
import io.github.luxiang0412.kelpie.core.herdr.ChatMessage.Kind
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

    @Test fun previousTurnTextIsNotReused_pendingArgsShown() {
        // Claude 弹权限框：这一轮没有正文，只有工具调用；上一轮的回复不该冒充问题
        val p = Previews.fromTranscript(listOf(
            ChatMessage(Kind.USER, "pwd?"),
            ChatMessage(Kind.ASSISTANT, "The current directory is /home/u."),
            ChatMessage(Kind.USER, "run ls"),
            ChatMessage(Kind.TOOL_CALL, "{\"command\":\"ls -la\",\"description\":\"List\"}", toolName = "Bash"),
        ))!!
        assertEquals("", p.text)
        assertEquals("Bash", p.pendingTool)
        assertEquals("ls -la", p.pendingArgs)
    }

    @Test fun nothingInThisTurnFallsBackToScreen() =
        assertNull(Previews.fromTranscript(listOf(ChatMessage(Kind.ASSISTANT, "old"), ChatMessage(Kind.USER, "new"))))

    @Test fun claudePermissionPromptOnScreen() {
        val raw = """
❯ Use the Bash tool to run exactly: pwd
● Bash(pwd)
  ⎿  Waiting…
────────────────────────────────────────────────────────
 Bash command
 Print current working directory
╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌
 pwd
╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌
 │ Hook PreToolUse:Bash requires confirmation for this command:
 │ 手机 120 秒内没有回答，改在终端确认 [settings]
 Do you want to proceed?
 ❯ 1. Yes
   2. No
 Esc to cancel · Tab to amend
"""
        val p = Previews.fromScreen(raw)!!
        assertEquals("Bash command", p.pendingTool)
        assertEquals("Print current working directory\npwd\nHook PreToolUse:Bash requires confirmation for this command:\n手机 120 秒内没有回答，改在终端确认 [settings]", p.text)
    }

    /** Codex 0.166 的终端确认框（2026-11-03 在沙箱 pane 里实际截到的屏幕）。 */
    @Test fun codexCommandPrompt() {
        val raw = """
› Now run: touch timeout.txt (request approval again)

• Running touch timeout.txt

  Would you like to run the following command?

  Environment: local

  Reason: Allow creating timeout.txt file?

  $ touch timeout.txt

› 1. Yes, proceed (y)
  2. Yes, and don't ask again for commands that start with `touch timeout.txt` (p)
  3. No, and tell Codex what to do differently (esc)

  Press enter to confirm or esc to cancel
"""
        io.github.luxiang0412.kelpie.core.herdr.L10n.update(java.util.Locale.SIMPLIFIED_CHINESE)
        val p = Previews.fromScreen(raw)!!
        assertEquals("命令", p.pendingTool)
        assertEquals("touch timeout.txt\n# Allow creating timeout.txt file?", p.text)
    }

    @Test fun codexPromptNeedsOptions() {
        assertNull(Previews.codexPrompt("  Would you like to run the following command?\n  $ ls\n"))
    }
}
